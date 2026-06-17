package dev.gonodono.webwidgets.shooter

import android.graphics.Bitmap

data class WebShot(
    val url: String?,
    val bitmap: Bitmap,
    val overflows: Boolean
)