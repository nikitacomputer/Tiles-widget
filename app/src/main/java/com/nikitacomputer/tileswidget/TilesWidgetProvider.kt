package com.nikitacomputer.tileswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class TilesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        private val TILE_VIEW_IDS = intArrayOf(
            R.id.tile_0, R.id.tile_1, R.id.tile_2, R.id.tile_3, R.id.tile_4,
            R.id.tile_5, R.id.tile_6, R.id.tile_7, R.id.tile_8, R.id.tile_9,
            R.id.tile_10, R.id.tile_11, R.id.tile_12, R.id.tile_13, R.id.tile_14,
        )

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.tiles_widget)
            TILES.forEachIndexed { index, tile ->
                val viewId = TILE_VIEW_IDS[index]
                views.setImageViewResource(viewId, tile.icon)
                views.setContentDescription(viewId, tile.label)
                // Each tile goes through LaunchActivity so the target app is
                // resolved at tap time (handles installs/uninstalls/remaps).
                val intent = Intent(context, LaunchActivity::class.java)
                    .putExtra(LaunchActivity.EXTRA_TILE_INDEX, index)
                    .setData(Uri.parse("tiles://tile/$index"))
                val pending = PendingIntent.getActivity(
                    context, index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(viewId, pending)
            }
            return views
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TilesWidgetProvider::class.java))
            ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
        }
    }
}
