@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package net.iegy.hisn.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import android.graphics.Bitmap
import net.iegy.hisn.ui.components.TextInputDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.BrowserHost
import net.iegy.hisn.R
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.tabs.TabState
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.ConfirmDialog
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.ui.home.Letter
import net.iegy.hisn.viewmodel.BrowserViewModel

@Composable
fun TabSwitcherScreen(vm: BrowserViewModel, host: BrowserHost, onBack: () -> Unit) {
    val model by vm.model.collectAsStateWithLifecycle()
    val thumbnails by vm.tabs.thumbnails.collectAsStateWithLifecycle()
    var groupFor by remember { mutableStateOf<TabState?>(null) }
    var newGroupFor by remember { mutableStateOf<TabState?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmCloseAll by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(MaterialTheme.colorScheme.background),
    ) {
        BackTopBar(
            title = stringResource(R.string.tabs_title, model.tabs.size),
            onBack = onBack,
            actions = {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tabs_close_others)) },
                        enabled = model.tabs.size > 1,
                        onClick = {
                            menuOpen = false
                            model.selectedId?.let { vm.tabs.closeOthers(it) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tabs_close_all)) },
                        onClick = { menuOpen = false; confirmCloseAll = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_reopen_tab)) },
                        enabled = model.closed.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            if (vm.tabs.reopenClosed()) onBack()
                        },
                    )
                }
            },
        )

        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Ungrouped tabs first, then one section per group (in the order the groups were created).
                val groups = model.tabs.mapNotNull { it.group }.distinct()
                val card: @Composable (TabState) -> Unit = { tab ->
                    TabCard(
                        tab = tab,
                        thumbnail = thumbnails[tab.id],
                        selected = tab.id == model.selectedId,
                        onClick = {
                            vm.tabs.select(tab.id)
                            onBack()
                        },
                        onLongClick = { groupFor = tab },
                        onClose = { vm.tabs.close(tab.id) },
                    )
                }
                items(model.tabs.filter { it.group == null }, key = { it.id }) { tab -> card(tab) }
                groups.forEach { group ->
                    item(key = "group:$group", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(model.tabs.filter { it.group == group }, key = { it.id }) { tab -> card(tab) }
                }
            }
            ExtendedFloatingActionButton(
                onClick = {
                    vm.tabs.openNewTab()
                    onBack()
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_tab)) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }

    groupFor?.let { tab ->
        val existing = model.tabs.mapNotNull { it.group }.distinct()
        AlertDialog(
            onDismissRequest = { groupFor = null },
            title = { Text(stringResource(R.string.tab_group_title)) },
            text = {
                Column {
                    existing.filter { it != tab.group }.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.tabs.setGroup(tab.id, name); groupFor = null }
                                .padding(vertical = 12.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.tab_group_new),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { newGroupFor = tab; groupFor = null }
                            .padding(vertical = 12.dp),
                    )
                    if (tab.group != null) {
                        Text(
                            text = stringResource(R.string.tab_group_remove),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.tabs.setGroup(tab.id, null); groupFor = null }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { groupFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    newGroupFor?.let { tab ->
        TextInputDialog(
            title = stringResource(R.string.tab_group_new),
            firstLabel = stringResource(R.string.tab_group_name_hint),
            initialFirst = "",
            onConfirm = { name, _ ->
                vm.tabs.setGroup(tab.id, name)
                newGroupFor = null
            },
            onDismiss = { newGroupFor = null },
        )
    }

    if (confirmCloseAll) {
        ConfirmDialog(
            title = stringResource(R.string.tabs_close_all),
            message = stringResource(R.string.tabs_close_all_message),
            confirmLabel = stringResource(R.string.tabs_close_all),
            onConfirm = {
                confirmCloseAll = false
                vm.tabs.closeAll()
                // A private session with no tabs ends itself; the regular browser opens a fresh start page.
                onBack()
            },
            onDismiss = { confirmCloseAll = false },
        )
    }
}

@Composable
private fun TabCard(
    tab: TabState,
    thumbnail: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    val host = UrlResolver.displayHost(tab.url).orEmpty()
    val title = tab.title.ifBlank { host.ifBlank { stringResource(R.string.tab_new) } }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(184.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Letter(title, 28)
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_tab), modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (thumbnail != null && !tab.isHome) {
                    val image = remember(thumbnail) { thumbnail.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    )
                } else if (host.isNotBlank()) {
                    UrlText(host, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                } else {
                    Text(
                        stringResource(R.string.tab_start_page),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
