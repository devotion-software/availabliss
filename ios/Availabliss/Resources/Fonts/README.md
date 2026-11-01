# Fonts

The design uses two Google Fonts (SIL Open Font License, bundled and committed):

- **Bricolage Grotesque**: display (titles, numbers)
- **Instrument Sans**: body / UI

This folder holds the **variable** TTFs from Google Fonts, one file per family
covering every weight:

- `BricolageGrotesque-VariableFont_opsz,wdth,wght.ttf`
- `InstrumentSans-VariableFont_wdth,wght.ttf`
- `InstrumentSans-Italic-VariableFont_wdth,wght.ttf`

They're registered in `project.yml` (`UIAppFonts`). `AppFont` asks for each family by
name and sets the weight with `.weight(…)`, which drives the variable weight axis.
It falls back to the system font if a family is missing.
After replacing a file, run `make ios-project`.
