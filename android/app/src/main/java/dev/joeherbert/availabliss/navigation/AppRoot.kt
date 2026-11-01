package dev.joeherbert.availabliss.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.VennLogo
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.feature.onboarding.OnboardingScreen
import dev.joeherbert.availabliss.feature.signin.SignInScreen

/** Switches between sign in, onboarding and the main app based on [RootState]. */
@Composable
fun AppRoot(root: RootViewModel) {
    val state by root.state.collectAsStateWithLifecycle()
    Crossfade(targetState = state::class, label = "root") { key ->
        when (key) {
            RootState.SignedOut::class -> SignInScreen()
            RootState.Onboarding::class -> OnboardingScreen()
            RootState.Main::class -> MainNavHost(root)
            else -> SplashScreen(
                failed = (state as? RootState.Loading)?.failed == true,
                onRetry = root::retryLoad,
                onSignOut = root::signOut,
            )
        }
    }
}

@Composable
private fun SplashScreen(failed: Boolean, onRetry: () -> Unit, onSignOut: () -> Unit) {
    AvailablissBackground(OrbLayout.SignIn) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            VennLogo(72.dp)
            if (failed) {
                Text("We couldn't reach Availabliss.", style = AvailablissTheme.type.cardTitle, color = AvailablissTheme.colors.ink)
                PrimaryButton("Try again", onRetry, height = 50.dp)
                TextAction("Sign out", onSignOut)
            }
        }
    }
}
