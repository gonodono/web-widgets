package dev.gonodono.webwidgets.remoteviews

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.core.widget.RemoteViewsCompat.setViewStubInflatedId
import androidx.core.widget.RemoteViewsCompat.setViewStubLayoutResource
import dev.gonodono.webwidgets.ActionOpen
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.WikipediaRandomPageUrl
import dev.gonodono.webwidgets.handleActionOpen
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import dev.gonodono.webwidgets.shooter.createWebShooter
import dev.gonodono.webwidgets.takeShotForAppWidget
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

abstract class BaseRemoteViewsWidget(
    private val clampImageHeightToWidget: Boolean,
    private val contentLayoutId: Int,
    private val contentId: Int
) : AppWidgetProvider() {

    final override fun onReceive(context: Context, intent: Intent) =
        when (intent.action) {
            ActionOpen -> {
                handleActionOpen(context, intent)
            }
            ActionRandom -> {
                val appWidgetIds = intArrayOf(intent.appWidgetIdExtra)
                updateWidgets(context, context.appWidgetManager, appWidgetIds)
            }
            else -> {
                super.onReceive(context, intent)
            }
        }

    final override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) =
        updateWidgets(context, appWidgetManager, appWidgetIds)

    final override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val widgetData = context.widgetData
        val savedSizeDp = widgetData.getSizeDp(appWidgetId)
        val orientation = context.resources.configuration.orientation
        val currentSizeDp = newOptions.widgetSizeDp(orientation)
        if (savedSizeDp != currentSizeDp) {
            widgetData.putSizeDp(appWidgetId, currentSizeDp)
            updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        doAsync {
            val busy = RemoteViews(context.packageName, R.layout.widget_busy)
            appWidgetManager.updateWidgets(appWidgetIds, busy)

            val viewsArray = arrayOfNulls<RemoteViews>(appWidgetIds.size)

            withTimeoutOrNull(ReceiverTimeout) {
                createWebShooter(context).use { webShooter ->
                    appWidgetIds.forEachIndexed { index, id ->
                        if (!isActive) return@withTimeoutOrNull

                        val sizeDp = context.widgetData.getSizeDp(id)
                        viewsArray[index] =
                            if (sizeDp.width > 0 && sizeDp.height > 0) {
                                contentViews(context, webShooter, id, sizeDp)
                            } else {
                                busy
                            }
                    }
                }
            }

            appWidgetIds.forEachIndexed { index, id ->
                val views = viewsArray[index]
                    ?: createWidgetViews(
                        receiver = this@BaseRemoteViewsWidget.javaClass,
                        context = context,
                        appWidgetId = id,
                        contentLayoutId = R.layout.text_timeout,
                        contentId = R.id.text
                    )
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private suspend fun contentViews(
        context: Context,
        webShooter: WebShooter,
        appWidgetId: Int,
        sizeDp: Size
    ): RemoteViews {
        val webShot =
            webShooter.takeShotForAppWidget(
                url = WikipediaRandomPageUrl,
                targetSize = sizeDp.toPx(context),
                clampHeightToTarget = clampImageHeightToWidget
            )

        return when (webShot) {
            is WebShot.Complete ->
                createWidgetViews(
                    receiver = javaClass,
                    context = context,
                    appWidgetId = appWidgetId,
                    contentLayoutId = contentLayoutId,
                    contentId = contentId
                )
                    .apply { buildContent(context, webShot, appWidgetId) }

            is WebShot.Error ->
                createWidgetViews(
                    receiver = javaClass,
                    context = context,
                    appWidgetId = appWidgetId,
                    contentLayoutId = R.layout.text_error,
                    contentId = R.id.text
                )
        }
    }

    internal abstract fun RemoteViews.buildContent(
        context: Context,
        webShot: WebShot.Complete,
        appWidgetId: Int
    )

    protected fun createOpenAction(
        context: Context,
        appWidgetId: Int,
        url: String
    ): PendingIntent {
        val open = Intent(ActionOpen, url.toUri(), context, javaClass)
        open.appWidgetIdExtra = appWidgetId
        return PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ appWidgetId,
            /* intent = */ open,
            /* flags = */ FLAG_IMMUTABLE
        )
    }

    final override fun onDeleted(context: Context, appWidgetIds: IntArray) =
        context.widgetData.removeSizeDps(appWidgetIds)
}

private fun createWidgetViews(
    receiver: Class<out BaseRemoteViewsWidget>,
    context: Context,
    appWidgetId: Int,
    @LayoutRes contentLayoutId: Int,
    @IdRes contentId: Int
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_main).apply {
        val random = Intent(ActionRandom, null, context, receiver)
        random.appWidgetIdExtra = appWidgetId
        val pendingRandom =
            PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ appWidgetId,
                /* intent = */ random,
                /* flags = */ FLAG_IMMUTABLE
            )
        setOnClickPendingIntent(R.id.random, pendingRandom)

        setViewStubLayoutResource(R.id.stub, contentLayoutId)
        setViewStubInflatedId(R.id.stub, contentId)
        setViewVisibility(R.id.stub, View.VISIBLE)
    }