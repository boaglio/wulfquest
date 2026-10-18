# Wulf Quest

A Java game: you are a ranger, alone in a 256-room jungle, looking for four
pieces of a broken amulet. Large animals guard them. Smaller things want to
kill you on the way. Flowers grow that make you untouchable, at a price: fast,
or slow, or with your sense of direction reversed, or flat on your back. And somewhere out there is the Wulf, which
cannot be killed and does not lose interest.

A clean-room homage to the flip-screen jungle mazes of 1984, built from
scratch with original artwork, original audio, and no third-party game
engine.

## Screenshots

<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-title.png" width="420" alt="The title screen: WULF over QUEST, with START, OPTIONS and EXIT">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-lake.png" width="420" alt="Ranger Vale by a jungle lake, a tribesman watching, an orchid's spell running out on the bar below">
</p>
<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-jungle.png" width="420" alt="A jungle room with spiders, an orchid and Ranger Vale on the right">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-beast.png" width="420" alt="Ranger Vale swinging his sabre under a wall of carved stone">
</p>
<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-options.png" width="420" alt="The options page: difficulty, keys, sound, volume and the four 8-bit switches">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-8bit.png" width="420" alt="A room of mountains and crates in 8-BIT MODE: colour clash, one-colour sprites and scanlines">
</p>

