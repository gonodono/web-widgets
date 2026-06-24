package dev.gonodono.webwidgets.shooter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

suspend fun createWebShooter(
    context: Context,
    virtual: Boolean = context.appSettings.useVirtualWebShooter
): WebShooter =
    if (virtual) {
        VirtualWebShooter.create(context)
    } else {
        OverlayWebShooter.create(context)
    }

interface WebShooter : AutoCloseable {

    val mainContext: Context

    suspend fun shoot(
        url: String,
        width: Int,
        maxHeight: Int,
        layoutWidth: Int = width,
        delayStrategy: DelayStrategy = DelayStrategy.None
    ): WebShot
}

internal abstract class AbstractWebShooter(context: Context) : WebShooter {

    protected val webView = WebShooterWebView(context)

    final override suspend fun shoot(
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

        return try {
            shootLocked(url, width, maxHeight, layoutWidth, delayStrategy)
        } catch (e: Exception) {
            WebShot.Error(e.message, e)
        }
    }

    private val mutex = Mutex()

    private suspend fun shootLocked(
        url: String,
        width: Int,
        maxHeight: Int,
        layoutWidth: Int,
        delayStrategy: DelayStrategy
    ): WebShot.Complete =
        mutex.withLock {
            val view = webView

            val loaded = view.awaitLoadUrl(url)
            view.measureAndLayout(layoutWidth, 1)
            view.awaitVisualStateCallback()

            currentCoroutineContext().ensureActive()

            performDelay(delayStrategy) { webView.invalidations }

            val contentHeight = view.contentHeightPx()
            val scale = width.toFloat() / layoutWidth

            val maxViewHeight = (maxHeight / scale).roundToInt()
            val viewHeight = contentHeight.coerceAtMost(maxViewHeight)
            view.measureAndLayout(layoutWidth, viewHeight)
            view.awaitVisualStateCallback()

            val scaledContentHeight = (contentHeight * scale).roundToInt()
            val imageHeight = scaledContentHeight.coerceAtMost(maxHeight)

            currentCoroutineContext().ensureActive()

            val bitmap = createBitmap(width, imageHeight)
            bitmap.applyCanvas { withScale(scale, scale) { view.draw(this) } }

            val overflows = scaledContentHeight > maxHeight
            return WebShot.Complete(loaded, bitmap, overflows)
        }
}

internal class WebShooterWebView(context: Context) : WebView(context) {

    init {
        visibility = GONE
    }

    private var _invalidations: MutableSharedFlow<Unit>? = null

    val invalidations: Flow<Unit>
        get() = _invalidations
            ?: MutableSharedFlow<Unit>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_LATEST
            )
                .also { _invalidations = it }

    private val choreographer = Choreographer.getInstance()

    private val fakeViewTreeDraw =
        Choreographer.FrameCallback { draw(DummyCanvas) }

    override fun invalidate() {
        _invalidations?.tryEmit(Unit)
        choreographer.postFrameCallback(fakeViewTreeDraw)
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
                        continuation.resume(url)
                    }
                }

            continuation.invokeOnCancellation {
                this@awaitLoadUrl.post {
                    this@awaitLoadUrl.webViewClient = original
                    this@awaitLoadUrl.stopLoading()
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
                    override fun onComplete(requestId: Long) =
                        continuation.resume(Unit)
                }
            this@awaitVisualStateCallback.postVisualStateCallback(0, callback)
        }
    }

internal suspend fun WebView.contentHeightPx(): Int =
    withContext(Dispatchers.Main) { this@contentHeightPx.contentHeight }.let {
        (it * this@contentHeightPx.resources.displayMetrics.density).roundToInt()
    }