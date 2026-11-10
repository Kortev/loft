"""The core: a lumpy grey figure 8, about 1.5 blocks across, crying. Two human eyes (bloodshot but mostly white, a small dark wet iris,
HEAVY lids and a swollen bag under each eye), an eye and a mouth on each loop, two small mouths at the waist (wiki), the mouths
sagging (corners down, a slack lower lip) with small uneven stained teeth.

Voxels: the body, lids and mouths are 1/32 block, the eyeballs and tears 1/64, so the mesh's json carries posUnit 64 (quad positions
and sizes are shorts in 1/64 block; every other Eternity mesh is 1/16).

Frame (blocks): X toward the players (the front), Y up, Z across; origin at the centre of the figure 8, the loops at Z = -0.36 / +0.36.
Parts: body, eye_L/eye_R (roll), lid_L/lid_R (blink), tear_L/tear_R (fall), mouth_0..3 (the lower lip with its teeth: hinge, sob open).
Left/right are by Z: L = -Z, R = +Z.  mouth_0/1 = the loops' mouths (L, R), mouth_2/3 = the two at the waist (upper, lower).
"""
import math

import numpy as np

import eternity_3d_kit as K
from eternity_3d_kit import ell, sphere, box, caps, polyline, smin, smax, S32, S64

COOL = K.FLESH_COOL
LOBE_Z = 0.36
EYE_R = 0.215
GAZE = np.array([0.98, -0.2, 0.0])      # the iris looks a little down: pitiful, not startled
GAZE = GAZE / np.linalg.norm(GAZE)
EYES = {'L': np.array([0.27, 0.17, -LOBE_Z]), 'R': np.array([0.27, 0.17, LOBE_Z])}
# mouths: centre (the x is snapped to the surface in build), half width, half height at the middle, corner droop
MOUTHS = [
    dict(c=np.array([0.0, -0.21, -LOBE_Z]), hw=0.3, hh=0.15, sag=0.22),
    dict(c=np.array([0.0, -0.21, LOBE_Z]), hw=0.3, hh=0.15, sag=0.22),
    dict(c=np.array([0.0, 0.15, 0.0]), hw=0.14, hh=0.07, sag=0.1),       # the two at the waist
    dict(c=np.array([0.0, -0.1, 0.0]), hw=0.14, hh=0.07, sag=0.1),
]


def body_raw(noise):
    rr = np.random.default_rng(3)
    lumps = []
    for i in range(60):
        a = rr.uniform(0, 2 * math.pi)
        b = rr.uniform(-1, 1)
        s = rr.choice([-1, 1])
        # a bulge near the surface of one loop
        p = np.array([0.4 * math.cos(a) * math.sqrt(1 - b * b), 0.5 * b, s * LOBE_Z + 0.4 * math.sin(a) * math.sqrt(1 - b * b)])
        if p[0] > 0.1 and -0.5 < p[1] < 0.42:
            continue                      # the face stays clear of random lumps
        lumps.append((p, rr.uniform(0.06, 0.16) * np.array([1, 1, 1]) * rr.uniform(0.8, 1.3)))

    def fn(px, py, pz):
        d = None
        for s in (-1, 1):
            e = ell(px, py, pz, (0.0, 0.0, s * LOBE_Z), (0.42, 0.5, 0.4))
            d = e if d is None else smin(d, e, 0.14)
            d = smin(d, ell(px, py, pz, (0.2, 0.34, s * LOBE_Z), (0.2, 0.13, 0.26)), 0.08)          # a heavy brow over each eye
            d = smin(d, ell(px, py, pz, (0.27, -0.03, s * LOBE_Z), (0.12, 0.085, 0.21)), 0.06)      # the swollen bag under each eye
        d = smin(d, ell(px, py, pz, (0.17, 0.02, 0.0), (0.12, 0.26, 0.11)), 0.07)                   # the waist bulges where its mouths are
        for c, r in lumps:
            d = smin(d, ell(px, py, pz, c, r), 0.07)
        d = d + (noise.fbm(px, py, pz, 3.0) - 0.5) * 0.16 + (noise.fbm(px, py, pz, 9.0, 3) - 0.5) * 0.06 + (noise.fbm(px, py, pz, 21.0, 2) - 0.5) * 0.02
        # skin folds: creases cut into the lumps
        cr = np.abs(noise.fbm(px + 2, py, pz, 5.0, 2) - 0.5)
        d = d + 0.028 * np.clip(1 - cr / 0.05, 0, 1)
        return d
    return fn


