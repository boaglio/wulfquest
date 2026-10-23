# Wulf Quest — for developers

Everything technical about Wulf Quest: how the repository is laid out, how to
build, run, check and release it, and where it is going. The player's side is
in [README.md](README.md).

[AGENTS.md](AGENTS.md) is the full specification and working agreement: the
rules of the game, the data schemas, the fidelity tiers, the IP rules and the
milestones. Read it before your first change. This file is the short way in;
where the two disagree, AGENTS.md wins.

## Requirements

- **JDK 21 or later** (the code targets `--release 21`; no preview features).
- **Maven 3.9.**
- **Python 3**, only to redraw art with the forges in `tools/art/` or rewrite
  the music with `tools/audio/`. Neither the build nor the tests need it.

## Modules

The build is a Maven parent (`pom.xml`) with two modules:

```
wulfquest/
├── data/       the content database: every number, word, sprite and sound, as JSON
├── replays/    recorded runs, re-played on every build
├── core/       the game itself, for every platform
└── desktop/    Windows, Linux and the plain jar
```

**`core`** is the game: the simulation (`sim`, `world`, `engine`), the data
layer (`data`, `Content`), the shell and every screen (`ui`), the framebuffer
and the painters that draw into it (`render`), the input actions (`input`),
the sound synthesiser (`audio/BeeperSynth`), and the headless tools
(`MapAudit`, `ReplayRunner`, `SpriteForgeCli`). It draws into an indexed
320×256 framebuffer, takes input as `InputState` and `MenuInput`, and names
sounds through `SoundSink`. It never opens a window, reads a key or plays a
sample itself.

**`desktop`** is what runs on Windows and Linux: `Boot` (the command line and
the main loop), the window (`Window`, `Scaler` with scanlines and glow,
`AppIcon`), the keyboard (`KeyboardInput`, `InputMap`), gamepads (`SdlPad`,
through Jamepad), Java Sound (`Mixer`), and the desktop-only tools
(`IconForge`, `PadProbe`). It builds the shaded jar, `wulfquest-VERSION.jar`,
that `tools/package.sh` turns into the downloads.

Some packages (`render`, `input`, `audio`, `tools`) have classes in both
modules. That is deliberate: a class sits in the module its dependencies
allow, under the package that describes what it does.

**The rule:** nothing in `core` may touch AWT, Swing, Java Sound, ImageIO or
Jamepad. `CorePurityTest` scans every compiled core class and fails the build
on a reference to any of them. Code that needs one of those belongs in a
platform module, behind an interface core already has (or a new one).

`data/` and `replays/` stay at the top of the repository: core packs them
into its jar (the game falls back to the classpath when there is no `data/`
beside it), and the tests read them from there, so every module's tests run
with the repository root as their working directory.

### Where it is going

| Platform | Where | Status |
|---|---|---|
| Windows, Linux, jar | `desktop/` | shipping |
| Steam | `steam/`: desktop plus Steamworks (achievements, cloud saves) | planned; AGENTS.md §28.5. Needs a decision on the GPL and Steamworks first |
| Android | `android/`: a Gradle app compiling core's sources | planned; touch controls need a section in AGENTS.md first |
| MSX | its own repository, `wulfquest-msx`, with this one as a git submodule for `data/` | planned; written in C with MSXgl |

Each Java platform is a module beside `desktop`, sharing `core`, so a rule or
map fix lands once and the replays and golden frames check it for all of
them. One version number covers them all. The MSX shares only the data, so it
lives on its own and pins this repository to a release tag.

## Build and run

`./run.sh` builds only when code or a `pom.xml` changed, then runs from the
compiled classes:

