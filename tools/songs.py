"""The preset tunes (design content.songs) -> app/src/main/assets/songs.txt.

  python3 tools/songs.py

v0.1.3 (QA #3 q3-1, user "Widen the comb"): the comb has 18 tines, G4..C6 with every semitone (Drum.TINES, Comb in
domain/Song.kt). The file starts with the line "#comb 18 G4-C6" and numbers tines 1..18 (G4 = 1, C5 = 6, C6 = 18).
The 17 v0.1.2 tunes keep their pitch: their melody strings below still use the old C-major digits 1..8 (C5 D5 E5 F5 G5
A5 B5 C6) and are mapped through V1 = Drum.FROM_V1; Frere Jacques gets its low sol (G4) back. The tunes added in v0.1.3
are written with note names (tokens like G4 C#5; "-" holds one more column; "|" is a bar line, ignored).

Melody digits (old tunes): "-" holds the previous note for one more step (an empty column); "|" is a bar line.
One step = one column; a drum turn = 64 columns. The column unit is per tune (quarter, eighth or sixteenth note; bpm
counts columns per minute), noted per tune.
Harmony rule (design twinkle.harmony, reused by ode / mary): on the first beat of every second bar add one note a third
below the melody note (or a third above when that falls under 1); the final note gets 1 + 3 + 5.
Checks: every tine in 1..18, <= 64 columns, <= 256 pins, no duplicate (column, tine).
Line format: id|nameZh|nameEn|tagZh|tagEn|licZh|licEn|bpm|c.t;c.t;...|0|group   (t = tine 1..18; the app stores tine-1;
group = kids | folk | classic | holiday; field 9 is updatedAt, 0 for presets).
Not added: The Swan and Air on the G String. Both open on notes held for several bars (a cello / violin line); a tine
dies away in about 2.5 s, so on a drum they turn into a few isolated plucks, and the recognisable part does not fit 64
columns. Turkish March is its opening theme only (the first four bars).
"""
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "songs.txt")


V1 = [5, 7, 9, 10, 12, 14, 16, 17]   # old digit 1..8 -> 0-based tine (Drum.FROM_V1)
NOTE = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
TINES = 18


def tine_of(note):
    """'G4' -> 0 ... 'C6' -> 17 (0-based)."""
    midi = 12 * (int(note[-1]) + 1) + NOTE[note[:-1]]
    t = midi - 67
    assert 0 <= t < TINES, note
    return t


def notes(s):
    """Note-name melody -> ([(col, tine0)], columns). Tokens: note names, '-' = hold one column, '|' ignored."""
    pins, col = [], 0
    for tok in s.replace("|", " ").split():
        if tok == "-" or set(tok) == {"-"}:
            col += len(tok)
        else:
            pins.append((col, tine_of(tok)))
            col += 1
    return pins, col


def chord(col, *names):
    return [(col, tine_of(n)) for n in names]


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
    """Frere Jacques / 两只老虎 (trad. French), one column = one eighth. v0.1.3: the low sol of the last line is back in
    its own octave (G4, tine 0); v0.1.2 had it an octave up because the comb started at C5. Returns 0-based tines."""
    p, n = melody("1-2-3-1- | 1-2-3-1- | 3-4-5--- | 3-4-5--- | 56543-1- | 56543-1- | 1-5-1--- | 1-5-1---")
    assert n == 64
    bass = [(0, 3), (8, 3), (16, 1), (24, 1), (48, 3), (56, 3)]
    out = [(c, V1[t - 1]) for c, t in p + bass + [(60, 3), (60, 5)]]
    return NEW, [(c, tine_of("G4")) if c in (50, 58) else (c, t) for c, t in out]


NEW = "new"   # marker: the function returns 0-based 18-tine pins


# ---------------------------------------------------------------- v0.1.3: tunes that need sharps or the low fourth

def birthday():
    """Happy Birthday / Good Morning to All (Mildred & Patty Hill, 1893), C major, 3/4, one column = one eighth (the
    dotted pickup is two even eighths)."""
    p, n = notes("G4 G4 | A4 - G4 - C5 - | B4 - - - G4 G4 | A4 - G4 - D5 - | C5 - - - G4 G4 | G5 - E5 - C5 - | B4 - A4 - F5 F5 | E5 - C5 - D5 - | C5 - - - - -")
    assert n == 50, n
    return NEW, p + chord(44, "E5", "G5") + chord(8, "G4") + chord(20, "G4") + chord(32, "G4")


