package com.linenfeng.mtdownloader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.linenfeng.mtdownloader.data.DownloadStatus

/**
 * 下载任务实体
 *
 * @param id 主键（自增）
 * @param url 下载链接
 * @param fileName 文件名
 * @param saveDirPath 保存目录绝对路径
 * @param filePath 最终文件绝对路径
 * @param totalSize 总大小（字节），未知为 -1
 * @param downloadedSize 已下载字节
 * @param threads 线程数
 * @param status 状态
 * @param headersText 自定义请求头（每行一条 "Key: Value"）
 * @param supportRange 是否支持断点续传（Range 请求）
 * @param blocksText 分块已下载进度（持久化用于断点续传），格式 "index:start:end:downloaded;..."
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param errorMsg 错误信息
 * @param retries 已重试次数
 */
@Entity(tableName = "download_task")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "save_dir_path")
    val saveDirPath: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "total_size")
    val totalSize: Long = -1L,

    @ColumnInfo(name = "downloaded_size")
    val downloadedSize: Long = 0L,

    @ColumnInfo(name = "threads")
    val threads: Int = 1,

    @ColumnInfo(name = "status")
    val status: Int = DownloadStatus.WAITING.value,

    @ColumnInfo(name = "headers_text")
    val headersText: String = "",

    @ColumnInfo(name = "support_range")
    val supportRange: Boolean = false,

    @ColumnInfo(name = "blocks_text")
    val blocksText: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "error_msg")
    val errorMsg: String? = null,

    @ColumnInfo(name = "retries")
    val retries: Int = 0
) {
    val statusEnum: DownloadStatus get() = DownloadStatus.fromValue(status)
}
