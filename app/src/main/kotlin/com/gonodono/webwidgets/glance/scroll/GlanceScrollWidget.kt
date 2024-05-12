package com.gonodono.webwidgets.glance.scroll

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDefaults
import androidx.glance.unit.ColorProvider
import com.gonodono.webwidgets.WebShooter
import com.gonodono.webwidgets.glance.BaseGlanceWidget
import com.gonodono.webwidgets.glance.BaseGlanceWidgetReceiver
import com.gonodono.webwidgets.glance.WebLinkImage

private class GlanceScrollWidget : BaseGlanceWidget() {

    override val imageHeightFitsWidget: Boolean = false

    @Composable
    override fun Content(context: Context, webShot: WebShooter.WebShot) {
        LazyColumn {
            item {
                when {
                    webShot.overflows -> OverflowImage(context, webShot)
                    else -> WebLinkImage(
                        context = context,
                        webShot = webShot,
                        receiver = GlanceScrollWidgetReceiver::class.java
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowImage(
    context: Context,
    webShot: WebShooter.WebShot
) {
    Box(contentAlignment = Alignment.BottomCenter) {
        WebLinkImage(
            context = context,
            webShot = webShot,
            receiver = GlanceScrollWidgetReceiver::class.java
        )
        Text(
            text = "Continues…",
            style = TextDefaults.defaultTextStyle.copy(
                color = ColorProvider(Color.Blue),
                textAlign = TextAlign.Center,
                fontSize = 22.sp
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0xE8FFFFFF))
                .padding(top = 2.dp, bottom = 2.dp)
        )
    }
}

class GlanceScrollWidgetReceiver : BaseGlanceWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceScrollWidget()
}