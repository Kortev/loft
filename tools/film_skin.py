"""Draws the stand-in skin for the first friend in a film, until there is his own: python tools/film_skin.py OUT.png

The strongest there is, as he is drawn in the show: white hair standing up in spikes, pale, and the high-collared black
uniform to the wrists and ankles. His blindfold is the mod's own, drawn over the eyes in the game. A 64x64 skin in the
game's layout (the outer layer gives the hair and the collar their bulk), with a little shading so it doesn't look flat.
Swap it for the friend's own skin by putting that at build/selftest/skins/Rival.png instead. Needs numpy and Pillow."""
import sys

import numpy as np
from PIL import Image

rng = np.random.default_rng(1989)
img = np.zeros((64, 64, 4), np.uint8)


def hexc(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)] + [255], np.uint8)


HAIR = [hexc('#f7f8fc'), hexc('#e6eaf2'), hexc('#cfd5e2'), hexc('#b7bfd0')]
SKIN = hexc('#f0cdb6')
SKIN_SHADE = hexc('#e0b79f')
CLOTH = [hexc('#1c1e28'), hexc('#22253a'), hexc('#191a22'), hexc('#2b2f45')]
PANTS = [hexc('#17181f'), hexc('#1d1f29'), hexc('#14151b')]
SHOE = hexc('#2b2c33')
GOLD = hexc('#d2ac52')
EYE = hexc('#8fe3ff')
EYE_WHITE = hexc('#f4fbff')
MOUTH = hexc('#d9ab98')


def fill(x0, y0, x1, y1, palette, weights=None):
    """Fills a rectangle with a palette's colours scattered, as a skin painter's noise."""
    palette = palette if isinstance(palette, list) else [palette]
    p = np.array(weights if weights else [1] * len(palette), float)
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y, x] = palette[rng.choice(len(palette), p=p / p.sum())]


def box(u, v, w, h, d, palette, weights=None):
    """Every face of one part of the model, its net at (u, v): w wide, h tall, d deep."""
    fill(u + d, v, u + d + w, v + d, palette, weights)  # top
    fill(u + d + w, v, u + d + 2 * w, v + d, palette, weights)  # bottom
    fill(u, v + d, u + 2 * d + 2 * w, v + d + h, palette, weights)  # the four sides


# The head: skin all round, hair over the top, the back and the upper sides.
box(0, 0, 8, 8, 8, SKIN)
fill(8, 0, 16, 8, HAIR, [3, 3, 2, 1])  # top
fill(24, 8, 32, 16, HAIR, [3, 3, 2, 1])  # back
for u in (0, 16):  # the sides: hair down to the ears, longer at the back
    fill(u, 8, u + 8, 11, HAIR, [3, 3, 2, 1])
    for x in range(8):
        depth = 11 + ((7 - x) * 4) // 8 if u == 0 else 11 + (x * 4) // 8
        fill(u + x, 11, u + x + 1, depth, HAIR, [2, 3, 2, 1])
# The face: the fringe falling over the brow in points, the eyes, a nose's shadow, the mouth.
fill(8, 8, 16, 10, HAIR, [3, 3, 2, 1])
for x, down in enumerate([2, 1, 2, 0, 1, 2, 1, 2]):
    fill(8 + x, 10, 9 + x, 10 + down, HAIR, [2, 3, 2, 1])
fill(8, 11, 16, 16, SKIN)
for x in (9, 13):
    img[12, x] = EYE_WHITE
    img[12, x + 1] = EYE
img[14, 11] = MOUTH
img[14, 12] = MOUTH

# The hat layer: spikes of hair standing out over the top, the sides and the back (the rest left clear).
for y in range(0, 16):
    for x in range(32, 64):
        img[y, x] = 0
fill(40, 0, 48, 8, HAIR, [2, 3, 2, 1])
for x in range(8):
    for y in range(8):
        if (x + y * 3) % 5 in (0, 1, 3):
            img[y, 40 + x, 3] = 0  # gaps between the spikes on top
fill(56, 8, 64, 13, HAIR, [2, 3, 2, 1])  # back
for u in (32, 48):  # sides
    fill(u, 8, u + 8, 10, HAIR, [2, 3, 2, 1])
    for x in range(0, 8, 2):
        fill(u + x, 10, u + x + 1, 11, HAIR, [2, 3, 2, 1])
for x in range(8):  # the fringe standing out over the front
    if x in (0, 2, 3, 5, 7):
        fill(40 + x, 8, 41 + x, 9 + (x % 2), HAIR, [2, 3, 2, 1])

# The body: the uniform, buttoned to the throat with a gold button at the collar.
box(16, 16, 8, 12, 4, CLOTH, [5, 2, 2, 1])
fill(23, 20, 25, 32, CLOTH[3])  # the placket down the front
img[20, 21] = GOLD
img[21, 21] = GOLD
# Its outer layer: the high collar standing up round the neck.
for y in range(32, 48):
    for x in range(16, 40):
        img[y, x] = 0
fill(20, 36, 28, 38, CLOTH, [3, 1, 2, 2])  # front collar
fill(16, 36, 20, 38, CLOTH, [3, 1, 2, 2])  # right side
fill(28, 36, 40, 38, CLOTH, [3, 1, 2, 2])  # left side and back
img[36, 21] = GOLD

# The arms: sleeves to the wrists, hands bare.
box(40, 16, 4, 12, 4, CLOTH, [5, 2, 2, 1])
fill(40, 30, 56, 32, SKIN)
fill(48, 16, 52, 20, SKIN)  # the right hand's palm (the arm's bottom)
box(32, 48, 4, 12, 4, CLOTH, [5, 2, 2, 1])
fill(32, 62, 48, 64, SKIN)
fill(40, 48, 44, 52, SKIN)
for y in range(32, 48):  # no sleeve layer on the right arm
    for x in range(40, 56):
        img[y, x] = 0
for y in range(48, 64):  # nor the left
    for x in range(48, 64):
        img[y, x] = 0

# The legs: black trousers, dark shoes.
box(0, 16, 4, 12, 4, PANTS)
fill(0, 30, 16, 32, SHOE)
fill(8, 16, 12, 20, SHOE)
box(16, 48, 4, 12, 4, PANTS)
fill(16, 62, 32, 64, SHOE)
fill(24, 48, 28, 52, SHOE)
for y in range(32, 48):  # no trouser layers
    for x in range(0, 16):
        img[y, x] = 0
for y in range(48, 64):
    for x in range(0, 16):
        img[y, x] = 0

Image.fromarray(img, 'RGBA').save(sys.argv[1])
print('wrote', sys.argv[1])
