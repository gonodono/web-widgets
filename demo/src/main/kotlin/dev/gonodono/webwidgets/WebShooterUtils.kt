package dev.gonodono.webwidgets

import android.util.Size
import dev.gonodono.webwidgets.shooter.DelayStrategy
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import androidx.annotation.FloatRange as FloatRangeAnnotation

// It's a bit redundant since it's always the same, but we pass the screen size
// in so that we don't mix up our Contexts or Displays and grab the wrong size.
suspend fun WebShooter.takeShotForAppWidget(
    url: String,
    screenSize: Size,
    targetSize: Size,
    fitTargetHeight: Boolean,
    @FloatRangeAnnotation(from = 0.0, to = 1.5)
    screenAreaMultiplier: Float = 1.45F,
    delayStrategy: DelayStrategy = DelayStrategy.None
): WebShot {
    val width = targetSize.width
    val maxHeight =
        if (fitTargetHeight) {
            targetSize.height
        } else {
            val screenArea = screenSize.width * screenSize.height
            val maxArea = screenArea * screenAreaMultiplier
            (maxArea / width).toInt()
        }
    return this.takeShot(url, width, maxHeight, screenSize.width, delayStrategy)
}