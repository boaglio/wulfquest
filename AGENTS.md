# AGENTS.md — Wulf Quest

**Wulf Quest** is a clean-room Java reimplementation of *Sabre Wulf*
(Ultimate Play the Game, ZX Spectrum, 1984): the same 16×16 flip-screen
jungle maze, the same movement feel, the same creature roster, the same
four-piece amulet hunt, the same colour-coded orchid drug system — with
**100% original artwork and audio** and **local JSON files as the only
database**.

This file is the contract for every agent working in this repository. Read
it fully before writing code. When you change a rule here, change the code
and the tests in the same commit.

---

## 0. Table of contents

| §  | Section |
|----|---------|
| 1  | Mission, scope, non-goals |
| 2  | IP rules (hard constraints) |
| 3  | Toolchain and commands |
| 4  | Repository layout |
| 5  | Display model |
| 6  | Simulation model (tick, fixed-point, determinism) |
| 7  | World model (rooms, cells, collision, flip-screen) |
| 8  | The authentic map data |
| 9  | Scenery art work-order (41 objects) |
| 10 | Sprite Forge — art as JSON, zero binaries |
| 11 | Sabreman — movement spec (frame-exact) |
| 12 | Creature roster and AI behaviours |
| 13 | The Wulf |
| 14 | Guardians, the amulet, the exit |
| 15 | Orchids (the drug system) |
| 16 | Loot and scoring |
| 17 | Game rules, states, HUD |
| 18 | Audio — procedural 1-bit beeper |
| 19 | Input |
| 20 | The JSON database layer |
| 21 | Save data |
| 22 | Testing, validation, CI gates |
| 23 | Coding conventions |
| 24 | Milestones and acceptance criteria |
| 25 | Open canon questions (research backlog) |
| 26 | Glossary |
| 27 | Agent working agreement |
| 28 | Distribution — the downloads, and Steam later |

---

## 1. Mission, scope, non-goals

### 1.1 Mission

Recreate the *experience* of Sabre Wulf precisely enough that a person who
played it in 1984 recognises the map, the routes, the panic, and the feel of
Sabreman's walk — while shipping something that is legally ours to
distribute.

Three fidelity tiers govern every decision. Every claim in this document is
tagged with one:

| Tag | Meaning | Rule |
|-----|---------|------|
| **`[CANON]`** | Verified against extracted original data in this repo, or trivially observable | Must match exactly. Changing it is a bug. |
| **`[RECON]`** | Reconstructed by design because the original value is not yet extracted | Implement as specified, keep it in JSON, mark the JSON entry `"fidelity": "recon"`. Replaceable in one edit when canon is found. |
| **`[NEW]`** | Deliberate Wulf Quest addition, not in the original | Must sit behind a feature flag in `game.json`, default **off**. |

### 1.2 In scope

- 256-room (16×16) flip-screen jungle, **[CANON]** layout from extracted data.
- Sabreman: 8-direction walk, sabre swing, 5 lives in reserve, death and respawn.
- Full creature roster with per-species AI.
- The Sabre Wulf: unkillable, cross-room pursuer.
- Four guardians, four amulet pieces, the exit sequence.
- Orchid growth cycle and its six effects.
- Score, hi-score table, panel HUD in the border.
- Procedural beeper audio.
- Everything data-driven from JSON; no game constants in code except
  engine plumbing.

### 1.3 Non-goals

- Not a Z80 emulator. We reimplement behaviour, not opcodes.
- No smooth scrolling between rooms. Rooms **flip**. This is not a bug.
- No mouse control, no analogue movement, no physics engine.
- No network, no telemetry, no accounts, no cloud saves.
- No external game engine (no libGDX, no LWJGL). Java2D only.
- No modern-comfort features (checkpoints, minimap, difficulty easing)
  unless flagged `[NEW]` and defaulted off.

---

## 2. IP rules (hard constraints)

These are not stylistic preferences. Violating them makes the project
undistributable.

### 2.1 Forbidden

1. **No original pixel art.** No sprite, tile, font glyph, or UI element may
   be copied, traced, decoded, re-coloured, or re-scaled from the original
   game, from a disassembly, from a sprite rip, or from a screenshot.
2. **No original audio.** No sampled, transcribed, or re-synthesised
   reproduction of the original's tunes or effect pitches.
3. **No ROM, tape image, snapshot, or disassembly source** in the repo or in
   the build.
4. **No original trade dress**: not the original title, logo, typeface,
   loading screen, box art, character name, or creature names. The
   protagonist is **Ranger Vale**, not Sabreman-the-name. The beast is **the
   Wulf**. The studio name never appears.
5. **No binary image or audio files at all**, not even ours — see §10.

### 2.2 Permitted, and why

**Coordinates and behaviour are facts, not artwork.** Room adjacency, object
placement coordinates, footprint sizes, speeds, and timings are functional
game mechanics — the layout of a maze is not a protectable picture. We reuse:

- `assets/data/original_map.json` — the extracted 16×16 room grid, 48 room
  templates, and 919 object placements (§8). **Keep this file.**
- The **footprint table** in §9 (width × height in cells per object id).
  This is measurement data, already transcribed into this document so the
  source PNGs can be deleted.

### 2.3 Cleanup task — **DONE (M0, 2026-09-12)**

The repository used to contain infringing art inherited from a prototype
(`assets/original/` — 41 decoded scenery graphics; `assets/tiles/`,
`assets/sprites/` — ripped tiles and player frames). All of it has been
deleted, along with the `out/` copies, the prototype source, and the stale
logs. `README.md` no longer carries an "Art & licensing" caveat because
there is nothing left to caveat.

`assets/data/original_map.json` was preserved and moved to
`data/world/original_map.json` (§4) — coordinates, not pictures (§2.2).

If art ever reappears under `assets/`, `tools/check-no-binaries.sh` (§2.4)
fails the build. Do not re-add it; draw it (§9, §10).

### 2.4 CI gate

`tools/check-no-binaries.sh` must fail the build if any file matching
`*.png *.jpg *.jpeg *.gif *.bmp *.ico *.wav *.mp3 *.ogg *.ttf *.otf *.z80
*.sna *.tap *.tzx *.rom` exists anywhere under the repo root. There are no
exceptions and no allowlist. Art lives in JSON (§10); audio is synthesised
(§18); the font is a JSON glyph set (§10.4).

### 2.5 Attribution text (ship this, verbatim)

In `README.md` and on the title screen credits page:

> Wulf Quest is an independent, clean-room homage to the flip-screen jungle
> maze genre of the early 1980s. All artwork, audio, and code are original.
> Room layout geometry was reconstructed from publicly documented map data.
> No assets from any commercial game are included. Not affiliated with,
> endorsed by, or derived from the code of any prior work.

### 2.6 Licence

The whole repository — code, art, music and data — is free software under the
**GNU General Public License, version 3 or later** (`GPL-3.0-or-later`). The
full text is `LICENSE` at the root, and it travels with every copy: inside the
jar (a `pom.xml` resource) and beside the launcher in each release archive
(`tools/package.sh`). The credits page (§17.2) ends with a short notice of it,
after the §2.5 text, which stays verbatim. A dependency must stay compatible
with the GPLv3 (Jackson and the schema validator are Apache-2.0, which is).

---

## 3. Toolchain and commands

| Item | Choice |
|------|--------|
| Language | **Java 21** (`--release 21`); the dev box has JDK 25, do not use preview features |
| Build | **Maven** (`pom.xml` at root); `./run.sh` is a thin wrapper over it (§3.2) |
| Rendering | **Java2D** on an AWT `Canvas` with `BufferStrategy`, hosted in a `JFrame` |
| Audio | `javax.sound.sampled.SourceDataLine`, samples generated in-process |
| JSON | **Jackson 3** (`tools.jackson.core:jackson-databind`) — the `tools.jackson` package, not `com.fasterxml` |
| Schema validation | `com.networknt:json-schema-validator` **3.x**, which is built on Jackson 3 — so the whole data layer shares one `JsonNode` type. Do not mix in a `com.fasterxml` Jackson; the two are incompatible. |
| Tests | **JUnit 5** + AssertJ |
| Static analysis | ErrorProne (or SpotBugs) — warnings are errors in CI |

### 3.1 Canonical commands

Agents must use exactly these. Do not invent ad-hoc `javac` invocations.

```bash
mvn -q verify                  # compile + unit tests + all data validators + CI gates
mvn -q -Pheadless verify       # the same on a machine with no display
mvn -q -Pfast verify           # skip anything tagged slow
mvn -q verify -Dgolden.update=true   # rewrite the golden frames (§22.5), deliberately
mvn -q test                    # unit tests only
mvn -q exec:java                                      # run the game
mvn -q exec:java -Dexec.args="--dev"                  # run with hot-reload + debug overlay
mvn -q exec:java -Dexec.args="--room 8,10"            # boot straight into a room
mvn -q exec:java -Dexec.args="--seed 12345"           # fixed PRNG seed
mvn -q exec:java -Dexec.args="--scale 3 --no-audio"
mvn -q exec:java -Dexec.mainClass=wulf.tools.MapAudit        # §22.3
mvn -q exec:java -Dexec.mainClass=wulf.tools.SpriteForgeCli  # §10.3
mvn -q exec:java -Dexec.mainClass=wulf.tools.ReplayRunner -Dexec.args="replays/foo.json"
```

On a display-less machine, `--headless` validates the content and
`HeadlessSimTest` plays 100 000 ticks with no window; neither may touch AWT
(§22.4). (This section used to name a `wulf.tools.HeadlessSim` entry point; it
never existed outside the test.)

Which tests run is one property, `excludedGroups`, read by the Surefire
plugin — not a literal in its configuration, or the profiles that set it
would do nothing, as they did until M9. Nothing carries the `slow` or `gui`
tag today: the whole suite is a few seconds, and no test opens a window. The
tags and their profiles are there for the first test that needs one.

### 3.2 `./run.sh` — the convenience wrapper

`run.sh` wraps the commands above for day-to-day play. It is a wrapper, not a
second build system: `pom.xml` stays the source of truth, and CI calls Maven
directly. Never add build logic to the script that Maven does not also do.

It offers every way there is to run the project, as commands (the first word
that is not a script option; the default is `play`):

```bash
./run.sh                      # build if stale, then play
./run.sh --scale 3            # unrecognised options pass straight to the game
./run.sh --room 7,3           # play, starting in a room
./run.sh browse --room 7,3    # the room browser, opened at a room
./run.sh check                # load and validate the data, then exit (--headless)
./run.sh replay [FILE...]     # ReplayRunner; every replays/*.json by default
./run.sh audit                # MapAudit (§22.3)
./run.sh sprites preview 7C0C # SpriteForgeCli: validate | preview | sheet | mask (§10.3)
./run.sh jar --no-audio       # mvn package -DskipTests, then java -jar the shaded jar
./run.sh test                 # full mvn verify (tests + CI gates)
./run.sh menu                 # the same choices as a numbered list
./run.sh --clean              # mvn clean first
./run.sh --rebuild            # compile even if nothing looks stale
./run.sh --no-build           # skip the build (fails if nothing is compiled)
./run.sh --test replay        # full verify first, then the command
./run.sh help                 # every command and every game option
```

Three behaviours worth knowing:

- **Editing `data/` does not trigger a rebuild.** Staleness is judged from
  `pom.xml` and `src/main` only, because the game reads `./data` from the
  working tree first (§20.1). Content edits are picked up on the next run with
  no compile at all — a cold run is ~4.5 s, a warm one ~0.4 s.
- It reads the Java version from the line that carries it, not the first
  line: with `JAVA_TOOL_OPTIONS` set the JVM prints `Picked up ...` first, and
  the script used to decide it had Java 0.
- It **appends** to `MAVEN_OPTS` rather than replacing it, so a developer's
  own heap or locale settings survive. It adds only
  `--sun-misc-unsafe-memory-access=allow`, which silences a deprecation banner
  Maven 3.9's bundled Guava emits on modern JDKs.
- It `exec`s the JVM, so the game's exit code is the script's: **0** clean,
  **1** a replay diverged or the audit failed, **2** data error (§17.1),
  **64** bad command-line option. A malformed
  argument is reported in one line — which option, and why — never as a stack
  trace.

---

## 4. Repository layout

```
wulfquest/
  AGENTS.md                     this file — the contract
  README.md                     player-facing; carries the §2.5 attribution
  pom.xml                       Maven build — the source of truth
  run.sh                        convenience wrapper around it, §3.2
  .gitattributes                LF endings for all text, §20.9
  .gitignore                    target/, attic/, *.log, save/
  .github/
    workflows/release.yml       a v* tag builds and publishes the downloads, §28
    release-notes.md            the release page's download table and first-run help, §28
  tools/
    art/scenery_forge.py        generates the 41 scenery sprites, §10.5 (dev-time only)
    art/character_forge.py      generates Ranger Vale's frames, §10.5 (dev-time only)
    art/creature_forge.py       generates the 13 creature sprites and the puff, §10.5 (dev-time only)
    art/item_forge.py           draws the map scroll, the eyes, the looking glass and its arrow, the map's diamond and the treasures, §14.8, §14.10, §16.4, §17.7 (dev-time only)
    art/ending_forge.py         draws Mr Jones, the later jobs' amulets and the horses home, §14.7 (dev-time only)
    art/font_forge.py           generates the 8x8 display set in font.json, §10.4 (dev-time only)
    audio/music_forge.py        writes music.json from phrases of note tokens, §18.3 (dev-time only)
    check-no-binaries.sh        CI gate, §2.4 — bound to the verify phase
    package.sh                  the jar to this machine's app: jlink + jpackage, §28.2
    check-schemas.sh            validates every data file against its schema
  data/                         THE DATABASE. Read-only at runtime. Ships in the jar.
    schema/                     *.schema.json, one per data family, §20.2
    config/
      game.json                 tuning: tick rate, lives, feature flags
      display.json              palette, scale, border, CRT options
      input.json                keymaps
      shell.json                every string and beat the shell shows, §17
      default_highscores.json   the table a first run starts from, §21.2
      settings_defaults.json    the settings a first run starts from, §21.3
    world/
      original_map.json         [CANON] extracted grid + templates (§8)
      scenery.json              object id -> footprint, art ref, collision mask (§9)
      rooms.json                per-room overrides: biome, entity budget, props
      room_entities.json        spawn rules, biome markers, budgets, authored rooms (§12.6)
      landmarks.json            exit, lair rooms, shrines, the map scroll and eyes, the temple (§14)
      treasures.json            [CANON] where treasures lie, what they are, what they score (§16.4)
    entities/
      player.json               Sabreman/Ranger Vale stats + animation (§11)
      creatures.json            full roster (§12)
      wulf.json                 the Sabre Wulf (§13)
      flame.json                the flame that comes for a player who lingers (§12.7)
      jungle.json               the original's creatures: the swarm and its 14 kinds (§12.8)
      guardians.json            four guardians (§14)
      orchids.json              growth cycle + effect table (§15)
      loot.json                 amulet pieces, score values (§16)
    art/
      palette.json              the 16-colour palette (§5.2)
      sprites/*.sprite.json     ALL artwork, as text (§10)
      font/font.json            4x6 and 8x8 glyph sets (§10.4)
    audio/
      sfx.json                  beeper effect definitions (§18.2)
      music.json                original tunes as note lists (§18.3)
    i18n/
      en.json                   every user-visible string
  save/                         WRITTEN AT RUNTIME to the user data dir, not here
  src/main/java/wulf/
    Boot.java                   main(), CLI args, window setup
    Content.java                loads and cross-checks the whole content DB, for game and tools
    engine/                     tick loop, timing, fixed-point, PRNG, replay
    render/                     Framebuffer, Blitter, Palette, Scaler, HudPainter, AppIcon
    audio/                      BeeperSynth, Mixer
    input/                      InputMap, InputState, RecordedInput
    data/                       JsonDb, repositories, records, schema validation
    world/                      Room, RoomGrid, CollisionMask, RoomTransition
    sim/                        Simulation, entity systems, collision, effects
    sim/ai/                     one class per behaviour in §12.5
    ui/                         Shell (the state machine), ShellPainter, GamePainter,
                                GameSession, and one class per screen
    tools/                      MapAudit, SpriteForgeCli, HeadlessSim, ReplayRunner, IconForge
  src/test/java/wulf/           mirrors the above
  src/test/resources/golden/    play.hash and shell.hash — framebuffer hashes, §22.5
  replays/                      recorded input streams for regression, §22.6
```

Rules:

- `src/main/java/wulf/sim/**` must not import `java.awt.*`, `javax.swing.*`,
  or `javax.sound.*`. Enforced by a test (§22.4).
- `data/**` is the only source of game values. A numeric literal describing
  game behaviour inside `src/**` is a defect. Engine plumbing constants
  (buffer sizes, thread counts) are fine.
- Nothing in `src/**` writes to `data/**`. Saves go to the OS user data dir
  (§21.1).

---

## 5. Display model

### 5.1 Geometry **[CANON]** for the playfield, **[RECON]** for the border

The original is a 256×192 pixel screen of 32×24 character cells of 8×8
pixels, wrapped in a coloured border. **The top two character rows are the
score banner; the jungle is the 32×22 cells below it** (256×176 px). The
disassembly shows it (§25 Q14): the play area is cleared and drawn from
screen address `$4040`, which is row 2; lives are printed on row 0 and the
scores on row 1; and a placement's `y` is used unchanged as a screen row, so
the lowest `y` in `original_map.json`, 2, is the first row under the banner.
`OriginalMapRepository.SCREEN_TOP_ROW` takes those two rows off every
placement, so a room's own row 0 is its top.

Until 2026-09-27 this section claimed the placements proved a full 32×24
playfield. They prove the opposite, and the two empty rows that claim put at
the top of every room were a corridor the original never had.

Our panel stays below the playfield, in the border (`[RECON]`): where the
banner sits is presentation, and the rooms are the same either way.

```
virtual canvas: 320 x 256
+--------------------------------------------------+  y=0
|                border (32 px)                    |
|   +------------------------------------------+   |  y=32
|   |                                          |   |
|   |   PLAYFIELD  256 x 176  = 32 x 22 cells  |   |
|   |   room origin at (32,32)                 |   |
|   |                                          |   |
|   +------------------------------------------+   |  y=208
|   |   PANEL  256 x 40   (score, lives,       |   |
|   |          amulet, effect timer)           |   |
|   +------------------------------------------+   |  y=248
|                border (8 px)                     |
+--------------------------------------------------+  y=256
     x=0        x=32                      x=288  x=320
```

Constants live in `display.json`:

```json
{
  "schemaVersion": 1,
  "canvas":    { "w": 320, "h": 256 },
  "playfield": { "x": 32, "y": 32, "w": 256, "h": 176, "cell": 8, "cols": 32, "rows": 22 },
  "panel":     { "x": 32, "y": 208, "w": 256, "h": 40 },
  "scale":     { "default": 4, "min": 1, "max": 6, "integerOnly": true },
  "border":    { "idleColour": "black", "flashOnEvent": true },
  "crt":       { "scanlines": false, "scanlineLuminancePercent": 82, "glow": false, "attributeClash": false },
  "spriteFlicker": false
}
```

### 5.2 Palette **[RECON]** (Spectrum-*shaped*, our own values)

A 16-entry palette: 8 hues × 2 brightness levels. Do **not** use the
original hardware's exact RGB values as a set copied from a reference table;
these are ours, chosen to read correctly on modern sRGB displays.

`data/art/palette.json`:

```json
{
  "schemaVersion": 1,
  "entries": [
    { "i": 0,  "name": "black",         "rgb": "#000000" },
    { "i": 1,  "name": "blue",          "rgb": "#1926A8" },
    { "i": 2,  "name": "red",           "rgb": "#A82219" },
    { "i": 3,  "name": "magenta",       "rgb": "#A826A8" },
    { "i": 4,  "name": "green",         "rgb": "#19A226" },
    { "i": 5,  "name": "cyan",          "rgb": "#19A2A8" },
    { "i": 6,  "name": "yellow",        "rgb": "#A8A226" },
    { "i": 7,  "name": "white",         "rgb": "#B4B4B4" },
    { "i": 8,  "name": "brightBlack",   "rgb": "#000000" },
    { "i": 9,  "name": "brightBlue",    "rgb": "#2740FF" },
    { "i": 10, "name": "brightRed",     "rgb": "#FF3A2C" },
    { "i": 11, "name": "brightMagenta", "rgb": "#FF40FF" },
    { "i": 12, "name": "brightGreen",   "rgb": "#2CE840" },
    { "i": 13, "name": "brightCyan",    "rgb": "#2CE8FF" },
    { "i": 14, "name": "brightYellow",  "rgb": "#FFE840" },
    { "i": 15, "name": "brightWhite",   "rgb": "#FFFFFF" }
  ],
  "transparent": -1
}
```

### 5.3 Rendering pipeline — indexed, not RGB

```
Simulation ──► Framebuffer (byte[320*256], palette indices, -1 illegal)
                   │  clear to border colour, draw panel, draw room scenery,
                   │  draw entities in z-order, draw effect overlays
                   ▼
              PaletteResolver ──► int[320*256] ARGB (cached, one lookup table)
                   ▼
              Scaler (nearest-neighbour, integer factor) ──► BufferedImage
                   ▼
              BufferStrategy.getDrawGraphics().drawImage(...)  [vsync]
```

Hard rules:

- **The framebuffer is a `byte[]` of palette indices.** No `Graphics2D`
  drawing primitives touch game pixels — no `drawOval`, no `fillRect`, no
  antialiasing, no alpha blending, ever. `Graphics2D` is used for exactly
  one call per frame: blitting the final upscaled image.
