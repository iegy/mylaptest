@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import net.iegy.hisn.R
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.data.ClearRange
import net.iegy.hisn.data.HistoryEntity
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.ConfirmDialog
import net.iegy.hisn.ui.components.EmptyState
import net.iegy.hisn.ui.components.SectionHeader
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.ui.home.Letter
import net.iegy.hisn.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var clearMenu by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<ClearRange?>(null) }

    BackHandler {
        if (searching) { searching = false; vm.query.value = "" } else onBack()
    }

    val dayFormat = remember { DateFormat.getDateInstance(DateFormat.FULL) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        BackTopBar(
            title = stringResource(R.string.title_history),
            onBack = onBack,
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
                IconButton(onClick = { clearMenu = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_clear_title))
                }
                DropdownMenu(expanded = clearMenu, onDismissRequest = { clearMenu = false }) {
                    ClearRange.entries.forEach { range ->
                        DropdownMenuItem(
                            text = { Text(stringResource(rangeLabel(range))) },
                            onClick = { clearMenu = false; pendingClear = range },
                        )
                    }
                }
            },
        )
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (entries.isEmpty()) {
            EmptyState(stringResource(if (query.isBlank()) R.string.history_empty else R.string.search_no_results))
        } else {
            val grouped = remember(entries) { entries.groupBy { dayFormat.format(Date(it.visitedAt)) } }
            LazyColumn(Modifier.fillMaxSize()) {
                grouped.forEach { (day, list) ->
                    item(key = "h$day") { SectionHeader(day) }
                    items(list, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            time = timeFormat.format(Date(entry.visitedAt)),
                            onClick = { onOpen(entry.url) },
                            onLongOpen = { onOpenInNewTab(entry.url) },
                            onDelete = { vm.delete(entry.id) },
                        )
                    }
                }
            }
        }
    }

    pendingClear?.let { range ->
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(
                if (range == ClearRange.ALL) R.string.history_clear_all_message else R.string.history_clear_range_message,
                stringResource(rangeLabel(range)),
            ),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = { vm.clear(range); pendingClear = null },
            onDismiss = { pendingClear = null },
        )
    }
}

fun rangeLabel(range: ClearRange): Int = when (range) {
    ClearRange.LAST_HOUR -> R.string.range_hour
    ClearRange.TODAY -> R.string.range_day
    ClearRange.LAST_WEEK -> R.string.range_week
    ClearRange.ALL -> R.string.range_all
}

@Composable
private fun HistoryRow(
    entry: HistoryEntity,
    time: String,
    onClick: () -> Unit,
    onLongOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Letter(entry.title.ifBlank { UrlResolver.displayHost(entry.url).orEmpty() }, 36)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            UrlText(
                "${UrlResolver.displayHost(entry.url) ?: entry.url} · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.lp_open_new_tab)) }, onClick = { menu = false; onLongOpen() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
        }
    }
}
