package dev.joeherbert.availabliss.feature.intro

// The intro's little animated scenes, built from the app's own pieces: a group card, calendar rows
// turning into busy time, the month grid lighting up, a proposal filling with replies, and a chat.
// Each is drawn at 340 × 320dp and plays while its page shows (with Remove animations, its last frame).

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.availability.LegendItem
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.softShadow
import dev.joeherbert.availabliss.designsystem.component.stripes
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissColors
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.delay

// region Playback

/**
 * Steps a scene through its frames ([gaps] = ms before each) while its page shows, holds the last
 * one, then rests and plays again. Leaving the page puts it back to the start once it's off screen.
 */
@Composable
private fun rememberStep(active: Boolean, reduceMotion: Boolean, gaps: List<Long>, holdMs: Long = 3000): Int {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, reduceMotion) {
        if (!active) {
            delay(600)
            step = 0
            return@LaunchedEffect
        }
        if (reduceMotion) {
            step = gaps.size
            return@LaunchedEffect
        }
        while (true) {
            gaps.forEachIndexed { index, gap ->
                delay(gap)
                step = index + 1
            }
            delay(holdMs)
            step = 0
            delay(900)
        }
    }
    return step
}

/** 0 → 1 with a bouncy spring as [visible] turns on. */
@Composable
private fun pop(visible: Boolean, damping: Float = 0.55f): Float = animateFloatAsState(
    if (visible) 1f else 0f,
    spring(dampingRatio = damping, stiffness = Spring.StiffnessMediumLow),
    label = "pop",
).value

/** 0 → 1, eased, as [on] turns on. */
@Composable
private fun fade(on: Boolean, durationMs: Int = 420, delayMs: Int = 0): Float = animateFloatAsState(
    if (on) 1f else 0f,
    tween(durationMs, delayMillis = delayMs),
    label = "fade",
).value

/** Pops in from [from] scale, fading, as [p] goes 0 → 1. */
private fun Modifier.popIn(p: Float, from: Float = 0.4f, dy: Float = 0f, origin: TransformOrigin = TransformOrigin.Center): Modifier = graphicsLayer {
    alpha = p.coerceIn(0f, 1f)
    val s = from + (1f - from) * p
    scaleX = s
    scaleY = s
    translationY = (1f - p) * dy * density
    transformOrigin = origin
}

private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private data class Friend(val name: String, val tile: (AvailablissColors) -> Color) {
    val person get() = AvatarPerson(name, name)
}

private val friends = listOf(
    Friend("You") { it.t2 },
    Friend("Alex") { it.t1 },
    Friend("Priya") { it.t3 },
    Friend("Jo") { it.t4 },
    Friend("Tom") { it.t5 },
)

private val CardShape = RoundedCornerShape(28.dp)

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(AvailablissTheme.colors.line))

/** A small glass capsule under a scene ("Invite link copied"). */
@Composable
private fun ScenePill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.height(38.dp).glass(CircleShape).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// endregion

// region 1. Make a group

