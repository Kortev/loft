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
# A ripe tomato, glossy: dark in the creases, a hot red, a pale shine where the light lies on it.
RED = [hx(c) for c in ('#3a0604', '#5e0a07', '#82110b', '#a5180f', '#c2231a', '#d8352a', '#e85040', '#f3765f')]
LID = [hx(c) for c in ('#2e0504', '#4a0806', '#6a0d09', '#8a140d', '#a51d14')]
GREEN = [hx(c) for c in ('#13260b', '#1f3d11', '#2e5719', '#3f7422', '#56912e')]
INNER = [hx(c) for c in ('#120203', '#260405', '#3e0807', '#5e0e0b')]
GUM = [hx(c) for c in ('#4e0a10', '#701420', '#8e2230')]
TOOTH = [hx(c) for c in ('#8a7a50', '#bfae80', '#ddd0a8', '#f0e8cc')]
# Human arms: pale, a little grey and blotchy, red at the knuckles and elbows.
SKIN = [hx(c) for c in ('#4c362e', '#6a4c40', '#876354', '#a07a68', '#b8917c', '#c9a690', '#d8baa4')]
NAIL = hx('#d9c2b4')
SCLERA = [hx(c) for c in ('#a89a84', '#cfc4ae', '#e6dcc8', '#f4ecdc')]
VEIN = hx('#b0302a')
IRIS = [hx(c) for c in ('#3a2208', '#6e4410', '#a0701c', '#c89a30')]
PUPIL = hx('#050203')
SEED = [hx(c) for c in ('#9c8a58', '#cbb886', '#e6d8aa', '#f6eecb')]
PULP = [hx(c) for c in ('#8a1a0e', '#b8301a', '#e05a2c')]

RX = 1.30            # the body's half width
RY = 1.06            # its half height
UPPER = 1.30
FORE = 1.20
EYE_R = 0.20


# ---------------------------------------------------------------- the body

def body_sdf(px, py, pz):
    """The tomato: a squat ball with soft lobes round it, a dimple on top where the calyx sits, a little flat underneath."""
    az = np.arctan2(px, pz)
    yy = np.clip(py / RY, -1, 1)
    lobe = 1.0 + 0.045 * np.cos(5 * az + 0.4) * (1 - yy ** 2) + 0.015 * np.cos(11 * az) * (1 - yy ** 2)
    rx = RX * lobe
    ry = np.where(py < 0, RY * 0.96, RY)
    k = np.sqrt((px / rx) ** 2 + (py / ry) ** 2 + (pz / rx) ** 2)
    d = (k - 1.0) * 1.08
    rr = px ** 2 + pz ** 2
    d = d + 0.20 * np.exp(-rr / 0.10) * (py > 0)
    return d


MOUTH_Y = -0.03
MOUTH_HY = 0.66      # half its height
MOUTH_HX = 0.30      # half its width at the lips


def in_mouth(px, py, pz):
    """The lips' ellipse on the front, seen face on."""
    return ((px / MOUTH_HX) ** 2 + ((py - MOUTH_Y) / MOUTH_HY) ** 2 < 1.0) & (pz > 0.5)


def cavity_sdf(px, py, pz):
    """The inside of the mouth: a deep throat behind the lips."""
    return K.ell(px, py, pz, (0.0, MOUTH_Y, 0.95), (0.27, 0.60, 0.50))


def eye_list():
    """Where the eyes are: all round, bigger at the front and round the middle, none on the calyx, the mouth's strip or the underside."""
    rng = np.random.default_rng(7)
    eyes = []
    # hand placed: the big ones at the front either side of the mouth and over it
    fixed = [(-0.62, 0.30, 0.215), (0.60, 0.36, 0.21), (-0.32, 0.72, 0.17), (0.34, 0.68, 0.16), (0.0, 0.86, 0.13),
             (-0.80, -0.22, 0.17), (0.82, -0.18, 0.18), (-0.45, -0.62, 0.13), (0.47, -0.58, 0.12),
             (-1.05, 0.30, 0.15), (1.05, 0.26, 0.15)]
    tries = 0
    pts = []
    for (u, v, r) in fixed:
        pts.append((u, v, r))
    while len(pts) < 160 and tries < 4000:
        tries += 1
        az = rng.uniform(-math.pi, math.pi)
        el = rng.uniform(-0.55, 0.95)
        pts.append(('a', az, el, rng.uniform(0.09, 0.17)))
    out = []
    for p in pts:
        if p[0] == 'a':
            _, az, el, r = p
            d = np.array([math.sin(az) * math.cos(el), math.sin(el), math.cos(az) * math.cos(el)])
        else:
            u, v, r = p
            # u, v: where on the front (seen face on), on a unit sphere
            w = math.sqrt(max(0.0, 1.0 - u * u * 0.6 - v * v * 0.6))
            d = np.array([u, v, w])
            d /= np.linalg.norm(d)
        # walk the ray out to the surface
        c = surface_point(d)
        n = surface_normal(c)
        if n[1] > 0.88 or n[1] < -0.62:
            continue
        if abs(c[0]) < MOUTH_HX + r + 0.08 and c[2] > 0.4 and abs(c[1] - MOUTH_Y) < MOUTH_HY + 0.05:
            continue
        ok = True
        for e in out:
            if np.linalg.norm(np.array(e[:3]) - c) < (e[6] + r) * 1.4 + 0.05:
                ok = False
                break
        if ok and len(out) < 26:
            centre = c + n * r * 0.42
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


