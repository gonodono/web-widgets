package dev.gonodono.webwidgets.glance

import androidx.compose.runtime.Composable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import dev.gonodono.webwidgets.shooter.WebShot

private class GlanceScrollWidget :
    BaseGlanceWidget(clampImageHeightToWidget = false) {

    class Receiver : BaseGlanceWidget.Receiver() {
        override val glanceAppWidget: GlanceAppWidget = GlanceScrollWidget()
    }

    @Composable
    override fun Content(webShot: WebShot.Complete) =
        LazyColumn { item { WebLinkImage(Receiver::class.java, webShot) } }
}