package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * An event's cover photo: full width, cropped to a wide [aspectRatio], on a `line2` placeholder
 * while it loads. [model] is a URL, or the picked JPEG bytes before they're uploaded.
 */
@Composable
fun CoverPhoto(
    model: Any,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f,
    shape: Shape = Shapes.inner,
    contentDescription: String? = null,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(AvailablissTheme.colors.line2),
    )
}
