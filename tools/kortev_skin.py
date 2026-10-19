"""Draws kortev's skin in the Public Safety uniform: py tools/kortev_skin.py SOURCE.png OUT.png [--hood [--under] [--dark]]

With --hood the head is their skin's own, hood up, untouched, and the onesie's top is worn open over the shirt
(--under: under the shirt, only its hood and its neck above the collar showing); without it, the hood is pulled down (below).

His own head, from his skin (SOURCE: his shark onesie, hood up, only his face and fringe showing), with the hood
pulled down: his face and fringe exactly as they were, and the hood become hair in his fringe's own oranges, hanging in
strands round the sides and back on the outer layer.
Under it Public Safety's devil-hunter uniform as Denji wears it in the anime: a white shirt with the sleeves rolled to
the elbow, a black tie, a belt, black trousers and black shoes. And a little of his old skin's style: a shark-tooth
clip on the tie and a wallet chain on his right hip. A 64x64 skin in the game's
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
# His head is his own skin's with the hood pulled down: every pixel of his face and his fringe stays where it is (on
# both layers), and every pixel that was the hood (its blue, its teeth, its eyes and blush) becomes hair, in the
# oranges of his own fringe and nothing else. On the outer layer the hair hangs round the sides and the back in strands
# with jagged ends, the way the e-boy style draws it, ending at the nape.

# His fringe's oranges, from his skin, lightest to darkest.
HAIR_TONES = [hexc('#ffac05'), hexc('#f2a304'), hexc('#e39904'), hexc('#e09704'), hexc('#db9304'), hexc('#c98803'),
              hexc('#b07703')]
# The head's six faces in the skin, for the inner layer (u = 0) and the outer (u = 32): name -> (x, y) of its corner.
FACES = {'top': (8, 0), 'bottom': (16, 0), 'right': (0, 8), 'front': (8, 8), 'left': (16, 8), 'back': (24, 8)}


def his(c):
    """Whether a pixel of his old skin is his own (his face, his hair: warm), not the hood's (blue, white, grey, pink)."""
    r, g, b, a = c
    return a > 0 and r > b + 30 and not (r > 230 and b > 200)


