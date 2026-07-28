package com.linenfeng.mtdownloader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.linenfeng.mtdownloader.R
import com.linenfeng.mtdownloader.ui.theme.BrandGradientEnd
import com.linenfeng.mtdownloader.ui.theme.BrandGradientStart
import java.net.URL

/**
 * xiaomirom.com 域名转换器页面
 *
 * 功能：把原始 OTA/固件 下载链接（从 xiaomirom.com 获取），替换 host 为预设加速服务器，
 * 生成满速下载的新链接，支持一键复制和一键加入下载任务。
 *
 * 作者：林恩风
 */

/** 加速服务器列表：host 到 标签说明 */
private val ACCEL_SERVERS: List<Pair<String, String>> = listOf(
    "bkt-sgp-miui-ota-update-alisgp.oss-ap-southeast-1.aliyuncs.com" to "首选 · 阿里云新加坡（最快）",
    "cdn-ota.azureedge.net" to "Azure CDN",
    "cdnorg.d.miui.com" to "小米官方源",
    "hugeota.d.miui.com" to "小米官方大文件源",
    "airtel.browserdl.in" to "Airtel 镜像",
    "bigota.d.miui.com" to "小米官方历史源"
)

private val ACCEL_HOSTS: List<String> = ACCEL_SERVERS.map { it.first }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainConverterScreen(
    onBack: () -> Unit,
    onDownload: (url: String) -> Unit
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var converted by remember { mutableStateOf("") }
    var serverIndex by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("🔧 域名转换器", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // 头部说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.AutoAwesome, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "xiaomirom.com 加速",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "将原始下载链接一键转换为加速域名，实现满速下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 输入
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; converted = ""; copied = false },
                        label = { Text("原始下载链接") },
                        placeholder = {
                            Text(
                                "例如 https://bkt-sgp-miui-ota-update-alisgp.oss-ap-southeast-1.aliyuncs.com/...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "选择加速服务器",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    ServersList(
                        selected = serverIndex,
                        onSelect = {
                            serverIndex = it
                            if (converted.isNotBlank()) {
                                converted = convert(input, it)
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                converted = ""
                                copied = false
                                val result = convert(input, serverIndex)
                                if (result.isBlank()) {
                                    Toast.makeText(ctx, "请输入有效的 http/https 链接", Toast.LENGTH_SHORT).show()
                                } else {
                                    converted = result
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("转换链接")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { input = ""; converted = ""; copied = false },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清空")
                        }
                    }
                }
            }

            // 结果
            if (converted.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Language, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "加速后的下载链接",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                text = converted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copyToClip(ctx, converted)
                                    copied = true
                                    Toast.makeText(ctx, "链接已复制", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                    null, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (copied) "已复制" else "复制链接")
                            }
                            OutlinedButton(
                                onClick = { onDownload(converted) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("加入下载")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ServersList(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ACCEL_SERVERS.forEachIndexed { i, (host, tag) ->
            val sel = i == selected
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(10.dp),
                color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    RadioButton(selected = sel, onClick = { onSelect(i) })
                    Spacer(Modifier.width(2.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            host,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 核心转换：取 URL 的 path+query+hash，host 替换为选定服务器 */
private fun convert(input: String, serverIndex: Int): String {
    val url = input.trim()
    if (url.isBlank()) return ""
    return runCatching {
        val parsed = URL(url)
        val queryPart = if (parsed.query != null) "?${parsed.query}" else ""
        val refPart = if (parsed.ref != null) "#${parsed.ref}" else ""
        val path = parsed.path + queryPart + refPart
        "https://${ACCEL_HOSTS[serverIndex]}$path"
    }.getOrDefault("")
}

private fun copyToClip(ctx: Context, text: String) {
    val mgr = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    mgr.setPrimaryClip(ClipData.newPlainText("download_url", text))
}
