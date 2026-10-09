"""The carving on the sides of Baron Bomburst's gondola, laid out as the film's: black lacquer under heavy gilded rococo
carving, with a winged cherub at the bow beside the Baron's medallion, its B in black letter, under a crown.

The ornament is drawn as flat shapes in SVG (rasterised with CairoSVG), each parted from the ones behind it by a fine
groove as a carver would cut it. That gives the carving's outline; its relief comes from how far each point lies inside
it (round-topped, so a leaf swells and a stem is a rounded bead), and the gilding is then lit from above as raised
gold: bright where it faces the light, dark in its grooves and on the far side, with the lacquer shadowed beneath it.

    python tools/airship_carving.py OUT_DIR     # writes each side's carving and relief height as PNGs
"""
import io
import math
import os
import sys

import numpy as np

PANEL = (2048, 1024)    # the carved side, from the engine section to the bow: 2.6 blocks along by 1.3 high
GAP = 2.2               # the groove round each carved piece, in pixels
RELIEF = 9.0            # how far in from its edge a piece reaches its full height, in pixels
BOW_RAKE = 276          # how far the raked bow cuts back across the panel's foot, in pixels (0.35 of 2.6 blocks)
MEDALLION = (1806, 372)  # the Baron's medallion, near the bow

LACQUER = np.array((15, 13, 17), np.float32) / 255
GOLD_DARK = np.array((66, 46, 20), np.float32) / 255
GOLD = np.array((184, 144, 72), np.float32) / 255
GOLD_LIGHT = np.array((242, 222, 164), np.float32) / 255


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
        lobe = 0.62 + 0.38 * math.sin(math.pi * ph) ** 0.45    # each lobe swells round, then dips to the next
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


def fillet(x0, y0, x1, y1):
    return ('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" fill="#000"/>' % (x0, y0, x1 - x0, y1 - y0), '')


def bead_swag(a, b, sag, r):
    """A festoon of beads hung from a to b, the beads fullest at the middle."""
    out = []
    line = cubic(a, (a[0], a[1] + sag * 1.3), (b[0], b[1] + sag * 1.3), b, 200)
    lens = [0.0]
    for p, q in zip(line, line[1:]):
        lens.append(lens[-1] + math.hypot(q[0] - p[0], q[1] - p[1]))
    d = 0.0
    while d < lens[-1]:
        i = min(range(len(lens)), key=lambda k: abs(lens[k] - d))
        t = d / lens[-1]
        rr = r * (0.7 + 0.45 * math.sin(math.pi * t))
        out.append(disc(line[i], rr, ring=False))
        d += rr * 1.75
    return out


def crested_arch(p0, c0, c1, p3, w, tooth, inward=1):
    """A rocaille C-scroll: a fluted band along a curve from p0 to p3, its outer edge crested with little flames,
    its far end winding into a volute."""
    arc = cubic(p0, c0, c1, p3, 60)
    out = []
    # The crest: flames along the outside of the bow (the side away from the chord's middle).
    mid = ((p0[0] + p3[0]) / 2, (p0[1] + p3[1]) / 2)
    for i in range(5, len(arc) - 8, 2):
        p, q = arc[i - 1], arc[i + 1]
        dx, dy = q[0] - p[0], q[1] - p[1]
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        if (arc[i][0] - mid[0]) * nx + (arc[i][1] - mid[1]) * ny < 0:
            nx, ny = -nx, -ny
        t = i / len(arc)
        ln2 = tooth * (0.35 + 0.3 * math.sin(math.pi * t))
        base = (arc[i][0] + nx * w * 0.3, arc[i][1] + ny * w * 0.3)
        tip = (base[0] + nx * ln2 + dx / ln * ln2 * 0.5, base[1] + ny * ln2 + dy / ln * ln2 * 0.5)
        out.append(band(cubic(base, (base[0] + nx * ln2 * 0.7, base[1] + ny * ln2 * 0.7), tip, tip, 10),
                        w * 0.75, w * 0.42))
    out.append(band(arc, w, w * 0.8, fluted=True))
    # The volute at the far end, winding in towards the inside of the bow.
    e, f = arc[-1], arc[-4]
    tx, ty = e[0] - f[0], e[1] - f[1]
    tl = math.hypot(tx, ty)
    nx, ny = -ty / tl, tx / tl
    if (mid[0] - e[0]) * nx + (mid[1] - e[1]) * ny < 0:
        nx, ny = -nx, -ny
    r = w * 1.6
    c = (e[0] + nx * r * inward, e[1] + ny * r * inward)
    a0 = math.atan2(e[1] - c[1], e[0] - c[0])
    sign = 1 if (-math.sin(a0) * tx + math.cos(a0) * ty) > 0 else -1
    out.append(band(spiral(c, r, 0.9, a0, sign), w * 0.8, w * 0.4))
    out.append(disc(c, w * 0.45, ring=False))
    return out


