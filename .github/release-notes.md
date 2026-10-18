## Screenshots

<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-title.png" width="400" alt="The new title screen: WULF over QUEST, with START, OPTIONS and EXIT">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.1/screenshot-options.png" width="400" alt="The options page: difficulty, keys, sound, volume, scale, full screen and the four 8-bit switches">
</p>
<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-lake.png" width="400" alt="Ranger Vale by a jungle lake, a tribesman watching, an orchid's spell running out on the bar below">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-jungle.png" width="400" alt="A jungle room with spiders, an orchid and Ranger Vale on the right">
</p>
<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-beast.png" width="400" alt="Ranger Vale swinging his sabre under a wall of carved stone">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.0/screenshot-8bit.png" width="400" alt="A room of mountains and crates in 8-BIT MODE: colour clash, one-colour sprites and scanlines">
</p>
<p align="center">
  <img src="https://github.com/boaglio/wulfquest/releases/download/v0.7.2/screenshot-map.png" width="400" alt="The map: the rooms walked in green and the rest in blue, a quarter of the amulet shining where an eye showed it, a dull one where it was taken, and the stone arch">
</p>

## What's new in 0.7.2

**Glow, like an old television.** The options page has a new `GLOW` line,
just under `SCANLINES`. Turn it on and every bright pixel throws a soft halo
onto the dark around it, the way a CRT's phosphor did. The pixels themselves
keep their colour and their detail. It is off by default, goes well with
scanlines, and stays out of 8-BIT MODE: that switch is the original game's
look, and glow is the television's.

## In 0.7.1

**Size the window, or fill the screen.** The options page has `SCALE`, from
1X to 6X with left and right, and `FULLSCREEN`, which fills the screen at the
largest size that fits. Both are remembered, and work over a game too: the
window changes as you pick. `F11` switches full screen on and off anywhere,
and the game now opens at the size you chose (`--scale` still sets it for one
run).

**A plane lands in the jungle.** Leave the title alone for 20 seconds and a
small plane comes down over the forest and rolls to a stop on a dirt
airstrip, while *ONE MAN, / ONE MISSING ARTIFACT, / ONE MISSION* types out
below it. Then the demo plays, as before. Any key brings the title back.

**Space or Enter picks.** On every menu, in either key layout, Space or Enter
chooses the line, as fire does. In the jungle they do only what your layout
says, so Enter never swings.

**The keys page lists the map's key.** `M` in the modern layout, `B` in the
period one.

**The credits are for the end.** They are no longer on the options page, and
`C` on the title no longer opens them: they roll after a won game, as they
always have.

## In 0.7.0

**A new title screen.** WULF QUEST, big, over a short menu: `START`,
`OPTIONS` and `EXIT`, with `CONTINUE` first when you have a game saved. Up and
down choose, fire picks, and a little wolf marks the line you are on, on every
menu. Under `START` the title says whether the next game is Hard or Easy and,
once you have won, which of Mr Jones's jobs it is; left and right choose the
job. The difficulty, the keys, loading a game, the hi-scores, your ledger and
the credits are on the title's options page now. The old letters still work
on the title for those who know them.

**Every game has its save slot.** A new game first asks which of the three
slots to keep it in (a slot that already holds a game asks for fire again
before replacing it). From then on the game saves itself there every minute
and whenever you leave for the title, so `CONTINUE` always takes you back to
where you were. A game that ends, won or lost, is cleared from its slot.

**Four looking glasses to show the way out.** Once the amulet is whole, four
more looking glasses lie in the jungle, one in each quarter of the map. Find
one and it shows you the way to the stone arch.

**Gamepads.** Plug one in before the game or while it runs: the left stick or
the d-pad walks, `A` or `X` swings and picks, `Start` pauses, `Back` leaves the
jungle or goes back a step, and `Y` opens the map. They have not yet been tried
with a real pad in hand, so tell us how yours does; `--no-gamepad` leaves them
alone. `./run.sh pad` shows what your system takes for a gamepad, if anything.

## In 0.6.3

**Carry on where you left off.** With a game saved, the title offers it first:
fire takes you back into the game you saved last, and `G` starts a new one.

**Your saves survive updates.** A save from an earlier version now loads, as
long as the new version's rules bring it back to the same place, which they do
across changes to menus, words, keys and pictures. Your 0.6.2 saves load in
0.6.3.

**8-bit mode.** A new line on the options page gives the game the Spectrum
original's look in one switch: the jungle in solid blocks of colour with black
detail, Vale and every creature in one colour, anything moving through the
foliage a black shape cut out of it, scanlines, and sprite flicker. The colour
clash now works the way the original's did, and each switch is still there on
its own.

**Sound on the options page.** Turn the sound on or off, and the volume up or
down, from the pause menu without leaving your game.

