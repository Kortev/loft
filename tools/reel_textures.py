"""Paints what the first reel needs that the mod hasn't got: the Zombie Devil (episode 1), as the anime has it: an undead
upper torso, its arms gone (raw stumps), its ribcage open at the bottom with its guts hanging out of it like tentacles
(what it moves with), an oversized pink brain bulging out of its neck where its head should be, and a zombie's head
growing out of the right side of its chest. Laid out for the boxes of ReelZombieDevil (src/gametest/.../test), 128x64.

    py tools/reel_textures.py

Needs Pillow and numpy.
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'gametest', 'resources', 'assets', 'shootingstar-gametest', 'textures', 'entity')
rng = np.random.default_rng(66)


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


SKIN = [hexc('#a3aa97'), hexc('#9aa18d'), hexc('#a8ae9c'), hexc('#959c88')]
SKIN_SHADE = [hexc('#7e8672'), hexc('#78806b')]
ROT = [hexc('#66705a'), hexc('#5d6651')]
BRUISE = [hexc('#6e5d6f'), hexc('#655566')]
FLESH = [hexc('#8e1016'), hexc('#7a0c12'), hexc('#a3141c')]
BONE = [hexc('#e4dcc6'), hexc('#d6cdb4')]
GAP = hexc('#2a0a0c')
BRAIN = [hexc('#e7a2a8'), hexc('#df98a0'), hexc('#ecaab0')]
BRAIN_FOLD = [hexc('#bf6f7b'), hexc('#b46572')]
BRAIN_SHINE = hexc('#f6c9cd')
GUT = [hexc('#8e2a3a'), hexc('#a33b4a'), hexc('#7d2232')]
GUT2 = [hexc('#b2536a'), hexc('#c0607a'), hexc('#a34a60')]
GUT3 = [hexc('#6e1828'), hexc('#5e1220'), hexc('#7a2030')]
EYE = hexc('#f1f0e8')
MOUTH = hexc('#1a0507')
TOOTH = hexc('#e8e2cf')

img = np.zeros((128, 128, 4), dtype=np.uint8)


def put(x, y, c):
    img[y, x] = c


def fill(x0, y0, x1, y1, palette):
    palette = palette if isinstance(palette, list) else [palette]
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y, x] = palette[rng.integers(len(palette))]


def box(u, v, w, h, d, paint):
    """Paints a model box's faces from its corner (u, v) as the game lays them out; paint(face, x0, y0, fw, fh)."""
    faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
             'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
    for name, (x0, y0, fw, fh) in faces.items():
        paint(name, x0, y0, fw, fh)


def rotten(x0, y0, fw, fh, spots=0.08):
    fill(x0, y0, x0 + fw, y0 + fh, SKIN)
    for y in range(fh):
        for x in range(fw):
            r = rng.random()
            if r < spots:
                put(x0 + x, y0 + y, ROT[rng.integers(2)])
            elif r < spots * 1.5:
                put(x0 + x, y0 + y, BRUISE[rng.integers(2)])


