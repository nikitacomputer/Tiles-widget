package com.nikitacomputer.tileswidget

import android.content.Context
import android.graphics.Color

/** User-adjustable look of the widget. */
data class WidgetStyle(
    /** Tile background colour, RGB only (alpha comes from [opacity]). */
    val backgroundRgb: Int = DEFAULT_BACKGROUND,
    /** Tile background opacity, 0–100 %. */
    val opacity: Int = 100,
    /** Outline + separator width in dp, [MIN_OUTLINE]–[MAX_OUTLINE]. */
    val outlineDp: Int = 1,
    val outlineWhite: Boolean = false,
    /** Corner radius in dp: 0–[MAX_RADIUS] in steps of [RADIUS_STEP]. Android 12+ only. */
    val cornerRadiusDp: Int = 0,
) {
    val backgroundArgb: Int
        get() = (Math.round(opacity * 2.55f) shl 24) or (backgroundRgb and 0xFFFFFF)

    val outlineColor: Int
        get() = if (outlineWhite) Color.WHITE else Color.BLACK

    companion object {
        const val DEFAULT_BACKGROUND = 0x87CAC1
        const val MIN_OUTLINE = 1
        const val MAX_OUTLINE = 5
        const val RADIUS_STEP = 5
        const val MAX_RADIUS = 30

        private const val PREFS = "widget_style"

        fun load(context: Context): WidgetStyle {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetStyle(
                backgroundRgb = p.getInt("background", DEFAULT_BACKGROUND),
                opacity = p.getInt("opacity", 100).coerceIn(0, 100),
                outlineDp = p.getInt("outline_dp", 1).coerceIn(MIN_OUTLINE, MAX_OUTLINE),
                outlineWhite = p.getBoolean("outline_white", false),
                cornerRadiusDp = (p.getInt("corner_radius_dp", 0) / RADIUS_STEP * RADIUS_STEP)
                    .coerceIn(0, MAX_RADIUS),
            )
        }

        fun save(context: Context, style: WidgetStyle) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("background", style.backgroundRgb and 0xFFFFFF)
                .putInt("opacity", style.opacity)
                .putInt("outline_dp", style.outlineDp)
                .putBoolean("outline_white", style.outlineWhite)
                .putInt("corner_radius_dp", style.cornerRadiusDp)
                .apply()
        }
    }
}
