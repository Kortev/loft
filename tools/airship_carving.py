"""The carving on the sides of Baron Bomburst's gondola: black lacquer under heavy gilded rococo carving, as the film has
it, with the Baron's B at the bow in an oval wreath under a coronet.

The ornament is drawn as flat shapes in SVG (rasterised with CairoSVG), each parted from the ones behind it by a fine
groove as a carver would cut it. That gives the carving's outline; its relief comes from how far each point lies inside
it (round-topped, so a leaf swells and a stem is a rounded bead), and the gilding is then lit from above as raised
gold: bright where it faces the light, dark in its grooves and on the far side, with the lacquer shadowed beneath it.

    python tools/airship_carving.py OUT_DIR     # writes carving.png, carving_height.png and carving_gilt.png
"""
import io
import math
import os
import sys

import numpy as np

PANEL = (2048, 660)     # the carved side, from the engine section to the bow: 3.1 blocks along by 1.0 high
GAP = 2.2               # the groove round each carved piece, in pixels
RELIEF = 6.5            # how far in from its edge a piece reaches its full height, in pixels

LACQUER = np.array((15, 13, 17), np.float32) / 255
GOLD_DARK = np.array((74, 46, 10), np.float32) / 255
GOLD = np.array((190, 138, 44), np.float32) / 255
GOLD_LIGHT = np.array((246, 214, 128), np.float32) / 255


# --- geometry ----------------------------------------------------------------------------------------------------

def polygon(pts):
    return 'M ' + ' L '.join('%.2f %.2f' % (p[0], p[1]) for p in pts) + ' Z'


def polyline(pts):
    return 'M ' + ' L '.join('%.2f %.2f' % (p[0], p[1]) for p in pts)


def ribbon(dense, profile):
    """The outline of a band along a line of points, profile(t) its full width at t (by length along it)."""
    n = len(dense)
    lens = [0.0]
    for a, b in zip(dense, dense[1:]):
        lens.append(lens[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    left, right = [], []
    for i, p in enumerate(dense):
        q, o = dense[min(i + 1, n - 1)], dense[max(i - 1, 0)]
        dx, dy = q[0] - o[0], q[1] - o[1]
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        hw = profile(lens[i] / (lens[-1] or 1)) / 2
        left.append((p[0] + nx * hw, p[1] + ny * hw))
        right.append((p[0] - nx * hw, p[1] - ny * hw))
    return left + list(reversed(right))


def spiral(c, r0, turns, a0, sign, n=60):
    """Points winding in from radius r0 about c, starting at angle a0, `sign` +1 clockwise on the page."""
    out = []
    for i in range(n + 1):
        t = i / n
        a = a0 + sign * t * turns * 2 * math.pi
        r = r0 * (1 - 0.82 * t)
        out.append((c[0] + math.cos(a) * r, c[1] + math.sin(a) * r))
    return out


def cubic(p0, p1, p2, p3, n=40):
    out = []
    for i in range(n + 1):
        t = i / n
        a, b, c, e = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + e * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + e * p3[1]))
    return out


# --- carved pieces ---------------------------------------------------------------------------------------------
# A piece is (svg, cuts): its shape, and the grooves cut into it.

def fill(d):
    return '<path d="%s" fill="#000"/>' % d


def groove(pts, width=1.8):
    return '<path d="%s" fill="none" stroke="#000" stroke-width="%.2f" stroke-linecap="round" ' \
           'stroke-linejoin="round"/>' % (polyline(pts), width)


def band(dense, w0, w1=None, fluted=False):
    """A carved stem or scroll along a line, w0 wide tapering to w1, a groove down it if fluted."""
    w1 = w0 if w1 is None else w1
    cuts = groove(dense[2:-6], 1.6) if fluted and len(dense) > 10 else ''
    return (fill(polygon(ribbon(dense, lambda t: w0 + (w1 - w0) * t))), cuts)


