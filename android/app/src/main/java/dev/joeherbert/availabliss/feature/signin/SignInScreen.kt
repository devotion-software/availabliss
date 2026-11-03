package dev.joeherbert.availabliss.feature.signin

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.auth.CustomTabs
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.OtpInput
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.VennLogo
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Credential Manager sheets run in this scope so the ViewModel never holds the Activity.
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.appleUrl) {
        val url = state.appleUrl ?: return@LaunchedEffect
        CustomTabs.open(context, url)
        viewModel.onAppleUrlOpened()
    }

    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onSendCode = viewModel::sendCode,
        onCodeChange = viewModel::onCodeChange,
        onUseDifferentEmail = viewModel::useDifferentEmail,
        onApple = viewModel::signInWithApple,
        onGoogle = { scope.launch { viewModel.signInWithGoogle(context) } },
        onPasskey = { scope.launch { viewModel.signInWithPasskey(context) } },
    )
}

@Composable
fun SignInContent(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onUseDifferentEmail: () -> Unit,
    onApple: () -> Unit,
    onGoogle: () -> Unit,
    onPasskey: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvailablissBackground(OrbLayout.SignIn) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 44.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VennLogo(40.dp)
                        Text("Availabliss", style = type.headlineSmall, color = colors.ink)
                    }
                    Spacer(Modifier.height(56.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "Find the time that works for everyone.",
                            style = type.hero,
                            color = colors.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            "Connect your calendar once. We'll find the overlap, and nobody has to say “what about Thursday?” again.",
                            style = type.bodyLarge,
                            color = colors.muted,
                            modifier = Modifier.widthIn(max = 300.dp),
                        )
                    }
                }

                Column(
                    Modifier
                        .padding(top = 32.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(30.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "sign-in-step",
                    ) { step ->
                        when (step) {
                            SignInStep.Email -> EmailStep(state, onEmailChange, onSendCode)
                            SignInStep.Code -> CodeStep(state, onCodeChange, onUseDifferentEmail)
                        }
                    }

                    state.error?.let {
                        Text(
                            it,
                            style = type.caption,
                            color = colors.warnInk,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }

                    OrDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialButton("Apple", AvIcons.Apple, colors.ink, state.busy == SignInBusy.Apple, state.busy == null, onApple, "Sign in with Apple", Modifier.weight(1f))
                        SocialButton("Google", AvIcons.Google, Color.Unspecified, state.busy == SignInBusy.Google, state.busy == null && state.googleAvailable, onGoogle, "Sign in with Google", Modifier.weight(1f))
                        SocialButton("Passkey", AvIcons.Passkey, colors.ink, state.busy == SignInBusy.Passkey, state.busy == null, onPasskey, "Sign in with a passkey", Modifier.weight(1f))
                    }

                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AvIcons.Shield, null, tint = colors.muted, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                        Text(
                            "Your group only ever sees when you're busy, never what you're doing.",
                            style = type.caption.copy(fontWeight = FontWeight.Normal, lineHeight = type.caption.fontSize * 1.4f),
                            color = colors.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailStep(state: SignInUiState, onEmailChange: (String) -> Unit, onSendCode: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Email", style = AvailablissTheme.type.label, color = AvailablissTheme.colors.ink)
        AvTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            onImeAction = onSendCode,
            enabled = state.busy == null,
        )
        PrimaryButton(
            text = "Email me a code",
            onClick = onSendCode,
            height = 50.dp,
            shape = Shapes.control,
            loading = state.busy == SignInBusy.Email,
            enabled = state.busy == null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CodeStep(state: SignInUiState, onCodeChange: (String) -> Unit, onUseDifferentEmail: () -> Unit) {
    val colors = AvailablissTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Check your inbox", style = AvailablissTheme.type.bodyMedium, color = colors.ink)
                if (state.busy == SignInBusy.Code) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "Enter the 6-digit code we just sent to ${state.email.trim()}.",
                style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        OtpInput(code = state.code, onCodeChange = onCodeChange, focusRequester = focus)
        TextAction(
            "Use a different email",
            onUseDifferentEmail,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.busy == null,
        )
    }
}

@Composable
private fun OrDivider() {
    val colors = AvailablissTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.line2))
        Text("or", style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        Box(Modifier.weight(1f).height(1.dp).background(colors.line2))
    }
}

@Composable
private fun SocialButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    Row(
        modifier
            .height(50.dp)
            .clip(Shapes.control)
            .background(colors.surface)
            .border(1.dp, colors.glassBorder, Shapes.control)
            .alpha(if (enabled || loading) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 1)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SignInEmailPreview() {
    AvailablissPreviewTheme {
        SignInContent(SignInUiState(email = "sam@example.com"), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SignInCodeDarkPreview() {
    AvailablissPreviewTheme(dark = true) {
        SignInContent(SignInUiState(step = SignInStep.Code, email = "sam@example.com", code = "481"), {}, {}, {}, {}, {}, {}, {})
    }
}