def surface_x(raw, y, z, hi=0.9, step=0.004):
    xs = np.arange(hi, 0.0, -step)
    d = raw(xs, np.full_like(xs, y), np.full_like(xs, z))
    ins = np.where(d < 0)[0]
    return float(xs[ins[0]]) if len(ins) else 0.3


def mouth_lines(M):
    """top(z), bot(z): the mouth's upper and lower edge: a crescent with the corners drawn down."""
    def yc(z):
        t = (z - M['c'][2]) / M['hw']
        return M['c'][1] + 0.03 - M['sag'] * t * t

    def hh(z):
        t = (z - M['c'][2]) / M['hw']
        return M['hh'] * np.sqrt(np.clip(1 - t * t, 0.05, 1.0))

    return (lambda z: yc(z) + hh(z)), (lambda z: yc(z) - hh(z))


DEPTH = 0.15       # how far behind its centre x the mouth's cavity goes


def cavity(px, py, pz, M, top, bot):
    d2 = np.maximum(py - top(pz), bot(pz) - 0.07 - py)           # open below the lip, so it can swing open
    d2 = np.maximum(d2, np.abs(pz - M['c'][2]) - M['hw'])
    return np.maximum(d2, (M['c'][0] - DEPTH) - px)


def upper_teeth(M, top, rr):
    """(z, width/2, length) of each upper tooth: uneven, some gaps, a few snapped stubs. None taller than 1/8 block."""
    out = []
    n = max(4, int(2 * M['hw'] / 0.052))
    for i in range(n):
        z = M['c'][2] - M['hw'] * 0.84 + (i + 0.5) * (M['hw'] * 1.68 / n) + rr.uniform(-0.008, 0.008)
        u = rr.random()
        if u < 0.1:
            continue                                  # a missing tooth
        L = rr.uniform(0.06, 0.13) if u > 0.35 else rr.uniform(0.02, 0.045)      # the short ones are snapped
        out.append((z, rr.uniform(0.014, 0.03), L, rr.uniform(0.018, 0.026)))
    return out


def lower_teeth(M, bot, rr):
    out = []
    n = max(3, int(2 * M['hw'] / 0.068))
    for i in range(n):
        z = M['c'][2] - M['hw'] * 0.78 + (i + 0.5) * (M['hw'] * 1.56 / n) + rr.uniform(-0.012, 0.012)
        u = rr.random()
        if u < 0.22:
            continue
        L = rr.uniform(0.035, 0.09) if u > 0.5 else rr.uniform(0.015, 0.03)
        out.append((z, rr.uniform(0.014, 0.028), L, rr.uniform(0.018, 0.026)))
    return out


def body_fn(noise, small_eyes, mouths):
    raw = body_raw(noise)

    def fn(px, py, pz):
        d = raw(px, py, pz)
        for k, E in EYES.items():
            d = smax(d, -caps(px, py, pz, E, E + np.array([0.4, 0.0, 0.0]), EYE_R + 0.012), 0.02)          # the socket the eye sits in (open to the front)
        carves = []
        for (M, top, bot, ut) in mouths:
            cav = cavity(px, py, pz, M, top, bot)
            d = smax(d, -cav, 0.015)
            carves.append((M, cav, ut, top))
        for c in small_eyes:
            d = smax(d, -sphere(px, py, pz, c, 0.07), 0.01)
        layers = [('flesh', d)]
        for (M, cav, ut, top) in carves:
            layers.append(('inner', np.maximum(d, cav - 0.03)))
            for (z, hw, L, th) in ut:
                ty = float(top(np.array(z)))
                layers.append(('tooth_bone', box(px, py, pz, (M['c'][0] - 0.01, ty + 0.02 - (L + 0.02) * 0.5, z), (th, (L + 0.02) * 0.5, hw))))
        for c in small_eyes:
            layers.append(('redeye', sphere(px, py, pz, (c[0] - 0.02, c[1], c[2]), 0.035)))
        return layers
    return fn


BONE = [K.hx(c) for c in ('#6e6244', '#9c8e62', '#c2b484', '#d9cc9c', '#e8dfb4')]       # dull bone-yellow, never brown


def _bone(ctx, mask):
    n = ctx.noise(ctx.px * 2, ctx.py * 2, ctx.pz * 2, 5.0)
    t = 0.72 - ctx.ao * 0.6 + (n - 0.5) * 0.5
    return K.pick(BONE, t, n), 0.0


