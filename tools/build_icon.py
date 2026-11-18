"""Music Box icon (design appIcon; background moved from walnut to sage #9DB5A5: walnut / dark brown is taken by theremin
#4A2C1A and beat-pad #3B281C): an open walnut music box seen from the front, its lid's velvet inside with a gold note,
the brass drum in the movement and a brass nameplate. No text.

  ~/.factory-venv/bin/python tools/build_icon.py [--icon512 out.png] [--preview out.png]

Writes res/drawable/icon_3d_layer_{0,1,2}.png + icon_3d_sdf_{0,1,2}.png (PICO layered icon), mipmap-xxxhdpi/
ic_spatial_launcher.png and the adaptive foreground vector res/drawable/ic_launcher_fg.xml (same polygons).
"""
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
S, SS = 1024, 4
SAGE = (0x9D, 0xB5, 0xA5)
WALNUT = (0x4A, 0x32, 0x22)
WALNUT_LIGHT = (0x6B, 0x4A, 0x33)
VELVET = (0x5A, 0x1A, 0x2A)
BRASS = (0xC9, 0xA2, 0x5A)
GOLD = (0xF3, 0xD2, 0x7A)


def circle(cx, cy, r, n=28):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


LID_RIM = [(29, 20), (79, 20), (82, 50), (26, 50)]
LID_IN = [(32.5, 23.5), (75.5, 23.5), (78, 47.5), (30, 47.5)]
TOP = [(26, 50), (82, 50), (86, 58), (22, 58)]
FRONT = [(22, 58), (86, 58), (86, 84), (22, 84)]
DRUM = [(34, 51.5), (74, 51.5), (75, 56.5), (33, 56.5)]
PLATE = [(41, 66), (67, 66), (67, 74), (41, 74)]
NOTE_HEAD = circle(50, 40.5, 4.6)
NOTE_STEM = [(53.2, 25), (55.8, 25), (55.8, 41), (53.2, 41)]
NOTE_FLAG = [(55.8, 25), (64, 25), (64, 30.5), (55.8, 30.5)]
WALLS = [LID_RIM, FRONT]
SMALL = []


def big(draw_fn):
    im = Image.new("RGBA", (S * SS, S * SS), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im), S * SS / 108.0)
    return im.resize((S, S), Image.LANCZOS)


def sc(pts, k):
    return [(x * k, y * k) for x, y in pts]


def layer_bg():
    return big(lambda dr, k: dr.ellipse((0, 0, 108 * k - 1, 108 * k - 1), fill=SAGE + (255,)))


def layer_box():
    def d(dr, k):
        dr.polygon(sc(LID_RIM, k), fill=WALNUT_LIGHT + (255,))
        dr.polygon(sc(LID_IN, k), fill=VELVET + (255,))
        dr.polygon(sc(TOP, k), fill=VELVET + (255,))
        dr.polygon(sc(DRUM, k), fill=BRASS + (255,))
        dr.polygon(sc(FRONT, k), fill=WALNUT + (255,))
        dr.polygon(sc(PLATE, k), fill=BRASS + (255,))
    return big(d)


def layer_tile():
    def d(dr, k):
        for poly in (NOTE_HEAD, NOTE_STEM, NOTE_FLAG):
            dr.polygon(sc(poly, k), fill=GOLD + (255,))
    return big(d)


def sdf(layer):
    inside = layer.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    dist = Image.new("L", inside.size, 0)
    cur = inside
    for i in range(1, 33):
        dist.paste(min(255, i * 8), mask=cur)
        cur = cur.filter(ImageFilter.MinFilter(3) if i % 2 else ImageFilter.Kernel((3, 3), (0, 1, 0, 1, 1, 1, 0, 1, 0), 5))
        cur = cur.point(lambda v: 255 if v >= 255 else 0)
    dist = Image.composite(dist, Image.new("L", inside.size, 0), inside)
    return Image.merge("RGBA", (dist, dist, dist, dist))


def path(pts):
    return "M" + "L".join("%.2f,%.2f" % p for p in pts) + "Z"


def vector():
    hexc = lambda c: "#FF%02X%02X%02X" % c
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Music Box launcher foreground (tools/build_icon.py draws the same artwork for the PICO layered icon). -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">',
        *['    <path android:fillColor="%s" android:pathData="%s" />' % (hexc(c), path(p)) for c, p in (
            (WALNUT_LIGHT, LID_RIM), (VELVET, LID_IN), (VELVET, TOP), (BRASS, DRUM), (WALNUT, FRONT), (BRASS, PLATE),
            (GOLD, NOTE_HEAD), (GOLD, NOTE_STEM), (GOLD, NOTE_FLAG))],
        '</vector>',
    ]
    open(os.path.join(RES, "drawable", "ic_launcher_fg.xml"), "w").write("\n".join(lines) + "\n")


def main():
    layers = [layer_bg(), layer_box(), layer_tile()]
    for i, layer in enumerate(layers):
        layer.save(os.path.join(RES, "drawable", f"icon_3d_layer_{i}.png"))
        sdf(layer).save(os.path.join(RES, "drawable", f"icon_3d_sdf_{i}.png"))
    flat = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for l in layers:
        flat.alpha_composite(l)
    flat.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_spatial_launcher.png"))
    vector()
    if "--icon512" in sys.argv:
        flat.resize((512, 512), Image.LANCZOS).save(sys.argv[sys.argv.index("--icon512") + 1])
    if "--preview" in sys.argv:
        w = 256
        sheet = Image.new("RGB", (w * 4, w), (60, 64, 70))
        for i, t in enumerate(layers + [flat]):
            r = t.resize((w, w), Image.LANCZOS)
            sheet.paste(r, (i * w, 0), r)
        sheet.save(sys.argv[sys.argv.index("--preview") + 1])
    print("ICON ok")


main()
