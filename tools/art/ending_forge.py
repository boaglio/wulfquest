#!/usr/bin/env python3
"""
Ending Forge — Mr Jones, who takes the amulet at the end (AGENTS.md §14.7, [NEW]).

An old jungle hand in a fedora, grey-bearded, in a khaki-green shirt, leaning on a
cane. Original drawing, as JSON pixel art like every other sprite (§10). 16x26,
origin bottom-centre, drawn facing left: he stands on the right and Vale walks up to
him from the left.

  stand, talk          leaning on the cane; talk opens his mouth in the beard
  hold, hold_talk      both hands out, the amulet in them (the screen draws it)

Outputs data/art/sprites/ending_jones.sprite.json and merges the name into
data/art/sprites/index.json.  Run:  python3 tools/art/ending_forge.py
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # importing the other forges must not leave a .pyc behind (§2.4)
sys.path.insert(0, str(Path(__file__).resolve().parent))
from scenery_forge import Canvas, update_index  # noqa: E402
from creature_forge import write_sprite  # noqa: E402

W, H = 16, 26


def jones(talking, holding):
    cv = Canvas(W, H)

    def box(x0, y0, x1, y1, c):
        cv.rect(x0, y0, x1 + 1, y1 + 1, c)     # corners inclusive

    # The fedora: a pinched crown, a dark band, a brim wider than his head.
    box(6, 1, 9, 1, "y")
    box(5, 2, 10, 3, "y")
    cv.set(7, 2, "w")                     # the pinch catches the light
    box(5, 4, 10, 4, "r")             # the band
    box(2, 5, 13, 5, "y")             # the brim
    # Face to the left, grey hair behind, a long grey beard.
    box(4, 6, 9, 8, "W")
    cv.set(3, 7, "W")                     # the nose
    cv.set(5, 6, "K")                     # the eye, under the brim
    box(10, 6, 11, 9, "w")            # hair
    box(4, 9, 9, 10, "w")             # moustache and beard
    box(5, 11, 8, 11, "w")
    if talking:
        cv.set(4, 9, "K")
    # The shirt.
    box(5, 12, 11, 18, "g")
    box(6, 12, 8, 12, "W")            # the open collar
    box(5, 18, 11, 18, "y")           # the belt
    # Trousers and boots.
    box(5, 19, 7, 23, "y")
    box(9, 19, 11, 23, "y")
    box(4, 24, 7, 25, "r")
    box(9, 24, 12, 25, "r")
    if holding:
        box(2, 14, 5, 15, "g")        # both arms out
        box(0, 14, 1, 15, "W")        # the hands, cupped
    else:
        box(4, 13, 4, 17, "g")        # the arm down to the cane
        cv.set(3, 17, "W")                # the hand on its crook
        box(2, 17, 2, 25, "y")        # the cane
        cv.set(3, 16, "y")
    cv.outline("K")
    return cv.rows()


def main():
    write_sprite("ending_jones", W, H, [("stand", jones(False, False)), ("talk", jones(True, False)),
                                        ("hold", jones(False, True)), ("hold_talk", jones(True, True))])
    update_index(["ending_jones"], lambda n: n == "ending_jones")
    print("ending forge: ending_jones (4 frames)")


if __name__ == "__main__":
    main()
