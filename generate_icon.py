import os
from PIL import Image, ImageDraw, ImageFont

RES = "/Users/junjunyi/src-code/refuse-phone/app/src/main/res"
FONT = "/System/Library/Fonts/STHeiti Medium.ttc"
OUT_PREVIEW = "/Users/junjunyi/src-code/refuse-phone/release-artifacts"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def make_icon(size):
    img = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    # 黑字「骚扰」：字号约 0.45*size，水平垂直居中
    f = int(size * 0.45)
    font = ImageFont.truetype(FONT, f, index=0)
    d.text((size / 2, size / 2), "骚扰", font=font,
           fill=(31, 31, 31, 255), anchor="mm")
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
big = make_icon(512)
big.save(os.path.join(OUT_PREVIEW, "icon_preview.png"))
print("wrote preview 512px")
print("done")