def fur_elise():
    """Für Elise (Beethoven, 1810), the A theme, one column = one sixteenth, moved up a fifth to E minor (the original
    A minor reaches down to C4, under the comb)."""
    p, n = notes("B5 A#5 | B5 A#5 B5 F#5 A5 G5 | E5 - - G4 B4 E5 | F#5 - - B4 D#5 F#5 | G5 - - B4 B5 A#5 | B5 A#5 B5 F#5 A5 G5 | E5 - - G4 B4 E5 | F#5 - - B4 G5 F#5 | E5 - -")
    assert n == 47, n
    return NEW, p + chord(44, "B4", "G5")


def greensleeves():
    """Greensleeves (trad. English, 16th c.), 6/8, one column = one eighth (dotted figures as even eighths), in D minor:
    the verse."""
    p, n = notes("D5 | F5 - G5 A5 A#5 A5 | G5 - E5 C5 D5 E5 | F5 - D5 D5 C#5 D5 | E5 - C#5 A4 - D5 | F5 - G5 A5 A#5 A5 | G5 - E5 C5 D5 E5 | F5 E5 D5 C#5 B4 C#5 | D5 - - - - -")
    assert n == 49, n
    bass = chord(1, "D5") + chord(7, "C5") + chord(13, "A4") + chord(19, "A4") + chord(25, "D5") + chord(31, "C5") + chord(37, "A#4")
    return NEW, p + bass + chord(43, "F5", "A5")


def auld_lang_syne():
    """Auld Lang Syne (trad. Scottish, words Robert Burns 1788), 4/4, one column = one eighth, C major: the verse."""
    p, n = notes("G4 - | C5 - - C5 C5 - E5 - | D5 - - C5 D5 - E5 - | C5 - - C5 E5 - G5 - | A5 - - - - - A5 - | G5 - - E5 E5 - C5 - | D5 - - C5 D5 - E5 - | C5 - - A4 A4 - G4 - | C5 -")
    assert n == 60, n
    bass = chord(2, "G4") + chord(10, "G4") + chord(18, "G4") + chord(26, "F5") + chord(34, "E5") + chord(42, "G4")
    return NEW, p + bass + chord(58, "E5", "G5")


def new_year():
    """新年好 Happy New Year (trad. English children's tune), 3/4, one column = one eighth, C major."""
    p, n = notes("C5 C5 C5 - G4 - | E5 E5 E5 - C5 - | C5 E5 G5 - G5 - | F5 E5 D5 - - - | D5 E5 F5 - F5 - | E5 D5 E5 - C5 - | C5 E5 D5 - G4 - | B4 D5 C5 - - -")
    assert n == 48, n
    return NEW, p + chord(6, "G4") + chord(24, "B4") + chord(30, "G4") + chord(44, "E5", "G5")


def farewell():
    """送别 Farewell: "Dreaming of Home and Mother" (John P. Ordway, 1851; Chinese words Li Shutong 1915), 4/4, one
    column = one eighth, C major (do = C5): the first eight bars."""
    p, n = notes("G5 - E5 G5 C6 - - - | A5 - C6 - G5 - - - | G5 - C5 D5 E5 - D5 C5 | D5 - - - - - - - | G5 - E5 G5 C6 - - B5 | A5 - C6 - G5 - - - | G5 - D5 E5 F5 - - B4 | C5 - - - - - - -")
    assert n == 64, n
    bass = chord(0, "C5") + chord(8, "F5") + chord(16, "E5") + chord(24, "G4") + chord(32, "C5") + chord(40, "F5") + chord(48, "B4")
    return NEW, p + bass + chord(56, "E5", "G5")


def silent_night():
    """Silent Night (Franz Gruber, 1818), 6/8, one column = one eighth, G major (do = G4: the tune spans an octave and
    a third). 12 bars are 72 columns; the repeat of bars 5-6 (bars 7-8) is left out so it fits one turn."""
    p, n = notes("D5 E5 D5 B4 - - | D5 E5 D5 B4 - - | A5 - A5 F#5 - - | G5 - G5 D5 - - | E5 - E5 G5 F#5 E5 | D5 E5 D5 B4 - - | A5 - A5 C6 A5 F#5 | G5 - - B5 - - | G5 D5 B4 D5 C5 A4 | G4 - - - - -")
    assert n == 60, n
    bass = chord(0, "G4") + chord(12, "D5") + chord(18, "B4") + chord(24, "C5") + chord(30, "G4") + chord(36, "D5") + chord(45, "G5")
    return NEW, p + bass + chord(54, "B4", "D5")


