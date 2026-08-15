package gg.floyd.features.impl.misc

/** Pure layout helpers shared by the queued menu-title renderer and its regression tests. */
internal object FloydMenuTextLayout {
    private const val HORIZONTAL_OVERFLOW = 8
    private const val VERTICAL_OVERFLOW = 4

    data class Slot(val left: Int, val top: Int, val width: Int, val height: Int)

    fun screenTitleSlot(screenWidth: Int, top: Int, height: Int): Slot =
        Slot(left = 0, top = top, width = screenWidth, height = height)

    fun widgetSlot(left: Int, top: Int, width: Int, height: Int): Slot =
        Slot(
            left = left - HORIZONTAL_OVERFLOW,
            top = top - VERTICAL_OVERFLOW,
            width = width + HORIZONTAL_OVERFLOW * 2,
            height = height + VERTICAL_OVERFLOW * 2,
        )

    fun centeredLocalX(centerX: Float, slotLeft: Int, immediateTextWidth: Float): Float =
        centerX - slotLeft - immediateTextWidth * 0.5f
}