def body_paint(streaks):
    def f(ctx):
        col, em = K.standard_paint(ctx, ramp=COOL, base=0.46, vein=0.018, noise_f=3.5, special={'tooth_bone': _bone})
        # the swollen bags under the eyes and the heavy brows: darker, bruised grey
        for k, E in EYES.items():
            dz = ctx.pz - E[2]
            bag = ((ctx.py - (E[1] - 0.2)) / 0.095) ** 2 + (dz / 0.22) ** 2 + ((ctx.px - 0.3) / 0.3) ** 2 < 1.0
            bag &= ctx.normal[..., 0] > -0.2
            col = np.where(bag[..., None], col * np.array([0.78, 0.72, 0.78]), col)
            sock = np.sqrt((ctx.px - E[0]) ** 2 + (ctx.py - E[1]) ** 2 + dz ** 2) < EYE_R + 0.045
            col = np.where(sock[..., None], col * np.array([0.62, 0.56, 0.6]), col)
        # wet tear streaks down the cheeks to the mouths
        for (E, sgn) in streaks:
            for dz, wiggle in ((0.2 * sgn, 0.0), (0.12 * sgn, 1.3)):
                zc = E[2] + dz + 0.025 * np.sin(ctx.py * 18 + wiggle)
                m = (np.abs(ctx.pz - zc) < 0.016) & (ctx.py < E[1] - 0.26) & (ctx.py > E[1] - 0.56) & (ctx.normal[..., 0] > 0.15) & \
                    (ctx.px > 0.15)
                t = 0.5 + 0.5 * np.sin(ctx.py * 40)
                col = np.where(m[..., None], K.pick(K.TEAR, t), col)
                em = np.where(m, 0.16, em)
        return col, em
    return f


def eye_fn(E):
    def fn(px, py, pz):
        return [('sclera', sphere(px, py, pz, E, EYE_R))]

    def paint(ctx):
        lx, ly, lz = ctx.px - E[0], ctx.py - E[1], ctx.pz - E[2]
        r = np.sqrt(lx * lx + ly * ly + lz * lz) + 1e-6
        ang = np.arccos(np.clip((lx * GAZE[0] + ly * GAZE[1] + lz * GAZE[2]) / r, -1, 1))
        # azimuth round the gaze axis (for radial veins)
        up = np.array([0.2, 0.98, 0.0])
        up = up / np.linalg.norm(up)
        side = np.cross(GAZE, up)
        phi = np.arctan2(lx * side[0] + ly * side[1] + lz * side[2], lx * up[0] + ly * up[1] + lz * up[2])
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 6.0)
        # sclera: a cream-grey white, yellowed and pinkish toward the corners, darker where the lids shade it
        t = 0.92 - 0.14 * (n - 0.5) - 0.16 * np.clip((ang - 1.1) / 0.9, 0, 1) - 0.18 * ctx.ao
        col = K.pick(K.SCLERA, t, n, 0.25)
        haze = np.clip((ang - 1.0) / 0.6, 0, 1)
        col = col + haze[..., None] * np.array([14.0, -6.0, -6.0])
        # thin red veins creeping in from the rim, radial
        s = 2.4
        rid = np.abs(ctx.noise(np.cos(phi) * s + 5, np.sin(phi) * s, ang * 1.2, 1.0) - 0.5)
        rid2 = np.abs(ctx.noise(np.cos(phi) * s * 1.9 + 11, np.sin(phi) * s * 1.9, ang * 1.5 + 3, 1.0) - 0.5)
        vein = ((rid < 0.026) & (ang > 0.7 + 0.3 * n)) | ((rid2 < 0.02) & (ang > 1.0))
        col = np.where(vein[..., None], np.array([150.0, 44.0, 42.0]), col)
        # iris, pupil, limbal ring, a wet glint (small, so the eye is sad and not startled)
        ir = ang < 0.33
        irc = K.pick(K.IRIS_BROWN, 0.35 + 0.55 * np.clip(ang / 0.33, 0, 1) + 0.2 * (n - 0.5), n, 0.4)
        irc = np.where((np.abs(ang - 0.31) < 0.03)[..., None], np.array(K.IRIS_BROWN[0]) * 0.8, irc)
        col = np.where(ir[..., None], irc, col)
        col = np.where((ang < 0.135)[..., None], K.PUPIL, col)
        # glints: relative to the gaze axis, up and toward the middle of the face
        gu = (lx * up[0] + ly * up[1] + lz * up[2])
        gs = (lx * side[0] + ly * side[1] + lz * side[2])
        g1 = (ang > 0.15) & (ang < 0.27) & (gu > 0.0) & (gs * np.sign(E[2]) > 0.0)
        g1 &= gu > 0.9 * np.abs(gs)
        col = np.where(g1[..., None], np.array([255.0, 253.0, 250.0]), col)
        em = np.where(ir, 0.0, 0.3)
        return col, np.where(g1, 0.9, em)
    return fn, paint


