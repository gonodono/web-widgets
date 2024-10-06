package com.gonodono.webwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.util.Size
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

internal class WebShooter(context: Context) {

    sealed interface Result

    data class WebShot(
        val url: String,
        val bitmap: Bitmap,
        val overflows: Boolean
    ) : Result

    data class Error(val message: String) : Result


    private val context: Context = context.applicationContext

    private var frameLayout: FrameLayout? = null

    private var webView: WebView? = null

    var canDraw: Boolean = false
        private set

    fun initialize() = runBlocking(Dispatchers.Main.immediate) {
        val frame = FrameLayout(context)
        if (frame.addToWindowManager() && frame.removeFromWindowManager()) {
            webView = WebView(context).also { frame.addView(it) }
            frameLayout = frame
            canDraw = true
        }
    }

    private val mutex = Mutex()

    suspend fun takeShot(
        url: String,
        targetSize: Size,
        fitTargetHeight: Boolean,
        screenAreaMultiplier: Float = 1.45F  // Max 1.5F
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
                val current = web.awaitLoadUrl(url)
                coroutineContext.ensureActive()
                if (current == null) return Error("Load error")

                val imageSpecs = web.performLayouts(
                    targetSize,
                    fitTargetHeight,
                    screenAreaMultiplier
                )
                coroutineContext.ensureActive()
                if (imageSpecs.height <= 0) return Error("Layout error")

                val bitmap = web.drawToBitmap(
                    imageSpecs.width,
                    imageSpecs.height,
                    imageSpecs.downScale
                )
                coroutineContext.ensureActive()

                return WebShot(current, bitmap, imageSpecs.overflows)
            } finally {
                withContext(NonCancellable) {
                    frame.removeFromWindowManager()
                }
            }
            else return Error("WindowManager error")
        }
    }

    private suspend fun WebView.performLayouts(
        targetSize: Size,
        fitTargetHeight: Boolean,
        screenAreaMultiplier: Float
    ): ImageSpecs = withContext(Dispatchers.Main) {

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
            awaitLayout(screenSize.width, screenSize.height)
            val density = context.resources.displayMetrics.density
            val contentHeightPx = (contentHeight * density).toInt()
            val desiredHeight = (contentHeightPx * downScale).toInt()
            val screenArea = screenSize.width * screenSize.height
            val maxArea = screenArea * screenAreaMultiplier
            val maxHeight = (maxArea / imageWidth).toInt()
            val maxViewHeight = (maxHeight / downScale).toInt()
            imageHeight = minOf(desiredHeight, maxHeight)
            viewHeight = minOf(contentHeightPx, maxViewHeight)
            overflows = desiredHeight > maxHeight
        }
        awaitLayout(screenSize.width, viewHeight)

        ImageSpecs(imageWidth, imageHeight, downScale, overflows)
    }

    private class ImageSpecs(
        val width: Int,
        val height: Int,
        val downScale: Float,
        val overflows: Boolean
    )
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
    when {
        Build.VERSION.SDK_INT >= 26 -> TYPE_APPLICATION_OVERLAY
        else -> @Suppress("DEPRECATION") TYPE_SYSTEM_OVERLAY
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
                    if (continuation.isActive) continuation.resume(url)
                }
            }
            continuation.invokeOnCancellation { post { stopLoading() } }
            loadUrl(url)
        }
    }

internal suspend fun WebView.awaitLayout(width: Int, height: Int) =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            postVisualStateCallback(
                0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            )
            layout(0, 0, width, height)
        }
    }

internal suspend fun WebView.drawToBitmap(
    width: Int,
    height: Int,
    downScale: Float
): Bitmap = withContext(Dispatchers.Default) {
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).applyCanvas {
        withScale(downScale, downScale) { this@drawToBitmap.draw(this) }
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