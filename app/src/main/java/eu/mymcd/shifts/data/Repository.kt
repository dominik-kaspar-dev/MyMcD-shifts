package eu.mymcd.shifts.data

import android.content.Context
import eu.mymcd.shifts.network.AuthException
import eu.mymcd.shifts.network.McDClient
import eu.mymcd.shifts.network.NetworkException
import eu.mymcd.shifts.network.Shift
import eu.mymcd.shifts.notify.Notifier
import eu.mymcd.shifts.store.AccountMeta
import eu.mymcd.shifts.store.SecureStore

sealed class RefreshResult {
    data class Success(val shifts: List<Shift>, val fromCache: Boolean = false) : RefreshResult()
    data class Error(val message: String, val cached: List<Shift>) : RefreshResult()
    object NotLoggedIn : RefreshResult()
}

class Repository private constructor(context: Context) {

    val appContext = context.applicationContext
    val store = SecureStore(appContext)

    fun clientFor(accountId: String): McDClient = McDClient(store, accountId)

    fun activeAccountId(): String {
        val id = store.activeAccountId
        if (id.isNotBlank() && store.getEmail(id).isNotBlank()) return id
        return store.accountIds().firstOrNull { store.hasCredentials(it) } ?: ""
    }

    fun activeClient(): McDClient? {
        val id = activeAccountId()
        return if (id.isBlank()) null else clientFor(id)
    }

    fun cachedShifts(accountId: String = activeAccountId()): List<Shift> =
        if (accountId.isBlank()) emptyList()
        else McDClient.parseShifts(store.getShifts(accountId))

    fun previousShifts(accountId: String = activeAccountId()): List<Shift> =
        if (accountId.isBlank()) emptyList()
        else McDClient.parseShifts(store.getPreviousShifts(accountId))

    fun accounts(): List<AccountMeta> = store.accounts()

    /** Returns shifts; records history + posts notification when the plan changed. */
    fun refreshAccount(accountId: String, notify: Boolean = true): RefreshResult {
        if (!store.hasCredentials(accountId)) return RefreshResult.NotLoggedIn
        val client = clientFor(accountId)
        return try {
            val old = McDClient.parseShifts(store.getShifts(accountId))
            val new = client.refreshAll(10)
            applyShiftUpdate(accountId, old, new, notify)
            RefreshResult.Success(new)
        } catch (e: AuthException) {
            store.setError(accountId, e.message ?: "auth")
            RefreshResult.Error(e.message ?: ERROR_AUTH, cachedShifts(accountId))
        } catch (e: NetworkException) {
            store.setError(accountId, e.message ?: "network")
            RefreshResult.Error(ERROR_NETWORK, cachedShifts(accountId))
        } catch (e: Exception) {
            store.setError(accountId, e.message ?: "unknown")
            RefreshResult.Error(ERROR_UNKNOWN, cachedShifts(accountId))
        }
    }

    fun refreshActive(notify: Boolean = true): RefreshResult {
        val id = activeAccountId()
        if (id.isBlank()) return RefreshResult.NotLoggedIn
        return refreshAccount(id, notify)
    }

    /** Refresh every stored account (for worker / widget). */
    fun refreshAllAccounts(notify: Boolean = true) {
        for (id in store.accountIds()) {
            if (store.hasCredentials(id)) {
                refreshAccount(id, notify)
            }
        }
    }

    private fun applyShiftUpdate(
        accountId: String,
        old: List<Shift>,
        new: List<Shift>,
        notify: Boolean
    ) {
        val changed = old.isNotEmpty() && !shiftListsEqual(old, new)
        if (changed) {
            store.setPreviousShifts(accountId, McDClient.shiftsToJson(old))
        }
        store.setShifts(accountId, McDClient.shiftsToJson(new))
        store.setLastUpdate(accountId, System.currentTimeMillis())

        if (notify && changed) {
            val d = Notifier.diff(old, new)
            if (d.hasChange) {
                Notifier.notifyShiftChange(
                    appContext,
                    email = store.getEmail(accountId).ifBlank { accountId },
                    added = d.added,
                    removed = d.removed,
                    changed = d.changed,
                    next = new.firstOrNull()
                )
            }
        }

        try {
            eu.mymcd.shifts.notify.ReminderScheduler.rescheduleAll(appContext)
        } catch (_: Throwable) {
        }
    }

    fun loginToAccount(email: String, password: String, accountId: String? = null): RefreshResult {
        return try {
            val id = accountId ?: store.ensureAccountForEmail(email)
            store.setEmail(id, email.trim())
            store.savePassword(id, password)
            val user = clientFor(id).login(email.trim(), password)
            store.activeAccountId = id

            val old = McDClient.parseShifts(store.getShifts(id))
            val shifts = try {
                clientFor(id).fetchNextShifts(user.id, 10)
            } catch (_: Exception) {
                emptyList()
            }
            applyShiftUpdate(id, old, shifts, notify = old.isNotEmpty())
            RefreshResult.Success(shifts)
        } catch (e: AuthException) {
            RefreshResult.Error(e.message ?: ERROR_AUTH, emptyList())
        } catch (e: NetworkException) {
            RefreshResult.Error(ERROR_NETWORK, emptyList())
        } catch (e: Exception) {
            RefreshResult.Error(ERROR_UNKNOWN, emptyList())
        }
    }

    fun switchAccount(id: String) {
        if (store.getEmail(id).isNotBlank()) {
            store.activeAccountId = id
        }
    }

    fun removeAccount(id: String) {
        store.deleteAccount(id)
    }

    fun logoutActive() {
        val id = activeAccountId()
        if (id.isNotBlank()) store.deleteAccount(id)
    }

    fun logoutAll() {
        store.clearAll()
    }

    private fun shiftListsEqual(a: List<Shift>, b: List<Shift>): Boolean {
        if (a.size != b.size) return false
        val sa = a.sortedBy { it.id }
        val sb = b.sortedBy { it.id }
        return sa.indices.all { sa[it].sameAs(sb[it]) }
    }

    companion object {
        const val ERROR_AUTH = "auth"
        const val ERROR_NETWORK = "network"
        const val ERROR_UNKNOWN = "unknown"

        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
