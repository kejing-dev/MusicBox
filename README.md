# 八音盒 Music Box

A wooden music box on your desk, for PICO OS 6 Shared Space (Spatial SDK BOM 6.1.9). Wind it and it plays. Tap a tine
and a pin lands on the drum, so you can pin your own tune and hear the box sing it back.

- **Box volume** (default container, 0.64 x 0.512 x 0.474 m): walnut / maple / red-lacquer box, lid with the song card,
  a porcelain dancer on a lift, a brass drum (64 columns x 8 tines, up to 256 pins), the steel comb, the winding key,
  the brass nameplate and an 8-cell spring gauge.
- **Control window** (Planar 560 x 600 dp, beside the box): native TabBar with Play / Tunes / Settings.
- Six public-domain tunes (Twinkle, Ode to Joy, Mary Had a Little Lamb, Jingle Bells, Lightly Row, Canon) and up to six
  of your own. Fully offline, Chinese and English.

Sounds are real recordings (CC0): the tines are single notes of a real music box (freesound pack 4540 by folkman),
re-pitched to C major; the ratchet is "Winding Up a Music Box" (freesound 760760, wolfmagpie); the lid taps are Kenney
CC0. The dancer is a Tripo model generated with the owner's account. Fonts: Noto Sans (OFL).

## Build
```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.13+9/Contents/Home
./gradlew assembleDebug :app:testDebugUnitTest
```
Assets are generated: `tools/build_musicbox.py` (GLBs), `tools/calibrate_dancer.py` (dancer), `tools/make_sounds.py`
(sounds), `tools/songs.py` (tunes), `tools/build_icon.py` (icon). See AGENTS.md for the debug broadcast.
