#!/usr/bin/env bash
# Fails when structure.css uses a '#com-vogella-themes-*' token that no theme
# palette registers. A misspelled token reference silently renders black.
set -euo pipefail
cd "$(dirname "$0")/.."

used=$(grep -oh "'#com-vogella-themes-[A-Z_0-9]*'" \
	plugins/com.vogella.eclipse.themes.common/css/structure.css \
	plugins/com.vogella.eclipse.themes.vscode/css/vscode_tabs.css \
	| sed "s/'#com-vogella-themes-//; s/'//" | sort -u)
registered=$(grep -h "^ColorDefinition#com-vogella-themes-" \
	plugins/*/css/*_palette.css \
	| sed -n 's/^ColorDefinition#com-vogella-themes-\([A-Za-z_0-9]*\).*$/\1/p' | sort -u)

missing=$(comm -23 <(printf '%s\n' "$used") <(printf '%s\n' "$registered"))
if [ -n "$missing" ]; then
	echo "Tokens used but registered by no palette:" >&2
	printf '%s\n' "$missing" >&2
	exit 1
fi
echo "check-tokens: all $(printf '%s\n' "$used" | grep -c .) tokens are registered"
