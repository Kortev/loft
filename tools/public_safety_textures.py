"""Block textures and item sprites for the Public Safety outpost (Chainsaw Man mod): the look kortev approves before any building.

16 x 16 PIL textures painted the way Minecraft's are (a few flat tones to an area, a little noise, dark pixels in the creases),
with the small painting kit of tools/hotel_textures.py.

The show's Public Safety is a government office: plain, serious, a little worn. So the outpost is pale painted concrete with
rows of ordinary windows, a navy sign, a grey vinyl floor, laminate counters, grey steel filing cabinets, a cork board of mission
sheets, black desk phones and fluorescent panels. Our own are the emblem (an octagon with a red diamond, not a real logo),
the sign, the board's sheets and the gear.

Run (system Python with Pillow):

    python tools/public_safety_textures.py        # writes work/public_safety_look/textures/*.png, items/*.png and two contact sheets

`build()` returns {name: 16 x 16 image} for the blocks; `build_items()` returns {name: 16 x 16 RGBA sprite}.
"""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hotel_textures as ht  # noqa: E402
from hotel_textures import Tex, shade, mix, text, text_width, rgb  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

OUT = os.path.abspath(os.path.join(HERE, '..', 'work', 'public_safety_look'))

NAVY = (34, 42, 64)
NAVY_D = (22, 28, 44)
NAVY_L = (54, 66, 96)
CRIM = (156, 30, 38)
CRIM_D = (108, 18, 28)
SILVER = (196, 200, 208)
SILVER_D = (130, 136, 148)
SILVER_L = (228, 232, 238)
CONC = (178, 176, 168)
GREY_STEEL = (122, 128, 134)
PAPER = (236, 232, 218)
PAPER_Y = (232, 222, 184)
CORK = (172, 128, 82)
WOOD = (112, 74, 46)
WOOD_D = (78, 50, 32)
WOOD_L = (146, 100, 62)


# --- the emblem (our own) ---------------------------------------------------------------------------------------------

def emblem_pixels(t, ox, oy, size=16, ring=SILVER, field=NAVY, core=CRIM, spark=PAPER):
    """An octagon ring round a navy field, a red diamond in it and a pale vertical stroke through the diamond. Drawn on a
    Tex at (ox, oy), size 12 or 16."""
    n = size
    c = (n - 1) / 2.0
    cut = n * 0.30
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x - c), abs(y - c)
            inside = dx <= c and dy <= c and dx + dy <= c * 2 - cut + 1.2
            if not inside:
                continue
            edge = not (abs(x - c) <= c - 1 and abs(y - c) <= c - 1 and dx + dy <= c * 2 - cut - 0.3)
            if edge:
                t.set(ox + x, oy + y, ring)
            else:
                dd = dx + dy
                if dd <= c * 0.78:
                    t.set(ox + x, oy + y, core)
                else:
                    t.set(ox + x, oy + y, field)
    for y in range(int(n * 0.22), int(n * 0.78)):
        t.set(ox + int(c), oy + y, spark)
        if n >= 16:
            t.set(ox + int(c) + 1, oy + y, mix(spark, core, 0.5))


def emblem_tile(bg=None):
    t = Tex(bg or NAVY_D, 3)
    emblem_pixels(t, 0, 0, 16)
    return t.image()


# --- the outside ------------------------------------------------------------------------------------------------------

def wall_concrete(seed=101):
    t = Tex(CONC, seed)
    t.noise(0.045, 0.7)
    t.hline(0, 15, 15, (150, 148, 140))
    t.hline(0, 15, 0, (190, 188, 180))
    for x, n in ((3, 7), (10, 11), (13, 5)):
        t.streak(x, 1, n, (166, 164, 156), 0.15)
    t.speckle((156, 154, 146), 8)
    return t.image()


def wall_concrete_base(seed=102):
    t = Tex((138, 138, 132), seed)
    t.noise(0.08, 0.8)
    t.hline(0, 15, 0, (116, 116, 112))
    t.speckle((106, 106, 102), 14)
    t.speckle((156, 154, 148), 8)
    return t.image()


def wall_concrete_top(seed=103):
    """The parapet's cap and the plain band under the roof."""
    t = Tex((194, 192, 184), seed)
    t.noise(0.04, 0.6)
    t.hline(0, 15, 13, (140, 138, 132))
    t.hline(0, 15, 14, (122, 120, 116))
    t.hline(0, 15, 15, (150, 148, 142))
    return t.image()


def window_day(seed=104):
    """An office window by day: a pale aluminium frame round blue-grey glass with the sky in it and a venetian blind."""
    t = Tex((96, 118, 138), seed)
    t.rect(2, 2, 13, 6, (132, 158, 178))
    t.line([(3, 6), (6, 3)], (178, 200, 214))
    t.line([(8, 6), (11, 3)], (170, 192, 208))
    for y in (8, 10, 12):
        t.hline(2, 13, y, (106, 128, 146))
    t.noise(0.04, 0.5)
    t.frame(0, 0, 15, 15, (92, 96, 100))
    t.frame(1, 1, 14, 14, (186, 190, 194))
    t.vline(7, 2, 13, (186, 190, 194))
    t.vline(8, 2, 13, (126, 130, 136))
    return t.image()


