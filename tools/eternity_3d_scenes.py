"""Preview scenes for the Eternity Devil's 3D look: the hotel corridor turned into a shaft, and the corridor with the avatar at its
end. Walls are painted voxel faces (16 texels a block) so the previews read like the game; they are not game assets."""
import math

import numpy as np

import eternity_3d_kit as K

hx = K.hx


def _wall(name, axis, plane, lo2, hi2, scale, color_fn, inward):
    """A one-voxel-thick wall. axis: 0 -> plane x = `plane`, in-plane (z, y); 2 -> plane z = `plane`, in-plane (x, y);
    1 -> plane y = `plane`, in-plane (x, z). color_fn(u, v) -> (rgb[n,3], emit[n])."""
    u = np.arange(lo2[0], hi2[0], scale) + scale / 2
    v = np.arange(lo2[1], hi2[1], scale) + scale / 2
    U, V = np.meshgrid(u, v, indexing='ij')
    col, em = color_fn(U, V)
    nu, nv = U.shape
    if axis == 0:
        shape = (1, nv, nu)
        solid = np.ones(shape, bool)
        c = np.zeros(shape + (3,), np.uint8)
        c[0] = np.clip(col, 0, 255).astype(np.uint8).transpose(1, 0, 2)
        e = np.zeros(shape, np.uint8)
        e[0] = np.clip(em * 255, 0, 255).astype(np.uint8).T
        lo = (plane if inward > 0 else plane - scale, lo2[1], lo2[0])
        nrm = (inward, 0, 0)
    elif axis == 2:
        shape = (nu, nv, 1)
        solid = np.ones(shape, bool)
        c = np.zeros(shape + (3,), np.uint8)
        c[:, :, 0] = np.clip(col, 0, 255).astype(np.uint8)
        e = np.zeros(shape, np.uint8)
        e[:, :, 0] = np.clip(em * 255, 0, 255).astype(np.uint8)
        lo = (lo2[0], lo2[1], plane if inward > 0 else plane - scale)
        nrm = (0, 0, inward)
    else:
        shape = (nu, 1, nv)
        solid = np.ones(shape, bool)
        c = np.zeros(shape + (3,), np.uint8)
        c[:, 0, :] = np.clip(col, 0, 255).astype(np.uint8)
        e = np.zeros(shape, np.uint8)
        e[:, 0, :] = np.clip(em * 255, 0, 255).astype(np.uint8)
        lo = (lo2[0], plane if inward > 0 else plane - scale, lo2[1])
        nrm = (0, inward, 0)
    g = K.Grid(name, lo, scale, solid, c, e)
    g.normal = np.broadcast_to(np.array(nrm, np.float16), shape + (3,)).copy()
    g.ao = np.zeros(shape, np.float16)
    return g


def _rect(U, V, u0, u1, v0, v1):
    return (U >= u0) & (U < u1) & (V >= v0) & (V < v1)


def noise2(U, V, f, seed):
    rng = np.random.default_rng(seed)
    t = rng.random((64, 64)).astype(np.float32)
    from scipy import ndimage
    c = np.stack([np.ravel(U) * f, np.ravel(V) * f]) % 64
    return ndimage.map_coordinates(t, c, order=0, mode='grid-wrap').reshape(U.shape)


# ---- the hotel's surfaces (16 texels a block)

def carpet(U, V):
    """Across the corridor U (-1.5..1.5), along it V. Dark red-brown, a diamond lattice, yellow dashes, borders."""
    n = noise2(U, V, 16, 1)
    base = np.where((n > 0.5)[..., None], hx('#5c2220'), hx('#552020'))
    ux, vx = np.floor(U * 8).astype(int), np.floor(V * 8).astype(int)
    diamond = ((np.abs(((U * 4) % 1) - 0.5) + np.abs(((V * 4) % 1) - 0.5)) < 0.42)
    col = np.where(diamond[..., None], hx('#6e2a26'), base)
    dash = (np.abs(U) < 0.07) & (((V * 2) % 1) < 0.5)
    col = np.where(dash[..., None], hx('#b08a3a'), col)
    border = (np.abs(U) > 1.2) & (np.abs(U) < 1.3)
    col = np.where(border[..., None], hx('#a88438'), col)
    edge = np.abs(U) > 1.3
    col = np.where(edge[..., None], hx('#2c1410'), col)
    return col, np.zeros(U.shape)


def ceiling(U, V):
    n = noise2(U, V, 16, 2)
    col = np.where((n > 0.5)[..., None], hx('#6c5a44'), hx('#665440'))
    for k in range(-1, 12):
        v0 = 0.7 + k * 2.5
        pan = _rect(U, V, -0.72, 0.72, v0, v0 + 1.1)
        rim = _rect(U, V, -0.82, 0.82, v0 - 0.1, v0 + 1.2) & ~pan
        col = np.where(rim[..., None], hx('#2c2016'), col)
        col = np.where(pan[..., None], hx('#d8c080'), col)
    em = np.zeros(U.shape)
    for k in range(-1, 12):
        v0 = 0.7 + k * 2.5
        em = np.where(_rect(U, V, -0.72, 0.72, v0, v0 + 1.1), 0.55, em)
    return col, em