- Sprites blit with index `-1` (in the sprite's own encoding, `.`) meaning
  *skip this pixel*. There is no partial transparency.
- `RenderingHints.KEY_INTERPOLATION` = `VALUE_INTERPOLATION_NEAREST_NEIGHBOR`.
- Window resize snaps to the largest integer scale that fits; remaining space
  is letterboxed in `border.idleColour`.
- Z-order, back to front: panel → room scenery → orchids → loot → creatures
  (sorted by `y` of feet, ascending) → the Wulf → player → player sabre →
  effect overlay → panel flash.

### 5.4 Optional retro toggles (`[NEW]`, all default off)

The player turns the `crt` ones on in `settings.json` (§21.3), which
overrides `display.json`'s shipped `false`; the scanline depth is always the
shipped one. `spriteFlicker` lives only in `display.json`. None of them
touches the simulation, and with all of them off the golden frames (§22.5)
are unchanged. `RetroTogglesTest` covers each.

- `crt.scanlines` — every odd output row multiplied by
  `crt.scanlineLuminancePercent` (82) per channel, after upscale, so the lines
  are one output pixel tall at any scale. `Scaler.setScanlines`.
- `crt.attributeClash` — quantise the playfield to one ink + one paper per
  8×8 cell, resolving conflicts by the last sprite drawn. Faithful, ugly,
  fun. Never on by default. Paper is black (either brightness); the
  framebuffer remembers the last non-paper colour written into each cell
  (`Framebuffer.trackInk`) and `AttributeClash` repaints every non-paper
  pixel of the cell in it. Playfield only; the panel and border never clash,
  and the `--dev` collision mask switches it off.
- `crt.glow` — **reserved, unimplemented.** The flag exists in both schemas
  but nothing here says what it should look like; spec it before building it.
- `spriteFlicker` — when 3+ sprites overlap one cell, alternate which draws
  on odd/even ticks. Creatures only (`CreaturePainter`): Vale, the Wulf and
  the guardians always draw.
- `border.flashOnEvent` — the border strobes on pickup/death/wulf-spawn (red) and
  flashes white on a parry (§13.5),
  Ultimate style. **This one defaults on** — it is part of the feel.

---

## 6. Simulation model

### 6.1 Fixed timestep

The host machine's display was **50 Hz** **[CANON]**; a game of this kind ran
its logic once per video frame, so we tick at **50 Hz** **[RECON]**. If Q5
(§25) ever shows the original updated on alternate frames, change `TICK_HZ`
and rescale every `*Ticks` and `*Fp` value in `data/` by the same factor —
which is exactly why no timing constant lives in Java.

```
TICK_HZ         = 50
TICK_NANOS      = 20_000_000
MAX_CATCHUP     = 5 ticks per frame (then drop time, never spiral)
RENDER          = once per tick, vsynced; no interpolation, no sub-tick motion
```

Loop shape (`engine/GameLoop.java`):

```java
long acc = 0, prev = System.nanoTime();
while (running) {
    long now = System.nanoTime();
    acc += now - prev;
    prev = now;
    int steps = 0;
    while (acc >= TICK_NANOS && steps < MAX_CATCHUP) {
        input.sampleForTick();       // exactly one sample per tick
        sim.tick();                  // pure, deterministic
        acc -= TICK_NANOS;
        steps++;
    }
    if (acc >= TICK_NANOS) acc = 0;  // drop, do not accumulate debt
    renderer.render(sim.view());
}
```

### 6.2 Fixed-point arithmetic — mandatory

**No `float` or `double` anywhere in `sim/**` or `world/**`.** Positions and
speeds are `int` in **8.8 fixed point**: `1 pixel == 256`.

```java
public static final int ONE = 256;
static int px(int fp)        { return fp >> 8; }            // to whole pixels
static int fp(int pixels)    { return pixels << 8; }
static int mul(int a, int b) { return (int)(((long)a * b) >> 8); }
```

Rationale: bit-exact determinism across JVMs and platforms, which the replay
tests (§22.6) and golden-frame tests (§22.5) depend on. A `double` in the
simulation is a build-breaking defect.

### 6.3 Randomness

- One PRNG type: **xorshift128+**, implemented in `engine/Rng.java`. Never
  `java.util.Random`, never `Math.random()`.
- **Two streams, never mixed:**
  - `simRng` — seeded from the run seed. Every gameplay decision uses it.
    Its state is part of the save/replay record.
  - `cosmeticRng` — seeded from wall clock. Border flashes, idle animation
    phase offsets. Must not influence gameplay, ever.
- Room content generation uses a **third, derived, stateless** stream:
  `roomSeed = hash64(runSeed, col, row, visitCount)`. Re-entering a room
  reproduces its layout for that visit count (§7.6).

### 6.4 Determinism contract

Given `(runSeed, dataDirHash, inputStream)`, the simulation must produce a
byte-identical state hash at every tick, on any JVM 21+, on any OS. Anything
that breaks this: `HashMap` iteration order in simulation paths (use
`LinkedHashMap` / sorted iteration), `System.currentTimeMillis()` in sim,
floats, unordered parallel streams, identity hash codes — and **`Map.copyOf` /
`Map.of`**, whose iteration order is randomised per JVM run. A record holding a
map it will iterate (behaviour params, biome weights) copies it with
`Collections.unmodifiableMap(new LinkedHashMap<>(m))`, keeping file order; a
weighted pick walks the weights in that order. `Map.ofEntries` is fine only for
lookups that are never iterated.

Entity update order is fixed: **by stable integer `entityId`, ascending**.
Ids are assigned from a monotonic counter that is part of sim state.

---

## 7. World model

### 7.1 Units

| Unit | Size | Notes |
|------|------|-------|
| pixel | 1 | virtual pixel |
| cell | 8×8 px | the collision and layout grid |
| room | 32×22 cells = 256×176 px | one flip-screen; the screen less its 2-row banner (§5.1) |
| map | 16×16 rooms = 256 rooms = 4096×2816 px | **[CANON]** |

Room addressing is `(col, row)` with `col` 0..15 left→right and `row` 0..15
**top→bottom** (row 0 is north). Canonical id string is `"c,r"`, e.g.
`"8,10"`. Linear index is `row * 16 + col`.

### 7.2 Room composition

A room is not a tilemap. It is a **list of placed scenery objects** —
exactly as the original stored it. Each placement is `{ object, x, y }` with
`x,y` in **cells**, origin top-left of the playfield. (In the JSON `y` counts
from the top of the original's *screen*; the repository subtracts the two
banner rows — §5.1.)

At load time each room is baked into a `CollisionMask`: a
`32 × 22` bitset, one bit per cell, `true` = impassable.

```
Room load:
  1. roomType = original_map.json.roomTypeGrid[row][col]
  2. placements = original_map.json.templates[roomType]
  3. for each placement: OR the object's collisionCells into the mask,
     offset by (x, y); clip to the 32x22 bounds
  4. apply rooms.json overrides for this specific room id (added/removed props)
  5. cache the mask (immutable, shared by all rooms of the same type
     unless overridden)
```

### 7.3 Collision mask derivation (§9 feeds this)

For each scenery object, `scenery.json` declares its footprint and its
collision. Default rule: **a cell is solid if the object's own art paints
≥ 25% of that cell's 64 pixels.** Authors may override per object with an
explicit `collisionCells` bitmap string (rows of `#` and `.`).

If the map audit (§22.3) reports a sealed room or an unreachable room, **fix
the object's `collisionCells`, never the room layout** — the layout is
`[CANON]`.

**Measured in M2 — read before touching collision.** With every object's
*full footprint* solid, all 196 interior rooms are reachable from the start,
at 25 / 39 / 52 % walkable (min / median / max). The extracted map is
therefore **a maze at footprint resolution**: the paths are the gaps between
object rectangles. Two consequences:

- **Art can never break connectivity.** An art-derived mask is always a
  subset of the footprint, and reachability only grows as walkable space
  grows. `RoomBakerTest` locks the subset invariant; `MapAuditTest` locks
  the footprint baseline.
- **The real risk runs the other way: sparse art opens routes the original
  walled off.** Interior median walkable, by rule:

  | collision rule | min / median / max % |
  |---|---|
  | footprint (the canon maze) | 25 / **39** / 52 |
  | art, cell solid at ≥ 1 % painted | 38 / 48 / 57 |
  | art, ≥ 10 % | 41 / 53 / 60 |
  | art, ≥ 25 %, M2's first art | 41 / 56 / 61 |
  | **art, ≥ 25 %, after the redraw (current)** | 35 / **45** / 55 |
  | art, ≥ 50 % | 42 / 65 / 70 |

  No threshold recovers the canon maze: thin sprites leave whole columns of
  their footprint unpainted. The gap is concentrated — `771F`, `7462` and
  `71B3` cause 37 % of the extra walkable area, and ten objects cause 82 %.

**Decided 2026-09-12 (§25 Q13): keep art-derived collision, and make the art
fill its footprint.** Footprint collision was rejected because it puts
invisible walls beside thin plants; leaving sparse art was rejected because it
loosens the routes the player is meant to recognise. Eleven sprites were
redrawn to fill 81–96 % of their footprint cells, bringing the interior median
from 56 % to **45 %** — **6 points** more open than the canon maze, down from
17 — with no invisible walls anywhere. `MapAuditTest` fails if that gap grows
past 6 points, so a sprite edit cannot quietly reopen the maze.

What remains is mostly mountain silhouettes (`7E4B`, `81C5`), whose triangles
are kept as drawn on purpose, and whose placements sit largely on the boundary
ring, which does not count toward the interior figure.

Do not change `solidCoveragePercent`, and do not add blanket `collisionCells`
overrides to close a gap: fix the art (§9).

### 7.4 Player/creature collision

- Every mobile entity has an axis-aligned **collision box** in pixels,
  relative to its sprite origin, declared in JSON.
- The box is a **feet box**, not the sprite bounds: Ranger Vale's sprite is
  16×24 px, his box is `{ x: 3, y: 15, w: 10, h: 9 }` — only the lower body
  collides with scenery. This is what makes the maze feel navigable while
  the art stays tall.
- Scenery collision test: convert the box's four corners to cells, test the
  mask. A box overlapping any solid cell is blocked.
- **Axis-separated resolution**, in this order, every tick:
  1. Apply the full X component of velocity. If blocked, settle on the
     nearest non-colliding **whole pixel**, stepping back 1 px at a time
     (binary search is forbidden). The fraction is dropped deliberately:
     collision is tested per whole pixel, so a kept sub-pixel remainder lets a
     pinned player creep about inside the last free pixel — M3's
     `WallSlideTest` caught exactly that. The settle only ever lands **between
     the current position and the target**, never behind the current position:
     settling a sub-pixel mover back to its own pixel boundary made slow
     creatures twitch forever against walls and broke stall detection (found in
     M4, pinned by `MovementFeelTest.aSubPixelMoverPinnedAgainstAWallIsStill`).
  2. Apply the full Y component. Same clamping.
  This ordering is what produces the original's **wall-sliding**: pushing
  diagonally into a wall slides you along it instead of stopping dead. It is
  a required feel property, tested in §22.2.
- **Sliding keeps full speed.** The diagonal scale (§11.2) applies only when
  neither axis is already touching a wall (a 1 px probe on each axis). Pressed
  against a wall on one axis, the player slides along the other at that axis's
  full cardinal rate.
- Entity-vs-entity collision is box overlap, tested after all movement.
  Creatures use the same resolver on their own boxes, against the scenery with
  the room's edges as walls (fliers: the edges only).

### 7.5 Flip-screen transition **[CANON]** behaviour, **[RECON]** timings

Triggered when the player's **feet box centre** crosses a playfield edge.

```
positions live in WORLD pixels (0..4096 x 0..2816), never room-local
centre   = the feet-box centre
room     = (floor(centre.x / 256), floor(centre.y / 192))
a change of room is the transition; the position itself does not change
```

Implemented this way in M3. Keeping the player in world coordinates makes the
preserved cross-edge coordinate exact by construction — there is no entry
offset to compute, and none to get wrong. Rendering subtracts the room origin.

- The cross-edge coordinate is **preserved exactly** (you come out of the
  new room's edge at the same offset you left at). No re-centring.
- The map does **not** wrap. The outer ring of rooms (row 0, row 15, col 0,
  col 15) is the impassable river/mountain boundary **[CANON]** — its rooms
  are fully masked at the outward edge, so the player can never trigger a
  transition off the grid. If one is ever requested, that is a mask bug:
  log an error and cancel the transition.
- Transition sequence: **6 ticks of frozen simulation** (player and creatures
  stop, room is already the new one, panel keeps updating), then play resumes.
  No wipe, no fade — instant flip, brief hitch. Set
  `transition.freezeTicks` in `game.json`.
- On transition: all creatures of the old room are **discarded**. Orchid
  growth state is **persisted per room** (§15.1). The Wulf is **carried
  across** (§13.4). Active player effects persist.

### 7.6 Room population lifecycle

```
enter room ──► look up room_entities.json["c,r"]
                 │  present?  spawn exactly that list (authored, canon-track)
                 │  absent?   generate from rooms.json biome budget
                 │            using roomSeed = hash64(runSeed, col, row, visits)
                 ▼
            visits++ ; store per-room state (orchids, taken loot) in sim state
```

- Loot that has been collected **never respawns** (`takenLoot` set in sim
  state, persisted in saves).
- Creatures **always** respawn on re-entry. Killing things is relief, not
  progress. This is core to the original's tension.
- `visits` is capped at 8 for seeding purposes so long sessions don't drift
  into unbounded variety.

---

## 8. The authentic map data **[CANON]**

`data/world/original_map.json` is extracted, verified data. Treat it as
read-only reference. Both halves are now checked against the game itself
(§25 Q14, 2026-09-27): the 48 templates are byte for byte the game's
`Room_00`–`Room_2F` in order, and `roomTypeGrid` is the game's layout table at
`$6066`. The grid used to be a consistently relabelled copy of that table
while the templates kept the game's numbering, so every room was drawn with
the wrong template; it was replaced, and nothing else in the file changed.
Its shape:

```json
{
  "gridW": 16, "gridH": 16,
  "startCol": 8, "startRow": 10,
  "roomTypeGrid": [[0,20,20,...], ... 16 rows of 16 ...],
  "numTemplates": 48,
  "templates": { "0": [ { "graphic": "7C0C", "x": 0, "y": 2 }, ... ], ... "47": [...] }
}
```

Verified facts — assert all of these in `MapDataTest`:

| Fact | Value |
|------|-------|
| Grid | 16 × 16 = **256 rooms** |
| Start room | **col 8, row 10** |
| Templates defined | **48** (ids 0..47) |
| Templates actually used on the grid | **45** (all but **1, 19 and 36**) |
| Distinct scenery objects | **41** |
| Object placements across templates | **919** |
| Object placements across all 256 rooms | **4920** |
| Objects per template | 13 (template 18) to 26 (templates 15, 16) |
| Max extent of any placement | right edge exactly **32** cells, bottom edge exactly **24** screen rows (22 room rows) |
| Smallest `y` | **2**: the first screen row under the banner (§5.1) |
| md5 | `5a01a207a4786a261830bdb26c742083` (was `daf8a2b6…` before the grid fix) |

The placement coordinate space is **cells**, and the graphics' pixel sizes
are exactly `4 × (cells × 8)` — the source PNGs were 4× upscales. The
footprints in §9 are already divided down; use them directly.

### 8.1 The room type grid

Row 0 is north. This is the map. Do not "improve" it.

```
      c0  c1  c2  c3  c4  c5  c6  c7  c8  c9 c10 c11 c12 c13 c14 c15
 r0     0  20  20  26  22  20  20  22  20  26  27  20  26  20  22   2
 r1    38  33  29  28  17  12  13  23  29  23  29  33  17  31  11   3
 r2    37  21  21  13  11  12  18  24  21  17  23  24  24  18  17  30
 r3    37  24  17  12   5  18  21  32  28  25  17  11  11  45  17   3
 r4    39  28  21  18   8  24  21  13  31  31  28  24  21   8  17  30
 r5    39  33   5  17  31  28  17  12  18  18  13  23  24  13  11   3
 r6    39  23  17  11  31  12  17  46  11  17  12  21  17  31  23  30
 r7    37  17  23   8  18  13  11  10  15  10  12  17  23  13  23  34
 r8    40  11  11  31  45  18  21  16   7  16  18  21  32  31  28  35
 r9    37   5  24  29  11  23  17  14   6  14  47  17  12  18  29   3
 r10   37  32  28  25  21  24  21  18   8  18  17  23  18  21  32  30
 r11   40  33  29  31  28   8  32  23  29  28  21  11  23  11  31  34
 r12   39  11  45  18  29  31  33  17  28  13  23   8  24  24  29  34
 r13   37  24   5   5  32  33  32  11  31  12  17  31  28  21  24  35
 r14   41   8  25  21  18  32  33  25  29  33  21  29  31  28  21  35
 r15    4  42  44  43  43  42  43  42  43  43  43  43  42  42  43   9
```

### 8.2 Region structure (derived, `[CANON]` observations)

Re-derived 2026-09-27 from the corrected grid (§8, §25 Q14). The earlier
version of this section described the relabelled grid: a central lake, 21
hut villages and 8 arch rooms, none of which the game has.

- **Boundary ring** — types **0, 2–4, 9, 20, 22, 26, 27, 30, 34, 35, 37–44**
  appear only on row 0, row 15, col 0, col 15. These are the river/mountain
  edge: impassable outward, walkable inward. Emulating the original's
  collision over this grid finds no way off the edge of the world (§25 Q14).
- **Interior** — types **5–8, 10–18, 21, 23–25, 28, 29, 31–33, 45–47** fill rows
  1–14, cols 1–14: the 196 playable jungle rooms. The interior and boundary
  type sets are disjoint — the ring has its own vocabulary of templates —
  and `MapDataTest` locks both sets exactly.
- **The temple** — a mirrored structure around col 8, rows 6–9, built from
  otherwise-unique types:
  ```
        c7  c8  c9  c10
   r6   46  11  17        <- 46 unique
   r7   10  15  10        <- 15 unique, 10 mirrored pair
   r8   16   7  16        <- 7 unique, 16 mirrored pair
   r9   14   6  14   47   <- 6 unique, 14 mirrored pair
  ```
  Its templates — 6, 7, 10, 14, 15, 16 — are exactly the rocky ones: yellow
  rock (`847C`, `872A`, `8558`, `8B80`, `8D3C`, `8C5C`, `8CCC`, `83D2`,
  `8427`, `86DA`, `8702`) and the two bone-strewn walls (`8806`, `89C3`), the
  **Bonefields** biome (§12.6). At its heart, `(8,8)`, stands the map's only
  stone arch. The start room `(8,10)` is two rooms south — and, on foot, 132
  room crossings away. Its mirror symmetry is the player's primary
  navigation anchor; preserve it exactly.
- **Water** — the water object `93C4` is scattered through 27 rooms of five
  templates (24, 25, 39, 42, 47), mostly near the edges; there is no lake.
  `landmarks.json → temple` names the temple's rooms "The Bone Temple" (it was
  "The Still Water", after the lake the relabelled grid put here); nothing reads
  it but the validator.
- **Hut rooms** — template 5 contains the single hut object `8E18` and is
  used in **5 rooms**: `(4,3) (2,5) (1,9) (2,13) (3,13)`. These are the
  tribesmen's villages: they get a guaranteed elevated tribesman spawn (§12.6),
  and they are the shrines (§14.5): ask the way at the hut door.
- **The arch** — template 7 contains the stone arch `85C8`, used in exactly
  one room, `(8,8)`: the way out (§14.2). There are no other arch rooms, so
  the shrines moved to the huts (§14.5).

### 8.3 Adapter rule

Do not refactor `original_map.json`'s shape. Write
`data/JsonDb` → `OriginalMapRepository` which exposes:

```java
int  roomType(int col, int row);
List<Placement> placements(int roomType);          // immutable
RoomAddress startRoom();                           // (8, 10)
int  gridW(); int gridH();
```

and let everything else in the codebase speak in terms of `Room` objects
built from that.

---

## 9. Scenery art work-order (41 objects)

Each of the 41 object ids below needs **one newly drawn sprite** at the given
size. The id strings are the original data's keys — keep them as opaque
identifiers so `original_map.json` still resolves. `uses (tmpl)` counts
placements across the 48 templates; `uses (rooms)` counts placements across
all 256 rooms and is your **art priority order**.

`subject` describes the silhouette the map needs at that spot — a mountain
must read as a mountain or the map stops being legible. Draw *our* mountain.

| id | cells (w×h) | pixels | uses (tmpl) | uses (rooms) | subject to draw (original art, same footprint) | palette |
|----|-------------|--------|-------------|--------------|-----------------------------------------------|---------|
| `7298` | 2×5 | 16×40 | 104 | **609** | single narrow tall fern, one slim stem | greens |
| `78F2` | 3×3 | 24×24 | 76 | **530** | small palm, banded trunk | green + red |
| `7947` | 2×3 | 16×24 | 62 | **418** | squat leafy plant with one fruit | green + yellow |
| `8F2A` | 6×7 | 48×56 | 42 | 365 | reed cluster, vertical stems | green + red |
| `71B3` | 5×5 | 40×40 | 67 | 342 | palm cluster, two crossing trunks | greens + red + cyan |
| `7523` | 8×7 | 64×56 | 46 | 276 | palm grove, dense, small ground detail | greens + red |
| `72F6` | 8×5 | 64×40 | 50 | 275 | wide broad-leaf bank, hanging fruit | green + red + yellow |
| `955D` | 4×3 | 32×24 | 42 | 275 | dense blocky hedge | bright green |
| `95CD` | 6×3 | 48×24 | 47 | 267 | low shrub row with one red plant and a fruit | green + red + yellow |
| `7981` | 4×11 | 32×88 | 34 | 255 | very tall narrow conifer/cypress, crowned top | green + yellow |
| `70BC` | 9×3 | 72×24 | 33 | 202 | long low leafy bank with flowers | green + red + magenta |
| `7462` | 3×7 | 24×56 | 37 | 196 | tall spike plant topped with a bloom | green + magenta |
| `90A8` | 8×11 | 64×88 | 23 | 144 | the big one: mixed palm/banana grove, gourd at base | greens + red + yellow |
| `785E` | 4×4 | 32×32 | 23 | 135 | spiky agave fan, radial blades | bright green |
| `771F` | 5×7 | 40×56 | 29 | 111 | fern with a gourd at its base | green + yellow |
| `9673` | 1×3 | 8×24 | 8 | 78 | single thin grass tuft (the filler piece) | green |
| `7C0C` | 7×3 | 56×24 | 23 | 62 | distant mountain range, snow caps | magenta + white |
| `7E4B` | 8×7 | 64×56 | 18 | 62 | large mountain massif, heavy snow | magenta + white |
| `872A` | 4×6 | 32×48 | 24 | 36 | tall rock wall slab, vertical strata | yellow |
| `7BB7` | 3×3 | 24×24 | 13 | 34 | single snowy peak | magenta + white |
| `7B11` | 6×3 | 48×24 | 12 | 32 | low mountain ridgeline | magenta + white |
| `7CCD` | 7×6 | 56×48 | 13 | 32 | scattered peaks, two tiers | magenta + white |
| `8047` | 7×6 | 56×48 | 13 | 32 | mountain range with cloud gaps | magenta + white |
| `81C5` | 7×7 | 56×56 | 13 | 32 | twin peaks, deep valley between | magenta + white |
| `93C4` | 9×5 | 72×40 | 6 | 27 | **water**: pool with ripple lines, walkable-look but solid | bright blue |
| `8702` | 4×1 | 32×8 | 18 | 26 | low rock ledge strip (variant A) | yellow |
| `847C` | 8×3 | 64×24 | 11 | 17 | wide horizontal rock strata shelf | yellow |
| `86DA` | 4×1 | 32×8 | 6 | 10 | low rock ledge strip (variant B, pebbles) | yellow |
| `8B80` | 4×6 | 32×48 | 4 | 6 | rock wall slab, smoother face | yellow |
| `8D3C` | 4×6 | 32×48 | 4 | 6 | rock pillar, cracked | yellow |
| `8E18` | 6×5 | 48×40 | 1 | 5 | **the hut**: thatched dome roof on stilts | yellow + red |
| `83D2` | 3×3 | 24×24 | 2 | 3 | small rock chunk (variant A) | yellow |
| `8427` | 3×3 | 24×24 | 2 | 3 | small rock chunk (variant B) | yellow |
| `8806` | 7×7 | 56×56 | 2 | 3 | rock wall with a bleached skeleton half-buried | yellow + white |
| `89C3` | 7×7 | 56×56 | 2 | 3 | rock wall with scattered bones | yellow + white |
| `8C5C` | 4×3 | 32×24 | 2 | 3 | cracked rock face (variant A) | yellow |
| `8CCC` | 4×3 | 32×24 | 2 | 3 | cracked rock face (variant B) | yellow |
| `8558` | 4×3 | 32×24 | 2 | 2 | short rock column | yellow |
| `8382` | 1×4 | 8×32 | 1 | 1 | thin rock spur, lone (used once, at room `(10,9)`) | magenta |
| `83AA` | 1×4 | 8×32 | 1 | 1 | thin rock spur / stalagmite | magenta |
| `85C8` | 6×5 | 48×40 | 1 | 1 | **the stone arch**: free-standing cave mouth | white/grey |

Art direction for scenery:

- **Fill the footprint.** Collision comes from the art (§7.3), and the map is
  a maze at footprint resolution, so every column a sprite leaves unpainted is
  a path the original walled off. Foliage should cover roughly 80 % or more of
  its footprint cells: clumps rather than single stems, palms standing in
  undergrowth, crowns reaching the edges. `MapAuditTest` fails if the interior
  drifts more than 6 points more open than the footprint maze.
- **Silhouette first.** These are maze walls. The player must read
  "impassable" instantly. Solid dark outline (index 0) on every object,
  1 px, then fill.
- **Two inks per object, max three.** The Spectrum's per-cell colour limit is
  the aesthetic; honour it even though our renderer does not enforce it.
- **Bases must be visually distinct** from canopy, because the collision
  mask is derived from the art (§7.3) and the player reads the base as the
  wall line.
- Water (`93C4`) is **solid**: a river you cannot cross. Draw it flat with
  2 px horizontal ripple dashes, no animation on the base layer; add an
  optional 4-frame ripple shimmer (`[NEW]`, flag `features.animatedWater`).
- The hut and the arch are landmarks. Make them memorable — the player will
  navigate 256 rooms by them.

Deliverable per object: `data/art/sprites/scenery_<id>.sprite.json` (§10)
plus an entry in `data/world/scenery.json`:

```json
{
  "schemaVersion": 1,
  "objects": {
    "7298": {
      "cells": { "w": 2, "h": 5 },
      "sprite": "scenery_7298",
      "collisionCells": ["..", "..", ".#", ".#", "##"],
      "biomeHint": "jungle",
      "fidelity": "recon-art-canon-footprint"
    }
  }
}
```

`collisionCells` is `h` strings of `w` chars, `#` solid, `.` passable. Omit
it to use the derive-from-art rule.

---

## 10. Sprite Forge — art as JSON, zero binaries

Because §2.4 forbids binary assets, **all artwork is text**: pixel grids of
palette-index characters, parsed at startup into `byte[]` sprites. This
guarantees every pixel in the game was authored here, reviewable in a diff.

### 10.1 Sprite file format

`data/art/sprites/<name>.sprite.json`:

```json
{
  "schemaVersion": 1,
  "name": "vale_walk_side",
  "size": { "w": 16, "h": 24 },
  "origin": { "x": 8, "y": 24 },
  "legend": {
    ".": -1, "K": 0, "R": 2, "Y": 6, "W": 7,
    "g": 4, "G": 12, "M": 11, "C": 13, "B": 9
  },
  "frames": [
    { "id": "walk0", "rows": [
      "......KKKK......",
      ".....KYYYYK.....",
      "....KYWWWWYK....",
      "....KWKWWKWK....",
      "....KWWWWWWK....",
      ".....KWRRWK.....",
      "....KKRRRRKK....",
      "...KYYKRRKYYK...",
      "...KYYKRRKYYK...",
      "....KKKRRKKK....",
      "......KRRK......",
      "......KRRK......",
      ".....KKRRKK.....",
      ".....KY..YK.....",
      "....KYY..YYK....",
      "....KY....YK....",
      "...KYY....YYK...",
      "...KY......YK...",
      "..KYY......KK...",
      "..KY............",
      ".KKK............",
      "................",
      "................",
      "................"
    ] }
  ],
  "mirror": { "walk0_l": { "from": "walk0", "flipX": true } }
}
```

Rules:

- `rows` length must equal `size.h`; every row length must equal `size.w`.
  Validated at load; a mismatch is a fatal startup error naming the file.
- `origin` is the sprite's anchor in its own pixel space. For all ground
  entities, origin is **bottom-centre** (`{ x: w/2, y: h }`). Positions in
  the simulation are the origin's position, which makes feet-based z-sorting
  and collision natural.
- `legend` maps a char to a palette index; `-1` is transparent. A char used
  in `rows` but missing from `legend` is a fatal error.
- `mirror` derives left-facing frames from right-facing ones at load — never
  duplicate mirrored pixel data in the file.
- A frame may carry `anchors`: named points in sprite pixels, e.g.
  `"anchors": { "hand": { "x": 15, "y": 9 } }`. They mirror with their frame
  (`x` becomes `w - 1 - x`), and one outside the sprite is a load-time
  `DataException`. The blade is drawn from the swing frames' `hand` (§11.6).
- The example above is a **placeholder skeleton**, not the final art.
  Replace it with real 16×24 art in milestone M2.

### 10.2 Animation definition

Animations live with the entity, not the sprite:

```json
"animations": {
  "walk_side":  { "frames": ["side_walk0","side_walk1","side_walk2","side_walk3"],
                  "ticksPerFrame": 5, "loop": true, "driver": "ticks", "gaitVariants": false },
  "swing_side": { "frames": ["side_swing0","side_swing1","side_swing2"],
                  "ticksPerFrame": 0, "loop": false, "driver": "sabrePhase", "gaitVariants": true },
  "die":        { "frames": ["die0","die1","die2","die3"],
                  "ticksPerFrame": 10, "loop": false, "driver": "ticks", "gaitVariants": false }
}
```

- **`driver: "ticks"`** — frame = ticks / `ticksPerFrame`. A held (0-tick)
  animation has exactly one frame.
- **`driver: "sabrePhase"`** — exactly three frames, windup, strike and
  recover, switched by the sabre's own phase boundaries (§11.6), never by a
  tick count. `ticksPerFrame` must be 0. A uniform tick count cannot line up
  with a 3 / 6 / 3 split: M3's first swing changed pose every 4 ticks, so the
  blade was out while the arm was still raised in the windup.
- **`gaitVariants: true`** (swing animations only) — every frame also exists as
  `<frame>_walk<g>` for each gait frame of the matching walk, e.g.
  `side_swing1_walk2`. While moving, the renderer uses the variant for the
  current gait, so a swing on the move keeps stepping instead of gliding.
- The `AnimationValidator` (§20.6) enforces all of this at load, plus mirrors
  for side-view frames and a `hand` anchor on every strike frame.

### 10.3 SpriteForgeCli

A dev tool (`wulf.tools.SpriteForgeCli`) that:

- `validate` — parses every `*.sprite.json`, checks dimensions, legend
  coverage, palette bounds, and that declared `size` matches the owning
  `scenery.json` footprint (`cells.w * 8`, `cells.h * 8`).
- `preview <name>` — prints the sprite to the terminal with ANSI 24-bit
  colour, so art can be reviewed without launching the game.
- `sheet` — dumps an ANSI contact sheet of all 41 scenery objects, for
  checking the map reads correctly as a set.
- `mask <name>` — prints the derived collision mask alongside the art.

`preview` and `sheet` are the review loop. Use them; do not add a PNG
exporter (it would tempt binaries back into the repo).

### 10.4 Font

`data/art/font/font.json` holds two original glyph sets: a **4×6** set for
the panel's dense numerics and an **8×8** set for titles. Same row/legend
format, one frame per codepoint, `"glyphs": { "A": [...], "0": [...] }`.
Cover: `A–Z`, `0–9`, and `. , : ; ! ? ' " - + * = / \ ( ) [ ] { } < > # $ ^
_ & @ | ~` plus space — 68 glyphs. The punctuation set is not decorative: JSON
Schema messages on the `DATA_ERROR` screen quote pointers and regexes, and a
missing glyph renders as `?`, which turns `^#[0-9A-Fa-f]{6}$` into noise. `©`
is omitted deliberately — it is unreadable at 3×5 and belongs to the 8×8 set.
Lowercase is optional; the painter upper-cases on draw (the period look is
all-caps).

### 10.5 Scenery Forge — where the scenery art comes from

The 41 scenery sprites are **generated**, by `tools/art/scenery_forge.py`
(Python 3, standard library only). Each object is drawn procedurally from
primitives — fronds, banded trunks, peaks, rock slabs, blobs — with a fixed
per-object seed, so every pixel is authored in this repository and nothing is
traced from any image.

- **Dev-time only.** Its output is committed, the game reads the JSON, and
  neither CI nor `mvn verify` needs Python.
- **Deterministic.** A second run reproduces all 43 output files
  byte-for-byte (checked in M2). So if regenerating changes a file you did
  not mean to change, the forge changed — find out why before committing.
- **The §9 table is load-bearing.** The forge parses footprints and subjects
  straight out of that table in this file. Reformatting it breaks the forge.
- **Regenerate and verify together:**
  ```bash
  python3 tools/art/scenery_forge.py && mvn -q verify
  ```
  It rewrites all 41 `scenery_*.sprite.json`, `art/sprites/index.json` and
  `world/scenery.json`.
- **`character_forge.py`** draws Ranger Vale the same way, from hand-authored
  stamps (hat, head, torso) and limbs stroked between joints: 61 frames — the
  swing poses standing and on every walking gait — plus 19 left-facing mirrors.
  It has no randomness at all. The blade is not in the sprite: every swing frame
  carries a `hand` anchor, and the renderer draws the blade out of it (§11.6).
- **`creature_forge.py`** draws the 13 creatures and the Wulf the same way — silhouettes
  built from ellipses, strokes and stamps, two walk frames each plus their
  left-facing mirrors — and the 2-frame death `puff`. Deliberately different
  animals from the original's: a tribesman with a shield, a hat-less chief with a
  headdress, a warthog rather than a boar, and a pale, hackled Wulf with two
  galloping frames and a howl it holds while it warns. `CreatureSpriteValidator` fails the
  load if a creature's sprite or any of `walk0`, `walk1`, `walk0_l`, `walk1_l`
  is missing.
- **Forges share `art/sprites/index.json`** and each merges only its own
  entries into it. Regenerating the scenery must never drop the player sprite,
  and vice versa.
- **Hand edits to generated files are overwritten** on the next run. To hand
  tune one object, change its drawing function — or take it out of the
  forge's work order and own its JSON by hand, and say so in the commit.
- Python bytecode (`__pycache__/`, `*.pyc`) is gitignored. The no-binaries
  gate fails on it by design; clean it up, never allowlist it.

Review art with `SpriteForgeCli sheet` / `preview` / `mask` (§10.3), or boot
the room browser: `./run.sh --room C,R` — arrows move between rooms, `[` `]`
or PgUp/PgDn step through all 256, `M` overlays the collision mask.

---

## 11. Ranger Vale (the player) — movement spec

This section is the most feel-critical in the document. Movement must match
the original's *character*: a short run-up and a glide (§11.3), grid-free, slightly faster
horizontally than vertically, and sliding along walls.

### 11.1 `data/entities/player.json`

```json
{
  "schemaVersion": 1,
  "id": "player",
  "displayName": "Ranger Vale",
  "sprite": { "size": { "w": 16, "h": 24 }, "origin": "bottom-centre" },
  "collisionBox": { "x": -5, "y": -9, "w": 10, "h": 9 },
  "speed": { "xFp": 768, "yFp": 768, "diagonalScaleFp": 218 },
  "lives": { "start": 5, "max": 9 },
  "spawn": { "invulnTicks": 100, "blinkPeriodTicks": 4,
             "insideRoomPx": { "left": 8, "right": 8, "top": 24, "bottom": 0 } },
  "death": { "animTicks": 40, "freezeTicks": 20, "keepAmulet": true },
  "sabre": {
    "windupTicks": 3, "activeTicks": 6, "recoverTicks": 3, "cooldownTicks": 6,
    "reachPx": 21, "bladePx": 14, "thicknessPx": 12, "diagonalOffsetPx": 4,
    "repelWulfPx": 24, "repelWulfStunTicks": 30
  },
  "animations": { "...": "see §10.2" }
}
```

### 11.2 Speed — **[CANON]** one clamp for both axes, **[RECON]** px/s

| Axis | Fixed-point | px/tick | px/s | time to cross a room |
|------|-------------|---------|------|----------------------|
| Horizontal | `768` | 3 | 150 | 256 px ≈ **1.7 s** |
| Vertical | `768` | 3 | 150 | 176 px ≈ **1.2 s** |

Full speed, at the top of the run-up (§11.3). The original clamps both axes
to the same 3 px a frame (`$AFC1`). Until 2026-10-01 vertical was ⅔ of
horizontal — "deliberate and required", to read as perspective — which was
ours, not the original's, and a playtest felt it as Vale being slower than
he should be. The Wulf's vertical speed rose with it, keeping its 1.27×
(§12). Absolute px/s follows from the original's main loop, which moves him every 50 Hz frame
(§12.10, §25 Q5): 3 px a frame is 3 px a tick. Until 2026-10-11 we ran him at 2 ticks a
frame, half the original's pace, and the user found him slow.

Diagonal: both components are applied, each scaled by
`diagonalScaleFp = 218` (≈ 0.85), giving ~2.55 px/tick on each axis.
A diagonal must be *slightly* faster than a cardinal in total distance (the
original rewarded diagonals) but must not be the strictly dominant way to
move. Verified in `MovementFeelTest`.

### 11.3 Inertia **[CANON]** shape, **[RECON]** timing

Rewritten 2026-09-30. This section used to say "no acceleration, no inertia",
from memory; the original has both. `GamePlay_Start` (`$AEEF`) keeps a velocity
per axis in sixteenths of a pixel per frame, and each frame:

1. a held direction adds `momentum.accel` (7) to that axis (`$AF21`);
2. the velocity is clamped to `momentum.max` (48 — 3 px a frame, `$AFC1`);
3. the feet move `velocity / momentum.step` (16) whole steps (`$B031`), so the
   speed comes in steps — standing, ⅓, ⅔, full — and below one step he stands;
4. the velocity decays by `momentum.decay` (1) toward rest (`$B047`).

So he reaches full speed in about eight frames and, let go, glides about 48
px over some 32 frames. Press the other way and he skids round. Fighting is a
different handler (`$ADD0`): the velocity is set to `momentum.fight` (32, ⅔)
in the held direction every frame — no run-up, no glide (§11.6). A secondary
source agrees: the Spectrum original "keeps him moving a bit after you lose
hold of the joystick … kind of slippery", and "slows down a notch once he
starts swishing his sabre" (<http://frgcb.blogspot.com/2017/11/sabre-wulf-ultimate-play-game-1984.html>).

Ours (`player.json → momentum`, `Simulation.move`): the same integers, run
once per original frame, which lasts `momentum.ticksPerFrame` (1) of our 50 Hz
ticks — the original runs Sabreman once each time the 50 Hz frame counter moves (§12.10). Full speed is
§11.2's per-axis speed, so the room-crossing times there hold at the top of
the run-up; the steps are ⅓ of it. The step is taken once per frame and held
for its ticks. When the feet are blocked the velocity is kept, as the
original keeps it. Diagonals still scale by `diagonalScaleFp` when both axes
are moving and neither is pressed against a wall.

- Opposing keys held simultaneously → that axis adds nothing.
- Sub-pixel remainder accumulates in the fixed-point position and is never
  rounded away between ticks.
- Respawning puts him at rest.
- The test bots steer with it as a player does — down the straight, braking
  before the turn (`wulf.tools.Steering`); `CornerWalkTest` walks to every
  corner of the map that way.

### 11.4 Facing

- Facing is one of 8 directions, updated whenever velocity is non-zero.
- Facing **persists** when velocity returns to zero (you keep looking where
  you were going). This matters: the sabre swings along `facing`.
- The sprite has three view sets — `side` (mirrored for left), `up`, `down`.
  Diagonals use the `side` set, mirrored by the X sign; if X is zero the
  diagonal is impossible, so no special case needed.

### 11.5 Walk animation cadence **[RECON]**

- 4 frames, cycling `0,1,2,3`, advancing every **3 ticks** (≈ 17 fps) while
  moving. One full cycle = 12 ticks = 0.24 s = 36 px travelled horizontally —
  roughly one stride per 36 px, which reads correctly at 16 px sprite width.
- Idle: snap to frame 0 **immediately** on stopping (not a freeze of the
  current frame). Original-style, no idle breathing animation.
- The animation clock is driven by **ticks moved**, not wall time, so slow
  and fast effects (§15) visibly change stride rate. Optional refinement
  (`[NEW]`, flag `features.distanceDrivenGait`): advance the frame every
  `7` px of distance travelled instead, so the gait stays locked to the
  ground at every speed.
- **Footsteps** (`sfx.json → cadence.footstepEveryTicks`, 6): two to a stride, on gait frames 2
  and 0 — the step and the walk count the same ticks moved and both start again when he stops.
  At 9, from when he took 2 ticks a frame, they fell twice as far apart once he ran at the
  original's pace (2026-10-11). In haste (cyan, §15.2) each tick counts
  `cadence.hasteFootstepStep` (2) towards the next step, so they come twice as often (`$BF4B`).

### 11.6 The sabre — fighting **[CANON]** shape, from the disassembly

Rewritten 2026-09-27 after a playtest said fighting moved differently from the
original, and the disassembly agreed (§25 Q11). The original has two player
handlers: walking, and fighting while fire is held (`Player Movement` `$ADD0`,
entered through `$ADBF`). Fighting:

- **The blade is out the whole time the key is down.** One constant hit box
  (`$AB0E`), no windup and no recovery. Ours: `windupTicks` 0, `activeTicks`
  8, `recoverTicks` 0, `holdRepeatTicks` 0, so held strokes chain with the
  blade never dark; a tap is one 8-tick stroke, then `cooldownTicks` (6).
- **It reaches 16 px past his touch** (`$AB0E`, 2026-10-11). The original tests a touch by the
  sprites' left edges: within 12 px either way walking (`HL` = `$0F0C`), but within 28 px in
  front while fighting (`$0F1C`) — the fighting sprite is wider by the blade. Ours: `reachPx` 21
  from the feet box's centre, 16 past its 5 px half-width (`SabreTest`). Until then it was 14,
  ours, and reached 9. The blade is still drawn `bladePx` (14) out of the hand, which stands
  about 7 px in front, so the drawn tip and the hit box end together.
- **It fences.** Every fourth frame (`FRAMES & 3`) the sprite changes to one
  of its fighting poses picked by `Rand8` (`$AE4B`). Ours is fencing, the
  whole body: each stroke is **on guard** for `fence.everyTicks` (4) — knees
  bent, sword hand at the chest, the free arm raised behind the head — then
  **lunges** for the next 4 — front leg thrown out, back leg straight, body
  sunk and leaning in, sword arm fully extended (`side_swing0` and
  `side_swing1`, drawn by `character_forge.py`; `side_swing2` is the
  recovery). The blade comes out of each frame's `hand` anchor, drawn back
  short on guard and at full reach in the lunge, its angle picked at random
  from `fence.guards` / `fence.thrusts` by a hash of the tick. Two earlier
  versions failed the playtest: a blade swinging through eight angles from a
  frozen body read as flailing, and the same blade pumping in and out still
  did not look like someone using a weapon — the body has to move. Drawing
  only: the hitbox never follows the pose, and the original's did not either.
  Up and down fence too (`up_swing*`, `down_swing*`): feet apart and the body
  sunk on guard, the free arm raised; in the lunge the legs spread wide and
  the sword arm drives down at the viewer, or up and away over the pack.
  Pointing into or out of the screen the blade is foreshortened, drawn
  `fence.towardViewerPercent` (50) of its length — a full-length line straight
  down over the legs was the "weird" of the third playtest.
- **Vale moves steadily and slower.** Fighting moves a fixed 2 px a frame in
  the held direction, against walking's top speed of 3 (`$AFC1`):
  `momentum.fight` 32 — two of the three speed steps, held, with none of
  walking's run-up or glide (§11.3).

State machine (the pose follows it: `driver: "sabrePhase"`, §10.2 — with no
windup or recovery it is the strike pose throughout):

```
tick 0..3    ACTIVE   on guard, blade drawn back, hitbox live
tick 4..7    ACTIVE   the lunge, blade out, hitbox live; kills on first overlap per creature per stroke
then         COOLDOWN 6 ticks before another stroke, unless fire is still held
```

- **A stroke on the move keeps walking**: while moving, the strike pose is drawn on
  the current gait's legs (`gaitVariants`, §10.2).
- **The blade is drawn from the hand, the hitbox lies on the ground.** The
  hitbox is projected from the feet box, on the ground plane where creatures'
  feet are; that is what collides. The visible blade comes out of the strike
  frame's `hand` anchor, at the current fencing pose rotated into the
  facing, a guard shorter than `reachPx` and a thrust its full length. Drawing it from the hitbox itself put the blade at knee
  height, and between the legs or through the pack facing down or up (found
  in M3).
- Hitbox: a rectangle `reachPx` (14) long and `thicknessPx` (12) wide,
  projected from the player's collision-box centre along `facing`. For
  diagonals, project along the dominant axis and offset by 4 px on the
  other — do not implement rotated rectangles.
- One creature may be hit **once per swing** (keep a per-swing hit set).
- The hitbox is **not** blocked by scenery. The blade sweeps over the
  foliage line.
- **Holding the fire key keeps the sabre working.** While it is held the next
  stroke begins as the last ends (`holdRepeatTicks` 0), and with no windup or
  recovery the blade is never dark. Let go and the full `cooldownTicks` (6)
  applies, so tapping is never quicker than holding. *Corrected in M7* (it had
  been edge-triggered, against the user's memory of the original), and again
  on 2026-09-27: M7 kept a windup and a recovery in every stroke, so held fire
  read as a repeated slash; the original's blade stays out.

### 11.7 Death and respawn

```
contact with any hostile while not invulnerable and not immune
  ──► DYING state, 40 ticks (4-frame animation, 10 ticks each)
        sim frozen for creatures? NO — creatures keep moving, it is a diorama
        border flashes red on ticks 0,4,8,12 (Ultimate style)
  ──► 20 ticks of black/frozen
  ──► if lives > 0: lives--, and get up WHERE HE FELL, at rest — as the original does:
        losing a life ($AA27) never touches the position. Only if that spot
        leaves the sprite cut by the room's edge (spawn.insideRoomPx, a death
        straddling a flip) is he moved, to the nearest spot he can walk to
        that keeps him whole — the wall never gives way (SpawnFinder),
        with 100 ticks of invulnerability (blink 2 on / 2 off)
        the room's creatures are re-rolled from scratch
  ──► if lives == 0: GAME_OVER
```

- **Lives are a reserve** **[CANON]** (2026-10-02, §25 Q6). `lives` counts
  the lives still to come, as the panel shows them; the one being played is
  not in it. `$AA27` runs when the fall is over: none in reserve and it is
  game over (`$AAA4`), else one comes off (`$AA37`). So `start: 5` is six
  falls, as in the original (`$B21E` writes 5). Until then ours took the life
  at the hit and ended the game at 0, one fall sooner.
- **Where he fell** (2026-10-01, from a playtest and `$AA27`). Until then he
  went back to where he had come into the room.
- **A respawn is never walled off from the way in** (found in a playtest,
  2026-09-26). The search used to count cells alone, so it would step through
  scenery: the empty top strip every room has (§25 Q14) is 16 px deep, less
  than `insideRoomPx.top` (24), so dying there put Vale in whichever pocket lay
  nearest below it — 1 069 of 7 816 ways into rooms did this, some into
  pockets with no exit at all. `SpawnFinder` now floods the walkable feet
  positions from the entry point first; `SpawnFinderTest` checks every way
  into every room. The creature placement of §12.6 starts from the same
  point, so it was placing creatures around the wrong pocket too.
- **The amulet is kept on death** (`keepAmulet: true`) **[CANON]** (§25 Q7).
  The quarters held are bits of `$970D`, the player's record; only the
  pickup writes them (`$A1E3`), and the death path `$AA27`–`$AA57` leaves the
  record's byte alone.
- Score is kept. Collected loot stays collected.
- Invulnerability does **not** protect against the room's exit-blocking
  guardian (§14.4) — you simply cannot pass.

### 11.8 What the player cannot do

No jumping, no crouching, no running, no rolling, no blocking, no throwing,
no inventory, no map screen (`[NEW]` flag `features.pauseMap` exists,
default off). The verb list is: **walk, swing, pause.** Resist every
temptation to add a verb.

---

## 12. Creature roster and AI behaviours

### 12.1 Fidelity note — read this before implementing

**Since 2026-10-10 the original's creatures are extracted and the default** (§12.8, §12.9).
What follows, §12.2–§12.6, is our invented jungle, kept as `[NEW]` `features.inventedCreatures`
(default off); where §12.2's universal rules and §12.8 disagree, the original's play §12.8's.

The original's creature table had **not** been extracted into this repo when this was written. The
roster below is tagged `[RECON]`: it reproduces the *documented composition*
of the original's jungle (roaming tribesmen, large charging beasts, small
fast vermin, fliers, the four guardians, the Wulf) with our own tuned
numbers. Every value lives in `data/entities/creatures.json` so a verified
canon table can replace it without touching code. See §25 for the research
backlog. **Do not silently "correct" these numbers from memory or from a web
search without recording the source in §25.**

### 12.2 Universal creature rules **[CANON]** in spirit

- **Touching any hostile kills the player instantly.** No health bar, no
  knockback, no damage frames. One touch, one life.
- Creatures **do not collide with each other**. They pass through freely.
- Creatures **respect scenery collision** unless `ignoresScenery: true`
  (fliers). A creature that would leave the room bounces or wraps per its
  behaviour.
- Creatures **never leave the room** they spawned in. Only the Wulf travels.
- Killable creatures die in one sabre hit unless `hp > 1`. Death is a
  2-frame puff, 12 ticks, no corpse.
- Killing a creature **does not** stop its species respawning on re-entry.
- Creature spawn positions must be at least **48 px** from the player's entry
  point, and the creature's box must fit clear of the scenery and inside the
  room. A **ground** creature must also start within one cell (8 px) of a place
  the player's feet can get to **without leaving the room** — otherwise it waits
  in a strip behind the scenery that only a neighbouring room opens onto, and
  the room is quietly empty (seen in M4 in room `7,3`). If no valid position
  exists after 24 tries, skip that spawn.
- A hit that does not kill flashes the creature white on alternate ticks for
  `hurtFlashTicks` (6). A swing damages a creature at most once.

### 12.3 The roster

Speeds are px/tick (fixed-point value in parentheses). `hp` is sabre hits to
kill; `∞` means unkillable, sabre only repels.

| id | name | size px | speed x (fp) | speed y (fp) | hp | behaviour (§12.5) | score | biomes | notes |
|----|------|---------|--------------|--------------|----|-------------------|-------|--------|-------|
| `tribesman` | Tribesman | 16×24 | 1.25 (320) | 0.85 (218) | 1 | `LINEAR_BOUNCE` | 150 | all but water | the bread-and-butter enemy; 2–4 per room |
| `spearman` | Spear Tribesman | 16×24 | 1.00 (256) | 0.70 (179) | 1 | `PATROL_THROW` | 250 | jungle, swamp, hut | throws a spear projectile every 90 ticks along its facing |
| `chief` | Village Chief | 16×28 | 1.40 (358) | 0.95 (243) | 2 | `CHASE_AXIS` | 400 | hut rooms only | 1 per hut room, 35% chance |
| `scorpion` | Scorpion | 12×10 | 2.00 (512) | 1.40 (358) | 1 | `WANDER_ERRATIC` | 200 | bonefields | fast, twitchy, low to the ground |
| `snake` | Snake | 20×8 | 0.90 (230) | 0.60 (154) | 1 | `WALL_FOLLOW` | 150 | jungle, swamp, water | hugs the foliage line, hard to see |
| `spider` | Spider | 12×12 | 0.60 (154) | 1.50 (384) | 1 | `DROP_THREAD` | 200 | jungle, bonefields | hangs at top of room, drops when player passes under; `ignoresScenery: true` — its thread passes through the canopy |
| `bat` | Bat | 14×10 | 1.60 (410) | 1.10 (282) | 1 | `SINE_FLIGHT` | 300 | bonefields | `ignoresScenery: true` |
| `frog` | Jungle Toad | 12×12 | burst 2.50 (640) | burst 1.70 (435) | 1 | `HOP` | 100 | swamp, water | 8 ticks hop, 20 ticks rest |
| `vulture` | Vulture | 20×14 | 1.80 (461) | 1.20 (307) | 1 | `CHASE_AXIS` | 350 | mountain, bonefields | `ignoresScenery: true` |
| `boar` | Warthog | 22×16 | 1.40 (358) | 1.00 (256) | 2 | `AMBUSH_BURST` | 400 | jungle | idles, then charges at 3.0 (768) for 40 ticks |
| `rhino` | Rhino | 28×20 | 1.20 (307) | 0.85 (218) | 3 | `CHASE_DIRECT` slow turn | 500 | jungle, mountain | turn rate limited to 1 direction step / 12 ticks |
| `hippo` | Hippo | 30×22 | 0.80 (205) | 0.55 (141) | 3 | `LINEAR_BOUNCE` | 450 | swamp, water | huge hitbox, corridors become lethal |
| `wildebeest` | Wildebeest | 24×18 | 1.70 (435) | 1.20 (307) | 1 | `HERD_BOUNCE` | 250 | mountain, jungle | spawns as a herd of 3, shared direction |
| `wulf` | The Wulf | 32×22 | 3.80 (972) | 3.80 (972) | ∞ | `CHASE_DIRECT` | — | anywhere | §13 |
| `guardian_*` | the four guardians | 32×28 | 2.20 (564) | 1.60 (410) | ∞ | `GUARD_ORBIT` | — | lair rooms | §14; arrives in M6 |
| `cave_guardian` | Keeper of the Arch | 24×32 | 0 | 0 | ∞ | `BLOCK_STATIC` | — | exit room | §14.4; arrives in M6 |

The table's biome column is descriptive; `room_entities.json` (§12.6) is what
places creatures. The first 13 rows ship in `creatures.json` (M4); the Wulf is
`wulf.json` (M5) and the guardians `guardians.json` (M6).

### 12.4 `data/entities/creatures.json` shape

```json
{
  "schemaVersion": 1,
  "fidelity": "recon",
  "diagonalScaleFp": 218,
  "puffTicks": 12,
  "hurtFlashTicks": 6,
  "walkTicksPerFrame": 6,
  "creatures": [
    {
      "id": "tribesman",
      "displayName": "Tribesman",
      "sprite": "creature_tribesman",
      "size": { "w": 16, "h": 24 },
      "collisionBox": { "x": -6, "y": -10, "w": 12, "h": 10 },
      "speed": { "xFp": 320, "yFp": 218 },
      "hp": 1,
      "score": 150,
      "ignoresScenery": false,
      "behaviour": { "kind": "LINEAR_BOUNCE", "params": { "reverseChancePer10k": 40 } }
    }
  ],
  "projectiles": [
    { "id": "spear", "displayName": "Spear", "speedFp": 640, "lengthPx": 12,
      "collisionBox": { "x": -3, "y": -3, "w": 6, "h": 6 }, "score": 50, "maxTicks": 150 }
  ]
}
```

**Every param is an integer**, named for its unit: `…Fp` is 8.8 fixed point,
`…Px` pixels, `…Ticks` ticks, `…Per10k` a chance or ratio out of 10 000, and
`…Cos10k` a cosine × 10 000. No decimal ever reaches the loader, so there is
nothing to convert. `BehaviourCatalog.validate` rejects an unknown `kind`, a
missing param and an unexpected one, each with its JSON pointer
(`/creatures/3/behaviour/params/rerollMinTicks`).

The sprite's frames are `walk0`/`walk1` and their `_l` mirrors; every creature
steps through them at `walkTicksPerFrame` while it moves, and fliers flap on the
global clock even when still. Per-creature animations and sfx are not in the
file — they arrive with audio (M8) if a species earns them.

### 12.5 Behaviour catalogue — one class each in `sim/ai/`

| kind | implementation |
|------|----------------|
| `LINEAR_BOUNCE` | Pick a random 8-direction at spawn. Move. On scenery or room-edge block, reflect the blocked axis. Random reverse with `reverseChancePer10k` per tick. The original's signature "patrolling, mindless" motion. |
| `PATROL_THROW` | `LINEAR_BOUNCE` plus: every `throwPeriodTicks`, if the player is within `throwRangePx` (160) and inside the facing cone — `dot(facing, toPlayer) ≥ throwConeCos10k × |toPlayer| / 10 000`, compared squared in integers; 7071 is 45° — spawn a `spear` projectile (speed 2.5 px/tick, dies on scenery or after `maxTicks`, kills player, killable by sabre for 50 pts). |
| `CHASE_AXIS` | Each tick, move at full speed on the axis with the **larger** player delta, and at half speed on the other. Produces determined-but-dumb pursuit that corners badly. |
| `CHASE_DIRECT` | Move toward the player's position along the exact 8-direction that best matches the delta vector. `turnCooldownTicks` limits direction changes. Blocked by scenery → try the two neighbouring directions, then stall for 8 ticks. Never pathfind. No A*. The stupidity is the design. |
| `WANDER_ERRATIC` | Re-roll direction every `4 + rng(8)` ticks. Never targets the player. Lethal by accident. |
| `WALL_FOLLOW` | Keep a "wall side" (left/right). **Following**: go straight while the wall is beside you; blocked ahead, turn away from the wall side. When the wall ends, turn **once** toward it and go **loose**: straight ahead until a wall is touched again, then follow it. No params. (Turning toward the wall every tick while loose spun the snake in place at every outside corner — found in M4.) |
| `DROP_THREAD` | Anchored at a top-of-room cell. Idle until the player's X is within 24 px, then descend at `speed.y` up to `dropMaxPx` (120), pause 30 ticks, ascend at half speed. Draw a 1 px thread to the anchor. |
| `SINE_FLIGHT` | Straight-line base course at `speed.x`; Y offset is a fixed-point sine lookup table (`engine/SinTable.java`, 256 entries, 8.8) with `amplitudePx` 28 and `periodTicks` 70. Ignores scenery; bounces only off room edges. |
| `HOP` | Alternate `BURST` (move at burst speed, `hopTicks`) and `REST` (still, `restTicks`). Direction re-rolled at the start of each burst, biased 60% toward the player. |
| `AMBUSH_BURST` | Idle (still, facing the player) until the player enters `triggerPx` (96) **and** has line-of-cells (no solid cell on the straight cell path). Then charge along that direction at `chargeSpeedFp` for `chargeTicks`, ignoring further input. On hitting scenery, stun 24 ticks. Then idle again. |
| `HERD_BOUNCE` | A group entity: one shared direction, N members in a loose triangle offset by ±20 px. Reflects as one. If one member dies the others continue. |
| `GUARD_ORBIT` | Walk the ring round a fixed anchor — the amulet pedestal — at `orbitRadiusPx` (32, §14.3) and `orbitTicksPerRev` (240); scenery still stops it. Within `lungePx` (40), break off and charge the player for `lungeTicks` (90), then back to the ring. Unkillable: a swing repels and stuns it (§14.3). |
| `BLOCK_STATIC` | Never moves. Occupies its collision box as an impassable, lethal volume. |

Every behaviour class implements:

```java
public interface Behaviour {
    List<String> params();                                    // exactly the params it needs
    default int preferredLocalYPx(Species s, int proposedY)   // spiders hang from the top
    default void spawn(Creature self, SimContext ctx, Rng rng) // starting direction, timers
    void tick(Creature self, SimContext ctx);                 // mutates self, reads ctx
}
```

Behaviours are stateless singletons, one per species slot, built by
`BehaviourCatalog`. Per-creature state lives on `Creature`: `timer`, `phase`,
`aux` and an `anchor`. `SimContext` exposes the sim RNG, the tick, whether the
player is alive and where, the room origin, and movement through the §7.4
resolver — `move`, `step` (returns `BLOCKED_X | BLOCKED_Y`), `probe`,
`lineClear` over cells — plus `throwSpear`. It must not expose the renderer,
audio, wall time, or other creatures. `SimPurityTest` scans `sim/ai/` too.

### 12.6 Room population — `data/world/room_entities.json`

Two-layer system:

```json
{
  "schemaVersion": 1,
  "fidelity": "recon",
  "spawn": { "minDistanceFromEntryPx": 48, "attempts": 24, "visitSeedCap": 8 },
  "biomeMarkers": { "hut": "8E18", "water": "93C4", "swamp": "8F2A" },
  "authored": {
    "8,10": { "note": "the start room is always safe", "creatures": [] }
  },
  "biomes": {
    "jungle":     { "budget": { "min": 2, "max": 4 }, "weights": { "tribesman": 40, "spearman": 15, "snake": 12, "spider": 10, "boar": 10, "rhino": 7, "wildebeest": 6 }, "extras": [] },
    "swamp":      { "budget": { "min": 2, "max": 4 }, "weights": { "hippo": 25, "frog": 25, "snake": 20, "tribesman": 20, "spearman": 10 }, "extras": [] },
    "mountain":   { "budget": { "min": 2, "max": 3 }, "weights": { "vulture": 30, "wildebeest": 25, "rhino": 20, "tribesman": 25 }, "extras": [] },
    "bonefields": { "budget": { "min": 3, "max": 5 }, "weights": { "scorpion": 30, "bat": 25, "spider": 20, "tribesman": 15, "vulture": 10 }, "extras": [] },
    "hut":        { "budget": { "min": 3, "max": 5 }, "weights": { "tribesman": 45, "spearman": 30 }, "extras": [ { "id": "chief", "chancePer10k": 3500 } ] },
    "water":      { "budget": { "min": 1, "max": 2 }, "weights": { "frog": 50, "hippo": 30, "snake": 20 } , "extras": [] }
  }
}
```

How a room fills (`BiomePopulator`, M4):

1. The room's RNG is `Rng.hash(runSeed, col, row, min(visit, visitSeedCap))`,
   so the same run replays exactly and the first eight visits to a room differ.
2. A lair or the way out is left alone entirely: a lair is one clean puzzle (§14.3),
   and its guardian and quarter are placed by the quest, not the populator.
3. An `authored` room places exactly its list.
4. Otherwise roll `count = min(maxRoomBudget, budget.min + rng(span) + bonus)`
   picks from `weights`, walked in file order; a herd species fills one slot
   with its whole herd. Then each `extras` entry is rolled once — this is how
   "1 chief per hut room, 35%" is expressed.
5. Each pick tries up to `attempts` positions against the §12.2 rules.
6. Rooms repopulate on every entry and after every respawn (§7.6, §11.7).

`CreatureRefValidator` fails the load on a marker that is not a scenery id, a
biome the resolver can produce but the file lacks, `min > max`, an unknown
species in weights, extras or authored lists, or an authored room or position
out of range.

Biome per room is derived from the room's scenery **[CANON]** composition
(§8.2) by `BiomeResolver`; `rooms.json` overrides are not needed yet:

```
contains 8E18 (hut)                        -> "hut"
contains 93C4 (water)                      -> "water"
majority of cells from yellow rock objects -> "bonefields"
majority of cells from mountain objects    -> "mountain"
contains 8F2A reeds and no mountains       -> "swamp"
otherwise                                  -> "jungle"
```

Measured over the real map in M4 (`BiomeResolverTest`): of the 196 interior
rooms, jungle 72, swamp 65, hut 21, bonefields 20, mountain 16, water 2; of the
60 boundary rooms, swamp 30, jungle 25, water 5. Every biome is in use.

Difficulty ramp **[RECON]**: the room's creature budget gains `+1` when the
player holds 2 amulet pieces and `+1` again at 4 pieces (cap 6). Store as
`game.json → difficulty.budgetBonusByPieces: [0,0,1,1,2]`.

---

### 12.7 The flame **[CANON]** — `data/entities/flame.json`

From the disassembly (2026-10-08, §25 Q1), built at the user's choice as the
default. The original punishes a player who stays put: linger in a room and a
flame comes for you.

```json
{ "lingerTicks": 256, "lifeTicks": 512, "steerTicks": 128, "homingPer10k": 5000,
  "speed": { "xFp": 512, "yFp": 512 }, "spawnInsetPx": 24, "collisionBox": { "x": -6, "y": -12, "w": 12, "h": 12 } }
```

- **When** (`$9ABC`): every room entered (`$B164`) and every getting up after
  a fall (`$AA52`) sets a wait, `$96B4` = `$80` (128 passes of the actor table,
  256 of our ticks at 2 a pass, §12.10); it counts down while Vale is alive and no flame
  burns. At zero a flame appears. The wait is **not** set again while he stays,
  so when a flame goes out the next comes at once.
- **Where** (`$9AE9`): in line with him on one axis — straight above or
  below him at a random height, or level with him at a random x — never on
  him and never in the scenery; if the spot will not do, it tries again next tick.
- **How it moves** (`$9D91`, `$9E0E`): at 2 px a tick (the original's 4 px an
  update, §12.10 — slower than Vale's 3). Every `steerTicks` it takes a new course: half
  the time a diagonal straight at him, half one of eight at random. Whatever
  stops it, it glances off (the original bounces off the room's edges and
  re-steers at scenery; ours reflects at both, `[RECON]`).
- **How long** it burns: `lifeTicks` (256 updates), then it goes out in a puff.
  It goes out too when he falls (`$9DA2`), and is gone when he leaves the room.
- **Its touch costs a life**, unless he wears a flower's colour (§15.2,
  `$9DE5`). **No blade can touch it**: it is a creature with `hp` 0, which the
  sabre skips — it can only be outrun or left behind. No score.
- **Ours**: a room creature of behaviour `FLAME`, made by the simulation (not
  the room's population) so drawing, hashing and leaving the room come free;
  the art is ours (`creature_forge.py → flame`, two flickering frames). The
  wait is in the state hash. `FlameTest`.

### 12.8 The original's creatures **[CANON]** — `data/entities/jungle.json`

From the disassembly (2026-10-10, §25 Q1). **The default since 2026-10-10**: the small ones
here, the big ones in §12.9. §12.3–§12.6's invented jungle stays as `[NEW]`
`features.inventedCreatures` (default off) — the roster, the biome populator and all.

**The swarm.** The original fills no room on entry. Once an update while Vale is on his feet
(`$9A3C`), if his room holds fewer than **3** (`$96BE`) — puffs and bursts counted — and all
rooms fewer than **6** (the slots at `$9792`), a puff comes at a random spot (`$9B0D`: `$30` +
0..127 each way; ours `swarm.area`, room-local feet) clear of him (`$AB01`) and of the scenery
(`$B81C`), or none this update. So a room you stand in keeps topping up as you kill.

- **Its colour** is one of red, magenta, cyan, yellow (`$9AB8`), kept for life. Ours draws
  each kind in its own colours instead — a green parrot with a red head, a grey rat, a
  black-and-white skunk — since the user found one ink a creature did not look good here
  (2026-10-12, `[NEW]`); its colour still tints the puff it forms from. The original's look is
  `[NEW]` `features.inkedCreatures` (default off): the sprite inked in that colour, keeping
  only its black outline. The drawings (`tools/art/creature_forge.py`) read in either.
- **It forms** over 6 updates (sprites `$01`–`$06`, 12 ticks): harmless, but a blade can take
  it (`$A3B7`), scoring 165–185 by the frame.
- **What it becomes** (`$A3E0`, `$A41A`): a roll of 0..15 into spider, scorpion, bat, parrot,
  monkey, lizard, snake, spider, rat, bird, frog, mosquito, skunk, flea, earwig, rat; a roll at
  or past `8 + 2 × quarters held` folds into the first eight (`AND 7`). So the last eight open
  two at a time as the amulet fills, and with it whole every kind is out.
- **How it moves** — two ways:
  - `DART` (`$A54F`, `$A5CD`), all but three: a straight line at a random speed per axis,
    3–6 px an update each way (`$A601`: ±`$30`..`$60`, ÷16; ours 1.5–3 px a tick, §12.10), so always
    slanting; for up to 256 updates, or until the scenery stops it dead (`$AAED`), when it
    stands 8–15 updates and sets off again. A room edge turns it about (`$A44B`).
  - `HOME_IN` (`$A4D7`), the bat, the lizard and the snake: each axis leans towards Vale by
    `$08` an update up to `$30` (3 px), stopping dead when level with him — so it swings in,
    overshoots and comes round. Edges turn it; scenery it slides along.
- **Its touch** (`$AB36`), only on a white Vale (§15.2): it **bursts either way**. Walking,
  it takes a life with it. Fencing, one on the side he faces is simply struck (`$AB68`); one
  behind him still takes him.
- **Its score** (`$A4B3`): 165 + 20 for scorpion, parrot, lizard, bird, mosquito and flea (bit
  2 of the sprite), + 10 if it faced right as it burst (bit 1). **Every burst scores** —
  blade, touch, or his fall.
- **When he falls**, or the yellow flower sits him down, every small creature in the room
  bursts (`$A54F` sees him not on his feet), and none come for 16 updates after he gets up
  (`$96B1`, `$AA4D`).
- **Left behind**, it waits where it was: walk back within 64 updates all told (`$A493`,
  `IX+$08` never reset) and it is still there; it keeps its slot meanwhile, so leaving a full
  room and coming straight back brings back the same three.
- **Ours**: the species are ordinary roster entries (`Content.originalCreatures()`, with
  `creatures.json`'s shared puff and gait numbers); the swarm is `SwarmRules`, run by
  `Simulation.tickSwarm`; the art is ours (`creature_forge.py → vermin_*`, two tones for the
  ink). `[RECON]`: the spot's mapping to our feet, the box against him and the scenery (ours
  14×10, the original tests `$1313` and `$1717`), and one puff struck in its sixth update.
  `SwarmTest`.

### 12.9 The original's big beasts **[CANON]** — `jungle.json → beasts`

From the disassembly (2026-10-10). The actor table (`$AC77`, copied to `$9702` by a new game)
sets them down: **four rhinos** — two in (8,9), one in (7,0), one in (3,15), all magenta;
**three tribesmen** — (7,10) red, (4,4) magenta, (2,14) cyan; and **a sleeper** — awake, a
yellow hippo heading west in (3,8). Unlike the swarm they live on the whole map: every update,
wherever they are, they move (`$AFE8`), crossing from room to room as Vale does, the map's ends
joined (`$B193`, `$B12F`). **Nothing kills them**: a blade only pushes. Each keeps its colour,
and ours inks its sprite in it. `jungle.json → beasts.placed` keeps the table's own numbers (x
the sprite's left, y its feet from the top of the screen); `Content.beastRules()` turns them into
ours.

- **The rhino** (`$AD07`) paces its row at 3 px an update (`±$30`; ours 1.5 px a tick, §12.10), and
  when the scenery stops it dead (`$AAED`) it turns about at that pace again (`$AD20`). Running
  into a walking Vale it takes a life and turns about (`$ADA2`). Met with the blade, facing it,
  it is **sent off the way the blade points at 6 px an update** (`$AD69`) until something stops
  it — then back to its pace.
- **The tribesman** (`$AB74`) walks a slant, one of ±2 or ±3 px each way (`$AC24`), and picks a
  new one whenever stopped dead (`$ABDE`). Met with the blade he is pushed back at 4 px
  (`$ABAB`, `±$40`); walked into, he takes a life all the same, and is pushed. **He follows the
  player** (`$A630`): two rows or more from him, he is set down a row above or below (at random;
  his own row at the map's edge), in his block of four columns but at his own column of it, at
  the room's **spot** — never in his room. And **level with an amulet quarter still lying** (on
  its map column and within 16 px of it across, or on its row and within 16 px up and down,
  `$9D41`) he **flashes yellow and calls** (`$BF3D`, `$ABCE`): the original's own hint.
- **The sleeper** (`$9EB3`, `$9E72`) lies asleep and follows the player as a tribesman does;
  each time it is set down it is a hippo or a warthog, at random (`$9F06`). Touched it wakes,
  turns about, stands 15 updates — deadly to touch — then charges at 2 px an update (`$E0`)
  until the scenery stops it, and sleeps there (`$9E92`). A blade turns it as it does a rhino.
  With Vale not on his feet, any touch at all wakes it (`$9F01`).
- **The spots** (`$D6A6`, through `$A7D4`): one per room type, the table the orchids grow at —
  48 pairs in `beasts.spots`.
- **Touch** (`$AB36`, as §12.8): only a white Vale on his feet, in its room.
- **Ours**: `Beast` and `BeastRules`, run by `Simulation.tickBeasts`; the art is the invented
  roster's rhino, tribesman, hippo and warthog, in their own colours (inked in the actor
  table's with `features.inkedCreatures`, §12.8), with a `sleep` frame for the last two; a
  tribesman level with a quarter turns all yellow either way, for that is his call.
  `[RECON]`: the room spot's x taken as the sprite's left (ours adds half its width); crossing
  south keeps x (the original sets the room type's entry x, `$6036`); the boxes; and the shove's
  direction from our eight-way facing. `BeastTest`.

### 12.10 How often the original's actors move **[RECON]**

The main loop (`$99AA`–`$9A1D`) runs the actor table (`$970E`–`$982E`) one actor at a time,
and whenever the 50 Hz frame counter (`FRAMES`) has moved on it breaks off to run Sabreman
(`$9702`) once, then picks up where it was. A whole pass of the table — every creature, beast,
the Wulf, the flame, each moving and redrawing itself — ends at `$9A1D`, which also runs the
swarm (`$9A3C`) and the flame's wait (`$9ABC`). So **Vale moves every frame, everything else
once a pass**, and a pass with a few sprites to draw takes about two frames.

Ours is 50 Hz too, so **Vale takes one tick a frame** (`player.json → momentum.ticksPerFrame`
1: up to 3 px a tick) and **the actors one update every 2 ticks**: every speed in `jungle.json`,
`flame.json` and `wulf.json → patrol` is the original's per-update figure ÷ 2 (a 3 px update
is 1.5 px a tick), every count of updates × 2 (the swarm's `everyTicks`, `appearTicks`,
`lingerTicks`, `quietAfterGettingUpTicks`; a dart's run and pause; the flame's wait, life and
steering; the Wulf's sit and rise), and the homing creatures' lean ÷ 4 (a speed per tick gained
per tick). The original's pass grows with what is on screen; ours is fixed. Timers Sabreman
keeps — a flower's colour (§15.2) — count his frames, one tick each.

History, 2026-10-11: we had counted 2 ticks for Vale's frame and for an actor's update alike,
and the user found the jungle twice too fast for him. The first fix slowed the actors to 4
ticks an update; the user then found Vale himself slow, and he was — running at half the
original's pace all along. Now both are at the original's: the ratio the user approved, at
twice the speed. Our own `[RECON]` movers that were tuned against Vale (the invented Wulf, §13;
the lair guardians, §14) were doubled with him, to keep their ratios.

## 13. The Wulf

The pursuing beast is the game's identity. Get this wrong and nothing else
matters. In code and in player-facing text it is **the Wulf** — never the
original creature's name (§2.1, rule 4).

**Two Wulfs, one at a time** (2026-10-03, §25 Q9). By default it is the
original's: **one Wulf prowling the bottom row of the map**, §13.8 **[CANON]**.
The Wulf that appears anywhere after a warning and follows the player between
rooms — §13.1 to §13.7, built in M5 before the disassembly was read — is
**[NEW]**, behind `game.json → features.roamingWulf` (default **off**), kept by
the user's decision. `Content.wulfRules()` picks one: `WulfRules.patrol` or
`WulfRules.of`.

### 13.1 Design intent (the roaming Wulf, [NEW])

It is **not** an enemy you fight. It is weather. It arrives without warning,
it is faster than you, it cannot be killed, it follows you between rooms, and
the only correct response is to run and to use the terrain. Every design
decision below serves "oh no, not now".

### 13.2 `data/entities/wulf.json`

```json
{
  "schemaVersion": 1,
  "fidelity": "recon",
  "id": "wulf",
  "displayName": "The Wulf",
  "sprite": "creature_wulf",
  "size": { "w": 32, "h": 22 },
  "collisionBox": { "x": -13, "y": -9, "w": 26, "h": 9 },
  "speed": { "xFp": 972, "yFp": 972 },
  "appearance": {
    "baseChancePer10k": 1200,
    "chancePerAmuletPiecePer10k": 400,
    "chancePerQuietRoomPer10k": 200,
    "quietRoomsCap": 10,
    "rollEveryTicks": 200,
    "minTicksBetweenAppearances": 400,
    "graceTicksAfterPlayerDeath": 250,
    "minDistancePx": 72,
    "neverInRooms": ["start", "lair", "exit"],
    "warningTicks": 40,
    "warningFlashPeriodTicks": 8,
    "warningFlashOnTicks": 2
  },
  "pursuit": {
    "turnCooldownTicks": 6,
    "stallTicks": 10,
    "giveUpTicks": 900,
    "giveUpRoomDistance": 3,
    "arrivalClearancePx": 32,
    "leaveMaxTicks": 150
  },
  "parry": { "borderFlashTicks": 2 },
  "patrol": {
    "fidelity": "canon",
    "room": "6,15",
    "xPx": 128,
    "yPx": 48,
    "heading": -1,
    "speedFp": 512,
    "risenSpeedFp": 768,
    "sitReleaseTicks": 30,
    "riseTicks": 16
  },
  "audio": { "warning": "wulf_howl", "loop": "wulf_growl", "parry": "wulf_parry", "loopSilentAtPx": 300 }
}
```

`patrol` is the original's Wulf (§13.8); everything else in the file but the
sprite, the box and the audio is the roaming one's. Integers only, like every file the simulation reads. Three Wulf numbers live
elsewhere, where their siblings are: the parry's push and stun are the sabre's
(`player.json → sabre.repelWulfPx` 24, `repelWulfStunTicks` 30), the arrival
delay is the flip's (`game.json → transition.wulfArrivalDelayTicks` 12), and an
escape's score is loot (`loot.json → events.wulfEvaded` 250). `WulfValidator`
checks the sprite has `walk0`, `walk1`, `howl` and their `_l` mirrors, that a
warning flash fits its period, and that the box fits the sprite. `neverInRooms`
resolves `start` now; `lair` and `exit` resolve when landmarks arrive (M6).

The Wulf's body is an ordinary `Creature` steered by `CHASE_DIRECT` — the rhino's
behaviour, same collision, same turning — carried in `Simulation` rather than in
the room's creature list, so a flip never discards it.

### 13.3 Appearance algorithm

States: `ABSENT → WARNING → PURSUE ⇄ ARRIVING`, and `PURSUE → LEAVING → ABSENT`.

```
roll — on entering a room (quietRooms += 1 first, capped), and every rollEveryTicks in one:
    skip if the Wulf is about, the player is not ALIVE, it left fewer than
         minTicksBetweenAppearances ago, the player respawned fewer than
         graceTicksAfterPlayerDeath ago, or the room is in neverInRooms
    chance per 10 000 = baseChancePer10k
                      + chancePerAmuletPiecePer10k × pieces held
                      + chancePerQuietRoomPer10k × quietRooms
    if simRng.chance(chance): appear

appear:
    the three edges the player's feet-box centre is NOT nearest (ties go W, E, N, S)
    from a random one of them, at a random point along it, searching outward in 4 px steps,
    seat the Wulf's box just inside the edge where it is
        clear of the scenery (the room's edges are walls),
        within a cell of feet the player can reach without leaving the room (§12.2),
        at least minDistancePx from the player, centre to centre on either axis
    no seat on any of the three: no appearance this roll (the RNG draws still happened)
    WARNING: standing still in its howl frame for warningTicks; the border
             strobes bright red on stateTick % warningFlashPeriodTicks < warningFlashOnTicks
             (ticks 0,1, 8,9, … 32,33); quietRooms = 0
    then PURSUE
```

The 40-tick warning is essential and non-negotiable: the player must always
get 0.8 s to react. Without it the Wulf feels unfair instead of frightening. It
is already lethal to touch while it warns, and a swing already parries it.

**Refuges.** The Wulf's box is 26 px wide. Measured in M5, 13 of the 196
interior rooms (`7,3` among them) have no edge it can seat at — their openings
are too narrow for it — so it never appears in them, and a chase into one
leaves it waiting at the door until it gives up. Terrain is the player's weapon;
this is that, not a bug.

The dev key (`--dev`, **H** in the modern profile) calls `summonWulf()`: an
appearance now, skipping the chance, the cooldown, the grace and `neverInRooms`,
but never the seat rules.

### 13.4 Pursuit

- `CHASE_DIRECT` with `turnCooldownTicks: 6` — it commits to a direction
  briefly, which is what lets a skilled player juke it around foliage. Blocked,
  it tries the two neighbouring directions, then stalls for `stallTicks`.
- Blocked by scenery like anything else. It does **not** pathfind. Terrain is
  the player's weapon.
- **Follows through room flips.** When the player flips while it warns, chases
  or is still arriving: if the new room is `giveUpRoomDistance` (3) or more rooms
  from where it appeared (Chebyshev), the chase is over — the player escaped.
  It also never follows into a room it never appears in (§13.3's `neverInRooms`:
  the start room, the lairs, the way out) — the chase ends at that door, unscored,
  so a sanctuary cannot be farmed for escapes. Otherwise it is `ARRIVING`: off screen through the flip's hitch plus
  `wulfArrivalDelayTicks` (12) — a beat of false hope — then it comes in by the
  edge the player came through, where it was along that edge (or, if it was
  still arriving from an earlier flip, on the player's line), **at least
  `arrivalClearancePx` (32) from the player**, searching outward along the edge.
  With no such spot yet, it waits at the door and tries every tick.
  *Found in M5:* without the clearance it arrived on top of a player just through
  the edge — the escape forge's bot died that way 1 159 times in 12 000 runs,
  a death with no chance to act.
- **Gives up** after `giveUpTicks` (900 = 18 s) counted from the end of the
  warning, arrivals included: it turns for the nearest edge and runs off it
  (`LEAVING`) — the scenery still stops it, the room's edge does not — and is
  gone once its box has left the room, or after `leaveMaxTicks` (150). Exit is a
  run off an edge, not a despawn in place. It is still lethal while it leaves.
- An escape — given up, or outrun by distance — scores `wulfEvaded` (250) once
  and counts in `Wulf.evasions()`.
- **Killing the player** ends the chase: it stands over the fallen player
  through the death animation and is gone at the respawn, which starts the
  `graceTicksAfterPlayerDeath` (250) grace.

### 13.5 The sabre against the Wulf

A connecting swing does **not** kill it. Once per swing, it:

- pushes it back `repelWulfPx` (24 px) along the player's facing (diagonals
  scaled like movement), through the collision resolver,
- stuns it for `repelWulfStunTicks` (30) — `CHASE_DIRECT`'s stall counter —
  flashing white on alternate ticks,
- awards **0 points**,
- flashes the border bright white for `borderFlashTicks` (2),
- counts in `Wulf.parries()`; the `wulf_parry` sound plays from M8.

This gives the player exactly one tool: buy 0.6 s, then run. Do not add a
"3 parries kills it" mechanic.

### 13.6 Audio presence

A low growl loop whose volume scales with distance to the player — the
player should hear it before the room even settles — and the howl on
`WARNING`. The names are in `wulf.json → audio`; `loopSilentAtPx` (300) maps
0..300 px to gain 1.0..0.0. Nothing plays until the synthesiser lands (M8).

### 13.7 Where it is checked

`WulfTest` pins every rule of the roaming Wulf on hand-built worlds — a corridor where only
one edge fits, and a pen whose 16 px door lets the player through but never the
Wulf — plus the shipped chance (about one first room entry in seven, measured
over 2 000 seeds). `DeterminismTest` runs it; `replays/wulf_escape.json`
(§22.6) is a chase survived across four rooms, recorded with `"features":
{ "roamingWulf": true }` so it keeps playing that rule.

### 13.8 The original's Wulf **[CANON]** — the default

Read from the disassembly (§25 Q9). One Wulf, one row, all game:

- **Where.** A new game copies the actor table (`$AC77`) into place: one Wulf,
  in **room (6,15)** at local (128, 48) (the table's x 112 is the sprite's
  left edge), heading **west**. Nothing else ever makes a Wulf.
- **One row.** It moves only along x. Past the east end of the map it comes in
  at the west end, and the other way about (`$B19F`, `$B187`, `$B193`); it
  never changes row. Ours collides with the joined world, so it turns where
  the map's own scenery closes the row: on the true map it runs (2,15) to
  (15,15). (The original checks only the room it is in, so it can step into
  the next room's edge before turning.)
- **Always moving**, in its own room whether the player is there or not, while
  the player is dying too: **2 px a tick** (`speedFp` 512 — the original's 4
  px an update, `IX+$06` = ±`$40` ÷ 16, at our 2 ticks an update, §12.10). It is
  slower than Vale's 3. Blocked by scenery, it turns back (`$AAED`, `$ADB3`).
- **It turns towards the player once** (`$A922`): when the player comes onto
  its row it heads for their room — east or west by room, or within its own
  room by which side of it they are. Then it keeps that heading, turning only
  at scenery, until the player has left the row and come back.
- **A touch costs a life** (`$A880`), as any creature's does; the sabre does
  not block it (small fry's touch it does, from the front, `$AB3D`). Only the
  green flower's immunity (§15.2) keeps it off. After a hit on a player who was
  not fighting, it turns away (`$A8AC`).
- **Held off, it sits** (`$A8B2`, `$A8E4`). A player fighting — the blade out,
  §11.6 — and facing the Wulf as it comes at him, anywhere in the room, makes
  it **stop and sit** (`SITTING`, its howl frame). It sits for as long as the
  blade is held towards it (`$A8F5`), then `sitReleaseTicks` (30) more, then
  **gets up** (`RISING`, `riseTicks` 16, moving at its old speed) and runs on
  at `risenSpeedFp` (768, 3 px a tick; `$A964` adds `$20`). Having sat once it
  will not sit again, nor turn towards the player, until the player has left
  its row (`IX+$02` = `$10`; `Wulf.Latch.SPENT`).
- **No warning, no chase, no giving up.** It is simply there when you walk
  into its room. Its growl loops while it is in the player's room (§13.6);
  sitting down, it howls. The dev key (H) does nothing to it.
- **The hash** carries its room, heading, speed and latch.

`ProwlerTest` pins each rule on open and walled worlds; `DeterminismTest`
runs beside it on its row; `HeadlessSimTest` checks it never leaves the row.

---

## 14. Guardians, the amulet, the exit

### 14.1 The quest **[CANON]** structure

Four amulet pieces are held in four lair rooms, which the game picks afresh at
the start of every game from the original's eight sets (§14.2). Each is
watched by an unkillable guardian beast. With all four pieces the player can
pass the Keeper of the Arch and escape. That is the whole game.

### 14.2 Lair and landmark placement — the rooms **[CANON]**, the rest **[RECON]**, see §25

`data/world/landmarks.json`:

```json
{
  "schemaVersion": 1,
  "fidelity": "recon",
  "startFacing": "N",
  "exit": {
    "room": "8,8", "arch": "85C8",
    "zone": { "x": 0, "y": 0, "w": 256, "h": 103 },
    "keeper": { "x": 128, "y": 119 },
    "requiresPieces": 4, "escapeWalkTicks": 40
  },
  "lairs": [
    { "id": "lair_nw", "guardian": "guardian_hippo",      "piece": { "id": "amulet_nw", "frame": "nw", "slot": 0 } },
    { "id": "lair_ne", "guardian": "guardian_rhino",      "piece": { "id": "amulet_ne", "frame": "ne", "slot": 1 } },
    { "id": "lair_sw", "guardian": "guardian_boar",       "piece": { "id": "amulet_sw", "frame": "sw", "slot": 2 } },
    { "id": "lair_se", "guardian": "guardian_wildebeest", "piece": { "id": "amulet_se", "frame": "se", "slot": 3 } }
  ],
  "amuletSets": {
    "source": "Place Amulet Pieces $A255: one of the eight sets at $A29D, …",
    "quarters": ["lair_nw", "lair_sw", "lair_ne", "lair_se"],
    "sets": [ ["3,4", "1,9", "13,4", "11,12"], ["13,9", "12,10", "13,2", "6,2"], "…",
              ["5,3", "3,12", "13,4", "9,10"], "…" ],
    "pedestals": { "3,4": { "x": 106, "y": 84 }, "1,9": { "x": 68, "y": 80, "orbitPx": 20 }, "…" }
  },
  "amulet": { "sprite": "amulet_piece", "size": { "w": 16, "h": 16 }, "collisionBox": { "x": -8, "y": -16, "w": 16, "h": 16 },
              "pickupFlashTicks": 8, "flyToPanelTicks": 24 },
  "shrines": { "object": "8E18", "zone": { "x": 104, "y": 88, "w": 48, "h": 16 },
               "rooms": ["4,3", "2,5", "1,9", "2,13", "3,13"] },
  "hint": { "messageTicks": 90, "oncePerLife": true },
  "temple": { "name": "The Bone Temple", "rooms": ["7,6", "…"] }
}
```

The start room is **not** here: it is extracted canon (§8), and duplicating it
would let the two disagree. Only the direction Vale faces at the start is.

**Re-chosen on the true map, 2026-09-27** (§25 Q14). M6 chose rooms 2,7,
12,7, 6,14 and 8,14 and the exit 6,11 on the relabelled grid; none of that
survives:

- **The lairs are where the original hides the amulet, picked afresh every
  game** (since 2026-10-12, asked for by the user: "keep it random like the
  original"). `Place Amulet Pieces` (`$A255`) picks one of **eight preset
  sets** of four rooms from `$A29D` — a room byte each, the row in the high
  nibble — and lays each quarter at its room type's first object spot
  (`$DC6C`). `amuletSets.sets` is that table, in the game's byte order;
  `quarters` says which lair's quarter each entry holds. Which quarter the
  game puts first is not known; ours are ordered so that set 3, `$35 $C3 $4D
  $A9`, lays them where the fixed lairs of 0.5 stood.
- **The pick is the simulation's.** `Simulation` picks the set from the run
  seed by a stream of its own (`Rng.hash(seed, 0xA29D)`), so a seed always
  hides the amulet in the same rooms and a replay needs nothing more than its
  seed; `Simulation.landmarks()` is the game's landmarks with every lair given
  its room. `landmarks.json` as loaded has no rooms in its lairs
  (`LandmarksData.placed()` is false): anything that needs this game's lairs —
  the map screen, the quest painter, the treasures, the populator, the Wulf's
  no-go rooms, the forges — asks the simulation, never the content.
  `Content.withAmuletSet(k)` and `--amulet-set N` (§17.6) name a set instead,
  for tests and practice.
- **The ±4 balance is gone.** It was a design wish measured on the wrong map.
  The true map is one long maze, and every one of the game's eight sets
  spreads by 50 rooms or more; set 3's lairs lie 76, 105, 25 and 11 room
  crossings from the start, set 6's 142, 56, 7 and 64. `LandmarkBalanceTest`
  checks that every set's rooms and the way out can be reached, and that 200
  seeds pick every one of the eight sets, each seed the same set twice.
- **Pedestals** are by room, `amuletSets.pedestals`: the nearest spot to the
  game's own piece position where the quarter rests, every guardian fits, and
  its 32 px orbit clears the scenery at all 32 points round it — (106, 84) or
  (98, 84) in most of the 23 rooms. The game's own spots lie against walls,
  fine for a quarter lying on the ground and no good for a guardian circling
  it. Two room types have a rock in the middle that leaves no room for a
  32 px ring anywhere: there `orbitPx` names the widest that fits — 20 px in
  `1,9` and `2,5`, 12 px in `9,3`, `7,14`, `2,14` and `3,10`, where the
  guardian all but stands guard. `PedestalFinder` (test tools) finds them.
- **The way out is the map's only arch**, `(8,8)`, at the heart of the temple
  two rooms north of the start — and 132 crossings away on foot, so the
  player meets it late, having walked the whole maze. Whether this is the
  original's exit is still Q3; it is the only arch there is.

- **The arch's own cells are solid**, so nobody can stand under it. The exit is
  the `zone` in front of it — where the Keeper stands, and where a shrine is
  touched. The arch stands at cells (13, 6) of the room, the zone below it.
- `LandmarkValidator` checks the lot at load: the rooms exist and hold an arch;
  lair ids and slots are unique and `quarters` names each once; no set hides
  a quarter in the start room or the way out; every room of every set has a
  pedestal, and no pedestal belongs to no set; and that the guardians, the
  quarter, the Keeper and its step aside all fit where they are put. A set's
  rooms need not be one per quadrant — the game's own are not.

### 14.3 Guardians `data/entities/guardians.json`

Four beasts, `GUARD_ORBIT`, unkillable, one per lair, each a larger sibling of a
roster species recoloured to its quarter's accent, so the player reads the
family and the scale at once:

| id | based on | colour | guards |
|----|----------|--------|------|
| `guardian_hippo` | `hippo` | bright green | the NW quarter (`lair_nw`; `5,3` in set 3) |
| `guardian_rhino` | `rhino` | bright blue | the NE quarter (`lair_ne`; `13,4` in set 3) |
| `guardian_boar` | `boar` | bright red | the SW quarter (`lair_sw`; `3,12` in set 3) |
| `guardian_wildebeest` | `wildebeest` | bright yellow | the SE quarter (`lair_se`; `9,10` in set 3) |

A guardian goes with its quarter, wherever the game hides it. The original has
no guardians at all (§25 Q2); ours stay `[RECON]`.

All four are **32x28** (§12.3's roster line), drawn by `creature_forge.py` as
their sibling's silhouette grown into that box: "1.4x" in the first draft of
this section was prose, the roster's numbers are the contract.

```json
"orbit": { "radiusPx": 32, "ticksPerRev": 240, "lungePx": 40, "lungeTicks": 90,
           "repelPx": 20, "stunTicks": 20, "hurtFlashTicks": 8 }
```

- It walks the ring rather than being placed on it, so scenery still stops it;
  in a lair's clear middle that reads as a clean, timeable orbit.
- **The radius is 32 px, not 56.** The clearest centre any candidate lair has is
  80 px across (§14.2): a 56 px ring puts a 32 px beast through the hedges.
  A pedestal's `orbitPx` makes it tighter where its room is tighter still
  (`QuestRules` sets that guardian's `orbitRadiusPx`).
- Within `lungePx` it breaks off and charges for `lungeTicks`, then returns to
  the ring. A swing never hurts it: `repelPx` back, `stunTicks` stunned, white
  flash, no score — the Wulf's parry (§13.5) by another name.
- Lair rooms hold **no other creatures**, no orchids and no treasure: the
  simulation keeps the populator out of this game's lairs and the exit room. The Wulf never
  appears in them **and never follows into them** (§13.4) — a chase ends at a
  sanctuary's door, unscored.

### 14.4 The Keeper of the Arch **[CANON]** since 2026-10-11

From the disassembly (`$9F16`, `$A18C`, §25 Q3). Room (8,8) opens only to the south, through a
gap in its bottom wall; the arch is scenery, and **the way out is the room itself**: with the
whole amulet, feet above y 112 (`$A18C`: `$80` on the original's screen) and he is out.

- The actor table sets the Keeper ("the Guardian", sprites `$94`/`$95`) over that gap:
  `landmarks.json → exit.keeper`, feet (128, 119). `exit.zone` is everything above y 112.
- **While the amulet is not whole** it stands there, and its touch throws him (`$9704` = `$80`,
  `$A9AB`: flying straight down) — a life, **whatever his colour** (`$AB3D` skips the white
  check), though only while he is on his feet. There is no grace: `game.json →
  exit.keeperNudgeZonePx` is 0. Set it (16, with `keeperNudgePx` 8, was ours before) and the
  front of it pushes instead of killing — `[RECON]`, kept for anyone who wants it.
- **With the amulet whole** it is harmless, and while he is in its room it slides away from his
  half at 2 px an update (`guardians.json → keeper.stepAsideFp`, 1 px a tick, §12.10): right while he is
  on the room's left, left while he is on its right, stopping at `asideMaxPx` 204 or `asideMinPx`
  76 (the original's `$C0` and `$40`, the sprite's left edge; ours adds half its width). Cross
  to its side and it slides back across the doorway. It keeps its place when he leaves.
- Its frames: `stand`, then `step0`/`step1` as it slides, `aside` when it stops.
- Standing in the zone with the whole amulet then starts the escape (§14.7).

### 14.5 Shrines **[NEW]**

The villages are shrines: stand at a hut's door — `shrines.zone`, the same
rectangle in every hut room, since all five share template 5 — and the panel
names the way to the nearest quarter still out there for `hint.messageTicks`
(90): `AMULET STIRS TO THE NORTH-EAST`. Once the amulet is whole it names the
way to the arch instead. **Once per room per life** (`hint.oncePerLife`),
cleared by a death, so it cannot be tapped for a compass. A hut room where this
game hides a quarter (`2,5` in set 4, `1,9` in sets 0 and 6) keeps its shrine
quiet: the guardian is the answer. Flag
`features.caveHints`, default on — 256 rooms with no guidance is unfair to a
player who has no inlay map.

The original has no such thing. The shrines were first the arch rooms ("cave
mouths"), but on the true map the only arch is the way out (§8.2), so since
2026-10-01 they are the five hut rooms, `(4,3) (2,5) (1,9) (2,13) (3,13)`,
where the tribesmen live — ask the way where people are, and pay for it with
their spears. `LandmarkValidator` checks that each room holds the hut, is
not the start, the way out or a lair, and that the zone has standing room.

### 14.6 Amulet pieces

- Sprite: a quarter of a 32x32 amulet, each quarter its guardian's accent, rim
  in plain white so the yellow quarter still reads. The amulet is **a wolf's
  face** in a medallion (2026-10-04, from the user: the original's amulet was a
  face, not a plain disc), drawn whole by `creature_forge.py → amulet_face` and
  cut into the four quarters, so it assembles as they are found. Our own
  drawing: ears up, brows low over slanting eyes, a long muzzle, a black nose,
  two fangs — the original's amulet graphic is never traced or decoded (§2.1).
- Pickup: box overlap with the player's feet box. Awards `7500` (§25 Q8), flashes the
  border bright white for `pickupFlashTicks` (8), and flies to its panel slot
  over `flyToPanelTicks` (24). The arpeggio arrives with the synthesiser (M8).
- The panel shows the amulet assembling: four slots, held quarters drawn from
  the sprite at half size, missing ones as silhouettes in `white` (§17.3).
- **Kept on death** (`player.json → death.keepAmulet`): a quarter taken is taken.
- **The reveal** **[CANON]** concept, our own words and art (added 2026-09-27,
  after a playtest remembered it). The original clears the play area on a
  pickup, draws the amulet with only the quarters held (`$A207`, table
  `$C374`), prints a four-line verse chosen by how many are held — one for
  each count (`Display Poem` `$A01B`) — plays a tune and pauses (`$A234`),
  then redraws the room. Ours: `shell.json → amuletReveal`, four verses of our
  own (never the original's), the amulet sprite drawn ×`scale` as the panel
  lays it out, held quarters only. `GameSession` holds the game still while it
  shows, so no tick passes, nothing is recorded, and replays are untouched; it
  **waits for fire** (the user's request, 2026-10-07; the original's pause is
  timed): a press in the first `skipAfterTicks` (50) is ignored, so the press
  that took the quarter cannot close it unseen, and from then on `prompt`
  ("PRESS FIRE TO GO ON") shows under the verse. `ShellValidator.checkAmuletReveal`
  checks a verse per quarter and that every line, the prompt and the amulet fit. Its tune, `reveal`
  (§18.3), starts as it opens and stops as it closes — music in `PLAYING`,
  approved by the user (§27.8).

### 14.8 The map scroll and the eyes **[NEW]**

Asked for by the user (2026-10-01), with the map screen of §17.7. Two kinds of
thing lie in the jungle besides the amulet, in `landmarks.json → items`, each
taken by touching `pickupBox` round its `spot` (room-local feet), once per
game, scoring nothing, with the quarter's white border flash and the orchid's
pick-up sound:

- **The map scroll** (`item_map`), five rooms from the start at `6,11`: once
  taken, the map shows every room, not only those visited.
- **Two eyes** (`item_eye`, glancing about every `glanceTicks`): the north eye
  at `14,6` shows the north-west and north-east quarters on the map, the south
  eye at `11,13` the south-west and south-east — each eye two, both eyes all
  four. On the map (§17.7) a shown lair carries a shiny diamond over its
  room, and once its quarter is taken a dull grey one — eye or no eye — so the
  map says where to go and what is done. Changed 2026-10-07 from half-size
  amulet pieces, which the user could not see; changed again 2026-10-12 from
  filling the room in its quarter's colour, which hid the room's own map, at
  the user's asking.

Drawn by `tools/art/item_forge.py`. `LandmarkValidator` checks the rooms, the
sprites, clear ground at each spot, and that the eyes name real lairs and
between them every lair, once. What has been found is in the state hash only
once something has been, so a game that finds nothing hashes as before.

### 14.7 The win sequence

```
feet above the Keeper's doorway in the exit room, with 4 pieces
  ──► control locked (mode ESCAPING)
  ──► Vale walks up into the arch over escapeWalkTicks (40), the playfield
      going black a row at a time
  ──► mode WON, and the closing scene (ENDING, below)
  ──► fire: the tally (TALLY):
        collected score
        + escape bonus            10000
        + time bonus              max(0, 30000 - ticksElapsed / 6)
        + lives remaining bonus   lives * 2500
  ──► fire: the credits roll (ROLL)
  ──► the roll ends, or fire cuts it short: hi-score entry if the run earns a
      place (§21.2), else the title
```

The tally holds fire for 100 ticks first, so the press that won the game cannot
skip it. Its words, colours and the beat between its lines are
`shell.json → tally`'s. Escape at any point of the three banks the run as an
escape and leaves, as Escape does everywhere (§17.1).

**The closing scene [NEW]** (`shell.json → ending`, `EndingScreen`), asked for
by the user (2026-10-12). The original ends on its tally; this is ours. On the
whole screen, a clearing drawn from scenery sprites (`backdrop`). Vale walks in
from the left carrying the amulet, whole and at half size, to **Mr Jones** — an
old jungle hand in a fedora, grey-bearded, leaning on a cane (sprite
`ending_jones`, drawn by `tools/art/ending_forge.py`) — and hands it over. What
they say types out under them, a character every `ticksPerChar`, each line
from its own tick:

> \- I FOUND IT, MR JONES.
>
> \- THANK YOU SO MUCH. THIS AMULET BELONGS TO THE WORLD. I WILL PUT IT BACK
> IN THE MUSEUM.

The scene is silent until its tune, `ending`, comes in on `tuneAt`, after the
words; `PRESS FIRE` shows from `promptAt`, and fire before it is ignored. The
user asked for "a few notes of the famous movie Indiana Jones" there. That
melody is not ours to use: §2.1 forbids audio we did not write, and the
attribution of §2.5 says all of it is original. So `ending` is a short
original march in the same mood (§18.3). `ShellValidator.checkEnding` checks
every sprite, frame, colour and the tune exist, and that every line fits the
screen in the display font.

**Each job its own ending** (asked for by the user, 2026-10-12). A later job
(§14.9) lays `shell.json → newGamePlus.levels[n].ending` over this scene
(`ShellConfig.endingFor`): its own words, its own beats, and the amulet that
job was played for, its four quarters put together as in every ending —
job 1's medallion, job 2's sun (§14.9, `newGamePlus.levels[n].amulet`):

> job 1: \- HERE ARE BOTH HALVES, MR JONES. — \- MARVELLOUS! TWO HALVES, ONE
> AMULET. IT WILL SHINE IN THE MUSEUM, RIGHT BESIDE THE FIRST ONE.
>
> job 2: \- THE LAST ONE, MR JONES. WHOLE. — \- THANK YOU, MY FRIEND. MY LAST
> JOB IS DONE, AND SO IS YOURS. LET'S RIDE HOME.

**The ride home**, the last job's (`ending.ride`): once Mr Jones has spoken, a
bay and a grey trot in from the left (`comeAt`, `comeTicks`), each to stand
where one of them does; at `mountAt` they are up, Vale on the bay and Mr Jones
on the grey; from `rideAt` they gallop off to the right over `rideTicks`, past
the hut and out of the picture, and the tune comes in as they go. The horses,
with and without their riders, are `ending_horse` (32×36, four frames each),
drawn by `tools/art/ending_forge.py`.
`checkEnding` checks each job's ending as it does the game's, and that the ride
keeps its order: the horses there before anyone mounts, up before they ride,
gone before the prompt. `--endings` (§17.6) plays all three.

**The credits roll [NEW]** (`shell.json → credits.roll`, `CreditsRoll`): the
credits page's heading and lines, between `theEnd` and `before` and `after`
(`THANK YOU FOR PLAYING`), rising a pixel every `ticksPerPx` from `fromY`
until the last row is gone; then the run is banked. The credits name the
game's author (2026-10-12). After the first win of a job, the roll is followed
by Mr Jones's offer of the next (§14.9). Its first row is the job's own
`newGamePlus.levels[n].theEnd`, as the user asked: `THE END?` after the game,
`THE END??` after job 1, `THE END! (FOR REAL)` after the last (the display font
has no lower case); `roll.theEnd` (`THE END`) stands in for a job without one.

### 14.9 New Game+ — Mr Jones's later jobs **[NEW]**

Asked for by the user (2026-10-12): after the first win, a New Game+ with the
amulet in two pieces, and after that one in one piece; and, after the credits
of a first win, a new scene in which Mr Jones says "I have a new job for you".
The user chose (2026-10-12): the next job starts **from the title**; the
jungle is **harder** in it as well as shorter; and the pieces lie in
**random rooms of the set**.

**The jobs** (`game.json → newGamePlus.levels`). Level 0 is the game itself —
`NewGamePlusValidator` holds it to one quarter a lair, no head start and the
Wulf at 100%, so nothing here touches the original's game or its replays. Each
later level names its `pieces`, each the panel slots (0–3) it fills:

| level | pieces | `rampHeadStart` | `wulfSpeedPercent` |
|-------|--------|-----------------|--------------------|
| 0 — the game | `[0] [1] [2] [3]` — four quarters | 0 | 100 |
| 1 — New Game+ | `[0,1] [2,3]` — two halves, top and bottom | 2 | 110 |
| 2 — New Game+2 | `[0,1,2,3]` — the amulet whole | 3 | 120 |

- **Where they lie.** The game picks its amulet set as always (§14.2); a later
  job then keeps as many of the set's lairs as it has pieces, picked from the
  run seed by a stream of its own (`Simulation.shareOut`), each with its own
  guardian, the first kept holding the first piece. The rest are empty rooms
  this game: no guardian, no piece, and nothing on the map.
- **A piece is drawn as its quarters** where they sit in the amulet — a half
  is two quarters side by side, the whole is all four — on the pedestal, and
  in flight to the panel, where each quarter flies to its own slot
  (`QuestPainter`). The panel, the reveal and the closing scene already draw
  the amulet by slot, and need nothing new.
- **How much is held** is counted in quarters (`Quest.quarters()`, the bits of
  `slotMask`): the Keeper steps aside, and the arch lets him out, at four
  (`exit.requiresPieces`); the ledger, the hi-score row and game over's
  percentage count quarters too. In the game itself quarters and pieces are
  the same number.
- **A harder jungle.** What the jungle bases its pace on — which small
  creatures the swarm may send (§12.8), the invented jungle's room budget
  (§12.6), the roaming Wulf's chance to come (§13.3) — reads
  `Simulation.ramp()`: the quarters held plus the job's `rampHeadStart`, at
  most four. So New Game+ starts as if half the amulet were already in hand,
  and New Game+2 as if three quarters were. The prowling Wulf (§13.8) runs at
  `wulfSpeedPercent` of its speeds.
- **The verses.** The amulet reveal (§14.6) shows a later job's own verses, one
  a piece (`shell.json → newGamePlus.levels[n].verses`).

**Each job its own amulet** (asked for by the user, 2026-10-13). The game's
amulet is the wolf's face in four quarters; job 1's is another amulet, the one
broken in two — a medallion, silver above and gold below as its halves lie in
the jungle, a crescent on the one and a star on the other, its seam mended
across the middle and a green stone where the halves meet; job 2's was whole
all along, a gold sun with its rays and a red stone. Each is a 32×32 face cut
into quarters of the amulet sprite (`twin_nw`… and `sun_nw`…, drawn by
`tools/art/creature_forge.py`), and `shell.json → newGamePlus.levels[n].amulet`
names them slot by slot. Everything that shows the amulet draws the job's own
(`AmuletFrames`): the pieces on their pedestals and in flight, the panel, the
reveal and the closing scene (§14.7). `NewGamePlusValidator.checkAmulets`
refuses a frame the sprite has not got, or other than four, or any for the game
itself.

**Unlocking.** Winning job *n* opens job *n + 1*, if there is one. It is kept
in the ledger (`stats.json → newGamePlus`, §21.5), and the next game from the
title is the new job. Once one is open, the title shows the chosen job's
`label` under `PRESS FIRE TO BEGIN` and its `tagline` in place of the
title's (the game itself keeps `title.tagline`), and **N** (`newGamePlus.key`)
steps through the jobs open, back to the game itself. A practice win (§17.6) opens
the next job for the session only; the ledger never hears of it.

**The offer [NEW]** (`shell.json → newGamePlus.levels[n].offer`, state `OFFER`,
`EndingScreen.paintOffer`). After the credits roll of the win that opens job
*n*, the clearing of the closing scene again: Vale standing where he stopped,
Mr Jones with his hands free — the amulet is in its museum — and their lines
typing out as the ending's do, in silence. Fire, from `promptAt`, banks the
run; Escape too. The words:

> \- I HAVE A NEW JOB FOR YOU... ANOTHER AMULET, BROKEN IN TWO, DEEPER IN THE
> JUNGLE. — \- I'M ON MY WAY.
>
> \- I HAVE ONE LAST JOB FOR YOU... THIS AMULET IS IN ONE PIECE, AND THE JUNGLE
> GUARDS IT WELL. — \- THEN I'LL BRING IT BACK.

Winning the last job rolls the credits and offers nothing.

A game's job is in its replay (`newGamePlus`, left out for the game itself,
§22.6) and in its quick save (§21.4), so both rebuild the same game.
`--new-game-plus N` starts every game of the session at job N, as practice
(§17.6). `NewGamePlusTest`, `QuestTest`, `ShellTest` and
`NewGamePlusValidationTest` check all of it.

### 14.10 The looking glasses **[NEW]**

Asked for by the user (2026-10-12). Two looking glasses (`item_glass`, a brass
rim and a lens whose glint moves on the eyes' beat) lie in the jungle, in
`landmarks.json → items.glass.places`: the north one at `12,5`, the south one
at `4,14`, a few rooms from the start. Each is taken like the scroll and the
eyes (§14.8): by touching `items.pickupBox` round its spot, once per game,
scoring nothing, with the same flash and sound.

A glass shows **the way to the next quarter** for `showWayTicks` (4500, 90
seconds of play; the clock stands still while the game does, under the map or
pause). The next quarter is the one nearest *on foot* among those this game
hides and Vale has not taken (in a later job, §14.9, only its kept lairs);
with the amulet whole, the way leads to the arch instead. A second glass
starts the 90 seconds again; it does not add to them. **Taking a piece ends
it** (asked for by the user, 2026-10-12): the glass showed the way to a piece,
and once one is in hand the arrow and the trail are gone, time left or not.

- **In the jungle**, a yellow arrow (`way_arrow`, frames `n` `e` `s` `w`) sits
  `insetPx` inside the playfield's edge where the way leaves the room, in line
  with where Vale's feet cross. It blinks, on and off for `blinkTicks`; for
  the last `warnTicks` (10 s) it blinks at `warnBlinkTicks`. In the room the
  way ends in — the lair itself — there is no arrow.
- **On the map** (§17.7), the whole way is dotted from Vale's feet to its end
  in `shell.json → map.trail.colour`, a dot every `dotEveryPx` map pixels,
  the dots marching towards the end a pixel every `marchTicks`; a cross blinks
  where it ends. It crosses rooms the map has not shown yet: that is the point
  of the glass.

**The way** is worked out by `wulf.world.Wayfinder`, a breadth-first flood
over the stitched world (`WorldGrid`) through the cells the feet fit in, the
same clearance the audit uses (§22.3), so it goes the way a player can walk,
room edges, pockets and the Wulf's row included. It floods from the targets
once, until a quarter is taken, and follows the field down from wherever Vale
stands, so it is always the shortest way from here. It is drawing only
(`wulf.render.WayPainter`): the simulation keeps just the glasses taken and
the ticks left, which enter the state hash only once a glass is found, so a
game that never finds one hashes as before.

**Where they lie.** `LandmarkValidator` keeps each glass on clear ground in a
room of its own: not the start, the way out, a room any of the eight sets can
hide a quarter in, a shrine, the scroll's or an eye's room, or the other
glass's. Treasures keep out of their rooms too (§16.4). The two rooms are ones
no recorded replay walks into, so the replays (§22.6) still check what they
were recorded to check. Drawn by `tools/art/item_forge.py`. `WayfinderTest`,
`WayPainterTest`, `QuestTest`, `QuestDataValidationTest`.

---

## 15. Orchids — the drug system **[CANON]** concept and effects, **[RECON]** growth

The single most distinctive mechanic in the original: flowers grow in the
jungle, bloom into a colour, and touching a bloom does something to you —
sometimes wonderful, usually catastrophic. The player learns the colours the
hard way.

### 15.1 Growth cycle

One orchid per anchor point, cycling forever:

| stage | ticks | drawn as | touchable |
|-------|-------|----------|-----------|
| `SEED` | 150 | nothing (bare ground) | no |
| `SPROUT` | 100 | 8×8 green shoot | no |
| `BUD` | 80 | 12×12 closed bud, already showing its colour | no |
| `BLOOM` | 250 | 16×16 open flower, its colour, 2-frame 20-tick sway | **YES** |
| `WILT` | 100 | 12×12 drooping, colour desaturated to the non-bright index | no |
| → back to `SEED` | — | full cycle = **680 ticks ≈ 13.6 s** | — |

- The colour is decided at `SPROUT` and is **visible from `BUD` onward** —
  the player gets ~80 ticks of warning to decide whether to approach. This
  is essential: the mechanic must be a *choice*, not a lottery.
- Touching a bloom consumes it: it jumps straight to `SEED`.
- **Where — the original's** (**[CANON]**, the default since 2026-10-11): **one orchid a room**,
  at its room type's spot (`$D6A6`, through `$A7D4`, which looks the room's type up in `$6066`)
  — the same 48-entry table the beasts stand at, `jungle.json → beasts.spots`. The spot's x is
  the sprite's left edge (ours adds half the flower's 16 px) and its y the feet, counted from the
  top of the screen (ours takes `screenTopPx` off). Type 1's spot is `0, 0`, no flower; no room of
  the map is that type, so every room flowers, and every spot is on open ground
  (`OriginalOrchidSpotsTest`). `Content.orchidSpots()` → `OrchidSpots` → `OrchidField`.
  Not copied: the original keeps one flower's state per room *type* (`$A7F8`, saved on leaving,
  `$A79F`), so rooms of a type share a flower; ours keeps one per room.
- **Where — ours** (with `features.inventedOrchids`, §15.4): a room's orchid anchors are
  **derived**, not authored: up to `anchors.perRoom` (3) spots at least `anchors.minSpacingPx` (40) apart, no
  nearer the edges than `anchors.insetPx` (24) so a bloom is never half in the
  next room, each with its whole 16x16 footprint clear of scenery, drawn from
  `hash(runSeed, col, row)`. The same jungle every run, and no data file to keep
  in step with the map. **Lairs and the way out never flower** (§14.3).
- Orchid stage **persists per room** while the game runs (the cycle keeps
  advancing for rooms you are not in — the jungle does not wait for you).
  Nothing is ticked: an anchor stores only the tick its cycle began, and its
  stage is arithmetic on the clock whenever anyone asks. Picking a bloom sets
  that tick to now, which is what "straight back to `SEED`" means. The first
  cycle of each anchor starts at a staggered negative offset, so a room's
  flowers are at different stages the first time it is seen, and are not in
  step with each other. `Simulation.orchidStagesRead()` counts every stage ever
  worked out; `OrchidTest.onlyTheRoomYouAreInCostsAnything` holds it to exactly
  one per flower per tick in the room the player is in.

### 15.2 The effect table **[CANON]** — the original's five

Read from the disassembly (2026-10-04, §25 Q4) and the default since then. The
six effects M7 invented are §15.4, behind a flag.

```json
  "orchids": [
    { "colour": "red",     "weight": 3, "effect": "TORPOR",    "ticks": 384, "speedScaleFp": 171, "score": 0, "untouchable": true },
    { "colour": "cyan",    "weight": 3, "effect": "HASTE",     "ticks": 384, "speedScaleFp": 341, "score": 0, "untouchable": true },
    { "colour": "magenta", "weight": 3, "effect": "REVERSAL",  "ticks": 384, "speedScaleFp": 256, "score": 0, "untouchable": true },
    { "colour": "yellow",  "weight": 3, "effect": "KNOCKDOWN", "ticks": 192, "speedScaleFp": 256, "score": 0, "untouchable": true },
    { "colour": "white",   "weight": 4, "effect": "CURE",      "ticks": 1,   "speedScaleFp": 256, "score": 0, "untouchable": false }
  ],
  "tint": { "warnTicks": 94, "flashTicks": 16 },
```

| colour | the original | ours |
|--------|--------------|------|
| red | slow: at most 2 px a frame against 3 (`$AFC1`, `$AFE0`) | `TORPOR`, speed ×2/3 |
| cyan | fast: 4 px a frame (`$AFE4`), footsteps twice as often (`$BF4B`) | `HASTE`, speed ×4/3, footsteps twice as often (§11.5) |
| magenta | every direction reversed (`$AF14`) | `REVERSAL` |
| yellow | knocked down, sitting helpless (`$AEF5`, `$AE8F`, `$A61B`) | `KNOCKDOWN`: no walking, no fighting; he sits on the ground, legs out (`side_sit`, facing his way; no stars round his head — the original has none) |
| white | the cure: whatever was on is gone (`$A757`) | `CURE`: clears the effect, leaves none |

- **Every colour but white makes him untouchable** (`untouchable`): the
  original's creatures, Wulf and flame hurt only a white Vale (`$AB36`,
  `$A880`, `$9DE5`). So red and magenta are protection at a price, and yellow
  is protection you cannot use. Our guardians and the Keeper (§14) obey it too.
- **He wears the flower's colour** while it lasts, as the original's
  one-colour Sabreman does: his black outline stays, his bright pixels take
  the flower's bright shade and his plain ones its plain twin
  (`Sprite.blitInked`), so the hat, face, body and legs still read. For the
  last `tint.warnTicks` he flashes back to himself every `tint.flashTicks`
  (`$AF7A`: the last `$2F` counts, 8 counts a phase). Changed 2026-10-07 from a
  flat silhouette, at the user's request.
- **God mode walks through the flowers** (§17.6, the user's choice, 2026-10-07):
  under `--god` no bloom is picked, so none takes him and none is counted
  (`OriginalOrchidTest`). `--debug-effect` still puts him under one.
- **How long**: the timer `$96B0` starts at `$C0` and counts down every other
  frame — 384 frames, 384 of our ticks at 1 a frame (§12.10). Yellow's is
  `$60`: 192 ticks.
- **Odds** (`$A6FD`, sixteen entries): white 4, each other colour 3.
- **No score** for a flower; the original adds none.
- **Sitting, he picks nothing** (`$AB01`, `$A68D`: only a Sabreman in a walking or fighting
  frame, `$9702` − `$10` < `$20`, takes a flower). Without this, a yellow bloom grows back
  (330 ticks) before he could stand (then 384), and took him again, for ever — found by the user,
  2026-10-11. `OriginalOrchidTest.sittingDownHePicksNothing`.
- `[RECON]` still: the growth cycle of §15.1, and the speed ratios applied to
  our base speed rather than as the original's per-frame clamp.

Rules:

- **Only one effect is active at a time.** A new bloom **replaces** the
  current effect entirely (does not stack, does not extend). This keeps the
  state space tiny and testable.
- `HASTE` and `TORPOR` multiply the *base* speed, never the current one.
- Effects persist across room flips and are **cleared on death**.
- The panel shows the active effect as a coloured bar that drains — and
  nothing else. No text label. The player learns the colours.

### 15.3 Implementation

`sim/effects/EffectState.java` holds the one effect: its kind, which orchid it
came from, how many ticks are left of how many, and delirium's own little
clock. `EffectKind` is an enum with the hooks:

```java
int     modifySpeedFp(int baseFp, int scaleFp);   // HASTE, TORPOR
boolean invertsInput();                           // REVERSAL
boolean scramblesInput();                         // DELIRIUM
boolean blocksLethalContact();                    // IMMUNITY
boolean freezesCreatures();                       // STILLNESS
boolean flashesThePlayer();                       // IMMUNITY
```

Nothing else in the simulation branches on which flower it was: `EffectState`
answers "how fast", "which way" and "does this kill me", and the three places
that ask — the player's step, the contact check and the creature tick — ask
only that. The effect is aged before a bloom underfoot is taken, so a flower
picked this tick keeps every tick it promised.

`--debug-effect <kind>` starts every game of a session under one, which is how
each effect is looked at on screen; an unknown name is refused at startup.

---


### 15.4 Our own six **[NEW]**, `features.inventedOrchids` (default off)

M7's flowers, built before the disassembly was read and kept by the user's
decision (2026-10-04) as `orchids.json → inventedOrchids`: `Content.orchids()`
hands out that list instead when the flag is on, so the jungle, the painter
and the panel all see the same garden.

```json
  "inventedOrchids": [
    { "colour": "yellow",  "weight": 22, "effect": "HASTE",     "ticks": 500, "speedScaleFp": 512, "score": 50, "untouchable": false },
    { "colour": "cyan",    "weight": 20, "effect": "TORPOR",    "ticks": 400, "speedScaleFp": 128, "score": 25, "untouchable": false },
    { "colour": "magenta", "weight": 18, "effect": "REVERSAL",  "ticks": 600, "speedScaleFp": 256, "score": 25, "untouchable": false },
    { "colour": "green",   "weight": 15, "effect": "IMMUNITY",  "ticks": 400, "speedScaleFp": 256, "score": 75, "untouchable": false },
    { "colour": "white",   "weight": 15, "effect": "DELIRIUM",  "ticks": 300, "speedScaleFp": 256, "score": 25, "untouchable": false },
    { "colour": "blue",    "weight": 10, "effect": "STILLNESS", "ticks": 250, "speedScaleFp": 256, "score": 75, "untouchable": false }
  ],
```

| effect | what it does |
|--------|--------------|
| `HASTE` | Player speed ×2.0. Exhilarating and lethal — you overshoot into creatures. |
| `TORPOR` | Player speed ×0.5. The Wulf becomes a death sentence. |
| `REVERSAL` | Left↔right **and** up↔down inverted, inside the simulation, so recorded replays stay faithful. |
| `IMMUNITY` | Cannot be killed by contact (still cannot pass `BLOCK_STATIC`); flashes bright white every `immunity.flashPeriodTicks`. |
| `DELIRIUM` | Every `20 + rng(20)` ticks the direction is overridden by a random 8-direction for `10` ticks. |
| `STILLNESS` | Every creature in the room (not the Wulf) freezes, drawn in its plain colours. |

`OrchidTest` pins these; `OriginalOrchidTest` pins §15.2's. The forge
(`creature_forge.py`) draws every colour either list grows.

## 16. Loot and scoring

### 16.1 What exists to collect **[CANON]** set

| item | source | effect | score |
|------|--------|--------|-------|
| amulet piece ×4 | lair pedestals | quest progress | 7500 each |
| treasure | two places in every room, half of them filled (§16.4) | a life, 1 in 16 | 150 each |
| orchid bloom | grown in rooms | an effect (§15) | 25–75 |
| creature kill | sabre | removal, relief | 100–500 (§12.3) |
| spear intercept | sabre vs. thrown spear | removal | 50 |

That is the whole economy. **There is no currency, no shop, no keys, no
food, no health pickup, no ammo.** The deleted prototype had a food/energy
meter; it is **not canon** and must not come back. One touch, one life
(§12.2).

### 16.2 `[NEW]` optional extras — flags, default off

| flag | item | notes |
|------|------|-------|
| `features.bonusRooms` | — | do not build this without asking the user first |

### 16.3 Score events

```json
{
  "schemaVersion": 1,
  "events": {
    "amuletPiece": 7500,
    "escapeBonus": 10000,
    "timeBonusMax": 30000,
    "timeBonusTicksDivisor": 6,
    "lifeRemainingBonus": 2500,
    "roomFirstVisit": 10,
    "wulfEvaded": 250
  }
}
```

- **The time bonus runs out after an hour** (`timeBonusTicksDivisor` 6: 30 000 points
  less one for every 6 ticks, so none at 180 000 ticks). It was 2 — none after 20
  minutes, set when the game ran at half its pace — and every win, the forged
  `full_run` at 25 minutes among them, scored 0 for it. Now that run scores 17 411.
  The bonus is added at the win, after a replay's last checkpoint, so no replay moved.
- `roomFirstVisit: 10` gently rewards exploration (256 rooms = 2560 points
  for a full sweep).
- `wulfEvaded: 250` is awarded when a pursuit ends in a give-up, not a death.
- **No score earns a life** **[CANON]** (§25 Q6). The only writes to the
  original's lives count are the new game (`$B21E`), a fall (`$AA37`), the
  end of the adventure (`$9F87`) and the treasure (`$A2F8`). Ours had extra
  lives at 15k/40k/75k/120k (`[RECON]`); they are gone. A life comes from a
  treasure only (§16.4), with the panel flash and the rising jingle.
- Hi-score is shown on the panel next to the score at all times.

### 16.4 Treasures **[CANON]**

The original scatters odds and ends through the jungle at the start of every
game, and it is where most of its lives come from. Found in the SkoolKit
disassembly (§25 Q10) and kept in `data/world/treasures.json`:

- **Where** (`Initialise Objects` `$A2BD`, `Room Objects` `$A33B`). Every room
  has two places (`$DDEC`, two bytes a room). Each room type has four spots
  (`$DC6C`, `slotsByRoomType`: the sprite's left edge and bottom screen row,
  the banner included; `fromScreen` (+8, −15) turns one into room-local feet,
  which puts 1020 of the 1024 on open ground). The table is filled by a
  counter running down from 512 whose low two bits pick the spot, so place 0
  of an even room takes spot 0 and place 1 spot 3; an odd room's take 2 and 1.
  Room type 1 has no spots. A spot inside our scenery is skipped.
- **How many.** A place is filled when a byte read from the ROM is below
  `$80`, with a kind from a running random nibble. The ROM's share of such
  bytes is not measured, so `fillPercent` is `[RECON]` 50: about 250 treasures
  a game. Ours are laid from the run seed by their own random stream, so the
  creatures' draws are not disturbed and a seed lays the same jungle.
- **What.** Sixteen kinds, picked evenly (`$C084`…`$C0A2`): crate, ring, fruit,
  cap, shield, life, money bag, sword, crate, ring, fruit, cap, shield, shield,
  money bag, sword. Each one ink, from `$A3A7`: bright red, magenta, green,
  cyan, yellow, round again — so the same thing comes in different colours.
  The drawings are ours (`tools/art/item_forge.py`, sprite `treasure`).
- **Taking one** (`$A305`, `$A312`, `$A323`). Touch it: it is gone for the rest
  of the game, deaths included, and scores **150**. The life (kind 5, one in
  sixteen) is a life as well, while fewer than nine are in reserve (`$A2F8`;
  our `lives.max`). `pickupBox` is 12×12 (`$AB01` is called with `BC=$0C0C`).
- **Not in the quest's rooms.** Ours keep this game's lairs, the way out and
  the rooms of the map scroll, the eyes (§14.8) and the glasses (§14.10) clear; the original has no
  such rule. `Content.treasurePlaces()` leaves out the fixed rooms; the lairs,
  known only once the game has picked its set, are skipped as the game lays
  them — on the same draws, so the rest of the jungle is laid alike.
- **Hashing.** Taken treasures enter the state hash only once one is taken.

The `[NEW]` `features.treasures` and `features.extraLifeIdol` that §16.2 used
to plan (an idol, a gem, a tusk) are dropped: the original had its own.

---

## 17. Game rules, states, HUD

### 17.1 Game state machine

```
BOOT ──► TITLE ──► (key) ──► PLAYING ──► ...
 │           │        │                    │
 │           │        ├─► CREDITS          ├─► PAUSED  (P; sim frozen, panel dims)
 │           │        ├─► HISCORES         ├─► DYING   (§11.7)
 │           │        ├─► STATS  [NEW]     ├─► GAME_OVER ──► HISCORE_ENTRY ──► TITLE
 │           │        ├─► SOUND  [NEW]     └─► WON ──► ENDING ──► TALLY ──► ROLL ──► (OFFER) ──► HISCORE_ENTRY ──► TITLE
 │           │        └─► KEY_CONFIG
 │           └─► ATTRACT (after `attract.idleTicks` idle; any key returns)
 └─► fatal data error ──► DATA_ERROR (renders the failing file and the reason, exits on key)
```

Two states were added to this diagram deliberately in M8. `STATS` is the
title-screen stats page §21.5 asks for, and `SOUND` is where the audio settings
of §21.3 are actually reachable — a setting the player cannot change is not a
setting. Both hang off the title like `CREDITS`, and both are named by
`shell.json`, not by Java.

`ENDING` and `ROLL` were added on 2026-10-12, at the user's asking: the
closing scene and the credits after a win (§14.7). `OFFER` came the same day:
Mr Jones's next job, after the roll of a win that opens one (§14.9).

`HISCORE_ENTRY` is entered from `GAME_OVER` and from `ROLL` alike, and only
when the run qualifies (§21.2); otherwise the way back to the title is direct.
Escape leaves the jungle for the title, banking the run; Escape on the title
quits the game. There is no key you have to know.

`DATA_ERROR` is a real state, not an exception dump. A JSON typo must produce
a readable in-game screen naming the file, the JSON pointer, and the
expected shape. This is the single highest-value debugging feature in a
data-driven game; build it in M1.

### 17.2 Title screen

- Game title in the 8×8 font, our own wordmark, animated colour cycling
  through the palette per row.
- An attract-mode demo: `ReplayRunner` playing `replays/attract.json` behind
  a dimmed overlay after 20 s idle. Any key returns to the title.
- Lines: `PRESS FIRE TO BEGIN`, then one per menu entry in `shell.json`, and
  the attribution line from §2.5 in small type.

  **As shipped (M8):** `K KEYS`, `A SOUND`, `H HI-SCORES`, `C CREDITS`,
  `S STATS`. There is no `2 JOYSTICK`: `gamepad.enabled` is false (§19.1) and
  there is no gamepad code, so offering it would be a lie. The two key layouts
  §19.1 does define are chosen on the `KEY_CONFIG` page, by digit, and the
  choice is written to `settings.json`.

  Once a win has opened one of Mr Jones's later jobs (§14.9), a line under the
  prompt names the job fire will start, the tagline becomes that job's, and `N`
  steps through those open.

  The first line of the menu is the difficulty (§17.8): `D DIFFICULTY: HARD`,
  or `D DIFFICULTY: EASY` in green; `D` switches.

### 17.3 The panel (256×40, in the lower border)

```
+----------------------------------------------------------+
| SCORE 0012450          HI 0048300              LIVES ### |   row 0-7, 4x6 font
|                                                          |
|   [NW][NE]     <amulet assembly, 4 quarters, 16x16 ea>   |   row 10-28
|   [SW][SE]                                               |
|                                                          |
| <effect bar: 160px, drains, coloured by active effect>   |   row 30-38
+----------------------------------------------------------+
```

- `LIVES` shows up to 5 small head icons then `x7` numerically beyond that.
- The amulet quarters assemble into a single shape in the centre — the
  visual goal of the game, drawn from the quarter sprites at half size so all
  four fit rows 10-26. Empty slots are silhouettes in `white` (index 7)
  against the panel's black ground, with held quarters in `brightWhite`.
  **Not index 8**: `brightBlack` is `#000000` in this palette — bright black
  is still black — so anything drawn in it on the panel is invisible. The
  same trap applies anywhere you reach for a "dark grey".
- The effect bar is blank when no effect is active (do not draw an empty
  frame — blank means blank).
- Panel messages (`AMULET STIRS TO THE NORTH-WEST`, `EXTRA LIFE`) overwrite
  the effect-bar row for their duration, then restore it.
- The panel is redrawn every tick. It is cheap; do not add dirty-rect logic.

### 17.4 Pause

`P` freezes the simulation completely (including orchid clocks — store a
`pausedAtTick` and shift `stageStartTick` on resume so nothing jumps). The
playfield stays visible, dimmed to the non-bright palette. Pause must not be
usable as an exploit to study a Wulf pursuit at leisure — it is fine that it
is, actually; the original had no pause and we are not shipping a competitive
game.

**The pause menu [NEW]** (asked for by the user, 2026-10-14; `shell.json →
pause`, `MenuScreen`, state `PAUSED`): pausing shows a box over the dimmed
jungle — `RESUME`, `SAVE GAME`, `LOAD GAME`, `OPTIONS`, `QUIT TO TITLE` — up
and down to choose, fire to pick. `P` or Escape resumes; Escape on a page
reached from it goes back to it, on the line that led there. `QUIT TO TITLE`
banks the run as Escape from the jungle does. Not while the amulet reveal or
the map is up. The menus over a game are silent, as the jungle is (§18.3).
`ShellValidator` checks every line names something the shell does, once, and
that one of them resumes.

### 17.9 Options [NEW]

Asked for by the user (2026-10-14; `shell.json → options`, state `OPTIONS`),
from the title (`O OPTIONS`) and from the pause menu: the switches that were
only in `settings.json` until now — `SCANLINES` and `COLOUR CLASH` (§5.4) and
`ONE-COLOUR CREATURES` (`features.inkedCreatures`, §12.8) — each shown `ON` or
`OFF`, fire flipping it, written to `settings.json` at once and shown at once:
the painter reads them every frame (`GamePainter.retro`, `Scaler.setScanlines`).
Sound has its own page (`A`) and the difficulty its own key (`D`, §17.8).

### 17.5 Game over

`GAME OVER` in the 8×8 font over the frozen playfield, 150 ticks, then
hi-score entry if qualified, then title.

Under it, **how much of the adventure was done** **[CANON]** sum, our words
(added 2026-09-27 from a playtest). The original's `Print Rooms Visited`
(`$9CC6`) counts every room visited — a bit per room, set on every room flip
and for the start room (`Visit Room`, `$9D23`) — plus every amulet quarter
held, out of 256 + 4, and prints the whole percent rounded down (its
fixed-point step `$6276` is exactly 100/260). Ours:
`GameOverScreen.percentDone`, the same sum from `Simulation.roomsVisited()`
and the quarters held, on the line `shell.json → gameOver.progress`
(`{percent}% OF THE QUEST DONE`; the original's own wording is not used). The
big font gained a `%` for it.

**Continues [NEW]** (asked for by the user, 2026-10-12). A game may go on after
game over `game.json → continues` times (3), in Easy and Hard alike. While any
are left the screen adds `gameOver.continuePrompt` (`PRESS FIRE TO CONTINUE`)
and `continuesLeft` (`CONTINUES LEFT {n}`), and under them the seconds
counting down, `countdownScale` (3) times the big font in yellow; it stands for
`continueTicks` (500, 10 s) instead of 150. Fire, after
`screenHoldTicks`, goes on: Vale gets up where he fell with a new game's lives
in reserve (the mode's, §17.8), keeping everything found — the quarters, the
map, the scroll, eyes and glasses, the treasures taken, the jungle as it is —
but **the score starts again from 0**. Escape, or the time running out, banks
the run as before. The hi-score table and the ledger take the run's **best**
score (`Simulation.bestScore()`): the highest it stood before any continue, or
after the last. The continue is the fire press played into the simulation in
its `GAME_OVER` mode (`GameSession.carryOn`), so it is in the recorded input:
a replay (§22.6) or a quick load (§21.4) takes it too. The continues taken and
the banked score enter the state hash only once one is taken.
`ContinueTest`, `ShellTest`.

---


### 17.6 Practice options **[NEW]**

Asked for by the user (2026-10-01): command-line options for how many lives a
game starts with, for lives that never run out, and for a Vale nothing can
kill. Off unless given; they last the whole session (every new game).

| option | effect |
|--------|--------|
| `--lives N` | start with N lives in reserve, 1–99, instead of `player.json`'s |
| `--infinite-lives` | deaths still happen — the sequence, the respawn, the ledger's cause — but the count never goes down |
| `--god` | `Simulation.kill` refuses: no creature, spear, guardian or the Wulf can kill him; and no flower takes him (§15.2) |
| `--amulet-set N` | every game hides the amulet in the original's set N (0–7, §14.2) instead of one picked at random: knowing where to go is practice too |
| `--endings` | every ending in turn, each job won on the spot — the closing scene, the tally, the roll, and Mr Jones's offer of the next job, then the title (`./run.sh endings`); offers are shown whatever is open, nothing is unlocked; Esc skips to the next |
| `--new-game-plus N` | the title starts Mr Jones's job N (1 or 2, §14.9), as if it were open; a practice win opens the next for the session only |

Any of them makes the game a **practice run** (`Simulation.practice()`, in the
state hash): when it ends it goes straight back to the title — no hi-score
entry, nothing added to the ledger — because a table that a god-mode run can
top means nothing. A replay file cannot carry them, so `--record` refuses to
combine with any of them (exit 64). A quick load (§21.4) makes a game practice
the same way.

### 17.7 The map **[NEW]**

`M` during play (`shell.json → map.key`) opens the map over the playfield; `M`
again closes it. The game stands still underneath, as under pause, so nothing
is recorded and replays never know (§22.6). The panel says
`MAP: PRESS M TO RETURN`. With `--dev`, the collision view moved from `M` to
`N` to make room.

The whole 16×16 jungle at 16×11 pixels a room — the room's open ground in
miniature, a pixel for every 2×2 cells, walls left dark, so the ways through
read as a maze. Rooms Vale has been in are drawn in `visited` (green); once
the map scroll is found every other room is drawn in `revealed` (blue); the
rest stays black. The room he is in blinks in `here` every `blinkTicks`.
A lair an eye has shown carries a diamond (`map.quarterIcon`: sprite
`map_diamond`, 13×11, drawn by `tools/art/item_forge.py`) sitting in its room
over the room's own map, which still shows around it. While the quarter waits
the diamond is shiny and twinkles — `shiny` frames in turn, `frameTicks` each,
every lair a frame ahead of the last so they do not twinkle together; once the
quarter is taken it is the `taken` frame, the same gem gone grey, eye or no
eye (§14.8). `ShellValidator.checkMap` refuses a sprite or frame that does not
exist, or a sprite larger than a room on the map. While a looking glass shows
the way (§14.10), the map dots it (`map.trail`), with a blinking cross at its
end. `MapScreen`, `MapScreenTest`.

### 17.8 Easy mode and Hard mode **[NEW]**

Asked for by the user (2026-10-12). **Hard** is the game as it is everywhere
else in this document — the original's rules, every number above; nothing
about it changes, and every replay and golden frame is Hard. **Easy** is the
same jungle, map and quest, with less that kills, in `game.json → easy`:

| | Hard | Easy |
|---|---|---|
| lives in reserve at the start (§11.7) | 5 | `livesStart` 9 |
| small creatures in a room at once (§12.8) | 3 | `swarmRoomMax` 2 |
| kinds let in per quarter held (`swarm.pick.openPerQuarter`) | 2 | `swarmOpenPerQuarter` 1 |
| the Wulf's pace (§13.8), on top of a later job's (§14.9) | 100% | `wulfSpeedPercent` 75 |
| the big beasts' pace (§12.9): rhinos, tribesmen, hippos, warthogs | 100% | `beastSpeedPercent` 60 |
| the flame for a player who lingers (§12.7) | comes | `flame` false: never |
| a looking glass shows the way (§14.10) | 90 s | `showWayTicks` 9000, 3 min |
| a shrine names the way (§14.5) | once a room a life | `hintsOncePerLife` false: every time |

At 75% the prowling Wulf goes 1.5 px a tick and, risen, 2.25, against Vale's
3: he can always outrun it. The big beasts go at 60% of their own paces
(`paceFp`, `walkSlowFp`, `walkFastFp`, `chargeFp`; `BeastRules.slower`), so a rhino
or a tribesman is easier to see coming and to step round; a blade shoves them as
hard as ever. Asked for (2026-10-15) after a playtest found Easy only a fifth kinder
than Hard: over 40 seeds of 20 000 ticks a random walker died 2 091 times in Hard and
1 641 in Easy, more of them to tribesmen and rhinos than in Hard. At 60% it dies
1 478 times. A walker blunders into a beast whatever its pace; slower is for a
player who watches. The tribesmen still follow him room to room (§12.9), as their
calls are the hints. The orchids, the guardians, the Keeper, the map
items, the treasures, the ending and New Game+ are the same in both.

**Choosing.** `D` on the title (`shell.json → difficulty.key`) switches Hard
and Easy for the next game; the choice is kept in `settings.json → easy`
(§21.3), so a new session starts where the last one left it. A settings file
from before it means Hard.

**Showing.** An Easy game says `EASY` (`difficulty.panelLabel`, in
`easyColour`) at the right of the panel's lives row (§17.3). A Hard game shows
nothing new.

**Scoring.** One hi-score table for both: an Easy run's row carries `E`
(`difficulty.hiScoreMark`) after the name, in `easyColour`, and
`highscores.json` an `"easy": true` (§21.2). Nothing else about scoring
differs.

**New Game+.** A win in either mode opens Mr Jones's next job (§14.9), and
the mode lays over any job: Easy job 2 is job 2's fewer pieces and head start,
with Easy's lives, swarm, no flame, and its Wulf at 120% × 75% = 90%.

**Rebuilt the same.** A replay (§22.6) and a quick save (§21.4) record
`"easy": true` for an Easy game, left out for Hard, and rebuild it in that
mode. `Content.ecosystem(level, easy)` and `Ecosystem.easy` make the Easy
jungle; `QuestRules.easy` carries it into the simulation, which answers
`Simulation.easy()`. `EasyModeTest`, `ShellTest`, `SaveSlotsTest`,
`ReplayTest`, `QuestTest`; `ShellValidator` checks the key is free on the
title and the colour is in the palette.

## 18. Audio — procedural 1-bit beeper

### 18.1 Approach

All sound is generated in-process. No files. A single mixer thread feeds a
`SourceDataLine` at `44100 Hz, 16-bit, mono`, with a 2048-frame buffer.

**The line comes from the device named `[default]` when Java lists one**
(`Mixer.openLine`), else from Java's own choice. On Linux that is ALSA's
default, which goes through PipeWire or PulseAudio to the speakers the user
picked. Left to choose, Java handed back a line that opened, played without an
error, and was never heard — on a desktop with four sound cards, where the
Windows build was fine (2026-10-04). Only `[default]` played reliably; a
second line opened in the same run often did not, so the game opens one.

`audio/BeeperSynth.java` generates **square waves with hard edges** — the
period's authentic sound — plus a simple LFSR noise source for impacts. No
reverb, no filters, no envelopes beyond linear attack/decay. Two channels
maximum, mixed by simple addition with clipping (which is itself
period-correct).

Audio is **never** allowed to affect the simulation and **never** blocks a
tick. The sim emits `SoundEvent` records into a queue; the audio thread
drains it.

### 18.2 `data/audio/sfx.json`

Each effect is a list of steps. `shape` is `square` or `noise`.

```json
{
  "schemaVersion": 1,
  "sfx": {
    "footstep":     { "steps": [ { "shape": "noise",  "hz": 900,  "ms": 12, "vol": 0.18 } ] },
    "sabre_swing":  { "steps": [ { "shape": "noise",  "hz": 2600, "ms": 40, "vol": 0.30, "slideTo": 1200 } ] },
    "creature_die": { "steps": [ { "shape": "square", "hz": 420,  "ms": 60, "vol": 0.45, "slideTo": 90 } ] },
    "spear_throw":  { "steps": [ { "shape": "square", "hz": 1500, "ms": 30, "vol": 0.25, "slideTo": 700 } ] },
    "orchid_pick":  { "steps": [
        { "shape": "square", "hz": 523, "ms": 40, "vol": 0.4 },
        { "shape": "square", "hz": 659, "ms": 40, "vol": 0.4 },
        { "shape": "square", "hz": 784, "ms": 60, "vol": 0.4 } ] },
    "amulet_piece": { "steps": [
        { "shape": "square", "hz": 523, "ms": 70, "vol": 0.5 },
        { "shape": "square", "hz": 659, "ms": 70, "vol": 0.5 },
        { "shape": "square", "hz": 784, "ms": 70, "vol": 0.5 },
        { "shape": "square", "hz": 1047, "ms": 70, "vol": 0.5 },
        { "shape": "square", "hz": 1319, "ms": 160, "vol": 0.55 } ] },
    "wulf_howl":    { "steps": [ { "shape": "square", "hz": 180, "ms": 700, "vol": 0.5, "slideTo": 520, "vibratoHz": 7, "vibratoDepth": 0.1 } ] },
    "wulf_growl":   { "loop": true, "steps": [ { "shape": "noise", "hz": 130, "ms": 250, "vol": 0.35 } ] },
    "wulf_parry":   { "steps": [ { "shape": "noise", "hz": 4000, "ms": 25, "vol": 0.5, "slideTo": 2000 } ] },
    "player_die":   { "steps": [ { "shape": "square", "hz": 700, "ms": 500, "vol": 0.5, "slideTo": 60 } ] },
    "extra_life":   { "steps": [
        { "shape": "square", "hz": 784, "ms": 60, "vol": 0.5 },
        { "shape": "square", "hz": 988, "ms": 60, "vol": 0.5 },
        { "shape": "square", "hz": 1319, "ms": 120, "vol": 0.5 } ] },
    "room_flip":    { "steps": [ { "shape": "noise", "hz": 1600, "ms": 18, "vol": 0.12 } ] },
    "keeper_moves": { "steps": [ { "shape": "square", "hz": 110, "ms": 400, "vol": 0.45, "slideTo": 220 } ] }
  }
}
```

Priority rules: `wulf_howl` > `player_die` > `amulet_piece` > everything
else. With two channels, a higher-priority event steals channel 0.

### 18.3 Music

`data/audio/music.json`: **original compositions only.** Note lists of
`{ note, octave, ms }`, monophonic square wave.

- `title` — 24-bar jungle-menace theme, loops.
- `win` — 8-bar fanfare.
- `game_over` — 4-bar descending figure.
- `reveal` — 4 bars in E minor, once, under the amulet reveal (§14.6); done
  before the reveal is. The original plays a tune there too.
- `ending` — 4 bars in C major, once, a short march as Mr Jones takes the
  amulet (§14.7). Asked for as a quote of a famous film theme; it is our own
  tune instead, because a recognisable melody someone else wrote is exactly
  what §2.1 rule 2 and §2.5 rule out.
- **No in-game music.** During play there is only the jungle: footsteps,
  creature noises, and the growl. Silence is the tension. Do not add
  background music to `PLAYING`. The one exception is `reveal`, which plays
  while the game stands still under the amulet — approved by the user on
  2026-10-01 (§27.8) — and the jungle is silent again the moment it closes.

---

## 19. Input

### 19.1 `data/config/input.json`

```json
{
  "schemaVersion": 1,
  "profiles": {
    "modern": {
      "up":    ["UP", "W"],  "down":  ["DOWN", "S"],
      "left":  ["LEFT", "A"], "right": ["RIGHT", "D"],
      "fire":  ["SPACE", "Z"], "pause": ["P"], "quit": ["ESCAPE"],
      "devKill": ["K"], "devMask": ["N"], "devWulf": ["H"]
    },
    "period": {
      "up": ["Q"], "down": ["A"], "left": ["O"], "right": ["P"],
      "fire": ["M"], "pause": ["H"], "quit": ["ESCAPE"],
      "devKill": ["K"], "devMask": ["N"], "devWulf": ["J"]
    }
  },
  "active": "modern",
  "gamepad": { "enabled": false, "deadzonePercent": 40, "dpadAsDirections": true }
}
```

The `period` profile is `Q/A/O/P/M` — the era's default key layout. Offer it
on the title screen as a nod; do not make it default.

- `devKill`, `devMask` and `devWulf` act only with `--dev`. `devWulf` summons the
  roaming Wulf (§13.3) — only under `features.roamingWulf`; the original's
  (§13.8) is never summoned. It is not `W`, which is up.
- A key bound to two actions within a profile is a load-time `DataException`.
- The deadzone is an integer percent: nothing that reaches the game is a float.
- **X11 auto-repeat** delivers a release and a press with the same timestamp
  while a key is merely held. `KeyboardInput` treats that pair as still held,
  or holding fire would swing on its own (§11.6).

### 19.2 Sampling discipline

- The AWT key listener writes into a **thread-safe pending set**.
- `input.sampleForTick()` snapshots that set into an immutable `InputState`
  **once per tick**, and derives edges (`firePressedThisTick`) by comparing
  to the previous tick's snapshot.
- The simulation reads only `InputState`. It must never see an AWT event.
- Gamepad axes are quantised to the 8 directions at sample time — the sim
  has no concept of analogue input.
- `RecordedInput` serialises the per-tick snapshots for replays (§22.6) as a
  run-length-encoded array of direction+button bitmasks.

---

## 20. The JSON database layer

**JSON files are the database.** There is no SQLite, no embedded server, no
binary cache, no ORM. This section is the contract for that layer; treat
`data/` with the discipline you would give a schema-migrated relational DB.

### 20.1 Two halves

| half | location | lifecycle | writable |
|------|----------|-----------|----------|
| **Content DB** | `data/**` (also packed into the jar) | loaded once at boot, immutable | **no** |
| **Player DB** | OS user data dir (§21.1) | read at boot, written on events | **yes** |

Nothing in the content DB is ever written at runtime. Nothing in the player
DB affects game balance.

### 20.2 Every file is schema-validated

For every `data/<family>/<file>.json` there is
`data/schema/<file>.schema.json` (JSON Schema draft 2020-12). On boot, in
this order:

```
1. locate file (filesystem in --dev, classpath otherwise)
2. parse to JsonNode                       → parse error   ⇒ DATA_ERROR screen
3. validate against schema                 → schema error  ⇒ DATA_ERROR screen
4. check schemaVersion == expected         → mismatch      ⇒ run migration (§20.7)
5. bind to records via Jackson
6. run semantic validators (§20.6)         → semantic error⇒ DATA_ERROR screen
7. freeze into immutable collections, publish to the repository
```

Every error message must contain: the absolute file path, the JSON pointer
(`/creatures/3/speed/xFp`), what was found, and what was expected. No
stack-trace-only failures.

### 20.3 Repository API

`data/JsonDb.java` is the only thing that touches the filesystem.

```java
public final class JsonDb {
    public JsonDb(Path contentRoot, Path playerRoot, boolean devMode) {...}

    public <T> T load(String logicalName, Class<T> type);   // validated, cached, immutable
    public <T> void savePlayer(String logicalName, T value); // atomic, player DB only
    public void reloadChanged();                             // dev mode only, §20.5
    public String contentHash();                             // for the determinism contract (§6.4)
}
```

Typed repositories sit on top, one per family, each exposing **domain**
queries rather than raw maps:

```java
public interface CreatureRepository {
    CreatureDef byId(String id);                    // throws UnknownCreatureException
    List<CreatureDef> all();
    List<WeightedCreature> forBiome(String biome);  // pre-resolved weights
}
```

Rules:

- Records only. Every loaded type is a `record` with `List`/`Map` fields
  wrapped in `List.copyOf` / `Map.copyOf` in a compact constructor. No
  setters, no mutable DTOs, no `@JsonAnySetter`.
- **Indexes are built once at load**, never computed per tick. Examples:
  `Map<String, CreatureDef> byId`, `Map<String, List<Placement>> byRoomType`,
  `int[256] biomeByRoomIndex`, `Map<String, Sprite> spritesByName`.
- A cache miss at runtime is a bug: everything is resolved eagerly at boot.
  `byId` on an unknown id throws; it never lazily loads.
- Jackson config: `FAIL_ON_UNKNOWN_PROPERTIES = true` (typos must fail),
  `FAIL_ON_NULL_FOR_PRIMITIVES = true`, no default typing, no polymorphic
  deserialisation by class name.
- Decimal fields in JSON (probabilities, scales) are read as `double`
  **only inside the data layer** and converted to fixed-point or per-10000
  integers before crossing into `sim/**` (§6.2).

### 20.4 File-size and split policy

- One file per concept; keep any single file under ~300 KB.
  `original_map.json` is ~56 KB — fine as one file.
- If per-room authored data grows past 300 KB, split
  `room_entities.json` into `data/world/rooms/<col>_<row>.json` **and add an
  index file** `data/world/rooms/index.json` listing them. Never glob a
  directory at runtime: the index is authoritative, so the jar and the
  filesystem behave identically.
- Sprites are naturally one file each; the loader reads
  `data/art/sprites/index.json`, not a directory listing.

### 20.5 Hot reload (dev mode only)

With `--dev`, a `WatchService` on `data/` marks families dirty; pressing
`F5` calls `reloadChanged()`, which re-runs §20.2 for dirty families. If
validation fails, **keep the old data**, show the error as a panel overlay,
and continue running. Never half-apply a reload.

Hot reload must **reset the simulation to the start of the current room** —
reloading creature stats mid-pursuit would break the determinism contract.

### 20.6 Semantic validators (beyond schema)

Run at boot and in `mvn verify`. Each is a named check with a clear message:

| validator | asserts |
|-----------|---------|
| `SpriteSizeValidator` | every sprite's `rows` match its `size`; every legend char resolves to a palette index in 0..15 or -1 |
| `SceneryFootprintValidator` | every object in `scenery.json` has a sprite whose pixel size is `cells * 8`; every `collisionCells` bitmap is `h` rows of `w` chars |
| `MapReferenceValidator` | every `graphic` id in `original_map.json` exists in `scenery.json`; every room type on the grid exists in `templates` |
| `PlacementBoundsValidator` | every placement + footprint fits within 32×22 cells, below the banner |
| `LandmarkValidator` | start/exit/lair rooms are valid addresses; pedestals and spawn points are on non-solid cells |
| `CreatureRefValidator` | every creature id in `room_entities.json` and `biomes` weights exists; every sprite and sfx reference resolves |
| `AnimationValidator` | every animation frame name exists in its sprite; `ticksPerFrame >= 0` |
| `AudioValidator` | every `sfx` referenced from anywhere exists; every step has positive `ms` and `vol` in 0..1 |
| `I18nValidator` | every string key used in code exists in `en.json` (collected via a constants class, checked by test) |
| `PaletteValidator` | exactly 16 entries, indices 0..15 unique, all `rgb` well-formed |

A failing validator fails the build. There is no "warn and continue".

### 20.7 Versioning and migration

- Every file's root object carries `"schemaVersion": <int>`.
- The expected version per family is a constant in
  `data/SchemaVersions.java`.
- On mismatch, `data/migrate/MigrationChain.java` applies ordered
  `JsonNode → JsonNode` steps (`V1ToV2`, `V2ToV3`, …). Content DB migrations
  run in memory only (the files on disk are the source of truth and get
  updated by hand in the same commit that bumps the version). Player DB
  migrations are written back, after a `.bak` copy.
- Bumping a version without a migration step is a build failure
  (`MigrationCoverageTest`).

### 20.8 Atomic writes (player DB)

```java
Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
Files.writeString(tmp, json, CREATE, TRUNCATE_EXISTING, WRITE);
// fsync the file, then move
try (FileChannel ch = FileChannel.open(tmp, WRITE)) { ch.force(true); }
Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE);
```

- Keep one rolling backup (`<name>.bak`) written before the move.
- A corrupt player file (parse or schema failure) is **renamed** to
  `<name>.corrupt-<epochSeconds>` and replaced with defaults. The player is
  told once, on the title screen. Never delete player data silently.
- Writes happen on a **single-threaded executor**, never on the game loop
  thread, and never more often than once per 2 s (coalesce). Force a flush
  on `GAME_OVER`, on `WON`, and in a shutdown hook.

### 20.9 Pretty-printing and diffability

All written JSON uses 2-space indent, sorted keys where order is not
semantic, and `\n` line endings. Content DB files are hand-edited and
reviewed in diffs; keep arrays of objects one-object-per-line-group, not
one-giant-line. Add `*.json text eol=lf` to `.gitattributes`.

---

## 21. Save data

### 21.1 Location

```
Linux/BSD : $XDG_DATA_HOME/wulfquest  (default ~/.local/share/wulfquest)
macOS     : ~/Library/Application Support/WulfQuest
Windows   : %APPDATA%\WulfQuest
```

Resolved by `wulf/data/UserDataDir.java`. Overridable with **`--user-dir
<path>`** — not `--data-dir`, which this file already gives to the content
database root (§3.1); an earlier draft of this section used the same flag for
both, and the content root won. Tests must **never** write to the real user
dir — they use a JUnit `@TempDir`.

### 21.2 `highscores.json`

```json
{
  "schemaVersion": 1,
  "entries": [
    { "name": "AJB", "score": 48300, "pieces": 4, "ticks": 214500, "date": "2026-09-12" }
  ]
}
```

An Easy run (§17.8) adds `"easy": true`, and the table marks it; one
without is Hard. Top 10, sorted descending by score then ascending by `ticks`. Name is
exactly 3 chars from `A–Z` and space, entered with the classic
up/down/fire selector. Seeded on first run with 10 plausible entries from
`data/config/default_highscores.json` (`[NEW]`, keeps the table from looking
broken at 0 points).

### 21.3 `settings.json`

Scale, audio on/off + volume, input profile, CRT toggles, chosen feature
flags, and `easy` — Easy mode chosen on the title (§17.8), absent for Hard.
Written on change. **Feature flags in settings override
`game.json`** — `game.json` is the shipped default, settings are the
player's choice.

### 21.4 Save and load — three slots, `save1.json`… (`[NEW]`)

Asked for by the user (2026-10-04 as a quick save; 2026-10-14 as three slots).
From the pause menu (§17.4) `SAVE GAME` keeps the moment in a slot of
`shell.json → saves.slots` (3), over what was there; `LOAD GAME` — from the
pause menu, or `L` on the title — goes back to one. Each slot's line says what
is in it: the mode, the job, the quarters and the score, and under it the date
and the room (`saves.slot`, `saves.detail`), or `EMPTY`. The panel says
`SAVED TO SLOT n` or `LOADED SLOT n`; a slot that is empty or refused says so
on the page. Saving is always on: the F5/F9 quick save and its
`features.quickSave` are gone.

**A save is the game as it was made, not a snapshot of it**: the run seed, the
start room, `dev`, and every tick's input since the game began (`GameSession`
records what it feeds `Simulation.play`), run-length encoded as a replay's
(§22.6), plus the state hash it reached, the job (§14.9) and the mode (§17.8);
and, for the list, the date, quarters, score and room. Loading builds a fresh
game the way a new game of this session is built (`Boot`'s one `silentGame`
function, practice options and all), replays the input silently at full speed —
an hour of play is well under a second — and compares the hash. Nothing is
copied field by field, so nothing can be forgotten. A save is refused, never
loaded wrong, when it is another version's (`contentHash`), another session's
options (start room, `--dev`), damaged, or does not replay to its hash.

**A loaded game ranks like any other**, as the user asked: saving and loading
are part of play now. A game that was already practice when saved (§17.6)
stays practice. `SaveSlots`, `SaveSlotsTest`: a load lands on exactly the hash
and tick saved, a slot says what is in it, every refusal, and the menus from
the pause menu and the title.

The `progress.json` snapshot this section used to plan
(`features.saveInProgress`) is dropped: replaying the input is exact by
construction.

### 21.5 `stats.json` (`[NEW]`, default on, purely local)

Lifetime counters: rooms visited, creatures killed by species, deaths by
cause, orchids by colour, Wulf encounters, Wulf deaths, best time. Shown on
a title-screen stats page. It also keeps `newGamePlus`, the furthest of Mr
Jones's jobs open (§14.9); a ledger written before it loads as none open. Never transmitted anywhere — there is no network
code in this project.

---

## 22. Testing, validation, CI gates

`mvn -q verify` must run all of the following. A red gate blocks a commit.

### 22.1 Data tests

- `MapDataTest` — asserts **every** fact in the §8 table (256 rooms, start
  `8,10`, 48 templates, 45 used, ids 0/46/47 unused, 41 objects, 919
  template placements, 5105 room placements, max extent exactly 32×24
  cells). These are regression locks on the canon data: if one fails,
  someone edited the map.
- `SchemaValidationTest` — every file in `data/**` validates against its
  schema, and every schema is actually used by some file.
- `SemanticValidatorTest` — runs all §20.6 validators.

### 22.2 Movement feel tests (the important ones)

- `MovementSpeedTest` — from a clear room centre, at full speed, holding right for 85
  ticks moves exactly 256 px ±1; holding down for 59 ticks moves 176 px ±1.
- `WallSlideTest` — against a vertical wall, holding down-right moves the
  player **down** at the full Y rate with X pinned; the player never stops.
  This is the axis-separated resolution contract (§7.4).
- `NoInertiaTest` — velocity is 0 on the exact tick the key releases.
- `DiagonalTest` — diagonal total displacement per tick is greater than
  cardinal-X but less than cardinal-X + cardinal-Y.
- `RoomFlipTest` — leaving east at y=100 enters the next room at y=100,
  x≈2; the reverse trip returns to the original room and position; the outer
  ring never permits an off-grid transition.
- `SabreHitboxTest` — the blade's hitbox is live exactly on ticks 3..8, hits
  each creature at most once per swing, and is not blocked by scenery.

### 22.3 `MapAudit` (tool + test)

Since 2026-09-22 it also answers the three questions it used to skip, after a
player walked into the edge of the map and found it open and impassable:

- **every room, not only the interior** — reachability was counted over the
  196 interior rooms and the 60 on the border were skipped outright, so the
  audit had never once looked at the edge of the map;
- **can each room edge be crossed** — being *reached* says nothing about
  whether the way between two neighbours exists or whether the player can get
  to it from inside the room (`Seam`, and `stranded()` for a gap that is
  walled off from its own room, which is a wall that lies);
- **does the border lie** — walkable cells on the outermost cell-line, which
  are open ground with the world's edge in front of them (§7.5).

The last two currently report a known defect rather than failing the build:
107 north-south edges blocked against 0 east-west, and 937 open cells on the
world's edge. Both are Q14 in §25, both are pinned by `MapAuditTest` so they
cannot get worse, and neither is blessed.

Runs over the whole baked world and reports/asserts:

1. **Room connectivity** — all 256 masks are stitched into one 512×384 cell
   world (§7.5 preserves the cross-edge coordinate, so moving between rooms is
   continuous movement on it; outside the map is solid). A flood fill from
   the start room over **2×2-cell clearances** — exactly the 10×9 px feet box
   of §7.4, which is wider and taller than one cell but never two — must
   reach **all 196 interior rooms**. Any unreachable room is a failure.
2. **Intra-room connectivity** — for each room, the walkable cells form the
   fewest possible connected components; a room whose entry-side border
   openings are not mutually connected is reported as a "sealed" room. Fix
   with `collisionCells` overrides (§7.3), never by editing placements.
3. **Lair balance** — BFS room-distance from start to each of the 4 lairs;
   the max−min spread must be ≤ 4.
4. **Landmark validity** — pedestals, exit prop, and spawn points on
   non-solid cells; a 24 px clear radius around each.
5. **Walkable ratio** — each room must be between **18% and 70%** walkable
   cells. Below 18% is a claustrophobic near-wall; above 70% is a boring
   field. Report the outliers; fail only below 12%.
6. **Text map dump** — writes an ASCII map of all 256 rooms (walkable/solid)
   to `target/map-audit.txt` for human inspection, plus a one-line-per-room
   summary with walkable %, footprint-baseline walkable %, passable blocks,
   and whether the flood reached it.
7. **Footprint baseline** — every room is also measured with each object's
   whole footprint solid: the extracted maze itself (§7.3). Art collision must
   never be tighter than it.

### 22.4 Architecture tests

- `SimPurityTest` — reflectively scans `wulf.sim`, `wulf.world`,
  `wulf.engine` for references to `java.awt`, `javax.swing`, `javax.sound`,
  `java.io.File`, `java.net`, `java.util.Random`, `Math.random`, `float`,
  and `double` in field/method signatures. Any hit fails.
- `HeadlessSimTest` — runs 100 000 ticks of scripted input with no display;
  must not throw, must not allocate unboundedly (assert entity count stays
  under a cap), and must complete in under 10 s.

### 22.5 Golden-frame tests

For a fixed seed and a fixed input script, hash the framebuffer
(`byte[320*256]`) at ticks 1, 50, 200, 1000 and compare to
`src/test/resources/golden/*.hash`. Regenerate deliberately with
`-Dgolden.update=true`, and **explain every intentional change in the commit
message**. This catches accidental rendering and layout drift better than
any screenshot review.

`GoldenFrameTest` (M9) holds two files:

- `golden/play.hash` — the jungle at those four ticks, the whole canvas
  including the panel, drawn through `ui/GamePainter`, exactly what the game
  draws.
- `golden/shell.hash` — one frame per shell screen (§17), plus the title a
  colour-cycle step later, so the wordmark's animation is pinned too.

Both also assert that the frames differ from one another, because a renderer
that has quietly started drawing nothing would otherwise match itself
forever. A hash that moved for a reason nobody can name is a bug, not a
golden file that needs updating.

### 22.6 Replay regression

`replays/*.json` hold recorded per-tick input plus the expected sim-state
hash at every 50th tick. `ReplayRunner` re-executes them. A run of one of Mr
Jones's later jobs says which in `newGamePlus` (§14.9); the game itself leaves
it out, so every replay of it reads as before. Ship at least:

- `replays/attract.json` — the title-screen demo (doubles as a test).
  (**M8, shipped**; since M10 cut by `AttractForge` from `lair_nw.json`)
- `replays/lair_nw.json` — start → NW lair → piece → back. Slipped in M6 and
  forged in M9 with
  `-Dforge.lairs=0 -Dforge.pieces=1 … FullRunForge replays/lair_nw.json`:
  the same bot, sent to one lair, finishing when the quarter is carried back
  out instead of waiting for a win. (**M9, shipped**; re-forged on the true
  map in M10, with inertia on 2026-09-30, and at the equal speeds of
  2026-10-01: seed 202, 35 389 ticks, no death; re-forged 2026-10-03 with the
  original's Wulf (§13.8): seed 202 again, 43 420 ticks, no death; and with the
  original's orchids (§15.2), 2026-10-04: seed 202, 28 326 ticks, no death;
  and with the flame (§12.7), 2026-10-08: seed 190, 25 849 ticks, no death; and with the
  original's creatures (§12.8, §12.9), 2026-10-10: seed 350, 51 119 ticks, no death, found by
  6 forges over seeds 300–359 once the bot learned to fence them and to step out of the flame's
  line — a trace before that put 208 of 299 deaths on the flame; and 2026-10-11 at the
  actors' true pace (§12.10): seed 310, 42 059 ticks, no death, 179 rewinds — four seeds of
  four won, where the faster jungle needed 6 forges; and the same day with Vale at the
  original's pace, 1 tick a frame (§11.3): seed 300, 48 507 ticks, no death, 101 rewinds — 5
  of 6 forges over seeds 300–359 won; and with the original's orchid spots (§15.1): seed 331,
  70 084 ticks, no death, 2 303 rewinds — 5 of 6 forges won, slower; and with the original's
  sabre reach (§11.6): seed 320, 28 798 ticks, no death, 189 rewinds; and 2026-10-12 with the
  amulet hidden in a set picked by the seed (§14.2): seed 316, amulet set 0 — the north-west
  quarter in `3,4` — 27 009 ticks, no death, 117 rewinds; 11 of 12 forges over seeds 300–359 won)
- `replays/wulf_escape.json` — a Wulf pursuit survived across rooms. (**M5, shipped**;
  re-forged 2026-10-08 with the flame: seed 35, chased across 6 rooms,
  parried 8 times, outrun, no life lost; from 2026-10-10 to 2026-10-11 it carried
  `"inventedCreatures": true`, the jungle it was recorded in; re-forged 2026-10-11 at the
  actors' true pace (§12.10), in the original's jungle: seed 5, chased across 4 rooms, parried
  10 times, outrun, no life lost; and the same day with Vale at the original's pace: seed 52,
  chased across 6 rooms, parried 7 times, until it gave up, no life lost; and with the
  original's orchid spots: seed 1145, chased across 5 rooms, parried 7 times, no life lost;
  and with the original's sabre reach: seed 21, chased across 4 rooms, parried 16 times, outrun;
  and with the amulet set picked by the seed (§14.2): the same seed 21 and the same chase)
  It is the roaming Wulf's (§13.1–§13.7, [NEW]), so since 2026-10-03 the file
  carries `"features": { "roamingWulf": true }`: a replay may name the flags it
  was recorded under, and `ReplayRunner` lays them over `game.json`'s, so a
  [NEW] rule's replay keeps playing that rule when the default changes.
- `replays/full_run.json` — a complete 4-piece win, forged by `FullRunForge`
  (**M6, shipped; re-forged 2026-09-30 on the true map, then with inertia
  (§11.3)**): seed 306, lairs in order SE, SW, NW, NE, out through the arch
  in 122 220 ticks without a death — quicker than the 157 879 without it, once
  the bot braked before its turns. Re-forged 2026-10-01 at equal speeds (seed
  2088, 109 412 ticks), and **2026-10-03 with the original's Wulf** (§13.8):
  seed 2018, lairs SE, SW, NW, NE, 106 638 ticks, no death, found by 20 forges
  over seeds 2000–2159; and **2026-10-04 with the original's orchids** (§15.2):
  seed 2008, the same lair order, 71 179 ticks, no death — the bot, untouchable
  under every flower but white, wins faster; and **2026-10-08 with the flame**
  (§12.7): seed 2016, the same order, 72 191 ticks, no death; and **2026-10-10 with the
  original's creatures** (§12.8, §12.9): seed 2050, the same order, 112 295 ticks, no death,
  score 277 950 — every burst scores — found by 16 forges over seeds 2000–2079; and **2026-10-11
  with the original's Keeper** (§14.4): seed 2310, the same order, 110 063 ticks, no death, score
  269 995. The wider exit zone first put the bot's target block inside the solid arch, so every run
  holding the whole amulet stood still in room 8,8 — 276 seeds, no win — until `exitBlock` aimed at
  the zone's bottom edge, the head of the way in; and **2026-10-11 at the actors' true pace**
  (§12.10): seed 2030, the same order, 107 967 ticks, no death, score 194 340 — 14 of 20 forges
  over seeds 2000–2199 won with their first seed, where the faster jungle won one seed in 8 or
  fewer; and **the same day with Vale at the original's pace** (§11.3, §12.10): seed 2002, the
  same order, 115 400 ticks, no death, score 227 875, 1 645 rewinds; and **with the original's orchid spots** (§15.1): seed 2015, lairs SE, NE, NW,
  SW, 160 471 ticks, no death, score 355 850, 792 rewinds — the only win of 20 forges in about
  20 minutes; a flower in every room is in the bot's way more often; and **with the original's sabre
  reach** (§11.6): seed 2003, the same order, 390 490 ticks, no death, score 367 795, 4 250
  rewinds — the only win of 20 forges in 40 minutes; and **2026-10-12 with the amulet hidden in
  a set the seed picks** (§14.2): seed 2055, amulet set 6, lairs NE, SE, SW, NW
  (`2,14`, `1,9`, `7,10`, `9,5` — the first two the tight 12 px and 20 px rings), 75 534 ticks,
  no death, score 239 915, 455 rewinds — 4 of 12 forges over seeds 2000–2239 won, in sets 6, 5,
  2 and 3. `FullRunForge` now plans each game's own way round: the nearest lair next, or (way 1,
  odd seeds) the second nearest second. A seed decides the lairs and every creature, and the bot's luck
  with them varies enormously: seeds 1–60 for `lair_nw` won only in
  108 000–241 000 ticks, and seeds 3000–3159 for the full run in 300 000 and
  more, so search near the seeds that won before. It took three bot fixes, each found by tracing where runs died or
  stood still: pressing the other way under the reversal flower; meeting
  the Wulf by turning to it with fire held — fencing makes the blade live at
  once, and the old wait for a swing that would land stood still while it
  walked in (3 543 of 6 000 deaths in one trace); and giving ground once it
  is parried, instead of following the stunned Wulf in. `forge.maxTicks` is
  500 000 (the old 120 000 was sized for the wrong map).

`ReplayTest.everyShippedReplayStillMatchesItsHashes` runs **every** file in
`replays/`, so one added later is covered the moment it lands; the ones above
also have a test each that checks the replay still *shows* what it was
recorded to show.

A replay divergence means determinism broke (§6.4). It is never "just update
the hash" — find the cause first. The exception is a deliberate change to the
simulation's rules or data: then re-forge the replay in the same commit and say
why.

**Format** (`wulf.engine.Replay`, schema version 1):

```json
{
  "schemaVersion": 1,
  "name": "wulf_escape",
  "note": "what it shows and how it was made",
  "contentHash": "sha-256 of the content DB when recorded — informational",
  "seed": 1234,
  "startRoom": "8,10",
  "dev": false,
  "checkpointEvery": 50,
  "input": [[12, 8], [3, 0], [1, 56]],
  "hashes": ["00c0ffee00c0ffee", "…"]
}
```

`input` is run-length encoded `[ticks, bits]`: up 1, down 2, left 4, right 8,
fire 16, fire pressed 32, dev kill 64, dev Wulf 128 (`RecordedInput`). Pause,
quit and the mask toggle never reach the simulation and are not kept. Every tick
goes through `Simulation.play(input, dev)` — the dev actions first, then the
tick — in the game, the recorder and the runner alike, so a dev-mode recording
replays exactly.

**Making one:** `./run.sh --record FILE` records the first game of a session
(a restart is a new run) and writes it on exit. Acceptance replays that must
show something specific are **forged**: `WulfEscapeForge` and `FullRunForge`
(test sources) drive a bot — a planned route, predictive parries, standing its
ground when something comes at it — through the real game until a run shows what
is wanted, and record it. `FullRunForge` also **searches**: the simulation is
deterministic, so on a death or a stall it replays the input so far minus a
stretch into a fresh simulation and plays on from there with different dice,
reaching further back when the same place keeps killing it, and never undoing a
quarter already carried out of its lair. What it writes is an ordinary input
stream with no death in it. `ReplayTest` then checks both the hashes and that the replay still
shows what it was made to show, so a rules change that quietly turns the escape
into a death fails loudly.

```bash
mvn -q exec:java -Dexec.mainClass=wulf.tools.ReplayRunner -Dexec.args="replays/wulf_escape.json"
```

---

## 23. Coding conventions

### 23.1 Language use

- Java 21, `--release 21`. No preview features even though the box has JDK 25.
- `record` for all data; `sealed interface` + records for state unions
  (`PlayerState`, `EffectKind`, `SoundEvent`).
- `final` on every field and local that can be. No setters in the sim.
- Package-private by default; `public` only across package boundaries.
- No `null` in the sim. Use empty collections, sentinel objects, or
  `Optional` **at data-layer boundaries only** (never as a field).
- No reflection, no annotations processors, no bytecode generation, no
  service loaders, no dependency injection framework. Wiring is explicit
  constructor calls in `Boot.java`.
- No static mutable state. Ever. The only statics are `final` constants and
  pure functions.
- Exceptions: `DataException` (with file + pointer) for data problems,
  `IllegalStateException` for programmer errors. Never catch and swallow.

### 23.2 Naming

- Fixed-point quantities end in `Fp`: `speedXFp`, `posXFp`.
- Pixel quantities end in `Px`; cell quantities end in `Cells`; ticks end in
  `Ticks`. A bare `int x` in a signature is a review rejection.
- Room addresses are `RoomAddress(int col, int row)` — never a bare pair of
  ints, never a `Point`.

### 23.3 Performance

At 50 Hz with ≤ 30 entities and a 320×256 framebuffer, performance is a
non-issue — **do not optimise**. Specifically forbidden without a measured
justification: object pooling, dirty rectangles, spatial hashes, quadtrees,
multithreaded simulation, `sun.misc.Unsafe`, off-heap buffers.

One real rule: **allocate nothing per tick in the hot path.** Reuse the
framebuffer array and the entity list; iterate with indexed `for` loops over
`ArrayList`, not streams, inside `tick()`. Streams are fine everywhere else.

### 23.4 Comments

Comment the *why*, especially where a value is deliberately weird — the
vertical/horizontal speed asymmetry (§11.2), the 40-tick Wulf warning
(§13.3), the axis-separated collision order (§7.4). Every one of those will
look like a bug to the next reader. Reference the section number.

Do not comment the obvious. Do not write Javadoc on private methods.

### 23.5 Commits

- One concern per commit. Data changes and the code that reads them go
  together.
- If a change alters a rule in this file, update this file in the same
  commit.
- If a change alters a golden hash or replay hash, say why in the message.
- End commit messages with the attribution lines the session provides.

---

## 24. Milestones and acceptance criteria

Work in this order. Do not start a milestone until the previous one's
acceptance criteria pass.

### M0 — Clean slate — **COMPLETE (2026-09-12)**

**Accepted:** `mvn -q verify` green (the gate runs inside it); no binary
asset of any kind remains; both run paths (`mvn exec:java` and the packaged
jar) work; the content DB is packaged into the jar at `data/`.

What landed:

- All infringing art deleted: `assets/original/` (41 files), `assets/tiles/`,
  `assets/sprites/`, plus the whole regenerable `out/` tree and the three
  empty `gui_*.log` files. `run.sh` removed — Maven replaces it (§3).
- `assets/data/original_map.json` → **`data/world/original_map.json`**,
  md5 `daf8a2b6c79a7d3b004735fca1df92cb`, unchanged. This hash is the canon
  lock; `MapDataTest` (M1) asserts its contents.
- The 1 406-line `src/wulf/` prototype deleted. Its 8×8 room grid, energy
  meter, and food pickups all contradict this spec, so it was not
  refactored. A tarball of the original source sits in
  `attic/prototype-src-2026-09-12.tar.gz` (untracked, `.gitignore`d) purely
  as a safety copy — delete it whenever. Do not import code from it.
- `pom.xml`: Java 21 `--release`, `-Xlint:all -Werror`, Jackson 2.21.5,
  networknt json-schema-validator 3.0.1, JUnit 5.12.2, AssertJ 3.27.7,
  surefire excluding the `slow` group, `data/` packaged as a jar resource,
  and a `headless` profile that also excludes the `gui` group (§22.4).
- `tools/check-no-binaries.sh` (§2.4), bound to the `verify` phase via
  exec-maven-plugin. Verified in **both** directions: it passes on the clean
  tree, and it fails on a planted `.png` **and** on an extensionless binary
  blob (it checks content, not just extensions).
- `src/main/java/wulf/Boot.java` — a banner-only placeholder so the
  build/run path is provable. Contains no game values.
- `.gitattributes` (LF for all text, §20.9) and `.gitignore`
  (`target/`, `attic/`, `*.log`, `save/`).
- `README.md` rewritten: status, build commands, controls, the design
  summary, and the §2.5 attribution. No IP caveat.

The work lives in the git repository `boaglio/wulfquest` (branch `main`,
remote `origin` → github.com/boaglio/wulfquest.git). The infringing art was
deleted **before** the first commit of this codebase, so it has never
entered git history and never can be recovered from it — which is exactly
where you want that line to fall.

### M1 — Data spine and a window — **COMPLETE (2026-09-12)**

**Accepted:** the window renders the border, the playfield outline and the
panel; corrupting `tickHz` to a string yields
`file: …/data/config/game.json, pointer: /tickHz, problem: fails its schema:
string found, integer expected`, and the process exits 2. 52 tests green,
`mvn -q verify` green.

What landed:

- `JsonDb` (§20.3): filesystem-first then classpath, parse → schema-validate
  → version-check → bind → cache, with a SHA-256 `contentHash()` over every
  file loaded, in load order, for the determinism contract (§6.4).
- Five content files and their schemas: `config/game`, `config/display`,
  `art/palette`, `art/font/font`, `world/original_map`.
- `DataException` carries file + JSON pointer + reason; `DataErrorScreen`
  renders it; the console prints the same three lines.
- `OriginalMapRepository` (§8.3) with every index built once at load.
- `Framebuffer` (indexed bytes, FNV-1a hash for §22.5), `Scaler` (integer
  nearest-neighbour into a reused `BufferedImage`), `Window` (one
  `drawImage` per frame), `GameLoop` (§6.1 exactly), `PanelPainter` (§17.3).
- `MapDataTest`: 18 assertions locking the §8 table — grid, start room,
  template counts, the 41 scenery ids, 919/5105 placements, template size
  range, placement bounds, the disjoint interior/boundary type sets, the
  central landmark's mirror symmetry, the 21 hut rooms and 8 arch rooms by
  address, and the §9 per-room usage weights.

Three bugs the milestone found, all now fixed:

1. **`$id` must be an absolute IRI.** `"wulfquest/game.schema.json"` made the
   validator throw `InvalidSchemaException`. Schemas now use
   `https://wulfquest.invalid/schema/…` — a reserved TLD, never fetched.
2. **Validator messages are localised.** On a pt-BR machine the schema error
   came back in Portuguese, which the accent-free font cannot render.
   `JsonDb` now pins `Locale.ENGLISH` and `PathType.JSON_POINTER`, which also
   made the hand-rolled pointer conversion unnecessary — the library emits
   real JSON pointers.
3. **`brightBlack` is `#000000`.** Unheld amulet slots and the effect-bar
   track were drawn in it, i.e. invisible on the black panel. See §17.3.

Deferred, deliberately:

- **The 8×8 title font.** Nothing before M8 draws with it, and the M1
  acceptance criteria do not need it. The 4×6 panel set is complete at 68
  glyphs, including the JSON/regex punctuation the `DATA_ERROR` screen needs.
- **Most of the §20.6 named validators.** Schema validation plus the
  loader's own structural checks cover everything that exists today
  (`FontSet` performs the font half of `SpriteSizeValidator` at load).
  `SceneryFootprintValidator`, `MapReferenceValidator`, `CreatureRefValidator`,
  `AnimationValidator`, `AudioValidator` and `I18nValidator` land with the
  data families they police, in M2 and M4.
- Hot reload (§20.5) and the `--dev` flag beyond argument parsing.

### M2 — Scenery art and the world — **COMPLETE (2026-09-12)**

**Accepted:** all 256 rooms render; `MapAudit` reports **196 / 196** interior
rooms reachable, **0** sealed, **0** below the floor, **0** cramped, **0** open
fields; `target/map-audit.txt` is a maze. 75+ tests green, `mvn -q verify`
green.

What landed:

- **41 original scenery sprites** from the Scenery Forge (§10.5), every one at
  its exact §9 footprint, reviewed as a contact sheet and in composed rooms.
- `world/scenery.json` (footprints, subjects, biome hints) and
  `art/sprites/index.json`; schemas for sprites, the index and scenery.
  `JsonDb` now keys schema versions by **family** (one `sprite` schema, 41
  files) and takes an explicit schema name.
- **World model:** `SceneryCatalog` (collision derived from art, per §7.3),
  `RoomBaker` (every template baked eagerly; rooms of one template share one
  mask), `CollisionMask`, `Room`, and `WorldGrid` (the stitched 512×384 world,
  solid outside). `Content` loads everything in dependency order for the game
  and the tools alike.
- **§20.6 validators now implemented:** sprite size and legend coverage
  (`SpriteData.decode`), `SceneryFootprintValidator` (`SceneryCatalog`),
  `MapReferenceValidator` and `PlacementBoundsValidator` (`RoomBaker`) — each
  failing with a file and JSON pointer.
- **Renderer:** `Sprite`, `SpriteBank`, `RoomPainter` with a collision overlay.
- **Tools:** `MapAudit` (§22.3, including the footprint baseline) and
  `SpriteForgeCli` (`validate`, `preview`, `sheet`, `mask`).
- **Room browser:** `--room C,R`; arrows, `[` `]` / PgUp / PgDn, `M` for the
  mask. Brackets are matched by character, not key code, so they work on
  ABNT2 and other layouts.

Found along the way:

- **The extracted map is a maze at footprint resolution** (§7.3). This
  retired a claim this file used to make without evidence — that the original
  let you walk under overhanging fronds — and opened §25 Q13: art-derived
  collision leaves the interior 56 % walkable against the canon maze's 39 %.
  M2 passes either way; the rule is the user's call.
- The no-binaries gate caught a real file: a `.pyc` from checking the forge's
  syntax. Fixed by deleting it and ignoring Python bytecode, not by loosening
  the gate.
- The command line crashed with a raw stack trace on `--room 16,3`, and would
  have on `--scale big` or a missing value. `Boot.Args` now turns every
  malformed argument into a one-line usage error with exit code 64, covered by
  `BootArgsTest`.
- `8047`'s first art cut its cloud gaps as transparent holes, which showed the
  black ground through the peaks as notches. They are drawn as clouds now.

**Follow-up, same day — the collision decision (§25 Q13).** Kept art-derived
collision and made the art fill its footprint. Redrew 11 sprites — `771F`,
`7462`, `71B3`, `78F2`, `785E`, `72F6`, `7523`, `7981`, `90A8`, `7947`, `8F2A`
— using two new forge primitives, `leafy_spine` and `broad_leaf`; the forge's
determinism meant exactly those 11 files changed. Interior median walkable went
from 56 % to 45 %, and the gap to the canon maze from 17 points to 6, now locked
by `MapAuditTest`.

### M3 — Ranger Vale — **COMPLETE (2026-09-12)**

**Accepted:** Ranger Vale walks from `8,10` to all four corners of the map —
proven by `CornerWalkTest`, which plans a route over the real map at the feet
box's exact clearance and drives the real simulation there with ordinary
per-tick input, flips and all. Every §22.2 movement-feel test is green. The live
window was launched and only its own frame captured; no input was injected into
the desktop.

What landed:

- **Simulation core** (`wulf.sim`): 8-direction movement with no acceleration
  or inertia; axis-separated collision on the feet box; wall sliding at the
  free axis's full rate; flip-screen transitions with the 6-tick hitch; the
  sabre state machine and hitbox; death, the border strobe, respawn and
  blinking invulnerability; game over. Integer 8.8 fixed point throughout, and
  a `stateHash()` over all state. Positions are **world** coordinates, so a
  flip preserves the cross-edge coordinate by construction (§7.5).
- `CollisionWorld`, which the simulation depends on instead of the map, so
  tests build open fields and walls from lambdas.
- **Input** (`wulf.input`): a per-tick immutable `InputState`, `InputMap` from
  `input.json` with conflicting bindings rejected at load, and `KeyboardInput`,
  which handles X11 auto-repeat (§19.1).
- **Art:** `character_forge.py` draws 25 frames plus 7 mirrors. The blade is
  drawn from the live hitbox, not the sprite.
- **Rendering:** `PlayerPainter` with pure, tested frame selection; a
  `Framebuffer` clip so sprites and the blade never paint the border.
- **Play mode** in `Boot`, with the headless, tested `GameSession` for pause,
  quit, game over and restart. The M2 room browser moved to `--browse`.
- The §20.6 `AnimationValidator`: every frame an animation names exists, and
  every side-view frame has a mirror.
- Determinism: two 5 000-tick runs over the real map agree on every tick.
  `SimPurityTest` bans AWT, Swing, sound, `java.util.Random`, the clock, and
  float or double from the simulation.

Found along the way:

- **Sub-pixel creep against walls.** Collision is per whole pixel, but the
  blocked-axis step-back kept the target's fraction, so a pinned player drifted
  half a pixel. `WallSlideTest` caught it; blocked axes now settle on whole
  pixels (§7.4).
- **Respawns half-hidden at room edges** — spotted by eye in a rendered
  playthrough, not by any test. The respawn anchor is the entry point, which
  straddles the edge by definition. Respawns now keep the whole sprite inside
  the room (`spawn.insideRoomPx`, §11.7), with a test.
- Two bugs in M3's own tests, both worth remembering. The corner-walk bot
  reached three corners one room short because the first route block past an
  edge sits *on* the edge, where a 1 px steering tolerance leaves the feet on
  the old side — the bot now aims a full cell inside. And a validator test
  failed on the wrong animation because validation walks animations in name
  order.
- Spec corrections: the die animation's frame timing contradicted its 40-tick
  duration; the gamepad deadzone was a float; the flip entry formulas were
  superseded by world coordinates; dev keys joined the input profiles.

Deferred, deliberately: gamepad input (`gamepad.enabled` is false); score, hi
score and the amulet on the panel stay placeholders until M4–M6; `--dev` hot
reload (§20.5) is still pending.

**Follow-up, 2026-09-13 — the swing looked wrong.** Reported by the user
after playing. A tick-by-tick probe measured three defects:

1. **Pose and blade out of step.** Poses changed every 4 ticks; the blade was
   live on ticks 3–8, so it showed with the windup pose on tick 3 and the
   recover pose on tick 8. Swing animations are now `sabrePhase`-driven (§10.2).
2. **The blade at knee height.** It was drawn from the ground-plane hitbox: 5 px
   above the feet side-on, 15 px below the hand; between the legs facing down;
   through the pack facing up. It is now drawn from a `hand` anchor (§10.1,
   §11.6). The hitbox is unchanged.
3. **Gliding.** Swing frames had fixed legs while Vale kept moving at full
   speed. The forge now draws every swing pose on every gait (`gaitVariants`).

Tests pin all three: the strike pose shows exactly while the blade is live, a
moving swing steps through gaits, and the blade's pixels sit at the hand's row.

### M4 — Creatures — **COMPLETE (2026-09-13)**

**Accepted:** every species appears in its biomes (`PopulationTest`, over the
real map and many seeds); every behaviour is visually distinct — pinned
tick-by-tick in `BehaviourTest` and checked by eye on rendered contact sheets of
real rooms; `HeadlessSimTest` runs 100 000 ticks of a bot through the real map
with creatures in well under a second (65 ms: 17 games, 81 deaths, 31 kills,
11 species, a thrown spear).

What landed:

- **Data:** `creatures.json` (13 species, the spear), `room_entities.json`,
  `loot.json` (score events; the first visit to a room is worth 10), each with a
  schema. All params are integers (§12.4).
- **Engine:** `Rng` (xorshift128+, seeded through splitmix64; `hash(...)` for
  room seeds) and `SinTable` (256 × 8.8 literals).
- **World:** `BiomeResolver` (§12.6).
- **Simulation:** `Creature`, `Spear`, `Herd`; 11 behaviours in `sim/ai/`
  behind `Behaviour` / `SimContext`, validated by `BehaviourCatalog`;
  `BiomePopulator` behind `RoomPopulator` / `Spawner`, so tests hand-place
  creatures with lambdas. Combat resolves sabre hits on creatures and spears
  before contact, so a swing that lands saves you; a swing hurts a creature at
  most once. Score, kills and extra lives at `extraAt`. Creatures keep moving
  while the player dies. `stateHash` covers creatures, spears, score and visits.
- **Rendering:** `CreaturePainter` — back to front by feet, walk frames while
  moving, fliers always flapping, mirrors, the hurt flash, the puff, the
  spider's thread, spears as a shaft with a bright point, clipped to the
  playfield.
- **Play:** creatures in the live game; `--seed N` replays a run's creatures
  (without it the seed is printed); the panel shows the score and the session's
  best until M8 keeps a table.
- **Art:** `creature_forge.py` (§10.5); `CreatureSpriteValidator` at load.
- **Tests:** 244 runs, from 170 — `BehaviourTest`, `CombatTest`, `PopulationTest`,
  `HeadlessSimTest`, `CreaturePainterTest`, `RngTest`, `SinTableTest`,
  `BiomeResolverTest`, the validators, and a determinism run that starts among
  creatures and proves the run seed changes the jungle.

Found along the way:

- **Unreachable spawns** — spotted by eye on a contact sheet of room `7,3`, not
  by any test: scorpions scuttling in the open strip above the room's top wall,
  which only the room to the north opens onto. Ground creatures now start near
  a place the player can reach inside the room (`Reach`, §12.2), and
  `PopulationTest` checks it against an independent pixel-by-pixel flood.
- **Sub-pixel settle behind the mover** (§7.4): slow creatures never stood still
  against a wall, so `CHASE_DIRECT` never noticed it had stalled.
- **`WALL_FOLLOW` spun at outside corners** (§12.5).
- **`Map.copyOf` iteration order** changes between JVM runs, which broke
  weighted picks' replay (§6.4).
- Test bugs worth remembering: the first determinism run never left the empty
  start room, so it compared runs with no creatures in them — it now asserts
  creatures were present; a stall test's pocket let the rhino slide out; a
  flight test counted distinct x positions instead of ticks aloft.
- Spec corrections: decimal params became integers (`reverseChancePer10k`,
  `throwConeCos10k`); the chief is a hut `extra` rather than a weight; spiders
  ignore scenery; biome columns now match the data; `GUARD_ORBIT` and
  `BLOCK_STATIC` belong to the guardians and move to M6.

Deferred, deliberately: guardian behaviours (M6); creature sound (M8); the
hurt flash and spears are covered by tests but were not caught on a sampled
contact-sheet frame.

### M5 — The Wulf — **COMPLETE (2026-09-13)**

**Accept when:** the `wulf_escape.json` replay passes, and the first time it
appears while you are playing you swear out loud. That is the acceptance
test. It is not a joke.

**Accepted, half of it:** `replays/wulf_escape.json` passes — every hash, and
`ReplayTest` re-derives what it shows: from the start room, the Wulf appears in
`9,10` and chases the player across four rooms to `12,10`, where it is outrun —
parried four times on the way, no life lost, 945 ticks.
The other half is the user's to judge at the keyboard: `./run.sh`, or
`./run.sh --dev` and **H** to call it.

What landed:

- **Data:** `wulf.json` and its schema, integers only (§13.2); `WulfValidator`;
  `devWulf` in both input profiles.
- **Simulation:** the `Wulf` state machine — `ABSENT`, `WARNING`, `PURSUE`,
  `ARRIVING`, `LEAVING` — on a `CHASE_DIRECT` body carried across flips;
  appearance rolls on entry and every 200 ticks with cooldown, grace, quiet-room
  bonus and forbidden rooms; seats at an edge the player is not nearest, clear,
  reachable and at a distance; follow-through with the arrival delay and
  clearance; give-up by time (running off an edge) or by distance; the parry;
  lethal contact. `WulfRules` rides in `Ecosystem`; `Simulation.play(input, dev)`
  is the one tick path for game, recorder and runner; `borderFlash()` replaces
  `borderAlarm()` as what the border shows.
- **Rendering and play:** the Wulf over the creatures, howling while it warns,
  flashing while stunned; the border red for the warning and white for a parry;
  **H** summons it in `--dev`.
- **Replays (§22.6):** `RecordedInput`, `Replay`, `ReplayRecorder`,
  `ReplayRunner`; `./run.sh --record FILE`; `WulfEscapeForge`.
- **Art:** the Wulf in `creature_forge.py` — two galloping frames and a howl.
- **Tests:** 274 runs, from 244 — `WulfTest` (14), `WulfValidatorTest`,
  `RecordedInputTest`, `ReplayTest`, a determinism run with the Wulf, the soak
  requiring it (29 appearances in 100 000 ticks), the painter, the session's dev
  key and `--record`.

Found along the way:

- **The Wulf arrived on top of the player.** Seated "where it was along the
  edge", a Wulf right behind a player who had just flipped landed on them: an
  instant death with no chance to act. No test caught it — the escape forge
  did, by tallying how its bot died: 1 159 of 12 000 runs, the largest single
  cause. Arrivals now keep `arrivalClearancePx` (32) and wait at the door if
  they must, appearances keep `minDistancePx` (72), and
  `WulfTest.itNeverArrivesOnTopOfYou` flips with it close behind over 60 seeds.
- **Refuges.** 13 of 196 interior rooms have no edge the Wulf fits (§13.3),
  measured by probe. Kept as terrain doing its job. Two tests that started in
  `7,3` — one of them — had silently never met the Wulf; they start in the hut
  at `4,1` now.
- **A false escape, caught by eye.** The first forged replay "escaped across six
  rooms" and passed `ReplayTest` — but a contact sheet of it showed two chases,
  each giving up in the room it began. The forge and the test both opened a
  "chase" whenever the Wulf was not `ABSENT`, so its `LEAVING` run-off started
  one that credited the player's ordinary wandering. A chase now begins at the
  appearance, and a room counts only if the Wulf was after the player through
  that flip. The real replay needed four parries.
- **Balance, for a player's eye.** Across the forge's runs its bot died to
  creatures — vultures most, then rhinos and boars — about as often as to the
  Wulf, and the parry window is roughly 4–5 ticks. M4's creature speeds deserve
  a look at the keyboard.
- **`mvn exec:java -Dexec.mainClass=…` ran the game.** The plugin read a
  `${mainClass}` property, so every documented tool command (§3) started the
  game instead. The pom now routes `exec.mainClass`, defaulting to the game.
- **Stale test classes.** Adding a component to `InputState` and
  `InputConfig.Profile` left old test classes that still compiled nothing new:
  the incremental build did not recompile the tests calling the old
  constructors, and they failed at runtime with `NoSuchMethodError`. After
  changing a record's components, run `mvn clean verify`.

Deferred, deliberately: the howl, growl and parry sounds (M8); `lair` and
`exit` in `neverInRooms` and the amulet-piece chance bonus (M6); Wulf
encounter statistics (M8).

### M6 — Quest — **COMPLETE (2026-09-18)**

**Accept when:** a full run is completable and `replays/full_run.json` passes.

**Accepted:** `replays/full_run.json` is a whole game won on the real map — all
four quarters and out through the arch, 48 272 ticks (16 minutes of play), no
death, 70 384 points — and `ReplayTest` replays it every build, checking both
its 965 state hashes and that it still ends in `WON` with four quarters and the
Keeper aside.

What landed:

- **Data:** `landmarks.json` and `guardians.json` with schemas and validators
  (§14.2, §14.3); `exit.keeperNudgePx` and `escapeWalkTicks` moved out of the
  code into `game.json` and `landmarks.json`.
- **Simulation:** a `Quest` beside the `Wulf` — guardians seated on their rings
  in lairs, quarters taken (score, flash, flight), the Keeper blocking, nudging,
  stepping aside, the shrines' hints, the escape walk and the tally; `Player.Mode`
  gains `ESCAPING` and `WON`; the difficulty ramp and the Wulf's chance now see
  real amulet pieces; lairs and the way out are kept clear of creatures.
- **Behaviours:** `GUARD_ORBIT` and `BLOCK_STATIC` (§12.5), the last two kinds.
- **Rendering:** guardians and the Keeper over the creatures; the quarter on its
  pedestal and its flight to the panel; the panel's amulet assembling from the
  real quarter art; the playfield going black row by row as Vale walks into the
  arch; `TallyScreen`.
- **Art:** four guardians, the Keeper with a four-frame step aside, and the four
  amulet quarters, all from `creature_forge.py`.
- **Tools:** `MapAudit.roomDistances` (a 0-1 flood over rooms) and
  `FullRunForge`, a searching bot (§22.6).
- **Tests:** 293 runs, from 274 — `QuestTest` (nine, including a scripted whole
  quest to the tally with its sums), `QuestDataValidationTest`,
  `LandmarkBalanceTest`, `PanelAmuletTest`, and the full-run replay.

Found along the way:

- **The spec's own placements do not fit the map.** Every lair room it named has
  solid scenery at the pedestal it named, the four were 18, 19, 9 and 8 rooms
  from the start (the ±4 rule wants a spread of 4), and the start room has no
  arch to escape through and no space to put one. §14.2 now records what the map
  will actually take, and why.
- **The Wulf followed players into lairs**, which §14.3 keeps as a clean puzzle:
  in the forge's search it was the single biggest killer inside lair rooms. A
  room it never appears in is now a room it never enters (§13.4).
- **A lair's door can sit inside its guardian's reach** (`8,14` is 60 px from
  pedestal to doorway), so a death there can respawn you inside the orbit. Kept:
  the guardian is slower than Vale and a swing buys 20 ticks. Worth watching at
  the keyboard.
- **`mvn verify` excluded the `slow` group**, so an acceptance replay tagged
  that way would never have run. The full run takes 50 ms; it runs every build.
- **Forging a full run needed a search, not a better bot.** Straight bot runs
  died to attrition — creatures, the chief, the Wulf — in 6 000 attempts without
  ever winning. Because the simulation is deterministic, the forge now rewinds
  on a death, replays the input minus a stretch and plays on with different
  dice, anchoring on each quarter once it is out of its lair. The winning run
  took 13 065 rewinds. The bot is no better than a nervous beginner; the replay
  is proof the game can be won, not a demonstration of good play.
- **Balance, again for a player's eye.** Across the searches the killers were,
  in order: the Wulf, the chief, the guardians, hippos and rhinos. The Wulf's
  quiet-room bonus (+2% a room, capped at +20%) means it is rarely away for long
  on a cross-map trek. Nothing changed without a player's judgement.

Deferred, deliberately: orchids (M7); the hi-score entry and title screen that
follow the tally (M8); `lair`/`exit` in `neverInRooms` are resolved, but the
guardians' own sounds wait for the synthesiser (M8).

### M7 — Orchids — **COMPLETE (2026-09-19)**

**Accept when:** each effect is individually testable via
`--debug-effect <kind>`, and off-screen orchids are provably not ticked
per-frame (assert via a counter in a test).

**Accepted:** `./run.sh --debug-effect haste` (or torpor, reversal, immunity,
delirium, stillness) starts every game of a session under that flower, and an
unknown name is refused at startup. Nothing is ticked off screen at all:
`Simulation.orchidStagesRead()` counts every stage ever worked out, and
`OrchidTest.onlyTheRoomYouAreInCostsAnything` pins it to exactly one per flower
per tick in the room the player is in — 1 000 ticks with three flowers costs
3 000 lookups, and the other 255 rooms nothing.

What landed:

- **Data:** `orchids.json` and its schema — the cycle, the anchor rules, and one
  row per flower binding a colour to an effect, its ticks and its score — with
  `OrchidValidator` checking both halves of each colour are in the palette, that
  a frame is drawn for every stage of it, and that `HASTE`/`TORPOR` actually
  change the speed.
- **Simulation:** `OrchidField` (anchors from the run seed, stage as arithmetic
  on the clock) and `sim/effects/` — `EffectKind`'s six hooks and `EffectState`,
  one effect at a time, replaced outright by a new bloom, cleared by death.
  Taking a bloom scores and sends it straight back to seed.
- **The six effects** where they belong: speed at the player's step, steering at
  the input inside the simulation (so a recording stays honest), immunity at the
  contact check, stillness at the creature tick, and the player's white flash.
- **Rendering:** `OrchidPainter` (shoot, bud in its colour, two-frame swaying
  bloom, wilt drained of bright), the panel's effect bar in the flower's colour,
  and creatures drawn in their plain colours while stillness holds them.
- **Art:** 25 orchid frames from `creature_forge.py`.
- **Tests:** 314 runs, from 293 — `OrchidTest` (13), `OrchidValidationTest`,
  `OrchidPainterTest`, and `--debug-effect` in `BootArgsTest`.

Found along the way:

- **Every flower was drawn a thousand pixels off screen.** Orchid anchors are
  room-local, but the painter offset them by the room's world origin, the way
  the creature painter must. Three tests passed over it and my own eye missed it
  on a contact sheet — what caught it was counting drawn pixels in the anchor's
  box in the probe. `OrchidPainterTest.aFlowerIsDrawnAtItsAnchor` now walks a
  whole cycle and insists on ink at every stage but seed.
- **A bloom lost its first tick**: the effect was aged in the same tick it was
  taken, so `HASTE` arrived with 499 of its 500 ticks. Aged first, taken second.
- **A full cycle's phase is the next cycle's start.** `stageAt` now folds the
  phase, which also makes it safe to ask about any tick, past or future.
- **Both replays diverged, and both were re-forged**, as §22.6 requires when the
  rules change deliberately: flowers now grow where the bots walk, and the state
  hash carries the effect and every anchor's clock. The new full run is longer
  for it (77 984 ticks against 48 272).
- **Orchids in a guardian's lair or at the way out** would have broken §14.3's
  clean puzzle: those rooms never flower.

Deferred, deliberately: the title screen, hi-score entry and the attract-mode
replay that follow the tally (M8), and every sound the flowers should make (M8).

**Follow-up, 2026-09-19 — holding fire keeps the sabre working.** Reported by
the user, who has played the original: keep the key down there and the weapon
stays busy. We had built it edge-triggered since M3 — one swing per key-down,
§11.6 calling it "a skill-expression choice" — which was our reasoning against
their memory of the game, and canon wins (§27.7). The swing now starts again the
moment the last ends while the key is held (`sabre.holdRepeatTicks`, 0), while
letting go still costs the full `cooldownTicks` (6), so tapping is never
quicker. Measured over 26 ticks of holding, the blade reads
`...######......######.....` — three windup, six live, three recover, straight
into the next — and Vale walked 171 px through it. Neither replay moved: the
forge bots tap fire for a single tick, and the level is what changed.

### M8 — Shell and polish — **COMPLETE (2026-09-19)**

**Accept when:** the game is playable start to finish with no keyboard
shortcut knowledge, from a double-clickable jar.

**Accepted:** the title screen names every key it has (`PRESS FIRE TO BEGIN`,
and one line per page), Escape always goes one step back — jungle to title,
title to quit — and a finished run walks itself to the hi-score table and back
without a prompt you have to know. `mvn -q package` produces one 4 MB jar with
the two dependencies, the whole content database and the attract demo inside it:
`java -jar wulfquest-0.1.0-SNAPSHOT.jar` from any directory, with no `data/`
beside it, boots to the same content hash.

What landed:

- **The state machine.** `ui/Shell` — headless, clock-free, driven by input
  alone, so `ShellTest` walks every state with no window. `ui/ShellPainter`
  draws whichever screen it is on and decides nothing; `ui/GamePainter` gathers
  the jungle's own frame into one place, so playing it, pausing it, dying in it
  and watching the demo all draw the same picture. `Boot.runGame` is now the
  wiring and nothing else.
- **The screens:** title (wordmark at 2× the 8×8 font with its colour cycling
  down the rows), credits carrying §2.5 verbatim, the hi-score table, the
  three-letter entry selector, the keys page, the sound page, the ledger, and
  `GAME OVER` over a frozen jungle. Pause drops the playfield to the non-bright
  palette (§17.4) with `Framebuffer.dim`.
- **Attract mode** (§17.2): `replays/attract.json` — 3 000 ticks cut out of the
  full run by `AttractForge` — replayed behind a dimmed, striped playfield after
  20 s idle. A missing replay means no demo, not a broken title screen.
- **The player database** (§20.8, §21): `PlayerDb` with atomic writes, an fsync,
  a rolling `.bak`, a single-threaded writer that coalesces, and a flush on game
  over, on a win, and in a shutdown hook. A file that will not parse or will not
  validate is renamed `*.corrupt-<epoch>`, never deleted, and the title screen
  says so once. `highscores.json` seeds from `config/default_highscores.json`,
  `settings.json` from `config/settings_defaults.json`; settings override
  `game.json` (§21.3).
- **The jar** (maven-shade): the content database and `replays/attract.json`
  ship as classpath resources, and `JsonDb` and `Replay` both prefer the working
  tree and fall back to the jar — so a developer's `data/` edits still win, and a
  shipped copy still has everything.
- **The 8×8 font**, forged by `tools/art/font_forge.py`: 53 glyphs authored on a
  6×7 grid inside an 8×8 cell, so the gutters are part of the cell.
- **Audio** (§18), all of it: `audio/BeeperSynth` — hard-edged squares and a
  16-bit LFSR for noise, with glide, vibrato and a 32-sample fade so a step does
  not click — and `audio/Mixer`, one line, two channels, addition and clipping.
  Music shares those two channels: an effect steals the one the tune is on and
  the tune keeps running underneath, which is what a one-bit speaker did.
  `tools/audio/music_forge.py` writes the three tunes from phrases of note
  tokens: 24 bars of jungle menace that loops, 8 bars of fanfare, 4 bars
  descending. No sound card at all means the mixer runs silent and the game
  never notices.
- **How the sim makes a noise:** `sim/SoundSink`, declared in `wulf.sim` and
  naming nothing from `javax.sound`, so §22.4's purity test still holds. It is
  write-only and defaults to `NONE`, which is why every test and every replay
  runs in silence and is unaffected by sound.
- **The ledger's raw material:** the simulation now counts kills by species,
  deaths by cause and orchids by colour. Bookkeeping, deliberately outside
  `stateHash()` — counting is not simulating.
- **Tests:** 403 runs, from 314 — `ShellTest`, `ShellPainterTest`,
  `HiScoreEntryTest`, `PlayerDbTest`, `HighScoresTest`, `UserDataDirTest`,
  `ShellValidationTest`, `AudioValidationTest`, `BeeperSynthTest`, `MixerTest`
  and `SoundTest`.

Found along the way:

- **The keys page numbered the profiles wrongly.** `InputConfig` held its
  profiles in a `Map.copyOf`, whose iteration order is deliberately randomised
  per JVM, so `1` was `period` and `2` was `modern` — the reverse of both
  `input.json` and the page's own footer. Insertion order now, everywhere a map
  of content is shown to the player.
- **A tune that had finished started again.** The mixer cleared the samples but
  left the request standing, so the next block began the fanfare afresh, for
  ever. The request is cleared with the samples.
- **`--data-dir` could not mean two things.** §21.1 gave it to the player
  database and §3.1 to the content database; the content root won and the player
  database got `--user-dir`. §21.1 now says so.
- **The proof that sound changes nothing** is `SoundTest`: the attract replay run
  twice side by side, one with a sink that records every noise and one with
  none, asserting equal state hashes every tick of the 3 000.

Deferred to M9, deliberately: golden frames, the slow full-run test, and the CRT
toggles of §5.4, which are still default-off and still unimplemented.

### M9 — Hardening — **COMPLETE (2026-09-20)**

**Accept when:** `mvn -q verify` is green from a clean clone on a machine
with no display (headless profile skips the window tests).

**Accepted:** `env -u DISPLAY mvn -Pheadless -Djava.awt.headless=true clean
verify` is green in six seconds — 408 tests, every data validator, the
no-binaries gate, and the shaded jar built at the end of it.

What landed:

- **Golden frames** (§22.5): `GoldenFrameTest` with `golden/play.hash` and
  `golden/shell.hash`. Proved to bite by moving a hash by hand and watching
  it fail with the message that says how to regenerate it on purpose.
- **Every replay** is run by `ReplayTest.everyShippedReplayStillMatchesItsHashes`,
  which lists `replays/` rather than naming files, so the next one is covered
  for free. `attract.json` had shipped in M8 with no test at all.
- **`replays/lair_nw.json`**, which §22.6 has asked for since M6: the
  north-west quarter fetched and carried back out, 6 845 ticks, no death
  (re-forged 2026-09-26 for the respawn fix of §11.7; it was 6 323).
  `FullRunForge` grew two properties to make it — `forge.lairs` chooses which
  lairs and in what order, `forge.pieces` finishes once they are out of the
  lair instead of waiting for a win — so it is the same bot and the same
  search, asked a smaller question. It found one on the first seed.
- **The test profiles work now.** Surefire had `slow` written into its
  configuration as a literal while the `headless` profile set a *property* of
  the same name, so the profile had never had any effect on anything. The
  plugin reads the property, the default is empty (§22: verify runs
  everything), `headless` excludes `gui` and `fast` excludes `slow`.
- **Docs**: §3.1's command list, §22.5, §22.6, and the README's own
  hardening section.

Found along the way:

- **Nothing in this suite is slow.** The M9 line item was "the slow full-run
  test", but the full run replays in well under a second and the whole suite
  is under four. Nothing is tagged `slow`, and nothing is tagged `gui`
  either, because no test opens a window — `Scaler` only needs
  `BufferedImage`, which is happy headless. The tags and their profiles are
  plumbing for the first test that needs them, and §3.1 now says so rather
  than implying a slow suite that does not exist.
- **The golden frames caught nothing**, which is the point: they are a
  tripwire for the next change, not a bug report about this one.

Deferred, deliberately: the CRT toggles of §5.4 (`scanlines`, `glow`,
`attributeClash`) are still `[NEW]`, still default-off, and still
unimplemented — `Framebuffer.dim` and `stripe` from M8 are most of what
scanlines would need.

**Landed after M9 (2026-09-26):** `scanlines`, `attributeClash` and
`spriteFlicker`, as §5.4 now describes, still `[NEW]` and default off.
Scanlines went in the `Scaler` rather than through `stripe`, because the spec
says *after upscale* and a framebuffer stripe would be a whole source row
tall. `glow` is still unbuilt: §5.4 never said what it is. There is no menu
page for any of them yet — they are edited in `settings.json`.

---

### M10 — The true map — **COMPLETE (2026-09-27), one replay slipped**

Not planned as a milestone: it grew out of researching Q14 and a round of
playtests. Everything below is sourced from the SkoolKit disassembly (§25).

What landed:

- **The map is the original's.** `roomTypeGrid` was a relabelled copy of the
  game's layout table; it is now the table itself, and the rooms lost the two
  banner rows they never had (32×22, §5.1, §7, §8). An emulation of the
  original's collision and room flips agrees with ours edge for edge (§25
  Q14). `MapAudit`, `MapDataTest`, `BiomeResolverTest` and friends re-pinned
  to the true map; the audit no longer fails on the original's own 29
  dead-end crossings.
- **Landmarks re-chosen on it** (§14.2): the way out at the only arch, `8,8`;
  lairs at the game's own amulet set 3; the ±4 lair balance dropped; no cave
  mouths (§14.5).
- **The amulet reveal** on every quarter (§14.6), **fencing** in place of the
  old swing — the blade live while fire is held, ⅔ speed, guard-and-lunge
  frames in every view (§11.6) — and **how much of the adventure was done**
  on the game-over screen (§17.5), each from the disassembly and a playtest.
- `run.sh` offers every way to run the project (§3.2); `FullRunForge` got a
  configurable tick cap and learned to press the other way under the reversal
  flower.
- `replays/wulf_escape.json`, `lair_nw.json` and `attract.json` re-forged;
  golden frames regenerated.

Slipped, then landed 2026-09-30: **`replays/full_run.json`** (§22.6) — re-forged
once the bot learned to fence the Wulf. **Slipped again 2026-10-01:** when the
vertical speed was matched to the horizontal (§11.2) its input stopped winning,
and the bot often stalls at a rhino in the shaft mouth of `0,15`. It was
re-forged the same day by searching more seeds, 18 forges in parallel: 10 of
154 seeds won, and the shortest, seed 2088 (109 412 ticks, about 36 minutes),
is the new `full_run.json`; its test is back on. Its checkpoints were then
re-recorded with the treasures in (§16.4): the same input still wins, with 63
treasures picked up on the way. Walking momentum
landed after M10 too (§11.3), and so did the rest, on 2026-10-01: the reveal's
tune (§18.3), the shrines at the hut doors (§14.5), and the temple's name.

From the disassembly, 2026-10-02 and 03 (§25 Q6, Q7, Q9): the original's lives
— five in reserve, none for score (§11.7, §16.3) — and **the original's Wulf as
the default** (§13.8): one, prowling the bottom row, sat down by a sabre held
towards it. The roaming Wulf of M5 stays as `[NEW]` `features.roamingWulf`.
Replays may now carry the flags they were recorded under (§22.6).

---

## 25. Open canon questions (research backlog)

These are the known gaps between this spec and the original. Each is a
discrete task. **Rules for closing one:** find a primary source (an
extracted data table, a disassembly listing, a frame-counted video
analysis), record the source URL and what it showed **in this section**,
change only the relevant JSON, flip that entry's `"fidelity"` from `"recon"`
to `"canon"`, and update the affected tests.

Do not close one from memory. Do not close one from a wiki summary without
noting it as weaker evidence. If a source contradicts a `[RECON]` value,
the source wins.

| # | Question | Currently | Where it lives |
|---|----------|-----------|----------------|
| Q1 | The exact creature roster and per-room creature assignment table | 13 invented species, biome-weighted. **Lead 2026-10-03:** the original's room creatures are not per room. Up to `$96BE` (3) at once appear as a puff in the player's room (`$9A68`), then become one of 15 kinds from `$A41A` — spider, scorpion, bat, parrot, monkey, lizard, snake, then rat, bird, frog, mosquito, skunk, flea, earwig — picked from the first `8 + 2 × quarters held` entries (`$A3EC`), so the jungle gets worse as the amulet fills. The fixed ones are in the actor table `$AC77`: rhinos in (8,9) twice, (7,0) and (3,15); tribesmen in (7,10), (4,4), (2,14); a hippo in (3,8); the guardian at the arch (8,8); and the Wulf (Q9). A flame (`$34`) comes for a player who lingers — **built 2026-10-08**, §12.7. **Built 2026-10-10 and now the default**: the small ones (§12.8) and the big ones (§12.9); ours kept as [NEW] `features.inventedCreatures`. In brief: rhinos (`$AD07`) pace a row at 3 px and turn at the scenery, and a blade sends one back at 6 (`$AD69`); tribesmen (`$AB74`) walk a slant, re-steer when stopped, are pushed back by a blade, and follow the player — two rows away, one is set down a row from him (`$A630`) — and flash and beep when level with an amulet quarter (`$9D41`); the hippo sleeps (`$9EB3`), follows the same way, comes back a hippo or a warthog at random (`$9F06`), and touched it wakes, turns and charges until stopped (`$9E72`). | `jungle.json`, `creatures.json`, `room_entities.json` |
| Q2 | The four guardians' species, colours, and lair rooms | **Rooms answered 2026-10-12:** the original has no fixed lairs — it picks one of eight preset four-room sets at random (`$A29D`, `Place Amulet Pieces` `$A255`) and lays each quarter at its room type's first object spot (`$DC6C`). Ours now does the same, the set picked from the run seed (§14.2). Which quarter goes in which entry of a set is still ours. The original has no guardians; ours (`[RECON]`) go with their quarters. Guardians' species and colours still open; the game's immortals table (`$AC77`) lists rhinos, spear men, fire, the wolf, a hippo and "guardian" as sprites to look at next. | `landmarks.json`, `guardians.json` |
| Q3 | The exit's actual location and its guard condition | **Closed 2026-10-11** from the disassembly, and built (§14.4): the exit room is (8,8), the only stone arch, entered only from the south; the win is feet above `$80` there with the amulet whole (`$A18C` → Game Complete `$9F6A`). The actor table's Guardian (`$94`/`$95`, `$ACFB`) stands over the doorway; until all four quarters are held (`$970D` = `$0F`) its touch throws Vale (`$9704` = `$80`) whatever his colour (`$AB3D`); then, with him in the room, it slides away from his half at 2 px an update to `$C0` or `$40` (`$9F40`). | `landmarks.json`, `guardians.json` |
| Q4 | The orchid colour→effect mapping and durations | 6 colours per §15.2. **Answered and built 2026-10-04** (§15.2, now the default; ours kept as [NEW] `features.inventedOrchids`, §15.4). Touching a flower **in full bloom** (state `$4F`, `$A737`) turns Vale its colour (`$9707`) and sets the timer `$96B0` to `$C0`; it counts down every other frame (`$AE7A`), so an effect lasts **384 frames** (384 of our ticks at 1 a frame, §12.10), and for its last `$2F` counts Vale flashes white (`$AF7A`). At zero he is white (`$47`) again. **Five colours** (`$A6FD`, picked when a flower closes, from the frame counter): **red** `$42` — slow, at most 2 px a frame against 3 (`$AFC1`, `$AFE0`); **cyan** `$45` — fast, 4 px a frame (`$AFE4`), footsteps twice as often (`$BF4B`); **magenta** `$43` — every direction reversed (`$AF14`, `XOR %1111`); **yellow** `$46` — knocked down, sitting helpless for `$60` counts, then he gets up (`$AEF5`, `$AE8F`, `$A61B`); **white** `$47` — the cure: clears any effect (`$A757`). Odds of a colour: white 4 in 16, each other 3 in 16. **Every coloured state makes him untouchable**: creatures, the Wulf and the flame all hurt only a white Vale (`$AB36`, `$A880`, `$9DE5`) — so red and magenta are protection with a cost, yellow is protection you cannot use. **No score** for a flower. A death clears the effect (`$AA49`). Ours — haste, torpor, reversal, immunity, delirium, stillness, scored 25–75 — is `[RECON]` that the original does not support. Where it grows: one a room, at its type's spot (`$D6A6`), §15.1 — built 2026-10-11. Not built: the growth timings (`$A680`–`$A731`, read 2026-10-11): it opens a stage every other pass to full bloom, stays open `$10` counts, closes, takes a new colour, and stays shut `$80`–`$BF` counts — and the counts go down only while Sabreman walks or fights (`$A68D`). Ours keeps §15.1's clock. | `orchids.json` |
| Q5 | Exact player speed in px/frame, and whether it was frame-quantised | **Shape answered 2026-09-30** (§11.3): up to 3 px a frame in whole steps (0–3), with inertia ($AEEF, $AFC1, $B031, $B047); fighting a steady 2 ($ADD0). **Answered 2026-10-11** (§12.10): the main loop (`$99AA`–`$9A1D`) runs Sabreman once each time the 50 Hz frame counter moves, so 3 px a frame is 150 px/s — 3 px a tick, `ticksPerFrame` 1 (we had 2, half the pace). The other actors move once a pass of the actor table, about two frames: 2 ticks an update. | `player.json → speed, momentum` |
| Q6 | Starting lives and extra-life thresholds | **Closed 2026-10-02** from the disassembly: **5 in reserve** (`$B21E`), so six falls — a fall with none left is game over (`$AA27`–`$AA37`); **no extra lives for score** — the lives count's only writers are `$B21E`, `$AA37`, `$9F87` and the treasure life `$A2F8` (up to 9 in reserve). §11.7, §16.3. | `player.json → lives` |
| Q7 | Whether amulet pieces were lost on death | **Closed 2026-10-02: kept.** The quarters held are bits of `$970D`, written only by the pickup (`$A1E3`); the death path `$AA27`–`$AA57` does not touch it. §11.7. | `player.json → death.keepAmulet` |
| Q8 | Score values per creature and per piece | **Partly answered 2026-10-01** from `Add Points To Score` (`$B5A9`, BCD) and its callers: a quarter of the amulet is **7500** (`$A1ED`, now in `loot.json`), a treasure **150** (`$A332`, §16.4), and a creature **165, 175, 185 or 195** by its type (`$A4B3`). **Creatures closed 2026-10-10** with Q1: the original's are the default (§12.8), scored as `$A4B3` scores them; the invented roster's 100–500 stay `[RECON]` behind `features.inventedCreatures`. | `creatures.json`, `loot.json` |
| Q9 | The Wulf's appearance rules and whether the sabre affected it | 12% on entry + bonuses; repel only. **Answered and built 2026-10-03** (§13.8, now the default; ours kept as [NEW] `features.roamingWulf`). Each actor's state byte is also its sprite ID and indexes the jump table `$9B3E`. (1) **One Wulf, on one row.** A new game copies the actor table `$AC77` to `$9702`: one Wulf record (`$976E`, state `$50`) in room **(6,15)**, x 112. Its y velocity is never set, and a side exit changes only the column, wrapping 15↔0 (`$B187`, `$B19F`, `$B193`), so it **never leaves row 15**. Nothing else makes a Wulf: the room's random creatures (`$9A68`, puff `$01`, `$A3E0`) come from `$A41A`, all small fry. (2) **Always moving, on- or off-screen**, 4 px per update in x (`IX+$06` = ±`$40`, ÷16 in `$B031`) against the player's 3; blocked by its own room's scenery, it turns (`$AAED`→`$ADB3`). (3) **It hunts only along the row**: with the player in row 15 it heads for the player's room, east or west by room number, or by x in the same room (`$A922`); otherwise it keeps its heading. (4) **Touch costs a life**, the same throw as any creature (`$9704`, `$A985`, then `$AA0D`→`$AA27`), unless the player's colour is not plain `$47` — under an orchid's tint (`$A880`). (5) **The sabre does not stop the hit**, unlike against small fry, whose touch it blocks when Vale faces them (`$AB3D`). But facing an oncoming Wulf while fighting makes it **stop and sit** (state +8, `$A8B2`/`$A8E4`) for as long as he keeps facing it (`$A8F5`), then it goes on, briefly faster (±`$60`, `$A964`). Our Wulf — appearing anywhere after a warning, following across rooms, giving up — is `[RECON]` that the original does not support. | `wulf.json` |
| Q10 | Whether anything besides orchids and the amulet was collectable | **Closed 2026-10-01: yes, treasures.** Source: the SkoolKit disassembly (Q14's link) — `Initialise Objects` `$A2BD`, `Room Objects` `$A33B`, the object location table `$DC6C`, the handlers `$A305`/`$A312`/`$A323` and the sprite table `$C084`. Up to two per room, seven kinds of thing and a life, 150 points each. All in §16.4 and `world/treasures.json`; only the fill rate is `[RECON]`. | `world/treasures.json` |
| Q11 | Sabre swing duration, reach, and whether movement was locked | **Shape answered 2026-09-27** (§11.6): no windup or recovery, live while fire is held (`$AB0E`), a random one of eight poses every 4th frame (`$AE4B`, `Rand8`), movement at 2 px/frame against walking's 3 (`$ADD0`, `$AFC1`). **Reach answered 2026-10-11** (§11.6): 16 px past his touch, `$AB0E` widening the touch test in front from 12 px to 28 while fighting — `reachPx` 21. The frame rate is answered too (Q5). | `player.json → sabre` |
| Q12 | Whether creature spawns were fixed per room or random | authored-with-fallback | `room_entities.json` |
| Q13 | How the original resolved scenery collision — per pixel, per cell, or per object rectangle — and so which rule reproduces its routes | **Answered 2026-09-27: per object rectangle.** `$B873`, called by the player's movement through `$B81C` with `BC=$160E`, tests the player's box (15 px wide at x..x+14, 23 px tall ending at the feet y) against every placement's whole graphic rectangle (header: height in pixel rows, width in bytes ×8), then retries on one axis for a slide. Our art-derived collision (≥25 % painted) is kept as `[RECON]`: over the corrected map it gives exactly the original's room-to-room topology (below), so the routes are the original's. | `world/scenery.json → solidCoveragePercent`, per-object `collisionCells` |

| Q14 | The playfield's true height, and whether a top border strip is missing | **Closed 2026-09-27.** Source: the SkoolKit disassembly of the Spectrum game, <https://skoolkit.arcadegeek.co.uk/ultimate/sabrewulf/> (source <https://github.com/pobtastic/ultimate/tree/main/sources/sabrewulf/>), corroborated by <https://www.icemark.com/dataformats/sabrewulf/>. (1) **The banner is rows 0–1.** `Clear Play Area` `$BB5C` clears from `$4040` (row 2) "only from below the banner… the banner takes up two"; lives print at `$4008`/`$4016` (row 0), scores at `$4021`/`$402D`/`$4039` (row 1); `Draw Room` `$BBF7` passes a placement's x/y straight to the screen-address routine, so `y=2` is the first row under the banner. The feet run y 40..191 (`$AFE8`, `$B027`): the play area is rows 2–23, 32×22 cells. (2) **The grid was wrong.** The 48 templates match the game's `Room_00`–`Room_2F` byte for byte, but `roomTypeGrid` was a consistently relabelled copy of the layout table `$6066` (a bijection over all 256 rooms), so every room drew the wrong template. With the game's own table, an emulation of the original's collision (`$B873`) and flips (`$AFE8` → `$B12F`/`$B19F`/`$B187`/`$B1AB`; going south puts you at a per-type x from `$6036`, which the emulation reproduces for all 45 types) connects all 256 rooms, opens 154 east-west and 160 north-south edges, 29 east-west ones only from pockets, and leaves no way off the world; the old grid connected 2 rooms. Both fixed: the grid replaced, the adapter takes the banner off `y`, rooms are 32×22. Our map now has exactly those edges. The raw pages and the emulation are not kept here (§2.1). | `original_map.json → roomTypeGrid`, `OriginalMapRepository.SCREEN_TOP_ROW`, `CollisionMask.ROWS`, `display.json → playfield` |

Two facts are **already closed** and must not be re-litigated: the 16×16 /
256-room grid and the start room at `(8, 10)` are `[CANON]`, extracted
directly into `original_map.json` and locked by `MapDataTest`.

---

## 26. Glossary

| term | meaning |
|------|---------|
| **cell** | an 8×8 pixel unit; the collision and layout grid |
| **room** | one flip-screen, 32×22 cells, 256×176 px |
| **playfield** | the 256×176 area where rooms are drawn; the original screen less its banner |
| **panel** | the 256×40 HUD strip drawn in the lower border |
| **tick** | one simulation step, 1/50 s |
| **fp / 8.8** | fixed-point integer where 256 == 1 pixel |
| **scenery object** | one of the 41 placeable maze pieces, keyed by its original 4-hex-digit id |
| **template** | one of the 48 authored room layouts (a list of placements) |
| **room type** | a template id assigned to a grid position |
| **lair** | one of the four rooms holding an amulet piece |
| **the Wulf** | the unkillable pursuer |
| **orchid** | a growing flower whose bloom applies an effect |
| **effect** | one of the six timed player states from an orchid |
| **content DB** | the read-only JSON under `data/` |
| **player DB** | the writable JSON in the user data dir |
| **`[CANON]` / `[RECON]` / `[NEW]`** | the fidelity tiers from §1.1 |

---

## 27. Agent working agreement

1. **Read this file before your first edit** in a session. If something here
   contradicts what the code does, this file wins — fix the code, or change
   this file deliberately and say so.
2. **Never put a game number in Java.** If you are typing a speed, a
   duration, a score, or a probability into a `.java` file, stop and put it
   in JSON.
3. **Never edit the map layout** in `original_map.json`. It is extracted
   canon. Fix problems with collision masks and landmark data.
4. **Never add a binary asset.** Art is JSON (§10), audio is synthesised
   (§18).
5. **Never break determinism** (§6.4). No floats, no wall clock, no
   `java.util.Random`, no unordered iteration in the sim.
6. **Run `mvn -q verify` before declaring anything done.** Report the actual
   result, including failures.
7. **Respect the fidelity tiers.** New ideas are welcome as `[NEW]` behind a
   default-off flag. Do not quietly improve the original's design.
8. **Do not add a verb** to the player's moveset (§11.8), a currency to the
   economy (§16.1), or music to `PLAYING` (§18.3) without asking the user
   first.
9. When you finish a milestone, update §24 with what actually landed and
   what slipped.

---

## 28. Distribution — the downloads, and Steam later

Players get the game from **GitHub Releases**: a download each for Windows
and Linux with no Java to install, and the jar for everyone else. **A Steam version is planned for some time in the future**
(§28.5): everything here is chosen so that it carries over, and nothing added
before then may make it harder.

### 28.1 What a release is

Push a tag `vMAJOR.MINOR.PATCH` and `.github/workflows/release.yml` does the
rest.

**The game is not ready for 1.0.** Releases are `v0.x.y` — `v0.2.0`,
`v0.3.0`, … — and GitHub marks each one a pre-release. Never tag `v1.0.0`, and
never let a version start with 1 anywhere, until the user says it is ready.


1. **jar** (Ubuntu): `mvn -Pheadless verify` — every test, validator and gate
   — then the shaded jar, renamed `wulfquest-VERSION.jar`.
2. **app**, once per platform, from that one jar: Linux x64 and Windows x64.
   Each runs `tools/package.sh`. **No macOS app for now** (§28.4): Mac
   players use the jar.
3. **release**: a GitHub Release for the tag with the two archives and the
   jar, its page from `.github/release-notes.md` plus the generated changes;
   a pre-release while the version is `0.x`.

Running the workflow by hand ("Run workflow") does steps 1–2 only, with
version `0.0.RUN`, and leaves the archives as workflow artifacts.

| archive | inside |
|---------|--------|
| `WulfQuest-V-windows-x64.zip` | `WulfQuest/WulfQuest.exe` and its runtime |
| `WulfQuest-V-linux-x64.tar.gz` | `WulfQuest/bin/WulfQuest` and its runtime |
| `wulfquest-V.jar` | the jar, for macOS and anyone with Java 21+ |

### 28.2 `tools/package.sh`

`tools/package.sh JAR VERSION` makes this machine's archive under
`target/package/`, the same on a dev box as in CI:

- **`jlink`** builds a runtime of only the modules `jdeps` finds in the jar
  (today `java.base`, `java.desktop`, `java.sql`) — about 40 MB packed.
- **`jpackage --type app-image`**: a launcher beside the jar and the runtime.
  **App images, not installers** (`.msi`, `.dmg`, `.deb`): nothing to install
  or uninstall, no WiX on Windows, and a folder is what Steam takes (§28.5).
- The app reads its data from inside the jar (§20.1) and saves to the user
  data dir (§21.1), so it runs from wherever it is unpacked.

### 28.3 The icon

The whole amulet — the wolf's face of §14.6: the four quarters of `amulet_piece` put together
(`wulf.render.AppIcon`), drawn nearest-neighbour at each size. The window and
the macOS dock get it at runtime. **`wulf.tools.IconForge OUT_DIR`** writes
the packagers' `WulfQuest.png`, `.ico` and `.icns` at build time, into
`target/` only: they are build output, like the jar, and §2.4 still holds — no
image file is ever committed, and the gate fails the build if one is.

### 28.4 Not signed, and no Mac app, yet

The builds are unsigned, so Windows SmartScreen warns on the first launch
(More info → Run anyway); the release page says so.

**macOS is left out on purpose, by the user's decision (2026-10-01).** An
unsigned `.app` meets Gatekeeper's warnings, and jpackage refuses a macOS
version starting with 0 while releases are `0.x` (§28.1). Mac players run
the jar with Java 21+. A Mac app comes back with signing — an Apple
Developer ID (yearly fee) plus notarization — which Steam on macOS needs
anyway (§28.5). `IconForge` still writes the `.icns` so that step is ready.
Signing on Windows (a code-signing certificate) is a later decision too.

### 28.5 Steam, later **[planned, not started]**

Wulf Quest is meant to reach Steam eventually. Nothing is built for it yet;
do not start without asking the user. What is already in place, and must stay
so:

- **The depot is the app image.** Steam ships a folder per platform and a
  launch command; `tools/package.sh`'s `target/package/app/` is that folder.
  Steam adds an upload step (`steamcmd`, a ContentBuilder script), not a new
  build.
- **No installers.** Steam installs and updates by itself.
- **Self-contained, location-free.** Data in the jar, saves in the user data
  dir, never a path relative to the install. Steam Cloud can then sync the
  player DB (§21) by pointing at that directory, with no code change.
- **Bundled runtime.** No system Java is assumed.

What Steam will add, when it comes:

- **Steamworks** (achievements, cloud API, overlay) through a native binding
  such as steamworks4j. §1.3 rules out network, telemetry and accounts, so it
  belongs in a Steam-only build or behind a default-off flag, and the GitHub
  build must still run with no Steam client.
- **The overlay** hooks OpenGL/Direct3D; whether it draws over a Java2D
  window has to be tried before it is promised.
- **macOS and signing**: the Mac app left out in §28.4 comes back here,
  signed and notarized, which Steam expects on macOS.
- **A store page** — screenshots, capsule art, a trailer. They live outside
  this repository (§2.4) and follow §2.1 like everything else: our art, our
  name, no original trade dress.

