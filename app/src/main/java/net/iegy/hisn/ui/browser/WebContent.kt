package net.iegy.hisn.ui.browser

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import net.iegy.hisn.ui.components.horizontalSwipe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.iegy.hisn.R
import net.iegy.hisn.tabs.PageError
import net.iegy.hisn.tabs.PageErrorKind
import net.iegy.hisn.tabs.TabState
import net.iegy.hisn.ui.components.UrlText
import net.iegy.hisn.viewmodel.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebContent(vm: BrowserViewModel, tab: TabState) {
    // The engine view is created on demand and cached by the tab manager, so showing the tab switcher or another
    // screen and coming back re-attaches the same live view instead of reloading the page.
    val view = remember(tab.id, tab.error?.kind) { vm.tabs.viewFor(tab.id) }
    // Pull down at the top of a page to reload it. The indicator stops when loading ends (or after a short timeout).
    var refreshing by remember(tab.id) { mutableStateOf(false) }
    LaunchedEffect(tab.isLoading) { if (!tab.isLoading) refreshing = false }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(4_000)
            refreshing = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                vm.tabs.reload(tab.id)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            EngineViewHost(view, Modifier.fillMaxSize())
        }
        // Narrow edge strips: swipe in from the start edge to go back, from the end edge to go forward.
        if (tab.canGoBack) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(EDGE_SWIPE_WIDTH)
                    .horizontalSwipe(onNext = {}, onPrevious = { vm.tabs.goBack(tab.id) }),
            )
        }
        if (tab.canGoForward) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(EDGE_SWIPE_WIDTH)
                    .horizontalSwipe(onNext = { vm.tabs.goForward(tab.id) }, onPrevious = {}),
            )
        }
        tab.error?.let { error ->
            ErrorPage(
                error = error,
                onRetry = { vm.tabs.retry(tab.id) },
                onContinueHttp = { vm.tabs.allowHttp(tab.id) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val EDGE_SWIPE_WIDTH = 18.dp

@Composable
private fun EngineViewHost(view: View?, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> FrameLayout(context) },
        update = { container ->
            if (view == null) {
                container.removeAllViews()
            } else if (container.childCount != 1 || container.getChildAt(0) !== view) {
                container.removeAllViews()
                (view.parent as? ViewGroup)?.removeView(view)
                container.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
        // Detach only; the tab manager owns the view's lifetime and destroys it when the tab is closed.
        onRelease = { it.removeAllViews() },
    )
}

@Composable
private fun ErrorPage(
    error: PageError,
    onRetry: () -> Unit,
    onContinueHttp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, titleRes, messageRes) = when (error.kind) {
        PageErrorKind.OFFLINE -> Triple(Icons.Filled.WifiOff, R.string.err_offline_title, R.string.err_offline_message)
        PageErrorKind.DNS -> Triple(Icons.Filled.CloudOff, R.string.err_dns_title, R.string.err_dns_message)
        PageErrorKind.TIMEOUT -> Triple(Icons.Filled.CloudOff, R.string.err_timeout_title, R.string.err_timeout_message)
        PageErrorKind.CONNECTION -> Triple(Icons.Filled.CloudOff, R.string.err_connection_title, R.string.err_connection_message)
        PageErrorKind.SSL -> Triple(Icons.Filled.Lock, R.string.err_ssl_title, R.string.err_ssl_message)
        PageErrorKind.CRASH -> Triple(Icons.Filled.ErrorOutline, R.string.err_crash_title, R.string.err_crash_message)
        PageErrorKind.GENERIC -> Triple(Icons.Filled.ErrorOutline, R.string.err_generic_title, R.string.err_generic_message)
        PageErrorKind.HTTPS_ONLY -> Triple(Icons.Filled.Lock, R.string.err_https_title, R.string.err_https_message)
    }
    Box(modifier.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (error.url.isNotBlank()) {
                UrlText(error.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            if (error.kind == PageErrorKind.HTTPS_ONLY) {
                androidx.compose.material3.TextButton(onClick = onContinueHttp) {
                    Text(stringResource(R.string.err_https_continue))
                }
            }
        }
    }
}
