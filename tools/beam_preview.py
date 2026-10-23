"""Looks at the box Beam without the game: every texel of his texture put where it is on him (the boxes at their rest pose), seen
orthographically from any side, nearest-sampled and flat lit, painted far to near.

    python tools/beam_preview.py OUT_DIR [TEXTURE.png]      writes side.png, front.png, three_quarter.png, top.png, mouth.png

The game's own picture (the beamlook test) is the real check; this is for quick looks while shaping him. The texture is the
game's (chainsawman/src/client/resources/.../entity/beam.png) unless one is given.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_boxes as bb  # noqa: E402
import beam_profile as bp  # noqa: E402

TEXTURE = os.path.join('chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman', 'textures', 'entity', 'beam.png')
SKY = np.array(bp.SKY + (255,), np.uint8)
GRASS = np.array(bp.GRASS + (255,), np.uint8)


def texels(tex, pose=None):
    """Every opaque texel of every box face: its model position, its normal and colour."""
    mats = bb.part_matrices(pose or bb.standing())
    pos, nrm, col = [], [], []
    for b in bb.BOXES:
        M = mats[b.part]
        R = M[:3, :3]
        for face, (x0, y0, fw, fh) in bb.faces(b).items():
            if fw <= 0 or fh <= 0:
                continue
            n = R @ np.array(bb.NORMALS[face], float)
            for j in range(fh):
                for i in range(fw):
                    c = tex[y0 + j, x0 + i]
                    if c[3] == 0:
                        continue
                    lp = bb.pixel(b, face, i, j)
                    pos.append((M @ np.array([lp[0], lp[1], lp[2], 1.0]))[:3])
                    nrm.append(n)
                    col.append(c)
    return np.array(pos), np.array(nrm), np.array(col)


def render(data, yaw, pitch, scale=6, size=(190, 100), centre=(0.0, 30.0, 0.0), shade=True):
    """Views him from the direction (yaw, pitch) in degrees: yaw 0 is from his +x side, 90 from in front of him. Returns RGB."""
    pos, nrm, col = data
    ya, pa = math.radians(yaw), math.radians(pitch)
    # the view's axes: right, up (model y is down), and toward the camera
    to_cam = np.array([math.cos(ya) * math.cos(pa), -math.sin(pa), -math.sin(ya) * math.cos(pa)])
    right = np.array([math.sin(ya), 0.0, math.cos(ya)])
    up = np.cross(to_cam, right)
    up = up if up[1] < 0 else -up
    up = -up   # screen up is model -y
    right = np.cross(np.array([0.0, -1.0, 0.0]), to_cam)
    right /= np.linalg.norm(right)
    up = np.cross(to_cam, right)
    p = pos - np.array(centre)
    sx = p @ right
    sy = -(p @ up)
    depth = p @ to_cam
    order = np.argsort(depth)
    w, h = size
    img = np.zeros((h * scale, w * scale, 4), np.uint8)
    img[:] = SKY
    light = np.array([0.3, -0.7, -0.6])
    light /= np.linalg.norm(light)
    lam = np.clip(-(nrm @ light), 0, 1)
    face_to = nrm @ to_cam
    for idx in order:
        if face_to[idx] < 0.08:
            continue
        x = int((sx[idx] + w / 2.0) * scale)
        y = int((sy[idx] + h / 2.0) * scale)
        c = col[idx].astype(float)
        if shade:
            c[:3] *= 0.72 + 0.28 * lam[idx]
        img[max(0, y):max(0, y + scale + 1), max(0, x):max(0, x + scale + 1)] = np.clip(c, 0, 255).astype(np.uint8)
    return Image.fromarray(img[:, :, :3])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'beam_preview'
    tex_path = sys.argv[2] if len(sys.argv) > 2 else TEXTURE
    os.makedirs(out, exist_ok=True)
    bb.build()
    bb.pack()
    tex = np.array(Image.open(tex_path).convert('RGBA'))
    data = texels(tex)
    views = {'side': (0, 0, (190, 100), (0, 36, 0)), 'front': (90, 0, (110, 100), (0, 36, 0)),
             'three_quarter': (60, 16, (150, 110), (0, 34, -10)), 'top': (90, 89, (130, 140), (0, 36, 0)),
             'mouth': (70, 12, (90, 70), (0, 40, -42))}
    for name, (yaw, pitch, size, centre) in views.items():
        render(data, yaw, pitch, scale=6, size=size, centre=centre).save(os.path.join(out, name + '.png'))
        print('wrote', name)


if __name__ == '__main__':
    main()
