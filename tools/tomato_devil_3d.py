"""The Tomato Devil's look: a big red tomato covered in bulging eyes, a vertical mouth down its front, walking on eight
human arms. Built with the Eternity Devil's kit (tools/eternity_3d_kit.py: signed distances -> voxels -> merged quads) into
two meshes the game draws with FleshMesh:

    tomato_body_mesh.bin / tomato_body_parts.json    1/16 block voxels: the body (with the eyes' lids and the mouth's inside)
        part 0 body (static), part 1 lip_l, part 2 lip_r (the two halves of the mouth, hinged at their outer edges); the json's
        "eyes" lists every eyeball: centre x, y, z, outward normal x, y, z, radius (blocks, the body's frame).
    tomato_parts_mesh.bin / tomato_parts_parts.json  1/32 block voxels: one of each moving piece, drawn many times:
        upper (shoulder to elbow along +Y, 1.30 long), fore (elbow to wrist along +Y, 1.20 long), hand_l / hand_r (the wrist at
        the origin, the palm flat in XZ, fingers toward +Z), fingers_l / fingers_r (hinged at the knuckles), eye (radius 0.2,
        looking along +Z) and seed (pointing along +Z).

Frame of the body: blocks, its centre at the origin, its front (the mouth) toward +Z, up +Y.

    py -3.12 tools/tomato_devil_3d.py build       # write both meshes to the game's resources
    py -3.12 tools/tomato_devil_3d.py textures    # the cut, the splat and the carcass's picture
    py -3.12 tools/tomato_devil_3d.py preview     # a few views of the rest pose -> <main checkout>/work/tomato/look_*.png
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_3d_kit as K  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..'))
MAIN = os.path.abspath(os.path.join(ROOT, '..', '..', '..')) if '.claude' in ROOT else ROOT
OUT = os.path.join(MAIN, 'work', 'tomato')
OUT_ASSETS = os.path.join(ROOT, 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'models', 'entity')

hx = K.hx
# The show's tomato (the anime): a deep dark red, black-red in the creases, lighter only on the bulges, darker toward the underside;
# thin stringy streaks of a paler orange-red run down it like the fibres of a split tomato.
RED = [hx(c) for c in ('#1e0204', '#2e0406', '#430709', '#5a0b0c', '#721210', '#8a1a14', '#a2271c', '#b83a26')]
STREAK = [hx(c) for c in ('#9a3220', '#bb4a2c', '#d2683e')]
LID = [hx(c) for c in ('#260306', '#3a060a', '#520a0e', '#6c1214', '#86201a')]
LIP = [hx(c) for c in ('#2a0408', '#40080c', '#5a0e12', '#761a18', '#922a20', '#ac3e2a')]
GREEN = [hx(c) for c in ('#13260b', '#1f3d11', '#2e5719', '#3f7422', '#56912e')]
INNER = [hx(c) for c in ('#0a0103', '#160204', '#240406', '#36080a')]
GUM = [hx(c) for c in ('#3a060c', '#561018', '#701c22')]
# The teeth: big, flat and blunt, a pale yellow-green like old bone.
TOOTH = [hx(c) for c in ('#6c7048', '#98a068', '#bcc48a', '#d8dcac', '#ecedcc')]
# Human arms: pale, a little grey and blotchy, red at the knuckles and elbows.
SKIN = [hx(c) for c in ('#4c362e', '#6a4c40', '#876354', '#a07a68', '#b8917c', '#c9a690', '#d8baa4')]
NAIL = hx('#d9c2b4')
# The eyeballs: a warm off-white, greying to the back; a red-brown iris ringed darker; a black pupil; a wet highlight.
SCLERA = [hx(c) for c in ('#8a7c72', '#b4a89c', '#d6ccc0', '#ece4d8', '#f8f2e8')]
VEIN = hx('#a8302a')
IRIS = [hx(c) for c in ('#3c1606', '#6a2c0c', '#98461a', '#b8622a', '#cc7a38')]
PUPIL = hx('#080304')
SHINE = hx('#fffaf2')
SEED = [hx(c) for c in ('#9c8a58', '#cbb886', '#e6d8aa', '#f6eecb')]
PULP = [hx(c) for c in ('#8a1a0e', '#b8301a', '#e05a2c')]

RX = 1.30            # the body's half width
RY = 1.02            # its half height (wider than tall, like a beefsteak tomato)
UPPER = 1.30
FORE = 1.20
EYE_R = 0.20
LOBES = 7            # the lobes running top to bottom


# ---------------------------------------------------------------- the body

_LUMP = K.Noise(21)


def body_sdf(px, py, pz):
    """The tomato: squat and lumpy, seven lobes running top to bottom with deep creases between, a dimple on top where the calyx sits,
    a little flat underneath."""
    az = np.arctan2(px, pz)
    yy = np.clip(py / RY, -1, 1)
    band = np.clip(1 - yy ** 2, 0, 1)
    # |sin| is 0 in a crease and 1 on a lobe's crown: creases sharp, crowns round
    s = np.abs(np.sin(LOBES * 0.5 * az + 0.9))
    lobe = 1.0 + band * (0.085 * np.sqrt(s) - 0.06)
    rx = RX * lobe
    ry = np.where(py < 0, RY * 0.93, RY)
    k = np.sqrt((px / rx) ** 2 + (py / ry) ** 2 + (pz / rx) ** 2)
    d = (k - 1.0) * 1.1
    # lumps: soft swellings all over
    d = d - 0.07 * (_LUMP.fbm(px, py, pz, 1.3, 2) - 0.5)
    rr = px ** 2 + pz ** 2
    d = d + 0.22 * np.exp(-rr / 0.09) * (py > 0)
    return d


MOUTH_Y = -0.02
MOUTH_HY = 0.80      # half its height: the mouth runs most of the body's height
MOUTH_HX = 0.47      # half its width out to where the lips join the body
SLIT = 0.21          # half the gap between the lips' inner edges at the middle, shut


def mouth_w(y):
    """Half the mouth's width at height y (to the lips' roots)."""
    t = np.clip((y - MOUTH_Y) / MOUTH_HY, -1, 1)
    return MOUTH_HX * np.sqrt(np.clip(1 - t ** 2, 0, 1)) ** 0.8


def slit_w(y):
    """Half the gap between the lips' inner edges at height y, shut."""
    t = np.clip((y - MOUTH_Y) / MOUTH_HY, -1, 1)
    return SLIT * np.sqrt(np.clip(1 - t ** 2, 0, 1)) + 0.01


