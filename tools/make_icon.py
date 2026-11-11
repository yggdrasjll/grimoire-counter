# Grimoire Counter - Copyright (c) 2026 Yggdrasjll. All rights reserved.
# Draws the mod's icon: a tally of five in gold on navy.
from PIL import Image, ImageDraw
S = 128
im = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rounded_rectangle((4, 4, S - 5, S - 5), radius=18, fill=(8, 16, 36, 255), outline=(255, 212, 92, 255), width=4)
for x in (32, 50, 68, 86):
    d.rounded_rectangle((x - 4, 30, x + 4, 98), radius=3, fill=(239, 232, 212, 255))
d.line((22, 92, 104, 36), fill=(255, 212, 92, 255), width=9)
d.ellipse((98, 14, 114, 30), fill=(127, 207, 106, 255))
im.save('src/main/resources/assets/grimoire_counter/icon.png')