def strap(pts, w, curl_start=None, curl_end=None):
    """Rococo strapwork: a fluted band through the points, curling into a volute at either end (curl_* is the
    volute's radius and which way it turns, +1 or -1)."""
    line = catmull_open(pts, 12)
    out = []
    # Leaves curling off its underside, every so often.
    for i in range(18, len(line) - 18, 30):
        p, q = line[i - 2], line[i + 2]
        fwd = 1 if q[0] >= p[0] else -1
        ang = math.degrees(math.atan2(q[1] - p[1], q[0] - p[0])) + 40 * fwd
        out.append(acanthus(line[i], ang, w * 3.2, w * 1.5, lobes=3, curl=0.45, side=-fwd))
    out.append(band(line, w, w, fluted=True))
    for end, prev, curl in ((line[0], line[3], curl_start), (line[-1], line[-4], curl_end)):
        if not curl:
            continue
        r, turn = curl
        tx, ty = end[0] - prev[0], end[1] - prev[1]
        tl = math.hypot(tx, ty)
        tx, ty = tx / tl, ty / tl
        c = (end[0] - ty * r * turn, end[1] + tx * r * turn)
        a0 = math.atan2(end[1] - c[1], end[0] - c[0])
        sign = 1 if (-math.sin(a0) * tx + math.cos(a0) * ty) > 0 else -1
        out.append(band(spiral(c, r, 0.95, a0, sign), w, w * 0.5))
        out.append(disc(c, w * 0.5, ring=False))
    return out


def catmull_open(pts, per=10):
    P = [(p[0], p[1]) for p in pts]
    n = len(P)
    out = []
    for i in range(n - 1):
        p0, p1, p2, p3 = P[max(i - 1, 0)], P[i], P[i + 1], P[min(i + 2, n - 1)]
        for k in range(per):
            t = k / per
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
    out.append(P[-1])
    return out


def limb(pts, w0, w1):
    return band(catmull_open(pts, 10), w0, w1)


def placed(pieces, c, k):
    """Pieces drawn about the origin, moved to c and scaled by k."""
    t = '<g transform="translate(%.2f %.2f) scale(%.4f)">%%s</g>' % (c[0], c[1], k)
    return [(t % svg, t % cuts if cuts else '') for svg, cuts in pieces]


def putto(c, k=1.0):
    """The winged cherub at the bow, as the film carves it: sitting on the moulding beside the Baron's medallion with
    his wings raised behind him, one hand on the medallion, the other holding up a festoon of beads, a cloth over
    his lap. c is where he sits; k his size."""
    return placed(_putto(), c, k)


