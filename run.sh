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
# Usage:  ./run.sh [script options] [command] [options for the command]
#
# With no command it plays, and anything it does not recognise goes straight
# to the game, so `./run.sh --room 3,4` works as it always has. `./run.sh help`
# lists every command and option; `./run.sh menu` offers them as a list.
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
COMMAND="play"
ARGS=()

die() {
    echo "run.sh: $*" >&2
    exit 1
}

usage() {
    cat <<'USAGE'
Wulf Quest

  ./run.sh [script options] [command] [options]

Commands (default: play):
  play [game options]         play the game
  browse [--room C,R]         the room browser: arrows move, [ ] or PgUp/PgDn step
                              through all 256 rooms, M shows collision
  check                       load and validate every data file, then exit
  replay [FILE...]            re-run recorded replays and check every state hash
                              (default: every replays/*.json)
  audit                       the whole-map navigability audit; full report in
                              target/map-audit.txt
  sprites validate            cross-check every sprite and scenery object
  sprites preview NAME|ID     one sprite in the terminal, by name or scenery id
  sprites sheet [COLUMNS]     every scenery object on one contact sheet
  sprites mask NAME|ID        a scenery object's art above its collision cells
  jar [game options]          build the single runnable jar, then play from it
  test                        the full verify: tests, data validators, CI gates
  menu                        pick one of the above from a list
  help                        this message

Game options (play, browse and jar):
  --room C,R                  start in this room (default: 8,10)
  --seed N                    run seed: the same seed gives the same creatures
  --record FILE               save the first game as a replay (see: replay)
  --debug-effect E            start every game under an orchid's effect:
                              haste, torpor, reversal, immunity, delirium, stillness
  --browse                    the room browser instead of the game
  --dev                       developer mode: K kills Vale, N shows collision,
                              H calls the Wulf, room and position on the panel
  --scale N                   window scale, 1..6
  --data-dir PATH             content database root (default: ./data)
  --user-dir PATH             player database: hi-scores, settings, stats
  --no-audio                  silence for this run; your settings are not changed
  --headless, --no-window     load and validate the data, then exit
  --lives N                   start every game with N lives (1-99)
  --infinite-lives            you still die, but never run out of lives
  --god                       nothing can kill you
                              (any of these three makes a practice run: kept off
                              the hi-score table and the ledger, and not
                              allowed with --record)

Script options (before the command):
  --clean                     mvn clean first (full rebuild)
  --rebuild                   compile even if nothing looks stale
  --no-build                  skip the build entirely (fail if not compiled)
  --test                      run the full verify first, then the command
  -h, --help                  this message

Controls: arrows or WASD walk, Space or Z swing, P pause, M the map, Esc leaves the
jungle for the title screen, and quits from there.

Exit codes:
  0   clean exit
  1   a replay diverged, or the map audit failed
  2   data error (a JSON file is bad; the message names the file and pointer)
  64  bad command-line option (the message says which, and why)
USAGE
}

menu() {
    local choices=(
        "play                       the game"
        "play --dev                 the game in developer mode"
        "play --seed 42             the game with fixed creatures"
        "browse                     the room browser"
        "check                      validate the data and exit"
        "replay                     re-run every recorded replay"
        "audit                      the map audit"
        "sprites sheet              every scenery object in the terminal"
        "jar                        build the runnable jar and play from it"
        "test                       the full verify"
    )
    echo "Wulf Quest — what shall we run?"
    local i
    for i in "${!choices[@]}"; do
        printf '  %2d) %s\n' "$((i + 1))" "${choices[$i]}"
    done
    printf '  %2s) %s\n' "q" "quit"
    local pick
    read -r -p "> " pick || exit 0
    [[ "$pick" == "q" || -z "$pick" ]] && exit 0
    [[ "$pick" =~ ^[0-9]+$ && "$pick" -ge 1 && "$pick" -le "${#choices[@]}" ]] || die "no choice '$pick'"
    # The command is everything before the run of spaces that starts the description.
    local line="${choices[$((pick - 1))]}"
    read -r -a ARGS <<<"${line%%  *}"
    COMMAND="${ARGS[0]}"
    ARGS=("${ARGS[@]:1}")
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)     DO_CLEAN=1; shift ;;
        --rebuild)   RUN_BUILD="force"; shift ;;
        --no-build)  RUN_BUILD="never"; shift ;;
        --test)      RUN_TESTS=1; shift ;;
        -h|--help|help) usage; exit 0 ;;
        play|browse|check|replay|audit|sprites|jar|test|menu)
                     # A command word only counts before any game option: `--record play`
                     # records to a file called play.
                     if [[ ${#ARGS[@]} -eq 0 && "$COMMAND" == "play" ]]; then COMMAND="$1"; else ARGS+=("$1"); fi
                     shift ;;
        --)          shift; ARGS+=("$@"); break ;;
        *)           ARGS+=("$1"); shift ;;
    esac
done

[[ "$COMMAND" == "menu" ]] && menu
[[ "$COMMAND" == "test" ]] && RUN_TESTS=1

# ---------------------------------------------------------------- toolchain

command -v java >/dev/null 2>&1 || die "no 'java' on PATH; Wulf Quest needs a JDK ${MIN_JAVA} or newer"

java_major() {
    local v line
    # Not simply the first line: with JAVA_TOOL_OPTIONS set the JVM prints "Picked up ..." first.
    line="$(java -version 2>&1 | grep -m1 'version "')"
    v="$(sed -n 's/.*version "\([0-9][0-9]*\)\(\.[0-9]*\)*.*/\1/p' <<<"$line")"
    [[ "$v" == "1" ]] && v="$(sed -n 's/.*version "1\.\([0-9][0-9]*\).*/\1/p' <<<"$line")"
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

readonly CP="$CLASSES:$(cat "$CP_FILE")"

case "$COMMAND" in
    play)    exec java -cp "$CP" "$MAIN_CLASS" "${ARGS[@]}" ;;
    browse)  exec java -cp "$CP" "$MAIN_CLASS" --browse "${ARGS[@]}" ;;
    check)   exec java -cp "$CP" "$MAIN_CLASS" --headless "${ARGS[@]}" ;;
    replay)
        if [[ ${#ARGS[@]} -eq 0 ]]; then
            shopt -s nullglob
            ARGS=(replays/*.json)
            [[ ${#ARGS[@]} -gt 0 ]] || die "no replays in replays/"
        fi
        exec java -cp "$CP" wulf.tools.ReplayRunner "${ARGS[@]}" ;;
    audit)   exec java -cp "$CP" wulf.tools.MapAudit "${ARGS[@]}" ;;
    sprites)
        [[ ${#ARGS[@]} -gt 0 ]] || ARGS=(validate)
        exec java -cp "$CP" wulf.tools.SpriteForgeCli "${ARGS[@]}" ;;
    jar)
        needs_maven
        echo "run.sh: packaging (tests skipped; ./run.sh --test jar runs them first)"
        mvn -q package -DskipTests || die "packaging failed"
        shopt -s nullglob
        jars=()
        for j in target/wulfquest-*.jar; do
            [[ "$(basename "$j")" == original-* ]] || jars+=("$j")
        done
        [[ ${#jars[@]} -eq 1 ]] || die "expected one runnable jar in target/, found ${#jars[@]}"
        exec java -jar "${jars[0]}" "${ARGS[@]}" ;;
    test)    echo "run.sh: verify passed"; exit 0 ;;
esac
