"""Music Box contrast table (D13, ui-audit): every text colour x its background (WCAG ratio) and the 3D colour pairs (CIE L*)."""
def lin(c):
    c = c / 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
def hexrgb(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def Y(h): r, g, b = (lin(v) for v in hexrgb(h)); return 0.2126 * r + 0.7152 * g + 0.0722 * b
def ratio(a, b): ya, yb = sorted((Y(a), Y(b)), reverse=True); return (ya + 0.05) / (yb + 0.05)
def L(h): y = Y(h); return 116 * (y ** (1 / 3) if y > 0.008856 else 7.787 * y + 16 / 116) - 16
GLASS = "#B4B3B1"  # darkest measured window glass (royal-ur / factory measure)
pairs = [
    ("正文 Ink / 窗口玻璃", "#1B1F23", GLASS, 4.5), ("次要字 InkSecondary / 玻璃", "#3A3F44", GLASS, 4.5),
    ("状态卡 Ink / TagFace", "#1B1F23", "#FBF6EC", 4.5), ("卡内次要字 / TagFace", "#3A3F44", "#FBF6EC", 4.5),
    ("曲子行 Ink / 选中 PrimaryContainer", "#1B1F23", "#F6DDE3", 4.5), ("「在滚筒上」Primary / PrimaryContainer", "#6E1F33", "#F6DDE3", 4.5),
    ("主按钮字 白 / Primary", "#FFFFFF", "#6E1F33", 4.5), ("退出 / 确认按钮字 白 / Error", "#FFFFFF", "#B3261E", 4.5),
    ("怎么操作序号 白 / Primary", "#FFFFFF", "#6E1F33", 4.5), ("静音开（已静音）白 / Primary", "#FFFFFF", "#6E1F33", 4.5),
    ("盒盖曲名卡 字 / 丝绒", "#F3E6C8", "#5A1A2A", 4.5), ("齿号条 字 / 深木", "#F3E6C8", "#2A1E16", 4.5),
    ("铜铭牌 刻字 / 黄铜", "#3A2A10", "#C9A25A", 4.5),
]
print("| 文字 | 前景 | 底色 | 对比度 | 门槛 | 结果 |\n|---|---|---|---|---|---|")
for n, f, b, t in pairs:
    r = ratio(f, b); print(f"| {n} | {f} | {b} | {r:.2f}:1 | {t} | {'过' if r >= t else '不过'} |")
print()
lp = [("发条格 亮 vs 暗", "#F2B24D", "#1A120A", 25), ("钢钉 vs 黄铜滚筒", "#B9BEC4", "#C9A25A", 0),
      ("铜铭牌 vs 胡桃盒", "#C9A25A", "#4A3222", 25), ("铜铭牌 vs 枫木盒", "#C9A25A", "#D9B98A", 0), ("铜铭牌 vs 红漆盒", "#C9A25A", "#8E1F1B", 25)]
print("| 3D 对比 | A | B | ΔL* | 门槛 |\n|---|---|---|---|---|")
for n, a, b, t in lp:
    print(f"| {n} | {a} | {b} | {abs(L(a) - L(b)):.1f} | {'≥ %d' % t if t else '—'} |")
