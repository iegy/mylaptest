package net.iegy.hisn.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.iegy.hisn.R
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.data.DownloadEntity
import net.iegy.hisn.data.DownloadsRepository

sealed interface DownloadStart {
    data class Started(val fileName: String) : DownloadStart
    data class Failed(@StringRes val messageRes: Int) : DownloadStart
}

enum class DownloadStatus { PENDING, RUNNING, PAUSED, SUCCESS, FAILED, MISSING }

// تمت إضافة تصنيفات الملفات لدعم التبويبات الذكية
enum class DownloadCategory { IMAGE, VIDEO, AUDIO, DOCUMENT, APK, ARCHIVE, OTHER }

data class DownloadItem(
    val entity: DownloadEntity,
    val status: DownloadStatus,
    val bytes: Long,
    val total: Long,
    @StringRes val errorRes: Int?,
    val fileUri: Uri?,
    val category: DownloadCategory, // التصنيف الذكي
) {
    val isActive: Boolean get() = status == DownloadStatus.PENDING || status == DownloadStatus.RUNNING || status == DownloadStatus.PAUSED
    
    // نسبة التقدم الجاهزة للواجهة (0.0 إلى 1.0)
    val progressPercent: Float get() = if (total > 0) (bytes.toFloat() / total.toFloat()) else 0f
}

/**
 * Downloads go through Android's system DownloadManager: it survives process death, resumes after network loss,
 * posts the completion notification, and writes to the public Downloads folder without any storage permission
 * on Android 10+ (Android 9 needs WRITE_EXTERNAL_STORAGE, declared with maxSdkVersion=28).
 */
