package dev.gonodono.webwidgets.remoteviews

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
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.util.Size
import dev.gonodono.webwidgets.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

internal const val ActionReload = "${BuildConfig.APPLICATION_ID}.action.RELOAD"

internal inline val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(this)

internal var Intent.appWidgetIdExtra: Int
    get() = this.getIntExtra(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
    set(value) {
        this.putExtra(EXTRA_APPWIDGET_ID, value)
    }

internal fun widgetSize(context: Context, appWidgetId: Int): Size {
    val orientation = context.resources.configuration.orientation
    val options = context.appWidgetManager.getAppWidgetOptions(appWidgetId)

    val widthDp: Int
    val heightDp: Int
    if (orientation == ORIENTATION_PORTRAIT) {
        widthDp = options.getInt(OPTION_APPWIDGET_MIN_WIDTH)
        heightDp = options.getInt(OPTION_APPWIDGET_MAX_HEIGHT)
    } else {
        widthDp = options.getInt(OPTION_APPWIDGET_MAX_WIDTH)
        heightDp = options.getInt(OPTION_APPWIDGET_MIN_HEIGHT)
    }
    if (widthDp > 0 && heightDp > 0) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        return Size(width, height)
    }

    val info = context.appWidgetManager.getAppWidgetInfo(appWidgetId)
    return Size(info.minWidth, info.minHeight)
}

internal fun BroadcastReceiver.doAsync(
    coroutineContext: CoroutineContext = Dispatchers.Default,
    block: suspend CoroutineScope.() -> Unit
) {
    val scope = CoroutineScope(coroutineContext)
    val pendingResult = this.goAsync()
    scope.launch {
        try {
            try {
                coroutineScope { block() }
            } catch (_: CancellationException) {
                // No rethrow; scope is canceled anyway.
            } finally {
                scope.cancel()
            }
        } finally {
            pendingResult.finish()
        }
    }
}