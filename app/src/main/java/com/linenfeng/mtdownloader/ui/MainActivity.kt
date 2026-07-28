package com.linenfeng.mtdownloader.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.linenfeng.mtdownloader.ui.screens.AboutScreen
import com.linenfeng.mtdownloader.ui.screens.AddTaskScreen
import com.linenfeng.mtdownloader.ui.screens.DownloadListScreen
import com.linenfeng.mtdownloader.ui.screens.SettingsScreen
import com.linenfeng.mtdownloader.ui.theme.MtDownloaderTheme
import com.linenfeng.mtdownloader.ui.viewmodel.DownloadViewModel

/**
 * 主 Activity：承载 Compose 导航与主题切换。
 *
 * 作者：林恩风
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val initialUrl = extractUrlFromIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MtDownloaderTheme(darkTheme = darkTheme) {
                AppNavigation(viewModel = viewModel, initialUrl = initialUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 通过静态变量传递新 URL（简化处理）
        pendingShareUrl = extractUrlFromIntent(intent)
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val trimmed = text.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else null
    }

    companion object {
        @Volatile
        var pendingShareUrl: String? = null
    }
}

private enum class Screen { LIST, ADD, SETTINGS, ABOUT }

@Composable
private fun AppNavigation(viewModel: DownloadViewModel, initialUrl: String?) {
    // 使用 String 持久化当前屏幕，避免 sealed class 不可序列化导致的崩溃
    var currentName by rememberSaveable { mutableStateOf(Screen.LIST.name) }
    val current = runCatching { Screen.valueOf(currentName) }.getOrDefault(Screen.LIST)
    var addInitialUrl by remember { mutableStateOf(initialUrl) }

    // 监听分享进来的新 URL
    val pending = MainActivity.pendingShareUrl
    if (pending != null) {
        addInitialUrl = pending
        currentName = Screen.ADD.name
        MainActivity.pendingShareUrl = null
    }

    androidx.compose.animation.Crossfade(
        targetState = current,
        label = "screen"
    ) { screen ->
        when (screen) {
            Screen.LIST -> DownloadListScreen(
                viewModel = viewModel,
                onAddClick = {
                    addInitialUrl = null
                    currentName = Screen.ADD.name
                },
                onOpenAbout = { currentName = Screen.ABOUT.name },
                onOpenSettings = { currentName = Screen.SETTINGS.name }
            )
            Screen.ADD -> AddTaskScreen(
                viewModel = viewModel,
                initialUrl = addInitialUrl,
                onBack = { currentName = Screen.LIST.name },
                onAdded = { currentName = Screen.LIST.name }
            )
            Screen.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                onBack = { currentName = Screen.LIST.name }
            )
            Screen.ABOUT -> AboutScreen(
                onBack = { currentName = Screen.LIST.name }
            )
        }
    }
}
