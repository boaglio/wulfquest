#!/usr/bin/env python3
"""
Item Forge — the map scroll and the two seeing eyes (AGENTS.md §14.8, [NEW]).

Original drawings, as JSON pixel art like every other sprite (§10). 16x16 each,
origin bottom-centre, so a placement's spot is where the item rests on the ground.

  item_map   a rolled parchment, tied, half unrolled: the map of the jungle
  item_eye   an eye with a cyan iris: it shows where two quarters of the amulet lie
  item_glass a looking glass, brass rim, a pale lens catching the light (§14.10): it shows the
             way to the next quarter for a while. f0-f2 move the glint across the lens.
  way_arrow  the glass's arrow at the room's way out (§14.10): n, e, s, w, 13x13.
  map_diamond the map's mark for a quarter an eye has shown (§17.7): a cut gem, 13x11 to
             fit a room on the map. shiny0-3 twinkle, a glint at one corner and then the
             other; dull is the same gem gone grey, once its quarter is taken.
  treasure   the odds and ends lying about the jungle (§16.4, [CANON] in kind, ours in
             drawing): crate, ring, fruit, cap, shield, life, money_bag, sword. One ink
             each, as on the Spectrum: the game tints them, so they are drawn in white.

Outputs data/art/sprites/item_map.sprite.json, item_eye.sprite.json, item_glass.sprite.json,
way_arrow.sprite.json,
map_diamond.sprite.json and treasure.sprite.json and merges the names into data/art/sprites/index.json.  Run:  python3 tools/art/item_forge.py
"""
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # importing the other forges must not leave a .pyc behind (§2.4)
sys.path.insert(0, str(Path(__file__).resolve().parent))
from scenery_forge import Canvas, update_index  # noqa: E402
from creature_forge import write_sprite  # noqa: E402


def scroll():
    """A parchment sheet, unrolled between two rolled ends, with a hint of a drawn map on it."""
    cv = Canvas(16, 16)
    cv.rect(3, 4, 13, 14, "y")              # the sheet
    cv.rect(1, 3, 4, 15, "Y")               # the left roll
    cv.rect(12, 3, 15, 15, "Y")             # the right roll
    for y in (3, 14):
        cv.set(1, y, "w")
        cv.set(14, y, "w")
    cv.rect(2, 4, 3, 14, "y")               # shade inside each roll
    cv.rect(13, 4, 14, 14, "y")
    # Marks on the sheet: a winding path and a cross where something waits.
    for x, y in ((5, 6), (6, 7), (7, 7), (8, 8), (8, 9), (9, 10), (10, 10), (10, 11)):
        cv.set(x, y, "r")
    cv.set(6, 11, "R")
    cv.set(5, 10, "R")
    cv.set(7, 10, "R")
    cv.set(6, 9, "R")
    cv.outline("K")
    return cv.rows()


def eye(look):
    """An almond eye with a cyan iris and a black pupil; {@code look} shifts the gaze a pixel."""
    cv = Canvas(16, 16)
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            # An almond: two arcs meeting at the corners.
            dx = (x + 0.5 - cx) / 7.0
            dy = (y + 0.5 - cy) / 4.2
            if dx * dx + dy * dy * (1.0 + 0.6 * abs(dx)) <= 1.0:
                cv.set(x, y, "W")
    ix = cx + look
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - ix, y + 0.5 - cy)
            if cv.get(x, y) == "W" and r <= 3.2:
                cv.set(x, y, "C" if r > 1.4 else "K")
    cv.set(int(ix) + 1, int(cy) - 2, "W")    # the glint
    for x in range(3, 13):                   # the lid's crease above
        if (x + look) % 3 != 0:
            cv.set(x, 3, "c")
    cv.outline("K")
    return cv.rows()


def glass(glint):
    """A round lens in a brass rim, its handle down to the left; {@code glint} is where the light sits."""
    cv = Canvas(16, 16)
    cx, cy, r = 9.5, 6.5, 5.6
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r - 1.3:
                cv.set(x, y, "c")                    # the lens
            elif d <= r:
                cv.set(x, y, "Y" if (x + 0.5 - cx) + (y + 0.5 - cy) < 0 else "y")   # the rim, lit from the top left
    for x, y in ((glint[0], glint[1]), (glint[0] + 1, glint[1]), (glint[0], glint[1] + 1)):
        cv.set(x, y, "W")
    cv.set(int(cx) + 2, int(cy) + 2, "C")           # a softer light low in the lens
    for i in range(5):                              # the handle, wood, two wide
        cv.set(4 - i, 11 + i, "r")
        cv.set(5 - i, 11 + i, "R" if i < 4 else "r")
    cv.outline("K")
    return cv.rows()


# The arrow, pointing north: '#' ink, 'o' its shadow side. 11x11, its point at the top.
ARROW = [
    ".....#.....",
    "....###....",
    "...#####...",
    "..#######..",
    ".####o####.",
    "...##o##...",
    "...##o##...",
    "...##o##...",
    "...##o##...",
    "...##o##...",
    "...#####...",
]


def arrow(way):
    """The arrow turned to {@code way}: n as drawn, the others a quarter turn at a time."""
    rows = [list(r) for r in ARROW]
    n = len(rows)
    turns = {"n": 0, "e": 1, "s": 2, "w": 3}[way]
    for _ in range(turns):                          # clockwise
        rows = [[rows[n - 1 - j][i] for j in range(n)] for i in range(n)]
    cv = Canvas(n + 2, n + 2)                       # a pixel all round, for the outline
    for y in range(n):
        for x in range(n):
            if rows[y][x] == "#":
                cv.set(x + 1, y + 1, "Y")
            elif rows[y][x] == "o":
                cv.set(x + 1, y + 1, "y")
    cv.outline("K")
    return cv.rows()


