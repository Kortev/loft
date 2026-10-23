"""Beam's side profile as pixel art at game resolution (1 px = 1/16 block): the drawing kortev approved, and the spec the
box model (tools/beam_boxes.py) is built from. Facing right. Run: python tools/beam_profile.py OUT.png

Everything is written down here: the outline's control points (TOP, SNOUT, UPPER_RIM, CORNER, LOWER_RIM, CHIN, BELLY), the
mint boundary, the teeth, gills, spikes, tail, fin, eyes and the four legs' hip, knee and foot (LEGS). body() gives the
body on its own layer (extras=False leaves off the spikes, tail, fin and eyes), scene() the whole picture at 1 px."""
import sys
from PIL import Image, ImageDraw

W, H, GROUND = 158, 88, 82
OX, STRETCH, FROM = 30, 1.4, 66


def sx(x):
    """Longer through his back half (kortev: he's longer): everything behind the head stretched, the head kept."""
    return OX + (x if x >= FROM else FROM - (FROM - x) * STRETCH)


def m(points):
    return [(sx(x), y) for x, y in points]
NAVY, NAVY_SH = (38, 74, 140), (27, 52, 104)
MINT, MINT_SH = (196, 234, 226), (160, 205, 198)
INK = (16, 20, 34)
RED, PINK, CREAM = (92, 18, 26), (226, 120, 138), (246, 236, 206)
WHITE, BLACK = (250, 250, 248), (12, 12, 16)
SKY, GRASS = (190, 214, 236), (96, 150, 80)


def spline(points, steps=6):
    """Catmull-Rom through the points, so a few control points give a smooth pixel outline."""
    pts = [points[0]] + points + [points[-1]]
    out = []
    for i in range(len(pts) - 3):
        p0, p1, p2, p3 = pts[i:i + 4]
        for s in range(steps):
            t = s / steps
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)))
    out.append(points[-1])
    return out


def ink_outline(img, mask, keep=()):
    px, mp = img.load(), mask.load()
    for y in range(1, img.height - 1):
        for x in range(1, img.width - 1):
            if mp[x, y] and not (mp[x - 1, y] and mp[x + 1, y] and mp[x, y - 1] and mp[x, y + 1]) and px[x, y][:3] not in keep:
                px[x, y] = INK + ((255,) if len(px[x, y]) == 4 else ())


def leg(img, hip, knee, foot, w_hip, w_knee, colour, inner):
    """A leg as a thick blade: hip out and up to the knee, then down to a sharp point, inked round."""
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for a, b, wa, wb in ((hip, knee, w_hip, w_knee), (knee, foot, w_knee, 1)):
        for i in range(41):
            t = i / 40
            x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            r = (wa + (wb - wa) * t) / 2
            d.ellipse([x - r, y - r, x + r, y + r], fill=colour + (255,))
    d.line([(knee[0] - 1, knee[1] + 3), (foot[0] - 1, foot[1] - 3)], fill=inner + (255,), width=1)
    ink_outline(layer, layer.getchannel('A'))
    img.alpha_composite(layer)


# The outline of the body, in the drawing's own x before the stretch (sx maps it), y from the back's highest point.
TOP = m([(14, 30), (24, 22), (38, 14), (54, 9), (70, 7), (86, 9), (98, 13), (108, 19), (114, 25), (116, 31)])
SNOUT = m([(116, 31), (114, 35), (109, 37)])    # the snout's underside curving back to the upper rim
UPPER_RIM = m([(109, 37), (100, 38), (90, 39), (80, 41)])
CORNER = (sx(76), 43)                           # the mouth's corner, under the front eye
LOWER_RIM = m([(80, 46), (92, 50), (104, 53)])  # the lower jaw dropped about 38 degrees from the corner
CHIN = m([(104, 53), (105, 58), (100, 63), (90, 66), (78, 66)])
BELLY = m([(78, 66), (62, 64), (46, 60), (34, 54), (24, 46), (16, 38), (14, 30)])
BOUNDARY = m([(118, 28), (110, 30), (104, 26), (98, 29), (92, 25), (86, 28), (80, 27), (74, 32), (68, 35), (62, 34),
              (56, 39), (50, 38), (44, 43), (38, 42), (30, 45), (22, 41), (12, 41)])
SPIKES = [(66, 7), (56, 6), (46, 5), (36, 4), (26, 3)]      # x of the middle of the base, height; the front one biggest
EYES = [(sx(82), 30, 3.4), (sx(75), 24, 2.9), (sx(68), 19, 2.4)]
GILLS = [sx(66), sx(62), sx(58), sx(54)]
TAIL_STALK = m([(14, 28), (6, 30), (6, 36), (14, 38)])
TAIL_FIN = m([(7, 31), (2, 12), (0, 14), (4, 32), (0, 47), (2, 48), (7, 35)])
PECTORAL = m([(56, 52), (46, 60), (50, 60), (58, 55)])
LIFT = 12  # his back's highest point this far below the top of the picture; belly about a block off the ground
# The legs: hip, knee, foot, the thickness at the hip and the knee; far side then near side, the front pair then the back.
FAR_LEGS = [((sx(78), LIFT + 58), (sx(92), LIFT + 42), (sx(106), GROUND), 6, 5),
            ((sx(54), LIFT + 61), (sx(40), LIFT + 46), (sx(30), GROUND), 6, 5)]
