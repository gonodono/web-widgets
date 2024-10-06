package com.gonodono.webwidgets.glance.minimal

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.drawToBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.addToWindowManager
import com.gonodono.webwidgets.awaitLayout
import com.gonodono.webwidgets.awaitLoadUrl
import com.gonodono.webwidgets.glance.ErrorMessage
import com.gonodono.webwidgets.glance.GLANCE_TIMEOUT
import com.gonodono.webwidgets.glance.TimeoutMessage
import com.gonodono.webwidgets.removeFromWindowManager
import com.gonodono.webwidgets.screenSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private class GlanceMinimalWidget : GlanceAppWidget() {

    private sealed interface State {
        data object Loading : State
        data class Complete(val bitmap: Bitmap) : State
        data object Timeout : State
        data object Error : State
    }

    private var widgetState by mutableStateOf<State>(State.Loading)

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier.fillMaxSize().background(Color.White)
            ) {
                when (val state = widgetState) {
                    State.Loading -> CircularProgressIndicator()
                    is State.Complete -> Image(
                        provider = ImageProvider(state.bitmap),
                        contentDescription = "WebShot"
                    )
                    State.Timeout -> TimeoutMessage()
                    State.Error -> ErrorMessage()
                }
            }
            LaunchedEffect(Unit) { update(context) }
        }
    }

    private suspend fun update(context: Context) {
        widgetState = withTimeoutOrNull(GLANCE_TIMEOUT) {
            val frameLayout = FrameLayout(context)
            when {
                frameLayout.addToWindowManager() -> try {
                    val webView = withContext(Dispatchers.Main) {
                        WebView(context).also { frameLayout.addView(it) }
                    }
                    webView.awaitLoadUrl(WIKIPEDIA_RANDOM_URL)
                    val size = context.screenSize()
                    webView.awaitLayout(size.width, size.height / 2)
                    val bitmap = webView.drawToBitmap()
                    State.Complete(bitmap)
                } finally {
                    frameLayout.removeFromWindowManager()
                }

                else -> State.Error
            }
        } ?: State.Timeout
    }
}

class GlanceMinimalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceMinimalWidget()
}