def window_lit(seed=105):
    """The same window at night with the light on: warm, blinds half down."""
    t = Tex((255, 214, 128), seed)
    t.rect(2, 2, 13, 5, (255, 232, 164))
    for y in (7, 9, 11, 13):
        t.hline(2, 13, y, (226, 172, 98))
    t.noise(0.04, 0.5)
    t.frame(0, 0, 15, 15, (92, 96, 100))
    t.frame(1, 1, 14, 14, (176, 178, 180))
    t.vline(7, 2, 13, (176, 178, 180))
    t.vline(8, 2, 13, (120, 122, 126))
    return t.image()


def window_dark(seed=106):
    t = Tex((24, 30, 42), seed)
    t.line([(3, 7), (7, 3)], (58, 70, 94))
    t.line([(4, 12), (11, 5)], (44, 54, 76))
    t.noise(0.04, 0.5)
    t.frame(0, 0, 15, 15, (70, 74, 80))
    t.frame(1, 1, 14, 14, (140, 144, 150))
    t.vline(7, 2, 13, (140, 144, 150))
    t.vline(8, 2, 13, (96, 100, 106))
    return t.image()


def door_glass(half, lit=False, seed=107):
    """The glass double door with a steel frame and the emblem in white on the glass."""
    glass = (255, 214, 130) if lit else (88, 112, 132)
    t = Tex(glass, seed)
    hi = mix(glass, (255, 255, 255), 0.4)
    t.line([(3, 12), (9, 5)] if half == 'top' else [(3, 9), (8, 4)], hi)
    if half == 'bottom':
        t.rect(2, 2, 13, 3, SILVER)
        t.hline(2, 13, 3, SILVER_D)
        t.rect(0, 14, 15, 15, (120, 126, 132))
    else:
        t.hline(0, 15, 0, SILVER_D)
        t.hline(0, 15, 1, SILVER)
        # a small round decal, our emblem in white
        for (x, y) in ((4, 6), (5, 5), (6, 5), (7, 6), (7, 7), (6, 8), (5, 8), (4, 7)):
            t.set(x, y, (232, 236, 240))
    t.noise(0.03, 0.4)
    for x in (0, 15):
        t.vline(x, 0, 15, (96, 102, 108))
    for x in (1, 14):
        t.vline(x, 0, 15, SILVER)
    t.vline(7, 0, 15, (96, 102, 108))
    t.vline(8, 0, 15, (96, 102, 108))
    return t.image()


def sign_main(tiles=7, seed=108):
    """The sign over the door: PUBLIC SAFETY in white on navy, a silver rim and a thin red line."""
    img = ht.sign_strip('PUBLIC SAFETY', tiles, (238, 240, 244), NAVY, 2, seed, rim=SILVER_D)
    t = Tex(NAVY, seed, 16 * tiles, 16)
    t.p = [[img.getpixel((x, y)) for x in range(16 * tiles)] for y in range(16)]
    t.hline(2, 16 * tiles - 3, 14, CRIM)
    return t.image()


def sign_branch(seed=109):
    img = ht.sign_strip('DEVIL DIVISION', 4, (222, 226, 232), NAVY_D, 1, seed, rim=SILVER_D)
    return img


def banner(seed=110):
    """The flag on the pole: navy, a silver edge, the emblem, 32 x 16."""
    t = Tex(NAVY, seed, 32, 16)
    t.frame(0, 0, 31, 15, SILVER_D)
    t.hline(1, 30, 1, SILVER)
    t.hline(1, 30, 14, SILVER)
    emblem_pixels(t, 8, 2, 12)
    t.hline(2, 6, 7, CRIM)
    t.hline(2, 6, 8, CRIM)
    t.hline(21, 29, 7, CRIM)
    t.hline(21, 29, 8, CRIM)
    t.noise(0.04, 0.5)
    return t.image()


def roof(seed=111):
    t = Tex((86, 88, 92), seed)
    t.noise(0.1, 0.8)
    t.speckle((116, 118, 122), 18)
    t.speckle((62, 64, 68), 18)
    return t.image()


def canopy_under(seed=112):
    t = Tex((84, 82, 80), seed)
    for x, y in ((4, 4), (11, 11)):
        t.rect(x - 1, y - 1, x + 1, y + 1, (255, 232, 176))
        t.frame(x - 2, y - 2, x + 2, y + 2, (126, 118, 104))
    t.noise(0.05, 0.5)
    return t.image()


def ac_unit_side(seed=113):
    t = Tex((200, 202, 204), seed)
    t.rect(2, 2, 13, 13, (150, 154, 158))
    for y in range(3, 13, 2):
        t.hline(3, 12, y, (96, 100, 106))
    t.frame(1, 1, 14, 14, (120, 124, 128))
    return t.image()


def vending_front(seed=114):
    """A drinks machine by the door: blue, a lit window of cans, a coin slot."""
    t = Tex((38, 82, 150), seed)
    t.rect(2, 2, 13, 9, (232, 240, 248))
    cols = [(180, 40, 40), (230, 200, 60), (60, 140, 70), (180, 40, 40), (60, 120, 190), (230, 200, 60)]
    for i, c in enumerate(cols):
        for y in (3, 6):
            t.rect(2 + i * 2, y, 3 + i * 2, y + 1, c)
    t.rect(10, 11, 13, 13, (30, 30, 36))
    t.rect(2, 11, 8, 14, (24, 28, 36))
    t.frame(0, 0, 15, 15, (22, 54, 104))
    t.set(11, 12, (210, 210, 210))
    return t.image()


