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

**Milestone M8 complete** — and now it is a game you can sit down to. It opens
on a title screen with its own wordmark, waits twenty seconds and then plays
itself behind a dimmed jungle until you touch a key. Escape always goes one step
back: out of the jungle to the title, off the title to the desktop. A run that
earns a place asks for three letters and keeps them, along with your settings and
a lifetime ledger of everything you have ever killed and everything that has
ever killed you. And it makes a noise now: hard-edged square waves and noise,
synthesised as it goes, two channels, no sample files — footsteps, the blade, a
howl when the Wulf arrives and a growl that stays until it is gone. There are
three tunes, none of which plays in the jungle, where there is only the jungle.

Everything before it is still in: the whole quest, the Wulf, thirteen kinds of
creature, and six colours of orchid. `mvn -q package` now gives you one jar you
can double-click, with the game, its data and its demo inside it. The full plan
lives in [AGENTS.md](AGENTS.md) §24.

```bash
./run.sh                   # play, starting in 8,10
./run.sh --seed 42         # replay the same creatures in every room
./run.sh --room 3,4        # start somewhere else
./run.sh --dev             # K kills Vale, M shows collision, H calls the Wulf
./run.sh --record run.json # save the first game as a replay
./run.sh --debug-effect haste  # start under an orchid's effect: haste, torpor,
                               # reversal, immunity, delirium, stillness
./run.sh --no-audio        # silence for this run; your settings are not changed
./run.sh --user-dir ./save # keep hi-scores, settings and stats somewhere else
mvn -q exec:java -Dexec.mainClass=wulf.tools.ReplayRunner -Dexec.args="replays/full_run.json"
./run.sh --browse          # the room browser: arrows, [ ] or PgUp/PgDn, M
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

That jar is self-contained: copy it anywhere and run it, with no `data/`
directory and no classpath.

Editing anything under `data/` takes effect on the next `./run.sh` with no
rebuild — the game reads the working tree's `data/` directly.

## Controls

| Key | Action |
|-----|--------|
| Arrows / WASD | Walk (8 directions) |
| Space / Z | Swing the sabre |
| P | Pause |
| Escape | Leave the jungle for the title; quit from the title |

The title screen has the rest: `K` for the keys page, where the period layout
(`Q`/`A`/`O`/`P` to move, `M` to swing) is one digit away and the choice sticks;
`A` for sound and volume; `H` for the hi-scores, `C` for the credits, `S` for
your lifetime ledger. Nothing is hidden behind a key you have to know.

Hi-scores, settings and stats are written to your own user directory —
`~/.local/share/wulfquest` on Linux, `~/Library/Application Support/WulfQuest`
on macOS, `%APPDATA%\WulfQuest` on Windows — never into the game's own files.

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
- **The audio is synthesised.** Square waves with hard edges and an LFSR for
  noise, generated at runtime, in the spirit of a one-bit speaker. Two channels,
  mixed by addition and clipped. The tunes share those channels: a sound effect
  steals the one the music is on, and the music keeps going underneath.

## Repository layout

```
AGENTS.md     the full specification and working agreement — start here
data/         the content database (world, entities, art, audio, config)
src/main/     engine, simulation, renderer, data layer
src/test/     unit tests, data validators, replay and golden-frame checks
tools/        CI gates, the art forges that draw the scenery, the creatures,
              Ranger Vale and the font, and the forge that writes the music
```

## Credits and attribution

Wulf Quest is an independent, clean-room homage to the flip-screen jungle
maze genre of the early 1980s. All artwork, audio, and code are original.
Room layout geometry was reconstructed from publicly documented map data.
No assets from any commercial game are included. Not affiliated with,
endorsed by, or derived from the code of any prior work.

## Licence

To be decided before the first public release.
