"""The Leech Devil's look (the fact sheet, chainsawman/bible/leech_devil.md): a big wrinkled, sagging body on four legs with two
long tentacle arms in front; every limb ends in a round sucker mouth (a ring of small teeth, a pale lip, dark red inside); the
face is one large mouth full of blocky teeth, no eyes, long black hair hanging over it; three pairs of udder-like glands under
the body. The anime's colours: dark, wet, real-leech olive black with a paler belly and a wet sheen.

Built with the Eternity Devil's kit (tools/eternity_3d_kit.py: signed distances -> voxels -> merged quads), like the Tomato
(tools/tomato_devil_3d.py), into two meshes the game draws with FleshMesh:

    leech_body_mesh.bin / leech_body_parts.json    the body (1/16 block voxels, part 0, static) and the head, drawn on the
        neck's turn by the renderer: head (1/32), jaw (1/32, hinged at its pivot about X: + opens it), hair_bangs, hair_fl,
        hair_fr, hair_l, hair_r, hair_back (1/32, each swayed about its pivot, the crown).
    leech_parts_mesh.bin / leech_parts_parts.json  1/32 block voxels, one of each moving piece, drawn many times:
        thigh (hip at the origin, along +Y, THIGH long), shin (knee at the origin, along +Y, SHIN long), tseg (a tentacle's
        segment along +Y, TSEG long), sucker (the limb's end at the origin, the mouth opening toward +Y), tongue (a segment
        along +Y, TONGUE_SEG long), tongue_tip (along +Y, a point at its end).

Frame of the body: blocks, the ground under the middle of the body at the origin (standing), its front (the mouth) toward +Z,
up +Y, its left +X.

    py -3.12 tools/leech_devil_3d.py build       # write both meshes to the game's resources
    py -3.12 tools/leech_devil_3d.py textures    # the sucker's item picture
    py -3.12 tools/leech_devil_3d.py preview     # the rest pose from four sides -> <main checkout>/work/leech/look_rest.png
"""
import math
import zlib
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_3d_kit as K  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..'))
MAIN = os.path.abspath(os.path.join(ROOT, '..', '..', '..')) if '.claude' in ROOT else ROOT
OUT = os.path.join(MAIN, 'work', 'leech')
OUT_ASSETS = os.path.join(ROOT, 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'models', 'entity')
TEX = os.path.join(ROOT, 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'textures')

hx = K.hx
# A real leech: olive black on the back, a paler olive-brown belly, a wet sheen; dull ochre spots in two rows down the back.
BACK = [hx(c) for c in ('#0b0d08', '#12150d', '#191d12', '#222717', '#2b311d', '#353c24', '#41482c')]
BELLY = [hx(c) for c in ('#26261a', '#343323', '#44412d', '#545037', '#655e42', '#76704f')]
SHEEN = hx('#b2b896')
SPOT = [hx(c) for c in ('#26240f', '#323014', '#3e3a18')]
LIP = [hx(c) for c in ('#5c4e44', '#786658', '#96806e', '#b09a86', '#c8b49e')]
INNER = [hx(c) for c in ('#140205', '#24050a', '#3a0a10', '#541218', '#6c1c20')]
TONGUE = [hx(c) for c in ('#3c0a12', '#5a1420', '#7a2230', '#96343e', '#ac4a50')]
TOOTH = [hx(c) for c in ('#6e6448', '#968a64', '#bcb088', '#d8cea8', '#ebe4c6')]
HAIR = [hx(c) for c in ('#030304', '#08080a', '#0e0f12', '#16171c', '#22242b', '#353843')]
GLAND = [hx(c) for c in ('#3a3028', '#4c3e34', '#5e4c42', '#725c50', '#866c5e')]

# ---- the measures the game uses too (LeechDevilRenderer)
THIGH = 1.25
SHIN = 1.20
TSEG = 0.40
TONGUE_SEG = 0.50
SUCKER = 1.9             # how much bigger than round 1 the sucker mouths are
ANKLE_UP = 0.17 * SUCKER  # the ankle above the ground with the sucker flat on it
# the hips (+X left), the feet's rest on the ground, the tentacles' shoulders, the neck's pivot and the jaw's
HIPS = [(0.95, 1.15, 1.00), (-0.95, 1.15, 1.00), (1.05, 1.30, -1.50), (-1.05, 1.30, -1.50)]
FEET = [(1.80, 0.0, 1.55), (-1.80, 0.0, 1.55), (1.90, 0.0, -2.00), (-1.90, 0.0, -2.00)]
SHOULDERS = [(0.85, 1.55, 1.55), (-0.85, 1.55, 1.55)]
NECK = (0.0, 1.70, 1.40)
MOUTH_Y = 1.40           # the gape's line
JAW_PIVOT = (0.0, MOUTH_Y + 0.02, 1.48)
CROWN = (0.0, 2.36, 1.90)
HEAD_C = (0.0, 1.62, 2.02)
HEAD_R = (0.84, 0.80, 0.80)
CAV_C = (0.0, MOUTH_Y, 1.98)
CAV_R = (0.60, 0.44, 0.60)


