package com.rajasudhan.taskmind.wear

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture

/**
 * Next-due tile (#216). The phone publishes the soonest item as a DataItem at
 * [WearContract.PATH_NEXT_DUE]; this tile reads it and renders the title + when. No due logic on the
 * watch — the phone owns "what's due" (reusing its existing due-today query) and just pushes the answer.
 *
 * The tile re-reads on each render and on its freshness interval; pushing an update the instant the phone
 * changes the DataItem is a device-only nicety left to the follow-up.
 */
class NextDueTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()
        Wearable.getDataClient(this).dataItems
            .addOnSuccessListener { buffer ->
                var title: String? = null
                var whenText: String? = null
                for (item in buffer) {
                    if (item.uri.path == WearContract.PATH_NEXT_DUE) {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        title = map.getString(WearContract.KEY_TITLE)
                        whenText = map.getString(WearContract.KEY_WHEN)
                        break
                    }
                }
                buffer.release()
                future.set(buildTile(title, whenText))
            }
            .addOnFailureListener { future.set(buildTile(null, null)) }
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        return future
    }

    private fun buildTile(title: String?, whenText: String?): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(title, whenText)))
            .build()

    private fun tileLayout(title: String?, whenText: String?): LayoutElementBuilders.LayoutElement {
        val hasItem = !title.isNullOrBlank()
        val headline = if (hasItem) title!! else "All clear"
        val sub = if (hasItem) whenText.orEmpty().ifBlank { "TaskMind" } else "Nothing due"

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(headline)
                    .setMaxLines(3)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(18f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(sub)
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(13f))
                            .setColor(argb(ACCENT))
                            .build(),
                    )
                    .build(),
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val FRESHNESS_MS = 20 * 60 * 1000L
        const val ACCENT = 0xFFC5F82A.toInt()
    }
}
