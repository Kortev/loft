"""Draws the Public Safety cast as 64 x 64 player skins (wide arms, the game's layout) into tools/skins/public_safety/NAME.png.

    py tools/public_safety_skins.py

The captain and three office staff are our own characters (not from the show). The three suit tiers share one young hunter's
face so the look of the suit is what changes:
  tier_4th      the plain black suit, white shirt and black tie, like the show's hunters;
  tier_2nd      the same under a long dark coat (outer layer: sleeves, body and a skirt over the legs), the collar up;
  tier_special  our own: a black high-collared coat lined crimson, silver epaulettes and two columns of silver buttons, white
                gloves, the emblem on the chest and a crimson armband on the left arm.

Needs Pillow and numpy.
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins', 'public_safety')


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


CLEAR = (0, 0, 0, 0)

# (u, v), (w, h, d) of each box in the skin's layout; the 2 suffix is the outer layer.
PARTS = {
    'head': ((0, 0), (8, 8, 8)), 'head2': ((32, 0), (8, 8, 8)),
    'body': ((16, 16), (8, 12, 4)), 'body2': ((16, 32), (8, 12, 4)),
    'rarm': ((40, 16), (4, 12, 4)), 'rarm2': ((40, 32), (4, 12, 4)),
    'larm': ((32, 48), (4, 12, 4)), 'larm2': ((48, 48), (4, 12, 4)),
    'rleg': ((0, 16), (4, 12, 4)), 'rleg2': ((0, 32), (4, 12, 4)),
    'lleg': ((16, 48), (4, 12, 4)), 'lleg2': ((0, 48), (4, 12, 4)),
}


def face_rect(part, face):
    (u, v), (w, h, d) = PARTS[part]
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}[face]


class Face:
    """One face of one box, in its own pixels as seen from outside (x right, y down)."""

    def __init__(self, skin, part, face):
        self.s = skin
        self.x0, self.y0, self.w, self.h = face_rect(part, face)

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.s.img[self.y0 + y, self.x0 + x] = self.s.pick(c)

    def rect(self, x0, y0, x1, y1, c):
        """Inclusive corners."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def fill(self, c):
        self.rect(0, 0, self.w - 1, self.h - 1, c)

    def get(self, x, y):
        return tuple(self.s.img[self.y0 + y, self.x0 + x])


class Skin:
    def __init__(self, seed):
        self.img = np.zeros((64, 64, 4), np.uint8)
        self.rng = np.random.default_rng(seed)

    def pick(self, c):
        if isinstance(c, list):
            return c[self.rng.integers(len(c))]
        return c

    def f(self, part, face):
        return Face(self, part, face)

    def all_faces(self, part, c):
        for face in ('top', 'bottom', 'right', 'front', 'left', 'back'):
            self.f(part, face).fill(c)

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        Image.fromarray(self.img, 'RGBA').save(os.path.join(OUT, name + '.png'))
        print('wrote', name)


# --- the people -------------------------------------------------------------------------------------------------------

SHIRT = [hexc('#f4f1ea'), hexc('#efebe2')]
SHIRT_SHADE = hexc('#d9d4c6')
SHOE = hexc('#0c0c0e')
SHOE_SHINE = hexc('#32323a')
EYE_WHITE = hexc('#f2f0ea')
MOUTH = hexc('#8a5a52')
SCAR = hexc('#c88a84')
SILVER = hexc('#b9bec8')
SILVER_D = hexc('#7e848f')
CRIMSON = hexc('#8a1c24')
CRIMSON_D = hexc('#5e1218')
CRIMSON_L = hexc('#a82832')
NAVY = hexc('#222a40')


