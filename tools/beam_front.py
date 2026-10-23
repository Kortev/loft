"""Beam from the front as pixel art at game resolution (1 px = 1/16 block), matching the approved side profile
(beam_profile.py: same heights, colours and ink): the second half of the spec tools/beam_boxes.py builds the boxes from.
Writes the front view and a sheet with both views side by side.
Run: python tools/beam_front.py FRONT.png [SHEET.png [SIDE.png]]. body(extras=False) leaves off the spike, the fins and the
eyes; scene() is the whole picture at 1 px."""
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_profile as bp  # noqa: E402
from beam_profile import NAVY, NAVY_SH, MINT, MINT_SH, INK, RED, PINK, CREAM, WHITE, BLACK, SKY, GRASS  # noqa: E402

W, H, GROUND = 104, 88, 82
CX = W / 2
THROAT = (60, 10, 16)


def mirror(half):
    """Right half of an outline, top to bottom, made whole by mirroring it back up the left."""
    return half + [(2 * CX - x, y) for x, y in reversed(half)]


def r(points):
    return [(CX + x, y) for x, y in points]


LIFT = bp.LIFT
# The legs (hip, knee, foot, thickness at the hip and the knee), per side (the right one; the left mirrors it): the back pair
# behind him, then the front pair over him.
BACK_LEGS = ((14, LIFT + 62), (30, LIFT + 48), (34, GROUND), 6, 5)
FRONT_LEGS = ((18, LIFT + 60), (35, LIFT + 45), (41, GROUND), 7, 6)
BODY_HALF = [(0, 7), (8, 8), (15, 11), (20, 17), (23, 26), (25, 38), (24, 50), (19, 60), (10, 66), (0, 67)]
FACE_EDGE = [(27, 44), (23, 40), (21, 36), (17, 37), (14, 32), (10, 33), (7, 29), (3, 31), (0, 28)]
UPPER_LIP = [(-18, 42), (-12, 39), (0, 38), (12, 39), (18, 42)]
LOWER_LIP = [(18, 42), (15, 47), (8, 51), (0, 53), (-8, 51), (-15, 47), (-18, 42)]
FRONT_EYES = [(18, 30, 3.4), (19, 24, 2.9), (17, 18, 2.4)]


