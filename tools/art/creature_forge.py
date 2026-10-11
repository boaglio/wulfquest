#!/usr/bin/env python3
"""
Creature Forge — draws the jungle's 13 creatures, the Wulf and the death puff as JSON pixel art.

AGENTS.md §10 and §12.3. Every creature is drawn procedurally from primitives
(ellipses, strokes, stamps) at the size creatures.json gives it, facing right,
feet on the bottom row. Nothing is traced from any image. Deterministic.

Each creature sprite has two frames, walk0 and walk1 — a gait, a slither, a flap,
a hop — plus mirrored walk0_l and walk1_l for facing left. The renderer
alternates them while the creature moves (fliers flap always), and flashes a
creature white while it is hurt.

The puff sprite, two frames, is what a creature leaves for 12 ticks when it dies.

The original's 14 small creatures (§12.8) are drawn here too, from data/entities/jungle.json,
as vermin_<id>: two tones only, since the game inks each in the colour it came in.

The guardians, the Keeper of the Arch, the amulet quarters (§14) and the orchids
(§15) are drawn here too, from data/entities/guardians.json: each guardian is its roster sibling's
silhouette grown into a 32x28 box and recoloured to its quarter's accent, so the
player reads the family and the scale at once.

The Wulf (AGENTS.md §13) is drawn here too, from data/entities/wulf.json: a lean,
pale, hackled beast, deliberately nothing like the original's — two galloping
frames and a "howl" frame it holds at the room's edge while it warns.

Reads data/entities/creatures.json for names and sizes, so sprites always match
the roster. Outputs data/art/sprites/creature_<id>.sprite.json and
puff.sprite.json, and merges them into data/art/sprites/index.json.

Run from anywhere:  python3 tools/art/creature_forge.py
"""
import json
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # importing the scenery forge must not leave a .pyc behind (§2.4)
sys.path.insert(0, str(Path(__file__).resolve().parent))
from scenery_forge import LEGEND, ROOT, Canvas, update_index  # noqa: E402


def legs(cv, xs, top, bottom, frame, col, stride=2):
    """Pairs of legs that swap stride between the two frames."""
    for i, x in enumerate(xs):
        swing = stride if (i + frame) % 2 == 0 else -stride
        cv.stroke(x, top, x + swing, bottom, col, 0.8, 0.8)


def tribesman(cv, f, w, h, spear=False, chief=False):
    base = h - 1
    legs(cv, [w / 2 - 2, w / 2 + 1], base - 8, base, f, "y", 1.5)
    cv.rect(w / 2 - 4, base - 10, w / 2 + 4, base - 6, "R")                  # loincloth
    cv.ellipse(w / 2, base - 13, 3.5, 4, "y")                                  # torso
    cv.ellipse(w / 2 + 0.5, base - 19, 2.8, 3, "y")                            # head
    cv.set(w / 2 + 2, base - 20, "K")                                          # eye
    cv.rect(w / 2 - 3, base - 22, w / 2 + 4, base - 21, "W")                   # headband
    if chief:
        for dx, col in ((-4, "R"), (-2, "Y"), (0, "G"), (2, "Y"), (4, "R")):
            cv.stroke(w / 2 + dx * 0.6, base - 22, w / 2 + dx, base - 27, col, 0.7, 0.6)
        cv.rect(w / 2 - 4, base - 16, w / 2 + 4, base - 10, "M")               # robe
    else:
        cv.stroke(w / 2 - 1, base - 22, w / 2 - 3, base - 23 - 2, "G", 0.7)    # a feather
    arm = (w / 2 + 5, base - 12 - f)
    cv.stroke(w / 2 + 1, base - 15, arm[0], arm[1], "y", 0.7)
    if spear:
        cv.stroke(arm[0], base - 1, arm[0], 2, "W", 0.6)
        cv.stroke(arm[0], 2, arm[0], 0, "C", 0.9)
    elif not chief:
        cv.ellipse(w / 2 - 4, base - 12, 2.5, 3.5, "W")                        # a round shield
        cv.set(w / 2 - 4, base - 12, "R")


def scorpion(cv, f, w, h):
    base = h - 1
    for i in range(3):
        cv.ellipse(3 + i * 2.2, base - 2, 2, 1.8, "Y")
    tail = [(1, base - 3), (0.5, base - 6), (2.5, base - 8.5), (5, base - 8)]
    for (x0, y0), (x1, y1) in zip(tail, tail[1:]):
        cv.stroke(x0, y0, x1, y1, "Y", 0.8)
    cv.set(5 + f, base - 7, "R")                                                # stinger
    cv.stroke(8, base - 2, w - 1, base - 4 + f, "y", 0.6)                       # claws
    cv.stroke(8, base - 1, w - 1, base - 1 - f, "y", 0.6)
    legs(cv, [3, 5, 7], base - 1, base, f, "y", 1)


