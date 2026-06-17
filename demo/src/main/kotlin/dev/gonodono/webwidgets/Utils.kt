package dev.gonodono.webwidgets

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.core.content.edit
import androidx.window.layout.WindowMetricsCalculator

internal const val WikipediaRandomPageUrl =
    "https://en.wikipedia.org/wiki/Special:Random"

internal fun log(message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) Log.d("WebWidgets", message, throwable)
}

internal const val ActionOpen = "${BuildConfig.APPLICATION_ID}.action.OPEN"

internal fun handleActionOpen(context: Context, intent: Intent) {
    try {
        val view = Intent(Intent.ACTION_VIEW, intent.data!!)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
    } catch (e: Exception) {
        log("ACTION_VIEW error", e)
    }
}

internal fun checkIsMainThread() =
    check(Thread.currentThread() == Looper.getMainLooper().thread) {
        "Current thread is not main"
    }

internal fun Context.screenSize(): Size =
    WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this)
        .let { metrics -> metrics.bounds.run { Size(width(), height()) } }

internal inline val Context.appSettings: AppSettings get() = AppSettings(this)

@JvmInline
internal value class AppSettings
private constructor(val sp: SharedPreferences) {

    constructor(context: Context) :
            this(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

    var useVirtualWebShooter: Boolean
        get() = sp.getBoolean(UseVirtualWebShooter, true)
        set(value) = sp.edit { putBoolean(UseVirtualWebShooter, value) }

    var drawTypeLabel: Boolean
        get() = sp.getBoolean(DrawTypeLabel, true)
        set(value) = sp.edit { putBoolean(DrawTypeLabel, value) }
}

private const val UseVirtualWebShooter = "use_virtual_web_shooter"
private const val DrawTypeLabel = "draw_type_label"