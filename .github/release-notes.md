## What's new in 0.6.3

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
