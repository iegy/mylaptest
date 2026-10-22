@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package net.iegy.hisn.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.iegy.hisn.R
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.data.BookmarkEntity
import net.iegy.hisn.data.FolderEntity
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.ChoiceDialog
import net.iegy.hisn.ui.components.ConfirmDialog
import net.iegy.hisn.ui.components.EmptyState
import net.iegy.hisn.ui.components.TextInputDialog
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.ui.home.Letter
import net.iegy.hisn.viewmodel.BookmarksViewModel

@Composable
fun BookmarksScreen(
    vm: BookmarksViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
) {
    val folderId by vm.folderId.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val currentFolder = folders.firstOrNull { it.id == folderId }

    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }
    var addingFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deleting by remember { mutableStateOf<BookmarkEntity?>(null) }

    val goUp: () -> Unit = {
        when {
            searching -> { searching = false; vm.query.value = "" }
            folderId != null -> vm.folderId.value = null
            else -> onBack()
        }
    }
    BackHandler(onBack = goUp)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars),
    ) {
        BackTopBar(
            title = currentFolder?.name ?: stringResource(R.string.title_bookmarks),
            onBack = goUp,
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
                if (folderId == null) {
                    IconButton(onClick = { addingFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.bookmarks_new_folder))
                    }
                }
            },
        )
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.bookmarks_search_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val showFolders = folderId == null && query.isBlank()
        if (bookmarks.isEmpty() && (!showFolders || folders.isEmpty())) {
            EmptyState(stringResource(if (query.isBlank()) R.string.bookmarks_empty else R.string.search_no_results))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (showFolders) {
                    items(folders, key = { "f${it.id}" }) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = { vm.folderId.value = folder.id },
                            onRename = { renaming = folder },
                            onDelete = { deletingFolder = folder },
                        )
                    }
                }
                items(bookmarks, key = { "b${it.id}" }) { b ->
                    BookmarkRow(
                        bookmark = b,
                        onClick = { onOpen(b.url) },
                        onOpenNew = { onOpenInNewTab(b.url) },
                        onEdit = { editing = b },
                        onDelete = { deleting = b },
                    )
                }
            }
        }
    }

    if (addingFolder) {
        TextInputDialog(
            title = stringResource(R.string.bookmarks_new_folder),
            firstLabel = stringResource(R.string.field_name),
            initialFirst = "",
            onConfirm = { name, _ -> vm.addFolder(name); addingFolder = false },
            onDismiss = { addingFolder = false },
        )
    }
    renaming?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.bookmarks_rename_folder),
            firstLabel = stringResource(R.string.field_name),
            initialFirst = folder.name,
            onConfirm = { name, _ -> vm.renameFolder(folder, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deletingFolder?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.bookmarks_delete_folder),
            message = stringResource(R.string.bookmarks_delete_folder_message, folder.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteFolder(folder.id); deletingFolder = null },
            onDismiss = { deletingFolder = null },
        )
    }
    deleting?.let { b ->
        ConfirmDialog(
            title = stringResource(R.string.bookmarks_delete),
            message = b.title,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete(b.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    editing?.let { b ->
        EditBookmarkDialog(
            bookmark = b,
            folders = folders,
            onSave = { title, url, folder -> vm.save(b, title, url, folder); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun FolderRow(folder: FolderEntity, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Text(folder.name, modifier = Modifier.weight(1f).padding(horizontal = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onOpenNew: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Letter(bookmark.title, 36)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            UrlText(UrlResolver.displayHost(bookmark.url) ?: bookmark.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = { menu = false; onClick() })
                DropdownMenuItem(text = { Text(stringResource(R.string.lp_open_new_tab)) }, onClick = { menu = false; onOpenNew() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun EditBookmarkDialog(
    bookmark: BookmarkEntity,
    folders: List<FolderEntity>,
    onSave: (String, String, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var folder by remember { mutableStateOf(bookmark.folderId) }
    var pickingFolder by remember { mutableStateOf(false) }
    val rootLabel = stringResource(R.string.bookmarks_root)
    val folderName = folders.firstOrNull { it.id == folder }?.name ?: rootLabel

    TextInputDialog(
        title = stringResource(R.string.bookmarks_edit),
        firstLabel = stringResource(R.string.field_title),
        initialFirst = bookmark.title,
        secondLabel = stringResource(R.string.field_url),
        initialSecond = bookmark.url,
        secondIsUrl = true,
        validate = { _, url -> UrlResolver.normalizeWebUrl(url) != null },
        onConfirm = { title, url -> UrlResolver.normalizeWebUrl(url)?.let { onSave(title, it, folder) } },
        onDismiss = onDismiss,
        extraActions = {
            TextButton(onClick = { pickingFolder = true }) {
                Text(stringResource(R.string.bookmarks_folder_label, folderName))
            }
        },
    )
    if (pickingFolder) {
        val options = listOf<Pair<Long?, String>>(null to rootLabel) + folders.map { it.id to it.name }
        ChoiceDialog(
            title = stringResource(R.string.bookmarks_choose_folder),
            options = options,
            selected = folder,
            onSelect = { folder = it },
            onDismiss = { pickingFolder = false },
        )
    }
}
