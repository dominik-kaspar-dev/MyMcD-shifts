package eu.mymcd.shifts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.mymcd.shifts.legal.LegalAssets
import eu.mymcd.shifts.notify.Notifier
import eu.mymcd.shifts.store.SecureStore
import eu.mymcd.shifts.store.SettingsStore
import eu.mymcd.shifts.ui.AppRoot
import eu.mymcd.shifts.ui.AppViewModel
import eu.mymcd.shifts.util.LocaleUtil

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        val store = SecureStore(newBase)
        super.attachBaseContext(LocaleUtil.wrap(newBase, store.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LegalAssets.init(applicationContext)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        if (SettingsStore(this).legalAccepted) {
            requestNotifPermissionIfNeeded()
        }
        setContent {
            MyMcDTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(applicationContext))
                AppRoot(vm)
            }
        }
    }

    fun onLegalAccepted() {
        requestNotifPermissionIfNeeded()
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }
}

private val McDonaldsRed = Color(0xFFDA291C)
private val McDonaldsYellow = Color(0xFFFFC72C)
private val McDonaldsGreen = Color(0xFF00644A)

@Composable
fun MyMcDTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = McDonaldsYellow,
            onPrimary = Color.Black,
            secondary = McDonaldsRed,
            tertiary = McDonaldsGreen,
            background = Color(0xFF121212),
            surface = Color(0xFF1C1C1C),
            onBackground = Color(0xFFF2F2F2),
            onSurface = Color(0xFFF2F2F2),
            primaryContainer = Color(0xFF3A2E10),
            onPrimaryContainer = Color(0xFFFFE08A),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFCAC4D0)
        )
    } else {
        lightColorScheme(
            primary = McDonaldsRed,
            onPrimary = Color.White,
            secondary = McDonaldsYellow,
            onSecondary = Color.Black,
            tertiary = McDonaldsGreen,
            background = Color(0xFFFFFBF5),
            surface = Color.White,
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F),
            primaryContainer = Color(0xFFFFDAD4),
            onPrimaryContainer = Color(0xFF410001),
            surfaceVariant = Color(0xFFF5F0EC),
            onSurfaceVariant = Color(0xFF534340)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
