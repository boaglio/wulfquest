#!/usr/bin/env python3
"""
Scenery Forge — draws Wulf Quest's 41 scenery objects as JSON pixel art.

AGENTS.md §9 is the work order: for every object id it gives the exact cell
footprint the extracted map needs, and a subject. Nothing here is traced or
derived from any original image — the source graphics were deleted in M0.
Each object is drawn procedurally from primitives (fronds, trunks, triangles,
blobs) with a deterministic per-object seed, so output is reproducible and
every pixel was authored in this repository.

This is a dev-time authoring tool. Its output is committed; the game reads the
JSON, never this script, and CI does not need Python.

Outputs:
  data/art/sprites/scenery_<id>.sprite.json   one per object     (§10.1)
  data/art/sprites/index.json                 authoritative list (§20.4)
  data/world/scenery.json                     footprints, subjects (§9)

Run from anywhere:  python3 tools/art/scenery_forge.py
"""
import json
import math
import random
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CELL = 8

# Palette index per legend char (data/art/palette.json). lower = normal, UPPER = bright.
LEGEND = {".": -1, "K": 0, "b": 1, "r": 2, "m": 3, "g": 4, "c": 5, "y": 6, "w": 7,
          "B": 9, "R": 10, "M": 11, "G": 12, "C": 13, "Y": 14, "W": 15}


# --------------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [["."] * w for _ in range(h)]

    def set(self, x, y, c):
        x, y = int(math.floor(x)), int(math.floor(y))
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        x, y = int(math.floor(x)), int(math.floor(y))
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else "."

    def rect(self, x0, y0, x1, y1, c):
        for y in range(max(0, int(y0)), min(self.h, int(y1))):
            for x in range(max(0, int(x0)), min(self.w, int(x1))):
                self.px[y][x] = c

    def ellipse(self, cx, cy, rx, ry, c):
        if rx <= 0 or ry <= 0:
            return
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                    self.set(x, y, c)

    def stroke(self, x0, y0, x1, y1, c, r0=0.5, r1=None):
        """A line whose radius tapers from r0 to r1."""
        r1 = r0 if r1 is None else r1
        n = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
        for i in range(n + 1):
            t = i / n
            x, y, r = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r0 + (r1 - r0) * t
            if r <= 0.6:
                self.set(x, y, c)
            else:
                self.ellipse(x, y, r, r, c)

    def poly(self, pts, c):
        ys = [p[1] for p in pts]
        for y in range(max(0, int(min(ys))), min(self.h, int(max(ys)) + 1)):
            yc, xs = y + 0.5, []
            for i in range(len(pts)):
                (xa, ya), (xb, yb) = pts[i], pts[(i + 1) % len(pts)]
                if (ya <= yc < yb) or (yb <= yc < ya):
                    xs.append(xa + (yc - ya) * (xb - xa) / (yb - ya))
            xs.sort()
            for a, b in zip(xs[0::2], xs[1::2]):
                for x in range(max(0, math.ceil(a - 0.5)), min(self.w, math.floor(b - 0.5) + 1)):
                    self.px[y][x] = c

    def stipple(self, rng, density, c, only=None):
        for y in range(self.h):
            for x in range(self.w):
                v = self.px[y][x]
                if v != "." and (only is None or v in only) and rng.random() < density:
                    self.px[y][x] = c

    def outline(self, c="K"):
        """1 px dark rim around every shape: silhouette first (§9 art direction)."""
        src = [row[:] for row in self.px]
        for y in range(self.h):
            for x in range(self.w):
                if src[y][x] != ".":
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    v = src[yy][xx] if 0 <= xx < self.w and 0 <= yy < self.h else "."
                    if v not in (".", c):
                        self.px[y][x] = c
                        break

    def rows(self):
        return ["".join(r) for r in self.px]


# ----------------------------------------------------------------- vegetation