def body(extras=True):
    """The body on its own layer (y measured from his back's highest point, as in the side view). {extras} False: without
    the spike, the pectoral fins and the eyes."""
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # His bulk behind the head: tall and rounded, widest a little below the middle, the belly flatter than the back.
    half = bp.spline(r(BODY_HALF))
    outline = mirror(half)
    d.polygon(outline, fill=NAVY + (255,))
    sil = Image.new('L', (W, H), 0)
    ImageDraw.Draw(sil).polygon(outline, fill=255)
    # Mint underside below a jagged line, low on his flanks and rising across the face to just under the eyes.
    edge = r(FACE_EDGE)
    boundary = edge + [(2 * CX - x, y) for x, y in reversed(edge)][1:]
    low = Image.new('L', (W, H), 0)
    ImageDraw.Draw(low).polygon(boundary + [(CX - 27, H), (CX + 27, H)], fill=255)
    both = Image.new('L', (W, H), 0)
    both.paste(sil, (0, 0), low)
    img.paste(Image.new('RGBA', (W, H), MINT + (255,)), (0, 0), both)
    # Shade on the lower flanks either side of the chin.
    for s in (-1, 1):
        d.polygon([(CX + s * 25, 44), (CX + s * 24, 54), (CX + s * 18, 62), (CX + s * 15, 56), (CX + s * 20, 46)],
                  fill=MINT_SH + (255,))
    # Front back-spike standing up over the top, and the small pectoral fins out low at the sides.
    if extras:
        d.polygon([(CX - 3, 9), (CX + 3, 9), (CX, 1)], fill=NAVY_SH + (255,))
        for s in (-1, 1):
            d.polygon([(CX + s * 22, 52), (CX + s * 31, 59), (CX + s * 30, 61), (CX + s * 21, 57)], fill=NAVY_SH + (255,))
    # The snout dome's underside overhanging the mouth: an ink line across under it.
    snout = bp.spline(r([(-17, 36), (-9, 37), (0, 37), (9, 37), (17, 36)]))
    # The gape seen head on: the upper rim nearly flat under the snout, the corners out under the front eyes, the
    # lower jaw dropped (about 38 degrees) so the bottom rim hangs in a U.
    upper = bp.spline(r(UPPER_LIP))
    lower = bp.spline(r(LOWER_LIP))
    gape = upper + lower
    d.polygon(gape, fill=RED + (255,))
    d.ellipse([CX - 8, 42, CX + 8, 49], fill=THROAT + (255,))
    d.line(upper, fill=PINK + (255,))
    d.line(lower, fill=PINK + (255,))
    # Teeth: cream, small and many, hanging from the top rim and standing up from the bottom one.
    for pts, n, down in ((upper, 11, True), (lower, 10, False)):
        for i in range(n):
            x, y = pts[int((i + 0.5) / n * (len(pts) - 1))]
            tip = (x, y + 3) if down else (x, y - 3)
            d.polygon([(x - 1.1, y), (x + 1.1, y), tip], fill=CREAM + (255,))
    d.line(snout, fill=INK + (255,))
    # Eyes: three a side on the sides of the head, seen edge-on from the front, so narrow; the front one lowest and
    # biggest, each further back sitting higher and further out.
    for s in (-1, 1) if extras else ():
        for (ex, ey, rad) in FRONT_EYES:
            x, rx = CX + s * ex, rad * 0.65
            d.ellipse([x - rx - 0.7, ey - rad - 0.7, x + rx + 0.7, ey + rad + 0.7], fill=INK + (255,))
            d.ellipse([x - rx, ey - rad, x + rx, ey + rad], fill=WHITE + (255,))
            d.ellipse([x + s * 0.5 - rx * 0.55, ey - rad * 0.55, x + s * 0.5 + rx * 0.55, ey + rad * 0.55],
                      fill=BLACK + (255,))
    bp.ink_outline(img, img.getchannel('A'), keep=(RED, PINK, CREAM, THROAT))
    return img


def scene(legs=True):
    """The whole front view at 1 px."""
    img = Image.new('RGBA', (W, H), SKY + (255,))
    ImageDraw.Draw(img).rectangle([0, GROUND, W, H], fill=GRASS + (255,))
    if legs:
        # Back legs behind him: from under his middle, out to a high knee, down to a point, a little narrower set.
        for s in (-1, 1):
            hip, knee, foot, wh, wk = BACK_LEGS
            bp.leg(img, (CX + s * hip[0], hip[1]), (CX + s * knee[0], knee[1]), (CX + s * foot[0], foot[1]), wh, wk, NAVY_SH, MINT_SH)
    img.alpha_composite(body(), (0, LIFT))
    if legs:
        # Front legs over him: out of the low flank by the chin, out and up to the knee, down to a point.
        for s in (-1, 1):
            hip, knee, foot, wh, wk = FRONT_LEGS
            bp.leg(img, (CX + s * hip[0], hip[1]), (CX + s * knee[0], knee[1]), (CX + s * foot[0], foot[1]), wh, wk, NAVY, MINT)
    return img.convert('RGB')


def front(path):
    img = scene()
    img.resize((W * 8, H * 8), Image.NEAREST).save(path)
    print('wrote', path)
    return img


def sheet(front_img, side_path, path):
    """Side and front next to each other at the same scale, on the same ground line."""
    side = Image.open(side_path).convert('RGB')
    side = side.resize((side.width // 8, side.height // 8), Image.NEAREST)
    gap = 6
    out = Image.new('RGB', (side.width + gap + W, H), SKY)
    out.paste(side, (0, 0))
    out.paste(front_img, (side.width + gap, 0))
    ImageDraw.Draw(out).rectangle([side.width, 0, side.width + gap - 1, H], fill=(255, 255, 255))
    out.resize((out.width * 6, out.height * 6), Image.NEAREST).save(path)
    print('wrote', path)


if __name__ == '__main__':
    f = front(sys.argv[1])
    if len(sys.argv) > 2:
        sheet(f, sys.argv[3] if len(sys.argv) > 3 else 'work/beam_profile_v4.png', sys.argv[2])