def street_lamp_top(seed=115):
    t = Tex((70, 72, 76), seed)
    t.rect(3, 3, 12, 12, (255, 236, 176))
    t.frame(2, 2, 13, 13, (50, 52, 56))
    return t.image()


def pole(seed=116):
    t = Tex((84, 88, 94), seed)
    t.vline(5, 0, 15, (120, 126, 132))
    t.vline(10, 0, 15, (56, 60, 66))
    t.noise(0.05, 0.5)
    return t.image()


def mat(seed=117):
    t = Tex((52, 66, 58), seed)
    t.frame(1, 1, 14, 14, (36, 46, 40))
    t.noise(0.1, 0.8)
    t.hline(3, 12, 8, (180, 186, 180))
    return t.image()


# --- the inside -------------------------------------------------------------------------------------------------------

def floor_vinyl(seed=121):
    """Grey-green office vinyl, 8 x 8 px squares in two tones with a light joint."""
    t = Tex((112, 124, 116), seed)
    for y in range(16):
        for x in range(16):
            sq = (x // 8 + y // 8) % 2
            t.set(x, y, (118, 130, 122) if sq else (104, 116, 108))
    t.hline(0, 15, 0, (140, 150, 142))
    t.hline(0, 15, 8, (140, 150, 142))
    t.vline(0, 0, 15, (88, 98, 92))
    t.vline(8, 0, 15, (88, 98, 92))
    t.noise(0.05, 0.8)
    t.speckle((86, 94, 88), 8)
    return t.image()


def floor_carpet(seed=122):
    t = Tex((54, 62, 78), seed)
    t.noise(0.1, 0.9)
    t.speckle((70, 80, 98), 22)
    t.speckle((40, 46, 60), 22)
    return t.image()


def rug(seed=123):
    t = Tex((102, 26, 32), seed)
    t.noise(0.08, 0.9)
    t.frame(0, 0, 15, 15, (60, 14, 20))
    t.frame(2, 2, 13, 13, (176, 140, 80))
    t.speckle((124, 36, 42), 14)
    return t.image()


def wall_plaster(seed=124):
    """Off-white painted plaster above a pale-green dado."""
    t = Tex((222, 218, 200), seed)
    t.noise(0.03, 0.6)
    t.speckle((206, 202, 184), 10)
    return t.image()


def wall_dado(seed=125):
    t = Tex((136, 156, 140), seed)
    t.noise(0.04, 0.6)
    t.hline(0, 15, 0, (206, 202, 186))
    t.hline(0, 15, 1, (178, 176, 160))
    t.hline(0, 15, 15, (104, 122, 108))
    return t.image()


def ceiling_panel(seed=126):
    t = Tex((214, 214, 206), seed)
    t.frame(0, 0, 15, 15, (170, 170, 164))
    for x in range(2, 15, 3):
        for y in range(2, 15, 3):
            t.set(x, y, (180, 180, 174))
    t.noise(0.03, 0.5)
    return t.image()


def fluoro(seed=127):
    t = Tex((236, 244, 255), seed)
    t.frame(0, 0, 15, 15, (150, 154, 160))
    t.frame(1, 1, 14, 14, (200, 206, 214))
    t.hline(2, 13, 4, (255, 255, 255))
    t.hline(2, 13, 11, (255, 255, 255))
    return t.image()


def counter_top(variant='plain', seed=128):
    """The front desk's top, seen from above: dark laminate, an edge highlight, and what lies on it."""
    t = Tex((58, 64, 74), seed)
    t.noise(0.05, 0.7)
    t.hline(0, 15, 0, (116, 122, 132))
    t.hline(0, 15, 15, (36, 40, 48))
    if variant == 'phone':
        t.rect(3, 5, 11, 12, (20, 20, 24))           # a black desk phone: body, dial, handset
        t.rect(4, 6, 10, 7, (50, 50, 58))
        for x, y in ((5, 9), (7, 9), (9, 9), (5, 11), (7, 11), (9, 11)):
            t.set(x, y, (150, 150, 158))
        t.rect(2, 3, 12, 4, (34, 34, 40))
        t.hline(3, 11, 3, (80, 80, 90))
        t.line([(12, 12), (14, 14)], (30, 30, 34))
    elif variant == 'papers':
        t.rect(3, 3, 9, 10, PAPER)
        t.rect(6, 6, 12, 13, PAPER_Y)
        for y in (5, 7, 9):
            t.hline(4, 8, y, (156, 154, 144))
        t.hline(7, 11, 9, (170, 160, 130))
        t.hline(7, 11, 11, (170, 160, 130))
        t.rect(11, 3, 12, 6, (30, 30, 80))           # a pen
    elif variant == 'stamp':
        t.rect(3, 4, 8, 11, PAPER)
        t.rect(9, 8, 12, 12, (30, 30, 36))
        t.rect(10, 6, 11, 8, (150, 30, 36))
        t.set(5, 7, CRIM)
        t.set(6, 7, CRIM)
        t.set(5, 8, CRIM)
    elif variant == 'bell':
        t.rect(6, 6, 9, 9, (206, 164, 66))
        t.rect(7, 7, 8, 8, (240, 206, 108))
        t.frame(5, 5, 10, 10, (138, 98, 40))
    return t.image()


def counter_front(emblem=False, seed=129):
    """The counter's front: pale laminate panels on a dark kick plate."""
    t = Tex((156, 160, 164), seed)
    t.rect(0, 0, 15, 1, (72, 78, 90))                   # the lip
    t.hline(0, 15, 2, (196, 200, 204))
    t.rect(0, 13, 15, 15, (44, 48, 56))                 # the kick plate
    t.vline(0, 3, 12, (118, 122, 128))
    t.vline(15, 3, 12, (118, 122, 128))
    t.noise(0.04, 0.6)
    if emblem:
        t.rect(3, 3, 12, 12, (176, 180, 184))
        emblem_pixels(t, 2, 2, 12)
    return t.image()


def desk_front(seed=130):
    """A grey steel office desk's front, drawers on the left."""
    t = Tex((126, 132, 138), seed)
    t.rect(0, 0, 15, 1, (96, 102, 110))
    t.hline(0, 15, 2, (170, 176, 182))
    t.frame(1, 3, 6, 13, (96, 102, 110))
    for y in (4, 8):
        t.rect(2, y, 5, y + 3, (140, 146, 152))
        t.hline(3, 4, y + 1, (218, 222, 226))
    t.rect(7, 3, 14, 12, (84, 90, 98))                  # the knee space
    t.hline(7, 14, 3, (62, 66, 72))
    t.noise(0.04, 0.6)
    return t.image()


def desk_top(variant='papers', seed=131):
    t = Tex((142, 114, 82), seed)
    t.noise(0.05, 0.7)
    t.frame(0, 0, 15, 15, (104, 80, 56))
    if variant == 'papers':
        t.rect(2, 3, 7, 10, PAPER)
        t.rect(5, 8, 11, 13, PAPER_Y)
        for y in (4, 6):
            t.hline(3, 6, y, (156, 154, 144))
        t.rect(12, 3, 13, 6, (30, 30, 80))
    elif variant == 'phone':
        t.rect(3, 6, 10, 12, (20, 20, 24))
        t.rect(4, 7, 9, 8, (50, 50, 58))
        for x, y in ((5, 10), (7, 10), (9, 10)):
            t.set(x, y, (150, 150, 158))
        t.rect(2, 4, 11, 5, (34, 34, 40))
    elif variant == 'lamp':
        t.rect(10, 2, 13, 5, (30, 74, 52))
        t.rect(11, 3, 12, 4, (255, 232, 160))
        t.rect(2, 6, 8, 12, PAPER)
        t.hline(3, 7, 8, (156, 154, 144))
    return t.image()


def captain_desk_front(seed=132):
    t = Tex(WOOD_D, seed)
    t.rect(0, 0, 15, 2, (56, 36, 24))
    t.hline(0, 15, 3, (150, 106, 66))
    t.frame(2, 5, 13, 13, (60, 38, 24))
    t.rect(3, 6, 12, 12, (88, 58, 38))
    t.hline(6, 9, 8, ht.BRASS)
    t.set(7, 9, ht.BRASS_HI)
    t.noise(0.05, 0.6)
    return t.image()


def captain_desk_top(variant='plain', seed=133):
    t = Tex((86, 56, 36), seed)
    t.noise(0.05, 0.6)
    t.frame(0, 0, 15, 15, (56, 36, 24))
    t.rect(2, 2, 13, 13, (36, 52, 46))                   # a green leather blotter
    t.frame(2, 2, 13, 13, (150, 110, 60))
    if variant == 'plate':
        t.rect(4, 10, 11, 12, (206, 164, 66))
        t.hline(5, 10, 11, (138, 98, 40))
    elif variant == 'papers':
        t.rect(3, 3, 8, 9, PAPER)
        t.rect(8, 5, 12, 10, PAPER_Y)
        t.rect(11, 11, 12, 12, (150, 30, 36))
    return t.image()


def cabinet_front(seed=134):
    """A four-drawer grey steel filing cabinet."""
    t = Tex((120, 128, 134), seed)
    t.frame(0, 0, 15, 15, (78, 84, 92))
    for y in (1, 5, 9, 13):
        pass
    for i, y in enumerate((1, 5, 9)):
        t.rect(1, y, 14, y + 3, (132, 140, 146))
        t.hline(1, 14, y, (172, 178, 184))
        t.hline(1, 14, y + 3, (84, 90, 98))
        t.rect(6, y + 1, 9, y + 1, (212, 216, 220))        # the handle
        t.rect(4, y + 2, 11, y + 2, (232, 230, 214))        # the label
    t.rect(1, 13, 14, 14, (96, 102, 110))
    t.noise(0.04, 0.5)
    return t.image()


def cabinet_side(seed=135):
    t = Tex((112, 120, 126), seed)
    t.frame(0, 0, 15, 15, (78, 84, 92))
    t.noise(0.04, 0.5)
    return t.image()


def cabinet_top(seed=136):
    t = Tex((138, 146, 152), seed)
    t.frame(0, 0, 15, 15, (96, 104, 110))
    t.rect(3, 4, 7, 9, PAPER)
    t.noise(0.04, 0.5)
    return t.image()


def shelf_files(seed=137):
    """A bookcase of case files: binders in muted colours with white spine labels, two shelves."""
    t = Tex(WOOD_D, seed)
    r = random.Random(seed)
    cols = [(40, 52, 84), (116, 36, 40), (60, 84, 64), (124, 124, 128), (156, 130, 84), (30, 30, 36), (84, 60, 40)]
    for top in (1, 8):
        t.rect(1, top, 14, top + 6, (36, 24, 18))
        x = 1
        while x < 14:
            w = r.choice((1, 2, 2, 3))
            h = r.choice((5, 6, 6, 7))
            c = r.choice(cols)
            for xx in range(x, min(14, x + w)):
                t.rect(xx, top + 7 - h, xx, top + 6, shade(c, 1.0 + 0.1 * ((xx + top) % 2)))
            if w >= 2:
                t.set(x, top + 7 - h + 1, (226, 224, 206))
            x += w
        t.hline(1, 14, top + 7, (140, 96, 60))
    t.hline(0, 15, 15, (140, 96, 60))
    t.frame(0, 0, 15, 15, (56, 36, 24))
    t.noise(0.04, 0.6)
    return t.image()


def shelf_side(seed=138):
    t = Tex(WOOD_D, seed)
    t.frame(0, 0, 15, 15, (56, 36, 24))
    t.noise(0.05, 0.6)
    return t.image()


def clock(seed=139):
    t = Tex(PAPER, seed)
    t.frame(0, 0, 15, 15, (60, 62, 68))
    t.frame(1, 1, 14, 14, (180, 182, 186))
    t.rect(7, 4, 8, 8, (30, 30, 36))
    t.line([(8, 8), (11, 10)], (30, 30, 36))
    t.set(7, 8, CRIM)
    for x, y in ((7, 2), (7, 13), (2, 8), (13, 8)):
        t.set(x, y, (50, 50, 56))
    return t.image()


def stair_top(seed=140):
    t = Tex((74, 78, 84), seed)
    t.rect(0, 12, 15, 15, (222, 192, 70))               # the yellow nosing
    t.hline(0, 15, 12, (170, 142, 40))
    t.noise(0.06, 0.8)
    return t.image()


def stair_side(seed=141):
    t = Tex((148, 146, 140), seed)
    t.noise(0.05, 0.7)
    t.hline(0, 15, 0, (170, 168, 160))
    return t.image()


def bench_top(seed=142):
    t = Tex((38, 56, 96), seed)
    t.noise(0.07, 0.8)
    t.frame(0, 0, 15, 15, (24, 36, 66))
    t.hline(2, 13, 8, (28, 42, 74))
    return t.image()


def bench_side(seed=143):
    t = Tex((30, 44, 78), seed)
    t.noise(0.06, 0.8)
    t.hline(0, 15, 0, (52, 70, 116))
    return t.image()


def notice_poster(seed=144):
    """A small safety notice by the door: a red band, a devil's silhouette crossed out, lines of text."""
    t = Tex((236, 232, 214), seed)
    t.rect(0, 0, 15, 3, CRIM)
    t.hline(3, 12, 1, (240, 236, 224))
    t.rect(6, 5, 9, 9, (40, 40, 46))                       # a head with horns
    t.set(5, 4, (40, 40, 46))
    t.set(10, 4, (40, 40, 46))
    t.rect(5, 10, 10, 11, (40, 40, 46))
    t.line([(3, 4), (12, 12)], CRIM)
    t.line([(4, 4), (12, 11)], CRIM)
    t.hline(2, 13, 13, (150, 148, 136))
    t.hline(4, 11, 14, (150, 148, 136))
    t.frame(0, 0, 15, 15, (160, 156, 140))
    return t.image()


def phone_wall(seed=145):
    """A wall phone with a coiled cord, by the board."""
    t = Tex((222, 218, 200), seed)
    t.rect(4, 2, 11, 13, (28, 28, 34))
    t.rect(5, 3, 10, 6, (60, 60, 70))
    for y in (8, 10, 12):
        for x in (5, 7, 9):
            t.set(x, y, (150, 150, 158))
    t.rect(2, 3, 3, 10, (18, 18, 22))
    t.rect(12, 3, 13, 10, (18, 18, 22))
    return t.image()


# --- the mission board (3 x 2 blocks drawn as one picture) -----------------------------------------------------------

def board_art(seed=150):
    """A cork board in a dark wooden frame with mission sheets pinned to it: a red header band, ruled lines, a stamp on one,
    a yellow rush sheet, a map scrap. 48 x 32 pixels."""
    w, h = 48, 32
    t = Tex(CORK, seed, w, h)
    t.noise(0.1, 0.9)
    t.speckle((148, 106, 66), 60)
    t.speckle((194, 150, 100), 40)
    # the frame
    for i, c in enumerate((WOOD_L, WOOD, WOOD_D)):
        t.frame(i, i, w - 1 - i, h - 1 - i, c)
    t.frame(3, 3, w - 4, h - 4, (96, 62, 38))
    r = random.Random(seed)

    def sheet(x, y, sw, sh, paper=PAPER, band=CRIM, lines=3, stamp=False, pin=(190, 40, 40)):
        t.rect(x + 1, y + 1, x + sw, y + sh, (112, 82, 52))     # shadow
        t.rect(x, y, x + sw - 1, y + sh - 1, paper)
        if band:
            t.rect(x, y, x + sw - 1, y + 1, band)
        for i in range(lines):
            yy = y + 3 + i * 2
            if yy < y + sh - 1:
                t.hline(x + 1, x + sw - 2 - (i % 2) * 2, yy, (150, 148, 138))
        t.set(x + sw // 2, y, pin)
        if stamp:
            t.rect(x + sw - 4, y + sh - 4, x + sw - 2, y + sh - 2, CRIM)
            t.set(x + sw - 3, y + sh - 3, paper)
        return

    sheet(5, 5, 9, 11)
    sheet(16, 4, 8, 10, PAPER_Y, band=CRIM_D, stamp=True)
    sheet(26, 6, 9, 11, band=NAVY, pin=(50, 90, 190))
    sheet(37, 4, 7, 9)
    sheet(6, 18, 8, 9, band=NAVY, lines=2)
    sheet(15, 17, 10, 11, PAPER_Y, lines=4, stamp=True)
    sheet(28, 20, 7, 8, band=CRIM)
    sheet(36, 15, 8, 12, lines=4, band=CRIM_D, pin=(50, 90, 190))
    # a map scrap
    t.rect(27, 14, 33, 18, (196, 206, 184))
    t.line([(27, 17), (33, 15)], (120, 140, 150))
    t.set(30, 16, CRIM)
    t.noise(0.03, 0.5)
    return t


def split(name, t, cols, rows):
    img = t.image()
    out = {}
    for j in range(rows):
        for i in range(cols):
            out['%s_%d_%d' % (name, i, j)] = img.crop((16 * i, 16 * j, 16 * i + 16, 16 * j + 16))
    return out


def map_art(seed=151):
    """A wall map of the region, 32 x 16: pale land, a river, roads, a red pin for each outpost."""
    t = Tex((200, 208, 186), seed, 32, 16)
    t.noise(0.04, 0.6)
    t.blob(8, 8, 8, 6, (176, 192, 160), 0.5)
    t.blob(24, 6, 7, 5, (188, 200, 170), 0.5)
    t.line([(0, 3), (6, 6), (12, 9), (18, 8), (24, 12), (31, 11)], (112, 150, 182))     # river
    t.line([(2, 14), (10, 10), (20, 7), (30, 3)], (214, 206, 176))                      # a road
    t.line([(14, 0), (16, 6), (20, 7)], (214, 206, 176))
    for x, y in ((10, 10), (20, 7), (27, 5), (5, 5)):
        t.set(x, y, CRIM)
        t.set(x, y - 1, (230, 100, 100))
    t.frame(0, 0, 31, 15, WOOD_D)
    t.frame(1, 1, 30, 14, WOOD)
    return t


def log_side(seed=160):
    t = Tex((86, 62, 38), seed)
    for x in (2, 6, 10, 13):
        t.vline(x, 0, 15, (66, 46, 28))
    t.noise(0.08, 0.7)
    return t.image()


def log_top(seed=161):
    t = Tex((148, 112, 66), seed)
    t.frame(0, 0, 15, 15, (86, 62, 38))
    t.frame(3, 3, 12, 12, (120, 88, 52))
    t.noise(0.06, 0.7)
    return t.image()


def leaves(seed=162):
    t = Tex((58, 110, 48), seed)
    t.noise(0.2, 0.95)
    t.speckle((40, 84, 36), 40)
    t.speckle((88, 140, 62), 30)
    return t.image()


def build():
    out = {}
    out['log_side'] = log_side()
    out['log_top'] = log_top()
    out['leaves'] = leaves()
    out['wall_concrete'] = wall_concrete()
    out['wall_concrete_base'] = wall_concrete_base()
    out['wall_concrete_top'] = wall_concrete_top()
    out['window_day'] = window_day()
    out['window_lit'] = window_lit()
    out['window_dark'] = window_dark()
    out['door_top'] = door_glass('top')
    out['door_bottom'] = door_glass('bottom')
    out['door_top_lit'] = door_glass('top', True)
    out['door_bottom_lit'] = door_glass('bottom', True)
    out.update(ht.split_strip('sign_main', sign_main(7)))
    out.update(ht.split_strip('sign_branch', sign_branch()))
    out.update(ht.split_strip('banner', banner()))
    out['emblem'] = emblem_tile()
    out['roof'] = roof()
    out['canopy_top'] = ht.canopy('top')
    out['canopy_under'] = canopy_under()
    out['canopy_edge'] = wall_concrete_top(7)
    out['ac_side'] = ac_unit_side()
    out['vending_front'] = vending_front()
    out['lamp_top'] = street_lamp_top()
    out['pole'] = pole()
    out['mat'] = mat()
    out['pavement'] = ht.pavement()
    out['grass'] = ht.grass()
    out['asphalt'] = ht.asphalt()
    out['floor_vinyl'] = floor_vinyl()
    out['floor_carpet'] = floor_carpet()
    out['rug'] = rug()
    out['wall_plaster'] = wall_plaster()
    out['wall_dado'] = wall_dado()
    out['ceiling_panel'] = ceiling_panel()
    out['fluoro'] = fluoro()
    for v in ('plain', 'phone', 'papers', 'stamp', 'bell'):
        out['counter_top_%s' % v] = counter_top(v)
    out['counter_front'] = counter_front()
    out['counter_front_emblem'] = counter_front(True)
    out['desk_front'] = desk_front()
    for v in ('papers', 'phone', 'lamp'):
        out['desk_top_%s' % v] = desk_top(v)
    out['captain_desk_front'] = captain_desk_front()
    for v in ('plain', 'plate', 'papers'):
        out['captain_desk_top_%s' % v] = captain_desk_top(v)
    out['cabinet_front'] = cabinet_front()
    out['cabinet_side'] = cabinet_side()
    out['cabinet_top'] = cabinet_top()
    out['shelf_files'] = shelf_files()
    out['shelf_side'] = shelf_side()
    out['clock'] = clock()
    out['stair_top'] = stair_top()
    out['stair_side'] = stair_side()
    out['bench_top'] = bench_top()
    out['bench_side'] = bench_side()
    out['notice'] = notice_poster()
    out['phone_wall'] = phone_wall()
    out.update(split('board', board_art(), 3, 2))
    out.update(split('map', map_art(), 2, 1))
    return out


# --- the gear: 16 x 16 item sprites ------------------------------------------------------------------------------------

class Sprite:
    def __init__(self):
        self.px = {}

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[(x, y)] = tuple(c[:3]) + (255,)

    def line(self, p0, p1, c):
        n = max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1]), 1)
        for i in range(n + 1):
            self.set(round(p0[0] + (p1[0] - p0[0]) * i / n), round(p0[1] + (p1[1] - p0[1]) * i / n), c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def outline(self, c=(18, 18, 24)):
        """A dark one-pixel edge round the shape (Minecraft items are outlined)."""
        add = {}
        for (x, y) in self.px:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, y + dy)
                if p not in self.px and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                    add[p] = c + (255,)
        self.px.update(add)

    def image(self):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            img.putpixel((x, y), c)
        return img