# A brilliant cut seen side-on: the crown above the girdle, the pavilion narrowing to the
# point. W the light facets, C the body, B the shaded side. 11x9, set into a 13x11 frame.
GEM = [
    "..KKKKKKK..",
    ".KWWCWCCBK.",
    "KWWCCWCCCBK",
    "KKKKKKKKKKK",
    ".KWCCCCCBK.",
    "..KWCCCBK..",
    "...KWCBK...",
    "....KBK....",
    ".....K.....",
]
DULL = {"W": "w", "C": "w", "B": "b"}


def diamond(glint, dull=False):
    """The gem, shiny with a four-point glint centred on {@code glint} (or none), or dull."""
    cv = Canvas(13, 11)
    for y, row in enumerate(GEM):
        for x, ch in enumerate(row):
            if ch != ".":
                cv.set(x + 1, y + 2, DULL.get(ch, ch) if dull else ch)
    if glint is not None:
        gx, gy = glint
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            cv.set(gx + dx, gy + dy, "W")
    return cv.rows()


# Treasures: '#' is ink, '.' is ground. 16x16, resting on the bottom row.
TREASURES = {
    "crate": [
        "................",
        "................",
        "................",
        "..############..",
        "..#....#.....##.",
        "..############.#",
        "..#..........#.#",
        "..#.#......#.#.#",
        "..#..#....#..#.#",
        "..#...#..#...#.#",
        "..#....##....#.#",
        "..#...#..#...#.#",
        "..#..#....#..#.#",
        "..#.#......#.##.",
        "..#..........##.",
        "..############..",
    ],
    "ring": [
        "................",
        "................",
        "......#..#......",
        ".......##.......",
        "......#..#......",
        ".......##.......",
        ".....######.....",
        "....##....##....",
        "...##......##...",
        "...#........#...",
        "...#........#...",
        "...#........#...",
        "...##......##...",
        "....##....##....",
        ".....######.....",
        "................",
    ],
    "fruit": [
        "................",
        ".......#..###...",
        "........##..##..",
        "........#.......",
        "....###.#.###...",
        "...#####.#####..",
        "..##.#########..",
        "..#.##########..",
        "..#.##########..",
        "..############..",
        "..############..",
        "..############..",
        "...##########...",
        "...##########...",
        "....###..###....",
        "................",
    ],
    "cap": [
        "................",
        "................",
        "................",
        "................",
        ".......##.......",
        ".....######.....",
        "....###..###....",
        "...##......##...",
        "...#........#...",
        "...#.######.#...",
        "...##########...",
        "...#.#.#.#.##...",
        "..############..",
        ".##############.",
        "................",
        "................",
    ],
    "shield": [
        "................",
        ".##############.",
        ".#......#.....#.",
        ".#.####.#.###.#.",
        ".#......#.....#.",
        ".#.####.#.###.#.",
        ".##############.",
        ".#......#.....#.",
        ".#.###..#..##.#.",
        "..#.....#....#..",
        "..#..##.#.##.#..",
        "...#....#...#...",
        "....#...#..#....",
        ".....#..#.#.....",
        "......#.##......",
        ".......##.......",
    ],
    "life": [
        "................",
        "......####......",
        ".....#....#.....",
        ".....#.##.#.....",
        ".....#....#.....",
        "......####......",
        "....########....",
        "...#...##...#...",
        "...#.######.#...",
        "...#...##...#...",
        ".......##.......",
        "......#..#......",
        ".....#....#.....",
        ".....#....#.....",
        "....##....##....",
        "................",
    ],
    "money_bag": [
        "................",
        "......#..#......",
        ".......##.......",
        "......####......",
        ".......##.......",
        ".....#....#.....",
        "....#......#....",
        "...#...##...#...",
        "..#...#..#...#..",
        "..#....#.....#..",
        "..#.....#....#..",
        "..#...#..#...#..",
        "..#....##....#..",
        "...#........#...",
        "....########....",
        "................",
    ],
    "sword": [
        "................",
        "..............#.",
        ".............###",
        "............###.",
        "...........###..",
        "..........###...",
        ".........###....",
        "........###.....",
        ".......###......",
        "...#..###.......",
        "...#####........",
        "....###.........",
        "...#####........",
        "..##...#........",
        ".##.............",
        "................",
    ],
}


def treasure(name):
    rows = TREASURES[name]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    cv = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                cv.set(x, y, "W")
    return cv.rows()


def main():
    write_sprite("item_map", 16, 16, [("f0", scroll())])
    write_sprite("item_eye", 16, 16, [("f0", eye(0)), ("f1", eye(1)), ("f2", eye(-1))])
    write_sprite("item_glass", 16, 16, [("f0", glass((7, 3))), ("f1", glass((9, 3))), ("f2", glass((6, 5)))])
    write_sprite("way_arrow", 13, 13, [(w, arrow(w)) for w in ("n", "e", "s", "w")])
    write_sprite("map_diamond", 13, 11, [("shiny0", diamond((11, 1))), ("shiny1", diamond(None)),
                                         ("shiny2", diamond((1, 1))), ("shiny3", diamond(None)),
                                         ("dull", diamond(None, dull=True))])
    write_sprite("treasure", 16, 16, [(name, treasure(name)) for name in TREASURES])
    update_index(["item_map", "item_eye", "item_glass", "way_arrow", "map_diamond", "treasure"],
                 lambda n: n.startswith("item_") or n in ("way_arrow", "map_diamond", "treasure"))
    print("item forge: item_map (1 frame), item_eye (3 frames), item_glass (3 frames), way_arrow (4 frames), "
          "map_diamond (5 frames), treasure (%d frames)"
          % len(TREASURES))


if __name__ == "__main__":
    main()
