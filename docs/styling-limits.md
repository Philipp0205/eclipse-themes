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

## Check box and radio button indicators on GTK

An unchecked check box or radio button on a dark page shows only a border one shade off the page colour, and no rule in a theme can brighten the box without repainting the button.
`Button.setBackgroundGdkRGBA` injects `* { background: <colour> }` into the button's GTK style context, and the universal selector hits the `check` and `radio` indicator nodes together with the label.
The theme's Button background is the page colour, so the indicator's fill, which Yaru dark draws as `#393939` with a `#181818` border, is painted over with it and nothing but the border survives.
A checked box keeps the GTK accent colour because that part is a foreground drawing.

Measured 2026-09-03 with `eclipse_apply_css` and `Button { background-color: #ff00ff; }` on Preferences > General: the magenta reaches the label text and the indicator outline while the interior stays the page colour, so the indicator follows the Button background and nothing else.
A lighter Button background would fix the box and draw a rectangle behind every button label, and the engine cannot express "no background" for a widget.
The fix is in SWT's `Button`, scoping the injected selector to the widget's own CSS name (`checkbutton`, `radiobutton`, `button`) so the indicator keeps the GTK theme's colours; a headless Xvfb probe showed 0 indicator pixels with stock SWT and 184 with that change, matching an unthemed button.

## Widget specific gaps found while building these themes

- `CTabItem` busy state has only a color hook; no italic or icon override.
- The splash screen progress bar is deliberately excluded from CSS theming by the platform (see `Label#org-eclipse-ui-splash-progressText` in the platform dark stylesheets).