def soft(ctx, field, sigma=1.1):
    from scipy import ndimage
    w = ctx.solid.astype(np.float32)
    num = ndimage.gaussian_filter(field * w, sigma, mode='constant')
    den = ndimage.gaussian_filter(w, sigma, mode='constant')
    return np.where(den > 1e-4, num / np.maximum(den, 1e-4), field)


def blend(ramp, t, steps=4):
    r = np.array(ramp, np.float32)
    x = np.clip(t, 0, 1) * (len(r) - 1)
    x = np.round(x * steps) / steps
    i = np.clip(np.floor(x).astype(int), 0, len(r) - 2)
    f = (x - i)[..., None]
    return r[i] * (1 - f) + r[i + 1] * f


LIGHT = np.array([-0.35, 0.85, 0.4]) / np.linalg.norm([-0.35, 0.85, 0.4])


def skin(ctx, belly_dir=(0.0, -1.0, 0.0), spots=False, base=0.5, pale=1.0):
    """Leech skin: olive black, the side toward belly_dir paler, darker in every crease and ring, broken wet highlights."""
    n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 2.6)
    lit = soft(ctx, (ctx.normal * LIGHT).sum(-1))
    bd = np.array(belly_dir, float)
    under = soft(ctx, (ctx.normal * bd).sum(-1))
    t = base + 0.28 * lit + (n - 0.5) * 0.22 - ctx.ao * 0.85
    back = blend(BACK, soft(ctx, t))
    bel = blend(BELLY, soft(ctx, t + 0.05))
    k = (np.clip((under + 0.1) * 1.5, 0, 1) * pale)[..., None]
    col = back * (1 - k) + bel * k
    if spots:
        # two rows of dull ochre spots along the back, either side of the spine
        sp = ctx.noise(ctx.px * 3.0, ctx.py * 3.0, ctx.pz * 3.0, 1.0)
        row = (np.abs(np.abs(ctx.px) - 0.42) < 0.12) & (ctx.normal[..., 1] > 0.35) & (sp > 0.62) & (np.sin(ctx.pz * 7.0) > 0.2)
        col = np.where(row[..., None], blend(SPOT, 0.3 + 0.6 * lit), col)
    # the wet: a glossy pale sheen where the light lies, in broken patches
    wet = (lit > 0.78) & (ctx.ao < 0.12) & (ctx.noise(ctx.px * 5.0, ctx.py * 5.0, ctx.pz * 5.0, 1.0) > 0.58)
    col = np.where(wet[..., None], col * 0.4 + SHEEN * 0.6, col)
    return col


def rings(p, freq, depth, noise=None, warp=0.0):
    """The annuli: narrow creases every 1/freq blocks along a coordinate (positive: pushes the surface in)."""
    q = p * freq + (0.0 if noise is None else warp * noise)
    f = q - np.floor(q)
    return depth * np.exp(-((f - 0.5) / 0.11) ** 2)


# ---------------------------------------------------------------- the body

_N = K.Noise(41)


# The body's profile front to back (z): the top of each roll, the bottom (the belly sagging lowest in the middle), the half width.
PROFILE_Z = [-2.5, -1.6, -0.6, 0.3, 1.2]
PROFILE_TOP = [2.55, 3.0, 2.85, 2.6, 2.4]
PROFILE_BOTTOM = [1.5, 0.72, 0.36, 0.55, 1.15]
PROFILE_W = [0.95, 1.32, 1.38, 1.22, 1.0]
ROLLS = [-2.45 + 0.52 * i for i in range(8)]


def profile(z):
    return (float(np.interp(z, PROFILE_Z, PROFILE_TOP)), float(np.interp(z, PROFILE_Z, PROFILE_BOTTOM)), float(np.interp(z, PROFILE_Z, PROFILE_W)))