def in_mouth(px, py, pz):
    """The mouth's ellipse on the front, seen face on."""
    return ((px / MOUTH_HX) ** 2 + ((py - MOUTH_Y) / MOUTH_HY) ** 2 < 1.0) & (pz > 0.3)


def cavity_sdf(px, py, pz):
    """The inside of the mouth: a deep throat behind the lips."""
    return K.ell(px, py, pz, (0.0, MOUTH_Y, 0.78), (0.40, 0.76, 0.58))


# Where the eyes are: (azimuth, elevation in degrees; radius). About a dozen, each big; two biggest either side of the mouth, as the show's.
EYES = [(33, 22, 0.31), (-33, 22, 0.31),
        (17, 60, 0.19), (-19, 58, 0.20),
        (57, -16, 0.23), (-56, -14, 0.24),
        (27, -44, 0.15), (-29, -43, 0.16),
        (96, 30, 0.27), (-97, 28, 0.27),
        (118, -22, 0.20), (-116, -24, 0.19),
        (178, 22, 0.30), (146, -16, 0.21), (-148, -14, 0.22)]


def eye_list():
    """Every eyeball: centre (bulging well out of its mound), outward normal, radius."""
    out = []
    for az, el, r in EYES:
        a, e = math.radians(az), math.radians(el)
        d = np.array([math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e)])
        c = surface_point(d)
        n = surface_normal(c, 0.06)
        n = (n + d * 0.6)
        n /= np.linalg.norm(n)
        centre = c + n * r * 0.30
        out.append([float(x) for x in centre] + [float(x) for x in n] + [float(r)])
    return out


def surface_point(d):
    lo, hi = 0.0, 2.0
    for _ in range(40):
        m = (lo + hi) / 2
        p = d * m
        if body_sdf(np.array([p[0]]), np.array([p[1]]), np.array([p[2]]))[0] < 0:
            lo = m
        else:
            hi = m
    return d * lo


def surface_normal(p, e=0.02):
    def f(q):
        return body_sdf(np.array([q[0]]), np.array([q[1]]), np.array([q[2]]))[0]
    n = np.array([f(p + [e, 0, 0]) - f(p - [e, 0, 0]), f(p + [0, e, 0]) - f(p - [0, e, 0]), f(p + [0, 0, e]) - f(p - [0, 0, e])])
    return n / np.linalg.norm(n)


def tangent_up(n):
    """The way up along the skin at a point whose normal is n."""
    u = np.array([0.0, 1.0, 0.0]) - n * n[1]
    if np.linalg.norm(u) < 1e-3:
        u = np.array([0.0, 0.0, 1.0])
    return u / np.linalg.norm(u)


def sepal_sdf(px, py, pz):
    """The calyx: six green leaves lying out over the top from the stem, and the stem; and little green sprigs sprouting by some eyes."""
    d = K.caps(px, py, pz, (0, RY - 0.24, 0), (0, RY + 0.10, 0.02), 0.07, 0.05)
    for k in range(6):
        a = k * math.pi / 3 + 0.25
        pts = []
        for i in range(6):
            t = i / 5
            rad = 0.06 + 0.58 * t
            v = np.array([math.sin(a) * rad, RY, math.cos(a) * rad])
            y = surface_point(v / np.linalg.norm(v))[1]
            twist = 0.12 * math.sin(t * 3 + k)
            pts.append((math.sin(a + twist) * rad, y + 0.035, math.cos(a + twist) * rad))
        leaf = K.polyline(px, py, pz, pts, 0.085, 0.02)
        d = np.minimum(d, np.maximum(leaf, np.abs(py - interp_y(px, pz, pts)) - 0.035))
    # sprigs: two or three thin curled leaves standing out of the skin just above an eye, as the show's has
    for (az, el, r), e in zip(EYES, eye_list()):
        if r < 0.2:
            continue
        c, n = np.array(e[:3]), np.array(e[3:6])
        up = tangent_up(n)
        side = np.cross(n, up)
        root = c - n * r * 0.30 + up * r * 1.30
        for j, sd in enumerate((-1.0, 0.0, 1.0) if r > 0.25 else (-1.0, 1.0)):
            tip = root + n * 0.20 + up * 0.10 + side * sd * 0.13
            mid = root + n * 0.13 + up * 0.02 + side * sd * 0.05
            d = np.minimum(d, K.polyline(px, py, pz, [tuple(root - n * 0.05), tuple(mid), tuple(tip)], 0.032, 0.012))
    return d


def interp_y(px, pz, pts):
    rr = np.sqrt(px ** 2 + pz ** 2)
    ys = np.array([p[1] for p in pts])
    rs = np.array([math.hypot(p[0], p[2]) for p in pts])
    return np.interp(rr, rs, ys)


def soft(ctx, field, sigma=1.1):
    """A field smoothed over the solid cells only (so a texel never stands out from its neighbours: no speckle up close)."""
    from scipy import ndimage
    w = ctx.solid.astype(np.float32)
    num = ndimage.gaussian_filter(field * w, sigma, mode='constant')
    den = ndimage.gaussian_filter(w, sigma, mode='constant')
    return np.where(den > 1e-4, num / np.maximum(den, 1e-4), field)


def blend(ramp, t, steps=4):
    """A colour from the ramp by t, blended between its stops (not dithered): `steps` shades between each two, so the skin
    reads smooth up close and the mesher still merges faces of one shade."""
    r = np.array(ramp, np.float32)
    x = np.clip(t, 0, 1) * (len(r) - 1)
    x = np.round(x * steps) / steps
    i = np.clip(np.floor(x).astype(int), 0, len(r) - 2)
    f = (x - i)[..., None]
    return r[i] * (1 - f) + r[i + 1] * f


