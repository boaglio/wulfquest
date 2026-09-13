#!/usr/bin/env python3
"""
Character Forge — draws Ranger Vale, frame by frame, as JSON pixel art.

AGENTS.md §10 and §11.5. Every frame is composed from small hand-authored
stamps (hat, head, torso) plus limbs stroked between joints, then outlined.
Nothing is traced from any image. Deterministic: no randomness at all.

Frames match the animations in data/entities/player.json exactly:
  side_walk0-3, up_walk0-3, down_walk0-3      4-frame gaits (§11.5)
  side_swing0-2, up_swing0-2, down_swing0-2   windup, strike, recover (§11.6)
  die0-3                                      the death sequence (§11.7)
Left-facing side frames are mirrors, declared in the file, never duplicated.
The blade itself is not in the sprite: the renderer draws it from the live
hitbox, so what you see is exactly what hits.

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

def side_walk(i):
    # contact, passing (bob), contact with the other arm forward, passing (bob)
    front, back, hand, bob = [(11, 4, (5, 13), 0), (8, 7, (8, 14), -1),
                              (11, 4, (11, 13), 0), (8, 7, (8, 14), -1)][i]
    cv = Canvas(W, H)
    leg_side(cv, back)
    leg_side(cv, front)
    upper(cv, "side", bob)
    arm(cv, (8, 9 + bob), (hand[0], hand[1] + bob))
    return finish(cv)


def front_walk(view, i):
    left_lift, right_lift, left_hand_y, right_hand_y = [(0, 0, 13, 13), (1, 0, 14, 12),
                                                        (0, 0, 13, 13), (0, 1, 12, 14)][i]
    cv = Canvas(W, H)
    legs_front(cv, left_lift, right_lift)
    upper(cv, view)
    arm(cv, (4, 9), (3, left_hand_y))
    arm(cv, (11, 9), (12, right_hand_y))
    return finish(cv)


def side_swing(i):
    hand = [(4, 4), (15, 9), (13, 13)][i]          # windup behind the hat, strike level, recover low
    cv = Canvas(W, H)
    leg_side(cv, 4)
    leg_side(cv, 11)
    upper(cv, "side")
    arm(cv, (8, 9), hand)
    return finish(cv)


def front_swing(view, i):
    if view == "down":
        hand = [(13, 2), (11, 19), (12, 15)][i]   # overhead, then down toward the viewer
    else:
        hand = [(13, 15), (12, 1), (13, 6)][i]    # low, then up and away
    cv = Canvas(W, H)
    legs_front(cv)
    upper(cv, view)
    arm(cv, (4, 9), (3, 13))
    arm(cv, (11, 9), hand)
    return finish(cv)


def die(i):
    cv = Canvas(W, H)
    if i == 0:        # struck: arms flung out, hat jolted up
        legs_front(cv)
        upper(cv, "down", hat_dy=-1)
        arm(cv, (4, 9), (1, 6))
        arm(cv, (11, 9), (14, 6))
        return finish(cv)
    if i == 1:        # buckling to the knees
        cv.rect(5, 19, 7, 22, "w")
        cv.rect(9, 19, 11, 22, "w")
        cv.rect(4, 22, 7, 24, "r")
        cv.rect(9, 22, 12, 24, "r")
        upper(cv, "down", bob=4)
        arm(cv, (4, 13), (3, 18))
        arm(cv, (11, 13), (12, 18))
        return finish(cv)
    rows = {
        2: [  # toppled onto his side, hat falling
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "................",
            "..YYYY..........",
            ".yyyyyy.........",
            "YYYYYYYY........",
            "..WWW.CCCCCC....",
            ".WWKWCCCCCCCyww.",
            ".WWWWCCCCCCCywwr",
            "..WW..CCCCCC.wwr",
            "......W....W....",
            "................", "................",
        ],
        3: [  # flat on the ground, hat knocked clear
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "................", "................", "................",
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
    return finish(cv)


def main():
    frames = []
    for i in range(4):
        frames.append(("side_walk%d" % i, side_walk(i)))
    for view in ("up", "down"):
        for i in range(4):
            frames.append(("%s_walk%d" % (view, i), front_walk(view, i)))
    for i in range(3):
        frames.append(("side_swing%d" % i, side_swing(i)))
    for view in ("up", "down"):
        for i in range(3):
            frames.append(("%s_swing%d" % (view, i), front_swing(view, i)))
    for i in range(4):
        frames.append(("die%d" % i, die(i)))

    used = sorted({c for _, rows in frames for r in rows for c in r}, key=lambda c: LEGEND[c])
    sprite = {
        "schemaVersion": 1,
        "name": "player",
        "size": {"w": W, "h": H},
        "origin": {"x": W // 2, "y": H},
        "legend": {c: LEGEND[c] for c in used},
        "frames": [{"id": fid, "rows": rows} for fid, rows in frames],
        # Left-facing side frames are mirrors of the right-facing ones (§10.1).
        "mirror": {fid + "_l": {"from": fid, "flipX": True} for fid, _ in frames if fid.startswith("side_")},
    }
    out = ROOT / "data/art/sprites/player.sprite.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(sprite, f, indent=2)
        f.write("\n")
    update_index(["player"], lambda n: n == "player")
    print(f"character forge: drew {len(frames)} frames (+{len(sprite['mirror'])} mirrored) for Ranger Vale")


if __name__ == "__main__":
    main()
