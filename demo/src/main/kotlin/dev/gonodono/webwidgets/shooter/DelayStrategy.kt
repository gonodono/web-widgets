package dev.gonodono.webwidgets.shooter

import android.view.Choreographer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration

sealed interface DelayStrategy {
    data object None : DelayStrategy
    data class Time(val time: Duration) : DelayStrategy
    data class Frames(val frames: Int) : DelayStrategy
    data class DrawIdling(val debounce: Duration, val timeout: Duration) :
        DelayStrategy
}

internal suspend fun performDelay(
    strategy: DelayStrategy,
    drawFlow: () -> Flow<*>
) {
    when (strategy) {
        is DelayStrategy.None -> {
            return
        }
        is DelayStrategy.Time -> {
            delay(strategy.time)
        }
        is DelayStrategy.Frames -> {
            awaitFrames(strategy.frames)
        }
        is DelayStrategy.DrawIdling -> {
            withTimeoutOrNull(strategy.timeout) {
                @Suppress("OPT_IN_USAGE")
                drawFlow()
                    .onStart { emit(Unit) }
                    .debounce(strategy.debounce)
                    .take(1)
                    .collect()
            }
        }
    }
}

private suspend fun awaitFrames(count: Int) {
    if (count <= 0) return

    withContext(Dispatchers.Main) {
        var frames = count
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (!continuation.isActive) return

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