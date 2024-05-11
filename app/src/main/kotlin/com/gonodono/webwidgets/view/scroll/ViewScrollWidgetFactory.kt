package com.gonodono.webwidgets.view.scroll

import android.content.Context
import android.content.Intent
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.WebShooter
import com.gonodono.webwidgets.view.SERVICE_TIMEOUT
import com.gonodono.webwidgets.view.appWidgetIdExtra
import com.gonodono.webwidgets.view.appWidgetManager
import com.gonodono.webwidgets.view.busyViews
import com.gonodono.webwidgets.view.errorViews
import com.gonodono.webwidgets.view.getUrl
import com.gonodono.webwidgets.view.setUrl
import com.gonodono.webwidgets.view.widgetSize
import kotlinx.coroutines.runBlocking

private class ViewScrollWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val webShooter = WebShooter(context)

    override fun onCreate() {
        webShooter.initialize()
    }

    private var listItem: RemoteViews? = null

    private var currentSize = Size(0, 0)

    override fun onDataSetChanged() {
        if (!webShooter.canDraw) {
            listItem = errorViews(context)
            return
        }

        val url = getUrl(context, appWidgetId)
        if (url == null) {
            setUrl(context, appWidgetId, WIKIPEDIA_RANDOM_URL)
            listItem = null
            context.appWidgetManager.notifyListChanged(appWidgetId)
            return
        }

        val newSize = context.widgetSize(appWidgetId)
        if (listItem == null || currentSize != newSize) {
            val webShot = runBlocking {
                // Apparently RemoteViews straight out of a collection Factory
                // aren't checked for the max allowed image size. Might want to
                // take extra precautions if using a bigger size in production.
                webShooter.takeShot(url, SERVICE_TIMEOUT, newSize, false, 2.0F)
            }
            listItem = when {
                webShot != null -> {
                    setUrl(context, appWidgetId, webShot.url)
                    itemViews(context, webShot, appWidgetId)
                }

                else -> timeoutViews(context)
            }
            currentSize = newSize
        }
    }

    override fun getCount(): Int = if (listItem != null) 1 else 0

    override fun getViewAt(position: Int): RemoteViews = listItem!!

    override fun getLoadingView(): RemoteViews = busyViews(context)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getViewTypeCount(): Int = 1

    override fun onDestroy() {}
}

class ViewScrollWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ViewScrollWidgetFactory(applicationContext, intent.appWidgetIdExtra)
}

private fun itemViews(
    context: Context,
    webShot: WebShooter.WebShot,
    appWidgetId: Int
) = RemoteViews(context.packageName, R.layout.scroll_widget_item).apply {
    setImageViewBitmap(R.id.image, webShot.bitmap)
    val click = Intent().setData(webShot.url.toUri())
    click.appWidgetIdExtra = appWidgetId
    setOnClickFillInIntent(R.id.image, click)
    if (webShot.overflows) setViewVisibility(R.id.continues, View.VISIBLE)
}

private fun timeoutViews(context: Context) =
    RemoteViews(context.packageName, R.layout.widget_text)
        .apply { setTextViewText(R.id.text, "Timeout") }