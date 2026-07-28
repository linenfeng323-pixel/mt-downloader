package com.linenfeng.mtdownloader

import android.app.Application
import com.linenfeng.mtdownloader.data.DownloadRepository
import com.linenfeng.mtdownloader.data.SettingsRepository
import com.linenfeng.mtdownloader.data.db.DownloadDatabase
import com.linenfeng.mtdownloader.download.DownloadEngine
import com.linenfeng.mtdownloader.util.NotificationUtils

/**
 * Application 入口，提供全局单例依赖。
 *
 * 作者：林恩风
 */
class App : Application() {

    val database: DownloadDatabase by lazy { DownloadDatabase.get(this) }
    val repository: DownloadRepository by lazy { DownloadRepository(database.downloadDao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val engine: DownloadEngine by lazy {
        DownloadEngine(this, repository, settings).also { it.init() }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationUtils.createChannels(this)
        // engine 在首次访问时通过 lazy 初始化并自动调用 init()
        engine
    }

    companion object {
        @Volatile
        lateinit var instance: App
            private set
    }
}
