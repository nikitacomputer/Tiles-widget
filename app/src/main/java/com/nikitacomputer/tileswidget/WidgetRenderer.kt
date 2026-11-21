package com.nikitacomputer.tileswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Builds a widget's RemoteViews: background, outline, and a cols x rows grid of app tiles. */
object WidgetRenderer {

    fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews =
        render(ctx, id, largestSize(mgr, id), clickable = true)

    /** Settings preview of [id] (usually the unsaved draft) at [size] dp. */
    fun renderPreview(ctx: Context, id: Int, size: SizeF): RemoteViews = render(ctx, id, size, clickable = false)

    private fun render(ctx: Context, id: Int, size: SizeF, clickable: Boolean): RemoteViews {
        val prefs = WidgetPrefs(ctx)
        val pkg = ctx.packageName
        val rv = RemoteViews(pkg, R.layout.widget)
        val cols = prefs.cols(id)
        val rows = prefs.rows(id)
        val rim = prefs.rimWidth(id).toFloat()
        val rimColor = prefs.rimColor(id) or Color.BLACK
        val radiusIdx = radiusIndex(prefs.radius(id))

        // Background: rounded shape tinted with the colour, opacity via image alpha.
        val bg = prefs.bgColor(id)
        rv.setImageViewResource(R.id.bg, BG[radiusIdx])
        rv.setInt(R.id.bg, "setColorFilter", bg or Color.BLACK)
        rv.setInt(R.id.bg, "setImageAlpha", Color.alpha(bg))
        // Outline: a ring only (a filled shape would tint a see-through background).
        rv.setViewVisibility(R.id.rim, if (rim > 0f) View.VISIBLE else View.GONE)
        if (rim > 0f) rv.setImageViewResource(R.id.rim, RIMS[radiusIdx][rim.toInt() - 1])
        rv.setInt(R.id.rim, "setColorFilter", rimColor)
        inset(ctx, rv, R.id.bg, rim)
        inset(ctx, rv, R.id.grid, rim)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Clip separators that would poke past rounded corners.
            rv.setViewOutlinePreferredRadius(R.id.root, prefs.radius(id).toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }

        val slots = prefs.slots(id)
        val iconPx = iconPixels(ctx, size, cols, rows, rim, slots.size.coerceAtMost(cols * rows))
        val icons = HashMap<String, Bitmap?>()
        val iconPct = prefs.iconSize(id)

        rv.removeAllViews(R.id.grid)
        for (r in 0 until rows) {
            if (r > 0 && rim > 0f) rv.addView(R.id.grid, line(ctx, R.layout.tile_hline, R.id.hline, rim, rimColor, vertical = false))
            val row = RemoteViews(pkg, R.layout.tile_row)
            for (c in 0 until cols) {
                if (c > 0 && rim > 0f) row.addView(R.id.row, line(ctx, R.layout.tile_vline, R.id.vline, rim, rimColor, vertical = true))
                val cell = RemoteViews(pkg, R.layout.tile_cell)
                val slot = slots[WidgetPrefs.key(r, c)]
                if (slot != null && !slot.isEmpty) {
                    val target = resolve(ctx, slot)
                    val bmp = icons.getOrPut("${slot.art}|${target?.flattenToString()}") {
                        iconBitmap(ctx, slot, target, iconPx, iconPct)
                    }
                    if (bmp != null) cell.setImageViewBitmap(R.id.cell, bmp)
                    cell.setContentDescription(R.id.cell, slot.label ?: target?.let { label(ctx, it) } ?: "")
                }
                if (clickable) cell.setOnClickPendingIntent(R.id.cell, tapIntent(ctx, id, r, c))
                row.addView(R.id.row, cell)
            }
            rv.addView(R.id.grid, row)
        }
        return rv
    }

    private fun line(ctx: Context, layout: Int, viewId: Int, w: Float, color: Int, vertical: Boolean): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        v.setInt(viewId, "setBackgroundColor", color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // DIP is resolved by the launcher with the density of the screen the widget is on (DeX).
            if (vertical) v.setViewLayoutWidth(viewId, w, TypedValue.COMPLEX_UNIT_DIP)
            else v.setViewLayoutHeight(viewId, w, TypedValue.COMPLEX_UNIT_DIP)
        } else {
            val px = (w * ctx.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
            if (vertical) v.setViewPadding(viewId, px, 0, 0, 0) else v.setViewPadding(viewId, 0, px, 0, 0)
        }
        return v
    }

