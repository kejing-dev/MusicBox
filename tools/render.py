"""Offline renders of the music box (design sound.qaListen, B20 / D51; ui_gates loudness). Rendered, not recorded:
kj-mbp-m5 has no audio loopback from the emulator. The renderer follows the app: the same WAVs, gain = the volume stop
(default 100 % = 1.0), one SoundPool voice per tine (the same tine played again fades its old voice in 10 ms), no
limiter, tempo = bpm x speed, a 6-click wind at the start, fast-forward over the empty columns (silent), the run-down
(last rd steps from 100 % to 40 %) and the last "tick".

  ~/.factory-venv/bin/python tools/render.py OUTDIR [version]

Writes OUTDIR/loudness-<version>.wav (30 s: wind + Twinkle + Ode to Joy, voiced RMS / peak printed), sample-10s.wav,
tines/tine_<n>.wav + tines/spectra.png, songs/<id>.wav (one full play from column 0 with spring = min(L, 64)) +
songs/<id>-last15.wav, rundown-5s.wav, burst10.wav (10 plucks of one tine 80 ms apart: peaks within 1 dB, D51).
"""
import math
import os
import sys
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SND = os.path.join(ROOT, "app", "src", "main", "assets", "sounds")
SONGS = os.path.join(ROOT, "app", "src", "main", "assets", "songs.txt")
SR = 44100
GAIN = 1.0


def load(name):
    with wave.open(os.path.join(SND, name + ".wav")) as w:
        return np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(float) / 32768


TINES = [load("tine_%d" % i) for i in range(1, 9)]
CLICKS = [load(n) for n in ("click", "click_2", "click_3")]
STOP = load("stop")


