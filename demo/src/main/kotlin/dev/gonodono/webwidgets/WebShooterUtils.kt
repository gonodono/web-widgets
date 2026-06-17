package dev.gonodono.webwidgets

import android.util.Size
import androidx.annotation.FloatRange
import dev.gonodono.webwidgets.shooter.DefaultDelayStrategy
import dev.gonodono.webwidgets.shooter.DelayStrategy
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot

suspend fun WebShooter.takeShotForAppWidget(
    url: String,
    targetSize: Size,
    fitTargetHeight: Boolean,
    screenSize: Size = this.context.screenSize(),
    @FloatRange(from = 0.0, to = 1.5)
    screenAreaMultiplier: Float = 1.45F,
    delayStrategy: DelayStrategy = DefaultDelayStrategy
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