"""Builds every model of the music box volume as GLB, in Python + Pillow + numpy (no Blender on this machine).

  ~/.factory-venv/bin/python tools/build_musicbox.py [--test-f]   # --test-f: "F↑" test texture on the lid inner face (D10)

Writes app/src/main/assets/models/ (all coordinates in metres at scale k = 1, design-package spatialLayout; the app
scales the content root by k = 1.28 and by the measured volume size):
  box_<finish>.glb   walls + floor (finish wood), velvet floor liners, the dancer-well deck (velvet, 9.2 cm hole: dancer B's arabesque arm sweeps 4.2 cm) with a
                     mirror ring, the comb pedestal / bedplate / drum standards; origin = volume centre
  lid_<finish>.glb   0.38 x 0.22 x 0.015 lid, velvet inner face; origin = the hinge line (y -0.07, z -0.11) at x = 0
  glass.glb          the 2 mm window over the movement (BLEND, alpha 0.10, no collider; the only non-opaque model)
  stage.glb          dancer lift: 8 cm velvet disc with a brass rim + rod; origin = stage top centre
  drum.glb           brass drum r 4.5 cm x 20 cm, end flanges, axle; origin = drum axis centre; 48 segments
  pin.glb            steel pin r 0.9 mm x 3 mm + round cap, 10 segments (DV8); origin = foot, along +Y
  tine.glb           one steel tine 9.5 -> 8 mm wide, 2 mm thick, 3.95 cm long; origin = root top centre, extends to -Z
                     (v0.1.3: 18 tines at 10.8 mm pitch; the app stretches each clone to its graded length, Layout.tineLen)
  combbase.glb       the steel comb base block with a slanted back edge (bass tines longer); tines are separate entities
  key.glb            winding key: rod + boss + butterfly wings; origin = rod root, rod along +X; 32 segments
Finishes: walnut / maple / red (lacquer). All materials OPAQUE except glass (profile D24 / B12).
Texture convention: image up = +Y (or -Z on horizontal faces), image right = +X.
"""
import io
import json
import math
import os
import random
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "models")
TEST_F = "--test-f" in sys.argv

FINISH = {
    "walnut": ((0x4A, 0x32, 0x22), (0x6B, 0x4A, 0x33), 0.55),
    "maple": ((0xD9, 0xB9, 0x8A), (0xEA, 0xD3, 0xAE), 0.5),
    "red": ((0x8E, 0x1F, 0x1B), (0xB2, 0x3A, 0x2E), 0.22),
}
BRASS = (0xC9, 0xA2, 0x5A)
STEEL = (0xB9, 0xBE, 0xC4)
VELVET = (0x5A, 0x1A, 0x2A)
SILVER = (0xE4, 0xE6, 0xEA)
DARK = (0x2E, 0x26, 0x20)

# ---- design numbers (k = 1), domain/Layout.kt mirrors them
BX0, BX1, BY0, BY1, BZ0, BZ1 = -0.21, 0.17, -0.20, -0.07, -0.11, 0.11
WALL = 0.012
FLOOR_TOP = -0.188
DIV0, DIV1 = -0.08, -0.07          # divider between dancer well and movement
WIN_TOP = -0.11                    # lowered front wall over the movement
WIN_X0, WIN_X1 = -0.07, 0.158
WELL_C = (-0.14, 0.0)
WELL_R = 0.046
DRUM_C = (0.055, -0.125, -0.020)
DRUM_R, DRUM_L = 0.045, 0.20
LID_T = 0.015
UVM = 0.40                          # one texture repeat = 40 cm


def srgb_lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