STEEL = (204, 210, 218)
STEEL_HI = (242, 246, 250)
STEEL_LO = (136, 144, 158)
INK = (22, 22, 30)


def katana():
    """The Public Safety katana, drawn, tip up-right: a plain steel blade with a light edge and a darker spine, a small brass
    guard, a black grip wrapped with pale diamonds, a brass cap."""
    s = Sprite()
    for i in range(10):                                  # the blade from (5, 10) to (14, 1)
        x, y = 5 + i, 10 - i
        s.set(x, y, STEEL)
        s.set(x - 1, y, STEEL_HI)
        s.set(x + 1, y, STEEL_LO)
    s.set(15, 0, STEEL_HI)
    s.set(14, 0, STEEL_HI)
    s.set(4, 11, STEEL_LO)
    s.set(5, 11, STEEL_LO)
    for (x, y) in ((3, 10), (4, 11), (5, 12)):           # the guard
        s.set(x, y, (206, 164, 66))
    s.set(4, 10, (240, 206, 108))
    s.set(5, 11, (138, 98, 40))
    s.set(3, 11, (138, 98, 40))
    for i, (x, y) in enumerate(((3, 12), (2, 13), (1, 14))):   # the grip
        s.set(x, y, (36, 40, 62))
        s.set(x + 1, y, (28, 30, 48))
        s.set(x - 1, y, (28, 30, 48))
        if i % 2 == 0:
            s.set(x, y, (210, 212, 222))
    s.set(0, 15, (206, 164, 66))
    s.set(1, 15, (138, 98, 40))
    s.outline()
    return s.image()


