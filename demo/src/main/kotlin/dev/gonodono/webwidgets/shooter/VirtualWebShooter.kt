package dev.gonodono.webwidgets.shooter

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
import android.hardware.display.VirtualDisplay
import android.view.SurfaceView
import dev.gonodono.webwidgets.checkIsMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class VirtualWebShooter
private constructor(
    private val virtualDisplay: VirtualDisplay,
    private val presentation: Presentation,
) : AbstractWebShooter(presentation.context) {

    companion object {

        suspend fun create(context: Context): VirtualWebShooter =
            withContext(Dispatchers.Main) { createWebShooter(context) }

        fun createBlocking(context: Context): VirtualWebShooter {
            checkIsMainThread()
            return createWebShooter(context)
        }

        private fun createWebShooter(context: Context): VirtualWebShooter {
            val densityDpi = context.resources.displayMetrics.densityDpi
            val surface = SurfaceView(context).holder.surface
            val virtualDisplay =
                context.getSystemService(DisplayManager::class.java)
                    .createVirtualDisplay(
                        /* name = */ "VirtualWebShooter",
                        /* width = */ 1,
                        /* height = */ 1,
                        /* densityDpi = */ densityDpi,
                        /* surface = */ surface,
                        /* flags = */ VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    )
            checkNotNull(virtualDisplay) { "Failed to create VirtualDisplay" }

            val presentation = Presentation(context, virtualDisplay.display)
            return VirtualWebShooter(virtualDisplay, presentation)
        }
    }

    init {
        presentation.setContentView(webView)
        presentation.show()
    }

    override fun close() {
        presentation.dismiss()
        virtualDisplay.release()
    }
}