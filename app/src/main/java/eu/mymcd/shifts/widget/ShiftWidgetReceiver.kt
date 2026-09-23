package eu.mymcd.shifts.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import eu.mymcd.shifts.MainActivity
import eu.mymcd.shifts.R
import eu.mymcd.shifts.data.Repository
import eu.mymcd.shifts.network.Shift
import eu.mymcd.shifts.store.SecureStore
import eu.mymcd.shifts.util.LocaleUtil
import eu.mymcd.shifts.work.RefreshWorker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ShiftWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        renderFromCache(context, appWidgetManager, appWidgetIds)
        // Only schedule work here (system lifecycle), not from inside the worker.
        if (Repository.get(context).store.anyAccountConfigured()) {
            RefreshWorker.enqueuePeriodicSafe(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MANUAL_REFRESH -> {
                updateWidgets(context)
                if (Repository.get(context).store.anyAccountConfigured()) {
                    RefreshWorker.enqueueNow(context)
                }
            }
            ACTION_REFRESH_WIDGET -> {
                // Render only — worker already refreshed; avoids infinite loop.
                updateWidgets(context)
            }
        }
    }

    companion object {
        private const val TAG = "MyMcDWidget"

        const val ACTION_REFRESH_WIDGET = "eu.mymcd.shifts.ACTION_REFRESH_WIDGET"
        const val ACTION_MANUAL_REFRESH = "eu.mymcd.shifts.ACTION_MANUAL_REFRESH"

        /** Safe direct widget paint (no broadcast → no worker loop). */
        fun updateWidgets(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, ShiftWidgetReceiver::class.java)
                )
                if (ids.isNotEmpty()) {
                    renderFromCache(context, mgr, ids)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "updateWidgets failed", t)
            }
        }

        fun requestRender(context: Context) {
            // Direct render is safer than broadcast (broadcast used to re-enqueue work).
            updateWidgets(context)
        }

        fun renderFromCache(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return
            try {
                val store = SecureStore(context)
                val repo = Repository.get(context)
                val active = repo.activeAccountId()
                val shifts = try {
                    repo.cachedShifts(active)
                } catch (t: Throwable) {
                    Log.e(TAG, "cachedShifts failed", t)
                    emptyList()
                }
                val language = try {
                    LocaleUtil.resolveLanguage(
                        store.language,
                        context.resources.configuration.locales[0]
                    )
                } catch (_: Throwable) {
                    LocaleUtil.LANG_EN
                }
                val title = try {
                    if (active.isBlank()) ""
                    else store.getFullName(active).ifBlank { store.getEmail(active) }
                } catch (_: Throwable) {
                    ""
                }
                val loggedIn = try {
                    store.anyAccountConfigured()
                } catch (_: Throwable) {
                    false
                }

                val views = buildViews(context, shifts, language, loggedIn, title)
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "renderFromCache failed", t)
                // Last-resort minimal views so launcher is not stuck on error state.
                try {
                    val fallback = RemoteViews(context.packageName, R.layout.widget_layout)
                    fallback.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    fallback.setViewVisibility(R.id.row0, View.GONE)
                    fallback.setViewVisibility(R.id.row1, View.GONE)
                    fallback.setViewVisibility(R.id.row2, View.GONE)
                    fallback.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                    fallback.setTextViewText(
                        R.id.widget_empty,
                        context.getString(R.string.widget_login_required)
                    )
                    for (id in appWidgetIds) {
                        appWidgetManager.updateAppWidget(id, fallback)
                    }
                } catch (t2: Throwable) {
                    Log.e(TAG, "fallback widget failed", t2)
                }
            }
        }

        private fun buildViews(
            context: Context,
            shifts: List<Shift>,
            language: String,
            loggedIn: Boolean,
            titleText: String
        ): RemoteViews {
            val localized = LocaleUtil.localizedContext(context, language)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val title = when {
                !loggedIn -> localized.getString(R.string.widget_login_required)
                titleText.isNotBlank() -> titleText
                else -> localized.getString(R.string.app_name)
            }
            views.setTextViewText(R.id.widget_title, title)

            val top3 = shifts.take(3)
            val rowIds = intArrayOf(R.id.row0, R.id.row1, R.id.row2)
            for (i in rowIds.indices) {
                if (i < top3.size) {
                    views.setViewVisibility(rowIds[i], View.VISIBLE)
                    views.setTextViewText(rowIds[i], formatShift(localized, top3[i]))
                } else {
                    views.setViewVisibility(rowIds[i], View.GONE)
                }
            }

            val empty = shifts.isEmpty() && loggedIn
            views.setViewVisibility(
                R.id.widget_empty,
                if (empty) View.VISIBLE else View.GONE
            )
            if (empty) {
                views.setTextViewText(
                    R.id.widget_empty,
                    localized.getString(R.string.no_upcoming_shifts)
                )
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val refresh = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ShiftWidgetReceiver::class.java).setAction(ACTION_MANUAL_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)

            return views
        }

        private fun formatShift(locale: Context, shift: Shift): String {
            val loc = locale.resources.configuration.locales[0]
            val datePart = try {
                val date = LocalDate.parse(shift.date)
                if (loc.language == "cs") {
                    date.format(DateTimeFormatter.ofPattern("EEE d. M. yyyy", Locale("cs")))
                        .replaceFirstChar { it.uppercase(loc) }
                } else {
                    date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH))
                }
            } catch (_: Exception) {
                shift.date
            }

            val from = try {
                LocalDateTime.parse(shift.from, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                shift.from.substringAfterLast(' ').ifBlank { shift.from }
            }
            val to = try {
                LocalDateTime.parse(shift.to, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                shift.to.substringAfterLast(' ').ifBlank { shift.to }
            }
            return "$datePart  ·  $from – $to"
        }
    }
}