NEAR_LEGS = [((sx(72), LIFT + 60), (sx(88), LIFT + 46), (sx(98), GROUND), 7, 6),
             ((sx(58), LIFT + 63), (sx(44), LIFT + 48), (sx(36), GROUND), 7, 6)]


def outline():
    return spline(TOP) + spline(SNOUT) + spline(UPPER_RIM) + [CORNER] + spline(LOWER_RIM) + spline(CHIN) + spline(BELLY)


def gape():
    return spline(UPPER_RIM) + [CORNER] + spline(LOWER_RIM) + [(sx(108), 46)]


def body(extras=True):
    """The body on its own layer (y measured from his back's highest point). {extras} False: just the body, the head and the
    mouth, without the spikes, the tail, the fins and the eyes."""
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    outl = outline()
    d.polygon(outl, fill=NAVY + (255,))
    sil = Image.new('L', (W, H), 0)
    ImageDraw.Draw(sil).polygon(outl, fill=255)

    # Mint underside, below a jagged line from the snout back to the tail, rising on the head to just under the eyes.
    low = Image.new('L', (W, H), 0)
    ImageDraw.Draw(low).polygon(BOUNDARY + [(sx(12), H), (sx(118), H)], fill=255)
    both = Image.new('L', (W, H), 0)
    both.paste(sil, (0, 0), low)
    img.paste(Image.new('RGBA', (W, H), MINT + (255,)), (0, 0), both)
    shade = Image.new('L', (W, H), 0)
    ImageDraw.Draw(shade).polygon(spline(BELLY) + [(sx(14), H), (sx(78), H)], fill=255)
    sh = Image.new('L', (W, H), 0)
    sh.paste(both, (0, 0), shade)
    img.paste(Image.new('RGBA', (W, H), MINT_SH + (255,)), (0, 0), sh)

    # Inside the gape: dark red, pink gums along both rims, cream teeth hanging down from the top and standing up from
    # the bottom, small and many.
    d.polygon(gape(), fill=RED + (255,))
    d.line(spline(UPPER_RIM) + [CORNER], fill=PINK + (255,))
    d.line([CORNER] + spline(LOWER_RIM), fill=PINK + (255,))
    for (x0, y0, x1, y1, n, down) in ((sx(80), 41, sx(108), 37, 9, True), (sx(80), 46, sx(103), 53, 8, False)):
        for i in range(n):
            t = (i + 0.5) / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            tip = (x, y + 3) if down else (x - 0.5, y - 3)
            d.polygon([(x - 1.2, y), (x + 1.2, y), tip], fill=CREAM + (255,))
    # Gill lines behind the mouth's corner.
    for gx in GILLS:
        d.line([(gx + 2, 39), (gx, 45), (gx + 1, 51)], fill=INK + (255,))
    if extras:
        # Back spikes, the front one biggest.
        for i, (spx, h) in enumerate(SPIKES):
            y = 8 + i * 1.6
            spx = sx(spx)
            d.polygon([(spx - 3, y + 1), (spx + 3, y + 1), (spx - 2, y - h)], fill=NAVY_SH + (255,))
        # Tail: a short stalk and a crescent fin, the upper lobe longer.
        d.polygon(TAIL_STALK, fill=NAVY + (255,))
        d.polygon(TAIL_FIN, fill=NAVY + (255,))
        # Pectoral fin, small, low behind the gills.
        d.polygon(PECTORAL, fill=NAVY_SH + (255,))
        # Eyes: three, up and back from above the mouth's corner, the front one biggest; white, black pupil, ink ring.
        for (ex, ey, r) in EYES:
            d.ellipse([ex - r - 0.7, ey - r - 0.7, ex + r + 0.7, ey + r + 0.7], fill=INK + (255,))
            d.ellipse([ex - r, ey - r, ex + r, ey + r], fill=WHITE + (255,))
            pr = r * 0.55
            d.ellipse([ex - pr + 0.6, ey - pr, ex + pr + 0.6, ey + pr], fill=BLACK + (255,))
    ink_outline(img, img.getchannel('A'), keep=(RED, PINK, CREAM))
    return img


def scene(far=True, legs=True):
    """The whole picture at 1 px: sky, ground, the far legs behind him (far), him, the near legs over him."""
    img = Image.new('RGBA', (W, H), SKY + (255,))
    ImageDraw.Draw(img).rectangle([0, GROUND, W, H], fill=GRASS + (255,))
    if legs and far:
        for hip, knee, foot, wh, wk in FAR_LEGS:
            leg(img, hip, knee, foot, wh, wk, NAVY_SH, MINT_SH)
    img.alpha_composite(body(), (0, LIFT))
    if legs:
        for hip, knee, foot, wh, wk in NEAR_LEGS:
            leg(img, hip, knee, foot, wh, wk, NAVY, MINT)
    return img.convert('RGB')


def main(path):
    img = scene()
    img.resize((W * 8, H * 8), Image.NEAREST).save(path)
    print('wrote', path)


if __name__ == '__main__':
    main(sys.argv[1])
