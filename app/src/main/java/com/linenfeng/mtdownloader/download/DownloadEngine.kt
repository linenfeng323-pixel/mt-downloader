package com.linenfeng.mtdownloader.download

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.linenfeng.mtdownloader.Constants
import com.linenfeng.mtdownloader.data.DownloadRepository
import com.linenfeng.mtdownloader.data.DownloadStatus
import com.linenfeng.mtdownloader.data.ProgressInfo
import com.linenfeng.mtdownloader.data.SettingsRepository
import com.linenfeng.mtdownloader.data.db.DownloadEntity
import com.linenfeng.mtdownloader.service.DownloadNotifications
import com.linenfeng.mtdownloader.service.DownloadService
import com.linenfeng.mtdownloader.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载引擎：统一管理所有下载任务的生命周期、并发调度与进度聚合。
 *
 * - 单例，应用级生命周期
 * - 通过 [SettingsRepository] 读取并发数、重试次数等配置
 * - 限制同时活跃任务数（默认 3），超出任务进入等待队列
 * - 对外暴露 [progresses]（实时进度）与 [activeCount]（活跃数）
 *
 * 作者：林恩风
 */
class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settings: SettingsRepository
) {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)
    private val notificationManager = NotificationManagerCompat.from(context)

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // 0 = 无超时，大文件下载不会被中断
        .writeTimeout(0, TimeUnit.SECONDS)  // 0 = 无超时
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        // 配置连接池，支持多线程并发下载
        .connectionPool(okhttp3.ConnectionPool(
            50,  // 最大空闲连接数
            5,   // 空闲存活分钟数
            TimeUnit.MINUTES
        ))
        // 配置 Dispatcher 支持高并发
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 128          // 最大并发请求
            maxRequestsPerHost = 64    // 单主机最大并发
        })
        .build()

    private val runners = ConcurrentHashMap<Long, DownloadRunner>()
    private val waitingQueue = ConcurrentLinkedDeque<Long>()
    /** 当前占用并发槽位的任务 id 集合（已实际 start） */
    private val activeIds = ConcurrentHashMap.newKeySet<Long>()

    private val activeCountAtomic = AtomicInteger(0)
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    @Volatile
    private var maxConcurrent: Int = Constants.DEFAULT_MAX_CONCURRENT

    @Volatile
    private var maxRetry: Int = Constants.DEFAULT_RETRY

    @Volatile
    private var wifiOnly: Boolean = false

    /** 实时进度表（任务 id -> 进度） */
    private val _progresses = MutableStateFlow<Map<Long, ProgressInfo>>(emptyMap())
    val progresses: StateFlow<Map<Long, ProgressInfo>> = _progresses.asStateFlow()

    private val scheduleMutex = Mutex()

    /** 启动时加载配置 */
    fun init() {
        scope.launch {
            settings.settings.collect { s ->
                maxConcurrent = s.maxConcurrent
                maxRetry = s.retryCount
                wifiOnly = s.wifiOnly
            }
        }
    }

    /** 入队新任务（或重新启动已有任务） */
    suspend fun enqueue(entity: DownloadEntity) {
        if (runners.containsKey(entity.id)) {
            // 已存在 runner，直接 resume
            resume(entity.id)
            return
        }
        val runner = DownloadRunner(
            client = client,
            repository = repository,
            scope = scope,
            initial = entity,
            maxRetry = if (maxRetry > 0) maxRetry else 0,
            onState = { status -> updateState(entity.id, status) },
            onProgress = { info -> updateProgress(entity.id, info) },
            onFinished = { r -> scope.launch { handleFinished(r) } }
        )
        runners[entity.id] = runner
        updateProgress(entity.id, runner.progress.value)
        DownloadService.start(context)
        scheduleOrQueue(entity.id)
    }

    private suspend fun scheduleOrQueue(id: Long) {
        scheduleMutex.withLock {
            val runner = runners[id] ?: return@withLock
            if (runner.isRunning) return@withLock
            if (activeCountAtomic.get() < maxConcurrent && checkNetworkAllow()) {
                activeCountAtomic.incrementAndGet()
                activeIds.add(id)
                _activeCount.value = activeCountAtomic.get()
                runner.start()
            } else {
                if (id !in waitingQueue) waitingQueue.addLast(id)
                updateState(id, DownloadStatus.WAITING)
            }
        }
    }

    private fun checkNetworkAllow(): Boolean {
        if (!NetworkUtils.isOnline(context)) return false
        if (wifiOnly && !NetworkUtils.isWifi(context)) return false
        return true
    }

    /** 暂停 */
    suspend fun pause(id: Long) {
        val runner = runners[id] ?: return
        runner.pause()
        scheduleMutex.withLock {
            // 仅当任务确实占用槽位时才释放
            if (activeIds.remove(id)) {
                activeCountAtomic.decrementAndGet()
                _activeCount.value = activeCountAtomic.get()
            }
            waitingQueue.remove(id)
        }
        tryScheduleNext()
    }

    /** 继续（恢复） */
    suspend fun resume(id: Long) {
        // 如果 runner 还在且没运行，直接重新调度
        val existing = runners[id]
        if (existing != null && !existing.isRunning) {
            repository.updateStatus(id, DownloadStatus.WAITING)
            updateState(id, DownloadStatus.WAITING)
            scheduleOrQueue(id)
            return
        }
        if (existing != null && existing.isRunning) return
        // runner 已被清理（比如完成后或 app 重启后），从数据库恢复
        val entity = repository.getById(id) ?: return
        if (entity.statusEnum == DownloadStatus.COMPLETED) return
        repository.updateStatus(id, DownloadStatus.WAITING)
        enqueue(entity.copy(status = DownloadStatus.WAITING.value))
    }

    /** 取消（删除文件，标记已取消） */
    suspend fun cancel(id: Long) {
        val runner = runners[id]
        scheduleMutex.withLock { waitingQueue.remove(id) }
        runner?.cancel()
    }

    /** 重试失败/取消的任务 */
    suspend fun retry(id: Long) {
        val entity = repository.getById(id) ?: return
        if (entity.statusEnum != DownloadStatus.FAILED &&
            entity.statusEnum != DownloadStatus.CANCELED &&
            entity.statusEnum != DownloadStatus.PAUSED
        ) return
        repository.updateError(id, null, DownloadStatus.WAITING, 0)
        runners.remove(id)
        enqueue(entity.copy(status = DownloadStatus.WAITING.value, errorMsg = null, retries = 0))
    }

    /** 暂停全部 */
    suspend fun pauseAll() {
        val ids = runners.keys.toList()
        for (id in ids) pause(id)
        scheduleMutex.withLock {
            waitingQueue.clear()
            // waiting 状态的任务标记为 paused
        }
    }

    /** 全部开始（已完成的跳过） */
    suspend fun startAll() {
        val entities = repository.getByStatus(
            listOf(
                DownloadStatus.PAUSED.value,
                DownloadStatus.FAILED.value,
                DownloadStatus.WAITING.value,
                DownloadStatus.CANCELED.value
            )
        )
        for (e in entities) {
            if (e.statusEnum == DownloadStatus.COMPLETED) continue
            enqueue(e)
        }
    }

    /** 删除任务（可选删除文件） */
    suspend fun remove(id: Long, deleteFile: Boolean) {
        cancel(id)
        runners.remove(id)
        _progresses.value = _progresses.value - id
        val entity = repository.getById(id)
        if (entity != null) {
            if (deleteFile) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    runCatching { java.io.File(entity.filePath).delete() }
                }
            }
            repository.deleteById(id)
        }
    }

    fun observeProgress(id: Long): StateFlow<ProgressInfo>? =
        runners[id]?.progress

    private fun updateState(id: Long, status: DownloadStatus) {
        val cur = _progresses.value[id]
        _progresses.value = _progresses.value + (id to (cur?.copy(status = status) ?: ProgressInfo.idle(id, status)))
    }

    private fun updateProgress(id: Long, info: ProgressInfo) {
        _progresses.value = _progresses.value + (id to info)
    }

    private suspend fun handleFinished(runner: DownloadRunner) {
        val status = runner.progress.value.status
        scheduleMutex.withLock {
            // 仅当任务确实占用槽位时才释放
            if (activeIds.remove(runner.id)) {
                activeCountAtomic.decrementAndGet()
                _activeCount.value = activeCountAtomic.get()
            }
            if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELED) {
                runners.remove(runner.id)
            }
        }
        // 完成/失败通知
        if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED) {
            val entity = repository.getById(runner.id)
            if (entity != null && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                val notif = when (status) {
                    DownloadStatus.COMPLETED ->
                        DownloadNotifications.buildCompleted(context, entity.fileName)
                    DownloadStatus.FAILED ->
                        DownloadNotifications.buildError(context, entity.fileName, entity.errorMsg ?: "")
                    else -> null
                }
                notif?.let {
                    val notifId = if (status == DownloadStatus.COMPLETED)
                        Constants.NOTIF_ID_DONE_BASE + runner.id.toInt()
                    else Constants.NOTIF_ID_ERROR_BASE + runner.id.toInt()
                    runCatching { notificationManager.notify(notifId, it) }
                }
            }
        }
        // 无活跃任务时停止前台服务
        if (activeCountAtomic.get() == 0 && waitingQueue.isEmpty()) {
            DownloadService.stop(context)
        }
        tryScheduleNext()
    }

    private suspend fun tryScheduleNext() {
        scheduleMutex.withLock {
            while (activeCountAtomic.get() < maxConcurrent) {
                val id = waitingQueue.pollFirst() ?: break
                val runner = runners[id] ?: continue
                if (!checkNetworkAllow()) {
                    // 网络不允许，放回队首
                    waitingQueue.addFirst(id)
                    updateState(id, DownloadStatus.WAITING)
                    break
                }
                activeCountAtomic.incrementAndGet()
                activeIds.add(id)
                _activeCount.value = activeCountAtomic.get()
                runner.start()
            }
        }
    }

    /** 全局速度（bytes/s） */
    val totalSpeed: StateFlow<Long> = MutableStateFlow(0L).let { mut ->
        scope.launch {
            _progresses.collect { map ->
                mut.value = map.values
                    .filter { it.status == DownloadStatus.DOWNLOADING }
                    .sumOf { it.speed }
            }
        }
        mut.asStateFlow()
    }

    /** 全局已下载字节 */
    val totalDownloaded: StateFlow<Long> = MutableStateFlow(0L).let { mut ->
        scope.launch {
            _progresses.collect { map ->
                mut.value = map.values.sumOf { it.downloaded }
            }
        }
        mut.asStateFlow()
    }
}
