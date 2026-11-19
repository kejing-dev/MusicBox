# Changelog / 更新记录

## v0.1.4 · 2026-11-19

- 手柄 B（系统返回键）不再关掉控制窗、也不会把八音盒退到后台：拿着钥匙 / 滚筒时 = 松手，控制窗关着 = 在盒子旁重开，开着 = 什么也不做
- 音梳中文标签条抬高 3 mm，不再被盒子前壁挡住下沿：低音 5 #5 6 #6 7 下面的点看得见了
- Controller B (system Back) no longer closes the control window or sends the music box to the background: holding the key / drum = let go, window closed = reopen it beside the box, window open = nothing
- The comb label strip sits 3 mm higher, clear of the front wall, so the low-octave dots under 5 #5 6 #6 7 show

## v0.1.3 · 2026-11-18

- 音梳从 8 齿加宽到 18 齿：G4–C6 半音齐全，真实比例（齿距 10.8 mm，低音齿 5.2 cm 到高音 3.2 cm，底座斜切）；原来 8 根齿的声音逐字节不变
- 钉谱屏加一排 18 键键盘：11 个白键、7 个黑键（升号，深色）分两行不重叠，每个键 ≥ 48 dp；点键和点那根齿一样；控制窗加宽到 640 dp
- 补回 9 首公版曲：生日快乐、致爱丽丝（主题，e 小调）、绿袖子、友谊地久天长、新年好、送别、平安夜（G 大调，省一次重复）、斯卡布罗集市、土耳其进行曲（开头主题），共 26 首；两只老虎末句低音 sol 回到原八度
- 齿号标签：中文简谱（低音点在下），英文音名；卷谱 18 行，升号行像黑键一样加深
- 旧版本存的「我的曲子」按原音高读进来，不会变调
- The comb widens from 8 to 18 tines: G4–C6 with every semitone, real proportions (10.8 mm pitch, bass tines longer, slanted base); the old eight tines sound byte-identical
- The edit view gets an 18-key keyboard: 11 white and 7 black (sharp) keys in two rows, every key ≥ 48 dp; a key does what tapping that tine does; the control window is 640 dp wide
- Nine public-domain tunes are back: Happy Birthday, Für Elise (theme, in E minor), Greensleeves, Auld Lang Syne, Happy New Year, Farewell (Ordway), Silent Night (in G, one repeat out), Scarborough Fair, Turkish March (opening theme): 26 in all; Frère Jacques gets its low sol back
- Comb labels: numbered notation in Chinese, note names in English; the roll has 18 rows with the sharps shaded
- My tunes saved by older versions load at the same pitch

## v0.1.2 · 2026-11-18

- 经典曲从 6 首加到 17 首，全部公有领域，每首写明来源：童谣（小星星、玛丽有只小羊羔、小蜜蜂、伦敦桥、划船歌、两只老虎、王老先生有块地）、民歌（茉莉花、噢！苏珊娜、圣者进行曲）、古典（欢乐颂、卡农、勃拉姆斯摇篮曲、格里格《晨景》、德沃夏克《念故乡》）、节日（铃儿响叮当、普世欢腾）
- 曲子页按 童谣 / 民歌 / 古典 / 节日 分组，列表可上下滑；每行一个 [试听]：只放这首开头约 8 秒，不动滚筒；再点一下或开始播放就停
- 音梳只有 C5–C6 八根白键齿：要升降号或超过一个八度的曲子（生日快乐、致爱丽丝、绿袖子、友谊地久天长、平安夜等）没放进来，宁缺不改调
- The classics grow from 6 to 17 public-domain tunes, each with its source: Kids, Folk, Classical and Holiday groups
- The Tunes list is grouped and scrolls; every row has [Listen], which plays about 8 s of the opening without touching the drum (tap again, or start the box, to stop)
- The comb has eight natural tines C5–C6, so tunes that need a sharp or more than an octave (Happy Birthday, Für Elise, Greensleeves, Auld Lang Syne, Silent Night…) are left out rather than bent

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
