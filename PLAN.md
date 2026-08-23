# Plan: `com.vogella.eclipse.themes`

Executable specification for creating this repository from scratch.
The target is a git repository that ships Eclipse IDE themes as an installable p2 update site, built with a pomless Tycho 5.0.4 build.
Two themes are in scope for the first iteration: **AI Neon** and **VS Code Dark**.
The layout must stay open for further themes without restructuring.

Working directory: `/home/vogella/git/com.vogella.eclipse.themes` (currently empty, not yet a git repository).

Reference repository with the same build style, on the same machine: `/home/vogella/git/com.vogella.eclipse.mcp`.
Read it when a detail below is ambiguous; copy its conventions, not its content.

## 0. Design decisions (do not re-litigate)

**Three bundles, two features.**
`com.vogella.eclipse.themes.common` carries the structural CSS that every theme shares and declares no theme itself.
`com.vogella.eclipse.themes.neon` and `com.vogella.eclipse.themes.vscode` each declare one theme and contribute only a palette plus the preference blocks.

**Colors are indirected through `ColorDefinition` tokens.**
The e4 CSS engine has no `var()`, but it resolves `'#some-color-definition-id'` as a color value (see `e4-dark_globalstyle.css` in `org.eclipse.ui.themes`).
The common bundle therefore styles widgets against token ids such as `'#com-vogella-themes-ACCENT'`, and each theme bundle defines those tokens with its own hex values.
A third theme is then a palette file plus a `plugin.xml`, not a second copy of the widget rules.
Side benefit: the tokens show up in *Preferences > General > Appearance > Colors and Fonts*, so users can retune a theme without a rebuild.

Fallback rule for the executor: if a specific CSS property refuses to resolve a token reference at runtime, put the literal hex value in that one rule and leave a one line comment saying why.
Do not abandon the token approach wholesale because of a single property.

**Each theme's base stylesheet imports the platform dark stylesheet first, then overrides it.**
Starting from `platform:/plugin/org.eclipse.ui.themes/css/e4-dark_linux.css` (and the `_win` / `_mac` siblings) inherits hundreds of widget rules that the platform maintains, including the per OS quirks.
This couples us to internal file names of `org.eclipse.ui.themes`, which is acceptable here and is exactly the kind of flexibility this repository is meant to probe.
Pin the target platform to one release so the coupling cannot break silently.

**No Java code in any bundle.**
All three bundles are resource only plug ins.
Consequence: no `.classpath`, no `source..` entry, no BREE, and `pom.model.property.tycho.source.skip = true` in `build.properties` so Tycho does not try to build source bundles.

**Version** `1.0.0-SNAPSHOT` / `1.0.0.qualifier` everywhere.

## 1. Repository layout

```
com.vogella.eclipse.themes/
├── .github/workflows/build.yml
├── .gitignore
├── .mvn/
│   ├── extensions.xml
│   └── maven.config
├── LICENSE
├── README.md
├── pom.xml                                   <- the only pom in the repository
├── target-platform/
│   └── com.vogella.eclipse.themes.target/
│       ├── .project
│       └── com.vogella.eclipse.themes.target.target
├── plugins/
│   ├── com.vogella.eclipse.themes.common/
│   ├── com.vogella.eclipse.themes.neon/
│   └── com.vogella.eclipse.themes.vscode/
├── features/
│   ├── com.vogella.eclipse.themes.neon.feature/
│   └── com.vogella.eclipse.themes.vscode.feature/
└── update-site/
    └── com.vogella.eclipse.themes.repository/
        ├── .project
        └── category.xml
```

## 2. Step 1: repository skeleton

```bash
cd /home/vogella/git/com.vogella.eclipse.themes
git init -b main
```

`.gitignore`:

```
target/
bin/
*.class
.polyglot.*
```

`LICENSE`: the full text of the Eclipse Public License 2.0.

`README.md`: what the repository is, the list of themes with a one line description each, the update site URL placeholder, how to install, and how to build (`mvn clean verify`).
One sentence per line, no em dashes.

## 3. Step 2: build infrastructure

`.mvn/extensions.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
  <extension>
    <groupId>org.eclipse.tycho</groupId>
    <artifactId>tycho-build</artifactId>
    <version>5.0.4</version>
  </extension>
</extensions>
```

`.mvn/maven.config`:

```
-Dtycho.pomless.aggregator.names=plugins,features,update-site
```