def snake(cv, f, w, h):
    base = h - 2
    phase = f * math.pi
    for x in range(0, w - 3):
        y = base - 2 + math.sin(x * 0.7 + phase) * 1.6
        cv.ellipse(x + 0.5, y, 1.2, 1.2, "G")
        if x % 3 == 0:
            cv.set(x, y + 1, "Y")
    hy = base - 2 + math.sin((w - 3) * 0.7 + phase) * 1.6
    cv.ellipse(w - 2.5, hy, 2, 1.6, "G")
    cv.set(w - 2, hy - 1, "K")
    cv.stroke(w - 1, hy + 0.5, w, hy + 1.5, "R")                               # tongue


def spider(cv, f, w, h):
    cx, cy = w / 2, h - 5
    for side in (-1, 1):
        for i, a in enumerate((0.3, 0.9, 1.5, 2.1)):
            bend = 1.5 if (i + f) % 2 == 0 else -1.5
            x1 = cx + side * math.cos(a * 0.7) * 5
            y1 = cy - math.sin(a) * 2 + i - 1
            cv.stroke(cx, cy, x1, y1 + bend, "m", 0.5)
            cv.stroke(x1, y1 + bend, x1 + side * 1.5, y1 + bend + 3, "m", 0.5)
    cv.ellipse(cx, cy, 3, 2.6, "M")
    cv.ellipse(cx, cy - 2.5, 1.8, 1.5, "M")
    cv.set(cx - 1, cy - 3, "W")
    cv.set(cx + 1, cy - 3, "W")


def bat(cv, f, w, h):
    cx, cy = w / 2, h / 2
    lift = -3 if f == 0 else 2
    for side in (-1, 1):
        cv.poly([(cx, cy), (cx + side * 6.5, cy + lift), (cx + side * 4, cy + 2), (cx + side * 2, cy + 1)], "M")
    cv.ellipse(cx, cy + 0.5, 2, 2.5, "m")
    cv.set(cx - 1, cy, "R")
    cv.set(cx + 1, cy, "R")


def frog(cv, f, w, h):
    base = h - 1
    if f == 0:     # crouched
        cv.ellipse(w / 2, base - 3, 5, 3.2, "G")
        cv.ellipse(w / 2 - 3, base - 1, 2.5, 1.2, "g")
    else:          # mid-leap: body up, legs trailing
        cv.ellipse(w / 2 + 1, base - 5, 4.5, 2.8, "G")
        cv.stroke(w / 2 - 2, base - 4, 0, base, "g", 1.0, 0.7)
    cv.ellipse(w / 2 + 3, base - 6 - f, 1.4, 1.4, "Y")
    cv.set(w / 2 + 3, base - 6 - f, "K")


def vulture(cv, f, w, h):
    cy = h / 2 + 1
    tip = -5 if f == 0 else 3
    cv.poly([(4, cy), (w / 2, cy - 1), (w / 2 + 1, cy + 1), (0, cy + tip)], "w")
    cv.poly([(w / 2 - 2, cy), (w - 4, cy + tip), (w / 2 + 3, cy + 2)], "w")
    cv.ellipse(w / 2, cy + 1, 4, 2.5, "w")
    cv.ellipse(w / 2 + 4, cy - 1, 2, 1.5, "W")                                # ruff
    cv.ellipse(w - 4, cy - 2, 1.6, 1.4, "R")                                  # bare head
    cv.stroke(w - 3, cy - 2, w - 1, cy - 1, "Y")


def quadruped(cv, f, w, h, body, dark, head_extra):
    base = h - 1
    legs(cv, [4, 7, w - 9, w - 6], base - 5, base, f, dark, 1.5)
    cv.ellipse(w / 2 - 1, base - 7, w / 2 - 3, (h - 6) / 2.6, body)
    head_extra(cv, w, h, base)


def sleeping(cv, w, h, body, dark, head_extra):
    """Lying down, legs folded under: the original's sleeper (§12.9) before it is woken."""
    low = h + 4
    cv.ellipse(w / 2 - 1, low - 1 - 7, w / 2 - 3, (h - 6) / 2.6, body)
    head_extra(cv, w, low, low - 1)
    cv.rect(2, h - 2, w - 4, h - 1, dark)                                       # folded legs


SLEEPERS = {
    "boar": lambda cv, w, h: sleeping(cv, w, h, "r", "r", boar_head),
    "hippo": lambda cv, w, h: sleeping(cv, w, h, "M", "m", hippo_head),
}


