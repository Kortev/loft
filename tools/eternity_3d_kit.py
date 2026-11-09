"""Toolkit for the Eternity Devil's 3D look (phase A): signed-distance sculpting, per-part voxelization, the mesh format,
and an offline preview rasterizer that draws the voxel faces the way the game will (blocky, simple lights, animated parts).

It builds on tools/eternity_devil_sculpt.py (the v5 pipeline: SDF -> voxels -> merged quads) and keeps its binary format:

  *_mesh.bin   little-endian: int32 count, then per quad: uint8 dir (0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z),
               uint8 part (index into the json's "parts"), int16 x, y, z (corner, 1/16 block), int16 du, dv (size in its
               plane, 1/16 block; dir X: du along Z, dv along Y; dir Y: du along X, dv along Z; dir Z: du along X, dv
               along Y), uint8 r, g, b, uint8 emit (the v5 pad byte: 0 = lit by the world, 255 = glows on its own).
               The colour already carries Minecraft's face shade (tops brightest), as in v5.
  *_parts.json the named parts: pivot, how each one animates, sizes, counts.
"""
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_devil_sculpt as SC  # noqa: E402

smin, smax, capsule = SC.smin, SC.smax, SC.capsule

S8 = 1.0 / 8.0
S16 = 1.0 / 16.0
S32 = 1.0 / 32.0
S64 = 1.0 / 64.0


# ---------------------------------------------------------------- noise

class Noise:
    def __init__(self, seed=1):
        rng = np.random.default_rng(seed)
        self.t = rng.random((24, 24, 24)).astype(np.float32)

    def __call__(self, px, py, pz, f=1.0):
        sh = np.shape(px)
        c = np.stack([np.ravel(px) * f, np.ravel(py) * f, np.ravel(pz) * f]).astype(np.float32) % 24
        return ndimage.map_coordinates(self.t, c, order=1, mode='grid-wrap').reshape(sh)

    def fbm(self, px, py, pz, f=1.0, octaves=3):
        a, tot, out = 1.0, 0.0, 0.0
        for o in range(octaves):
            out = out + a * self(px, py, pz, f * (2 ** o))
            tot += a
            a *= 0.5
        return out / tot


# ---------------------------------------------------------------- distance functions

def rotmat(axis, deg):
    a = np.array(axis, float)
    a /= np.linalg.norm(a)
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    x, y, z = a
    return np.array([[c + x * x * (1 - c), x * y * (1 - c) - z * s, x * z * (1 - c) + y * s],
                     [y * x * (1 - c) + z * s, c + y * y * (1 - c), y * z * (1 - c) - x * s],
                     [z * x * (1 - c) - y * s, z * y * (1 - c) + x * s, c + z * z * (1 - c)]])


def facing(direction, up=(0, 1, 0)):
    """A frame whose local +X points along `direction`, local +Y as close to `up` as possible (columns = local axes)."""
    x = np.array(direction, float)
    x /= np.linalg.norm(x)
    up = np.array(up, float)
    z = np.cross(x, up)
    if np.linalg.norm(z) < 1e-6:
        z = np.cross(x, np.array([0, 0, 1.0]))
    z /= np.linalg.norm(z)
    y = np.cross(z, x)
    return np.stack([x, y, z], axis=1)


class Frame:
    """A local frame: world = c + R @ (local * s). to_local(P) gives the scaled-down local coordinates."""

    def __init__(self, c, R=None, s=1.0):
        self.c = np.array(c, float)
        self.R = np.eye(3) if R is None else np.array(R, float)
        self.s = float(s)

    def to_local(self, px, py, pz):
        dx, dy, dz = px - self.c[0], py - self.c[1], pz - self.c[2]
        R = self.R
        lx = (R[0, 0] * dx + R[1, 0] * dy + R[2, 0] * dz) / self.s
        ly = (R[0, 1] * dx + R[1, 1] * dy + R[2, 1] * dz) / self.s
        lz = (R[0, 2] * dx + R[1, 2] * dy + R[2, 2] * dz) / self.s
        return lx, ly, lz

    def to_world(self, v):
        return self.c + self.R @ (np.array(v, float) * self.s)

    def dir_world(self, v):
        return self.R @ np.array(v, float)


