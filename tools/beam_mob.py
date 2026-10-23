"""Beam the shark fiend as a Minecraft mob: a handful of big boxes, flat paint, the way Pochita is made (client/PochitaModel.java,
gen_chainsaw_textures.pochita_entity: two flat tones, simple eyes, no outlines). The proportions are the two drawings kortev
approved (tools/beam_profile.py, tools/beam_front.py), read off them by eye into the tables below; v6 (tools/beam_boxes.py,
~810 thin slices traced from the drawings) looked like a voxel sculpture, and kortev hated it.

    python tools/beam_mob.py --java      writes client/BeamLayout.java and the texture (entity/beam.png)
    python tools/beam_mob.py --preview DIR   quick Python renders (the game is the real check: the beamlook self test)

Uses beam_boxes's part, box, texture-layout and Java machinery. Space as there: pixels, x his +x side, y down with the ground at
y = 70, z = 75 - (the side drawing's x), his front toward -z. Below, a box is written the way the drawing reads: X along him (the
side drawing's x, bigger toward the snout) and h above the ground.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_boxes as bb  # noqa: E402

GROUND = bb.HEIGHT
# Colours: the drawings', a shade lighter (the game's light on a mob's sides darkens them by about a quarter).
NAVY, NAVY_SH = (50, 94, 170), (34, 66, 128)
MINT, MINT_SH = (206, 240, 232), (168, 214, 206)
RED, THROAT, PINK, CREAM = (128, 26, 36), (70, 12, 20), (232, 128, 146), (248, 240, 214)
WHITE, BLACK, INK = (250, 250, 248), (14, 14, 18), (20, 26, 44)
CLEAR = (0, 0, 0, 0)

JAW_REST = 0.5           # the jaw hangs open this far (radians) as drawn; shut is 0
TOOTH = 4                # the teeth's period along a lip (pixels)


def Y(h):
    return GROUND - h


def Z(X):
    return bb.XM - X


def block(part, kind, X0, X1, h0, h1, half, **meta):
    """A box from X0 to X1 along him, h0 to h1 above the ground, half wide each side of his middle."""
    return bb.add_box(part, kind, -half, Y(h1), Z(X1), 2 * half, h1 - h0, X1 - X0, **meta)


def plane(part, kind, X0, X1, h0, h1, shape, x=0.0):
    """A flat fin standing along him (no thickness): its {shape} a polygon in (X, h), painted, the rest of it clear."""
    return bb.add_box(part, kind, x, Y(h1), Z(X1), 0, h1 - h0, X1 - X0, shape=shape)


def build():
    bb.part('trunk', None, (0.0, 0.0, 0.0))
    # The body: the chest (his deepest, from the mouth's corner back), the middle, each with a narrower step on top and under it.
    block('trunk', 'body', 80, 106, 8, 56, 24)
    block('trunk', 'body', 81, 106, 56, 60, 21)
    block('trunk', 'body', 82, 106, 60, 63, 16)
    block('trunk', 'body', 84, 106, 5, 8, 16)
    block('trunk', 'body', 56, 80, 12, 54, 22)
    block('trunk', 'body', 57, 80, 54, 58, 19)
    block('trunk', 'body', 58, 80, 58, 60, 14)
    block('trunk', 'body', 60, 80, 9, 12, 15)
    # The head, hinged at the mouth's corner (it lifts as he bites): the big blunt dome over the mouth, its front stepped down to
    # the snout; the roof of the mouth set in under it, the upper teeth round it.
    bb.part('head', 'trunk', (0.0, Y(31), Z(106)))
    block('head', 'body', 106, 132, 31, 56, 20)
    block('head', 'body', 107, 130, 56, 60, 17)
    block('head', 'body', 108, 126, 60, 62, 12)
    block('head', 'body', 132, 142, 33, 56, 19)
    block('head', 'body', 142, 147, 36, 50, 15)
    block('head', 'teeth_up', 107, 140, 28, 31, 17)
    # The lower jaw from the same corner, hanging open: the jaw, its round chin under it, the lower teeth stood up round its top.
    bb.part('jaw', 'head', (0.0, 0.0, 0.0), rot=(JAW_REST, 0.0, 0.0))
    bb.add_box('jaw', 'jaw', -17, 0, -31, 34, 15, 31, local=True)
    bb.add_box('jaw', 'body', -13, 15, -27, 26, 2, 23, local=True)
    bb.add_box('jaw', 'teeth_low', -15, -3, -29, 30, 3, 27, local=True)
    # The rear of him (it sways), narrowing to the tail's stalk; the crescent fin on the stalk.
    bb.part('rear', 'trunk', (0.0, Y(36), Z(56)))
    block('rear', 'body', 38, 56, 17, 52, 17)
    block('rear', 'body', 39, 56, 52, 55, 12)
    block('rear', 'body', 28, 38, 23, 47, 11)
    bb.part('stalk', 'rear', (0.0, 0.0, Z(28) - Z(56)))
    block('stalk', 'body', 15, 28, 31, 41, 5)
    bb.part('tail', 'stalk', (0.0, 0.0, Z(16) - Z(28)))
    plane('tail', 'fin', 1, 17, 18, 63, [(17, 41), (12, 48), (8, 56), (4, 63), (1, 62), (5, 50), (8, 40), (5, 29), (1, 19),
                                         (4, 18), (10, 25), (17, 32)])
    # Five spikes down his back, the front one biggest, leaning back.
    for X, base, s, part in ((97, 63, 9, 'trunk'), (84, 63, 8, 'trunk'), (70, 60, 7, 'trunk'), (50, 54, 6, 'rear'), (39, 54, 5, 'rear')):
        plane(part, 'fin', X - s * 0.7, X + s * 0.6, base - 1, base + s, [(X - s * 0.7, base - 1), (X + s * 0.6, base - 1), (X - s * 0.55, base + s)])
    # Three eyes up each side, the front one biggest and lowest: a box standing out of his side, painted round.
    for X, h, s, side in ((114, 42, 9, 20), (102, 48, 8, 24), (92, 52, 7, 24)):
        for sign in (1, -1):
            x0 = side - 1 if sign > 0 else -side - 3
            bb.add_box('head' if X > 106 else 'trunk', 'eye', x0, Y(h + s / 2.0), Z(X + s / 2.0), 4, s, s, side=sign)
    # The small fins low on his flanks, out and back.
    for sign in (1, -1):
        name = 'pectoral_r' if sign > 0 else 'pectoral_l'
        bb.part(name, 'trunk', (sign * 23.0, Y(18), Z(86)), rot=(0.0, 0.0, -0.7 * sign))
        bb.add_box(name, 'fin', 0, 0, 0, 0, 9, 15, local=True, shape_local=[(0, 0), (6, 0), (15, 8), (11, 9), (2, 4)])
    build_legs()


def build_legs():
    """Each leg as drawn (beam_boxes.LEG_SPECS, from the two drawings): a broad thigh out and up to the knee, a shin down to a
    narrower point. The parts' names and turns are what BeamModel animates."""
    for name, s, end in bb.LEG_NAMES:
        (hip, knee, foot), _, _ = bb.LEG_SPECS[end]
        H = np.array([s * hip[0], hip[1], hip[2]])
        K = np.array([s * knee[0], knee[1], knee[2]])
        F = np.array([s * foot[0], foot[1], foot[2]])
        bb.part(name, 'trunk', tuple(H))
        ru = bb.rotation(*bb.along(K - H))
        lu = float(np.linalg.norm(K - H))
        bb.part(name + '_up', name, (0.0, 0.0, 0.0), rot=bb.euler(ru))
        rs = bb.rotation(*bb.along(F - K))
        ls = float(np.linalg.norm(F - K))
        bb.part(name + '_shin', name + '_up', (0.0, lu, 0.0), rot=bb.euler(ru.T @ rs))
        bb.add_box(name + '_up', 'leg', -4, -2, -3.5, 8, lu + 4, 7, local=True)
        bb.add_box(name + '_shin', 'leg', -3, -1, -3, 6, ls * 0.6 + 1, 6, local=True)
        bb.add_box(name + '_shin', 'claw', -1.5, ls * 0.6, -1.5, 3, ls * 0.4, 3, local=True)


