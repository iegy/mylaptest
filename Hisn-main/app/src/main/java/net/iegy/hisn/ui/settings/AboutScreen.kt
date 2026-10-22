@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import net.iegy.hisn.BuildConfig
import net.iegy.hisn.R
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.HisnLogo

@Composable
fun AboutScreen(
    isPrivate: Boolean,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onOpenSite: () -> Unit,
) {
    val context = LocalContext.current
    val webView = runCatching { WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.packageName} ${it.versionName}" } }.getOrNull()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        BackTopBar(title = stringResource(R.string.title_about), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HisnLogo(size = 96.dp)
            Text(stringResource(R.string.app_name_both), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.tagline_ar), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.tagline_en), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            // Creator credit: both languages are always shown, whatever the interface language is.
            Text(stringResource(R.string.credit_ar), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.credit_en), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            TextButton(onClick = onOpenSite) { Text("iegy.net") }

            Spacer(Modifier.height(8.dp))
            Text(
                "${stringResource(R.string.settings_version)}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
            if (webView != null) {
                Text("WebView: $webView", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_engine_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (isPrivate) {
                Text(
                    stringResource(R.string.private_banner_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_privacy_policy)) }
            OutlinedButton(onClick = onLicenses, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_licenses)) }
        }
    }
}