def boar_head(cv, w, h, base):
    cv.ellipse(w - 5, base - 7, 4, 3.2, "r")
    cv.rect(w - 3, base - 6, w, base - 4, "y")                                  # snout
    cv.stroke(w - 3, base - 4, w - 1, base - 7, "W", 0.6)                       # tusk
    cv.set(w - 5, base - 8, "K")
    for x in range(4, w - 8, 3):
        cv.set(x, base - 11, "y")                                               # bristles


def rhino_head(cv, w, h, base):
    cv.ellipse(w - 6, base - 8, 5, 4, "w")
    cv.poly([(w - 3, base - 10), (w, base - 16), (w - 1, base - 9)], "W")      # horn
    cv.set(w - 6, base - 10, "K")
    cv.stroke(w - 9, base - 12, w - 8, base - 15, "w", 0.8)                     # ear


def hippo_head(cv, w, h, base):
    cv.ellipse(w - 6, base - 8, 6, 5, "M")
    cv.rect(w - 6, base - 5, w, base - 4, "K")                                  # the mouth line
    cv.set(w - 4, base - 5, "W")
    cv.set(w - 2, base - 5, "W")
    cv.set(w - 8, base - 12, "K")
    cv.ellipse(w - 10, base - 13, 1.2, 1.2, "m")                                # ear


def wildebeest_head(cv, w, h, base):
    cv.ellipse(w - 5, base - 10, 3.5, 3, "Y")
    cv.stroke(w - 7, base - 13, w - 9, base - 16, "W", 0.6)                     # horns
    cv.stroke(w - 4, base - 13, w - 2, base - 16, "W", 0.6)
    for y in range(int(base - 11), int(base - 5)):
        cv.set(w - 8, y, "y")                                                   # mane
    cv.set(w - 4, base - 10, "K")
    cv.stroke(1, base - 8, 0, base - 4, "y", 0.6)                               # tail


def wulf(cv, f, w, h, howl=False):
    """Pale and long, hackles up, a red eye. Galloping, or sat back and howling."""
    base = h - 1
    if howl:
        cv.stroke(8, base - 2, 0, base, "w", 1.2, 0.5)                                 # tail on the ground
        cv.ellipse(11, base - 5, 7, 4.5, "w")                                          # haunch
        cv.ellipse(17, base - 9, 4.5, 6, "w")                                          # chest
        cv.ellipse(16, base - 11, 3, 4, "W")
        for i in range(4):
            cv.stroke(6 + i * 2.5, base - 9 + i * 0.5, 5 + i * 2.5, base - 12 + i * 0.5, "W", 0.6)   # hackles
        cv.stroke(18, base - 4, 18, base, "w", 0.9)                                    # forelegs
        cv.stroke(21, base - 4, 22, base, "w", 0.9)
        cv.ellipse(21, base - 15, 3.5, 3, "w")                                         # head, raised
        cv.poly([(22, base - 17), (27, base - 21), (25, base - 14)], "w")              # muzzle to the sky
        cv.stroke(24, base - 16, 26, base - 19, "r", 0.5)                              # open jaws
        cv.stroke(19, base - 17, 17, base - 21, "w", 0.9, 0.4)                         # ear
        cv.set(21, base - 16, "R")                                                     # eye
        return
    lift = 1 if f == 1 else 0
    cv.stroke(6, base - 10 - lift, 0, base - 14 + 3 * f, "w", 1.3, 0.5)               # tail streaming
    cv.ellipse(15, base - 9 - lift, 10, 4.5, "w")                                      # body
    cv.ellipse(13, base - 11 - lift, 8, 2.2, "W")                                      # pale back
    for i in range(5):
        cv.stroke(8 + i * 3, base - 13 - lift, 7 + i * 3, base - 16 - lift + i % 2, "W", 0.6)    # hackles
    if f == 0:     # stretched: fore and hind legs reaching apart
        cv.stroke(22, base - 7, 28, base, "w", 1.1, 0.6)
        cv.stroke(20, base - 7, 24, base, "w", 1.0, 0.6)
        cv.stroke(9, base - 7, 3, base, "w", 1.1, 0.6)
        cv.stroke(11, base - 7, 7, base, "w", 1.0, 0.6)
    else:          # gathered: all four under the body
        cv.stroke(22, base - 8, 18, base, "w", 1.1, 0.6)
        cv.stroke(20, base - 8, 15, base, "w", 1.0, 0.6)
        cv.stroke(9, base - 8, 13, base, "w", 1.1, 0.6)
        cv.stroke(11, base - 8, 16, base, "w", 1.0, 0.6)
    cv.ellipse(25, base - 12 - lift, 4, 3.2, "w")                                      # head
    cv.poly([(27, base - 14 - lift), (31, base - 11 - lift), (27, base - 9 - lift)], "w")   # muzzle
    cv.stroke(28, base - 9 - lift, 31, base - 10 - lift, "r", 0.5)                     # jaws
    cv.set(29, base - 9 - lift, "W")                                                   # a fang
    cv.stroke(23, base - 14 - lift, 22, base - 19 - lift, "w", 0.9, 0.4)               # ear
    cv.set(26, base - 13 - lift, "R")                                                  # eye


