package dev.joeherbert.availabliss.feature.intro

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.Orbs
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.VennLogo
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What each page says. The same pages, order and words as iOS's intro. */
internal enum class IntroPage(val eyebrow: String, val title: String, val body: String, val orbs: OrbLayout) {
    Group(
        "Make a group",
        "Bring your people together.",
        "Start a group for friends, family or a team, and invite everyone with a link.",
        OrbLayout.Main,
    ),
    Calendars(
        "Connect your calendars",
        "Your plans stay private.",
        "Connect your calendars once. Your group only ever sees when you're busy, never what you're doing.",
        OrbLayout.Calendars,
    ),
    Grid(
        "See what works",
        "Spot the days everyone's free.",
        "The group calendar lights up the days and times that work for everyone. No more “what about Thursday?”",
        OrbLayout.GroupCalendar,
    ),
    Proposal(
        "Propose a time",
        "Suggest it. Everyone replies.",
        "Pick a time and the group says yes, maybe or no. When everyone's in, it's a plan.",
        OrbLayout.Proposal,
    ),
    Chat(
        "Chat with the group",
        "Talk it through together.",
        "Sort out the details in the group chat: where to go, who's driving, what to bring.",
        OrbLayout.SignIn,
    ),
}

/**
 * The first-launch intro: five pages on what Availabliss does, shown once before sign-in.
 * Swipe or tap Next; Skip or Get started goes on to sign in.
 */
@Composable
fun IntroScreen(onFinish: () -> Unit) {
    val colors = AvailablissTheme.colors
    val pages = IntroPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val isLast = pager.currentPage == pages.lastIndex

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    var lastPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage != lastPage) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastPage = pager.currentPage
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        IntroOrbs(pager, pages)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 24.dp, end = 14.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VennLogo(30.dp, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Availabliss", style = AvailablissTheme.type.headlineSmall.copy(fontSize = 19.sp), color = colors.ink)
                Spacer(Modifier.weight(1f))
                val skipAlpha by animateFloatAsState(if (isLast) 0f else 1f, tween(250), label = "skip")
                TextAction(
                    "Skip",
                    onFinish,
                    modifier = Modifier.graphicsLayer { alpha = skipAlpha },
                    style = AvailablissTheme.type.bodyMedium,
                    enabled = !isLast,
                )
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                IntroPageContent(pages[index], index, pager, reduceMotion)
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                PageDots(pages.size, pager.currentPage)
                PrimaryButton(
                    text = if (isLast) "Get started" else "Next",
                    onClick = {
                        if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth().semantics { stateDescription = "Page ${pager.currentPage + 1} of ${pages.size}" },
                    style = if (isLast) ButtonStyle.Accent else ButtonStyle.Solid,
                    height = 54.dp,
                )
            }
        }
    }
}

/**
 * Each page's orbs, cross-faded as you swipe, so they drift from one arrangement to the next and end
 * where sign-in's sit. Only the (at most two) layers in view are drawn.
 */
@Composable
private fun IntroOrbs(pager: PagerState, pages: List<IntroPage>) {
    val position by remember { derivedStateOf { pager.currentPage + pager.currentPageOffsetFraction } }
    pages.forEachIndexed { index, page ->
        val visible by remember { derivedStateOf { abs(position - index) < 1f } }
        if (visible) {
            Orbs(page.orbs, Modifier.graphicsLayer { alpha = (1f - abs(position - index)).coerceIn(0f, 1f) })
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val colors = AvailablissTheme.colors
    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 24.dp else 7.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "dot")
            val color by animateColorAsState(if (selected) colors.accent else colors.line3, label = "dot-color")
            Box(Modifier.size(width, 7.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** One page: its scene above, title and a line of text below. */
@Composable
private fun IntroPageContent(page: IntroPage, index: Int, pager: PagerState, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    // Scenes play once the page has settled in the middle.
    val active = pager.settledPage == index
    // The text rises in once the page has arrived.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) shown = true }
    // How far this page is from the middle (-1 … 1), for the parallax.
    val offset = { (pager.currentPage - index) + pager.currentPageOffsetFraction }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val art = (maxHeight - 210.dp).coerceIn(220.dp, 320.dp)
        val scale = minOf(art / 320.dp, (maxWidth - 24.dp) / 340.dp)
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(art)
                    .graphicsLayer {
                        // The picture travels a little further than the page as you swipe.
                        val o = offset()
                        translationX = o * 70.dp.toPx()
                        val s = 1f - abs(o) * 0.08f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - abs(o).coerceAtMost(1f) * 0.6f
                    }
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.requiredSize(340.dp, 320.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        IntroPage.Group -> GroupScene(active, reduceMotion)
                        IntroPage.Calendars -> CalendarScene(active, reduceMotion)
                        IntroPage.Grid -> MonthScene(active, reduceMotion)
                        IntroPage.Proposal -> ProposalScene(active, reduceMotion)
                        IntroPage.Chat -> ChatScene(active, reduceMotion)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        val o = offset()
                        translationX = o * 24.dp.toPx()
                        alpha = 1f - abs(o).coerceAtMost(1f) * 0.8f
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    page.eyebrow.uppercase(),
                    style = AvailablissTheme.type.captionStrong.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                    color = colors.accentInk,
                    modifier = Modifier.entrance(shown, 0, reduceMotion),
                )
                Text(
                    page.title,
                    style = AvailablissTheme.type.displaySmall,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() }.entrance(shown, 70, reduceMotion),
                )
                Text(
                    page.body,
                    style = AvailablissTheme.type.bodyLarge,
                    color = colors.muted,
                    modifier = Modifier.entrance(shown, 140, reduceMotion),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Rises and fades in once [shown], after [delayMs]. */
@Composable
private fun Modifier.entrance(shown: Boolean, delayMs: Int, reduceMotion: Boolean): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) tween(200) else tween(520, delayMillis = 100 + delayMs, easing = EaseOutBack),
        label = "entrance",
    )
    return graphicsLayer {
        alpha = progress.coerceIn(0f, 1f)
        translationY = if (reduceMotion) 0f else (1f - progress) * 16.dp.toPx()
    }
}

private val EaseOutBack = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** System "Remove animations" (animator duration scale 0): scenes show their last frame. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
