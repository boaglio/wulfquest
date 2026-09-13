#!/usr/bin/env python3
"""
Character Forge — draws Ranger Vale, frame by frame, as JSON pixel art.

AGENTS.md §10 and §11.5–§11.7. Every frame is composed from small hand-authored
stamps (hat, head, torso) plus limbs stroked between joints, then outlined.
Nothing is traced from any image. Deterministic: no randomness at all.

Frames match the animations in data/entities/player.json:
  side_walk0-3, up_walk0-3, down_walk0-3        4-frame gaits (§11.5)
  side_swing0-2, up_swing0-2, down_swing0-2     windup, strike, recover, standing (§11.6)
  <view>_swing<p>_walk<g>                       the same three poses on each walking
                                                gait, so a swing on the move never glides
  die0-3                                        the death sequence (§11.7)

Every swing frame carries a "hand" anchor. The renderer draws the blade out of
that hand, with the hitbox's exact reach and timing; the hitbox itself stays on
the ground plane, where creatures' feet are.

Left-facing side frames are mirrors (anchors mirror with them), never duplicated.

Outputs data/art/sprites/player.sprite.json and merges "player" into
data/art/sprites/index.json.

Run from anywhere:  python3 tools/art/character_forge.py
"""
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # importing the scenery forge must not leave a .pyc behind (§2.4)
sys.path.insert(0, str(Path(__file__).resolve().parent))
from scenery_forge import LEGEND, ROOT, Canvas, update_index  # noqa: E402

W, H = 16, 24

# Wide-brimmed ranger hat: deliberately not the original explorer's pith helmet (§2.1).
HAT = ["....YYYY....",
       "...YYYYYY...",
       "...yyyyyy...",
       "YYYYYYYYYYYY"]

HEAD = {
    "down": ["WWWWWW",
             "WKWWKW",
             "WWWWWW",
             ".WWWW."],
    "side": ["WWWWW.",
             "WWWKW.",
             "WWWWWW",
             ".WWWW."],
    "up":   ["yyyyyy",
             "yyyyyy",
             "WyyyyW",
             ".WWWW."],
}

TORSO = {
    "down": [".CCCCCC.",
             "CCCCCCCC",
             "CCCcCCCC",
             "CCCCCCCC",
             "CCCCCCCC",
             ".CCCCCC.",
             ".yyyyyy."],
    "side": [".CCCC.",
             "CCCCCC",
             "CCCCCc",
             "CCCCCc",
             "CCCCCc",
             ".CCCC.",
             ".yyyy."],
    "up":   [".CCCCCC.",           # the back: a pack, so up never reads as down
             "CrrrrrrC",
             "CrRRRRrC",
             "CrrrrrrC",
             "CrrrrrrC",
             ".CCCCCC.",
             ".yyyyyy."],
}

# Side gait: front foot x, back foot x, free hand, body bob. Contact, passing, contact, passing.
SIDE_GAIT = [(11, 4, (5, 13), 0), (8, 7, (8, 14), -1), (11, 4, (11, 13), 0), (8, 7, (8, 14), -1)]
SIDE_STANCE = (11, 4, None, 0)
# Front and back gait: left leg lift, right leg lift, left hand y, right hand y.
FRONT_GAIT = [(0, 0, 13, 13), (1, 0, 14, 12), (0, 0, 13, 13), (0, 1, 12, 14)]
FRONT_STANCE = (0, 0, 13, 13)
# The sword hand through windup, strike, recover.
SIDE_SWING_HAND = [(4, 4), (15, 9), (13, 13)]            # behind the hat, level forward, low
FRONT_SWING_HAND = {
    "down": [(13, 2), (11, 19), (12, 15)],               # overhead, then down toward the viewer
    "up":   [(13, 15), (12, 1), (13, 6)],                # low, then up and away
}


def stamp(cv, rows, x, y):
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != ".":
                cv.set(x + dx, y + dy, ch)


def arm(cv, shoulder, hand):
    cv.stroke(shoulder[0], shoulder[1], hand[0], hand[1], "C")
    cv.set(hand[0], hand[1], "W")


def upper(cv, view, bob=0, hat_dy=0):
    stamp(cv, TORSO[view], 5 if view == "side" else 4, 8 + bob)
    stamp(cv, HEAD[view], 5, 4 + bob)
    stamp(cv, HAT, 2, bob + hat_dy)


def leg_side(cv, foot_x, lift=0):
    """A stride leg from the hip, 2 px wide, with a boot whose toe points forward (right)."""
    for off in (0, 1):
        cv.stroke(7 + off, 15, foot_x + off, 21 - lift, "w")
    cv.rect(foot_x - 1, 22 - lift, foot_x + 3, 24 - lift, "r")


def legs_front(cv, left_lift=0, right_lift=0):
    for x, lift in ((5, left_lift), (9, right_lift)):
        cv.rect(x, 15, x + 2, 22 - lift, "w")
        cv.rect(x, 22 - lift, x + 2, 24 - lift, "r")


def finish(cv):
    cv.outline("K")
    rows = cv.rows()
    assert len(rows) == H and all(len(r) == W for r in rows)
    return rows


# ---------------------------------------------------------------- the frames

