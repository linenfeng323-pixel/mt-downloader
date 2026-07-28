package com.linenfeng.mtdownloader

/**
 * 全局常量
 *
 * 作者：林恩风
 */
object Constants {
    const val APP_PACKAGE = "com.linenfeng.mtdownloader"
    const val APP_NAME = "多线程下载"
    const val APP_AUTHOR = "林恩风"
    const val APP_VERSION = "1.0.0"

    /** 下载子目录名：位于系统「下载」目录内的「多线程下载」子目录 */
    const val DOWNLOAD_SUBDIR = "多线程下载"

    /** 默认线程数 */
    const val DEFAULT_THREADS = 4

    /** 最小/最大线程数 */
    const val MIN_THREADS = 1
    const val MAX_THREADS = 16

    /** 默认同时下载数 */
    const val DEFAULT_MAX_CONCURRENT = 3

    /** 默认重试次数 */
    const val DEFAULT_RETRY = 3

    /** 单线程缓冲区大小 */
    const val BUFFER_SIZE = 8 * 1024

    /** 进度刷新间隔(ms) */
    const val PROGRESS_INTERVAL_MS = 500L

    /** 通知 ID */
    const val NOTIF_ID_FOREGROUND = 1001
    const val NOTIF_ID_DONE_BASE = 2000
    const val NOTIF_ID_ERROR_BASE = 3000

    /** 通知渠道 */
    const val CHANNEL_DOWNLOAD = "channel_download"
    const val CHANNEL_DONE = "channel_done"
    const val CHANNEL_ERROR = "channel_error"

    /** 临时分块文件后缀 */
    const val SUFFIX_PART = ".part"
    const val SUFFIX_TMP = ".tmp"

    /** DataStore 名称 */
    const val SETTINGS_NAME = "mt_settings"
}
