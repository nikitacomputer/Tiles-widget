package com.nikitacomputer.tileswidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Lists the 15 tiles and lets the user pick which installed app each one
 * opens. Doubles as the widget's (optional) configuration activity.
 */
class MappingActivity : Activity() {

    private lateinit var adapter: TileAdapter
    private var installedApps: List<AppEntry>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // When opened as the widget configure activity, accept immediately so
        // pressing back still places the widget.
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            AppWidgetManager.getInstance(this)
                .updateAppWidget(widgetId, TilesWidgetProvider.buildViews(this))
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }

        adapter = TileAdapter()
        val list = ListView(this).apply {
            adapter = this@MappingActivity.adapter
            addHeaderView(
                TextView(this@MappingActivity).apply {
                    setText(R.string.mapping_header)
                    setPadding(dp(16), dp(16), dp(16), dp(8))
                },
                null, false,
            )
            setOnItemClickListener { _, _, position, _ ->
                val index = position - headerViewsCount
                TILES.getOrNull(index)?.let(::showPicker)
            }
        }
        setContentView(list)

        intent.getIntExtra(EXTRA_PICK_TILE, -1).takeIf { it >= 0 }
            ?.let { TILES.getOrNull(it) }
            ?.let(::showPicker)
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private fun loadInstalledApps(): List<AppEntry> = installedApps ?: run {
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppEntry(it, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
            .also { installedApps = it }
    }

    private fun showPicker(tile: Tile) {
        val apps = loadInstalledApps()
        val hasOverride = TileStore.getOverride(this, tile) != null
        val entries: List<AppEntry?> = (if (hasOverride) listOf<AppEntry?>(null) else emptyList()) + apps

        val pickerAdapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? Row) ?: Row()
                val entry = entries[position]
                if (entry == null) {
                    row.bind(getDrawable(tile.icon), getString(R.string.reset_default), null)
                } else {
                    row.bind(entry.icon(), entry.label, null)
                }
                return row
            }
        }

        AlertDialog.Builder(this)
            .setTitle("${tile.label}: ${getString(R.string.choose_app)}")
            .setAdapter(pickerAdapter) { _, which ->
                val entry = entries[which]
                val component = entry?.let {
                    ComponentName(it.info.activityInfo.packageName, it.info.activityInfo.name)
                }
                TileStore.setOverride(this, tile, component)
                TilesWidgetProvider.updateAll(this)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class AppEntry(val info: ResolveInfo, val label: String) {
        private var cached: Drawable? = null
        fun icon(): Drawable = cached ?: info.loadIcon(packageManager).also { cached = it }
    }

    private inner class TileAdapter : BaseAdapter() {
        override fun getCount() = TILES.size
        override fun getItem(position: Int) = TILES[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? Row) ?: Row()
            val tile = TILES[position]
            val subtitle = when (val target = resolveTarget(this@MappingActivity, tile)) {
                is Target.Custom -> "${target.label} (${getString(R.string.custom_suffix)})"
                is Target.Default -> "${target.label} (${getString(R.string.default_suffix)})"
                is Target.Fallback -> "System default app"
                null -> getString(R.string.not_mapped)
            }
            row.bind(getDrawable(tile.icon), tile.label, subtitle)
            return row
        }
    }

    /** Icon + title + optional subtitle list row. */
    private inner class Row : LinearLayout(this@MappingActivity) {
        private val iconView = ImageView(context)
        private val title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        private val subtitle = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            alpha = 0.7f
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minimumHeight = dp(56)
            addView(iconView, LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(16) })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(title)
                addView(subtitle)
            })
        }

        fun bind(icon: Drawable?, text: CharSequence, sub: CharSequence?) {
            iconView.setImageDrawable(icon)
            title.text = text
            subtitle.text = sub
            subtitle.visibility = if (sub == null) GONE else VISIBLE
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
    ).toInt()

    companion object {
        const val EXTRA_PICK_TILE = "pick_tile"
    }
}
