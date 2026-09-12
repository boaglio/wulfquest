# Wulf Quest

A Java game: you are a ranger, alone in a 256-room jungle, looking for four
pieces of a broken amulet. Large animals guard them. Smaller things want to
kill you on the way. Flowers grow that will make you fast, or slow, or
invert your sense of direction. And somewhere out there is the Wulf, which
cannot be killed and does not lose interest.

A clean-room homage to the flip-screen jungle mazes of 1984, built from
scratch with original artwork, original audio, and no third-party game
engine.

## Status

**Milestone M1 complete** — the JSON content database with schema validation,
the indexed framebuffer and window, the status panel, and the canon lock on
the extracted 256-room map. There is no playable game yet: rooms arrive in M2
and the player in M3. The full plan lives in [AGENTS.md](AGENTS.md) §24.

```
$ mvn -q exec:java -Dexec.args="--headless"
Wulf Quest — M1
  content hash : 08cd040a5d66d8...
  files loaded : [config/game, config/display, art/palette, art/font/font, world/original_map]
  map          : 16x16 rooms, start 8,10, 48 templates (45 used), 41 scenery objects, 5105 placements
```

## Build and run

Needs a JDK 21+ and Maven.

```bash
./run.sh               # build if needed, then play
./run.sh --scale 3     # options are passed through to the game
./run.sh --headless    # load and validate the data, then exit
./run.sh --test        # full verify (tests + CI gates) first
./run.sh --help
```

`run.sh` is a convenience wrapper; Maven is the build:

```bash
mvn -q verify          # compile, test, validate all data, run the CI gates
mvn -q exec:java       # run
mvn -q package && java -jar target/wulfquest-0.1.0-SNAPSHOT.jar
```

Editing anything under `data/` takes effect on the next `./run.sh` with no
rebuild — the game reads the working tree's `data/` directly.

## Controls

| Key | Action |
|-----|--------|
| Arrows / WASD | Walk (8 directions) |
| Space / Z | Swing the sabre |
| P | Pause |
| Escape | Quit |

A period key layout (`Q`/`A`/`O`/`P` to move, `M` to swing) can be selected
from the title screen.

## How it is built

- **Java 21, Java2D, no engine.** A 320×256 indexed-colour framebuffer
  scaled by an integer factor. A fixed 50 Hz simulation with fixed-point
  integer maths, so a run is bit-for-bit reproducible from its seed.
- **JSON files are the database.** Every creature, speed, timing, score,
  palette entry, sound, and string lives in `data/`, schema-validated at
  boot. No game constant exists in the Java source.
- **The artwork is text.** Every sprite is a JSON grid of palette-index
  characters, so all the art in this game is authored, reviewable in a diff,
  and ours. The build fails if any binary image or audio file appears in the
  repository.
- **The audio is synthesised.** Square waves and noise, generated at
  runtime, in the spirit of a one-bit speaker.

## Repository layout

```
AGENTS.md     the full specification and working agreement — start here
data/         the content database (world, entities, art, audio, config)
src/main/     engine, simulation, renderer, data layer
src/test/     unit tests, data validators, replay and golden-frame checks
tools/        CI gates
```

## Credits and attribution

Wulf Quest is an independent, clean-room homage to the flip-screen jungle
maze genre of the early 1980s. All artwork, audio, and code are original.
Room layout geometry was reconstructed from publicly documented map data.
No assets from any commercial game are included. Not affiliated with,
endorsed by, or derived from the code of any prior work.

## Licence

To be decided before the first public release.
