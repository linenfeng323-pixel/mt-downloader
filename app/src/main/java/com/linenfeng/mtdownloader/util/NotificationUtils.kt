package com.linenfeng.mtdownloader.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.linenfeng.mtdownloader.Constants

/**
 * 通知工具：渠道创建、通知发送
 */
object NotificationUtils {

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                Constants.CHANNEL_DOWNLOAD,
                context.getString(com.linenfeng.mtdownloader.R.string.notif_channel_download),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(com.linenfeng.mtdownloader.R.string.notif_channel_download_desc)
                setShowBadge(false)
            },
            NotificationChannel(
                Constants.CHANNEL_DONE,
                context.getString(com.linenfeng.mtdownloader.R.string.notif_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(com.linenfeng.mtdownloader.R.string.notif_channel_done_desc)
            },
            NotificationChannel(
                Constants.CHANNEL_ERROR,
                context.getString(com.linenfeng.mtdownloader.R.string.notif_channel_error),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.linenfeng.mtdownloader.R.string.notif_channel_error_desc)
            }
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
