package com.nikitacomputer.tileswidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Settings: widget appearance (tile colour, opacity, outline width/colour)
 * and which installed app each of the 15 tiles opens. Doubles as the
 * widget's (optional) configuration activity.
 */
class MappingActivity : Activity() {

    private var style = WidgetStyle()
    private var installedApps: List<AppEntry>? = null

    private lateinit var preview: PreviewFrame
    private lateinit var swatchRow: LinearLayout
    private lateinit var hexInput: EditText
    private lateinit var opacityLabel: TextView
    private lateinit var opacityBar: SeekBar
    private lateinit var outlineLabel: TextView
    private lateinit var outlineBar: SeekBar
    private lateinit var outlineGroup: RadioGroup
    private lateinit var radiusLabel: TextView
    private lateinit var radiusBar: SeekBar
    private val tileRows = mutableListOf<Row>()
    private var updatingControls = false

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

        style = WidgetStyle.load(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        buildAppearanceSection(content)
        buildTilesSection(content)
        setContentView(ScrollView(this).apply { addView(content) })

        syncControls()
        refreshPreview()

        intent.getIntExtra(EXTRA_PICK_TILE, -1).takeIf { it >= 0 }
            ?.let { TILES.getOrNull(it) }
            ?.let(::showPicker)
    }

    override fun onResume() {
        super.onResume()
        refreshTileRows()
    }

    // ---------------------------------------------------------------- appearance

    private fun buildAppearanceSection(parent: LinearLayout) {
        parent.addView(sectionHeader(getString(R.string.appearance)))

        preview = PreviewFrame(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF5B6E8C.toInt(), 0xFFC98A6B.toInt()),
            ).apply { cornerRadius = dp(12).toFloat() }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        parent.addView(preview, matchWrap().apply { setMargins(dp(16), 0, dp(16), dp(8)) })

