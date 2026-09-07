#!/usr/bin/env python3
"""Parses the resolved GTK stylesheet with GTK's own CSS engine.

The e4 engine drops a whole sheet over one unknown token, which is why the CSS in
this repository is split by engine. GTK is the opposite and more dangerous: it skips
what it does not understand and keeps going, so a misspelled property is not a build
failure and not a runtime failure either, just a widget that stays the colour of the
desktop theme. Nothing else in the build would notice.

So resolve css/gtk.css against every palette the way GtkStyleSheet does, hand each
result to a real GtkCssProvider, and fail on any diagnostic GTK reports, including
the warnings it would otherwise only write to stderr at startup.

Needs PyGObject and the GTK 3 typelib (Debian and Ubuntu: python3-gi,
gir1.2-gtk-3.0) and an X display, which is what xvfb-run is for. Exits 0 with a note
when they are absent, so that running the release checks on a machine without them
is not a hard stop.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "plugins" / "com.vogella.eclipse.themes.common"
TEMPLATE = BUNDLE / "css" / "gtk.css"
PALETTES = BUNDLE / "palettes"

# The same pattern GtkStyleSheet compiles.
TOKEN = re.compile(r"'#com-vogella-themes-([A-Za-z_0-9]+)'")


def palette(path):
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, _, value = line.partition("=")
        values[name.strip()] = value.strip()
    return values


def resolve(template, values):
    missing = set()

    def substitute(match):
        token = match.group(1)
        if token not in values:
            missing.add(token)
            return ""
        return values[token]

    resolved = TOKEN.sub(substitute, template)
    if missing:
        raise SystemExit(f"palette carries no value for {sorted(missing)}")
    return resolved


def main():
    try:
        import gi

        gi.require_version("Gtk", "3.0")
        from gi.repository import Gtk
    except (ImportError, ValueError) as reason:
        print(f"check-gtk-stylesheet: skipped, no GTK bindings ({reason})")
        return 0
    if not Gtk.init_check()[0]:
        print("check-gtk-stylesheet: skipped, no display")
        return 0

    template = TEMPLATE.read_text()
    problems = []
    sheets = sorted(PALETTES.glob("*.properties"))
    if not sheets:
        raise SystemExit(f"no palettes in {PALETTES}")

    for path in sheets:
        theme = path.stem
        resolved = resolve(template, palette(path))
        provider = Gtk.CssProvider()
        provider.connect(
            "parsing-error",
            lambda _provider, section, error, theme=theme: problems.append(
                f"{theme}: line {section.get_start_line() + 1}: {error.message}"
            ),
        )
        try:
            provider.load_from_data(resolved.encode())
        except Exception as error:  # GLib.Error, which GTK raises for a fatal parse
            problems.append(f"{theme}: {error}")

    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        return 1
    print(f"check-gtk-stylesheet: {TEMPLATE.name} parses against {len(sheets)} palettes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
