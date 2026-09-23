package eu.mymcd.shifts.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.mymcd.shifts.data.RefreshResult
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.network.Shift
import eu.mymcd.shifts.store.AccountMeta
import eu.mymcd.shifts.util.LocaleUtil
import eu.mymcd.shifts.widget.ShiftWidgetReceiver
import eu.mymcd.shifts.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Login, Shifts, Settings }
enum class LoginMode { SignIn, AddAccount }

data class UiState(
    val screen: Screen = Screen.Login,
    val loginMode: LoginMode = LoginMode.SignIn,
    val loading: Boolean = false,
    val shifts: List<Shift> = emptyList(),
    val previousShifts: List<Shift> = emptyList(),
    val error: String? = null,
    val loggedIn: Boolean = false,
    val fullName: String = "",
    val email: String = "",
    val language: String = LocaleUtil.LANG_AUTO,
    val lastUpdateMs: Long = 0L,
    val accounts: List<AccountMeta> = emptyList(),
    val activeAccountId: String = "",
    val pinWidgetStatus: String? = null
)

class AppViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repo = Repository.get(appContext)

    var state by mutableStateOf(UiState())
        private set

    init {
        reloadFromStore()
        if (repo.store.anyAccountConfigured()) {
            refresh()
        }
    }

    private fun reloadFromStore() {
        val active = repo.activeAccountId()
        val configured = repo.store.anyAccountConfigured()
        state = state.copy(
            loggedIn = configured,
            email = if (active.isNotBlank()) repo.store.getEmail(active) else state.email,
            fullName = if (active.isNotBlank()) repo.store.getFullName(active) else "",
            language = repo.store.language,
            shifts = repo.cachedShifts(active),
            previousShifts = repo.previousShifts(active),
            lastUpdateMs = if (active.isNotBlank()) repo.store.getLastUpdate(active) else 0L,
            accounts = repo.accounts(),
            activeAccountId = active,
            screen = when {
                state.screen == Screen.Settings -> Screen.Settings
                configured -> Screen.Shifts
                else -> Screen.Login
            },
            loginMode = LoginMode.SignIn
        )
    }

    fun onEmailChange(v: String) {
        state = state.copy(email = v, error = null)
    }

    fun startAddAccount() {
        state = state.copy(
            screen = Screen.Login,
            loginMode = LoginMode.AddAccount,
            email = "",
            error = null,
            loading = false
        )
    }

    fun cancelAddAccount() {
        state = state.copy(
            screen = if (repo.store.anyAccountConfigured()) Screen.Shifts else Screen.Login,
            loginMode = LoginMode.SignIn,
            error = null
        )
        if (repo.store.anyAccountConfigured()) refresh()
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            state = state.copy(error = "empty")
            return
        }
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) {
                repo.loginToAccount(email.trim(), password)
            }
            when (result) {
                is RefreshResult.Success -> {
                    reloadFromStore()
                    state = state.copy(
                        loading = false,
                        loggedIn = true,
                        shifts = result.shifts,
                        error = null,
                        screen = Screen.Shifts,
                        loginMode = LoginMode.SignIn
                    )
                    kickWorker()
                }
                is RefreshResult.Error -> {
                    state = state.copy(
                        loading = false,
                        error = result.message,
                        loggedIn = repo.store.anyAccountConfigured()
                    )
                }
                is RefreshResult.NotLoggedIn -> {
                    state = state.copy(loading = false, error = "empty")
                }
            }
        }
    }

    fun refresh() {
        if (!repo.store.anyAccountConfigured()) {
            state = state.copy(screen = Screen.Login, loggedIn = false)
            return
        }
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) { repo.refreshActive(notify = true) }
            when (result) {
                is RefreshResult.Success -> {
                    val active = repo.activeAccountId()
                    state = state.copy(
                        loading = false,
                        shifts = result.shifts,
                        previousShifts = repo.previousShifts(active),
                        error = null,
                        loggedIn = true,
                        fullName = repo.store.getFullName(active),
                        email = repo.store.getEmail(active),
                        lastUpdateMs = repo.store.getLastUpdate(active),
                        accounts = repo.accounts()
                    )
                    kickWidgetRender()
                }
                is RefreshResult.Error -> {
                    val active = repo.activeAccountId()
                    state = state.copy(
                        loading = false,
                        error = result.message,
                        shifts = result.cached.ifEmpty { repo.cachedShifts(active) },
                        previousShifts = repo.previousShifts(active),
                        loggedIn = repo.store.anyAccountConfigured()
                    )
                }
                is RefreshResult.NotLoggedIn -> {
                    state = state.copy(loading = false, loggedIn = false, screen = Screen.Login)
                }
            }
        }
    }

    fun openShifts() {
        state = state.copy(screen = Screen.Shifts, error = null)
        refresh()
    }

    fun openSettings() {
        reloadFromStore()
        state = state.copy(screen = Screen.Settings, error = null, pinWidgetStatus = null)
    }

    fun backToShifts() {
        openShifts()
    }

    fun setLanguage(lang: String) {
        repo.store.language = lang
        state = state.copy(language = lang)
    }

    fun switchAccount(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.switchAccount(id) }
            reloadFromStore()
            state = state.copy(screen = Screen.Shifts, error = null)
            refresh()
        }
    }

    fun removeAccount(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.removeAccount(id) }
            reloadFromStore()
            if (!repo.store.anyAccountConfigured()) {
                state = state.copy(screen = Screen.Login, loggedIn = false, loginMode = LoginMode.SignIn)
            }
            kickWorker()
        }
    }

    fun logoutActive() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.logoutActive() }
            reloadFromStore()
            if (!repo.store.anyAccountConfigured()) {
                state = UiState(language = repo.store.language, screen = Screen.Login)
            } else {
                state = state.copy(screen = Screen.Shifts)
                refresh()
            }
            kickWorker()
        }
    }

    fun showPreviousPlan() {
        state = state.copy(screen = Screen.Shifts)
        // previous is already in state; UI can toggle
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    fun setPinWidgetStatus(msg: String?) {
        state = state.copy(pinWidgetStatus = msg)
    }

    private fun kickWorker() {
        RefreshWorker.enqueueNow(appContext)
        ShiftWidgetReceiver.requestRender(appContext)
    }

    private fun kickWidgetRender() {
        ShiftWidgetReceiver.requestRender(appContext)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(context) as T
        }
    }
}
