package eu.mymcd.shifts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.mymcd.shifts.R
import eu.mymcd.shifts.util.LocaleUtil
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(vm: AppViewModel) {
    val state = vm.state
    val doc = state.legalDoc
    val lang = LocaleUtil.resolveLanguage(state.language)
    val locale = if (lang == LocaleUtil.LANG_CS) Locale("cs", "CZ") else Locale("en", "US")

    val titleRes = when (doc) {
        LegalDoc.Eula -> R.string.eula_title
        LegalDoc.Terms -> R.string.terms_title
        LegalDoc.Privacy -> R.string.privacy_title
    }
    val body = remember(doc, lang) { loadLegalBody(doc, lang) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = vm::backFromLegal) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.legal_effective),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.legal_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

internal fun loadLegalBody(doc: LegalDoc, lang: String): String {
    val name = when (doc) {
        LegalDoc.Eula -> if (lang == LocaleUtil.LANG_CS) "eula_cs.txt" else "eula_en.txt"
        LegalDoc.Terms -> if (lang == LocaleUtil.LANG_CS) "terms_cs.txt" else "terms_en.txt"
        LegalDoc.Privacy -> if (lang == LocaleUtil.LANG_CS) "privacy_cs.txt" else "privacy_en.txt"
    }
    return try {
        eu.mymcd.shifts.legal.LegalAssets.read(name)
    } catch (_: Exception) {
        "Document not found."
    }
}
