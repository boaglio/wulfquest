#!/usr/bin/env bash
# AGENTS.md §2.4 — CI gate.
#
# Wulf Quest ships no binary assets. All artwork is JSON pixel data (§10),
# all audio is synthesised at runtime (§18), and the font is a JSON glyph
# set (§10.4). This gate fails the build if any binary asset, or any ROM /
# tape / snapshot image, exists anywhere in the repository.
#
# There is no allowlist and there are no exceptions. If you believe you need
# one, you need to re-read §2.
set -euo pipefail
cd "$(dirname "$0")/.."

readonly FORBIDDEN_EXT='png|jpg|jpeg|gif|bmp|ico|webp|tiff?|svg|wav|mp3|ogg|flac|aiff?|mid|midi|ttf|otf|woff2?|z80|sna|szx|tap|tzx|rom|bin|dsk|trd|scr'

# Directories that are not part of the repository proper.
readonly PRUNE='-path ./.git -o -path ./target -o -path ./attic -o -path ./out'

fail=0

found=$(find . \( $PRUNE \) -prune -o -type f -regextype posix-extended \
          -iregex ".*\.($FORBIDDEN_EXT)" -print | sort)
if [[ -n "$found" ]]; then
  echo "FAIL: binary assets found (AGENTS.md §2.4 forbids all of these):" >&2
  echo "$found" | sed 's/^/  /' >&2
  fail=1
fi

# A file with no extension can still be a smuggled binary. Flag any
# non-empty file the system reports as binary and that git would not diff.
while IFS= read -r f; do
  case "$f" in *.md|*.json|*.java|*.sh|*.xml|*.txt|*.yml|*.yaml|*.properties|*.gitattributes|*.gitignore) continue ;; esac
  [[ -s "$f" ]] || continue
  if LC_ALL=C grep -qI . "$f" 2>/dev/null; then continue; fi
  echo "FAIL: $f is a binary file" >&2
  fail=1
done < <(find . \( $PRUNE \) -prune -o -type f -print)

if [[ $fail -eq 0 ]]; then
  echo "check-no-binaries: OK (no binary assets)"
fi
exit $fail
