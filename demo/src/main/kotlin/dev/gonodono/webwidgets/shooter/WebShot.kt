package dev.gonodono.webwidgets.shooter

import android.graphics.Bitmap

sealed interface WebShot {

    data class Complete(
        val url: String?,
        val bitmap: Bitmap,
        val overflows: Boolean
    ) : WebShot

    data class Error(
        val message: String?,
        val exception: Exception?
    ) : WebShot
}