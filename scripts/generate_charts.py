#!/usr/bin/env python3
"""生成 README 用的图表 (PNG)，使用中文字体渲染。

输出目录：docs/assets/
依赖：matplotlib + 本机 STHeiti Medium.ttc 中文字体
"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "docs", "assets")
os.makedirs(ASSETS, exist_ok=True)

# 注册中文字体
FONT_PATH = "/System/Library/Fonts/STHeiti Medium.ttc"
if os.path.exists(FONT_PATH):
    font_manager.fontManager.addfont(FONT_PATH)
    plt.rcParams["font.family"] = font_manager.FontProperties(fname=FONT_PATH).get_name()
plt.rcParams["axes.unicode_minus"] = False
plt.rcParams["font.size"] = 13

# 统一配色
C_RED = "#E53935"
C_GREEN = "#2E9E5B"
C_AMBER = "#F2A100"
C_DARK = "#1F2933"
C_GREY = "#9AA5B1"

# ---------------------------------------------------------------------------
# 图 1：APK 体积 —— R8 关闭 vs 开启
# ---------------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(6.4, 4.2), dpi=160)
labels = ["R8 关闭\n(未混淆/未压缩)", "R8 开启\n(混淆+压缩+收缩)"]
sizes = [12.9, 4.9]
colors = [C_RED, C_GREEN]
bars = ax.bar(labels, sizes, color=colors, width=0.55, edgecolor="white", linewidth=1.5)
for b, v in zip(bars, sizes):
    ax.text(b.get_x() + b.get_width() / 2, v + 0.3, f"{v} MB",
            ha="center", va="bottom", fontweight="bold", color=C_DARK)
# 缩小标注
ax.annotate("", xy=(1, 4.9), xytext=(1, 12.9),
            arrowprops=dict(arrowstyle="-", color=C_GREY, linestyle="--", lw=1))
ax.text(1.18, 8.9, "↓ 62%", color=C_GREEN, fontweight="bold", fontsize=13,
        va="center")
ax.set_ylabel("APK 体积 (MB)")
ax.set_title("Release 包体积对比：开启 R8 后下降约 62%", fontweight="bold", pad=14)
ax.set_ylim(0, 15)
ax.spines[["top", "right"]].set_visible(False)
ax.grid(axis="y", color="#E4E7EB", lw=0.8)
ax.set_axisbelow(True)
fig.tight_layout()
fig.savefig(os.path.join(ASSETS, "apk-size.png"), bbox_inches="tight")
plt.close(fig)

# ---------------------------------------------------------------------------
# 图 2：HarmonyOS 3 功能可用性矩阵
# 评分：3=完全可用  2=取决于系统授权  1=不可用
# ---------------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(6.8, 4.2), dpi=160)
feats = ["号码查询", "来电识别悬浮窗", "系统级来电拦截\n(CallScreening)", "自动挂断\n(endCall)"]
scores = [3, 3, 2, 1]
bar_colors = [C_GREEN, C_GREEN, C_AMBER, C_RED]
y = range(len(feats))
bars = ax.barh(list(y), scores, color=bar_colors, height=0.6,
               edgecolor="white", linewidth=1.2)
ax.set_yticks(list(y))
ax.set_yticklabels(feats)
ax.set_xlim(0, 3.4)
ax.set_xticks([1, 2, 3])
ax.set_xticklabels(["不可用", "取决于授权", "完全可用"])
ax.invert_yaxis()
status_text = {3: "可用", 2: "取决于授权", 1: "不可用"}
for b, s in zip(bars, scores):
    ax.text(s + 0.08, b.get_y() + b.get_height() / 2, status_text[s],
            va="center", ha="left", fontsize=11, color=C_DARK, fontweight="bold")
ax.set_title("各功能在 HarmonyOS 3 (nova8se) 上的可用性",
             fontweight="bold", pad=14)
ax.spines[["top", "right"]].set_visible(False)
ax.grid(axis="x", color="#E4E7EB", lw=0.8)
ax.set_axisbelow(True)
fig.tight_layout()
fig.savefig(os.path.join(ASSETS, "harmonyos-support.png"), bbox_inches="tight")
plt.close(fig)

print("charts generated ->", os.listdir(ASSETS))
