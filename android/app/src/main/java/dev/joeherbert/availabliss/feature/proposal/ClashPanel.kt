package dev.joeherbert.availabliss.feature.proposal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** Warn-tone panel naming who's busy at a proposed time: "**Priya is busy then.** [detail]". */
@Composable
fun ClashPanel(clash: String, detail: String, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(colors.warnBg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AvIcons.Alert, null, tint = colors.warnInk, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$clash.") }
                append(" ")
                append(detail)
            },
            style = AvailablissTheme.type.label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
            color = colors.warnInk,
        )
    }
}
