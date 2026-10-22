package net.iegy.hisn.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.iegy.hisn.R
import java.io.IOException

/**
 * Lightweight in-app PDF reader using the platform [PdfRenderer].
 * Files stay on device; nothing is uploaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uriString: String,
    title: String?,
    onBack: () -> Unit,
    onShare: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val uri = remember(uriString) { Uri.parse(uriString) }

    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(uriString) {
        var localPfd: ParcelFileDescriptor? = null
        var localRenderer: PdfRenderer? = null
        try {
            localPfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (localPfd != null) {
                localRenderer = PdfRenderer(localPfd)
                pfd = localPfd
                renderer = localRenderer
                pageCount = localRenderer.pageCount
                loading = false
                if (localRenderer.pageCount == 0) {
                    error = context.getString(R.string.pdf_err_empty)
                }
            } else {
                loading = false
                error = context.getString(R.string.pdf_err_open)
            }
        } catch (e: SecurityException) {
            loading = false
            error = context.getString(R.string.pdf_err_open)
        } catch (e: IOException) {
            loading = false
            error = context.getString(R.string.pdf_err_open)
        } catch (e: Exception) {
            loading = false
            error = context.getString(R.string.pdf_err_open)
        }
        onDispose {
            try {
                localRenderer?.close()
            } catch (_: Exception) {
            }
            try {
                localPfd?.close()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(renderer, pageIndex, pageCount) {
        val r = renderer ?: return@LaunchedEffect
        if (pageCount <= 0 || pageIndex !in 0 until pageCount) return@LaunchedEffect
        loading = true
        val bmp = withContext(Dispatchers.IO) {
            try {
                r.openPage(pageIndex).use { page ->
                    val width = (page.width * 2).coerceAtMost(4096)
                    val height = (page.height * 2 * width / page.width.coerceAtLeast(1)).coerceAtMost(4096)
                    val bitmap = Bitmap.createBitmap(width, height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (_: Exception) {
                null
            }
        }
        pageBitmap?.recycle()
        pageBitmap = bmp
        loading = false
        if (bmp == null) error = context.getString(R.string.pdf_err_page)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.pdf_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { scale = (scale - 0.25f).coerceAtLeast(0.5f) }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = stringResource(R.string.pdf_zoom_out))
                    }
                    IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(4f) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = stringResource(R.string.pdf_zoom_in))
                    }
                    IconButton(onClick = { onShare(uri) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                },
            )
        },
        bottomBar = {
            if (pageCount > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0); scale = 1f },
                        enabled = pageIndex > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.pdf_prev))
                    }
                    Text(
                        stringResource(R.string.pdf_page, pageIndex + 1, pageCount),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(
                        onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1); scale = 1f },
                        enabled = pageIndex < pageCount - 1,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.pdf_next))
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null && pageBitmap == null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                loading && pageBitmap == null -> CircularProgressIndicator()
                pageBitmap != null -> {
                    val bmp = pageBitmap!!
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.pdf_page, pageIndex + 1, pageCount),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 4f)
                                }
                            },
                    )
                }
            }
        }
    }
}
