package com.nikitacomputer.tileswidget

/**
 * Outer outline drawables (generated): [radius step][width - 1].
 * Radius steps are 0, 5, ..., 30 dp; widths are 1..5 dp.
 */
object OutlineRings {
    private val RINGS = arrayOf(
        intArrayOf(R.drawable.ring_r0_w1, R.drawable.ring_r0_w2, R.drawable.ring_r0_w3, R.drawable.ring_r0_w4, R.drawable.ring_r0_w5), // 0dp,
        intArrayOf(R.drawable.ring_r5_w1, R.drawable.ring_r5_w2, R.drawable.ring_r5_w3, R.drawable.ring_r5_w4, R.drawable.ring_r5_w5), // 5dp,
        intArrayOf(R.drawable.ring_r10_w1, R.drawable.ring_r10_w2, R.drawable.ring_r10_w3, R.drawable.ring_r10_w4, R.drawable.ring_r10_w5), // 10dp,
        intArrayOf(R.drawable.ring_r15_w1, R.drawable.ring_r15_w2, R.drawable.ring_r15_w3, R.drawable.ring_r15_w4, R.drawable.ring_r15_w5), // 15dp,
        intArrayOf(R.drawable.ring_r20_w1, R.drawable.ring_r20_w2, R.drawable.ring_r20_w3, R.drawable.ring_r20_w4, R.drawable.ring_r20_w5), // 20dp,
        intArrayOf(R.drawable.ring_r25_w1, R.drawable.ring_r25_w2, R.drawable.ring_r25_w3, R.drawable.ring_r25_w4, R.drawable.ring_r25_w5), // 25dp,
        intArrayOf(R.drawable.ring_r30_w1, R.drawable.ring_r30_w2, R.drawable.ring_r30_w3, R.drawable.ring_r30_w4, R.drawable.ring_r30_w5), // 30dp
    )

    fun forStyle(style: WidgetStyle): Int =
        RINGS[style.cornerRadiusDp / WidgetStyle.RADIUS_STEP][style.outlineDp - WidgetStyle.MIN_OUTLINE]
}