def red_paint(ctx, ramp=RED, base=0.52):
    """The skin: dark, lighter on the bulges and on top, black-red in the creases and underneath, with pale stringy streaks running down."""
    n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 2.2)
    n2 = ctx.noise(ctx.px + 5.1, ctx.py, ctx.pz, 6.0)
    L = np.array([-0.35, 0.85, 0.4])
    L /= np.linalg.norm(L)
    lit = soft(ctx, (ctx.normal * L).sum(-1))
    t = base + 0.26 * lit + (n - 0.5) * 0.16 - ctx.ao * 0.75
    # the underside darker (a maroon), the bulges' crowns catching the light
    t = t - 0.18 * np.clip(-ctx.py / RY, 0, 1)
    t = t + 0.18 * np.clip((lit - 0.75) * 4.0, 0, 1)
    # (smoothed and blended, not dithered: round 2's skin was speckled up close)
    col = blend(ramp, soft(ctx, t))
    # the fibres: thin streaks running down the lobes, paler, most on the upper half
    fib = ctx.noise(ctx.px * 9.0, ctx.py * 0.9, ctx.pz * 9.0, 1.0)
    streak = (np.abs(fib - 0.5) < 0.016) & (ctx.py > -0.4 * RY) & (ctx.ao < 0.35)
    col = np.where(streak[..., None], K.pick(STREAK, 0.4 + 0.5 * lit, n2), col)
    # darker veins branching over it
    vein = np.abs(ctx.noise.fbm(ctx.px * 1.7 + 4.0, ctx.py * 1.7, ctx.pz * 1.7, 2.0) - 0.5) < 0.012
    col = np.where((vein & ~streak)[..., None], col * 0.55, col)
    # the wet: a pale glossy sheen on the bulges where the light lies, broken up
    wet = (lit > 0.84) & (ctx.ao < 0.12) & (ctx.noise(ctx.px * 4.0, ctx.py * 4.0, ctx.pz * 4.0, 1.0) > 0.62)
    col = np.where(wet[..., None], col * 0.45 + hx('#e8a090') * 0.55, col)
    return col