def doorwall(U, V, doors, lamps, open_at=None, seed=3):
    """U runs 0 (the carpet, the corridor floor) to 3 (the ceiling): the wall's height; V along the shaft."""
    h = U
    n = noise2(U, V, 16, seed)
    stripe = (np.floor(V * 8) % 2 == 0)
    col = np.where(stripe[..., None], hx('#8c7452'), hx('#80694a'))
    col = np.where((n > 0.8)[..., None], col * 0.93, col)
    col = np.where((h < 0.28)[..., None], hx('#2c1c12'), col)                      # skirting
    col = np.where(((h > 0.28) & (h < 0.34))[..., None], hx('#a08a64'), col)
    col = np.where((h > 2.85)[..., None], hx('#4a3a2a'), col)                      # the cornice
    em = np.zeros(U.shape)
    for v0 in doors:
        frame = _rect(h, V, 0.0, 2.2, v0 - 0.09, v0 + 1.09)
        door = _rect(h, V, 0.0, 2.1, v0, v0 + 1.0)
        col = np.where(frame[..., None], hx('#241810'), col)
        panels = door & ((_rect(h, V, 0.15, 0.95, v0 + 0.12, v0 + 0.88)) | (_rect(h, V, 1.1, 1.95, v0 + 0.12, v0 + 0.88)))
        col = np.where(door[..., None], hx('#4a2c1a'), col)
        col = np.where(panels[..., None], hx('#38200f'), col)
        plate = _rect(h, V, 1.55, 1.75, v0 + 0.36, v0 + 0.66)
        col = np.where(plate[..., None], hx('#d2a43c'), col)
        knob = _rect(h, V, 0.95, 1.05, v0 + 0.82, v0 + 0.9)
        col = np.where(knob[..., None], hx('#c89a38'), col)
    for v0 in lamps:
        sconce = _rect(h, V, 2.15, 2.65, v0 - 0.12, v0 + 0.12)
        bulb = _rect(h, V, 2.28, 2.5, v0 - 0.07, v0 + 0.07)
        col = np.where(sconce[..., None], hx('#3a2818'), col)
        col = np.where(bulb[..., None], hx('#ffd890'), col)
        em = np.where(bulb, 0.9, em)
    if open_at is not None:
        hole = _rect(h, V, 0.0, 2.2, open_at - 0.1, open_at + 1.1)
        col = np.where(hole[..., None], hx('#0c0806'), col)
    return col, em


def hotel_shaft(top=17.5, bottom=0.0, scale=1 / 16, open_at=None):
    """The corridor rolled onto its end: 3 x 3, the carpet one wall, the ceiling's light panels the opposite one, the door walls
    the other two (the carpet side is their floor). Y up; the shaft's centre line is X = Z = 0."""
    out = []
    lo2 = (-1.5, bottom)
    hi2 = (1.5, top)
    doors1 = [1.7 + 4.0 * k for k in range(0, 4)]
    doors2 = [3.7 + 4.0 * k for k in range(0, 4)]
    lamps1 = [d + 2.0 for d in doors1]
    lamps2 = [d + 2.0 for d in doors2]
    out.append(_wall('carpet', 0, -1.5, lo2, hi2, scale, carpet, +1))
    out.append(_wall('ceiling', 0, 1.5, lo2, hi2, scale, ceiling, -1))
    out.append(_wall('doors_a', 2, -1.5, lo2, hi2, scale,
                     lambda U, V: doorwall(U + 1.5, V, doors1, lamps1, open_at, 3), +1))
    # wall D: its height runs the other way (carpet at x = -1.5 is height 0 for both)
    out.append(_wall('doors_b', 2, 1.5, lo2, hi2, scale,
                     lambda U, V: doorwall(U + 1.5, V, doors2, lamps2, None, 4), -1))
    return out


def depth_shade(top=16.0, floor_y=1.5):
    def fn(p, tag):
        if tag != 'hotel':
            return np.ones(len(p))
        t = np.clip((p[:, 1] - floor_y) / (top - floor_y), 0, 1)
        return 1.5 * (0.22 + 0.78 * t ** 1.1)
    return fn


# ---- the corridor to the avatar (before the tilt)

def corridor(length=14.0, scale=1 / 16, x0=0.0, tear_x=6.4):
    """The hotel's corridor running along +X from the devil's end wall (x = 0) toward the players; 3 wide (z -1.5..1.5), 3 tall."""
    out = []
    # carpet floor y = 0
    out.append(_wall('c_floor', 1, 0.0, (x0, -1.5), (x0 + length, 1.5), scale, lambda U, V: carpet(V, U), +1))
    out.append(_wall('c_ceil', 1, 3.0, (x0, -1.5), (x0 + length, 1.5), scale, lambda U, V: ceiling(V, U), -1))
    doors1 = [x0 + 2.6 + 4.0 * k for k in range(0, 4)]
    doors2 = [x0 + 4.6 + 4.0 * k for k in range(0, 4)]

    def wallA(U, V):      # U along x, V is height; doorwall wants (height, along)
        return doorwall(V, U, doors1, [d + 2.0 for d in doors1], None, 3)

    def wallB(U, V):
        return doorwall(V, U, doors2, [d + 2.0 for d in doors2], None, 4)
    out.append(_wall('c_wall_a', 2, -1.5, (x0, 0.0), (x0 + length, 3.0), scale, wallA, +1))
    out.append(_wall('c_wall_b', 2, 1.5, (x0, 0.0), (x0 + length, 3.0), scale, wallB, -1))
    if tear_x:
        for g in out:                       # the walls and ceiling are torn away where the devil swells into the rooms beside
            if g.name == 'c_floor':
                continue
            sh = g.solid.shape
            xs = g.lo[0] + (np.arange(sh[0]) + 0.5) * g.scale
            along = np.arange(sh[2] if g.name == 'c_ceil' else sh[1]) * g.scale
            rag = (noise2(along[None, :], np.ones((1, len(along))), 5.0, 9)[0] - 0.5) * 1.4
            mask = xs[:, None] < (tear_x + rag)[None, :]
            if g.name == 'c_ceil':
                g.solid &= ~mask[:, None, :]
            else:
                g.solid &= ~mask[:, :, None]
    return out
