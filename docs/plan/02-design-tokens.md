# 02 — Design Tokens & Theme

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/theme/ (new)
- Depends on 01. Must exist before any screen is built.

## Goals
- `Color.kt`: token set as a data class `AcpColors` (background, surface, surfaceAlt, border, textPrimary, textSecondary, accent, onAccent, diffAdded, diffRemoved, error, warning)
  - Dark (default): bg `#14161A`, surface `#1C1F24`, text `#E7E9EC`, secondary `#8B92A0`, accent `#5FB3A3`, diff add `#4E9A6B`, diff remove `#C1666B`
  - Light: bg `#F5F6F8`, surface `#FFFFFF`, darker text, same accent
- `Type.kt`: one sans family (`FontFamily.SansSerif`) + one mono (`FontFamily.Monospace`); styles differ by weight/size/color only (`body`, `bodyMono`, `label`, `title`, `caption`)
- `Theme.kt`: `AcpTheme(darkTheme = isSystemInDarkTheme())`
  - builds full Material 3 `darkColorScheme` / `lightColorScheme` from tokens (primary = accent, background, surface, surfaceVariant/surfaceContainer*, onSurface, onSurfaceVariant = text secondary, outline/outlineVariant = border, error) so stock M3 components (Button, TextField, Dialog, BottomSheet, TopAppBar) look right with no per-call overrides
  - **no dynamic color** (see overview deviation)
  - extended colors not in M3 (`diffAdded`, `diffRemoved`, `warning`, role border colors) exposed through `LocalAcpExtendedColors` + `AcpTheme.extended` accessor, per skill's "Extended Colors" pattern
  - sets status/nav bar icon contrast for edge-to-edge
- `Type.kt`: M3 `Typography` type scale (`titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `labelMedium`, `labelSmall`) on the sans family, plus extended `codeMedium`/`codeSmall` mono styles exposed via `AcpTheme.code`
- `Shape.kt`: M3 `Shapes` overridden to small radii (`extraSmall`/`small` = 4dp, `medium` = 6dp, `large` = 8dp) — not the skill's default 12–16dp
- Tonal elevation only (`tonalElevation = 0` on list surfaces), no drop shadows

## Notes
- No hardcoded colors/styles in composables after this — use `MaterialTheme.colorScheme` / `MaterialTheme.typography` / `MaterialTheme.shapes` first, `AcpTheme.extended` / `AcpTheme.code` only for what M3 lacks
- Check contrast: text secondary `#8B92A0` on `#14161A` and accent on both backgrounds meet WCAG AA for body/labels
- Avoid: warm cream + terracotta, uniform big radii + shadows, ALL CAPS labels, decorative gradients, "→" on buttons

## Testing
- `./gradlew assembleDebug`
- `@Preview` of a token swatch in dark and light

## Tools / Skills
- Skill `mobile-android-design` → `references/material3-theming.md` (color scheme, extended colors, type scale, shapes, tonal elevation)
- Bash: `./gradlew assembleDebug`

## Implementation
<!-- Write you've done in here -->