/** A group card filling up as friends join from the invite link. */
@Composable
internal fun GroupScene(active: Boolean, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    // Step 1 sends the link; steps 2–5 bring in a friend each.
    val step = rememberStep(active, reduceMotion, listOf(600, 700, 450, 450, 450))
    val members = 1 + maxOf(0, step - 1)
    Box(contentAlignment = Alignment.Center) {
        // Another of your groups peeking out behind, for depth.
        GroupHeader(
            "Book club",
            "BC",
            colors.t4,
            "6 members",
            Modifier
                .offset(x = (-24).dp, y = (-124).dp)
                .graphicsLayer {
                    rotationZ = -7f
                    alpha = 0.75f
                }
                .width(260.dp)
                .glass(RoundedCornerShape(24.dp))
                .padding(16.dp),
        )
        Column(
            Modifier.offset(y = (-6).dp).width(300.dp).glass(CardShape).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupHeader("Lake District crew", "LD", colors.t3, if (members == 1) "1 member" else "$members members")
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    friends.forEachIndexed { index, friend ->
                        val p = pop(index < members, damping = 0.5f)
                        Avatar(friend.person, Modifier.popIn(p, from = 0.3f), size = 42.dp, background = friend.tile(colors), border = colors.bg)
                    }
                }
                Spacer(Modifier.weight(1f))
                val nudge by animateFloatAsState(if (step == 1) 1.08f else 1f, spring(dampingRatio = 0.5f), label = "invite")
                Row(
                    Modifier
                        .graphicsLayer {
                            scaleX = nudge
                            scaleY = nudge
                        }
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AvIcons.AddPerson, null, tint = colors.onAccent, modifier = Modifier.size(15.dp))
                    Text("Invite", style = AvailablissTheme.type.label, color = colors.onAccent)
                }
            }
        }
        ScenePill(Modifier.offset(y = 112.dp).popIn(pop(step >= 1, 0.65f), from = 0.7f, dy = 18f)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                Icon(AvIcons.Link, null, tint = colors.accentInk, modifier = Modifier.size(14.dp))
            }
            Text("Invite link copied", style = AvailablissTheme.type.label, color = colors.ink)
            Icon(AvIcons.Check, null, tint = colors.ok, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun GroupHeader(title: String, monogram: String, tile: Color, subtitle: String, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTile(monogram, tile, size = 50.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AvailablissTheme.type.titleXSmall.copy(fontSize = 18.sp), color = colors.ink, maxLines = 1)
            Text(subtitle, style = AvailablissTheme.type.labelRegular, color = colors.muted)
        }
    }
}

// endregion

// region 2. Connect your calendars

private data class SceneEvent(val time: String, val title: String, val color: Color)

private val events = listOf(
    SceneEvent("09:30", "Dentist", Color(0xFFFF8A5E)),
    SceneEvent("13:00", "Lunch with Mum", Color(0xFF5B8DEF)),
    SceneEvent("19:00", "Five-a-side", Color(0xFF2FB98A)),
)

/** Your day's events turning, one by one, into plain busy time: all the group ever sees. */
@Composable
internal fun CalendarScene(active: Boolean, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    val step = rememberStep(active, reduceMotion, listOf(800, 550, 400, 400, 450))
    Column(Modifier.offset(y = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(300.dp).glass(CardShape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val shared = fade(step >= 1, 320)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    val style = AvailablissTheme.type.titleXSmall.copy(fontSize = 18.sp)
                    Text("Your Tuesday", style = style, color = colors.ink, modifier = Modifier.graphicsLayer { alpha = 1f - shared })
                    Text("What your group sees", style = style, color = colors.ink, modifier = Modifier.graphicsLayer { alpha = shared })
                }
                Box(Modifier.size(32.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(if (step >= 1) AvIcons.Shield else AvIcons.Calendar, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
                }
            }
            events.forEachIndexed { index, event -> EventRow(event, hidden = step >= index + 2) }
        }
        ScenePill(Modifier.popIn(pop(step >= 5, 0.65f), from = 0.7f, dy = -12f)) {
            Icon(AvIcons.Shield, null, tint = colors.ok, modifier = Modifier.size(16.dp))
            Text("Only busy time is shared", style = AvailablissTheme.type.label, color = colors.ink)
        }
    }
}

@Composable
private fun EventRow(event: SceneEvent, hidden: Boolean) {
    val colors = AvailablissTheme.colors
    val p = fade(hidden, 450)
    val shape = RoundedCornerShape(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(event.time, style = AvailablissTheme.type.captionStrong, color = colors.muted, modifier = Modifier.width(42.dp))
        Box(Modifier.weight(1f).height(44.dp)) {
            // The event as it is in your calendar…
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer { alpha = 1f - p }
                    .blur((p * 8).dp)
                    .clip(shape)
                    .background(event.color.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(4.dp, 22.dp).clip(CircleShape).background(event.color))
                Text(event.title, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 1)
            }
            // …and as your group sees it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer {
                        alpha = p
                        val s = 0.94f + 0.06f * p
                        scaleX = s
                        scaleY = s
                    }
                    .clip(shape)
                    .background(colors.busyBg)
                    .stripes(colors.busyLine.copy(alpha = 0.35f), stripeWidth = 1.5.dp, period = 7.dp)
                    .border(1.dp, colors.glassBorder, shape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Busy", style = AvailablissTheme.type.label, color = colors.muted)
            }
        }
    }
}

