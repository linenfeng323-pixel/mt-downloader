package com.linenfeng.mtdownloader.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linenfeng.mtdownloader.App
import com.linenfeng.mtdownloader.Constants
import com.linenfeng.mtdownloader.data.AppSettings
import com.linenfeng.mtdownloader.data.DownloadRepository
import com.linenfeng.mtdownloader.data.DownloadStatus
import com.linenfeng.mtdownloader.data.ProgressInfo
import com.linenfeng.mtdownloader.data.db.DownloadEntity
import com.linenfeng.mtdownloader.download.DownloadEngine
import com.linenfeng.mtdownloader.util.FileUtils
import com.linenfeng.mtdownloader.util.HeaderParser
import com.linenfeng.mtdownloader.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 主界面 ViewModel：合并数据库任务与引擎实时进度，对外提供统一的 UI 状态。
 *
 * 作者：林恩风
 */
class DownloadViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val appCtx: App get() = getApplication()
    private val repository: DownloadRepository get() = appCtx.repository
    val engine: DownloadEngine get() = appCtx.engine
    private val settingsRepo get() = appCtx.settings

    /** 数据库任务流 */
    private val entitiesFlow = repository.observeAll()

    /** 实时进度流 */
    val progresses: StateFlow<Map<Long, ProgressInfo>> = engine.progresses

    /** 合并后的任务列表（带实时进度） */
    val tasks: StateFlow<List<DownloadUiItem>> = combine(
        entitiesFlow,
        engine.progresses
    ) { list, progressMap ->
        list.map { entity ->
            val info = progressMap[entity.id]
            DownloadUiItem(
                entity = entity,
                progress = info ?: ProgressInfo.idle(
                    entity.id,
                    entity.statusEnum,
                    if (entity.totalSize > 0) entity.totalSize else 0,
                    entity.downloadedSize
                ).copy(error = entity.errorMsg)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        AppSettings(
            maxConcurrent = Constants.DEFAULT_MAX_CONCURRENT,
            defaultThreads = Constants.DEFAULT_THREADS,
            retryCount = Constants.DEFAULT_RETRY,
            usePublicDir = false,
            wifiOnly = false,
            notifySound = true,
            autoRetry = true,
            themeMode = "system"
        )
    )

    val totalSpeed: StateFlow<Long> = engine.totalSpeed
    val activeCount: StateFlow<Int> = engine.activeCount

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun consumeToast() { _toast.value = null }

    /** 新建下载任务 */
    fun addTask(url: String, fileName: String, threads: Int, headers: String) {
        val ctx = getApplication<App>()
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            _toast.value = ctx.getString(com.linenfeng.mtdownloader.R.string.error_invalid_url)
            return
        }
        if (!NetworkUtils.isOnline(ctx)) {
            _toast.value = ctx.getString(com.linenfeng.mtdownloader.R.string.error_no_network)
            return
        }
        viewModelScope.launch {
            val s = settings.value
            val dir = if (s.usePublicDir) FileUtils.publicDownloadDir() else FileUtils.appDownloadDir(ctx)
            val name = fileName.trim().ifBlank { FileUtils.nameFromUrl(trimmedUrl).ifBlank { "download_${System.currentTimeMillis()}" } }
            val safeName = FileUtils.sanitize(name)
            val target = FileUtils.resolveTargetFile(dir, safeName)
            val finalThreads = threads.coerceIn(Constants.MIN_THREADS, Constants.MAX_THREADS)
            val entity = DownloadEntity(
                url = trimmedUrl,
                fileName = target.name,
                saveDirPath = dir.absolutePath,
                filePath = target.absolutePath,
                threads = finalThreads,
                status = DownloadStatus.WAITING.value,
                headersText = HeaderParser.parse(headers).let { HeaderParser.format(it) }
            )
            val id = repository.insert(entity)
            engine.enqueue(entity.copy(id = id))
            _toast.value = ctx.getString(com.linenfeng.mtdownloader.R.string.download_added)
        }
    }

    fun start(id: Long) = viewModelScope.launch {
        val entity = repository.getById(id) ?: return@launch
        engine.enqueue(entity)
    }

    fun pause(id: Long) = viewModelScope.launch { engine.pause(id) }

    fun resume(id: Long) = viewModelScope.launch { engine.resume(id) }

    fun cancel(id: Long) = viewModelScope.launch { engine.cancel(id) }

    fun retry(id: Long) = viewModelScope.launch { engine.retry(id) }

    fun pauseAll() = viewModelScope.launch { engine.pauseAll() }

    fun startAll() = viewModelScope.launch { engine.startAll() }

    fun clearCompleted() = viewModelScope.launch { repository.clearCompleted() }

    /** 删除任务 */
    fun delete(id: Long, deleteFile: Boolean) = viewModelScope.launch {
        engine.remove(id, deleteFile)
    }

    /** 打开已下载文件（通过系统 Intent + FileProvider） */
    fun openFile(id: Long) {
        val ctx = getApplication<App>()
        viewModelScope.launch {
            val entity = repository.getById(id) ?: return@launch
            val file = File(entity.filePath)
            if (!file.exists()) {
                _toast.value = "文件不存在"
                return@launch
            }
            runCatching {
                val authority = "${ctx.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, authority, file)
                val mime = ctx.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(Intent.createChooser(intent, "打开方式").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                _toast.value = "无法打开此文件"
            }
        }
    }

    // ============ 设置 ============
    fun setMaxConcurrent(v: Int) = viewModelScope.launch { settingsRepo.setMaxConcurrent(v) }
    fun setDefaultThreads(v: Int) = viewModelScope.launch { settingsRepo.setDefaultThreads(v) }
    fun setRetryCount(v: Int) = viewModelScope.launch { settingsRepo.setRetryCount(v) }
    fun setUsePublicDir(v: Boolean) = viewModelScope.launch { settingsRepo.setUsePublicDir(v) }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { settingsRepo.setWifiOnly(v) }
    fun setNotifySound(v: Boolean) = viewModelScope.launch { settingsRepo.setNotifySound(v) }
    fun setAutoRetry(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoRetry(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settingsRepo.setThemeMode(v) }
}

/**
 * UI 层任务展示模型
 */
data class DownloadUiItem(
    val entity: DownloadEntity,
    val progress: ProgressInfo
) {
    val id: Long get() = entity.id
    val status: DownloadStatus get() = progress.status
    val fileName: String get() = entity.fileName
    val downloaded: Long get() = progress.downloaded
    val total: Long get() = progress.total
    val speed: Long get() = progress.speed
    val percent: Int get() = progress.percent
    val remainingMs: Long get() = progress.remainingMs
    val error: String? get() = progress.error
    val threads: Int get() = progress.threads
}
