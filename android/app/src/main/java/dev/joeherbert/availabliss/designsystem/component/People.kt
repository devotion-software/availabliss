package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.joeherbert.availabliss.core.common.initial
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.DisplayFontFamily

/** What an avatar needs: a stable key for its tile colour, a name and optional photo. */
data class AvatarPerson(val key: String, val name: String, val image: String? = null)

/** Round avatar: photo if available, else the initial on a `t1`–`t5` tile colour. */
@Composable
fun Avatar(
    person: AvatarPerson,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    background: Color = AvailablissTheme.colors.tileFor(person.key),
    border: Color? = null,
    borderWidth: Dp = 1.5.dp,
    fontSize: TextUnit = (size.value * 0.38f).sp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(borderWidth, border, CircleShape) else Modifier)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(person.name.initial(), fontSize = fontSize, fontWeight = FontWeight.Bold, color = AvailablissTheme.colors.ink)
        if (!person.image.isNullOrBlank()) {
            AsyncImage(
                model = person.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

/** Overlapping avatars (groups list: 22dp, -7dp overlap; receipts: 16dp, -4dp). */
@Composable
fun AvatarStack(
    people: List<AvatarPerson>,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    overlap: Dp = 7.dp,
    max: Int = 5,
    border: Color = AvailablissTheme.colors.glassBorder,
) {
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(-overlap)) {
        people.take(max).forEach { p ->
            Avatar(person = p, size = size, border = border, fontSize = (size.value * 0.46f).sp)
        }
    }
}

/** Group monogram tile on its `t1`–`t5` colour (48dp / radius 16 on lists). */
@Composable
fun GroupTile(
    monogram: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    radius: Dp = 16.dp,
    fontSize: TextUnit = (size.value * 0.36f).sp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(color).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram.take(3),
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (-0.025f * fontSize.value).sp,
            color = AvailablissTheme.colors.ink,
        )
    }
}
