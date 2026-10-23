"""Paints Beam's texture for the boxes tools/beam_boxes.py builds, from the two drawings kortev approved.

Every pixel of every box face is looked up where it is on him: the drawings are projected onto the boxes, the side drawing
(tools/beam_profile.py) onto the faces that face his sides, the front drawing (tools/beam_front.py) onto the faces that face
fore and aft, so the navy above and the mint below, the jagged line between them, the shade, the gill lines, the gums, the teeth
and the eyes land exactly as drawn, in the drawings' own RGB values. Ink is drawn round the boxes' own silhouettes (as the
drawings ink theirs); the legs, fins and spikes, which the drawings only show from the one side, are painted by their shape.

    from beam_paint import paint_beam; img = paint_beam()          (tools/gen_chainsaw_textures.py does this for beam.png)
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_boxes as bb  # noqa: E402
import beam_front as bf  # noqa: E402
import beam_profile as bp  # noqa: E402

NAVY, NAVY_SH, MINT, MINT_SH, INK = bp.NAVY, bp.NAVY_SH, bp.MINT, bp.MINT_SH, bp.INK
RED, PINK, CREAM, WHITE, BLACK, THROAT = bp.RED, bp.PINK, bp.CREAM, bp.WHITE, bp.BLACK, bf.THROAT
KEEP = (RED, PINK, CREAM, THROAT)
BODY_KINDS = ('head', 'trunk', 'rear', 'stalk', 'jaw', 'fin', 'spike', 'eye', 'plate')
LEG_KINDS = ('leg_up', 'leg_shin', 'pect')


def opaque(rgba):
    return rgba + (255,)


class Sampler:
    """A drawing's body layer as a picture to look pixels up in, with the nearest opaque pixel when a spot is clear."""

    def __init__(self, arr):
        self.a = arr
        self.h, self.w = arr.shape[:2]

    def at(self, x, y):
        xi, yi = int(math.floor(x)), int(math.floor(y))
        if 0 <= xi < self.w and 0 <= yi < self.h and self.a[yi, xi, 3] > 0:
            return tuple(int(v) for v in self.a[yi, xi, :3])
        for r in (1, 2, 3):
            best = None
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    x2, y2 = xi + dx, yi + dy
                    if 0 <= x2 < self.w and 0 <= y2 < self.h and self.a[y2, x2, 3] > 0:
                        dist = dx * dx + dy * dy
                        if best is None or dist < best[0]:
                            best = (dist, tuple(int(v) for v in self.a[y2, x2, :3]))
            if best:
                return best[1]
        return None


def edge_of(mask, x, y):
    """Whether pixel (x, y) is on the edge of a silhouette: it is in it and a neighbour is not."""
    h, w = mask.shape
    xi, yi = int(math.floor(x)), int(math.floor(y))
    if not (0 <= xi < w and 0 <= yi < h) or not mask[yi, xi]:
        return False
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        x2, y2 = xi + dx, yi + dy
        if not (0 <= x2 < w and 0 <= y2 < h) or not mask[y2, x2]:
            return True
    return False


def paint_beam():
    bb.build()
    height = bb.pack()
    img = Image.new('RGBA', (bb.TEXTURE_W, height), (0, 0, 0, 0))
    mats = bb.part_matrices(bb.standing())
    side = Sampler(bb.side_img())
    front = Sampler(np.array(bf.body(extras=False)))
    not_leg = lambda b: b.kind not in LEG_KINDS and not b.kind.startswith('teeth')  # noqa: E731
    sil_side = bb.silhouette('side', only=not_leg)
    sil_front = bb.silhouette('front', only=not_leg)
    px = img.load()
    for b in bb.BOXES:
        M = mats[b.part]
        R = M[:3, :3]
        for face, (x0, y0, fw, fh) in bb.faces(b).items():
            if fw <= 0 or fh <= 0:
                continue
            nrm = R @ np.array(bb.NORMALS[face], float)
            for j in range(fh):
                for i in range(fw):
                    lp = bb.pixel(b, face, i, j)
                    pos = M @ np.array([lp[0], lp[1], lp[2], 1.0])
                    xd, yl = bb.XM - pos[2], pos[1]           # where this is in the side drawing (its x, the body layer's y)
                    xf = bb.CX + pos[0]                          # and in the front drawing
                    col = pixel_colour(b, face, i, j, fw, fh, pos, nrm, xd, yl, xf, side, front, sil_side, sil_front)
                    if col is not None:
                        px[x0 + i, y0 + j] = opaque(col)
    return img