def body_model():
    eyes = eye_list()
    noise = K.Noise(3)
    s = K.S16
    lo, hi = (-1.55, -1.25, -1.55), (1.55, 1.45, 1.6)

    def mounds(px, py, pz):
        """The flesh each eye sits in: a swelling round it, wrapping the ball up to a lid line (the upper lid heavier)."""
        d = None
        for e in eyes:
            c, n, r = np.array(e[:3]), np.array(e[3:6]), e[6]
            up = tangent_up(n)
            m = K.sphere(px, py, pz, c - n * r * 0.20, r * 1.30)
            ncut = n - 0.38 * up
            ncut /= np.linalg.norm(ncut)
            cut = (px - c[0]) * ncut[0] + (py - c[1]) * ncut[1] + (pz - c[2]) * ncut[2] - r * 0.22
            m = np.maximum(m, cut)
            d = m if d is None else np.minimum(d, m)
        return d

    def sockets(px, py, pz):
        d = None
        for e in eyes:
            c, r = np.array(e[:3]), e[6]
            di = K.sphere(px, py, pz, c, r * 1.03)
            d = di if d is None else np.minimum(d, di)
        return d

    def layers_body(px, py, pz):
        b = body_sdf(px, py, pz)
        mo = mounds(px, py, pz)
        bm = K.smin(b, mo, 0.10)
        cav = cavity_sdf(px, py, pz)
        mouth = in_mouth(px, py, pz)
        shell = np.where(mouth, 1.0, bm)                       # the lips are parts of their own
        throat = np.maximum(np.maximum(b, cav - 0.1), -cav)    # a lining 0.1 thick round the cavity
        sk = sockets(px, py, pz)
        flesh = np.maximum(np.maximum(shell, -cav), -sk)
        ld = np.maximum(np.maximum(np.where(mouth, 1.0, mo), -sk), -cav)
        ld = np.maximum(ld, -(b - 0.03))                       # only what stands out of the skin is the lid's
        return [('flesh', flesh), ('gum', np.maximum(throat, -(b + 0.06))), ('inner', np.maximum(throat, b + 0.06)), ('lid', ld),
                ('green', sepal_sdf(px, py, pz))]

    def paint(ctx):
        n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 2.2)
        n2 = ctx.noise(ctx.px + 5.1, ctx.py, ctx.pz, 6.0)
        L = np.array([-0.35, 0.85, 0.4])
        L /= np.linalg.norm(L)
        lit = (ctx.normal * L).sum(-1)
        col = red_paint(ctx)
        em = np.zeros(ctx.px.shape, np.float32)
        top = ctx.py > RY - 0.55
        rr = np.sqrt(ctx.px ** 2 + ctx.pz ** 2)
        blush = top & (rr < 0.45) & ctx.is_('flesh')
        col = np.where(blush[..., None], col * 0.8 + hx('#4a3410') * 0.2, col)
        m = ctx.is_('lid')
        col = np.where(m[..., None], blend(LID, soft(ctx, 0.55 + 0.35 * lit + (n - 0.5) * 0.4 - ctx.ao * 0.9)), col)
        m = ctx.is_('green')
        col = np.where(m[..., None], K.pick(GREEN, 0.45 + 0.4 * lit + (n - 0.5) * 0.5, n2), col)
        m = ctx.is_('inner')
        col = np.where(m[..., None], K.pick(INNER, 0.2 + (n - 0.5) * 0.6 + 0.4 * (ctx.pz - 0.6), n2), col)
        m = ctx.is_('gum')
        col = np.where(m[..., None], K.pick(GUM, 0.5 + (n - 0.5) * 0.6, n2), col)
        return col, em

    body = K.voxelize('body', lo, hi, s, layers_body, paint, noise, min_nb=3)

    def lip_layers(side):
        def f(px, py, pz):
            b = body_sdf(px, py, pz)
            mouth = in_mouth(px, py, pz)
            inner = slit_w(py)
            half = mouth & (side * px > inner)
            # the lip's flesh: the skin over its half of the mouth, 0.18 thick, swelling out toward the slit
            t = np.clip((side * px - inner) / np.maximum(mouth_w(py) - inner, 1e-3), 0, 1)
            swell = 0.10 * np.sin(np.clip(t, 0, 1) * math.pi * 0.75 + 0.35)
            skin = b - swell
            lip = np.where(half, np.maximum(skin, -(b + 0.18)), 1.0)
            # the rolled edge along the slit: thick in the middle, thin at the corners
            pts = []
            for i in range(17):
                tt = -0.97 + i * (1.94 / 16)
                y = MOUTH_Y + tt * MOUTH_HY
                x = side * (float(slit_w(np.array(y))) + 0.11)
                v = np.array([x, y, 1.0])
                z = surface_point(v / np.linalg.norm(v))[2] + 0.02
                pts.append((x, y, z))
            rope = None
            for i in range(16):
                tt = -0.97 + (i + 0.5) * (1.94 / 16)
                rad = 0.045 + 0.085 * math.sqrt(max(0.0, 1 - tt * tt))
                seg = K.caps(px, py, pz, pts[i], pts[i + 1], rad)
                rope = seg if rope is None else np.minimum(rope, seg)
            lipd = K.smin(lip, rope, 0.05)
            lipd = np.maximum(lipd, -(side * px - 0.004))
            # the teeth: two long rows along the inside of the lip, big flat blunt pegs pointing across the slit (they show when shut)
            teeth = None
            for row, (back, n_t, off) in enumerate(((0.035, 13, 0.0), (0.12, 12, 0.5))):
                for i in range(n_t):
                    tt = -0.86 + (i + off) * (1.72 / (n_t - 1 + off))
                    if abs(tt) > 0.9:
                        continue
                    y = MOUTH_Y + tt * MOUTH_HY
                    sw = float(slit_w(np.array(y)))
                    v = np.array([side * sw, y, 1.0])
                    zc = surface_point(v / np.linalg.norm(v))[2] - back
                    root_x = side * (sw + 0.09)
                    tip_x = side * max(0.03, sw - 0.09 - 0.03 * ((i * 7 + row) % 3) + (0.0 if row == 0 else 0.03))
                    hy = 0.033 * (0.7 + 0.3 * math.sqrt(max(0.0, 1 - tt * tt)))
                    tth = K.box(px, py, pz, ((root_x + tip_x) / 2, y, zc), (abs(root_x - tip_x) / 2, hy, 0.035), 0.022)
                    teeth = tth if teeth is None else np.minimum(teeth, tth)
            teeth = np.maximum(teeth, -(side * px - 0.004))
            return [('flesh', lipd), ('tooth', teeth)]
        return f

    def lip_paint(ctx):
        n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 2.2)
        n2 = ctx.noise(ctx.px + 5.1, ctx.py, ctx.pz, 6.0)
        L = np.array([-0.35, 0.85, 0.4])
        L /= np.linalg.norm(L)
        lit = (ctx.normal * L).sum(-1)
        col = blend(LIP, soft(ctx, 0.68 + 0.3 * lit + (n - 0.5) * 0.3 - ctx.ao * 0.7))
        # wrinkles running along the lip, and pale stringy streaks
        rid = np.abs(np.sin((np.abs(ctx.px) * 46.0 + (n - 0.5) * 3.0)))
        col = np.where((rid < 0.22)[..., None], col * 0.62, col)
        fib = ctx.noise(ctx.px * 10.0, ctx.py * 0.8, ctx.pz * 10.0, 1.0)
        col = np.where(((np.abs(fib - 0.5) < 0.04) & (ctx.ao < 0.4))[..., None], K.pick(STREAK, 0.3 + 0.5 * lit, n2), col)
        nn = ctx.noise(ctx.px * 2, ctx.py * 2, ctx.pz * 2, 5.0)
        m = ctx.is_('tooth')
        col = np.where(m[..., None], K.pick(TOOTH, 0.85 - ctx.ao * 1.4 + (nn - 0.5) * 0.35 + 0.15 * lit, nn), col)
        em = np.zeros(ctx.px.shape, np.float32)
        return col, em

    lip_l = K.voxelize('lip_l', (-0.62, -0.86, 0.3), (0.06, 0.84, 1.5), s, lip_layers(-1.0), lip_paint, noise, min_nb=2)
    lip_r = K.voxelize('lip_r', (-0.06, -0.86, 0.3), (0.62, 0.84, 1.5), s, lip_layers(1.0), lip_paint, noise, min_nb=2)
    m = K.Model('tomato_body', 'blocks, the body centre at the origin, its mouth toward +Z, up +Y')
    m.add(body, pivot=(0, 0, 0), anim=None, kind='body')
    piv_l = surface_point(np.array([-MOUTH_HX, MOUTH_Y, 1.2]) / np.linalg.norm([-MOUTH_HX, MOUTH_Y, 1.2]))
    piv_r = surface_point(np.array([MOUTH_HX, MOUTH_Y, 1.2]) / np.linalg.norm([MOUTH_HX, MOUTH_Y, 1.2]))
    m.add(lip_l, pivot=[float(x) for x in piv_l], anim=None, kind='grab_lip')
    m.add(lip_r, pivot=[float(x) for x in piv_r], anim=None, kind='grab_lip')
    return m, eyes


def chunk_grid(noise):
    """A chunk of its skin thrown off when it bursts: a ragged curved shard of the body's shell, about 0.8 across, red outside and pulp and
    seeds inside, its outside toward +Z, its middle at the origin."""
    def f(px, py, pz):
        c = np.array([0.0, 0.0, -1.25])
        d = K.sphere(px, py, pz, c, 1.3)
        shell = np.maximum(d, -(d + 0.13))
        rr = np.sqrt(px ** 2 + py ** 2)
        ang = np.arctan2(py, px)
        edge = 0.36 + 0.07 * np.sin(ang * 5 + 1.0) + 0.05 * np.sin(ang * 9 + 2.0)
        shell = np.maximum(shell, rr - edge)
        inner = np.maximum(shell, -(d + 0.07))
        return [('flesh', shell), ('pulp', inner)]

    def paint(ctx):
        col = red_paint(ctx)
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 7.0)
        m = ctx.is_('pulp')
        col = np.where(m[..., None], K.pick(PULP, 0.5 + (n - 0.5) * 0.8, n), col)
        seed = m & (n > 0.82)
        col = np.where(seed[..., None], SEED[2], col)
        return col, np.zeros(ctx.px.shape, np.float32)

    return K.voxelize('chunk', (-0.48, -0.48, -0.3), (0.48, 0.48, 0.12), K.S16, f, paint, noise, min_nb=2)


