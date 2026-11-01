package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * Text input from the concept: 48–50dp, `surface` fill, `line-2` border, radius 14–16.
 * [label] renders above (13 / 600 muted).
 */
@Composable
fun AvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    singleLine: Boolean = true,
    height: Dp = 50.dp,
    shape: Shape = Shapes.control,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = AvailablissTheme.type.bodyLarge,
    focusRequester: FocusRequester? = null,
) {
    val colors = AvailablissTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(it, style = AvailablissTheme.type.label, color = colors.muted) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics { label?.let { contentDescription = it } },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = height)
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, if (error != null) colors.warn else colors.line2, shape)
                        .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 12.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.muted.copy(alpha = 0.7f))
                    inner()
                }
            },
        )
        error?.let { Text(it, style = AvailablissTheme.type.caption, color = colors.warnInk) }
    }
}

/**
 * Six-box one-time code entry backed by a single hidden text field, so paste and
 * SMS autofill work.
 */
@Composable
fun OtpInput(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    onComplete: (String) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val colors = AvailablissTheme.colors
    BasicTextField(
        value = code,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(length)
            onCodeChange(digits)
            if (digits.length == length) onComplete(digits)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "One-time code, ${code.length} of $length digits entered" },
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { i ->
                    val char = code.getOrNull(i)?.toString().orEmpty()
                    val active = i == code.length.coerceAtMost(length - 1)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(Shapes.controlSmall)
                            .background(colors.surface)
                            .border(if (active) 1.5.dp else 1.dp, if (active) colors.accent else colors.line2, Shapes.controlSmall),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.ink, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}

/** Tappable field tile ("Date / Sat 21 Nov", "Starts / Fri from 18:00"), min height 60. */
@Composable
fun FieldTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    Column(
        modifier
            .defaultMinSize(minHeight = 60.dp)
            .clip(Shapes.control)
            .background(colors.surface)
            .border(1.dp, colors.glassBorder, Shapes.control)
            .clickable(role = Role.Button, onClickLabel = "Change $label", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = AvailablissTheme.type.micro, color = colors.muted)
        Text(value, style = AvailablissTheme.type.bodyMedium, color = colors.ink, maxLines = 1)
    }
}

/** Centered value box ("1 Nov 2026") used for range pickers. */
@Composable
fun ValueBox(value: String, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = value) {
    val colors = AvailablissTheme.colors
    Box(
        modifier
            .height(44.dp)
            .clip(Shapes.controlSmall)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(value, style = AvailablissTheme.type.bodyMedium, color = colors.ink)
    }
}
