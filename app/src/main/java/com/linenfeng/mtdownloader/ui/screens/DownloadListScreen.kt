package com.linenfeng.mtdownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linenfeng.mtdownloader.R
import com.linenfeng.mtdownloader.data.DownloadStatus
import com.linenfeng.mtdownloader.ui.components.DownloadProgress
import com.linenfeng.mtdownloader.ui.components.EmptyState
import com.linenfeng.mtdownloader.ui.components.FileTypeIcon
import com.linenfeng.mtdownloader.ui.components.StatChip
import com.linenfeng.mtdownloader.ui.components.StatusBadge
import com.linenfeng.mtdownloader.ui.theme.BrandGradientEnd
import com.linenfeng.mtdownloader.ui.theme.BrandGradientStart
import com.linenfeng.mtdownloader.ui.viewmodel.DownloadUiItem
import com.linenfeng.mtdownloader.ui.viewmodel.DownloadViewModel
import com.linenfeng.mtdownloader.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    viewModel: DownloadViewModel,
    onAddClick: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val totalSpeed by viewModel.totalSpeed.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()

    var filter by remember { mutableStateOf(Filter.ALL) }
    var deleteTarget by remember { mutableStateOf<DownloadUiItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val toast by viewModel.toast.collectAsState()

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    val filtered = when (filter) {
        Filter.ALL -> tasks
        Filter.ACTIVE -> tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.WAITING }
        Filter.PAUSED -> tasks.filter { it.status == DownloadStatus.PAUSED }
        Filter.DONE -> tasks.filter { it.status == DownloadStatus.COMPLETED }
        Filter.FAILED -> tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 渐变头部
            Header(
                activeCount = activeCount,
                totalSpeed = totalSpeed,
                totalTasks = tasks.size,
                onMenu = { menuOpen = true },
                onOpenAbout = onOpenAbout
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_pause_all)) },
                    onClick = { viewModel.pauseAll(); menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.Pause, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_start_all)) },
                    onClick = { viewModel.startAll(); menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_clear_done)) },
                    onClick = { viewModel.clearCompleted(); menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_settings)) },
                    onClick = { onOpenSettings(); menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.MoreVert, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_about)) },
                    onClick = { onOpenAbout(); menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.Bolt, null) }
                )
            }

            // 过滤标签
            FilterRow(current = filter, onSelect = { filter = it })

            if (filtered.isEmpty()) {
                EmptyState(message = stringResource(R.string.empty_downloads))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            onStart = { viewModel.start(item.id) },
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onRetry = { viewModel.retry(item.id) },
                            onCancel = { viewModel.cancel(item.id) },
                            onOpen = { viewModel.openFile(item.id) },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(target.fileName) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id, deleteFile = false)
                    deleteTarget = null
                }) { Text(stringResource(R.string.dialog_delete_task)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    TextButton(onClick = {
                        viewModel.delete(target.id, deleteFile = true)
                        deleteTarget = null
                    }) { Text(stringResource(R.string.dialog_delete_all)) }
                }
            }
        )
    }

    // Toast 已通过 SnackbarHost 展示
}

@Composable
private fun Header(
    activeCount: Int,
    totalSpeed: Long,
    totalTasks: Int,
    onMenu: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
            .padding(start = 20.dp, end = 12.dp, top = 48.dp, bottom = 20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "作者：${stringResource(R.string.app_author)} · ${stringResource(R.string.app_slogan)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                IconButton(onClick = onOpenAbout) {
                    Icon(Icons.Filled.Bolt, contentDescription = "关于", tint = Color.White)
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单", tint = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(
                    label = "活跃任务",
                    value = activeCount.toString(),
                    icon = Icons.Filled.List,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "总速度",
                    value = FormatUtils.speed(totalSpeed),
                    icon = Icons.Filled.Bolt,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "任务总数",
                    value = totalTasks.toString(),
                    icon = Icons.Filled.Memory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private enum class Filter(val label: String) {
    ALL("全部"), ACTIVE("进行中"), PAUSED("已暂停"), DONE("已完成"), FAILED("失败/取消")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(current: Filter, onSelect: (Filter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Filter.entries.forEach { f ->
            val selected = f == current
            Surface(
                onClick = { onSelect(f) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = f.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadUiItem,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(fileName = item.fileName, size = 44)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(item.status)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (item.total > 0)
                                FormatUtils.size(item.downloaded) + " / " + FormatUtils.size(item.total)
                            else if (item.status == DownloadStatus.COMPLETED)
                                FormatUtils.size(item.downloaded)
                            else "未知大小",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            DownloadProgress(
                percent = item.percent,
                status = item.status,
                indeterminate = item.total <= 0 && item.status == DownloadStatus.DOWNLOADING
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infoText = when (item.status) {
                    DownloadStatus.DOWNLOADING ->
                        "${FormatUtils.speed(item.speed)} · 剩余 ${
                            if (item.remainingMs > 0) FormatUtils.duration(item.remainingMs) else "--"
                        } · ${item.threads}线程"
                    DownloadStatus.WAITING -> "排队中…"
                    DownloadStatus.PAUSED -> "已暂停 ${FormatUtils.size(item.downloaded)}"
                    DownloadStatus.COMPLETED -> "完成 · ${FormatUtils.size(item.downloaded)}"
                    DownloadStatus.FAILED -> "失败：${item.error ?: "未知"}"
                    DownloadStatus.CANCELED -> "已取消"
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == DownloadStatus.FAILED)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    when (item.status) {
                        DownloadStatus.WAITING -> {
                            ActionIcon(Icons.Filled.Pause, "暂停", onPause)
                            ActionIcon(Icons.Filled.Close, "取消", onCancel)
                        }
                        DownloadStatus.PAUSED -> {
                            ActionIcon(Icons.Filled.PlayArrow, "继续", onResume)
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                            ActionIcon(Icons.Filled.Refresh, "重试", onRetry)
                        }
                        DownloadStatus.DOWNLOADING -> {
                            ActionIcon(Icons.Filled.Pause, "暂停", onPause)
                            ActionIcon(Icons.Filled.Close, "取消", onCancel)
                        }
                        DownloadStatus.COMPLETED -> {
                            ActionIcon(Icons.Filled.OpenInNew, "打开", onOpen)
                        }
                    }
                    ActionIcon(Icons.Filled.DeleteSweep, "删除", onDelete)
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon, contentDescription = desc,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