class Glb:
    def __init__(self):
        self.bin = bytearray()
        self.views, self.accessors = [], []

    def _view(self, data, target=None):
        while len(self.bin) % 4:
            self.bin.append(0)
        off = len(self.bin)
        self.bin += data
        v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
        if target:
            v["target"] = target
        self.views.append(v)
        return len(self.views) - 1

    def floats(self, arr, minmax=False):
        arr = np.asarray(arr, dtype="<f4")
        view = self._view(arr.tobytes(), 34962)
        acc = {"bufferView": view, "componentType": 5126, "count": int(arr.shape[0]), "type": {2: "VEC2", 3: "VEC3"}[arr.shape[1]]}
        if minmax:
            acc["min"] = [float(v) for v in arr.min(axis=0)]
            acc["max"] = [float(v) for v in arr.max(axis=0)]
        self.accessors.append(acc)
        return len(self.accessors) - 1

    def indices(self, idx):
        view = self._view(np.asarray(idx, dtype="<u4").tobytes(), 34963)
        self.accessors.append({"bufferView": view, "componentType": 5125, "count": len(idx), "type": "SCALAR"})
        return len(self.accessors) - 1

    def image(self, im):
        b = io.BytesIO()
        im.convert("RGB").save(b, "PNG", optimize=True)
        return self._view(b.getvalue())

    def write(self, path, gltf):
        gltf["asset"] = {"version": "2.0", "generator": "music-box tools/build_musicbox.py"}
        gltf["buffers"] = [{"byteLength": len(self.bin)}]
        gltf["bufferViews"] = self.views
        gltf["accessors"] = self.accessors
        js = json.dumps(gltf, separators=(",", ":")).encode()
        while len(js) % 4:
            js += b" "
        while len(self.bin) % 4:
            self.bin.append(0)
        total = 12 + 8 + len(js) + 8 + len(self.bin)
        with open(path, "wb") as f:
            f.write(struct.pack("<III", 0x46546C67, 2, total))
            f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
            f.write(struct.pack("<II", len(self.bin), 0x004E4942) + bytes(self.bin))


