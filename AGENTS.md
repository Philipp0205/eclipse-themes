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

Run `./releng/check-tokens.sh` and `mvn clean verify`.
Then install into the running IDE and restart, because a stylesheet that parses is not a stylesheet that renders:
`eclipse_add_repository` with `refresh`, `eclipse_update`, `eclipse_restart`.
Measure again afterwards.
