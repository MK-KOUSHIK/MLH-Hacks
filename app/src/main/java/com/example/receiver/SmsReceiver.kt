package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver.PendingResult
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.api.GeminiClient
import com.example.api.ScanResponse
import com.example.data.AppDatabase
import com.example.data.ThreatEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Log.d(TAG, "SmsReceiver: Incoming SMS detected in background!")

        val pendingResult: PendingResult = goAsync()
        val appCtx = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@launch

                // Gather SMS details
                val sender = messages[0].displayOriginatingAddress ?: "Unknown Senders"
                val body = messages.joinToString("") { it.messageBody ?: "" }

                Log.d(TAG, "SmsReceiver: Intercepted text from $sender. Length: ${body.length}")

                // Run real-time AI scan
                val response = GeminiClient.scanContent("SMS", body)
                Log.d(TAG, "SmsReceiver: Scanned with result: ${response.riskLevel}, score ${response.riskScore}")

                // Persist the scan result to Room SQLite immediately
                val db = AppDatabase.getDatabase(appCtx)
                val threatEntity = ThreatEntity(
                    type = "SMS",
                    content = body,
                    sender = sender,
                    riskLevel = response.riskLevel,
                    riskScore = response.riskScore,
                    analysisVerdict = response.verdict,
                    recommendedAction = response.recommendedAction,
                    timestamp = System.currentTimeMillis()
                )
                db.threatDao().insertThreat(threatEntity)

                // Always create channel & notify if risk indicates danger/warning
                createNotificationChannel(appCtx)
                
                // Show notification with threat details
                showInstantNotification(appCtx, sender, body, response)

            } catch (e: Exception) {
                Log.e(TAG, "SmsReceiver background scanner execution error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PhishSmsReceiver"
        private const val CHANNEL_ID = "phishguard_alerts"
        private const val NOTIFICATION_ID_BASE = 5000

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "PhishGuard Threat Alerts"
                val descriptionText = "Instant warnings and threat detections for incoming communications."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showInstantNotification(context: Context, sender: String, body: String, response: ScanResponse) {
            val isThreat = response.riskLevel != "SAFE"
            val emoji = when (response.riskLevel) {
                "DANGEROUS" -> "🚨"
                "SUSPICIOUS" -> "⚠️"
                else -> "✅"
            }

            val title = "$emoji PhishGuard: SMS is ${response.riskLevel} (${response.riskScore}%)"
            val contentText = if (isThreat) {
                "Text from $sender flagged as threat. Tap to investigate."
            } else {
                "Secure scan passed. Content from $sender is verified safe."
            }

            // Big text styling for deeper contextual details
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(
                    "Sender: $sender\n\n" +
                    "Content: \"$body\"\n\n" +
                    "Verdict: ${response.verdict}\n\n" +
                    "Action Guidance: ${response.recommendedAction}"
                )

            // Intent to open Main board on tap
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                openIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Dynamic icon setting depending on risk
            val iconRes = android.R.drawable.stat_sys_warning

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10000).toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())
        }
    }
}
