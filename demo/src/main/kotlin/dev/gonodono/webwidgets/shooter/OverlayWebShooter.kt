package dev.gonodono.webwidgets.shooter

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.widget.FrameLayout
import dev.gonodono.webwidgets.checkIsMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OverlayWebShooter
private constructor(override val context: Context) :
    AbstractWebShooter(context) {

    companion object {

        fun canDraw(context: Context): Boolean =
            Settings.canDrawOverlays(context)

        suspend fun create(context: Context): OverlayWebShooter =
            withContext(Dispatchers.Main) { createWebShooter(context) }

        fun createBlocking(context: Context): OverlayWebShooter {
            checkIsMainThread()
            return createWebShooter(context)
        }

        private fun createWebShooter(context: Context): OverlayWebShooter {
            check(canDraw(context)) { "Missing SYSTEM_ALERT_WINDOW permission" }
            return OverlayWebShooter(context)
        }
    }

    private val frameLayout = FrameLayout(context.applicationContext)

    init {
        val frame = frameLayout
        frame.addView(webView)
        frame.context.windowManager.addView(frame, OverlayWindowParams)
    }

    override val label: String get() = "Overlay"

    override fun close() {
        val frame = frameLayout
        frame.post {
            frame.context.windowManager.removeView(frame)
            frame.removeView(webView)
        }
    }
}

// NB: Use TYPE_SYSTEM_OVERLAY for API levels < 26.
private val OverlayWindowParams: WindowManager.LayoutParams =
    WindowManager.LayoutParams(
        /* w = */ 0,
        /* h = */ 0,
        /* _type = */ TYPE_APPLICATION_OVERLAY,
        /* _flags = */ FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE,
        /* _format = */ PixelFormat.OPAQUE
    )

private inline val Context.windowManager: WindowManager
    get() = this.getSystemService(WindowManager::class.java)