def draw_head(s, p):
    skin = p['skin']
    shade = p['skin_shade']
    hair = p['hair']
    for face in ('right', 'front', 'left', 'back', 'top', 'bottom'):
        s.f('head', face).fill(skin)
    style = p.get('style', 'short')
    ft = s.f('head', 'front')
    # hair on the front: a fringe, the hairline, sideburns
    if style == 'bald':
        pass
    else:
        ft.rect(0, 0, 7, 1, hair)
        for x in (0, 1, 6, 7):
            ft.put(x, 2, hair)
        if style in ('swept', 'spiky'):
            ft.put(2, 2, hair)
            ft.put(5, 2, hair[0] if isinstance(hair, list) else hair)
        if style == 'spiky':
            ft.put(3, 2, hair)
        ft.put(0, 3, hair)
        ft.put(7, 3, hair)
        for face in ('top',):
            s.f('head', face).fill(hair)
        for face, rev in (('right', False), ('left', True)):
            sd = s.f('head', face)
            sd.rect(0, 0, 7, 1, hair)
            back = range(0, 4) if not rev else range(4, 8)
            for x in back:
                for y in range(2, 6 if style != 'spiky' else 5):
                    sd.put(x, y, hair)
            front = 6 if not rev else 1
            sd.put(front, 2, hair)
            sd.put(front + (1 if not rev else -1), 2, hair)
            ear = (4, 5) if not rev else (2, 3)
            for x in ear:
                sd.put(x, 4, shade)
                sd.put(x, 5, shade)
        bk = s.f('head', 'back')
        bk.rect(0, 0, 7, 5, hair)
        bk.rect(1, 6, 6, 6, hair) if style not in ('spiky',) else None
        if style in ('grey_short',):
            for (x, y) in ((0, 3), (7, 3), (1, 2), (6, 2), (0, 2), (7, 2)):
                bk.put(x, y, p['grey'])
            for sd in (s.f('head', 'right'), s.f('head', 'left')):
                for (x, y) in ((6, 2), (6, 3), (7, 3), (1, 2), (1, 3), (0, 3)):
                    sd.put(x, y, p['grey'])
            for (x, y) in ((0, 2), (1, 2), (0, 3), (7, 2), (6, 2), (7, 3)):
                ft.put(x, y, p['grey'])
            for x in (1, 3, 5):
                ft.put(x, 1, p['grey'])
    # the face
    brow = p.get('brow', hair if not isinstance(hair, list) else hair[0])
    ft.rect(1, 3, 2, 3, brow)
    ft.rect(5, 3, 6, 3, brow)
    eye = p.get('eye', hexc('#3a2a20'))
    ft.put(1, 4, EYE_WHITE)
    ft.put(2, 4, eye)
    ft.put(5, 4, eye)
    ft.put(6, 4, EYE_WHITE)
    ft.put(3, 5, shade)
    ft.put(4, 5, shade)
    ft.rect(3, 6, 4, 6, p.get('mouth', MOUTH))
    if p.get('lines'):                         # a veteran's creases beside the nose and mouth
        for (x, y) in ((2, 5), (5, 5), (2, 6), (5, 6)):
            ft.put(x, y, shade)
        ft.put(1, 5, p['skin'][1] if isinstance(p['skin'], list) else p['skin'])
    if p.get('stubble'):
        for (x, y) in ((2, 7), (3, 7), (4, 7), (5, 7), (1, 6), (6, 6)):
            ft.put(x, y, p['stubble'])
    if p.get('scar'):
        for (x, y) in ((7, 1), (6, 2), (6, 3), (6, 4), (5, 5), (5, 6)):
            ft.put(x, y, SCAR)
        ft.put(6, 4, hexc('#e8c4b8'))
    # the outer layer: glasses, a bun
    h2 = s.f('head2', 'front')
    if p.get('glasses'):
        fr = hexc('#1c1c22')
        for x0 in (1, 5):
            h2.rect(x0, 3, x0 + 1, 3, fr)
            h2.put(x0 - 1 if x0 == 1 else x0 + 2, 4, fr)
        h2.put(1, 4, fr)
        h2.put(6, 4, fr)
        h2.rect(3, 3, 4, 3, fr)
        h2.put(0, 3, fr)
        h2.put(7, 3, fr)
        for sd in (s.f('head2', 'right'), s.f('head2', 'left')):
            sd.rect(0, 3, 7, 3, fr)
    if style == 'bun':
        bk2 = s.f('head2', 'back')
        bk2.rect(0, 0, 7, 7, hair)           # long hair down the back of the head to the nape, a pale clip
        bk2.rect(0, 0, 7, 3, hair)
        bk2.put(3, 1, p['clip'])
        bk2.put(4, 1, p['clip'])
        for face in ('right', 'left'):
            sd = s.f('head2', face)
            sd.rect(0 if face == 'right' else 4, 0, 3 if face == 'right' else 7, 5, hair)
        t2 = s.f('head2', 'top')
        t2.rect(2, 4, 5, 7, hair)
        t2.rect(3, 5, 4, 6, [hair[1], hair[1]])
    if style == 'swept':
        t2 = s.f('head2', 'top')
        t2.rect(1, 0, 6, 1, hair)


