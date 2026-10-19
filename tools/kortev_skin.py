"""Draws kortev's skin in the Public Safety uniform: py tools/kortev_skin.py SOURCE.png OUT.png

His own face, from his skin (SOURCE: his shark onesie, hood up, only his face and fringe showing), with the hood gone
and his hair as he has it: orange and fluffy, messy, an e-boy cut with a mullet (the outer layer gives it its bulk).
Under it Public Safety's devil-hunter uniform as Denji wears it in the anime: a white shirt with the sleeves rolled to
the elbow, a black tie, a belt, black trousers and black shoes. And a little of his old skin's style: a shark-tooth
clip on the tie, a silver hoop in his right ear, and a wallet chain on his right hip. A 64x64 skin in the game's
layout, wide arms. Needs Pillow and numpy.
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
TOOTH = hexc('#f6f4ee')

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
#
# His hair is drawn pixel by pixel, a map for each face of the head (8 x 8, the inner layer and the outer): orange and
# fluffy, messy, an e-boy cut: parted in the middle with curtain bangs falling either side of his face, full over the
# ears, and a mullet down the back of his neck and over his collar. Letters are colours (HAIR_KEY), '.' is bare (the
# outer layer) or his face as it was (the front of the inner layer's lower rows, 'x').

HAIR_KEY = {'L': hexc('#ffb35a'), 'O': hexc('#f58a1f'), 'M': hexc('#e2761a'), 'D': hexc('#b95a12'), 'K': hexc('#86400c'),
            's': hexc('#d8b49d'), 'S': hexc('#c9a28a'), 'r': hexc('#b9b9bd')}

# The inner layer. The top's last row meets the front's top; the sides run from the back (left) to the face (right) on
# his right side, mirrored on his left.
HEAD_TOP = ['OMOLOMOO',
            'MOLOOMLO',
            'OOMOLOOM',
            'LOOMOOLO',
            'OMOODOMO',
            'OOLDDLOO',
            'MOODDOOM',
            'OLMDDMLO']
HEAD_FRONT = ['OLOOOOLO',
              'OOODDOOO',
              'OOMssMOO',
              'MOssssOM',
              'xxxxxxxx',
              'xxxxxxxx',
              'xxxxxxxx',
              'xxxxxxxx']
HEAD_SIDE = ['OOLOOMOO',
             'MOOLOOMO',
             'OMOOMOOO',
             'DOMOOLOM',
             'DMOOMOsS',
             'DDMOOsss',
             'KDDMsrss',
             'KKDDssss']
HEAD_BACK = ['OMOLOMOO',
             'MOOMOOLO',
             'OLMOOMOO',
             'MOOMLOOM',
             'DMOOMODM',
             'DDMOODDD',
             'KDDMDDKD',
             'KKDDDDKK']

# The outer layer: the hair's bulk, standing off the head.
HAT_TOP = ['LOMOOLOM',
           'O.OLMO.O',
           'MOLOO.LO',
           'OO.MOLOO',
           'L.OOMO.L',
           'OLO..OLO',
           'MOL..LOM',
           'OM....MO']
HAT_FRONT = ['OOLOOLOO',
             'OLMO.LOO',
             'OMO...MO',
             'MOD...OM',
             'M......M',
             'D......D',
             'D......D',
             '........']
HAT_SIDE = ['OOLOOMOO',
            'OMOOLOOO',
            'LOOMOOMO',
            'OOMOLOOM',
            'MOOMOOM.',
            'DMOOMD..',
            'DDMDD...',
            'KDDD....']
HAT_BACK = ['OLOOMOLO',
            'OOMOLOOM',
            'MOLOOMOO',
            'OOMLOOMO',
            'DMOOMODM',
            'DDMODMDD',
            'KDDMDDKD',
            'KDDKDDKK']
# The mullet going on down over his collar (the outer layer of the body's back, its top rows).
MULLET = ['.DMDDMD.',
          '..DKDD..',
          '...K.K..']


def paint(x0, y0, rows, mirror=False, under=None):
    """Paints a face from its map; 'x' takes {@code under}'s pixel there (his own face)."""
    for y, row in enumerate(rows):
        row = row[::-1] if mirror else row
        for x, c in enumerate(row):
            if c == '.':
                continue
            if c == 'x':
                img[y0 + y, x0 + x] = under.getpixel((8 + x, 8 + y))
            else:
                img[y0 + y, x0 + x] = HAIR_KEY[c]


def head(src):
    def bottom(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SKIN_SHADE)

    cube(0, 0, 8, 8, 8, {'top': lambda x0, y0, w, h: paint(x0, y0, HEAD_TOP),
                         'bottom': bottom,
                         'front': lambda x0, y0, w, h: paint(x0, y0, HEAD_FRONT, under=src),
                         'right': lambda x0, y0, w, h: paint(x0, y0, HEAD_SIDE),
                         'left': lambda x0, y0, w, h: paint(x0, y0, HEAD_SIDE, mirror=True),
                         'back': lambda x0, y0, w, h: paint(x0, y0, HEAD_BACK)})
    put(16 + 2, 8 + 6, HAIR_KEY['s'])
    # The sides of his face under the curtain bangs: hair, not the hood's dark edge.
    for y in (4, 5, 6):
        put(8, 8 + y, HAIR_KEY['D'])
        put(15, 8 + y, HAIR_KEY['D'])


def hair_layer():
    """The outer layer of the head: the hair's bulk."""
    cube(32, 0, 8, 8, 8, {'top': lambda x0, y0, w, h: paint(x0, y0, HAT_TOP),
                          'front': lambda x0, y0, w, h: paint(x0, y0, HAT_FRONT),
                          'right': lambda x0, y0, w, h: paint(x0, y0, HAT_SIDE),
                          'left': lambda x0, y0, w, h: paint(x0, y0, HAT_SIDE, mirror=True),
                          'back': lambda x0, y0, w, h: paint(x0, y0, HAT_BACK)})


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
    out from the shirt with its shark-tooth clip, and his mullet falling over the collar at the back."""
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
        # A shark tooth clipped to the tie.
        put(x0 + 3, y0 + 4, BUCKLE[1])
        put(x0 + 4, y0 + 4, BUCKLE[0])
        put(x0 + 4, y0 + 5, TOOTH)

    def back(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])
        paint(x0, y0, MULLET)

    def ring(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])

    def top(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])
        for y in range(h):
            put(x0 + 1, y0 + y, SHIRT[1])
            put(x0 + w - 2, y0 + y, SHIRT[1])

    cube(16, 32, 8, 12, 4, {'top': top, 'front': front, 'right': ring, 'left': ring, 'back': back})


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


def wallet_chain():
    """A silver chain from the belt, hanging in a loop down the outside of his right leg and back up behind, on the
    outer layer of the leg."""
    loop = [(3, 0), (2, 1), (1, 2), (1, 3), (2, 4), (3, 5)]
    x0, y0 = 0, 36  # the right leg's outer layer, its outside face
    for i, (x, y) in enumerate(loop):
        put(x0 + x, y0 + y, BUCKLE[i % 2])
    for x, y in ((0, 5), (1, 4), (2, 3), (3, 2)):  # on round the back of the leg
        put(12 + x, 36 + y, BUCKLE[(x + y) % 2])


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
    wallet_chain()
    Image.fromarray(img, 'RGBA').save(sys.argv[2])
    print('wrote', sys.argv[2])


if __name__ == '__main__':
    main()
