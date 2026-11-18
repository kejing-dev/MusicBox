# 八音盒 Music Box: project guidance

Package `io.embodit.musicbox`, v0.1.0, PICO OS 6 Shared Space, Spatial SDK BOM 6.1.9. Design contract: spatial-app-factory
`apps/music-box/design-package.json` (gate B passed 2026-11-05, default options: scale k = 1.28, 8 tines, no import,
dancer B, lid closed at start). Skeleton from the factory's klotski-box project (CloudAir lineage: two containers, theme,
board-input template, debug receiver); everything music-box is new.

## Surfaces
- **Box volume** (default container `MusicBoxVolume`, Volumetric 0.64 x 0.512 x 0.474 m = design 0.50 x 0.40 x 0.37 x 1.28,
  fixed scale, gravity aligned, uniform resize). The content root scales with the measured volume (`BoxScene.setScale`),
  so a resized volume keeps the box filling it; drum drags convert hand travel by the same scale.
- **Control window** (`MusicBoxControl`, Planar 560 x 600 dp, `Placement.right(box)`): native SpatialUI `TabBar`, three pages
  播放 / 曲子 / 设置; confirmations (clear / delete / quit) and 怎么操作 unfold in place. The app never reads the head pose.
  System [×] on the panel = P5: tap the brass nameplate to reopen it (same id + tag, beside the box).

## Code
| Path | What |
|---|---|
| `domain/MusicBox.kt` | the whole machine: drum position (columns), pins, spring (steps), lid / dancer, edit row, undo (50), live pins, scrub, fast-forward of empty columns, run-down, presets fork, my tunes. Pure Kotlin; time only moves through `advance(dt)` |
| `domain/Song.kt` | Song / Pin, `songs.txt` codec, auto names |
| `domain/Lines.kt` | the one card / status table (D38) |
| `domain/Layout.kt` | every metre (k = 1), eyes, occlusion solids (unit-tested D35) |
| `platform/Platform.kt` | `Graph`, `BoxCtl` (owns the box + a 4 ms simulation thread on wall clock, D55; plays sounds the moment an action queues them), `SoundPlayer` (SoundPool) |
| `ui/board/*` | `BoxScene` (entities, per-frame drawing via `BoxFrameSystem`), `BoxScreen` (loading, scale, board-input wiring), textures |
| `ui/control/*` | the window pages; every Text logs `OVERFLOW <tag>` when cut |
| `tools/` | asset builders (see README), `mb.sh` harness, `loudness.py`, `contrast.py`, `blank.py` |

Holders (board-input): `lid` (closed: whole lid; open: top 4 cm), `tine:0..7`, `drum` (capsule, drag only), `key`
(tap = +25 %, drag down = wind), `nameplate` (reopens the panel). Box body, dancer, notes, card, gauge take no hits.

## Build / test (kj-mbp-m5)
```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.13+9/Contents/Home
./gradlew assembleDebug :app:testDebugUnitTest
DEV=emulator-5562 tools/mb.sh install
```
Offline release build: add `-x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease` if the lint jars are not cached.
Emulators are shared: borrow with `python3 -I bin/emu_borrow.py borrow music-box` in the factory, return after.

## Debug broadcast (QA screenshots)
`adb shell am broadcast -a io.embodit.musicbox.DEBUG -p io.embodit.musicbox --es cmd <cmd> --es a "<arg>"` (or
`tools/mb.sh cmd <cmd> [arg]`). Log tag `MusicBox`. Every `goto` starts with `reset` (twinkle, lid closed, spring 0,
column 0, Play page, unfrozen), so states never leak into each other. Frozen states stay frozen until the next `goto`
or `hold off`.

| State | Command | Notes |
|---|---|---|
| B0 准备中 | `b0hold on`, force-stop + launch, capture within 10 s, `b0hold off` | walnut slab stand-in |
| B1 合着 | `goto B1` | `coach on` = two lid pulses; `nearhint on/off` |
| B2 开着 | `goto B2` · `finish walnut|maple|red` | |
| B3 上弦中 | `goto B3` | key dragged 3 cm, frozen |
| B4 播放 | `goto B4` | frozen 40 ms after the chord at column 8: tines bent, notes in the air |
| B4r 慢下来 | `goto B4r` | spring 6 steps, frozen |
| B5 拨滚筒 | `goto B5` | drum dragged 3 cm, frozen |
| B6 钉谱 | `goto B6` (my tune) · `goto B6fork` (preset -> 另存) · `goto B6full` (6 tunes -> 满了) | |
| B7 合着有弦 | `goto B7` | 5 cells lit |
| B8 换曲 / B9 倒回 | `goto B8` · `goto B9` | frozen mid-animation |
| P2 曲子 | `goto P2` · `goto P2mine` (0 tunes) · `goto P2full` (6 tunes) | |
| P3 设置 / 怎么操作 | `goto P3` · `goto HOW` | |
| P4 确认 | `goto P4clear` · `goto P4delete` · `goto P4quit` · `yes` / `no` | |
| P5 面板关了 | `goto P5` (lid closed, nameplate breathes) · `goto P5open` · `tap nameplate` reopens | |
Other: `tap lid|key|nameplate|drum|tine N`, `drag key|drum <cm>`, `spring 0..1`, `song <id>`, `speed`, `col N`,
`pins "c.t;…"`, `windplay`, `stop`, `back`, `rest`, `undo`, `lang zh|en|system`, `page`, `mute`, `volume 0..2`,
`orient on|off` (F↑ test textures, D10), `replay "<holder>"`, `dump`.

## Known
- 3D taps / drags are not reachable by `pico-cli interact` on the Mac emulator: driven by the debug broadcast through the
  same `routeTap` / box calls; the three inputs (controller ray, hand ray, eye-hand) need the headset.
- Sounds are SoundPool (not spatial), real CC0 recordings; tines carry no random pitch (tuning).
