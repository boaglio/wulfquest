## What's new in 0.6.1

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

**Quick save** is off by default, since the original had none. To turn it on,
add `"features": { "quickSave": true }` to `settings.json` in your user folder
(see the README). Then F5 saves and F9 loads, during play or from the title
screen. A game you have loaded into gets no hi-score. A save may not load in a
later version: the game will say so.

Wulf Quest is free software under the GNU General Public License, version 3
or later; the full text is in `LICENSE` inside every download, and the source
is in this repository.