`pom.xml` (parent, packaging `pom`, groupId `com.vogella.eclipse.themes`, artifactId `com.vogella.eclipse.themes.parent`, version `1.0.0-SNAPSHOT`, name `vogella Eclipse Themes`):

- property `tycho.version` = `5.0.4`, `project.build.sourceEncoding` = `UTF-8`
- modules: `plugins`, `features`, `update-site`
- plugin `org.eclipse.tycho:tycho-maven-plugin` with `<extensions>true</extensions>`
- plugin `org.eclipse.tycho:target-platform-configuration` with
  - `<target><file>${maven.multiModuleProjectDirectory}/target-platform/com.vogella.eclipse.themes.target/com.vogella.eclipse.themes.target.target</file></target>`
  - `<executionEnvironment>JavaSE-25</executionEnvironment>`
  - environments: `linux/gtk/x86_64`, `linux/gtk/aarch64`, `win32/win32/x86_64`, `macosx/cocoa/aarch64`

No `tycho-surefire-plugin` configuration: there are no tests in this repository.

`target-platform/com.vogella.eclipse.themes.target/com.vogella.eclipse.themes.target.target`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?pde version="3.8"?>
<target name="com.vogella.eclipse.themes.target" sequenceNumber="1">
  <locations>
    <location includeAllPlatforms="false" includeConfigurePhase="true" includeMode="planner" includeSource="true" type="InstallableUnit">
      <unit id="org.eclipse.sdk.feature.group" version="0.0.0"/>
      <repository location="https://download.eclipse.org/releases/2026-06/"/>
    </location>
  </locations>
</target>
```

`target-platform/com.vogella.eclipse.themes.target/.project`: a `projectDescription` with the name `com.vogella.eclipse.themes.target`, empty `buildSpec` and `natures`.
The target folder is deliberately not an aggregator directory, so it is not built as a module.

## 4. Step 3: `com.vogella.eclipse.themes.common`

Path: `plugins/com.vogella.eclipse.themes.common/`.

`.project`: name `com.vogella.eclipse.themes.common`, buildSpec with `org.eclipse.pde.ManifestBuilder` and `org.eclipse.pde.SchemaBuilder`, natures `org.eclipse.pde.PluginNature`.
No Java builder, no Java nature.
The same shape applies to the other two plug in projects.

`META-INF/MANIFEST.MF`:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: %pluginName
Bundle-Vendor: %providerName
Bundle-SymbolicName: com.vogella.eclipse.themes.common;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Localization: plugin
Require-Bundle: org.eclipse.ui.themes;bundle-version="1.2.0"
Eclipse-BundleShape: dir
Automatic-Module-Name: com.vogella.eclipse.themes.common
```

`build.properties`:

```
bin.includes = META-INF/,\
               plugin.properties,\
               about.html,\
               css/
pom.model.property.tycho.source.skip = true
```

`plugin.properties`: `pluginName` and `providerName` (`vogella GmbH`).
`Bundle-Localization: plugin` is required for the `%key` references to resolve, the OSGi default location is `OSGI-INF/l10n/bundle` and would leave the literal `%pluginName` in the UI.
`Eclipse-BundleShape: dir` mirrors `org.eclipse.ui.themes` and keeps the stylesheets reachable as plain files.

`about.html`: the standard Eclipse "About This Content" EPL-2.0 boilerplate.

The common bundle declares **no** `ColorDefinition` at all.
Registering default values here and overriding them in each theme would make the result depend on the cascade between two `ColorDefinition#id` rules of equal specificity, which is an assumption nothing in the platform exercises.
Instead each theme's palette file owns the single `ThemesExtension { color-definition: ... }` registration and the `ColorDefinition` rules, and the common bundle only *uses* the ids.
The list of ids is therefore a contract documented here and repeated verbatim in every palette file, following `org.eclipse.ui.themes/css/dark/e4-dark_ide_colorextensions.css` as the syntactic model.

Token ids (all under category `'#org-eclipse-ui-presentation-default'`, `label` via `url('platform:/plugin/com.vogella.eclipse.themes.common?message=<KEY>')`):

