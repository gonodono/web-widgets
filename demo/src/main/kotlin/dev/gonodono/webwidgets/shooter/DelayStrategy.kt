package dev.gonodono.webwidgets.shooter

import kotlin.time.Duration

sealed interface DelayStrategy {
    data object None : DelayStrategy
    data class Time(val time: Duration) : DelayStrategy
    data class Frames(val frames: Int) : DelayStrategy
    data class DrawIdling(val debounce: Duration, val timeout: Duration) :
        DelayStrategy
}