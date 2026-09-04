# Working on these themes

## Measure, do not look

A theme change is only done once it has been measured in a running IDE.
Reading the stylesheet tells you what was asked for, never what is painted, and the gap between the two is where every bug in this repository has lived so far.
The tools below are the Eclipse MCP server's.

### Read the value the widget actually has

`eclipse_inspect_widget` reports three things per property, and only all three together are an answer.

- `declared` is what the merged CSS declaration asked for, so it says whether a rule matched at all.
- `computed` is what the widget reports back, so it says what SWT ended up with.
- `origin` is `css` or `widget`, which is how a themed colour is told apart from the window system's default.

`declared` set and `computed` different means something overwrote the theme after it applied.
That is how the chiclet behind every toolbar icon was traced to `CTabFolder.updateBkImages`, and how the unreadable form field was traced to `.MPart Composite > *` outranking the bare `Text` rule.
`origin: widget` on a foreground means no rule ever set it, which is how the XMI tab's white-by-default text was found.

### Read the pixels

`eclipse_screenshot` writes a PNG; scan it rather than eyeballing it.
Walking one row and printing every colour change gives exact boundaries and exact values:

```python
row=[];prev=None
for x in range(x0,x1):
    p=px[x,y]
    if p!=prev: row.append((x,p)); prev=p
```

Compare the result against the palette, in RGB, not by name.
`(33,34,44)` is BG_WINDOW and `(40,42,54)` is BG_PART, and a strip that should be one and is the other is the whole bug report.

Take the modal colour along the row rather than one sample from its middle, because a single sample lands on an antialiased glyph as often as not.
That error does not look like noise, it looks like a finding: the five dark palettes came out about a tenth lighter than `SELECTION_BG` and One Light about a tenth darker, which reads as the desktop theme blending something over the selection, and is only the text bleeding into one pixel.

Two traps.
A widget print can be taken before GTK has repainted, so a first capture that looks unchanged is not evidence; take a second one.
A part behind another tab is not rendered at all and the capture is refused, so bring it forward first, with `eclipse_select_tab` for a page inside a multi page editor.

### Prove which knob paints a region

When several rules could be responsible, apply one with an unmistakable colour through `eclipse_apply_css` and count the pixels:

```
CTabFolder { swt-selected-tab-fill: #00ff00; }
```

Then find them and their bounding box.
Zero green means the property is inert on that widget, which is a finding in itself.
Green in the wrong place names the real owner.
This is what proved that `swt-selected-tab-fill` paints the strip below the tab row, and that `ToolItem { background-color }` never reaches the button on GTK3.

Each call re-applies the theme first, so snippets replace each other rather than piling up, and `reset: true` puts the IDE back.

### Prove a preference block landed

`eclipse_get_preferences` with `scope: all` and `includeDefaults: true`.
A key the theme set reads `effectiveScope: instance`, and the CSS engine writes a twin key `<key>,defaultValueBeforeOverriddenFromCSS` holding the value it replaced.
That twin is the proof, and its absence means the block never ran.
It is also the cheapest way to see what a bundle's own dark stylesheet covers and what it leaves on the light default.

`keyPattern` understands `*` and `?` only, so a character class silently matches nothing and returns zero keys.

### Judge contrast by number

Compute the WCAG ratio against the background that was measured, not the one that was intended.
WCAG AA wants 4.5:1 for normal text and 3:1 for large text.
This is what turned "is the header colour wanted" into an answer: it was wanted, and it failed AA in four of the six palettes.

Compute it for every palette before changing a shared rule.
Several tokens read well in one theme and fail in another, and `ACCENT_2` in particular is a dark blue in VS Code Dark and unusable as a foreground.

## Two CSS engines, three kinds of stylesheet

Eclipse up to 2026-06 parses theme CSS with Batik (`org.eclipse.e4.ui.css.core` below 0.14.800).
Eclipse 2026-09 and later parse it with the platform's own parser (0.14.800 and up).
Neither engine skips syntax it does not know: one unknown selector or at-rule fails the whole sheet.
So new syntax lives in a sheet that the old engine never loads, and the gate is OSGi resolution, never CSS.

- `plugins/com.vogella.eclipse.themes.<theme>` and `.common`: the base sheets, loaded by both engines.
- `plugins/com.vogella.eclipse.themes.legacy`: `css/<theme>_legacy.css`, one per theme id, resolves only against css.core `[0,0.14.800)`.
- `plugins/com.vogella.eclipse.themes.modern`: `css/<theme>_modern.css`, one per theme id, resolves only against css.core `[0.14.800,1.0.0)`.