# ---------------------------------------------------------------- the pieces

def parts_model():
    noise = K.Noise(5)
    s = K.S32

    def skin_paint(ctx, knuckle=None):
        n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 4.0)
        n2 = ctx.noise(ctx.px + 3.3, ctx.py, ctx.pz, 9.0)
        L = np.array([-0.3, 0.6, 0.7])
        L /= np.linalg.norm(L)
        lit = (ctx.normal * L).sum(-1)
        t = 0.6 + 0.28 * lit + (n - 0.5) * 0.4 - ctx.ao * 0.55
        col = K.pick(SKIN, t, n2, 0.4)
        em = np.zeros(ctx.px.shape, np.float32)
        if knuckle is not None:
            k = knuckle(ctx)
            col = np.where(k[..., None], col * np.array([1.06, 0.86, 0.82]), col)
        m = ctx.is_('nail')
        col = np.where(m[..., None], NAIL * (0.9 + 0.2 * n2[..., None]), col)
        return np.clip(col, 0, 255), em

    # the upper arm: the shoulder's ball at the origin, the bicep, down to the elbow at +Y UPPER
    def upper(px, py, pz):
        d = K.sphere(px, py, pz, (0, 0.02, 0), 0.165)
        d = K.smin(d, K.caps(px, py, pz, (0, 0.06, 0), (0, UPPER, 0), 0.135, 0.105), 0.08)
        d = K.smin(d, K.ell(px, py, pz, (0, 0.56, 0.035), (0.135, 0.36, 0.15)), 0.06)
        d = K.smin(d, K.ell(px, py, pz, (0, 0.30, -0.02), (0.16, 0.22, 0.13)), 0.06)
        return [('flesh', d)]

    up = K.voxelize('upper', (-0.25, -0.2, -0.25), (0.25, UPPER + 0.15, 0.25), s, upper,
                    lambda c: skin_paint(c, lambda cc: (cc.py > UPPER - 0.12) & (cc.pz < -0.04)), noise)

    # the forearm: the elbow's knob at the origin (the point of the elbow behind, -Z), tapering to a flat wrist
    def fore(px, py, pz):
        d = K.sphere(px, py, pz, (0, 0.0, -0.02), 0.118)
        d = K.smin(d, K.sphere(px, py, pz, (0, 0.02, -0.075), 0.07), 0.03)
        shaft = K.caps(px, py, pz, (0, 0.03, 0), (0, FORE - 0.04, 0), 0.118, 0.07)
        d = K.smin(d, shaft, 0.06)
        d = K.smin(d, K.ell(px, py, pz, (0.02, 0.32, 0.01), (0.13, 0.30, 0.115)), 0.05)
        # the wrist wider than it is deep
        d = K.smin(d, K.ell(px, py, pz, (0, FORE - 0.03, 0), (0.088, 0.07, 0.06)), 0.03)
        return [('flesh', d)]

    fo = K.voxelize('fore', (-0.22, -0.18, -0.22), (0.22, FORE + 0.12, 0.22), s, fore,
                    lambda c: skin_paint(c, lambda cc: (cc.py < 0.12) & (cc.pz < -0.05)), noise)

    # the hand: the wrist at the origin, the palm lying flat (its underside at y -0.075), fingers on toward +Z; the thumb on +X
    # for the right hand (seen from above, palm down), on -X for the left.
    def hand(side):
        def f(px, py, pz):
            palm = K.box(px, py, pz, (0, -0.035, 0.19), (0.135, 0.04, 0.17), 0.035)
            palm = K.smin(palm, K.ell(px, py, pz, (0, -0.02, 0.05), (0.10, 0.05, 0.08)), 0.04)
            # the back of the hand a little domed, the tendons
            palm = K.smin(palm, K.ell(px, py, pz, (0, 0.0, 0.2), (0.12, 0.035, 0.15)), 0.03)
            th = K.polyline(px, py, pz, [(side * 0.09, -0.03, 0.07), (side * 0.19, -0.045, 0.17), (side * 0.235, -0.05, 0.27)], 0.042, 0.03)
            d = K.smin(palm, th, 0.035)
            nail = K.ell(px, py, pz, (side * 0.24, -0.028, 0.265), (0.022, 0.012, 0.03))
            return [('flesh', d), ('nail', np.maximum(nail, d - 0.002))]
        return f

    def knuckles(cc):
        return (cc.py > -0.01) & (cc.pz > 0.32)

    hl = K.voxelize('hand_l', (-0.32, -0.12, -0.1), (0.2, 0.08, 0.42), s, hand(-1.0), lambda c: skin_paint(c, knuckles), noise)
    hr = K.voxelize('hand_r', (-0.2, -0.12, -0.1), (0.32, 0.08, 0.42), s, hand(1.0), lambda c: skin_paint(c, knuckles), noise)

    KNUCKLE_Z = 0.345

    FXS = [0.098, 0.033, -0.033, -0.098]
    FLS = [0.27, 0.31, 0.29, 0.22]

    def fingers(side, only=None):
        def f(px, py, pz):
            d = None
            nails = None
            # index (next to the thumb) to little finger
            for j, (x, ln) in enumerate(zip(FXS, FLS)):
                if only is not None and j != only:
                    continue
                x = side * x
                a = (x, -0.035, KNUCKLE_Z - 0.02)
                b = (x * 1.04, -0.045, KNUCKLE_Z + ln * 0.55)
                c = (x * 1.07, -0.06, KNUCKLE_Z + ln)
                fi = K.polyline(px, py, pz, [a, b, c], 0.036, 0.027)
                d = fi if d is None else np.minimum(d, fi)
                nl = K.ell(px, py, pz, (x * 1.07, -0.042, KNUCKLE_Z + ln - 0.02), (0.022, 0.012, 0.03))
                nails = nl if nails is None else np.minimum(nails, nl)
            return [('flesh', d), ('nail', np.maximum(nails, d - 0.002))]
        return f

    fl = K.voxelize('fingers_l', (-0.2, -0.12, 0.28), (0.2, 0.03, 0.72), s, fingers(-1.0),
                    lambda c: skin_paint(c, lambda cc: cc.pz < KNUCKLE_Z + 0.04), noise)
    fr = K.voxelize('fingers_r', (-0.2, -0.12, 0.28), (0.2, 0.03, 0.72), s, fingers(1.0),
                    lambda c: skin_paint(c, lambda cc: cc.pz < KNUCKLE_Z + 0.04), noise)

    # each finger on its own, hinged at its knuckle, so they can land and press one after another, tap and feel about
    single = []
    for side, sn in ((-1.0, 'l'), (1.0, 'r')):
        for j in range(4):
            g = K.voxelize('finger_%s%d' % (sn, j), (-0.2, -0.12, 0.28), (0.2, 0.03, 0.72), s, fingers(side, j),
                           lambda c: skin_paint(c, lambda cc: cc.pz < KNUCKLE_Z + 0.04), noise)
            single.append((g, (side * FXS[j], -0.035, KNUCKLE_Z)))

    # a pupil: a black lens laid on the front of the eye, drawn at whatever size the eye's mood says (wide wandering, a pinprick on you)
    def pupil(px, py, pz):
        return [('pupil', np.maximum(K.ell(px, py, pz, (0, 0, EYE_R * 0.93), (0.07, 0.07, 0.05)), -K.sphere(px, py, pz, (0, 0, 0), EYE_R * 0.995)))]

    pu = K.voxelize('pupil', (-0.1, -0.1, 0.12), (0.1, 0.1, 0.26), s, pupil, lambda c: (np.tile(PUPIL, c.px.shape + (1,)), np.zeros(c.px.shape, np.float32)),
                    noise, keep_specks=True)

    # an eyeball: a big white ball, a red-brown iris ringed darker, a black pupil looking along +Z, a wet highlight up on its left
    def eye(px, py, pz):
        return [('sclera', K.sphere(px, py, pz, (0, 0, 0), EYE_R))]

    def eye_paint(ctx):
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 6.0)
        r = np.sqrt(ctx.px ** 2 + ctx.py ** 2 + ctx.pz ** 2) + 1e-6
        fz = ctx.pz / r
        col = K.pick(SCLERA, 0.92 + (n - 0.5) * 0.25 - (1 - fz) * 0.45, n, 0.3)
        veins = (np.abs(ctx.noise.fbm(ctx.px * 4, ctx.py * 4, ctx.pz * 4, 2.0) - 0.5) < 0.02) & (fz < 0.45)
        col = np.where(veins[..., None], VEIN, col)
        iris = fz > 0.86
        # the iris: darker at its rim, warm toward the pupil
        col = np.where(iris[..., None], K.pick(IRIS, (fz - 0.86) * 9.0 + 0.25 + (n - 0.5) * 0.3, n, 0.3), col)
        col = np.where(((fz > 0.86) & (fz < 0.885))[..., None], IRIS[0], col)
        col = np.where((fz > 0.985)[..., None], PUPIL, col)
        shine = ((ctx.px / r + 0.26) ** 2 + (ctx.py / r - 0.28) ** 2 < 0.012) & (fz > 0.8)
        col = np.where(shine[..., None], SHINE, col)
        em = np.where(fz > 0.86, 0.3, 0.75).astype(np.float32)
        em = np.where(shine, 0.5, em)
        return col, em

    ey = K.voxelize('eye', (-0.24, -0.24, -0.24), (0.24, 0.24, 0.24), s, eye, eye_paint, noise)

    # a lid: a quarter shell of flesh over the eye's front and top (the eye's frame); turned back it hides in the mound, turned
    # forward (with its twin turned up from below) it shuts the eye
    def lid(px, py, pz):
        sh = np.maximum(K.sphere(px, py, pz, (0, 0, 0), EYE_R * 1.13), -K.sphere(px, py, pz, (0, 0, 0), EYE_R * 1.035))
        sh = np.maximum(sh, np.maximum(-py + 0.004, -pz - 0.02))
        rim = K.caps(px, py, pz, (-EYE_R, 0.012, 0.0), (EYE_R, 0.012, 0.0), 0.022)
        rim = np.maximum(rim, -K.sphere(px, py, pz, (0, 0, 0), EYE_R * 1.03))
        rim = np.maximum(rim, K.sphere(px, py, pz, (0, 0, 0), EYE_R * 1.16))
        return [('flesh', np.minimum(sh, np.maximum(rim, -pz - 0.02)))]

    def lid_paint(ctx):
        n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 6.0)
        n2 = ctx.noise(ctx.px + 5.1, ctx.py, ctx.pz, 12.0)
        col = K.pick(LID, 0.6 + 0.3 * ctx.normal[..., 1] + (n - 0.5) * 0.4 - 0.5 * (ctx.py < 0.03), n2)
        return col, np.zeros(ctx.px.shape, np.float32)

    li = K.voxelize('lid', (-0.25, -0.05, -0.05), (0.25, 0.25, 0.25), s, lid, lid_paint, noise, min_nb=1)

    # a seed: a pale flat teardrop in a red jelly, pointing +Z
    def seed(px, py, pz):
        core = K.ell(px, py, pz, (0, 0, 0.0), (0.075, 0.05, 0.12))
        core = K.smin(core, K.ell(px, py, pz, (0, 0, 0.09), (0.04, 0.035, 0.07)), 0.03)
        jelly = K.ell(px, py, pz, (0, -0.01, -0.02), (0.11, 0.075, 0.13))
        return [('pulp', jelly), ('seed', core)]

    def seed_paint(ctx):
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 7.0)
        col = K.pick(PULP, 0.6 + (n - 0.5) * 0.7, n)
        em = np.full(ctx.px.shape, 0.25, np.float32)
        m = ctx.is_('seed')
        col = np.where(m[..., None], K.pick(SEED, 0.7 + 0.3 * ctx.normal[..., 1] + (n - 0.5) * 0.3, n), col)
        em = np.where(m, 0.55, em)
        return col, em

    se = K.voxelize('seed', (-0.15, -0.12, -0.18), (0.15, 0.12, 0.2), s, seed, seed_paint, noise, keep_specks=True)

    m = K.Model('tomato_parts', 'blocks; each piece in its own frame (see the tool)')
    m.add(up, pivot=(0, 0, 0), kind='grab_upper', length=UPPER)
    m.add(fo, pivot=(0, 0, 0), kind='grab_fore', length=FORE)
    m.add(hl, pivot=(0, 0, 0), kind='grab_hand')
    m.add(hr, pivot=(0, 0, 0), kind='grab_hand')
    m.add(fl, pivot=(0, -0.035, KNUCKLE_Z), kind='grab_fingers')
    m.add(fr, pivot=(0, -0.035, KNUCKLE_Z), kind='grab_fingers')
    m.add(ey, pivot=(0, 0, 0), kind='grab_eye', radius=EYE_R)
    m.add(se, pivot=(0, 0, 0), kind='grab_seed')
    m.add(li, pivot=(0, 0, 0), kind='grab_lid', radius=EYE_R)
    m.add(pu, pivot=(0, 0, 0), kind='grab_pupil')
    m.add(chunk_grid(noise), pivot=(0, 0, 0), kind='grab_chunk')
    for g, piv in single:
        m.add(g, pivot=piv, kind='grab_finger')
    return m


