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

**M10 — the true map.** The jungle is now the original's own layout, checked
against publicly documented data: the room grid had been mis-numbered, and
every room had two rows at the top it never had. Picking up a quarter of the
amulet now shows the amulet so far and a verse; the sabre fences instead of
swinging; and game over says how much of the adventure you got through.
`./run.sh help` lists every way to run it. Since then the jungle has filled up
with the original's own treasures — a crate, a ring, a sword, now and then a
spare life, about two hundred and fifty of them a game, worth 150 each.

A complete winning run — all four quarters and out through the arch, about 36
minutes of play — is recorded and replayed on every build.

**Milestone M9 complete.** M9 was the hardening pass:
golden-frame tests that hash what the renderer draws, every recorded replay
run on every build, the lair replay that had been missing since M6, and a
build that is green on a machine with no display.

M8 is what made it a game you can sit down to. It opens
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
creature, and six colours of orchid. `mvn -q package` gives you one jar you can
double-click, with the game, its data and its demo inside it. The full plan
lives in [AGENTS.md](AGENTS.md) §24.

```bash
./run.sh                   # play, starting in 8,10
./run.sh --seed 42         # replay the same creatures in every room
./run.sh --room 3,4        # start somewhere else
./run.sh --dev             # K kills Vale, N shows collision, H calls the Wulf
./run.sh --record run.json # save the first game as a replay
./run.sh --debug-effect haste  # start under an orchid's effect: haste, torpor,
                               # reversal, immunity, delirium, stillness
./run.sh --no-audio        # silence for this run; your settings are not changed
./run.sh --lives 9         # practice: start with nine lives
./run.sh --infinite-lives  # practice: die as often as you like
./run.sh --god             # practice: nothing can kill you
                           # (practice runs never reach the hi-score table)
./run.sh --user-dir ./save # keep hi-scores, settings and stats somewhere else
./run.sh browse            # the room browser: arrows, [ ] or PgUp/PgDn, M
./run.sh replay            # re-run every recorded replay and check it still matches
./run.sh check             # validate every data file and exit
./run.sh audit             # can every room still be reached?
./run.sh sprites sheet     # every scenery object, drawn in the terminal
./run.sh jar               # build the single runnable jar and play from it
./run.sh menu              # all of the above as a list to pick from
./run.sh help              # every command and option
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
mvn -q verify            # compile, test, validate all data, run the CI gates
mvn -q -Pheadless verify # the same on a machine with no display
mvn -q exec:java         # run
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

In the jungle, `M` opens a map of everywhere you have been; the game waits while
it is open. Find the scroll a few rooms from the start and it shows the whole
jungle; find the two eyes and it shows where the four quarters of the amulet lie.

The title screen has the rest: `K` for the keys page, where the period layout
(`Q`/`A`/`O`/`P` to move, `M` to swing) is one digit away and the choice sticks;
`A` for sound and volume; `H` for the hi-scores, `C` for the credits, `S` for
your lifetime ledger. Nothing is hidden behind a key you have to know.

Hi-scores, settings and stats are written to your own user directory —
`~/.local/share/wulfquest` on Linux, `~/Library/Application Support/WulfQuest`
on macOS, `%APPDATA%\WulfQuest` on Windows — never into the game's own files.

For the nostalgic, `settings.json` there has two switches, both off by default
and not yet on any menu, in
`"crt": { "scanlines": false, "glow": false, "attributeClash": false }`:
`scanlines` darkens every other line of the picture, and `attributeClash`
gives each 8×8 cell of the jungle one colour, so Vale turns the undergrowth
his colour as he pushes through it — faithful, ugly, fun. (`glow` does
nothing yet.)

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

## How it is checked

`mvn -q verify` runs 453 tests and every gate the project has:

- **The data validates itself.** Every JSON file is schema-checked at load,
  and the semantic validators go further — a palette colour that does not
  exist, a sprite frame an animation needs and has not got, a lair on a solid
  cell, a hotkey bound twice. A typo is a readable in-game DATA ERROR screen
  naming the file and the JSON pointer, never a stack trace.
- **Golden frames.** The framebuffer is hashed at four ticks of a fixed run
  and once per menu screen, so a sprite that moves by a pixel or a panel that
  loses a digit fails the build. Regenerate them deliberately with
  `mvn -q verify -Dgolden.update=true`, and say in the commit why they moved.
- **Replays.** `replays/*.json` are recorded runs with the simulation's state
  hash every 50 ticks; every one of them is re-executed on every build. A
  divergence means determinism broke, and is never fixed by updating the hash.
  Three of them also assert what they were recorded to show: a Wulf chase
  survived across four rooms, the north-west quarter fetched and carried out,
  and the whole game won without a death.
- **Architecture.** The simulation is scanned for any reference to AWT, Swing,
  sound, `java.util.Random`, the wall clock, or a `float` in a signature. It
  has none, which is why a run is reproducible from its seed and why the sound
  added in M8 cannot change what happens.
- **No binaries.** The build fails if any image or audio file appears in the
  repository. All the art is JSON; all the audio is synthesised at runtime.

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
