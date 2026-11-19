package com.mokshith.jarvis.ai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mokshith.jarvis.ai.R
import com.mokshith.jarvis.ai.app.JarvisApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Battery-aware foreground service used ONLY when the user explicitly starts Telegram polling.
 * Android OS may stop or throttle background services if the user force-stops the app or under severe Doze.
 */
class TelegramPollingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val container = (applicationContext as JarvisApplication).container

        if (action == ACTION_STOP) {
            serviceScope.launch { container.telegramConnector.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("JARVIS Telegram Channel Active")
            .setContentText("Listening for authorized Telegram commands")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            container.telegramConnector.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val container = (applicationContext as JarvisApplication).container
        serviceScope.launch { container.telegramConnector.stop() }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS Telegram Channel", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_START = "com.mokshith.jarvis.ai.action.START_TELEGRAM"
        const val ACTION_STOP = "com.mokshith.jarvis.ai.action.STOP_TELEGRAM"
        private const val CHANNEL_ID = "jarvis_telegram_channel"
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context) {
            val intent = Intent(context, TelegramPollingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TelegramPollingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
