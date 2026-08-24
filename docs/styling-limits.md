# Styling limits

Things the e4 CSS theme engine cannot reach, recorded so nobody hunts for a selector that does not exist.
Verified against the Eclipse 2026-06 target platform (org.eclipse.e4.ui.css.swt 0.17.100).

## Native chrome

No CSS theme can recolor anything drawn by the operating system or by native toolkit code: GTK scrollbars, native menus and the native file and print dialogs keep their system look.
SWT renders some of these itself depending on version and platform settings, so what exactly stays native can shift between releases.

## Tree, table and list selection colors

The engine has no CSS property for the selection background and foreground of `Tree`, `Table` and `List`.
There is no `swt-selection-*` handler in org.eclipse.e4.ui.css.swt; those colors come from SWT's internal dark theme handling.
The `swt-header-color` and `swt-header-background-color` properties are the only header related knobs.
Selection colors in this repository's themes therefore stay close to the platform dark defaults.

## Preference based colors are written, not resolved live

Editor and syntax colors are written as `IEclipsePreferences` values when the theme is activated, not resolved live per widget.
`EclipsePreferencesHandler.overrideProperty` writes a value only when the preference is unset or when the theme has just changed, so a value already in the preference store wins until the next theme switch, which overwrites it.

## Widget specific gaps found while building these themes

- `CTabItem` busy state has only a color hook; no italic or icon override.
- The splash screen progress bar is deliberately excluded from CSS theming by the platform (see `Label#org-eclipse-ui-splash-progressText` in the platform dark stylesheets).