    /**
     * Insets a view by [dp] on all sides. Android 12+: layout margins in DIP, resolved by the
     * launcher for the screen the widget is on (converting here would use the phone's density on
     * DeX). Older Android: padding in pixels.
     */
    private fun inset(ctx: Context, rv: RemoteViews, viewId: Int, dp: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val u = TypedValue.COMPLEX_UNIT_DIP
            for (m in intArrayOf(RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM)) {
                rv.setViewLayoutMargin(viewId, m, dp, u)
            }
        } else {
            val px = (dp * ctx.resources.displayMetrics.density).toInt()
            rv.setViewPadding(viewId, px, px, px, px)
        }
    }

    private fun tapIntent(ctx: Context, id: Int, r: Int, c: Int): PendingIntent {
        val i = Intent(ctx, LaunchActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(LaunchActivity.EXTRA_ROW, r)
            .putExtra(LaunchActivity.EXTRA_COL, c)
            .setData(Uri.parse("tiles://widget/$id/$r/$c"))
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // ------------------------------------------------------------------ apps & icons

    /** The launchable activity for [slot], or null if its app isn't installed. */
    fun resolve(ctx: Context, slot: Slot): ComponentName? {
        val pm = ctx.packageManager
        slot.component?.let(ComponentName::unflattenFromString)?.let { cn ->
            if (runCatching { pm.getActivityInfo(cn, 0) }.isSuccess) return cn
        }
        for (p in slot.packages) pm.getLaunchIntentForPackage(p)?.component?.let { return it }
        return null
    }

    fun label(ctx: Context, cn: ComponentName): String? =
        runCatching { ctx.packageManager.getActivityInfo(cn, 0).loadLabel(ctx.packageManager).toString() }.getOrNull()

    /**
     * Square icon bitmap: bundled artwork or the app's own icon, [pct] % of the tile. Artwork of an
     * app that isn't installed is drawn faded.
     */
    fun iconBitmap(ctx: Context, slot: Slot, target: ComponentName?, px: Int, pct: Int): Bitmap? {
        val art: Drawable? = slot.art?.let { name ->
            val res = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (res != 0) ctx.getDrawable(res) else null
        }
        val d: Drawable = art ?: target?.let {
            runCatching { ctx.packageManager.getActivityIcon(it) }.getOrNull()
        } ?: return null
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        // Artwork has a wide built-in margin: scale it so the drawing itself is pct % of the tile.
        val size = px * pct / 100f / (if (art != null) TileArt.CONTENT else 1f)
        val off = (px - size) / 2f
        d.setBounds(off.roundToInt(), off.roundToInt(), (off + size).roundToInt(), (off + size).roundToInt())
        if (target == null) d.alpha = 90
        d.draw(c)
        d.alpha = 255
        return b
    }

    /**
     * Icon bitmap size: the tile's size in pixels at this screen's density, lowered if needed so all
     * icons together stay well inside the system's widget bitmap memory limit.
     */
    private fun iconPixels(ctx: Context, size: SizeF, cols: Int, rows: Int, rim: Float, count: Int): Int {
        val dm = ctx.resources.displayMetrics
        val cellDp = min((size.width - rim * (cols + 1)) / cols, (size.height - rim * (rows + 1)) / rows)
        val wanted = cellDp * dm.density
        val budget = 6f * dm.widthPixels * dm.heightPixels * 0.4f
        val cap = sqrt(budget / (4f * count.coerceAtLeast(1)))
        return min(min(wanted, cap), 384f).roundToInt().coerceAtLeast(16)
    }

    /** Largest size the launcher reports for the widget, in dp. */
    @Suppress("DEPRECATION")
    fun largestSize(mgr: AppWidgetManager, id: Int): SizeF {
        val o = mgr.getAppWidgetOptions(id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val list = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!list.isNullOrEmpty()) return SizeF(list.maxOf { it.width }, list.maxOf { it.height })
        }
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (w > 0 && h > 0) SizeF(w.toFloat(), h.toFloat()) else SizeF(320f, 200f)
    }

    // ------------------------------------------------------------------ shapes

    private fun radiusIndex(r: Int) = WidgetPrefs.RADII.indices.minBy { kotlin.math.abs(WidgetPrefs.RADII[it] - r) }

    private val BG = intArrayOf(
        R.drawable.bg_r0, R.drawable.bg_r4, R.drawable.bg_r8, R.drawable.bg_r12, R.drawable.bg_r16,
        R.drawable.bg_r20, R.drawable.bg_r24, R.drawable.bg_r28, R.drawable.bg_r32,
    )

    /** Outline rings: [radius index][width 1..5 - 1]. */
    private val RIMS = arrayOf(
        intArrayOf(R.drawable.rim_r0_w1, R.drawable.rim_r0_w2, R.drawable.rim_r0_w3, R.drawable.rim_r0_w4, R.drawable.rim_r0_w5),
        intArrayOf(R.drawable.rim_r4_w1, R.drawable.rim_r4_w2, R.drawable.rim_r4_w3, R.drawable.rim_r4_w4, R.drawable.rim_r4_w5),
        intArrayOf(R.drawable.rim_r8_w1, R.drawable.rim_r8_w2, R.drawable.rim_r8_w3, R.drawable.rim_r8_w4, R.drawable.rim_r8_w5),
        intArrayOf(R.drawable.rim_r12_w1, R.drawable.rim_r12_w2, R.drawable.rim_r12_w3, R.drawable.rim_r12_w4, R.drawable.rim_r12_w5),
        intArrayOf(R.drawable.rim_r16_w1, R.drawable.rim_r16_w2, R.drawable.rim_r16_w3, R.drawable.rim_r16_w4, R.drawable.rim_r16_w5),
        intArrayOf(R.drawable.rim_r20_w1, R.drawable.rim_r20_w2, R.drawable.rim_r20_w3, R.drawable.rim_r20_w4, R.drawable.rim_r20_w5),
        intArrayOf(R.drawable.rim_r24_w1, R.drawable.rim_r24_w2, R.drawable.rim_r24_w3, R.drawable.rim_r24_w4, R.drawable.rim_r24_w5),
        intArrayOf(R.drawable.rim_r28_w1, R.drawable.rim_r28_w2, R.drawable.rim_r28_w3, R.drawable.rim_r28_w4, R.drawable.rim_r28_w5),
        intArrayOf(R.drawable.rim_r32_w1, R.drawable.rim_r32_w2, R.drawable.rim_r32_w3, R.drawable.rim_r32_w4, R.drawable.rim_r32_w5),
    )
}
