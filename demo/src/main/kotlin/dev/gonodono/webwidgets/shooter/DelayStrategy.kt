package dev.gonodono.webwidgets.shooter

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The default is [DelayStrategy.DrawIdling] with a `debounce` of 200
 * milliseconds and a `timeout` of 2,000 milliseconds.
 */
val DefaultDelayStrategy =
    DelayStrategy.DrawIdling(200.milliseconds, 2_000.milliseconds)

sealed interface DelayStrategy {
    data class Time(val time: Duration) : DelayStrategy
    data class Frames(val frames: Int) : DelayStrategy
    data class DrawIdling(val debounce: Duration, val timeout: Duration) :
        DelayStrategy
}