# --- paint -------------------------------------------------------------------------------------------------------------------

def _interp(points, v):
    xs, ys = zip(*points)
    return float(np.interp(v, xs, ys))


SIDE_LINE = [(15, 30), (45, 26), (65, 28), (82, 32), (98, 36), (110, 41), (128, 42), (148, 42)]   # (X, h): mint below it
FRONT_LINE = [(0, 42), (7, 40), (14, 36), (19, 33), (23, 30), (30, 27)]                          # (|x|, h) on faces to the front


def saw(t):
    """The jagged edge between the navy and the mint: teeth 6 pixels apart, 3 high."""
    k = (t % 6.0) / 6.0
    return 3.0 * (1.0 - abs(2.0 * k - 1.0))


def skin(face, x, h, X):
    if face == 'top':
        return NAVY
    if face == 'bottom':
        return MINT_SH
    line = _interp(FRONT_LINE, abs(x)) + saw(abs(x)) if face == 'front' else _interp(SIDE_LINE, X) + saw(X)
    return NAVY if h > line else MINT


def inside(poly, p):
    x, y = p
    n, c = len(poly), False
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            c = not c
    return c


def paint_pixel(b, face, i, j, fw, fh, pos):
    x, y, z = pos
    h, X = GROUND - y, bb.XM - z
    k = b.kind
    if k == 'body':
        if face == 'front' and abs(X - 106) < 0.6 and abs(x) < 16 and 12 < h < 31:
            return THROAT                          # down his throat, through the open mouth
        if face in ('west', 'east') and 78 < X < 100:
            for g in (97, 92, 87, 82):             # gill slits
                if 19 < h < 33 and abs(X - (g + (h - 26) * 0.2)) < 0.5:
                    return MINT_SH if h < 30 else NAVY_SH
        return skin(face, x, h, X)
    if k == 'jaw':
        if face == 'top':
            return PINK if (i < 2 or i >= fw - 2 or j < 2) else RED
        return skin(face, x, h, X)
    if k in ('teeth_up', 'teeth_low'):
        if face in ('top', 'bottom'):
            return RED
        r = j if k == 'teeth_up' else fh - 1 - j       # rows counted from the gum
        c = i % TOOTH
        if r == 0:
            return PINK
        if r == 1 and c < 3 or r == 2 and c == 1:
            return CREAM
        return RED
    if k == 'eye':
        outward = 'east' if b.meta['side'] > 0 else 'west'
        if face != outward:
            return NAVY_SH
        cx, cy = (fw - 1) / 2.0, (fh - 1) / 2.0
        d = math.hypot(i - cx, j - cy)
        r = fw / 2.0
        if d > r - 0.2:
            return NAVY
        if d > r - 1.1:
            return INK
        front = -1 if outward == 'east' else 1     # the face's i runs back along him on the right, forward on the left
        if math.hypot(i - (cx + front * 0.8), j - cy) < max(1.0, r * 0.42):
            return BLACK
        return WHITE
    if k == 'fin':
        if face not in ('west', 'east'):
            return CLEAR
        if 'shape_local' in b.meta:
            lz = b.z + (b.d - 0.5 - i if face == 'west' else i + 0.5)
            return NAVY_SH if inside(b.meta['shape_local'], (lz - b.z, j + 0.5)) else CLEAR
        return NAVY_SH if inside(b.meta['shape'], (X, h)) else CLEAR
    if k == 'leg':
        return NAVY_SH if face == 'bottom' else NAVY
    if k == 'claw':
        return INK if face == 'bottom' else NAVY_SH
    return (255, 0, 255)