Only the modern sheets may use what the new engine adds: `@media` blocks (including the `-eclipse-os` and `-eclipse-bundle-version` queries of eclipse-platform/eclipse.platform.ui#4323), `:not()` (#4322) and every other functional pseudo-class, and anything else Batik rejects.
The base sheets and the legacy sheets must stay free of all of it.
The lower bound 0.14.800 is the first css.core with the new parser; if `@media` and `:not()` ship in a later version, move both ranges (the two `MANIFEST.MF` files) to that version, do not bump any `Bundle-Version` by hand.

The two bundles sit in features of their own, `com.vogella.eclipse.themes.legacy.feature` and `.modern.feature`, and every theme feature includes both with `optional="true"`.
p2 turns the `Require-Bundle` range into an install requirement, so a feature listing both bundles directly would install nowhere; with optional includes p2 installs the one that resolves and silently drops the other, and a dropins install gets the same result from plain OSGi resolution.
An unresolved variant bundle is therefore intended and produces no error and no warning; it shows only in Help > About > Installation Details > Plug-ins.
After a release, install a theme feature once on an Eclipse before 2026-09 and once on 2026-09 or later and confirm through Installation Details, and through a measured rule, that the right variant is active.
The p2 director does the same check without an IDE, `-verifyOnly` against the built repository plus the release train, once per variant and train; the variant that does not belong must fail with a missing requirement on css.core.

Order between a base sheet and a contributed sheet is extension registry order, not declaration order.
An override in a legacy or modern sheet therefore needs higher specificity or `!important`; "later wins" is not available.

### Why the build has two targets and two repositories

One p2 resolution holds one version of css.core, because the bundle is a singleton, and the two variants need one version each.
So the modern bundle, its feature and the main update site carry a `pom.xml` that points `target-platform-configuration` at `com.vogella.eclipse.themes.target.modern.target` (the 2026-09 train); everything else stays on the default target (2026-06).
Putting both trains into one target file does not work: the planner refuses the second css.core, and slicer mode drops Batik and Felix SCR because their SimRel units carry an empty filter.

The same singleton rule drops one variant from a repository: the main update site resolves against the modern target and lists the modern feature as an explicit root, so the legacy variant is assembled in `update-site/com.vogella.eclipse.themes.repository.legacy` against the default target and mirrored into the main repository before signing and archiving.
The mirror logs `Problems resolving provisioning plan` for the legacy bundle's css.core range on every build; that is expected, the range is unsatisfiable in the modern target and the artifacts are mirrored anyway.
The main repository has xz index files switched off because the mirror step would leave them stale, and stale xz indexes are what p2 reads first.

## A third engine, GTK's own

`plugins/com.vogella.eclipse.themes.common` is the one bundle here with Java in it, and its `css/gtk.css` is the only stylesheet written for GTK's CSS engine rather than the e4 one.
It started as a GTK only bundle of its own gated by `Eclipse-PlatformFilter: (osgi.ws=gtk)`, which is the tidier shape and the wrong one, and the reason is worth keeping: a bundle nobody has heard of is absent from the workspace after a checkout, absent from an Eclipse Application launch configuration set to "plug-ins selected below", and reachable from an installed theme only through an optional feature include p2 may drop, and all three look identical from the outside, namely a theme that applies while these widgets do not change.
Every theme requires `common` and imports its stylesheets, so a theme that renders at all proves this code is loaded; the window system is checked at runtime in `GtkThemeStartup.earlyStartup` instead.
It exists because SWT hands a short list of widgets to GTK and gives the e4 engine no property that reaches them, so a desktop GTK theme paints through an otherwise complete theme.
`css/gtk.css` is a template: `GtkStyleSheet` substitutes the `'#com-vogella-themes-*'` tokens, deliberately spelled the repository's way so `check-tokens.sh` holds it to the same contract as `structure.css`, and `GtkStyleProvider` loads the result into one provider attached to the display.

The one rule that governs what may go in it follows from the priority.
The provider is attached at `GTK_STYLE_PROVIDER_PRIORITY_USER`, 800, because GTK resolves across providers by priority before specificity and SWT installs its per widget CSS at 600, including the `treeview:selected` rule `Tree.setBackgroundGdkRGBA` re-asserts from the desktop accent.
Measured: a screen provider at 201 loses to that rule and one at 800 wins, so 800 is the only priority at which the file does anything.
The cost is that anything named there also outranks the e4 cascade.

So a declaration may only name something SWT provably never emits, which is one of three things: a widget state, a node type SWT has no colour API for, or a node that exists only inside a native dialog.
Never a bare `label`, `button`, `entry`, `treeview`, `widget` or `window` rule: SWT emits `* { background-color }` and `* { color }` on all of those from whatever the e4 cascade resolved, and a rule here would flatten the BG_WINDOW, BG_PART, BG_EDITOR and BG_RAISED distinctions `structure.css` spends six hundred lines making.
Before adding one, read the widget's `setBackgroundGdkRGBA` and `updateCss` in the SWT sources and see what CSS it writes.

Where the e4 engine drops a whole sheet over one unknown token, GTK skips the rule and carries on, so a mistake here is neither a build failure nor a runtime failure, only a widget that kept the desktop theme's colour.
`releng/check-gtk-stylesheet.py` is the answer to that: it resolves the template against all six palettes and parses each result with a real `GtkCssProvider`, failing on any diagnostic, including the warnings GTK would otherwise only write to stderr.
`releng/check-gtk-palettes.sh` covers the other half, that the palette values copied into `palettes/<theme id>.properties` still match the `ColorDefinition` they came from; `--write` regenerates them and is the whole maintenance procedure after a palette change.
They are copies rather than a colour registry lookup because `ThemeEngine.setTheme` sends `THEME_CHANGED` before it calls `reapply()`, so when the theme id arrives the registry still holds the previous theme's colours.

Two traps beyond the ones in the measuring section above.
GTK does not queue a redraw when a provider on the screen changes, and a shell wide `redraw` with `allChildren` does not reach a `Tree`, which draws its rows into a `GdkWindow` of its own; every control has to be asked, which is what `GtkThemeStartup.repaint` does.
And a property that changes a node's box, `border-width` on the `check` indicator for one, repaints without re-laying out, so it shifts the widgets around it until something else resizes the page: use `box-shadow: inset` for an outline instead.

One trap that has nothing to do with GTK and cost a round trip anyway.
The build resolves against `JavaSE-25` and the IDE running the result may be on anything from `JavaSE-17` up, so the bytecode version is not the build's to choose: a class file 69 bundle on a Java 21 Eclipse fails with `UnsupportedClassVersionError` at early startup, which looks exactly like the GTK layer being absent.
`common` therefore pins `JavaSE-17` three times, once per consumer, and all three have to move together: `Bundle-RequiredExecutionEnvironment` for p2, `.settings/org.eclipse.jdt.core.prefs` for JDT, and `tycho-compiler-plugin`'s `release` in its own `pom.xml`, because the parent's `executionEnvironment` would otherwise decide it.
The JDT half of that only counts once the IDE has read the file, and a workspace with auto refresh off never does after a checkout that added it, so the project keeps building at the workspace default and the pin looks like it did not land.
Check the output rather than the settings: `od -An -t u1 -j 6 -N 2` on a class file in `bin` prints `61` for `JavaSE-17` and `69` for a workspace defaulting to 25.

Measuring here does not need an IDE, which is the one part of this repository that can be checked without one.
Drive `GtkStyleProvider` against real SWT widgets under `xvfb-run`, read the pixels back with `GC.copyArea` into an `Image`, and compare against the palette in RGB.
That is how every claim in this section was established.

## The recurring platform bug

A bundle ships its dark values in a stylesheet bound with `themeid refid="org.eclipse.e4.ui.css.theme.e4_dark"`.
Our themes declare their own ids and do not inherit from it, so they keep the light defaults.
Ant, PDE, Compare, the generic editor, EGit, Mylyn and the e4 model editor were all found this way, and the fix is the same each time: repeat the preference block in each theme's own `*_preferences.css`.

Two variants are worse than that.
A stylesheet contributed with no `themeid` at all applies everywhere and actively overwrites the theme, which is what `org.eclipse.e4.tools.emf.ui/css/default.css` did with three hard `#fff` areas.
Colours that live in Java constants or in a bundle's own resource file reach no preference and no CSS, and no theme can override them; those need a change in the owning bundle.

To find the next one, look for `<themeid` in the installed plugins:

```bash
cd <eclipse>/plugins
for j in *.jar; do
  unzip -p "$j" plugin.xml 2>/dev/null | tr -d '\n' | grep -q 'css.swt.theme' && echo "$j"
done
```

## Before pushing

Run `./releng/check-tokens.sh`, `./releng/check-gtk-palettes.sh`, `xvfb-run ./releng/check-gtk-stylesheet.py` and `mvn clean verify`.
Then install into the running IDE and restart, because a stylesheet that parses is not a stylesheet that renders:
`eclipse_add_repository` with `refresh`, `eclipse_update`, `eclipse_restart`.
Measure again afterwards.
