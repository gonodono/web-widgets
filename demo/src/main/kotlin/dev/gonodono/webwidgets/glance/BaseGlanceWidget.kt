package dev.gonodono.webwidgets.glance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
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
import dev.gonodono.webwidgets.shooter.createWebShooter
import dev.gonodono.webwidgets.takeShotForAppWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import android.util.Size as AndroidSize

internal abstract class BaseGlanceWidget(private val clampImageHeightToWidget: Boolean) :
    GlanceAppWidget() {

    @Composable
    protected abstract fun Content(webShot: WebShot.Complete)

    private sealed interface State {
        data object Busy : State
        data class Complete(val webShot: WebShot.Complete) : State
        data object Timeout : State
        data object Error : State
    }

    private val scope = CoroutineScope(SupervisorJob())

    private var widgetState: State by mutableStateOf(State.Busy)

    private var webShooter: WebShooter? = null

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (webShooter == null) webShooter = createWebShooter(context)

        // The demo assumes that the host is a basic launcher that handles only
        // portrait and landscape configurations. provideGlance() is called for
        // each size for every update, and the results are combined into one
        // RemoteViews. If we were to provide full size scroll images for both
        // orientations, they could exceed the maximum limit for an update, so
        // the demo provides actual content for only the current orientation.
        val orientation = context.resources.configuration.orientation
        val isDevicePortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        provideContent {
            val isWidgetPortrait = LocalSize.current.run { width <= height }
            MainContent(isWidgetPortrait == isDevicePortrait)
        }
    }

    @Composable
    private fun MainContent(isProvidingCurrentOrientation: Boolean) {
        val state = widgetState
        val context = LocalContext.current
        val size = with(Density(context)) { LocalSize.current.toSize() }

        Box(
            contentAlignment =
                if (state == State.Busy) Alignment.Center
                else Alignment.BottomEnd,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .appWidgetBackground()
        ) {
            if (isProvidingCurrentOrientation) {
                when (state) {
                    State.Busy -> {
                        CircularProgressIndicator()
                        LaunchedEffect(size) { update(size) }
                        return@Box
                    }
                    is State.Complete -> Content(state.webShot)
                    State.Timeout -> TimeoutMessage()
                    State.Error -> ErrorMessage()
                }
            } else {
                CircularProgressIndicator()
            }

            RandomButton { update(size) }
        }
    }

    private var job: Job? = null

    private fun update(size: Size) {
        job?.cancel()

        val shooter = checkNotNull(webShooter) { "Mising WebShooter" }

        job = scope.launch {
            widgetState = State.Busy

            val width = size.width.fastRoundToInt()
            val height = size.height.fastRoundToInt()
            val targetSize = AndroidSize(width, height)

            val webShot =
                withTimeoutOrNull(GlanceTimeout) {
                    shooter.takeShotForAppWidget(
                        url = WikipediaRandomPageUrl,
                        targetSize = targetSize,
                        clampHeightToTarget = clampImageHeightToWidget
                    )
                }

            ensureActive()

            widgetState =
                when (webShot) {
                    is WebShot.Complete -> State.Complete(webShot)
                    is WebShot.Error -> State.Error
                    null -> State.Timeout
                }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        webShooter?.close()
        scope.cancel()
    }

    abstract class Receiver : GlanceAppWidgetReceiver() {

        override fun onReceive(context: Context, intent: Intent) =
            if (intent.action == ActionOpen) {
                handleActionOpen(context, intent)
            } else {
                super.onReceive(context, intent)
            }
    }
}

private val GlanceTimeout = 40.seconds  // Max at ~45

@Composable
internal fun WebLinkImage(
    receiver: Class<out BaseGlanceWidget.Receiver>,
    webShot: WebShot.Complete
) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(webShot.bitmap),
        contentDescription = "WebShot",
        modifier = GlanceModifier.run {
            if (webShot.url != null) {
                val uri = webShot.url.toUri()
                val open = Intent(ActionOpen, uri, context, receiver)
                clickable(actionSendBroadcast(open))
            } else {
                this
            }
        }
    )
}

@Composable
private fun TimeoutMessage() =
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Timeout",
            style = TextDefaults.defaultTextStyle.copy(
                color = Color.Magenta.toColorProvider(),
                fontSize = 22.sp
            )
        )
    }

@Composable
private fun ErrorMessage() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Error",
            style = TextDefaults.defaultTextStyle.copy(
                color = Color.Red.toColorProvider(),
                fontSize = 22.sp
            )
        )
    }
}

@Composable
private fun RandomButton(onClick: () -> Unit) =
    Image(
        provider = ImageProvider(R.drawable.random),
        contentDescription = "Random",
        modifier = GlanceModifier
            .padding(12.dp)
            .background(ImageProvider(R.drawable.circle))
            .clickable(onClick)
    )

@Suppress("NOTHING_TO_INLINE")
private inline fun Color.toColorProvider(): ColorProvider =
    @SuppressLint("RestrictedApi") ColorProvider(this)