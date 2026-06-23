package dev.gonodono.webwidgets.glance

import androidx.compose.runtime.Composable
import androidx.glance.appwidget.GlanceAppWidget
import dev.gonodono.webwidgets.shooter.WebShot

private class GlanceSimpleWidget :
    BaseGlanceWidget(clampImageHeightToWidget = true) {

    class Receiver : BaseGlanceWidget.Receiver() {
        override val glanceAppWidget: GlanceAppWidget = GlanceSimpleWidget()
    }

    @Composable
    override fun Content(webShot: WebShot.Complete) =
        WebLinkImage(Receiver::class.java, webShot)
}