def lid_edge(lz, o):
    """Height (above the eye centre) of the upper lid's lower edge: low in the middle (the top third of the eyeball is covered), the
    outer corner drooping more than the inner one (a sad eye)."""
    t = lz / EYE_R
    return np.clip(0.075 + 0.04 * t * t - 0.07 * (o / EYE_R), 0.0, 0.3)


def lid_fn(E, sgn):
    """The upper lid: a heavy shell over the top of the eyeball, thicker toward its edge, sagging."""
    def fn(px, py, pz):
        lx, ly, lz = px - E[0], py - E[1], pz - E[2]
        r = np.sqrt(lx * lx + ly * ly + lz * lz)
        edge = lid_edge(lz, lz * sgn)
        k = np.clip(1.0 - (ly - edge) / 0.09, 0.0, 1.0)             # 1 at the edge, 0 well above it
        th = 0.04 + 0.032 * k
        shell = np.maximum(np.abs(r - (EYE_R + 0.008 + th * 0.5)) - th * 0.5, np.maximum(-lx - 0.07, edge - ly))
        return [('flesh', shell)]

    def paint(ctx):
        col, em = K.standard_paint(ctx, ramp=COOL, base=0.46, vein=0.03, noise_f=4.0)
        edge = lid_edge(ctx.pz - E[2], (ctx.pz - E[2]) * sgn)
        rel = ctx.py - E[1]
        # a dark, swollen lash line along the edge, and a crease above it
        lash = rel < edge + 0.02
        col = np.where(lash[..., None], np.array([58.0, 48.0, 52.0]), col)
        crease = np.abs(rel - (edge + 0.085)) < 0.011
        col = np.where(crease[..., None], col * 0.6, col)
        return col, em
    return fn, paint


def mouth_jaw_fn(M, top, bot, lt, raw):
    def fn(px, py, pz):
        t = (pz - M['c'][2]) / M['hw']
        b = bot(pz)
        slack = 0.025 * np.clip(1 - t * t, 0, 1)                   # the lip hangs slack in the middle
        # the lower lip: a slab filling the bottom of the cavity, a drooping pouch in front
        d = np.maximum(np.abs(py - (b - 0.012 - slack * 0.5)) - (0.032 + slack * 0.5), np.abs(pz - M['c'][2]) - (M['hw'] - 0.01))
        d = np.maximum(d, np.abs(px - (M['c'][0] - 0.05)) - 0.09)
        d = np.maximum(d - 0.01, raw(px, py, pz) - 0.035)            # hugs the body's skin
        layers = [('flesh', d)]
        for (z, hw, L, th) in lt:
            yb = float(bot(np.array(z)))
            layers.append(('tooth_bone', box(px, py, pz, (M['c'][0] - 0.01, yb + 0.012 + L * 0.5, z), (th, L * 0.5, hw))))
        return layers
    return fn


