package dev.gonodono.webwidgets.remoteviews.simple

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
import dev.gonodono.webwidgets.ActionOpen
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.WikipediaRandomPageUrl
import dev.gonodono.webwidgets.handleActionOpen
import dev.gonodono.webwidgets.remoteviews.ActionReload
import dev.gonodono.webwidgets.remoteviews.ReceiverTimeout
import dev.gonodono.webwidgets.remoteviews.appWidgetIdExtra
import dev.gonodono.webwidgets.remoteviews.appWidgetManager
import dev.gonodono.webwidgets.remoteviews.busyViews
import dev.gonodono.webwidgets.remoteviews.doAsync
import dev.gonodono.webwidgets.remoteviews.getWidgetSizeDp
import dev.gonodono.webwidgets.remoteviews.getWidgetUrl
import dev.gonodono.webwidgets.remoteviews.setWidgetSizeDp
import dev.gonodono.webwidgets.remoteviews.setWidgetUrl
import dev.gonodono.webwidgets.remoteviews.showView
import dev.gonodono.webwidgets.remoteviews.sizeDpKey
import dev.gonodono.webwidgets.remoteviews.toPx
import dev.gonodono.webwidgets.remoteviews.updateWidgets
import dev.gonodono.webwidgets.remoteviews.urlKey
import dev.gonodono.webwidgets.remoteviews.widgetSizeDp
import dev.gonodono.webwidgets.remoteviews.widgetStates
import dev.gonodono.webwidgets.screenSize
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.takeShotForAppWidget
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

class RemoteViewsSimpleWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) =
        when (intent.action) {
            ActionOpen -> {
                handleActionOpen(context, intent)
            }
            ActionReload -> {
                val appWidgetId = intent.appWidgetIdExtra
                setWidgetUrl(context, appWidgetId, null)
                val array = intArrayOf(appWidgetId)
                updateWidgets(context, context.appWidgetManager, array)
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
        updateWidgets(context, appWidgetManager, appWidgetIds)

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val savedSizeDp = getWidgetSizeDp(context, appWidgetId)
        val orientation = context.resources.configuration.orientation
        val currentSizeDp = newOptions.widgetSizeDp(orientation)
        if (savedSizeDp != currentSizeDp) {
            setWidgetSizeDp(context, appWidgetId, currentSizeDp)
            updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        doAsync {
            appWidgetManager.updateWidgets(appWidgetIds, busyViews(context))

            val viewsArray = arrayOfNulls<RemoteViews>(appWidgetIds.size)

            withTimeoutOrNull(ReceiverTimeout) {
                WebShooter.create(context).use { webShooter ->
                    appWidgetIds.forEachIndexed { index, id ->
                        if (!isActive) return@withTimeoutOrNull

                        val sizeDp = getWidgetSizeDp(context, id)
                        viewsArray[index] =
                            if (sizeDp.width > 0 && sizeDp.height > 0) {
                                contentViews(context, webShooter, id, sizeDp)
                            } else {
                                busyViews(context)  // Leave busy
                            }
                    }
                }
            }

            appWidgetIds.forEachIndexed { index, id ->
                val views = viewsArray[index] ?: timeoutViews(context, id)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) =
        context.widgetStates.edit {
            appWidgetIds.forEach { id ->
                remove(sizeDpKey(id))
                remove(urlKey(id))
            }
        }
}

private suspend fun contentViews(
    context: Context,
    webShooter: WebShooter,
    appWidgetId: Int,
    sizeDp: Size
): RemoteViews {
    val views = simpleWidgetViews(context, appWidgetId)

    val url = getWidgetUrl(context, appWidgetId) ?: WikipediaRandomPageUrl
    val result =
        webShooter.takeShotForAppWidget(
            url = url,
            screenSize = context.screenSize(),
            targetSize = sizeDp.toPx(context),
            fitTargetHeight = true
        )
    views.setImageViewBitmap(R.id.image, result.bitmap)

    setWidgetUrl(context, appWidgetId, result.url)
    if (result.url != null) {
        val open =
            Intent(
                /* action = */ ActionOpen,
                /* uri = */ result.url.toUri(),
                /* packageContext = */ context,
                /* cls = */ RemoteViewsSimpleWidget::class.java
            )
        open.appWidgetIdExtra = appWidgetId
        val pendingOpen =
            PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ appWidgetId,
                /* intent = */ open,
                /* flags = */ PendingIntent.FLAG_IMMUTABLE
            )
        views.setOnClickPendingIntent(R.id.image, pendingOpen)
    }

    views.showView(R.id.image)

    return views
}

private fun timeoutViews(context: Context, appWidgetId: Int): RemoteViews =
    simpleWidgetViews(context, appWidgetId).apply { showView(R.id.timeout) }

private fun simpleWidgetViews(context: Context, appWidgetId: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_simple).apply {
        val reload =
            Intent(
                /* action = */ ActionReload,
                /* uri = */ null,
                /* packageContext = */ context,
                /* cls = */ RemoteViewsSimpleWidget::class.java
            )
        reload.appWidgetIdExtra = appWidgetId
        val pendingReload =
            PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ appWidgetId,
                /* intent = */ reload,
                /* flags = */ PendingIntent.FLAG_IMMUTABLE
            )
        setOnClickPendingIntent(R.id.reload, pendingReload)
        showView(R.id.reload)
    }