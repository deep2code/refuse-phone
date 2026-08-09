import os
from PIL import Image, ImageDraw, ImageFont

RES = "/Users/junjunyi/src-code/refuse-phone/app/src/main/res"
FONT = "/System/Library/Fonts/STHeiti Medium.ttc"
OUT_PREVIEW = "/Users/junjunyi/src-code/refuse-phone/release-artifacts"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def draw_phone(d, size, color):
    # 文字下方的手机图标：圆角矩形机身 + 白色屏幕 + 顶部听筒
    s = size
    cx = s / 2
    pw = s * 0.24          # 机身宽
    ph = s * 0.30          # 机身高
    top = s * 0.595
    left = cx - pw / 2
    right = cx + pw / 2
    bottom = top + ph
    r = s * 0.035          # 机身圆角半径
    # 机身（黑色实心圆角矩形）
    d.rounded_rectangle([left, top, right, bottom], radius=r, fill=color)
    # 屏幕（白色内嵌圆角矩形）
    inset = s * 0.028
    d.rounded_rectangle([left + inset, top + inset, right - inset, bottom - inset],
                        radius=r * 0.65, fill=(255, 255, 255, 255))
    # 听筒（顶部小黑条）
    sw = s * 0.09
    sh = s * 0.013
    sy = top + inset * 0.8
    d.rounded_rectangle([cx - sw / 2, sy, cx + sw / 2, sy + sh],
                        radius=sh / 2, fill=color)


def make_foreground(size):
    # 透明底版本，用于通知/悬浮窗小图标（无白底方块）
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    f = int(size * 0.44)
    font = ImageFont.truetype(FONT, f, index=0)
    d.text((size / 2, size * 0.40), "骚扰", font=font,
           fill=(31, 31, 31, 255), anchor="mm")
    draw_phone(d, size, (31, 31, 31, 255))
    lw = max(2, int(size * 0.0375))

    def p(c):
        return (c / 160.0) * size

    red = (226, 75, 74, 255)
    d.line([(p(54), p(42)), (p(134), p(122))], fill=red, width=lw)
    d.line([(p(134), p(42)), (p(54), p(122))], fill=red, width=lw)
    return img


def make_icon(size):
    img = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    # 黑字「骚扰」：字号约 0.44*size，上移给下方电话留位
    f = int(size * 0.44)
    font = ImageFont.truetype(FONT, f, index=0)
    d.text((size / 2, size * 0.40), "骚扰", font=font,
           fill=(31, 31, 31, 255), anchor="mm")
    # 文字下方的电话图标（黑色，与字同色）
    draw_phone(d, size, (31, 31, 31, 255))
    # 红叉：范围与预览一致（160 网格内 x54~134 / y42~122），线宽 6/160
    lw = max(2, int(size * 0.0375))

    def p(c):
        return (c / 160.0) * size

    red = (226, 75, 74, 255)
    d.line([(p(54), p(42)), (p(134), p(122))], fill=red, width=lw)
    d.line([(p(134), p(42)), (p(54), p(122))], fill=red, width=lw)
    return img


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
