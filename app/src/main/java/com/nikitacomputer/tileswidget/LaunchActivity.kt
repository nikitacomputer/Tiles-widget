package com.nikitacomputer.tileswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

/**
 * Invisible trampoline for tile taps: resolves the tile's app at tap time. Empty tiles, or tiles
 * whose app isn't installed, open this widget's settings at that tile.
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val r = intent.getIntExtra(EXTRA_ROW, 0)
        val c = intent.getIntExtra(EXTRA_COL, 0)
        val slot = WidgetPrefs(this).slots(id)[WidgetPrefs.key(r, c)]
        val target = slot?.let { WidgetRenderer.resolve(this, it) }
        val launched = target != null && try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (!launched) {
            startActivity(
                Intent(this, ConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(ConfigActivity.EXTRA_EDIT_TILE, WidgetPrefs.key(r, c))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ROW = "row"
        const val EXTRA_COL = "col"
    }
}
