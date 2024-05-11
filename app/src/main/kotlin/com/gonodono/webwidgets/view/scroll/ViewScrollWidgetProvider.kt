package com.gonodono.webwidgets.view.scroll

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
import androidx.core.net.toUri
import com.gonodono.webwidgets.ACTION_OPEN
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.handleActionOpen
import com.gonodono.webwidgets.view.ACTION_RELOAD
import com.gonodono.webwidgets.view.appWidgetIdExtra
import com.gonodono.webwidgets.view.appWidgetManager
import com.gonodono.webwidgets.view.setUrl
import com.gonodono.webwidgets.view.updateAppWidgetIds
import com.gonodono.webwidgets.view.urlKey
import com.gonodono.webwidgets.view.widgetStates

class ViewScrollWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RELOAD -> {
                val appWidgetId = intent.appWidgetIdExtra
                setUrl(context, appWidgetId, null)
                context.appWidgetManager.notifyListChanged(appWidgetId)
            }

            ACTION_OPEN -> handleActionOpen(context, intent)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        appWidgetManager.notifyListChanged(appWidgetId)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> update(context, appWidgetManager, id) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        context.widgetStates.edit().apply {
            appWidgetIds.forEach { id -> remove(urlKey(id)) }
        }.apply()
    }

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray
    ) {
        updateAppWidgetIds(context, oldWidgetIds, newWidgetIds)
    }
}

internal fun AppWidgetManager.notifyListChanged(appWidgetId: Int) {
    notifyAppWidgetViewDataChanged(appWidgetId, R.id.list)
}

private fun update(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.scroll_widget_main)
    val adapter = Intent(context, ViewScrollWidgetService::class.java)
    adapter.appWidgetIdExtra = appWidgetId
    adapter.setData(adapter.toUri(Intent.URI_INTENT_SCHEME).toUri())
    views.setRemoteAdapter(R.id.list, adapter)
    views.setEmptyView(R.id.list, R.id.progress)

    val open = Intent(
        ACTION_OPEN,
        null,
        context,
        ViewScrollWidgetProvider::class.java
    )
    views.setPendingIntentTemplate(
        R.id.list,
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            open,
            FLAG_MUTABLE or FLAG_UPDATE_CURRENT
        ),
    )

    val reload = Intent(
        ACTION_RELOAD,
        null,
        context,
        ViewScrollWidgetProvider::class.java
    )
    reload.appWidgetIdExtra = appWidgetId
    views.setOnClickPendingIntent(
        R.id.reload,
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            reload,
            FLAG_IMMUTABLE
        )
    )

    appWidgetManager.updateAppWidget(appWidgetId, views)
}