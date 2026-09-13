#!/usr/bin/env bash
#
# Build and run Wulf Quest.
#
# A convenience wrapper around the Maven commands in AGENTS.md §3.1. It
# compiles only when something under src/main or pom.xml has actually changed,
# then launches the game on a cached classpath — so repeat runs start without
# paying Maven's startup cost.
#
# Editing files under data/ does NOT trigger a rebuild: the game reads ./data
# from the working tree first (AGENTS.md §20.1), so content changes are picked
# up on the next run with no compile at all. That is the point of keeping the
# game data-driven.
#
# Usage:  ./run.sh [script options] [-- ] [game options]
#
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

readonly MAIN_CLASS="wulf.Boot"
readonly CLASSES="target/classes"
readonly CP_FILE="target/cp.txt"
readonly STAMP="target/.build-stamp"
readonly MIN_JAVA=21

RUN_BUILD="auto"
RUN_TESTS=0
DO_CLEAN=0
GAME_ARGS=()

die() {
    echo "run.sh: $*" >&2
    exit 1
}

usage() {
    cat <<'USAGE'
Wulf Quest

  ./run.sh                    build if needed, then play
  ./run.sh --scale 3          any option after the script's own is passed
                              straight to the game
Script options:
  --clean                     mvn clean first (full rebuild)
  --rebuild                   compile even if nothing looks stale
  --no-build                  skip the build entirely (fail if not compiled)
  --test                      run the full verify (tests + CI gates) first
  -h, --help                  this message

Game options (see AGENTS.md §3.1):
  --room C,R                  start in this room (default: 8,10)
  --browse                    the room browser instead of the game
  --dev                       K kills, M shows collision, position on the panel
  --scale N                   window scale, 1..6
  --data-dir PATH             content database root (default: ./data)
  --headless                  load and validate the data, then exit
  --dev                       developer mode

Exit codes:
  0   clean exit
  2   data error (a JSON file is bad; the message names the file and pointer)
  64  bad command-line option (the message says which, and why)
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)     DO_CLEAN=1; shift ;;
        --rebuild)   RUN_BUILD="force"; shift ;;
        --no-build)  RUN_BUILD="never"; shift ;;
        --test)      RUN_TESTS=1; shift ;;
        -h|--help)   usage; exit 0 ;;
        --)          shift; GAME_ARGS+=("$@"); break ;;
        *)           GAME_ARGS+=("$1"); shift ;;
    esac
done

# ---------------------------------------------------------------- toolchain

command -v java >/dev/null 2>&1 || die "no 'java' on PATH; Wulf Quest needs a JDK ${MIN_JAVA} or newer"

java_major() {
    local v
    v="$(java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9][0-9]*\)\(\.[0-9]*\)*.*/\1/p')"
    [[ "$v" == "1" ]] && v="$(java -version 2>&1 | head -1 | sed -n 's/.*version "1\.\([0-9][0-9]*\).*/\1/p')"
    echo "${v:-0}"
}

JAVA_MAJOR="$(java_major)"
[[ "$JAVA_MAJOR" -ge "$MIN_JAVA" ]] \
    || die "Java $JAVA_MAJOR found, but Wulf Quest needs $MIN_JAVA or newer"

needs_maven() {
    command -v mvn >/dev/null 2>&1 || die "no 'mvn' on PATH; needed to build (try --no-build if already compiled)"
    # Maven 3.9 on a modern JDK prints a sun.misc.Unsafe deprecation banner from
    # its own bundled Guava. Not our code, and nothing we can fix upstream.
    # Appended, not defaulted: the caller's own MAVEN_OPTS (heap sizes, locale)
    # must survive.
    export MAVEN_OPTS="${MAVEN_OPTS:-} --sun-misc-unsafe-memory-access=allow"
}

# ---------------------------------------------------------------- build

is_stale() {
    [[ -d "$CLASSES" && -f "$STAMP" && -s "$CP_FILE" ]] || return 0
    # Only code and build config invalidate the compile; data/ does not.
    [[ -n "$(find pom.xml src/main -newer "$STAMP" -print -quit 2>/dev/null)" ]]
}

if [[ "$DO_CLEAN" -eq 1 ]]; then
    needs_maven
    echo "run.sh: cleaning"
    mvn -q clean
fi

if [[ "$RUN_TESTS" -eq 1 ]]; then
    needs_maven
    echo "run.sh: verifying (tests + CI gates)"
    mvn -q verify || die "verify failed"
    touch "$STAMP"
elif [[ "$RUN_BUILD" == "force" ]] || { [[ "$RUN_BUILD" == "auto" ]] && is_stale; }; then
    needs_maven
    echo "run.sh: building"
    mvn -q compile || die "build failed"
    touch "$STAMP"
fi

[[ -d "$CLASSES" ]] || die "nothing compiled in $CLASSES (drop --no-build to build it)"

if [[ ! -s "$CP_FILE" || "pom.xml" -nt "$CP_FILE" ]]; then
    needs_maven
    echo "run.sh: resolving dependencies"
    mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" \
        || die "could not resolve the dependency classpath"
fi

# ---------------------------------------------------------------- run

exec java -cp "$CLASSES:$(cat "$CP_FILE")" "$MAIN_CLASS" "${GAME_ARGS[@]}"
