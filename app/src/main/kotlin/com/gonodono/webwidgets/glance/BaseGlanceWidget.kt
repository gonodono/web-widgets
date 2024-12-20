package com.gonodono.webwidgets.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import androidx.glance.unit.ColorProvider
import com.gonodono.webwidgets.ACTION_OPEN
import com.gonodono.webwidgets.BuildConfig
import com.gonodono.webwidgets.R
import com.gonodono.webwidgets.TAG
import com.gonodono.webwidgets.WIKIPEDIA_RANDOM_URL
import com.gonodono.webwidgets.WebShooter
import com.gonodono.webwidgets.handleActionOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Size as AndroidSize

internal abstract class BaseGlanceWidget : GlanceAppWidget() {

    protected abstract val imageHeightFitsWidget: Boolean

    @Composable
    protected abstract fun Content(webShot: WebShooter.WebShot)

    private sealed interface State {
        data object Loading : State
        data class Complete(val webShot: WebShooter.WebShot) : State
        data object Timeout : State
        data object Error : State
    }

    private val scope = CoroutineScope(SupervisorJob())

    private var widgetState by mutableStateOf<State>(State.Loading)

    private lateinit var webShooter: WebShooter

    private var url: String? = null

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (!::webShooter.isInitialized) {
            webShooter = WebShooter(context).apply { initialize() }
        }

        // The demo assumes that the host is a basic launcher that handles only
        // portrait and landscape configurations. provideGlance() is called for
        // each size for every update, and the results are combined into one
        // RemoteViews. If we were to provide full size scroll images for both
        // orientations, they could exceed the maximum limit for an update, so
        // the demo provides actual content for only the current orientation.
        val orientation = context.resources.configuration.orientation
        val isDevicePortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        provideContent {
            val isWidgetPortrait = LocalSize.current.run { width < height }
            if (isWidgetPortrait == isDevicePortrait) {
                MainContent()
            } else {
                CircularProgressIndicator()
            }
        }
    }

    @Composable
    private fun MainContent() {
        val context = LocalContext.current
        val size = with(Density(context)) { LocalSize.current.toSize() }
            .run { AndroidSize(width.toInt(), height.toInt()) }

        Box(
            contentAlignment = when (widgetState) {
                State.Loading -> Alignment.Center
                else -> Alignment.BottomEnd
            },
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .appWidgetBackground()
        ) {
            val state = widgetState
            when (state) {
                State.Loading -> CircularProgressIndicator()
                is State.Complete -> Content(state.webShot)
                State.Timeout -> TimeoutMessage()
                else -> ErrorMessage()
            }
            if (state !is State.Loading && webShooter.canDraw) {
                ReloadButton { url = null; update(size) }
            }
        }

        LaunchedEffect(Unit) { update(size) }
    }

    private fun update(size: AndroidSize) {
        scope.launch {
            if (webShooter.canDraw) {
                widgetState = State.Loading
                val result = withTimeoutOrNull(GLANCE_TIMEOUT) {
                    webShooter.takeShot(
                        url ?: WIKIPEDIA_RANDOM_URL,
                        size,
                        imageHeightFitsWidget
                    )
                }
                widgetState = when (result) {
                    is WebShooter.WebShot -> {
                        url = result.url
                        State.Complete(result)
                    }
                    is WebShooter.Error -> {
                        if (BuildConfig.DEBUG) Log.e(TAG, result.message)
                        State.Error
                    }
                    null -> State.Timeout
                }
            } else {
                widgetState = State.Error
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        scope.cancel()
    }
}

abstract class BaseGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_OPEN) {
            handleActionOpen(context, intent)
        } else {
            super.onReceive(context, intent)
        }
    }
}

internal const val GLANCE_TIMEOUT = 40_000L  // Max at ~45_000L

@Composable
internal fun WebLinkImage(
    webShot: WebShooter.WebShot,
    receiver: Class<out BroadcastReceiver>
) {
    val context = LocalContext.current
    val intent = Intent(ACTION_OPEN, webShot.url.toUri(), context, receiver)
    Image(
        provider = ImageProvider(webShot.bitmap),
        contentDescription = "WebShot",
        modifier = GlanceModifier.clickable(actionSendBroadcast(intent))
    )
}

@Composable
internal fun TimeoutMessage() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Timeout",
            style = TextDefaults.defaultTextStyle.copy(
                color = ColorProvider(Color.Magenta),
                fontSize = 22.sp
            )
        )
    }
}

@Composable
internal fun ErrorMessage() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Error",
            style = TextDefaults.defaultTextStyle.copy(
                color = ColorProvider(Color.Red),
                fontSize = 22.sp
            )
        )
    }
}

@Composable
private fun ReloadButton(onClick: () -> Unit) {
    Box(modifier = GlanceModifier.padding(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .size(40.dp)
                .background(ImageProvider(R.drawable.circle))
                .clickable(onClick)
        ) {
            Image(
                provider = ImageProvider(R.drawable.reload),
                contentDescription = "Reload"
            )
        }
    }
}