def _putto():
    x, y = 0.0, 0.0
    out = []
    # His wing, raised behind him: rounded at the shoulder, its long feathers ending in points.
    wing = [(x - 6, y - 150), (x - 40, y - 196), (x - 86, y - 236), (x - 130, y - 254), (x - 150, y - 232),
            (x - 140, y - 206), (x - 168, y - 196), (x - 146, y - 176), (x - 172, y - 160), (x - 140, y - 148),
            (x - 160, y - 126), (x - 112, y - 128), (x - 70, y - 124), (x - 30, y - 128)]
    cuts = ''.join(groove(catmull_open(pts, 6), 1.5) for pts in (
        [(x - 40, y - 176), (x - 90, y - 196), (x - 136, y - 210)],
        [(x - 40, y - 160), (x - 100, y - 172), (x - 142, y - 178)],
        [(x - 44, y - 144), (x - 100, y - 146), (x - 140, y - 150)],
        [(x - 30, y - 186), (x - 60, y - 214), (x - 96, y - 232)]))
    out.append((fill(polygon(catmull_open(wing + [wing[0]], 8))), cuts))
    far = [(x + 20, y - 150), (x + 46, y - 196), (x + 64, y - 226), (x + 76, y - 206), (x + 70, y - 180),
           (x + 84, y - 172), (x + 62, y - 150)]
    out.append((fill(polygon(catmull_open(far + [far[0]], 8))), groove([(x + 34, y - 170), (x + 60, y - 196)], 1.4)))
    # The far leg, drawn up, and the cloth over his lap.
    out.append(limb([(x + 10, y - 40), (x - 50, y - 20), (x - 66, y + 30)], 34, 22))
    out.append(disc((x - 68, y + 34), 12, ring=False))
    out.append(acanthus((x + 20, y - 52), 196, 120, 52, lobes=3, curl=0.35, side=-1))
    # His body and the near leg, crossed over the other.
    body = [(x - 16, y - 150), (x + 24, y - 150), (x + 42, y - 110), (x + 38, y - 64), (x + 16, y - 40), (x - 18, y - 42),
            (x - 36, y - 70), (x - 34, y - 116)]
    out.append((fill(polygon(catmull_open(body + [body[0]], 8))), ''))
    out.append(limb([(x + 26, y - 50), (x - 20, y - 10), (x - 88, y + 6)], 34, 20))
    out.append(disc((x - 92, y + 8), 11, ring=False))
    # His arms: the near one reaching to the medallion, the far one raising the festoon.
    out.append(limb([(x + 30, y - 132), (x + 64, y - 108), (x + 96, y - 102)], 22, 15))
    out.append(disc((x + 98, y - 102), 10, ring=False))
    out.append(limb([(x - 24, y - 132), (x - 62, y - 150), (x - 86, y - 176)], 21, 14))
    out.append(disc((x - 88, y - 180), 10, ring=False))
    # His head, turned towards the festoon, with curls.
    out.append(disc((x + 4, y - 188), 37, ring=False))
    for k in range(8):
        a = math.radians(140 + 30 * k)
        out.append(disc((x + 4 + math.cos(a) * 34, y - 188 + math.sin(a) * 34), 11, ring=False))
    return out


def crown(c, w):
    """A crown: a jewelled circlet with leaves standing up from it, pearls between them."""
    x, y = c
    out = []
    for k, (dx, h) in enumerate(((-0.5, 0.55), (-0.25, 0.4), (0.0, 0.7), (0.25, 0.4), (0.5, 0.55))):
        if k % 2 == 0:
            out.append(acanthus((x + dx * w, y - 4), -90, h * w, w * 0.3, lobes=2, curl=0.0))
        else:
            out.append(band([(x + dx * w, y - 4), (x + dx * w, y - h * w)], 6, 4))
            out.append(disc((x + dx * w, y - h * w - 6), 7, ring=False))
    out.append(band([(x - w * 0.62, y + 2), (x, y - 5), (x + w * 0.62, y + 2)], 20, 20))
    for k in range(5):
        out.append(('<ellipse cx="%.2f" cy="%.2f" rx="5.5" ry="7" fill="#000"/>' % (x - w * 0.4 + w * 0.2 * k, y - 1), ''))
    return out


def bow_edge(y):
    """Where the raked bow cuts the panel, at height y."""
    W, H = PANEL
    return W - BOW_RAKE * y / H


