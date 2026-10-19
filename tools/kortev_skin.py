"""Draws kortev's skin in the Public Safety uniform: py tools/kortev_skin.py SOURCE.png OUT.png

His own face and hair, from his skin (SOURCE: his shark onesie, hood up, only his face and orange-blond fringe
showing): the hood is gone and his hair is all there, the colours of his fringe, messy, with its fringe and spikes on
the outer layer. Under it Public Safety's devil-hunter uniform as Denji wears it in the anime: a white shirt with the
sleeves rolled to the elbow, a black tie, a belt, black trousers and black shoes. A 64x64 skin in the game's layout,
wide arms. Needs Pillow and numpy.
"""
import sys

import numpy as np
from PIL import Image

rng = np.random.default_rng(1124)


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


CLEAR = (0, 0, 0, 0)
# His hair, from his fringe: bright, middle, the shadowed and the darkest.
HAIR = [hexc('#ffac05'), hexc('#f2a304'), hexc('#e09704')]
HAIR_SHADE = [hexc('#c98803'), hexc('#b07703')]
SKIN = [hexc('#d8b49d'), hexc('#dbbea9')]
SKIN_SHADE = hexc('#c9a28a')
EAR = hexc('#d4ac9d')
# The shirt: white, a little warm, shaded in its folds.
SHIRT = [hexc('#f4f1ea'), hexc('#efebe2')]
SHIRT_SHADE = hexc('#e2ddd1')
SHIRT_DEEP = hexc('#cdc7b8')
TIE = [hexc('#1b1b20'), hexc('#24242b')]
TIE_KNOT = hexc('#33333b')
BELT = hexc('#121214')
BUCKLE = [hexc('#8e8e92'), hexc('#b9b9bd')]
PANTS = [hexc('#1d1d23'), hexc('#22222a')]
PANTS_SHADE = hexc('#141418')
PANTS_CREASE = hexc('#2c2c35')
SHOE = hexc('#0c0c0e')
SHOE_SHINE = hexc('#2a2a30')

img = np.zeros((64, 64, 4), np.uint8)


def put(x, y, c):
    img[y, x] = c


def fill(x0, y0, x1, y1, palette):
    """A rectangle in a palette's colours, scattered (a painter's noise); one colour fills it flat."""
    palette = palette if isinstance(palette, list) else [palette]
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y, x] = palette[rng.integers(len(palette))]


def cube(u, v, w, h, d, faces):
    """Paints a box's six faces in the skin's layout, from the corner (u, v): top and bottom across the first d rows,
    then right, front, left and back in a band below them. {@code faces} maps each face's name to a function of
    (x0, y0, width, height) that paints it."""
    rects = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
             'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}
    for name, paint in faces.items():
        paint(*rects[name])


# --- the head ---------------------------------------------------------------------------------------------------------

def head(src):
    def top(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, HAIR)

    def bottom(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SKIN_SHADE)

    def front(x0, y0, w, h):
        # His own face, the hood's rows above it turned to hair.
        for y in range(h):
            for x in range(w):
                img[y0 + y, x0 + x] = src.getpixel((8 + x, 8 + y))
        fill(x0, y0, x0 + w, y0 + 2, HAIR)

    def side(x0, y0, w, h, toward_front):
        # Hair over the top and the back, the ear and cheek bare toward the face.
        bare = {3: 2, 4: 3, 5: 5, 6: 6, 7: 7}
        for y in range(h):
            for x in range(w):
                f = x if toward_front > 0 else w - 1 - x  # 0 at the back edge, w - 1 at the front edge
                skin_from = w - bare.get(y, 0)
                if y >= 3 and f >= skin_from and not (y in (3, 4) and f == w - 1):
                    img[y0 + y, x0 + x] = SKIN[rng.integers(2)]
                else:
                    img[y0 + y, x0 + x] = HAIR[rng.integers(3)] if f > 2 or y < 2 else HAIR_SHADE[rng.integers(2)]
        ear_x = x0 + (w - 4 if toward_front > 0 else 3)
        put(ear_x, y0 + 5, EAR)
        put(ear_x, y0 + 6, EAR)

    def back(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + 6, HAIR + HAIR_SHADE[:1])
        fill(x0, y0 + 6, x0 + w, y0 + 7, HAIR_SHADE)
        fill(x0, y0 + 7, x0 + w, y0 + 8, SKIN)
        for x in (1, 4, 6):
            put(x0 + x, y0 + 7, HAIR_SHADE[0])

    cube(0, 0, 8, 8, 8, {'top': top, 'bottom': bottom, 'front': front, 'right': lambda *r: side(*r, toward_front=1),
                         'left': lambda *r: side(*r, toward_front=-1), 'back': back})