class Mesh:
    def __init__(self):
        self.pos, self.nor, self.uv = [], [], []
        self.prims = {}

    def add(self, mat, verts, norms, uvs, idx):
        base = len(self.pos)
        self.pos += [tuple(map(float, v)) for v in verts]
        self.nor += [tuple(map(float, n)) for n in norms]
        self.uv += [tuple(map(float, t)) for t in uvs]
        self.prims.setdefault(mat, []).extend(base + i for i in idx)

    def quad(self, mat, a, b, c, d, n, uvs=((0, 0), (1, 0), (1, 1), (0, 1))):
        """Counter-clockwise a-b-c-d seen from the normal side."""
        self.add(mat, (a, b, c, d), (n,) * 4, uvs, (0, 1, 2, 0, 2, 3))

    def tris(self):
        return sum(len(v) for v in self.prims.values()) // 3

    def write(self, name, materials, images=()):
        g = Glb()
        pa = g.floats(self.pos, minmax=True); na = g.floats(self.nor); ta = g.floats(self.uv)
        prims = [{"attributes": {"POSITION": pa, "NORMAL": na, "TEXCOORD_0": ta}, "indices": g.indices(idx), "material": m}
                 for m, idx in sorted(self.prims.items())]
        gltf = {"scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"name": name, "mesh": 0}],
                "meshes": [{"name": name, "primitives": prims}], "materials": materials}
        if images:
            gltf["images"] = [{"bufferView": g.image(im), "mimeType": "image/png"} for im in images]
            gltf["textures"] = [{"source": i, "sampler": 0} for i in range(len(images))]
            gltf["samplers"] = [{"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497}]
        path = os.path.join(OUT, name + ".glb")
        g.write(path, gltf)
        print("%-18s %6d triangles %8d bytes" % (name + ".glb", self.tris(), os.path.getsize(path)))


def mat(name, color, rough=0.6, metal=0.0, tex=None, alpha=None):
    pbr = {"metallicFactor": metal, "roughnessFactor": rough,
           "baseColorFactor": [srgb_lin(c) for c in color] + [1.0 if alpha is None else alpha]}
    if tex is not None:
        pbr["baseColorTexture"] = {"index": tex}
        pbr["baseColorFactor"] = [1.0, 1.0, 1.0, 1.0]
    m = {"name": name, "pbrMetallicRoughness": pbr, "alphaMode": "OPAQUE" if alpha is None else "BLEND", "doubleSided": alpha is not None}
    return m


# ------------------------------------------------------------------ primitives

def cuboid(m, mi, x0, x1, y0, y1, z0, z1, faces="xXyYzZ"):
    """Axis-aligned box with world-planar UVs (UVM metres per repeat). faces: x=-X X=+X y=-Y Y=+Y z=-Z Z=+Z."""
    u = lambda a: a / UVM
    if "Y" in faces:
        m.quad(mi, (x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), (0, 1, 0), ((u(x0), -u(z1)), (u(x1), -u(z1)), (u(x1), -u(z0)), (u(x0), -u(z0))))
    if "y" in faces:
        m.quad(mi, (x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (0, -1, 0), ((u(x0), u(z0)), (u(x1), u(z0)), (u(x1), u(z1)), (u(x0), u(z1))))
    if "Z" in faces:
        m.quad(mi, (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1), ((u(x0), u(y0)), (u(x1), u(y0)), (u(x1), u(y1)), (u(x0), u(y1))))
    if "z" in faces:
        m.quad(mi, (x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1), ((-u(x1), u(y0)), (-u(x0), u(y0)), (-u(x0), u(y1)), (-u(x1), u(y1))))
    if "X" in faces:
        m.quad(mi, (x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0), ((-u(z1), u(y0)), (-u(z0), u(y0)), (-u(z0), u(y1)), (-u(z1), u(y1))))
    if "x" in faces:
        m.quad(mi, (x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0), ((u(z0), u(y0)), (u(z1), u(y0)), (u(z1), u(y1)), (u(z0), u(y1))))


def cylinder_x(m, mi, x0, x1, r, cy, cz, seg=48, caps=True, cap_mat=None):
    """Cylinder along X, smooth sides, flat caps."""
    vs, ns, us, idx = [], [], [], []
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        y, z = math.cos(a), math.sin(a)
        for x in (x0, x1):
            vs.append((x, cy + r * y, cz + r * z)); ns.append((0, y, z)); us.append((i / seg, (x - x0) / UVM))
    for i in range(seg):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, d, c, a, c, b]
    m.add(mi, vs, ns, us, idx)
    if caps:
        cm = mi if cap_mat is None else cap_mat
        for x, sgn in ((x0, -1), (x1, 1)):
            vs = [(x, cy, cz)] + [(x, cy + r * math.cos(2 * math.pi * i / seg), cz + r * math.sin(2 * math.pi * i / seg)) for i in range(seg)]
            us = [(0.5, 0.5)] + [(0.5 + 0.5 * math.cos(2 * math.pi * i / seg), 0.5 + 0.5 * math.sin(2 * math.pi * i / seg)) for i in range(seg)]
            idx = []
            for i in range(seg):
                j = 1 + i; k = 1 + (i + 1) % seg
                idx += [0, j, k] if sgn > 0 else [0, k, j]
            m.add(cm, vs, [(sgn, 0, 0)] * len(vs), us, idx)


def cylinder_y(m, mi, y0, y1, r, cx, cz, seg=32, top=True, bottom=True, top_mat=None):
    vs, ns, us, idx = [], [], [], []
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        x, z = math.sin(a), math.cos(a)
        for y in (y0, y1):
            vs.append((cx + r * x, y, cz + r * z)); ns.append((x, 0, z)); us.append((i / seg, y / UVM))
    for i in range(seg):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, d, c, a, c, b]
    m.add(mi, vs, ns, us, idx)
    for y, sgn, want in ((y1, 1, top), (y0, -1, bottom)):
        if not want:
            continue
        cm = top_mat if (sgn > 0 and top_mat is not None) else mi
        vs = [(cx, y, cz)] + [(cx + r * math.sin(2 * math.pi * i / seg), y, cz + r * math.cos(2 * math.pi * i / seg)) for i in range(seg)]
        us = [(0.5, 0.5)] + [(0.5 + 0.5 * math.sin(2 * math.pi * i / seg), 0.5 - 0.5 * math.cos(2 * math.pi * i / seg)) for i in range(seg)]
        idx = []
        for i in range(seg):
            j = 1 + i; k = 1 + (i + 1) % seg
            idx += [0, j, k] if sgn > 0 else [0, k, j]
        m.add(cm, vs, [(0, sgn, 0)] * len(vs), us, idx)


def annulus_y(m, mi, y, r0, r1, cx, cz, seg=48, up=True):
    vs, us, idx = [], [], []
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        for r in (r0, r1):
            vs.append((cx + r * math.sin(a), y, cz + r * math.cos(a))); us.append((i / seg, r / r1))
    for i in range(seg):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, b, c, a, c, d] if up else [a, c, b, a, d, c]
    m.add(mi, vs, [(0, 1 if up else -1, 0)] * len(vs), us, idx)


