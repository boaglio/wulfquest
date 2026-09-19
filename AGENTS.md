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
- Sabreman: 8-direction walk, sabre swing, 5 lives, death and respawn.
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
mvn -q test                    # unit tests only
mvn -q exec:java                                      # run the game
mvn -q exec:java -Dexec.args="--dev"                  # run with hot-reload + debug overlay
mvn -q exec:java -Dexec.args="--room 8,10"            # boot straight into a room
mvn -q exec:java -Dexec.args="--seed 12345"           # fixed PRNG seed
mvn -q exec:java -Dexec.args="--scale 3 --no-audio"
mvn -q exec:java -Dexec.mainClass=wulf.tools.MapAudit        # §22.3
mvn -q exec:java -Dexec.mainClass=wulf.tools.SpriteForgeCli  # §10.3
mvn -q exec:java -Dexec.mainClass=wulf.tools.HeadlessSim -Dexec.args="--ticks 100000"
mvn -q exec:java -Dexec.mainClass=wulf.tools.ReplayRunner -Dexec.args="replays/foo.json"
```

On a display-less machine, `HeadlessSim` is the only way to exercise the
game; it must never touch AWT (§22.4).

### 3.2 `./run.sh` — the convenience wrapper

`run.sh` wraps the commands above for day-to-day play. It is a wrapper, not a
second build system: `pom.xml` stays the source of truth, and CI calls Maven
directly. Never add build logic to the script that Maven does not also do.

```bash
./run.sh                 # build if stale, then play
./run.sh --scale 3       # unrecognised options pass straight to the game
./run.sh --headless      # load and validate the data, then exit
./run.sh --room 7,3      # open the room browser at a room
./run.sh --clean         # mvn clean first
./run.sh --rebuild       # compile even if nothing looks stale
./run.sh --no-build      # skip the build (fails if nothing is compiled)
./run.sh --test          # full mvn verify (tests + CI gates) first
./run.sh --help
```

Three behaviours worth knowing:

- **Editing `data/` does not trigger a rebuild.** Staleness is judged from
  `pom.xml` and `src/main` only, because the game reads `./data` from the
  working tree first (§20.1). Content edits are picked up on the next run with
  no compile at all — a cold run is ~4.5 s, a warm one ~0.4 s.
- It **appends** to `MAVEN_OPTS` rather than replacing it, so a developer's
  own heap or locale settings survive. It adds only
  `--sun-misc-unsafe-memory-access=allow`, which silences a deprecation banner
  Maven 3.9's bundled Guava emits on modern JDKs.
- It `exec`s the JVM, so the game's exit code is the script's: **0** clean,
  **2** data error (§17.1), **64** bad command-line option. A malformed
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
  tools/
    art/scenery_forge.py        generates the 41 scenery sprites, §10.5 (dev-time only)
    art/character_forge.py      generates Ranger Vale's frames, §10.5 (dev-time only)
    art/creature_forge.py       generates the 13 creature sprites and the puff, §10.5 (dev-time only)
    check-no-binaries.sh        CI gate, §2.4 — bound to the verify phase
    check-schemas.sh            validates every data file against its schema
  data/                         THE DATABASE. Read-only at runtime. Ships in the jar.
    schema/                     *.schema.json, one per data family, §20.2
    config/
      game.json                 tuning: tick rate, lives, feature flags
      display.json              palette, scale, border, CRT options
      input.json                keymaps
    world/
      original_map.json         [CANON] extracted grid + templates (§8)
      scenery.json              object id -> footprint, art ref, collision mask (§9)
      rooms.json                per-room overrides: biome, entity budget, props
      room_entities.json        spawn rules, biome markers, budgets, authored rooms (§12.6)
      landmarks.json            start room, exit, lair rooms, cave mouths (§14)
    entities/
      player.json               Sabreman/Ranger Vale stats + animation (§11)
      creatures.json            full roster (§12)
      wulf.json                 the Sabre Wulf (§13)
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
    render/                     Framebuffer, Blitter, Palette, Scaler, HudPainter
    audio/                      BeeperSynth, SfxPlayer, MusicPlayer
    input/                      InputMap, InputState, RecordedInput
    data/                       JsonDb, repositories, records, schema validation
    world/                      Room, RoomGrid, CollisionMask, RoomTransition
    sim/                        Simulation, entity systems, collision, effects
    sim/ai/                     one class per behaviour in §12.5
    ui/                         title, credits, hi-score entry, pause, game over
    tools/                      MapAudit, SpriteForgeCli, HeadlessSim, ReplayRunner
  src/test/java/wulf/           mirrors the above
  src/test/resources/golden/    golden framebuffer hashes, §22.5
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
pixels, wrapped in a coloured border. Object placements in
`original_map.json` prove the playfield is the **full 32×24 cells** — the
status panel therefore lives *in the border*, exactly as Ultimate did it.

```
virtual canvas: 320 x 256
+--------------------------------------------------+  y=0
|                border (16 px)                    |
|   +------------------------------------------+   |  y=16
|   |                                          |   |
|   |   PLAYFIELD  256 x 192  = 32 x 24 cells  |   |
|   |   room origin at (32,16)                 |   |
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
  "playfield": { "x": 32, "y": 16, "w": 256, "h": 192, "cell": 8, "cols": 32, "rows": 24 },
  "panel":     { "x": 32, "y": 208, "w": 256, "h": 40 },
  "scale":     { "default": 4, "min": 1, "max": 6, "integerOnly": true },
  "border":    { "idleColour": "black", "flashOnEvent": true },
  "crt":       { "scanlines": false, "glow": false, "attributeClash": false },
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