def torso_sdf(px, py, pz):
    """A leech's body: a chain of fat rolls, front to back, barely smoothed together so the creases between them are deep
    (shapes you see from across a field, not paint); the middle rolls sag low between the legs; loose folds of skin hang off
    each roll's lower sides."""
    d = None
    for z in ROLLS:
        top, bot, w = profile(z)
        cy, ry = (top + bot) / 2, (top - bot) / 2
        roll = K.ell(px, py, pz, (0, cy, z), (w, ry, 0.44))
        # loose skin hanging off its lower sides, a fold under each roll
        for sx in (1, -1):
            roll = K.smin(roll, K.ell(px, py, pz, (sx * w * 0.72, bot + ry * 0.38, z + 0.04), (0.42, 0.34, 0.36)), 0.08)
        d = roll if d is None else K.smin(d, roll, 0.07)
    d = K.smin(d, K.ell(px, py, pz, (0, 1.70, 1.45), (0.70, 0.62, 0.60)), 0.2)        # the neck
    for h in HIPS:
        d = K.smin(d, K.ell(px, py, pz, (h[0] * 0.92, h[1], h[2]), (0.44, 0.40, 0.44)), 0.15)
    for sh in SHOULDERS:
        d = K.smin(d, K.ell(px, py, pz, (sh[0] * 0.92, sh[1], sh[2] - 0.05), (0.40, 0.38, 0.40)), 0.14)
    n = _N.fbm(px, py, pz, 1.4, 2)
    # finer wrinkles across each roll, and lumps
    d = d + rings(pz, 5.0, 0.025, n, 0.8)
    d = d - 0.07 * (n - 0.5)
    return d


GLANDS = [(sx * 0.45, z) for z in (-1.25, -0.5, 0.25) for sx in (1, -1)]


def gland_sdf(px, py, pz):
    d = None
    for x, z in GLANDS:
        bot = max(profile(z)[1] + 0.1, 0.42)
        g = K.ell(px, py, pz, (x, bot - 0.08, z), (0.2, 0.28, 0.2))
        g = K.smin(g, K.caps(px, py, pz, (x, bot + 0.25, z), (x, bot - 0.1, z), 0.16, 0.18), 0.08)
        g = K.smin(g, K.caps(px, py, pz, (x, bot - 0.25, z), (x * 1.05, bot - 0.42, z + 0.02), 0.07, 0.04), 0.05)   # the teat
        d = g if d is None else np.minimum(d, g)
    return d


def body_model():
    noise = K.Noise(3)

    def layers(px, py, pz):
        t = torso_sdf(px, py, pz)
        gl = gland_sdf(px, py, pz)
        return [('flesh', t), ('gland', np.maximum(gl, -(t + 0.02)))]

    def paint(ctx):
        col = skin(ctx, spots=True, base=0.52)
        n = ctx.noise(ctx.px * 4, ctx.py * 4, ctx.pz * 4, 3.0)
        m = ctx.is_('gland')
        lit = soft(ctx, (ctx.normal * LIGHT).sum(-1))
        gcol = blend(GLAND, soft(ctx, 0.55 + 0.25 * lit + (n - 0.5) * 0.3 - ctx.ao * 0.7))
        tip = ctx.py < 0.25
        gcol = np.where(tip[..., None], gcol * 0.75 + INNER[3] * 0.25, gcol)
        col = np.where(m[..., None], gcol, col)
        return col, np.zeros(ctx.px.shape, np.float32)

    return K.voxelize('body', (-1.95, -0.05, -3.0), (1.95, 3.2, 2.0), K.S16, layers, paint, noise, min_nb=3)


# ---------------------------------------------------------------- the head: one big mouth, no eyes, hair over it

MOUTH_HW = 0.68          # half the mouth's width, nearly the whole face
SLIT_H = 0.16            # half the gap between the lips at the middle (the teeth fill it shut)


def slit_sdf(px, py, pz):
    """The mouth's opening: a wide slit across the front of the head, tallest in the middle."""
    hgt = SLIT_H * np.sqrt(np.clip(1 - (px / MOUTH_HW) ** 2, 0, 1)) + 0.025
    return np.maximum(np.maximum(np.abs(py - MOUTH_Y) - hgt, np.abs(px) - MOUTH_HW), 2.05 - pz)


def head_sdf(px, py, pz):
    d = K.ell(px, py, pz, HEAD_C, HEAD_R)
    d = K.smin(d, K.ell(px, py, pz, (0, 1.70, 1.45), (0.70, 0.62, 0.62)), 0.25)
    n = _N.fbm(px + 3.0, py, pz, 2.0, 2)
    d = d + rings(py, 3.0, 0.02, n, 0.5) * (pz < 2.3)
    d = d - 0.03 * (n - 0.5)
    # thick lips round the slit
    sl = slit_sdf(px, py, pz)
    d = d - 0.08 * np.exp(-(np.maximum(sl, 0) / 0.07) ** 2) * (pz > 2.0) * (np.abs(px) < MOUTH_HW + 0.12)
    return d


def cav_sdf(px, py, pz):
    """The inside of the mouth: a big throat behind the slit (and the slit itself)."""
    return np.minimum(K.ell(px, py, pz, CAV_C, CAV_R), slit_sdf(px, py, pz))


def jaw_zone(px, py, pz):
    return (py < MOUTH_Y) & (pz > JAW_PIVOT[2] - 0.03)


def front_z(x, y):
    """The head's front at (x, y)."""
    k = 1 - (x / HEAD_R[0]) ** 2 - ((y - HEAD_C[1]) / HEAD_R[1]) ** 2
    return HEAD_C[2] + HEAD_R[2] * math.sqrt(max(0.0, k))