        // Background colour: preset swatches + hex input.
        parent.addView(label(getString(R.string.background_color)))
        swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        PRESET_COLORS.forEach { rgb ->
            swatchRow.addView(
                View(this).apply {
                    tag = rgb
                    contentDescription = String.format("#%06X", rgb)
                    setOnClickListener { setStyle(style.copy(backgroundRgb = rgb)) }
                },
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) },
            )
        }
        parent.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(swatchRow)
        })

        hexInput = EditText(this).apply {
            hint = "#87CAC1"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(7))
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updatingControls) return
                    parseHex(s?.toString())?.let { setStyle(style.copy(backgroundRgb = it), fromHex = true) }
                }
            })
        }
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(TextView(context).apply { setText(R.string.hex_label) })
            addView(hexInput, LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(8) })
        })

        // Opacity 0–100 %.
        opacityLabel = label("")
        parent.addView(opacityLabel)
        opacityBar = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(seekListener { setStyle(style.copy(opacity = it)) })
        }
        parent.addView(opacityBar, matchWrap().apply { setMargins(dp(8), 0, dp(8), 0) })

        // Outline width 1–5 dp.
        outlineLabel = label("")
        parent.addView(outlineLabel)
        outlineBar = SeekBar(this).apply {
            min = WidgetStyle.MIN_OUTLINE
            max = WidgetStyle.MAX_OUTLINE
            setOnSeekBarChangeListener(seekListener { setStyle(style.copy(outlineDp = it)) })
        }
        parent.addView(outlineBar, matchWrap().apply { setMargins(dp(8), 0, dp(8), 0) })

        // Outline colour: black / white.
        parent.addView(label(getString(R.string.outline_color)))
        outlineGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(RadioButton(context).apply { id = ID_BLACK; setText(R.string.black) })
            addView(RadioButton(context).apply { id = ID_WHITE; setText(R.string.white) },
                RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(24) })
            setOnCheckedChangeListener { _, checkedId ->
                if (!updatingControls) setStyle(style.copy(outlineWhite = checkedId == ID_WHITE))
            }
        }
        parent.addView(outlineGroup)

        // Corner radius 0–30 dp in steps of 5 (SeekBar holds the step index).
        radiusLabel = label("")
        parent.addView(radiusLabel)
        radiusBar = SeekBar(this).apply {
            max = WidgetStyle.MAX_RADIUS / WidgetStyle.RADIUS_STEP
            isEnabled = TilesWidgetProvider.supportsCornerRadius
            setOnSeekBarChangeListener(seekListener {
                setStyle(style.copy(cornerRadiusDp = it * WidgetStyle.RADIUS_STEP))
            })
        }
        parent.addView(radiusBar, matchWrap().apply { setMargins(dp(8), 0, dp(8), 0) })
        if (!TilesWidgetProvider.supportsCornerRadius) {
            parent.addView(TextView(this).apply {
                setText(R.string.radius_unsupported)
                alpha = 0.7f
                setPadding(dp(16), 0, dp(16), 0)
            })
        }

        parent.addView(Button(this).apply {
            setText(R.string.reset_style)
            setOnClickListener { setStyle(WidgetStyle()) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(16), dp(8), dp(16), 0) })
    }

    private fun setStyle(newStyle: WidgetStyle, fromHex: Boolean = false) {
        if (newStyle == style) return
        style = newStyle
        WidgetStyle.save(this, style)
        syncControls(skipHex = fromHex)
        refreshPreview()
        TilesWidgetProvider.updateAll(this)
    }

    /** Pushes [style] into the controls without re-triggering listeners. */
    private fun syncControls(skipHex: Boolean = false) {
        updatingControls = true
        if (!skipHex) hexInput.setText(String.format("#%06X", style.backgroundRgb and 0xFFFFFF))
        opacityBar.progress = style.opacity
        opacityLabel.text = getString(R.string.opacity_label, style.opacity)
        outlineBar.progress = style.outlineDp
        outlineLabel.text = getString(R.string.outline_width_label, style.outlineDp)
        outlineGroup.check(if (style.outlineWhite) ID_WHITE else ID_BLACK)
        radiusBar.progress = style.cornerRadiusDp / WidgetStyle.RADIUS_STEP
        radiusLabel.text = getString(R.string.corner_radius_label, style.cornerRadiusDp)
        for (i in 0 until swatchRow.childCount) {
            val swatch = swatchRow.getChildAt(i)
            val rgb = swatch.tag as Int
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(Color.red(rgb), Color.green(rgb), Color.blue(rgb)))
                val selected = rgb == (style.backgroundRgb and 0xFFFFFF)
                setStroke(dp(if (selected) 3 else 1), if (selected) 0xFF1E88E5.toInt() else 0x55000000)
            }
        }
        updatingControls = false
    }

    private fun refreshPreview() {
        preview.removeAllViews()
        val widget = TilesWidgetProvider.buildViews(this, style).apply(this, preview)
        preview.addView(widget, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
            if (fromUser && !updatingControls) onChange(value)
        }
        override fun onStartTrackingTouch(bar: SeekBar) {}
        override fun onStopTrackingTouch(bar: SeekBar) {}
    }

    private fun parseHex(text: String?): Int? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)
    }

    /** Widget preview: keeps a 5:3 aspect ratio and swallows taps. */
    private class PreviewFrame(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val inner = width - paddingLeft - paddingRight
            val height = inner * 3 / 5 + paddingTop + paddingBottom
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }

        override fun onInterceptTouchEvent(ev: MotionEvent) = true
    }

    // --------------------------------------------------------------------- tiles

    private fun buildTilesSection(parent: LinearLayout) {
        parent.addView(sectionHeader(getString(R.string.tiles)))
        parent.addView(TextView(this).apply {
            setText(R.string.mapping_header)
            setPadding(dp(16), 0, dp(16), dp(8))
        })
        TILES.forEach { tile ->
            val row = Row().apply {
                isClickable = true
                isFocusable = true
                background = selectableBackground()
                setOnClickListener { showPicker(tile) }
            }
            tileRows += row
            parent.addView(row)
        }
    }

    private fun refreshTileRows() {
        TILES.forEachIndexed { index, tile ->
            val subtitle = when (val target = resolveTarget(this, tile)) {
                is Target.Custom -> "${target.label} (${getString(R.string.custom_suffix)})"
                is Target.Default -> "${target.label} (${getString(R.string.default_suffix)})"
                is Target.Fallback -> getString(R.string.system_default_app)
                null -> getString(R.string.not_mapped)
            }
            tileRows[index].bind(getDrawable(tile.icon), tile.label, subtitle)
        }
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
                refreshTileRows()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class AppEntry(val info: ResolveInfo, val label: String) {
        private var cached: Drawable? = null
        fun icon(): Drawable = cached ?: info.loadIcon(packageManager).also { cached = it }
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

    // ------------------------------------------------------------------- helpers

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setPadding(dp(16), dp(20), dp(16), dp(8))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(16), dp(12), dp(16), dp(4))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun selectableBackground(): Drawable? {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return getDrawable(value.resourceId)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
    ).toInt()

    companion object {
        const val EXTRA_PICK_TILE = "pick_tile"
        private const val ID_BLACK = 1001
        private const val ID_WHITE = 1002

        private val PRESET_COLORS = intArrayOf(
            0x87CAC1, // default teal
            0xFFFFFF, 0xF4EEE3, 0xFFD1DC, 0xFFE5A0, 0xC8E6A0,
            0xA9D4FF, 0xCBB8F0, 0xF0A07A, 0x8A8A8A, 0x2B2B2B, 0x000000,
        )
    }
}
