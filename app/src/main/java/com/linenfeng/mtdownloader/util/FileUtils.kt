package com.linenfeng.mtdownloader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.linenfeng.mtdownloader.Constants
import java.io.File
import java.security.MessageDigest

/**
 * 文件与目录工具
 *
 * 下载目录：系统「下载」目录内的「多线程下载」子目录。
 * 默认使用应用专属外部下载目录（无需权限）；
 * 用户在设置开启「公共下载目录」时，使用公共 Downloads/多线程下载。
 */
object FileUtils {

    /** 应用专属下载目录：Android/data/<pkg>/files/Download/多线程下载 */
    fun appDownloadDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
        return File(base, Constants.DOWNLOAD_SUBDIR).apply { ensureDir() }
    }

    /** 公共下载目录：Download/多线程下载（需 MANAGE_EXTERNAL_STORAGE 权限） */
    fun publicDownloadDir(): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(base, Constants.DOWNLOAD_SUBDIR).apply { ensureDir() }
    }

    private fun File.ensureDir(): File {
        if (!exists() && !mkdirs()) {
            // 极少数情况，mkdirs 返回 false 但目录已存在
            if (!isDirectory) throw IllegalStateException("无法创建目录: $absolutePath")
        }
        return this
    }

    /** 在目录中生成可用文件名（自动避免重名覆盖） */
    fun resolveTargetFile(dir: File, fileName: String): File {
        val safe = sanitize(fileName.ifBlank { "download.bin" })
        var candidate = File(dir, safe)
        if (!candidate.exists()) return candidate
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base($i)$ext")
            i++
        }
        return candidate
    }

    /** 清理文件名中的非法字符 */
    fun sanitize(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "download.bin" }
    }

    /** 从 URL 中提取文件名 */
    fun nameFromUrl(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val seg = noQuery.substringAfterLast('/', "").trim()
        if (seg.isNotEmpty() && !seg.contains('/')) {
            return Uri.decode(seg)
        }
        return ""
    }

    /** 根据扩展名返回文件类型图标分类 */
    fun categoryOf(fileName: String): FileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk" -> FileCategory.APK
            "zip", "rar", "7z", "tar", "gz", "bz2" -> FileCategory.ARCHIVE
            "mp4", "mkv", "avi", "mov", "flv", "wmv" -> FileCategory.VIDEO
            "mp3", "flac", "aac", "wav", "ogg", "m4a" -> FileCategory.AUDIO
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> FileCategory.IMAGE
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub" -> FileCategory.DOC
            else -> FileCategory.OTHER
        }
    }

    /** MD5 简易哈希（用于生成任务 ID） */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 递归删除目录/文件 */
    fun deleteQuietly(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteQuietly(it) }
        }
        return file.delete()
    }

    /** 目录可用空间 */
    fun availableBytes(dir: File): Long {
        return dir.usableSpace
    }
}

enum class FileCategory(val label: String) {
    APK("应用"),
    ARCHIVE("压缩包"),
    VIDEO("视频"),
    AUDIO("音频"),
    IMAGE("图片"),
    DOC("文档"),
    OTHER("文件")
}
