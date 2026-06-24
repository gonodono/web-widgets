package dev.gonodono.webwidgets.remoteviews

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.core.widget.RemoteViewsCompat
import androidx.core.widget.RemoteViewsCompat.setViewStubInflatedId
import androidx.core.widget.RemoteViewsCompat.setViewStubLayoutResource
import dev.gonodono.webwidgets.ActionOpen
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.WikipediaRandomPageUrl
import dev.gonodono.webwidgets.handleActionOpen
import dev.gonodono.webwidgets.shootForAppWidget
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import dev.gonodono.webwidgets.shooter.createWebShooter
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class RemoteViewsWebWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) =
        when (intent.action) {
            ActionOpen -> {
                handleActionOpen(context, intent)
            }
            ActionReload -> {
                val appWidgetIds = intArrayOf(intent.appWidgetIdExtra)
                onUpdate(context, context.appWidgetManager, appWidgetIds)
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
            val busy = RemoteViews(context.packageName, R.layout.widget_busy)
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, busy) }

            val viewsArray = arrayOfNulls<RemoteViews>(appWidgetIds.size)

            withTimeoutOrNull(9.seconds) {  // ANR at ~10
                createWebShooter(context).use { webShooter ->
                    appWidgetIds.forEachIndexed { index, id ->
                        ensureActive()

                        val views = createViews(context, webShooter, id)
                        viewsArray[index] = views ?: busy
                    }
                }
            }

            appWidgetIds.forEachIndexed { index, id ->
                val views = viewsArray[index] ?: timeoutViews(context, id)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
}

private suspend fun createViews(
    context: Context,
    webShooter: WebShooter,
    appWidgetId: Int
): RemoteViews? {
    val size = widgetSize(context, appWidgetId)
    if (size.width <= 0 || size.height <= 0) return null

    val webShot = webShooter.shootForAppWidget(WikipediaRandomPageUrl, size)

    return when (webShot) {
        is WebShot.Complete -> contentViews(context, appWidgetId, webShot)
        is WebShot.Error -> errorViews(context, appWidgetId)
    }
}

private fun contentViews(
    context: Context,
    appWidgetId: Int,
    webShot: WebShot.Complete
): RemoteViews {
    val views =
        widgetViews(
            context = context,
            appWidgetId = appWidgetId,
            contentLayoutId = R.layout.list,
            contentId = R.id.list
        )

    val item = RemoteViews(context.packageName, R.layout.image)
    item.setImageViewBitmap(R.id.image, webShot.bitmap)

    if (webShot.url != null) {
        val open =
            Intent(
                /* action = */ ActionOpen,
                /* uri = */ webShot.url.toUri(),
                /* packageContext = */ context,
                /* cls = */ RemoteViewsWebWidget::class.java
            )
        open.appWidgetIdExtra = appWidgetId

        val pendingOpen =
            PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ appWidgetId,
                /* intent = */ open,
                /* flags = */ FLAG_IMMUTABLE
            )
        views.setPendingIntentTemplate(R.id.list, pendingOpen)

        item.setOnClickFillInIntent(R.id.image, Intent())
    }

    RemoteViewsCompat.setRemoteAdapter(
        context = context,
        remoteViews = views,
        appWidgetId = appWidgetId,
        viewId = R.id.list,
        items = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .addItem(0L, item)
            .build()
    )

    return views
}

private fun errorViews(
    context: Context,
    appWidgetId: Int
): RemoteViews =
    widgetViews(
        context = context,
        appWidgetId = appWidgetId,
        contentLayoutId = R.layout.text_error,
        contentId = R.id.text
    )

private fun timeoutViews(
    context: Context,
    appWidgetId: Int
): RemoteViews =
    widgetViews(
        context = context,
        appWidgetId = appWidgetId,
        contentLayoutId = R.layout.text_timeout,
        contentId = R.id.text
    )

private fun widgetViews(
    context: Context,
    appWidgetId: Int,
    @LayoutRes contentLayoutId: Int,
    @IdRes contentId: Int
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_main).apply {
        val relaod =
            Intent(
                /* action = */ ActionReload,
                /* uri = */ null,
                /* packageContext = */ context,
                /* cls = */ RemoteViewsWebWidget::class.java
            )
        relaod.appWidgetIdExtra = appWidgetId

        val pendingReload =
            PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ appWidgetId,
                /* intent = */ relaod,
                /* flags = */ FLAG_IMMUTABLE
            )
        setOnClickPendingIntent(R.id.reload, pendingReload)

        setViewStubLayoutResource(R.id.stub, contentLayoutId)
        setViewStubInflatedId(R.id.stub, contentId)
        setViewVisibility(R.id.stub, View.VISIBLE)
    }