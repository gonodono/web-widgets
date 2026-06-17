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
import android.content.SharedPreferences
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import dev.gonodono.webwidgets.BuildConfig
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal const val ActionReload = "${BuildConfig.APPLICATION_ID}.action.RELOAD"

internal val ReceiverTimeout = 9_500.milliseconds  // ANR at 10_000

internal var Intent.appWidgetIdExtra: Int
    get() = this.getIntExtra(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
    set(value) {
        this.putExtra(EXTRA_APPWIDGET_ID, value)
    }

internal inline val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(this)

internal fun AppWidgetManager.updateWidgets(
    appWidgetIds: IntArray,
    views: RemoteViews
) =
    appWidgetIds.forEach { this.updateAppWidget(it, views) }

internal fun busyViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_busy)

internal fun RemoteViews.showView(id: Int) =
    this.setViewVisibility(id, View.VISIBLE)


internal val Context.widgetStates: SharedPreferences
    get() = this.getSharedPreferences("widget_states", Context.MODE_PRIVATE)

internal fun getWidgetUrl(context: Context, appWidgetId: Int): String? =
    context.widgetStates.getString(urlKey(appWidgetId), null)

internal fun setWidgetUrl(context: Context, appWidgetId: Int, url: String?) =
    context.widgetStates.edit { putString(urlKey(appWidgetId), url) }

internal fun urlKey(appWidgetId: Int): String = "url:$appWidgetId"


internal fun getWidgetSizeDp(context: Context, appWidgetId: Int): Size {
    val saved = context.widgetStates.getString(sizeDpKey(appWidgetId), null)
    return Size.parseSize(saved ?: "0x0")
}

internal fun setWidgetSizeDp(context: Context, appWidgetId: Int, sizeDp: Size) =
    context.widgetStates.edit {
        putString(sizeDpKey(appWidgetId), sizeDp.toString())
    }

internal fun sizeDpKey(appWidgetId: Int): String = "size_dp:$appWidgetId"

internal fun Size.toPx(context: Context): Size {
    val density = context.resources.displayMetrics.density
    return Size(
        /* width = */ (this.width * density).roundToInt(),
        /* height = */ (this.height * density).roundToInt()
    )
}

internal fun Context.widgetSizeDpFromOptions(appWidgetId: Int): Size {
    val options = this.appWidgetManager.getAppWidgetOptions(appWidgetId)
    return options.widgetSizeDp(this.resources.configuration.orientation)
}

internal fun Bundle.widgetSizeDp(orientation: Int): Size =
    if (orientation == ORIENTATION_PORTRAIT) {
        Size(
            /* width = */ getInt(OPTION_APPWIDGET_MIN_WIDTH),
            /* height = */ getInt(OPTION_APPWIDGET_MAX_HEIGHT)
        )
    } else {
        Size(
            /* width = */ getInt(OPTION_APPWIDGET_MAX_WIDTH),
            /* height = */ getInt(OPTION_APPWIDGET_MIN_HEIGHT)
        )
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
                // No rethrow. scope is canceled anyway.
            } catch (e: Exception) {
                log("doAsync error", e)
            } finally {
                scope.cancel()
            }
        } finally {
            pendingResult.finish()
        }
    }
}