| Token id | Role |
| --- | --- |
| `com-vogella-themes-BG_WINDOW` | trim, toolbars, status line |
| `com-vogella-themes-BG_PART` | view and part bodies |
| `com-vogella-themes-BG_EDITOR` | editor area background |
| `com-vogella-themes-BG_RAISED` | dialogs, popups, content assist |
| `com-vogella-themes-BORDER` | keylines, sashes, separators |
| `com-vogella-themes-FG` | primary text |
| `com-vogella-themes-FG_MUTED` | secondary text, line numbers, inactive tabs |
| `com-vogella-themes-ACCENT` | active tab, focus, progress, active keyline |
| `com-vogella-themes-ACCENT_2` | secondary accent, hovers |
| `com-vogella-themes-SELECTION_BG` | list and editor selection background |
| `com-vogella-themes-SELECTION_FG` | selection foreground |
| `com-vogella-themes-LINK` | hyperlinks |
| `com-vogella-themes-ERROR` | errors |
| `com-vogella-themes-WARNING` | warnings |
| `com-vogella-themes-SUCCESS` | success, resolved, added |

The `?message=` label URL resolves against the bundle named in it, so the keys belong in the `plugin.properties` of whichever bundle declares the `ColorDefinition`, that is the theme bundle.

**Token references must be single quoted**: write `background-color: '#com-vogella-themes-BG_PART';`, never `background-color: #com-vogella-themes-BG_PART;`.
`CSSSWTColorHelper.hasColorDefinitionAsValue` only accepts a value whose CSS kind is `STRING`, an unquoted value is parsed as a color literal and silently falls back to black.
For the same reason a token id must never look like a hex color.

`css/structure.css`: the widget rules shared by all themes, written exclusively against the tokens above.
Cover at minimum: `Shell`, `.MPartStack` and `.MPart` backgrounds, `CTabFolder` active and inactive tabs, `Tree`, `Table`, `Text`, `Combo`, `Button`, `ToolBar`, `Sash`, `StatusLine`, `Form Section`, and the drag feedback.
Reuse the selector set of the platform dark stylesheets: the point of this file is different colors, not different selectors.
Do not put any hex literal in this file except where the fallback rule of section 0 forces it.

`css/preferences-platform.css`: `IEclipsePreferences` blocks for the nodes that every theme has to recolor, written with hex literals because preference values are strings and cannot reference tokens.
This file is a **template**: each theme bundle owns its own copy with its own values.
Keep the master copy here as documentation and let the theme bundles carry the real ones.
Nodes to cover, copying the key list from `org.eclipse.ui.themes/css/dark/e4-dark_preferencestyle.css`:
`org-eclipse-ui-editors`, `org-eclipse-ui-workbench`, plus `org-eclipse-ui-console` and `org-eclipse-debug-ui` if straightforward.
Keep the `:org-eclipse-ui-themes` pseudo attribute on every node, it is what allows other bundles to contribute to the same node.

There is **no** `plugin.xml` in the common bundle.

## 5. Step 4: `com.vogella.eclipse.themes.neon` (AI Neon)

Path: `plugins/com.vogella.eclipse.themes.neon/`.
Manifest, `build.properties`, `about.html`, `plugin.properties` mirror the common bundle, with `Require-Bundle: org.eclipse.ui.themes;bundle-version="1.2.0", com.vogella.eclipse.themes.common;bundle-version="1.0.0"`.

`plugin.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
   <extension point="org.eclipse.e4.ui.css.swt.theme">
      <theme id="com.vogella.eclipse.themes.neon" label="%theme.neon"
             basestylesheeturi="css/neon_gtk.css" os="linux"/>
      <theme id="com.vogella.eclipse.themes.neon" label="%theme.neon"
             basestylesheeturi="css/neon_win.css" os="win32"/>
      <theme id="com.vogella.eclipse.themes.neon" label="%theme.neon"
             basestylesheeturi="css/neon_mac.css" os="macosx"/>
   </extension>
</plugin>
```

`%theme.neon` = `AI Neon`.

The three base stylesheets differ only in which platform file they import first:

```css
@import url("platform:/plugin/org.eclipse.ui.themes/css/e4-dark_linux.css");
@import url("platform:/plugin/com.vogella.eclipse.themes.common/css/tokens.css");
@import url("platform:/plugin/com.vogella.eclipse.themes.neon/css/neon_palette.css");
@import url("platform:/plugin/com.vogella.eclipse.themes.common/css/structure.css");
@import url("platform:/plugin/com.vogella.eclipse.themes.neon/css/neon_preferences.css");
@import url("platform:/plugin/com.vogella.eclipse.themes.neon/css/neon_jdt.css");
```

