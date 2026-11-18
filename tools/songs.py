"""The six preset tunes (design content.songs) -> app/src/main/assets/songs.txt, validated.

  python3 tools/songs.py

Melody strings use tine numbers 1..8 (C D E F G A B C'); "-" holds the previous note for one more step (an empty
column); "|" is a bar line (ignored). One step = one column = a quarter note; a drum turn = 64 columns.
Harmony rule (design twinkle.harmony, reused by ode / mary): on the first beat of every second bar add one note a third
below the melody note (or a third above when that falls under 1); the final note gets 1 + 3 + 5.
Checks: every tine in 1..8, <= 64 columns, <= 256 pins, no duplicate (column, tine).
Line format: id|nameZh|nameEn|tagZh|tagEn|licZh|licEn|bpm|c.t;c.t;...   (t = tine 1..8; the app stores tine-1)
"""
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "songs.txt")


def melody(s):
    pins, col = [], 0
    for tok in s.replace("|", " ").split():
        for ch in tok:
            if ch == "-":
                col += 1
            else:
                pins.append((col, int(ch)))
                col += 1
    return pins, col


def harmonise(pins, start, end, last_chord=True):
    out = []
    for c, t in pins:
        if start <= c < end and (c - start) % 8 == 0:
            h = t - 2 if t - 2 >= 1 else t + 2
            if h != t and 1 <= h <= 8:
                out.append((c, h))
    if last_chord:
        lc = max(c for c, _ in pins if c < end)
        out += [(lc, 3), (lc, 5)]
    return out


def shift(pins, k):
    return [(c + k, t) for c, t in pins]


def twinkle():
    p, n = melody("1 1 5 5 6 6 5- | 4 4 3 3 2 2 1- | 5 5 4 4 3 3 2- | 5 5 4 4 3 3 2- | 1 1 5 5 6 6 5- | 4 4 3 3 2 2 1-")
    assert n == 48
    return p + harmonise(p, 0, 48)


def twice_with_harmony(m, steps):
    p, n = melody(m)
    assert n == steps, (n, steps)
    second = shift(p, steps)
    return p + second + harmonise(second, steps, 2 * steps)


def ode():
    return twice_with_harmony("3 3 4 5 | 5 4 3 2 | 1 1 2 3 | 3 2 2- | 3 3 4 5 | 5 4 3 2 | 1 1 2 3 | 2 1 1-", 32)


def mary():
    return twice_with_harmony("3 2 1 2 | 3 3 3- | 2 2 2- | 3 5 5- | 3 2 1 2 | 3 3 3 3 | 2 2 3 2 | 1---", 32)


def jingle():
    p, n = melody("3 3 3- | 3 3 3- | 3 5 1 2 | 3--- | 4 4 4 4 | 4 3 3 3 | 3 2 2 3 | 2- 5- | 3 3 3- | 3 3 3- | 3 5 1 2 | 3--- | 4 4 4 4 | 4 3 3 3 | 5 5 4 2 | 1---")
    assert n == 64
    return p + [(60, 3), (60, 5)]


def lightly_row():
    p, n = melody("5 3 3- | 4 2 2- | 1 2 3 4 | 5 5 5- | 5 3 3- | 4 2 2- | 1 3 5 5 | 3--- | 2 2 2 2 | 2 3 4- | 3 3 3 3 | 3 4 5- | 5 3 3- | 4 2 2- | 1 3 5 5 | 1---")
    assert n == 64
    return p + [(60, 3), (60, 5)]


def canon():
    main = [6, 5, 4, 3, 2, 1, 2, 3]
    pins = []
    for i, t in enumerate(main):                       # pass 1: main line on even columns
        pins.append((2 * i, t))
    for i, t in enumerate(main):                       # pass 2: + a chord tone on every odd column
        pins.append((16 + 2 * i, t))
    for i, t in enumerate([8, 3, 6, 1, 4, 4, 4, 5]):
        pins.append((16 + 2 * i + 1, t))
    below = [4, 3, 2, 1, None, None, None, 1]          # pass 3: pass 2 + a chord tone under the main note
    for i, t in enumerate(main):
        pins.append((32 + 2 * i, t))
        if below[i]:
            pins.append((32 + 2 * i, below[i]))
    for i, t in enumerate([8, 3, 6, 1, 4, 4, 4, 5]):
        pins.append((32 + 2 * i + 1, t))
    above = [8, 8, 6, 6, 4, 4, 4, 5]                   # pass 4: main + a chord tone above, odd columns empty
    for i, t in enumerate(main):
        pins += [(48 + 2 * i, t), (48 + 2 * i, above[i])]
    pins += [(63, 4), (63, 6), (63, 8)]                # closing chord
    return pins


SONGS = [
    ("twinkle", "小星星", "Twinkle, Twinkle", "睡前那首", "The bedtime one", "公版 · 法国民歌", "Public domain · French folk tune", 100, twinkle),
    ("ode", "欢乐颂", "Ode to Joy", "一听就会哼", "You already know it", "公版 · 贝多芬 1824", "Public domain · Beethoven, 1824", 110, ode),
    ("mary", "玛丽有只小羊羔", "Mary Had a Little Lamb", "四个音就能唱", "Four notes, whole song", "公版 · 1830", "Public domain · 1830", 100, mary),
    ("jingle", "铃儿响叮当", "Jingle Bells", "副歌那段", "Just the chorus", "公版 · Pierpont 1857", "Public domain · Pierpont, 1857", 120, jingle),
    ("lightlyRow", "小蜜蜂", "Lightly Row", "嗡嗡嗡那首", "The buzzy one", "公版 · 德国民歌", "Public domain · German folk tune", 108, lightly_row),
    ("canon", "卡农（简）", "Canon (simple)", "婚礼上那首", "The wedding one", "公版 · Pachelbel", "Public domain · Pachelbel", 72, canon),
]


def main():
    lines = []
    for sid, zh, en, tz, te, lz, le, bpm, fn in SONGS:
        pins = sorted(set(fn()))
        assert len(pins) == len(fn()), f"{sid}: duplicate pins"
        assert all(0 <= c < 64 and 1 <= t <= 8 for c, t in pins), sid
        assert len(pins) <= 256, sid
        last = max(c for c, _ in pins)
        L = min(64, ((last + 1 + 3) // 4) * 4)
        print(f"{sid:11s} {len(pins):3d} pins, last column {last:2d}, L = {L:2d}, bpm {bpm}")
        lines.append("|".join([sid, zh, en, tz, te, lz, le, str(bpm), ";".join(f"{c}.{t}" for c, t in pins)]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