def ell(px, py, pz, c, r):
    """Ellipsoid distance (v5's approximation)."""
    dx, dy, dz = px - c[0], py - c[1], pz - c[2]
    k0 = np.sqrt((dx / r[0]) ** 2 + (dy / r[1]) ** 2 + (dz / r[2]) ** 2)
    k1 = np.sqrt((dx / r[0] ** 2) ** 2 + (dy / r[1] ** 2) ** 2 + (dz / r[2] ** 2) ** 2)
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-6)


def sphere(px, py, pz, c, r):
    return np.sqrt((px - c[0]) ** 2 + (py - c[1]) ** 2 + (pz - c[2]) ** 2) - r


def box(px, py, pz, c, h, rad=0.0):
    qx, qy, qz = np.abs(px - c[0]) - h[0] + rad, np.abs(py - c[1]) - h[1] + rad, np.abs(pz - c[2]) - h[2] + rad
    out = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2 + np.maximum(qz, 0) ** 2)
    ins = np.minimum(np.maximum(qx, np.maximum(qy, qz)), 0)
    return out + ins - rad


def caps(px, py, pz, a, b, r0, r1=None, floor=0.0):
    """Capsule a->b, radii r0 -> r1, never thinner than `floor` (so a finger is at least a voxel wide)."""
    r1 = r0 if r1 is None else r1
    return capsule(px, py, pz, a, b, max(r0, floor), max(r1, floor))


def polyline(px, py, pz, pts, r0, r1=None, floor=0.0):
    r1 = r0 if r1 is None else r1
    d = None
    n = len(pts) - 1
    for i in range(n):
        ra = r0 + (r1 - r0) * i / n
        rb = r0 + (r1 - r0) * (i + 1) / n
        di = caps(px, py, pz, pts[i], pts[i + 1], ra, rb, floor)
        d = di if d is None else np.minimum(d, di)
    return d


def curve(p0, p1, p2, n=8):
    """A quadratic bezier as n+1 points."""
    p0, p1, p2 = np.array(p0, float), np.array(p1, float), np.array(p2, float)
    ts = np.linspace(0, 1, n + 1)
    return [((1 - t) ** 2) * p0 + 2 * (1 - t) * t * p1 + t * t * p2 for t in ts]


# ---------------------------------------------------------------- palette

def hx(s):
    s = s.lstrip('#')
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], float)


# grey-brown dead flesh, dark to lit; never pink, never red
FLESH = [hx(c) for c in ('#161212', '#241d1c', '#352b28', '#4a3e38', '#5f514a', '#75655b', '#8b7a6c', '#a08e7d')]
FLESH_COOL = [hx(c) for c in ('#14161a', '#20232a', '#30343a', '#42464a', '#575a58', '#6d6e68', '#84837a', '#9a988c')]
TOOTH = [hx(c) for c in ('#6e5a34', '#a8925c', '#d2c39a', '#ece3c6', '#fbf6e6')]
TOOTH_OLD = [hx(c) for c in ('#2e2210', '#54421e', '#7a6234', '#9c8248', '#bca468')]     # old, stained, brownish
TOOTH_YEL = [hx(c) for c in ('#5a4a1c', '#8c7430', '#bca048', '#d6bc5c', '#e8d278')]
SCLERA = [hx(c) for c in ('#9c9288', '#c4bab0', '#e0d8cc', '#f0eae0')]
VEIN = hx('#a8302c')
IRIS_BROWN = [hx(c) for c in ('#1c0e08', '#3c2214', '#6a4426')]
IRIS_RED = [hx(c) for c in ('#3c0807', '#7a1210', '#b02820')]
IRIS_GREY = [hx(c) for c in ('#161c24', '#2c3844', '#586c7c')]
PUPIL = hx('#040202')
RED_EYE = hx('#e2321c')
BLACK = hx('#07040a')
INNER = [hx(c) for c in ('#05030a', '#12080c', '#26100f')]    # the inside of a mouth, a socket
GLOW = hx('#6a120c')
RIM_FLESH = [hx(c) for c in ('#3a1c12', '#5c2c1a', '#8a4a2a', '#b06a3c')]
TEAR = [hx(c) for c in ('#6a7c88', '#9ab0bc', '#d0e0e8')]