DRAW = {
    "tribesman": lambda cv, f, w, h: tribesman(cv, f, w, h),
    "spearman": lambda cv, f, w, h: tribesman(cv, f, w, h, spear=True),
    "chief": lambda cv, f, w, h: tribesman(cv, f, w, h, chief=True),
    "scorpion": scorpion,
    "snake": snake,
    "spider": spider,
    "bat": bat,
    "frog": frog,
    "vulture": vulture,
    "boar": lambda cv, f, w, h: quadruped(cv, f, w, h, "r", "r", boar_head),
    "rhino": lambda cv, f, w, h: quadruped(cv, f, w, h, "w", "w", rhino_head),
    "hippo": lambda cv, f, w, h: quadruped(cv, f, w, h, "M", "m", hippo_head),
    "wildebeest": lambda cv, f, w, h: quadruped(cv, f, w, h, "Y", "y", wildebeest_head),
}


BRIGHT = {"brightGreen": "G", "brightBlue": "B", "brightRed": "R", "brightYellow": "Y", "brightCyan": "C",
          "brightMagenta": "M", "brightWhite": "W"}

# ------------------------------------------------------------- the original's swarm (§12.8)
#
# Ours, not the original's: each small creature in two strokes of light and shade, bright
# ("W") for the body and plain ("w") for what is behind. The game inks the whole sprite in
# the colour the creature came in, keeping only the black outline, so the shapes must read
# on their own — as the Spectrum's one-colour sprites did.

def v_parrot(cv, f, w, h):
    base = h - 1
    cv.ellipse(7, base - 5, 3.5, 3, "W")                                       # body
    cv.ellipse(10, base - 8, 2.3, 2.2, "W")                                    # head
    cv.poly([(12, base - 9), (14.5, base - 8), (12, base - 6.5)], "w")          # hooked beak
    cv.set(10, base - 9, "K")
    cv.poly([(4, base - 4), (0, base - 2 + f), (4, base - 2)], "w")             # tail
    wing = -2 if f == 0 else 1
    cv.poly([(5, base - 6), (9, base - 6), (6, base - 9 + wing)], "w")
    cv.stroke(7, base - 2, 7, base, "w", 0.5)


def v_monkey(cv, f, w, h):
    base = h - 1
    legs(cv, [5, 9], base - 4, base, f, "w", 1)
    cv.ellipse(7, base - 5, 3, 2.5, "W")                                       # body
    cv.ellipse(11, base - 8, 2.5, 2.4, "W")                                    # head
    cv.set(9, base - 9, "w")                                                   # ear
    cv.set(12, base - 9, "K")
    cv.stroke(4, base - 5, 1, base - 9 + 2 * f, "w", 0.6)                       # tail, curled up
    cv.stroke(1, base - 9 + 2 * f, 2.5, base - 11 + 2 * f, "w", 0.5)
    cv.stroke(9, base - 5, 13, base - 3 - 2 * f, "W", 0.6)                      # a reaching arm


def v_lizard(cv, f, w, h):
    base = h - 2
    cv.ellipse(8, base - 1, 4.5, 1.6, "W")                                     # body
    cv.ellipse(13, base - 1.5, 2, 1.3, "W")                                    # head
    cv.set(13, base - 2, "K")
    cv.stroke(4, base - 1, 0, base - 2 + f, "w", 0.7, 0.4)                      # tail
    for i, x in enumerate((6, 10)):
        swing = 1.5 if (i + f) % 2 == 0 else -1.5
        cv.stroke(x, base, x + swing, base + 1.5, "w", 0.5)
        cv.stroke(x, base - 2, x - swing, base - 3.5, "w", 0.5)


def v_rat(cv, f, w, h):
    base = h - 1
    legs(cv, [6, 9], base - 2, base, f, "w", 1)
    cv.ellipse(8, base - 3, 4, 2.4, "W")                                       # body
    cv.ellipse(12.5, base - 3.5, 2, 1.6, "W")                                  # snout
    cv.set(11, base - 6, "w")                                                  # ear
    cv.set(13, base - 4, "K")
    cv.stroke(4, base - 3, 0, base - 1 - f, "w", 0.4)                          # tail