def frond(cv, x, y, ang, length, droop, col, dark, width=1.3):
    """A drooping palm frond: a tapering spine with alternating leaflets."""
    spine = []
    for i in range(int(length) + 1):
        t = i / max(1, length)
        spine.append((x + math.cos(ang) * i, y + math.sin(ang) * i + droop * t * t * length * 0.6, t))
    for sx, sy, t in spine:
        r = width * (1 - 0.7 * t)
        if r > 0.6:
            cv.ellipse(sx, sy, r, r, col)
        else:
            cv.set(sx, sy, col)
    for k, (sx, sy, t) in enumerate(spine):
        if k % 2 == 0 and 0.12 < t < 0.97:
            span = (1 - t) * length * 0.32 + 1.5
            for side in (1, -1):
                a = ang + side * 1.25
                cv.stroke(sx, sy, sx + math.cos(a) * span, sy + math.sin(a) * span + span * 0.6,
                          dark if (k // 2) % 3 == 0 else col)


def palm(cv, rng, bx, by, top, lean, trunk=("r", "R"), leaf=("G", "g"), fronds=6, reach=None, nuts=True):
    reach = reach or max(6, (by - top) * 0.7)
    cx = bx
    for y in range(int(by), int(top) - 1, -1):
        t = (by - y) / max(1, by - top)
        cx = bx + lean * t * t
        cv.rect(cx - 1, y, cx + 2, y + 1, trunk[(y // 2) % 2])
    angles = [-math.pi + 0.35, -math.pi + 0.95, -math.pi / 2 - 0.25,
              -math.pi / 2 + 0.35, -0.95, -0.35][:fronds]
    for a in angles:
        frond(cv, cx, top, a + rng.uniform(-0.12, 0.12), reach * rng.uniform(0.8, 1.05), 1.1, leaf[0], leaf[1])
    if nuts:
        for dx in (-2, 2):
            cv.ellipse(cx + dx, top + 3, 1.6, 1.6, "y")


def fern(cv, rng, bx, by, top, col="G", dark="g", spread=1.0, sway=1.0):
    height = by - top
    for y in range(int(by), int(top) - 1, -1):
        t = (by - y) / max(1, height)
        x = bx + math.sin(t * 3.2) * sway
        cv.set(x, y, dark)
        cv.set(x + 1, y, col)
        if int(by - y) % 3 == 0 and t < 0.97:
            span = (1 - t) * 7 * spread + 2
            side = 1 if (int(by - y) // 3) % 2 else -1
            cv.stroke(x, y, x + side * span, y - span * 0.7, col, 0.9, 0.5)
            cv.stroke(x, y, x - side * span * 0.7, y - span * 0.5, dark, 0.6, 0.5)


def leafy_spine(cv, x0, y0, x1, y1, col="G", dark="g", span=6.0):
    """A fern frond along any direction: a stem with alternating leaflets angled toward the tip."""
    n = int(max(abs(x1 - x0), abs(y1 - y0)))
    ang = math.atan2(y1 - y0, x1 - x0)
    for i in range(n + 1):
        t = i / max(1, n)
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        cv.set(x, y, dark)
        cv.set(x + 1, y, col)
        if i % 3 == 0 and t < 0.95:
            length = (1 - t) * span + 1.5
            side = 1 if (i // 3) % 2 else -1
            a = ang + side * 1.1
            cv.stroke(x, y, x + math.cos(a) * length, y + math.sin(a) * length, col, 0.9, 0.5)
            a = ang - side * 1.1
            cv.stroke(x, y, x + math.cos(a) * length * 0.7, y + math.sin(a) * length * 0.7, dark, 0.6, 0.5)


def broad_leaf(cv, x0, y0, ang, length, width, col="G", dark="g", droop=0.0):
    """A banana-style leaf: a lens-shaped blade with a dark midrib."""
    left, right, spine = [], [], []
    steps = max(4, int(length))
    nx, ny = -math.sin(ang), math.cos(ang)
    for i in range(steps + 1):
        t = i / steps
        x = x0 + math.cos(ang) * length * t
        y = y0 + math.sin(ang) * length * t + droop * t * t * length
        half = width * math.sin(math.pi * min(t, 0.999))
        left.append((x + nx * half, y + ny * half))
        right.append((x - nx * half, y - ny * half))
        spine.append((x, y))
    cv.poly(left + right[::-1], col)
    for (xa, ya), (xb, yb) in zip(spine, spine[1:]):
        cv.stroke(xa, ya, xb, yb, dark)


def agave(cv, rng, bx, by, blades, reach, col="G", dark="g"):
    for i in range(blades):
        a = math.pi + (i + 0.5) / blades * math.pi + rng.uniform(-0.1, 0.1)
        length = reach * rng.uniform(0.7, 1.0) * (0.75 + 0.25 * math.sin((i + 0.5) / blades * math.pi))
        cv.stroke(bx, by, bx + math.cos(a) * length, by + math.sin(a) * length, col if i % 2 else dark, 1.8, 0.5)


def hedge(cv, rng, x0, y0, x1, y1, col="G", dark="g", lumps=None):
    lumps = lumps or int((x1 - x0) * (y1 - y0) / 30) + 3
    for _ in range(lumps):
        rx = rng.uniform(3, 6)
        cv.ellipse(rng.uniform(x0 + rx, x1 - rx), rng.uniform(y0 + rx * 0.8, y1 - 2), rx, rx * 0.85, col)
    cv.rect(x0 + 1, (y0 + y1) / 2, x1 - 1, y1, col)
    cv.stipple(rng, 0.16, dark, only=(col,))


def gourd(cv, cx, cy, rx, ry):
    cv.ellipse(cx, cy, rx, ry, "Y")
    for k in (-2, 0, 2):
        cv.stroke(cx + k * rx / 3, cy - ry + 1, cx + k * rx / 3, cy + ry - 1, "y")
    cv.ellipse(cx - rx / 3, cy - ry / 3, 1.2, 1.0, "W")


def flower(cv, cx, cy, petal, heart="Y"):
    for dx, dy in ((0, -2), (2, 0), (0, 2), (-2, 0)):
        cv.ellipse(cx + dx, cy + dy, 1.4, 1.4, petal)
    cv.set(cx, cy, heart)


def reeds(cv, rng, x0, x1, by, top, count, cols=("G", "g", "R")):
    for i in range(count):
        x = x0 + (i + 0.5) / count * (x1 - x0) + rng.uniform(-1, 1)
        t = top + rng.uniform(0, (by - top) * 0.35)
        bend = rng.uniform(-3, 3)
        c = cols[i % len(cols)]
        cv.stroke(x, by, x + bend, t, c, 0.8, 0.5)
        if c == "R":
            cv.ellipse(x + bend, t + 3, 1.3, 3, "r")
        else:
            mid = (by + t) / 2
            cv.stroke(x + bend * 0.5, mid, x + bend * 0.5 + rng.choice((-4, 4)), mid - 6, c, 0.8, 0.5)


def cypress(cv, rng, cx, by, top, halfw, taper=0.65):
    height = by - top
    tiers = 6
    for k in range(tiers):
        t = k / tiers
        cy = by - 6 - t * (height - 12)
        cv.ellipse(cx + rng.uniform(-1, 1), cy, halfw * (1 - taper * t), height / tiers * 0.85, "G" if k % 2 else "g")
    cv.stipple(rng, 0.12, "g", only=("G",))
    cv.rect(cx - 1, by - 5, cx + 2, by, "r")
    for i in range(5):
        a = -math.pi / 2 + (i - 2) * 0.35
        cv.stroke(cx, top + 8, cx + math.cos(a) * 7, top + 8 + math.sin(a) * 7, "Y", 0.7, 0.5)


# ------------------------------------------------------------------ mountains

def mountain_range(cv, rng, peaks, snow=0.38, clouds=0):
    """peaks: (centre x, base y, half width, height), drawn back to front."""
    for cx, base, halfw, height in peaks:
        apex = base - height
        ridge = [cx + rng.uniform(-1.5, 1.5) for _ in range(int(height) + 2)]
        for y in range(int(apex), int(base)):
            t = (y - apex) / max(1, height)
            half = halfw * t
            for x in range(int(cx - half), int(cx + half) + 1):
                snowline = apex + height * snow + math.sin(x * 0.9) * 2.2
                shade = x > ridge[min(len(ridge) - 1, int(y - apex))] + t * 2
                c = ("w" if shade else "W") if y < snowline else ("m" if shade else "M")
                cv.set(x, y, c)
        for _ in range(2):
            gx = cx + rng.uniform(-halfw * 0.5, halfw * 0.5)
            cv.stroke(gx, base - height * 0.25, gx + rng.uniform(-3, 3), base - 1, "m")
    for _ in range(clouds):
        # Drawn as cloud, not cut as a hole: a transparent gap shows the black
        # ground through the peak and reads as a notch, not weather.
        cx, cy, rx = rng.uniform(10, cv.w - 10), rng.uniform(cv.h * 0.3, cv.h * 0.65), rng.uniform(6, 9)
        cv.ellipse(cx, cy + 1, rx, 2.2, "w")
        cv.ellipse(cx, cy, rx - 1.5, 2.0, "W")
        cv.ellipse(cx - rx / 3, cy - 1.5, rx / 2.5, 1.8, "W")


# ---------------------------------------------------------------------- rock

def rock(cv, rng, x0, y0, x1, y1, grain="v", cracks=2):
    cut = 2
    cv.poly([(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut), (x1, y1 - cut),
             (x1 - cut, y1), (x0 + cut, y1), (x0, y1 - cut), (x0, y0 + cut)], "Y")
    cv.rect(x1 - 3, y0 + 2, x1 - 1, y1 - 2, "y")
    if grain == "v":
        for gx in range(int(x0) + 4, int(x1) - 3, 5):
            x = gx
            for y in range(int(y0) + 2, int(y1) - 2):
                x += rng.choice((-1, 0, 0, 1)) * 0.5
                cv.set(x, y, "y")
    elif grain == "h":
        for gy in range(int(y0) + 4, int(y1) - 2, 5):
            y = gy
            for x in range(int(x0) + 2, int(x1) - 2):
                y += rng.choice((-1, 0, 0, 1)) * 0.4
                cv.set(x, y, "y")
    for _ in range(cracks):
        x, y = rng.uniform(x0 + 4, x1 - 4), y0 + 2
        while y < y1 - 3:
            cv.set(x, y, "K")
            x += rng.choice((-1, 0, 1))
            y += 1
            if rng.random() < 0.08:
                break


def bones(cv, rng, x0, y0, x1, y1, skeleton):
    if skeleton:
        sx, sy = (x0 + x1) / 2, y0 + 8
        cv.ellipse(sx, sy, 4.5, 4, "W")
        cv.set(sx - 2, sy - 1, "K")
        cv.set(sx + 1, sy - 1, "K")
        cv.rect(sx - 1, sy + 2, sx + 2, sy + 3, "K")
        cv.stroke(sx, sy + 4, sx, sy + 20, "W", 0.8)
        for k in range(5):
            ry = sy + 7 + k * 3
            cv.stroke(sx, ry, sx - 6 + k * 0.4, ry + 2, "W")
            cv.stroke(sx, ry, sx + 6 - k * 0.4, ry + 2, "W")
        for side in (-1, 1):
            cv.stroke(sx, sy + 20, sx + side * 7, sy + 30, "W", 0.8)
    for _ in range(3 if skeleton else 7):
        bx, by = rng.uniform(x0 + 6, x1 - 6), rng.uniform(y0 + 6, y1 - 6)
        a, length = rng.uniform(0, math.pi), rng.uniform(4, 8)
        ex, ey = bx + math.cos(a) * length, by + math.sin(a) * length
        cv.stroke(bx, by, ex, ey, "W", 0.6)
        cv.ellipse(bx, by, 1.2, 1.2, "W")
        cv.ellipse(ex, ey, 1.2, 1.2, "W")


def spur(cv, rng, w, h):
    pts = [(w / 2, 0)]
    for y in range(2, h, 4):
        pts.append((w / 2 + (w / 2 - 0.5) * (y / h) + rng.uniform(-1, 1), y))
    pts += [(w, h), (0, h)]
    for y in range(h - 2, 1, -4):
        pts.append((w / 2 - (w / 2 - 0.5) * (y / h) + rng.uniform(-1, 1), y))
    cv.poly(pts, "M")
    cv.stroke(w / 2, 3, w / 2 + 1, h - 1, "m")


# ----------------------------------------------------------------- landmarks

def hut(cv):
    w, h = cv.w, cv.h
    cv.rect(10, 20, w - 10, 30, "r")                      # walls
    cv.rect(w / 2 - 3, 22, w / 2 + 3, 30, "K")            # doorway
    for lx in (8, 18, w - 20, w - 10):                    # stilts
        cv.rect(lx, 28, lx + 3, h, "R")
    cv.rect(6, 29, w - 6, 31, "y")                        # platform
    for y in range(2, 22):                                 # thatched dome
        t = (y - 2) / 20
        half = (w / 2 - 1) * math.sqrt(max(0.0, 1 - (1 - t) ** 2))
        for x in range(int(w / 2 - half), int(w / 2 + half) + 1):
            cv.set(x, y, "y" if (x + y) % 4 == 0 else "Y")
    cv.rect(2, 19, w - 2, 22, "y")                         # eaves
    for x in range(3, w - 3, 3):
        cv.set(x, 22, "Y")
    cv.stroke(w / 2, 0, w / 2, 3, "y")                     # finial


def arch(cv):
    w, h = cv.w, cv.h
    cx = w / 2
    for y in range(h):
        for x in range(w):
            dx, dy = x + 0.5 - cx, y + 0.5 - 22
            outer = (dx / 23) ** 2 + (dy / 22) ** 2 <= 1 or (abs(dx) <= 23 and y >= 22)
            inner = (dx / 13) ** 2 + (dy / 14) ** 2 <= 1 or (abs(dx) <= 13 and y >= 22)
            if outer and not inner:
                c = "W" if ((x // 6) + (y // 5)) % 2 else "w"
                if y % 5 == 0 or (x + (y // 5) * 3) % 6 == 0:
                    c = "w"
                cv.set(x, y, c)
            elif inner:
                cv.set(x, y, "K")                           # the dark mouth: solid
    cv.rect(2, h - 3, w - 2, h, "w")


def water(cv, rng):
    w, h = cv.w, cv.h
    for _ in range(9):
        cv.ellipse(rng.uniform(14, w - 14), rng.uniform(12, h - 10), rng.uniform(9, 16), rng.uniform(7, 11), "B")
    cv.ellipse(w / 2, h / 2, w / 2 - 3, h / 2 - 4, "B")
    for _ in range(4):
        cv.ellipse(rng.uniform(20, w - 20), rng.uniform(15, h - 15), rng.uniform(4, 8), 3, "b")
    for y in range(6, h - 4, 5):
        x = rng.uniform(6, 14)
        while x < w - 8:
            run = rng.randint(2, 4)
            for k in range(run):
                if cv.get(x + k, y) in ("B", "b"):
                    cv.set(x + k, y, "C")
            x += run + rng.randint(4, 9)
    reeds(cv, rng, 0, 7, h - 1, h - 16, 3, cols=("G", "g"))


def grass(cv):
    h = cv.h
    for i, (bx, lean, col) in enumerate(((2, -2, "G"), (4, 2, "g"), (5, 1, "G"), (3, 0, "G"))):
        cv.stroke(bx, h - 1, bx + lean, 3 + i * 3, col, 0.9, 0.5)


# ------------------------------------------------------------- the work order

def draw(k, w, h, rng):
    cv = Canvas(w, h)
    if k == "7298":      # single narrow tall fern
        fern(cv, rng, w / 2 - 1, h - 1, 1, spread=0.45, sway=0.8)
    elif k == "78F2":    # small palm, banded trunk, standing in a tuft of undergrowth
        hedge(cv, rng, 0, h - 9, w, h, lumps=4)
        palm(cv, rng, w / 2, h - 1, 9, 2, reach=12, fronds=6, nuts=False)
    elif k == "7947":    # squat leafy plant with one fruit, on a leafy base
        hedge(cv, rng, 0, h - 10, w, h, lumps=4)
        agave(cv, rng, w / 2, h - 3, 7, 21)
        gourd(cv, w / 2 + 2, h - 5, 3.5, 3)
    elif k == "7462":    # tall spike plant topped with a bloom, sword leaves out to both edges
        hedge(cv, rng, 0, h - 16, w, h)
        for tx, ty in ((1, 12), (w - 1, 14), (0, 28), (w, 30), (1, 44), (w - 1, 44)):
            cv.stroke(w / 2, h - 4, tx, ty, "G" if tx < w / 2 else "g", 2.4, 0.6)
        cv.stroke(w / 2, h - 2, w / 2, 14, "g", 1.0)
        for i in range(4):
            cv.ellipse(w / 2, 6 + i * 3, 4.5 - i * 0.6, 3, "M" if i % 2 == 0 else "m")
        cv.stroke(w / 2, 0, w / 2, 4, "Y")
    elif k == "71B3":    # palm cluster, two crossing trunks, crowns and undergrowth to the edges
        hedge(cv, rng, 0, h - 16, w, h)
        palm(cv, rng, 8, h - 1, 10, 18, reach=16)
        palm(cv, rng, w - 8, h - 1, 6, -20, trunk=("y", "Y"), leaf=("G", "c"), reach=16, nuts=False)
    elif k == "72F6":    # wide broad-leaf bank with hanging fruit, leaves rising to the top corners
        hedge(cv, rng, 0, 14, w, h)
        for x0, ang, length in ((8, -2.25, 22), (18, -1.85, 20), (w - 18, -1.3, 20), (w - 8, -0.9, 22)):
            broad_leaf(cv, x0, 22, ang, length, 5.0)
        palm(cv, rng, w / 2, h - 2, 5, -3, reach=16)
        for fx in (10, w - 12, w / 2 + 10):
            gourd(cv, fx, h - 10, 3, 3.5)
        agave(cv, rng, 8, h - 2, 5, 14, col="C", dark="c")
    elif k == "7523":    # palm grove, dense: edge palms and taller undergrowth fill the footprint
        hedge(cv, rng, 0, h - 30, w, h)
        palm(cv, rng, 5, h - 22, 12, -1, reach=11, nuts=False)
        palm(cv, rng, w - 5, h - 22, 10, 1, reach=11, nuts=False)
        palm(cv, rng, 14, h - 6, 8, 4, reach=16)
        palm(cv, rng, w - 16, h - 6, 5, -4, reach=17)
        palm(cv, rng, w / 2, h - 10, 16, 1, trunk=("y", "Y"), reach=14, nuts=False)
        bones(cv, rng, w / 2 - 6, h - 12, w / 2 + 6, h - 2, False)
    elif k == "7981":    # very tall narrow cypress, crowned: a fuller column
        cypress(cv, rng, w / 2, h - 1, 2, w / 2 - 1, taper=0.3)
    elif k == "771F":    # fern with a gourd at its base: a fanned clump, not a single stem
        hedge(cv, rng, 0, h - 22, w, h)
        for tx, ty in ((1, 9), (8, 2), (w / 2, 0), (w - 8, 2), (w - 1, 9)):
            leafy_spine(cv, w / 2, h - 12, tx, ty, span=7)
        gourd(cv, w / 2 + 7, h - 8, 6, 5.5)
    elif k == "70BC":    # long low leafy bank with flowers
        hedge(cv, rng, 0, 4, w, h)
        for i, fx in enumerate(range(6, w - 4, 11)):
            flower(cv, fx, rng.uniform(6, 12), "R" if i % 2 else "M")
    elif k == "8F2A":    # reed cluster: a dense stand edge to edge
        # A negative top lets reeds() jitter their tips down from ABOVE the sprite
        # (clipped), so the stand reaches the top row without changing reeds()
        # itself, which the water object also uses.
        reeds(cv, rng, 0, w, h - 1, -10, 16)
        hedge(cv, rng, 0, h - 18, w, h)
    elif k == "872A":    # tall rock slab, vertical strata
        rock(cv, rng, 1, 1, w - 1, h - 1, grain="v", cracks=2)
    elif k == "785E":    # spiky agave fan, blades reaching the top of the footprint
        agave(cv, rng, w / 2, h - 2, 13, 30)
    elif k == "95CD":    # low shrub row, one red plant, a fruit
        hedge(cv, rng, 0, 6, w, h)
        agave(cv, rng, w / 3, h - 3, 5, 12, col="R", dark="r")
        gourd(cv, w - 11, h - 8, 4, 3.5)
    elif k == "955D":    # dense blocky hedge
        hedge(cv, rng, 0, 1, w, h, lumps=14)
    elif k == "8702":    # low rock ledge strip, variant A
        rock(cv, rng, 0, 1, w, h, grain="h", cracks=0)
    elif k == "847C":    # wide horizontal strata shelf
        rock(cv, rng, 0, 1, w, h - 1, grain="h", cracks=3)
    elif k == "90A8":    # the big one: mixed palm/banana grove, gourd at base, filled edge to edge
        hedge(cv, rng, 0, h - 42, w, h)
        leafy_spine(cv, 5, h - 40, 2, 14, span=9)
        leafy_spine(cv, w - 5, h - 40, w - 2, 12, span=9)
        for ang, length in ((-2.6, 26), (-2.05, 30), (-1.57, 30), (-1.1, 30), (-0.55, 26)):
            broad_leaf(cv, w / 2, h - 40, ang, length, 6.5, col="C", dark="c", droop=0.15)
        palm(cv, rng, 16, h - 12, 12, 6, reach=18)
        palm(cv, rng, w - 18, h - 10, 6, -5, reach=19)
        gourd(cv, w / 2 - 12, h - 7, 7, 6)
    elif k == "7C0C":    # distant mountain range
        mountain_range(cv, rng, [(12, h, 12, 18), (30, h, 14, 23), (46, h, 11, 16)])
    elif k == "7E4B":    # large massif, heavy snow
        mountain_range(cv, rng, [(16, h, 16, 34), (46, h, 18, 44), (34, h, 22, 55)], snow=0.5)
    elif k == "86DA":    # low rock ledge strip, variant B (pebbles)
        rock(cv, rng, 0, 2, w, h, grain="n", cracks=0)
        for px in range(3, w - 3, 5):
            cv.ellipse(px, 3, 1.3, 1.1, "y")
    elif k == "7BB7":    # single snowy peak
        mountain_range(cv, rng, [(w / 2, h, w / 2, h - 1)], snow=0.45)
    elif k == "7CCD":    # scattered peaks, two tiers
        mountain_range(cv, rng, [(14, h - 20, 12, 22), (42, h - 22, 13, 24),
                                 (10, h, 10, 20), (30, h, 16, 30), (48, h, 9, 18)])
    elif k == "8047":    # mountain range with cloud gaps
        mountain_range(cv, rng, [(12, h, 12, 26), (32, h, 16, 44), (48, h, 10, 30)], clouds=3)
    elif k == "81C5":    # twin peaks, deep valley between
        mountain_range(cv, rng, [(15, h, 15, 52), (42, h, 15, 50)], snow=0.42)
    elif k == "8558":    # short rock column
        rock(cv, rng, 3, 1, w - 3, h, grain="v", cracks=1)
    elif k == "7B11":    # low mountain ridgeline
        mountain_range(cv, rng, [(10, h, 10, 14), (24, h, 13, 20), (38, h, 11, 16)], snow=0.3)
    elif k == "9673":    # single thin grass tuft
        grass(cv)
    elif k == "8B80":    # rock wall slab, smoother face
        rock(cv, rng, 1, 1, w - 1, h - 1, grain="n", cracks=1)
    elif k == "8D3C":    # rock pillar, cracked
        rock(cv, rng, 4, 1, w - 4, h - 1, grain="v", cracks=4)
    elif k in ("83D2", "8427"):  # small rock chunks
        pts = [(rng.uniform(2, 6), rng.uniform(3, 8)), (rng.uniform(12, 16), rng.uniform(0, 3)),
               (rng.uniform(19, 23), rng.uniform(6, 10)), (rng.uniform(19, 23), 22), (rng.uniform(2, 6), 23)]
        cv.poly(pts, "Y")
        cv.stroke(pts[1][0], pts[1][1] + 2, pts[3][0] - 3, 21, "y", 0.8)
        cv.stroke(rng.uniform(6, 10), 8, rng.uniform(8, 14), 18, "K")
    elif k == "8E18":    # the hut
        hut(cv)
    elif k == "8806":    # rock wall with a half-buried skeleton
        rock(cv, rng, 1, 1, w - 1, h - 1, grain="v", cracks=1)
        bones(cv, rng, 8, 4, w - 8, h - 6, True)
    elif k == "89C3":    # rock wall with scattered bones
        rock(cv, rng, 1, 1, w - 1, h - 1, grain="h", cracks=2)
        bones(cv, rng, 4, 4, w - 4, h - 4, False)
    elif k in ("8C5C", "8CCC"):  # cracked rock faces
        rock(cv, rng, 1, 1, w - 1, h - 1, grain="h" if k == "8C5C" else "n", cracks=4)
    elif k in ("83AA", "8382"):  # thin rock spurs
        spur(cv, rng, w, h)
    elif k == "85C8":    # the stone arch
        arch(cv)
    elif k == "93C4":    # water
        water(cv, rng)
    else:
        raise SystemExit(f"no drawing for object {k}")
    cv.outline("K")
    return cv


BIOME = {
    "mountain": {"7C0C", "7E4B", "7BB7", "7CCD", "8047", "81C5", "7B11", "83AA", "8382"},
    "bonefields": {"872A", "8702", "847C", "86DA", "8558", "8B80", "8D3C", "83D2", "8427",
                   "8806", "89C3", "8C5C", "8CCC"},
    "swamp": {"8F2A"},
    "hut": {"8E18"},
    "arch": {"85C8"},
    "water": {"93C4"},
}


def work_order():
    """Footprints and subjects straight from the AGENTS.md §9 table."""
    doc = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
    rows = re.findall(r"^\| `([0-9A-F]{4})` \| (\d+)×(\d+) \| [^|]+\| [^|]+\| [^|]+\| ([^|]+)\|", doc, re.M)
    order = [(i, int(w), int(h), re.sub(r"\*\*", "", s).strip()) for i, w, h, s in rows]
    if len(order) != 41:
        raise SystemExit(f"expected 41 objects in AGENTS.md §9, found {len(order)}")
    return order


def main():
    sprites_dir = ROOT / "data/art/sprites"
    sprites_dir.mkdir(parents=True, exist_ok=True)
    objects, names = {}, []
    for obj_id, cw, ch, subject in work_order():
        w, h = cw * CELL, ch * CELL
        rows = draw(obj_id, w, h, random.Random(int(obj_id, 16))).rows()
        used = sorted({c for r in rows for c in r}, key=lambda c: LEGEND[c])
        name = "scenery_" + obj_id.lower()
        sprite = {"schemaVersion": 1, "name": name, "size": {"w": w, "h": h}, "origin": {"x": 0, "y": 0},
                  "legend": {c: LEGEND[c] for c in used}, "frames": [{"id": "f0", "rows": rows}]}
        with open(sprites_dir / f"{name}.sprite.json", "w", encoding="utf-8") as f:
            json.dump(sprite, f, indent=2)
            f.write("\n")
        names.append(name)
        objects[obj_id] = {"cells": {"w": cw, "h": ch}, "sprite": name, "subject": subject,
                           "biomeHint": next((b for b, ids in BIOME.items() if obj_id in ids), "jungle"),
                           "fidelity": "recon-art-canon-footprint"}
    with open(sprites_dir / "index.json", "w", encoding="utf-8") as f:
        json.dump({"schemaVersion": 1, "sprites": sorted(names)}, f, indent=2)
        f.write("\n")
    with open(ROOT / "data/world/scenery.json", "w", encoding="utf-8") as f:
        json.dump({"schemaVersion": 1, "solidCoveragePercent": 25, "objects": dict(sorted(objects.items()))},
                  f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"scenery forge: drew {len(names)} objects")


if __name__ == "__main__":
    main()
