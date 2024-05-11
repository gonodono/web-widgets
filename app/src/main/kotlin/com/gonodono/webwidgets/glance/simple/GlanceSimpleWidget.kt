package com.gonodono.webwidgets.glance.simple

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.appwidget.GlanceAppWidget
import com.gonodono.webwidgets.WebShooter.WebShot
import com.gonodono.webwidgets.glance.BaseGlanceWidget
import com.gonodono.webwidgets.glance.BaseGlanceWidgetReceiver
import com.gonodono.webwidgets.glance.WebLinkImage

private class GlanceSimpleWidget : BaseGlanceWidget() {

    override val imageHeightFitsWidget: Boolean = true

    @Composable
    override fun Content(context: Context, webShot: WebShot) {
        WebLinkImage(
            context = context,
            webShot = webShot,
            receiver = GlanceSimpleWidgetReceiver::class.java
        )
    }
}

class GlanceSimpleWidgetReceiver : BaseGlanceWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceSimpleWidget()
}