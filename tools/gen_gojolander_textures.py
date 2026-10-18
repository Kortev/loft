"""Draws Gojolander's textures (needs NumPy and Pillow): the blindfold's item icon, the cloth it is drawn with when worn,
and the Six Eyes that show, burning, when it is pushed up.

- textures/item/blindfold.png (16x16): the blindfold as it is carried, a loop of black cloth tied in a knot, its two
  ends hanging; a little blue sheen along its folds.
- textures/entity/blindfold.png (64x32): the cloth on the band round the head, its knot and its ends, laid out as
  BlindfoldFeature's boxes are unwrapped: matte black, a faint weave, darker in its folds, lighter along its top edge.
- textures/entity/six_eyes.png (64x64): in the head's own layout, nothing but the eyes, where a skin has them (the
  middle row of the face), in the colour of a clear sky lit from inside, a glow over and under them. The game adds it
  full bright to the face.

Usage: python tools/gen_gojolander_textures.py
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'gojolander', 'src', 'main', 'resources', 'assets', 'gojolander')
CLIENT = os.path.join(os.path.dirname(__file__), '..', 'gojolander', 'src', 'client', 'resources', 'assets', 'gojolander')
rng = np.random.default_rng(1989)

# The icon, drawn by hand: O outline, B cloth, H the light along its folds, S its sheen, K the knot.
ICON = [
    '................',
    '................',
    '.....OOOOOO.....',
    '...OOBBBBBBOO...',
    '..OBHHHHHHHHBO..',
    '.OBHBOOOOOOBSBO.',
    '.OBSO......OBBO.',
    '.OBBO......OBBO.',
    '.OBSBO....OBSBO.',
    '..OBBBOOOOBBBO..',
    '...OOBBKKBBOO...',
    '.....OOKKOO.....',
    '......OBBO......',
    '.....OBOOBO.....',
    '....OBO..OBO....',
    '....OO....OO....',
]
ICON_COLOURS = {
    'O': (8, 8, 11, 255),
    'B': (27, 28, 35, 255),
    'H': (52, 55, 70, 255),
    'S': (58, 76, 108, 255),
    'K': (40, 42, 54, 255),
    '.': (0, 0, 0, 0),
}


def save(img, base, *path):
    full = os.path.join(base, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, optimize=True)
    print('wrote', os.path.relpath(full, os.path.join(os.path.dirname(__file__), '..')))


def icon():
    px = np.array([[ICON_COLOURS[c] for c in row] for row in ICON], dtype=np.uint8)
    return Image.fromarray(px, 'RGBA')


def cloth(w, h):
    """Matte black cloth, its weave a faint check of lighter and darker threads."""
    base = np.array([21, 22, 27], float)
    weave = ((np.arange(w)[None, :] + np.arange(h)[:, None]) % 2) * 3.0 - 1.5
    speck = rng.normal(0.0, 2.2, (h, w))
    c = base[None, None, :] + (weave + speck)[..., None]
    return c


def entity():
    img = np.zeros((32, 64, 4), np.uint8)
    # The band: 8 wide, 2 high, 8 deep, at (0, 0): its top and bottom in rows 0-7, its four sides in rows 8-9.
    band = cloth(32, 10)
    band[8, :, :] += 12.0  # the top edge catches the light
    band[9, :, :] -= 5.0  # the bottom edge is in shadow
    # The fold where it is pulled tight over the eyes: the front, a little lighter across its middle.
    band[8:10, 8:16, :] += np.array([4.0, 5.0, 8.0])
    img[0:10, 0:32, :3] = np.clip(band, 0, 255)
    img[0:10, 0:32, 3] = 255
    # The knot and the two ends hanging from it, in rows 12-17.
    knot = cloth(16, 6)
    knot[:, 0:6, :] += 6.0
    for x in range(8, 16):
        knot[:, x, :] -= (x % 4 == 0) * 4.0
    img[12:18, 0:16, :3] = np.clip(knot, 0, 255)
    img[12:18, 0:16, 3] = 255
    return Image.fromarray(img, 'RGBA')


def six_eyes():
    """Drawn full bright and added to the face, so black is nothing and light is light: the eyes themselves bright, a
    dimmer glow in the rows over and under them."""
    img = np.zeros((64, 64, 4), np.uint8)
    white = (190, 245, 255, 255)
    iris = (90, 215, 255, 255)
    glow = (20, 70, 110, 255)
    # The face is at (8, 8) to (16, 16); the eyes in its fifth row, as on most skins: white, iris, iris, white.
    for x, colour in ((9, white), (10, iris), (13, iris), (14, white)):
        img[12, x] = colour
        img[11, x] = glow
        img[13, x] = glow
    return Image.fromarray(img, 'RGBA')


def main():
    save(icon(), ROOT, 'textures', 'item', 'blindfold.png')
    save(entity(), CLIENT, 'textures', 'entity', 'blindfold.png')
    save(six_eyes(), CLIENT, 'textures', 'entity', 'six_eyes.png')


if __name__ == '__main__':
    main()
