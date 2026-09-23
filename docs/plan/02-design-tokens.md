# 02 — Design Tokens & Theme

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/theme/ (new)
- Depends on 01. Must exist before any screen is built.

## Goals
- `Color.kt`: token set as a data class `AcpColors` (background, surface, surfaceAlt, border, textPrimary, textSecondary, accent, onAccent, diffAdded, diffRemoved, error, warning)
  - Dark (default): bg `#14161A`, surface `#1C1F24`, text `#E7E9EC`, secondary `#8B92A0`, accent `#5FB3A3`, diff add `#4E9A6B`, diff remove `#C1666B`
  - Light: bg `#F5F6F8`, surface `#FFFFFF`, darker text, same accent
- `Type.kt`: one sans family (`FontFamily.SansSerif`) + one mono (`FontFamily.Monospace`); styles differ by weight/size/color only (`body`, `bodyMono`, `label`, `title`, `caption`)
- `Theme.kt`: `AcpTheme` providing `LocalAcpColors`/`LocalAcpType` via `CompositionLocal` and mapping onto `MaterialTheme` color scheme so Material 3 components pick up tokens; dark by default, follows system
- Shape tokens: small radius (4dp) for blocks, no default elevation/shadows

## Notes
- No hardcoded colors/styles in composables after this — always `AcpTheme.colors` / `AcpTheme.type`
- Avoid: warm cream + terracotta, uniform big radii + shadows, ALL CAPS labels, decorative gradients, "→" on buttons

## Testing
- `./gradlew assembleDebug`
- `@Preview` of a token swatch in dark and light

## Tools / Skills
- Bash: `./gradlew assembleDebug`

## Implementation
<!-- Write you've done in here -->
