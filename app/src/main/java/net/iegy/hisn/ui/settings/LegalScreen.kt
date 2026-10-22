@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.iegy.hisn.ui.components.BackTopBar

/**
 * Shows a bundled plain-text document. "privacy" has Arabic and English versions (privacy_ar.txt / privacy_en.txt);
 * "licenses" is a single English file because it lists package names and licence texts.
 */
@Composable
fun LegalScreen(@StringRes titleRes: Int, asset: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val language = Locale.getDefault().language
    val text by produceState("", asset, language) {
        value = withContext(Dispatchers.IO) {
            val candidates = listOf("legal/${asset}_$language.txt", "legal/${asset}_en.txt", "legal/$asset.txt")
            candidates.firstNotNullOfOrNull { name ->
                runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
            }.orEmpty()
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        BackTopBar(title = stringResource(titleRes), onBack = onBack)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.let {
                // The licenses file is Latin text; keep it left-to-right even inside an RTL interface.
                if (asset == "licenses") it.copy(textDirection = TextDirection.Ltr) else it
            },
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}