def carving():
    """Every carved piece of one side (the bow on the right), from the back to the front, placed where the film's
    gondola has them (measured off a still of her right side): a fine gilt border; along the top, rococo strapwork
    with festoons of beads hung under it and a shell over the post in the middle; a moulding along the middle and a
    pendant hung on the post; along the bottom a great acanthus at the stern end, then a shell between two great
    crested C-scrolls with acanthus curling out under them; and at the bow a winged cherub beside the Baron's
    medallion under a crown, a crested rocaille rolling over beneath him."""
    W, H = PANEL
    out = []
    edge_top, edge_bot = bow_edge(18), bow_edge(H - 26)
    # The border, and the moulding along the middle.
    out.append(fillet(10, 14, edge_top - 12, 23))
    out.append(fillet(10, H - 32, edge_bot - 12, H - 23))
    out.append(fillet(10, 14, 19, H - 23))
    out.append(band([(edge_top - 17, 18), (edge_bot - 17, H - 28)], 9, 9))
    mid_y = 488
    out.append(fillet(404, mid_y - 8, bow_edge(mid_y) - 17, mid_y - 3))
    out.append(fillet(404, mid_y + 4, bow_edge(mid_y) - 17, mid_y + 9))
    # The post in the middle of the side.
    px = 927
    out.append(fillet(px - 13, 18, px - 6, H - 26))
    out.append(fillet(px + 6, 18, px + 13, H - 26))
    # Along the top: strapwork either side of the post, a shell over it, festoons of beads hung under.
    out += strap([(44, 196), (60, 80), (150, 52), (269, 57), (344, 112), (419, 152), (508, 138), (568, 84), (640, 62)],
                 34, (46, 1), (30, 1))
    out += strap([(660, 60), (730, 66), (790, 110), (840, 168)], 32, (34, -1), (24, -1))
    out += strap([(1194, 60), (1124, 66), (1064, 110), (1014, 168)], 32, (34, 1), (24, 1))
    out += strap([(1240, 60), (1310, 104), (1360, 150), (1420, 150), (1500, 128), (1560, 96)], 32, (30, -1), (28, -1))
    out += shell((px, 168), 116, up=-1)
    for a, b, sag in (((136, 280), (450, 282), 66), ((540, 264), (806, 250), 76), ((1046, 250), (1306, 262), 70),
                      ((1340, 270), (1530, 330), 46)):
        out += bead_swag(a, b, sag, 11)
    # The pendant on the post: a rose among berries, leaves and a tassel hanging below.
    for k in range(3):
        out.append(acanthus((px, mid_y - 30), 90 + (k - 1) * 22, 130 - abs(k - 1) * 26, 42, lobes=2, curl=0.15))
    for k in range(9):
        a = 2 * math.pi * k / 9
        out.append(disc((px + math.cos(a) * 42, 392 + math.sin(a) * 36), 14, ring=False))
    out += rosette((px, 392), 36, 8)
    # Along the bottom: a great acanthus scroll at the stern end ...
    out.append(band(spiral((210, 760), 150, 0.8, math.pi * 0.95, 1, 60), 26, 14))
    for ang, ln, wd in ((-66, 360, 150), (-38, 330, 134), (-12, 290, 116)):
        out.append(acanthus((92, H - 80), ang, ln, wd, lobes=4, curl=0.45, side=-1))
    # ... the shell over the foot of the post between two great crested C-scrolls, with acanthus curling out under
    # them ...
    out += crested_arch((760, H - 120), (750, 640), (470, 610), (430, H - 210), 40, 44)
    out += crested_arch((1060, H - 120), (1090, 620), (1430, 600), (1480, H - 190), 40, 44)
    for x0, ang, side in ((880, 182, 1), (980, -2, -1), (560, 160, 1), (1340, 20, -1)):
        out.append(acanthus((x0, H - 72), ang, 240, 82, lobes=4, curl=0.4, side=side))
    out += shell((px, 690), 130, up=-1)
    # ... and under the cherub at the bow a great crested rocaille, rolling over like a wave.
    out += crested_arch((1520, H - 60), (1540, 640), (1700, 520), (1760, 640), 44, 48)
    for ang, ln in ((-24, 190), (6, 180), (36, 150)):
        out.append(acanthus((1640, H - 170), ang, ln, 70, lobes=3, curl=0.4, side=1))
    # The cherub, the medallion and the crown.
    out += [(fill(polygon(ribbon(catmull_open([(1736, 470), (1708, 330), (1690, 190), (1702, 110)], 8),
                                 lambda t: 40 * (1 - t) ** 0.8 + 4))), groove([(1730, 456), (1696, 200)], 1.6))]
    out += putto((1640, 476), 1.32)
    mc = MEDALLION
    out.append(disc(mc, 88, ring=False))
    ring = [(mc[0] + math.cos(a) * 97, mc[1] + math.sin(a) * 97) for a in np.linspace(0, 2 * math.pi, 100)]
    out.append(band(ring, 16, 16, fluted=True))
    out += crown((mc[0] + 4, mc[1] - 128), 150)
    return out


def letter_b(c, size=200):
    """The Baron's B, in black letter, cut into his medallion."""
    return ('<text x="%.2f" y="%.2f" font-family="FreeSerif" font-size="%d" text-anchor="middle" fill="#000">'
            '&#x1D505;</text>' % (c[0], c[1] + size * 0.3, size), '')


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
    mx = MEDALLION[0] if bow_right else W - MEDALLION[0]
    b = raster(letter_b((mx, MEDALLION[1]))[0])
    m = m * (1 - b)
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