def knives():
    """Two throwing knives, steel blades and black grips with a red cord."""
    s = Sprite()
    for ox, oy in ((0, 0), (4, 3)):
        for i in range(5):
            x, y = 5 + i + ox, 9 - i + oy
            s.set(x, y, STEEL)
            s.set(x - 1, y, STEEL_HI)
            s.set(x + 1, y, STEEL_LO)
        s.set(10 + ox, 4 + oy, STEEL_HI)
        for (x, y) in ((4, 10), (3, 11), (2, 12)):
            s.set(x + ox, y + oy, (30, 30, 36))
        s.set(3 + ox, 11 + oy, (150, 30, 38))
        s.set(1 + ox, 13 + oy, (60, 60, 70))
    s.outline()
    return s.image()


def pistol():
    """A black semi-automatic, barrel to the right: serrated slide, a pale highlight, a ridged grip, the trigger guard."""
    rows = [
        '................',
        '................',
        '..hhhhhhhhhhhh..',
        '.sSSSSSSSSSSSSS.',
        '.SSsSsSsSSSSSSS.',
        '.SSSSSSSSSSSSSS.',
        '.bbbbbbbbbbbbb..',
        '.bgbgbbbbbbgg...',
        '.bbbbb.b.gb.....',
        '.bgbgb.bbb......',
        '.bbbbb..........',
        '.bgbgb..........',
        '.bbbbb..........',
        '................',
        '................',
        '................',
    ]
    pal = {'s': (112, 116, 128), 'S': (58, 62, 72), 'h': (170, 176, 188), 'b': (44, 46, 56), 'g': (24, 24, 30)}
    s = Sprite()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                s.set(x, y, pal[ch])
    s.set(14, 3, (170, 176, 188))
    s.outline()
    return s.image()