def pick(ramp, t, noise=None, jitter=0.45):
    """Pick (no blending: crisp texels) from a ramp by t in 0..1, jittered by noise for a dithered edge."""
    n = len(ramp)
    x = np.clip(t, 0, 1) * (n - 1)
    if noise is not None:
        x = x + (noise - 0.5) * jitter * 2
    i = np.clip(np.floor(x + 0.5), 0, n - 1).astype(int)
    return np.array(ramp)[i]


# ---------------------------------------------------------------- parts: voxelization and painting

class Grid:
    def __init__(self, name, lo, scale, solid, col, em):
        self.name, self.lo, self.scale = name, np.array(lo, float), scale
        self.solid, self.col, self.em = solid, col, em

    @property
    def n(self):
        return int(self.solid.sum())


class Ctx:
    pass


def voxelize(name, lo, hi, scale, layers_fn, paint_fn, noise, keep_specks=False, min_nb=2):
    """layers_fn(px, py, pz) -> ordered list of (material, distance); later layers win where they are inside.
    paint_fn(ctx) -> (rgb float array [..., 3], emit float array [...]) for the whole grid (only solid cells are kept)."""
    lo = np.floor(np.array(lo, float) / scale) * scale
    hi = np.ceil(np.array(hi, float) / scale) * scale
    n = np.round((hi - lo) / scale).astype(int)
    xs = lo[0] + (np.arange(n[0]) + 0.5) * scale
    ys = lo[1] + (np.arange(n[1]) + 0.5) * scale
    zs = lo[2] + (np.arange(n[2]) + 0.5) * scale
    px, py, pz = [a.astype(np.float32) for a in np.meshgrid(xs, ys, zs, indexing='ij')]
    layers = layers_fn(px, py, pz)
    names = []
    dall = None
    mat = np.full(px.shape, -1, np.int16)
    for m, d in layers:
        if m not in names:
            names.append(m)
        mat[d < 0] = names.index(m)
        dall = d if dall is None else np.minimum(dall, d)
    solid = dall < 0
    if not keep_specks:
        nb = ndimage.convolve(solid.astype(np.int8), np.ones((3, 3, 3), np.int8), mode='constant') - solid
        solid &= nb >= min_nb
    ctx = Ctx()
    ctx.px, ctx.py, ctx.pz, ctx.mat, ctx.names, ctx.d = px, py, pz, mat, names, dall
    ctx.solid, ctx.noise, ctx.scale, ctx.lo = solid, noise, scale, lo
    blur = ndimage.gaussian_filter(solid.astype(np.float32), 1.6 * (S8 / scale) ** 0.5, mode='constant')
    ctx.ao = np.clip((blur - 0.42) * 2.4, 0, 1)         # 0 on open convex skin, 1 deep in a crease or socket
    g = np.gradient(ndimage.gaussian_filter(solid.astype(np.float32), 1.0, mode='constant'))
    nrm = -np.stack(g, axis=-1)
    nrm /= np.maximum(np.linalg.norm(nrm, axis=-1, keepdims=True), 1e-6)
    ctx.normal = nrm
    ctx.is_ = lambda m: (mat == names.index(m)) if m in names else np.zeros(px.shape, bool)
    col, em = paint_fn(ctx)
    col = np.clip(np.where(solid[..., None], col, 0), 0, 255).astype(np.uint8)
    em = np.where(solid, em, 0)
    gr = Grid(name, lo, scale, solid, col, np.clip(em * 255, 0, 255).astype(np.uint8))
    gr.normal = nrm.astype(np.float16)
    gr.ao = ctx.ao.astype(np.float16)
    return gr


