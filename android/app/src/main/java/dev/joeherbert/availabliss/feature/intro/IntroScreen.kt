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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.Orbs
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.VennLogo
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What each page says. The same pages, order and words as iOS's intro. */
internal enum class IntroPage(val eyebrow: String, val title: String, val body: String, val orbs: OrbLayout) {
    Welcome(
        "Welcome to Availabliss",
        "What should we call you?",
        "",
        OrbLayout.Appearance,
    ),
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
 * The first-launch intro, shown once before sign-in: a welcome that asks your name, then five pages on
 * what Availabliss does. Your name is one piece of text that travels with you: it slides from page to
 * page into each scene (the group you made, your calendar, the month, your reply, your message) as you
 * swipe, taking on the size and colour of the names around it.
 * Swipe or tap Next; Skip or Get started goes on to sign in with the name typed (empty if none).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroScreen(initialName: String = "", onFinish: (String) -> Unit) {
    val colors = AvailablissTheme.colors
    val pages = IntroPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val isLast = pager.currentPage == pages.lastIndex
    var name by rememberSaveable { mutableStateOf(initialName) }
    val firstName = firstNameOf(name)
    // The welcome needs a name before you can go on (Skip still skips the whole intro).
    val needsName = pager.currentPage == 0 && firstName.isEmpty()
    val chip = remember { IntroChipLayout() }

    fun next() {
        if (needsName) return
        keyboard?.hide()
        focus.clearFocus()
        if (isLast) onFinish(name.trim()) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    var lastPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage != lastPage) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            keyboard?.hide()
            focus.clearFocus()
        }
        lastPage = pager.currentPage
    }

    CompositionLocalProvider(LocalIntroChip provides chip, LocalIntroName provides firstName) {
        Box(Modifier.fillMaxSize().background(colors.bg).onGloballyPositioned { chip.origin = it.positionInRoot() }) {
            IntroOrbs(pager, pages)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
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
                        { onFinish(name.trim()) },
                        modifier = Modifier.graphicsLayer { alpha = skipAlpha },
                        style = AvailablissTheme.type.bodyMedium,
                        enabled = !isLast,
                    )
                }
                // Neighbouring pages stay laid out, so the chip has both spaces to slide between.
                HorizontalPager(
                    pager,
                    Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !needsName,
                ) { index ->
                    IntroPageContent(pages[index], index, pager, reduceMotion, name, { name = it.take(60) }, ::next)
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    // Out of the way while typing your name.
                    if (!WindowInsets.isImeVisible) PageDots(pages.size, pager.currentPage)
                    PrimaryButton(
                        text = if (isLast) "Get started" else "Next",
                        onClick = ::next,
                        modifier = Modifier.fillMaxWidth().semantics { stateDescription = "Page ${pager.currentPage + 1} of ${pages.size}" },
                        style = if (isLast) ButtonStyle.Accent else ButtonStyle.Solid,
                        height = 54.dp,
                        enabled = !needsName,
                    )
                }
            }
            TravellingName(firstName, chip, pager, pages.size)
        }
    }
}

/** A space a scene leaves for your name (in root pixels), and the colour of names there. */
internal data class IntroSlot(val rect: Rect, val tone: IntroNameTone)

/** The colour the name takes in a space: ink, the chat's muted grey, or the accent of the welcome's "Hi,". */
internal enum class IntroNameTone { Ink, Muted, Accent }

/** The travelling name is drawn at this multiple of 15sp and scaled down into each space, so it stays sharp. */
private const val NAME_DRAW_SCALE = 3f

