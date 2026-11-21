package com.nikitacomputer.tileswidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle

class TilesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx, ids)

    /** Size changed (resize, rotation, moved to DeX): re-render icons for the new tile size. */
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) =
        update(ctx, intArrayOf(id))

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val p = WidgetPrefs(ctx)
        ids.forEach { p.delete(it) }
    }

    companion object {
        fun ids(ctx: Context): IntArray =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, TilesWidgetProvider::class.java))

        fun updateAll(ctx: Context) = update(ctx, ids(ctx))

        fun update(ctx: Context, ids: IntArray) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in ids) mgr.updateAppWidget(id, WidgetRenderer.build(ctx, mgr, id))
        }
    }
}
