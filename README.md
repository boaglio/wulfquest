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

**Milestone M2 complete** — the whole 256-room jungle renders, from 41
original scenery sprites laid out on the extracted map, with collision baked
for every room. A navigability audit confirms all 196 interior rooms are
reachable from the start. There is no player yet: that is M3. The full plan
lives in [AGENTS.md](AGENTS.md) §24.

Explore the map in the room browser:

```bash
./run.sh --room 7,3        # open at the stone arch (default: the start room, 8,10)
```

| Key | Room browser |
|-----|--------------|
| Arrows | Move to the neighbouring room |
| `[` `]` or PgUp / PgDn | Step through all 256 rooms in order |
| M | Show the collision mask |
| Esc | Quit |

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
tools/        CI gates, and tools/art/scenery_forge.py, which draws the scenery
```

## Credits and attribution

Wulf Quest is an independent, clean-room homage to the flip-screen jungle
maze genre of the early 1980s. All artwork, audio, and code are original.
Room layout geometry was reconstructed from publicly documented map data.
No assets from any commercial game are included. Not affiliated with,
endorsed by, or derived from the code of any prior work.

## Licence

To be decided before the first public release.