def teeth_sdf(px, py, pz, upper):
    """Blocky teeth along the slit just behind the lips: the upper hang down to the gape, the lower stand up to it, so shut
    the mouth is a band of teeth from corner to corner."""
    d = None
    rng = np.random.default_rng(7 if upper else 8)
    n_t = 12 if upper else 11
    for i in range(n_t):
        u = -1 + (i + (0.5 if upper else 1.0)) * 2.0 / (n_t + (0.0 if upper else 1.0))
        x = MOUTH_HW * 0.94 * u
        hgt = SLIT_H * math.sqrt(max(0.0, 1 - (x / MOUTH_HW) ** 2)) + 0.025
        z = front_z(x, MOUTH_Y) - 0.07 - 0.02 * abs(u)
        length = hgt + 0.10 + rng.uniform(-0.025, 0.02)
        w = 0.036 + rng.uniform(-0.004, 0.008)
        if upper:
            c = (x, MOUTH_Y + 0.004 + length / 2, z)
        else:
            c = (x, MOUTH_Y - 0.004 - length / 2, z)
        # each turned to follow the face round
        th = math.atan2(x, max(0.2, front_z(x, MOUTH_Y) - HEAD_C[2]))
        ca, sa = math.cos(th), math.sin(th)
        lx = (px - c[0]) * ca - (pz - c[2]) * sa
        lz = (px - c[0]) * sa + (pz - c[2]) * ca
        tt = K.box(lx, py - c[1], lz, (0, 0, 0), (w, length / 2, 0.05), 0.012)
        d = tt if d is None else np.minimum(d, tt)
    return d


def head_model():
    noise = K.Noise(9)
    lo, hi = (-0.95, 0.75, 0.75), (0.95, 2.50, 2.95)

    def mouth_paint(ctx, col, lower):
        cav = cav_sdf(ctx.px, ctx.py, ctx.pz)
        sl = slit_sdf(ctx.px, ctx.py, ctx.pz)
        lit = soft(ctx, (ctx.normal * LIGHT).sum(-1))
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 5.0)
        lip = ctx.is_('flesh') & (sl < 0.16) & (ctx.pz > 2.05) & (np.abs(ctx.px) < MOUTH_HW + 0.14)
        col = np.where(lip[..., None], blend(LIP, soft(ctx, 0.55 + 0.35 * lit + (n - 0.5) * 0.3 - ctx.ao * 0.6)), col)
        rid = (np.abs(np.sin(ctx.px * 38.0 + (n - 0.5) * 2.0)) < 0.2) & lip
        col = np.where(rid[..., None], col * 0.7, col)
        cut = ctx.is_('flesh') & ((ctx.py > MOUTH_Y - 0.07) if lower else (ctx.py < MOUTH_Y + 0.07)) & (ctx.pz > JAW_PIVOT[2])
        fz = HEAD_C[2] + HEAD_R[2] * np.sqrt(np.clip(1 - (ctx.px / HEAD_R[0]) ** 2 - ((ctx.py - HEAD_C[1]) / HEAD_R[1]) ** 2, 0, 1))
        deep = ctx.is_('flesh') & (((sl < 0.04) & (ctx.pz < fz - 0.1)) | (K.ell(ctx.px, ctx.py, ctx.pz, CAV_C, CAV_R) < 0.05))
        inner = ctx.is_('inner') | cut | deep
        col = np.where(inner[..., None], blend(INNER, 0.25 + (n - 0.5) * 0.5 + 0.3 * (ctx.pz - 1.9)), col)
        m = ctx.is_('tongue')
        col = np.where(m[..., None], blend(TONGUE, 0.5 + 0.3 * lit + (n - 0.5) * 0.4 - ctx.ao * 0.5), col)
        m = ctx.is_('tooth')
        col = np.where(m[..., None], K.pick(TOOTH, 0.88 - ctx.ao * 1.1 + (n - 0.5) * 0.3 + 0.12 * lit, n), col)
        return col

    def head_layers(px, py, pz):
        h = head_sdf(px, py, pz)
        cav = cav_sdf(px, py, pz)
        jz = jaw_zone(px, py, pz)
        flesh = np.where(jz, 1.0, np.maximum(h, -cav))
        thr = K.ell(px, py, pz, CAV_C, CAV_R)
        lining = np.where(jz, 1.0, np.maximum(np.maximum(h, thr - 0.09), -cav))
        teeth = np.maximum(teeth_sdf(px, py, pz, True), MOUTH_Y - py)     # (nothing below the gape)
        teeth = np.maximum(teeth, h)
        return [('flesh', flesh), ('inner', lining), ('tooth', teeth)]

    def head_paint(ctx):
        col = skin(ctx, belly_dir=(0.0, -0.6, 0.6), base=0.5)
        return mouth_paint(ctx, col, False), np.zeros(ctx.px.shape, np.float32)

    head = K.voxelize('head', lo, hi, K.S32, head_layers, head_paint, noise, min_nb=3)

    def jaw_layers(px, py, pz):
        h = head_sdf(px, py, pz)
        cav = cav_sdf(px, py, pz)
        jz = jaw_zone(px, py, pz)
        flesh = np.where(jz, np.maximum(h, -cav), 1.0)
        thr = K.ell(px, py, pz, CAV_C, CAV_R)
        lining = np.where(jz, np.maximum(np.maximum(h, thr - 0.09), -cav), 1.0)
        tongue = np.where(jz, K.ell(px, py, pz, (0, MOUTH_Y - 0.30, CAV_C[2]), (0.36, 0.12, 0.44)), 1.0)
        teeth = np.maximum(teeth_sdf(px, py, pz, False), py - MOUTH_Y)
        teeth = np.maximum(teeth, h)
        return [('flesh', flesh), ('inner', lining), ('tongue', tongue), ('tooth', teeth)]

    def jaw_paint(ctx):
        col = skin(ctx, belly_dir=(0.0, -1.0, 0.3), base=0.5)
        return mouth_paint(ctx, col, True), np.zeros(ctx.px.shape, np.float32)

    jaw = K.voxelize('jaw', (-0.95, 0.75, 1.40), (0.95, MOUTH_Y + 0.03, 2.95), K.S32, jaw_layers, jaw_paint, noise, min_nb=2)
    return head, jaw