def badge():
    """The rank badge: a brass-rimmed octagon with the emblem on navy, a red ribbon over the top."""
    t = Tex((0, 0, 0), 5)
    emblem_pixels(t, 2, 3, 12, ring=(214, 172, 74), field=NAVY, core=CRIM)
    s = Sprite()
    # take the octagon's pixels (anything not the base black)
    for y in range(16):
        for x in range(16):
            c = t.get(x, y)
            if c != (0, 0, 0):
                s.set(x, y, c)
    for x in range(2, 14):                                  # brass highlight on the rim's top
        if (x, 3) in s.px:
            s.set(x, 3, (244, 210, 112))
    s.rect(6, 0, 9, 3, CRIM)                                # the ribbon
    s.rect(7, 0, 8, 3, (190, 40, 48))
    s.set(6, 0, CRIM_D)
    s.set(9, 0, CRIM_D)
    s.outline()
    return s.image()


def yen(value='1K', body=(172, 198, 162), accent=(98, 128, 92)):
    """A made-up note: sage green, a guilloche border, a yen sign and the value."""
    s = Sprite()
    s.rect(0, 3, 15, 12, body)
    for x in range(0, 16):
        s.set(x, 3, accent)
        s.set(x, 12, accent)
    for y in range(3, 13):
        s.set(0, y, accent)
        s.set(15, y, accent)
    for x in range(2, 14, 2):
        s.set(x, 4, shade(body, 1.12))
        s.set(x + 1, 11, shade(body, 1.12))
    ink = shade(accent, 0.45)
    for (x, y) in ((2, 6), (4, 6), (2, 7), (4, 7), (2, 8), (3, 8), (4, 8), (3, 9), (2, 10), (3, 10), (4, 10)):   # a yen sign
        s.set(x, y, ink)
    t = Tex(body, 1, 16, 16)
    text(t, value, 7, 6, ink)
    for y in range(6, 11):
        for x in range(7, 15):
            if t.get(x, y) != body:
                s.set(x, y, t.get(x, y))
    s.outline(shade(accent, 0.3))
    return s.image()


