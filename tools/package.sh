#!/usr/bin/env bash
# AGENTS.md §28 — turns the game jar into this machine's app: a trimmed Java
# runtime (jlink) and a launcher (jpackage app-image), zipped to download.
# The release workflow runs it on Linux and Windows (Git Bash); it runs the
# same way here on a dev box with JDK 21 or later. No macOS app for now (§28.4).
#
#   tools/package.sh JAR VERSION      e.g. target/wulfquest-0.2.0.jar 0.2.0
#
# Everything it writes stays under target/package/ (the binaries gate, §2.4,
# does not look there). The archive's path is the last line it prints.
set -euo pipefail
cd "$(dirname "$0")/.."

[[ $# -eq 2 ]] || { echo "usage: tools/package.sh JAR VERSION" >&2; exit 64; }
jar=$1
version=$2
[[ -f "$jar" ]] || { echo "package.sh: no jar at $jar" >&2; exit 66; }
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "package.sh: VERSION must be N.N.N, got '$version'" >&2; exit 64; }

case "$(uname -s)" in
    Linux)  os=linux;   icon=WulfQuest.png ;;
    MINGW*|MSYS*|CYGWIN*) os=windows; icon=WulfQuest.ico ;;
    *) echo "package.sh: cannot package on $(uname -s)" >&2; exit 69 ;;
esac
case "$(uname -m)" in
    x86_64|amd64)  arch=x64 ;;
    arm64|aarch64) arch=arm64 ;;
    *) echo "package.sh: cannot package for $(uname -m)" >&2; exit 69 ;;
esac

work=target/package
rm -rf "$work"
mkdir -p "$work/input" "$work/app"
cp "$jar" "$work/input/wulfquest.jar"

java -Djava.awt.headless=true -cp "$jar" wulf.tools.IconForge "$work/icons"

# Only the modules the jar uses: AWT, Swing and sound, and what Jackson needs.
modules=$(jdeps --print-module-deps --ignore-missing-deps --multi-release 21 "$jar")
echo "package.sh: runtime modules $modules"
jlink --add-modules "$modules" --strip-debug --no-header-files --no-man-pages \
      --compress=zip-6 --output "$work/runtime"

jpackage --type app-image --name WulfQuest --app-version "$version" \
         --description "A flip-screen jungle adventure" \
         --input "$work/input" --main-jar wulfquest.jar \
         --runtime-image "$work/runtime" --icon "$work/icons/$icon" \
         --dest "$work/app"

archive=WulfQuest-$version-$os-$arch
case $os in
    linux)   tar -C "$work/app" -czf "$work/$archive.tar.gz" WulfQuest
             echo "$work/$archive.tar.gz" ;;
    windows) powershell -NoProfile -Command \
                 "Compress-Archive -Path '$work/app/WulfQuest' -DestinationPath '$work/$archive.zip'"
             echo "$work/$archive.zip" ;;
esac
