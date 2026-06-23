package dev.gonodono.webwidgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Size
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.applyCanvas
import androidx.window.layout.WindowMetricsCalculator
import dev.gonodono.webwidgets.shooter.DelayStrategy
import dev.gonodono.webwidgets.shooter.WebShooter
import dev.gonodono.webwidgets.shooter.WebShot
import kotlin.math.roundToInt
import androidx.annotation.FloatRange as FloatRangeAnnotation

suspend fun WebShooter.takeShotForAppWidget(
    url: String,
    targetSize: Size,
    clampHeightToTarget: Boolean,
    screenSize: Size = this.mainContext.screenSize(),
    @FloatRangeAnnotation(from = 0.0, to = 1.5)
    screenAreaMultiplier: Float = 1.45F,
    delayStrategy: DelayStrategy = DelayStrategy.None
): WebShot {
    val width = targetSize.width
    val screenArea = screenSize.width * screenSize.height
    val maxArea = screenArea * screenAreaMultiplier
    val maxHeightByScreenArea = (maxArea / width).roundToInt()
    val maxHeight =
        if (clampHeightToTarget) {
            minOf(targetSize.height, maxHeightByScreenArea)
        } else {
            maxHeightByScreenArea
        }

    val webShot =
        this.takeShot(
            url = url,
            width = width,
            maxHeight = maxHeight,
            layoutWidth = screenSize.width,
            delayStrategy = delayStrategy
        )

    if (webShot is WebShot.Complete) {
        webShot.drawLabels(this, width, clampHeightToTarget)
    }

    return webShot
}

private fun Context.screenSize(): Size =
    WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this)
        .let { metrics -> metrics.bounds.run { Size(width(), height()) } }

private fun WebShot.Complete.drawLabels(
    webShooter: WebShooter,
    width: Int,
    clampHeightToTarget: Boolean
) {
    val drawTypeLabel = webShooter.mainContext.appSettings.drawTypeLabel
    val overflows = this.overflows
    if (!drawTypeLabel && !overflows) return

    val paint = Paint()
    paint.textAlign = Paint.Align.CENTER

    this.bitmap.applyCanvas {
        if (drawTypeLabel) {
            val simpleName = webShooter.javaClass.simpleName
            val text = simpleName.replace("WebShooter", "")
            val textSize = width / 5F
            drawLabel(text, textSize, width, 0F, paint)
        }
        if (!clampHeightToTarget && overflows) {
            val text = "Continues…"
            val height = this@drawLabels.bitmap.height
            val textSize = width / 7F
            val top = height - 1.5f * textSize
            drawLabel(text, textSize, width, top, paint)
        }
    }
}

private fun Canvas.drawLabel(
    text: String,
    textSize: Float,
    width: Int,
    top: Float,
    paint: Paint
) {
    paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 200)
    this.drawRect(0F, top, width.toFloat(), top + 1.5f * textSize, paint)

    paint.textSize = textSize
    paint.color = Color.BLUE
    this.drawText(text, width / 2F, top + 1.15f * textSize, paint)
}