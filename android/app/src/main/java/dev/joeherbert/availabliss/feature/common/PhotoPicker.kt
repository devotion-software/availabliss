package dev.joeherbert.availabliss.feature.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android's photo picker (no storage permission needed). Returns a launcher; the picked photo
 * arrives in [onPicked] as a JPEG at most [maxSide] px on its longest side (the server shrinks
 * it further), or [onUnreadable] is called if it couldn't be decoded.
 */
@Composable
fun rememberPhotoPicker(maxSide: Int = 1024, onUnreadable: () -> Unit, onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val unreadable by rememberUpdatedState(onUnreadable)
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { photoAsJpeg(context, uri, maxSide) }
            if (jpeg == null) unreadable() else picked(jpeg)
        }
    }
    return remember(launcher) { { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) } }
}

/** A picked photo as a JPEG at most [maxSide] px on its longest side. */
private fun photoAsJpeg(context: Context, uri: Uri, maxSide: Int): ByteArray? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        // Android 8: decode at a power-of-two sample size, then scale down.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxSide) decoded
        else Bitmap.createScaledBitmap(decoded, decoded.width * maxSide / longest, decoded.height * maxSide / longest, true)
    }
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}.getOrNull()
