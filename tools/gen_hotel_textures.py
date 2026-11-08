"""Textures for the Eternity Devil's loot (Chainsaw Man mod): Eternity flesh (a lump of the devil's meat with an eye and a gape of uneven
teeth) and Eternity blood (a stoppered bottle of dark red). 16 x 16 item textures in the devil's own palette (tools/eternity_drawings.py).

Run: python tools/gen_hotel_textures.py   (writes into chainsawman/src/main/resources/assets/chainsawman/textures/item)
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_drawings as ed  # noqa: E402

OUT = os.path.join(HERE, '..', 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'textures', 'item')

INK = (34, 12, 18, 255)
FLESH = (204, 108, 98, 255)
FLESH_LIGHT = (226, 140, 126, 255)
FLESH_DARK = (150, 64, 66, 255)
TOOTH = (238, 228, 204, 255)
TOOTH_SHADE = (200, 188, 160, 255)
THROAT = (70, 14, 24, 255)
WHITE = (246, 240, 228, 255)
IRIS = (150, 22, 34, 255)


def put(img, pts, colour):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), colour)


def flesh():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    # A lump: an irregular blob, ink round it.
    rows = {2: (6, 10), 3: (4, 12), 4: (3, 13), 5: (2, 14), 6: (2, 14), 7: (1, 14), 8: (1, 14), 9: (1, 14), 10: (2, 14), 11: (2, 13), 12: (3, 12), 13: (4, 11)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), FLESH)
    # Light on the top left, shade on the bottom right.
    for y, (a, b) in rows.items():
        if y <= 5:
            put(img, [(a + 1, y), (a + 2, y)], FLESH_LIGHT)
        if y >= 10:
            put(img, [(b, y), (b - 1, y)], FLESH_DARK)
    put(img, [(5, 3), (5, 4), (6, 3), (3, 6)], FLESH_LIGHT)
    put(img, [(12, 8), (12, 9), (13, 9), (11, 11), (10, 12), (9, 12)], FLESH_DARK)
    # An outline.
    mask = img.copy()
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < 16 and 0 <= y + dy < 16 and mask.getpixel((x + dx, y + dy))[3] != 0:
                        img.putpixel((x, y), INK)
                        break
    # An eye up in the corner: white, a red iris, a pupil.
    put(img, [(4, 5), (5, 5), (6, 5), (4, 6), (5, 6), (6, 6)], WHITE)
    put(img, [(5, 5), (5, 6)], IRIS)
    put(img, [(5, 6)], INK)
    put(img, [(3, 5), (7, 5), (3, 6), (7, 6), (4, 4), (5, 4), (6, 4), (4, 7), (5, 7), (6, 7)], FLESH_DARK)
    # A gape of uneven teeth across the lower middle.
    put(img, [(x, 9) for x in range(4, 13)], THROAT)
    put(img, [(x, 10) for x in range(5, 12)], THROAT)
    uppers = {4: 1, 5: 2, 6: 1, 7: 2, 8: 1, 9: 1, 10: 2, 11: 1, 12: 1}
    for x, h in uppers.items():
        put(img, [(x, 8)] if h == 1 else [(x, 8), (x, 9)], TOOTH if x % 2 else TOOTH_SHADE)
    lowers = {5: 1, 6: 1, 7: 2, 8: 1, 9: 2, 10: 1, 11: 1}
    for x, h in lowers.items():
        put(img, [(x, 10)] if h == 1 else [(x, 10), (x, 9)], TOOTH_SHADE if x % 2 else TOOTH)
    put(img, [(3, 9), (13, 9), (4, 10), (12, 10)], INK)
    return img


def blood():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    glass = (180, 205, 214, 255)
    glass_light = (222, 238, 244, 255)
    red = (122, 12, 26, 255)
    red_light = (178, 28, 44, 255)
    red_dark = (74, 6, 18, 255)
    cork = (146, 104, 64, 255)
    cork_dark = (104, 70, 42, 255)
    # The bottle: a neck and a round belly.
    put(img, [(7, 1), (8, 1), (7, 2), (8, 2)], cork)
    put(img, [(7, 2)], cork_dark)
    for x in (6, 9):
        put(img, [(x, 3), (x, 4)], INK)
    put(img, [(7, 3), (8, 3), (7, 4), (8, 4)], glass)
    belly = {5: (5, 10), 6: (4, 11), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (4, 11), 12: (4, 11), 13: (5, 10)}
    for y, (a, b) in belly.items():
        for x in range(a, b + 1):
            img.putpixel((x, y), red if y >= 7 else glass)
        put(img, [(a - 1, y), (b + 1, y)], INK)
    for x in range(5, 11):
        put(img, [(x, 14)], INK)
        put(img, [(x, 5 - 1)], INK)
    put(img, [(4, 13), (11, 13), (3, 12), (12, 12)], INK)
    put(img, [(4, 5), (11, 5), (3, 6), (12, 6)], INK)
    # The blood, lit on the left, dark at the bottom.
    put(img, [(4, 8), (4, 9), (5, 8), (4, 10)], red_light)
    put(img, [(x, 12) for x in range(5, 11)] + [(x, 13) for x in range(6, 10)] + [(10, 11), (10, 10)], red_dark)
    put(img, [(x, 7) for x in range(4, 12)], red_light)
    # The glass shining above it.
    put(img, [(5, 6), (5, 5), (4, 6)], glass_light)
    put(img, [(9, 6), (10, 6), (10, 5)], glass)
    # A drop on the glass.
    put(img, [(9, 9), (9, 10)], red_light)
    return img


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name))
    print('wrote', name)


if __name__ == '__main__':
    save(flesh(), 'eternity_flesh.png')
    save(blood(), 'eternity_blood.png')
