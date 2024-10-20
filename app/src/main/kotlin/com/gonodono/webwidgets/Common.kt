package com.gonodono.webwidgets

import android.content.Context
import android.content.Intent
import android.util.Log

internal const val TAG = "WebWidgets"

internal const val WIKIPEDIA_RANDOM_URL =
    "https://en.m.wikipedia.org/wiki/Special:Random"

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