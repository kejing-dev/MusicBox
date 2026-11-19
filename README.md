# 八音盒 Music Box

![八音盒 Music Box](docs/screenshots/poster-zh.jpg)

[中文](#中文) · [English](#english)

## 中文

桌上一只会唱歌的木盒。拧发条它就唱，舞者跟着滚筒转；点一根音梳齿，就往滚筒上钉一颗钉——钉一首你自己的曲子，再拧发条听它唱回来。

- **开盖**：盒盖内的曲名卡告诉你现在唱哪首，舞者从井口升起
- **拧发条**：钥匙下 8 格余量，唱着唱着减少，最后渐慢停下；也可以按 [上弦并播放]
- **拨滚筒**：用手前后转，一格一格出声
- **音梳**：18 根齿，G4–C6 半音齐全（v0.1.3 加宽），低音齿更长
- **钉谱**：钉谱屏里有一排 18 键键盘（黑键是升号），点键或点齿就在当前列钉一颗，播放中也能边听边钉；退一拍、空一拍、撤销 50 步
- **曲子**：26 首公有领域曲（含生日快乐、致爱丽丝、绿袖子、友谊地久天长、新年好、送别、平安夜、斯卡布罗集市、土耳其进行曲），分 童谣 / 民歌 / 古典 / 节日 四组，每行可 [试听]；另有最多 6 首自己的，本机保存
- **木色**：胡桃木 / 枫木 / 红漆；快慢 50%–150%；静音常显
- **声音**：真实八音盒单音的 CC0 录音，逐齿调到十二平均律；节拍按真实时钟走，掉帧也不慢
- 木盒在系统的立体窗口（Shared Space 体积）里，可拖动；控制窗用原生页签：播放 / 曲子 / 设置
- 手势捏合 / 眼动 + 捏合 / 手柄射线（手柄 B / 返回键不会退出应用）；中英双语；全部本机运行，不联网、不需要账号

| | |
|---|---|
| ![音梳特写](docs/screenshots/01-comb-zh.jpg) 音梳特写：18 根齿 G4–C6，齿下标中文简谱 | ![播放页](docs/screenshots/02-play-zh.jpg) 播放页：[上弦并播放]、发条余量、快慢、[钉谱…] |
| ![钉谱屏](docs/screenshots/03-edit-zh.jpg) 钉谱屏：18 键键盘（黑键是升号），[钉好了] 回播放 | ![曲子](docs/screenshots/04-tunes-zh.jpg) 曲子：26 首公有领域曲分四组，每行 [试听] |
| ![设置](docs/screenshots/05-settings-zh.jpg) 设置：语言、音量、木色、[退出应用] | |

> 截图和演示视频都在 PICO 模拟器里拍（v0.1.4），3D 操作用调试广播驱动（Mac 模拟器发不出 3D 捏合 / 拖动），**非真实手势录像**。演示视频**没有声音**（模拟器录屏不录音）——八音盒以声音为主，请在真机上听。

### 安装

从 [Releases](https://github.com/kejing-dev/MusicBox/releases) 下载 `MusicBox-v0.1.4.apk`（assembleRelease **未签名**包，供商店重签名，不能直接 adb install）。自己装到头显请用 debug 构建：

```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.13+9/Contents/Home
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch io.embodit.musicbox
```

需要 PICO OS 6（Android 15，minSdk 35），Spatial SDK 6.1.9。

### 已知问题（v0.1.4）

- 在「曲子」或「设置」页点过页签后，焦点停在 TabBar 上，这时按手柄 A / ENTER / 确认键会跳回「播放」页（不退出、不改数据）
- 钉谱屏收起 TabBar（专注模式，[钉好了] 回播放页）
- 手柄射线 / 手势射线 / 眼手三种输入点 3D 齿、盒盖、钥匙、铭牌和拖钥匙 / 滚筒，以及真机上按 B 的表现，还没在真机上逐项验证；头显上的声音听感没测

### 素材与致谢

齿音：freesound pack 4540（folkman，CC0）；上弦声：freesound 760760（wolfmagpie，CC0）；盒盖声：Kenney（CC0）；舞者：Tripo 生成模型；字体 Noto Sans（SIL OFL）；曲目均为公有领域。构建方法和调试广播见 [AGENTS.md](AGENTS.md)，更新记录见 [CHANGELOG.md](CHANGELOG.md)。

## English

A wooden music box that sings on your desk, for PICO OS 6 Shared Space. Wind it and it plays while the dancer turns with the drum. Tap a tine and a pin lands on the drum, so you can pin a tune of your own and wind it to hear it sung back.

- **Lid**: the card inside names the tune; the dancer rises from her well
- **Wind**: 8 cells of spring under the key run down as it plays, then it slows and stops; or press [Wind & play]
- **Drum**: turn it by hand, note by note
- **Comb**: 18 tines, G4–C6 with every semitone (widened in v0.1.3), longer bass tines
- **Pin a tune**: an 18-key keyboard (black keys are sharps) in the edit view; tap a key or a tine to pin the current column, even while it plays; back, rest, 50-step undo
- **Tunes**: 26 public-domain tunes (now with Happy Birthday, Für Elise, Greensleeves, Auld Lang Syne, Silent Night, Scarborough Fair, Turkish March…) in four groups (Kids, Folk, Classical, Holiday), each with [Listen], plus up to six of your own, saved on device
- **Finish**: walnut / maple / red lacquer; speed 50–150 %; mute always on screen
- **Sound**: real CC0 recordings of a music box, each tine tuned to equal temperament; timing follows the real clock, so dropped frames don't slow the tune
- The box lives in a system volume you can move; the control window uses the native TabBar: Play / Tunes / Settings
- Gaze + pinch, hand ray or controller (B / Back never quits the app); Chinese / English; fully offline, no account

| | |
|---|---|
| ![Comb](docs/screenshots/01-comb-zh.jpg) The 18-tine comb, G4–C6 (Chinese UI) | ![Play](docs/screenshots/02-play-en.jpg) Play: [Wind & play], spring gauge, speed, [Edit pins…] |
| ![Edit](docs/screenshots/03-edit-en.jpg) Edit view: 18-key keyboard (black keys are sharps), [Done] | ![Tunes](docs/screenshots/04-tunes-en.jpg) Tunes: 26 public-domain tunes in four groups, each with [Listen] |
| ![Settings](docs/screenshots/05-settings-en.jpg) Settings: language, volume, finish, [Quit app] | |

> Screenshots and the demo video were taken on the PICO emulator (v0.1.4) with 3D actions driven by the debug broadcast (the Mac emulator can't inject 3D pinch / drag): **not a real-hand recording**. The demo video **has no sound** (emulator screen recording has no audio); listen on a headset.

### Install

Download `MusicBox-v0.1.4.apk` from [Releases](https://github.com/kejing-dev/MusicBox/releases) (assembleRelease, **unsigned**, for store re-signing; it can't be adb-installed as is). To try it on a headset, use a debug build:

```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.13+9/Contents/Home
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch io.embodit.musicbox
```

Needs PICO OS 6 (Android 15, minSdk 35), Spatial SDK 6.1.9.

### Known issues (v0.1.4)

- After tapping the Tunes or Settings tab, focus stays on the TabBar, and controller A / Enter / D-pad center then jumps back to Play (nothing quits, no data changes)
- The pin-edit view hides the TabBar (focus mode, [Done] goes back)
- The three inputs (controller ray, hand ray, gaze + pinch) on the 3D tines, lid, key, nameplate and the key / drum drags, and B on a real headset, are not yet checked on a headset; nor is the sound there

### Credits

Tines: freesound pack 4540 (folkman, CC0); winding: freesound 760760 (wolfmagpie, CC0); lid taps: Kenney (CC0); dancer: a Tripo-generated model; font Noto Sans (SIL OFL); all tunes are public domain. Build and debug broadcast: [AGENTS.md](AGENTS.md); history: [CHANGELOG.md](CHANGELOG.md).
