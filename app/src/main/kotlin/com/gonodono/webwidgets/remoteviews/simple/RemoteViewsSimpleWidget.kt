package com.gonodono.webwidgets.remoteviews.simple

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.net.toUri
import com.gonodono.webwidgets.ACTION_OPEN
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.WebShooter
import com.gonodono.webwidgets.handleActionOpen
import com.gonodono.webwidgets.log
import com.gonodono.webwidgets.remoteviews.ACTION_RELOAD
import com.gonodono.webwidgets.remoteviews.RECEIVER_TIMEOUT
import com.gonodono.webwidgets.remoteviews.appWidgetIdExtra
import com.gonodono.webwidgets.remoteviews.appWidgetManager
import com.gonodono.webwidgets.remoteviews.busyViews
import com.gonodono.webwidgets.remoteviews.doAsync
import com.gonodono.webwidgets.remoteviews.getUrl
import com.gonodono.webwidgets.remoteviews.setUrl
import com.gonodono.webwidgets.remoteviews.showView
import com.gonodono.webwidgets.remoteviews.updateWidgets
import com.gonodono.webwidgets.remoteviews.urlKey
import com.gonodono.webwidgets.remoteviews.widgetSize
import com.gonodono.webwidgets.remoteviews.widgetStates
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

class RemoteViewsSimpleWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OPEN -> handleActionOpen(context, intent)

            ACTION_RELOAD -> {
                val appWidgetId = intent.appWidgetIdExtra
                setUrl(context, appWidgetId, null)
                val array = intArrayOf(appWidgetId)
                updateWidgets(context, context.appWidgetManager, array)
            }

            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) =
        updateWidgets(context, appWidgetManager, appWidgetIds)

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
    ) =
        doAsync {
            val webShooter = WebShooter(context).apply { initialize() }
            val initial = if (webShooter.canDraw) {
                busyViews(context)
            } else {
                errorViews(context)
            }
            appWidgetManager.updateWidgets(appWidgetIds, initial)

            if (!webShooter.canDraw) return@doAsync

            val views = arrayOfNulls<RemoteViews>(appWidgetIds.size)
            withTimeoutOrNull(RECEIVER_TIMEOUT) {
                appWidgetIds.forEachIndexed { index, id ->
                    if (!isActive) return@withTimeoutOrNull

                    val size = context.widgetSize(id)
                    views[index] = if (size.width > 0 && size.height > 0) {
                        contentViews(context, webShooter, id, size)
                    } else {
                        initial  // Leave busy.
                    }
                }
            }
            appWidgetIds.forEachIndexed { index, id ->
                appWidgetManager.updateAppWidget(
                    id,
                    views[index] ?: timeoutViews(context, id)
                )
            }
        }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) =
        context.widgetStates.edit {
            appWidgetIds.forEach { id ->
                remove(initializedKey(id))
                remove(urlKey(id))
            }
        }
}

private suspend fun contentViews(
    context: Context,
    webShooter: WebShooter,
    appWidgetId: Int,
    size: Size
): RemoteViews {
    val views = simpleWidgetViews(context, appWidgetId)
    val url = getUrl(context, appWidgetId) ?: WIKIPEDIA_RANDOM_URL
    val result = webShooter.takeShot(url, size, true)
    when (result) {
        is WebShooter.WebShot -> {
            setUrl(context, appWidgetId, result.url)
            views.setImageViewBitmap(R.id.image, result.bitmap)
            val open = Intent(
                ACTION_OPEN,
                result.url.toUri(),
                context,
                RemoteViewsSimpleWidget::class.java
            )
            open.appWidgetIdExtra = appWidgetId
            val pendingOpen = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                open,
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.image, pendingOpen)
            views.showView(R.id.image)
        }

        is WebShooter.Error -> {
            views.showView(R.id.error)
            log(result.message)
        }
    }
    return views
}

private fun timeoutViews(context: Context, appWidgetId: Int): RemoteViews =
    simpleWidgetViews(context, appWidgetId).apply { showView(R.id.timeout) }

private fun simpleWidgetViews(context: Context, appWidgetId: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_simple).apply {
        val reload = Intent(
            ACTION_RELOAD,
            null,
            context,
            RemoteViewsSimpleWidget::class.java
        )
        reload.appWidgetIdExtra = appWidgetId
        val pendingReload = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            reload,
            PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.reload, pendingReload)
        showView(R.id.reload)
    }

private fun errorViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_text).apply {
        val text = context.getText(R.string.error)
        setTextViewText(android.R.id.background, text)
    }

private fun isInitialized(context: Context, appWidgetId: Int): Boolean =
    context.widgetStates.getBoolean(initializedKey(appWidgetId), false)

private fun setInitialized(context: Context, appWidgetId: Int) =
    context.widgetStates.edit { putBoolean(initializedKey(appWidgetId), true) }

private fun initializedKey(appWidgetId: Int) = "initialized:$appWidgetId"