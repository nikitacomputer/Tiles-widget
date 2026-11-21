package com.nikitacomputer.tileswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * App entry: every placed widget is listed and has its own settings (e.g. one on the phone home
 * screen and one on DeX), plus the defaults new widgets start with.
 */
class HomeActivity : Activity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Color.WHITE)
            addView(list)
        })
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = "Tiles Widget"; textSize = 22f; setTextColor(Color.BLACK)
        })
        list.addView(TextView(this).apply {
            text = "Each widget has its own grid, apps and look. Long-press a widget and choose its " +
                "settings, or pick it here. Tapping an empty tile also opens its widget's settings."
            textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, dp(8), 0, dp(16))
        })

        val ids = TilesWidgetProvider.ids(this)
        if (ids.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No widgets placed yet."; textSize = 15f; setTextColor(Color.BLACK)
            })
        }
        for (id in ids) button(describe(this, id)) { open(id) }

        button("Defaults for new widgets") { open(WidgetPrefs.DEFAULTS) }

        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            button("Add a widget to the home screen") {
                mgr.requestPinAppWidget(ComponentName(this, TilesWidgetProvider::class.java), null, null)
            }
        }
    }

    private fun open(id: Int) = startActivity(
        Intent(this, ConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    )

    private fun button(label: String, onClick: () -> Unit) = list.addView(Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** "Widget 2 · 5×3 tiles · 412×240 dp" — numbered in placement order; size tells phone from DeX. */
        fun describe(ctx: Context, id: Int): String {
            val ids = TilesWidgetProvider.ids(ctx).sorted()
            val n = ids.indexOf(id) + 1
            val p = WidgetPrefs(ctx)
            val size = WidgetRenderer.largestSize(AppWidgetManager.getInstance(ctx), id)
            return "Widget ${if (n > 0) n else id} · ${p.cols(id)}×${p.rows(id)} tiles · " +
                "${size.width.toInt()}×${size.height.toInt()} dp"
        }
    }
}
