# -*- coding: utf-8 -*-
"""
生成 Android 各密度的 App 图标与启动图。

源图：豆包生产物料/app图标/用户选中_原图.png（1024x1024，圆角紫色渐变 + 折叠地图 + 橙色定位针）
该图四角是纯白（不是透明），所以这里统一做两件事：
  1. 按原设计的圆角半径重新裁一个 alpha 遮罩，把白角变成透明（避免在深色壁纸上露出白方块）
  2. 另外裁一个正圆版本给 ic_launcher_round

产出：
  mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png          传统方形圆角图标
  mipmap-{...}/ic_launcher_round.png                                传统圆形图标
  mipmap-{...}/ic_launcher_foreground.png                           自适应图标前景（满幅、圆角透明）
  drawable-{...}/splash_logo.png                                    启动页居中的 logo
"""
import os
import sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "..", "android", "app", "src", "main", "res"))
SOURCE = os.path.abspath(os.path.join(
    HERE, "..", "..", "豆包生产物料", "app图标", "用户选中_原图.png"
))

# 原设计的圆角半径约为图标边长的 22.5%
CORNER_RATIO = 0.225

# 各密度的尺寸：传统图标 / 圆形图标 / 自适应图标（108dp）
# 启动 logo 用 120dp，在 360dp 宽的屏幕上约占 1/3
DENSITIES = {
    "mdpi":    {"legacy": 48,  "adapt": 108, "splash": 120},
    "hdpi":    {"legacy": 72,  "adapt": 162, "splash": 180},
    "xhdpi":   {"legacy": 96,  "adapt": 216, "splash": 240},
    "xxhdpi":  {"legacy": 144, "adapt": 324, "splash": 360},
    "xxxhdpi": {"legacy": 192, "adapt": 432, "splash": 480},
}

# 4x 超采样做遮罩，边缘更平滑
SS = 4


def load_source():
    if not os.path.exists(SOURCE):
        sys.exit(f"[gen-assets] 找不到源图标：{SOURCE}")
    im = Image.open(SOURCE).convert("RGB")
    if im.size != (1024, 1024):
        im = im.resize((1024, 1024), Image.LANCZOS)
    return im


def rounded_alpha(size, radius_ratio, super_sample=SS):
    """返回一个圆角矩形的 L 模式遮罩"""
    big = size * super_sample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, big - 1, big - 1),
        radius=int(big * radius_ratio),
        fill=255,
    )
    return mask.resize((size, size), Image.LANCZOS)


def circle_alpha(size, super_sample=SS):
    """返回一个正圆遮罩"""
    big = size * super_sample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def square_rgba(src, size, mask):
    """把源图缩放到 size 并套上遮罩，返回 RGBA"""
    base = src.resize((size, size), Image.LANCZOS).convert("RGBA")
    base.putalpha(mask)
    return base


def ensure(path):
    os.makedirs(path, exist_ok=True)
    return path


def main():
    src = load_source()
    written = []

    for density, spec in DENSITIES.items():
        legacy = spec["legacy"]
        adapt = spec["adapt"]

        mipmap = ensure(os.path.join(RES, f"mipmap-{density}"))
        drawable = ensure(os.path.join(RES, f"drawable-{density}"))

        # 传统方形圆角图标
        out = os.path.join(mipmap, "ic_launcher.png")
        square_rgba(src, legacy, rounded_alpha(legacy, CORNER_RATIO)).save(out, optimize=True)
        written.append(out)

        # 传统圆形图标
        out = os.path.join(mipmap, "ic_launcher_round.png")
        square_rgba(src, legacy, circle_alpha(legacy)).save(out, optimize=True)
        written.append(out)

        # 自适应图标前景：满幅 108dp，圆角处透明（外层由 drawable/ic_launcher_bg.xml 的
        # 同色系渐变兜底，且启动器遮罩本身会裁掉圆角，所以看不到接缝）
        out = os.path.join(mipmap, "ic_launcher_foreground.png")
        square_rgba(src, adapt, rounded_alpha(adapt, CORNER_RATIO)).save(out, optimize=True)
        written.append(out)

        # 启动页 logo
        out = os.path.join(drawable, "splash_logo.png")
        square_rgba(src, spec["splash"], rounded_alpha(spec["splash"], CORNER_RATIO)).save(out, optimize=True)
        written.append(out)

    for p in written:
        print("[gen-assets]", os.path.relpath(p, RES))
    print(f"[gen-assets] 共生成 {len(written)} 个文件")


if __name__ == "__main__":
    main()
