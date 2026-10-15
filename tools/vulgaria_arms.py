"""Vulgaria's arms, as Baron Bomburst's airship wears them on each flank of her envelope: a black griffin rearing behind
a shield quartered gold and black.

The layout is the film's (traced from a frame, kortev's reference): the griffin faces left, its eagle's head turned to
the left with its beak open and an ear pricked behind; one foreleg is raised high with its talons spread, the other
reaches over the shield's top and hooks its talons over the corner; its great grey wing spreads up to the right; one
lion's hind leg stands below and the tail curls up to a tuft on the right.

The drawing is in the manner of official heraldry: flat tinctures with no outlines, each feather, lock and limb a crisp
shape parted from the ones behind it by a fine gap, so the arms read cleanly from a distance and in detail alike.
The griffin is sable, its wing grey (the film's), armed (beak and claws) gold and langued red.

Each shape is drawn as SVG (on the traced frame's grid: the crop enlarged twelve times), rasterised with CairoSVG
(pip install cairosvg; it needs the Cairo library), and laid over the ones before it after cutting the gap round it.

    python tools/vulgaria_arms.py OUT.png [HEIGHT]     # writes the arms as a PNG with a clear background
"""
import io
import math
import sys

import numpy as np

VIEW = (90, 180, 600, 840)      # x, y, width, height of the drawing on the traced grid
GAP = 2.4                       # the gap round each shape, in grid units

SABLE = '#17151a'
WING_DARK = '#38363e'
WING = '#4a4851'
WING_LIGHT = '#5b5963'
OR = '#d29514'
GULES = '#b1232b'
FIELD_SABLE = '#1b191e'


# --- geometry ----------------------------------------------------------------------------------------------------

def catmull(pts, per=10, closed=False):
    """Points along a Catmull-Rom spline through pts."""
    P = [(p[0], p[1]) for p in pts]
    n = len(P)
    out = []
    for i in (range(n) if closed else range(n - 1)):
        p0 = P[(i - 1) % n] if closed else P[max(i - 1, 0)]
        p1, p2 = P[i], P[(i + 1) % n]
        p3 = P[(i + 2) % n] if closed else P[min(i + 2, n - 1)]
        for k in range(per):
            t = k / per
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
    if not closed:
        out.append(P[-1])
    return out


def smooth(pts, closed=True):
    """An SVG path through the points as a smooth curve (Catmull-Rom as cubic Beziers). A point given as (x, y, 'c')
    is a corner: the curve comes into it and leaves it straight."""
    P = [(p[0], p[1]) for p in pts]
    corner = [len(p) > 2 for p in pts]
    n = len(P)
    out = ['M %.2f %.2f' % P[0]]
    for i in (range(n) if closed else range(n - 1)):
        p0 = P[(i - 1) % n] if closed else P[max(i - 1, 0)]
        p1, p2 = P[i], P[(i + 1) % n]
        p3 = P[(i + 2) % n] if closed else P[min(i + 2, n - 1)]
        if corner[i]:
            c1 = (p1[0] + (p2[0] - p1[0]) / 3, p1[1] + (p2[1] - p1[1]) / 3)
        else:
            c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        if corner[(i + 1) % n]:
            c2 = (p2[0] - (p2[0] - p1[0]) / 3, p2[1] - (p2[1] - p1[1]) / 3)
        else:
            c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        out.append('C %.2f %.2f %.2f %.2f %.2f %.2f' % (c1 + c2 + p2))
    if closed:
        out.append('Z')
    return ' '.join(out)


def polygon(pts):
    return 'M ' + ' L '.join('%.2f %.2f' % (p[0], p[1]) for p in pts) + ' Z'


def ribbon(centre, profile, per=10):
    """The outline of a band along a smooth centre line, profile(t) its full width at t (0 at the start, 1 at the
    end, by length)."""
    dense = catmull(centre, per)
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
        hw = profile(lens[i] / lens[-1]) / 2
        left.append((p[0] + nx * hw, p[1] + ny * hw))
        right.append((p[0] - nx * hw, p[1] - ny * hw))
    return left + list(reversed(right))


def feather_profile(w, base=0.6, widest=0.42, point=0.75):
    """A feather's width: from its root swelling to its fullest, then drawn in to a rounded point."""
    def f(t):
        if t < widest:
            return w * (base + (1 - base) * math.sin(t / widest * math.pi / 2))
        return w * max(math.cos((t - widest) / (1 - widest) * math.pi / 2), 0) ** point
    return f


