"""Lays the box Beam's silhouettes over the approved drawings' (tools/beam_profile.py, tools/beam_front.py) and prints how well they
agree: grey where both have him, red where only the boxes do, blue where only the drawing does. Also the head and mouth region of
the side view on its own, where the shape matters most.

    python tools/beam_check.py OUT_DIR       writes side.png, front.png, head.png into OUT_DIR, prints the overlaps (IoU)

The drawing's far-side legs (drawn offset so they show in the side view) are left out of the side target: in a true side view
the far legs are hidden behind the near ones.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_boxes as bb  # noqa: E402
import beam_front as bf  # noqa: E402
import beam_profile as bp  # noqa: E402

SCALE = 8


def target(view):
    img = np.array(bp.scene(far=False) if view == 'side' else bf.scene())
    sky, grass = np.array(bp.SKY), np.array(bp.GRASS)
    return ~((img == sky).all(axis=2) | (img == grass).all(axis=2))


def overlay(mine, theirs, path, crop=None):
    img = np.full(mine.shape + (3,), 255, np.uint8)
    img[theirs & ~mine] = (70, 110, 220)
    img[mine & ~theirs] = (230, 80, 80)
    img[mine & theirs] = (170, 170, 170)
    im = Image.fromarray(img)
    if crop:
        im = im.crop(crop)
    im.resize((im.width * SCALE, im.height * SCALE), Image.NEAREST).save(path)


def iou(a, b):
    return (a & b).sum() / max(1, (a | b).sum())


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'beam_check'
    os.makedirs(out, exist_ok=True)
    bb.build()
    bb.pack()
    results = {}
    for view in ('side', 'front'):
        mine, theirs = bb.silhouette(view), target(view)
        overlay(mine, theirs, os.path.join(out, view + '.png'))
        results[view] = iou(mine, theirs)
        print('%-5s IoU %.3f   boxes %d px, drawing %d px, both %d' % (view, results[view], mine.sum(), theirs.sum(), (mine & theirs).sum()))
    # the head and the mouth: the side view from the corner of the mouth to the nose, back to belly
    mine, theirs = bb.silhouette('side'), target('side')
    box = (100, 10, 150, 90)
    sub = (slice(box[1], box[3]), slice(box[0], box[2]))
    print('head  IoU %.3f   (side, x %d..%d)' % (iou(mine[sub], theirs[sub]), box[0], box[2]))
    overlay(mine, theirs, os.path.join(out, 'head.png'), crop=box)
    # the body alone (no legs, spikes, tail, fin, eyes): against the outline polygon
    body = bb.silhouette('side', only=lambda b: b.kind in ('head', 'trunk', 'rear', 'jaw', 'plate'))
    sil = bb.SIDE_SIL | bb.GAPE
    full = np.zeros_like(body)
    full[bb.LIFT:, :] = sil[:sil.shape[0] - bb.LIFT, :]
    print('body  IoU %.3f   (side, outline only)' % iou(body, full))
    fb = bb.silhouette('front', only=lambda b: b.kind in ('head', 'trunk', 'rear', 'jaw', 'plate'))
    ft = np.array(bf.body(extras=False))[:, :, 3] > 0
    ftt = np.zeros_like(fb)
    ftt[bb.LIFT:, :] = ft[:ft.shape[0] - bb.LIFT, :]
    print('body  IoU %.3f   (front, body only)' % iou(fb, ftt))


if __name__ == '__main__':
    main()