def draw_body(s, p):
    jacket = p['jacket']
    jshade = p['jacket_shade']
    kind = p.get('kind', 'suit')
    for face in ('top', 'bottom', 'right', 'left'):
        s.f('body', face).fill(jacket if kind == 'suit' else p.get('shirt', SHIRT))
    bf = s.f('body', 'front')
    bb = s.f('body', 'back')
    if kind == 'suit':
        bf.fill(jacket)
        # shirt in a V down to the button, the tie down it
        for y in range(0, 7):
            half = max(0, 3 - (y + 1) // 2)
            for x in range(4 - half, 4 + half):
                bf.put(x, y, p['shirt'])
        for y in range(0, 8):
            bf.rect(3, y, 4, y, p['tie'])
        bf.put(3, 0, hexc('#3a3a46') if p['tie'][0] < 60 else p['tie'])
        for y in range(2, 8):                                  # the lapels' edge, a shade lighter, and the tie's shadow
            hw = max(0, 3 - (y + 1) // 2)
            bf.put(4 - hw - 1, y, shade_c(jacket, 1.35))
            bf.put(4 + hw, y, shade_c(jacket, 1.35))
        bf.put(4, 8, jshade)
        bf.put(4, 10, jshade)
        bf.rect(0, 11, 7, 11, jshade)
        bf.put(0, 0, jshade)
        bf.put(7, 0, jshade)
        if p.get('pocket_square'):
            bf.put(6, 4, hexc('#e4e2dc'))
            bf.put(6, 3, hexc('#c8c6c0'))
        if p.get('pin'):
            bf.put(2, 3, p['pin'])
        if p.get('lanyard'):
            for y in range(0, 5):
                bf.put(2 + (1 if y > 2 else 0), y, p['lanyard'])
                bf.put(5 - (1 if y > 2 else 0), y, p['lanyard'])
            bf.rect(3, 5, 4, 7, hexc('#ece8de'))
            bf.put(3, 6, hexc('#b03238'))
        bb.fill(jacket)
        bb.rect(3, 0, 4, 11, shade_c(jacket, 0.85))
        bb.rect(3, 9, 4, 11, jshade)
        bb.rect(0, 11, 7, 11, jshade)
    else:   # a shirt with a vest or none: the office staff
        bf.fill(p['shirt'])
        bf.rect(3, 0, 4, 1, p['shirt'])
        bf.put(2, 0, SHIRT_SHADE)
        bf.put(5, 0, SHIRT_SHADE)
        if p.get('vest'):
            v = p['vest']
            for y in range(3, 12):
                for x in range(0, 8):
                    inside_gap = 3 <= x <= 4 and y < 8
                    if not inside_gap or y >= 8:
                        bf.put(x, y, v)
            for y in range(2, 8):
                hw = 2 if y > 3 else 1
                bf.put(4 - hw - 1, y, v)
                bf.put(4 + hw, y, v)
            bf.rect(3, 3, 4, 7, p['tie'])
            bf.put(3, 8, shade_c(v, 0.8))
            bf.put(4, 10, shade_c(v, 0.8))
        else:
            bf.rect(3, 1, 4, 6, p['tie'])
            bf.put(3, 0, p['tie'])
            bf.put(4, 0, p['tie'])
            bf.put(4, 1, shade_c(p['tie'], 0.8))
        if p.get('lanyard'):
            for y in range(0, 6):
                bf.put(2 + (1 if y > 3 else 0), y, p['lanyard'])
                bf.put(5 - (1 if y > 3 else 0), y, p['lanyard'])
            bf.rect(3, 6, 4, 8, hexc('#ece8de'))
            bf.put(3, 7, hexc('#b03238'))
        bf.rect(0, 11, 7, 11, shade_c(p.get('vest') or p['shirt'], 0.88))
        bb.fill(p.get('vest') or p['shirt'])
        if not p.get('vest'):
            bb.rect(3, 0, 4, 11, shade_c(p['shirt'][0], 0.96))
        for face in ('right', 'left'):
            s.f('body', face).fill(p.get('vest') or p['shirt'])
        if p.get('vest'):
            for face in ('right', 'left'):
                s.f('body', face).rect(0, 0, 3, 1, p['shirt'])


def shade_c(c, f):
    c = c if not isinstance(c, list) else c[0]
    return (min(255, int(c[0] * f)), min(255, int(c[1] * f)), min(255, int(c[2] * f)), 255)


def draw_arms(s, p):
    for part in ('rarm', 'larm'):
        sleeve = p['jacket'] if p.get('kind', 'suit') == 'suit' else p.get('sleeve', p['shirt'])
        for face in ('right', 'front', 'left', 'back'):
            f = s.f(part, face)
            f.fill(sleeve)
            if p.get('sleeves') == 'rolled':
                f.rect(0, 5, 3, 5, shade_c(p['shirt'], 0.82))
                f.rect(0, 6, 3, 11, p['skin'])
                if face == 'front':
                    f.put(1, 8, p['skin_shade'])
            else:
                f.rect(0, 9, 3, 9, p.get('cuff', SHIRT))
                f.rect(0, 10, 3, 11, p.get('glove', p['skin']))
                if p.get('glove'):
                    f.put(0, 10, shade_c(p['glove'], 0.85))
        s.f(part, 'top').fill(sleeve)
        s.f(part, 'bottom').fill(p.get('glove', p['skin']))
        # a shade down the inner edge
        for face in ('right', 'left'):
            f = s.f(part, face)
            if p.get('sleeves') != 'rolled':
                f.rect(0, 0, 0, 8, shade_c(sleeve, 0.86))


def draw_legs(s, p):
    tr = p['trousers']
    for part in ('rleg', 'lleg'):
        for face in ('right', 'front', 'left', 'back'):
            f = s.f(part, face)
            f.rect(0, 0, 3, 11, tr)
            f.rect(0, 10, 3, 11, SHOE)
            f.put(1, 10, SHOE_SHINE)
            if face in ('front', 'back'):
                f.rect(1, 0, 1, 9, shade_c(tr, 1.18))
        s.f(part, 'top').fill(tr)
        s.f(part, 'bottom').fill(SHOE)


def draw_long_coat(s, c, lapel, lining=None, special=False):
    """The outer layer: sleeves, body, a skirt over the legs. The front stays open down the middle."""
    dark = shade_c(c, 0.8)
    for part in ('rarm2', 'larm2'):
        for face in ('right', 'front', 'left', 'back'):
            f = s.f(part, face)
            f.rect(0, 0, 3, 9, c)
            if face in ('right', 'left'):
                f.rect(0, 0, 0, 9, dark)
            f.rect(0, 9, 3, 9, shade_c(c, 0.7) if not special else CRIMSON_D)
            if special:
                f.rect(0, 8, 3, 8, SILVER_D)
        s.f(part, 'top').fill(c)
    bf = s.f('body2', 'front')
    bf.rect(0, 0, 7, 11, c)
    gap = lining
    for y in range(0, 12):
        for x in (3, 4):
            bf.put(x, y, CLEAR if gap is None else gap)
    for y in range(0, 5):                                  # the lapels turned back
        bf.put(2, y, lapel)
        bf.put(5, y, lapel)
        if y > 0:
            bf.put(3 if y > 2 else 2, y, lapel) if gap is None and False else None
    bf.rect(0, 11, 7, 11, dark)
    if not special:
        for y in (3, 6, 9):
            bf.put(2, y, hexc('#14161c'))
            bf.put(5, y, hexc('#14161c'))
        bf.rect(0, 8, 2, 8, dark)           # a belt, open at the front
        bf.rect(5, 8, 7, 8, dark)
        bf.put(2, 8, hexc('#8a8e98'))
    for face in ('right', 'left'):
        f = s.f('body2', face)
        f.fill(c)
        f.rect(0, 11, 3, 11, dark)
    bk = s.f('body2', 'back')
    bk.fill(c)
    bk.rect(3, 0, 4, 11, dark)
    bk.rect(0, 11, 7, 11, dark)
    for y in (3, 4):
        bk.rect(0, y, 7, y, shade_c(c, 0.92))
    # the collar: turned up round the neck on the top and the first rows of the back and sides
    s.f('body2', 'top').fill(lapel if not special else c)
    if not special:
        bk.rect(0, 0, 7, 1, lapel)
        for face in ('right', 'left'):
            s.f('body2', face).rect(0, 0, 3, 1, lapel)
    for part in ('rleg2', 'lleg2'):
        for face in ('right', 'front', 'left', 'back'):
            f = s.f(part, face)
            f.rect(0, 0, 3, 8, c)
            f.rect(0, 8, 3, 8, dark)
            if special:
                f.rect(0, 7, 3, 7, CRIMSON_D)
            if face == 'front':
                f.rect(1, 0, 1, 6, shade_c(c, 1.12))
                inner = 3 if part == 'rleg2' else 0       # the coat hangs open: the trousers show down the middle
                for y in range(0, 9):
                    f.put(inner, y, CLEAR)
                f.put(2 if part == 'rleg2' else 1, 8, shade_c(c, 0.7))
        s.f(part, 'top').fill(c)


def finish_long_coat(s, c):
    pass


def special_extras(s):
    # the high collar: standing up round the neck on the outer body layer, silver edge on top
    bf = s.f('body2', 'front')
    bf.rect(0, 0, 7, 1, hexc('#14141a'))
    bf.rect(0, 0, 7, 0, SILVER_D)
    bk = s.f('body2', 'back')
    bk.rect(0, 0, 7, 1, hexc('#14141a'))
    bk.rect(0, 0, 7, 0, SILVER_D)
    for face in ('right', 'left'):
        f = s.f('body2', face)
        f.rect(0, 0, 3, 1, hexc('#14141a'))
        f.rect(0, 0, 3, 0, SILVER_D)
    s.f('body2', 'top').fill(hexc('#14141a'))
    # two columns of silver buttons and the crimson lining down the opening
    for y in (3, 6, 9):
        bf.put(1, y, SILVER)
        bf.put(6, y, SILVER)
    for y in range(2, 12):
        bf.put(2, y, shade_c(hexc('#14141a'), 1.3)) if False else None
    # the emblem on the left of the chest (right of the picture): a silver octagon with a red diamond
    for (x, y) in ((5, 3), (6, 3), (5, 4), (6, 4)):
        bf.put(x, y, SILVER)
    bf.put(5, 4, CRIMSON_L)
    bf.put(6, 3, CRIMSON_L)
    # epaulettes: silver bars across the shoulders, and the armband on the left arm
    for part in ('rarm2', 'larm2'):
        s.f(part, 'top').fill(hexc('#14141a'))
        s.f(part, 'top').rect(0, 1, 3, 2, SILVER)
        for face in ('front', 'back', 'left', 'right'):
            f = s.f(part, face)
            f.rect(0, 0, 3, 0, SILVER)
    for face in ('front', 'back', 'left', 'right'):
        f = s.f('larm2', face)
        f.rect(0, 3, 3, 5, CRIMSON)
        f.rect(0, 3, 3, 3, CRIMSON_L)
        f.rect(0, 5, 3, 5, CRIMSON_D)
        if face == 'front':
            f.put(1, 4, hexc('#ece8de'))
            f.put(2, 4, hexc('#ece8de'))


def make(name, seed, p, coat=None):
    s = Skin(seed)
    draw_head(s, p)
    draw_body(s, p)
    draw_arms(s, p)
    draw_legs(s, p)
    if coat == 'long':
        draw_long_coat(s, hexc('#2e3340'), hexc('#4a5164'))
    elif coat == 'special':
        draw_long_coat(s, hexc('#101015'), hexc('#101015'), lining=CRIMSON, special=True)
        special_extras(s)
    s.save(name)
    return s


BLACK = [hexc('#17171c'), hexc('#1a1a20')]
BLACK_SHADE = hexc('#0e0e12')
PANTS = [hexc('#1a1a20'), hexc('#1d1d24')]

PEOPLE = {
    # our own captain: a veteran. Grey at the temples, a scar through the left brow, creases, a silver tie bar and a pin.
    'captain': dict(skin=[hexc('#cfa584'), hexc('#c99f7e')], skin_shade=hexc('#b48a6c'), hair=[hexc('#26211e'), hexc('#2d2723')],
                    grey=hexc('#8a8a8c'), style='grey_short', scar=True, lines=True, stubble=hexc('#b8957a'), jacket=[hexc('#202027'), hexc('#24242c')],
                    jacket_shade=hexc('#121216'), shirt=SHIRT, tie=hexc('#262c42'), pocket_square=True, pin=hexc('#c33a40'),
                    trousers=[hexc('#202027'), hexc('#24242c')], eye=hexc('#3a2e26')),
    'clerk_woman': dict(skin=[hexc('#e4c2a8'), hexc('#dfbca2')], skin_shade=hexc('#c9a088'), hair=[hexc('#1b1613'), hexc('#241d19')],
                        style='bun', clip=hexc('#c9a24a'), kind='shirt', shirt=SHIRT, vest=hexc('#2d3550'), tie=hexc('#8a1c24'), lanyard=hexc('#3c8a9c'),
                        jacket=hexc('#2d3550'), jacket_shade=hexc('#1c2236'), trousers=[hexc('#23283a'), hexc('#272d40')], mouth=hexc('#a8584e'),
                        eye=hexc('#3a2a20'), sleeve=SHIRT, cuff=SHIRT),
    'clerk_man': dict(skin=[hexc('#d9b394'), hexc('#d3ac8c')], skin_shade=hexc('#bd9577'), hair=[hexc('#4a3322'), hexc('#533a27')],
                      style='swept', glasses=True, kind='shirt', shirt=[hexc('#aebfd6'), hexc('#a6b8cf')], tie=hexc('#6a1a22'), lanyard=hexc('#3c8a9c'),
                      sleeves='rolled', jacket=hexc('#aebfd6'), jacket_shade=hexc('#8a9bb2'), trousers=[hexc('#4a4640'), hexc('#4f4b44')],
                      eye=hexc('#2a2a2a'), sleeve=hexc('#aebfd6')),
    'hunter_rookie': dict(skin=[hexc('#e0bca0'), hexc('#d9b496')], skin_shade=hexc('#c4a084'), hair=[hexc('#17120f'), hexc('#1d1713')],
                          style='spiky', jacket=BLACK, jacket_shade=BLACK_SHADE, shirt=SHIRT, tie=hexc('#17171c'), trousers=PANTS, eye=hexc('#2c2018')),
}
TIER_FACE = dict(skin=[hexc('#d9b294'), hexc('#d3ab8c')], skin_shade=hexc('#bd9577'), hair=[hexc('#3a281c'), hexc('#43301f')], style='swept',
                 jacket=BLACK, jacket_shade=BLACK_SHADE, shirt=SHIRT, tie=hexc('#15151a'), trousers=PANTS, eye=hexc('#35261c'))


def hunter_sword_harness(s):
    """The rookie's katana carried across the back: a diagonal strap on the outer layer and a pale grip over the shoulder."""
    bk = s.f('body2', 'back')
    for i in range(12):
        bk.put(1 + i // 2, i, hexc('#3b2a1c'))
    bf = s.f('body2', 'front')
    for i in range(10):
        bf.put(6 - i // 2, i, hexc('#3b2a1c'))
    bk.put(6, 0, hexc('#2c2c3a'))
    bk.put(6, 1, hexc('#d6d8e2'))
    bk.put(7, 0, hexc('#2c2c3a'))
    s.f('body2', 'top').rect(5, 2, 6, 3, hexc('#2c2c3a'))


def main():
    seed = 40
    for name, p in PEOPLE.items():
        seed += 1
        s = make(name, seed, p)
        if name == 'hunter_rookie':
            hunter_sword_harness(s)
            s.save(name)
    for name, coat in (('tier_4th', None), ('tier_2nd', 'long'), ('tier_special', 'special')):
        p = dict(TIER_FACE)
        if coat == 'special':
            p['glove'] = hexc('#ece8e0')
        seed += 1
        make(name, seed, p, coat)


if __name__ == '__main__':
    main()