```bash
./run.sh                   # play, starting in 8,10
./run.sh --seed 42         # replay the same creatures in every room
./run.sh --room 3,4        # start somewhere else
./run.sh --dev             # K kills Vale, N shows collision, H calls the roaming Wulf
./run.sh --record run.json # save the first game as a replay
./run.sh --debug-effect haste  # start under an orchid's effect: torpor (red), haste
                               # (cyan), reversal (magenta), knockdown (yellow)
./run.sh --no-audio        # silence for this run; your settings are not changed
./run.sh --no-gamepad      # the keys only, for this run
./run.sh --scale 3         # a smaller or bigger window: 1 to 6 times (4 by default)
./run.sh --lives 9         # practice: nine lives in reserve
./run.sh --infinite-lives  # practice: die as often as you like
./run.sh --god             # practice: nothing can kill you
./run.sh --amulet-set 3    # practice: hide the amulet in the original's set 3 (0-7),
                           # not in a set picked at random each game
./run.sh --new-game-plus 1 # practice: start at Mr Jones's job 1 (2 halves) or 2 (whole)
./run.sh endings           # see every ending in turn: the game's, then each job's
                           # (practice runs never reach the hi-score table)
./run.sh --user-dir ./save # keep hi-scores, settings and stats somewhere else
./run.sh --data-dir PATH   # read the content database from somewhere else
./run.sh --headless        # load and validate the data, then exit
./run.sh browse            # the room browser: arrows, [ ] or PgUp/PgDn, M
./run.sh replay            # re-run every recorded replay and check it still matches
./run.sh check             # validate every data file and exit
./run.sh audit             # can every room still be reached? (target/map-audit.txt)
./run.sh sprites sheet     # every scenery object, drawn in the terminal
./run.sh pad               # what SDL takes for a gamepad here
./run.sh jar               # build the single runnable jar and play from it
./run.sh test              # the full verify
./run.sh menu              # all of the above as a list to pick from
./run.sh help              # every command and option
```

`run.sh` is a convenience; Maven is the build, and CI calls it directly:

```bash
mvn -q verify                        # compile, test, validate all data, run the CI gates
mvn -q -Pheadless verify             # the same on a machine with no display
mvn -q -Pfast verify                 # skip anything tagged slow
mvn -q verify -Dgolden.update=true   # rewrite the golden frames, deliberately
mvn -q compile exec:java             # run the game
mvn -q compile exec:java -Dexec.args="--dev --room 8,10"
mvn -q compile exec:java -Dexec.mainClass=wulf.tools.ReplayRunner -Dexec.args="replays/full_run.json"
mvn -q package && java -jar desktop/target/wulfquest-0.1.0-SNAPSHOT.jar
```

`exec:java` needs `compile` in the same command: that is how Maven finds
core's classes without installing a jar of it. It runs in `desktop` and is
skipped in the parent and in `core`.

The jar is self-contained: copy it anywhere and run it, with no `data/`
directory and no classpath.

Exit codes: **0** clean, **1** a replay diverged or the map audit failed,
**2** a data error (the message names the file and the JSON pointer), **64** a
bad command-line option.

## Changing the game

- **Every game value is in `data/`**: speeds, timings, scores, colours,
  strings, sounds. None is in the Java. Edits there take effect on the next
  `./run.sh` with no rebuild, because the game reads the working tree's
  `data/` first.
- **The art is text.** Every sprite is a JSON grid of palette-index
  characters in `data/art/sprites/`. Most are written by the Python forges in
  `tools/art/`; edit the forge, run it, and commit both.
- **Feature flags** in `data/config/game.json` switch on what Wulf Quest adds
  to the original or take back, all off by default: `inventedCreatures`,
  `roamingWulf`, `inventedOrchids`, and `inkedCreatures` (one colour per
  creature, as the original drew them; a player can also set it under
  `features` in their own `settings.json`).
- **Never edit `data/world/original_map.json`.** It is the original's map,
  extracted. Fix problems with collision masks and landmark data.

## How it is built

- **Java 21, no engine.** A 320×256 indexed-colour framebuffer, scaled up by
  an integer factor on the desktop with Java2D. A fixed 50 Hz simulation with
  fixed-point integer maths, so a run is bit-for-bit reproducible from its
  seed.
- **JSON files are the database.** Every file is validated against its schema
  in `data/schema/` at load, then by semantic validators. Jackson 3
  (`tools.jackson`) and json-schema-validator 3.x share one `JsonNode` type.
- **The artwork is text**, so all the art is authored, reviewable in a diff,
  and ours. The build fails if any binary image or audio file appears in the
  repository.
- **The audio is synthesised.** Square waves with hard edges and an LFSR for
  noise, generated at runtime, in the spirit of a one-bit speaker. Two
  channels, mixed by addition and clipped. The tunes share those channels: a
  sound effect steals the one the music is on, and the music keeps going
  underneath.
- **Gamepads** come through Jamepad, SDL2's game controller API with its
  natives in its jar. It is an input library, not an engine: it draws nothing.

## How it is checked

