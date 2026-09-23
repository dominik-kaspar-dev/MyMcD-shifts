package eu.mymcd.shifts.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.mymcd.shifts.R
import eu.mymcd.shifts.util.LocaleUtil
import eu.mymcd.shifts.widget.ShiftWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val state = vm.state
    var showLogout by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val settings = state.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = vm::backToShifts) {
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
                .padding(16.dp)
        ) {
            SectionTitle(stringResource(R.string.accounts))
            Spacer(Modifier.height(8.dp))

            state.accounts.forEach { acc ->
                val isActive = acc.id == state.activeAccountId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isActive,
                                onClick = { if (!isActive) vm.switchAccount(acc.id) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isActive, onClick = {
                            if (!isActive) vm.switchAccount(acc.id)
                        })
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = acc.email,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            if (acc.fullName.isNotBlank()) {
                                Text(
                                    text = acc.fullName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActive) {
                                Text(
                                    text = stringResource(R.string.account_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { pendingDeleteId = acc.id }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove_account),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = vm::startAddAccount,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_account))
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.widget_section))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { pinWidget(context, vm) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_widget))
            }
            state.pinWidgetStatus?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.language))
            Spacer(Modifier.height(8.dp))

            val options = listOf(
                LocaleUtil.LANG_AUTO to stringResource(R.string.lang_auto),
                LocaleUtil.LANG_CS to "Čeština",
                LocaleUtil.LANG_EN to "English"
            )
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.language == value,
                                onClick = {
                                    val act = context.findActivity()
                                    vm.setLanguage(value, act)
                                },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.language == value,
                            onClick = {
                                val act = context.findActivity()
                                vm.setLanguage(value, act)
                            }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.lang_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Refresh interval
            SectionTitle(stringResource(R.string.refresh_interval))
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.refresh_interval_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val intervals = listOf(15, 30, 60, 180, 360, 720)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                intervals.forEach { m ->
                    FilterChip(
                        selected = settings.refreshIntervalMin == m,
                        onClick = { vm.setRefreshInterval(m) },
                        label = { Text(intervalLabel(m)) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Notifications
            SectionTitle(stringResource(R.string.notifications))
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.notifications_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            ToggleRow(
                title = stringResource(R.string.notify_on_add),
                subtitle = stringResource(R.string.notify_on_add_hint),
                checked = settings.notifyOnAdd,
                onCheckedChange = vm::setNotifyOnAdd
            )
            ToggleRow(
                title = stringResource(R.string.notify_on_change),
                subtitle = stringResource(R.string.notify_on_change_hint),
                checked = settings.notifyOnChange,
                onCheckedChange = vm::setNotifyOnChange
            )
            ToggleRow(
                title = stringResource(R.string.notify_on_remove),
                subtitle = stringResource(R.string.notify_on_remove_hint),
                checked = settings.notifyOnRemove,
                onCheckedChange = vm::setNotifyOnRemove
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.reminders_section),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(6.dp))

            ToggleRow(
                title = stringResource(R.string.remind_dayof_title),
                subtitle = stringResource(R.string.remind_dayof_hint),
                checked = settings.notifyDayOf,
                onCheckedChange = vm::setNotifyDayOf
            )
            if (settings.notifyDayOf) {
                HourPickerRow(
                    label = stringResource(R.string.remind_dayof_time),
                    selected = settings.dayOfHour,
                    onSelect = vm::setDayOfHour
                )
            }

            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.remind_before_title),
                subtitle = stringResource(R.string.remind_before_hint),
                checked = settings.notifyDayBefore,
                onCheckedChange = vm::setNotifyDayBefore
            )
            if (settings.notifyDayBefore) {
                HourPickerRow(
                    label = stringResource(R.string.remind_before_time),
                    selected = settings.dayBeforeHour,
                    onSelect = vm::setDayBeforeHour
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.legal_section))
            Spacer(Modifier.height(8.dp))
            LegalLink(stringResource(R.string.eula_title)) { vm.openLegal(LegalDoc.Eula) }
            LegalLink(stringResource(R.string.terms_title)) { vm.openLegal(LegalDoc.Terms) }
            LegalLink(stringResource(R.string.privacy_title)) { vm.openLegal(LegalDoc.Privacy) }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.logout))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.refresh_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(stringResource(R.string.logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    vm.logoutActive()
                }) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.remove_account_title)) },
            text = { Text(stringResource(R.string.remove_account_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId = null
                    vm.removeAccount(id)
                }) {
                    Text(stringResource(R.string.remove_account))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HourPickerRow(
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val hours = listOf(6, 7, 8, 9, 10, 12, 14, 16, 18, 20, 21, 22, 23)
    Column(Modifier.padding(start = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hours.take(7).forEach { h ->
                FilterChip(
                    selected = selected == h,
                    onClick = { onSelect(h) },
                    label = { Text("%02d:00".format(h)) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hours.drop(7).forEach { h ->
                FilterChip(
                    selected = selected == h,
                    onClick = { onSelect(h) },
                    label = { Text("%02d:00".format(h)) }
                )
            }
        }
    }
}

@Composable
private fun LegalLink(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}

private fun intervalLabel(min: Int): String = when {
    min < 60 -> "${min}m"
    min == 60 -> "1h"
    min < 1440 -> "${min / 60}h"
    else -> "24h"
}

private fun Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun pinWidget(context: Context, vm: AppViewModel) {
    try {
        val mgr = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, ShiftWidgetReceiver::class.java)
        if (!mgr.isRequestPinAppWidgetSupported) {
            vm.setPinWidgetStatus(context.getString(R.string.pin_unsupported))
            Toast.makeText(context, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        mgr.requestPinAppWidget(provider, null, null)
        vm.setPinWidgetStatus(context.getString(R.string.pin_requested))
        Toast.makeText(context, R.string.pin_requested, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        vm.setPinWidgetStatus(context.getString(R.string.pin_failed))
        Toast.makeText(context, R.string.pin_failed, Toast.LENGTH_LONG).show()
    }
}
