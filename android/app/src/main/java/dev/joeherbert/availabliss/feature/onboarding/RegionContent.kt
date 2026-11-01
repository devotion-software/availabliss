package dev.joeherbert.availabliss.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.network.model.NamedCode
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.time.ZoneId

/** The region form: country, region (bank holidays), school holidays, time zone. */
@Composable
fun RegionForm(
    state: RegionUiState,
    onOpenPicker: (RegionPicker) -> Unit,
    onUseDeviceTimezone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        LoadingIndicator(modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.Top) {
            PickerRow("Country", state.countryName ?: "Choose", { onOpenPicker(RegionPicker.Country) })
            RowDivider()
            PickerRow(
                label = "Region",
                value = state.subdivisionName ?: "Whole country",
                onClick = { onOpenPicker(RegionPicker.Subdivision) },
                hint = "Sets which bank holidays apply",
                enabled = state.country != null && state.subdivisions.isNotEmpty(),
            )
            RowDivider()
            PickerRow(
                label = "School holidays",
                value = state.schoolAuthorityName ?: "None",
                onClick = { onOpenPicker(RegionPicker.SchoolAuthority) },
                hint = "For groups that only meet in the holidays",
                enabled = state.country != null && state.authorities.isNotEmpty(),
            )
        }
        GlassPanel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AvIcons.Clock, null, tint = AvailablissTheme.colors.accentInk, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Time zone", style = AvailablissTheme.type.bodySmallStrong, color = AvailablissTheme.colors.ink)
                    Text(state.timezone.replace('_', ' '), style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = AvailablissTheme.colors.muted)
                }
                if (state.timezone != ZoneId.systemDefault().id) {
                    TextAction("Use this phone's", onUseDeviceTimezone, style = AvailablissTheme.type.label)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit, hint: String? = null, enabled: Boolean = true) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Change $label", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = AvailablissTheme.type.micro, color = colors.muted)
            Text(value, style = AvailablissTheme.type.bodyMedium, color = if (enabled) colors.ink else colors.muted)
            hint?.let { Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted) }
        }
        if (enabled) Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    }
}

/** Searchable list in a bottom sheet for the open [RegionUiState.picker]. */
@Composable
fun RegionPickerSheet(
    state: RegionUiState,
    onQueryChange: (String) -> Unit,
    onPick: (RegionOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = state.picker ?: return
    val selected = when (picker) {
        RegionPicker.Country -> state.country
        RegionPicker.Subdivision -> state.subdivision
        RegionPicker.SchoolAuthority -> state.schoolAuthority
    }
    val title = when (picker) {
        RegionPicker.Country -> "Country"
        RegionPicker.Subdivision -> "Region"
        RegionPicker.SchoolAuthority -> "School holidays"
    }
    val colors = AvailablissTheme.colors
    AvBottomSheet(title = title, onDismissRequest = onDismiss, scrollable = false) {
        AvTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = "Search",
            height = 48.dp,
            shape = Shapes.controlSmall,
            imeAction = ImeAction.Search,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(state.pickerOptions, key = { it.code ?: "__none" }) { option ->
                val isSelected = option.code == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp)
                        .clickable(role = Role.RadioButton) { onPick(option) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.name,
                        style = if (isSelected) AvailablissTheme.type.bodyMedium else AvailablissTheme.type.bodyMediumRegular,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Icon(AvIcons.Check, "Selected", tint = colors.accentInk, modifier = Modifier.size(16.dp))
                }
                RowDivider()
            }
        }
    }
}

@Preview
@Composable
private fun RegionFormPreview() {
    AvailablissPreviewTheme {
        RegionForm(
            state = RegionUiState(
                loading = false,
                countries = listOf(NamedCode("GB", "United Kingdom")),
                subdivisions = listOf(NamedCode("GB-ENG", "England")),
                authorities = listOf(RegionOption("bristol", "Bristol City Council")),
                country = "GB",
                subdivision = "GB-ENG",
                schoolAuthority = "bristol",
                timezone = "Europe/London",
            ),
            onOpenPicker = {},
            onUseDeviceTimezone = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
