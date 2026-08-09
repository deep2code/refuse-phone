import io
import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

RES = "/Users/junjunyi/src-code/refuse-phone/app/src/main/res"
FONT = "/System/Library/Fonts/STHeiti Medium.ttc"
OUT_PREVIEW = "/Users/junjunyi/src-code/refuse-phone/release-artifacts"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# Material Design 经典 "call" 图标路径（24x24 viewBox，实心听筒）
CALL_PATH = ("M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 "
             "1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 "
             "0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z")
RSVG = "/opt/homebrew/bin/rsvg-convert"

BLACK = (31, 31, 31, 255)
RED = (226, 75, 74, 255)


def render_handset(target_px, color_hex):
    """用 rsvg-convert 把 Material 电话听筒矢量路径渲染成 PNG。"""
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{target_px}" height="{target_px}" '
           f'viewBox="0 0 24 24"><path d="{CALL_PATH}" fill="{color_hex}"/></svg>')
    result = subprocess.run(
        [RSVG, "-w", str(target_px), "-h", str(target_px)],
        input=svg.encode("utf-8"),
        capture_output=True,
        check=True
    )
    return Image.open(io.BytesIO(result.stdout)).convert("RGBA")


def draw_handset(img, size, alpha=255):
    """在图像底部中央叠加标准电话听筒。"""
    big = size
    hw = int(big * 0.34)
    color = "#1f1f1f"
    handset = render_handset(hw, color).resize((hw, hw), Image.LANCZOS)
    if alpha != 255:
        r, g, b, a = handset.split()
        a = a.point(lambda x: int(x * alpha / 255))
        handset = Image.merge("RGBA", (r, g, b, a))
    hx = int(big / 2 - hw / 2)
    hy = int(big * 0.60)
    img.alpha_composite(handset, (hx, hy))


def draw_red_cross(d, size, lw):
    def p(c):
        return (c / 160.0) * size

    d.line([(p(54), p(42)), (p(134), p(122))], fill=RED, width=lw)
    d.line([(p(134), p(42)), (p(54), p(122))], fill=RED, width=lw)


def make_foreground(size, supersample=4):
    # 透明底版本，用于通知/悬浮窗小图标（无白底方块）
    big = size * supersample
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    f = int(big * 0.44)
    font = ImageFont.truetype(FONT, f, index=0)
    d.text((big / 2, big * 0.37), "骚扰", font=font, fill=BLACK, anchor="mm")
    draw_handset(img, big)
    lw = max(2, int(big * 0.0375))
    draw_red_cross(d, big, lw)
    return img.resize((size, size), Image.LANCZOS)


def make_icon(size, supersample=4):
    big = size * supersample
    img = Image.new("RGBA", (big, big), (255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    # 黑字「骚扰」：字号约 0.44*size，上移给下方电话留位
    f = int(big * 0.44)
    font = ImageFont.truetype(FONT, f, index=0)
    d.text((big / 2, big * 0.37), "骚扰", font=font, fill=BLACK, anchor="mm")
    # 文字下方的标准电话听筒图标（黑色，与字同色）
    draw_handset(img, big)
    # 红叉：范围（160 网格内 x54~134 / y42~122），线宽 6/160
    lw = max(2, int(big * 0.0375))
    draw_red_cross(d, big, lw)
    return img.resize((size, size), Image.LANCZOS)


for dpi, size in SIZES.items():
    img = make_icon(size)
    d = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, "ic_launcher.png"))
    img.save(os.path.join(d, "ic_launcher_round.png"))
    print(f"wrote mipmap-{dpi} ({size}px)")

os.makedirs(OUT_PREVIEW, exist_ok=True)
# 透明底 foreground（通知/悬浮窗用）
fg = make_foreground(192)
drawable_dir = os.path.join(RES, "drawable")
os.makedirs(drawable_dir, exist_ok=True)
fg.save(os.path.join(drawable_dir, "ic_launcher_foreground.png"))
print("wrote drawable/ic_launcher_foreground.png")

big = make_icon(512)
big.save(os.path.join(OUT_PREVIEW, "icon_preview.png"))
print("wrote preview 512px")
print("done")
