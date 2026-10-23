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
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.2/screenshot-options.png" width="420" alt="The options page drawn with GLOW on: difficulty, keys, sound, volume, scale, full screen, glow and the 8-bit switches">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-8bit.png" width="420" alt="A room of mountains and crates in 8-BIT MODE: colour clash, one-colour sprites and scanlines">
</p>
<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.2/screenshot-map.png" width="420" alt="The map: the rooms walked in green and the rest in blue, a quarter of the amulet shining where an eye showed it, a dull one where it was taken, and the stone arch">
</p>

The title, a few rooms of the jungle, the options page, a room with
**8-BIT MODE** on, and the map: one quarter of the amulet shown by an eye and
waiting, one already taken. Each one is drawn by the game itself.

## Download

Get the latest build for Windows or Linux from the
[Releases page](https://github.com/boaglio/wulfquest/releases). Each one
brings its own Java, so there is nothing else to install: unzip and run
`WulfQuest.exe` or `WulfQuest/bin/WulfQuest`. On a Mac, or anywhere with
Java 21 or later, the plain jar works: `java -jar wulfquest-….jar`.

The builds are not signed yet, so Windows warns on the first launch: click
**More info**, then **Run anyway**.

## The game

**The amulet.** Four quarters of it lie in four rooms of the jungle, each
watched by a guardian. Like the original, each game picks one of eight sets
of four rooms for them, so the map you learned last game is only half the
answer. Picking one up shows the amulet so far and a verse. Get out through
the stone arch with all four and you walk the amulet to old Mr Jones, who
knows where it belongs. Then the sums, then the credits roll.

**The jungle is the original's.** The same 256 rooms, laid out as they were
in 1984. Small creatures (spiders, scorpions, bats, parrots and ten more)
appear as puffs, three to a room, and the later kinds only come as the amulet
fills. Meet them with the sabre out and facing them and they burst; walk into
one and it takes you with it. Rhinos pace their corridors, tribesmen follow
you about the map, and light up and call when they stand level with a
quarter of the amulet, and somewhere a hippo or a warthog sleeps. None of
those can be killed, only pushed back. And don't stand about: stay in one
room too long and a flame comes for you, one no sabre can touch.

**The Wulf.** There is one, and it prowls the bottom row of the jungle all
game. It cannot be killed. A sabre held towards it as it comes makes it sit,
for a while.

**The orchids.** Red, cyan, magenta and yellow each make you untouchable
while they last, at a price: slow, fast, reversed or knocked flat. White
cures you.

**Treasure.** About two hundred and fifty pieces a game (a crate, a ring, a
sword, now and then a spare life), worth 150 each. Five lives in reserve, and
none for score, as it was then.

**Easy and Hard.** The game as the original plays it is Hard. Choose Easy on
the title's options: nine lives instead of five, two small creatures in a room
instead of three and the nastier kinds slower to come, a Wulf you can always
outrun, no flames at all, a looking glass that shows the way for three
minutes, and shrines that answer every time. Easy runs share the hi-score
table, marked `E`, and winning one opens New Game+ just the same.

**Continues.** Game over is not quite the end: three times a game, in either
mode, fire on the game-over screen sends you back where you fell with fresh
lives and everything you found, but your score starts again from zero. The
hi-score table keeps the run's best. Game over says how much of the adventure
you got through.

**New Game+.** Win once and Mr Jones has a new job for you: another amulet,
broken in two. Win that and there is one last job, an amulet in one piece.
Fewer pieces, but the jungle starts out angrier and the Wulf runs faster.
Once a job is open the title starts it, and left and right on `START` switch
back.

**Sound.** Hard-edged square waves and noise, synthesised as it goes, on two
channels: footsteps, the blade, a howl when the Wulf arrives and a growl that
stays until it is gone. There are three tunes, none of which plays in the
jungle, where there is only the jungle.

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

`GLOW` on the options page gives every bright pixel the soft halo of an old
television.

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

## Building it yourself

Wulf Quest is free software, and its source is all here. How to build it,
run it from the source, change the jungle and check your change is in
[DEVELOPER.md](DEVELOPER.md).

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
