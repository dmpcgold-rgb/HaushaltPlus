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
        val work = PeriodicWorkRequestBuilder<FreeNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work
        )
    }

    fun process(context: Context, uid: String, homeId: String, documents: List<DocumentSnapshot>) {
        val prefs = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
        val key = "seen_" + uid + "_" + homeId
        synchronized(lock) {
            val known = prefs.getString(key, null)?.split("|")?.filter { it.isNotBlank() }?.toMutableSet()
                ?: mutableSetOf()
            // Seed previous events on the first sync; avoid notification floods.
            val firstSync = !prefs.contains(key)
            val ordered = documents.sortedBy { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
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
                    if (allowed && title.isNotBlank()) show(context, title)
                }
            }
            val latest = documents.mapNotNull { it.getTimestamp("createdAt")?.toDate()?.time }
                .maxOrNull() ?: 0L
            val cursorKey = "activity_after_" + uid + "_" + homeId
            val cursor = prefs.getLong(cursorKey, 0L)
            prefs.edit().putString(key, known.toList().takeLast(120).joinToString("|"))
                .putLong(cursorKey, maxOf(cursor, latest)).apply()
        }
    }

    fun dueReminders(context: Context, uid: String, homeId: String, documents: List<DocumentSnapshot>) {
        val prefs = context.getSharedPreferences("haushalt_notifications", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify_due", true)) return
        val today = java.time.LocalDate.now().toString()
        synchronized(lock) {
            documents.filter { it.getBoolean("done") != true &&
                    !it.getString("due").isNullOrBlank() && (it.getString("due") ?: "") <= today }
                .forEach { doc ->
                    val key = "due_" + uid + "_" + homeId + "_" + today + "_" + doc.id
                    if (!prefs.getBoolean(key, false)) {
                        val shown = show(context, "Heute fällig: " + (doc.getString("title") ?: "Haushaltsaufgabe"))
                        // Keep reminders eligible if Android notification permission is missing.
                        if (shown) prefs.edit().putBoolean(key, true).apply()
                    }
                }
        }
    }

    private fun show(context: Context, description: String): Boolean {
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
            context, 0, Intent(context, MainActivity::class.java),
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
            if (canNotify && notificationPrefs.getString(dueKey, null) != today) {
                val tasks = db.collection("households").document(homeId).collection("tasks")
                    .whereEqualTo("done", false).limit(100).get(Source.SERVER).await()
                FreeNotifications.dueReminders(applicationContext, uid, homeId, tasks.documents)
                notificationPrefs.edit().putString(dueKey, today).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
