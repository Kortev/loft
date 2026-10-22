"""Lays the box Beam's silhouette over the smooth blueprint's (tools/beam_measure.py's orthographic renders): where the boxes
and the blueprint agree is grey, boxes only red, blueprint only blue. Prints how much of the blueprint's body the boxes cover.

    python tools/beam_compare.py BLUEPRINT_DIR OUT.png [side|top|front]
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_boxes as bb  # noqa: E402

PX = 10  # canvas pixels per model pixel (the blueprint renders are 20 to a model pixel: they are halved)


def hull(pts):
    pts = sorted(set(map(tuple, np.round(pts, 4))))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def box_mask(view, pose=None, only=None):
    bb.build()
    pose = pose or bb.standing()
    mats = bb.part_matrices(pose)
    img = Image.new('L', (1920, 1920), 0)
    dr = ImageDraw.Draw(img)
    for b in bb.BOXES:
        if only and not only(b):
            continue
        c = bb.corners(b, mats[b.part])
        x, y, z = c[:, 0], c[:, 1], c[:, 2]
        if view == 'side':
            col = 960 - PX * z
            row = 960 - PX * ((bb.HEIGHT - y) - 27)
        elif view == 'front':
            col = 960 + PX * x
            row = 960 - PX * ((bb.HEIGHT - y) - 27)
        else:
            col = 960 + PX * x
            row = 960 + PX * z
        poly = hull(list(zip(col, row)))
        if len(poly) >= 3:
            dr.polygon(poly, fill=255)
    return np.array(img) > 0


def blueprint_mask(path, view):
    im = np.array(Image.open(path).convert('RGB')).astype(int)
    bg = im[5, 5]
    m = np.abs(im - bg).sum(axis=2) > 24
    small = m.reshape(960, 2, 960, 2).mean(axis=(1, 3)) > 0.5
    canvas = np.zeros((1920, 1920), bool)
    # the blueprint's u runs shifted by the nose, so its image slides over by NOSE model pixels
    if view == 'side':
        canvas[398:398 + 960, 420:420 + 960] = small
    elif view == 'front':
        canvas[398:398 + 960, 480:480 + 960] = small
    else:
        canvas[480:480 + 960, 480:480 + 960] = small
    return canvas


def main():
    src, out = sys.argv[1], sys.argv[2]
    view = sys.argv[3] if len(sys.argv) > 3 else 'side'
    mine = box_mask(view)
    theirs = blueprint_mask(os.path.join(src, 'ortho_%s.png' % view), view)
    img = np.full(mine.shape + (3,), 255, np.uint8)
    img[theirs & ~mine] = (70, 110, 220)
    img[mine & ~theirs] = (230, 80, 80)
    img[mine & theirs] = (170, 170, 170)
    ys, xs = np.nonzero(mine | theirs)
    box = (max(0, xs.min() - 40), max(0, ys.min() - 40), min(1920, xs.max() + 40), min(1920, ys.max() + 40))
    crop = Image.fromarray(img).crop(box)
    crop = crop.resize((int(crop.width * 1.4), int(crop.height * 1.4)), Image.NEAREST)
    crop.save(out)
    both = (mine & theirs).sum()
    print('boxes', int(mine.sum() / PX / PX), 'px2; blueprint', int(theirs.sum() / PX / PX), 'px2; both', int(both / PX / PX),
          'IoU %.2f' % (both / max(1, (mine | theirs).sum())))


if __name__ == '__main__':
    main()