def flesh_paint(ctx, ramp=FLESH, base=0.55, noise_f=3.0, vein=0.0, up_light=0.22):
    """Grey-brown skin: a mid base, mottled by noise, darker in creases (AO), a little lighter on upward faces.
    The game adds the real light; this only gives the forms and the dead-skin mottle."""
    n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, noise_f)
    n2 = ctx.noise(ctx.px + 7.3, ctx.py, ctx.pz, noise_f * 2.3)
    t = base + (n - 0.5) * 0.7 + up_light * ctx.normal[..., 1] - ctx.ao * 0.36
    t = np.where(ctx.normal[..., 1] < -0.4, t - 0.12, t)
    col = pick(ramp, t, n2)
    if vein > 0:
        rid = np.abs(ctx.noise.fbm(ctx.px + 3, ctx.py, ctx.pz, noise_f * 0.9) - 0.5)
        col = np.where((rid < vein)[..., None], np.array(ramp)[1] * 0.9, col)
    return col


def standard_paint(ctx, ramp=FLESH, base=0.55, vein=0.012, noise_f=3.0, special=None):
    """Paint by material. special: {material: fn(ctx, mask) -> (rgb, emit)} for the ones that aren't plain flesh."""
    col = flesh_paint(ctx, ramp=ramp, base=base, vein=vein, noise_f=noise_f)
    em = np.zeros(ctx.px.shape, np.float32)
    n = ctx.noise(ctx.px * 2, ctx.py * 2, ctx.pz * 2, 5.0)
    for m in ctx.names:
        if m == 'flesh':
            continue
        mask = ctx.is_(m)
        if special and m in special:
            c, e = special[m](ctx, mask)
            col = np.where(mask[..., None], c, col)
            em = np.where(mask, e, em)
        elif m == 'tooth':
            t = 0.8 - ctx.ao * 0.9 + (n - 0.5) * 0.5
            col = np.where(mask[..., None], pick(TOOTH, t * 0.8 + 0.2, n), col)
        elif m == 'tooth_old':
            t = 0.62 - ctx.ao * 0.8 + (n - 0.5) * 0.7
            col = np.where(mask[..., None], pick(TOOTH_OLD, t, n), col)
        elif m == 'tooth_yel':
            t = 0.7 - ctx.ao * 0.9 + (n - 0.5) * 0.6
            col = np.where(mask[..., None], pick(TOOTH_YEL, t, n), col)
        elif m == 'inner':
            col = np.where(mask[..., None], pick(INNER, 0.1 + n * 0.5, n), col)
        elif m == 'black':
            col = np.where(mask[..., None], BLACK, col)
        elif m == 'redeye':
            col = np.where(mask[..., None], RED_EYE, col)
            em = np.where(mask, 0.9, em)
        elif m.startswith('glow') and m[4:].isdigit():
            # graded dim red glow deep in a pit: glow1 (near black, faint) .. glow4 (dim dark red); never bright
            lv = int(m[4:]) - 1
            gc = [hx('#0c0304'), hx('#1c0507'), hx('#2e0809'), hx('#481012')][min(lv, 3)]
            ge = [0.10, 0.20, 0.32, 0.45][min(lv, 3)]
            col = np.where(mask[..., None], gc, col)
            em = np.where(mask, ge, em)
        elif m == 'glow':
            col = np.where(mask[..., None], GLOW, col)
            em = np.where(mask, 1.0, em)
    return col, em


# ---------------------------------------------------------------- meshes

DIRS = SC.DIRS
FACE_SHADE = SC.FACE_SHADE


