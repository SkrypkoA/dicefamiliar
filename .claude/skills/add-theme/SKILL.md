---
name: add-theme
description: Add a new Dice Familiar app theme (AppThemeSpec) - palettes, typography, shapes, themed icons, tray painter and dice colours - the data-driven way. Use when the user asks for a new theme or to restyle an existing one.
---

# Add a theme

Themes are data, not code branches (CLAUDE.md *Shared → Themes*). Never add `when (theme)` anywhere.

1. **Read the existing specs** in `ui/theme/themes/` (Classic, Tavern, Cyberpunk), `AppThemeSpec`, `AppThemes`, `ThemeIcons`, `ui/theme/tray/`.
2. **Write `ui/theme/themes/<Name>Theme.kt`** with one `AppThemeSpec`:
   - `id` (stable, lowercase; it's stored in DataStore `settings.theme`), name and description string resources.
   - `light` and/or `dark` `ColorScheme`. With one palette, the theme always has that brightness; with both, the user's DarkMode applies. Set `supportsDynamicColor` only if wallpaper colours make sense.
   - Typography and shapes.
   - `ThemeIcons`: tab icons, Roll and Generate icons (and every other slot the type defines).
   - `TrayPainter`: reuse `PlainTray`, `FeltTray` or `NeonGridTray` with its own colours, or add a new painter in `ui/theme/tray/`. Decorative "noise" uses a fixed hash, never `RandomSource`.
   - Optional `dice = ThemeDiceColors(fill, edge, text)`. A dice set's own colours always win.
3. **Register it** in `AppThemes.all`.
4. **Strings:** `theme_<id>` and `theme_<id>_description` in `res/values/strings.xml`.
5. **Check:** Settings → Appearance shows the preview automatically; check every tab, both brightnesses if supported, text contrast on the tray (`trayColors()`), system-bar icon colours, and share snapshots (they render on the theme's tray).
6. **Tests:** run the theme-related unit/UI tests only (e.g. `--tests '*Theme*'`).
7. **Docs:** add the theme to the CLAUDE.md *Themes* list and README's *Themes* section.
