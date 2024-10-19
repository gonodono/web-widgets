package com.gonodono.webwidgets.remoteviews.scroll

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import com.gonodono.webwidgets.BuildConfig
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.TAG
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.WebShooter
import com.gonodono.webwidgets.remoteviews.appWidgetIdExtra
import com.gonodono.webwidgets.remoteviews.appWidgetManager
import com.gonodono.webwidgets.remoteviews.getUrl
import com.gonodono.webwidgets.remoteviews.setUrl
import com.gonodono.webwidgets.remoteviews.widgetSize
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private class RemoteViewsScrollFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val webShooter = WebShooter(context)

    private var listItem: RemoteViews? = null

    private var currentSize = Size(0, 0)

    override fun onCreate() {
        webShooter.initializeBlocking()
    }

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

        val size = context.widgetSize(appWidgetId)
        if (listItem != null && currentSize == size) return

        val result = runBlocking {
            withTimeoutOrNull(SERVICE_TIMEOUT) {

                // Apparently RemoteViews straight from a collection Factory
                // aren't checked for the max allowed image size. Might want
                // to take extra precautions if using a bigger size in prod.
                webShooter.takeShot(url, size, false, 2.0F)
            }
        }
        listItem = when (result) {
            is WebShooter.WebShot -> {
                setUrl(context, appWidgetId, result.url)
                itemViews(context, result, appWidgetId)
            }
            is WebShooter.Error -> {
                if (BuildConfig.DEBUG) Log.e(TAG, result.message)
                errorViews(context)
            }
            null -> timeoutViews(context)
        }
        currentSize = size
    }

    override fun getCount(): Int = if (listItem != null) 1 else 0

    override fun getViewAt(position: Int): RemoteViews = listItem!!

    override fun getLoadingView(): RemoteViews = busyViews(context)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getViewTypeCount(): Int = 1

    override fun onDestroy() {}
}

class RemoteViewsScrollService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RemoteViewsScrollFactory(applicationContext, intent.appWidgetIdExtra)
}

private fun itemViews(
    context: Context,
    webShot: WebShooter.WebShot,
    appWidgetId: Int
) = RemoteViews(context.packageName, R.layout.widget_scroll_item).apply {
    setImageViewBitmap(R.id.image, webShot.bitmap)
    val click = Intent().setData(webShot.url.toUri())
    click.appWidgetIdExtra = appWidgetId
    setOnClickFillInIntent(R.id.image, click)
    if (webShot.overflows) setViewVisibility(R.id.continues, View.VISIBLE)
}

private fun busyViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_scroll_busy)

private fun timeoutViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_scroll_text).apply {
        setTextViewText(R.id.text, context.getText(R.string.timeout))
    }

private fun errorViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_scroll_text).apply {
        setTextViewText(R.id.text, context.getText(R.string.error))
    }

private const val SERVICE_TIMEOUT = 40_000L  // Matches GLANCE_TIMEOUT