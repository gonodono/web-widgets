package com.gonodono.webwidgets

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Choreographer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal const val TAG = "WebWidgets"

internal const val WIKIPEDIA_RANDOM_URL =
    "https://news.google.com"

internal const val ACTION_OPEN = "${BuildConfig.APPLICATION_ID}.action.OPEN"

internal fun handleActionOpen(context: Context, intent: Intent) {
    try {
        val view = Intent(Intent.ACTION_VIEW, intent.data!!)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "ACTION_VIEW error", e)
    }
}

// Waits at least frameCount, possibly up to frameCount + 1.
@Suppress("unused")
internal suspend fun awaitDisplayFrames(frameCount: Int) {
    check(frameCount > 0) { "frameCount must be positive" }

    withContext(Dispatchers.Main) {
        var count = frameCount
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine { continuation ->
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(it: Long) {
                    ensureActive()
                    if (count-- > 0) {
                        choreographer.postFrameCallback(this)
                    } else {
                        if (continuation.isActive) continuation.resume(Unit)
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