def greedy(grid, part):
    """Exposed faces merged into rectangles of one colour and emission (v5's greedy mesher, with emission kept)."""
    solid, col, em, scale, origin = grid.solid, grid.col, grid.em, grid.scale, grid.lo
    quads = []
    sh = solid.shape
    pad = np.pad(solid, 1)
    keyc = (col[..., 0].astype(np.int64) << 24) | (col[..., 1].astype(np.int64) << 16) | \
           (col[..., 2].astype(np.int64) << 8) | em.astype(np.int64)
    for di, (dx, dy, dz) in enumerate(DIRS):
        nb = pad[1 + dx:1 + dx + sh[0], 1 + dy:1 + dy + sh[1], 1 + dz:1 + dz + sh[2]]
        exposed = solid & ~nb
        axis = 0 if dx else (1 if dy else 2)
        uax, vax = {0: (2, 1), 1: (0, 2), 2: (0, 1)}[axis]
        shade = FACE_SHADE[di]
        for sl in range(sh[axis]):
            idx = [slice(None)] * 3
            idx[axis] = sl
            ex = exposed[tuple(idx)]
            if not ex.any():
                continue
            kc = keyc[tuple(idx)]
            rem = [a for a in (0, 1, 2) if a != axis]
            if rem.index(uax) == 1:
                ex, kc = ex.T, kc.T
            used = np.zeros_like(ex)
            U, V = ex.shape
            for u in range(U):
                for v in range(V):
                    if not ex[u, v] or used[u, v]:
                        continue
                    k = kc[u, v]
                    dv = 1
                    while v + dv < V and ex[u, v + dv] and not used[u, v + dv] and kc[u, v + dv] == k:
                        dv += 1
                    du = 1
                    while u + du < U and np.all(ex[u + du, v:v + dv]) and not np.any(used[u + du, v:v + dv]) \
                            and np.all(kc[u + du, v:v + dv] == k):
                        du += 1
                    used[u:u + du, v:v + dv] = True
                    corner = [0, 0, 0]
                    corner[axis] = sl + (1 if (dx + dy + dz) > 0 else 0)
                    corner[uax] = u
                    corner[vax] = v
                    e = int(k & 255)
                    rgb = ((k >> 24) & 255, (k >> 16) & 255, (k >> 8) & 255)
                    sh2 = shade + (1 - shade) * e / 255.0
                    rgb = tuple(int(min(255, c * sh2)) for c in rgb)
                    pos = [origin[i] + corner[i] * scale for i in range(3)]
                    quads.append((di, part, pos, du * scale, dv * scale, rgb, e))
    return quads


def write_mesh(quads, path, unit=16):
    """Positions and sizes are shorts in 1/unit block (unit 16 for every mesh but the core, which has finer voxels: its json says posUnit)."""
    with open(path, 'wb') as f:
        f.write(struct.pack('<i', len(quads)))
        for (di, part, pos, du, dv, rgb, e) in quads:
            f.write(struct.pack('<BBhhhhhBBBB', di, part, *[int(round(c * unit)) for c in pos],
                                int(round(du * unit)), int(round(dv * unit)), *rgb, e))


