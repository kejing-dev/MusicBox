"""Pops / cut-notes scan of offline renders: compare each tune against the v0.1.1 baseline renders.
click = max |2nd difference| over the 20 ms local RMS, outside the first 3 ms after a pluck onset (the pluck attack is
the real recording); cut = an envelope drop > 18 dB within 6 ms that stays >= 15 dB down for the next 40 ms with no new onset (a note
that stops dead; two tines beating dip for a few ms and come straight back, which is not a cut)."""
import sys, wave, glob, os, json
import numpy as np
SR = 44100

def load(p):
    with wave.open(p) as w:
        x = np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(float) / 32768
        if w.getnchannels() == 2: x = x.reshape(-1, 2).mean(1)
    return x

def scan(p):
    x = load(p)
    hop = int(0.001 * SR)
    n = len(x) // hop
    env = np.sqrt(np.convolve(x[:n*hop] ** 2, np.ones(hop) / hop, "same")[::hop] + 1e-12)
    envdb = 20 * np.log10(env + 1e-9)
    # onsets: 1 ms envelope rise > 6 dB
    rise = np.diff(envdb, prepend=envdb[0])
    onset = np.where(rise > 6)[0]
    near_onset = np.zeros(n, bool)
    for o in onset: near_onset[max(0, o - 1):o + 4] = True
    d2 = np.abs(np.diff(x, 2))
    d2m = np.array([d2[i*hop:(i+1)*hop].max() if (i+1)*hop <= len(d2) else 0 for i in range(n)])
    loc = np.sqrt(np.convolve(env ** 2, np.ones(20) / 20, "same"))
    ratio = np.where(near_onset, 0, d2m / (loc + 1e-4))
    click = float(ratio.max())
    cuts = 0
    for i in range(n - 48):
        if envdb[i] > -45 and envdb[i] - envdb[i + 6] > 18 and not near_onset[i:i + 48].any() and envdb[i + 6:i + 46].max() < envdb[i] - 15:
            cuts += 1
    clip = int((np.abs(x) >= 0.999).sum())
    return {"click": round(click, 3), "cuts": cuts, "clipped": clip, "peak_db": round(20 * np.log10(np.abs(x).max() + 1e-9), 1)}

base_dir, new_dir = sys.argv[1], sys.argv[2]
out = {"baseline_v0.1.1": {}, "v0.1.2": {}}
for p in sorted(glob.glob(os.path.join(base_dir, "*.wav"))):
    if "last15" in p: continue
    out["baseline_v0.1.1"][os.path.basename(p)[:-4]] = scan(p)
for p in sorted(glob.glob(os.path.join(new_dir, "*.wav"))):
    if "last15" in p: continue
    out["v0.1.2"][os.path.basename(p)[:-4]] = scan(p)
bmax = max(v["click"] for v in out["baseline_v0.1.1"].values())
out["limit_click"] = round(bmax * 1.10, 3)
out["pass"] = all(v["click"] <= out["limit_click"] and v["cuts"] == 0 and v["clipped"] == 0 for v in out["v0.1.2"].values())
print(json.dumps(out, indent=1))