def v_bird(cv, f, w, h):
    cy = h / 2
    tip = -4 if f == 0 else 3
    cv.ellipse(8, cy + 1, 3.5, 2, "W")                                         # body
    cv.ellipse(11.5, cy, 1.8, 1.6, "W")                                        # head
    cv.stroke(13, cy, 15, cy + 0.5, "w", 0.4)                                  # beak
    cv.set(12, cy - 0.5, "K")
    cv.poly([(5, cy + 1), (3, cy + 1 + tip), (9, cy)], "w")                    # wing
    cv.poly([(5, cy + 1), (1, cy), (2, cy + 2.5)], "w")                       # tail


def v_mosquito(cv, f, w, h):
    cy = h / 2
    lift = -3 if f == 0 else 1
    cv.ellipse(7, cy + 1, 3.5, 1.2, "W")                                       # body
    cv.ellipse(11, cy, 1.4, 1.4, "W")                                          # head
    cv.stroke(12, cy + 0.5, 15, cy + 2, "w", 0.4)                              # proboscis
    cv.poly([(6, cy), (4, cy - 3 + lift), (9, cy - 1)], "w")                   # wings
    cv.poly([(8, cy), (8, cy - 4 + lift), (10, cy - 1)], "w")
    for x in (6, 8, 10):
        cv.stroke(x, cy + 2, x - 1 + f, h - 1, "w", 0.4)                        # long legs


def v_skunk(cv, f, w, h):
    base = h - 1
    legs(cv, [6, 10], base - 2, base, f, "w", 1)
    cv.ellipse(8.5, base - 3.5, 4, 2.5, "w")                                   # body, dark
    cv.stroke(6, base - 6, 11, base - 6, "W", 0.6)                             # the white stripe
    cv.ellipse(13, base - 3.5, 2, 1.8, "w")                                    # head
    cv.set(13.5, base - 4, "W")
    cv.ellipse(3.5, base - 7 + f, 2.6, 3.4, "W")                               # the great plumed tail


def v_flea(cv, f, w, h):
    base = h - 1
    lift = 0 if f == 0 else -3
    cv.ellipse(8, base - 4 + lift, 3.5, 3, "W")                                # a round armoured back
    cv.stroke(6, base - 6 + lift, 9, base - 7 + lift, "w", 0.5)
    cv.set(11, base - 4 + lift, "K")
    cv.stroke(6, base - 2 + lift, 3, base, "w", 0.6)                           # the jumping legs
    cv.stroke(9, base - 2 + lift, 11, base, "w", 0.5)


def v_earwig(cv, f, w, h):
    base = h - 2
    for i in range(3):
        cv.ellipse(5 + i * 2.6, base - 1, 1.6, 1.4, "W")                        # segments
    cv.ellipse(12.5, base - 1.3, 1.6, 1.3, "W")                                # head
    cv.stroke(14, base - 2, 15.5, base - 3.5 - f, "w", 0.3)                    # feelers
    cv.stroke(3.5, base - 1, 1, base - 3, "w", 0.4)                            # pincers
    cv.stroke(3.5, base - 0.5, 1, base + 1, "w", 0.4)
    for x in (6, 9, 11):
        cv.stroke(x, base, x - 1 + 2 * f, base + 1.5, "w", 0.4)


def inked(draw):
    """One of our roster's drawings redrawn for the swarm: every bright colour bright, every plain one plain."""
    def redraw(cv, f, w, h):
        draw(cv, f, w, h)
        for y in range(cv.h):
            for x in range(cv.w):
                c = cv.px[y][x]
                if c not in (".", "K"):
                    cv.px[y][x] = "W" if LEGEND[c] >= 9 else "w"
    return redraw


VERMIN = {
    "spider": inked(spider),
    "scorpion": inked(scorpion),
    "bat": inked(bat),
    "parrot": v_parrot,
    "monkey": v_monkey,
    "lizard": v_lizard,
    "snake": inked(snake),
    "rat": v_rat,
    "bird": v_bird,
    "frog": inked(frog),
    "mosquito": v_mosquito,
    "skunk": v_skunk,
    "flea": v_flea,
    "earwig": v_earwig,
}


