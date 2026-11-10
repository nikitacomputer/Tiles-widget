package com.nikitacomputer.tileswidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible activity started by a tile tap; launches the mapped app. */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tile = TILES.getOrNull(intent.getIntExtra(EXTRA_TILE_INDEX, -1))
        if (tile != null) {
            val target = resolveTarget(this, tile)
            if (target == null || !tryStart(target.intent)) {
                // Nothing to open: let the user pick an app for this tile.
                val message = if (target == null) getString(R.string.tile_needs_mapping, tile.label)
                else getString(R.string.app_missing)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                tryStart(
                    Intent(this, MappingActivity::class.java)
                        .putExtra(MappingActivity.EXTRA_PICK_TILE, TILES.indexOf(tile))
                )
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TILE_INDEX = "tile_index"
    }
}
