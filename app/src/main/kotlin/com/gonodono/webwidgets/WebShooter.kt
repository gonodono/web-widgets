package com.gonodono.webwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.withScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal class WebShooter(context: Context) {

    sealed interface Result

    data class WebShot(
        val url: String,
        val bitmap: Bitmap,
        val overflows: Boolean
    ) : Result

    data class Error(val message: String) : Result

    sealed interface DrawDelay {
        data class Time(val millis: Long = 100L) : DrawDelay
        data class Frames(val count: Int = 10) : DrawDelay
        data class Invalidations(
            val debounceTimeout: Long = 100L,
            val completionTimeout: Long = 2_000L
        ) : DrawDelay
    }


    private val context: Context = context.applicationContext

    private var frameLayout: FrameLayout? = null

    private var webView: ShooterWebView? = null

    var canDraw: Boolean = false
        private set

    fun initializeBlocking() =
        runBlocking(Dispatchers.Main.immediate) { initialize() }

    suspend fun initialize() = withContext(Dispatchers.Main) {
        val frame = FrameLayout(context)
        if (frame.addToWindowManager() && frame.removeFromWindowManager()) {
            webView = ShooterWebView(context).also { frame.addView(it) }
            frameLayout = frame
            canDraw = true
        }
    }

    private val mutex = Mutex()

    suspend fun takeShot(
        url: String,
        targetSize: Size,
        fitTargetHeight: Boolean,
        screenAreaMultiplier: Float = 1.45F,  // Widget API max is 1.5F.
        drawDelay: DrawDelay = DrawDelay.Invalidations()
    ): Result {

        check(canDraw) { "Cannot draw" }
        check(url.isNotBlank()) { "Blank or empty URL" }
        check(targetSize.width > 0 && targetSize.height > 0) {
            "Invalid size: $targetSize"
        }

        mutex.withLock {
            val frame = frameLayout!!
            if (frame.addToWindowManager()) try {

                val web = webView!!
                val loaded = web.awaitLoadUrl(url) ?: return Error("Load error")

                val imageWidth = targetSize.width
                val screenSize = context.screenSize()
                val downScale = imageWidth.toFloat() / screenSize.width

                val imageHeight: Int
                val viewHeight: Int
                val overflows: Boolean
                if (fitTargetHeight) {
                    imageHeight = targetSize.height
                    viewHeight = (imageHeight / downScale).toInt()
                    overflows = false
                } else {
                    web.awaitLayout(screenSize.width, screenSize.height)
                    val contentHeightPx = web.contentHeightPx()
                    val desiredHeight = (contentHeightPx * downScale).toInt()
                    val screenArea = screenSize.width * screenSize.height
                    val maxArea = screenArea * screenAreaMultiplier
                    val maxHeight = (maxArea / imageWidth).toInt()
                    val maxViewHeight = (maxHeight / downScale).toInt()
                    imageHeight = minOf(desiredHeight, maxHeight)
                    viewHeight = minOf(contentHeightPx, maxViewHeight)
                    overflows = desiredHeight > maxHeight
                }
                if (imageHeight <= 0) return Error("Layout error")

                web.awaitLayout(screenSize.width, viewHeight)

                when (drawDelay) {
                    is DrawDelay.Time -> {
                        delay(drawDelay.millis)
                    }
                    is DrawDelay.Frames -> {
                        awaitFrames(drawDelay.count)
                    }
                    is DrawDelay.Invalidations -> {
                        web.awaitInvalidations(
                            drawDelay.debounceTimeout,
                            drawDelay.completionTimeout
                        )
                    }
                }

                val bitmap = web.drawBitmap(imageWidth, imageHeight, downScale)

                return WebShot(loaded, bitmap, overflows)
            } finally {
                withContext(NonCancellable) {
                    frame.removeFromWindowManager()
                }
            } else return Error("WindowManager error")
        }
    }
}

// This class exists only because of awaitInvalidations(). If you're not using
// that delay option, the other settings and members can be pulled out of here
// and applied to a regular WebView instance. The rest of the helper functions
// have been kept separate below to make such modifications easier, if needed.
private class ShooterWebView(context: Context) : WebView(context) {

    init {
        isVerticalScrollBarEnabled = false
    }

    suspend fun contentHeightPx(): Int = withContext(Dispatchers.Main) {
        (contentHeight * context.resources.displayMetrics.density).roundToInt()
    }

    private val invalidations = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    @OptIn(FlowPreview::class)
    suspend fun awaitInvalidations(
        debounceTimeout: Long,
        completionTimeout: Long
    ) {
        withTimeoutOrNull(completionTimeout) {
            invalidations
                .onEach { coroutineContext.ensureActive() }
                .debounce(debounceTimeout)
                .take(1)
                .collect()
        }
    }

    override fun invalidate() {
        invalidations.tryEmit(Unit)
    }

    fun drawBitmap(width: Int, height: Int, scale: Float): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .applyCanvas { withScale(scale, scale) { draw(this) } }
}

internal suspend fun View.addToWindowManager(): Boolean =
    withContext(Dispatchers.Main) {
        try {
            context.windowManager.addView(this@addToWindowManager, windowParams)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error adding View", e)
            false
        }
    }

private val windowParams = WindowManager.LayoutParams(
    0, 0,
    if (Build.VERSION.SDK_INT >= 26) {
        TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        TYPE_SYSTEM_OVERLAY
    },
    FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE,
    PixelFormat.OPAQUE
)

internal suspend fun View.removeFromWindowManager(): Boolean =
    withContext(Dispatchers.Main) {
        try {
            context.windowManager.removeView(this@removeFromWindowManager)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error removing View", e)
            false
        }
    }

internal suspend fun WebView.awaitLoadUrl(url: String): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    ensureActive()
                    continuation.resume(url)
                }
            }
            continuation.invokeOnCancellation { post { stopLoading() } }
            loadUrl(url)
        }
    }

internal suspend fun WebView.awaitLayout(width: Int, height: Int) {
    if (this.width == width && this.height == height) return

    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            postVisualStateCallback(
                0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        ensureActive()
                        continuation.resume(Unit)
                    }
                }
            )
            layout(0, 0, width, height)
        }
    }
}

// Waits at least count frames, possibly up to count + 1.
internal suspend fun awaitFrames(count: Int) {
    if (count <= 0) return

    withContext(Dispatchers.Main) {
        var frames = count
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine { continuation ->
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(it: Long) {
                    ensureActive()
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

internal fun Context.screenSize(): Size =
    if (Build.VERSION.SDK_INT >= 30) {
        windowManager.currentWindowMetrics.bounds.let { bounds ->
            Size(bounds.width(), bounds.height())
        }
    } else {
        Point().let { point ->
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
            Size(point.x, point.y)
        }
    }

private inline val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager