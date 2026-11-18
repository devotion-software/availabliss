package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.joeherbert.availabliss.BuildConfig
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Identifies the app to OpenStreetMap's tile and search servers, as their usage policies ask. */
val OsmUserAgent = "Availabliss/${BuildConfig.VERSION_NAME} (Android)"

/**
 * A still map centred on [lat], [lng], drawn from OpenStreetMap's standard tiles (no API key),
 * with a pin in the middle and the © OpenStreetMap credit the tiles need.
 */
@Composable
fun OsmMap(lat: Double, lng: Double, modifier: Modifier = Modifier, zoom: Int = 16, tileSize: Dp = 128.dp, showPin: Boolean = true) {
    val colors = AvailablissTheme.colors
    val context = LocalContext.current
    val headers = remember { NetworkHeaders.Builder().set("User-Agent", OsmUserAgent).build() }
    BoxWithConstraints(modifier.clipToBounds().background(colors.accentSoft)) {
        val n = 2.0.pow(zoom)
        val latRad = lat.coerceIn(-85.0, 85.0) * PI / 180
        // The centre in tile units, then in dp.
        val tx = (lng + 180) / 360 * n
        val ty = (1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * n
        val t = tileSize.value
        val left = tx * t - maxWidth.value / 2
        val top = ty * t - maxHeight.value / 2
        val firstX = floor(left / t).toInt()
        val lastX = floor((left + maxWidth.value) / t).toInt()
        val firstY = floor(top / t).toInt()
        val lastY = floor((top + maxHeight.value) / t).toInt()
        for (x in firstX..lastX) for (y in firstY..lastY) {
            if (y < 0 || y >= n.toInt()) continue
            val wrappedX = Math.floorMod(x, n.toInt())
            val request = remember(zoom, wrappedX, y) {
                ImageRequest.Builder(context)
                    .data("https://tile.openstreetmap.org/$zoom/$wrappedX/$y.png")
                    .httpHeaders(headers)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset(x = (x * t - left).toFloat().dp, y = (y * t - top).toFloat().dp)
                    .size(tileSize),
            )
        }
        if (showPin) MapPin(Modifier.align(Alignment.Center))
        Text(
            "© OpenStreetMap",
            style = AvailablissTheme.type.micro.copy(fontSize = 8.sp, fontWeight = FontWeight.Normal),
            color = Color(0xFF333333),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .clearAndSetSemantics {},
        )
    }
}

/** The pin at the centre of a map: an accent circle with a white location mark. */
@Composable
fun MapPin(modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Box(
        modifier
            .size(30.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AvIcons.Location, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** A shared place in a bubble: a small map, its name and address. Tapping it asks where to open it. */
@Composable
fun LocationCard(
    location: MessageLocation,
    own: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AvailablissTheme.colors
    val title = location.name ?: "Location"
    val detail = location.address?.takeIf { it.isNotBlank() }
        ?: location.accuracy?.takeIf { location.name == null }?.let { "Accurate to ${it.toInt()} m" }
    Column(
        modifier
            .width(GalleryWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(if (own) colors.onAccent.copy(alpha = 0.14f) else colors.surface)
            .combinedClickable(role = Role.Button, onClickLabel = "Open in a maps app", onLongClick = onLongClick, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull("Location: $title", detail).joinToString(", ")
            },
    ) {
        OsmMap(location.lat, location.lng, Modifier.fillMaxWidth().height(130.dp))
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AvailablissTheme.type.bodySmallStrong, color = if (own) colors.onAccent else colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(
                        detail,
                        style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                        color = if (own) colors.onAccent.copy(alpha = 0.78f) else colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(AvIcons.External, null, tint = if (own) colors.onAccent else colors.accentInk, modifier = Modifier.size(18.dp))
        }
    }
}