def plate_with_hole(m, mi, y, x0, x1, z0, z1, cx, cz, r, seg=48):
    """A horizontal rectangle with a round hole (top face, +Y): each hole vertex pairs with the rectangle boundary point
    on the same ray from the centre (the rectangle is star-shaped about the hole centre)."""
    vs, us, idx = [], [], []
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        dx, dz = math.sin(a), math.cos(a)
        ts = []
        if dx > 1e-9: ts.append((x1 - cx) / dx)
        if dx < -1e-9: ts.append((x0 - cx) / dx)
        if dz > 1e-9: ts.append((z1 - cz) / dz)
        if dz < -1e-9: ts.append((z0 - cz) / dz)
        t = min(ts)
        for p in ((cx + r * dx, cz + r * dz), (cx + t * dx, cz + t * dz)):
            vs.append((p[0], y, p[1])); us.append((p[0] / UVM, -p[1] / UVM))
    for i in range(seg):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, b, c, a, c, d]
    # the corners are cut by the straight segments between boundary samples; add the four corner triangles
    m.add(mi, vs, [(0, 1, 0)] * len(vs), us, idx)
    corners = [(x1, z1), (x1, z0), (x0, z0), (x0, z1)]
    for (px, pz) in corners:
        ang = math.atan2(px - cx, pz - cz) % (2 * math.pi)
        i = int(ang / (2 * math.pi) * seg)
        p0 = vs[2 * i + 1]; p1 = vs[2 * (i + 1) + 1]
        tri = [p0, (px, y, pz), p1]
        # wind CCW seen from +Y
        ax, az = tri[1][0] - tri[0][0], tri[1][2] - tri[0][2]
        bx, bz = tri[2][0] - tri[0][0], tri[2][2] - tri[0][2]
        if ax * bz - az * bx > 0:
            tri = [tri[0], tri[2], tri[1]]
        m.add(mi, tri, [(0, 1, 0)] * 3, [(t[0] / UVM, -t[2] / UVM) for t in tri], (0, 1, 2))


def tube_ring_y(m, mi, y, rc, rt, cx, cz, seg=48, seg_t=12):
    """A torus lying flat (axis Y): ring radius rc, tube radius rt."""
    vs, ns, us, idx = [], [], [], []
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        ca, sa = math.sin(a), math.cos(a)
        for j in range(seg_t + 1):
            b = 2 * math.pi * j / seg_t
            nx, ny = math.cos(b), math.sin(b)
            vs.append((cx + (rc + rt * nx) * ca, y + rt * ny, cz + (rc + rt * nx) * sa))
            ns.append((nx * ca, ny, nx * sa)); us.append((i / seg, j / seg_t))
    w = seg_t + 1
    for i in range(seg):
        for j in range(seg_t):
            a, b, c, d = i * w + j, (i + 1) * w + j, (i + 1) * w + j + 1, i * w + j + 1
            idx += [a, b, c, a, c, d]
    m.add(mi, vs, ns, us, idx)


def extrude_x(m, mi, poly, x0, x1, side_mat=None):
    """Extrudes a star-shaped (about its centroid) polygon in the YZ plane along X; poly = [(y, z)] counter-clockwise
    seen from +X."""
    cy = sum(p[0] for p in poly) / len(poly); cz = sum(p[1] for p in poly) / len(poly)
    n = len(poly)
    for x, sgn in ((x1, 1), (x0, -1)):
        vs = [(x, cy, cz)] + [(x, p[0], p[1]) for p in poly]
        us = [(cz / 0.08 + 0.5, cy / 0.08 + 0.5)] + [(p[1] / 0.08 + 0.5, p[0] / 0.08 + 0.5) for p in poly]
        idx = []
        for i in range(n):
            j = 1 + i; k = 1 + (i + 1) % n
            idx += [0, j, k] if sgn > 0 else [0, k, j]
        m.add(mi, vs, [(sgn, 0, 0)] * len(vs), us, idx)
    sm = mi if side_mat is None else side_mat
    vs, ns, us, idx = [], [], [], []
    for i in range(n + 1):
        p = poly[i % n]; q = poly[(i + 1) % n]; o = poly[(i - 1) % n]
        ty, tz = q[0] - o[0], q[1] - o[1]
        ln = math.hypot(ty, tz) or 1
        ny, nz = tz / ln, -ty / ln  # outward for CCW (y, z) seen from +X
        for x in (x0, x1):
            vs.append((x, p[0], p[1])); ns.append((0, ny, nz)); us.append((i / n, x))
    for i in range(n):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, d, c, a, c, b]
    m.add(sm, vs, ns, us, idx)


