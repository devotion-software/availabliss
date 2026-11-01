package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/* @Previews for the design-system components, light and dark. */

@Composable
private fun PreviewCanvas(dark: Boolean, theme: ColorTheme = ColorTheme.Dawn, content: @Composable () -> Unit) {
    AvailablissPreviewTheme(dark = dark, theme = theme) {
        AvailablissBackground(OrbLayout.Main) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

private val people = listOf("Sam", "Priya", "Tom", "Ana", "Leo").map { AvatarPerson(it, it) }

@Composable
private fun GroupTileRowSample() {
    val colors = AvailablissTheme.colors
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GroupTile("LD", colors.t1)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lake District crew", style = AvailablissTheme.type.bodyStrong, modifier = Modifier.weight(1f))
                    StatusPill("2 weekends work", PillTone.Indigo)
                }
                Text("A weekend · Fri 6pm → Sun · Nov–Jan", style = AvailablissTheme.type.labelRegular, color = colors.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(people)
                    Fill()
                    SyncRing(4 / 6f)
                    Text("  2 of 6 to sync", style = AvailablissTheme.type.caption, color = colors.muted)
                }
            }
        }
    }
}

@Preview(name = "Glass + group tile", widthDp = 390)
@Composable
private fun GlassPreviewLight() = PreviewCanvas(dark = false) { GroupTileRowSample() }

@Preview(name = "Glass + group tile (dark)", widthDp = 390)
@Composable
private fun GlassPreviewDark() = PreviewCanvas(dark = true) { GroupTileRowSample() }

@Preview(name = "Buttons", widthDp = 390)
@Composable
private fun ButtonsPreview() = PreviewCanvas(dark = false) {
    PrimaryButton("Continue", {}, Modifier.fillMaxWidth())
    PrimaryButton("Block this time", {}, Modifier.fillMaxWidth(), style = ButtonStyle.Accent, height = 52.dp, shape = Shapes.control)
    PrimaryButton("Added to your calendar", {}, Modifier.fillMaxWidth(), icon = AvIcons.Check, style = ButtonStyle.Success)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("See who's free", {}, Modifier.weight(1f), height = 44.dp)
        GlassIconButton(AvIcons.Back, "Back", {})
        GlassPillButton("New group", {}, icon = AvIcons.Plus)
    }
    TextAction("Use a different email", {})
}

@Preview(name = "Controls", widthDp = 390)
@Composable
private fun ControlsPreview() = PreviewCanvas(dark = false) {
    var view by remember { mutableStateOf("Month") }
    SegmentedControl(listOf("Month", "Week", "Day").map { Segment(it, it) }, view, { view = it })
    var repeat by remember { mutableStateOf("Once") }
    SegmentedControl(listOf("Once", "Weekly", "Fortnightly").map { Segment(it, it) }, repeat, { repeat = it }, Modifier.fillMaxWidth(), style = SegmentedStyle.Inset)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("All day", true, {})
        Chip("Morning", false, {})
        RemovableChip("27 Dec – 2 Jan", "Remove", {})
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        var on by remember { mutableStateOf(true) }
        ToggleRow("Bank holidays", on, { on = it }, subtitle = "England · treat like a weekend day")
    }
    StepIndicator(2, 2)
}

@Preview(name = "Pills, rings, avatars (dark)", widthDp = 390)
@Composable
private fun PillsPreview() = PreviewCanvas(dark = true, theme = ColorTheme.Lagoon) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill("Works", PillTone.Mint)
        StatusPill("Needs your reply", PillTone.Apricot)
        StatusPill("Finding times", PillTone.Neutral)
        StatusPill("Admins only", PillTone.Indigo, icon = AvIcons.Shield)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        SyncRing(4 / 6f, size = 38.dp, strokeWidth = 4.dp, color = AvailablissTheme.colors.warn) {
            Text("4/6", style = AvailablissTheme.type.micro)
        }
        SyncRing(5 / 6f, size = 76.dp, strokeWidth = 7.dp, color = AvailablissTheme.colors.ok)
        Avatar(AvatarPerson("ana", "Ana"))
        VennLogo(40.dp)
    }
    Box(Modifier.fillMaxWidth().height(46.dp).glass(Shapes.inner).stripes(AvailablissTheme.colors.accentSoft))
}

@Preview(name = "Fields", widthDp = 390)
@Composable
private fun FieldsPreview() = PreviewCanvas(dark = false) {
    AvTextField("", {}, label = "Email", placeholder = "you@example.com")
    OtpInput("481", {})
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTile("Date", "Sat 21 Nov", {}, Modifier.weight(1f))
        FieldTile("Time", "All day", {}, Modifier.weight(1f))
    }
}

@Preview(name = "Tab bar", widthDp = 390, heightDp = 140)
@Composable
private fun TabBarPreview() = AvailablissPreviewTheme {
    AvailablissBackground(OrbLayout.Main) {
        TabBar(
            items = listOf(
                TabItem(0, "Groups", AvIcons.Groups),
                TabItem(1, "My time", AvIcons.Calendar),
                TabItem(2, "Activity", AvIcons.Bell, dot = true),
                TabItem(3, "You", AvIcons.Person),
            ),
            selected = 0,
            onSelect = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Preview(name = "States", widthDp = 390)
@Composable
private fun StatesPreview() = PreviewCanvas(dark = false) {
    ErrorBanner("You appear to be offline.", onRetry = {})
    EmptyState("No groups yet", "Create a group and invite people to find a time.", icon = AvIcons.Groups, actionLabel = "New group", onAction = {})
    Box(Modifier.size(1.dp))
}