def ration():
    """A devil-blood ration: a dark red retort pouch with crimped ends, a white label with a drop on it."""
    s = Sprite()
    pouch = (92, 14, 20)
    pouch_hi = (150, 26, 34)
    s.rect(4, 3, 11, 14, pouch)
    for y in range(3, 15):
        s.set(5, y, pouch_hi)
        s.set(10, y, shade(pouch, 0.7))
    for x in range(4, 12):                                  # the crimped seals, top and bottom
        s.set(x, 1 + (x % 2), (62, 62, 70))
        s.set(x, 2, (130, 132, 142))
        s.set(x, 14 + (x % 2) - 1 if False else 14, (130, 132, 142))
        s.set(x, 15, (62, 62, 70) if x % 2 else (100, 102, 112))
    s.rect(5, 5, 10, 11, (236, 232, 218))                   # the label
    s.rect(5, 5, 10, 5, (70, 74, 90))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)):  # a drop
        s.set(x, y, CRIM)
    s.set(7, 6, CRIM)
    s.set(8, 6, (236, 232, 218))
    s.set(4, 3, (62, 62, 70))                               # the tear notch
    s.outline()
    return s.image()


def build_items():
    return {
        'katana': katana(),
        'throwing_knives': knives(),
        'pistol': pistol(),
        'rank_badge': badge(),
        'yen_note_1000': yen('1K', (176, 200, 164), (98, 128, 92)),
        'yen_note_5000': yen('5K', (176, 190, 214), (84, 106, 150)),
        'devil_blood_ration': ration(),
    }