/** The travelling name's own size and where it's drawn from, and the space each page leaves for it. */
@Stable
internal class IntroChipLayout {
    val slots = mutableStateMapOf<Int, IntroSlot>()
    var size by mutableStateOf(IntSize(450, 150))
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalIntroChip = staticCompositionLocalOf { IntroChipLayout() }

/** Your first name, for the scenes' avatars. */
internal val LocalIntroName = compositionLocalOf { "" }

internal fun firstNameOf(name: String): String = name.trim().split(" ").firstOrNull().orEmpty()

/**
 * The space a scene leaves for your name on [page], at [scale] of 15sp (so 13sp is 13 / 15), in [tone].
 * The name itself is drawn over the pages and slides between these.
 */
@Composable
internal fun IntroNameSlot(page: Int, scale: Float = 1f, tone: IntroNameTone = IntroNameTone.Ink, modifier: Modifier = Modifier) {
    val chip = LocalIntroChip.current
    val density = LocalDensity.current
    val fit = scale / NAME_DRAW_SCALE
    val size = with(density) { DpSize(chip.size.width.toDp() * fit, chip.size.height.toDp() * fit) }
    DisposableEffect(page) { onDispose { chip.slots.remove(page) } }
    Box(
        modifier.size(size).onGloballyPositioned { coordinates ->
            val topLeft = coordinates.localToRoot(Offset.Zero)
            val bottomRight = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            chip.slots[page] = IntroSlot(Rect(topLeft, bottomRight), tone)
        },
    )
}

/** Where the travelling name is drawn: its centre (relative to the intro), scale, and how muted and how accent (0–1). */
private class NamePlacement(val center: Offset, val scale: Float, val muted: Float, val accent: Float)

/**
 * Between the spaces left on the two pages either side of the scroll position, in proportion: both
 * pages move with the scroll, so the scroll cancels out and the name glides straight from one to the next.
 */
private fun IntroChipLayout.placement(pager: PagerState, count: Int, height: Int): NamePlacement? {
    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
    val from = position.toInt()
    val a = slots[from]
    val b = slots[minOf(from + 1, count - 1)]
    val start = a ?: b ?: return null
    val end = b ?: a ?: return null
    val t = if (a != null && b != null) position - from else 0f
    val cx = start.rect.center.x + (end.rect.center.x - start.rect.center.x) * t - origin.x
    val cy = start.rect.center.y + (end.rect.center.y - start.rect.center.y) * t - origin.y
    val h = start.rect.height + (end.rect.height - start.rect.height) * t
    fun blend(tone: IntroNameTone) = (if (start.tone == tone) 1f else 0f) * (1 - t) + (if (end.tone == tone) 1f else 0f) * t
    return NamePlacement(Offset(cx, cy), if (height > 0) h / height else 1f, blend(IntroNameTone.Muted), blend(IntroNameTone.Accent))
}

/**
 * Your first name: the one element that moves through the whole intro. Drawn large (3× the scenes' 15sp
 * names, so it stays sharp) and scaled down to each space, blending from ink to the muted grey the chat's
 * author names use, or to the accent of the welcome's "Hi,".
 */
@Composable
private fun TravellingName(name: String, chip: IntroChipLayout, pager: PagerState, count: Int) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .onSizeChanged { chip.size = it }
            .graphicsLayer {
                val place = chip.placement(pager, count, size.height.toInt())
                if (place == null || name.isEmpty()) {
                    alpha = 0f
                    return@graphicsLayer
                }
                alpha = 1f
                transformOrigin = TransformOrigin.Center
                translationX = place.center.x - size.width / 2f
                translationY = place.center.y - size.height / 2f
                scaleX = place.scale
                scaleY = place.scale
            }
            .clearAndSetSemantics { },
    ) {
        val base = AvailablissTheme.type.bodyMedium
        val style = base.copy(fontSize = base.fontSize * NAME_DRAW_SCALE, lineHeight = base.lineHeight * NAME_DRAW_SCALE)
        val place = { chip.placement(pager, count, chip.size.height) }
        Text(
            name,
            style = style,
            color = colors.ink,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val p = place()
                alpha = (1f - (p?.muted ?: 0f) - (p?.accent ?: 0f)).coerceAtLeast(0f)
            }
        )
        Text(name, style = style, color = colors.muted, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = place()?.muted ?: 0f })
        Text(name, style = style, color = colors.accent, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = place()?.accent ?: 0f })
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
private fun IntroPageContent(
    page: IntroPage,
    index: Int,
    pager: PagerState,
    reduceMotion: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    onSubmitName: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    // Scenes play once the page has settled in the middle.
    val active = pager.settledPage == index
    // The text rises in once the page has arrived.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) shown = true }
    // How far this page is from the middle (-1 … 1), for the parallax.
    val offset = { (pager.currentPage - index) + pager.currentPageOffsetFraction }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Smaller on shorter phones (and with the keyboard up), so the text always fits.
        val art = (maxHeight - 210.dp).coerceIn(if (page == IntroPage.Welcome) 120.dp else 220.dp, 320.dp)
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
                        translationX = -o * 70.dp.toPx()
                        val s = 1f - abs(o) * 0.08f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - abs(o).coerceAtMost(1f) * 0.6f
                    }
                    .clearAndSetSemantics { },
                // The welcome's "Hi," lines up with the text under it, so it shrinks towards the left.
                contentAlignment = if (page == IntroPage.Welcome) Alignment.CenterStart else Alignment.Center,
            ) {
                Box(
                    Modifier
                        .then(if (page == IntroPage.Welcome) Modifier.padding(start = 24.dp) else Modifier)
                        .requiredSize(340.dp, 320.dp)
                        .graphicsLayer {
                            if (page == IntroPage.Welcome) transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        IntroPage.Welcome -> WelcomeScene(index, reduceMotion)
                        IntroPage.Group -> GroupScene(index, active, reduceMotion)
                        IntroPage.Calendars -> CalendarScene(index, active, reduceMotion)
                        IntroPage.Grid -> MonthScene(index, active, reduceMotion)
                        IntroPage.Proposal -> ProposalScene(index, active, reduceMotion)
                        IntroPage.Chat -> ChatScene(index, active, reduceMotion)
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
                        translationX = -o * 24.dp.toPx()
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
                if (page == IntroPage.Welcome) {
                    AvTextField(
                        value = name,
                        onValueChange = onNameChange,
                        placeholder = "First name",
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        onImeAction = onSubmitName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .semantics { contentDescription = "Your name" }
                            .entrance(shown, 140, reduceMotion),
                    )
                }
                if (page.body.isNotEmpty()) {
                    Text(
                        page.body,
                        style = AvailablissTheme.type.bodyLarge,
                        color = colors.muted,
                        modifier = Modifier.entrance(shown, 140, reduceMotion),
                    )
                }
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