Import order is load bearing: platform dark first so our rules override it, tokens before the palette so the palette overrides the defaults, palette before `structure.css` so structural rules resolve against the final values, preferences last.
`_win` imports `e4-dark_win.css`, `_mac` imports `e4-dark_mac.css`.

`css/neon_palette.css` redefines the common tokens and the platform's own `#org-eclipse-ui-workbench-*` color definitions (tab gradients, `DARK_BACKGROUND`, `DARK_FOREGROUND`, `LINK_COLOR`) with the neon values:

| Token | Value |
| --- | --- |
| `BG_WINDOW` | `#0A0E17` |
| `BG_PART` | `#10141F` |
| `BG_EDITOR` | `#0B0F1A` |
| `BG_RAISED` | `#161B2B` |
| `BORDER` | `#1E2740` |
| `FG` | `#D6E1FF` |
| `FG_MUTED` | `#7A88B0` |
| `ACCENT` | `#00E5FF` |
| `ACCENT_2` | `#FF2FB9` |
| `SELECTION_BG` | `#1B3550` |
| `SELECTION_FG` | `#EAF6FF` |
| `LINK` | `#4D9FFF` |
| `ERROR` | `#FF2FB9` |
| `WARNING` | `#FFC857` |
| `SUCCESS` | `#39FF88` |

The neon character comes from where the accents are spent, not from raising every color: keep large surfaces near black and let the cyan and magenta appear on the active tab keyline, the focus border, the caret, selection edges, the progress bar and the active view title.
A theme where everything glows is unreadable after ten minutes.

`css/neon_preferences.css`: the platform preference nodes with neon values.
Editor background `30,31,44`, foreground `214,225,255`, selection background `27,53,80`, line number `122,136,176`, current line `22,28,44`, hyperlink `77,159,255`, occurrence indication `27,60,90`, error `255,47,185`, warning `255,200,87`.

`css/neon_jdt.css`: the `IEclipsePreferences#org-eclipse-jdt-ui:org-eclipse-jdt-ui` block.
Take the full key list from `/home/vogella/git/eclipse.jdt.ui/org.eclipse.jdt.ui/css/e4-dark_jdt_syntaxhighlighting.css` and keep every key, only changing values:

| Element | Value |
| --- | --- |
| `java_keyword`, `java_keyword_return` | `255,47,185` |
| `java_string` | `57,255,136` |
| `java_default` | `214,225,255` |
| `java_operator` | `166,180,216` |
| `java_bracket`, `matchingBracketsColor` | `0,229,255` |
| comments (`java_multi_line_comment`, `java_single_line_comment`) | `76,90,122` |
| `java_doc_default` | `95,112,153` |
| `java_doc_tag`, `java_doc_keyword` | `0,184,212` |
| `java_doc_link` | `77,159,255` |
| `semanticHighlighting.class.color`, `.interface`, `.enum` | `0,229,255` |
| `semanticHighlighting.abstractClass.color` | `125,227,255` |
| `semanticHighlighting.method.color`, `.methodDeclarationName` | `168,85,247` |
| `semanticHighlighting.field.color`, `.staticField` | `255,200,87` |
| `semanticHighlighting.annotation.color` | `168,85,247`, italic true |
| `semanticHighlighting.localVariable.color`, `.parameterVariable` | `166,180,216` |
| `semanticHighlighting.number.color` | `57,255,136` |
| `semanticHighlighting.deprecatedMember.color` | `90,100,125` |
| content assist background / foreground | `22,27,43` / `214,225,255` |

JDT is not a dependency of this bundle: the stylesheet only writes preference values, and writing into a node whose owner is not installed is harmless.
Do **not** add `org.eclipse.jdt.ui` to `Require-Bundle`, that would make the theme uninstallable on a plain platform.

## 6. Step 5: `com.vogella.eclipse.themes.vscode` (VS Code Dark)

Identical structure to the neon bundle, with theme id `com.vogella.eclipse.themes.vscodedark`, label `VS Code Dark`, base stylesheets `vscode_gtk.css` / `vscode_win.css` / `vscode_mac.css`, and files `vscode_palette.css`, `vscode_preferences.css`, `vscode_jdt.css`.

Goal: an Eclipse IDE that a VS Code user recognises immediately, matching the *Dark Modern* default.
Do not invent colors, use the published VS Code values.

Palette:

