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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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

    val context: Context

    suspend fun takeShot(
        url: String,
        width: Int,
        maxHeight: Int,
        layoutWidth: Int,
        delayStrategy: DelayStrategy = DefaultDelayStrategy
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

    private var _invalidations: MutableSharedFlow<Unit>? = null

    // We intercept invalidate() calls in order to minimize work on the main
    // thread but WebView won't stop invalidating until it draws so we fake it.
    protected val webView =
        object : WebView(context) {

            private val choreographer = Choreographer.getInstance()

            private val fakeViewTreeDraw =
                Choreographer.FrameCallback { draw(DummyCanvas) }

            override fun invalidate() {
                choreographer.postFrameCallback(fakeViewTreeDraw)
                _invalidations?.tryEmit(Unit)
            }
        }

    private val invalidations: Flow<Unit>
        get() = _invalidations
            ?: MutableSharedFlow<Unit>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_LATEST
            )
                .also { _invalidations = it }

    override suspend fun takeShot(
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

        val terminus = view.awaitLoadUrl(url)

        performDelay(delayStrategy) { invalidations }

        val contentHeight = view.contentHeightPx()
        val scale = width.toFloat() / layoutWidth

        val maxViewHeight = (maxHeight / scale).toInt()
        val viewHeight = contentHeight.coerceAtMost(maxViewHeight)
        view.measureAndLayout(layoutWidth, viewHeight)
        view.awaitVisualStateCallback()

        val scaledContentHeight = (contentHeight * scale).toInt()
        val imageHeight = scaledContentHeight.coerceAtMost(maxHeight)
        val bitmap = createBitmap(width, imageHeight)

        bitmap.applyCanvas {
            withScale(scale, scale) { view.draw(this) }
            drawLabel(width)
        }

        return WebShot(
            url = terminus,
            bitmap = bitmap,
            overflows = scaledContentHeight > maxHeight
        )
    }

    protected abstract val label: String

    private var paint: Paint? = null

    private fun Canvas.drawLabel(width: Int) {
        if (!context.appSettings.drawTypeLabel) return

        val paint = this@AbstractWebShooter.paint
            ?: Paint().also {
                it.textAlign = Paint.Align.CENTER
                this@AbstractWebShooter.paint = it
            }
        val size = width / 5F
        paint.textSize = size
        paint.color = Color.MAGENTA
        drawText(label, 0, label.length, width / 2F, size, paint)
    }

    override fun close() {
        _invalidations = null
        paint = null
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
        val view = this@awaitLoadUrl
        val original = view.webViewClient

        suspendCancellableCoroutine { continuation ->
            webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.webViewClient = original
                        continuation.resumeIfActive(url)
                    }
                }

            continuation.invokeOnCancellation {
                view.post {
                    view.webViewClient = original
                    view.stopLoading()
                }
            }

            view.loadUrl(url)
        }
    }

internal suspend fun performDelay(
    strategy: DelayStrategy,
    drawFlow: () -> Flow<*>
) =
    when (strategy) {
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
    withContext(Dispatchers.Main) {
        val height = this@contentHeightPx.contentHeight
        val density = this@contentHeightPx.resources.displayMetrics.density
        (height * density).roundToInt()
    }

internal suspend fun WebView.awaitVisualStateCallback() =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) =
                        continuation.resumeIfActive(Unit)
                }
            this@awaitVisualStateCallback.postVisualStateCallback(0, callback)
        }
    }

@Suppress("NOTHING_TO_INLINE")
private inline fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (this.isActive) this.resume(value)
}