def scarborough():
    """Scarborough Fair (trad. English), 3/4, one column = one quarter, A Dorian."""
    p, n = notes("A4 - A4 | E5 - E5 | B4 C5 B4 | A4 - - | E5 - G5 | A5 - G5 | E5 F#5 D5 | E5 - - | - - A5 | A5 - A5 | G5 - E5 | E5 D5 C5 | B4 G4 - | A4 - E5 | D5 - C5 | B4 - A4 | G4 - - | A4 - -")
    assert n == 54, n
    bass = chord(12, "A4") + chord(21, "A4") + chord(27, "E5") + chord(36, "G4")
    return NEW, p + bass + chord(51, "C5", "E5")


def turkish():
    """Rondo alla Turca (Mozart, K. 331, 1783), the opening theme, one column = one sixteenth, A minor (original key)."""
    p, n = notes("B4 A4 G#4 A4 | C5 - - - D5 C5 B4 C5 | E5 - - - F5 E5 D#5 E5 | B5 A5 G#5 A5 B5 A5 G#5 A5 | C6 - - -")
    assert n == 32, n
    return NEW, p + chord(4, "A4") + chord(12, "C5") + chord(20, "E5") + chord(24, "E5") + chord(28, "E5", "A5")


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
    # v0.1.3: added back once the comb had sharps and the low fourth
    ("greensleeves", "绿袖子", "Greensleeves", "英国古调", "Old English air", "公版 · 英国民歌 16 世纪", "Public domain · English folk tune, 16th c.", 132, greensleeves, "folk"),
    ("auldLangSyne", "友谊地久天长", "Auld Lang Syne", "老朋友那首", "For old friends", "公版 · 苏格兰民歌（彭斯 1788）", "Public domain · Scottish tune (Burns, 1788)", 132, auld_lang_syne, "folk"),
    ("scarborough", "斯卡布罗集市", "Scarborough Fair", "欧芹鼠尾草", "Parsley, sage", "公版 · 英国民歌", "Public domain · English folk tune", 100, scarborough, "folk"),
    ("farewell", "送别", "Farewell", "长亭外", "Dreaming of home", "公版 · 奥德威 1851（李叔同填词）", "Public domain · J. P. Ordway, 1851", 120, farewell, "folk"),
    ("furElise", "致爱丽丝", "Für Elise", "主题 · e 小调", "Theme, in E minor", "公版 · 贝多芬 1810", "Public domain · Beethoven, 1810", 220, fur_elise, "classic"),
    ("turkish", "土耳其进行曲", "Turkish March", "开头主题", "Opening theme", "公版 · 莫扎特 1783", "Public domain · Mozart, 1783", 240, turkish, "classic"),
    ("birthday", "生日快乐", "Happy Birthday", "吹蜡烛那首", "Candles out", "公版 · 希尔姐妹 1893", "Public domain · Hill sisters, 1893", 132, birthday, "holiday"),
    ("newYear", "新年好", "Happy New Year", "过年那首", "New Year song", "公版 · 英国童谣", "Public domain · English children's tune", 140, new_year, "holiday"),
    ("silentNight", "平安夜", "Silent Night", "G 大调 · 省一次重复", "In G, one repeat out", "公版 · 格鲁伯 1818", "Public domain · Franz Gruber, 1818", 120, silent_night, "holiday"),
]


def pins_of(fn):
    """0-based 18-tine pins: new tunes return (NEW, pins); old tunes return digits 1..8 mapped through V1."""
    r = fn()
    if isinstance(r, tuple) and r and r[0] == NEW:
        return r[1]
    return [(c, V1[t - 1]) for c, t in r]


def main():
    lines = ["#comb 18 G4-C6"]
    for sid, zh, en, tz, te, lz, le, bpm, fn, group in SONGS:
        raw = pins_of(fn)
        pins = sorted(set(raw))
        assert len(pins) == len(raw), f"{sid}: duplicate pins {sorted(raw)}"
        assert all(0 <= c < 64 and 0 <= t < TINES for c, t in pins), sid
        assert len(pins) <= 256, sid
        last = max(c for c, _ in pins)
        L = min(64, ((last + 1 + 3) // 4) * 4)
        print(f"{sid:13s} {group:8s} {len(pins):3d} pins, last column {last:2d}, L = {L:2d}, bpm {bpm}")
        lines.append("|".join([sid, zh, en, tz, te, lz, le, str(bpm), ";".join(f"{c}.{t + 1}" for c, t in pins), "0", group]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("wrote", OUT, len(lines) - 1, "tunes")


if __name__ == "__main__":
    main()