class Model:
    """A mesh: ordered parts (index 0 is the static body), each its own voxel grid, plus animation and notes."""

    def __init__(self, name, frame_note):
        self.name, self.frame_note = name, frame_note
        self.grids = []
        self.meta = []

    def add(self, grid, pivot=None, anim=None, **extra):
        i = len(self.grids)
        self.grids.append(grid)
        m = dict(name=grid.name, index=i, pivot=[round(float(x), 4) for x in (pivot if pivot is not None else (0, 0, 0))],
                 anim=anim, voxel=round(grid.scale, 5), voxels=grid.n)
        m.update(extra)
        self.meta.append(m)
        return i

    def write(self, out_dir, mesh_name, parts_name, note='', extra=None):
        os.makedirs(out_dir, exist_ok=True)
        quads = []
        counts = []
        for i, g in enumerate(self.grids):
            q = greedy(g, i)
            counts.append(len(q))
            quads += q
        unit = 16
        fine = min(g.scale for g in self.grids)
        if fine < S16 - 1e-9:
            unit = int(round(1.0 / fine))
        write_mesh(quads, os.path.join(out_dir, mesh_name), unit)
        for m, c in zip(self.meta, counts):
            m['quads'] = c
        data = dict(note=note, frame=self.frame_note, mesh=mesh_name, quads=len(quads), voxels=int(sum(g.n for g in self.grids)),
                    parts=self.meta)
        if unit != 16:
            data['posUnit'] = unit          # quad positions/sizes are shorts in 1/posUnit block (absent = 16)
        if extra:
            data.update(extra)
        with open(os.path.join(out_dir, parts_name), 'w') as f:
            json.dump(data, f, indent=1)
        return len(quads), counts


# ---------------------------------------------------------------- animation (the json's `anim` field, used by the preview and phase B)
#
# anim = dict(kind=..., axis=[x,y,z], amp=degrees or blocks, period=ticks, phase=0..1, base=degrees, axis2=[...])
#   sway   rotate about the pivot: amp*sin about `axis`, 0.6*amp*cos about `axis2`  (arms, ropes, heads)
#   hinge  rotate about `axis`: base + amp*(0.5 + 0.5 sin)   (jaws, lids)       degrees, + opens the part
#   blink  rest at `base`, a quick snap of `amp` degrees once per `period`       (eyelids)
#   roll   the eyeball: gaze wanders by amp degrees about the axis and axis2
#   scale  scale about the pivot by 1 + amp*sin on the plane across `axis` (lips flexing, breathing)
#   reach  slide along `axis` by amp*(0.5+0.5 sin) blocks                         (a grab arm's hand)
#   fall   slide along `axis` by amp blocks, a looping drop                       (tears)

def anim_transform(a, t_ticks):
    """-> (R 3x3, scale vector 3, translate 3) about the part's pivot, at time t (ticks)."""
    R, sc, tr = np.eye(3), np.ones(3), np.zeros(3)
    if not a:
        return R, sc, tr
    kind = a['kind']
    ph = a.get('phase', 0.0)
    w = 2 * math.pi * (t_ticks / a.get('period', 60.0) + ph)
    amp = a.get('amp', 0.0)
    if kind == 'sway':
        R = rotmat(a['axis'], amp * math.sin(w))
        if a.get('axis2'):
            R = R @ rotmat(a['axis2'], 0.6 * amp * math.cos(w * 0.8 + 1.0))
    elif kind == 'hinge':
        R = rotmat(a['axis'], a.get('base', 0.0) + amp * (0.5 + 0.5 * math.sin(w)))
    elif kind == 'blink':
        f = (t_ticks / a.get('period', 60.0) + ph) % 1.0
        c = 0.0 if f < 0.88 else math.sin((f - 0.88) / 0.12 * math.pi)
        R = rotmat(a['axis'], a.get('base', 0.0) + amp * c)
    elif kind == 'roll':
        R = rotmat(a['axis'], amp * math.sin(w))
        if a.get('axis2'):
            R = R @ rotmat(a['axis2'], 0.7 * amp * math.cos(w * 0.7 + 0.5))
    elif kind == 'scale':
        k = 1 + amp * math.sin(w)
        ax = np.abs(np.array(a['axis'], float))
        sc = np.where(ax > 0.5, 1.0, k)
    elif kind == 'reach':
        tr = np.array(a['axis'], float) * amp * (0.5 + 0.5 * math.sin(w))
    elif kind == 'fall':
        f = (t_ticks / a.get('period', 40.0) + ph) % 1.0
        tr = np.array(a['axis'], float) * amp * f
    return R, sc, tr


# ---------------------------------------------------------------- the preview rasterizer

