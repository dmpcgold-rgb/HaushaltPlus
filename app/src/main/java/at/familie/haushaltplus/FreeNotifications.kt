package at.familie.haushaltplus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.Date
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Free Spark-plan fallback: Firestore activity feed + local Android notifications.
 * Live while connected; background checks are deferred by Android, not instant FCM.
 */
object FreeNotifications {
    private val lock = Any()
    private const val WORK_NAME = "haushalt_free_notifications"
    private const val CHANNEL = "haushalt_familie"

    fun schedule(context: Context) {
        val eco = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
            .getBoolean("eco_mode", true)
        val work = PeriodicWorkRequestBuilder<FreeNotificationWorker>(
            if (eco) 60 else 15, TimeUnit.MINUTES
        ).setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work
        )
    }

    fun quietHours(context: Context): Boolean {
        val prefs = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("quiet_enabled", true)) return false
        val start = runCatching { java.time.LocalTime.parse(prefs.getString("quiet_start", "22:00")) }
            .getOrDefault(java.time.LocalTime.of(22, 0))
        val end = runCatching { java.time.LocalTime.parse(prefs.getString("quiet_end", "07:00")) }
            .getOrDefault(java.time.LocalTime.of(7, 0))
        val current = java.time.LocalTime.now()
        return if (start <= end) current >= start && current < end
            else current >= start || current < end
    }

    fun scheduleLocal(context: Context) {
        val eco = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
            .getBoolean("eco_mode", true)
        val request = PeriodicWorkRequestBuilder<LocalReminderWorker>(
            if (eco) 60 else 15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "haushalt_local_due", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun process(context: Context, uid: String, homeId: String, documents: List<DocumentSnapshot>) {
        if (quietHours(context)) return
        val prefs = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
        val key = "seen_" + uid + "_" + homeId
        synchronized(lock) {
            val known = prefs.getString(key, null)?.split("|")?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            // Seed previous events on the first sync; avoid notification floods.
            val firstSync = !prefs.contains(key)
            val ordered = documents.sortedBy { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
            val messages = mutableListOf<Pair<String, Int>>()
            for (doc in ordered) {
                if (known.add(doc.id) && !firstSync && doc.getString("actorUid") != uid) {
                    val kind = doc.getString("kind") ?: "all"
                    val allowed = when(kind) {
                        "shopping" -> prefs.getBoolean("notify_shopping", true)
                        "tasks" -> prefs.getBoolean("notify_tasks", true)
                        "family" -> prefs.getBoolean("notify_family", true)
                        else -> true
                    }
                    val title = doc.getString("title") ?: continue
                    if (allowed && title.isNotBlank()) messages.add(
                        title to if (kind == "shopping") 1 else if (kind == "tasks") 2 else 0
                    )
                }
            }
            if (prefs.getBoolean("notify_summary", true) && messages.size > 1) {
                show(context, messages.size.toString() + " neue Haushaltsänderungen", 0)
            } else messages.forEach { (description, tab) -> show(context, description, tab) }
            val latest = documents.mapNotNull { it.getTimestamp("createdAt")?.toDate()?.time }
                .maxOrNull() ?: 0L
            val cursorKey = "activity_after_" + uid + "_" + homeId
            val cursor = prefs.getLong(cursorKey, 0L)
            prefs.edit().putString(key, known.toList().takeLast(120).joinToString("|"))
                .putLong(cursorKey, maxOf(cursor, latest)).apply()
        }
    }

    private data class Reminder(val key: String, val text: String)

    private fun emitDue(context: Context, uid: String, homeId: String,
        tasks: List<Pair<String, Pair<String, String>>>) {
        val prefs = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify_due", true) || quietHours(context)) return
        val today = java.time.LocalDate.now()
        val daysEarly = prefs.getInt("notify_before_days", 1).coerceIn(0, 7)
        val candidates = tasks.mapNotNull { (id, detail) ->
            val (title, due) = detail
            val date = runCatching { java.time.LocalDate.parse(due) }.getOrNull() ?: return@mapNotNull null
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
            if (days > daysEarly) return@mapNotNull null
            val key = "due_" + uid + "_" + homeId + "_" + today + "_" + id
            if (prefs.getBoolean(key, false)) null else Reminder(key,
                (when {
                    days < 0 -> "Überfällig: "
                    days == 0L -> "Heute fällig: "
                    days == 1L -> "Morgen fällig: "
                    else -> "In " + days + " Tagen fällig: "
                }) + title)
        }
        if (candidates.isEmpty()) return
        synchronized(lock) {
            val summary = prefs.getBoolean("notify_summary", true) && candidates.size > 1
            if (summary) {
                if (show(context, candidates.size.toString() + " Aufgaben fällig oder bald fällig", 2)) {
                    val edit = prefs.edit()
                    candidates.forEach { edit.putBoolean(it.key, true) }
                    edit.apply()
                }
            } else {
                candidates.take(10).forEach { task ->
                    if (show(context, task.text, 2)) prefs.edit().putBoolean(task.key, true).apply()
                }
            }
        }
    }

    fun dueReminders(context: Context, uid: String, homeId: String, documents: List<DocumentSnapshot>) {
        emitDue(context, uid, homeId, documents.filter { it.getBoolean("done") != true }
            .map { it.id to ((it.getString("title") ?: "Aufgabe") to
                (it.getString("due") ?: "")) })
    }

    fun localDueReminders(context: Context, tasks: List<HouseholdTask>) {
        emitDue(context, "local", "local", tasks.filterNot { it.done }
            .map { it.id to (it.title to it.due) })
    }

    fun expiryReminder(context: Context, uid: String, homeId: String,
        stock: List<StockItem>) {
        val prefs = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify_expiry", true) || quietHours(context)) return
        val before = prefs.getInt("expiry_before_days", 3).coerceIn(0, 14)
        val today = java.time.LocalDate.now()
        synchronized(lock) {
            val candidates = stock.mapNotNull { item ->
                if (item.quantity <= 0) return@mapNotNull null
                val due = V4Logic.expiryDays(item, today) ?: return@mapNotNull null
                if (due > before) return@mapNotNull null
                val key = "expiry_" + uid + "_" + homeId + "_" + today + "_" + item.id
                if (prefs.getBoolean(key, false)) null else key to (
                    if (due < 0) item.name + ": MHD überschritten"
                    else if (due == 0L) item.name + ": MHD heute"
                    else item.name + ": MHD in " + due + " Tagen")
            }
            if (candidates.isEmpty()) return
            if (candidates.size > 1 && prefs.getBoolean("notify_summary", true)) {
                if (show(context, candidates.size.toString() + " Vorräte mit nahem MHD", 3)) {
                    val edit = prefs.edit()
                    candidates.forEach { edit.putBoolean(it.first, true) }
                    edit.apply()
                }
            } else candidates.take(10).forEach { (key, message) ->
                if (show(context, message, 3)) prefs.edit().putBoolean(key, true).apply()
            }
        }
    }

    private fun show(context: Context, description: String, openTab: Int = 0): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "Familienänderungen", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        val pending = PendingIntent.getActivity(
            context, openTab, Intent(context, MainActivity::class.java)
                .putExtra("open_tab", openTab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Haushalt+")
            .setContentText(description)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify((System.nanoTime() and 0x7fffffff).toInt(), notification)
        return true
    }
}

class FreeNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_ENABLED) return Result.success()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("haushalt_cloud", Context.MODE_PRIVATE)
        if (prefs.getString("homeUid", null) != uid) return Result.success()
        val homeId = prefs.getString("homeId", null) ?: return Result.success()
        return try {
            val db = FirebaseFirestore.getInstance()
            val activity = db.collection("households").document(homeId).collection("activity")
            val notificationPrefs = applicationContext.getSharedPreferences(
                "haushalt_notifications", Context.MODE_PRIVATE
            )
            val lastSeen = notificationPrefs.getLong("activity_after_" + uid + "_" + homeId, 0L)
            // Only query new activity after initial baseline, rather than re-reading
            // dozens of already-delivered events on every periodic check.
            val query = if (lastSeen > 0L) {
                activity.orderBy("createdAt", Query.Direction.ASCENDING)
                    .startAfter(Timestamp(Date(lastSeen))).limit(40)
            } else activity.orderBy("createdAt", Query.Direction.DESCENDING).limit(40)
            val docs = query.get(Source.SERVER).await()
            FreeNotifications.process(applicationContext, uid, homeId, docs.documents)

            // Task reminders only need a daily background read. The foreground
            // task listener still handles reminders while the app is open.
            val today = java.time.LocalDate.now().toString()
            val dueKey = "due_checked_" + uid + "_" + homeId
            val canNotify = android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            if (canNotify && !FreeNotifications.quietHours(applicationContext) &&
                notificationPrefs.getString(dueKey, null) != today) {
                val tasks = db.collection("households").document(homeId).collection("tasks")
                    .whereEqualTo("done", false).limit(100).get(Source.SERVER).await()
                FreeNotifications.dueReminders(applicationContext, uid, homeId, tasks.documents)
                val stock = db.collection("households").document(homeId).collection("stock")
                    .limit(100).get(Source.SERVER).await()
                FreeNotifications.expiryReminder(applicationContext, uid, homeId, stock.documents.map { d ->
                    StockItem(d.id, d.getString("name") ?: "",
                        (d.getLong("quantity") ?: 0L).toInt(), (d.getLong("minimum") ?: 1L).toInt(),
                        d.getString("unit") ?: "Stk", d.getString("expiresOn") ?: "")
                })
                notificationPrefs.edit().putString(dueKey, today).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class LocalReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (BuildConfig.FIREBASE_ENABLED || FreeNotifications.quietHours(applicationContext))
            return Result.success()
        return try {
            val data = LocalStore(applicationContext).data.value
            FreeNotifications.localDueReminders(applicationContext, data.tasks)
            FreeNotifications.expiryReminder(applicationContext, "local", "local", data.stock)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
