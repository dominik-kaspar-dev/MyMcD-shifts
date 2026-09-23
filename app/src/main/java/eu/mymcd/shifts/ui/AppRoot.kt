package eu.mymcd.shifts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.mymcd.shifts.R

@Composable
fun AppRoot(vm: AppViewModel) {
    val state = vm.state
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state.screen) {
            Screen.Login -> LoginScreen(vm)
            Screen.Shifts -> ShiftsScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.Legal -> LegalScreen(vm)
        }
        if (!state.legalAccepted) {
            LegalGate(vm)
        }
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}

fun errorText(kind: String?, @androidx.annotation.StringRes emptyRes: Int): String? = null

@Composable
fun mapError(kind: String?): String = when {
    kind == null -> ""
    kind == "empty" || kind == "NOT_CONFIGURED" -> stringResource(R.string.error_empty_fields)
    kind == "auth" -> stringResource(R.string.error_auth)
    kind == "network" -> stringResource(R.string.error_network)
    kind.startsWith("server:") -> kind.removePrefix("server:")
    kind.startsWith("HTTP_") -> stringResource(R.string.error_network)
    else -> stringResource(R.string.error_unknown)
}
