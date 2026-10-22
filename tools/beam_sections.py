"""Compares the box Beam's body with the smooth blueprint's, section by section (pixels): the blueprint's from
tools/beam_measure.py's geometry.npz, the boxes' from tools/beam_boxes.py. The blueprint's snout is NOSE pixels shorter (the
boxes carry the nose on past the front teeth), so a blueprint section at u is compared with the boxes' at u + NOSE.

    python tools/beam_sections.py BLUEPRINT_DIR
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_boxes as bb  # noqa: E402


def box_section(u):
    """The boxes' body at u: the highest top, lowest bottom and widest half width of the body boxes covering it."""
    top, bot, half = -1e9, 1e9, 0.0
    for b in bb.BOXES:
        if b.kind not in ('head', 'trunk', 'rear', 'stalk'):
            continue
        z0 = b.z + bb.PARTS[b.part].pivot[2]
        # (absolute z of the part's pivot chain)
        ox, oy, oz = bb.absolute_pivot(b.part)
        za, zb = b.z + oz + bb.Z0, b.z + b.d + oz + bb.Z0
        if za <= u < zb:
            top = max(top, bb.HEIGHT - (b.y + oy))
            bot = min(bot, bb.HEIGHT - (b.y + b.h + oy))
            half = max(half, max(abs(b.x + ox), abs(b.x + b.w + ox)))
    return top, bot, half


def main():
    geo = np.load(os.path.join(sys.argv[1], 'geometry.npz'))
    body = geo['v_body__body']
    stem = geo['v_tail_stem__tail']
    pts = np.concatenate([body, stem])
    # build space (pixels): x left, y back (snout at -40), z up
    print(' u(old) | blueprint top bot half | boxes top bot half (at u+%d) | d top  d bot  d half' % bb.NOSE)
    bb.build()
    for u in range(2, 74, 4):
        y = u - 40.0
        slab = pts[np.abs(pts[:, 1] - y) < 0.8]
        if len(slab) == 0:
            continue
        t, b_, h = slab[:, 2].max(), slab[:, 2].min(), np.abs(slab[:, 0]).max()
        bt, bb_, bh = box_section(u + bb.NOSE)
        print('%6d | %6.1f %5.1f %5.1f | %6.1f %5.1f %5.1f | %+5.1f %+5.1f %+5.1f' % (u, t, b_, h, bt, bb_, bh, bt - t, bb_ - b_, bh - h))


if __name__ == '__main__':
    main()