# ---------------------------------------------------------------- preview: the rest pose, arms by the same two-bone solve as the game

SHOULDER_R = 0.72
SHOULDER_Y = -0.86
CENTRE_Y = 2.45
HAND_R = 1.95
# How the game draws the pieces (TomatoDevilRenderer): the arms' thickness across, the hands' size.
ARM_THICK = (1.34, 1.0, 1.34)
HAND_SCALE = 1.56


def ik(s, w, pole):
    d = w - s
    ln = np.linalg.norm(d)
    ln = min(ln, (UPPER + FORE) * 0.999)
    u = d / np.linalg.norm(d)
    a = (UPPER ** 2 + ln ** 2 - FORE ** 2) / (2 * UPPER * ln)
    a = math.acos(max(-1, min(1, a)))
    p = pole - u * np.dot(pole, u)
    p /= np.linalg.norm(p)
    return s + (u * math.cos(a) + p * math.sin(a)) * UPPER


def frame_y(axis, z_hint):
    y = axis / np.linalg.norm(axis)
    z = z_hint - y * np.dot(z_hint, y)
    z /= np.linalg.norm(z)
    x = np.cross(y, z)
    return np.stack([x, y, z], axis=1)


def preview():
    import eternity_devil_3d as ED  # noqa (for its sheet helpers)
    bm, eyes = body_model()
    pm = parts_model()
    items = []
    tr = np.array([0, CENTRE_Y, 0])
    for g, md in zip(bm.grids, bm.meta):
        fc = K.Faces(g, pivot=md['pivot'])
        fc.p = fc.p + tr
        items.append(fc)
    byname = {md['name']: g for g, md in zip(pm.grids, pm.meta)}

    def place(name, R, t, scale=1.0, axes=(1.0, 1.0, 1.0)):
        fc = K.Faces(byname[name])
        fc.p = (fc.p * scale * np.array(axes)) @ R.T + t
        fc.n = fc.n @ R.T
        fc.ns = fc.ns @ R.T
        fc.scale = fc.scale * scale
        items.append(fc)

    for e in eyes:
        c, n, r = np.array(e[:3]), np.array(e[3:6]), e[6]
        R = frame_y(np.cross(n, [1, 0, 0]) if abs(n[0]) < 0.9 else np.cross(n, [0, 1, 0]), n)
        # +Z of the eye toward n
        zc = n
        xc = np.cross([0, 1, 0], zc)
        if np.linalg.norm(xc) < 1e-3:
            xc = np.array([1.0, 0, 0])
        xc /= np.linalg.norm(xc)
        yc = np.cross(zc, xc)
        place('eye', np.stack([xc, yc, zc], axis=1), c + tr, r / EYE_R)
    for i in range(8):
        az = math.radians(22.5 + 45 * i)
        out = np.array([math.sin(az), 0, math.cos(az)])
        s = np.array([out[0] * SHOULDER_R, CENTRE_Y + SHOULDER_Y, out[2] * SHOULDER_R])
        w = np.array([out[0] * HAND_R, 0.085, out[2] * HAND_R])
        e = ik(s, w, out + np.array([0, 1.2, 0]))
        # (as the game draws them: arms 1.4 thick, hands 1.56 big)
        place('upper', frame_y(e - s, -out), s, 1.0, ARM_THICK)
        place('fore', frame_y(w - e, -out), e, 1.0, ARM_THICK)
        side = 1.0 if math.sin(az) < 0 else -1.0
        hz = out
        hxv = np.cross([0, 1, 0], hz)
        hxv /= np.linalg.norm(hxv)
        R = np.stack([hxv, np.array([0, 1.0, 0]), hz], axis=1)
        place('hand_r' if side > 0 else 'hand_l', R, w, HAND_SCALE)
        place('fingers_r' if side > 0 else 'fingers_l', R, w, HAND_SCALE)
    lights = [((-0.4, 0.9, 0.5), (1.0, 0.95, 0.85), 0.75), ((0.6, 0.3, -0.6), (0.7, 0.75, 0.9), 0.25)]
    rims = [((0.8, 0.4, -0.3), (255, 150, 90), 0.5)]
    tiles = []
    for yaw, pitch, dist, cy in [(0, 6, 4.2, CENTRE_Y), (35, 10, 4.6, CENTRE_Y), (90, 6, 4.6, CENTRE_Y), (0, 10, 7.5, 1.6), (40, 15, 8.0, 1.6), (180, 15, 5.0, CENTRE_Y)]:
        a, p = math.radians(yaw), math.radians(pitch)
        centre = np.array([0, cy, 0])
        pos = centre + dist * np.array([math.sin(a) * math.cos(p), math.sin(p), math.cos(a) * math.cos(p)])
        cam = K.Camera(pos, centre, fov=50, size=(360, 360))
        im = K.render(items, cam, lights, rims, ambient=0.55, bg=(120, 150, 190))
        tiles.append(ED.label(im, 'yaw %d pitch %d' % (yaw, pitch), 12))
    out = os.environ.get('TOMATO_PREVIEW', os.path.join(OUT, 'look_rest.png'))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet = ED.sheet(tiles, 3)
    from PIL import Image
    Image.fromarray(sheet).save(out)
    print('wrote', out)


