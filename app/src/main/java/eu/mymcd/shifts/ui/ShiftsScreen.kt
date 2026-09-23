package eu.mymcd.shifts.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.mymcd.shifts.R
import eu.mymcd.shifts.network.Shift
import eu.mymcd.shifts.util.LocaleUtil
import eu.mymcd.shifts.util.ShareUtil
import eu.mymcd.shifts.util.TimeUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsScreen(vm: AppViewModel) {
    val state = vm.state
    val localeTag = LocaleUtil.resolveLanguage(state.language)
    val locale = remember(localeTag) {
        if (localeTag == LocaleUtil.LANG_CS) Locale("cs", "CZ") else Locale("en", "US")
    }
    var showPrevious by remember { mutableStateOf(false) }
    val displayed = if (showPrevious) state.previousShifts else state.shifts
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.next_shifts))
                        if (state.fullName.isNotBlank()) {
                            Text(
                                text = state.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showPrevious,
                    onClick = { showPrevious = !showPrevious },
                    label = {
                        Text(
                            stringResource(
                                if (showPrevious) R.string.show_current
                                else R.string.show_previous
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.History, contentDescription = null)
                    }
                )
            }

            if (!showPrevious && displayed.isNotEmpty()) {
                HoursSummaryCard(displayed, locale)
            }

            if (showPrevious && state.previousShifts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.previous_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.loading && displayed.isEmpty() && !showPrevious) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.error != null) mapError(state.error)
                            else stringResource(R.string.no_upcoming_shifts),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (state.error != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.pull_to_retry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (state.error != null && !showPrevious) {
                    Text(
                        text = mapError(state.error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (showPrevious) {
                    Text(
                        text = stringResource(R.string.previous_plan),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                } else if (state.lastUpdateMs > 0) {
                    Text(
                        text = stringResource(
                            R.string.last_updated,
                            formatUpdated(state.lastUpdateMs, locale)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayed, key = { if (showPrevious) "p${it.id}" else "c${it.id}" }) { shift ->
                        ShiftCard(
                            shift = shift,
                            locale = locale,
                            onShare = { ShareUtil.shareShift(context, shift) },
                            onCalendar = {
                                val ok = ShareUtil.addToCalendar(context, shift)
                                if (!ok) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.calendar_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.showing_count, displayed.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoursSummaryCard(shifts: List<Shift>, locale: Locale) {
    val now = remember(shifts) { LocalDateTime.now() }
    val next7 = remember(shifts, now) {
        shifts.filter {
            val s = TimeUtil.shiftStart(it) ?: return@filter false
            !s.isBefore(now) && s.isBefore(now.plusDays(7))
        }
    }
    val allH = TimeUtil.formatHours(TimeUtil.totalHours(shifts), locale)
    val weekH = TimeUtil.formatHours(TimeUtil.totalHours(next7), locale)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    stringResource(R.string.hours_next7, weekH),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.hours_all, allH),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShiftCard(
    shift: Shift,
    locale: Locale,
    onShare: () -> Unit,
    onCalendar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDate(shift.date, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimeRange(shift.from, shift.to),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!shift.note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = shift.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            DurationBadge(shift, locale)
            IconButton(onClick = onCalendar) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = stringResource(R.string.add_to_calendar),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.share_shift),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun DurationBadge(shift: Shift, locale: Locale) {
    val hours = TimeUtil.hoursBetween(shift) ?: return
    val text = TimeUtil.formatHours(hours, locale)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.Bold
    )
}

private val PARSER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun formatDate(dateStr: String, locale: Locale): String = try {
    val date = LocalDate.parse(dateStr)
    if (locale.language == "cs") {
        date.format(DateTimeFormatter.ofPattern("EEEE d. M. yyyy", locale))
            .replaceFirstChar { it.uppercase(locale) }
    } else {
        date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
            .replaceFirstChar { it.uppercase(locale) }
    }
} catch (_: Exception) {
    dateStr
}

fun formatTimeRange(from: String, to: String): String {
    val f = timePart(from)
    val t = timePart(to)
    return "$f – $t"
}

private fun timePart(value: String): String = try {
    LocalDateTime.parse(value, PARSER).format(DateTimeFormatter.ofPattern("HH:mm"))
} catch (_: Exception) {
    value.substringAfterLast(' ').ifBlank { value }
}

private fun formatUpdated(ms: Long, locale: Locale): String {
    val dt = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return dt.format(DateTimeFormatter.ofPattern("d.M. HH:mm", locale))
}
