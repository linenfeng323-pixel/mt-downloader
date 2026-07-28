package com.linenfeng.mtdownloader.data

import com.linenfeng.mtdownloader.data.db.DownloadDao
import com.linenfeng.mtdownloader.data.db.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * 下载任务仓库
 */
class DownloadRepository(private val dao: DownloadDao) {

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<DownloadEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun getByStatus(statuses: List<Int>): List<DownloadEntity> = dao.getByStatus(statuses)

    suspend fun insert(entity: DownloadEntity): Long = dao.insert(entity)

    suspend fun update(entity: DownloadEntity) = dao.update(entity)

    suspend fun delete(entity: DownloadEntity) = dao.delete(entity)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearCompleted() = dao.deleteByStatus(DownloadStatus.COMPLETED.value)

    suspend fun updateStatus(id: Long, status: DownloadStatus) =
        dao.updateStatus(id, status.value)

    suspend fun updateProgress(id: Long, downloaded: Long, total: Long, blocks: List<BlockInfo>) {
        dao.updateProgress(
            id = id,
            downloaded = downloaded,
            total = total,
            blocks = serializeBlocks(blocks)
        )
    }

    suspend fun updateError(id: Long, error: String?, status: DownloadStatus, retries: Int) =
        dao.updateError(id, error, status.value, retries)

    /** 序列化分块为字符串：index:start:end:downloaded;... */
    fun serializeBlocks(blocks: List<BlockInfo>): String =
        blocks.joinToString(";") { "${it.index}:${it.start}:${it.end}:${it.downloaded}" }

    /** 反序列化分块 */
    fun deserializeBlocks(text: String): List<BlockInfo> {
        if (text.isBlank()) return emptyList()
        return text.split(';').mapNotNull { seg ->
            val p = seg.split(':')
            if (p.size != 4) return@mapNotNull null
            try {
                BlockInfo(
                    index = p[0].toInt(),
                    start = p[1].toLong(),
                    end = p[2].toLong(),
                    downloaded = p[3].toLong()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
