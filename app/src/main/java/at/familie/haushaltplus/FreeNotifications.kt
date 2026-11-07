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
            prefs.edit().putString(key, known.toList().takeLast(120).joinToString("|")).apply()
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
            val docs = FirebaseFirestore.getInstance().collection("households").document(homeId)
                .collection("activity")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40).get(Source.SERVER).await()
            FreeNotifications.process(applicationContext, uid, homeId, docs.documents)
            val tasks = FirebaseFirestore.getInstance().collection("households").document(homeId)
                .collection("tasks").whereEqualTo("done", false).limit(60).get(Source.SERVER).await()
            FreeNotifications.dueReminders(applicationContext, uid, homeId, tasks.documents)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