def flame_profile(w):
    """A lock of hair or a flame: full near its root, then drawn out to a fine point."""
    def f(t):
        if t < 0.28:
            return w * (0.62 + 0.38 * math.sin(t / 0.28 * math.pi / 2))
        return w * (1 - (t - 0.28) / 0.72) ** 1.15
    return f


def taper_profile(w0, w1):
    return lambda t: w0 + (w1 - w0) * t


def bent(root, tip, bend, n=4):
    """Points along a gentle curve from root to tip, its middle pushed `bend` across."""
    dx, dy = tip[0] - root[0], tip[1] - root[1]
    ln = math.hypot(dx, dy)
    nx, ny = -dy / ln, dx / ln
    mid = ((root[0] + tip[0]) / 2 + nx * bend, (root[1] + tip[1]) / 2 + ny * bend)
    out = []
    for i in range(n + 1):
        t = i / n
        out.append(((1 - t) ** 2 * root[0] + 2 * (1 - t) * t * mid[0] + t * t * tip[0],
                    (1 - t) ** 2 * root[1] + 2 * (1 - t) * t * mid[1] + t * t * tip[1]))
    return out


def rotate(v, deg):
    a = math.radians(deg)
    return (v[0] * math.cos(a) - v[1] * math.sin(a), v[0] * math.sin(a) + v[1] * math.cos(a))


def claw_outline(base, direction, length, width, curl=0.6):
    """A hooked claw from its base towards direction (degrees), curling round to the side `curl` (+ clockwise)."""
    a = math.radians(direction)
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy, ux
    tip = (base[0] + ux * length + nx * length * curl * 0.5, base[1] + uy * length + ny * length * curl * 0.5)
    back = (base[0] + nx * width / 2, base[1] + ny * width / 2)
    front = (base[0] - nx * width / 2, base[1] - ny * width / 2)
    mid_out = (base[0] + ux * length * 0.55 + nx * width * (0.45 + curl * 0.55),
               base[1] + uy * length * 0.55 + ny * width * (0.45 + curl * 0.55))
    mid_in = (base[0] + ux * length * 0.62 - nx * width * (0.2 - curl * 0.35),
              base[1] + uy * length * 0.62 - ny * width * (0.2 - curl * 0.35))
    return [back + ('c',), mid_out, tip + ('c',), mid_in, front + ('c',)]


# --- parts -------------------------------------------------------------------------------------------------------
# A part is (svg, cuts, gap): its shapes, fine lines cut out of it, and the gap cut round it from what lies behind.

def fill(d, colour=SABLE):
    return '<path d="%s" fill="%s"/>' % (d, colour)


def cut(pts, width=1.6, closed=False):
    return '<path d="%s" fill="none" stroke="#000" stroke-width="%.2f" stroke-linecap="round" stroke-linejoin="round"/>' \
        % (smooth(pts, closed), width)


def part(svg, cuts='', gap=GAP):
    return (svg, cuts, gap)


def blade(root, tip, w, bend=0.0, colour=SABLE, quill=False, **profile):
    pts = ribbon(bent(root, tip, bend), feather_profile(w, **profile))
    cuts = cut(bent(root, tip, bend)[1:4], 1.3) if quill else ''
    return part(fill(polygon(pts), colour), cuts)