| Token | Value | VS Code origin |
| --- | --- | --- |
| `BG_WINDOW` | `#181818` | activity bar, side bar, title bar, status bar |
| `BG_PART` | `#181818` | side bar |
| `BG_EDITOR` | `#1F1F1F` | `editor.background` |
| `BG_RAISED` | `#252526` | `editorWidget.background` |
| `BORDER` | `#2B2B2B` | `editorGroup.border` |
| `FG` | `#CCCCCC` | `foreground` |
| `FG_MUTED` | `#6E7681` | `editorLineNumber.foreground` |
| `ACCENT` | `#0078D4` | `focusBorder`, `button.background` |
| `ACCENT_2` | `#04395E` | `list.activeSelectionBackground` |
| `SELECTION_BG` | `#264F78` | `editor.selectionBackground` |
| `SELECTION_FG` | `#FFFFFF` | |
| `LINK` | `#4DAAFC` | `textLink.foreground` |
| `ERROR` | `#F14C4C` | `editorError.foreground` |
| `WARNING` | `#CCA700` | `editorWarning.foreground` |
| `SUCCESS` | `#89D185` | `gitDecoration.addedResourceForeground` |

Tab styling matters more here than anywhere else, because it is the most recognisable part of VS Code: active tab background `#1F1F1F` with a `#0078D4` top keyline, inactive tab background `#181818`, active tab text `#FFFFFF`, inactive tab text `#9D9D9D`, no gradients.

Syntax mapping (VS Code Dark+ TextMate rules):

| Element | Value |
| --- | --- |
| control keywords (`if`, `for`, `return`) | `197,134,192` |
| other keywords, `storage`, primitive types | `86,156,214` |
| `java_string` | `206,145,120` |
| numbers | `181,206,168` |
| comments and Javadoc | `106,153,85` |
| classes, interfaces, enums, annotations types | `78,201,176` |
| methods and method declarations | `220,220,170` |
| fields, local variables, parameters | `156,220,254` |
| static final constants | `79,193,255` |
| operators, brackets, default text | `212,212,212` |
| `matchingBracketsColor` | `255,215,0` |
| editor background / foreground | `31,31,31` / `212,212,212` |
| selection background | `38,79,120` |
| current line | `42,45,46` |
| line numbers | `110,118,129` |

Note for the executor: Eclipse splits `java_keyword` and `java_keyword_return` but has no separate "control keyword" preference, so `return` gets `197,134,192` through `java_keyword_return` and the rest of the keywords get `86,156,214`.
That is the closest faithful mapping, and it is worth a one line comment in the CSS.

## 7. Step 6: features and update site

`features/com.vogella.eclipse.themes.neon.feature/`:
- `.project` with `org.eclipse.pde.FeatureBuilder` and `org.eclipse.pde.FeatureNature`
- `build.properties` containing exactly `bin.includes = feature.xml`
- `feature.xml`: id `com.vogella.eclipse.themes.neon.feature`, label `AI Neon Theme`, version `1.0.0.qualifier`, provider `vogella GmbH`, EPL-2.0 license block, description, and two `<plugin>` entries with `version="0.0.0" unpack="false"`: `com.vogella.eclipse.themes.common` and `com.vogella.eclipse.themes.neon`

`features/com.vogella.eclipse.themes.vscode.feature/`: the same with `com.vogella.eclipse.themes.vscode` and label `VS Code Dark Theme`.
Both features include the common bundle; p2 installs it once.

`update-site/com.vogella.eclipse.themes.repository/`:
- `.project` with empty buildSpec and natures, name `com.vogella.eclipse.themes.repository`
- `category.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<site>
   <feature id="com.vogella.eclipse.themes.neon.feature" version="0.0.0">
      <category name="com.vogella.eclipse.themes"/>
   </feature>
   <feature id="com.vogella.eclipse.themes.vscode.feature" version="0.0.0">
      <category name="com.vogella.eclipse.themes"/>
   </feature>
   <category-def name="com.vogella.eclipse.themes" label="vogella Eclipse Themes">
      <description>
         Additional themes for the Eclipse IDE.
      </description>
   </category-def>
</site>
```

Pomless Tycho infers `eclipse-repository` packaging from the presence of `category.xml`, so no pom is needed here.

## 8. Step 7: continuous integration

`.github/workflows/build.yml`: triggers on push to `main`, on pull requests and on `workflow_dispatch`, with a `concurrency` group cancelling superseded runs.
Steps: `actions/checkout@v4`, `actions/setup-java@v4` with `temurin` / `25` / `cache: maven`, then `mvn -B clean verify`.
No `xvfb-run` is needed, this build runs no tests.