**The way out is on the map.** The stone arch, where the amulet goes, is
marked on the map from the start, and its doorway glows gold once the amulet
is whole.

**Credits you can read.** The roll after a win is slower, fire stops it and
starts it again, and Escape skips ahead without missing Mr Jones's offer.

**Fixes.**
- In the period layout (`M` to swing), every swing also opened the map. The map
  is `B` in that layout now.
- In the small font, `S` looked exactly like `5` and `O` like `0`, so the
  credits' "early 1980s" read "19805". Both letters are new.

## In 0.6.2

**Save your game.** Press `P` in the jungle for the new pause menu:
resume, save, load, options, or quit to the title. There are three save slots,
and you can save anywhere. Each slot shows the mode, the job, the quarters
found, the score, the room and the date. `L` on the title loads a game
straight away. A loaded game counts for the hi-score table like any other.

**Options.** `O` on the title, or the pause menu, switches the scanlines, the
colour clash and the one-colour creatures on and off. The change shows at once.

**The time bonus pays again.** It now runs down over an hour instead of
twenty minutes, so a win in 25 minutes earns about 17 000 points for its
speed. Before this, every win got nothing for it.

**Easy is easier.** In Easy the rhinos, tribesmen, hippos and warthogs move at
40% of their speed, so they are easy to see coming and to step round. Your
sabre still knocks them back as hard. Hard is unchanged.

**And a smaller fix.** In job 1, the verse you get on taking the first half of
the medallion now fits either half. It used to speak of the moon, which is on
the silver half only.

## In 0.6.1

**Each job plays for its own amulet.** Mr Jones's later jobs no longer reuse
the wolf amulet. Job 1's is a medallion broken across the middle, silver above
and gold below, a crescent on one half and a star on the other; job 2's is a
gold sun with a red stone. You see the job's own amulet everywhere: on the
pedestals, flying to the panel, in the panel, on the reveal, and in Mr Jones's
hands at the end.

## In 0.6.0, if you are coming from 0.5

**An ending — three of them.** Get out through the arch with the whole amulet
and you walk it to old Mr Jones, who knows where it belongs. Then the sums,
then the credits roll.

**New Game+.** Win once and Mr Jones has a new job for you: another amulet,
broken in two. Win that and there is one last job, an amulet in one piece.
Fewer pieces, but the jungle starts out angrier and the Wulf runs faster. Each
job ends its own way, with its own amulet and its own words, and the last one
ends on horseback. Press `N` on the title to pick a job once it is open.

**Easy and Hard.** The game as the original plays it is now Hard. Press `D` on
the title for Easy: nine lives, fewer creatures, a Wulf you can outrun, no
flames, longer help from the looking glass, and shrines that always answer.
Easy runs share the hi-score table, marked `E`.

**Continues.** Game over is not quite the end: three times a game, press fire
to get up where you fell with fresh lives and everything you found. Your score
starts again from zero; the table keeps the run's best.

**Looking glasses.** Two lie somewhere in the jungle. Pick one up and for 90
seconds an arrow at the edge of each room shows the way to the nearest quarter
of the amulet, and the map dots the whole way there.

**The amulet moves.** Like the original, each game hides it in one of eight
sets of rooms, so the map you learned last game is only half the answer. On the
map, a lair an eye has shown gets a shining diamond, dull once you have taken
its quarter.

**And more.** The creatures are drawn in their own colours (the original's one
colour each is still there: `"features": { "inkedCreatures": true }` in
`settings.json`). The sabre reaches as far as the original's. `--endings`, or
`./run.sh endings` from the source, plays every ending in turn without
touching your saves.

**Licence.** Wulf Quest is now free software under the GNU GPL, version 3 or
later.

## Download

| You have | Get | Then |
|---|---|---|
| Windows | `WulfQuest-…-windows-x64.zip` | Unzip, open the `WulfQuest` folder, run `WulfQuest.exe`. |
| Linux | `WulfQuest-…-linux-x64.tar.gz` | `tar xzf` it, run `WulfQuest/bin/WulfQuest`. |
| a Mac, or Java 21 or later already | `wulfquest-….jar` | `java -jar wulfquest-….jar` (needs Java 21+, e.g. from adoptium.net) |

The Windows and Linux apps bring their own Java; nothing else to install.

**The first time you open it** Windows warns that the app is from an
unidentified developer, because these builds are not signed yet: at "Windows
protected your PC" click **More info**, then **Run anyway**.

Hi-scores and settings are kept per user and survive updates.

**Saving**: press `P` in the jungle for the pause menu, which saves to one of
three slots and loads them; the title offers the game you saved last, and `L`
loads any slot. A save made by an earlier version loads unless the game's rules
have changed under it; if one cannot, the game says so.

Wulf Quest is free software under the GNU General Public License, version 3
or later; the full text is in `LICENSE` inside every download, and the source
is in this repository.
