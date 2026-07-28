package com.linenfeng.mtdownloader.download

import com.linenfeng.mtdownloader.data.BlockInfo
import com.linenfeng.mtdownloader.data.DownloadRepository
import com.linenfeng.mtdownloader.data.DownloadStatus
import com.linenfeng.mtdownloader.data.ProgressInfo
import com.linenfeng.mtdownloader.data.db.DownloadEntity
import com.linenfeng.mtdownloader.util.HeaderParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** 静默关闭 */
private fun Closeable?.closeQuietly() = runCatching { this?.close() }

/**
 * 单个下载任务的执行器。
 *
 * 核心流程：
 * 1. 解析文件信息（HEAD/GET 获取 Content-Length 与 Accept-Ranges）
 * 2. 按线程数切分分块
 * 3. 每个分块一个协程，通过 Range 请求并发下载并写入 [RandomAccessFile]
 * 4. 定时聚合进度，持久化断点
 * 5. 支持：暂停（取消协程并保存进度）、取消（删除临时文件）、重试
 *
 * 作者：林恩风
 */
class DownloadRunner(
    private val client: OkHttpClient,
    private val repository: DownloadRepository,
    private val scope: CoroutineScope,
    private val initial: DownloadEntity,
    private val maxRetry: Int,
    private val onState: (DownloadStatus) -> Unit,
    private val onProgress: (ProgressInfo) -> Unit,
    private val onFinished: (DownloadRunner) -> Unit
) {
    @Volatile
    private var entity: DownloadEntity = initial

    @Volatile
    private var job: Job? = null

    @Volatile
    private var paused = false

    @Volatile
    private var canceled = false

    private val blockProgress = ConcurrentHashMap<Int, AtomicLong>()

    private var totalSize: Long = initial.totalSize
    private var supportRange: Boolean = initial.supportRange
    private var blocks: List<BlockInfo> = emptyList()

    val id: Long get() = entity.id
    val isRunning: Boolean get() = job?.isActive == true

    /** 进度快照 */
    private val _progress = MutableStateFlow(
        ProgressInfo.idle(id, entity.statusEnum, totalSize, entity.downloadedSize)
    )
    val progress: StateFlow<ProgressInfo> = _progress.asStateFlow()

    /** 启动下载（若已在运行则忽略） */
    fun start() {
        if (isRunning) return
        paused = false
        canceled = false
        job = scope.launch { runDownload() }
    }

    /** 暂停：取消协程并持久化断点 */
    suspend fun pause() {
        paused = true
        job?.cancelAndJoinSafely()
        syncBlockProgress()
        persistProgress(DownloadStatus.PAUSED)
        onState(DownloadStatus.PAUSED)
    }

    /** 取消：删除文件并标记已取消 */
    suspend fun cancel() {
        canceled = true
        job?.cancelAndJoinSafely()
        cleanupFiles()
        repository.updateError(id, null, DownloadStatus.CANCELED, entity.retries)
        entity = entity.copy(status = DownloadStatus.CANCELED.value, downloadedSize = 0)
        _progress.value = ProgressInfo.idle(id, DownloadStatus.CANCELED, 0, 0)
        onState(DownloadStatus.CANCELED)
        onFinished(this)
    }

    private suspend fun runDownload() {
        try {
            onState(DownloadStatus.WAITING)
            // 1. 解析文件信息
            if (!resolveFileInfo()) {
                fail("无法获取文件信息（HTTP 错误或无网络）")
                return
            }
            // 2. 准备分块
            prepareBlocks()
            // 3. 写入文件预分配空间
            prepareFile()
            // 4. 持久化初始信息
            entity = entity.copy(
                totalSize = totalSize,
                supportRange = supportRange,
                saveDirPath = File(entity.filePath).parent ?: entity.saveDirPath
            )
            repository.update(entity)
            onState(DownloadStatus.DOWNLOADING)

            // 5. 启动进度监控
            val monitorJob = scope.launch { monitorProgress() }

            // 6. 启动分块下载
            if (supportRange && blocks.size > 1) {
                runMultiBlock()
            } else {
                runSingleBlock()
            }

            monitorJob.cancel()

            if (canceled || paused) return

            // 7. 校验并完成
            finalizeDownload()
        } catch (e: CancellationException) {
            // 暂停或取消引发的
            syncBlockProgress()
            if (canceled) {
                cleanupFiles()
                repository.updateError(id, null, DownloadStatus.CANCELED, entity.retries)
                entity = entity.copy(status = DownloadStatus.CANCELED.value, downloadedSize = 0)
                _progress.value = ProgressInfo.idle(id, DownloadStatus.CANCELED, 0, 0)
                onState(DownloadStatus.CANCELED)
                onFinished(this)
            } else if (paused) {
                persistProgress(DownloadStatus.PAUSED)
                onState(DownloadStatus.PAUSED)
            } else {
                persistProgress(entity.statusEnum)
            }
            throw e
        } catch (e: Throwable) {
            syncBlockProgress()
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    /** HEAD/GET 解析文件信息 */
    private suspend fun resolveFileInfo(): Boolean = withContext(Dispatchers.IO) {
        val headers = HeaderParser.parse(entity.headersText)
        val builder = Request.Builder().url(entity.url).head()
        headers.forEach { (k, v) -> builder.header(k, v) }
        builder.header("User-Agent", DEFAULT_UA)

        var resp: Response? = null
        try {
            resp = client.newCall(builder.build()).execute()
            if (!resp.isSuccessful) {
                // 某些服务器不支持 HEAD，回退到 GET
                resp.closeQuietly()
                val getBuilder = Request.Builder().url(entity.url).get()
                headers.forEach { (k, v) -> getBuilder.header(k, v) }
                getBuilder.header("User-Agent", DEFAULT_UA)
                getBuilder.header("Range", "bytes=0-0")
                resp = client.newCall(getBuilder.build()).execute()
                if (!resp.isSuccessful) return@withContext false
            }
            totalSize = parseContentLength(resp)
            supportRange = parseAcceptRanges(resp) || totalSize <= 0
            if (totalSize <= 0) {
                // 无法获取大小，按单线程不支持 Range 处理
                supportRange = false
                totalSize = -1
            }
            true
        } catch (e: Throwable) {
            false
        } finally {
            resp?.closeQuietly()
        }
    }

    private fun prepareBlocks() {
        if (!supportRange || totalSize <= 0) {
            // 不支持断点续传或未知大小：单线程从头下载
            val end = if (totalSize > 0) totalSize - 1 else Long.MAX_VALUE
            blocks = listOf(BlockInfo(0, 0, end, 0L))
            blockProgress[0] = AtomicLong(0L)
            return
        }
        // 从持久化数据恢复断点
        val saved = repository.deserializeBlocks(entity.blocksText)
        val threads = entity.threads.coerceIn(1, 64)
        val per = totalSize / threads
        blocks = (0 until threads).map { i ->
            val start = i * per
            val end = if (i == threads - 1) totalSize - 1 else (start + per - 1)
            val savedBlock = saved.firstOrNull { it.index == i }
            BlockInfo(i, start, end, savedBlock?.downloaded?.coerceIn(0, end - start + 1) ?: 0L)
        }
        blocks.forEach { b ->
            blockProgress[b.index] = AtomicLong(b.downloaded)
        }
    }

    private fun prepareFile() = runCatching {
        val file = File(entity.filePath)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        if (totalSize > 0 && file.length() != totalSize) {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(totalSize)
            }
        }
    }.getOrElse {
        throw IllegalStateException("无法创建下载文件: ${entity.filePath}")
    }

    private suspend fun runMultiBlock() {
        val jobs = blocks.map { block ->
            scope.launch { downloadBlock(block) }
        }
        jobs.forEach { it.join() }
    }

    private suspend fun runSingleBlock() {
        val block = blocks.first()
        downloadBlock(block)
    }

    /** 下载单个分块，带失败重试 */
    private suspend fun downloadBlock(block: BlockInfo) {
        var attempt = 0
        while (true) {
            try {
                coroutineContext[Job]?.ensureActive()
                downloadBlockOnce(block)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                attempt++
                if (attempt > maxRetry) {
                    throw IllegalStateException("分块 ${block.index} 下载失败: ${e.message}")
                }
                delay(1000L * attempt)
            }
        }
    }

    private suspend fun downloadBlockOnce(block: BlockInfo) = withContext(Dispatchers.IO) {
        // 仅在支持断点续传且已知大小时才检查分块是否已完成
        if (supportRange && totalSize > 0 && block.isDone) return@withContext

        val downloaded = if (supportRange && totalSize > 0)
            (blockProgress[block.index]?.get() ?: 0L) else 0L
        val start = block.start + downloaded
        if (totalSize > 0 && start > block.end) return@withContext

        val headers = HeaderParser.parse(entity.headersText)
        val builder = Request.Builder().url(entity.url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        builder.header("User-Agent", DEFAULT_UA)
        if (supportRange && totalSize > 0) {
            builder.header("Range", "bytes=$start-${block.end}")
        }

        val file = File(entity.filePath)
        val raf = RandomAccessFile(file, "rw")
        raf.seek(start)

        var resp: Response? = null
        var input: java.io.InputStream? = null
        try {
            resp = client.newCall(builder.build()).execute()
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            input = resp.body?.byteStream()
                ?: throw IllegalStateException("响应体为空")
            // 使用大缓冲区提升下载速度
            val buffer = ByteArray(BUFFER_SIZE)
            var lastFlush = System.currentTimeMillis()
            var bytesReadSinceFlush = 0L
            while (true) {
                coroutineContext[Job]?.ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                raf.write(buffer, 0, read)
                val progress = blockProgress[block.index]
                progress?.addAndGet(read.toLong())

                // 每 8MB 刷一次盘，避免频繁 fsync 拖慢速度
                bytesReadSinceFlush += read
                val now = System.currentTimeMillis()
                if (bytesReadSinceFlush >= FLUSH_THRESHOLD_BYTES || now - lastFlush > 5000) {
                    raf.fd.sync()
                    lastFlush = now
                    bytesReadSinceFlush = 0
                }
            }
            raf.fd.sync()
        } finally {
            input?.closeQuietly()
            resp?.closeQuietly()
            raf.closeQuietly()
        }
    }

    /** 定时计算速度并更新进度 */
    private suspend fun monitorProgress() {
        var lastTime = System.currentTimeMillis()
        var lastDownloaded = currentDownloaded()
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            coroutineContext[Job]?.ensureActive()
            val now = System.currentTimeMillis()
            val cur = currentDownloaded()
            val dt = (now - lastTime).coerceAtLeast(1)
            val speed = ((cur - lastDownloaded) * 1000 / dt).coerceAtLeast(0)
            val remaining = if (speed > 0 && totalSize > 0) {
                ((totalSize - cur) * 1000 / speed)
            } else 0L
            // 同步 block.downloaded 用于 UI 显示线程数
            syncBlockProgress()
            val info = ProgressInfo(
                taskId = id,
                status = DownloadStatus.DOWNLOADING,
                downloaded = cur,
                total = if (totalSize > 0) totalSize else 0L,
                speed = speed,
                remainingMs = remaining,
                threads = blocks.count { !it.isDone }
            )
            _progress.value = info
            onProgress(info)
            // 持久化断点（节流）
            repository.updateProgress(id, cur, totalSize, blocks)
            lastTime = now
            lastDownloaded = cur
        }
    }

    /** 将 AtomicLong 的实时进度同步回 BlockInfo.downloaded，用于 UI/持久化 */
    private fun syncBlockProgress() {
        blocks.forEach { b ->
            val atomic = blockProgress[b.index] ?: return@forEach
            b.downloaded = atomic.get().coerceAtLeast(0L)
        }
    }

    private fun currentDownloaded(): Long =
        if (blocks.isEmpty()) entity.downloadedSize
        else blockProgress.values.sumOf { it.get() }

    private suspend fun finalizeDownload() {
        val cur = currentDownloaded()
        val finalTotal = if (totalSize > 0) totalSize else cur
        if (totalSize > 0 && cur < totalSize && !canceled) {
            fail("下载不完整 ($cur / $totalSize)")
            return
        }
        entity = entity.copy(
            status = DownloadStatus.COMPLETED.value,
            downloadedSize = finalTotal,
            totalSize = finalTotal,
            blocksText = "",
            errorMsg = null,
            updatedAt = System.currentTimeMillis()
        )
        repository.update(entity)
        _progress.value = ProgressInfo(
            taskId = id,
            status = DownloadStatus.COMPLETED,
            downloaded = finalTotal,
            total = finalTotal,
            speed = 0,
            remainingMs = 0,
            threads = 0
        )
        onState(DownloadStatus.COMPLETED)
        onFinished(this)
    }

    private suspend fun fail(msg: String) {
        entity = entity.copy(
            status = DownloadStatus.FAILED.value,
            errorMsg = msg,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateError(id, msg, DownloadStatus.FAILED, entity.retries)
        _progress.value = _progress.value.copy(
            status = DownloadStatus.FAILED,
            error = msg,
            speed = 0,
            remainingMs = 0,
            threads = 0
        )
        onState(DownloadStatus.FAILED)
        onFinished(this)
    }

    private suspend fun persistProgress(status: DownloadStatus) {
        val cur = currentDownloaded()
        repository.updateProgress(id, cur, totalSize, blocks)
        repository.updateStatus(id, status)
        entity = entity.copy(
            status = status.value,
            downloadedSize = cur,
            totalSize = totalSize,
            blocksText = repository.serializeBlocks(blocks),
            updatedAt = System.currentTimeMillis()
        )
        _progress.value = _progress.value.copy(status = status, speed = 0, remainingMs = 0)
    }

    private fun cleanupFiles() {
        runCatching {
            val file = File(entity.filePath)
            if (file.exists()) file.delete()
        }
    }

    private suspend fun Job.cancelAndJoinSafely() {
        try {
            cancel()
            join()
        } catch (_: Throwable) {
        }
    }

    private fun parseContentLength(resp: Response): Long {
        val contentRange = resp.header("Content-Range")
        if (!contentRange.isNullOrBlank()) {
            val slash = contentRange.indexOf('/')
            if (slash >= 0) {
                return contentRange.substring(slash + 1).trim().toLongOrNull() ?: -1L
            }
        }
        return resp.header("Content-Length")?.toLongOrNull() ?: -1L
    }

    private fun parseAcceptRanges(resp: Response): Boolean {
        return resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
    }

    companion object {
        const val DEFAULT_UA = "MtDownloader/1.0 (Android; linenfeng)"
        // 64KB 缓冲区，大幅提升下载吞吐量
        const val BUFFER_SIZE = 64 * 1024
        // 每 8MB 才刷一次盘，避免 fsync 拖慢速度
        const val FLUSH_THRESHOLD_BYTES = 8L * 1024 * 1024
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
