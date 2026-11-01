# Availabliss design system

The source of truth is the signed-off concept in [`design-concept/`](design-concept/)
(one `.dc.html` file per screen). Every platform implements the same tokens and
components below, so the iOS, Android and web apps look like one product.

## Look

"Dawn glass": frosted panels floating over three soft, blurred colour orbs.
Calm, bright, generous spacing, rounded everything. No gradient washes, no emoji
in UI chrome, icons are 2px-stroke line icons.

## Typography

| Role | Font | Weight | Size / tracking |
| ---- | ---- | ------ | --------------- |
| Display (titles, numbers) | Bricolage Grotesque | 650–750 | 20–44px, letter-spacing -0.025em |
| Body / UI | Instrument Sans | 400–600 | 11–16px |

Both are Google Fonts (OFL). Bundle them in the native apps.

## Colour tokens

Mode tokens:

| Token | Light | Dark |
| ----- | ----- | ---- |
| `bg` | `#ECEEF7` | `#0C0D1D` |
| `ink` (text) | `#1A1B3A` | `#F1F2FA` |
| `muted` | `#4D5072` | `#A6A9C8` |
| `neutral-ink` | `#3A3D5C` | `#C9CBE3` |
| `surface` (inputs, cells) | `rgba(255,255,255,.62)` | `rgba(255,255,255,.07)` |
| `glass` (panels) | `rgba(255,255,255,.50)` | `rgba(26,28,56,.50)` |
| `glass-border` | `rgba(255,255,255,.80)` | `rgba(255,255,255,.12)` |
| `glass-hi` (inner top highlight) | `rgba(255,255,255,.90)` | `rgba(255,255,255,.10)` |
| `shadow` | `rgba(43,38,120,.38)` | `rgba(0,0,0,.60)` |
| `line` / `line-2` / `line-3` | ink at 7% / 14% / 28% | white at 8% / 16% / 30% |
| `solid` / `on-solid` (primary button) | `#1A1B3A` / `#FFFFFF` | `#F1F2FA` / `#0C0D1D` |
| `on-accent` | `#FFFFFF` | `#FFFFFF` |
| `busy-bg` / `busy-line` | `rgba(255,255,255,.40)` / `#B9BCD6` | `rgba(0,0,0,.28)` / `#4A4D70` (sinks below the page so busy ≠ free in dark) |
| `ok` / `ok-bg` / `ok-ink` | `#0FA37F` / `#D3F5EA` / `#0D6B52` | `#34D3A0` / `rgba(52,211,160,.16)` / `#7EE8C6` |
| `warn` / `warn-bg` / `warn-ink` | `#FF8A5E` / `#FFE0CF` / `#8A3410` | `#FF9A73` / `rgba(255,138,94,.18)` / `#FFBCA2` |
| `t1`…`t5` (group tiles, avatars) | `#FFD3BF #D6DAFF #C8F1E4 #F5D6EE #FFE7A3` | `#5C3B2E #363B78 #1E5045 #58304F #5A4A1E` |
| orb opacity | 0.85 | 0.42 |

Colour themes (user setting `colorTheme`, saved to the account):

| Theme | `accent` | `accent-ink` light / dark | `accent-soft` light / dark | Orbs 1 / 2 / 3 |
| ----- | -------- | ------------------------- | -------------------------- | -------------- |
| dawn (default) | `#5546FF` (dark `#6A5CFF`) | `#3B2FD0` / `#B4ACFF` | `rgba(85,70,255,.14)` / `rgba(130,118,255,.24)` | `#FFB894 #9DA6FF #8EE3CC` |
| lagoon | `#0B7F8A` | `#06646D` / `#7FDDE4` | `rgba(11,127,138,.14)` / `rgba(19,156,168,.26)` | `#86E3E0 #8EC5FF #B5EBA8` |
| sunset | `#C9401F` | `#A8341A` / `#FFB199` | `rgba(201,64,31,.13)` / `rgba(224,83,47,.26)` | `#FFA585 #FF9CC0 #FFD37E` |
| orchid | `#9333C9` (dark `#A646DB`) | `#7A27AA` / `#DDB0F7` | `rgba(147,51,201,.13)` / `rgba(166,70,219,.26)` | `#F4B0E0 #C9A8FF #A9B8FF` |
| grove | `#2D7A4A` | `#22633B` / `#9FE0B6` | `rgba(45,122,74,.14)` / `rgba(58,148,96,.26)` | `#C3E88F #8FD8B5 #F3D78A` |

All accent fills keep white text at ≥ 4.5:1 contrast.

## Glass

- Panel: `glass` fill, 26px background blur + 170% saturation, 1px `glass-border`,
  1px inner top highlight `glass-hi`, shadow `0 18px 40px -22px shadow`.
- iOS: `.ultraThinMaterial` tinted with `glass`. Android: `Modifier.blur` is not a
  backdrop blur, so use a translucent `glass` fill over the orbs (API 31+ can use
  `RenderEffect` on the orb layer for extra softness).
- Orbs: 3 circles ~300–380px, blur 60px, positioned off-canvas corners per screen.

## Shape & spacing

- Radii: screens 40, sheets 32, cards 24–28, controls 14–18, chips/pills 999.
- Touch targets ≥ 44px. Screen padding 20px. Gap scale 4 / 6 / 8 / 10 / 12 / 14 / 18.
- Primary button height 50–54. Secondary uses `surface` with `line-2` border.

## Availability states (calendar cells)

| State | Look |
| ----- | ---- |
| hidden | not drawn at all (column dropped if the weekday can never match) |
| free | `surface` fill, `glass-border` border |
| busy | `busy-bg` fill at **50% opacity** |
| match | `accent` fill, `on-accent` text, glow `0 12px 24px -14px accent`; multi-day slots join into one band (inner corners 5px) |
| blocked by hand | diagonal stripes of `accent-soft` with an `accent` border (own calendar) / `line-3` stripes (group day view) |
| selected | 2px `ink` outline, 2px offset |

## Components

`GlassPanel`, `Orbs`, `PrimaryButton`, `SecondaryButton`, `IconButton` (44pt circle),
`SegmentedControl`, `Chip`, `Toggle`, `Avatar` / `AvatarStack`, `GroupTile`
(monogram on `t1`–`t5`), `StatusPill` (indigo / mint / apricot / neutral), `SyncRing`,
`MonthGrid`, `WeekGrid`, `DayTimeline`, `TabBar` (floating glass), `BottomSheet`,
`MessageBubble`, `PollCard`, `ProposalCard`, `ReadReceipt`.

## Screens

Sign in · Choose busy calendars · My time · Groups home · Group availability
(month / week / day) · Group timing · Proposal & accept · Group chat (polls, read
receipts, message info) · Appearance · Members & invites · New group · Event editor.
