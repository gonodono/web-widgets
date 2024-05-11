package com.gonodono.webwidgets.view.minimal

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.view.drawToBitmap
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.addToWindowManager
import com.gonodono.webwidgets.awaitLayout
import com.gonodono.webwidgets.awaitLoadUrl
import com.gonodono.webwidgets.removeFromWindowManager
import com.gonodono.webwidgets.screenSize
import com.gonodono.webwidgets.view.RECEIVER_TIMEOUT
import com.gonodono.webwidgets.view.doAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ViewMinimalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val busyViews = RemoteViews(context.packageName, R.layout.widget_busy)
        updateViews(appWidgetManager, appWidgetIds, busyViews)
        doAsync { updateWidgets(context, appWidgetManager, appWidgetIds) }
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val bitmap = withTimeoutOrNull(RECEIVER_TIMEOUT) {
            val frameLayout = FrameLayout(context)
            when {
                frameLayout.addToWindowManager() -> try {
                    val webView = withContext(Dispatchers.Main) {
                        WebView(context).also { frameLayout.addView(it) }
                    }
                    webView.awaitLoadUrl(WIKIPEDIA_RANDOM_URL)
                    val size = context.screenSize()
                    webView.awaitLayout(size.width, size.height / 2)
                    webView.drawToBitmap()
                } finally {
                    frameLayout.removeFromWindowManager()
                }

                else -> null
            }
        }
        val views = if (bitmap != null) {
            RemoteViews(context.packageName, R.layout.widget_minimal)
                .apply { setImageViewBitmap(R.id.image, bitmap) }
        } else {
            RemoteViews(context.packageName, R.layout.widget_text)
                .apply { setTextViewText(R.id.text, "Error") }
        }
        updateViews(appWidgetManager, appWidgetIds, views)
    }

    private fun updateViews(
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        rv: RemoteViews
    ) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, rv) }
    }
}