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
import android.os.Bundle
import android.util.Size
import android.widget.RemoteViews
import dev.gonodono.webwidgets.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

internal const val ActionRandom = "${BuildConfig.APPLICATION_ID}.action.RANDOM"

internal val ReceiverTimeout = 9.seconds  // ANR at 10

internal inline val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(this)

internal fun AppWidgetManager.updateWidgets(
    appWidgetIds: IntArray,
    views: RemoteViews
) =
    appWidgetIds.forEach { this.updateAppWidget(it, views) }

internal var Intent.appWidgetIdExtra: Int
    get() = this.getIntExtra(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
    set(value) {
        this.putExtra(EXTRA_APPWIDGET_ID, value)
    }

internal fun Bundle.widgetSizeDp(orientation: Int): Size =
    if (orientation == ORIENTATION_PORTRAIT) {
        val width = this.getInt(OPTION_APPWIDGET_MIN_WIDTH)
        val height = this.getInt(OPTION_APPWIDGET_MAX_HEIGHT)
        Size(width, height)
    } else {
        val width = this.getInt(OPTION_APPWIDGET_MAX_WIDTH)
        val height = this.getInt(OPTION_APPWIDGET_MIN_HEIGHT)
        Size(width, height)
    }

internal fun Size/*Dp*/.toPx(context: Context): Size {
    val density = context.resources.displayMetrics.density
    val widthPx = (this.width * density).roundToInt()
    val heightPx = (this.height * density).roundToInt()
    return Size(widthPx, heightPx)
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