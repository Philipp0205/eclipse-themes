# Styling limits

Things the e4 CSS theme engine cannot reach, recorded so nobody hunts for a selector that does not exist.
Verified against the Eclipse 2026-06 target platform (org.eclipse.e4.ui.css.swt 0.17.100).

## Native chrome

No CSS theme can recolor anything drawn by the operating system or by native toolkit code: GTK scrollbars, native menus and the native file and print dialogs keep their system look.
SWT renders some of these itself depending on version and platform settings, so what exactly stays native can shift between releases.

On GTK these are no longer left alone, but they are still not reached from CSS.
`com.vogella.eclipse.themes.common` writes a second stylesheet for GTK's own engine and loads it into a style provider on the Eclipse display, which is a different mechanism with a different set of rules; see [platform-styling-bugs.md](platform-styling-bugs.md) and the AGENTS.md section on it.
What stays out of reach on any platform is the window decorations, which belong to the window manager.

## Tree, table and list selection colors

The engine has no CSS property for the selection background and foreground of `Tree`, `Table` and `List`.
There is no `swt-selection-*` handler in org.eclipse.e4.ui.css.swt; those colors come from SWT's internal dark theme handling.
The `swt-header-color` and `swt-header-background-color` properties are the only header related knobs.

Worse than absent: `Tree.setBackgroundGdkRGBA` and `Table.setBackgroundGdkRGBA` re-assert the desktop colour every time the theme sets a background, because setting the background would otherwise take the selection with it:

```java
GdkRGBA selectedBackground = display.getSystemColor(SWT.COLOR_LIST_SELECTION).handle;
String css = "treeview {background-color: " + ... + ";}\n"
        + "treeview:selected {background-color: " + gtk_rgba_to_css_string(selectedBackground) + ";}";
```

`COLOR_LIST_SELECTION` is read off the desktop theme during `Device` init, so the more completely a theme colours a tree the more certainly its selection is the desktop accent.
That is what makes this unreachable from CSS rather than merely unsupported, and it is why the fix had to be a GTK style provider at a priority above the one SWT installs that rule at.
Handled on GTK in `plugins/com.vogella.eclipse.themes.common/css/gtk.css`; still open on Windows and macOS.

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

Worked around on GTK since the GTK stylesheet exists, and it is the clearest case for why that stylesheet is loaded at `PRIORITY_USER`.
The indicator is a CSS node of its own, `check` or `radio`, so a rule that names it costs nothing anywhere else, which is the same observation the SWT fix rests on.
Measured on GTK 3.24 with a live check button: the indicator resolves to `#FFFFFF` under Adwaita alone, to the page colour once SWT's universal selector is installed at `PRIORITY_APPLICATION`, and to the palette value once `check` is named at `PRIORITY_USER`.
The outline needs `box-shadow: inset`, not `border-color`: the desktop theme draws it from a `border-image`, so `border-color` recolours nothing, and `border-width` does paint but grows the indicator and closes the gap to the label, because a provider change repaints without re-laying out.
The SWT fix is still the right one, since it would carry the checked state's own drawing and every platform.

## A Button has no border, and no hover, pressed or checked state

`background-color` and `color` are the whole of what the engine offers for a `Button`.
There is no border property, so the outline around a push button is the desktop GTK theme's; and there is no state at all, so hover and pressed cannot be expressed, while `:checked` exists only on `ToolItemElement` and is dead unless `-Dorg.eclipse.e4.ui.css.dynamic=true` is set.
The consequence is that a fully themed IDE still shows desktop chrome on every button.

Measured on the VS Code Dark palette with the providers layered the way a running IDE layers them.
Under Breeze a push button was outlined in `#BCBEBF` and under Adwaita in `#CDC7C2`, each with a white line inside the top edge, and that outline turned Breeze's accent `#3DAEE9` on hover and on focus.
Hover changed no fill anywhere, because `Button.setBackgroundGdkRGBA` writes `* { background: <colour> }` with no state qualifier and that declaration, at `PRIORITY_APPLICATION`, holds the fill through every state.
A toggled `ToolItem` measured `#19191A` on a `#181818` bar, 1.01:1, so the desktop outline was the only thing marking it; under Adwaita a hovered one came out `#F8F8F7`, a near white box on near black trim.

Worked around on GTK, and it is the one case where the stylesheet names a bare node.
Because SWT never writes a `border-*` or a `box-shadow`, those properties cannot flatten a surface distinction the way a `background-color` would, so `button { border-color }`, `button:hover`, `button:active`, `button:checked` and `button:focus` are safe at `PRIORITY_USER` while the fill stays with the e4 cascade.
Two things had to be measured rather than reasoned about: a desktop theme keeps a toolbar button flat by painting its border transparent rather than by leaving it out, so the bare rule boxed every toolbar icon under Adwaita and changed nothing under Breeze; and `outline-color` is inert on a GTK 3 button, zero pixels with an unmistakable colour, so keyboard focus has to be marked with a border colour instead.

## Widget specific gaps found while building these themes

- `CTabItem` busy state has only a color hook; no italic or icon override.
- The splash screen progress bar is deliberately excluded from CSS theming by the platform (see `Label#org-eclipse-ui-splash-progressText` in the platform dark stylesheets).
