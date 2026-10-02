#!/usr/bin/env python3
"""
Item Forge — the map scroll and the two seeing eyes (AGENTS.md §14.8, [NEW]).

Original drawings, as JSON pixel art like every other sprite (§10). 16x16 each,
origin bottom-centre, so a placement's spot is where the item rests on the ground.

  item_map   a rolled parchment, tied, half unrolled: the map of the jungle
  item_eye   an eye with a cyan iris: it shows where two quarters of the amulet lie
  treasure   the odds and ends lying about the jungle (§16.4, [CANON] in kind, ours in
             drawing): crate, ring, fruit, cap, shield, life, money_bag, sword. One ink
             each, as on the Spectrum: the game tints them, so they are drawn in white.

Outputs data/art/sprites/item_map.sprite.json, item_eye.sprite.json and
treasure.sprite.json and merges the names into data/art/sprites/index.json.  Run:  python3 tools/art/item_forge.py
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
    write_sprite("treasure", 16, 16, [(name, treasure(name)) for name in TREASURES])
    update_index(["item_map", "item_eye", "treasure"], lambda n: n.startswith("item_") or n == "treasure")
    print("item forge: item_map (1 frame), item_eye (3 frames), treasure (%d frames)" % len(TREASURES))


if __name__ == "__main__":
    main()