- `crt.scanlines` — every odd output row multiplied by 0.82 luminance, after
  upscale.
- `crt.attributeClash` — quantise the playfield to one ink + one paper per
  8×8 cell, resolving conflicts by the last sprite drawn. Faithful, ugly,
  fun. Never on by default.
- `spriteFlicker` — when 3+ sprites overlap one cell, alternate which draws
  on odd/even ticks.
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
| room | 32×24 cells = 256×192 px | one flip-screen |
| map | 16×16 rooms = 256 rooms = 4096×3072 px | **[CANON]** |

Room addressing is `(col, row)` with `col` 0..15 left→right and `row` 0..15
**top→bottom** (row 0 is north). Canonical id string is `"c,r"`, e.g.
`"8,10"`. Linear index is `row * 16 + col`.

### 7.2 Room composition

A room is not a tilemap. It is a **list of placed scenery objects** —
exactly as the original stored it. Each placement is `{ object, x, y }` with
`x,y` in **cells**, origin top-left of the playfield.

At load time each room is baked into a `CollisionMask`: a
`32 × 24` bitset, one bit per cell, `true` = impassable.

```
Room load:
  1. roomType = original_map.json.roomTypeGrid[row][col]
  2. placements = original_map.json.templates[roomType]
  3. for each placement: OR the object's collisionCells into the mask,
     offset by (x, y); clip to the 32x24 bounds
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
positions live in WORLD pixels (0..4096 x 0..3072), never room-local
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
read-only reference. Its shape:

```json
{
  "gridW": 16, "gridH": 16,
  "startCol": 8, "startRow": 10,
  "roomTypeGrid": [[33,32,...], ... 16 rows of 16 ...],
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
| Templates actually used on the grid | **45** (ids **1..45**; **0, 46, 47 are unused**) |
| Distinct scenery objects | **41** |
| Object placements across templates | **919** |
| Object placements across all 256 rooms | **5105** |
| Objects per template | 13 (template 18) to 26 (templates 15, 16) |
| Max extent of any placement | right edge exactly **32** cells, bottom edge exactly **24** cells |

The placement coordinate space is **cells**, and the graphics' pixel sizes
are exactly `4 × (cells × 8)` — the source PNGs were 4× upscales. The
footprints in §9 are already divided down; use them directly.

### 8.1 The room type grid

Row 0 is north. This is the map. Do not "improve" it.

```
      c0  c1  c2  c3  c4  c5  c6  c7  c8  c9 c10 c11 c12 c13 c14 c15
 r0   33  32  32  31  34  32  32  34  32  31  43  32  31  32  34  42
 r1   45  16  11  10   5  14  22   8  11   8  11  16   5  13  12  40
 r2   27   6   6  22  12  14   9  21   6   5   8  21  21   9   5  41
 r3   27  21   5  14  19   9   6   7  10  15   5  12  12  29   5  40
 r4   28  10   6   9   1  21   6  22  13  13  10  21   6   1   5  41
 r5   28  16  19   5  13  10   5  14   9   9  22   8  21  22  12  40
 r6   28   8   5  12  13  14   5  39  12   5  14   6   5  13   8  41
 r7   27   5   8   1   9  22  12  38  36  38  14   5   8  22   8  44
 r8   30  12  12  13  29   9   6  37  35  37   9   6   7  13  10  24
 r9   27  19  21  11  12   8   5   3   2   3   4   5  14   9  11  40
 r10  27   7  10  15   6  21   6   9   1   9   5   8   9   6   7  41
 r11  30  16  11  13  10   1   7   8  11  10   6  12   8  12  13  44
 r12  28  12  29   9  11  13  16   5  10  22   8   1  21  21  11  44
 r13  27  21  19  19   7  16   7  12  13  14   5  13  10   6  21  24
 r14  26   1  15   6   9   7  16  15  11  16   6  11  13  10   6  24
 r15  25  18  20  17  17  18  17  18  17  17  17  17  18  18  17  23
```

### 8.2 Region structure (derived, `[CANON]` observations)

- **Boundary ring** — types **17–20, 23–34, 40–45** appear only on row 0,
  row 15, col 0, col 15. These are the river/mountain edge: impassable
  outward, walkable inward.
- **Interior** — types **1–16, 19, 21, 22, 29** plus the central-landmark
  types **35–39** fill rows 1–14, cols 1–14: the 196 playable jungle rooms.
  The interior and boundary type sets are disjoint — the ring has its own
  vocabulary of templates — and `MapDataTest` locks both sets exactly.
- **Central landmark** — a mirrored structure around col 8, rows 6–9, built
  from otherwise-unique types:
  ```
        c7  c8  c9  c10
   r6   39  12   5        <- 39 unique
   r7   38  36  38        <- 36 unique, 38 mirrored pair
   r8   37  35  37        <- 35 unique, 37 mirrored pair
   r9    3   2   3    4   <- 2 and 4 unique, 3 mirrored pair
  ```
  Types 36 and 39 contain the water object `93C4` — this is the **central
  lake**. The start room `(8,10)` sits directly south of it. Name it
  **the Still Water** in `landmarks.json`. Its mirror symmetry is the
  player's primary navigation anchor; preserve it exactly.
- **Rocky rooms** — templates 6, 7, 10, 14, 15, 16 mix the yellow rock
  objects (`847C`, `872A`, `8558`, `8B80`, `8D3C`, `8C5C`, `8CCC`, `83D2`,
  `8427`, `86DA`, `8702`) and the two bone-strewn walls (`8806`, `89C3`).
  Treat these as the **Bonefields** biome (§12.6).
- **Hut rooms** — template 5 contains the single hut object `8E18` and is
  used in **21 rooms**: `(4,1) (12,1) (9,2) (14,2) (2,3) (10,3) (14,3)
  (14,4) (3,5) (6,5) (2,6) (6,6) (9,6) (12,6) (1,7) (11,7) (6,9) (11,9)
  (10,10) (7,12) (10,13)`. These are the tribesmen's villages: they get a
  guaranteed elevated tribesman spawn (§12.6).
- **Arch rooms** — template 7 contains the stone arch `85C8`, used in 8
  rooms: `(7,3) (12,8) (1,10) (14,10) (6,11) (4,13) (6,13) (5,14)`. These
  are the **cave mouths** (§14.5).

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
| `7298` | 2×5 | 16×40 | 104 | **583** | single narrow tall fern, one slim stem | greens |
| `78F2` | 3×3 | 24×24 | 76 | **483** | small palm, banded trunk | green + red |
| `7947` | 2×3 | 16×24 | 62 | **456** | squat leafy plant with one fruit | green + yellow |
| `7462` | 3×7 | 24×56 | 37 | 298 | tall spike plant topped with a bloom | green + magenta |
| `71B3` | 5×5 | 40×40 | 67 | 294 | palm cluster, two crossing trunks | greens + red + cyan |
| `72F6` | 8×5 | 64×40 | 50 | 247 | wide broad-leaf bank, hanging fruit | green + red + yellow |
| `7523` | 8×7 | 64×56 | 46 | 247 | palm grove, dense, small ground detail | greens + red |
| `7981` | 4×11 | 32×88 | 34 | 239 | very tall narrow conifer/cypress, crowned top | green + yellow |
| `771F` | 5×7 | 40×56 | 29 | 207 | fern with a gourd at its base | green + yellow |
| `70BC` | 9×3 | 72×24 | 33 | 204 | long low leafy bank with flowers | green + red + magenta |
| `8F2A` | 6×7 | 48×56 | 42 | 194 | reed cluster, vertical stems | green + red |
| `872A` | 4×6 | 32×48 | 24 | 176 | tall rock wall slab, vertical strata | yellow |
| `785E` | 4×4 | 32×32 | 23 | 171 | spiky agave fan, radial blades | bright green |
| `95CD` | 6×3 | 48×24 | 47 | 151 | low shrub row with one red plant and a fruit | green + red + yellow |
| `955D` | 4×3 | 32×24 | 42 | 126 | dense blocky hedge | bright green |
| `8702` | 4×1 | 32×8 | 18 | 120 | low rock ledge strip (variant A) | yellow |
| `847C` | 8×3 | 64×24 | 11 | 112 | wide horizontal rock strata shelf | yellow |
| `90A8` | 8×11 | 64×88 | 23 | 94 | the big one: mixed palm/banana grove, gourd at base | greens + red + yellow |
| `7C0C` | 7×3 | 56×24 | 23 | 71 | distant mountain range, snow caps | magenta + white |
| `7E4B` | 8×7 | 64×56 | 18 | 68 | large mountain massif, heavy snow | magenta + white |
| `86DA` | 4×1 | 32×8 | 6 | 56 | low rock ledge strip (variant B, pebbles) | yellow |
| `7BB7` | 3×3 | 24×24 | 13 | 52 | single snowy peak | magenta + white |
| `7CCD` | 7×6 | 56×48 | 13 | 38 | scattered peaks, two tiers | magenta + white |
| `8047` | 7×6 | 56×48 | 13 | 38 | mountain range with cloud gaps | magenta + white |
| `81C5` | 7×7 | 56×56 | 13 | 38 | twin peaks, deep valley between | magenta + white |
| `8558` | 4×3 | 32×24 | 2 | 36 | short rock column | yellow |
| `7B11` | 6×3 | 48×24 | 12 | 34 | low mountain ridgeline | magenta + white |
| `9673` | 1×3 | 8×24 | 8 | 32 | single thin grass tuft (the filler piece) | green |
| `8B80` | 4×6 | 32×48 | 4 | 32 | rock wall slab, smoother face | yellow |
| `8D3C` | 4×6 | 32×48 | 4 | 32 | rock pillar, cracked | yellow |
| `83D2` | 3×3 | 24×24 | 2 | 26 | small rock chunk (variant A) | yellow |
| `8427` | 3×3 | 24×24 | 2 | 26 | small rock chunk (variant B) | yellow |
| `8E18` | 6×5 | 48×40 | 1 | 21 | **the hut**: thatched dome roof on stilts | yellow + red |
| `8806` | 7×7 | 56×56 | 2 | 20 | rock wall with a bleached skeleton half-buried | yellow + white |
| `89C3` | 7×7 | 56×56 | 2 | 20 | rock wall with scattered bones | yellow + white |
| `8C5C` | 4×3 | 32×24 | 2 | 16 | cracked rock face (variant A) | yellow |
| `8CCC` | 4×3 | 32×24 | 2 | 16 | cracked rock face (variant B) | yellow |
| `83AA` | 1×4 | 8×32 | 1 | 15 | thin rock spur / stalagmite | magenta |
| `85C8` | 6×5 | 48×40 | 1 | 8 | **the stone arch**: free-standing cave mouth | white/grey |
| `93C4` | 9×5 | 72×40 | 6 | 7 | **water**: pool with ripple lines, walkable-look but solid | bright blue |
| `8382` | 1×4 | 8×32 | 1 | 1 | thin rock spur, lone (used once, at room `(10,9)`) | magenta |

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
the original's *character*: instant, weightless, grid-free, slightly faster
horizontally than vertically, and sliding along walls.

### 11.1 `data/entities/player.json`

```json
{
  "schemaVersion": 1,
  "id": "player",
  "displayName": "Ranger Vale",
  "sprite": { "size": { "w": 16, "h": 24 }, "origin": "bottom-centre" },
  "collisionBox": { "x": -5, "y": -9, "w": 10, "h": 9 },
  "speed": { "xFp": 384, "yFp": 256, "diagonalScaleFp": 218 },
  "lives": { "start": 5, "max": 9, "extraAt": [15000, 40000, 75000, 120000] },
  "spawn": { "invulnTicks": 100, "blinkPeriodTicks": 4,
             "insideRoomPx": { "left": 8, "right": 8, "top": 24, "bottom": 0 } },
  "death": { "animTicks": 40, "freezeTicks": 20, "keepAmulet": true },
  "sabre": {
    "windupTicks": 3, "activeTicks": 6, "recoverTicks": 3, "cooldownTicks": 6,
    "reachPx": 14, "thicknessPx": 12, "diagonalOffsetPx": 4,
    "moveSpeedScaleFp": 256,
    "repelWulfPx": 24, "repelWulfStunTicks": 30
  },
  "animations": { "...": "see §10.2" }
}
```

### 11.2 Speed **[RECON]**, calibrated to the original's screen-crossing time

| Axis | Fixed-point | px/tick | px/s | time to cross a room |
|------|-------------|---------|------|----------------------|
| Horizontal | `384` | 1.5 | 75 | 256 px ≈ **3.4 s** |
| Vertical | `256` | 1.0 | 50 | 192 px ≈ **3.8 s** |

The asymmetry is deliberate and **required**: it is what makes the top-down
jungle read as a perspective view rather than a plan. Do not "fix" it.

Diagonal: both components are applied, each scaled by
`diagonalScaleFp = 218` (≈ 0.85), giving ~1.28 px/tick X and ~0.85 px/tick Y.
A diagonal must be *slightly* faster than a cardinal in total distance (the
original rewarded diagonals) but must not be the strictly dominant way to
move. Verified in `MovementFeelTest`.

### 11.3 No acceleration, no inertia **[CANON]** feel

Velocity is a pure function of the input held **this tick**:

```
vx = (right ? +1 : 0) + (left ? -1 : 0)
vy = (down  ? +1 : 0) + (up   ? -1 : 0)
```

- Release the key → velocity is zero **the same tick**. No deceleration
  ramp, no coyote frames, no input buffering, no dash.
- Opposing keys held simultaneously → that axis is zero.
- Sub-pixel remainder accumulates in the fixed-point position and is never
  rounded away between ticks.

### 11.4 Facing

- Facing is one of 8 directions, updated whenever velocity is non-zero.
- Facing **persists** when velocity returns to zero (you keep looking where
  you were going). This matters: the sabre swings along `facing`.
- The sprite has three view sets — `side` (mirrored for left), `up`, `down`.
  Diagonals use the `side` set, mirrored by the X sign; if X is zero the
  diagonal is impossible, so no special case needed.

### 11.5 Walk animation cadence **[RECON]**

- 4 frames, cycling `0,1,2,3`, advancing every **5 ticks** (10 fps) while
  moving. One full cycle = 20 ticks = 0.4 s = 30 px travelled horizontally —
  roughly one stride per 30 px, which reads correctly at 16 px sprite width.
- Idle: snap to frame 0 **immediately** on stopping (not a freeze of the
  current frame). Original-style, no idle breathing animation.
- The animation clock is driven by **ticks moved**, not wall time, so slow
  and fast effects (§15) visibly change stride rate. Optional refinement
  (`[NEW]`, flag `features.distanceDrivenGait`): advance the frame every
  `7` px of distance travelled instead, so the gait stays locked to the
  ground at every speed.

### 11.6 The sabre swing

State machine, 12 ticks total, non-interruptible once started:

```
tick 0..2    WINDUP   blade not drawn, no hitbox
tick 3..8    ACTIVE   blade drawn, hitbox live, kills on first overlap per creature
tick 9..11   RECOVER  blade retracting, no hitbox
then         COOLDOWN 6 ticks before another swing is allowed
```

- The player **can still move** during the swing at full speed
  (`moveSpeedScaleFp = 256` = ×1.0). The original let you walk and slash;
  keep it.
- **The pose follows the phases exactly** (`driver: "sabrePhase"`, §10.2):
  windup pose during WINDUP, strike pose during ACTIVE, recover pose during
  RECOVER. The strike pose is on screen precisely while the blade can hit.
- **A swing on the move keeps walking**: while moving, the same pose is drawn on
  the current gait's legs (`gaitVariants`, §10.2). Fixed-leg swing frames at
  full speed make Vale skate.
- **The blade is drawn from the hand, the hitbox lies on the ground.** The
  hitbox is projected from the feet box, on the ground plane where creatures'
  feet are; that is what collides. The visible blade comes out of the strike
  frame's `hand` anchor with the hitbox's exact reach and timing — horizontal
  for side views, tilting by `diagonalOffsetPx` for diagonals, vertical for up
  and down. Drawing it from the hitbox itself put the blade at knee height, and
  between the legs or through the pack facing down or up (found in M3).
- Hitbox: a rectangle `reachPx` (14) long and `thicknessPx` (12) wide,
  projected from the player's collision-box centre along `facing`. For
  diagonals, project along the dominant axis and offset by 4 px on the
  other — do not implement rotated rectangles.
- One creature may be hit **once per swing** (keep a per-swing hit set).
- The hitbox is **not** blocked by scenery. The blade sweeps over the
  foliage line.
- Holding the fire key does **not** auto-repeat. Each swing needs a fresh
  key-down edge. This is a skill-expression choice and matches the period.

### 11.7 Death and respawn

```
contact with any hostile while not invulnerable and not immune
  ──► lives--
  ──► DYING state, 40 ticks (4-frame animation, 10 ticks each)
        sim frozen for creatures? NO — creatures keep moving, it is a diorama
        border flashes red on ticks 0,4,8,12 (Ultimate style)
  ──► 20 ticks of black/frozen
  ──► if lives > 0: respawn in the SAME room, at the room's safe-spawn point
        (nearest non-solid cell to the entry point used last, breadth-first,
        with the whole sprite inside the room — spawn.insideRoomPx — because an
        entry point straddles the edge, where the flip-screen clip cuts the
        sprite in half),
        with 100 ticks of invulnerability (blink 2 on / 2 off)
        the room's creatures are re-rolled from scratch
  ──► if lives == 0: GAME_OVER
```

- **The amulet is kept on death** (`keepAmulet: true`). Losing pieces would
  make a 256-room map miserable. This is the one place we choose kindness;
  it is in JSON if a purist wants it flipped.
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

The original's creature table has **not** been extracted into this repo. The
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
| `wulf` | The Wulf | 32×22 | 1.90 (486) | 1.35 (346) | ∞ | `CHASE_DIRECT` | — | anywhere | §13 |
| `guardian_*` | the four guardians | 32×28 | 1.10 (282) | 0.80 (205) | ∞ | `GUARD_ORBIT` | — | lair rooms | §14; arrives in M6 |
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

## 13. The Wulf **[CANON]** concept, **[RECON]** numbers

The pursuing beast is the game's identity. Get this wrong and nothing else
matters. In code and in player-facing text it is **the Wulf** — never the
original creature's name (§2.1, rule 4).

### 13.1 Design intent

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
  "speed": { "xFp": 486, "yFp": 346 },
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
  "audio": { "warning": "wulf_howl", "loop": "wulf_growl", "parry": "wulf_parry", "loopSilentAtPx": 300 }
}
```

Integers only, like every file the simulation reads. Three Wulf numbers live
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

`WulfTest` pins every rule above on hand-built worlds — a corridor where only
one edge fits, and a pen whose 16 px door lets the player through but never the
Wulf — plus the shipped chance (about one first room entry in seven, measured
over 2 000 seeds). `DeterminismTest` runs it; `HeadlessSimTest` requires it to
appear; `replays/wulf_escape.json` (§22.6) is a chase survived across four rooms.

---

## 14. Guardians, the amulet, the exit

### 14.1 The quest **[CANON]** structure

Four amulet pieces are held in four lair rooms, one per map quadrant. Each is
watched by an unkillable guardian beast. With all four pieces the player can
pass the Keeper of the Arch and escape. That is the whole game.

### 14.2 Lair and landmark placement **[RECON]** — see §25

`data/world/landmarks.json`:

```json
{
  "schemaVersion": 1,
  "fidelity": "recon",
  "startFacing": "N",
  "exit": {
    "room": "6,11", "arch": "85C8",
    "zone": { "x": 108, "y": 104, "w": 40, "h": 24 },
    "keeper": { "x": 128, "y": 120 },
    "requiresPieces": 4, "escapeWalkTicks": 40
  },
  "lairs": [
    { "id": "lair_nw", "room": "2,7",  "guardian": "guardian_hippo",      "piece": { "id": "amulet_nw", "frame": "nw", "slot": 0 }, "pedestal": { "x": 128, "y": 88 } },
    { "id": "lair_ne", "room": "12,7", "guardian": "guardian_rhino",      "piece": { "id": "amulet_ne", "frame": "ne", "slot": 1 }, "pedestal": { "x": 128, "y": 88 } },
    { "id": "lair_sw", "room": "6,14", "guardian": "guardian_boar",       "piece": { "id": "amulet_sw", "frame": "sw", "slot": 2 }, "pedestal": { "x": 128, "y": 88 } },
    { "id": "lair_se", "room": "8,14", "guardian": "guardian_wildebeest", "piece": { "id": "amulet_se", "frame": "se", "slot": 3 }, "pedestal": { "x": 128, "y": 88 } }
  ],
  "amulet": { "sprite": "amulet_piece", "size": { "w": 16, "h": 16 }, "collisionBox": { "x": -8, "y": -16, "w": 16, "h": 16 },
              "pickupFlashTicks": 8, "flyToPanelTicks": 24 },
  "caveMouths": ["7,3", "12,8", "1,10", "14,10", "4,13", "6,13", "5,14"],
  "hint": { "messageTicks": 90, "oncePerLife": true },
  "stillWater": { "name": "The Still Water", "rooms": ["7,6", "…"] }
}
```

The start room is **not** here: it is extracted canon (§8), and duplicating it
would let the two disagree. Only the direction Vale faces at the start is.

**Chosen against the real map, and measured, in M6:**

- **The lairs are balanced, not deepest.** One per quadrant, each with a clear
  80 px square at its centre for a pedestal and an orbit. The deepest rooms of
  each quadrant lie 22, 22, 10 and 8 rooms from the start — the start room is a
  southern one, so "the deepest room of each quadrant" is wildly unfair. These
  four are 11, 11, 10 and 8 rooms away: a spread of 3, inside the ±4 this
  section asks for, checked by `LandmarkBalanceTest` through
  `MapAudit.roomDistances` (a 0-1 flood: free within a room, one per crossing).
- **Pedestals sit at (128, 88)**, that clear centre. The (128, 96) of this
  section's first draft is solid scenery in every room it named.
- **The way out is a real arch.** The start room has no arch in the extracted
  map, and its largest clear space is 32 px — nowhere to put one without
  inventing scenery over canon. The exit is therefore `6,11`, the arch room
  nearest the start (3 rooms), which keeps this section's intent: the player
  meets the way out early and finds it barred.
- **The arch's own cells are solid**, so nobody can stand under it. The exit is
  the `zone` in front of it — where the Keeper stands, and where a shrine is
  touched. Every arch room places its arch identically, at cells (13, 8), so one
  rectangle serves all of them.
- `LandmarkValidator` checks the lot at load: the rooms exist and hold an arch,
  one lair per quadrant, ids and slots unique, and that the guardian, the
  quarter, the Keeper and its step aside all fit where they are put.

### 14.3 Guardians `data/entities/guardians.json`

Four beasts, `GUARD_ORBIT`, unkillable, one per lair, each a larger sibling of a
roster species recoloured to its quarter's accent, so the player reads the
family and the scale at once:

| id | based on | colour | lair |
|----|----------|--------|------|
| `guardian_hippo` | `hippo` | bright green | NW `2,7` |
| `guardian_rhino` | `rhino` | bright blue | NE `12,7` |
| `guardian_boar` | `boar` | bright red | SW `6,14` |
| `guardian_wildebeest` | `wildebeest` | bright yellow | SE `8,14` |

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
- Within `lungePx` it breaks off and charges for `lungeTicks`, then returns to
  the ring. A swing never hurts it: `repelPx` back, `stunTicks` stunned, white
  flash, no score — the Wulf's parry (§13.5) by another name.
- Lair rooms hold **no other creatures** and no orchids: `Content.ecosystem()`
  keeps the populator out of them and out of the exit room. The Wulf never
  appears in them **and never follows into them** (§13.4) — a chase ends at a
  sanctuary's door, unscored.

### 14.4 The Keeper of the Arch

- A `BLOCK_STATIC` figure standing in the exit zone in front of the arch,
  `24x32 px`, lethal to touch.
- While `piecesHeld < 4` it stands there. Walking into it kills you — except
  from the front, within `game.json → exit.keeperNudgeZonePx` (16 px), where you
  are pushed back `exit.keeperNudgePx` (8 px) instead, so brushing the way out in
  your first minute is not a death.
- When the last quarter is taken — wherever the player is — it **steps aside**
  over `exit.keeperStepAsideTicks` (60) and `guardians.json → keeper.stepAsidePx`
  (36 px), through a four-frame animation, and is harmless from then on.
- Standing in the zone with the whole amulet then starts the escape (§14.7).

### 14.5 Cave mouths **[RECON]**

The arch rooms of §8.2 are shrines: touch the spot in front of the arch — the
same rectangle as the way out's, since every arch room is laid out alike — and
the panel names the way to the nearest quarter still out there for
`hint.messageTicks` (90): `AMULET STIRS TO THE SOUTH-WEST`. Once the amulet is
whole it names the way to the arch instead. **Once per room per life**
(`hint.oncePerLife`), cleared by a death, so it cannot be tapped for a compass.
Seven rooms, not eight: the way out's own arch belongs to the Keeper. Flag
`features.caveHints`, default on — 256 rooms with no guidance is unfair to a
player who has no inlay map.

### 14.6 Amulet pieces

- Sprite: a quarter of a 16x16 amulet, each quarter its guardian's accent, rim
  in plain white so the yellow quarter still reads.
- Pickup: box overlap with the player's feet box. Awards `5000`, flashes the
  border bright white for `pickupFlashTicks` (8), and flies to its panel slot
  over `flyToPanelTicks` (24). The arpeggio arrives with the synthesiser (M8).
- The panel shows the amulet assembling: four slots, held quarters drawn from
  the sprite at half size, missing ones as silhouettes in `white` (§17.3).
- **Kept on death** (`player.json → death.keepAmulet`): a quarter taken is taken.

### 14.7 The win sequence

```
stand in the exit zone with 4 pieces
  ──► control locked (mode ESCAPING)
  ──► Vale walks up into the arch over escapeWalkTicks (40), the playfield
      going black a row at a time
  ──► mode WON, and the tally:
        collected score
        + escape bonus            10000
        + time bonus              max(0, 30000 - ticksElapsed / 2)
        + lives remaining bonus   lives * 2500
  ──► fire starts a new game (hi-score entry and the title screen: M8)