def build(seed=5):
    global EYES
    noise = K.Noise(23)
    m = K.Model('eternity_core', 'X toward the players (front), Y up, Z across; origin at the centre of the figure 8')
    raw = body_raw(noise)
    for k, E in EYES.items():
        EYES[k] = np.array([surface_x(raw, E[1], E[2]) - 0.125, E[1], E[2]])
    # the surface decides where the mouths sit
    mouths = []
    for i, M0 in enumerate(MOUTHS):
        M = dict(M0)
        c = M0['c'].copy()
        c[0] = surface_x(raw, c[1], c[2]) - 0.025
        M['c'] = c
        top, bot = mouth_lines(M)
        ut = upper_teeth(M, top, np.random.default_rng(100 + i))
        lt = lower_teeth(M, bot, np.random.default_rng(200 + i))
        mouths.append((M, top, bot, ut, lt))
    # a few small red eyes on the back and flanks of the body (guess carried from the concept: dark flesh dotted with small red eyes)
    small = [np.array(p) for p in ((-0.2, 0.42, -0.5), (-0.25, 0.1, 0.6), (-0.3, -0.35, -0.2), (-0.2, 0.5, 0.15), (-0.1, -0.45, 0.4),
                                   (0.05, 0.38, 0.62), (-0.05, -0.3, -0.62), (-0.35, 0.3, 0.3))]
    snapped = []
    for p in small:
        ys, zs = p[1], p[2]
        xs = np.linspace(-1.0, 1.0, 400)
        dd = raw(xs, np.full_like(xs, ys), np.full_like(xs, zs))
        idx = np.where(dd < 0)[0]
        if len(idx):
            x = xs[idx[0]] if p[0] < 0 else xs[idx[-1]]
            snapped.append(np.array([x, ys, zs]))
    streaks = [(EYES['L'], -1), (EYES['R'], 1)]
    mfn = [(M, top, bot, ut) for (M, top, bot, ut, lt) in mouths]
    body = K.voxelize('body', (-0.8, -0.8, -1.0), (0.8, 0.8, 1.0), S32, body_fn(noise, snapped, mfn), body_paint(streaks), noise,
                      keep_specks=False)
    m.add(body, pivot=(0, 0, 0), kind='static')
    for k, E in EYES.items():
        fn, paint = eye_fn(E)
        g = K.voxelize('eye_' + k, E - 0.27, E + 0.27, S64, fn, paint, noise, keep_specks=True)
        m.add(g, pivot=E, anim=dict(kind='roll', axis=[0, 1, 0], axis2=[0, 0, 1], amp=12.0, period=140, phase=0.3 if k == 'L' else 0.0),
              kind='eye', note='the eyeball: the iris looks along gaze at rest (a little down, +X mostly); roll it by rotating about the pivot',
              gaze=[round(float(v), 4) for v in GAZE])
    for k, E in EYES.items():
        sgn = -1 if k == 'L' else 1
        fn, paint = lid_fn(E, sgn)
        g = K.voxelize('lid_' + k, E - 0.34, E + 0.34, S32, fn, paint, noise, keep_specks=True)
        m.add(g, pivot=E, anim=dict(kind='blink', axis=[0, 0, 1], base=0.0, amp=-62.0, period=110, phase=0.4 if k == 'L' else 0.0),
              kind='lid', note='the heavy upper lid: rotate about +Z by a NEGATIVE angle to close it (about -62 degrees shuts it)')
    for k, E in EYES.items():
        sgn = -1 if k == 'L' else 1
        ty = E[1] - 0.17
        tz = E[2] + sgn * 0.2
        c = np.array([surface_x(raw, ty, tz) + 0.012, ty, tz])
        tfn = lambda px, py, pz, c=c: [('tear', ell(px, py, pz, c, (0.022, 0.04, 0.022)))]

        def tpaint(ctx):
            return np.tile(K.TEAR[1], ctx.px.shape + (1,)), np.full(ctx.px.shape, 0.3)
        g = K.voxelize('tear_' + k, c - 0.1, c + 0.1, S64, tfn, tpaint, noise, keep_specks=True, min_nb=0)
        m.add(g, pivot=c, anim=dict(kind='fall', axis=[0, -1, 0], amp=0.45, period=70, phase=0.5 if k == 'L' else 0.1), kind='tear',
              note='a tear drop (glowing a little): loops down the cheek')
    for i, (M, top, bot, ut, lt) in enumerate(mouths):
        c = M['c']
        zmid = c[2]
        ybot = float(bot(np.array(zmid)))
        hinge = np.array([c[0] - DEPTH + 0.03, ybot, zmid])
        lo = np.array([c[0] - 0.22, ybot - 0.22, zmid - M['hw'] - 0.06])
        hi = np.array([c[0] + 0.14, ybot + 0.22, zmid + M['hw'] + 0.06])
        g = K.voxelize('mouth_%d' % i, lo, hi, S32, mouth_jaw_fn(M, top, bot, lt, raw),
                       lambda c_: K.standard_paint(c_, ramp=COOL, base=0.42, special={'tooth_bone': _bone}), noise, keep_specks=True)
        m.add(g, pivot=hinge, anim=dict(kind='hinge', axis=[0, 0, 1], base=0.0, amp=-26.0, period=int(70 + 15 * i), phase=0.2 * i), kind='mouth',
              note='slack lower lip with its teeth: rotate about +Z by a NEGATIVE angle to open (sobbing)')
    return m