def songs():
    out = {}
    for line in open(SONGS, encoding="utf-8"):
        f = line.strip().split("|")
        if len(f) < 9:
            continue
        pins = [(int(c), int(t) - 1) for c, t in (p.split(".") for p in f[8].split(";"))]
        last = max(c for c, _ in pins)
        L = min(64, ((last + 1 + 3) // 4) * 4)
        out[f[0]] = {"bpm": int(f[7]), "pins": pins, "L": L, "name": f[2]}
    return out


class Mix:
    def __init__(self, seconds):
        self.buf = np.zeros(int(seconds * SR) + SR * 4)
        self.voice = [None] * 8  # (start index, length) of the sounding voice per tine

    def add(self, x, t, g=1.0):
        i = int(t * SR)
        n = min(len(x), len(self.buf) - i)
        if n > 0:
            self.buf[i:i + n] += x[:n] * g

    def tine(self, r, t):
        i = int(t * SR)
        v = self.voice[r]
        if v is not None:
            s0, ln = v
            fade = int(0.010 * SR)
            if s0 + ln > i:  # fade the old voice: 0.15 at once, silent after 10 ms (SoundPlayer)
                seg = self.buf[i:i + min(ln - (i - s0), len(self.buf) - i)]
                old = TINES[r][i - s0:i - s0 + len(seg)] * GAIN
                k = np.ones(len(seg)) * 0.0
                k[:min(fade, len(seg))] = 0.15
                self.buf[i:i + len(seg)] -= old * (1 - k)
        self.add(TINES[r], t, GAIN)
        self.voice[r] = (i, len(TINES[r]))


def play(mix, song, t0, speed=1.0, spring=None, start_col=0):
    """One wound play from start_col (the app's run(): plucks on arrival, fast-forward, run-down). Returns end time."""
    bpm = song["bpm"]; L = song["L"]
    cols = {}
    for c, r in song["pins"]:
        cols.setdefault(c, []).append(r)
    s = float(min(L, 64)) if spring is None else spring
    s0 = s
    rd = min(8.0, max(2.0, math.ceil(0.15 * s0)))
    base = bpm / 60.0 * speed
    t = t0; col = start_col
    for r in sorted(cols.get(col % 64, [])):
        mix.tine(r, t)
    while s > 1e-9:
        rate = base * (0.4 + 0.6 * s / rd) if s <= rd else base
        # integrate one step through the run-down in small pieces
        d = 0.0
        while d < 1.0 - 1e-9 and s > 1e-9:
            h = min(0.05, 1.0 - d, s)
            rate = base * (0.4 + 0.6 * s / rd) if s <= rd else base
            t += h / rate; d += h; s -= h
        if d < 1.0 - 1e-9:
            t += (1.0 - d) / (base * 0.4)
        col += 1
        c = col % 64
        for r in sorted(cols.get(c, [])):
            mix.tine(r, t)
        if s > 1e-9 and L < 64 and c == L:
            t += 0.3; col = 64  # fast-forward, silent, no spring
            for r in sorted(cols.get(0, [])):
                mix.tine(r, t)
    mix.add(STOP, t, GAIN)
    return t


def write(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    y = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(y.tobytes())


def levels(x):
    """The ui_gates measure: 20 ms windows, voiced = above peak - 30 dB."""
    peak = np.abs(x).max() or 1e-9
    win = int(0.02 * SR)
    n = len(x) // win
    e = (x[:n * win].reshape(n, win) ** 2).mean(axis=1)
    v = e[e >= (peak * 0.0316) ** 2]
    return 20 * math.log10(math.sqrt(v.mean())), 20 * math.log10(peak)


def trim(x):
    nz = np.where(np.abs(x) > 1e-4)[0]
    return x[: nz[-1] + int(0.2 * SR)] if len(nz) else x


def main(out, ver):
    S = songs()
    # loudness: 6 clicks (a tap on the key) then Twinkle, then Ode, cut to 30 s
    m = Mix(40)
    for i in range(6):
        m.add(CLICKS[i % 3], 0.05 * i, 0.8 * GAIN)
    end = play(m, S["twinkle"], 0.6)
    play(m, S["ode"], end + 0.5)
    x = m.buf[: 30 * SR]
    write(os.path.join(out, "loudness-%s.wav" % ver), x)
    rms, peak = levels(x)
    print("loudness-%s.wav  voiced RMS %.1f dBFS  peak %.1f dBFS  (gate: RMS >= -20, peak <= -1)" % (ver, rms, peak))
    write(os.path.join(out, "sample-10s.wav"), m.buf[: 10 * SR])
    # tines solo + spectra
    try:
        from PIL import Image, ImageDraw
        from scipy.signal import spectrogram
        rows = []
        for i, x in enumerate(TINES):
            write(os.path.join(out, "tines", "tine_%d.wav" % (i + 1)), x * GAIN)
            f, tt, Sx = spectrogram(x, SR, nperseg=2048, noverlap=1536)
            Sx = 10 * np.log10(Sx + 1e-12)[f < 8000]
            Sx = np.clip((Sx + 100) / 80, 0, 1)
            im = Image.fromarray((Sx[::-1] * 255).astype(np.uint8)).resize((800, 140))
            canvas = Image.new("L", (800, 160), 0); canvas.paste(im, (0, 20))
            ImageDraw.Draw(canvas).text((6, 3), "tine %d (C5..C6 #%d), 0-8 kHz, 0-%.1f s" % (i + 1, i + 1, len(x) / SR), fill=255)
            rows.append(canvas)
        sheet = Image.new("L", (800, 160 * len(rows)))
        for i, r in enumerate(rows):
            sheet.paste(r, (0, 160 * i))
        sheet.save(os.path.join(out, "tines", "spectra.png"))
    except ImportError:
        pass
    # each tune, one full play + its last 15 s
    for sid, s in S.items():
        mm = Mix(80)
        e = play(mm, s, 0.0)
        full = trim(mm.buf[: int((e + 3) * SR)])
        write(os.path.join(out, "songs", sid + ".wav"), full)
        write(os.path.join(out, "songs", sid + "-last15.wav"), full[-15 * SR:])
        r, p = levels(full)
        print("songs/%-11s %5.1f s  voiced RMS %.1f  peak %.1f dBFS" % (sid + ".wav", len(full) / SR, r, p))
    # run-down: 6 steps of spring from column 40 of Jingle Bells
    mm = Mix(10)
    play(mm, S["jingle"], 0.0, spring=6.0, start_col=40)
    write(os.path.join(out, "rundown-5s.wav"), mm.buf[: 5 * SR])
    # D51 burst: one tine 10 times 80 ms apart, every pluck's peak
    mm = Mix(4)
    for k in range(10):
        mm.tine(4, 0.08 * k)
    b = mm.buf[: 2 * SR]
    peaks = [20 * math.log10(np.abs(b[int(0.08 * k * SR): int(0.08 * k * SR) + int(0.03 * SR)]).max()) for k in range(10)]
    write(os.path.join(out, "burst10.wav"), b)
    print("burst10: peaks %.1f .. %.1f dBFS (spread %.2f dB)" % (min(peaks), max(peaks), max(peaks) - min(peaks)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "v0.1.0")