def hair_layer():
    """The outer layer of the head: his hair's bulk, messy, the fringe hanging in strands over his forehead."""
    def top(x0, y0, w, h):
        for y in range(h):
            for x in range(w):
                if rng.random() < 0.78:
                    put(x0 + x, y0 + y, HAIR[rng.integers(3)])

    def front(x0, y0, w, h):
        rows = ['########', '########', '########', '#.####.#', '#..##..#', '#...#..#']
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c == '#':
                    put(x0 + x, y0 + y, HAIR[rng.integers(3)] if y < 4 else HAIR[2])

    def side(x0, y0, w, h, toward_front):
        # How far forward the hair comes on each row (from the back edge); ragged at its ends.
        reach = [8, 8, 8, 6, 4, 3, 2, 0]
        for y in range(h):
            for f in range(reach[y]):
                if f == reach[y] - 1 and rng.random() < 0.4:
                    continue
                x = f if toward_front < 0 else w - 1 - f
                x = w - 1 - x
                put(x0 + x, y0 + y, HAIR[rng.integers(3)] if y < 4 else HAIR_SHADE[rng.integers(2)])

    def back(x0, y0, w, h):
        for y in range(7):
            for x in range(w):
                put(x0 + x, y0 + y, HAIR[rng.integers(3)] if y < 5 else HAIR_SHADE[rng.integers(2)])
        for x in (0, 2, 3, 5, 7):
            put(x0 + x, y0 + 7, HAIR_SHADE[1])

    cube(32, 0, 8, 8, 8, {'top': top, 'front': front, 'right': lambda *r: side(*r, toward_front=1),
                          'left': lambda *r: side(*r, toward_front=-1), 'back': back})


# --- the body ---------------------------------------------------------------------------------------------------------

def shirt_body():
    def front(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHIRT)
        for y in range(h):
            put(x0, y0 + y, SHIRT_SHADE)
            put(x0 + w - 1, y0 + y, SHIRT_SHADE)
        # The tie down the middle, its knot at the collar and its point above the belt.
        put(x0 + 3, y0, TIE_KNOT)
        put(x0 + 4, y0, TIE_KNOT)
        for y in range(1, 9):
            put(x0 + 3, y0 + y, TIE[0])
            put(x0 + 4, y0 + y, TIE[1])
        put(x0 + 3, y0 + 9, TIE[0])
        # Tucked in: blousing over the belt, then the belt and its buckle.
        fill(x0, y0 + 10, x0 + w, y0 + 11, SHIRT_SHADE)
        put(x0 + 2, y0 + 10, SHIRT_DEEP)
        put(x0 + 5, y0 + 10, SHIRT_DEEP)
        fill(x0, y0 + 11, x0 + w, y0 + 12, BELT)
        put(x0 + 3, y0 + 11, BUCKLE[1])
        put(x0 + 4, y0 + 11, BUCKLE[0])
        # A fold or two.
        put(x0 + 1, y0 + 5, SHIRT_SHADE)
        put(x0 + 6, y0 + 7, SHIRT_SHADE)

    def side(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHIRT)
        fill(x0, y0 + 10, x0 + w, y0 + 11, SHIRT_SHADE)
        fill(x0, y0 + 11, x0 + w, y0 + 12, BELT)
        put(x0 + 1, y0 + 4, SHIRT_SHADE)

    def back(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHIRT)
        for y in range(1, 10):
            put(x0 + 3 + (y % 2), y0 + y, SHIRT_SHADE)
        fill(x0, y0 + 10, x0 + w, y0 + 11, SHIRT_SHADE)
        fill(x0, y0 + 11, x0 + w, y0 + 12, BELT)

    def top(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHIRT)

    def bottom(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, PANTS)

    cube(16, 16, 8, 12, 4, {'top': top, 'bottom': bottom, 'front': front, 'right': side, 'left': side, 'back': back})


