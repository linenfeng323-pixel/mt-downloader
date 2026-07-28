package com.linenfeng.mtdownloader.util

import java.text.DecimalFormat

/**
 * 格式化工具：文件大小、速度、时间等
 */
object FormatUtils {

    private val df = DecimalFormat("0.00")

    /** 字节数转可读字符串，例如 1.50 MB */
    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.lastIndex) {
            v /= 1024.0
            i++
        }
        return "${df.format(v)} ${units[i]}"
    }

    /** 每秒字节数转速度字符串 */
    fun speed(bytesPerSecond: Long): String = "${size(bytesPerSecond)}/s"

    /** 毫秒转 mm:ss 或 hh:mm:ss */
    fun duration(ms: Long): String {
        if (ms <= 0) return "--:--"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }

    /** 百分比，保留一位小数 */
    fun percent(downloaded: Long, total: Long): Int {
        if (total <= 0) return 0
        val p = (downloaded * 100 / total).toInt()
        return p.coerceIn(0, 100)
    }

    /** 时间戳转简短日期 */
    fun timeAgo(ts: Long): String {
        if (ts <= 0) return ""
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            else -> "${diff / 86_400_000} 天前"
        }
    }
}