`mvn -q verify` runs 607 tests and every gate the project has:

- **The data validates itself.** Every JSON file is schema-checked at load,
  and the semantic validators go further: a palette colour that does not
  exist, a sprite frame an animation needs and has not got, a lair on a solid
  cell, a hotkey bound twice. A typo is a readable in-game DATA ERROR screen
  naming the file and the JSON pointer, never a stack trace.
- **Golden frames.** The framebuffer is hashed at four ticks of a fixed run
  and once per menu screen (`core/src/test/resources/golden/`), so a sprite
  that moves by a pixel or a panel that loses a digit fails the build.
  Regenerate them deliberately with `mvn -q verify -Dgolden.update=true`, and
  say in the commit why they moved.
- **Replays.** `replays/*.json` are recorded runs with the simulation's state
  hash every 50 ticks; every one is re-executed on every build. A divergence
  means determinism broke, and is never fixed by updating the hash. Three of
  them also assert what they were recorded to show: a Wulf chase survived
  across four rooms, the north-west quarter fetched and carried out, and the
  whole game won without a death.
- **Architecture.** Core never references AWT, Swing, sound, ImageIO or
  Jamepad (`CorePurityTest`). The simulation also never uses
  `java.util.Random`, the wall clock, or a `float` in a signature
  (`SimPurityTest`), which is why a run is reproducible from its seed and why
  sound cannot change what happens.
- **No binaries.** `tools/check-no-binaries.sh` fails the build if any image,
  audio, ROM or tape file appears in the repository. There is no allowlist.

## Repository layout

```
AGENTS.md         the full specification and working agreement
DEVELOPER.md      this file
README.md         for players
pom.xml           the Maven parent: versions, plugins, the no-binaries gate
run.sh            the convenience wrapper
data/             the content database (world, entities, art, audio, config, schemas)
replays/          recorded runs, checked on every build; attract.json is the title's demo
core/
  src/main/java/wulf/
    Content.java  loads and cross-checks the whole content database
    engine/       tick loop, fixed-point, PRNG, replays
    data/         JsonDb, records, schema and semantic validation, the player database
    world/        rooms, collision, the way between rooms
    sim/          the simulation; sim/ai/ one class per creature behaviour
    ui/           the shell (the state machine) and one class per screen
    render/       the framebuffer and the painters
    input/        InputState, MenuInput, the gamepad mapping, recorded input
    audio/        BeeperSynth
    tools/        MapAudit, ReplayRunner, SpriteForgeCli
  src/test/       unit tests, data validators, replays, golden frames
desktop/
  src/main/java/wulf/
    Boot.java     main(), the command line, the loop, the window's mode
    render/       Window, Scaler, AppIcon
    input/        KeyboardInput, InputMap, SdlPad
    audio/        Mixer (Java Sound)
    tools/        IconForge, PadProbe
  src/main/resources/   the gamepad mappings file
  src/test/       the window's scaler, the keyboard, the mixer, the icon, the command line
tools/
  check-no-binaries.sh  the CI gate
  package.sh            the jar to this machine's app: jlink + jpackage
  art/, audio/          the Python forges that write the sprites, the font and the music
.github/
  workflows/release.yml a v* tag builds and publishes the downloads
  release-notes.md      the release page's text
```

## Releasing

Releases are `v0.x.y` tags. Pushing one runs `.github/workflows/release.yml`:
a headless `mvn verify`, the jar, a Windows and a Linux app made by
`tools/package.sh` (each with its own trimmed Java), and a GitHub pre-release
with all three, its page from `.github/release-notes.md`. Running the
workflow by hand builds the same files as workflow artifacts, with no release.

- **Never tag v1.0**, or anything starting with 1, until it is decided the
  game is ready.
- Update `.github/release-notes.md` before tagging: a "What's new" section on
  top, the earlier ones below it.
- **Screenshots** are drawn by the game's own painters from a fixed seed and
  attached to the release (`gh release upload vX.Y.Z screenshot-….png`), never
  committed. The README and the release notes link to them there.
- The builds are not signed yet; there is no macOS app until they are
  (AGENTS.md §28.4).

## Licence

GPL-3.0-or-later; see [LICENSE](LICENSE). A dependency must stay compatible
with it: Jackson, the schema validator and Jamepad are Apache-2.0, and the
SDL2 inside Jamepad is zlib.