# --- sheets -----------------------------------------------------------------------------------------------------------

def contact_sheet(imgs, scale, cols, label_h=12, bg=(34, 34, 38), cell_px=16):
    names = sorted(imgs)
    rows = (len(names) + cols - 1) // cols
    cell = cell_px * scale + 8
    sheet = Image.new('RGB', (cols * cell + 8, rows * (cell + label_h) + 8), bg)
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        x, y = 8 + (i % cols) * cell, 8 + (i // cols) * (cell + label_h)
        im = imgs[n].resize((cell_px * scale, cell_px * scale), Image.NEAREST)
        if im.mode == 'RGBA':
            sheet.paste(im, (x, y), im)
        else:
            sheet.paste(im, (x, y))
        d.text((x, y + cell_px * scale + 1), n[:18], fill=(190, 190, 196))
    return sheet


def main():
    tiles = build()
    items = build_items()
    os.makedirs(os.path.join(OUT, 'textures'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'items'), exist_ok=True)
    for n, img in tiles.items():
        img.save(os.path.join(OUT, 'textures', n + '.png'))
    for n, img in items.items():
        img.save(os.path.join(OUT, 'items', n + '.png'))
    contact_sheet(tiles, 5, 14).save(os.path.join(OUT, 'textures_sheet.png'))
    contact_sheet(items, 10, 4, bg=(70, 70, 76)).save(os.path.join(OUT, 'items_sheet.png'))
    print(len(tiles), 'block textures,', len(items), 'item sprites in', OUT)


if __name__ == '__main__':
    main()
