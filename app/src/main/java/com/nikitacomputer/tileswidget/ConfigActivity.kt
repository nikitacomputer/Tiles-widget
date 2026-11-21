package com.nikitacomputer.tileswidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.SizeF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.min

/**
 * Settings for one widget (or, from the app, the defaults new widgets start with). Live preview
 * pinned on top (reflects unsaved changes), settings scroll below, Save pinned at the bottom —
 * the same structure and look controls as the Particular Calendar widget.
 *
 * Unsaved values live under the scratch id [WidgetPrefs.PREVIEW]; Save copies them to the widget.
 */
class ConfigActivity : Activity() {

    private var widgetId = WidgetPrefs.DEFAULTS
    private lateinit var prefs: WidgetPrefs
    private lateinit var settings: LinearLayout
    private lateinit var preview: PreviewFrame
    private lateinit var tileEditor: LinearLayout
    private val draft = WidgetPrefs.PREVIEW
    private val handler = Handler(Looper.getMainLooper())
    private var installedApps: List<AppEntry>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WidgetPrefs.DEFAULTS)
            .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID } ?: WidgetPrefs.DEFAULTS
        prefs = WidgetPrefs(this)
        // The widget is added with defaults even if the user backs out (configuration_optional).
        if (widgetId != WidgetPrefs.DEFAULTS) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        prefs.copy(widgetId, draft)

        preview = PreviewFrame(this).apply { setBackgroundColor(Color.rgb(28, 58, 40)) }
        settings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener { save() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(Color.WHITE)
            addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.32f).toInt()))
            addView(ScrollView(this@ConfigActivity).apply { addView(settings) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(save, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(dp(16), dp(4), dp(16), dp(8))
            })
        }
        setContentView(root)
        build()
        refreshPreview()

        intent.getStringExtra(EXTRA_EDIT_TILE)?.let { key ->
            val (r, c) = key.split(",").map { it.toInt() }
            if (r < prefs.rows(draft) && c < prefs.cols(draft)) tileDialog(key)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ settings

    private fun build() {
        settings.removeAllViews()

        settings.addView(TextView(this).apply {
            text = if (widgetId == WidgetPrefs.DEFAULTS) "Defaults for new widgets" else HomeActivity.describe(this@ConfigActivity, widgetId)
            textSize = 18f; setTextColor(Color.BLACK); setPadding(0, dp(8), 0, 0)
        })
        if (widgetId == WidgetPrefs.DEFAULTS) {
            text("Widgets you haven't customised follow these. Each widget can also be set up on its own.")
        }
        val sources = copySources()
        if (sources.isNotEmpty()) {
            settings.addView(Button(this).apply {
                text = "Copy settings from…"
                setOnClickListener {
                    AlertDialog.Builder(this@ConfigActivity)
                        .setTitle("Copy settings from")
                        .setItems(sources.map { it.second }.toTypedArray()) { _, which ->
                            prefs.copy(sources[which].first, draft)
                            build()
                            refreshPreview()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }

        section("Grid")
        text("The widget is divided into this many tiles whatever its size.")
        val colsLabel = text("Columns: ${prefs.cols(draft)}")
        slider(WidgetPrefs.MAX_GRID - 1, prefs.cols(draft) - 1) {
            prefs.setCols(draft, it + 1); colsLabel.text = "Columns: ${it + 1}"; refreshTiles(); refreshPreview()
        }
        val rowsLabel = text("Rows: ${prefs.rows(draft)}")
        slider(WidgetPrefs.MAX_GRID - 1, prefs.rows(draft) - 1) {
            prefs.setRows(draft, it + 1); rowsLabel.text = "Rows: ${it + 1}"; refreshTiles(); refreshPreview()
        }

        section("Tiles")
        text("Tap a tile to choose its app or icon.")
        tileEditor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        settings.addView(tileEditor, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        refreshTiles()

        section("Icon size")
        val iconLabel = text("${prefs.iconSize(draft)}% of the tile")
        slider(WidgetPrefs.MAX_ICON - WidgetPrefs.MIN_ICON, prefs.iconSize(draft) - WidgetPrefs.MIN_ICON) {
            val v = it + WidgetPrefs.MIN_ICON
            prefs.setIconSize(draft, v); iconLabel.text = "$v% of the tile"; refreshPreview()
        }

        section("Background colour")
        colorPicker(BG_LIGHT, BG_DARK, prefs.bgColor(draft), withAlpha = true) { prefs.setBgColor(draft, it); refreshPreview() }

        section("Outline")
        text("Around the widget and between tiles.")
        fun rimText(w: Int) = if (w == 0) "None" else "$w dp"
        val rimLabel = text(rimText(prefs.rimWidth(draft)))
        slider(5, prefs.rimWidth(draft)) {
            prefs.setRimWidth(draft, it); rimLabel.text = rimText(it); refreshPreview()
        }
        text("Width in dp (1 dp ≈ 1.3 px on DeX, ≈ 2.8 px on the phone)")
        colorPicker(RIM_LIGHT, RIM_DARK, prefs.rimColor(draft), withAlpha = false) { prefs.setRimColor(draft, it); refreshPreview() }

        section("Corner radius")
        val radii = WidgetPrefs.RADII
        val radiusLabel = text("${prefs.radius(draft)} dp")
        slider(radii.size - 1, radii.indexOfFirst { it >= prefs.radius(draft) }.coerceAtLeast(0)) {
            prefs.setRadius(draft, radii[it]); radiusLabel.text = "${radii[it]} dp"; refreshPreview()
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            text("Separators are only clipped to rounded corners on Android 12+.")
        }
    }

    /** Other widgets (and the defaults) whose settings can be copied into this one. */
    private fun copySources(): List<Pair<Int, String>> {
        val list = ArrayList<Pair<Int, String>>()
        if (widgetId != WidgetPrefs.DEFAULTS) list += WidgetPrefs.DEFAULTS to "Defaults for new widgets"
        for (id in TilesWidgetProvider.ids(this)) if (id != widgetId) list += id to HomeActivity.describe(this, id)
        return list
    }

    private fun save() {
        prefs.copy(draft, widgetId)
        if (widgetId == WidgetPrefs.DEFAULTS) TilesWidgetProvider.updateAll(this)
        else TilesWidgetProvider.update(this, intArrayOf(widgetId))
        finish()
    }

    // ------------------------------------------------------------------ tiles

    /** Rebuilds the tap-to-edit grid of tiles. */
    private fun refreshTiles() {
        tileEditor.removeAllViews()
        val cols = prefs.cols(draft)
        val rows = prefs.rows(draft)
        val slots = prefs.slots(draft)
        val avail = resources.displayMetrics.widthPixels - dp(32)
        val cell = min(dp(56), avail / cols)
        val icons = HashMap<String, Drawable?>()
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val key = WidgetPrefs.key(r, c)
                val slot = slots[key]
                val icon = slot?.takeIf { !it.isEmpty }?.let { s ->
                    val target = WidgetRenderer.resolve(this, s)
                    icons.getOrPut("${s.art}|${target?.flattenToString()}") {
                        WidgetRenderer.iconBitmap(this, s, target, cell, 80)?.let { BitmapDrawable(resources, it) }
                    }
                }
                row.addView(ImageView(this).apply {
                    setImageDrawable(icon)
                    background = GradientDrawable().apply {
                        setColor(0xFFF3F3F3.toInt()); setStroke(1, 0xFFBDBDBD.toInt())
                    }
                    contentDescription = slot?.label ?: "Empty tile"
                    setOnClickListener { tileDialog(key) }
                }, LinearLayout.LayoutParams(cell, cell))
            }
            tileEditor.addView(row)
        }
    }

    private fun tileDialog(key: String) {
        val slots = prefs.slots(draft)
        val slot = slots[key] ?: Slot()
        val target = WidgetRenderer.resolve(this, slot)
        val name = slot.label ?: target?.let { WidgetRenderer.label(this, it) }
        val options = ArrayList<Pair<String, () -> Unit>>()
        options += "Choose app…" to { pickApp(key) }
        if (!slot.isEmpty) {
            options += "Choose icon…" to { pickIcon(key) }
            options += "Clear tile" to { updateSlot(key, null) }
        }
        val (r, c) = key.split(",").map { it.toInt() + 1 }
        AlertDialog.Builder(this)
            .setTitle(if (name != null) "$name  (row $r, column $c)" else "Empty tile  (row $r, column $c)")
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSlot(key: String, slot: Slot?) {
        val slots = prefs.slots(draft)
        if (slot == null) slots.remove(key) else slots[key] = slot
        prefs.setSlots(draft, slots)
        refreshTiles()
        refreshPreview()
    }

    private fun pickApp(key: String) {
        val apps = loadInstalledApps()
        AlertDialog.Builder(this)
            .setTitle("Open which app?")
            .setAdapter(rowsAdapter(apps.size) { i -> apps[i].icon() to apps[i].label }) { _, which ->
                val a = apps[which].info.activityInfo
                val old = prefs.slots(draft)[key]
                // Keep a custom artwork choice only if it was made for this same app.
                val keepArt = old?.art?.takeIf { old.component?.startsWith("${a.packageName}/") == true }
                updateSlot(key, Slot(
                    component = ComponentName(a.packageName, a.name).flattenToString(),
                    art = keepArt ?: TileArt.forPackage(a.packageName),
                    label = apps[which].label,
                ))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** App's own icon, or any of the bundled hand-drawn icons. */
    private fun pickIcon(key: String) {
        val slot = prefs.slots(draft)[key] ?: return
        val target = WidgetRenderer.resolve(this, slot)
        val choices = listOf<String?>(null) + TileArt.all.map { it.first }
        val labels = listOf("App's own icon") + TileArt.all.map { "Artwork: ${it.second}" }
        val px = dp(40)
        AlertDialog.Builder(this)
            .setTitle("Icon")
            .setAdapter(rowsAdapter(choices.size) { i ->
                val icon = WidgetRenderer.iconBitmap(this, slot.copy(art = choices[i]), target, px, 90)
                icon?.let { BitmapDrawable(resources, it) } to labels[i]
            }) { _, which -> updateSlot(key, slot.copy(art = choices[which])) }
            .setNegativeButton("Cancel", null)
            .show()
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

    private inner class AppEntry(val info: ResolveInfo, val label: String) {
        private var cached: Drawable? = null
        fun icon(): Drawable = cached ?: info.loadIcon(packageManager).also { cached = it }
    }

    /** List adapter of icon + label rows. */
    private fun rowsAdapter(count: Int, item: (Int) -> Pair<Drawable?, String>) = object : BaseAdapter() {
        override fun getCount() = count
        override fun getItem(position: Int) = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@ConfigActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(16) })
                addView(TextView(context).apply { textSize = 16f })
            }
            val (icon, label) = item(position)
            (row.getChildAt(0) as ImageView).setImageDrawable(icon)
            (row.getChildAt(1) as TextView).text = label
            return row
        }
    }

    // ------------------------------------------------------------------ preview

    /** Size of the widget being edited (launcher-reported), or a typical 5x3 phone widget for the defaults. */
    @Suppress("DEPRECATION")
    private fun previewSize(): SizeF {
        if (widgetId != WidgetPrefs.DEFAULTS) {
            val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()?.let { return it }
            }
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (w > 0 && h > 0) return SizeF(w.toFloat(), h.toFloat())
        }
        return SizeF(360f, 216f)
    }

    /** Re-render the preview shortly after the last change (sliders fire continuously). */
    private fun refreshPreview() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ renderPreview() }, 80)
    }

    private fun renderPreview() {
        val size = previewSize()
        val v = try {
            WidgetRenderer.renderPreview(this, draft, size).apply(this, preview)
        } catch (e: Exception) {
            return
        }
        preview.show(v, size.width, size.height)
    }

    /**
     * Shows the widget at its real dp size, scaled down to fit and centred. Swallows all touches.
     */
    private class PreviewFrame(ctx: Context) : FrameLayout(ctx) {
        private var wDp = 0f
        private var hDp = 0f

        fun show(v: View, wDp: Float, hDp: Float) {
            this.wDp = wDp; this.hDp = hDp
            removeAllViews()
            addView(v)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent) = true

        @Suppress("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent) = true

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val d = resources.displayMetrics.density
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
            val child = getChildAt(0) ?: return
            child.measure(
                MeasureSpec.makeMeasureSpec((wDp * d).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((hDp * d).toInt(), MeasureSpec.EXACTLY)
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val child = getChildAt(0) ?: return
            val pad = 12 * resources.displayMetrics.density
            val cw = child.measuredWidth.toFloat()
            val ch = child.measuredHeight.toFloat()
            val scale = min(1f, min((width - 2 * pad) / cw, (height - 2 * pad) / ch))
            child.layout(0, 0, cw.toInt(), ch.toInt())
            child.pivotX = 0f
            child.pivotY = 0f
            child.scaleX = scale
            child.scaleY = scale
            child.translationX = (width - cw * scale) / 2f
            child.translationY = (height - ch * scale) / 2f
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun section(s: String) = settings.addView(TextView(this).apply {
        text = s; textSize = 16f; setTextColor(Color.BLACK); setPadding(0, dp(20), 0, dp(4))
    })

    private fun text(s: String): TextView = TextView(this).apply {
        text = s; textSize = 13f; setTextColor(Color.DKGRAY); gravity = Gravity.START
    }.also { settings.addView(it) }

    private fun slider(max: Int, value: Int, onChange: (Int) -> Unit) = settings.addView(SeekBar(this).apply {
        this.max = max
        progress = value
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    /**
     * Two rows of 5 preset swatches, a "Colour picker" button (HSV sliders, plus opacity when
     * [withAlpha]) and a hex field (RRGGBB, or AARRGGBB when [withAlpha]).
     */
    private fun colorPicker(row1: IntArray, row2: IntArray, initial: Int, withAlpha: Boolean, onPick: (Int) -> Unit) {
        val swatches = LinkedHashMap<View, Int>()
        lateinit var hex: EditText
        var current = if (withAlpha) initial else initial or Color.BLACK
        if (Color.alpha(current) == 0) current = current or Color.BLACK
        fun hexOf(c: Int) = if (withAlpha && Color.alpha(c) != 255) String.format("%08X", c) else String.format("%06X", c and 0xFFFFFF)
        fun refresh() {
            for ((v, c) in swatches) {
                val sel = (c and 0xFFFFFF) == (current and 0xFFFFFF)
                (v.background as GradientDrawable).setStroke(dp(if (sel) 3 else 1), if (sel) Color.BLACK else Color.LTGRAY)
            }
        }
        fun pick(c: Int, fromHex: Boolean) {
            current = c
            onPick(c)
            refresh()
            if (!fromHex) hex.setText(hexOf(c))
        }
        for (presets in listOf(row1, row2)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
            for (c in presets) {
                val v = View(this).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                    // Presets keep the current opacity.
                    setOnClickListener { pick((c and 0xFFFFFF) or (current and 0xFF000000.toInt()), false) }
                }
                swatches[v] = c
                row.addView(v, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            }
            settings.addView(row)
        }
        hex = EditText(this).apply {
            hint = if (withAlpha) "RRGGBB / AARRGGBB" else "RRGGBB"
            filters = arrayOf(InputFilter.LengthFilter(if (withAlpha) 8 else 6))
            setText(hexOf(current))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) {
                    val t = e?.toString().orEmpty()
                    val c = when (t.length) {
                        6 -> t.toLongOrNull(16)?.toInt()?.or(Color.BLACK)
                        8 -> if (withAlpha) t.toLongOrNull(16)?.toInt() else null
                        else -> null
                    }
                    if (c != null && c != current) pick(c, true)
                }
            })
        }
        settings.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(this@ConfigActivity).apply {
                text = "Colour picker"
                setOnClickListener { colorDialog(current, withAlpha) { pick(it, false) } }
            })
            addView(TextView(this@ConfigActivity).apply { text = "  #"; textSize = 16f })
            addView(hex, LinearLayout.LayoutParams(dp(130), WRAP_CONTENT))
        })
        refresh()
    }

    /** HSV colour picker with an optional opacity slider; tracks show live gradients. */
    private fun colorDialog(initial: Int, withAlpha: Boolean, onPick: (Int) -> Unit) {
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        var alpha = if (withAlpha) Color.alpha(initial) else 255
        fun color() = Color.HSVToColor(alpha, hsv)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), 0) }
        val swatch = View(this)
        box.addView(swatch, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
        val bars = ArrayList<Pair<SeekBar, () -> IntArray>>()
        fun updateUi() {
            swatch.background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(color()); setStroke(dp(1), Color.LTGRAY) }
            for ((bar, colors) in bars) {
                bar.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors()).apply { cornerRadius = dp(6).toFloat() }
            }
        }
        fun bar(label: String, max: Int, value: Int, colors: () -> IntArray, set: (Int) -> Unit) {
            box.addView(TextView(this).apply { text = label; setTextColor(Color.DKGRAY); setPadding(0, dp(12), 0, dp(2)) })
            val sb = SeekBar(this).apply {
                this.max = max
                progress = value
                progressDrawable = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                minimumHeight = dp(28)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { set(p); updateUi() }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            }
            bars += sb to colors
            box.addView(sb, LinearLayout.LayoutParams(MATCH_PARENT, dp(28)))
        }
        bar("Hue", 360, hsv[0].toInt(), { IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) } }) { hsv[0] = it.toFloat() }
        bar("Saturation", 100, (hsv[1] * 100).toInt(), {
            intArrayOf(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])), Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
        }) { hsv[1] = it / 100f }
        bar("Brightness", 100, (hsv[2] * 100).toInt(), {
            intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
        }) { hsv[2] = it / 100f }
        if (withAlpha) bar("Opacity", 255, alpha, {
            intArrayOf(Color.HSVToColor(0, hsv), Color.HSVToColor(255, hsv))
        }) { alpha = it }
        updateUi()
        AlertDialog.Builder(this)
            .setTitle("Colour")
            .setView(box)
            .setPositiveButton("OK") { _, _ -> onPick(color()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val EXTRA_EDIT_TILE = "edit_tile"

        private val BG_LIGHT = intArrayOf(
            0xFF87CAC1.toInt(), 0xFFFFFFFF.toInt(), 0xFFF1ECE4.toInt(), 0xFFD9D9D9.toInt(), 0xFFD6DEE6.toInt(),
        )
        private val BG_DARK = intArrayOf(
            0xFF444444.toInt(), 0xFF2B2B2B.toInt(), 0xFF1F2933.toInt(), 0xFF2E3B30.toInt(), 0xFF000000.toInt(),
        )
        private val RIM_LIGHT = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFFBDBDBD.toInt(), 0xFF9E9E9E.toInt(), 0xFF757575.toInt(),
        )
        private val RIM_DARK = intArrayOf(
            0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF8B5A5A.toInt(), 0xFF3B6E8F.toInt(), 0xFFC28A2C.toInt(),
        )
    }
}
