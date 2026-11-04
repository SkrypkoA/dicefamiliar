---
name: new-feature-screen
description: Checklist for adding or reworking a Dice Familiar feature play screen (like Dice, Number, Cards, Pool pick) so it follows the shared layout, theming, sharing, help, settings and testing rules. Use when creating a new feature tab/screen or restructuring an existing play screen.
---

# New feature play screen

Every rule here comes from CLAUDE.md (*Shared*, *Core Rules*, *Coding Conventions*). Go through it while building the screen and again before review.

## Structure
- [ ] Pure-Kotlin domain in `domain/<feature>/`, all randomness through the injected `RandomSource`, unbiased algorithms, input errors as codes (no throwing). Unit tests with a seeded source (delegate to `domain-tdd` when the spec is clear).
- [ ] ViewModel with an immutable UI state in a `StateFlow`; stateless composables; Hilt wiring in `di/`.
- [ ] Persistence: DataStore keys `<feature>.*` for options and last result; Room only for real collections (bump the version and add a migration for any schema change).
- [ ] Navigation: type-safe route under its tab; the bottom bar has one tab per feature; the app reopens on the last tab.

## Layout (top to bottom)
- [ ] `FeaturePanel` with the inputs/options, visibility persisted as `<feature>.panel_visible` (default shown).
- [ ] `FeatureHandleBar`: share (`FeatureShare`) and history (`FeatureHistory`) left, chevron centre, "?" right. Disabled buttons stay in place. No share/copy/history/help buttons anywhere else.
- [ ] Main area with `Modifier.weight(1f)`, drawn on the theme tray (`Modifier.themedTray(shape)`, `trayColors()` for text).
- [ ] Bottom action (Generate / Roll / Pick …) or action buttons.
- [ ] No top app bar and no screen title.

## Behaviour
- [ ] Result decided before any reveal animation; the UI never shows fake random values. Animations, sounds (`SoundPlayer`) and haptics follow the app settings.
- [ ] Numeric fields use `StepperNumberField`; the step logic lives in the domain.
- [ ] Sharing through `ShareMenuButton`: copy via `copyText`, text via `ACTION_SEND`, image via a `<Feature>Snapshot` + `CaptureAsImage` + `shareImage`.
- [ ] History: the last 20 results in memory, clearable, in a sheet.
- [ ] A `HelpTopic` list (`<FEATURE>_HELP`) for the "?" `HelpSheet`, covering every gesture.
- [ ] New settings get their own Settings section.

## Theming and text
- [ ] Colours only from `MaterialTheme.colorScheme`; icons from `LocalThemeStyle.current.icons` (add new icon slots to `ThemeIcons` for every theme). No hard-coded colours, no `when (theme)`.
- [ ] Every string in `res/values/strings.xml`; content descriptions on icons and the play area; check light and dark.

## Remote/LAN
- [ ] If the action may run on another device later, route it through a `RemoteAction` (see the `new-remote-action` skill), even when only local today.

## Finish
- [ ] Related unit tests + a Compose UI test class for the screen; run only those during the task.
- [ ] Update CLAUDE.md (feature details) and README.md (feature table, how to use).
