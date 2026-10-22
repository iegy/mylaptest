@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.R
import net.iegy.hisn.download.DownloadCategory
import net.iegy.hisn.download.DownloadItem
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.download.DownloadStatus
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.EmptyState
import net.iegy.hisn.ui.home.Letter
import net.iegy.hisn.viewmodel.DownloadsViewModel

// فئات التبويبات الذكية لتصفية الملفات مثل جوجل كروم
enum class FilterTab(val title: String, val matches: (DownloadCategory) -> Boolean) {
    ALL("الكل", { true }),
    IMAGES("صور", { it == DownloadCategory.IMAGE }),
    VIDEOS("فيديو", { it == DownloadCategory.VIDEO }),
    AUDIO("صوتيات", { it == DownloadCategory.AUDIO }),
    DOCS("مستندات", { it == DownloadCategory.DOCUMENT }),
    APPS("برامج ومضغوطات", { it == DownloadCategory.APK || it == DownloadCategory.ARCHIVE }),
    OTHER("أخرى", { it == DownloadCategory.OTHER }),
}

@Composable
fun DownloadsScreen(
    vm: DownloadsViewModel,
    onBack: () -> Unit,
    onOpenPdf: (uri: android.net.Uri, title: String?) -> Unit = { _, _ -> },
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = FilterTab.entries.toTypedArray()
    val filteredItems = items.filter { tabs.getOrNull(selectedTabIndex)?.matches(it.category) == true }

    fun open(item: DownloadItem) {
        val uri = vm.contentUri(item)
        if (uri == null) {
            Toast.makeText(context, R.string.dl_file_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val mime = item.entity.mimeType ?: context.contentResolver.getType(uri).orEmpty()
        val name = item.entity.fileName
        if (mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)) {
            onOpenPdf(uri, name)
            return
        }

        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime.ifBlank { null })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.dl_no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(item: DownloadItem) {
        val uri = vm.contentUri(item) ?: run {
            Toast.makeText(context, R.string.dl_file_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val send = Intent(Intent.ACTION_SEND)
            .setType(item.entity.mimeType ?: context.contentResolver.getType(uri) ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun retry(item: DownloadItem) {
        when (val start = vm.retry(item)) {
            is DownloadStart.Started -> Toast.makeText(
                context,
                context.getString(R.string.msg_download_started, start.fileName),
                Toast.LENGTH_SHORT,
            ).show()

            is DownloadStart.Failed -> Toast.makeText(context, start.messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        BackTopBar(title = stringResource(R.string.title_downloads), onBack = onBack)

        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.downloads_empty))
            return@Column
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.title) },
                )
            }
        }

        if (filteredItems.isEmpty()) {
            EmptyState(stringResource(R.string.search_no_results))
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(filteredItems, key = { it.entity.id }) { item ->
                DownloadRow(
                    item = item,
                    context = context,
                    onOpen = { open(item) },
                    onShare = { share(item) },
                    onRetry = { retry(item) },
                    onDelete = { vm.remove(item, deleteFile = false) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    context: android.content.Context,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val fileLabel = item.entity.fileName.ifBlank { "file" }
    val statusLabel = when (item.status) {
        DownloadStatus.PENDING -> stringResource(R.string.dl_waiting_network)
        DownloadStatus.RUNNING -> "Downloading"
        DownloadStatus.PAUSED -> stringResource(R.string.dl_paused)
        DownloadStatus.SUCCESS -> stringResource(R.string.dl_completed)
        DownloadStatus.FAILED -> stringResource(R.string.dl_err_failed)
        DownloadStatus.MISSING -> stringResource(R.string.dl_file_unavailable)
    }

    val sizeText = buildString {
        append(Formatter.formatFileSize(context, item.bytes))
        if (item.total > 0 && item.total != item.bytes) {
            append(" / ")
            append(Formatter.formatFileSize(context, item.total))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == DownloadStatus.SUCCESS || item.status == DownloadStatus.MISSING) { onOpen() }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Letter(fileLabel, 40)

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = fileLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.MISSING) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (item.status == DownloadStatus.PENDING || item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { item.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            if (item.errorRes != null) {
                Text(
                    text = stringResource(item.errorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.MISSING) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.action_retry))
                }
            }

            if (item.status == DownloadStatus.SUCCESS) {
                IconButton(onClick = onOpen) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = stringResource(R.string.action_open))
                }
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
