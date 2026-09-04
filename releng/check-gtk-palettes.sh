#!/usr/bin/env bash
# Verifies that the GTK palettes carry exactly the values their theme's palette
# stylesheet declares.
#
# The GTK stylesheet is resolved in Java, before the CSS engine has re-applied the
# palette, so its values have to be read from a file rather than from the workbench
# colour registry: see the class comment on Palette. That means the palette values
# exist twice, and a copy that drifts is invisible at runtime, because the wrong
# colour is still a colour. This is what keeps them equal, the way check-tokens.sh
# keeps the token names equal.
#
# Run with --write to regenerate the properties files from the stylesheets, which
# is the whole maintenance procedure after a palette change.
set -euo pipefail
cd "$(dirname "$0")/.."
# Byte order throughout, so that 'sort' and 'comm' agree on where ACCENT_2 goes.
export LC_ALL=C

palettes=plugins/com.vogella.eclipse.themes.common/palettes
write=false
[ "${1:-}" = "--write" ] && write=true

# TOKEN = #RRGGBB for every token a palette stylesheet declares.
declared() {
	awk '
		/^ColorDefinition#com-vogella-themes-/ {
			token = $0
			sub(/^ColorDefinition#com-vogella-themes-/, "", token)
			sub(/[^A-Za-z_0-9].*$/, "", token)
			next
		}
		token != "" && /^[[:space:]]*color:[[:space:]]*#/ {
			value = $0
			sub(/^[[:space:]]*color:[[:space:]]*/, "", value)
			sub(/[[:space:]]*;.*$/, "", value)
			print token " = " toupper(value)
			token = ""
		}
	' "$1" | sort
}

# The same shape, read back out of a GTK palette.
recorded() {
	sed -n 's/^\([A-Za-z_0-9]*\)[[:space:]]*=[[:space:]]*\(#[0-9A-Fa-f]\{6\}\).*/\1 = \2/p' "$1" \
		| tr '[:lower:]' '[:upper:]' | sort
}

status=0
expected=()

for stylesheet in plugins/*/css/*_palette.css; do
	bundle=${stylesheet#plugins/}
	bundle=${bundle%%/*}
	# One theme bundle can register its id for several operating systems.
	for theme in $(grep -o 'theme id="[^"]*"' "plugins/$bundle/plugin.xml" | cut -d'"' -f2 | sort -u); do
		file=$palettes/$theme.properties
		expected+=("$file")

		if $write; then
			mkdir -p "$palettes"
			{
				echo "# The palette of $theme, for the GTK stylesheet."
				echo "# Generated from $stylesheet by releng/check-gtk-palettes.sh --write."
				echo "# Do not edit: the same script fails the build when the two disagree."
				declared "$stylesheet"
			} > "$file"
			continue
		fi

		if [ ! -f "$file" ]; then
			echo "$theme: no GTK palette, expected $file" >&2
			status=1
			continue
		fi
		if ! difference=$(diff <(declared "$stylesheet") <(recorded "$file")); then
			echo "$theme: GTK palette disagrees with $stylesheet" >&2
			printf '%s\n' "$difference" | sed 's/^/  /' >&2
			echo "  run releng/check-gtk-palettes.sh --write" >&2
			status=1
		fi
	done
done

# A theme that was renamed or dropped would otherwise leave its palette behind,
# where it applies to nothing and quietly rots.
for file in "$palettes"/*.properties; do
	[ -e "$file" ] || continue
	printf '%s\n' "${expected[@]}" | grep -qxF "$file" && continue
	echo "$(basename "$file" .properties): GTK palette for a theme id no plugin.xml registers" >&2
	status=1
done

# Every token the GTK stylesheet asks for has to be in the palettes it reads.
# check-tokens.sh already proves the token is declared by all six palette
# stylesheets; this proves it survived the copy.
used=$(grep -oh "'#com-vogella-themes-[A-Za-z_0-9]*'" \
	plugins/com.vogella.eclipse.themes.common/css/gtk.css \
	| sed "s/.*#com-vogella-themes-\([A-Za-z_0-9]*\).*/\1/" | sort -u)
if ! $write; then
	for file in "${expected[@]}"; do
		[ -f "$file" ] || continue
		if missing=$(comm -23 <(printf '%s\n' "$used") <(recorded "$file" | cut -d' ' -f1)) && [ -n "$missing" ]; then
			echo "$(basename "$file" .properties): css/gtk.css uses tokens this palette lacks" >&2
			printf '  %s\n' $missing >&2
			status=1
		fi
	done
fi

if [ $status -ne 0 ]; then
	exit 1
fi
if $write; then
	echo "check-gtk-palettes: wrote ${#expected[@]} palettes"
else
	echo "check-gtk-palettes: $(printf '%s\n' "$used" | grep -c .) tokens, consistent across ${#expected[@]} palettes"
fi
