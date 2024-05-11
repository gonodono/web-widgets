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
import androidx.compose.runtime.Immutable
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.withScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal class WebShooter(private val context: Context) {

    @Immutable
    class WebShot(
        val url: String,
        val bitmap: Bitmap,
        val overflows: Boolean
    )

    private var frameLayout: FrameLayout? = null

    private var webView: WebView? = null

    var canDraw: Boolean = false
        private set

    fun initialize() = runBlocking(Dispatchers.Main.immediate) {
        val frame = FrameLayout(context)
        if (frame.addToWindowManager() && frame.removeFromWindowManager()) {
            frameLayout = frame
            webView = WebView(frame.context).also { frame.addView(it) }
            canDraw = true
        }
    }

    private val mutex = Mutex()

    // It's assumed that null means timeout; almost everything else throws.
    // Better failure details could be relayed by using some kind of Result type.
    suspend fun takeShot(
        url: String,
        timeout: Long,
        targetSize: Size,
        fitTargetHeight: Boolean,
        screenAreaMultiplier: Float = 1.45F  // Max 1.5F
    ): WebShot? = mutex.withLock {

        check(canDraw) { "Cannot draw." }
        check(url.isNotBlank()) { "Blank or empty URL" }
        check(targetSize.width > 0 && targetSize.height > 0) {
            "Invalid size: $targetSize"
        }

        val frame = frameLayout!!
        check(frame.addToWindowManager()) { "WindowManager error" }

        try {
            val web = webView!!
            withTimeoutOrNull(timeout) {
                val current = web.awaitLoadUrl(url)
                checkNotNull(current) { "WebView load error" }

                val imageSpecs = web.performLayouts(
                    targetSize,
                    fitTargetHeight,
                    screenAreaMultiplier
                )
                check(imageSpecs.height > 0) { "Layout error" }

                when {
                    isActive -> {
                        val bitmap = web.drawToBitmap(
                            imageSpecs.width,
                            imageSpecs.height,
                            imageSpecs.downScale
                        )
                        WebShot(current, bitmap, imageSpecs.overflows)
                    }

                    else -> null
                }
            }
        } finally {
            withContext(NonCancellable) {
                frame.removeFromWindowManager()
            }
        }
    }

    private suspend fun WebView.performLayouts(
        targetSize: Size,
        fitTargetHeight: Boolean,
        screenAreaMultiplier: Float
    ): ImageSpecs = withContext(Dispatchers.Main) {

        val screenSize = context.screenSize()
        val imageWidth = targetSize.width
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

// This fun and others below are internal for use in the minimal examples.
internal suspend fun View.addToWindowManager(): Boolean =
    withContext(Dispatchers.Main) {
        try {
            context.windowManager.addView(this@addToWindowManager, windowParams)
            true
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
) = withContext(Dispatchers.Default) {
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .applyCanvas { withScale(downScale, downScale) { draw(this) } }
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