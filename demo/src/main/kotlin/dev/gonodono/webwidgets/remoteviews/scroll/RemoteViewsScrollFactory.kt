package dev.gonodono.webwidgets.remoteviews.scroll

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Size
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.WikipediaRandomPageUrl
import dev.gonodono.webwidgets.remoteviews.appWidgetIdExtra
import dev.gonodono.webwidgets.remoteviews.appWidgetManager
import dev.gonodono.webwidgets.remoteviews.getWidgetUrl
import dev.gonodono.webwidgets.remoteviews.setWidgetUrl
import dev.gonodono.webwidgets.remoteviews.showView
import dev.gonodono.webwidgets.remoteviews.toPx
import dev.gonodono.webwidgets.remoteviews.widgetSizeDpFromOptions
import dev.gonodono.webwidgets.screenSize
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import dev.gonodono.webwidgets.takeShotForAppWidget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private class RemoteViewsScrollFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private lateinit var webShooter: WebShooter

    private var listItem: RemoteViews? = null

    private var currentSizeDp = Size(0, 0)

    override fun onCreate() {
        webShooter = WebShooter.createBlocking(context)
    }

    override fun onDataSetChanged() {
        val url = getWidgetUrl(context, appWidgetId)
        if (url == null) {
            setWidgetUrl(context, appWidgetId, WikipediaRandomPageUrl)
            listItem = null
            context.appWidgetManager.notifyListChanged(appWidgetId)
            return
        }

        val sizeDp = context.widgetSizeDpFromOptions(appWidgetId)
        if (listItem != null && currentSizeDp == sizeDp) return

        val result = runBlocking {
            withTimeoutOrNull(ServiceTimeout) {

                // Apparently RemoteViews straight from a collection Factory
                // aren't checked for the max allowed image size. Might want to
                // take extra precautions if using a multiplier > 1.5F in prod.
                // Or just use a FileProvider instead, like a sane person.
                webShooter.takeShotForAppWidget(
                    url = url,
                    screenSize = context.screenSize(),
                    targetSize = sizeDp.toPx(context),
                    fitTargetHeight = false,
                    screenAreaMultiplier = @SuppressLint("Range") 2.0F
                )
            }
        }

        listItem =
            if (result != null) {
                setWidgetUrl(context, appWidgetId, result.url)
                itemViews(context, result, appWidgetId)
            } else {
                timeoutViews(context)
            }

        currentSizeDp = sizeDp
    }

    override fun getCount(): Int = if (listItem != null) 1 else 0

    override fun getViewAt(position: Int): RemoteViews = listItem!!

    override fun getLoadingView(): RemoteViews = busyViews(context)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getViewTypeCount(): Int = 1

    override fun onDestroy() = webShooter.close()
}

class RemoteViewsScrollService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RemoteViewsScrollFactory(applicationContext, intent.appWidgetIdExtra)
}

private fun itemViews(
    context: Context,
    webShot: WebShot,
    appWidgetId: Int
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_scroll_item).apply {
        setImageViewBitmap(R.id.image, webShot.bitmap)
        if (webShot.url != null) {
            val click = Intent().setData(webShot.url.toUri())
            click.appWidgetIdExtra = appWidgetId
            setOnClickFillInIntent(R.id.image, click)
        }
        if (webShot.overflows) showView(R.id.continues)
    }

private fun busyViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_scroll_busy)

private fun timeoutViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_scroll_text)
        .apply { setTextViewText(R.id.text, context.getText(R.string.timeout)) }

private val ServiceTimeout = 40_000.milliseconds  // Matches GlanceTimeout