def head_point(az_deg, el_deg, lift=1.05):
    """A point on (just off) the head's skin: azimuth from the front (+Z) toward its left (+X), elevation up."""
    a, e = math.radians(az_deg), math.radians(el_deg)
    d = np.array([math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e)])
    return np.array(HEAD_C) + np.array(HEAD_R) * d * lift


# The hair: (name, azimuths, where it leaves the head's skin (elevation), how far down it hangs, how many strands)
HAIR_GROUPS = [
    ('hair_bangs', (-15.0, 15.0), 30.0, 1.86, 7),
    ('hair_fl', (24.0, 58.0), 6.0, 0.12, 7),
    ('hair_fr', (-58.0, -24.0), 6.0, 0.12, 7),
    ('hair_l', (58.0, 118.0), 0.0, 0.10, 8),
    ('hair_r', (-118.0, -58.0), 0.0, 0.10, 8),
    ('hair_back', (125.0, 235.0), -8.0, 1.70, 10),
]


def strands(group):
    name, (a0, a1), el_fall, y_end, count = group
    rng = np.random.default_rng(zlib.crc32(name.encode()))
    out = []
    for i in range(count):
        az = a0 + (a1 - a0) * (i + 0.5) / count + rng.uniform(-3, 3)
        pts = []
        # over the skull from the crown to where it falls
        for e in np.linspace(78.0, el_fall, 6):
            pts.append(head_point(az, e, 1.06 + 0.02 * rng.random()))
        top = pts[-1]
        if top[1] - 0.05 > y_end:
            # hanging down from there, flaring out a little, waving, to its end (the long ones drag on the ground)
            out_dir = np.array([math.sin(math.radians(az)), 0.0, math.cos(math.radians(az))])
            steps = 7
            for k in range(1, steps + 1):
                f = k / steps
                y = top[1] + (y_end + rng.uniform(0.0, 0.2) - top[1]) * f
                wob = 0.05 * math.sin(f * 7.0 + i * 1.3)
                p = top + out_dir * (0.18 * f ** 0.7) + np.array([wob, 0.0, 0.03 * f]) * (1 if az >= 0 else -1)
                p[1] = y
                pts.append(p)
        out.append([tuple(p) for p in pts])
    return out


def hair_models():
    noise = K.Noise(13)
    grids = []
    for grp in HAIR_GROUPS:
        lines = strands(grp)
        allp = np.array([p for ln in lines for p in ln])
        lo = allp.min(0) - 0.12
        hi = allp.max(0) + 0.12

        def layers(px, py, pz, lines=lines):
            d = None
            for ln in lines:
                s = K.polyline(px, py, pz, ln, 0.072, 0.05)
                d = s if d is None else np.minimum(d, s)
            # (none inside the skull)
            d = np.maximum(d, -(head_sdf(px, py, pz) - 0.01))
            return [('hair', d)]

        def paint(ctx):
            n = ctx.noise(ctx.px * 12, ctx.py * 2, ctx.pz * 12, 2.0)
            lit = soft(ctx, (ctx.normal * LIGHT).sum(-1), 0.8)
            col = blend(HAIR, 0.25 + 0.3 * lit + (n - 0.5) * 0.5 - ctx.ao * 0.4)
            shine = (lit > 0.6) & (n > 0.62)
            col = np.where(shine[..., None], HAIR[5], col)
            return col, np.zeros(ctx.px.shape, np.float32)

        grids.append(K.voxelize(grp[0], lo, hi, K.S32, layers, paint, noise, min_nb=1))
    return grids


