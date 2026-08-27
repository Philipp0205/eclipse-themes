# vogella Eclipse Themes

Additional themes for the Eclipse IDE, shipped as an installable p2 update site.
The themes are pure resource plugins: CSS and preference definitions only, no Java code.

## Themes

Every theme builds on a platform stylesheet of `org.eclipse.ui.themes`, the dark one or the light one, and recolors it through shared `ColorDefinition` tokens, so a theme change is a palette change, not a rewrite.

### One Light

Atom's One Light palette: a near white editor on a soft grey chrome, with purple keywords, green strings and the blue accent.

![One Light](docs/images/one-light.png)

### GitHub Dark

GitHub's Primer dark palette: blue-black surfaces with the familiar blue accent and GitHub syntax colors.

![GitHub Dark](docs/images/github-dark.png)

### Dracula

The official Dracula palette on purple tinted surfaces with pink and purple accents.

![Dracula](docs/images/dracula.png)

### Nord

Calm arctic blues and frost accents on Polar Night surfaces, following the official Nord palette.

![Nord](docs/images/nord.png)

### AI Neon

Near black surfaces with cyan and magenta accents on active elements.

![AI Neon](docs/images/ai-neon.png)

### VS Code Dark

The VS Code Dark Modern look, including its tab styling and Dark+ syntax colors.

![VS Code Dark](docs/images/vscode-dark.png)

All six are the same workspace and the same file, captured on GTK at 200% scaling, so the differences are the themes and nothing else.
The orange row selection in the tree and the outline is the desktop accent color rather than the theme: GTK owns tree selection and no stylesheet can set it, see [styling-limits.md](docs/styling-limits.md).

## Installing

There is no hosted update site yet, so build the p2 repository locally first, see [Building](#building).

In Eclipse: *Help > Install New Software > Add > Local*, point at

```
update-site/com.vogella.eclipse.themes.repository/target/repository
```

and select one or both features.
Then switch under *Preferences > General > Appearance* to the installed theme.

Note on preference based colors: when you switch to a theme, the theme writes its values into the affected preferences (editor colors, Java syntax highlighting, console colors), overwriting whatever was set before, including hand tuned settings.
The built-in dark theme behaves the same way.
If you want your own colors back, re-tune them under *Preferences > General > Appearance > Colors and Fonts* after switching.

The Linux stylesheets are the ones exercised by this repository's build and testing.
The Windows and macOS variants ship untested until someone runs them there.

## Building

```
mvn clean verify
```

Maven has to run on JDK 25 or newer, because the target platform is resolved against the
`JavaSE-25` execution environment configured in `pom.xml`.
The build is pomless Tycho (5.0.4) against the Eclipse 2026-06 release train target platform.
The resulting p2 repository lands in
`update-site/com.vogella.eclipse.themes.repository/target/repository/`.

## Adding a theme

Copy an existing bundle under `plugins/` and replace the old bundle symbolic name everywhere
in the copy.
Copy a dark theme for a dark one and `com.vogella.eclipse.themes.onelight` for a light one:
the two differ in which platform stylesheet the `*_gtk.css`, `*_win.css` and `*_mac.css` files
import, and in whether `javadocElementsStyling.darkModeDefaultColors` is `true` or `false`.
That last part is the step that is easy to miss: the base stylesheets import the palette and
the preference sheets through `platform:/plugin/<bundle>/css/...` URIs, and every
`ColorDefinition` label is a `platform:/plugin/<bundle>?message=...` URI.
A copy that keeps the old name silently renders with the old theme's palette when that bundle
is installed, and renders black when it is not.

Then rename the theme id and the `%theme.*` key pair in `plugin.xml` and `plugin.properties`,
keeping in mind that `ThemeEngine` decides whether to put GTK itself into dark mode by testing
whether the theme id contains `dark`, so a dark theme needs it in the id and a light theme must
not have it,
rename the `.project` name and `Automatic-Module-Name`, replace the palette values and the
preference stylesheets (`*_preferences.css` and `*_jdt.css`, plus `vscode_tabs.css` if you
copied the VS Code theme), add a feature under `features/` and list it in the update site
`category.xml`.
Run `./releng/check-tokens.sh` afterwards, it verifies the token contract for every palette it
finds.
No pom changes are needed, the pomless aggregator picks up new directories automatically.

## License

Eclipse Public License 2.0, see [LICENSE](LICENSE).

## Commercial support

[vogella GmbH](https://vogella.com/services/) builds and maintains these themes and offers training and consulting around Eclipse, the Eclipse platform and its tooling.

If you use these themes in earnest and something looks wrong, saying so is welcome either way: the issues and pull requests here are open, and the commercial route exists for work that wants a schedule attached.