class Faces:
    """The exposed faces of a Grid as points: centre, axis normal, smooth normal, base colour, emission."""

    def __init__(self, grid, pivot=None, anim=None, hidden=False, tag=None):
        g = grid
        sh = g.solid.shape
        pad = np.pad(g.solid, 1)
        P, N, NS, C, E, A = [], [], [], [], [], []
        for di, (dx, dy, dz) in enumerate(DIRS):
            nb = pad[1 + dx:1 + dx + sh[0], 1 + dy:1 + dy + sh[1], 1 + dz:1 + dz + sh[2]]
            ex = g.solid & ~nb
            ij = np.argwhere(ex)
            if not len(ij):
                continue
            ctr = g.lo + (ij + 0.5) * g.scale + np.array([dx, dy, dz]) * g.scale * 0.5
            P.append(ctr)
            N.append(np.tile(np.array([dx, dy, dz], float), (len(ij), 1)))
            NS.append(g.normal[ij[:, 0], ij[:, 1], ij[:, 2]].astype(float))
            C.append(g.col[ij[:, 0], ij[:, 1], ij[:, 2]].astype(float))
            E.append(g.em[ij[:, 0], ij[:, 1], ij[:, 2]].astype(float) / 255)
            A.append(g.ao[ij[:, 0], ij[:, 1], ij[:, 2]].astype(float))
        z3 = np.zeros((0, 3))
        self.p = np.concatenate(P) if P else z3
        self.n = np.concatenate(N) if N else z3
        self.ns = np.concatenate(NS) if NS else z3
        self.c = np.concatenate(C) if C else z3
        self.e = np.concatenate(E) if E else np.zeros(0)
        self.ao = np.concatenate(A) if A else np.zeros(0)
        self.scale = g.scale
        self.pivot = np.array(pivot if pivot is not None else (0, 0, 0), float)
        self.anim = anim
        self.hidden = hidden
        self.name = g.name
        self.tag = tag


class Camera:
    def __init__(self, pos, look, up=(0, 1, 0), fov=70, size=(360, 640)):
        self.pos = np.array(pos, float)
        f = np.array(look, float) - self.pos
        f /= np.linalg.norm(f)
        r = np.cross(np.array(up, float), f)          # the devil frames are the hotel's mirrored in x: as v5 did
        r /= np.linalg.norm(r)
        u = np.cross(f, r)
        self.R = np.stack([r, u, f])                  # rows: right, up, forward
        self.size = size
        self.focal = (size[1] / 2) / math.tan(math.radians(fov) / 2)


def pose_faces(fc, t_ticks, extra=None):
    """World-space points and normals of one Faces after its animation (and an optional whole-fixture motion)."""
    p, n, ns = fc.p, fc.n, fc.ns
    if fc.anim:
        R, sc, tr = anim_transform(fc.anim, t_ticks)
        p = ((p - fc.pivot) * sc) @ R.T + fc.pivot + tr
        n = n @ R.T
        ns = ns @ R.T
    if extra is not None:
        R, tr = extra
        p = p @ R.T + tr
        n = n @ R.T
        ns = ns @ R.T
    return p, n, ns


