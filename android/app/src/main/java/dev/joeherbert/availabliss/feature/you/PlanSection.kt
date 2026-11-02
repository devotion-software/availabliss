package dev.joeherbert.availabliss.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.billing.PlanLabels
import dev.joeherbert.availabliss.core.network.model.Plan
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** "Plan": Free (2 of 3 groups created) with Upgrade, or Pro (renews 2 Dec) with Manage. */
@Composable
fun PlanSection(plan: Plan?, onOpenPro: () -> Unit) {
    val colors = AvailablissTheme.colors
    plan ?: return
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Plan")
            StatusPill(if (plan.isPro) "Pro" else "Free", if (plan.isPro) PillTone.Indigo else PillTone.Neutral)
        }
        Text(PlanLabels.summary(plan), style = AvailablissTheme.type.body.copy(fontWeight = FontWeight.Normal), color = colors.ink)
        if (plan.isPro) {
            SecondaryButton("Manage Pro", onOpenPro, height = 44.dp, shape = Shapes.controlSmall, textStyle = AvailablissTheme.type.bodySmallStrong)
        } else {
            PrimaryButton(
                text = "Upgrade to Pro",
                onClick = onOpenPro,
                style = ButtonStyle.Accent,
                icon = AvIcons.Sparkle,
                height = 44.dp,
                shape = Shapes.controlSmall,
                textStyle = AvailablissTheme.type.bodySmallStrong,
            )
        }
    }
}