def acanthus(base, angle, length, width, lobes=3, curl=0.35, side=1):
    """An acanthus leaf from base along angle (degrees): deeply lobed along both edges, its tip curling over to
    `side`, with a rib down it and a vein into each lobe."""
    a = math.radians(angle)
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy * side, ux * side
    centre = []
    for i in range(41):
        t = i / 40
        bend = curl * length * t ** 2.2
        centre.append((base[0] + ux * length * t + nx * bend, base[1] + uy * length * t + ny * bend))

    def profile(t):
        env = math.sin(math.pi * min(t * 1.04, 1)) ** 0.6 * (1 - 0.3 * t)
        ph = (lobes * t) % 1.0
        lobe = 0.58 + 0.42 * (1 - ph) ** 0.6     # each lobe swells, then is cut back sharply to the next
        return width * env * lobe
    outline = ribbon(centre, profile)
    cuts = groove(centre[2:32], 1.5)
    for k in range(1, lobes + 1):
        t = (k - 0.75) / lobes
        i = int(t * 40)
        p, q = centre[i], centre[min(i + 4, 40)]
        dx, dy = q[0] - p[0], q[1] - p[1]
        ln = math.hypot(dx, dy) or 1
        w = profile(t + 0.1) * 0.4
        for s in (1, -1):
            cuts += groove([p, (p[0] + dx / ln * w * 0.9 - dy / ln * w * s, p[1] + dy / ln * w * 0.9 + dx / ln * w * s)],
                           1.3)
    return (fill(polygon(outline)), cuts)


def laurel(base, angle, length, width):
    """A laurel leaf: a slim pointed oval with a rib."""
    a = math.radians(angle)
    ux, uy = math.cos(a), math.sin(a)
    centre = [(base[0] + ux * length * t, base[1] + uy * length * t) for t in np.linspace(0, 1, 16)]
    outline = ribbon(centre, lambda t: width * math.sin(math.pi * t) ** 0.8 * (1 - 0.2 * t))
    return (fill(polygon(outline)), groove(centre[2:12], 1.2))


def disc(c, r, ring=True):
    cuts = '<circle cx="%.2f" cy="%.2f" r="%.2f" fill="none" stroke="#000" stroke-width="1.6"/>' % (c[0], c[1], r * 0.55) \
        if ring else ''
    return ('<circle cx="%.2f" cy="%.2f" r="%.2f" fill="#000"/>' % (c[0], c[1], r), cuts)


def rosette(c, r, petals=8, a0=0.0):
    """A carved rose: a ring of petals round a boss."""
    out = []
    for k in range(petals):
        a = a0 + 360 * k / petals
        out.append(acanthus((c[0], c[1]), a, r, r * 0.62, lobes=1, curl=0.0))
    out.append(disc(c, r * 0.36))
    return out


def shell(c, r, up=-1):
    """A scallop shell: ribs fanning from its hinge, opening upward (up=-1) or downward."""
    pts = [c]
    for k in range(25):
        a = math.radians(-170 + 160 * k / 24) * (1 if up < 0 else -1)
        rr = r * (0.93 + 0.07 * math.cos(k * math.pi))
        pts.append((c[0] + math.cos(a) * rr, c[1] + math.sin(a) * rr))
    cuts = ''
    for k in range(1, 12):
        a = math.radians(-170 + 160 * k / 12) * (1 if up < 0 else -1)
        cuts += groove([(c[0] + math.cos(a) * r * 0.2, c[1] + math.sin(a) * r * 0.2),
                        (c[0] + math.cos(a) * r * 0.86, c[1] + math.sin(a) * r * 0.86)], 1.6)
    return [(fill(polygon(pts)), cuts), disc((c[0], c[1] - up * r * 0.04), r * 0.18, ring=False)]