def side_walk(g):
    front, back, hand, bob = SIDE_GAIT[g]
    cv = Canvas(W, H)
    leg_side(cv, back)
    leg_side(cv, front)
    upper(cv, "side", bob)
    arm(cv, (8, 9 + bob), (hand[0], hand[1] + bob))
    return finish(cv), None


def front_walk(view, g):
    left_lift, right_lift, left_hand_y, right_hand_y = FRONT_GAIT[g]
    cv = Canvas(W, H)
    legs_front(cv, left_lift, right_lift)
    upper(cv, view)
    arm(cv, (4, 9), (3, left_hand_y))
    arm(cv, (11, 9), (12, right_hand_y))
    return finish(cv), None


def side_swing(phase, gait=None):
    """A swing pose; with a gait, on that gait's legs and body bob."""
    front, back, _, bob = SIDE_STANCE if gait is None else SIDE_GAIT[gait]
    cv = Canvas(W, H)
    leg_side(cv, back)
    leg_side(cv, front)
    upper(cv, "side", bob)
    hx, hy = SIDE_SWING_HAND[phase]
    hy += bob
    arm(cv, (8, 9 + bob), (hx, hy))
    return finish(cv), {"hand": {"x": hx, "y": hy}}


def front_swing(view, phase, gait=None):
    left_lift, right_lift, left_hand_y, _ = FRONT_STANCE if gait is None else FRONT_GAIT[gait]
    cv = Canvas(W, H)
    legs_front(cv, left_lift, right_lift)
    upper(cv, view)
    arm(cv, (4, 9), (3, left_hand_y))
    hx, hy = FRONT_SWING_HAND[view][phase]
    arm(cv, (11, 9), (hx, hy))
    return finish(cv), {"hand": {"x": hx, "y": hy}}


def die(i):
    cv = Canvas(W, H)
    if i == 0:        # struck: arms flung out, hat jolted up
        legs_front(cv)
        upper(cv, "down", hat_dy=-1)
        arm(cv, (4, 9), (1, 6))
        arm(cv, (11, 9), (14, 6))
        return finish(cv), None
    if i == 1:        # buckling to the knees
        cv.rect(5, 19, 7, 22, "w")
        cv.rect(9, 19, 11, 22, "w")
        cv.rect(4, 22, 7, 24, "r")
        cv.rect(9, 22, 12, 24, "r")
        upper(cv, "down", bob=4)
        arm(cv, (4, 13), (3, 18))
        arm(cv, (11, 13), (12, 18))
        return finish(cv), None
    blank = "." * W
    rows = {
        2: [blank] * 14 + [  # toppled onto his side, hat falling
            "..YYYY..........",
            ".yyyyyy.........",
            "YYYYYYYY........",
            "..WWW.CCCCCC....",
            ".WWKWCCCCCCCyww.",
            ".WWWWCCCCCCCywwr",
            "..WW..CCCCCC.wwr",
            "......W....W....",
        ] + [blank] * 2,
        3: [blank] * 16 + [  # flat on the ground, hat knocked clear
            "...........YYYY.",
            "..........YYYYYY",
            "................",
            "..WWW.CCCCCC....",
            ".WWKWCCCCCCCyww.",
            ".WWWWCCCCCCCywwr",
            "..WW.WCCCCCCWwwr",
            "................",
        ],
    }[i]
    stamp(cv, rows, 0, 0)
    return finish(cv), None


def main():
    frames = []

    def add(fid, drawn):
        rows, anchors = drawn
        frames.append((fid, rows, anchors))

    for g in range(4):
        add("side_walk%d" % g, side_walk(g))
    for view in ("up", "down"):
        for g in range(4):
            add("%s_walk%d" % (view, g), front_walk(view, g))
    for p in range(3):
        add("side_swing%d" % p, side_swing(p))
    for view in ("up", "down"):
        for p in range(3):
            add("%s_swing%d" % (view, p), front_swing(view, p))
    # Swing poses on walking legs: a swing on the move keeps walking instead of gliding (§11.6).
    for p in range(3):
        for g in range(4):
            add("side_swing%d_walk%d" % (p, g), side_swing(p, g))
    for view in ("up", "down"):
        for p in range(3):
            for g in range(4):
                add("%s_swing%d_walk%d" % (view, p, g), front_swing(view, p, g))
    for i in range(4):
        add("die%d" % i, die(i))

    used = sorted({c for _, rows, _ in frames for r in rows for c in r}, key=lambda c: LEGEND[c])
    out_frames = []
    for fid, rows, anchors in frames:
        entry = {"id": fid, "rows": rows}
        if anchors:
            entry["anchors"] = anchors
        out_frames.append(entry)
    sprite = {
        "schemaVersion": 1,
        "name": "player",
        "size": {"w": W, "h": H},
        "origin": {"x": W // 2, "y": H},
        "legend": {c: LEGEND[c] for c in used},
        "frames": out_frames,
        # Left-facing side frames are mirrors of the right-facing ones (§10.1).
        "mirror": {fid + "_l": {"from": fid, "flipX": True} for fid, _, _ in frames if fid.startswith("side_")},
    }
    out = ROOT / "data/art/sprites/player.sprite.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(sprite, f, indent=2)
        f.write("\n")
    update_index(["player"], lambda n: n == "player")
    print(f"character forge: drew {len(frames)} frames (+{len(sprite['mirror'])} mirrored) for Ranger Vale")


if __name__ == "__main__":
    main()
