"""All Music Box sounds from real CC0 recordings (profile D51 / B20, ui_gates sound-source). Nothing is synthesised.

  ~/.factory-venv/bin/python tools/make_sounds.py [~/Downloads/cc0-sounds]

Tines (C major, one octave, C5..C6, equal temperament A4 = 440): single notes of a real music box recorded one by one
by folkman (freesound pack 4540, CC0: D5 E5 F#5 G5 A5 B5 C#6 ...). Each tine takes the nearest recorded note and is
re-pitched by resampling (at most 2 semitones, the usual sampler practice) to the exact target frequency, so the measured
fundamental is within 3 cents (printed). The recordings ring at 590-1100 Hz; the design's C4 octave would need 14
semitones of shift (a slowed-tape sound), so the comb sits one octave up, where real cylinder music boxes play
(deviation recorded in state.json).

Mechanical (event sounds only, no loops, B24):
  click[_2,_3]  ratchet clicks cut from "Winding Up a Music Box" (wolfmagpie, freesound 760760, CC0)
  lid_open      light wood tap (Kenney impact sounds, CC0)          lid_close  medium wood knock (Kenney, CC0)
  stop          the last "tick" when the spring runs out (Kenney light wood, CC0)
  pin           a very light metal tick when a pin pops up / goes down (a ratchet click at -10 dB)

Chain: decode (ffmpeg, 44.1 kHz mono) -> cut -> high-pass 100 Hz -> re-pitch -> fades -> level. No reverb, no
thickening (D51). Tines get a restoration step first (the source was wound by hand while recording, so its decays carry
mechanism clicks): after the first 25 ms only narrow bands (+-12 Hz) around the note's own partials are kept (the
partials are read off the recording's spectrum), which removes the broadband clicks and room noise and leaves the
recorded tone (the 8 strongest partials above -38 dB); a 5 ms gain rider then pulls any window that rises above the\nrecent decay back down (a plucked tine only decays). The pluck attack itself (first 15-35 ms) stays raw, held to at most\n+6 dB over the ring. Writes app/src/main/assets/sounds/*.wav (16-bit PCM) and prints f0 / cents / peak / RMS.
"""
import math
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy.signal import butter, resample_poly, sosfiltfilt

SRC = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/Downloads/cc0-sounds")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "sounds")
SR = 44100
FM = "freesound-music-box/folkman_%d.mp3"
# recorded note -> freesound id (pack 4540)
REC = {"D5": 70181, "E5": 70184, "F#5": 70173, "G5": 70174, "A5": 70175, "B5": 70177, "C#6": 70179, "D6": 70182}
NOTE = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
# tine -> (target note, recorded source)
TINES = [("C5", "D5"), ("D5", "D5"), ("E5", "E5"), ("F5", "E5"), ("G5", "G5"), ("A5", "A5"), ("B5", "B5"), ("C6", "C#6")]
TINE_LEN = 2.6
TINE_RMS = -16.0  # dBFS over 30-400 ms (the ring)
TINE_PEAK = -3.0


def freq(note):
    n, o = note[:-1], int(note[-1])
    midi = 12 * (o + 1) + NOTE[n]
    return 440.0 * 2 ** ((midi - 69) / 12)


def decode(rel):
    with tempfile.TemporaryDirectory() as d:
        out = os.path.join(d, "x.wav")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", os.path.join(SRC, rel), "-ac", "1", "-ar", str(SR), out], check=True)
        with wave.open(out) as w:
            return np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(float) / 32768


def hp(x, f=100):
    return sosfiltfilt(butter(2, f, "highpass", fs=SR, output="sos"), x)


def f0(x):
    """Fundamental of a plucked note: FFT peak 30-530 ms after the onset (parabolic interpolation), sub-harmonic check."""
    on = int(np.argmax(np.abs(x) > 0.3 * np.abs(x).max()))
    seg = x[on + int(0.03 * SR): on + int(0.53 * SR)]
    n = 1 << 19
    s = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), n))
    f = np.fft.rfftfreq(n, 1 / SR)
    s[f < 200] = 0
    k = int(np.argmax(s))
    for d in (2, 3):
        kk = int(k / d); lo, hi = max(1, kk - 40), kk + 40
        j = lo + int(np.argmax(s[lo:hi]))
        if s[j] > 0.2 * s[k] and f[j] > 200:
            k = j
    a, b, c = np.log(s[k - 1:k + 2] + 1e-12)
    p = 0.5 * (a - c) / (a - 2 * b + c)
    return (k + p) * SR / n


def repitch(x, ratio):
    """Plays x faster by ratio (> 1 = higher), 8x oversampled linear interpolation."""
    up = resample_poly(x, 8, 1)
    pos = np.arange(0, len(up) - 1, 8 * ratio)
    return np.interp(pos, np.arange(len(up)), up)


