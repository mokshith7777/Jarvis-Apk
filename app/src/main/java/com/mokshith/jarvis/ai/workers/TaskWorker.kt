package com.mokshith.jarvis.ai.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mokshith.jarvis.ai.R
import com.mokshith.jarvis.ai.app.JarvisApplication
import kotlinx.coroutines.flow.first

class TaskWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val container = (applicationContext as JarvisApplication).container
        val outcome = container.taskEngine.executeTaskNow(taskId)
        val task = container.database.taskDao().getById(taskId)
        val settings = container.settingsStore.settingsFlow.first()

        if (settings.notificationsEnabled && task != null) {
            val title = if (outcome.isSuccess) "JARVIS Task Completed" else "JARVIS Task Failed"
            val body = task.completionSummary?.take(180) ?: task.failureReason ?: task.title
            notifyUser(title, body, taskId.hashCode())
        }

        // Notify Telegram chat if task originated from Telegram
        if (task?.telegramChatId != null) {
            val text = if (outcome.isSuccess) {
                "Task completed: ${task.title}\n\n${task.completionSummary.orEmpty().take(3200)}"
            } else {
                "Task failed: ${task.title}\nReason: ${task.failureReason ?: "Unknown error"}"
            }
            runCatching { container.telegramConnector.sendMessage(task.telegramChatId, text) }
        }

        return if (outcome.isSuccess) Result.success() else Result.failure()
    }

    private fun notifyUser(title: String, body: String, notificationId: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Background Tasks",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(notificationId, notif) }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "jarvis_tasks_channel"
    }
}