// endregion

// region 3. The group calendar

private enum class SceneDay { Works, Busy, Outside, Free }

private val days = listOf(
    SceneDay.Outside, SceneDay.Busy, SceneDay.Outside, SceneDay.Free, SceneDay.Works, SceneDay.Works, SceneDay.Busy,
    SceneDay.Outside, SceneDay.Busy, SceneDay.Outside, SceneDay.Works, SceneDay.Busy, SceneDay.Works, SceneDay.Works,
    SceneDay.Outside, SceneDay.Outside, SceneDay.Busy, SceneDay.Free, SceneDay.Works, SceneDay.Busy, SceneDay.Works,
)
private const val FIRST_DAY = 9

/** The day picked out at the end (Saturday the 14th). */
private const val PICKED = 5

/**
 * A few weeks of the month grid: busy days darken, days outside the timing fade, then the days that
 * work for everyone light up in the accent, in a wave.
 */
@Composable
internal fun MonthScene(active: Boolean, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    val step = rememberStep(active, reduceMotion, listOf(600, 600, 1100), holdMs = 3200)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(330.dp).glass(CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("June", style = AvailablissTheme.type.headlineSmall.copy(fontSize = 20.sp), color = colors.ink, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                    friends.take(4).forEach { Avatar(it.person, size = 24.dp, background = it.tile(colors), border = colors.bg) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = AvailablissTheme.type.micro, color = colors.muted, modifier = Modifier.weight(1f).padding(start = 6.dp))
                }
            }
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { column -> DayCell(row * 7 + column, step, Modifier.weight(1f)) }
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem("Works for everyone") { Box(Modifier.size(12.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(colors.accent)) }
                LegendItem("Someone's busy") {
                    Box(
                        Modifier
                            .size(12.dp, 10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.busyBg.copy(alpha = colors.busyBg.alpha * 0.5f))
                            .border(1.dp, colors.busyLine, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        ScenePill(Modifier.popIn(pop(step >= 3, 0.65f), from = 0.7f, dy = -14f)) {
            Icon(AvIcons.Sparkle, null, tint = colors.accentInk, modifier = Modifier.size(14.dp))
            Text("Sat 14 June works for everyone", style = AvailablissTheme.type.label, color = colors.ink)
        }
    }
}

@Composable
private fun DayCell(index: Int, step: Int, modifier: Modifier) {
    val colors = AvailablissTheme.colors
    val kind = days[index]
    val shape = RoundedCornerShape(11.dp)
    val revealed = fade(step >= 1, 380)
    // The wave: across, then down, as the days light up.
    val wave = if (step == 2) (index % 7 + index / 7) * 60 else 0
    val lit by animateFloatAsState(
        if (step >= 2 && kind == SceneDay.Works) 1f else 0f,
        tween(450, delayMillis = wave, easing = Overshoot),
        label = "lit",
    )
    val selected = pop(step >= 3 && index == PICKED, 0.5f)
    val litAlpha = lit.coerceIn(0f, 1f)
    val fadeTo = when (kind) {
        SceneDay.Busy -> 0.5f
        SceneDay.Outside -> 0.35f
        else -> 1f
    }
    val alpha = 1f - (1f - fadeTo) * revealed
    val restScale = if (kind == SceneDay.Works) 0.94f + 0.06f * lit else 1f
    Box(
        modifier
            .zIndex(if (index == PICKED) 1f else 0f)
            .graphicsLayer {
                val s = restScale + 0.12f * selected
                scaleX = s
                scaleY = s
            }
            .height(38.dp)
            .then(if (litAlpha > 0f) Modifier.softShadow(shape, colors.accent.copy(alpha = 0.5f * litAlpha), offsetY = 6.dp, blur = 16.dp, spread = (-4).dp) else Modifier)
            .clip(shape)
            .background(colors.surface.copy(alpha = colors.surface.alpha * alpha))
            .background(colors.busyBg.copy(alpha = colors.busyBg.alpha * (if (kind == SceneDay.Busy) revealed else 0f) * alpha))
            .background(colors.accent.copy(alpha = litAlpha))
            .border(1.dp, colors.glassBorder.copy(alpha = colors.glassBorder.alpha * alpha * (1f - litAlpha)), shape)
            .then(if (selected > 0.01f) Modifier.border(2.dp, colors.onAccent.copy(alpha = selected.coerceIn(0f, 1f)), shape) else Modifier)
            .padding(start = 7.dp, top = 5.dp),
    ) {
        val ink = if (litAlpha > 0.5f) colors.onAccent else colors.ink
        Text("${FIRST_DAY + index}", style = AvailablissTheme.type.label, color = ink.copy(alpha = if (litAlpha > 0.5f) 1f else alpha))
    }
}

// endregion

// region 4. Propose a time

/** A proposal: the replies tick in, then "Everyone's in." */
@Composable
internal fun ProposalScene(active: Boolean, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    val step = rememberStep(active, reduceMotion, listOf(800, 550, 550, 700), holdMs = 3200)
    val replies = 1 + minOf(step, 3)
    val confirmed = pop(step >= 4, 0.6f)
    Column(
        Modifier
            .graphicsLayer {
                val s = 1f + 0.02f * confirmed
                scaleX = s
                scaleY = s
            }
            .width(300.dp)
            .glass(CardShape)
            .border(2.dp, colors.accent.copy(alpha = (confirmed * 0.9f).coerceIn(0f, 1f)), CardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
            Row(
                Modifier.graphicsLayer { alpha = (1f - confirmed).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Avatar(friends[1].person, size = 24.dp, background = colors.t1)
                Text("Alex proposed a time", style = AvailablissTheme.type.label, color = colors.muted)
            }
            Row(
                Modifier
                    .popIn(confirmed, from = 0.5f, origin = TransformOrigin(0f, 0.5f))
                    .softShadow(CircleShape, colors.accent.copy(alpha = 0.55f), offsetY = 6.dp, blur = 24.dp, spread = (-6).dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AvIcons.ProposalCheck, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                Text("Everyone's in.", style = AvailablissTheme.type.titleXSmall, color = colors.onAccent)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Saturday 14 June", style = AvailablissTheme.type.headlineMedium, color = colors.ink)
            Text("7pm · The Old Bell", style = AvailablissTheme.type.body, color = colors.muted)
        }
        Divider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            friends.take(4).forEachIndexed { index, friend ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(friend.person, size = 30.dp, background = friend.tile(colors))
                    Text(friend.name, style = AvailablissTheme.type.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                    ReplyChip(index < replies)
                }
            }
        }
    }
}

@Composable
private fun ReplyChip(accepted: Boolean) {
    val colors = AvailablissTheme.colors
    val p = pop(accepted, 0.5f)
    Box(contentAlignment = Alignment.Center) {
        Text(
            "Waiting",
            style = AvailablissTheme.type.captionStrong,
            color = colors.muted,
            modifier = Modifier
                .graphicsLayer { alpha = (1f - p).coerceIn(0f, 1f) }
                .height(26.dp)
                .border(1.dp, colors.line2, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Row(
            Modifier.popIn(p).height(26.dp).clip(CircleShape).background(colors.okBg).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(AvIcons.Check, null, tint = colors.okInk, modifier = Modifier.size(11.dp))
            Text("In", style = AvailablissTheme.type.captionStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = colors.okInk)
        }
    }
}

// endregion

// region 5. Chat with the group

/** The group's chat: messages arrive, someone's typing, and your reply gets a heart. */
@Composable
internal fun ChatScene(active: Boolean, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    val step = rememberStep(active, reduceMotion, listOf(600, 700, 1100, 900, 600))
    Column(Modifier.width(310.dp).height(290.dp).glass(CardShape).padding(16.dp)) {
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GroupTile("LD", colors.t3, size = 32.dp, radius = 11.dp)
            Text("Lake District crew", style = AvailablissTheme.type.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
            Icon(AvIcons.Chat, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
        }
        Divider()
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(0.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            Arrival(step >= 1, mine = false) { Incoming(friends[1], "Saturday works for me!") }
            Arrival(step == 2, mine = false) { Typing(friends[2], reduceMotion) }
            Arrival(step >= 3, mine = false) { Incoming(friends[2], "Shall we book the Old Bell?") }
            Arrival(step >= 4, mine = true) { Mine("Booked! Table for four at 7.", hearted = step >= 5) }
        }
    }
}

@Composable
private fun Arrival(visible: Boolean, mine: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Bottom) +
            scaleIn(spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.5f, transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)) +
            fadeIn(),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(400)),
    ) { content() }
}

private fun bubbleShape(mine: Boolean) = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomEnd = if (mine) 6.dp else 18.dp,
    bottomStart = if (mine) 18.dp else 6.dp,
)

@Composable
private fun Incoming(friend: Friend, text: String) {
    val colors = AvailablissTheme.colors
    Row(Modifier.fillMaxWidth().padding(end = 30.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(friend.person, size = 26.dp, background = friend.tile(colors))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(friend.name, style = AvailablissTheme.type.micro, color = colors.muted, modifier = Modifier.padding(start = 4.dp))
            Text(
                text,
                style = AvailablissTheme.type.body,
                color = colors.ink,
                modifier = Modifier
                    .clip(bubbleShape(false))
                    .background(colors.surface)
                    .border(1.dp, colors.glassBorder, bubbleShape(false))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Mine(text: String, hearted: Boolean) {
    val colors = AvailablissTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 50.dp, bottom = 10.dp), horizontalArrangement = Arrangement.End) {
        Box {
            Text(
                text,
                style = AvailablissTheme.type.body,
                color = colors.onAccent,
                modifier = Modifier.clip(bubbleShape(true)).background(colors.accent).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-8).dp, y = 10.dp)
                    .popIn(pop(hearted, 0.45f), from = 0.2f)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.bg)
                    .border(1.dp, colors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AvIcons.Heart, null, tint = colors.accentInk, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Three dots that rise in turn, in an incoming bubble. */
@Composable
private fun Typing(friend: Friend, reduceMotion: Boolean) {
    val colors = AvailablissTheme.colors
    val phase by rememberInfiniteTransition(label = "typing").animateFloat(
        0f,
        3f,
        infiniteRepeatable(tween(840, easing = LinearEasing), RepeatMode.Restart),
        label = "typing-phase",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(friend.person, size = 26.dp, background = friend.tile(colors))
        Row(
            Modifier.height(34.dp).clip(bubbleShape(false)).background(colors.surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { dot ->
                val lit = !reduceMotion && phase.toInt() == dot
                Box(
                    Modifier
                        .graphicsLayer {
                            alpha = if (lit) 1f else 0.4f
                            translationY = if (lit) -3.dp.toPx() else 0f
                        }
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.muted),
                )
            }
        }
    }
}

// endregion
