package com.linenfeng.mtdownloader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linenfeng.mtdownloader.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = Constants.SETTINGS_NAME)

/**
 * 应用设置（DataStore）
 */
class SettingsRepository(private val context: Context) {

    object Keys {
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val DEFAULT_THREADS = intPreferencesKey("default_threads")
        val RETRY_COUNT = intPreferencesKey("retry_count")
        val USE_PUBLIC_DIR = booleanPreferencesKey("use_public_dir")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val NOTIFY_SOUND = booleanPreferencesKey("notify_sound")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val THEME_MODE = stringPreferencesKey("theme_mode") // system/light/dark
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            maxConcurrent = p[Keys.MAX_CONCURRENT] ?: Constants.DEFAULT_MAX_CONCURRENT,
            defaultThreads = p[Keys.DEFAULT_THREADS] ?: Constants.DEFAULT_THREADS,
            retryCount = p[Keys.RETRY_COUNT] ?: Constants.DEFAULT_RETRY,
            usePublicDir = p[Keys.USE_PUBLIC_DIR] ?: false,
            wifiOnly = p[Keys.WIFI_ONLY] ?: false,
            notifySound = p[Keys.NOTIFY_SOUND] ?: true,
            autoRetry = p[Keys.AUTO_RETRY] ?: true,
            themeMode = p[Keys.THEME_MODE] ?: "system"
        )
    }

    suspend fun setMaxConcurrent(value: Int) =
        context.dataStore.edit { it[Keys.MAX_CONCURRENT] = value.coerceIn(1, 8) }

    suspend fun setDefaultThreads(value: Int) =
        context.dataStore.edit { it[Keys.DEFAULT_THREADS] = value.coerceIn(Constants.MIN_THREADS, Constants.MAX_THREADS) }

    suspend fun setRetryCount(value: Int) =
        context.dataStore.edit { it[Keys.RETRY_COUNT] = value.coerceIn(0, 10) }

    suspend fun setUsePublicDir(value: Boolean) =
        context.dataStore.edit { it[Keys.USE_PUBLIC_DIR] = value }

    suspend fun setWifiOnly(value: Boolean) =
        context.dataStore.edit { it[Keys.WIFI_ONLY] = value }

    suspend fun setNotifySound(value: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_SOUND] = value }

    suspend fun setAutoRetry(value: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_RETRY] = value }

    suspend fun setThemeMode(value: String) =
        context.dataStore.edit { it[Keys.THEME_MODE] = value }
}

data class AppSettings(
    val maxConcurrent: Int,
    val defaultThreads: Int,
    val retryCount: Int,
    val usePublicDir: Boolean,
    val wifiOnly: Boolean,
    val notifySound: Boolean,
    val autoRetry: Boolean,
    val themeMode: String
)
