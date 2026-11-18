"""Dancer GLB calibration (profile D25): Tripo candidate B (dancer-B-porcelain-gold.glb, 1.89 M triangles) ->
app/src/main/assets/models/dancer.glb.

  ~/.factory-venv/bin/python tools/calibrate_dancer.py <tripo.glb> <work dir>

1. cut Tripo's gilded pedestal (faces whose centre is below y = 0.128 in Tripo units; the stage has its own velvet disc);
2. decimate to 30 000 triangles, base colour 1024 px (factory bin/glb_decimate.py);
3. calibrate: the supporting toe (mean of the lowest 1 cm of vertices) to the origin, toe on y = 0, face toward +Z
   (Tripo already faces +Z, checked on a front render), scale to HEIGHT; material OPAQUE, glazed (roughness 0.35).
Prints the numbers; domain/Layout.kt DANCER_* and the CalibrationTest read the same GLB.
"""
import os, subprocess, sys
import numpy as np
import trimesh

HEIGHT = 0.095
FACTORY = "/Users/bytedance/Desktop/Spatial Apps/spatial-app-factory"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "models", "dancer.glb")

src, work = sys.argv[1], sys.argv[2]
os.makedirs(work, exist_ok=True)
cut, dec = os.path.join(work, "dancer-nobase.glb"), os.path.join(work, "dancer-30k.glb")
m = trimesh.load(src, force="mesh", process=False)
m.update_faces(m.triangles_center[:, 1] > 0.128); m.remove_unreferenced_vertices(); m.export(cut)
subprocess.run([sys.executable, os.path.join(FACTORY, "bin", "glb_decimate.py"), cut, dec, "--faces", "30000", "--tex", "1024"], check=True)
m = trimesh.load(dec, force="mesh", process=False)
v = m.vertices.copy()
toe = v[v[:, 1] < v[:, 1].min() + 0.01].mean(axis=0)
v[:, 0] -= toe[0]; v[:, 2] -= toe[2]; v[:, 1] -= v[:, 1].min()
s = HEIGHT / v[:, 1].max()
v *= s
m.vertices = v
mat = m.visual.material
mat.alphaMode = "OPAQUE"; mat.metallicFactor = 0.0; mat.roughnessFactor = 0.35; mat.doubleSided = False
m.export(OUT)
r = np.hypot(v[:, 0], v[:, 2])
print("dancer.glb: %d triangles, scale %.5f, height %.4f m, max radius about the toe %.4f m, bbox %s..%s, %d KB"
      % (len(m.faces), s, v[:, 1].max(), r.max(), np.round(v.min(0), 4), np.round(v.max(0), 4), os.path.getsize(OUT) // 1024))