def c_scroll(a, b, bulge, w, r, outward=False):
    """A C-scroll from a to b, bowed `bulge` across (to the left of a-to-b if positive), each end curling on round
    into a volute of radius r, inside the bow or (outward) outside it."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(dx, dy)
    ox, oy = -dy / ln, dx / ln          # towards the bow's outside, for a positive bulge
    if bulge < 0:
        ox, oy = -ox, -oy
    k = abs(bulge)
    arc = cubic(a, (a[0] + dx * 0.12 + ox * k, a[1] + dy * 0.12 + oy * k),
                (a[0] + dx * 0.88 + ox * k, a[1] + dy * 0.88 + oy * k), b)
    out = [band(arc, w, w, fluted=True)]
    for end, prev in ((a, arc[1]), (b, arc[-2])):
        tx, ty = end[0] - prev[0], end[1] - prev[1]
        side = 1 if outward else -1
        c = (end[0] + side * ox * r, end[1] + side * oy * r)
        a0 = math.atan2(end[1] - c[1], end[0] - c[0])
        sign = 1 if (-math.sin(a0) * tx + math.cos(a0) * ty) > 0 else -1
        out.append(band(spiral(c, math.hypot(end[0] - c[0], end[1] - c[1]), 0.9, a0, sign), w, w * 0.5))
        out.append(disc(c, w * 0.5, ring=False))
    return out


def swag(a, b, sag, size):
    """A festoon of laurel hung from a to b: leaves laid from each end in towards the middle, fullest there, and a
    rose where they meet."""
    out = []
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + sag)
    line = cubic(a, (a[0], a[1] + sag * 1.25), (b[0], b[1] + sag * 1.25), b, 60)
    n = len(line)
    for half in (0, 1):
        idx = range(2, n // 2 - 2, 3) if half == 0 else range(n - 3, n // 2 + 2, -3)
        for k, i in enumerate(idx):
            p, q = line[i], line[i + (1 if half == 0 else -1)]
            ang = math.degrees(math.atan2(q[1] - p[1], q[0] - p[0]))
            t = 1 - abs(i - n / 2) / (n / 2)
            ln = size * (0.55 + 0.6 * t)
            wd = size * (0.26 + 0.2 * t)
            for s in (1, -1):
                out.append(laurel(p, ang + s * 32, ln, wd))
    out += rosette(mid, size * 0.62, 7)
    return out


def bead_row(y, x0, x1, step=44):
    """A bead-and-reel moulding."""
    out = []
    x = x0
    while x < x1:
        out.append(('<ellipse cx="%.2f" cy="%.2f" rx="%.2f" ry="%.2f" fill="#000"/>' % (x, y, step * 0.36, step * 0.21), ''))
        out.append(disc((x + step * 0.5, y), step * 0.1, ring=False))
        x += step
    return out


def fillet(x0, y0, x1, y1):
    return ('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" fill="#000"/>' % (x0, y0, x1 - x0, y1 - y0), '')


def rinceau(x0, x1, y, amp, period, size, phase=0.0):
    """A running scroll of acanthus: a stout stem waving along from x0 to x1; inside each hump of the wave a branch
    leaves the stem at the crest and curls round into a rose; leaves sheathe the stem, pointing the way it grows."""
    k = 2 * math.pi / period
    stem = [(x, y + amp * math.sin(k * (x - x0) + phase)) for x in np.arange(x0, x1 + 0.1, 3.0)]
    branches, leaves = [], []
    # The crests: where the wave turns, k(x - x0) + phase = pi/2 + m pi.
    m = -5
    while True:
        x = x0 + (math.pi / 2 + m * math.pi - phase) / k
        m += 1
        if x < x0 + period * 0.2:
            continue
        if x > x1 - period * 0.2:
            break
        top = math.sin(k * (x - x0) + phase) < 0        # a crest above the middle line (y grows downwards)
        up = -1 if top else 1
        cy = y + amp * (-1 if top else 1)
        r = min(amp * 0.6, period * 0.15)
        c = (x + period * 0.06, cy - up * r)
        a0 = math.atan2(cy - c[1], x - c[0])
        branches.append(band(spiral(c, r, 1.05, a0, -up, 50), size * 0.2, size * 0.1))
        leaves += rosette(c, size * 0.3, 7)
        # A leaf sheathing the branch where it springs, and one on the stem beyond the crest pointing on.
        leaves.append(acanthus((x - period * 0.1, cy + up * 3), -up * 14 if top else 14, size * 0.9, size * 0.5,
                               lobes=3, curl=0.35, side=up))
        xm = x + period * 0.22
        ang = math.degrees(math.atan(amp * k * math.cos(k * (xm - x0) + phase)))
        leaves.append(acanthus((xm, y + amp * math.sin(k * (xm - x0) + phase)), ang - up * 12, size * 0.9,
                               size * 0.46, lobes=3, curl=0.4, side=-up))
    return [band(stem, size * 0.28, size * 0.24, fluted=True)] + branches + leaves


def wave_scroll(x0, x1, y, h, period, w):
    """A running wave (Vitruvian) scroll along the bottom: each wave rising out of the last and breaking over into a
    volute."""
    out = []
    x = x0
    while x + period <= x1 + 1:
        r = h * 0.36
        c = (x + period * 0.62, y - h + r)
        rise = cubic((x, y), (x + period * 0.25, y), (x + period * 0.3, y - h), (c[0], y - h), 20)
        curl_pts = spiral(c, r, 0.9, -math.pi / 2, 1, 40)
        out.append(band(rise + curl_pts[1:], w, w * 0.5))
        out.append(disc(c, w * 0.55, ring=False))
        x += period
    return out


def medallion(c, rx, ry):
    """The Baron's medallion: a moulded oval ring in a laurel wreath tied with a rose at the foot, a coronet on top.
    (The B is cut separately, so it still reads forwards on her left side.)"""
    out = []
    # The wreath: laurel up each side from the foot, behind the ring.
    for side in (-1, 1):
        for k in range(14):
            t = k / 14
            a = math.pi / 2 - side * (0.25 + t * 2.45)
            p = (c[0] + math.cos(a) * (rx + 20), c[1] + math.sin(a) * (ry + 20))
            tang = math.degrees(a - side * math.pi / 2)
            for s in (1, -1):
                out.append(laurel(p, tang + s * 34, 40 - 10 * t, 15 - 3 * t))
    ring = [(c[0] + math.cos(a) * rx, c[1] + math.sin(a) * ry) for a in np.linspace(0, 2 * math.pi, 120)]
    out.append(band(ring, 18, 18, fluted=True))
    for s in (-1, 1):
        out.append(band(cubic((c[0], c[1] + ry + 26), (c[0] + s * 22, c[1] + ry + 58), (c[0] + s * 40, c[1] + ry + 40),
                              (c[0] + s * 56, c[1] + ry + 72)), 11, 4))
    out += rosette((c[0], c[1] + ry + 22), 20, 7)
    # The coronet: a jewelled circlet with leaves standing up from it, the middle one tallest, pearls between.
    top = c[1] - ry - 14
    for x, h in ((c[0] - 50, 40), (c[0] + 50, 40), (c[0], 54)):
        out.append(acanthus((x, top - 6), -90, h, 30, lobes=2, curl=0.0))
    for x in (c[0] - 26, c[0] + 26):
        out.append(band([(x, top - 6), (x, top - 24)], 6, 4))
        out.append(disc((x, top - 30), 7.5, ring=False))
    out.append(band([(c[0] - 66, top + 2), (c[0], top - 4), (c[0] + 66, top + 2)], 20, 20))
    for k in range(5):
        x = c[0] - 44 + 22 * k
        out.append(('<ellipse cx="%.2f" cy="%.2f" rx="5.5" ry="7" fill="#000"/>' % (x, top - 1 + abs(k - 2) * 0.8), ''))
    return out


def carving():
    """Every carved piece of one side (the bow on the right), from the back to the front."""
    W, H = PANEL
    out = []
    # The mouldings: a bead-and-reel along the top under a fillet, a fillet along the bottom, and up each end.
    out.append(fillet(0, 6, W, 13))
    out += bead_row(29, 22, W)
    out.append(fillet(0, 44, W, 50))
    out.append(fillet(0, H - 22, W, H - 12))
    out.append(fillet(8, 6, 15, H - 12))
    out.append(fillet(W - 15, 6, W - 8, H - 12))
    # Festoons of laurel along the top, hung from roses.
    hangs = [W * k / 6 for k in range(7)]
    for xa, xb in zip(hangs, hangs[1:]):
        out += swag((xa + 30, 80), (xb - 30, 80), 70, 44)
    for x in hangs:
        out += rosette((min(max(x, 40), W - 40), 78), 26, 8)
    # The wave scroll along the bottom.
    out.append(fillet(20, H - 40, W - 20, H - 33))
    out += wave_scroll(30, W - 30, H - 42, 66, 116, 15)
    # The running acanthus scroll through the middle, broken by the cartouche and the medallion.
    cx, cy = W * 0.43, H * 0.54
    out += rinceau(36, cx - 196, cy + 6, 62, 236, 70)
    out += rinceau(cx + 196, W * 0.8 - 236, cy + 6, 62, 236, 70, phase=math.pi)
    # The cartouche: C-scrolls either side, a shell over it, acanthus under it, a rose in the middle.
    for s in (-1, 1):
        out += c_scroll((cx + s * 112, cy - 124), (cx + s * 112, cy + 114), -s * 46, 18, 22, outward=True)
        out.append(acanthus((cx + s * 150, cy), 90 - s * 90, 90, 40, lobes=3, curl=0.4, side=s))
        out.append(acanthus((cx + s * 30, cy + 150), 90 + s * 70, 84, 34, lobes=3, curl=0.4, side=-s))
    out.append(band(cubic((cx - 112, cy - 128), (cx - 60, cy - 150), (cx + 60, cy - 150), (cx + 112, cy - 128)), 14, 14,
                    fluted=True))
    out.append(band(cubic((cx - 112, cy + 120), (cx - 60, cy + 140), (cx + 60, cy + 140), (cx + 112, cy + 120)), 14, 14,
                    fluted=True))
    out += shell((cx, cy - 150), 66, up=-1)
    out += shell((cx, cy + 140), 46, up=1)
    out += rosette((cx, cy - 6), 46, 8, 22.5)
    for k in range(4):
        out.append(acanthus((cx, cy - 6), 45 + 90 * k, 92, 30, lobes=2, curl=0.25))
    out += rosette((cx, cy - 6), 30, 8)
    # The Baron's medallion near the bow, a great rose and a spray of acanthus before it.
    mx, my = W * 0.8, H * 0.46
    out += medallion((mx, my), 96, 124)
    for k in range(5):
        out.append(acanthus((mx - 186, my + 40), 200 + 30 * k, 100 - 8 * k, 40, lobes=3, curl=0.35, side=1))
    out += rosette((mx - 186, my + 40), 44, 9)
    return out


def letter_b(c, size=200):
    return ('<text x="%.2f" y="%.2f" font-family="FreeSerif" font-weight="bold" font-style="italic" '
            'font-size="%d" text-anchor="middle" fill="#000">B</text>' % (c[0], c[1] + size * 0.33, size), '')


# --- drawing ----------------------------------------------------------------------------------------------------

def raster(svg):
    import cairosvg
    from PIL import Image
    W, H = PANEL
    doc = '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">%s</svg>' % (W, H, W, H, svg)
    png = cairosvg.svg2png(bytestring=doc.encode())
    return np.asarray(Image.open(io.BytesIO(png)).convert('RGBA'), np.float32)[..., 3] / 255


def mask(pieces):
    """The carving's coverage, each piece cut round with its groove where it lies over the ones before it."""
    from scipy.ndimage import distance_transform_edt
    W, H = PANEL
    m = np.zeros((H, W), np.float32)
    for svg, cuts in pieces:
        a = raster(svg)
        ys, xs = np.nonzero(a > 0.004)
        if not len(ys):
            continue
        k = int(GAP) + 3
        y0, y1 = max(ys.min() - k, 0), min(ys.max() + k + 1, H)
        x0, x1 = max(xs.min() - k, 0), min(xs.max() + k + 1, W)
        sub = a[y0:y1, x0:x1]
        ring = np.clip(GAP + 0.5 - distance_transform_edt(sub < 0.5), 0, 1)
        m[y0:y1, x0:x1] *= 1 - np.maximum(sub, ring)
        if cuts:
            a = a * (1 - raster(cuts))
        m = a + m * (1 - a)
    return m


