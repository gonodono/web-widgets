package dev.gonodono.webwidgets.shooter

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
import android.hardware.display.VirtualDisplay
import android.view.SurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class VirtualWebShooter
private constructor(
    override val mainContext: Context,
    private val virtualDisplay: VirtualDisplay,
    private val presentation: Presentation,
) : AbstractWebShooter(presentation.context) {

    companion object {

        suspend fun create(context: Context): VirtualWebShooter =
            withContext(Dispatchers.Main) {
                val densityDpi = context.resources.displayMetrics.densityDpi
                val surface = SurfaceView(context).holder.surface
                val virtualDisplay =
                    context.getSystemService(DisplayManager::class.java)
                        .createVirtualDisplay(
                            /* name = */ "VirtualWebShooterDisplay",
                            /* width = */ 1,
                            /* height = */ 1,
                            /* densityDpi = */ densityDpi,
                            /* surface = */ surface,
                            /* flags = */ VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        )
                checkNotNull(virtualDisplay) { "Failed to create VirtualDisplay" }

                val presentation = Presentation(context, virtualDisplay.display)
                VirtualWebShooter(context, virtualDisplay, presentation)
            }
    }

    init {
        presentation.setOnShowListener {
            presentation.setOnShowListener(null)
            webView.post { isReady.complete(Unit) }
        }
        presentation.setContentView(webView)
        presentation.show()
    }

    override fun close() {
        presentation.dismiss()
        virtualDisplay.release()
    }
}