```

The tally holds fire for 100 ticks first, so the press that won the game cannot
skip it.

---

## 15. Orchids — the drug system **[CANON]** concept, **[RECON]** effects

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
- A room's orchid anchors are **derived**, not authored: up to
  `anchors.perRoom` (3) spots at least `anchors.minSpacingPx` (40) apart, no
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

### 15.2 The effect table

```json
{
  "schemaVersion": 1,
  "fidelity": "recon",
  "sprite": "orchid",
  "cycle": { "seedTicks": 150, "sproutTicks": 100, "budTicks": 80, "bloomTicks": 250, "wiltTicks": 100, "swayTicks": 20 },
  "anchors": { "perRoom": 3, "minSpacingPx": 40, "attempts": 24, "insetPx": 24 },
  "orchids": [
    { "colour": "yellow",  "weight": 22, "effect": "HASTE",     "ticks": 500, "speedScaleFp": 512, "score": 50 },
    { "colour": "cyan",    "weight": 20, "effect": "TORPOR",    "ticks": 400, "speedScaleFp": 128, "score": 25 },
    { "colour": "magenta", "weight": 18, "effect": "REVERSAL",  "ticks": 600, "speedScaleFp": 256, "score": 25 },
    { "colour": "green",   "weight": 15, "effect": "IMMUNITY",  "ticks": 400, "speedScaleFp": 256, "score": 75 },
    { "colour": "white",   "weight": 15, "effect": "DELIRIUM",  "ticks": 300, "speedScaleFp": 256, "score": 25 },
    { "colour": "blue",    "weight": 10, "effect": "STILLNESS", "ticks": 250, "speedScaleFp": 256, "score": 75 }
  ],
  "delirium": { "everyMinTicks": 20, "everyRandomTicks": 20, "holdTicks": 10 },
  "immunity": { "flashPeriodTicks": 4 }
}
```

One row per flower, colour and effect both unique, so the player can learn one
from the other. A colour needs both halves of the palette — `yellow` for the
wilt, `brightYellow` for the bloom — and a frame of each stage drawn for it, or
the load fails (`OrchidValidator`).

| effect | what it does |
|--------|--------------|
| `HASTE` | Player speed ×2.0. Exhilarating and lethal — you overshoot into creatures. Walk animation rate doubles. |
| `TORPOR` | Player speed ×0.5. The Wulf becomes a death sentence. |
| `REVERSAL` | Left↔right **and** up↔down inverted. Applied at the input-mapping layer, so recorded replays stay faithful. |
| `IMMUNITY` | Player cannot be killed by contact (still cannot pass `BLOCK_STATIC`). Player flashes between its normal palette and bright white every 4 ticks. |
| `DELIRIUM` | Every `20 + rng(20)` ticks, the input direction is overridden by a random 8-direction for `10` ticks. Panic. |
| `STILLNESS` | All creatures in the current room (not the Wulf) freeze for the duration. Drawn in the non-bright version of their palette. The one unambiguously good flower. |

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

## 16. Loot and scoring

### 16.1 What exists to collect **[CANON]** set

| item | source | effect | score |
|------|--------|--------|-------|
| amulet piece ×4 | lair pedestals | quest progress | 5000 each |
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
| `features.treasures` | `idol` (2000 pts), `gem` (750), `tusk` (400) | scattered score pickups, 1 per 12 rooms by `roomSeed` |
| `features.extraLifeIdol` | `idol_life` | +1 life, one per game, in a fixed far-corner room |
| `features.bonusRooms` | — | do not build this without asking the user first |

### 16.3 Score events

```json
{
  "schemaVersion": 1,
  "events": {
    "amuletPiece": 5000,
    "escapeBonus": 10000,
    "timeBonusMax": 30000,
    "timeBonusTicksDivisor": 2,
    "lifeRemainingBonus": 2500,
    "roomFirstVisit": 10,
    "wulfEvaded": 250
  }
}
```

- `roomFirstVisit: 10` gently rewards exploration (256 rooms = 2560 points
  for a full sweep).
- `wulfEvaded: 250` is awarded when a pursuit ends in a give-up, not a death.
- Extra lives at the `player.json → lives.extraAt` thresholds, capped at
  `lives.max` (9). A 10-tick panel flash and a rising 3-note jingle.
- Hi-score is shown on the panel next to the score at all times.

---

## 17. Game rules, states, HUD

### 17.1 Game state machine

```
BOOT ──► TITLE ──► (key) ──► PLAYING ──► ...
 │                   │                     │
 │                   ├─► CREDITS           ├─► PAUSED  (P; sim frozen, panel dims)
 │                   ├─► HISCORES          ├─► DYING   (§11.7)
 │                   └─► KEY_CONFIG        ├─► GAME_OVER ──► HISCORE_ENTRY ──► TITLE
 │                                         └─► WON ──► TALLY ──► HISCORE_ENTRY ──► TITLE
 └─► fatal data error ──► DATA_ERROR (renders the failing file and the reason, exits on key)