# ---------------------------------------------------------------- the pieces

def parts_model():
    noise = K.Noise(5)
    s = K.S32

    def limb_paint(ctx):
        col = skin(ctx, belly_dir=(0.0, 0.0, 1.0), base=0.5, pale=0.45)
        return col, np.zeros(ctx.px.shape, np.float32)

    def thigh(px, py, pz):
        d = K.caps(px, py, pz, (0, -0.05, 0), (0, THIGH, 0), 0.43, 0.30)
        d = K.smin(d, K.ell(px, py, pz, (0, 0.45, -0.05), (0.44, 0.5, 0.42)), 0.1)
        d = d + rings(py, 2.8, 0.06)
        return [('flesh', d)]

    def shin(px, py, pz):
        # the knee: a big knob at the top, then a thick shin tapering to the ankle
        d = K.caps(px, py, pz, (0, -0.03, 0), (0, SHIN + 0.04, 0), 0.31, 0.22)
        d = K.smin(d, K.sphere(px, py, pz, (0, 0.0, -0.06), 0.36), 0.06)
        d = d + rings(py, 3.2, 0.05)
        return [('flesh', d)]

    th = K.voxelize('thigh', (-0.5, -0.5, -0.5), (0.5, THIGH + 0.3, 0.5), s, thigh, limb_paint, noise)
    sh = K.voxelize('shin', (-0.45, -0.45, -0.45), (0.45, SHIN + 0.25, 0.45), s, shin, limb_paint, noise)

    def tseg(px, py, pz):
        d = K.caps(px, py, pz, (0, -0.04, 0), (0, TSEG + 0.04, 0), 0.17, 0.155)
        d = d + rings(py + 0.2, 1.0 / TSEG * 2.0, 0.028)
        return [('flesh', d)]

    ts = K.voxelize('tseg', (-0.22, -0.1, -0.22), (0.22, TSEG + 0.1, 0.22), s, tseg, limb_paint, noise)

    def sucker(px, py, pz):
        px, py, pz = px / SUCKER, py / SUCKER, pz / SUCKER
        r = np.sqrt(px ** 2 + pz ** 2)
        # the cup flaring from the limb's end to the lip
        cup = np.maximum(r - (0.165 + 0.55 * np.clip(py, 0, 0.16)), np.maximum(-py - 0.02, py - 0.15))
        # the lip: a fat pale ring round its mouth
        lip = np.sqrt((r - 0.205) ** 2 + (py - 0.15) ** 2) - 0.062
        mouth = np.maximum(r - 0.17, -(py - 0.07))
        flesh = np.maximum(K.smin(cup, lip, 0.03), -mouth)
        inside = np.maximum(np.maximum(r - 0.19, np.abs(py - 0.065) - 0.02), -(r - 0.0))
        # a ring of small teeth just inside the lip, pointing in toward the middle
        teeth = None
        for k in range(12):
            a = k * math.tau / 12
            c0 = (math.sin(a) * 0.17, 0.14, math.cos(a) * 0.17)
            c1 = (math.sin(a) * 0.10, 0.16, math.cos(a) * 0.10)
            t = K.caps(px, py, pz, c0, c1, 0.026, 0.008)
            teeth = t if teeth is None else np.minimum(teeth, t)
        return [('flesh', flesh), ('inner', inside), ('tooth', teeth)]

    def sucker_paint(ctx):
        col = skin(ctx, belly_dir=(0.0, 1.0, 0.0), base=0.5)
        r = np.sqrt(ctx.px ** 2 + ctx.pz ** 2) / SUCKER
        lit = soft(ctx, (ctx.normal * LIGHT).sum(-1))
        n = ctx.noise(ctx.px * 4, ctx.py * 4, ctx.pz * 4, 6.0)
        lip = ctx.is_('flesh') & (ctx.py / SUCKER > 0.09) & (r > 0.13)
        col = np.where(lip[..., None], blend(LIP, 0.6 + 0.3 * lit + (n - 0.5) * 0.3 - ctx.ao * 0.5), col)
        m = ctx.is_('inner') | (ctx.is_('flesh') & (r < 0.175) & (ctx.py / SUCKER > 0.04))
        col = np.where(m[..., None], blend(INNER, 0.45 + (n - 0.5) * 0.5 + 0.6 * (r / 0.19 - 0.5)), col)
        m = ctx.is_('tooth')
        col = np.where(m[..., None], K.pick(TOOTH, 0.8 + (n - 0.5) * 0.3, n), col)
        return col, np.zeros(ctx.px.shape, np.float32)

    su = K.voxelize('sucker', (-0.3 * SUCKER, -0.05 * SUCKER, -0.3 * SUCKER), (0.3 * SUCKER, 0.24 * SUCKER, 0.3 * SUCKER), s, sucker, sucker_paint, noise, min_nb=1)

    def tongue(px, py, pz, tip=False):
        ln = 0.45 if tip else TONGUE_SEG
        if tip:
            w = 0.12 * np.clip(1.0 - py / ln, 0.0, 1.0) ** 0.8 + 0.012
        else:
            w = 0.125 - 0.01 * py / ln
        r = np.sqrt((px / w) ** 2 + (pz / (w * 0.55)) ** 2)
        d = (r - 1.0) * np.minimum(w, w * 0.55)
        d = np.maximum(d, np.maximum(-py - 0.03, py - ln - 0.03))
        # a groove down its top
        d = d + 0.015 * np.exp(-(px / 0.02) ** 2) * (pz > 0)
        return [('tongue', d)]

    def tongue_paint(ctx):
        n = ctx.noise(ctx.px * 4, ctx.py * 4, ctx.pz * 4, 6.0)
        lit = soft(ctx, (ctx.normal * LIGHT).sum(-1))
        col = blend(TONGUE, 0.5 + 0.3 * lit + (n - 0.5) * 0.4 - ctx.ao * 0.6)
        wet = (lit > 0.7) & (n > 0.6)
        col = np.where(wet[..., None], col * 0.5 + hx('#e0a0a0') * 0.5, col)
        return col, np.zeros(ctx.px.shape, np.float32)

    to = K.voxelize('tongue', (-0.16, -0.06, -0.1), (0.16, TONGUE_SEG + 0.06, 0.1), s, tongue, tongue_paint, noise, min_nb=1)
    tt = K.voxelize('tongue_tip', (-0.16, -0.06, -0.1), (0.16, 0.5, 0.1), s, lambda a, b, c: tongue(a, b, c, True), tongue_paint, noise,
                    min_nb=1)

    m = K.Model('leech_parts', 'blocks; each piece in its own frame (see the tool)')
    m.add(th, pivot=(0, 0, 0), kind='grab_thigh', length=THIGH)
    m.add(sh, pivot=(0, 0, 0), kind='grab_shin', length=SHIN)
    m.add(ts, pivot=(0, 0, 0), kind='grab_tseg', length=TSEG)
    m.add(su, pivot=(0, 0, 0), kind='grab_sucker')
    m.add(to, pivot=(0, 0, 0), kind='grab_tongue', length=TONGUE_SEG)
    m.add(tt, pivot=(0, 0, 0), kind='grab_tongue_tip')
    return m