def sepal_sdf(px, py, pz):
    """The calyx: six green leaves lying out over the top from the stem, and the stem."""
    d = K.caps(px, py, pz, (0, RY - 0.24, 0), (0, RY + 0.08, 0.02), 0.07, 0.05)
    for k in range(6):
        a = k * math.pi / 3 + 0.25
        pts = []
        for i in range(6):
            t = i / 5
            rad = 0.06 + 0.62 * t
            y = surface_point(np.array([math.sin(a) * rad, RY, math.cos(a) * rad]) / np.linalg.norm([math.sin(a) * rad, RY, math.cos(a) * rad]))[1]
            twist = 0.12 * math.sin(t * 3 + k)
            pts.append((math.sin(a + twist) * rad, y + 0.035, math.cos(a + twist) * rad))
        leaf = K.polyline(px, py, pz, pts, 0.085, 0.02)
        # flattened: thin across the body's surface
        d = np.minimum(d, np.maximum(leaf, np.abs(py - interp_y(px, pz, pts)) - 0.035))
    return d


def interp_y(px, pz, pts):
    rr = np.sqrt(px ** 2 + pz ** 2)
    ys = np.array([p[1] for p in pts])
    rs = np.array([math.hypot(p[0], p[2]) for p in pts])
    return np.interp(rr, rs, ys)


