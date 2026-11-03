package dev.joeherbert.availabliss.feature.plan

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.billing.PlanLabels
import dev.joeherbert.availabliss.core.billing.StorePackage
import dev.joeherbert.availabliss.core.common.findActivity
import dev.joeherbert.availabliss.core.network.model.AiPlannerAccess
import dev.joeherbert.availabliss.core.network.model.Plan
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * "Availabliss Pro" sheet: what Pro adds and how to get it on Google Play. Pro bought on
 * iPhone or the web shows here too. [reason] says why it opened (e.g. the group limit);
 * [onUpgraded] gets "Welcome to Pro" after a purchase or restore and the sheet closes.
 */
@Composable
fun UpgradeSheet(
    onDismiss: () -> Unit,
    onUpgraded: (String) -> Unit,
    reason: String? = null,
    viewModel: UpgradeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Play's purchase sheet runs in this scope so the ViewModel never holds the Activity.
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.finished) {
        val message = state.finished ?: return@LaunchedEffect
        viewModel.finishedHandled()
        onUpgraded(message)
        onDismiss()
    }
    UpgradeSheetContent(
        state = state,
        reason = reason,
        onDismiss = onDismiss,
        onBuy = { pkg -> context.findActivity()?.let { scope.launch { viewModel.buy(it, pkg) } } },
        onRestore = viewModel::restore,
        onManageInPlay = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpgradeViewModel.PLAY_SUBSCRIPTIONS_URL))) },
    )
}

@Composable
fun UpgradeSheetContent(
    state: UpgradeUiState,
    reason: String?,
    onDismiss: () -> Unit,
    onBuy: (StorePackage) -> Unit,
    onRestore: () -> Unit,
    onManageInPlay: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val plan = state.plan
    AvBottomSheet(title = "Availabliss Pro", onDismissRequest = onDismiss) {
        Text(reason ?: "Plan with as many groups as you like.", style = type.body, color = colors.muted)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Perk(AvIcons.Groups, "Unlimited groups", "Free accounts can create ${PlanLabels.FREE_GROUP_LIMIT}. Joining other people's groups is always free.")
            if (plan?.aiPlanner?.available == true) {
                Perk(AvIcons.Sparkle, "AI planning", "Picks the best time, suggests things to do and drafts the plan.")
            }
            Perk(AvIcons.Star, "Supports Availabliss", "Everything else stays free for everyone in your groups.")
        }

        when {
            state.loadError != null -> Text(state.loadError, style = type.label, color = colors.warnInk)
            plan == null -> Spinner()
            plan.isPro -> {
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.control).background(colors.okBg).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AvIcons.ProposalCheck, null, tint = colors.okInk, modifier = Modifier.size(20.dp))
                    Text("You're on Pro. ${PlanLabels.proStatus(plan)}".trim(), style = type.bodySmallStrong, color = colors.okInk)
                }
                when {
                    state.managedInPlay -> SecondaryButton("Manage subscription", onManageInPlay, modifier = Modifier.fillMaxWidth())
                    state.managedOnWeb -> Note("You bought Pro on the web. Manage it on the web.")
                    state.otherStore != null -> Note("Manage your subscription in ${state.otherStore} on the device you bought it on.")
                }
            }
            else -> when (val purchase = state.purchase) {
                PurchaseUi.Loading -> Spinner()
                PurchaseUi.Unavailable -> Note("Upgrading in the app isn't available yet. Upgrade on the web: Pro follows your account everywhere.")
                PurchaseUi.NotOnSale -> Note("Pro isn't on sale just now. Check back soon.")
                is PurchaseUi.Failed -> Text(purchase.message, style = type.label, color = colors.warnInk)
                is PurchaseUi.Packages -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        purchase.packages.forEach { pkg ->
                            val text = "${pkg.label} · ${pkg.price}${pkg.per}"
                            if (pkg.featured) {
                                PrimaryButton(
                                    text = text,
                                    onClick = { onBuy(pkg) },
                                    style = ButtonStyle.Accent,
                                    height = 52.dp,
                                    shape = Shapes.control,
                                    enabled = !state.busy,
                                    loading = state.buying == pkg.id,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                SecondaryButton(text, { onBuy(pkg) }, enabled = !state.busy, height = 52.dp, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    state.note?.let { Text(it, style = type.label, color = colors.warnInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.restoring) Spinner() else TextAction("Restore purchases", onRestore, enabled = !state.busy)
                    }
                    Text(
                        "Cancel any time. Bought on the web or iPhone instead? It works here automatically.",
                        style = type.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Perk(icon: ImageVector, title: String, text: String) {
    val colors = AvailablissTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.accentInk, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink)
            Text(text, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = AvailablissTheme.type.label.copy(fontWeight = FontWeight.Normal), color = AvailablissTheme.colors.muted)
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), color = AvailablissTheme.colors.muted, strokeWidth = 2.dp)
    }
}

@Preview
@Composable
private fun UpgradeSheetProPreview() {
    AvailablissPreviewTheme {
        UpgradeSheetContent(
            state = UpgradeUiState(plan = Plan(tier = "pro", proUntil = "2026-12-02T10:00:00Z", willRenew = true, store = "play_store", aiPlanner = AiPlannerAccess(true, true))),
            reason = null,
            onDismiss = {},
            onBuy = {},
            onRestore = {},
            onManageInPlay = {},
        )
    }
}