def body_mesh_model():
    body = body_model()
    head, jaw = head_model()
    hair = hair_models()
    m = K.Model('leech_body', 'blocks, the ground under the body\'s middle at the origin, its mouth toward +Z, up +Y, its left +X')
    m.add(body, pivot=(0, 0, 0), anim=None, kind='body')
    m.add(head, pivot=NECK, anim=None, kind='grab_head')
    m.add(jaw, pivot=JAW_PIVOT, anim=None, kind='grab_jaw')
    for g in hair:
        m.add(g, pivot=CROWN, anim=None, kind='grab_hair')
    return m


# ---------------------------------------------------------------- preview: the rest pose (as the game would draw it at rest)

def ik(s, w, pole, a_len, b_len):
    d = w - s
    ln = min(np.linalg.norm(d), (a_len + b_len) * 0.999)
    u = d / np.linalg.norm(d)
    c = (a_len ** 2 + ln ** 2 - b_len ** 2) / (2 * a_len * ln)
    a = math.acos(max(-1, min(1, c)))
    p = pole - u * np.dot(pole, u)
    p /= np.linalg.norm(p)
    return s + (u * math.cos(a) + p * math.sin(a)) * a_len


def frame_y(axis, z_hint):
    y = axis / np.linalg.norm(axis)
    z = z_hint - y * np.dot(z_hint, y)
    if np.linalg.norm(z) < 1e-6:
        z = np.cross(y, [1.0, 0, 0])
    z /= np.linalg.norm(z)
    x = np.cross(y, z)
    return np.stack([x, y, z], axis=1)


ARM_THICK = (2.0, 0.85)    # the tentacles drawn thick at the shoulder, thin at the tip (LeechDevilRenderer)


