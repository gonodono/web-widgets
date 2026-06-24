package dev.gonodono.webwidgets.glance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.glance.appwidget.lazy.LazyColumn
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
import dev.gonodono.webwidgets.shootForAppWidget
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import dev.gonodono.webwidgets.shooter.createWebShooter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import android.util.Size as AndroidSize

internal class GlanceWebWidget : GlanceAppWidget() {

    class Receiver : GlanceAppWidgetReceiver() {

        override val glanceAppWidget: GlanceAppWidget = GlanceWebWidget()

        override fun onReceive(context: Context, intent: Intent) =
            if (intent.action == ActionOpen) {
                handleActionOpen(context, intent)
            } else {
                super.onReceive(context, intent)
            }
    }

    private sealed interface State {
        data object Busy : State
        data class Complete(val webShot: WebShot.Complete) : State
        data object Timeout : State
        data object Error : State
    }

    override val sizeMode: SizeMode = SizeMode.Exact

    private val scope = CoroutineScope(SupervisorJob())

    private var webShooter: WebShooter? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (webShooter == null) webShooter = createWebShooter(context)

        // The demo assumes that the host is a basic launcher that handles only
        // portrait and landscape configurations. provideGlance() may be called
        // for both sizes per update, and the results would be combined into one
        // RemoteViews. If we were to provide full size scroll images for both
        // orientations, they could exceed the maximum limit for an update, so
        // the demo provides actual content for only the current orientation.
        val orientation = context.resources.configuration.orientation
        val isDevicePortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        provideContent {
            val isWidgetPortrait = LocalSize.current.run { width <= height }
            if (isWidgetPortrait == isDevicePortrait) {
                MainContent()
            } else {
                BusyIndicator()
            }
        }
    }

    @Composable
    private fun MainContent() {
        val context = LocalContext.current
        val size = with(Density(context)) { LocalSize.current.toSize() }
        val (state, setState) = remember { mutableStateOf<State>(State.Busy) }

        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .appWidgetBackground()
        ) {
            when (state) {
                State.Busy -> {
                    BusyIndicator()
                    LaunchedEffect(Unit) { shoot(setState, size) }
                    return@Box
                }
                is State.Complete -> MainContent(state.webShot)
                State.Timeout -> TimeoutMessage()
                State.Error -> ErrorMessage()
            }

            ReloadButton({ shoot(setState, size) })
        }
    }

    private var job: Job? = null

    private fun shoot(setState: (State) -> Unit, size: Size) {
        job?.cancel()

        val shooter = checkNotNull(webShooter) { "Mising WebShooter" }

        job = scope.launch {
            setState(State.Busy)

            val width = size.width.fastRoundToInt()
            val height = size.height.fastRoundToInt()
            val targetSize = AndroidSize(width, height)

            val webShot =
                withTimeoutOrNull(10.seconds) {  // Max is ~45, but that's huge
                    shooter.shootForAppWidget(
                        url = WikipediaRandomPageUrl,
                        targetSize = targetSize
                    )
                }

            ensureActive()

            val state =
                when (webShot) {
                    is WebShot.Complete -> State.Complete(webShot)
                    is WebShot.Error -> State.Error
                    null -> State.Timeout
                }
            setState(state)
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        webShooter?.close()
        scope.cancel()
    }
}

@Composable
private fun MainContent(webShot: WebShot.Complete) =
    LazyColumn {
        item {
            Image(
                provider = ImageProvider(webShot.bitmap),
                contentDescription = "WebShot",
                modifier = GlanceModifier.run {
                    if (webShot.url != null) {
                        val uri = webShot.url.toUri()
                        val context = LocalContext.current
                        val receiver = GlanceWebWidget.Receiver::class.java
                        val open = Intent(ActionOpen, uri, context, receiver)
                        clickable(actionSendBroadcast(open))
                    } else {
                        this
                    }
                }
            )
        }
    }

@Composable
private fun BusyIndicator() = CenteredBox { CircularProgressIndicator() }

@Composable
private fun TimeoutMessage() = CenteredMessage("Timeout", Color.Magenta)

@Composable
private fun ErrorMessage() = CenteredMessage("Error", Color.Red)

@Composable
private fun CenteredBox(content: @Composable () -> Unit) =
    Box(GlanceModifier.fillMaxSize(), Alignment.Center, content)

@Composable
private fun CenteredMessage(text: String, color: Color) =
    CenteredBox {
        val colorProvider = color.toColorProvider()
        val style = TextDefaults.defaultTextStyle.copy(colorProvider, 22.sp)
        Text(text, style = style)
    }

@Composable
private fun ReloadButton(
    onClick: () -> Unit,
    modifier: GlanceModifier = GlanceModifier
) =
    Image(
        provider = ImageProvider(R.drawable.reload),
        contentDescription = "Reload",
        modifier = modifier
            .padding(12.dp)
            .background(ImageProvider(R.drawable.circle))
            .clickable(onClick)
    )

@Suppress("NOTHING_TO_INLINE")
private inline fun Color.toColorProvider(): ColorProvider =
    @SuppressLint("RestrictedApi") ColorProvider(this)