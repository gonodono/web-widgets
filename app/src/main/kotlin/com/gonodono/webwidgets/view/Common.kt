package com.gonodono.webwidgets.view

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import com.gonodono.webwidgets.BuildConfig
import com.gonodono.webwidgets.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal const val ACTION_RELOAD = "${BuildConfig.APPLICATION_ID}.action.RELOAD"

internal const val RECEIVER_TIMEOUT = 9_500L  // ANR at 10_000L


internal inline var Intent.appWidgetIdExtra: Int
    get() = getIntExtra(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
    set(value) {
        putExtra(EXTRA_APPWIDGET_ID, value)
    }

internal inline val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(this)

internal fun Context.widgetSize(appWidgetId: Int): Size {
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val density = resources.displayMetrics.density
    val isPortrait = resources.configuration.orientation == ORIENTATION_PORTRAIT
    return when {
        isPortrait -> portraitSize(options, density)
        else -> landscapeSize(options, density)
    }
}

private fun portraitSize(bundle: Bundle, density: Float): Size {
    val width = bundle.getInt(OPTION_APPWIDGET_MIN_WIDTH)
    val height = bundle.getInt(OPTION_APPWIDGET_MAX_HEIGHT)
    return Size((width * density).toInt(), (height * density).toInt())
}

private fun landscapeSize(bundle: Bundle, density: Float): Size {
    val width = bundle.getInt(OPTION_APPWIDGET_MAX_WIDTH)
    val height = bundle.getInt(OPTION_APPWIDGET_MIN_HEIGHT)
    return Size((width * density).toInt(), (height * density).toInt())
}


internal fun busyViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_busy)

internal fun RemoteViews.show(id: Int) = setViewVisibility(id, View.VISIBLE)

internal fun AppWidgetManager.updateAppWidgets(
    appWidgetIds: IntArray,
    views: RemoteViews
) {
    appWidgetIds.forEach { id -> updateAppWidget(id, views) }
}


internal inline val Context.widgetStates: SharedPreferences
    get() = getSharedPreferences("web_widget_states", Context.MODE_PRIVATE)

internal fun getUrl(context: Context, appWidgetId: Int) =
    context.widgetStates.getString(urlKey(appWidgetId), null)

internal fun setUrl(context: Context, appWidgetId: Int, url: String?) {
    context.widgetStates.edit().putString(urlKey(appWidgetId), url).apply()
}

internal fun urlKey(appWidgetId: Int) = "url:$appWidgetId"


internal fun BroadcastReceiver.doAsync(block: suspend () -> Unit) {
    val scope = CoroutineScope(SupervisorJob())
    val result = goAsync()
    scope.launch {
        try {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } finally {
                scope.cancel()
            }
        } finally {
            result.finish()
        }
    }
}