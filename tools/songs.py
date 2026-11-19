"""The preset tunes (design content.songs; v0.1.2: 17 public-domain tunes in four groups) -> app/src/main/assets/songs.txt.

  python3 tools/songs.py

Melody strings use tine numbers 1..8 (C D E F G A B C'); "-" holds the previous note for one more step (an empty
column); "|" is a bar line (ignored). One step = one column = a quarter note; a drum turn = 64 columns.
Harmony rule (design twinkle.harmony, reused by ode / mary): on the first beat of every second bar add one note a third
below the melody note (or a third above when that falls under 1); the final note gets 1 + 3 + 5.
Checks: every tine in 1..8, <= 64 columns, <= 256 pins, no duplicate (column, tine).
Line format: id|nameZh|nameEn|tagZh|tagEn|licZh|licEn|bpm|c.t;c.t;...|0|group   (t = tine 1..8; the app stores tine-1;
group = kids | folk | classic | holiday; field 9 is updatedAt, 0 for presets).
v0.1.2 column unit: some tunes use one column = one eighth note (bpm then counts eighths), noted per tune.
Range: the comb is 8 diatonic tines C5..C6 with no sharps, so a tune fits only if every note lands on a white key inside
one octave (C major, or F major / A minor / D dorian without the notes they lack). Tunes that need a sharp or more than
an octave (Happy Birthday, Fur Elise, Greensleeves, Auld Lang Syne, Silent Night, Scarborough Fair, Song of Farewell,
Turkish March, The Swan, Air on the G String, Happy New Year) are left out rather than bent out of shape.
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


def london():
    """London Bridge (trad. English nursery rhyme), C major, quarter columns, played twice (second time with thirds)."""
    return twice_with_harmony("5 6 5 4 | 3 4 5- | 2 3 4- | 3 4 5- | 5 6 5 4 | 3 4 5- | 2- 5- | 3 1--", 32)


def rowboat():
    """Row, Row, Row Your Boat (trad., Eliphalet Oram Lyte 1881), 6/8, one column = one eighth; a two-voice round,
    the second voice two bars (12 columns) behind."""
    p, n = melody("1--1-- | 1-23-- | 3-23-4 | 5----- | 888555 | 333111 | 5-43-2 | 1-----")
    assert n == 48
    return p + shift(p, 12)


def tigers():
    """Frere Jacques / 两只老虎 (trad. French), one column = one eighth. The low sol of the last line is an octave up:
    the comb starts at C5."""
    p, n = melody("1-2-3-1- | 1-2-3-1- | 3-4-5--- | 3-4-5--- | 56543-1- | 56543-1- | 1-5-1--- | 1-5-1---")
    assert n == 64
    bass = [(0, 3), (8, 3), (16, 1), (24, 1), (48, 3), (56, 3)]
    return p + bass + [(60, 3), (60, 5)]


def macdonald():
    """Old MacDonald Had a Farm (trad.), F major (do = tine 4; low sol / la = tines 1 / 2), quarter columns, twice."""
    return twice_with_harmony("4 4 4 1 | 2 2 1- | 6 6 5 5 | 4-- 1 | 4 4 4 1 | 2 2 1- | 6 6 5 5 | 4---", 32)


def jasmine():
    """茉莉花 Mo Li Hua (Jiangsu folk song), pentatonic C, one column = one eighth: the first four lines."""
    p, n = melody("3-35 6886 5-56 5--- | 3-35 6886 5-56 5--- | 5-5- 5356 6-5- ---- | 3-23 5-32 1-12 1---")
    assert n == 64
    return p + [(0, 1), (16, 1), (32, 3), (48, 1), (60, 5)]


def susanna():
    """Oh! Susanna (Stephen Foster, 1848), one column = one eighth: verse, verse, chorus, verse."""
    a1 = "1 2 3 5 5 6 5 3 1 2 3 3 2 1 2 -"
    a2 = "1 2 3 5 5 6 5 3 1 2 3 3 2 2 1 -"
    b = "4 - 6 - 6 - 5 5 3 1 2 - - - - -"
    p, n = melody(" ".join([a1, a2, b, a2]))
    assert n == 64
    return p + [(0, 3), (16, 3), (32, 1), (48, 3), (62, 3), (62, 5)]


def saints():
    """When the Saints Go Marching In (trad. American spiritual), quarter columns."""
    p, n = melody("134 5--- -134 5--- -134 5-3- 1-3- 2--- -332 1--1 3-55 4--- -345 3-1- 2--- 1")
    assert n <= 64, n
    last = max(c for c, _ in p)
    return p + [(3, 3), (11, 3), (19, 3), (last, 3), (last, 5)]


def lullaby():
    """Wiegenlied / Brahms' Lullaby (Johannes Brahms, 1868), 3/4, one column = one eighth: the first half."""
    p, n = melody("33 | 5--33- | 5--35- | 8-7-66 | 5--23- | 4-2-23 | 4--24- | 765-7- | 8-----")
    assert n == 50
    bars = [2 + 6 * k for k in range(8)]
    under = [1, 1, 4, 3, 5, 5, 5, 3]  # a chord tone under each bar's first note (C C F E G G G E), last bar C E G
    out = [(c, t) for c, t in zip(bars, under)]
    return p + out + [(44, 5)]


