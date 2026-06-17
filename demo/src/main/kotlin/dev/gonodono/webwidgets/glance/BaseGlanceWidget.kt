package dev.gonodono.webwidgets.glance

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import androidx.glance.unit.ColorProvider
import dev.gonodono.webwidgets.ActionOpen
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.WikipediaRandomPageUrl
import dev.gonodono.webwidgets.handleActionOpen
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import dev.gonodono.webwidgets.takeShotForAppWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.util.Size as AndroidSize

internal abstract class BaseGlanceWidget : GlanceAppWidget() {

    protected abstract val imageHeightFitsWidget: Boolean

    @Composable
    protected abstract fun Content(webShot: WebShot)

    private sealed interface State {
        data object Loading : State
        data class Complete(val webShot: WebShot) : State
        data object Timeout : State
    }

    private val scope = CoroutineScope(SupervisorJob())

    private var widgetState by mutableStateOf<State>(State.Loading)

    private var webShooter: WebShooter? = null

    private var url: String? = null

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (webShooter == null) webShooter = WebShooter.create(context)

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
        val state = widgetState
        val context = LocalContext.current
        val size = with(Density(context)) { LocalSize.current.toSize() }
            .run { AndroidSize(width.roundToInt(), height.roundToInt()) }

        Box(
            contentAlignment =
                if (state == State.Loading) Alignment.Center
                else Alignment.BottomEnd,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .appWidgetBackground()
        ) {
            when (state) {
                is State.Complete -> Content(state.webShot)
                State.Loading -> CircularProgressIndicator()
                State.Timeout -> TimeoutMessage()
            }
            if (state !is State.Loading) {
                ReloadButton { url = null; update(size) }
            }
        }

        LaunchedEffect(Unit) { update(size) }
    }

    private fun update(size: AndroidSize) {
        val shooter = checkNotNull(webShooter) { "Mising WebShooter" }

        scope.launch {
            widgetState = State.Loading

            val result =
                withTimeoutOrNull(GlanceTimeout) {
                    shooter.takeShotForAppWidget(
                        url = url ?: WikipediaRandomPageUrl,
                        targetSize = size,
                        fitTargetHeight = imageHeightFitsWidget
                    )
                }

            widgetState =
                if (result != null) {
                    url = result.url
                    State.Complete(result)
                } else {
                    State.Timeout
                }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        webShooter?.close()
        scope.cancel()
    }
}

abstract class BaseGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onReceive(context: Context, intent: Intent) =
        if (intent.action == ActionOpen) {
            handleActionOpen(context, intent)
        } else {
            super.onReceive(context, intent)
        }
}

internal val GlanceTimeout = 40_000.milliseconds  // Max at ~45_000

@Composable
internal fun WebLinkImage(
    webShot: WebShot,
    receiver: Class<out BroadcastReceiver>
) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(webShot.bitmap),
        contentDescription = "WebShot",
        modifier = GlanceModifier.run {
            if (webShot.url != null) {
                val intent =
                    Intent(ActionOpen, webShot.url.toUri(), context, receiver)
                clickable(actionSendBroadcast(intent))
            } else {
                this
            }
        }
    )
}

@Composable
internal fun TimeoutMessage() =
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Timeout",
            style = TextDefaults.defaultTextStyle.copy(
                color = @SuppressLint("RestrictedApi") ColorProvider(Color.Magenta),
                fontSize = 22.sp
            )
        )
    }

@Composable
private fun ReloadButton(onClick: () -> Unit) =
    Image(
        provider = ImageProvider(R.drawable.reload),
        contentDescription = "Reload",
        modifier = GlanceModifier
            .padding(12.dp)
            .background(ImageProvider(R.drawable.circle))
            .clickable(onClick)
    )