package net.iegy.hisn.browser

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves a page screenshot as a PNG into Pictures/Hisn, where the gallery app picks it up.
 * Android 10+ needs no permission (MediaStore); Android 9 writes the file directly and needs
 * WRITE_EXTERNAL_STORAGE (declared in the manifest with maxSdkVersion=28, requested by the UI when missing).
 */
object ScreenshotSaver {
    private const val FOLDER = "Hisn"
    private const val MIME = "image/png"

    /** Returns where the image was stored, or null when saving failed. */
    suspend fun save(context: Context, bitmap: Bitmap, pageTitle: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val name = fileName(pageTitle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(context, bitmap, name)
            else saveLegacy(context, bitmap, name)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, name: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            // Hidden from other apps until the bytes are fully written.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            if (!written) throw IOException("Could not write the screenshot")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, name: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, name)
        val written = FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!written) {
            file.delete()
            return null
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME), null)
        return Uri.fromFile(file)
    }

    /** Hisn_20261022_143015_page-title.png — the title part keeps letters and digits (Arabic included) only. */
    internal fun fileName(pageTitle: String, now: Date = Date()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val slug = pageTitle.trim()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(40)
        return if (slug.isEmpty()) "Hisn_$stamp.png" else "Hisn_${stamp}_$slug.png"
    }
}