def shirt_layer():
    """The outer layer of the body: the collar standing up round the neck, its points spread, and the tie standing
    out from the shirt."""
    def front(x0, y0, w, h):
        for x in (1, 2, 5, 6):
            put(x0 + x, y0, SHIRT[0])
        put(x0 + 2, y0 + 1, SHIRT_SHADE)
        put(x0 + 5, y0 + 1, SHIRT_SHADE)
        put(x0 + 3, y0, TIE_KNOT)
        put(x0 + 4, y0, TIE_KNOT)
        for y in range(1, 9):
            put(x0 + 3, y0 + y, TIE[1])
            put(x0 + 4, y0 + y, TIE[0])

    def ring(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])

    def top(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])
        for y in range(h):
            put(x0 + 1, y0 + y, SHIRT[1])
            put(x0 + w - 2, y0 + y, SHIRT[1])

    cube(16, 32, 8, 12, 4, {'top': top, 'front': front, 'right': ring, 'left': ring, 'back': ring})


def arm(u, v):
    """An arm: the sleeve to the elbow, rolled up in a cuff, and the bare forearm and hand below it."""
    def face(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + 6, SHIRT)
        put(x0, y0 + 2, SHIRT_SHADE)
        put(x0 + w - 1, y0 + 4, SHIRT_SHADE)
        fill(x0, y0 + 6, x0 + w, y0 + 7, SHIRT_SHADE)
        fill(x0, y0 + 7, x0 + w, y0 + 8, SHIRT[0])
        fill(x0, y0 + 8, x0 + w, y0 + h, SKIN)
        fill(x0, y0 + h - 1, x0 + w, y0 + h, SKIN_SHADE)

    def top(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHIRT)

    def bottom(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SKIN_SHADE)

    cube(u, v, 4, 12, 4, {'top': top, 'bottom': bottom, 'front': face, 'right': face, 'left': face, 'back': face})


def cuff_layer(u, v):
    """The rolled cuff standing out from the arm, on the outer layer."""
    def face(x0, y0, w, h):
        fill(x0, y0 + 6, x0 + w, y0 + 7, SHIRT_SHADE)
        fill(x0, y0 + 7, x0 + w, y0 + 8, SHIRT[0])

    cube(u, v, 4, 12, 4, {'front': face, 'right': face, 'left': face, 'back': face})


def leg(u, v):
    """A leg: black trousers with a crease down the front, and a black shoe."""
    def front(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + 10, PANTS)
        for y in range(10):
            put(x0 + 1, y0 + y, PANTS_CREASE)
        fill(x0, y0 + 10, x0 + w, y0 + 12, SHOE)
        fill(x0, y0 + 10, x0 + w, y0 + 11, SHOE_SHINE)

    def side(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + 10, PANTS)
        fill(x0, y0 + 9, x0 + w, y0 + 10, PANTS_SHADE)
        fill(x0, y0 + 10, x0 + w, y0 + 12, SHOE)
        put(x0 + 1, y0 + 10, SHOE_SHINE)

    def top(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, PANTS)

    def bottom(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHOE)

    cube(u, v, 4, 12, 4, {'top': top, 'bottom': bottom, 'front': front, 'right': side, 'left': side, 'back': side})


def main():
    src = Image.open(sys.argv[1]).convert('RGBA')
    head(src)
    hair_layer()
    shirt_body()
    shirt_layer()
    arm(40, 16)     # right arm
    arm(32, 48)     # left arm
    cuff_layer(40, 32)
    cuff_layer(48, 48)
    leg(0, 16)      # right leg
    leg(16, 48)     # left leg
    Image.fromarray(img, 'RGBA').save(sys.argv[2])
    print('wrote', sys.argv[2])


if __name__ == '__main__':
    main()