def morning():
    """Morning Mood from Peer Gynt (Edvard Grieg, 1875), 6/8, one column = one eighth, in C (the original is in E)."""
    p, n = melody("5-32-12-3 | 5-32-12-32-3 | 5-35-63-6 | 5-32-1------")
    assert n <= 64, n
    return p + [(0, 1), (9, 1), (21, 3), (30, 3), (33, 3)]


def largo():
    """Largo / "Going Home" from the New World Symphony (Antonin Dvorak, 1893), quarter columns, twice."""
    return twice_with_harmony("3 5 5- | 3 2 1- | 2 3 5 3 | 2--- | 3 5 5- | 3 2 1- | 2 3 2 1 | 1---", 32)


def joy():
    """Joy to the World (tune Antioch, Lowell Mason 1839 after Handel), one column = one eighth."""
    p, n = melody("8-76 5--4 3-2- 1--- | 56--67--78------ | 8876 5-43 8876 5-43 | 8-65-4343-2-1---")
    assert n == 64
    return p + [(0, 5), (16, 3), (32, 5), (40, 5), (48, 6), (60, 3), (60, 5)]


SONGS = [
    # id, zh, en, tag zh, tag en, source zh, source en, bpm (columns per minute), melody, group
    ("twinkle", "小星星", "Twinkle, Twinkle", "睡前那首", "The bedtime one", "公版 · 法国民歌", "Public domain · French folk tune", 100, twinkle, "kids"),
    ("mary", "玛丽有只小羊羔", "Mary Had a Little Lamb", "四个音就能唱", "Four notes, whole song", "公版 · 美国童谣 1830", "Public domain · US nursery song, 1830", 100, mary, "kids"),
    ("lightlyRow", "小蜜蜂", "Lightly Row", "嗡嗡嗡那首", "The buzzy one", "公版 · 德国民歌", "Public domain · German folk tune", 108, lightly_row, "kids"),
    ("london", "伦敦桥", "London Bridge", "要倒了那首", "Falling down", "公版 · 英国童谣", "Public domain · English nursery rhyme", 120, london, "kids"),
    ("rowboat", "划船歌", "Row Your Boat", "两声部轮唱", "Sung as a round", "公版 · 美国童谣 1881", "Public domain · US round, 1881", 132, rowboat, "kids"),
    ("tigers", "两只老虎", "Frère Jacques", "跑得快那首", "Are you sleeping", "公版 · 法国民歌", "Public domain · French folk tune", 140, tigers, "kids"),
    ("macdonald", "王老先生有块地", "Old MacDonald", "咿呀咿呀哟", "E-I-E-I-O", "公版 · 英美童谣", "Public domain · Folk nursery song", 116, macdonald, "kids"),
    ("jasmine", "茉莉花", "Jasmine Flower", "好一朵", "What a lovely flower", "公版 · 江苏民歌", "Public domain · Jiangsu folk song", 120, jasmine, "folk"),
    ("susanna", "噢！苏珊娜", "Oh! Susanna", "带着班卓琴", "Banjo on my knee", "公版 · 福斯特 1848", "Public domain · Stephen Foster, 1848", 140, susanna, "folk"),
    ("saints", "圣者进行曲", "When the Saints", "爵士老歌", "Marching in", "公版 · 美国灵歌", "Public domain · US spiritual", 132, saints, "folk"),
    ("ode", "欢乐颂", "Ode to Joy", "一听就会哼", "You already know it", "公版 · 贝多芬 1824", "Public domain · Beethoven, 1824", 110, ode, "classic"),
    ("canon", "卡农（简）", "Canon (simple)", "婚礼上那首", "The wedding one", "公版 · 帕赫贝尔", "Public domain · Pachelbel", 72, canon, "classic"),
    ("lullaby", "摇篮曲", "Brahms' Lullaby", "哄睡那首", "Good night", "公版 · 勃拉姆斯 1868", "Public domain · Brahms, 1868", 132, lullaby, "classic"),
    ("morning", "晨景", "Morning Mood", "清晨那首", "Sunrise", "公版 · 格里格 1875", "Public domain · Grieg, 1875", 132, morning, "classic"),
    ("largo", "念故乡", "Going Home", "新世界 慢板", "New World Largo", "公版 · 德沃夏克 1893", "Public domain · Dvořák, 1893", 76, largo, "classic"),
    ("jingle", "铃儿响叮当", "Jingle Bells", "副歌那段", "Just the chorus", "公版 · 皮尔彭特 1857", "Public domain · Pierpont, 1857", 120, jingle, "holiday"),
    ("joy", "普世欢腾", "Joy to the World", "圣诞颂歌", "Christmas carol", "公版 · 梅森 1839", "Public domain · Lowell Mason, 1839", 130, joy, "holiday"),
]


def main():
    lines = []
    for sid, zh, en, tz, te, lz, le, bpm, fn, group in SONGS:
        pins = sorted(set(fn()))
        assert len(pins) == len(fn()), f"{sid}: duplicate pins"
        assert all(0 <= c < 64 and 1 <= t <= 8 for c, t in pins), sid
        assert len(pins) <= 256, sid
        last = max(c for c, _ in pins)
        L = min(64, ((last + 1 + 3) // 4) * 4)
        print(f"{sid:11s} {group:8s} {len(pins):3d} pins, last column {last:2d}, L = {L:2d}, bpm {bpm}")
        lines.append("|".join([sid, zh, en, tz, te, lz, le, str(bpm), ";".join(f"{c}.{t}" for c, t in pins), "0", group]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
