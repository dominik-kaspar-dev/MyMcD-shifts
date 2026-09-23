package eu.mymcd.shifts.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.mymcd.shifts.R

@Composable
fun AppRoot(vm: AppViewModel) {
    val state = vm.state
    val context = LocalContext.current

    // gate → ignore; Legal → Settings; Settings → Shifts; Shifts/Login → leave app
    BackHandler(enabled = true) {
        when {
            !state.legalAccepted -> Unit
            state.screen == Screen.Legal -> vm.backFromLegal()
            state.screen == Screen.Settings -> vm.backToShifts()
            else -> findActivity(context)?.finish()
        }
    }

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

private fun findActivity(context: Context): Activity? {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
