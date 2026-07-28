package com.linenfeng.mtdownloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linenfeng.mtdownloader.data.DownloadStatus
import com.linenfeng.mtdownloader.ui.theme.BrandGradientEnd
import com.linenfeng.mtdownloader.ui.theme.BrandGradientStart
import com.linenfeng.mtdownloader.ui.theme.StatusCanceled
import com.linenfeng.mtdownloader.ui.theme.StatusCompleted
import com.linenfeng.mtdownloader.ui.theme.StatusDownloading
import com.linenfeng.mtdownloader.ui.theme.StatusFailed
import com.linenfeng.mtdownloader.ui.theme.StatusPaused
import com.linenfeng.mtdownloader.ui.theme.StatusWaiting
import com.linenfeng.mtdownloader.util.FileCategory
import com.linenfeng.mtdownloader.util.FileUtils

/** 状态徽章 */
@Composable
fun StatusBadge(status: DownloadStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        DownloadStatus.WAITING -> "等待中" to StatusWaiting
        DownloadStatus.DOWNLOADING -> "下载中" to StatusDownloading
        DownloadStatus.PAUSED -> "已暂停" to StatusPaused
        DownloadStatus.COMPLETED -> "已完成" to StatusCompleted
        DownloadStatus.FAILED -> "失败" to StatusFailed
        DownloadStatus.CANCELED -> "已取消" to StatusCanceled
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 渐变头部背景 */
@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))
        )
    ) {
        content()
    }
}

/** 文件类型图标 */
@Composable
fun FileTypeIcon(fileName: String, modifier: Modifier = Modifier, size: Int = 48) {
    val (icon, color) = fileTypeVisual(FileUtils.categoryOf(fileName))
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size((size * 0.55).dp)
            )
        }
    }
}

private fun fileTypeVisual(category: FileCategory): Pair<ImageVector, Color> = when (category) {
    FileCategory.APK -> Icons.Filled.Android to Color(0xFF22C55E)
    FileCategory.ARCHIVE -> Icons.Filled.Archive to Color(0xFFF5A623)
    FileCategory.VIDEO -> Icons.Filled.Movie to Color(0xFF8B5CF6)
    FileCategory.AUDIO -> Icons.Filled.AudioFile to Color(0xFFEC4899)
    FileCategory.IMAGE -> Icons.Filled.Image to Color(0xFF06B6D4)
    FileCategory.DOC -> Icons.Filled.Description to Color(0xFF3B6FF5)
    FileCategory.OTHER -> Icons.Filled.InsertDriveFile to Color(0xFF6B7280)
}

/** 进度条 */
@Composable
fun DownloadProgress(
    percent: Int,
    status: DownloadStatus,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false
) {
    val color = when (status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.WAITING -> StatusWaiting
        DownloadStatus.PAUSED -> StatusPaused
        DownloadStatus.COMPLETED -> StatusCompleted
        DownloadStatus.FAILED -> StatusFailed
        DownloadStatus.CANCELED -> StatusCanceled
    }
    if (indeterminate) {
        LinearProgressIndicator(
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    } else {
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

/** 空状态 */
@Composable
fun EmptyState(modifier: Modifier = Modifier, message: String) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** 统计信息小卡片 */
@Composable
fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 淡入淡出可见性 */
@Composable
fun FadeVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        content()
    }
}
