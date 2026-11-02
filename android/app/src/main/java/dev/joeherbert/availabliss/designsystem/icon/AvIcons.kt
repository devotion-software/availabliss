package dev.joeherbert.availabliss.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The concept's 2px-stroke line icons, transcribed from the SVG paths in
 * docs/design-concept. Tint them with `Icon(tint = …)`.
 */
object AvIcons {
    val Back = line("Back", "M15 5l-7 7 7 7", stroke = 2.2f)
    val Forward = line("Forward", "M9 5l7 7-7 7", stroke = 2.2f)
    val ChevronDown = line("ChevronDown", "M6 9l6 6 6-6")
    val Plus = line("Plus", "M12 5v14M5 12h14", stroke = 2.4f)
    val Close = line("Close", "M6 6l12 12M18 6L6 18", stroke = 2.6f)
    val Check = line("Check", "M5 12l5 5L20 7", stroke = 3f)
    val Calendar = line("Calendar", rect(3f, 5f, 18f, 16f, 3f), "M3 10h18M8 3v4M16 3v4")
    /** Connected calendars: a calendar with a sync arrow in the corner. */
    val CalendarSync = line(
        "CalendarSync",
        "M3 10h18M8 3v4M16 3v4",
        "M21 11V8a3 3 0 0 0-3-3H6a3 3 0 0 0-3 3v10a3 3 0 0 0 3 3h5",
        "M21.5 17.5a4 4 0 1 1-1.17-2.83",
        "M20.6 12.2v2.7h-2.7",
    )
    val CalendarAdd = line("CalendarAdd", rect(3f, 5f, 18f, 16f, 3f), "M3 10h18M8 3v4M16 3v4M12 13v5M9.5 15.5h5")
    val Groups = line("Groups", circle(9f, 10f, 5.5f), circle(15f, 10f, 5.5f), circle(12f, 15f, 5.5f))
    val Bell = line("Bell", "M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val Person = line("Person", circle(12f, 8f, 4f), "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Chat = line("Chat", "M4 5h16v11H9l-5 4V5z")
    val ProposalCheck = line("ProposalCheck", circle(12f, 12f, 9f), "M8 12l3 3 5-6")
    val Sliders = line("Sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val AddPerson = line("AddPerson", circle(10f, 8f, 4f), "M3 20c1.2-3.5 3.8-5 7-5s5.8 1.5 7 5M19 8v6M16 11h6")
    val Clock = line("Clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Pencil = line("Pencil", "M4 20h4L19 9l-4-4L4 16v4z")
    val Link = line("Link", "M10 14a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1", "M14 10a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1")
    val Repeat = line("Repeat", "M17 2l4 4-4 4", "M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4", "M21 13v2a3 3 0 0 1-3 3H3", stroke = 2.2f)
    val Shield = line("Shield", "M12 3l8 3v6c0 4.5-3.3 8.3-8 9-4.7-.7-8-4.5-8-9V6l8-3z")
    val Location = line("Location", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f))
    val Poll = line("Poll", "M5 20V10M12 20V4M19 20v-7", stroke = 2.2f)
    val ArrowRight = line("ArrowRight", "M5 12h14M13 6l6 6-6 6", stroke = 2.2f)
    val Sun = line("Sun", "M12 4V2M12 22v-2M4 12H2M22 12h-2M5.6 5.6L4.2 4.2M19.8 19.8l-1.4-1.4M5.6 18.4l-1.4 1.4M19.8 4.2l-1.4 1.4M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z")
    val Moon = line("Moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val Monitor = line("Monitor", "M3 5h18v11H3zM8 20h8M12 16v4")
    val CloudCheck = line("CloudCheck", "M17.5 19a4.5 4.5 0 1 0-1.4-8.8A6 6 0 0 0 4.5 12a3.5 3.5 0 0 0 1 6.9z", "M9 14l2 2 4-4")
    val Passkey = line("Passkey", circle(8f, 8f, 4f), "M2 21c.8-3.6 3-5.5 6-5.5 1.3 0 2.4.3 3.3.9", circle(17f, 13f, 2.5f), "M17 15.5V22M17 19h2.5")
    val Share = line("Share", "M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13")
    val More = line("More", "M5 12h.01M12 12h.01M19 12h.01", stroke = 3f)
    val Trash = line("Trash", "M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6")
    val SignOut = line("SignOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9")
    val Info = line("Info", circle(12f, 12f, 9f), "M12 11v5M12 8h.01")
    /** Warning: "!" in a circle (clashes, problems). */
    val Alert = line("Alert", circle(12f, 12f, 9f), "M12 7.5v5M12 16.2v.1")
    val Mail = line("Mail", rect(3f, 5f, 18f, 14f, 3f), "M3 7l9 6 9-6")
    val Palette = line("Palette", "M12 3a9 9 0 1 0 0 18c1 0 1.5-.8 1.5-1.5 0-.4-.2-.8-.4-1.1-.3-.3-.4-.7-.4-1.1 0-.8.7-1.5 1.5-1.5H16a5 5 0 0 0 5-5c0-4.4-4-7.8-9-7.8z", circle(7.5f, 11f, .5f), circle(10.5f, 7.5f, .5f), circle(15f, 8f, .5f))
    val Refresh = line("Refresh", "M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5")
    val Globe = line("Globe", circle(12f, 12f, 9f), "M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z")
    /** Pro and AI planning. */
    val Sparkle = line("Sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z")
    val Star = line("Star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3z")

    /** Double tick for read receipts (28×18 viewport, as in the concept). */
    val DoubleCheck = line("DoubleCheck", "M2 10l5 5L17 4", "M12 14l1 1L24 4", stroke = 2.6f, viewportWidth = 28f, viewportHeight = 18f, width = 16f, height = 12f)
    /** Single tick (delivered / pending receipts). */
    val SingleCheck = line("SingleCheck", "M2 10l5 5L17 4", stroke = 2.6f, viewportWidth = 28f, viewportHeight = 18f, width = 16f, height = 12f)

    /** Filled Apple logo (tint with ink). */
    val Apple: ImageVector = ImageVector.Builder("Apple", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes(
                "M16.37 1.43c0 1.14-.49 2.27-1.18 3.08-.74.9-1.99 1.57-2.99 1.57-.12 0-.23-.02-.3-.03-.01-.06-.04-.22-.04-.39 0-1.15.57-2.27 1.21-2.98.8-.94 2.14-1.64 3.25-1.68.03.13.05.28.05.43zm4.56 15.71c-.03.07-.46 1.58-1.52 3.12-.94 1.34-1.94 2.71-3.43 2.71-1.52 0-1.9-.88-3.63-.88-1.7 0-2.3.91-3.67.91-1.38 0-2.33-1.26-3.43-2.8-1.29-1.82-2.32-4.63-2.32-7.28 0-4.28 2.8-6.55 5.55-6.55 1.45 0 2.68.95 3.6.95.87 0 2.22-1.01 3.9-1.01.61 0 2.89.06 4.37 2.19-.13.09-2.38 1.37-2.38 4.19 0 3.26 2.85 4.42 2.96 4.45z",
            ),
            fill = SolidColor(Color.Black),
        )
    }.build()

    /** Full-colour Google "G" (use with `tint = Color.Unspecified`). */
    val Google: ImageVector = ImageVector.Builder("Google", 24.dp, 24.dp, 48f, 48f).apply {
        fun fill(hex: Long, d: String) = addPath(pathData = addPathNodes(d), fill = SolidColor(Color(hex)))
        fill(0xFFFFC107, "M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z")
        fill(0xFFFF3D00, "M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z")
        fill(0xFF4CAF50, "M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z")
        fill(0xFF1976D2, "M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z")
    }.build()

    private fun line(
        name: String,
        vararg paths: String,
        stroke: Float = 2f,
        viewportWidth: Float = 24f,
        viewportHeight: Float = 24f,
        width: Float = 24f,
        height: Float = 24f,
    ): ImageVector = ImageVector.Builder(name, width.dp, height.dp, viewportWidth, viewportHeight).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},${y}h${w - 2 * rx}a$rx,$rx 0 0 1 $rx,${rx}v${h - 2 * rx}a$rx,$rx 0 0 1 ${-rx},${rx}h${-(w - 2 * rx)}a$rx,$rx 0 0 1 ${-rx},${-rx}v${-(h - 2 * rx)}a$rx,$rx 0 0 1 $rx,${-rx}z"
}
