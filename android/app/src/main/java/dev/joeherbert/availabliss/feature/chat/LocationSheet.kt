package dev.joeherbert.availabliss.feature.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.joeherbert.availabliss.core.data.FoundPlace
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.ConfirmOption
import dev.joeherbert.availabliss.designsystem.component.ConfirmSheet
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.chat.OsmMap
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.util.Locale

/**
 * "Location" from the + menu: a map of where you are with Send my current location, or search
 * for a place (OpenStreetMap's Nominatim) and send that. Asks for location access when it opens.
 */
@Composable
fun LocationSheet(
    sheet: LocationSheetUi,
    onQuery: (String) -> Unit,
    onSearch: (near: Pair<Double, Double>?) -> Unit,
    onClear: () -> Unit,
    onPick: (FoundPlace) -> Unit,
    onSend: (MessageLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(hasLocationPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        allowed = result.values.any { it }
        refused = !allowed
    }
    LaunchedEffect(Unit) {
        if (!allowed) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // Back from Settings with access turned on.
    LifecycleResumeEffect(Unit) {
        if (!allowed && hasLocationPermission(context)) allowed = true
        onPauseOrDispose {}
    }
    var here by remember { mutableStateOf<Location?>(null) }
    var locating by remember { mutableStateOf(false) }
    DisposableEffect(allowed) {
        var stop: (() -> Unit)? = null
        if (allowed) {
            locating = true
            here = lastKnownLocation(context)
            stop = requestCurrentLocation(context) { location ->
                if (location != null) here = location
                locating = false
            }
        }
        onDispose { stop?.invoke() }
    }
    val near = here?.let { it.latitude to it.longitude }
    val myLocation = here?.let {
        MessageLocation(it.latitude, it.longitude, accuracy = if (it.hasAccuracy()) it.accuracy.toDouble() else null)
    }

    AvBottomSheet(title = "Share a location", onDismissRequest = onDismiss) {
        AvTextField(
            value = sheet.query,
            onValueChange = onQuery,
            placeholder = "Search for a place",
            imeAction = ImeAction.Search,
            onImeAction = { onSearch(near) },
            trailing = {
                when {
                    sheet.searching -> CircularProgressIndicator(color = colors.accentInk, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    sheet.query.isNotEmpty() -> PlainIconButton(AvIcons.Close, "Clear search", onClear, size = 36.dp, iconSize = 14.dp, tint = colors.muted)
                    else -> PlainIconButton(AvIcons.Search, "Search", { onSearch(near) }, size = 36.dp, iconSize = 18.dp, tint = colors.muted)
                }
            },
        )
        if (sheet.noResults) {
            Text("Nothing found for “${sheet.query.trim()}”.", style = AvailablissTheme.type.labelRegular, color = colors.muted)
        } else if (sheet.results.isNotEmpty() && sheet.picked == null) {
            Column(Modifier.fillMaxWidth().clip(Shapes.panel).background(colors.surface).padding(horizontal = 14.dp)) {
                sheet.results.forEachIndexed { index, place ->
                    if (index > 0) RowDivider()
                    PlaceRow(place) { onPick(place) }
                }
            }
        }

        val shown = sheet.picked?.let { it.lat to it.lng } ?: near
        Box(Modifier.fillMaxWidth().height(200.dp).clip(Shapes.panel).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            if (shown != null) {
                OsmMap(shown.first, shown.second, Modifier.fillMaxWidth().height(200.dp), zoom = if (sheet.picked != null) 16 else 15)
            } else {
                Text(
                    when {
                        allowed && locating -> "Finding where you are…"
                        allowed -> "Couldn't find where you are. Is location turned on?"
                        else -> "Search for a place to share it."
                    },
                    style = AvailablissTheme.type.labelRegular,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        if (refused || (!allowed && !hasLocationPermission(context))) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Location access is off for Availabliss. Allow it to send where you are, or search for a place.",
                    style = AvailablissTheme.type.labelRegular,
                    color = colors.muted,
                )
                TextAction("Open settings", onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                })
            }
        }

        sheet.picked?.let { place ->
            PrimaryButton(
                text = "Send ${place.name}",
                onClick = { onSend(place.location) },
                icon = AvIcons.Location,
                style = ButtonStyle.Accent,
                height = 52.dp,
                shape = Shapes.control,
                loading = sheet.sending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (sheet.picked == null) {
            PrimaryButton(
                text = "Send my current location",
                onClick = { myLocation?.let(onSend) },
                icon = AvIcons.Navigate,
                height = 52.dp,
                shape = Shapes.control,
                enabled = myLocation != null,
                loading = sheet.sending,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SecondaryButton(
                text = "Send my current location",
                onClick = { myLocation?.let(onSend) },
                icon = AvIcons.Navigate,
                height = 52.dp,
                enabled = myLocation != null && !sheet.sending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        myLocation?.accuracy?.let {
            Text(
                "Your location is accurate to ${it.toInt()} m",
                style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PlaceRow(place: FoundPlace, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClickLabel = "Choose", onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AvIcons.Location, null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            place.address?.let {
                Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Tapped a shared place: open it in Google Maps, Apple Maps or what3words. */
@Composable
fun OpenLocationSheet(location: MessageLocation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        onDismiss()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }
    ConfirmSheet(
        title = "Open in",
        message = location.name ?: "This location",
        options = listOf(
            ConfirmOption("Google Maps", { open(LocationLinks.googleMaps(location)) }, icon = AvIcons.Map),
            ConfirmOption("Apple Maps", { open(LocationLinks.appleMaps(location)) }, icon = AvIcons.Map, kind = ConfirmOption.Kind.Secondary),
            ConfirmOption("what3words", { open(LocationLinks.what3words(location)) }, icon = AvIcons.Grid, kind = ConfirmOption.Kind.Secondary),
        ),
        onDismiss = onDismiss,
    )
}

/** Links that open a shared place: each opens its app when installed (they handle these links), else the website. */
object LocationLinks {
    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.6f", value)
    private fun pair(l: MessageLocation) = "${coordinate(l.lat)},${coordinate(l.lng)}"

    fun googleMaps(l: MessageLocation) = "https://www.google.com/maps/search/?api=1&query=${pair(l)}"

    fun appleMaps(l: MessageLocation) = "https://maps.apple.com/?ll=${pair(l)}&q=${Uri.encode(l.name ?: "Shared location")}"

    /** what3words turns the coordinates in the link into the square's three words. */
    fun what3words(l: MessageLocation) = "https://what3words.com/${pair(l)}"
}

private fun hasLocationPermission(context: Context) =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private fun bestProvider(manager: LocationManager): String? {
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }
    return candidates.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
}

@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}

/** One fresh position, given to [onResult] on the main thread (null if there's none). Returns a way to stop waiting. */
@SuppressLint("MissingPermission")
private fun requestCurrentLocation(context: Context, onResult: (Location?) -> Unit): () -> Unit {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = manager?.let(::bestProvider)
    if (manager == null || provider == null || !hasLocationPermission(context)) {
        onResult(null)
        return {}
    }
    var active = true
    val signal = android.os.CancellationSignal()
    try {
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (active) onResult(location)
        }
    } catch (_: SecurityException) {
        onResult(null)
    }
    return {
        active = false
        signal.cancel()
    }
}
