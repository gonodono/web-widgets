package com.gonodono.webwidgets.view.simple

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.gonodono.webwidgets.ACTION_OPEN
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.WebShooter
import com.gonodono.webwidgets.handleActionOpen
import com.gonodono.webwidgets.view.ACTION_RELOAD
import com.gonodono.webwidgets.view.RECEIVER_TIMEOUT
import com.gonodono.webwidgets.view.appWidgetIdExtra
import com.gonodono.webwidgets.view.appWidgetManager
import com.gonodono.webwidgets.view.busyViews
import com.gonodono.webwidgets.view.doAsync
import com.gonodono.webwidgets.view.errorViews
import com.gonodono.webwidgets.view.getUrl
import com.gonodono.webwidgets.view.setUrl
import com.gonodono.webwidgets.view.setWidgetsRestored
import com.gonodono.webwidgets.view.urlKey
import com.gonodono.webwidgets.view.widgetSize
import com.gonodono.webwidgets.view.widgetStates
import kotlinx.coroutines.withTimeoutOrNull

class ViewSimpleWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OPEN -> handleActionOpen(context, intent)
            ACTION_RELOAD -> {
                val appWidgetId = intent.appWidgetIdExtra
                setUrl(context, appWidgetId, null)
                updateWidgets(
                    context,
                    context.appWidgetManager,
                    intArrayOf(appWidgetId)
                )
            }

            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Prevents double load for new widget.
        if (isInitialized(context, appWidgetId)) {
            updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
        } else {
            setInitialized(context, appWidgetId)
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val webShooter = WebShooter(context).apply { initialize() }
        val initial = when {
            webShooter.canDraw -> busyViews(context)
            else -> errorViews(context)
        }
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, initial)
        }
        if (!webShooter.canDraw) return

        doAsync {
            withTimeoutOrNull(RECEIVER_TIMEOUT) {
                appWidgetIds.forEach { id ->
                    updateWidget(context, appWidgetManager, id, webShooter)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        context.widgetStates.edit().apply {
            appWidgetIds.forEach { id ->
                remove(initializedKey(id))
                remove(urlKey(id))
            }
        }.apply()
    }

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray
    ) {
        context.widgetStates.edit().run {
            oldWidgetIds.forEachIndexed { index, oldId ->
                val newId = newWidgetIds[index]
                if (isInitialized(context, oldId)) {
                    putBoolean(initializedKey(newId), true)
                    remove(initializedKey(oldId))
                }
                putString(urlKey(newId), getUrl(context, oldId))
                remove(urlKey(oldId))
            }
            apply()
        }
        setWidgetsRestored(context, newWidgetIds)
        onUpdate(context, context.appWidgetManager, newWidgetIds)
    }
}

private suspend fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    webShooter: WebShooter
) {
    val size = context.widgetSize(appWidgetId)
    val views = when {
        size.width > 0 && size.height > 0 -> {
            val url = getUrl(context, appWidgetId) ?: WIKIPEDIA_RANDOM_URL
            val webShot = webShooter.takeShot(url, RECEIVER_TIMEOUT, size, true)
            if (webShot != null) setUrl(context, appWidgetId, webShot.url)
            mainViews(context, appWidgetId, webShot)
        }

        else -> busyViews(context)
    }
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun mainViews(
    context: Context,
    appWidgetId: Int,
    webShot: WebShooter.WebShot?
) = RemoteViews(context.packageName, R.layout.simple_widget).also { views ->
    when {
        webShot != null -> {
            views.setImageViewBitmap(R.id.image, webShot.bitmap)
            val open = Intent(
                ACTION_OPEN,
                webShot.url.toUri(),
                context,
                ViewSimpleWidgetProvider::class.java
            )
            open.appWidgetIdExtra = appWidgetId
            views.setViewVisibility(R.id.image, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.image,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    open,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        else -> views.setViewVisibility(R.id.timeout, View.VISIBLE)
    }
    val reload = Intent(
        ACTION_RELOAD,
        null,
        context,
        ViewSimpleWidgetProvider::class.java
    )
    reload.appWidgetIdExtra = appWidgetId
    views.setOnClickPendingIntent(
        R.id.reload,
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            reload,
            PendingIntent.FLAG_IMMUTABLE
        )
    )
}

private fun isInitialized(context: Context, appWidgetId: Int) =
    context.widgetStates.getBoolean(initializedKey(appWidgetId), false)

private fun setInitialized(context: Context, appWidgetId: Int) {
    context.widgetStates.edit()
        .putBoolean(initializedKey(appWidgetId), true).apply()
}

private fun initializedKey(appWidgetId: Int) = "initialized:$appWidgetId"