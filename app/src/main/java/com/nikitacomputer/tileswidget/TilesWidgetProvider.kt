package com.nikitacomputer.tileswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.TypedValue
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
        private val HLINE_IDS = intArrayOf(R.id.hline_0, R.id.hline_1)
        private val VLINE_IDS = intArrayOf(
            R.id.vline_0, R.id.vline_1, R.id.vline_2, R.id.vline_3, R.id.vline_4, R.id.vline_5,
            R.id.vline_6, R.id.vline_7, R.id.vline_8, R.id.vline_9, R.id.vline_10, R.id.vline_11,
        )

        /** Rounded corners need RemoteViews outline clipping (Android 12). */
        val supportsCornerRadius: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        fun buildViews(context: Context, storedStyle: WidgetStyle = WidgetStyle.load(context)): RemoteViews {
            val style = if (supportsCornerRadius) storedStyle else storedStyle.copy(cornerRadiusDp = 0)
            val views = RemoteViews(context.packageName, R.layout.tiles_widget)

            // Lines are empty FrameLayouts whose size is their padding.
            val linePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, style.outlineDp.toFloat(), context.resources.displayMetrics,
            ).toInt().coerceAtLeast(1)
            // Outer outline: the grid is inset by the line width and a
            // rounded ring is drawn on top; the root is clipped to the radius.
            views.setViewPadding(R.id.grid, linePx, linePx, linePx, linePx)
            views.setImageViewResource(R.id.outline_ring, OutlineRings.forStyle(style))
            views.setInt(R.id.outline_ring, "setColorFilter", style.outlineColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setViewOutlinePreferredRadius(
                    R.id.widget_root, style.cornerRadiusDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP,
                )
            }
            HLINE_IDS.forEach {
                views.setViewPadding(it, 0, linePx, 0, 0)
                views.setInt(it, "setBackgroundColor", style.outlineColor)
            }
            VLINE_IDS.forEach {
                views.setViewPadding(it, linePx, 0, 0, 0)
                views.setInt(it, "setBackgroundColor", style.outlineColor)
            }

            TILES.forEachIndexed { index, tile ->
                val viewId = TILE_VIEW_IDS[index]
                views.setImageViewResource(viewId, tile.icon)
                views.setInt(viewId, "setBackgroundColor", style.backgroundArgb)
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
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
