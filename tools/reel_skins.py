"""Draws the cast of the first reel (episode 1, recreated in the mod): the yakuza who take Denji to the job, in black suits,
their boss in a white one, and each of them again as the Zombie Devil's zombies (grey, eyes gone white, blood from the
mouth down the shirt). 64x64 skins in the game's layout, wide arms, into tools/skins/reel/NAME.png; the reel's self test
copies them into its run folder's skins/ (gradle seedReelSkins), where FilmSkins finds them by the stand-in's name.

    py tools/reel_skins.py

Needs Pillow and numpy.
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins', 'reel')


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


CLEAR = (0, 0, 0, 0)
SHIRT = [hexc('#f1eee7'), hexc('#e9e5dc')]
BLACK_SUIT = [hexc('#17171c'), hexc('#1c1c22')]
BLACK_SUIT_SHADE = hexc('#0f0f13')
WHITE_SUIT = [hexc('#e8e4da'), hexc('#ded9cc')]
WHITE_SUIT_SHADE = hexc('#c9c3b4')
DARK_SHIRT = [hexc('#2a2026'), hexc('#30252c')]
TIE_RED = [hexc('#6a1418'), hexc('#5a1014')]
TIE_GOLD = [hexc('#b88a2c'), hexc('#a77a22')]
SHOE = hexc('#0b0b0d')
SHOE_SHINE = hexc('#2a2a30')
WHITE_EYE = hexc('#f2f2ee')
PUPIL = hexc('#1d1712')
MOUTH = hexc('#7a4a44')
SHADES = hexc('#08080a')
SHADES_GLINT = hexc('#3a3a46')
BLOOD = [hexc('#7a0c12'), hexc('#8e1016'), hexc('#5e070c')]
ZOMBIE_SKIN = [hexc('#8f9a88'), hexc('#87927f'), hexc('#959f8c')]
ZOMBIE_SHADE = hexc('#6f7a6a')
ZOMBIE_ROT = hexc('#5f6a55')
ZOMBIE_MOUTH = hexc('#2a0a0c')


class Skin:
    def __init__(self, seed):
        self.img = np.zeros((64, 64, 4), dtype=np.uint8)
        self.rng = np.random.default_rng(seed)

    def put(self, x, y, c):
        self.img[y, x] = c

    def fill(self, x0, y0, x1, y1, palette):
        palette = palette if isinstance(palette, list) else [palette]
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.img[y, x] = palette[self.rng.integers(len(palette))]

    def cube(self, u, v, w, h, d, faces):
        rects = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
                 'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}
        for name, paint in faces.items():
            paint(*rects[name])

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        Image.fromarray(self.img, 'RGBA').save(os.path.join(OUT, name + '.png'))
        print('wrote', name)


def person(name, seed, skin, skin_shade, hair, suit, suit_shade, shirt, tie, *, style='short', shades=False, zombie=False):
    """One of them: a head (hair by style, sunglasses or not), a suit jacket over a shirt and tie, trousers and shoes.
    A zombie: eyes gone white, the mouth hanging open and dark, blood from it down the chin and the shirt, rot on the
    skin, a sleeve torn."""
    s = Skin(seed)
    skin_pal = ZOMBIE_SKIN if zombie else skin
    shade = ZOMBIE_SHADE if zombie else skin_shade

    def flat(c):
        return lambda x0, y0, w, h: s.fill(x0, y0, x0 + w, y0 + h, c)

    # The head.
    def face(x0, y0, w, h):
        s.fill(x0, y0, x0 + 8, y0 + 8, skin_pal)
        if style != 'bald':
            s.fill(x0, y0, x0 + 8, y0 + (2 if style != 'slick' else 1), hair)
            s.put(x0, y0 + 2, hair[0])
            s.put(x0 + 7, y0 + 2, hair[0])
            if style == 'slick':
                s.fill(x0, y0 + 1, x0 + 8, y0 + 2, hair)
        if shades and not zombie:
            s.fill(x0 + 1, y0 + 3, x0 + 7, y0 + 5, SHADES)
            s.put(x0 + 2, y0 + 3, SHADES_GLINT)
            s.put(x0 + 5, y0 + 3, SHADES_GLINT)
        elif zombie:
            for ex in (x0 + 1, x0 + 5):
                s.put(ex, y0 + 4, WHITE_EYE)
                s.put(ex + 1, y0 + 4, WHITE_EYE)
                s.put(ex, y0 + 3, ZOMBIE_SHADE)
                s.put(ex + 1, y0 + 3, ZOMBIE_SHADE)
        else:
            for ex in (x0 + 1, x0 + 5):
                s.put(ex, y0 + 4, WHITE_EYE)
                s.put(ex + 1, y0 + 4, PUPIL)
        if zombie:
            s.fill(x0 + 2, y0 + 6, x0 + 6, y0 + 8, ZOMBIE_MOUTH)
            s.put(x0 + 3, y0 + 6, WHITE_EYE)
            s.put(x0 + 4, y0 + 7, BLOOD[0])
            s.put(x0 + 2, y0 + 7, BLOOD[1])
            s.put(x0 + 6, y0 + 2, ZOMBIE_ROT)
            s.put(x0 + 1, y0 + 6, ZOMBIE_ROT)
        else:
            s.fill(x0 + 3, y0 + 6, x0 + 5, y0 + 7, MOUTH)

    def head_side(x0, y0, w, h):
        s.fill(x0, y0, x0 + w, y0 + h, skin_pal)
        if style != 'bald':
            s.fill(x0, y0, x0 + w, y0 + 3, hair)
            s.fill(x0 + w - 3, y0 + 3, x0 + w, y0 + 6, hair)
        if shades and not zombie:
            s.fill(x0, y0 + 3, x0 + 2, y0 + 4, SHADES)

    def head_back(x0, y0, w, h):
        s.fill(x0, y0, x0 + w, y0 + h, skin_pal)
        if style != 'bald':
            s.fill(x0, y0, x0 + w, y0 + 6, hair)

    def head_top(x0, y0, w, h):
        s.fill(x0, y0, x0 + w, y0 + h, hair if style != 'bald' else skin_pal)

    # (The left side mirrors the right: its hair at the back is at the other end.)
    def left_side(x0, y0, w, h):
        s.fill(x0, y0, x0 + w, y0 + h, skin_pal)
        if style != 'bald':
            s.fill(x0, y0, x0 + w, y0 + 3, hair)
            s.fill(x0, y0 + 3, x0 + 3, y0 + 6, hair)
        if shades and not zombie:
            s.fill(x0 + w - 2, y0 + 3, x0 + w, y0 + 4, SHADES)

    s.cube(0, 0, 8, 8, 8, {'top': head_top, 'bottom': flat(skin_pal), 'right': head_side, 'front': face, 'left': left_side,
                           'back': head_back})

    # The body: the jacket open over the shirt and tie, a button, the shirt at the bottom of the V.
    def body_front(x0, y0, w, h):
        s.fill(x0, y0, x0 + 8, y0 + 12, suit)
        for y in range(0, 7):
            half = max(0, 3 - y // 2)
            for x in range(4 - half - 1, 4 + half + 1):
                if 0 <= x < 8:
                    s.put(x0 + x, y0 + y, shirt[0] if x not in (3, 4) else tie[0])
        s.fill(x0 + 3, y0 + 0, x0 + 5, y0 + 6, tie)
        s.put(x0 + 3, y0 + 0, tie[1])
        s.put(x0 + 4, y0 + 7, suit_shade)
        s.put(x0 + 4, y0 + 9, suit_shade)
        s.fill(x0, y0 + 11, x0 + 8, y0 + 12, suit_shade)
        if zombie:
            for (x, y) in ((3, 0), (4, 1), (3, 2), (4, 3), (2, 4), (5, 2), (3, 5)):
                s.put(x0 + x, y0 + y, BLOOD[(x + y) % 3])

    s.cube(16, 16, 8, 12, 4, {'top': flat(suit), 'bottom': flat(suit_shade), 'right': flat(suit), 'front': body_front, 'left': flat(suit),
                              'back': flat(suit)})

    # The arms: the jacket's sleeves, a shirt cuff, the hands.
    def sleeve(torn):
        def paint(x0, y0, w, h):
            s.fill(x0, y0, x0 + w, y0 + h, suit)
            s.fill(x0, y0 + 9, x0 + w, y0 + 10, shirt)
            s.fill(x0, y0 + 10, x0 + w, y0 + 12, skin_pal)
            if torn:
                s.fill(x0, y0 + 5, x0 + w, y0 + 7, skin_pal)
                s.put(x0 + 1, y0 + 6, BLOOD[0])
        return paint

    def hand_end(x0, y0, w, h):
        s.fill(x0, y0, x0 + w, y0 + h, skin_pal)

    arm = {'top': flat(suit), 'bottom': hand_end}
    for f in ('right', 'front', 'left', 'back'):
        arm[f] = sleeve(False)
    s.cube(40, 16, 4, 12, 4, arm)
    arm2 = dict(arm)
    for f in ('right', 'front', 'left', 'back'):
        arm2[f] = sleeve(zombie)
    s.cube(32, 48, 4, 12, 4, arm2)

    # The legs: trousers, shoes.
    def trouser(x0, y0, w, h):
        s.fill(x0, y0, x0 + w, y0 + h, suit)
        s.fill(x0, y0 + 10, x0 + w, y0 + 12, SHOE)
        s.put(x0 + 1, y0 + 10, SHOE_SHINE)

    leg = {'top': flat(suit), 'bottom': flat(SHOE), 'right': trouser, 'front': trouser, 'left': trouser, 'back': trouser}
    s.cube(0, 16, 4, 12, 4, leg)
    s.cube(16, 48, 4, 12, 4, leg)
    s.save(name)


SKINS = [
    # name, seed, skin, shade, hair, style, shades
    ('Yakuza1', 11, [hexc('#d9b294'), hexc('#d3ab8c')], hexc('#c09a7c'), [hexc('#1b1714'), hexc('#221d19')], 'short', False),
    ('Yakuza2', 12, [hexc('#c99f80'), hexc('#c39878')], hexc('#ad866a'), [hexc('#3a2a1c'), hexc('#43321f')], 'slick', True),
    ('Yakuza3', 13, [hexc('#e0bca0'), hexc('#d9b496')], hexc('#c7a283'), [hexc('#c9a66b'), hexc('#bf9b5f')], 'short', False),
    ('Yakuza4', 14, [hexc('#cfa585'), hexc('#c89d7c')], hexc('#b58d6e'), [hexc('#121012'), hexc('#191619')], 'bald', False),
]
for name, seed, skin, shade, hair, style, shades in SKINS:
    person(name, seed, skin, shade, hair, BLACK_SUIT, BLACK_SUIT_SHADE, SHIRT, TIE_RED, style=style, shades=shades)
    person('Z' + name, seed + 100, skin, shade, hair, BLACK_SUIT, BLACK_SUIT_SHADE, SHIRT, TIE_RED, style=style, shades=shades, zombie=True)
BOSS = ('Boss', 21, [hexc('#d4ab8b'), hexc('#cda383')], hexc('#b8916f'), [hexc('#141214'), hexc('#1b181b')], 'slick', True)
person('Boss', 21, BOSS[2], BOSS[3], BOSS[4], WHITE_SUIT, WHITE_SUIT_SHADE, DARK_SHIRT, TIE_GOLD, style='slick', shades=True)
person('ZBoss', 121, BOSS[2], BOSS[3], BOSS[4], WHITE_SUIT, WHITE_SUIT_SHADE, DARK_SHIRT, TIE_GOLD, style='slick', shades=True, zombie=True)
