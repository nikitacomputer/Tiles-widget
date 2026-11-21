package com.nikitacomputer.tileswidget

/**
 * Bundled hand-drawn icons and the original 5x3 layout new widgets start with.
 * Artwork is matched to apps by package, so picking e.g. YouTube for any tile can use it.
 */
object TileArt {
    private class Art(val drawable: String, val label: String, val packages: List<String>)

    private val ART = listOf(
        Art("tile_youtube", "YouTube", listOf("com.google.android.youtube", "app.revanced.android.youtube")),
        Art("tile_samsung_internet", "Samsung Internet", listOf("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta")),
        Art("tile_notion", "Notion", listOf("notion.id")),
        Art("tile_k_app", "K-Bid", emptyList()),
        Art("tile_translate", "Google Translate", listOf("com.google.android.apps.translate")),
        Art("tile_sheets", "Google Sheets", listOf("com.google.android.apps.docs.editors.sheets")),
        Art("tile_gmail", "Gmail", listOf("com.google.android.gm")),
        Art("tile_gemini", "Gemini", listOf("com.google.android.apps.bard")),
        Art("tile_clock", "Clock", listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock")),
        Art("tile_claude", "Claude", listOf("com.anthropic.claude")),
        Art("tile_github", "GitHub", listOf("com.github.android")),
        Art("tile_android_auto", "Android Auto", listOf("com.google.android.projection.gearhead")),
        Art("tile_play_store", "Play Store", listOf("com.android.vending")),
        Art("tile_gallery", "Gallery", listOf("com.sec.android.gallery3d", "com.google.android.apps.photos")),
        Art("tile_nyt_games", "NYT Games", listOf("com.nytimes.crossword")),
    )

    /** The original layout: 5 columns x 3 rows, row-major. */
    fun defaultSlots(): Map<String, Slot> = ART.mapIndexed { i, a ->
        WidgetPrefs.key(i / 5, i % 5) to Slot(packages = a.packages, art = a.drawable, label = a.label)
    }.toMap()

    /** Bundled artwork for [pkg], if any. */
    fun forPackage(pkg: String): String? = ART.firstOrNull { pkg in it.packages }?.drawable

    /** All bundled artwork (drawable name to label), for picking art for any app. */
    val all: List<Pair<String, String>> get() = ART.map { it.drawable to it.label }

    /** Visible part of the artwork canvas (the drawings have a wide margin). */
    const val CONTENT = 0.6f
}
