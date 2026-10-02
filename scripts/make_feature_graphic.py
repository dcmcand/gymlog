#!/usr/bin/env python3
"""Renders the Play/F-Droid feature graphic (1024x500, RGB, no alpha). One-off, needs Pillow:

  python3 scripts/make_feature_graphic.py fastlane/metadata/android/en-US/images/featureGraphic.png

The dumbbell is the launcher icon's (app/src/main/res/drawable/ic_launcher_foreground.xml,
108-unit viewport) scaled up; the green is ic_launcher_background.
"""
import sys

from PIL import Image, ImageDraw, ImageFont

GREEN = (0x1B, 0x5E, 0x20)
WHITE = (255, 255, 255)
FONT = "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf"
# (x1, y1, x2, y2, stroke width) from ic_launcher_foreground.xml, round caps.
DUMBBELL = [(30, 54, 78, 54, 4), (24, 38, 24, 70, 6), (32, 42, 32, 66, 5), (84, 38, 84, 70, 6), (76, 42, 76, 66, 5)]


def round_line(draw, x1, y1, x2, y2, width):
    draw.line((x1, y1, x2, y2), fill=WHITE, width=width)
    r = width / 2
    for x, y in ((x1, y1), (x2, y2)):
        draw.ellipse((x - r, y - r, x + r, y + r), fill=WHITE)


def main(out):
    img = Image.new("RGB", (1024, 500), GREEN)
    draw = ImageDraw.Draw(img)
    # 4x scale; offsets put the dumbbell at x 98-362 (with caps), centred on y=250, so the whole
    # group sits about 100 px inside each side (Play may crop or overlay the edges).
    scale, ox, oy = 4, 14, 34
    for x1, y1, x2, y2, w in DUMBBELL:
        round_line(draw, ox + x1 * scale, oy + y1 * scale, ox + x2 * scale, oy + y2 * scale, w * scale)
    draw.text((410, 262), "GymLog", font=ImageFont.truetype(FONT, 100), fill=WHITE, anchor="ls")
    draw.text((414, 318), "Private, offline workout log", font=ImageFont.truetype(FONT, 32), fill=WHITE, anchor="ls")
    img.save(out, "PNG")


if __name__ == "__main__":
    main(sys.argv[1])
