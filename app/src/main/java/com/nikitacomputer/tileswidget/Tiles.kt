package com.nikitacomputer.tileswidget

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * One tile of the grid. [candidates] are package names tried in order; the
 * first one installed is launched. [fallback] is a generic intent (e.g. "any
 * calculator") used when none of the candidates is installed.
 */
data class Tile(
    val key: String,
    val label: String,
    val icon: Int, // drawable resource
    val candidates: List<String>,
    val fallback: (() -> Intent)? = null,
)

/** Tiles in row-major order: 5 columns × 3 rows. */
val TILES: List<Tile> = listOf(
    // Row 1
    Tile("youtube", "YouTube", R.drawable.tile_youtube,
        listOf("com.google.android.youtube", "app.revanced.android.youtube")),
    Tile("samsung_internet", "Samsung Internet", R.drawable.tile_samsung_internet,
        listOf("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta"),
        fallback = { selector(Intent.CATEGORY_APP_BROWSER) }),
    Tile("notion", "Notion", R.drawable.tile_notion,
        listOf("notion.id")),
    // Unidentified "K" app — not mapped by default; tapping it opens the picker.
    Tile("k_app", "K app", R.drawable.tile_k_app,
        emptyList()),
    Tile("translate", "Google Translate", R.drawable.tile_translate,
        listOf("com.google.android.apps.translate")),
    // Row 2
    Tile("sheets", "Google Sheets", R.drawable.tile_sheets,
        listOf("com.google.android.apps.docs.editors.sheets")),
    Tile("gmail", "Gmail", R.drawable.tile_gmail,
        listOf("com.google.android.gm")),
    Tile("gemini", "Gemini", R.drawable.tile_gemini,
        listOf("com.google.android.apps.bard")),
    Tile("clock", "Clock", R.drawable.tile_clock,
        listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        fallback = { Intent(AlarmClock.ACTION_SHOW_ALARMS) }),
    Tile("claude", "Claude", R.drawable.tile_claude,
        listOf("com.anthropic.claude")),
    // Row 3
    Tile("calculator", "Calculator", R.drawable.tile_calculator,
        listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        fallback = { selector(Intent.CATEGORY_APP_CALCULATOR) }),
    Tile("android_auto", "Android Auto", R.drawable.tile_android_auto,
        listOf("com.google.android.projection.gearhead")),
    Tile("play_store", "Play Store", R.drawable.tile_play_store,
        listOf("com.android.vending")),
    Tile("gallery", "Gallery", R.drawable.tile_gallery,
        listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        fallback = { selector(Intent.CATEGORY_APP_GALLERY) }),
    Tile("nyt_games", "NYT Games", R.drawable.tile_nyt_games,
        listOf("com.nytimes.crossword")),
)

private fun selector(category: String): Intent =
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

/** Persists user overrides (tile key -> flattened ComponentName). */
object TileStore {
    private const val PREFS = "tile_mapping"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOverride(context: Context, tile: Tile): ComponentName? =
        prefs(context).getString(tile.key, null)?.let(ComponentName::unflattenFromString)

    fun setOverride(context: Context, tile: Tile, component: ComponentName?) {
        prefs(context).edit().apply {
            if (component == null) remove(tile.key) else putString(tile.key, component.flattenToString())
        }.apply()
    }
}

/** What a tile currently points to, for display and launching. */
sealed class Target {
    abstract val intent: Intent
    data class Custom(override val intent: Intent, val label: CharSequence) : Target()
    data class Default(override val intent: Intent, val label: CharSequence) : Target()
    data class Fallback(override val intent: Intent) : Target()
}

fun resolveTarget(context: Context, tile: Tile): Target? {
    val pm = context.packageManager

    TileStore.getOverride(context, tile)?.let { component ->
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
        val info = runCatching { pm.getActivityInfo(component, 0) }.getOrNull()
        if (info != null) return Target.Custom(intent, info.loadLabel(pm))
        // Overridden app was uninstalled: fall through to defaults.
    }

    for (pkg in tile.candidates) {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) }
            .getOrDefault(tile.label)
        return Target.Default(intent, label)
    }

    return tile.fallback?.invoke()?.let { Target.Fallback(it) }
}

/** Starts [intent]; returns false if no activity could handle it. */
fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