# ------------------------------------------------------------------ textures

def fbm(w, h, seed, octaves=5, sx=1.0, sy=1.0):
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp = 1.0
    for o in range(octaves):
        gw, gh = max(2, int(4 * sx * 2 ** o)), max(2, int(4 * sy * 2 ** o))
        g = rng.standard_normal((gh + 1, gw + 1))
        im = Image.fromarray(((g - g.min()) / (np.ptp(g) + 1e-9) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += amp * (np.asarray(im, float) / 255 - 0.5)
        amp *= 0.5
    return out


def wood_texture(c0, c1, seed, lacquer=False, size=1024):
    """Grain along image X: stretched noise + ring bands; lacquer = mostly flat colour with faint grain."""
    w = h = size
    yy, xx = np.mgrid[0:h, 0:w] / size
    warp = fbm(w, h, seed, 4, 1.0, 1.0) * 0.6
    grain = fbm(w, h, seed + 1, 6, 0.25, 4.0)
    rings = np.sin((yy * 22 + warp * 3 + grain * 1.5) * math.pi * 2)
    t = 0.5 + 0.35 * rings + 0.5 * grain
    if lacquer:
        t = 0.5 + 0.12 * rings + 0.15 * grain
    t = np.clip(t, 0, 1)
    a, b = np.array(c0, float), np.array(c1, float)
    img = a[None, None, :] * (1 - t[..., None]) + b[None, None, :] * t[..., None]
    fine = fbm(w, h, seed + 2, 2, 8, 8)[..., None] * (6 if lacquer else 14)
    img = np.clip(img + fine, 0, 255).astype(np.uint8)
    return Image.fromarray(img, "RGB")


def velvet_texture(size=512):
    n = fbm(size, size, 77, 5, 2, 2)
    base = np.array(VELVET, float)
    img = base[None, None, :] * (1 + 0.18 * n[..., None])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def test_f(size=512):
    im = Image.new("RGB", (size, size), (255, 255, 255))
    dr = ImageDraw.Draw(im)
    f = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", int(size * 0.55))
    dr.text((size * 0.12, size * 0.15), "F↑", fill=(0, 0, 0), font=f)
    return im


# ------------------------------------------------------------------ models

def build_box(finish):
    c0, c1, rough = FINISH[finish]
    m = Mesh()
    W, V, MI, BR, DK = 0, 1, 2, 3, 4
    # floor
    cuboid(m, W, BX0, BX1, BY0, FLOOR_TOP, BZ0, BZ1, "xXyzZ")
    # back wall, left, right walls (full height)
    cuboid(m, W, BX0, BX1, FLOOR_TOP, BY1, BZ0, BZ0 + WALL)
    cuboid(m, W, BX0, BX0 + WALL, FLOOR_TOP, BY1, BZ0 + WALL, BZ1 - WALL, "xXYyz")
    cuboid(m, W, BX1 - WALL, BX1, FLOOR_TOP, BY1, BZ0 + WALL, BZ1 - WALL, "xXYyz")
    # front wall: dancer segment full height, movement segment lowered to WIN_TOP, right end full height
    cuboid(m, W, BX0, DIV1, FLOOR_TOP, BY1, BZ1 - WALL, BZ1, "xXYyzZ")
    cuboid(m, W, DIV1, WIN_X1, FLOOR_TOP, WIN_TOP, BZ1 - WALL, BZ1, "YyzZ")
    cuboid(m, W, WIN_X1, BX1, FLOOR_TOP, BY1, BZ1 - WALL, BZ1, "xYyzZ")
    # divider (full height, full inner depth)
    cuboid(m, W, DIV0, DIV1, FLOOR_TOP, BY1, BZ0 + WALL, BZ1 - WALL, "xXY")
    # velvet floor liners
    cuboid(m, V, BX0 + WALL, DIV0, FLOOR_TOP, FLOOR_TOP + 0.002, BZ0 + WALL, BZ1 - WALL, "Y")
    cuboid(m, V, DIV1, BX1 - WALL, FLOOR_TOP, FLOOR_TOP + 0.002, BZ0 + WALL, BZ1 - WALL, "Y")
    # dancer-well deck (velvet, 5 mm under the box top) with the 7.4 cm hole; mirror ring around the hole
    deck_y = BY1 - 0.005
    plate_with_hole(m, V, deck_y, BX0 + WALL, DIV0, BZ0 + WALL, BZ1 - WALL, WELL_C[0], WELL_C[1], WELL_R)
    cuboid(m, V, BX0 + WALL, DIV0, deck_y - 0.004, deck_y, BZ0 + WALL, BZ1 - WALL, "y")
    annulus_y(m, MI, deck_y + 0.0006, WELL_R, WELL_R + 0.007, WELL_C[0], WELL_C[1])
    tube_ring_y(m, MI, deck_y + 0.0008, WELL_R + 0.0075, 0.0012, WELL_C[0], WELL_C[1])
    # the well's inner tube (velvet) so the dancer goes down into a round shaft
    vs, ns, us, idx = [], [], [], []
    seg = 48
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        x, z = math.sin(a), math.cos(a)
        for y in (FLOOR_TOP, deck_y):
            vs.append((WELL_C[0] + WELL_R * x, y, WELL_C[1] + WELL_R * z)); ns.append((-x, 0, -z)); us.append((i / seg, y / UVM))
    for i in range(seg):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, b, c, a, c, d]
    m.add(V, vs, ns, us, idx)
    # movement: brass bedplate, two drum standards (with axle bosses), comb pedestal (dark steel)
    cuboid(m, BR, -0.066, 0.156, FLOOR_TOP + 0.002, -0.176, -0.095, 0.095, "xXYzZ")
    for x0, x1 in ((-0.054, -0.049), (0.1555, 0.1575)):
        cuboid(m, BR, x0, x1, -0.176, DRUM_C[1] + 0.006, DRUM_C[2] - 0.016, DRUM_C[2] + 0.016)
        cylinder_x(m, BR, x0 - 0.001, x1 + 0.001, 0.008, DRUM_C[1], DRUM_C[2], seg=32)
    cuboid(m, DK, -0.045, 0.155, -0.176, -0.121, 0.058, 0.09, "xXYzZ")
    mats = [mat("wood_" + finish, c0, rough=rough, tex=0), mat("velvet", VELVET, rough=1.0, tex=1),
            mat("mirror", SILVER, rough=0.08, metal=1.0), mat("brass", BRASS, rough=0.35, metal=1.0), mat("darksteel", DARK, rough=0.5, metal=0.6)]
    m.write("box_" + finish, mats, [wood_texture(c0, c1, 11 + len(finish), lacquer=finish == "red"), velvet_texture()])


def build_lid(finish):
    c0, c1, rough = FINISH[finish]
    m = Mesh()
    L = BZ1 - BZ0
    # lid local: hinge line at y = 0, z = 0; closed lid spans z 0..L, y 0..LID_T
    cuboid(m, 0, BX0, BX1, 0, LID_T, 0, L, "xXYzZ")
    # inner face: velvet, inset 8 mm border of wood
    b = 0.008
    m.quad(1, (BX0 + b, -0.0004, 0 + b), (BX1 - b, -0.0004, 0 + b), (BX1 - b, -0.0004, L - b), (BX0 + b, -0.0004, L - b), (0, -1, 0),
           ((0, 0), (1, 0), (1, 1), (0, 1)) if TEST_F else ((BX0 / UVM, 0), (BX1 / UVM, 0), (BX1 / UVM, L / UVM), (BX0 / UVM, L / UVM)))
    cuboid(m, 0, BX0, BX1, 0, 0.0001, 0, L, "y")
    # brass hinge barrels at the back edge
    for x0 in (-0.17, 0.10):
        cylinder_x(m, 2, x0, x0 + 0.04, 0.0045, 0.002, -0.001, seg=24)
    mats = [mat("wood_" + finish, c0, rough=rough, tex=0), mat("velvet", VELVET, rough=1.0, tex=1), mat("brass", BRASS, rough=0.35, metal=1.0)]
    m.write("lid_" + finish, mats, [wood_texture(c0, c1, 31 + len(finish), lacquer=finish == "red"), test_f() if TEST_F else velvet_texture()])


def build_glass():
    m = Mesh()
    z = BZ1 - WALL + 0.006
    m.quad(0, (WIN_X0, WIN_TOP, z), (WIN_X1, WIN_TOP, z), (WIN_X1, BY1, z), (WIN_X0, BY1, z), (0, 0, 1))
    m.write("glass", [mat("glass", (0xDD, 0xE8, 0xF0), rough=0.05, alpha=0.10)])


def build_stage():
    m = Mesh()
    r = 0.040
    cylinder_y(m, 1, -0.006, 0.0, r, 0, 0, seg=48, top=True, top_mat=0)
    tube_ring_y(m, 1, -0.0005, r, 0.0015, 0, 0, seg=48, seg_t=10)
    cylinder_y(m, 1, -0.13, -0.006, 0.004, 0, 0, seg=24, top=False)
    m.write("stage", [mat("velvet", VELVET, rough=1.0, tex=0), mat("brass", BRASS, rough=0.35, metal=1.0)], [velvet_texture()])


def build_drum():
    m = Mesh()
    h = DRUM_L / 2
    cylinder_x(m, 0, -h, h, DRUM_R, 0, 0, seg=64, caps=False)
    for x0, x1 in ((-h - 0.003, -h), (h, h + 0.003)):
        cylinder_x(m, 0, x0, x1, DRUM_R + 0.0015, 0, 0, seg=64)
    cylinder_x(m, 1, -h - 0.012, h + 0.012, 0.004, 0, 0, seg=24)
    # a faint engraved seam line so the turning is visible on an empty drum
    m.write("drum", [mat("brass", BRASS, rough=0.32, metal=1.0), mat("steel", STEEL, rough=0.3, metal=1.0)])


def build_pin():
    m = Mesh()
    r, h, seg = 0.0009, 0.0026, 10
    cylinder_y(m, 0, -0.0005, h, r, 0, 0, seg=seg, top=False, bottom=False)
    # round cap (half sphere, 3 rings)
    vs, ns, us, idx = [], [], [], []
    rings = 3
    for j in range(rings + 1):
        phi = (math.pi / 2) * j / rings
        for i in range(seg + 1):
            a = 2 * math.pi * i / seg
            nx, ny, nz = math.cos(phi) * math.sin(a), math.sin(phi), math.cos(phi) * math.cos(a)
            vs.append((r * nx, h + r * ny, r * nz)); ns.append((nx, ny, nz)); us.append((i / seg, j / rings))
    w = seg + 1
    for j in range(rings):
        for i in range(seg):
            a, b, c, d = j * w + i, j * w + i + 1, (j + 1) * w + i + 1, (j + 1) * w + i
            idx += [a, b, c, a, c, d]
    m.add(0, vs, ns, us, idx)
    m.write("pin", [mat("steel", STEEL, rough=0.25, metal=1.0)])


def build_tine():
    m = Mesh()
    L, t = 0.0395, 0.002
    w0, w1 = 0.0095, 0.0080
    # tapered plate from root (z = 0) to the tip (z = -L), top at y = 0
    pts = [(-w0 / 2, 0), (w0 / 2, 0), (w1 / 2, -L), (-w1 / 2, -L)]
    top = [(p[0], 0.0, p[1]) for p in pts]
    bot = [(p[0], -t, p[1]) for p in pts]
    m.quad(0, top[3], top[2], top[1], top[0], (0, 1, 0), ((0, 1), (1, 1), (1, 0), (0, 0)))
    m.quad(0, bot[0], bot[1], bot[2], bot[3], (0, -1, 0))
    for i in range(4):
        j = (i + 1) % 4
        a, b, c, d = bot[i], bot[j], top[j], top[i]
        ux, uz = b[0] - a[0], b[2] - a[2]
        ln = math.hypot(ux, uz) or 1
        n = (uz / ln, 0, -ux / ln)
        m.quad(0, a, b, c, d, n)
    m.write("tine", [mat("steel", STEEL, rough=0.22, metal=1.0)])


# comb (v0.1.3, Layout.kt): 18 tines G4..C6, pitch 10.8 mm centred at x 0.052, free length 5.2 cm (G4) -> 3.2 cm (C6), tips on z = 0.0155
TINE_TIP_Z, TINE_PITCH, COMB_X = 0.0155, 0.0108, 0.052
def tine_x(r): return COMB_X + (r - 8.5) * TINE_PITCH
def tine_root_z(r): return TINE_TIP_Z + 0.052 + (0.032 - 0.052) * r / 17


def build_combbase():
    m = Mesh()
    x0, x1, y0, y1, zf = -0.045, 0.155, -0.121, -0.095, 0.09
    # back edge: the straight line through the tine roots, extended to the block ends
    slope = (tine_root_z(17) - tine_root_z(0)) / (tine_x(17) - tine_x(0))
    zb0 = tine_root_z(0) + slope * (x0 - tine_x(0))
    zb1 = tine_root_z(0) + slope * (x1 - tine_x(0))
    top = [(x0, y1, zb0), (x1, y1, zb1), (x1, y1, zf), (x0, y1, zf)]
    bot = [(x, y0, z) for x, _, z in top]
    m.quad(0, top[3], top[2], top[1], top[0], (0, 1, 0))          # top (+Y)
    m.quad(0, bot[3], bot[0], bot[1], bot[2], (0, -1, 0))         # bottom
    m.quad(0, bot[3], bot[2], top[2], top[3], (0, 0, 1))          # front (+Z)
    m.quad(0, bot[0], bot[3], top[3], top[0], (-1, 0, 0))         # left
    m.quad(0, bot[2], bot[1], top[1], top[2], (1, 0, 0))          # right
    dx, dz = x1 - x0, zb1 - zb0
    ln = math.hypot(dx, dz)
    m.quad(0, bot[1], bot[0], top[0], top[1], (dz / ln, 0, -dx / ln))  # slanted back edge (faces the drum)
    # four screw heads on top
    for x in (-0.035, 0.025, 0.085, 0.145):
        cylinder_y(m, 1, -0.095, -0.0935, 0.003, x, 0.08, seg=24)
    m.write("combbase", [mat("steel", (0x9A, 0xA0, 0xA8), rough=0.35, metal=1.0), mat("brass", BRASS, rough=0.35, metal=1.0)])


def build_key():
    m = Mesh()
    cylinder_x(m, 0, -0.004, 0.022, 0.0035, 0, 0, seg=32)
    cylinder_x(m, 0, 0.004, 0.009, 0.007, 0, 0, seg=32)     # collar
    cylinder_x(m, 0, 0.019, 0.025, 0.0075, 0, 0, seg=32)    # boss
    # butterfly wings: two lobes (up / down) in the YZ plane, 6 cm tall x 5 cm wide, 5 mm thick
    poly = []
    n = 64
    for i in range(n):
        a = 2 * math.pi * i / n
        # bow-tie wings along +-Y (6 cm tip to tip), about 5 cm across Z
        rr = 0.016 + 0.014 * abs(math.cos(a)) ** 1.2
        poly.append((rr * math.cos(a), 1.3 * rr * math.sin(a)))
    # CCW seen from +X in (y, z): angle goes y -> z
    extrude_x(m, 0, poly, 0.020, 0.025)
    m.write("key", [mat("brass", BRASS, rough=0.3, metal=1.0)])


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in FINISH:
        build_box(f)
        build_lid(f)
    build_glass()
    build_stage()
    build_drum()
    build_pin()
    build_tine()
    build_combbase()
    build_key()


if __name__ == "__main__":
    main()
