# Changelog / 更新记录

## v0.1.1 · 2026-11-18

- 钉谱改成就地展开的「钉谱屏」：播放页只留 [上弦并播放]、快慢和 [钉谱…]；点 [钉谱…] 才展开 [◂ 退一拍] [空一拍 ▸] [撤销] [清空…] [钉好了]，这一屏像确认页一样收起 TabBar（每屏可点控件 ≤ 7，模拟器 QA #1 打回项）
- 「曲子 · 我的」不再有大片空白：0–5 首时上方是一张「滚筒上的曲子」卡（小卷谱 + [改这首] / [钉谱…]），下面是我的曲子列表（QA #1 打回项）
- 木盒整体朝你前倾 12°、上移 1.9 cm：默认站位能看到盒顶和音梳齿面，不再是侧着的一条
- 首次打开的「先拉近一点」提示改成琥珀色卡 + 放大镜灯，更醒目
- Pin editing moved into an in-place edit view: the Play page keeps only wind & play, speed and [Edit pins…]; the edit keys (back / rest / undo / clear… / done) unfold there and the TabBar folds away like a confirm page (≤ 7 controls per screen, emulator QA #1)
- Tunes › Mine no longer has a blank band: with 0–5 tunes a "tune on the drum" card (mini roll + [Change it] / [Edit pins…]) sits above the list (QA #1)
- The whole box leans 12° toward you and sits 1.9 cm higher, so from the default spot you see the lid top and the comb, not a thin edge
- The first-run "come a little closer" hint is now an amber card with a lamp

## v0.1.0 · 2026-11-17

- 第一版：桌上一只木八音盒（Shared Space 体积窗口）+ 一块控制窗（原生 TabBar：播放 / 曲子 / 设置）
- 开盖、拧发条（8 格余量，最后渐慢停下）、拨滚筒前后转、点音梳齿钉音；舞者随滚筒转
- 6 首公有领域曲 + 最多 6 首自己的曲子（本机保存，可撤销 50 步）
- 胡桃木 / 枫木 / 红漆三种木色；中英双语；全部离线
- 声音是真实八音盒单音的 CC0 录音，按 C 大调变调；节拍走墙钟，掉帧也不慢
- First version: a wooden music box (Shared Space volume) and a control window (native TabBar: Play / Tunes / Settings)
- Open the lid, wind the key (8-cell spring gauge, slows down at the end), turn the drum by hand, tap a tine to pin a note; the dancer turns with the drum
- Six public-domain tunes plus up to six of your own (saved on device, 50-step undo)
- Walnut / maple / red lacquer; Chinese and English; fully offline
- Real CC0 recordings of a music box, re-pitched to C major; timing runs on the wall clock, so dropped frames don't slow the tune
