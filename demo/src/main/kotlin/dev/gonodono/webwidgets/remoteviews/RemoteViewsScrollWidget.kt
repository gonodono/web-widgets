package dev.gonodono.webwidgets.remoteviews

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import dev.gonodono.webwidgets.R
import dev.gonodono.webwidgets.shooter.WebShot

class RemoteViewsScrollWidget :
    BaseRemoteViewsWidget(
        clampImageHeightToWidget = false,
        contentLayoutId = R.layout.list,
        contentId = R.id.list
    ) {

    override fun RemoteViews.buildContent(
        context: Context,
        webShot: WebShot.Complete,
        appWidgetId: Int
    ) {
        val item = RemoteViews(context.packageName, R.layout.image)
        item.setImageViewBitmap(R.id.image, webShot.bitmap)

        if (webShot.url != null) {
            val open = createOpenAction(context, appWidgetId, webShot.url)
            setPendingIntentTemplate(R.id.list, open)
            item.setOnClickFillInIntent(R.id.image, Intent())
        }

        RemoteViewsCompat.setRemoteAdapter(
            context = context,
            remoteViews = this,
            appWidgetId = appWidgetId,
            viewId = R.id.list,
            items = RemoteViewsCompat.RemoteCollectionItems.Builder()
                .addItem(0L, item)
                .build()
        )
    }
}