def body_model():
    eyes = eye_list()
    noise = K.Noise(3)
    s = K.S16
    lo, hi = (-1.5, -1.25, -1.5), (1.5, 1.45, 1.5)

    def lids(px, py, pz):
        d = None
        for e in eyes:
            c, n, r = np.array(e[:3]), np.array(e[3:6]), e[6]
            base = c - n * r * 0.42
            ring = K.sphere(px, py, pz, base + n * r * 0.18, r * 1.32)
            hole = K.sphere(px, py, pz, c + n * r * 0.55, r * 1.02)
            di = np.maximum(ring, -hole)
            d = di if d is None else np.minimum(d, di)
        return d

    def sockets(px, py, pz):
        d = None
        for e in eyes:
            c, r = np.array(e[:3]), e[6]
            di = K.sphere(px, py, pz, c, r * 1.02)
            d = di if d is None else np.minimum(d, di)
        return d

    def layers_body(px, py, pz):
        b = body_sdf(px, py, pz)
        cav = cavity_sdf(px, py, pz)
        mouth = in_mouth(px, py, pz)
        shell = np.where(mouth, 1.0, b)                       # the lips are parts of their own
        throat =np.maximum(np.maximum(b, cav - 0.1), -cav)    # a lining 0.1 thick round the cavity
        flesh = np.maximum(shell, -cav)
        flesh = np.maximum(flesh, -sockets(px, py, pz))
        ld = np.maximum(lids(px, py, pz), -sockets(px, py, pz))
        ld = np.maximum(ld, -cav)
        out = [('flesh', flesh), ('gum', np.maximum(throat, -(b + 0.06))), ('inner', np.maximum(throat, b + 0.06)), ('lid', ld),
               ('green', sepal_sdf(px, py, pz))]
        return out

    def paint(ctx):
        n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 2.2)
        n2 = ctx.noise(ctx.px + 5.1, ctx.py, ctx.pz, 6.0)
        L = np.array([-0.45, 0.8, 0.4])
        L /= np.linalg.norm(L)
        lit = (ctx.normal * L).sum(-1)
        t = 0.55 + 0.32 * lit + (n - 0.5) * 0.25 - ctx.ao * 0.5
        # the shine: a soft highlight where the light lies, and a narrow hot one at its middle
        t = t + 0.25 * np.clip((lit - 0.72) * 5.0, 0, 1)
        col = K.pick(RED, t, n2, 0.35)
        em = np.zeros(ctx.px.shape, np.float32)
        top = ctx.py > RY - 0.55
        rr = np.sqrt(ctx.px ** 2 + ctx.pz ** 2)
        # a greenish-orange blush round the calyx, as a tomato has
        blush = top & (rr < 0.55) & ctx.is_('flesh')
        col = np.where(blush[..., None], col * 0.75 + hx('#7a4a14') * 0.25, col)
        m = ctx.is_('lid')
        col = np.where(m[..., None], K.pick(LID, 0.5 + 0.35 * lit + (n - 0.5) * 0.4 - ctx.ao * 0.6, n2), col)
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
            cav = cavity_sdf(px, py, pz)
            mouth = in_mouth(px, py, pz)
            lip = np.where(mouth & (side * px > 0.0), b, 1.0)
            # a lip is the skin over the mouth, 0.16 thick, with a rolled edge where the halves meet
            lip = np.maximum(lip, -(b + 0.16))
            edge = K.caps(px, py, pz, (side * 0.03, MOUTH_Y - MOUTH_HY * 0.92, 1.02), (side * 0.03, MOUTH_Y + MOUTH_HY * 0.92, 1.02), 0.05)
            edge = np.maximum(edge, b - 0.02)
            lipd = np.minimum(lip, np.where(side * px > 0.0, edge, 1.0))
            lipd = np.maximum(lipd, -(side * px - 0.004))
            # teeth along the inner edge, behind the lip: uneven pegs
            teeth = None
            for i in range(9):
                y = MOUTH_Y - MOUTH_HY * 0.78 + i * (MOUTH_HY * 1.56 / 8)
                wid = MOUTH_HX * math.sqrt(max(0.05, 1 - ((y - MOUTH_Y) / MOUTH_HY) ** 2))
                zc = surface_point(np.array([0.0, y, 1.0]) / np.linalg.norm([0.0, y, 1.0]))[2] - 0.13
                ln = 0.07 + 0.03 * ((i * 7) % 3)
                t = K.caps(px, py, pz, (side * 0.09, y, zc), (side * (0.09 - ln), y + 0.01 * ((i % 2) * 2 - 1), zc - 0.01), 0.035, 0.016)
                teeth = t if teeth is None else np.minimum(teeth, t)
            teeth = np.maximum(teeth, -(side * px - 0.004))
            return [('flesh', lipd), ('tooth', teeth)]
        return f

    def lip_paint(ctx):
        col, em = paint(ctx)
        n = ctx.noise(ctx.px * 2, ctx.py * 2, ctx.pz * 2, 5.0)
        m = ctx.is_('tooth')
        col = np.where(m[..., None], K.pick(TOOTH, 0.75 - ctx.ao * 0.6 + (n - 0.5) * 0.4, n), col)
        # the lips' edge darker, where they meet
        edge = (np.abs(ctx.px) < 0.07) & ctx.is_('flesh')
        col = np.where(edge[..., None], col * 0.62, col)
        return col, em

    lip_l = K.voxelize('lip_l', (-0.45, -0.8, 0.4), (0.06, 0.75, 1.45), s, lip_layers(-1.0), lip_paint, noise, min_nb=2)
    lip_r = K.voxelize('lip_r', (-0.06, -0.8, 0.4), (0.45, 0.75, 1.45), s, lip_layers(1.0), lip_paint, noise, min_nb=2)
    m = K.Model('tomato_body', 'blocks, the body centre at the origin, its mouth toward +Z, up +Y')
    m.add(body, pivot=(0, 0, 0), anim=None, kind='body')
    piv_l = surface_point(np.array([-MOUTH_HX, MOUTH_Y, 1.2]) / np.linalg.norm([-MOUTH_HX, MOUTH_Y, 1.2]))
    piv_r = surface_point(np.array([MOUTH_HX, MOUTH_Y, 1.2]) / np.linalg.norm([MOUTH_HX, MOUTH_Y, 1.2]))
    m.add(lip_l, pivot=[float(x) for x in piv_l], anim=None, kind='grab_lip')
    m.add(lip_r, pivot=[float(x) for x in piv_r], anim=None, kind='grab_lip')
    return m, eyes


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

    def fingers(side):
        def f(px, py, pz):
            d = None
            nails = None
            # index (next to the thumb) to little finger
            xs = [0.098, 0.033, -0.033, -0.098]
            ls = [0.27, 0.31, 0.29, 0.22]
            for x, ln in zip(xs, ls):
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

    # an eyeball: bloodshot, a yellow-brown iris and a black pupil looking along +Z
    def eye(px, py, pz):
        return [('sclera', K.sphere(px, py, pz, (0, 0, 0), EYE_R))]

    def eye_paint(ctx):
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 6.0)
        r = np.sqrt(ctx.px ** 2 + ctx.py ** 2 + ctx.pz ** 2) + 1e-6
        fz = ctx.pz / r
        col = K.pick(SCLERA, 0.75 + (n - 0.5) * 0.4 - (1 - fz) * 0.3, n)
        veins = (np.abs(ctx.noise.fbm(ctx.px * 4, ctx.py * 4, ctx.pz * 4, 2.0) - 0.5) < 0.025) & (fz < 0.7)
        col = np.where(veins[..., None], VEIN, col)
        iris = fz > 0.80
        col = np.where(iris[..., None], K.pick(IRIS, (fz - 0.8) * 4.0 + (n - 0.5) * 0.4, n), col)
        col = np.where((fz > 0.935)[..., None], PUPIL, col)
        em = np.where(fz > 0.80, 0.0, 0.08).astype(np.float32)
        return col, em

    ey = K.voxelize('eye', (-0.24, -0.24, -0.24), (0.24, 0.24, 0.24), s, eye, eye_paint, noise)

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
    return m


