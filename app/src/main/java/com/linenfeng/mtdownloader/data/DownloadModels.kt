package com.linenfeng.mtdownloader.data

/**
 * 下载状态
 */
enum class DownloadStatus(val value: Int) {
    WAITING(0),
    DOWNLOADING(1),
    PAUSED(2),
    COMPLETED(3),
    FAILED(4),
    CANCELED(5);

    companion object {
        fun fromValue(v: Int): DownloadStatus =
            entries.firstOrNull { it.value == v } ?: WAITING

        /** 是否处于活跃下载状态（占用线程/前台服务） */
        fun DownloadStatus.isActive(): Boolean = this == DOWNLOADING || this == WAITING

        /** 是否可继续 */
        fun DownloadStatus.isResumable(): Boolean =
            this == PAUSED || this == FAILED || this == CANCELED

        /** 是否已结束 */
        fun DownloadStatus.isFinished(): Boolean =
            this == COMPLETED || this == CANCELED
    }
}

/**
 * 单个分块信息（不持久化，由任务运行时构造）
 */
data class BlockInfo(
    val index: Int,
    val start: Long,
    val end: Long,
    var downloaded: Long = 0L
) {
    val size: Long get() = end - start + 1
    val isDone: Boolean get() = downloaded >= size
}

/**
 * 实时进度数据（用于 UI/通知展示）
 */
data class ProgressInfo(
    val taskId: Long,
    val status: DownloadStatus,
    val downloaded: Long,
    val total: Long,
    val speed: Long,          // bytes/s
    val remainingMs: Long,    // 剩余时间
    val threads: Int,         // 当前活跃线程数
    val error: String? = null
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((downloaded * 100 / total).toInt()).coerceIn(0, 100)

    companion object {
        fun idle(taskId: Long, status: DownloadStatus, total: Long = 0, downloaded: Long = 0) =
            ProgressInfo(taskId, status, downloaded, total, 0, 0, 0)
    }
}