def flame(base, centre, w, colour=SABLE, vein=True):
    pts = [base] + list(centre)
    dense = catmull(pts, 4)
    cuts = cut(dense[len(dense) // 6: len(dense) * 3 // 5], 1.3) if vein and len(pts) > 3 else ''
    return part(fill(polygon(ribbon(pts, flame_profile(w))), colour), cuts)


def limb(centre, w0, w1, bands=(), colour=SABLE):
    """A tapering limb, scaled: `bands` are the fractions along it where a scale's edge is cut across."""
    pts = ribbon(centre, taper_profile(w0, w1))
    dense = catmull(centre, 10)
    cuts = []
    for t in bands:
        i = int(t * (len(dense) - 1))
        p, q = dense[i], dense[min(i + 1, len(dense) - 1)]
        dx, dy = q[0] - p[0], q[1] - p[1]
        ln = math.hypot(dx, dy) or 1
        ux, uy = dx / ln, dy / ln
        hw = (w0 + (w1 - w0) * t) / 2 * 0.78
        cuts.append(cut([(p[0] - uy * hw, p[1] + ux * hw), (p[0] + ux * hw * 0.3, p[1] + uy * hw * 0.3),
                         (p[0] + uy * hw, p[1] - ux * hw)], 1.5))
    caps = ''.join('<circle cx="%.2f" cy="%.2f" r="%.2f" fill="%s"/>' % (c[0], c[1], w / 2, colour)
                   for c, w in ((centre[0], w0), (centre[-1], w1)))
    return part(fill(polygon(pts), colour) + caps, ''.join(cuts))


def claw(base, direction, length, width, curl=0.6):
    return part(fill(smooth(claw_outline(base, direction, length, width, curl)), OR), gap=1.6)


def row(a, b, count, tip, w, colour=SABLE, sway=5.0, stagger=0.0, back=None, **profile):
    """A row of feathers with their roots spaced along a to b, each pointing along `tip` (turned a little, one way
    and the other, so the row is not regular), the last `back` of them swept back into a mane."""
    out = []
    for i in range(count):
        t = (i + 0.5 + stagger) / count
        root = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        v = rotate(tip, sway * (1 if i % 2 else -1))
        if back and i >= count - back:
            v = rotate((v[0] * 1.15, v[1] * 1.15), -38)
        out.append(blade(root, (root[0] + v[0], root[1] + v[1]), w, bend=-3 if i % 2 else 3, colour=colour,
                         **profile))
    return out


# --- the griffin ----------------------------------------------------------------------------------------------

def tail():
    # The tuft: a flame of locks sweeping up from the end of the tail and curling over to the right ...
    base = (596, 730)
    parts = [flame(base, [(580, 714), (562, 702), (550, 686), (550, 668)], 20),
             flame(base, [(610, 716), (628, 708), (642, 694), (648, 676)], 20),
             flame(base, [(604, 702), (610, 672), (620, 648), (636, 632), (650, 630)], 28),
             flame(base, [(586, 702), (576, 676), (574, 650), (582, 628), (596, 616)], 28),
             flame(base, [(594, 700), (590, 664), (594, 630), (608, 604), (628, 594)], 32)]
    # ... and the tail itself, out from behind the haunch and round and up into it.
    parts.append(part(fill(polygon(ribbon([(482, 774), (500, 806), (522, 836), (556, 842), (582, 820), (594, 784), (593, 748),
                                            (596, 718)], taper_profile(22, 13))))))
    return parts


def hind_leg():
    parts = []
    # Fur at the back of the hock, behind the leg.
    for base, tip in (((518, 872), (554, 884)), ((522, 890), (556, 906)), ((518, 906), (546, 928))):
        parts.append(flame(base, [((base[0] + tip[0]) / 2 + 2, (base[1] + tip[1]) / 2 - 3), tip], 13, vein=False))
    # The haunch: the great muscle of the thigh, down to the knee.
    haunch = [(430, 610), (476, 606), (506, 630), (516, 680), (510, 740), (490, 786), (462, 810), (436, 802),
              (418, 764), (406, 712), (404, 656)]
    parts.append(part(fill(smooth(haunch)), cut([(422, 676), (450, 660), (482, 664), (502, 690)], 1.6) +
                      cut([(428, 712), (446, 750), (452, 784)], 1.4)))
    # The leg from the knee, down and back to the sharp point of the hock, then down and forward to the paw.
    shank = [(436, 790), (468, 780), (494, 812), (516, 846), (532, 878, 'c'), (528, 906), (514, 936), (502, 962),
             (494, 986), (474, 992), (458, 976), (464, 956), (474, 932), (484, 906), (488, 882), (472, 856), (452, 826)]
    parts.append(part(fill(smooth(shank)), cut([(470, 812), (490, 842), (504, 868)], 1.4) +
                      cut([(506, 906), (496, 934), (484, 958)], 1.4)))
    # The paw: four toes forward, each with its claw curling down.
    for (x, y) in ((452, 960), (442, 975), (442, 990), (460, 1000)):
        toe = [(x + 22, y - 10), (x + 4, y - 11), (x - 8, y - 4), (x - 9, y + 5), (x + 2, y + 10), (x + 22, y + 9)]
        parts.append(part(fill(smooth(toe))))
        parts.append(claw((x - 6, y + 2), 168, 15, 7, -0.8))
    return parts


TORSO = [(276, 426), (330, 410), (400, 414), (452, 428), (470, 470), (476, 530), (486, 590), (498, 640), (496, 690),
         (478, 728), (446, 746), (414, 740), (402, 694), (396, 640), (392, 598), (300, 596), (262, 586), (250, 550),
         (250, 506), (258, 462)]


def body():
    # The body, smooth: the breast under the fringe of the neck's feathers, the flank and belly below the wing.
    return [part(fill(smooth(TORSO)), cut([(400, 606), (408, 650), (414, 690)], 1.5))]


def wing():
    parts = []
    # The long feathers, from the lowest secondary up to the topmost primary, each curved like a sickle.
    secondaries = [((474, 520), (510, 632), 25, 10), ((486, 504), (544, 606), 25, 12), ((500, 488), (576, 574), 26, 13),
                   ((514, 472), (608, 536), 26, 14), ((528, 454), (636, 492), 26, 14), ((542, 436), (658, 446), 26, 13),
                   ((554, 418), (674, 404), 25, 12)]
    primaries = [((564, 400), (682, 362), 24, 10), ((572, 384), (684, 326), 24, 12), ((578, 370), (674, 292), 24, 14),
                 ((578, 358), (650, 268), 23, 14), ((572, 348), (616, 262), 22, 12)]
    for root, tip, w, bend in secondaries:
        parts.append(blade(root, tip, w, bend, WING, quill=True, base=0.55, widest=0.4, point=0.7))
    for root, tip, w, bend in primaries:
        parts.append(blade(root, tip, w, bend, WING_LIGHT, quill=True, base=0.55, widest=0.4, point=0.7))
    # The coverts over their roots: the wing's shoulder, from the bend of the wing down to the flank.
    coverts = [(446, 452), (458, 420), (484, 394), (516, 372), (550, 354), (576, 346), (596, 356), (600, 384), (588, 420),
               (566, 452), (540, 486), (514, 518), (492, 548), (472, 566), (454, 542), (444, 498)]
    parts.append(part(fill(smooth(coverts), WING_DARK)))
    # The greater coverts, lying along the long feathers' roots, from the wrist in to the shoulder ...
    for i in range(7):
        t = i / 6
        root = (590 - 116 * t, 376 + 150 * t)
        sec = secondaries[6 - i]
        d = (sec[1][0] - sec[0][0], sec[1][1] - sec[0][1])
        ln = math.hypot(*d)
        tip = (root[0] + d[0] / ln * 58, root[1] + d[1] / ln * 58)
        parts.append(blade(root, tip, 30, 4, WING_DARK, base=0.8, widest=0.45, point=0.6))
    # ... the lesser coverts above them ...
    for i in range(6):
        t = i / 5
        root = (580 - 114 * t, 360 + 112 * t)
        d = rotate((0.62, 0.78), -40 * (1 - t))
        tip = (root[0] + d[0] * 40, root[1] + d[1] * 40)
        parts.append(blade(root, tip, 26, 3, WING_DARK, base=0.8, widest=0.45, point=0.6))
    # ... and the leading edge of the wing, from the shoulder to the wrist.
    parts.append(part(fill(polygon(ribbon([(450, 456), (466, 420), (496, 392), (536, 368), (574, 352), (600, 350)],
                                          taper_profile(26, 14))), WING_DARK)))
    return parts


THIGH = [(262, 486), (240, 456), (226, 420), (218, 388), (216, 366), (226, 352), (248, 348), (270, 362), (290, 398),
         (304, 436), (298, 470)]


def raised_foreleg():
    parts = []
    # The scaled shank, leaning up to the foot, and the talons spread to seize: three forward, raised, one behind.
    parts.append(limb([(252, 380), (243, 344), (235, 314), (229, 294)], 24, 18, bands=(0.2, 0.4, 0.6, 0.8)))
    for centre, w0, w1, cdir in (([(238, 298), (252, 302), (266, 302)], 13, 10, 18),
                                 ([(224, 296), (206, 282), (190, 266)], 15, 11, -150),
                                 ([(228, 290), (218, 268), (208, 246)], 16, 11, -122),
                                 ([(234, 290), (238, 268), (242, 246)], 15, 11, -88)):
        parts.append(limb(centre, w0, w1, bands=(0.35, 0.7)))
        e, f = centre[-1], centre[-2]
        ln = math.hypot(e[0] - f[0], e[1] - f[1])
        parts.append(claw((e[0] + (e[0] - f[0]) / ln * 2, e[1] + (e[1] - f[1]) / ln * 2), cdir, 24 if cdir > 0 else 28,
                          w1 * 0.95, 1.0))
    # The thigh, feathered: smooth, with a fringe of narrow feathers at the knee pointing up over the shank.
    parts.append(part(fill(smooth(THIGH)), cut([(232, 440), (252, 462), (280, 470)], 1.5)))
    parts += row((222, 392), (292, 404), 6, (-3, -44), 16, sway=5, base=0.9, widest=0.3, point=0.9)
    parts += row((226, 412), (296, 424), 5, (-4, -42), 17, sway=5, stagger=0.3, base=0.9, widest=0.3, point=0.9)
    return parts


NECK = [(380, 318), (392, 338), (420, 342), (446, 328), (462, 300), (476, 336), (482, 380), (476, 420), (452, 442),
        (400, 436), (340, 432), (288, 436), (300, 410), (330, 386), (356, 358)]


def neck():
    parts = [part(fill(smooth(NECK)))]
    # The neck's hackles: long narrow feathers, row over row up to the head, ending over the breast in a fringe of
    # points; the ones at the back swept down into a mane.
    parts += row((282, 424), (470, 412), 9, (-8, 58), 21, sway=4, back=2, base=0.9, widest=0.3, point=0.9)
    parts += row((312, 394), (476, 382), 8, (-7, 54), 20, sway=4, stagger=0.3, back=2, base=0.9, widest=0.3, point=0.9)
    parts += row((342, 364), (480, 352), 7, (-5, 50), 19, sway=4, back=2, base=0.9, widest=0.3, point=0.9)
    parts += row((380, 336), (482, 322), 5, (-2, 46), 18, sway=4, stagger=0.3, back=2, base=0.9, widest=0.3, point=0.9)
    parts += row((432, 308), (480, 288), 3, (2, 42), 17, sway=4, back=2, base=0.9, widest=0.3, point=0.9)
    return parts


def head():
    parts = []
    # The ear, pricked back, behind the skull.
    ear = [(432, 246, 'c'), (442, 218), (458, 194), (472, 182, 'c'), (470, 210), (464, 236), (458, 256, 'c')]
    parts.append(part(fill(smooth(ear)), cut([(446, 236), (454, 214), (466, 194)], 1.4)))
    # The skull, with its brow over the eye and a line of feathers swept back from it.
    skull = [(378, 262), (390, 240), (414, 228), (442, 230), (462, 248), (470, 276), (464, 304), (446, 326),
             (420, 340), (394, 338), (376, 322), (372, 296)]
    cuts = cut([(394, 264), (408, 256), (428, 255)], 2.2) + cut([(426, 270), (440, 262), (456, 262)], 1.4)
    parts.append(part(fill(smooth(skull)), cuts))
    # The tongue, out and curling, then the beak: the lower mandible and the upper one hooked down over it.
    parts.append(part(fill(polygon(ribbon([(380, 310), (356, 318), (336, 326), (318, 332), (304, 326)],
                                          flame_profile(16))), GULES), gap=1.8))
    lower = [(386, 306, 'c'), (364, 314), (348, 324), (336, 338, 'c'), (354, 338), (374, 330), (390, 320, 'c')]
    parts.append(part(fill(smooth(lower), OR), gap=1.8))
    upper = [(386, 252, 'c'), (368, 251), (348, 256), (330, 268), (318, 286), (316, 304), (322, 320, 'c'), (325, 305),
             (334, 294), (352, 289), (372, 290), (388, 298, 'c'), (394, 284), (392, 266)]
    parts.append(part(fill(smooth(upper), OR), cut([(366, 267), (372, 264), (378, 268)], 1.6), gap=2.0))
    # The eye: gold, ringed by the gap, under the brow.
    parts.append(part('<circle cx="408" cy="268" r="6.5" fill="%s"/>' % OR, gap=1.4))
    parts.append(part('<circle cx="406.5" cy="268" r="3.3" fill="%s"/>' % SABLE, gap=0))
    return parts


def shield_outline(inset=0.0):
    """The heater shield: a straight top, straight sides, curving in to a point."""
    x0, x1, top, side, tip = 118 + inset, 388 - inset, 588 + inset, 740, 992 - inset * 1.6
    cx = (x0 + x1) / 2 + 12
    return ('M %.2f %.2f L %.2f %.2f L %.2f %.2f C %.2f %.2f %.2f %.2f %.2f %.2f '
            'C %.2f %.2f %.2f %.2f %.2f %.2f L %.2f %.2f Z') % (
        x0, top, x1, top, x1, side,
        x1, side + 140, cx + 60, tip - 50, cx, tip,
        cx - 70, tip - 60, x0, side + 140, x0, side, x0, top)


def shield():
    # Quarterly gold and black, bordered black with a fine line parting the border from the field.
    sx, sy = 258, 742
    svg = ('<defs><clipPath id="field"><path d="%s"/></clipPath></defs>' % shield_outline() +
           fill(shield_outline(), OR) +
           '<g clip-path="url(#field)"><rect x="%d" y="580" width="200" height="%d" fill="%s"/>'
           '<rect x="100" y="%d" width="%d" height="300" fill="%s"/></g>' % (sx, sy - 580, FIELD_SABLE, sy, sx - 100,
                                                                              FIELD_SABLE) +
           '<path d="%s" fill="none" stroke="%s" stroke-width="12"/>' % (shield_outline(), FIELD_SABLE))
    cuts = '<path d="%s" fill="none" stroke="#000" stroke-width="1.8"/>' % shield_outline(6.5)
    return [part(svg, cuts, gap=3.0)]


def front_foreleg():
    parts = []
    # Talons hooked over the shield's top corner, under the foot.
    for centre, cdir in (([(150, 584), (132, 592), (124, 606)], 104), ([(160, 588), (148, 600), (142, 614)], 98),
                         ([(170, 590), (164, 604), (162, 618)], 92)):
        parts.append(limb(centre, 13, 10, bands=(0.5,)))
        parts.append(claw((centre[-1][0] - 1, centre[-1][1] + 2), cdir, 22, 10, 1.1))
    # The leg lying along the shield's top, scaled to the foot, and the feathers of the thigh at the breast.
    parts.append(limb([(272, 570), (232, 574), (196, 578), (156, 584)], 28, 22, bands=(0.35, 0.52, 0.69, 0.86)))
    parts += row((304, 546), (296, 590), 3, (-44, 8), 19, sway=6, base=0.9, widest=0.3, point=0.9)
    return parts


def griffin_and_shield():
    """Every part, from the back to the front."""
    return (tail() + hind_leg() + body() + wing() + neck() + head() + raised_foreleg() + shield() +
            front_foreleg())


# --- drawing ----------------------------------------------------------------------------------------------------

def raster(svg, width, height):
    import cairosvg
    from PIL import Image
    x, y, w, h = VIEW
    doc = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%d %d %d %d" width="%d" height="%d">%s</svg>'
           % (x, y, w, h, width, height, svg))
    png = cairosvg.svg2png(bytestring=doc.encode(), output_width=width, output_height=height)
    return np.asarray(Image.open(io.BytesIO(png)).convert('RGBA'), np.float32) / 255


def render(height=1680):
    """The arms as a PIL RGBA image with a clear background, `height` pixels tall."""
    from PIL import Image
    from scipy.ndimage import distance_transform_edt
    width = int(round(height * VIEW[2] / VIEW[3]))
    px = height / VIEW[3]
    canvas = np.zeros((height, width, 4), np.float32)      # premultiplied
    for svg, cuts, gap in griffin_and_shield():
        img = raster(svg, width, height)
        a = img[..., 3].copy()
        if cuts:
            a *= 1 - raster(cuts, width, height)[..., 3]
        rgb = img[..., :3] * a[..., None]
        ys, xs = np.nonzero(a > 0.004)
        if not len(ys):
            continue
        if gap:
            r = gap * px
            m = int(r) + 3
            y0, y1 = max(ys.min() - m, 0), min(ys.max() + m + 1, height)
            x0, x1 = max(xs.min() - m, 0), min(xs.max() + m + 1, width)
            sub = img[y0:y1, x0:x1, 3]
            ring = np.clip(r + 0.5 - distance_transform_edt(sub < 0.5), 0, 1)
            canvas[y0:y1, x0:x1] *= (1 - np.maximum(sub, ring))[..., None]
        canvas[..., :3] = rgb + canvas[..., :3] * (1 - a[..., None])
        canvas[..., 3] = a + canvas[..., 3] * (1 - a)
    out = np.zeros_like(canvas)
    alpha = canvas[..., 3]
    out[..., :3] = canvas[..., :3] / np.maximum(alpha, 1e-6)[..., None]
    out[..., 3] = alpha
    return Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')


if __name__ == '__main__':
    render(int(sys.argv[2]) if len(sys.argv) > 2 else 1680).save(sys.argv[1])