# ---------------------------------------------------------------- preview: the rest pose, arms by the same two-bone solve as the game

SHOULDER_R = 0.72
SHOULDER_Y = -0.86
CENTRE_Y = 2.45
HAND_R = 1.95


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

    def place(name, R, t, scale=1.0):
        fc = K.Faces(byname[name])
        fc.p = (fc.p * scale) @ R.T + t
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
        place('upper', frame_y(e - s, -out), s)
        place('fore', frame_y(w - e, -out), e)
        side = 1.0 if math.sin(az) < 0 else -1.0
        hz = out
        hxv = np.cross([0, 1, 0], hz)
        hxv /= np.linalg.norm(hxv)
        R = np.stack([hxv, np.array([0, 1.0, 0]), hz], axis=1)
        place('hand_r' if side > 0 else 'hand_l', R, w)
        place('fingers_r' if side > 0 else 'fingers_l', R, w)
    lights = [((-0.4, 0.9, 0.5), (1.0, 0.95, 0.85), 0.75), ((0.6, 0.3, -0.6), (0.7, 0.75, 0.9), 0.25)]
    rims = [((0.8, 0.4, -0.3), (255, 150, 90), 0.5)]
    tiles = []
    for yaw, pitch, dist in [(90, 8, 8.5), (30, 15, 8.0), (0, 10, 7.5), (200, 20, 8.0), (60, 45, 9.0), (90, 2, 6.5)]:
        a, p = math.radians(yaw), math.radians(pitch)
        centre = np.array([0, 1.6, 0])
        pos = centre + dist * np.array([math.sin(a) * math.cos(p), math.sin(p), math.cos(a) * math.cos(p)])
        cam = K.Camera(pos, centre, fov=50, size=(360, 360))
        im = K.render(items, cam, lights, rims, ambient=0.55, bg=(120, 150, 190))
        tiles.append(ED.label(im, 'yaw %d pitch %d' % (yaw, pitch), 12))
    os.makedirs(OUT, exist_ok=True)
    sheet = ED.sheet(tiles, 3)
    from PIL import Image
    Image.fromarray(sheet).save(os.path.join(OUT, 'look_rest.png'))
    print('wrote', os.path.join(OUT, 'look_rest.png'))


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
    # the splat: 64x64, a blot of red juice with droplets thrown out and seeds in it
    n = 64
    im = np.zeros((n, n, 4), np.uint8)
    yy, xx = np.mgrid[0:n, 0:n]
    cx = cy = n / 2
    ang = np.arctan2(yy - cy, xx - cx)
    r = np.hypot(xx - cx, yy - cy) / (n / 2)
    edge = 0.55 + 0.12 * np.sin(ang * 7 + 1) + 0.08 * np.sin(ang * 13 + 2) + 0.05 * np.sin(ang * 23)
    blot = r < edge
    for _ in range(26):
        a = rng.uniform(0, 2 * np.pi)
        d = rng.uniform(0.6, 0.95)
        s = rng.uniform(0.03, 0.08)
        blot |= np.hypot(xx - (cx + np.cos(a) * d * n / 2), yy - (cy + np.sin(a) * d * n / 2)) / (n / 2) < s
    shade = np.clip(1.0 - r * 0.5 + rng.uniform(-0.08, 0.08, (n, n)), 0.4, 1.0)
    im[..., 0] = (170 * shade).astype(np.uint8)
    im[..., 1] = (22 * shade).astype(np.uint8)
    im[..., 2] = (14 * shade).astype(np.uint8)
    im[..., 3] = np.where(blot, np.clip(235 - r * 60, 120, 235), 0).astype(np.uint8)
    for _ in range(14):
        x, y = rng.integers(14, 50, 2)
        if blot[y, x]:
            im[y, x] = (236, 216, 150, 255)
            im[y, min(n - 1, x + 1)] = (220, 196, 128, 255)
    Image.fromarray(im, 'RGBA').save(os.path.join(TEX, 'entity', 'tomato_splat.png'))
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