```

`DATA_ERROR` is a real state, not an exception dump. A JSON typo must produce
a readable in-game screen naming the file, the JSON pointer, and the
expected shape. This is the single highest-value debugging feature in a
data-driven game; build it in M1.

### 17.2 Title screen

- Game title in the 8×8 font, our own wordmark, animated colour cycling
  through the palette per row.
- An attract-mode demo: `ReplayRunner` playing `replays/attract.json` behind
  a dimmed overlay after 20 s idle. Any key returns to the title.
- Lines: `PRESS FIRE TO BEGIN`, `1 KEYS  2 JOYSTICK`, `H HI-SCORES`,
  `C CREDITS`, and the attribution line from §2.5 in small type.

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
playfield stays visible, dimmed to the non-bright palette; the panel shows
`PAUSED`. Pause must not be usable as an exploit to study a Wulf pursuit at
leisure — it is fine that it is, actually; the original had no pause and we
are not shipping a competitive game.

### 17.5 Game over

`GAME OVER` in the 8×8 font over the frozen playfield, 150 ticks, then
hi-score entry if qualified, then title.

---

## 18. Audio — procedural 1-bit beeper

### 18.1 Approach

All sound is generated in-process. No files. A single mixer thread feeds a
`SourceDataLine` at `44100 Hz, 16-bit, mono`, with a 2048-frame buffer.

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
- **No in-game music.** During play there is only the jungle: footsteps,
  creature noises, and the growl. Silence is the tension. Do not add
  background music to `PLAYING`.

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
      "devKill": ["K"], "devMask": ["M"], "devWulf": ["H"]
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
  Wulf (§13.3); it is not `W`, which is up.
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
| `PlacementBoundsValidator` | every placement + footprint fits within 32×24 cells |
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

Resolved by `data/UserDataDir.java`. Overridable with `--data-dir <path>`
for tests. Tests must **never** write to the real user dir — they use a
JUnit `@TempDir`.

### 21.2 `highscores.json`

```json
{
  "schemaVersion": 1,
  "entries": [
    { "name": "AJB", "score": 48300, "pieces": 4, "ticks": 214500, "date": "2026-09-12" }
  ]
}
```

Top 10, sorted descending by score then ascending by `ticks`. Name is
exactly 3 chars from `A–Z` and space, entered with the classic
up/down/fire selector. Seeded on first run with 10 plausible entries from
`data/config/default_highscores.json` (`[NEW]`, keeps the table from looking
broken at 0 points).

### 21.3 `settings.json`

Scale, audio on/off + volume, input profile, CRT toggles, chosen feature
flags. Written on change. **Feature flags in settings override
`game.json`** — `game.json` is the shipped default, settings are the
player's choice.

### 21.4 `progress.json` (`[NEW]`, flag `features.saveInProgress`, default off)

A full mid-run snapshot: `runSeed`, RNG state, room address, player position
and lives, pieces held, taken loot set, per-room orchid clocks, visit
counts, elapsed ticks, score. The original had no save; this is off by
default. If implemented, the snapshot must round-trip bit-exactly — assert
it in `SaveRoundTripTest` by hashing sim state before and after.

### 21.5 `stats.json` (`[NEW]`, default on, purely local)

Lifetime counters: rooms visited, creatures killed by species, deaths by
cause, orchids by colour, Wulf encounters, Wulf deaths, best time. Shown on
a title-screen stats page. Never transmitted anywhere — there is no network
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

- `MovementSpeedTest` — from a clear room centre, holding right for 171
  ticks moves exactly 256 px ±1; holding down for 192 ticks moves exactly
  192 px ±1.
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

### 22.6 Replay regression

`replays/*.json` hold recorded per-tick input plus the expected sim-state
hash at every 50th tick. `ReplayRunner` re-executes them. Ship at least:

- `replays/attract.json` — the title-screen demo (doubles as a test). (M8)
- `replays/lair_nw.json` — start → NW lair → piece → back. (M6)
- `replays/wulf_escape.json` — a Wulf pursuit survived across 4 rooms. (**M5, shipped**)
- `replays/full_run.json` — a complete 4-piece win, forged by `FullRunForge`
  (**M6**).

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

### M8 — Shell and polish (3 days)

- Title, credits, hi-scores, entry screen, pause, game over, attract mode,
  stats, settings, audio (all sfx + 3 tunes), border flashes, key config.

**Accept when:** the game is playable start to finish with no keyboard
shortcut knowledge, from a double-clickable jar.

### M9 — Hardening (2 days)

- Golden frames, all replays, the slow full-run test, README and controls
  documentation, `mvn package` producing a runnable fat jar.

**Accept when:** `mvn -q verify` is green from a clean clone on a machine
with no display (headless profile skips the window tests).

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
| Q1 | The exact creature roster and per-room creature assignment table | 13 invented species, biome-weighted | `creatures.json`, `room_entities.json` |
| Q2 | The four guardians' species, colours, and lair rooms | hippo/rhino/boar/wildebeest at `2,2 13,2 2,13 13,13` | `landmarks.json`, `guardians.json` |
| Q3 | The exit's actual location and its guard condition | start room `8,10`, needs 4 pieces | `landmarks.json` |
| Q4 | The orchid colour→effect mapping and durations | 6 colours per §15.2 | `orchids.json` |
| Q5 | Exact player speed in px/frame, and whether it was frame-quantised | 1.5 / 1.0 px/tick | `player.json` |
| Q6 | Starting lives and extra-life thresholds | 5 lives, extras at 15k/40k/75k/120k | `player.json` |
| Q7 | Whether amulet pieces were lost on death | kept | `player.json → death.keepAmulet` |
| Q8 | Score values per creature and per piece | 100–500, 5000 | `creatures.json`, `loot.json` |
| Q9 | The Wulf's appearance rules and whether the sabre affected it | 12% on entry + bonuses; repel only | `wulf.json` |
| Q10 | Whether anything besides orchids and the amulet was collectable | nothing | `loot.json` |
| Q11 | Sabre swing duration, reach, and whether movement was locked | 12 ticks, 14 px, movement free | `player.json → sabre` |
| Q12 | Whether creature spawns were fixed per room or random | authored-with-fallback | `room_entities.json` |
| Q13 | How the original resolved scenery collision — per pixel, per cell, or per object rectangle — and so which rule reproduces its routes | **Design decided 2026-09-12:** art-derived collision (cell solid at ≥ 25 % painted), with art redrawn to fill its footprint — interior median 45 % walkable against the footprint maze's 39 %, gap locked at ≤ 6 points (§7.3). How the original actually resolved collision is still unverified; if a source shows per-rectangle collision, revisit. | `world/scenery.json → solidCoveragePercent`, per-object `collisionCells` |

Two facts are **already closed** and must not be re-litigated: the 16×16 /
256-room grid and the start room at `(8, 10)` are `[CANON]`, extracted
directly into `original_map.json` and locked by `MapDataTest`.

---

## 26. Glossary

| term | meaning |
|------|---------|
| **cell** | an 8×8 pixel unit; the collision and layout grid |
| **room** | one flip-screen, 32×24 cells, 256×192 px |
| **playfield** | the 256×192 area where rooms are drawn |
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