def render(items, cam, lights, rim_lights, ambient=0.5, t_ticks=0.0, bg=(8, 6, 10), shade_fn=None, extra=None,
           rim_power=2.6, rim_gain=1.0):
    """Splat every exposed face (nearest wins). items: list of Faces.
    lights: [(dir_to_light, rgb 0..1, strength)]; rim_lights: [(dir_to_light, rgb 0..255, strength)] only on grazing edges
    that face them (the game's orange-red rim). shade_fn(p, tag) -> per-face multiplier (the hotel's darkening with depth).
    extra: {tag: (R, t)} moves a whole fixture (a turntable)."""
    W, H = cam.size
    P, Cc, Z, SZ = [], [], [], []
    for fc in items:
        if fc.hidden or not len(fc.p):
            continue
        ex = None if extra is None else extra.get(fc.tag)
        p, n, ns = pose_faces(fc, t_ticks, ex)
        cp = (p - cam.pos) @ cam.R.T                      # camera space: x right, y up, z forward
        z = cp[:, 2]
        view = cam.pos - p
        vn = view / np.maximum(np.linalg.norm(view, axis=1, keepdims=True), 1e-6)
        ok = (z > 0.05) & ((n * vn).sum(1) > -0.02)
        if not ok.any():
            continue
        p, n, ns, cp, z, vn = p[ok], n[ok], ns[ok], cp[ok], z[ok], vn[ok]
        base = fc.c[ok]
        em = fc.e[ok]
        ao = fc.ao[ok]
        key = np.zeros((len(p), 3))
        for L, rgb, k in lights:
            L = np.array(L, float)
            L = L / np.linalg.norm(L)
            d = np.clip(n @ L, 0, 1) * 0.6 + np.clip(ns @ L, 0, 1) * 0.4
            key += (k * d)[:, None] * np.array(rgb)[None]
        col = base * (ambient + key) * (1 - 0.45 * ao)[:, None]
        for L, rgb, k in rim_lights:
            L = np.array(L, float)
            L = L / np.linalg.norm(L)
            edge = np.clip(1 - np.clip((ns * vn).sum(1), 0, 1), 0, 1) ** rim_power
            facing_l = np.clip(ns @ L * 0.8 + 0.35, 0, 1)
            col = col + np.array(rgb)[None] * (k * rim_gain * edge * facing_l * (1 - 0.7 * ao))[:, None]
        if shade_fn is not None:
            col = col * shade_fn(p, fc.tag)[:, None]
        col = col * (1 - em[:, None]) + base * em[:, None] * 1.1
        P.append(cp)
        Cc.append(col)
        Z.append(z)
        SZ.append(np.full(len(p), fc.scale))
    img = np.zeros((H, W, 3), np.float32)
    img[:] = np.array(bg, np.float32)
    if not P:
        return img.astype(np.uint8)
    cp = np.concatenate(P)
    col = np.concatenate(Cc)
    z = np.concatenate(Z)
    sz = np.concatenate(SZ)
    f = cam.focal
    sx = (cp[:, 0] / z) * f + W / 2
    sy = (-cp[:, 1] / z) * f + H / 2
    w = np.maximum(sz * f / z, 1.0)               # a face's side in pixels
    x0 = np.floor(sx - w / 2 + 0.5).astype(int)
    y0 = np.floor(sy - w / 2 + 0.5).astype(int)
    wi = np.maximum(np.round(w).astype(int), 1)
    keep = (x0 + wi > 0) & (x0 < W) & (y0 + wi > 0) & (y0 < H)
    x0, y0, wi, z, col = x0[keep], y0[keep], wi[keep], z[keep], col[keep]
    order = np.argsort(-z, kind='stable')
    x0, y0, wi, z, col = x0[order], y0[order], wi[order], z[order], col[order]
    zbuf = np.full(H * W, np.inf, np.float32)
    flat = img.reshape(-1, 3)
    mx = int(wi.max()) if len(wi) else 0
    for oy in range(mx):
        for ox in range(mx):
            m = (wi > ox) & (wi > oy)
            if not m.any():
                continue
            xx, yy = x0[m] + ox, y0[m] + oy
            inb = (xx >= 0) & (xx < W) & (yy >= 0) & (yy < H)
            lin = yy[inb] * W + xx[inb]
            zz = z[m][inb]
            cc = col[m][inb]
            cond = zz < zbuf[lin]
            lin, zz, cc = lin[cond], zz[cond], cc[cond]
            zbuf[lin] = zz
            flat[lin] = cc
    return np.clip(img, 0, 255).astype(np.uint8)


def upscale(im, k):
    return np.repeat(np.repeat(im, k, 0), k, 1)


def save(im, path, k=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(upscale(im, k) if k > 1 else im).save(path)
