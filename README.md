# vogella Eclipse Themes

Additional dark themes for the Eclipse IDE, shipped as an installable p2 update site.
The themes are pure resource plugins: CSS and preference definitions only, no Java code.

## Themes

| Theme | Description |
| --- | --- |
| AI Neon | Near black surfaces with cyan and magenta accents on active elements. |
| VS Code Dark | The VS Code Dark Modern look, including its tab styling and Dark+ syntax colors. |

Both themes build on the platform dark stylesheet of `org.eclipse.ui.themes` and recolor it
through shared `ColorDefinition` tokens, so a theme change is a palette change, not a rewrite.

## Installing

Use the update site URL:

```
https://vogella.github.io/com.vogella.eclipse.themes/repository/
```

In Eclipse: *Help > Install New Software*, paste the URL and select one or both features.
Then switch under *Preferences > General > Appearance* to the installed theme.

Note on preference based colors: when you switch to a theme, the theme writes its values into
the affected preferences (editor colors, Java syntax highlighting, console colors),
overwriting whatever was set before, including hand tuned settings.
The built-in dark theme behaves the same way.
If you want your own colors back, re-tune them under *Preferences > General > Appearance >
Colors and Fonts* after switching.

The Linux stylesheets are the ones exercised by this repository's build and testing.
The Windows and macOS variants ship untested until someone runs them there.

## Building

```
mvn clean verify
```

The build is pomless Tycho (5.0.4) against the Eclipse 2026-06 release train target platform.
The resulting p2 repository lands in
`update-site/com.vogella.eclipse.themes.repository/target/repository/`.

## Adding a theme

Copy an existing bundle under `plugins/`, rename bundle and theme id, replace the palette
values and the two preference stylesheets, add a feature under `features/` and list it in the
update site `category.xml`.
No pom changes are needed, the pomless aggregator picks up new directories automatically.

## License

Eclipse Public License 2.0, see [LICENSE](LICENSE).
