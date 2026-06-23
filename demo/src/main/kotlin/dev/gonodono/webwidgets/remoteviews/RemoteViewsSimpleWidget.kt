package dev.gonodono.webwidgets.remoteviews

import android.content.Context
import android.widget.RemoteViews
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.shooter.WebShot

class RemoteViewsSimpleWidget :
    BaseRemoteViewsWidget(
        clampImageHeightToWidget = true,
        contentLayoutId = R.layout.image,
        contentId = R.id.image
    ) {

    override fun RemoteViews.buildContent(
        context: Context,
        webShot: WebShot.Complete,
        appWidgetId: Int
    ) {
        setImageViewBitmap(R.id.image, webShot.bitmap)
        if (webShot.url == null) return

        val open = createOpenAction(context, appWidgetId, webShot.url)
        setOnClickPendingIntent(R.id.image, open)
    }
}