def upscale(rows, w, h, colour):
    """A creature's own frame, grown to fill a bigger box and recoloured to one accent.

    A guardian is its roster sibling seen too large and in the wrong colour — the player
    should read hippo, rhino, boar or wildebeest at a glance, so the shape is the
    sibling's own, pixel for pixel, not a fresh drawing of the same animal.
    """
    sh = len(rows)
    sw = len(rows[0])
    out = []
    for y in range(h):
        line = []
        for x in range(w):
            c = rows[y * sh // h][x * sw // w]
            # Keep the outline and the pale bits — horn, tusk, teeth, eye — and take the rest.
            line.append(c if c in (".", "K", "W") else colour)
        out.append("".join(line))
    return out


def keeper(cv, f, w, h, aside):
    """A tall, still figure with a staff, facing the player. `aside` shifts it off the arch's mouth."""
    base = h - 1
    dx = aside * (w / 6)
    cv.stroke(w / 2 + dx + 5, base - 26, w / 2 + dx + 5, base, "y", 0.7)        # the staff
    cv.ellipse(w / 2 + dx + 5, base - 27, 1.6, 1.6, "C")                        # its stone
    legs(cv, [w / 2 + dx - 3, w / 2 + dx + 1], base - 9, base, f, "w", 1.2)
    cv.rect(w / 2 + dx - 5, base - 22, w / 2 + dx + 4, base - 8, "w")           # robe
    cv.ellipse(w / 2 + dx, base - 24, 3.2, 3.4, "W")                            # head
    cv.set(w / 2 + dx - 1, base - 24, "K")
    cv.set(w / 2 + dx + 2, base - 24, "K")
    cv.rect(w / 2 + dx - 5, base - 27, w / 2 + dx + 4, base - 26, "C")          # headdress
    for i in range(3):
        cv.set(w / 2 + dx - 4 + i * 3, base - 18, "C")                          # necklace


def _inside(pts, x, y):
    """Whether (x, y) is inside the polygon: the even-odd rule."""
    hit = False
    for i in range(len(pts)):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % len(pts)]
        if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
            hit = not hit
    return hit


# The wolf's head on the amulet, front on, in a 32x32 medallion: ears up, cheeks tufted,
# the muzzle narrowing to the nose. Ours — the original's amulet is never traced (§2.1).
WOLF_HEAD = [(8.5, 4), (13, 9), (19, 9), (23.5, 4), (26.5, 12), (26.5, 15), (28, 17.5), (25.5, 18), (26, 20),
             (22.5, 21.5), (19.5, 29), (12.5, 29), (9.5, 21.5), (6, 20), (6.5, 18), (4, 17.5), (5.5, 15), (5.5, 12)]
WOLF_EAR = [(9.4, 7), (12, 10), (8.6, 11.2)]


