#!/bin/bash
# Reports the state of the in-repo translations: for every values-<locale>/strings.xml under the
# given resource directories (default: the app's), how many of the translatable keys of
# values/strings.xml it translates, which keys are missing, and which stale keys no longer exist
# in the base file. Exits with status 1 when any locale has stale keys.
# Runs offline with bash and coreutils/grep/sed only.
#
# Usage: scripts/translation-status.sh [-q] [res-dir...]
#   -q  only print the per-locale summary, not the missing/stale key names
set -euo pipefail

verbose=1
if [[ ${1:-} == -q ]]; then
	verbose=0
	shift
fi

root=$(cd "$(dirname "$0")/.." && pwd)
if (($# == 0)); then
	set -- "$root/app/src/main/res"
fi

# Prints the sorted names of the string resources (string, plurals, string-array) in file $1.
# With $2 = base, resources marked translatable="false" are left out.
keys() {
	local opening
	opening=$(grep -oE '<(string|plurals|string-array)[[:space:]][^>]*>' "$1" || true)
	if [[ ${2:-} == base ]]; then
		opening=$(grep -v 'translatable="false"' <<<"$opening" || true)
	fi
	sed -nE 's/.*[[:space:]]name="([^"]+)".*/\1/p' <<<"$opening" | LC_ALL=C sort -u
}

status=0
for res in "$@"; do
	base=$res/values/strings.xml
	if [[ ! -f $base ]]; then
		echo "error: $base not found" >&2
		exit 2
	fi
	base_keys=$(keys "$base" base)
	total=$(grep -c . <<<"$base_keys" || true)
	echo "${res#"$root"/}: $total translatable keys"
	printf '  %-10s %10s %7s %8s %6s\n' locale translated percent missing stale

	for file in "$res"/values-*/strings.xml; do
		[[ -f $file ]] || continue
		dir=${file%/strings.xml}
		locale=${dir##*/values-}
		locale_keys=$(keys "$file")
		translated=$(LC_ALL=C comm -12 <(echo "$base_keys") <(echo "$locale_keys") | grep -c . || true)
		missing=$(LC_ALL=C comm -23 <(echo "$base_keys") <(echo "$locale_keys") | grep . || true)
		stale=$(LC_ALL=C comm -13 <(echo "$base_keys") <(echo "$locale_keys") | grep . || true)
		missing_count=$(grep -c . <<<"$missing" || true)
		stale_count=$(grep -c . <<<"$stale" || true)
		percent=$((total > 0 ? translated * 100 / total : 100))
		printf '  %-10s %10s %6s%% %8s %6s\n' "$locale" "$translated/$total" "$percent" "$missing_count" "$stale_count"

		if ((verbose)) && [[ -n $missing ]]; then
			echo "    missing: $(paste -sd ' ' <<<"$missing")"
		fi
		if [[ -n $stale ]]; then
			echo "    stale (not in values/strings.xml, remove): $(paste -sd ' ' <<<"$stale")"
			status=1
		fi
	done
done

exit $status
