package com.islamgpt.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AssistantForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "IslamGPT AI", NotificationManager.IMPORTANCE_LOW)
            channel.description = "حالة خدمة المساعد الذكي"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("IslamGPT AI يعمل")
            .setContentText("المساعد جاهز. استخدم التطبيق لإرسال أمر نصي أو صوتي.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent).setOngoing(true).build()
    }

    companion object {
        const val ACTION_STOP = "com.islamgpt.ai.STOP_ASSISTANT"
        private const val CHANNEL_ID = "islamgpt_assistant"
        private const val NOTIFICATION_ID = 7314
    }
}