def fades(x, fin=0.002, fout=0.4):
    x = x.copy()
    a = int(fin * SR); b = int(fout * SR)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def db(v):
    return 20 * math.log10(max(v, 1e-9))


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    y = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(y.tobytes())
    voiced = x[np.abs(x) > 10 ** (-40 / 20)]
    print("%-12s %5.2f s  peak %6.1f dBFS  rms(voiced) %6.1f dBFS" % (name + ".wav", len(x) / SR, db(np.abs(x).max()), db(np.sqrt(np.mean(voiced ** 2)) if len(voiced) else 0)))


def declick(x):
    """Keeps the recorded partials (narrow bands) after the attack; the attack stays raw (see module doc)."""
    a0, a1 = int(0.025 * SR), int(0.6 * SR)
    n = 1 << int(math.ceil(math.log2(len(x) * 2)))
    spec = np.abs(np.fft.rfft(x[a0:a1] * np.hanning(a1 - a0), n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[(f < 150) | (f > 12000)] = 0
    peaks = []
    floor = spec.max() * 10 ** (-38 / 20)
    s2 = spec.copy()
    for _ in range(8):
        k = int(np.argmax(s2))
        if s2[k] < floor:
            break
        peaks.append(f[k])
        s2[max(0, k - int(40 * n / SR)):k + int(40 * n / SR)] = 0
    X = np.fft.rfft(x, n)
    fx = np.fft.rfftfreq(n, 1 / SR)
    mask = np.zeros_like(fx)
    for pf in peaks:
        mask = np.maximum(mask, np.exp(-0.5 * ((fx - pf) / 12.0) ** 2))
    tail = np.fft.irfft(X * mask, n)[: len(x)]
    i0, i1 = int(0.015 * SR), int(0.035 * SR)
    ring_peak = np.abs(tail[i1:int(0.2 * SR)]).max()
    head = x.copy()
    hp_ = np.abs(head[:i1]).max()
    if hp_ > 2.0 * ring_peak:
        head[:i1] *= 2.0 * ring_peak / hp_
    w = np.zeros(len(x)); w[:i0] = 1; w[i0:i1] = np.linspace(1, 0, i1 - i0)
    y = head * w + tail * (1 - w)
    # click riding: a plucked tine only decays, so any 5 ms window louder than the recent past is a leftover click
    hop = int(0.005 * SR)
    nwin = len(y) // hop
    env = np.array([np.sqrt(np.mean(y[i * hop:(i + 1) * hop] ** 2)) + 1e-9 for i in range(nwin)])
    g = np.ones(nwin)
    for i in range(int(0.045 / 0.005), nwin):
        past = np.median(env[max(0, i - 8):i])
        g[i] = min(1.0, 1.05 * past / env[i])
        env[i] = min(env[i], 1.05 * past)
    gain = np.interp(np.arange(len(y)), np.arange(nwin) * hop + hop / 2, g)
    return y * gain, peaks


def tines():
    print("tine  target  source  f0(src)  ratio    f0(out)  cents")
    for i, (target, src) in enumerate(TINES, 1):
        x = hp(decode(FM % REC[src]))
        on = max(0, int(np.argmax(np.abs(x) > 0.25 * np.abs(x).max())) - int(0.004 * SR))
        x = x[on:]
        x, peaks = declick(x)
        fs = f0(x)
        ratio = freq(target) / fs
        y = repitch(x, ratio)[: int(TINE_LEN * SR)]
        y = fades(y, 0.001, 0.5)
        head = y[int(0.03 * SR): int(0.4 * SR)]
        g = 10 ** (TINE_RMS / 20) / np.sqrt(np.mean(head ** 2))
        g = min(g, 10 ** (TINE_PEAK / 20) / np.abs(y).max())
        y *= g
        fo = f0(y)
        cents = 1200 * math.log2(fo / freq(target))
        print("%d     %-6s  %-6s  %7.1f  %.4f  %7.1f  %+5.1f" % (i, target, src, fs, ratio, fo, cents))
        write("tine_%d" % i, y)


def cut(x, t0, t1, fin=0.002, fout=0.02):
    return fades(x[int(t0 * SR):int(t1 * SR)], fin, fout)


def norm(x, peak_db):
    return x * (10 ** (peak_db / 20) / np.abs(x).max())


def mechanical():
    wind = hp(decode("freesound-music-box/wolfmagpie_760760_winding.mp3"), 300)
    for n, t in (("click", 3.390), ("click_2", 4.994), ("click_3", 5.052)):
        write(n, norm(cut(wind, t - 0.004, t + 0.040, 0.001, 0.015), -6.0))
    write("pin", norm(cut(wind, 4.994 - 0.003, 4.994 + 0.025, 0.001, 0.012), -16.0))
    k = "kenney_impact-sounds/Audio/"
    write("lid_open", norm(cut(hp(decode(k + "impactWood_light_001.ogg")), 0, 0.30), -6.0))
    write("lid_close", norm(cut(hp(decode(k + "impactWood_medium_001.ogg")), 0, 0.35), -5.0))
    write("stop", norm(cut(hp(decode(k + "impactWood_light_003.ogg")), 0, 0.25), -8.0))


if __name__ == "__main__":
    tines()
    mechanical()
