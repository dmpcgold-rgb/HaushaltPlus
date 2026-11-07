package at.familie.haushaltplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Foreground and background data-message handling; server-triggered FCM push. */
class HouseholdMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val homeId = getSharedPreferences("haushalt_cloud", Context.MODE_PRIVATE).getString("homeId", null) ?: return
        FirebaseFirestore.getInstance().collection("households").document(homeId)
            .collection("devices").document(uid).set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: "Haushalt+"
        val text = message.data["body"] ?: message.notification?.body ?: "Es gibt eine Änderung im Haushalt."
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "haushalt_events"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Haushaltsmeldungen", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setContentIntent(pending).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
