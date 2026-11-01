package dev.joeherbert.availabliss.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Gap scale 4 / 6 / 8 / 10 / 12 / 14 / 18 and the 20dp screen padding. */
object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val s = 8.dp
    val m = 10.dp
    val l = 12.dp
    val xl = 14.dp
    val xxl = 18.dp
    val screen = 20.dp
    /** Clearance below scrolling content for the floating tab bar (66 + 22 + breathing room). */
    val tabBarClearance = 110.dp
}

/** Radii: screens 40, sheets 32, cards 24–28, controls 14–18, chips/pills 999. */
object Radii {
    val sheet = 32.dp
    val cardLarge = 28.dp
    val card = 24.dp
    val panel = 22.dp
    val row = 20.dp
    val controlLarge = 18.dp
    val control = 16.dp
    val controlSmall = 14.dp
    val inner = 12.dp
}

object Shapes {
    val sheet = RoundedCornerShape(Radii.sheet)
    val cardLarge = RoundedCornerShape(Radii.cardLarge)
    val card = RoundedCornerShape(Radii.card)
    val panel = RoundedCornerShape(Radii.panel)
    val row = RoundedCornerShape(Radii.row)
    val controlLarge = RoundedCornerShape(Radii.controlLarge)
    val control = RoundedCornerShape(Radii.control)
    val controlSmall = RoundedCornerShape(Radii.controlSmall)
    val inner = RoundedCornerShape(Radii.inner)
    val pill = RoundedCornerShape(percent = 50)
}
