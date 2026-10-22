package net.iegy.hisn.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmark_folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** [folderId] is null for bookmarks in the root level. */
@Entity(tableName = "bookmarks", indices = [Index("folderId"), Index("url")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long? = null,
    val createdAt: Long,
)

/** Only regular (non-private) visits are ever stored here. */
@Entity(tableName = "history", indices = [Index("visitedAt"), Index("url")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int,
)

/** Links our list to a system DownloadManager job. Progress/status is always read live from DownloadManager. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String?,
    val userAgent: String?,
    val referer: String?,
    val createdAt: Long,
)

/** Metadata used to bring regular tabs back after the app is closed (when "restore tabs" is the startup choice). */
@Entity(tableName = "saved_tabs")
data class SavedTabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val position: Int,
    val selected: Boolean,
)