class DownloadController(
    private val context: Context,
    private val repository: DownloadsRepository?,
    private val scope: CoroutineScope,
) {
    private val manager: DownloadManager get() = context.getSystemService(DownloadManager::class.java)

    fun enqueue(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        referer: String? = null,
    ): DownloadStart {
        if (!UrlResolver.isWebUrl(url)) return DownloadStart.Failed(R.string.dl_err_unsupported)
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return DownloadStart.Failed(R.string.dl_err_no_storage)
        }
        if (!isOnline()) return DownloadStart.Failed(R.string.dl_err_no_network)

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (contentLength > 0 && downloads.usableSpace in 0 until contentLength) {
            return DownloadStart.Failed(R.string.dl_err_no_space)
        }

        val resolvedMime = resolveMime(url, mimeType)
        val fileName = FileNameSanitizer.sanitize(
            URLUtil.guessFileName(url, contentDisposition, resolvedMime),
        )

        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            resolvedMime?.let { request.setMimeType(it) }
            userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
            referer?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Referer", it) }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
                ?.let { request.addRequestHeader("Cookie", it) }

            val systemId = manager.enqueue(request)
            repository?.let { repo ->
                scope.launch(Dispatchers.IO) {
                    repo.add(
                        DownloadEntity(
                            systemId = systemId,
                            fileName = fileName,
                            url = url,
                            mimeType = resolvedMime,
                            userAgent = userAgent,
                            referer = referer,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            DownloadStart.Started(fileName)
        } catch (e: SecurityException) {
            DownloadStart.Failed(R.string.dl_err_no_storage)
        } catch (e: IllegalArgumentException) {
            DownloadStart.Failed(R.string.dl_err_unsupported)
        }
    }

    fun retry(item: DownloadItem): DownloadStart {
        val e = item.entity
        val result = enqueue(e.url, e.userAgent, null, e.mimeType, -1, e.referer)
        if (result is DownloadStart.Started) {
            scope.launch(Dispatchers.IO) { repository?.delete(e.id) }
            manager.remove(e.systemId)
        }
        return result
    }

    fun remove(item: DownloadItem, deleteFile: Boolean) {
        if (item.isActive || deleteFile) manager.remove(item.entity.systemId)
        scope.launch(Dispatchers.IO) { repository?.delete(item.entity.id) }
    }

    fun snapshot(entities: List<DownloadEntity>): List<DownloadItem> {
        if (entities.isEmpty()) return emptyList()
        val byId = HashMap<Long, DownloadItem>()
        val bySystemId = entities.associateBy { it.systemId }
        val query = DownloadManager.Query().setFilterById(*entities.map { it.systemId }.toLongArray())
        manager.query(query)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val bytesCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            val uriCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            while (cursor.moveToNext()) {
                val systemId = cursor.getLong(idCol)
                val entity = bySystemId[systemId] ?: continue
                val reason = cursor.getInt(reasonCol)
                val status = when (cursor.getInt(statusCol)) {
                    DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
                    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESS
                    else -> DownloadStatus.FAILED
                }
                byId[entity.id] = DownloadItem(
                    entity = entity,
                    status = status,
                    bytes = cursor.getLong(bytesCol),
                    total = cursor.getLong(totalCol),
                    errorRes = when (status) {
                        DownloadStatus.FAILED -> failureMessage(reason)
                        DownloadStatus.PAUSED -> pausedMessage(reason)
                        else -> null
                    },
                    fileUri = cursor.getString(uriCol)?.let(Uri::parse),
                    category = getCategory(entity.mimeType, entity.fileName)
                )
            }
        }
        return entities.map { 
            byId[it.id] ?: DownloadItem(
                it, 
                DownloadStatus.MISSING, 
                0, 
                0, 
                null, 
                null,
                getCategory(it.mimeType, it.fileName)
            ) 
        }
    }

    fun contentUri(item: DownloadItem): Uri? =
        if (item.status == DownloadStatus.SUCCESS) manager.getUriForDownloadedFile(item.entity.systemId) else null

    // دالة جديدة: فتح الملف المحمل مباشرة (مثل كروم)
    fun getOpenFileIntent(item: DownloadItem): Intent? {
        val uri = contentUri(item) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.entity.mimeType ?: "*/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun failureMessage(reason: Int): Int = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> R.string.dl_err_no_space
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> R.string.dl_err_no_storage
        DownloadManager.ERROR_CANNOT_RESUME -> R.string.dl_err_cannot_resume
        DownloadManager.ERROR_FILE_ERROR, DownloadManager.ERROR_FILE_ALREADY_EXISTS -> R.string.dl_err_file
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> R.string.dl_err_redirects
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> R.string.dl_err_server
        in 400..599 -> R.string.dl_err_server
        else -> R.string.dl_err_failed
    }

    private fun pausedMessage(reason: Int): Int = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK, DownloadManager.PAUSED_QUEUED_FOR_WIFI -> R.string.dl_waiting_network
        else -> R.string.dl_paused
    }

    private fun resolveMime(url: String, mimeType: String?): String? {
        val provided = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (!provided.isNullOrEmpty() && provided != "application/octet-stream") return provided
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val guessed = if (extension.isNullOrEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        return guessed ?: provided
    }

    // دالة جديدة: تحليل نوع الملف بدقة لوضعه في التبويب الصحيح
    private fun getCategory(mimeType: String?, fileName: String): DownloadCategory {
        val mime = mimeType?.lowercase() ?: ""
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp") -> DownloadCategory.IMAGE
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi") -> DownloadCategory.VIDEO
            mime.startsWith("audio/") || ext in listOf("mp3", "wav", "ogg", "m4a") -> DownloadCategory.AUDIO
            mime.startsWith("application/pdf") || ext in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt") -> DownloadCategory.DOCUMENT
            mime == "application/vnd.android.package-archive" || ext == "apk" -> DownloadCategory.APK
            mime.contains("zip") || mime.contains("rar") || ext in listOf("zip", "rar", "7z", "tar", "gz") -> DownloadCategory.ARCHIVE
            else -> DownloadCategory.OTHER
        }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
