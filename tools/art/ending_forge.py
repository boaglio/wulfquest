#!/usr/bin/env python3
"""
Ending Forge — Mr Jones, who takes the amulet at the end (AGENTS.md §14.7, [NEW]).

An old jungle hand in a fedora, grey-bearded, in a khaki-green shirt, leaning on a
cane. Original drawing, as JSON pixel art like every other sprite (§10). 16x26,
origin bottom-centre, drawn facing left: he stands on the right and Vale walks up to
him from the left.

  stand, talk          leaning on the cane; talk opens his mouth in the beard
  hold, hold_talk      both hands out, the amulet in them (the screen draws it)

(The later jobs' amulets are quarters of amulet_piece, drawn by creature_forge.py, so
the ending shows the same amulet the game did.)

And the horses Vale and Mr Jones ride home on at the very last ending (§14.7), 32x36,
facing right, origin bottom-centre:

  bay_walk0-3, grey_walk0-3     a bay and a grey, saddled, trotting in with no one on them
  bay_vale0-3, grey_jones0-3    the same at a gallop, Vale on the bay, Mr Jones on the grey

Outputs data/art/sprites/ending_jones.sprite.json and ending_horse.sprite.json and merges the names into data/art/sprites/index.json.
Run:  python3 tools/art/ending_forge.py
"""
import math
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


HW, HH = 32, 36
COATS = {"bay": ("y", "r", "r"), "grey": ("W", "w", "w")}   # body, shade, mane and tail


def horse(coat, frame, rider=None, gallop=False):
    """A horse facing right, saddled; {@code frame} 0-3 steps its legs; a rider sits it if named."""
    body, shade, mane = COATS[coat]
    cv = Canvas(HW, HH)
    g = HH - 1                                       # the ground
    lift = (0, -1, 0, 1)[frame] if gallop else 0
    by = g - 12 + lift                               # the middle of the body
    # Legs first, behind the body: front pair and back pair, swinging against each other.
    swing = (2, 0, -2, 0)[frame] * (2 if gallop else 1)
    for x, phase in ((22, 1), (20, -1), (10, 1), (8, -1)):
        foot = x + phase * swing // 2
        cv.stroke(x, by + 2, foot, g - 1, shade if phase < 0 else body, 0.8, 0.6)
        cv.set(foot, g, "r" if coat == "grey" else "K")
    cv.ellipse(15, by, 9.5, 4.2, body)                                         # the barrel
    cv.ellipse(14, by + 2, 7, 1.8, shade)                                      # its belly in shade
    cv.poly([(20, by - 2), (24, by - 9), (27, by - 7), (24, by + 1)], body)    # the neck
    cv.ellipse(27, by - 8, 3.2, 2.2, body)                                     # the head
    cv.poly([(28, by - 9), (31, by - 7), (29, by - 5)], body)                  # the muzzle
    cv.set(26, by - 11, body)                                                  # the ear
    cv.set(27, by - 9, "K")                                                    # the eye
    cv.stroke(23, by - 9, 20, by - 2, mane, 0.6)                               # the mane
    tail = (0, 1, 2, 1)[frame]
    cv.stroke(6, by - 2, 2, by + 5 + tail, mane, 0.9, 0.5)                     # the tail
    cv.rect(12, by - 4, 18, by - 2, "r")                                       # the saddle
    cv.set(15, by - 1, "R")                                                    # its girth buckle
    if rider == "vale":
        top = by - 18
        cv.rect(13, top, 19, top + 1, "Y")              # the bush hat's crown
        cv.rect(11, top + 2, 20, top + 2, "Y")          # and its brim
        cv.rect(14, top + 3, 18, top + 5, "W")          # face to the right
        cv.set(17, top + 4, "K")
        cv.rect(13, top + 6, 18, top + 11, "C")         # the cyan shirt
        cv.rect(18, top + 7, 21, top + 8, "C")          # an arm to the reins
        cv.set(22, top + 8, "W")
        cv.rect(14, top + 12, 16, top + 17, "w")        # a leg down the horse's side
        cv.rect(14, top + 18, 17, top + 18, "r")        # the boot in its stirrup
    elif rider == "jones":
        top = by - 18
        cv.rect(14, top, 17, top + 1, "y")              # the fedora's crown
        cv.rect(14, top + 2, 17, top + 2, "r")          # its band
        cv.rect(11, top + 2, 20, top + 2, "y")          # and brim
        cv.set(13, top + 2, "y")
        cv.rect(14, top + 3, 18, top + 5, "W")          # face to the right
        cv.set(17, top + 4, "K")
        cv.rect(14, top + 6, 18, top + 7, "w")          # the grey beard
        cv.rect(13, top + 8, 18, top + 12, "g")         # the green shirt
        cv.rect(18, top + 8, 21, top + 9, "g")          # an arm to the reins
        cv.set(22, top + 9, "W")
        cv.rect(14, top + 13, 16, top + 17, "y")        # khaki trousers down the side
        cv.rect(14, top + 18, 17, top + 18, "r")        # the boot
    cv.outline("K")
    return cv.rows()


def main():
    write_sprite("ending_jones", W, H, [("stand", jones(False, False)), ("talk", jones(True, False)),
                                        ("hold", jones(False, True)), ("hold_talk", jones(True, True))])
    frames = []
    for coat in COATS:
        frames += [(f"{coat}_walk{f}", horse(coat, f)) for f in range(4)]
    frames += [(f"bay_vale{f}", horse("bay", f, "vale", gallop=True)) for f in range(4)]
    frames += [(f"grey_jones{f}", horse("grey", f, "jones", gallop=True)) for f in range(4)]
    write_sprite("ending_horse", HW, HH, frames)
    update_index(["ending_jones", "ending_horse"], lambda n: n.startswith("ending_"))
    print("ending forge: ending_jones (4 frames), ending_horse (%d)" % len(frames))


if __name__ == "__main__":
    main()