Optionally add a second job that publishes `update-site/com.vogella.eclipse.themes.repository/target/repository` to GitHub Pages on pushes to `main`.
Mark it as a follow up if the repository has no Pages configuration yet, do not block the first build on it.

## 9. Step 8: build and verify

```bash
cd /home/vogella/git/com.vogella.eclipse.themes
mvn -B clean verify
```

Expected: three `eclipse-plugin` modules, two `eclipse-feature` modules and one `eclipse-repository` module all build, and `update-site/com.vogella.eclipse.themes.repository/target/repository/` contains `content.jar`, `artifacts.jar`, the two feature jars and the three plug in jars.

Then verify the themes in a real IDE, which is the part that actually matters, since a Tycho build cannot tell whether a stylesheet does anything:

```bash
<eclipse>/eclipse -nosplash -consolelog \
  -application org.eclipse.equinox.p2.director \
  -repository file:/home/vogella/git/com.vogella.eclipse.themes/update-site/com.vogella.eclipse.themes.repository/target/repository \
  -installIU com.vogella.eclipse.themes.neon.feature.feature.group,com.vogella.eclipse.themes.vscode.feature.feature.group
```

Use a throwaway Eclipse installation and a throwaway workspace, never the user's daily IDE.
Start it, open *Preferences > General > Appearance*, confirm both themes are listed, switch to each one and check:

1. the theme applies without a restart prompt beyond the normal one, and survives a restart
2. view and editor backgrounds, tabs, trees, the status line and dialogs are all recolored, with no leftover light grey areas
3. the Java editor shows the specified syntax colors, including semantic highlighting and Javadoc
4. content assist, hovers and the problems view are readable
5. `Preferences > General > Appearance > Colors and Fonts` lists the `com-vogella-themes-*` tokens
6. the Error Log has no CSS parse errors or unresolved `platform:/plugin` URIs

Fix findings in the CSS and rebuild.
Record anything that Eclipse styling simply cannot reach in a `docs/styling-limits.md` file, that is one of the stated purposes of this repository.

## 9b. Cheap guard against typos in token names

A misspelled token silently renders black, and nothing in the build catches it.
Add `releng/check-tokens.sh`: extract every `'#com-vogella-themes-[A-Z_0-9]*'` occurrence from `plugins/com.vogella.eclipse.themes.common/css/structure.css`, extract the ids registered in each `*_palette.css`, and exit non zero on any id that is used but not registered in every palette, or registered but never used.
Call it from the CI workflow before `mvn verify`.
Fifteen lines of `grep` and `sort` buy back the one failure mode this architecture introduces.

## 10. Adding a third theme later

Copy one theme bundle directory, rename the bundle and the theme id, replace the palette values and the two preference stylesheets, add a feature, add the feature to `category.xml`.
No change to `pom.xml` is required, the pomless aggregator picks up new directories under `plugins` and `features` automatically.

## 11. Risks and notes

The imports of `org.eclipse.ui.themes/css/e4-dark_*.css` are internal file names and can change between Eclipse releases.
The target platform pins 2026-06; when it is raised, re-check that the six imported paths still exist.

`EclipsePreferencesHandler.overrideProperty` writes a preference when it is still unset **or** when the theme has just been changed.
So switching to one of these themes does overwrite hand tuned syntax colors, and a user who has customised colors loses them on the switch.
That is the platform behaviour for the built in dark theme as well; say so in the README instead of working around it.

No code outside `ThemeEngine` keys off the built in dark theme id, so a custom theme id is safe.
What a custom theme cannot reach is the native chrome: GTK scrollbars, native menus and the native file dialogs follow the OS GTK theme, not our CSS.
The built in dark theme has the same limitation, which makes it a fair baseline rather than an excuse.

`macosx` uses `e4-dark_mac.css`; the platform also ships `e4-dark_mac1013.css` for old macOS versions, which is out of scope.

The build is verified on Linux only; the Windows and macOS stylesheets are shipped untested until someone runs them.
Say so in the README instead of implying coverage.

## 12. Acceptance criteria

- `mvn -B clean verify` is green from a clean checkout
- the p2 repository contains both features and all three bundles
- both themes appear in the Appearance preference page and visibly change the IDE
- no CSS errors in the Error Log with either theme active
- no Java source file exists in the repository
- one commit on `main`, with a message describing the repository rather than the tooling