def gild(m):
    """The carving lit as raised gilding on black lacquer: (rgb, height), height 0 to 1."""
    from scipy.ndimage import distance_transform_edt, gaussian_filter
    d = distance_transform_edt(m > 0.5) + (m - 0.5).clip(-0.5, 0.5)
    t = np.clip(d / RELIEF, 0, 1)
    h = np.sqrt(1 - (1 - t) ** 2) * m
    hs = gaussian_filter(h, 0.8)
    gy, gx = np.gradient(hs * RELIEF * 1.4)
    n = np.stack([-gx, -gy, np.ones_like(gx)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    light = np.array((-0.45, -0.65, 0.62))
    light /= np.linalg.norm(light)
    half = light + np.array((0, 0, 1.0))
    half /= np.linalg.norm(half)
    diff = np.clip(n @ light, 0, 1)
    spec = np.clip(n @ half, 0, 1) ** 36
    # Gold: dark in the hollows, through gold to pale where it faces the light, a gleam on the crests.
    occl = 1 - 0.55 * np.clip(gaussian_filter(m, 4) - m * 0.3, 0, 1) * (1 - t)
    k = (diff * occl)[..., None]
    gold = np.where(k < 0.55, GOLD_DARK + (GOLD - GOLD_DARK) * (k / 0.55),
                    GOLD + (GOLD_LIGHT - GOLD) * ((k - 0.55) / 0.45))
    gold = gold + spec[..., None] * np.array((1.0, 0.95, 0.8)) * 0.55
    # The lacquer: black with a soft sheen along the upper part, darkened in the carving's shadow.
    H, W = m.shape
    sheen = 0.035 * np.exp(-((np.arange(H) - H * 0.3) / (H * 0.22)) ** 2)[:, None, None]
    shadow = np.roll(np.roll(gaussian_filter(m, 3.5), 4, axis=0), 3, axis=1)
    lac = (LACQUER + sheen) * (1 - 0.6 * shadow[..., None])
    rgb = lac * (1 - m[..., None]) + gold * m[..., None]
    return np.clip(rgb, 0, 1), h


_CACHE = {}


def side(bow_right):
    """One side's carving: (rgb, height, gilt), the bow on the right if bow_right (her right side). Her left side is
    the same carving the other way round, with its B still reading forwards."""
    if bow_right in _CACHE:
        return _CACHE[bow_right]
    if 'base' not in _CACHE:
        _CACHE['base'] = mask(carving())
    m = _CACHE['base'] if bow_right else _CACHE['base'][:, ::-1]
    W, H = PANEL
    mx = W * 0.8 if bow_right else W * 0.2
    b = raster(letter_b((mx, H * 0.46))[0])
    m = np.maximum(m, b)
    rgb, h = gild(m)
    _CACHE[bow_right] = rgb, h, m
    return _CACHE[bow_right]


if __name__ == '__main__':
    from PIL import Image
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name, right in (('right', True), ('left', False)):
        rgb, h, m = side(right)
        Image.fromarray((rgb * 255 + 0.5).astype(np.uint8)).save(os.path.join(out, 'carving_%s.png' % name))
        Image.fromarray((h * 255 + 0.5).astype(np.uint8)).save(os.path.join(out, 'carving_%s_height.png' % name))
