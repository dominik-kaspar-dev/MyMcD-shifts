package eu.mymcd.shifts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.mymcd.shifts.R
import eu.mymcd.shifts.store.SettingsStore
import eu.mymcd.shifts.util.LocaleUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Blocking consent gate. One checkbox per document; all three required.
 * Decline closes the app; next launch asks again. Re-prompts when docs change.
 */
@Composable
fun LegalGate(vm: AppViewModel) {
    val state = vm.state
    if (state.legalAccepted) return

    var eula by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<LegalDoc?>(null) }
    val allChecked = eula && terms && privacy

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        if (viewing != null) {
            LegalGateDocViewer(
                doc = viewing!!,
                language = state.language,
                onBack = { viewing = null }
            )
            return@Dialog
        }

        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text(
                    text = stringResource(R.string.legal_gate_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.legal_gate_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))

                    LegalDocCheckRow(
                        checked = eula,
                        onCheckedChange = { eula = it },
                        titleRes = R.string.eula_title,
                        onView = { viewing = LegalDoc.Eula }
                    )
                    LegalDocCheckRow(
                        checked = terms,
                        onCheckedChange = { terms = it },
                        titleRes = R.string.terms_title,
                        onView = { viewing = LegalDoc.Terms }
                    )
                    LegalDocCheckRow(
                        checked = privacy,
                        onCheckedChange = { privacy = it },
                        titleRes = R.string.privacy_title,
                        onView = { viewing = LegalDoc.Privacy }
                    )

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(
                            R.string.legal_gate_status,
                            formatTs(state.legalConfirmedAtMs),
                            formatTs(SettingsStore.LEGAL_DOCS_CHANGED_AT_MS)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.acceptLegal() },
                    enabled = allChecked
                ) {
                    Text(stringResource(R.string.legal_gate_accept))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.declineLegal() }) {
                    Text(stringResource(R.string.legal_gate_decline))
                }
            }
        )
    }
}

@Composable
private fun LegalDocCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleRes: Int,
    onView: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.legal_gate_view_doc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onView)
                    .padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun LegalGateDocViewer(
    doc: LegalDoc,
    language: String,
    onBack: () -> Unit
) {
    val lang = LocaleUtil.resolveLanguage(language)
    val titleRes = when (doc) {
        LegalDoc.Eula -> R.string.eula_title
        LegalDoc.Terms -> R.string.terms_title
        LegalDoc.Privacy -> R.string.privacy_title
    }
    val body = remember(doc, lang) { loadLegalBody(doc, lang) }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        AlertDialog(
            onDismissRequest = onBack,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.legal_effective),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.back))
                }
            },
            dismissButton = {}
        )
    }
}

private fun formatTs(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