The title, a few rooms of the jungle, and the options page, the last room
again with **8-BIT MODE** on. Each one is drawn by the game's own painters
from a fixed seed. The pictures are attached to the
[v0.7.0 release](https://github.com/boaglio/wulfquest/releases/tag/v0.7.0),
not kept in the repository, because §2.4 of `AGENTS.md` keeps every image
file out of it.

## Download

Get the latest build for Windows or Linux from the
[Releases page](https://github.com/boaglio/wulfquest/releases). Each one
brings its own Java, so there is nothing else to install: unzip and run
`WulfQuest.exe` or `WulfQuest/bin/WulfQuest`. On a Mac, or anywhere with
Java 21 or later, the plain jar works: `java -jar wulfquest-….jar`.

The builds are not signed yet, so Windows warns on the first launch: click
**More info**, then **Run anyway**.

## Status

**Continues.** Game over is not quite the end: three times a game, in either
mode, fire on the game-over screen sends you back where you fell with fresh
lives and everything you found — but your score starts again from zero. The
hi-score table keeps the run's best.

**Easy and Hard.** The game as the original plays it is Hard. Choose Easy on
the title's options: nine lives instead of five, two small creatures in a room
instead of three and the nastier kinds slower to come, a Wulf you can always
outrun, no flames at all, a looking glass that shows the way for three
minutes, and shrines that answer every time. Easy runs share the hi-score
table, marked `E`, and winning one opens New Game+ just the same.

**An ending.** Get out through the arch with all four quarters and you walk
the amulet to old Mr Jones, who knows where it belongs. Then the sums, then
the credits roll.

**New Game+.** Win once and Mr Jones has a new job for you: another amulet,
broken in two. Win that and there is one last job, an amulet in one piece.
Fewer pieces, but the jungle starts out angrier and the Wulf runs faster.
Once a job is open the title starts it, and left and right on `START` switch
back.

**The amulet hides somewhere new every game.** Like the original, each game
picks one of its eight sets of four rooms for the quarters, so the map you
learned last game is only half the answer. Each quarter's guardian goes with it.

**The original's creatures.** The jungle is now the original's, read from its
disassembly: small creatures — spiders, scorpions, bats, parrots and ten more —
appear as puffs, three to a room, each kind in its own colours, and the later kinds
only come as the amulet fills. Meet them with the sabre out and facing them and
they burst; walk into one and it takes you with it. Rhinos pace their corridors,
tribesmen follow you about the map — and light up and call when they stand level
with a quarter of the amulet — and somewhere a hippo or a warthog sleeps. None of
those can be killed, only pushed back. Our own invented creatures stay as an
option (`features.inventedCreatures`).

**The original's Wulf.** Read from the original's disassembly: there is one
Wulf, and it prowls the bottom row of the jungle all game. A
sabre held towards it as it comes makes it sit — for a while. The Wulf that
appears anywhere and follows you is still there as an option
(`features.roamingWulf` in `data/config/game.json`). Lives work as they did
then too: five in reserve, and none for score. And so do the orchids: red, cyan,
magenta and yellow each make you untouchable while they last — slow, fast,
reversed or knocked flat — and white cures you. And don't stand about: stay in
one room too long and a flame comes for you, one no sabre can touch. Our own six flowers stay as an
option too (`features.inventedOrchids`).

**M10 — the true map.** The jungle is now the original's own layout, checked
against publicly documented data: the room grid had been mis-numbered, and
every room had two rows at the top it never had. Picking up a quarter of the
amulet now shows the amulet so far and a verse; the sabre fences instead of
swinging; and game over says how much of the adventure you got through.
`./run.sh help` lists every way to run it. Since then the jungle has filled up
with the original's own treasures — a crate, a ring, a sword, now and then a
spare life, about two hundred and fifty of them a game, worth 150 each.

A complete winning run — all four quarters and out through the arch, about 25
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
creature, and the orchids. `mvn -q package` gives you one jar you can
double-click, with the game, its data and its demo inside it. The full plan
lives in [AGENTS.md](AGENTS.md) §24.

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
| Space / Z | Swing the sabre; in a menu, pick; at game over, continue |
| Enter | In a menu, pick (Space does too, in either key layout) |
| M | The map (the game waits while it is open; `B` in the period layout) |
| P | Pause, with the menu: save, load, options, quit |
| Escape | Leave the jungle for the title; quit from the title |

In every menu, up and down choose, Space or Enter (or fire) picks, and Escape goes back a step; on
the options page left and right turn the volume.

**A gamepad** works too: plug it in before the game or while it runs. The left
stick or the d-pad walks, `A` or `X` swings (and picks, in a menu), `Start`
pauses, `Back` (Select, View, Share — the small one on the left) leaves the
jungle or goes back a step, and `Y` opens the map. On the title, `Y` starts a
new game and `B` switches between Hard and Easy. The buttons are
named as on an Xbox pad and found by their place on the others: `A` is the
bottom face button on a PlayStation or Switch pad too. Gamepads are new in this
version, and so far tried with no pad in hand — tell us how yours does. If one
ever gets in the way, `--no-gamepad` leaves them alone.

In the jungle, `M` opens a map of everywhere you have been; the game waits while
it is open. Find the scroll a few rooms from the start and it shows the whole
jungle; find the two eyes and it shows where the four quarters of the amulet lie.
Find one of the two looking glasses and for ninety seconds an arrow at the
edge of each room shows the way to the nearest quarter, and the map dots the
whole way there. Once the amulet is whole, four more looking glasses lie in the
jungle, one in each quarter of the map, to show you the way out.

Left alone for 20 seconds, the title shows a small plane landing in the
forest, then a demo of the game; any key brings it back.

The title screen is a short menu: `START`, `OPTIONS` and `EXIT`. With a game
saved, `CONTINUE` comes first and carries on with the one saved last, and
`START` becomes `NEW GAME`. A new game first asks for one of the three save
slots (a slot that holds a game asks you to press fire again before it is
replaced), and from then on the game saves itself there every minute and when
you leave for the title. A game that ends, won or lost, is cleared from its
slot. Under it the title says whether the next game is
Hard or Easy and, once you have won, which of Mr Jones's jobs it is; left and
right choose the job.

The options page sizes the window: `SCALE` (left and right, 1X to 6X) and
`FULLSCREEN`, which fills the screen at the largest size that fits; `F11`
switches full screen anywhere. Both are remembered, and work over a game too.

The rest is on the title's options page: the difficulty; the keys page, where
the period layout (`Q`/`A`/`O`/`P` to move, `M` to swing, `H` to pause, `B` for
the map) is one digit away, every key either layout uses is listed, map included, and the choice sticks; loading a saved game; the
hi-scores and your lifetime ledger. Nothing is hidden behind a key
you have to know, but the old letters still work on the title for those who
know them: `G` new game, `D` difficulty, `N` the job, `K` keys, `A` sound, `H`
hi-scores, `S` stats, `L` load, `O` options. The credits roll at the end of a
won game.

**Pause, save and load.** `P` pauses the game with a menu: resume, save the
game to one of three slots, load one, options, or quit to the title. The
options page has sound and volume, 8-bit mode, scanlines, the colour clash, the
original's one-colour sprites and sprite flicker.

Hi-scores, settings, stats and saved games are written to your own user directory —
`~/.local/share/wulfquest` on Linux, `~/Library/Application Support/WulfQuest`
on macOS, `%APPDATA%\WulfQuest` on Windows — never into the game's own files.

The options page (from the title, or from the pause menu) has the
sound, on or off, and its volume, so you can turn it down without leaving a
game. For the nostalgic, **8-BIT MODE** turns on everything that makes it look
like the Spectrum original at once; each of its four switches is there on its
own too, all off by default: scanlines darken every other line of
the picture; the colour clash gives each 8×8 cell two colours the way the
original did, the jungle in solid blocks with black detail and anything moving
through the foliage a black shape cut out of it; one-colour sprites draws Vale
and each creature in a single ink, as the original did; and sprite flicker makes creatures crowded into one spot take
turns to be drawn, the way a Spectrum ran out of time to draw them all.

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

`mvn -q verify` runs 578 tests and every gate the project has:

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

Wulf Quest is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

That covers everything in this repository: the code, and the art, music and
data, which are all text and all written for this project. The downloads also
carry the libraries the game is built on, under their own licences, both
compatible with the GPL: Jackson and the JSON schema validator (Apache-2.0),
and for gamepads Jamepad (Apache-2.0) with SDL2 inside it (zlib).
