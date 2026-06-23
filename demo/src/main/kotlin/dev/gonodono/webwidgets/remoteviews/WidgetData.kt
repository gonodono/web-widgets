package dev.gonodono.webwidgets.remoteviews

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Size
import androidx.core.content.edit

internal inline val Context.widgetData: WidgetData get() = WidgetData(this)

@JvmInline
internal value class WidgetData
private constructor(val sp: SharedPreferences) {

    constructor(context: Context) :
            this(context.getSharedPreferences("widget_data", MODE_PRIVATE))

    fun getSizeDp(appWidgetId: Int): Size =
        Size.parseSize(sp.getString(appWidgetId.sizeDpKey, "0x0"))

    fun putSizeDp(appWidgetId: Int, sizeDp: Size): Unit =
        sp.edit { putString(appWidgetId.sizeDpKey, sizeDp.toString()) }

    fun removeSizeDps(appWidgetIds: IntArray): Unit =
        sp.edit { appWidgetIds.forEach { remove(it.sizeDpKey) } }
}

private inline val Int.sizeDpKey: String get() = "size_dp:${this}"