def amulet_face():
    """The whole amulet, 32x32: 'A' is a quarter's bright accent, 'a' its plain twin.

    A wolf's face in a grey-rimmed medallion, outlined in black so it reads at 16x16 a
    quarter; the brows low over slanting eyes, a black nose, two fangs.
    """
    cv = Canvas(32, 32)
    head = [[_inside(WOLF_HEAD, x + 0.5, y + 0.5) for x in range(32)] for y in range(32)]
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if r > 15.9:
                continue
            if r > 14.5:
                cv.set(x, y, "w")                      # the rim: grey, so the yellow quarter still reads
            elif head[y][x]:
                edge = any(not head[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                cv.set(x, y, "K" if edge else "A")     # the head, outlined
            else:
                cv.set(x, y, "a")                      # the field behind it
    for ear in (WOLF_EAR, [(32 - x, y) for x, y in WOLF_EAR]):
        for y in range(32):
            for x in range(32):
                if _inside(ear, x + 0.5, y + 0.5):
                    cv.set(x, y, "a")                  # inside the ear
    for sx in (1, -1):
        def m(x):
            return x if sx > 0 else 31 - x
        for x, y in ((8, 12), (9, 12), (10, 13), (11, 13), (12, 14), (13, 14)):
            cv.set(m(x), y, "K")                       # the brow, low over the eye
        for x, y in ((9, 14), (10, 14), (11, 15), (12, 15)):
            cv.set(m(x), y, "W")                       # the eye, slanting up and out
        cv.set(m(11), 15, "K")                         # its pupil
        for x, y in ((13, 18), (13, 19), (12, 20), (12, 21)):
            cv.set(m(x), y, "a")                       # the fold beside the muzzle
    for row, xs in ((24, range(13, 19)), (25, range(14, 18)), (26, range(15, 17)), (27, range(15, 17))):
        for x in xs:
            cv.set(x, row, "K")                        # the nose, and the line of the mouth
    cv.set(13, 28, "W")
    cv.set(18, 28, "W")                                # fangs
    return cv


def amulet_quarter(q):
    """One quarter of the amulet (16x16), in its guardian's accent: nw green, ne blue, sw red, se yellow.

    Cut from one face, so the four assemble into it in the panel's 2x2 slots and the reveal.
    """
    hue = (("G", "g"), ("B", "b"), ("R", "r"), ("Y", "y"))[q]
    face = amulet_face()
    ox = 16 if q % 2 else 0
    oy = 16 if q >= 2 else 0
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = face.get(ox + x, oy + y)
            cv.set(x, y, hue[0] if c == "A" else hue[1] if c == "a" else c)
    return cv.rows()


def flame(f):
    """The flame that comes for a player who lingers (§12.7): ours, a teardrop of fire, red
    outside, yellow, then a white heart; the tip leans one way and the other to flicker."""
    cv = Canvas(16, 16)
    lean = 1 if f == 0 else -1
    for y in range(16):
        t = (y - 1) / 13.0                      # 0 at the tip, 1 at the base
        if t < 0 or t > 1:
            continue
        cx = 8 + lean * (1 - t) * 1.8
        half = 5.4 * math.sin(t * math.pi * 0.88) + 0.3   # widest low down, rounding off at the base
        for x in range(16):
            d = abs(x + 0.5 - cx)
            if d > half:
                continue
            ratio = d / max(half, 0.01)
            cv.set(x, y, "W" if ratio < 0.3 and t > 0.45 else "Y" if ratio < 0.65 else "R")
    cv.outline("K")
    return cv.rows()


# A bright letter to its plain twin: a wilted flower keeps its hue and loses its light.
PLAIN = {"G": "g", "B": "b", "R": "r", "Y": "y", "C": "c", "M": "m", "W": "w"}


def orchid_stem(cv, h, lean=0):
    base = h - 1
    cv.stroke(8 + lean, base, 8, base - 7, "g", 0.8, 0.6)
    cv.stroke(8, base - 4, 4, base - 6, "G", 0.6)                               # leaves
    cv.stroke(8, base - 5, 12, base - 7, "G", 0.6)


def orchid_sprout(h):
    """A green shoot: no colour yet, nothing to decide."""
    cv = Canvas(16, 16)
    base = h - 1
    cv.stroke(8, base, 8, base - 5, "g", 0.8, 0.6)
    cv.stroke(8, base - 3, 5, base - 6, "G", 0.6)
    cv.stroke(8, base - 4, 11, base - 6, "G", 0.6)
    return cv.rows()


def orchid_bud(colour):
    """Closed, but already showing its colour: the player's 80 ticks of warning (§15.1)."""
    cv = Canvas(16, 16)
    orchid_stem(cv, 16)
    cv.ellipse(8, 5, 2.6, 3.4, colour)
    cv.ellipse(8, 3.5, 1.4, 1.8, "W")
    cv.stroke(6, 7, 10, 7, "g", 0.6)                                            # sepals
    return cv.rows()


def orchid_bloom(colour, f):
    """Open, swaying: touch it and it is yours, for better or worse."""
    cv = Canvas(16, 16)
    lean = -1 if f else 1
    orchid_stem(cv, 16, lean)
    cx = 8 + lean
    for a in range(5):
        angle = a * 2 * math.pi / 5 - math.pi / 2 + (0.2 if f else 0)
        cv.ellipse(cx + math.cos(angle) * 3.4, 5 + math.sin(angle) * 3.2, 2.2, 2.2, colour)
    cv.ellipse(cx, 5, 1.8, 1.8, "Y")
    cv.set(cx, 5, "K")
    return cv.rows()


def orchid_wilt(colour):
    """Drooping, and drained of its bright: over for this turn."""
    cv = Canvas(16, 16)
    base = 15
    cv.stroke(8, base, 7, base - 5, "g", 0.8, 0.6)
    cv.stroke(7, base - 5, 4, base - 7, "g", 0.7)
    for a in range(4):
        cv.ellipse(4 + math.cos(a * 1.6) * 1.8, base - 7 + math.sin(a * 1.6) * 1.6, 1.6, 1.4, PLAIN[colour])
    return cv.rows()


def puff(f):
    cv = Canvas(12, 12)
    for i in range(6):
        a = i * math.pi / 3 + f * 0.5
        r = 3 + f * 2
        cv.ellipse(6 + math.cos(a) * r, 6 + math.sin(a) * r, 1.6 - f * 0.4, 1.6 - f * 0.4, "W" if i % 2 else "Y")
    if f == 0:
        cv.ellipse(6, 6, 2, 2, "W")
    return cv.rows()


def write_sprite(name, w, h, frames):
    used = sorted({c for _, rows in frames for r in rows for c in r}, key=lambda c: LEGEND[c])
    sprite = {
        "schemaVersion": 1,
        "name": name,
        "size": {"w": w, "h": h},
        "origin": {"x": w // 2, "y": h},
        "legend": {c: LEGEND[c] for c in used},
        "frames": [{"id": fid, "rows": rows} for fid, rows in frames],
        "mirror": {fid + "_l": {"from": fid, "flipX": True} for fid, _ in frames},
    }
    with open(ROOT / f"data/art/sprites/{name}.sprite.json", "w", encoding="utf-8") as out:
        json.dump(sprite, out, indent=2)
        out.write("\n")


def main():
    roster = json.loads((ROOT / "data/entities/creatures.json").read_text(encoding="utf-8"))["creatures"]
    names = []
    drawn = {}
    for species in roster:
        sid, w, h = species["id"], species["size"]["w"], species["size"]["h"]
        if sid not in DRAW:
            raise SystemExit(f"no drawing for creature '{sid}'")
        frames = []
        for f in range(2):
            cv = Canvas(w, h)
            DRAW[sid](cv, f, w, h)
            cv.outline("K")
            frames.append((f"walk{f}", cv.rows()))
        if sid in SLEEPERS:
            cv = Canvas(w, h)
            SLEEPERS[sid](cv, w, h)
            cv.outline("K")
            frames.append(("sleep", cv.rows()))
        name = species["sprite"]
        write_sprite(name, w, h, frames)
        names.append(name)
        drawn[sid] = frames[:2]   # the walk frames: a guardian is its sibling grown, awake
    beast = json.loads((ROOT / "data/entities/wulf.json").read_text(encoding="utf-8"))
    ww, wh = beast["size"]["w"], beast["size"]["h"]
    frames = []
    for fid, f, howl in (("walk0", 0, False), ("walk1", 1, False), ("howl", 0, True)):
        cv = Canvas(ww, wh)
        wulf(cv, f, ww, wh, howl)
        cv.outline("K")
        frames.append((fid, cv.rows()))
    write_sprite(beast["sprite"], ww, wh, frames)
    names.append(beast["sprite"])
    wardens = json.loads((ROOT / "data/entities/guardians.json").read_text(encoding="utf-8"))
    for g in wardens["guardians"]:
        gw, gh = g["size"]["w"], g["size"]["h"]
        sibling = drawn[g["basedOn"]]
        frames = [(fid, upscale(rows, gw, gh, BRIGHT[g["colour"]])) for fid, rows in sibling]
        write_sprite(g["sprite"], gw, gh, frames)
        names.append(g["sprite"])
    k = wardens["keeper"]
    kw, kh = k["size"]["w"], k["size"]["h"]
    frames = []
    for fid, f, aside in (("stand", 0, 0), ("step0", 1, 0.4), ("step1", 0, 0.8), ("aside", 1, 1.2)):
        cv = Canvas(kw, kh)
        keeper(cv, f, kw, kh, aside)
        cv.outline("K")
        frames.append((fid, cv.rows()))
    write_sprite(k["sprite"], kw, kh, frames)
    names.append(k["sprite"])
    write_sprite("amulet_piece", 16, 16, [(fid, amulet_quarter(q)) for q, fid in enumerate(("nw", "ne", "sw", "se"))])
    names.append("amulet_piece")
    flowers = json.loads((ROOT / "data/entities/orchids.json").read_text(encoding="utf-8"))
    frames = [("sprout", orchid_sprout(16))]
    # Every colour either garden grows (§15.2, §15.4): ours in their order, then any only the original's has.
    colours = []
    for o in flowers["inventedOrchids"] + flowers["orchids"]:
        if o["colour"] not in colours:
            colours.append(o["colour"])
    for name in colours:
        colour = BRIGHT["bright" + name[0].upper() + name[1:]]
        frames.append((f"bud_{name}", orchid_bud(colour)))
        frames.append((f"bloom0_{name}", orchid_bloom(colour, 0)))
        frames.append((f"bloom1_{name}", orchid_bloom(colour, 1)))
        frames.append((f"wilt_{name}", orchid_wilt(colour)))
    write_sprite(flowers["sprite"], 16, 16, frames)
    names.append(flowers["sprite"])
    write_sprite("puff", 12, 12, [("f0", puff(0)), ("f1", puff(1))])
    names.append("puff")
    write_sprite("flame", 16, 16, [("walk0", flame(0)), ("walk1", flame(1))])
    names.append("flame")
    jungle = json.loads((ROOT / "data/entities/jungle.json").read_text(encoding="utf-8"))["creatures"]
    for species in jungle:
        sid, w, h = species["id"], species["size"]["w"], species["size"]["h"]
        if sid not in VERMIN:
            raise SystemExit(f"no drawing for the original's creature '{sid}'")
        frames = []
        for f in range(2):
            cv = Canvas(w, h)
            VERMIN[sid](cv, f, w, h)
            cv.outline("K")
            frames.append((f"walk{f}", cv.rows()))
        write_sprite(species["sprite"], w, h, frames)
        names.append(species["sprite"])
    update_index(names, lambda n: n.startswith("creature_") or n.startswith("vermin_")
                 or n in ("puff", "amulet_piece", "orchid", "flame"))
    print(f"creature forge: drew {len(roster)} creatures, the Wulf, {len(wardens['guardians'])} guardians, "
          f"the Keeper, the amulet, orchids in {len(colours)} colours, the puff, the flame and the original's "
          f"{len(jungle)} small creatures")


if __name__ == "__main__":
    main()
