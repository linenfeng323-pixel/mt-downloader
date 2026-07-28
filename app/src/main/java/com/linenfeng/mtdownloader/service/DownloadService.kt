package com.linenfeng.mtdownloader.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.linenfeng.mtdownloader.App
import com.linenfeng.mtdownloader.Constants
import com.linenfeng.mtdownloader.R
import com.linenfeng.mtdownloader.data.DownloadStatus
import com.linenfeng.mtdownloader.data.ProgressInfo
import com.linenfeng.mtdownloader.ui.MainActivity
import com.linenfeng.mtdownloader.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 通知构建工具
 */
object DownloadNotifications {

    fun buildForeground(
        context: Context,
        info: ProgressInfo?,
        activeCount: Int,
        fileName: String
    ): Notification {
        val title = if (activeCount > 1)
            context.getString(R.string.app_name) + " · $activeCount 个任务"
        else fileName
        val content = info?.let {
            val remain = if (it.remainingMs > 0) FormatUtils.duration(it.remainingMs) else "--"
            context.getString(
                R.string.notif_downloading,
                FormatUtils.size(it.downloaded),
                FormatUtils.size(it.speed),
                remain
            )
        } ?: context.getString(R.string.state_waiting)

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent(context))

        if (info != null && info.total > 0) {
            builder.setProgress(100, info.percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun buildCompleted(context: Context, fileName: String): Notification {
        return NotificationCompat.Builder(context, Constants.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_completed, fileName))
            .setContentText(fileName)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent(context))
            .build()
    }

    fun buildError(context: Context, fileName: String, error: String): Notification {
        return NotificationCompat.Builder(context, Constants.CHANNEL_ERROR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_failed, fileName))
            .setContentText(error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(context))
            .build()
    }

    private fun openIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}

/**
 * 下载前台服务：监听引擎进度，更新通知；无活跃任务时自动停止。
 */
class DownloadService : android.app.Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var lastNotifTime = 0L
    private val notificationManager by lazy {
        androidx.core.app.NotificationManagerCompat.from(this)
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(buildInitialNotification())
        startCollecting()
    }

    private fun startCollecting() {
        val engine = App.instance.engine
        collectJob = serviceScope.launch {
            engine.progresses.combine(engine.activeCount) { map, active ->
                Pair(map, active)
            }.collect { (map, active) ->
                val now = System.currentTimeMillis()
                if (now - lastNotifTime < 400) return@collect
                lastNotifTime = now
                val activeInfos = map.values.filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.WAITING
                }
                if (activeInfos.isEmpty() || active == 0) {
                    // 无活跃任务，停止服务
                    stopForegroundSafe()
                    stopSelf()
                    return@collect
                }
                val primary = activeInfos.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                    ?: activeInfos.first()
                val fileName = runCatching {
                    App.instance.repository.getById(primary.taskId)?.fileName
                }.getOrNull() ?: getString(R.string.app_name)
                val notif = DownloadNotifications.buildForeground(
                    this@DownloadService, primary, active, fileName
                )
                notify(notif)
            }
        }
    }

    private fun buildInitialNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.state_waiting))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun startForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Constants.NOTIF_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(Constants.NOTIF_ID_FOREGROUND, notification)
            }
        } catch (e: Throwable) {
            // 后台启动前台服务受限时忽略
        }
    }

    private fun notify(notification: Notification) {
        runCatching {
            notificationManager.notify(Constants.NOTIF_ID_FOREGROUND, notification)
        }
    }

    private fun stopForegroundSafe() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        collectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}
