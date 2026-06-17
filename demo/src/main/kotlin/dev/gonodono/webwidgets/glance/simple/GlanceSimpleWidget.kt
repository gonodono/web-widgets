package dev.gonodono.webwidgets.glance.simple

import androidx.compose.runtime.Composable
import androidx.glance.appwidget.GlanceAppWidget
import dev.gonodono.webwidgets.glance.BaseGlanceWidget
import dev.gonodono.webwidgets.glance.BaseGlanceWidgetReceiver
import dev.gonodono.webwidgets.glance.WebLinkImage
import dev.gonodono.webwidgets.shooter.WebShot

private class GlanceSimpleWidget : BaseGlanceWidget() {

    override val imageHeightFitsWidget: Boolean = true

    @Composable
    override fun Content(webShot: WebShot) =
        WebLinkImage(webShot, GlanceSimpleWidgetReceiver::class.java)
}

class GlanceSimpleWidgetReceiver : BaseGlanceWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceSimpleWidget()
}