def preview(path=None):
    import eternity_devil_3d as ED  # noqa (its sheet helpers)
    bm = body_mesh_model()
    pm = parts_model()
    items = []
    for g, md in zip(bm.grids, bm.meta):
        items.append(K.Faces(g))
    byname = {md['name']: g for g, md in zip(pm.grids, pm.meta)}

    def place(name, R, t, axes=(1.0, 1.0, 1.0)):
        fc = K.Faces(byname[name])
        fc.p = (fc.p * np.array(axes)) @ R.T + t
        fc.n = fc.n @ R.T
        fc.ns = fc.ns @ R.T
        items.append(fc)

    for hp, fp in zip(HIPS, FEET):
        hip = np.array(hp)
        ankle = np.array(fp) + np.array([0, ANKLE_UP, 0])
        out = np.array([np.sign(hp[0]), 0.0, 0.0])
        knee = ik(hip, ankle, out + np.array([0, 1.3, 0]), THIGH, SHIN)
        place('thigh', frame_y(knee - hip, -out), hip)
        place('shin', frame_y(ankle - knee, -out), knee)
        place('sucker', frame_y(np.array([0, -1.0, 0]), np.array([0, 0, 1.0])), ankle)
    for sp in SHOULDERS:
        sh = np.array(sp)
        side = np.sign(sp[0])
        pts = K.curve(sh, sh + np.array([side * 0.6, 0.3, 1.0]), sh + np.array([side * 0.2, -1.0, 1.65]), 10)
        for k, (a, b) in enumerate(zip(pts[:-1], pts[1:])):
            ln = np.linalg.norm(b - a)
            th = ARM_THICK[0] + (ARM_THICK[1] - ARM_THICK[0]) * (k + 0.5) / (len(pts) - 1)
            place('tseg', frame_y(b - a, np.array([0, 1.0, 0])), a, (th, ln / TSEG, th))
        place('sucker', frame_y(pts[-1] - pts[-2], np.array([0, 1.0, 0])), pts[-1], (0.8, 0.8, 0.8))
    lights = [((-0.4, 0.9, 0.5), (1.0, 0.95, 0.85), 0.8), ((0.6, 0.3, -0.6), (0.7, 0.75, 0.9), 0.3)]
    rims = [((0.8, 0.4, -0.3), (150, 170, 140), 0.5)]
    tiles = []
    for yaw, pitch, dist in [(0, 4, 6.5), (90, 6, 7.5), (180, 10, 7.5), (35, 14, 7.0), (0, 2, 3.4), (270, 6, 7.5)]:
        a, p = math.radians(yaw), math.radians(pitch)
        centre = np.array([0, 1.45, 0.3 if dist > 4 else 2.2])
        pos = centre + dist * np.array([math.sin(a) * math.cos(p), math.sin(p), math.cos(a) * math.cos(p)])
        cam = K.Camera(pos, centre, fov=50, size=(320, 320))
        im = K.render(items, cam, lights, rims, ambient=0.6, bg=(150, 165, 185))
        tiles.append(ED.label(im, 'yaw %d pitch %d' % (yaw, pitch), 12))
    out = path or os.path.join(OUT, 'look_rest.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    from PIL import Image
    Image.fromarray(ED.sheet(tiles, 3)).save(out)
    print('wrote', out)


def build():
    bm = body_mesh_model()
    n, counts = bm.write(OUT_ASSETS, 'leech_body_mesh.bin', 'leech_body_parts.json', note='The Leech Devil\'s body (tools/leech_devil_3d.py)')
    print('body', n, counts)
    pm = parts_model()
    n, counts = pm.write(OUT_ASSETS, 'leech_parts_mesh.bin', 'leech_parts_parts.json', note='The Leech Devil\'s pieces (tools/leech_devil_3d.py)')
    print('parts', n, counts)


def textures():
    """The sucker's item picture: a torn-off round sucker mouth, pale lip, a ring of teeth, dark red inside, a ragged stump."""
    from PIL import Image
    pal = {'.': (0, 0, 0, 0), 'k': (18, 21, 13, 255), 'K': (34, 39, 24, 255), 'o': (52, 58, 36, 255), 'l': (150, 128, 110, 255),
           'L': (196, 176, 154, 255), 'r': (58, 10, 16, 255), 'R': (108, 28, 32, 255), 'w': (220, 210, 176, 255), 'b': (120, 20, 26, 255)}
    art = [
        '................',
        '.....llllll.....',
        '...lLwlwlwLl....',
        '..lLwrrrrrwLl...',
        '..lwrRRRRRrwl...',
        '.lLrRrrrrrRrLl..',
        '.lwrRrrkrrRrwl..',
        '.lLrRrrrrrRrLl..',
        '..lwrRRRRRrwl...',
        '..lLwrrrrrwLl...',
        '...lLwlwlwLl....',
        '....KllllllK....',
        '.....KoooK......',
        '.....koKok......',
        '......bkb.......',
        '.......b........',
    ]
    im = np.array([[pal[ch] for ch in row] for row in art], np.uint8)
    os.makedirs(os.path.join(TEX, 'item'), exist_ok=True)
    Image.fromarray(im, 'RGBA').save(os.path.join(TEX, 'item', 'leech_sucker.png'))
    print('textures written')


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'preview'
    if what == 'build':
        build()
    elif what == 'textures':
        textures()
    else:
        preview(sys.argv[2] if len(sys.argv) > 2 else None)