def torso(face, x0, y0, fw, fh):
    rotten(x0, y0, fw, fh)
    if face in ('front', 'back'):
        # The ribs showing through, the belly's shadow, a seam of stitches.
        for y in range(4, fh - 2, 3):
            fill(x0 + 2, y0 + y, x0 + fw - 2, y0 + y + 1, SKIN_SHADE)
        fill(x0, y0 + fh - 2, x0 + fw, y0 + fh, SKIN_SHADE)
    if face == 'bottom':
        fill(x0, y0, x0 + fw, y0 + fh, FLESH)
    if face == 'top':
        fill(x0 + fw // 2 - 3, y0 + fh // 2 - 3, x0 + fw // 2 + 3, y0 + fh // 2 + 3, FLESH)


def ribs(face, x0, y0, fw, fh):
    # Open at the bottom: the ribs, bone and dark between them, raw flesh behind.
    fill(x0, y0, x0 + fw, y0 + fh, FLESH)
    if face in ('front', 'back', 'left', 'right'):
        for x in range(0, fw, 2):
            fill(x0 + x, y0, x0 + x + 1, y0 + fh, BONE)
            for y in range(fh):
                if (y + x) % 5 == 4:
                    put(x0 + x + 1 if x + 1 < fw else x0 + x, y0 + y, GAP)
    if face == 'bottom':
        fill(x0, y0, x0 + fw, y0 + fh, GAP)
        fill(x0 + 2, y0 + 2, x0 + fw - 2, y0 + fh - 2, FLESH)


def stump(face, x0, y0, fw, fh):
    rotten(x0, y0, fw, fh)
    if face in ('left', 'right'):
        # The torn end: raw, the bone in it.
        fill(x0, y0, x0 + fw, y0 + fh, FLESH)
        fill(x0 + fw // 2 - 1, y0 + fh // 2 - 1, x0 + fw // 2 + 1, y0 + fh // 2 + 1, BONE)


def brain(face, x0, y0, fw, fh):
    fill(x0, y0, x0 + fw, y0 + fh, BRAIN)
    # The folds: winding darker lines; a shine here and there.
    for y in range(fh):
        for x in range(fw):
            v = np.sin(x * 1.3 + np.sin(y * 0.9) * 2.0) + np.sin(y * 1.1 + np.cos(x * 0.7) * 2.0)
            if v > 1.15:
                put(x0 + x, y0 + y, BRAIN_FOLD[(x + y) % 2])
            elif v < -1.6:
                put(x0 + x, y0 + y, BRAIN_SHINE)
    if face == 'top':
        # The split down its middle.
        fill(x0 + fw // 2, y0, x0 + fw // 2 + 1, y0 + fh, BRAIN_FOLD)
    if face == 'bottom':
        fill(x0, y0, x0 + fw, y0 + fh, FLESH)


def neck(face, x0, y0, fw, fh):
    rotten(x0, y0, fw, fh, 0.15)


def head(face, x0, y0, fw, fh):
    rotten(x0, y0, fw, fh, 0.12)
    if face == 'front':
        # A zombie's face: eyes gone white, the mouth hanging open, teeth, blood.
        fill(x0 + 1, y0 + 2, x0 + 3, y0 + 3, EYE)
        fill(x0 + 5, y0 + 2, x0 + 7, y0 + 3, EYE)
        fill(x0 + 1, y0 + 1, x0 + 3, y0 + 2, SKIN_SHADE)
        fill(x0 + 5, y0 + 1, x0 + 7, y0 + 2, SKIN_SHADE)
        fill(x0 + 2, y0 + 4, x0 + 6, y0 + 8, MOUTH)
        put(x0 + 2, y0 + 4, TOOTH)
        put(x0 + 4, y0 + 4, TOOTH)
        put(x0 + 3, y0 + 7, TOOTH)
        put(x0 + 5, y0 + 7, TOOTH)
        put(x0 + 6, y0 + 6, FLESH[0])
    if face == 'top':
        fill(x0, y0, x0 + fw, y0 + 2, ROT)


def gut(palette):
    def paint(face, x0, y0, fw, fh):
        fill(x0, y0, x0 + fw, y0 + fh, palette)
        for y in range(0, fh, 3):
            fill(x0, y0 + y, x0 + fw, y0 + y + 1, palette[2])
    return paint


# The boxes (ReelZombieDevil mirrors these corners and sizes).
box(0, 0, 18, 16, 12, torso)
box(0, 28, 16, 5, 10, ribs)
box(0, 43, 3, 6, 8, stump)
box(22, 43, 8, 8, 6, head)
box(60, 0, 16, 12, 14, brain)
box(60, 26, 12, 2, 10, brain)
box(104, 26, 6, 3, 6, neck)
box(0, 64, 2, 8, 10, brain)
box(24, 64, 12, 9, 2, brain)
box(52, 64, 10, 2, 10, brain)
box(50, 43, 2, 6, 2, gut(GUT))
box(58, 43, 2, 6, 2, gut(GUT2))
box(66, 43, 2, 6, 2, gut(GUT3))
os.makedirs(OUT, exist_ok=True)
Image.fromarray(img, 'RGBA').save(os.path.join(OUT, 'zombie_devil.png'))
print('wrote zombie_devil.png')
