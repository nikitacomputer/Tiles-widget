package com.nikitacomputer.tileswidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What one tile opens and how it looks. */
data class Slot(
    /** Exact activity picked by the user ("pkg/.Activity"), if any. */
    val component: String? = null,
    /** Packages tried in order when there is no [component] (built-in defaults). */
    val packages: List<String> = emptyList(),
    /** Bundled artwork drawable name ("tile_youtube"); null = the app's own icon. */
    val art: String? = null,
    /** Shown in settings and to accessibility services. */
    val label: String? = null,
) {
    val isEmpty: Boolean get() = component == null && packages.isEmpty() && art == null

    fun toJson(): JSONObject = JSONObject().apply {
        component?.let { put("c", it) }
        if (packages.isNotEmpty()) put("p", JSONArray(packages))
        art?.let { put("a", it) }
        label?.let { put("l", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Slot(
            component = o.optString("c").ifEmpty { null },
            packages = o.optJSONArray("p")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            art = o.optString("a").ifEmpty { null },
            label = o.optString("l").ifEmpty { null },
        )
    }
}

/**
 * Per-widget settings, modelled on the Particular Calendar widget: every widget id has its own
 * values; widget id [DEFAULTS] holds the values new widgets start with (edited from the app).
 * A widget without its own value for a key follows the defaults.
 */
class WidgetPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    private fun int(key: String, id: Int, def: Int) = sp.getInt("${key}_$id", sp.getInt("${key}_$DEFAULTS", def))
    private fun putInt(key: String, id: Int, v: Int) = sp.edit().putInt("${key}_$id", v).apply()

    // ---- grid ----
    fun cols(id: Int) = int("cols", id, 5).coerceIn(1, MAX_GRID)
    fun setCols(id: Int, v: Int) = putInt("cols", id, v)
    fun rows(id: Int) = int("rows", id, 3).coerceIn(1, MAX_GRID)
    fun setRows(id: Int, v: Int) = putInt("rows", id, v)

    /**
     * Tiles by position "row,col". Positions outside the current grid are kept, so shrinking the
     * grid and growing it back restores them.
     */
    fun slots(id: Int): MutableMap<String, Slot> {
        val json = sp.getString("slots_$id", null) ?: sp.getString("slots_$DEFAULTS", null)
            ?: return TileArt.defaultSlots().toMutableMap()
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { Slot.fromJson(o.getJSONObject(it)) }.toMutableMap()
    }

    fun setSlots(id: Int, slots: Map<String, Slot>) {
        val o = JSONObject()
        for ((k, v) in slots) if (!v.isEmpty) o.put(k, v.toJson())
        sp.edit().putString("slots_$id", o.toString()).apply()
    }

    // ---- look (same controls as the calendar widget, minus the accent colour) ----
    /** Background colour incl. opacity (alpha). */
    fun bgColor(id: Int) = int("bg", id, DEFAULT_BG)
    fun setBgColor(id: Int, v: Int) = putInt("bg", id, v)

    /** Corner radius in dp, one of [RADII]. */
    fun radius(id: Int) = int("radius", id, 0)
    fun setRadius(id: Int, v: Int) = putInt("radius", id, v)

    /** Outline (around the widget and between tiles) width in dp, 0 = none, max 5, and colour. */
    fun rimWidth(id: Int) = int("rim", id, 1).coerceIn(0, 5)
    fun setRimWidth(id: Int, v: Int) = putInt("rim", id, v)
    fun rimColor(id: Int) = int("rimcol", id, 0xFF000000.toInt())
    fun setRimColor(id: Int, v: Int) = putInt("rimcol", id, v)

    /** Icon size as a percentage of the tile. */
    fun iconSize(id: Int) = int("icon", id, 60).coerceIn(MIN_ICON, MAX_ICON)
    fun setIconSize(id: Int, v: Int) = putInt("icon", id, v)

    /** True once this widget has its own saved settings. */
    fun hasOwn(id: Int) = sp.contains("cols_$id")

    /** Copies every setting of [from] (with defaults applied) to [to]. */
    fun copy(from: Int, to: Int) {
        setCols(to, cols(from))
        setRows(to, rows(from))
        setSlots(to, slots(from))
        setBgColor(to, bgColor(from))
        setRadius(to, radius(from))
        setRimWidth(to, rimWidth(from))
        setRimColor(to, rimColor(from))
        setIconSize(to, iconSize(from))
    }

    fun delete(id: Int) {
        val e = sp.edit()
        KEYS.forEach { e.remove("${it}_$id") }
        e.apply()
    }

    companion object {
        const val DEFAULTS = 0
        /** Scratch id for the settings screen's unsaved values (live preview). */
        const val PREVIEW = -7
        const val MAX_GRID = 20
        const val MIN_ICON = 30
        const val MAX_ICON = 100
        const val DEFAULT_BG = 0xFF87CAC1.toInt()
        val RADII = intArrayOf(0, 4, 8, 12, 16, 20, 24, 28, 32)
        private val KEYS = listOf("cols", "rows", "slots", "bg", "radius", "rim", "rimcol", "icon")

        fun key(row: Int, col: Int) = "$row,$col"
    }
}
