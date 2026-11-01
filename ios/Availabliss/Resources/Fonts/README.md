# Fonts

The design uses two Google Fonts (SIL Open Font License, fine to bundle and commit):

- **Bricolage Grotesque** — display (titles, numbers)
- **Instrument Sans** — body / UI

Download them from Google Fonts and drop these **static** TTFs into this folder
(`ios/Availabliss/Resources/Fonts/`), then re-run `make ios-project`:

| File | From |
| ---- | ---- |
| `BricolageGrotesque-SemiBold.ttf` | fonts.google.com/specimen/Bricolage+Grotesque → `static/` |
| `BricolageGrotesque-Bold.ttf` | 〃 |
| `BricolageGrotesque-ExtraBold.ttf` | 〃 |
| `InstrumentSans-Regular.ttf` | fonts.google.com/specimen/Instrument+Sans → `static/` |
| `InstrumentSans-Medium.ttf` | 〃 |
| `InstrumentSans-SemiBold.ttf` | 〃 |
| `InstrumentSans-Bold.ttf` | 〃 |

If the download names the Bricolage statics with an optical-size prefix (e.g.
`BricolageGrotesque_24pt-Bold.ttf`), use the default (non-prefixed) ones, or rename them to the
names above — they're registered by file name in `UIAppFonts` (see `project.yml`).

The code looks faces up by PostScript name (`BricolageGrotesque-Bold`, `InstrumentSans-SemiBold`,
… — see `DesignSystem/Typography.swift`). Check a file's PostScript name with
`fc-scan --format "%{postscriptname}\n" <file>` or Font Book → Get Info.

**Without the files the app still works**: `Font.custom` falls back to the system font at the
same size, still Dynamic Type scaled, with the intended weight.