def build():
    bm, eyes = body_model()
    n, counts = bm.write(OUT_ASSETS, 'tomato_body_mesh.bin', 'tomato_body_parts.json',
                         note='The Tomato Devil\'s body (tools/tomato_devil_3d.py)', extra={'eyes': [[round(x, 4) for x in e] for e in eyes]})
    print('body', n, counts, len(eyes), 'eyes')
    pm = parts_model()
    n, counts = pm.write(OUT_ASSETS, 'tomato_parts_mesh.bin', 'tomato_parts_parts.json', note='The Tomato Devil\'s pieces (tools/tomato_devil_3d.py)')
    print('parts', n, counts)


TEX = os.path.join(ROOT, 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'textures')


def textures():
    """The cut on its skin, the juice it leaves on the ground when it bursts, and its carcass's item picture."""
    from PIL import Image
    rng = np.random.default_rng(11)
    # the gash: 32x16, a lens of dark pulp, pale seeds and jelly along it, ragged red edges
    w, h = 32, 16
    im = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            u = (x + 0.5) / w * 2 - 1
            v = (y + 0.5) / h * 2 - 1
            half = (1 - u * u) ** 0.8 * 0.9 + rng.uniform(-0.08, 0.08)
            if abs(v) < half:
                k = abs(v) / max(half, 1e-3)
                if k > 0.72:
                    c = (150, 22, 14)
                elif k > 0.45:
                    c = (226, 120, 60) if rng.random() < 0.35 else (200, 50, 30)
                else:
                    c = (60, 6, 6) if rng.random() > 0.18 else (236, 214, 150)
                im[y, x] = (*c, 255)
    Image.fromarray(im, 'RGBA').save(os.path.join(TEX, 'entity', 'tomato_gash.png'))
    # the splat: 64x64, one ring of red juice thrown out round where it burst (thick at its rim, thin inside), a few seeds in it
    n = 64
    im = np.zeros((n, n, 4), np.uint8)
    yy, xx = np.mgrid[0:n, 0:n]
    cx = cy = n / 2
    ang = np.arctan2(yy - cy, xx - cx)
    r = np.hypot(xx - cx, yy - cy) / (n / 2)
    edge = 0.86 + 0.05 * np.sin(ang * 7 + 1) + 0.03 * np.sin(ang * 13 + 2) + 0.02 * np.sin(ang * 23)
    blot = r < edge
    rim = (r > edge - 0.2) & blot
    shade = np.clip(0.85 + rng.uniform(-0.08, 0.08, (n, n)), 0.4, 1.0)
    shade = np.where(rim, shade * 0.75, shade)
    im[..., 0] = (165 * shade).astype(np.uint8)
    im[..., 1] = (18 * shade).astype(np.uint8)
    im[..., 2] = (14 * shade).astype(np.uint8)
    im[..., 3] = np.where(rim, 235, np.where(blot, np.clip(60 + r * 90, 60, 150), 0)).astype(np.uint8)
    for _ in range(14):
        x, y = rng.integers(14, 50, 2)
        if blot[y, x]:
            im[y, x] = (236, 216, 150, 255)
            im[y, min(n - 1, x + 1)] = (220, 196, 128, 255)
    Image.fromarray(im, 'RGBA').save(os.path.join(TEX, 'entity', 'tomato_splat.png'))
    # a strand of spit and juice: 4x4, pinkish red, a pale wet line down its middle, see-through at its edges
    st = np.zeros((4, 4, 4), np.uint8)
    for v in range(4):
        st[v, :] = (150, 30, 30, 120) if v in (0, 3) else (215, 120, 110, 220)
    Image.fromarray(st, 'RGBA').save(os.path.join(TEX, 'entity', 'tomato_strand.png'))
    # the carcass: 16x16, a burst red husk with an eye and a limp hand
    pal = {'.': (0, 0, 0, 0), 'r': (194, 35, 26, 255), 'R': (232, 80, 64, 255), 'd': (110, 14, 10, 255), 'k': (60, 6, 6, 255),
           'g': (63, 116, 34, 255), 'G': (40, 80, 22, 255), 'w': (238, 230, 214, 255), 'p': (20, 10, 6, 255), 's': (201, 166, 144, 255),
           'S': (160, 122, 104, 255), 'y': (236, 214, 150, 255)}
    art = [
        '......gGg.......',
        '....gg.g.gg.....',
        '...drrrRRrrd....',
        '..drrRRRrrrrd...',
        '.drrwwrrrkyrrd..',
        '.drwpwrrkkkrrd..',
        'drrrwwrkyykyrrd.',
        'drrrrrrkkkkkrrd.',
        'drrrrrrrkkrrrrd.',
        '.drrrrrrrrrrrd..',
        '.ddrrrrrrrrrdd..',
        '...ddrrrrrdd....',
        '.....dddddsS....',
        '..........sSs...',
        '.........sSsSs..',
        '..........s.s...',
    ]
    im = np.array([[pal[ch] for ch in row] for row in art], np.uint8)
    os.makedirs(os.path.join(TEX, 'item'), exist_ok=True)
    Image.fromarray(im, 'RGBA').save(os.path.join(TEX, 'item', 'tomato_carcass.png'))
    print('textures written')


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'preview'
    if what == 'build':
        build()
    elif what == 'textures':
        textures()
    else:
        preview()