def strands(seed):
    """A hair tone for each pixel of a face: each column a strand of one tone, a step darker every few rows down and
    darkest at its tip, its neighbours a little different; like hair drawn in strands, not noise."""
    local = np.random.default_rng(seed)
    base = local.integers(0, 4, 8)
    return lambda x, y, length=8: HAIR_TONES[min(len(HAIR_TONES) - 1, int(base[x]) + y // 3 + (2 if y >= length - 1 else 0))]


def head(src):
    for i, (name, (fx, fy)) in enumerate(FACES.items()):
        tone = strands(100 + i)
        for y in range(8):
            for x in range(8):
                c = src.getpixel((fx + x, fy + y))
                if name == 'bottom':
                    img[fy + y, fx + x] = SKIN_SHADE
                elif name == 'front' and y >= 2:
                    # His face, exactly as it was: only the hood's brim above it changes.
                    img[fy + y, fx + x] = c
                elif his(c):
                    img[fy + y, fx + x] = c
                else:
                    img[fy + y, fx + x] = tone(x, y)


def hair_layer(src):
    """The outer layer of the head: his fringe's strands where they were, the rest of the hood become hair's bulk."""
    # How far each column's strand hangs (rows from the top), across each face. The sides run from the back of the
    # head to his face on his right (mirrored on his left): long at the back, shorter toward his face, which the
    # strands frame. The back ends at the nape, jagged.
    side = [8, 7, 8, 7, 7, 6, 5, 4]
    lengths = {'right': side, 'left': side[::-1], 'back': [7, 8, 6, 8, 7, 8, 6, 7]}
    for i, (name, (fx, fy)) in enumerate(FACES.items()):
        fx += 32
        tone = strands(200 + i)
        for y in range(8):
            for x in range(8):
                c = src.getpixel((fx + x, fy + y))
                if his(c):
                    img[fy + y, fx + x] = c
                elif name == 'top':
                    # The crown, all but a few gaps that let the hair under it show through.
                    if rng.random() < 0.88:
                        img[fy + y, fx + x] = tone(x, y // 3)
                elif name == 'front':
                    # Over his forehead where the hood's brim and teeth were: his fringe, carried on up to the top.
                    if y < 3 or (y == 3 and c[3] > 0):
                        img[fy + y, fx + x] = tone(x, y)
                elif name in lengths:
                    n = lengths[name][x]
                    if y < n:
                        img[fy + y, fx + x] = tone(x, y, n)


# --- the body ---------------------------------------------------------------------------------------------------------

def shirt_body():
    def front(x0, y0, w, h):
        fill(x0, y0, x0 + w, y0 + h, SHIRT)
        for y in range(h):
            put(x0, y0 + y, SHIRT_SHADE)
            put(x0 + w - 1, y0 + y, SHIRT_SHADE)
        # The tie down the middle, its knot at the collar and its point above the belt. Flat on the shirt: on the
        # outer layer it floats, and on both it shows twice, a little apart.
        put(x0 + 3, y0, TIE_KNOT)
        put(x0 + 4, y0, TIE_KNOT)
        for y in range(1, 9):
            put(x0 + 3, y0 + y, TIE[0])
            put(x0 + 4, y0 + y, TIE[1])
        put(x0 + 3, y0 + 9, TIE[0])
        # A shark tooth clipped to it: one pixel to one side (a bar across cut the tie in two), flat on the tie (on
        # the outer layer it floated).
        put(x0 + 4, y0 + 5, TOOTH)
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


def shirt_layer(hooded=False):
    """The outer layer of the body: the collar standing up round the neck, its points spread."""
    def front(x0, y0, w, h):
        for x in (1, 2, 5, 6):
            put(x0 + x, y0, SHIRT[0])
        put(x0 + 2, y0 + 1, SHIRT_SHADE)
        put(x0 + 5, y0 + 1, SHIRT_SHADE)

    def ring(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])

    def top(x0, y0, w, h):
        for x in range(w):
            put(x0 + x, y0, SHIRT[0])
        for y in range(h):
            put(x0 + 1, y0 + y, SHIRT[1])
            put(x0 + w - 2, y0 + y, SHIRT[1])

    cube(16, 32, 8, 12, 4, {'front': front})
    if hooded:
        # Under the hoodie only the front of the collar shows, between its open edges.
        put(16 + 4 + 1, 36, CLEAR)
        put(16 + 4 + 6, 36, CLEAR)
    else:
        cube(16, 32, 8, 12, 4, {'top': top, 'right': ring, 'left': ring, 'back': ring})


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


def cuff_layer(u, v, fold=SHIRT_SHADE):
    """The rolled cuff standing out from the arm, on the outer layer: its fold (or the hoodie's sleeve bunched above
    it) and the white of the cuff."""
    def face(x0, y0, w, h):
        fill(x0, y0 + 6, x0 + w, y0 + 7, fold)
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


def hood_up(src):
    """Their head exactly as their skin has it, hood up, on both layers."""
    img[0:16, 0:64] = np.array(src)[0:16, 0:64]


def hoodie(src):
    """With the hood up, the hood has to come from something: their onesie's top, unzipped and hanging open over the
    shirt and tie, its hem at the belt. Every pixel of it is the onesie's own (the fin down the back, the fins on the
    arms) except down the front, open on the shirt with the zip split along both edges, and the sleeves, pushed up to
    the elbow over the shirt's rolled cuffs."""
    s = np.array(src)
    zip_, hem = hexc('#c4cdd4'), hexc('#6886a9')
    # The body: top, sides and back whole; the front only at its edges.
    img[16:20, 20:28] = s[16:20, 20:28]
    for x0 in (16, 28, 32):
        img[20:32, x0:x0 + (8 if x0 == 32 else 4)] = s[20:32, x0:x0 + (8 if x0 == 32 else 4)]
    for x in (20, 21, 26, 27):
        img[20:32, x] = s[20:32, x]
    for y in range(20, 32):
        put(21, y, zip_)
        put(26, y, zip_)
    # The ribbed hem round the waist, over the belt (the buckle shows in the opening).
    for x in list(range(16, 22)) + list(range(26, 40)):
        put(x, 31, hem)
    # The fin, standing out from the back as on the onesie.
    img[32:48, 32:40] = np.where(s[32:48, 32:40, 3:] > 0, s[32:48, 32:40], img[32:48, 32:40])
    # The sleeves, to just above the elbow, then bunched; the cuff_layer draws the bunch over the shirt's cuff.
    for u, v, ou, ov in ((40, 16, 40, 32), (32, 48, 48, 48)):
        img[v:v + 4, u + 4:u + 8] = s[v:v + 4, u + 4:u + 8]
        img[v + 4:v + 10, u:u + 16] = s[v + 4:v + 10, u:u + 16]
        fill(u, v + 10, u + 16, v + 11, hem)
        o = s[ov + 4:ov + 10, ou:ou + 16]
        img[ov + 4:ov + 10, ou:ou + 16] = np.where(o[..., 3:] > 0, o, img[ov + 4:ov + 10, ou:ou + 16])


def hoodie_under(src):
    """A lighter way for the hood to come from something: their onesie's top worn under the shirt, so only a little
    of it shows: the hood, and its neck going down into the shirt, in a band under the chin above the collar all the
    way round and a small V at the open collar in front. The tie is tied round the neck, its knot in the V, and the
    collar stands a little out from the shirt where the fabric goes in under it."""
    s = np.array(src)
    blue, shade = tuple(int(c) for c in s[24, 22]), hexc('#6886a9')
    img[16:20, 20:28] = s[16:20, 20:28]  # round the neck, seen from above
    img[32:36, 20:28] = CLEAR
    # Front, inner layer: the band under the chin, dipping into a small V at the open collar, and the tie's knot in
    # the V with its band running up either side of it into the collar, so the tie is tied round the neck rather than
    # hanging from the hood.
    x0, y0, o0 = 20, 20, 36
    for x in range(8):
        put(x0 + x, y0, blue)
        put(x0 + x, y0 + 1, {2: TIE[0], 3: shade, 4: shade, 5: TIE[1]}.get(x, SHIRT[0]))
        put(x0 + x, y0 + 2, TIE_KNOT if x in (3, 4) else SHIRT[0])
    # Front, outer layer: the collar round the V, its points spread either side of the knot.
    for y in range(3):
        for x in range(8):
            put(x0 + x, o0 + y, CLEAR)
    for x, y in ((0, 1), (1, 1), (6, 1), (7, 1), (0, 2), (1, 2), (2, 2), (5, 2), (6, 2), (7, 2)):
        put(x0 + x, o0 + y, SHIRT[0])
    # Sides and back: the hood's fabric over the top two rows, the collar standing out just below it.
    for x in list(range(16, 20)) + list(range(28, 40)):
        put(x, 20, blue)
        put(x, 21, shade)
        put(x, 36, CLEAR)
        put(x, 37, CLEAR)
        put(x, 38, SHIRT[0])
        put(x, 22, SHIRT_SHADE)


def in_black():
    """--dark: the hood and the onesie's other blues (the hood's neck at the collar) in charcoal, to
    sit with the suit's black and white: each blue to a grey of its own lightness, so the hood's shading stays, and
    light enough that its black eyes still show. Its teeth and blush stay as they were."""
    for y in range(64):
        for x in range(64):
            r, g, b, a = (int(c) for c in img[y, x])
            if a and b > r + 15:
                v = max(30, min(100, int(40 + (0.3 * r + 0.59 * g + 0.11 * b - 110) * 0.5)))
                img[y, x] = (v, v, v + 4, a)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    src = Image.open(args[0]).convert('RGBA')
    if '--hood' in sys.argv:
        hood_up(src)
    else:
        head(src)
        hair_layer(src)
    hooded = '--hood' in sys.argv
    under = '--under' in sys.argv
    shirt_body()
    shirt_layer(hooded and not under)
    arm(40, 16)     # right arm
    arm(32, 48)     # left arm
    fold = hexc('#6886a9') if hooded and not under else SHIRT_SHADE
    cuff_layer(40, 32, fold)
    cuff_layer(48, 48, fold)
    leg(0, 16)      # right leg
    leg(16, 48)     # left leg
    wallet_chain()
    if hooded and under:
        hoodie_under(src)
    elif hooded:
        hoodie(src)
    if '--dark' in sys.argv:
        in_black()
    Image.fromarray(img, 'RGBA').save(args[1])
    print('wrote', args[1])


if __name__ == '__main__':
    main()