def pixel_colour(b, face, i, j, fw, fh, pos, nrm, xd, yl, xf, side, front, sil_side, sil_front):
    """The colour of pixel (i, j) of {face} of box {b}, or None to leave it clear."""
    k = b.kind
    sideways = abs(nrm[0]) > 0.6
    fore = abs(nrm[2]) > 0.6
    if k in ('head', 'trunk', 'rear', 'stalk'):
        return skin(b, face, i, j, fw, fh, nrm, xd, yl, xf, side, front, sil_side, sil_front, sideways, fore)
    if k == 'jaw':
        top_row = b.meta['row'][0] == b.meta['top']
        if face == 'top' and top_row:
            # the gums: pink along the lip's edge (every third slice, or the steps of the narrowing jaw would show as a wide
            # pink band from the front) and across the front of the jaw
            edge = (i == 0 or i == fw - 1) and b.meta['s'][0] % 3 == 0 or (j == 0 and b.meta['s'][1] >= bb.STATS['jaw_len'] - 1)
            # and the teeth standing up round the U of the lip, seen from the front as cream points leaning in from its edge
            k = min(i, fw - 1 - i)
            if k < 3 and bb.STATS['jaw_len'] - b.meta['s'][0] > 2 and b.meta['s'][0] > 4 and (b.meta['s'][0] + 2) % 5 < 3 - k:
                return CREAM
            return PINK if edge else (THROAT if xd < bb.X_CORNER + 8 else RED)
        if face == 'back' and top_row:
            return THROAT
        if face == 'front' and top_row:
            # the risers of the jaw's top surface seen from the front: the inside of the mouth, the lip pink round its edge
            front_most = b.meta['s'][1] >= bb.STATS['jaw_len']
            if front_most and j == 0:
                return PINK
            k = min(i, fw - 1 - i)
            if k < 3 and j < 3 and b.meta['s'][0] > 4 and (b.meta['s'][0] + 2) % 5 < 3 - k:
                return CREAM    # a tooth standing up at the lip
            return THROAT if xd < bb.X_CORNER + 8 else RED
        return skin(b, face, i, j, fw, fh, nrm, xd, yl, xf, side, front, sil_side, sil_front, sideways, fore)
    if k == 'plate':
        deep = xd < bb.X_CORNER + 9
        if face in ('west', 'east'):
            return PINK if (j == 0 or j == fh - 1) and not deep else (THROAT if deep else RED)
        return THROAT if deep else RED
    if k == 'eye':
        return eye_pixel(b, face, i, j, fw, fh, nrm, xd, yl, side)
    if k == 'spike':
        if sideways:
            c = side.at(xd, yl)
            return c if c is not None else NAVY_SH
        return INK if face in ('front', 'back') and (i == 0 or i == fw - 1 or j == 0) else NAVY_SH
    if k.startswith('teeth'):
        if k in ('teeth_up', 'teeth_low'):
            if face not in ('west', 'east'):
                return None
            return CREAM if side.at(xd, yl) == CREAM else None
        if face not in ('front', 'back'):
            return None
        c = front.at(xf, yl)
        return CREAM if c == CREAM else None
    if k == 'fin':
        if sideways:
            c = side.at(xd, yl)
            return c if c is not None else NAVY
        return INK if (face in ('front', 'back') or i == 0 or i == fw - 1) else NAVY
    if k in LEG_KINDS:
        return leg_pixel(b, face, i, j, fw, fh, nrm)
    return INK


def skin(b, face, i, j, fw, fh, nrm, xd, yl, xf, side, front, sil_side, sil_front, sideways, fore):
    """The body's skin: the drawings projected onto the boxes, ink round the boxes' own silhouette."""
    k = b.kind
    if face == 'top':
        c = side.at(xd, yl + 2.0)
        return MINT if c in (MINT, MINT_SH) else NAVY
    if face == 'bottom':
        if k == 'head' and gape_roof(b, xd, yl):
            edge = i == 0 or i == fw - 1
            return PINK if edge else (THROAT if xd < bb.X_CORNER + 8 else RED)
        return MINT_SH
    if sideways:
        c = side.at(xd, yl)
        if c is None:
            c = NAVY
        if c not in KEEP and edge_of(sil_side, xd, yl + bb.LIFT):
            return INK
        return c
    # facing fore or aft: the front drawing from the front (ink only round the snout), the side drawing from behind
    if face == 'front' and k == 'jaw' and front.at(xf, yl) in KEEP:
        return MINT          # the jaw's outer steps are skin, not the inside of the mouth the drawing shows there
    if face == 'back':
        c = side.at(xd, yl)
        return c if c is not None else NAVY_SH
    c = front.at(xf, yl)
    if c is None:
        c = NAVY
    if k == 'head' and b.meta['slice'][0] >= 138 and c not in KEEP and edge_of(sil_front, xf, yl + bb.LIFT):
        return INK
    return c


def gape_roof(b, xd, yl):
    """Whether a head box's bottom face is the roof of the open mouth (its bottom row, behind the front of the snout)."""
    if b.kind != 'head' or b.meta['row'][1] != max(bx.meta['row'][1] for bx in bb.PARTS['head'].boxes if bx.kind == 'head' and bx.meta['slice'] == b.meta['slice']):
        return False
    return bool(bb.GAPE[int(min(bb.bp.H - 1, max(0, math.floor(yl + 0.6)))), int(min(bb.bp.W - 1, max(0, math.floor(xd))))]) or b.meta['slice'][0] < 140


def eye_pixel(b, face, i, j, fw, fh, nrm, xd, yl, side):
    if face in ('west', 'east'):
        outer = (face == 'east') == (b.meta['side'] > 0)
        if outer:
            c = side.at(xd, yl)
            return c if c is not None else WHITE
        return INK
    if face in ('front', 'back'):
        if j == 0 or j == fh - 1:
            return INK
        outer = fw - 1 if b.meta['side'] > 0 else 0
        return BLACK if face == 'front' and i == outer and fh // 3 <= j < fh - fh // 3 else WHITE
    return INK


def leg_pixel(b, face, i, j, fw, fh, nrm):
    s = b.meta['side']
    out = nrm[0] * s
    last = b.kind == 'leg_shin' and b.meta['piece'] >= 5
    if b.kind == 'pect':
        return NAVY if nrm[1] < -0.5 else (MINT_SH if nrm[1] > 0.5 else NAVY_SH)
    if face in ('west', 'east'):
        edge = i == 0 or i == fw - 1
        if out > 0.3:
            return INK if edge else NAVY
        if out < -0.3:
            return INK if edge else MINT
        return NAVY_SH
    if last:
        return INK
    if nrm[1] < -0.4:
        return NAVY
    if nrm[1] > 0.4:
        return MINT_SH
    return NAVY_SH if (i == 0 or i == fw - 1) is False else INK


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'beam.png'
    paint_beam().save(out)
    print('wrote', out)