def paint():
    height = bb.pack()
    tex = np.zeros((height, bb.TEXTURE_W, 4), np.uint8)
    mats = bb.part_matrices(bb.standing())
    for b in bb.BOXES:
        m = mats[b.part]
        for face, (fx, fy, fw, fh) in bb.faces(b).items():
            for j in range(fh):
                for i in range(fw):
                    p = np.array(bb.pixel(b, face, i, j) + (1.0,))
                    pos = (m @ p)[:3]
                    c = paint_pixel(b, face, i, j, fw, fh, pos)
                    tex[fy + j, fx + i] = c if len(c) == 4 else c + (255,)
    return Image.fromarray(tex, 'RGBA')


def setup():
    bb.PARTS.clear()
    bb.BOXES.clear()
    bb._ORDER.clear()
    bb.TEXTURE_W = 512
    build()
    bb.STATS['jaw_theta'] = JAW_REST
    bb.mouth_open = lambda: JAW_REST


TEXTURE = os.path.join('chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman', 'textures', 'entity', 'beam.png')


def main():
    setup()
    img = paint()
    print('%d boxes in %d parts; texture %d x %d' % (len(bb.BOXES), len(bb.PARTS), img.width, img.height))
    if '--java' in sys.argv:
        src = bb.java().replace('tools/beam_boxes.py --java', 'tools/beam_mob.py --java')
        with open(bb.LAYOUT_JAVA, 'w', encoding='utf-8', newline='\n') as f:
            f.write(src)
        img.save(TEXTURE)
        print('wrote', bb.LAYOUT_JAVA, 'and', TEXTURE)
    if '--preview' in sys.argv:
        import beam_preview as pv
        out = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(out, exist_ok=True)
        data = pv.texels(np.array(img))
        for name, (yaw, pitch) in {'side': (0, 0), 'front': (90, 0), 'q_front': (50, 15), 'q_back': (-130, 15)}.items():
            pv.render(data, yaw, pitch, scale=5, size=(190, 100), centre=(0, 36, 0)).save(os.path.join(out, name + '.png'))
        img.resize((img.width * 2, img.height * 2), Image.NEAREST).save(os.path.join(out, 'texture.png'))


if __name__ == '__main__':
    main()
