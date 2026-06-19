package dev.gonodono.webwidgets.shooter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale
import dev.gonodono.webwidgets.appSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

interface WebShooter : AutoCloseable {

    suspend fun takeShot(
        url: String,
        width: Int,
        maxHeight: Int,
        layoutWidth: Int,
        delayStrategy: DelayStrategy = DelayStrategy.None
    ): WebShot

    companion object {

        suspend fun create(
            context: Context,
            virtual: Boolean = context.appSettings.useVirtualWebShooter
        ): WebShooter =
            withContext(Dispatchers.Main) {
                createBlocking(context, virtual)
            }

        fun createBlocking(
            context: Context,
            virtual: Boolean = context.appSettings.useVirtualWebShooter
        ): WebShooter =
            if (virtual) {
                VirtualWebShooter.createBlocking(context)
            } else {
                OverlayWebShooter.createBlocking(context)
            }
    }
}

internal abstract class AbstractWebShooter(context: Context) : WebShooter {

    protected val webView = WebShooterWebView(context)

    final override suspend fun takeShot(
        url: String,
        width: Int,
        maxHeight: Int,
        layoutWidth: Int,
        delayStrategy: DelayStrategy
    ): WebShot {
        check(url.isNotBlank()) { "Blank or empty URL" }
        check(width > 0) { "width must be positive: $width" }
        check(maxHeight > 0) { "maxHeight must be positive: $maxHeight" }
        check(layoutWidth > 0) { "layoutWidth must be positive: $layoutWidth" }

        val view = webView
        view.measureAndLayout(layoutWidth, 1)

        val loaded = view.awaitLoadUrl(url)
        view.awaitVisualStateCallback()

        currentCoroutineContext().ensureActive()

        performDelay(delayStrategy) { webView.invalidations }

        val contentHeight = view.contentHeightPx()
        val scale = width.toFloat() / layoutWidth

        val maxViewHeight = (maxHeight / scale).toInt()
        val viewHeight = contentHeight.coerceAtMost(maxViewHeight)
        view.measureAndLayout(layoutWidth, viewHeight)
        view.awaitVisualStateCallback()

        currentCoroutineContext().ensureActive()

        val scaledContentHeight = (contentHeight * scale).toInt()
        val imageHeight = scaledContentHeight.coerceAtMost(maxHeight)
        val bitmap = createBitmap(width, imageHeight)

        bitmap.applyCanvas {
            withScale(scale, scale) { view.draw(this) }
            drawLabel(width)
        }

        return WebShot(
            url = loaded,
            bitmap = bitmap,
            overflows = scaledContentHeight > maxHeight
        )
    }

    private var paint: Paint? = null

    private fun Canvas.drawLabel(width: Int) {
        if (!webView.context.appSettings.drawTypeLabel) return

        val name = this@AbstractWebShooter.javaClass.simpleName
        val label = name.replace("WebShooter", "")

        val paint = this@AbstractWebShooter.paint
            ?: Paint().also {
                it.color = Color.MAGENTA
                it.textAlign = Paint.Align.CENTER
                this@AbstractWebShooter.paint = it
            }

        val textSize = width / 5F
        paint.textSize = textSize

        this.drawText(label, 0, label.length, width / 2F, textSize, paint)
    }
}

internal class WebShooterWebView(context: Context) : WebView(context) {

    private val choreographer = Choreographer.getInstance()

    private val fakeViewTreeDraw =
        Choreographer.FrameCallback { draw(DummyCanvas) }

    private var _invalidations: MutableSharedFlow<Unit>? = null

    val invalidations: Flow<Unit>
        get() = _invalidations
            ?: MutableSharedFlow<Unit>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_LATEST
            )
                .also { _invalidations = it }

    init {
        visibility = GONE
    }

    override fun invalidate() {
        choreographer.postFrameCallback(fakeViewTreeDraw)
        _invalidations?.tryEmit(Unit)
    }

    private companion object {
        val DummyCanvas = Canvas(createBitmap(1, 1, Bitmap.Config.ALPHA_8))
    }
}

internal suspend fun WebView.measureAndLayout(width: Int, height: Int) =
    withContext(Dispatchers.Main) {
        val widthSpec = makeMeasureSpec(width, EXACTLY)
        val heightSpec = makeMeasureSpec(height, EXACTLY)
        this@measureAndLayout.measure(widthSpec, heightSpec)
        this@measureAndLayout.layout(0, 0, width, height)
    }

internal suspend fun WebView.awaitLoadUrl(url: String): String? =
    withContext(Dispatchers.Main) {
        val original = this@awaitLoadUrl.webViewClient

        suspendCancellableCoroutine { continuation ->
            webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.webViewClient = original
                        if (continuation.isActive) continuation.resume(url)
                    }
                }

            continuation.invokeOnCancellation {
                this@awaitLoadUrl.post {
                    this@awaitLoadUrl.stopLoading()
                    this@awaitLoadUrl.webViewClient = original
                }
            }

            this@awaitLoadUrl.loadUrl(url)
        }
    }

internal suspend fun WebView.awaitVisualStateCallback() =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            this@awaitVisualStateCallback.postVisualStateCallback(0, callback)
        }
    }

internal suspend fun performDelay(
    strategy: DelayStrategy,
    drawFlow: () -> Flow<*>
) {
    when (strategy) {
        is DelayStrategy.None -> {
            return
        }
        is DelayStrategy.Time -> {
            delay(strategy.time)
        }
        is DelayStrategy.Frames -> {
            awaitMainThreadFrames(strategy.frames)
        }
        is DelayStrategy.DrawIdling -> {
            withTimeoutOrNull(strategy.timeout) {
                @Suppress("OPT_IN_USAGE")
                drawFlow()
                    .debounce(strategy.debounce)
                    .take(1)
                    .collect()
            }
        }
    }
}

internal suspend fun awaitMainThreadFrames(count: Int) {
    if (count <= 0) return

    withContext(Dispatchers.Main) {
        var frames = count
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (!continuation.isActive) return

                        if (frames-- > 0) {
                            choreographer.postFrameCallback(this)
                        } else {
                            continuation.resume(Unit)
                        }
                    }
                }
            continuation.invokeOnCancellation {
                choreographer.removeFrameCallback(callback)
            }
            choreographer.postFrameCallback(callback)
        }
    }
}

// It's OK to use WebView's Context for density here; it's copied from actual.
internal suspend fun WebView.contentHeightPx(): Int =
    withContext(Dispatchers.Main) { this@contentHeightPx.contentHeight }.let {
        (it * this@contentHeightPx.resources.displayMetrics.density).roundToInt()
    }