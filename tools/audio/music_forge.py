#!/usr/bin/env python3
"""
Music Forge — the three tunes, written as text (AGENTS.md §18.3).

Original compositions only. Each tune is authored below as phrases of note
tokens, and this expands them into the flat note list music.json stores:
{ note, octave, ms }, monophonic, square wave, exactly as §18.3 specifies.

A token is NOTE[#]OCTAVE:SIXTEENTHS, or R:SIXTEENTHS for a rest. A bar is four
beats of four sixteenths; every phrase below is two bars, and the forge checks
that, so a mistyped length is caught here and not in your ears.

  title      24 bars, A natural minor, loops — the jungle, deciding about you
  win        8 bars, A major — the arch, and out
  game_over  4 bars, descending — it did not work out

Outputs data/audio/music.json.  Run:  python3 tools/audio/music_forge.py
"""
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "data" / "audio" / "music.json"

SIXTEENTHS_PER_BAR = 16
NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"}

# --- the jungle theme -------------------------------------------------------
# A low riff that keeps circling back to the same two notes, with one phrase
# that climbs and does not resolve. Monophonic: this is a bassline, not a song.
P1 = "A2:2 A2:2 C3:2 A2:2  D3:2 D3:2 C3:2 A2:2  A2:2 A2:2 C3:2 E3:2  G2:2 A2:2 R:4"
P2 = "A2:2 A2:2 C3:2 A2:2  F3:2 F3:2 E3:2 C3:2  D3:2 D3:2 C3:2 A2:2  E3:2 R:2 A2:2 R:2"
P3 = "A3:2 G3:2 E3:2 D3:2  C3:2 D3:2 E3:2 G3:2  A3:2 G3:2 E3:2 C3:2  D3:2 E3:2 A3:4"
P4 = "A2:2 C3:2 E3:2 A3:2  G3:2 E3:2 C3:2 A2:2  F2:2 A2:2 C3:2 F3:2  E3:2 C3:2 A2:4"

TUNES = {
    "title": {
        "bpm": 132,
        "loop": True,
        # 24 bars: the riff, twice; the climb; the turn; and back where it started.
        "phrases": [P1, P1, P2, P1, P3, P3, P4, P2, P1, P3, P4, P2],
    },
    "win": {
        "bpm": 144,
        "loop": False,
        # 8 bars: the call, the answer, the climb, the arch.
        "phrases": [
            "A3:2 C#4:2 E4:2 A4:2  G#4:2 E4:2 F#4:2 G#4:2  A4:4 E4:4  A4:8",
            "F#4:2 G#4:2 A4:2 B4:2  C#5:4 B4:4  A4:2 C#5:2 E5:2 A5:2  A5:8",
            "E4:2 F#4:2 G#4:2 A4:2  B4:2 C#5:2 D5:2 E5:2  C#5:4 A4:4  E5:8",
            "A4:2 E5:2 C#5:2 A4:2  B4:2 G#4:2 E4:2 B4:2  A4:8  A5:8",
        ],
    },
    "game_over": {
        "bpm": 92,
        "loop": False,
        "phrases": [
            "A3:4 G3:4 F3:4 E3:4  D3:4 C3:4 B2:4 A2:4",
            "F2:4 E2:4 D2:8  A2:8 A1:8",
        ],
    },
}


def parse(token):
    note, _, length = token.partition(":")
    sixteenths = int(length)
    if note == "R":
        return {"note": "R", "octave": 0, "sixteenths": sixteenths}
    octave = int(note[-1])
    name = note[:-1]
    if name not in NOTES:
        raise SystemExit("'%s' is not a note name" % name)
    if not 0 <= octave <= 8:
        raise SystemExit("octave %d is outside 0..8" % octave)
    return {"note": name, "octave": octave, "sixteenths": sixteenths}


def expand(tune):
    ms_per_sixteenth = round(60_000 / tune["bpm"] / 4)
    notes = []
    bars = 0
    for phrase in tune["phrases"]:
        total = 0
        for token in phrase.split():
            n = parse(token)
            total += n["sixteenths"]
            notes.append({"note": n["note"], "octave": n["octave"],
                          "ms": n["sixteenths"] * ms_per_sixteenth})
        if total != 2 * SIXTEENTHS_PER_BAR:
            raise SystemExit("a phrase is %d sixteenths — every phrase must be two whole bars" % total)
        bars += total // SIXTEENTHS_PER_BAR
    return notes, bars


def main():
    out = {"schemaVersion": 1, "tunes": {}}
    for name, tune in TUNES.items():
        notes, bars = expand(tune)
        out["tunes"][name] = {"bpm": tune["bpm"], "loop": tune["loop"], "notes": notes}
        print("%-10s %2d bars, %3d notes, %d bpm" % (name, bars, len(notes), tune["bpm"]))
    OUT.write_text(json.dumps(out, indent=2) + "\n")
    print("-> %s" % OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
