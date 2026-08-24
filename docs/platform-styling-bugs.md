# Styling bugs found outside this repository

Problems that show up while running these themes but whose cause is in the platform or in
another bundle, so they cannot be fixed here.
Each entry says what breaks, where the cause is, and what this repository does about it.

## EGit paints the staging view white in every theme but its own dark one

`org.eclipse.egit.ui/plugin.xml` contributes two stylesheets:

```xml
<stylesheet uri="css/egit.css" />
<stylesheet uri="css/e4-dark_egit_prefstyle.css">
   <themeid refid="org.eclipse.e4.ui.css.theme.e4_dark"/>
</stylesheet>
```

`css/egit.css` has no `themeid`, so it applies to every theme, and it hardcodes white:

```css
#org-eclipse-egit-ui-StagingView.active Tree { background-color: #FFFFFF; }
.MPart ScrolledComposite StyledText.org-eclipse-egit-ui-CommitAndDiffComponent { background-color: #FFFFFF; }
```

The correction that cancels those two rules lives in `css/e4-dark_egit_prefstyle.css`, which is
bound to `org.eclipse.e4.ui.css.theme.e4_dark` alone.
So every third party dark theme gets large white areas in the Git Staging view and in the
commit and diff component, while the platform's own dark theme does not.

The comment in `egit.css` explains the intent: restore white because the light theme's grey
looked jarring next to the commit message editor.
The mistake is the scope, not the intent.
An unconditional stylesheet is the wrong place for a color that only holds for light themes.

Fix in EGit would be to move those rules into a light-theme-scoped stylesheet, or to use
`inherit` in the unconditional one and let each theme decide.

Worked around here in `plugins/com.vogella.eclipse.themes.common/css/egit.css`, by mirroring
EGit's own dark selectors, which are more specific than the white ones.

## EGit's dark preference colors are bound to one theme id

Same file, same cause, different mechanism.
`e4-dark_egit_prefstyle.css` carries the `IEclipsePreferences` block that sets
`UncommittedChangeForegroundColor`, `IgnoredResourceForegroundColor` and the diff colors.
Being bound to `e4_dark`, a third party theme falls back to the defaults declared in EGit's
`plugin.xml`, which are `COLOR_LIST_FOREGROUND` on `COLOR_LIST_BACKGROUND`.
On a dark theme that is black text on a near black background.

Measured in the Project Explorer under AI Neon: project labels rendered `#060402` on
`#10141F`, a contrast ratio of about 1.1:1, which is unreadable.

Worked around here by writing the same preference keys from each theme's `*_preferences.css`
under an own pseudo attribute, so EGit's own block still applies when it is active.

## Tree and Table selection colors come from GTK, not from CSS

Selecting a row in any tree paints it in the desktop accent color rather than anything the
theme controls.
Measured on Ubuntu: the selected row renders `#E95420`, the Yaru orange, inside an otherwise
near black theme.

There is no `swt-selection-*` property handler in `org.eclipse.e4.ui.css.swt`, and neither the
platform dark stylesheets nor any theme can set it.
`swt-header-color` and `swt-header-background-color` are the only related properties, and they
cover the column header only.

Not fixable from a theme.
It needs an SWT or e4 CSS engine change that exposes selection colors as styleable properties.
Recorded in [styling-limits.md](styling-limits.md) as a limit for theme authors.

## The ToolItem ':checked' pseudo-class is dead unless a system property is set

`ToolItemElement` offers a `:checked` pseudo-class, and `CSSPropertyBackgroundSWTHandler`
explicitly supports `ToolItem.setBackground`, so `ToolItem:checked { background-color: ... }`
looks like the way to style a toggled toolbar button.
It never matches.

```java
boolean dynamicEnabled = Boolean.getBoolean("org.eclipse.e4.ui.css.dynamic");

public ToolItemElement(ToolItem toolItem, CSSEngine engine) {
	super(toolItem, engine);
	if (!dynamicEnabled) {
		return;
	}
	toolItem.addSelectionListener(selectionListener);
}
```

`isSelected` starts `false` and is assigned only inside that listener, so without
`-Dorg.eclipse.e4.ui.css.dynamic=true` the pseudo-class is always false.
Nothing reports this: the rule parses, the selector is valid, and it silently matches nothing.
`WidgetElement` is gated on the same property.

The consequence for theme authors is that a toggled toolbar button is painted by GTK, so on a
light desktop theme it comes out light inside a dark IDE.
No shipped theme uses `:checked`, which is consistent with it never having worked.

Worked around here by setting the background on `ToolItem` unconditionally rather than on the
checked state.
Verified: the fill follows the palette and GTK does not paint over it.
GTK still draws its own border around a toggled item, which is left alone deliberately, since
once the fill matches the toolbar that outline is the only thing marking the item as toggled.

A fix in the platform would be to maintain the selection listener regardless of the property,
or to drop the pseudo-class rather than ship one that cannot work by default.

