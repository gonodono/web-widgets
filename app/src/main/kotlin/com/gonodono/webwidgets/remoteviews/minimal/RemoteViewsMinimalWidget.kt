package com.gonodono.webwidgets.remoteviews.minimal

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
import com.gonodono.webwidgets.remoteviews.RECEIVER_TIMEOUT
import com.gonodono.webwidgets.remoteviews.busyViews
import com.gonodono.webwidgets.remoteviews.doAsync
import com.gonodono.webwidgets.remoteviews.show
import com.gonodono.webwidgets.remoteviews.updateAppWidgets
import com.gonodono.webwidgets.removeFromWindowManager
import com.gonodono.webwidgets.screenSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class RemoteViewsMinimalWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        doAsync { updateWidgets(context, appWidgetManager, appWidgetIds) }
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetManager.updateAppWidgets(appWidgetIds, busyViews(context))

        val views = RemoteViews(context.packageName, R.layout.widget_simple)
        withTimeoutOrNull(RECEIVER_TIMEOUT) {
            val frameLayout = FrameLayout(context)
            if (frameLayout.addToWindowManager()) {
                try {
                    val webView = withContext(Dispatchers.Main) {
                        WebView(context).also { frameLayout.addView(it) }
                    }
                    webView.awaitLoadUrl(WIKIPEDIA_RANDOM_URL)
                    val size = context.screenSize()
                    webView.awaitLayout(size.width, size.height / 2)
                    val bitmap = webView.drawToBitmap()
                    views.setImageViewBitmap(R.id.image, bitmap)
                    views.show(R.id.image)
                } finally {
                    withContext(NonCancellable) {
                        frameLayout.removeFromWindowManager()
                    }
                }
            } else {
                views.show(R.id.error)
            }
        } ?: views.show(R.id.timeout)

        appWidgetManager.updateAppWidgets(appWidgetIds, views)
    }
}