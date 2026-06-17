package dev.gonodono.webwidgets.remoteviews.scroll

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.gonodono.webwidgets.ActionOpen
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.handleActionOpen
import dev.gonodono.webwidgets.remoteviews.ActionReload
import dev.gonodono.webwidgets.remoteviews.appWidgetIdExtra
import dev.gonodono.webwidgets.remoteviews.appWidgetManager
import dev.gonodono.webwidgets.remoteviews.doAsync
import dev.gonodono.webwidgets.remoteviews.setWidgetUrl
import dev.gonodono.webwidgets.remoteviews.urlKey
import dev.gonodono.webwidgets.remoteviews.widgetStates

class RemoteViewsScrollWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) =
        when (intent.action) {
            ActionOpen -> {
                handleActionOpen(context, intent)
            }
            ActionReload -> {
                val appWidgetId = intent.appWidgetIdExtra
                setWidgetUrl(context, appWidgetId, null)
                context.appWidgetManager.notifyListChanged(appWidgetId)
            }
            else -> {
                super.onReceive(context, intent)
            }
        }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) =
        doAsync {
            appWidgetIds.forEach { id ->
                updateWidget(context, appWidgetManager, id)
            }
        }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) =
        appWidgetManager.notifyListChanged(appWidgetId)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) =
        context.widgetStates.edit {
            appWidgetIds.forEach { id -> remove(urlKey(id)) }
        }
}

// The API for collection Widgets has changed, but it's counterproductive to
// mess with it here, so the two deprecation warnings about it are suppressed.

internal fun AppWidgetManager.notifyListChanged(appWidgetId: Int) =
    @Suppress("DEPRECATION")
    notifyAppWidgetViewDataChanged(appWidgetId, R.id.list)

private fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_scroll_main)
    val adapter = Intent(context, RemoteViewsScrollService::class.java)

    adapter.appWidgetIdExtra = appWidgetId // <- Must do before toUri().
    adapter.setData(adapter.toUri(Intent.URI_INTENT_SCHEME).toUri())

    @Suppress("DEPRECATION")
    views.setRemoteAdapter(R.id.list, adapter)
    views.setEmptyView(R.id.list, R.id.progress)

    val open =
        Intent(
            /* action = */ ActionOpen,
            /* uri = */ null,
            /* packageContext = */ context,
            /* cls = */ RemoteViewsScrollWidget::class.java
        )
    val pendingOpen =
        PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ appWidgetId,
            /* intent = */ open,
            /* flags = */ FLAG_MUTABLE or FLAG_UPDATE_CURRENT
        )
    views.setPendingIntentTemplate(R.id.list, pendingOpen)

    val reload =
        Intent(
            /* action = */ ActionReload,
            /* uri = */ null,
            /* packageContext = */ context,
            /* cls = */ RemoteViewsScrollWidget::class.java
        )
    reload.appWidgetIdExtra = appWidgetId
    val pendingReload =
        PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ appWidgetId,
            /* intent = */ reload,
            /* flags = */ FLAG_IMMUTABLE
        )
    views.setOnClickPendingIntent(R.id.reload, pendingReload)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}