#!/usr/bin/env bash
# Verifies the '#com-vogella-themes-*' token contract across all theme bundles.
# A token that one palette misses or misspells silently renders black at runtime,
# and nothing else in the build catches it.
set -euo pipefail
cd "$(dirname "$0")/.."

names() { sed -n "s/.*#com-vogella-themes-\([A-Za-z_0-9]*\).*/\1/p" | sort -u; }

palettes=(plugins/*/css/*_palette.css)
# Every stylesheet that is not a palette consumes tokens rather than declaring them.
consumers=$(find plugins -name '*.css' ! -name '*_palette.css' | sort)

used=$(grep -oh "'#com-vogella-themes-[A-Za-z_0-9]*'" $consumers | names)

status=0
for palette in "${palettes[@]}"; do
	theme=${palette#plugins/}
	theme=${theme%%/*}

	defined=$(grep -h "^ColorDefinition#com-vogella-themes-" "$palette" | names)
	# Only tokens listed in ThemesExtension reach the theme engine.
	registered=$(sed -n '/ThemesExtension/,/}/p' "$palette" \
		| grep -o "'#com-vogella-themes-[A-Za-z_0-9]*'" | names)

	report() {
		local label=$1 list=$2
		[ -z "$list" ] && return 0
		echo "$theme: $label" >&2
		printf '  %s\n' $list >&2
		status=1
	}

	report "used by a stylesheet but defined by no ColorDefinition" \
		"$(comm -23 <(printf '%s\n' "$used") <(printf '%s\n' "$defined"))"
	report "defined but not listed in ThemesExtension, so never registered" \
		"$(comm -23 <(printf '%s\n' "$defined") <(printf '%s\n' "$registered"))"
	report "listed in ThemesExtension but defined by no ColorDefinition" \
		"$(comm -13 <(printf '%s\n' "$defined") <(printf '%s\n' "$registered"))"
done

if [ $status -ne 0 ]; then
	exit 1
fi
echo "check-tokens: $(printf '%s\n' "$used" | grep -c .) tokens, consistent across ${#palettes[@]} palettes"
