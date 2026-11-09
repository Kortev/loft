"""Mock-ups of the two Public Safety screens at Minecraft GUI scale, pixel-crisp: the mission board and the rank card.



    python tools/public_safety_screens.py     # writes work/public_safety_look/screen_mission_board.png and screen_rank_card.png



Drawn at the game's own pixel size (1 px = 1 GUI pixel) with the game's own font (assets/minecraft/textures/font/ascii.png, read

from the Minecraft 1.21.1 client jar Loom already cached), then enlarged 3x with nearest-neighbour, the usual GUI scale. The

item sprites come from tools/public_safety_textures.py and the portrait from tools/skins/public_safety/tier_2nd.png.

"""

import io

import os

import sys

import zipfile



HERE = os.path.dirname(os.path.abspath(__file__))

sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

import public_safety_textures as pt  # noqa: E402



OUT = pt.OUT

JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar')

SCALE = 3





def load_font():

    with zipfile.ZipFile(JAR) as z:

        data = z.read('assets/minecraft/textures/font/ascii.png')

    atlas = Image.open(io.BytesIO(data)).convert('RGBA')

    glyphs = {}

    for code in range(256):

        cx, cy = (code % 16) * 8, (code // 16) * 8

        cell = atlas.crop((cx, cy, cx + 8, cy + 8))

        width = 0

        for x in range(8):

            if any(cell.getpixel((x, y))[3] > 0 for y in range(8)):

                width = x + 1

        glyphs[code] = (cell, width if width else 3)     # a space is 4 wide with its gap

    return glyphs





GLYPHS = load_font()


def _yen_glyph():
    rows = ('X...X', '.X.X.', '..X..', 'XXXXX', '..X..', 'XXXXX', '..X..')
    cell = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c == 'X':
                cell.putpixel((x, y), (255, 255, 255, 255))
    return cell, 5


GLYPHS[0x9D] = _yen_glyph()


def code(ch):
    """The game's ascii.png is laid out like code page 437: the yen sign sits at 0x9D."""
    if ch == '¥':
        return 0x9D
    return ord(ch) if ord(ch) < 256 else 63





def text_width(s):

    return sum(GLYPHS[code(ch)][1] + 1 for ch in s) - 1 if s else 0





def draw_text(img, x, y, s, colour=(64, 64, 64), shadow=False, bold=False):

    """The game's font: 8 x 8 cells, a one-pixel gap, an optional drop shadow in a quarter of the colour."""

    def put(ox, oy, col):

        px = x + ox

        for ch in s:

            cell, w = GLYPHS[code(ch)]

            for gy in range(8):

                for gx in range(8):

                    if cell.getpixel((gx, gy))[3] > 0:

                        for bx in ((0, 1) if bold else (0,)):

                            xx, yy = px + gx + bx, y + oy + gy

                            if 0 <= xx < img.width and 0 <= yy < img.height:

                                img.putpixel((xx, yy), col + (255,))

            px += w + 1 + (1 if bold else 0)

    if shadow:

        put(1, 1, tuple(c // 4 for c in colour))

    put(0, 0, colour)

    return text_width(s)





def right_text(img, xr, y, s, colour=(64, 64, 64), shadow=False):

    draw_text(img, xr - text_width(s), y, s, colour, shadow)





def panel(img, x, y, w, h):

    """The container panel: light grey, white top-left bevel, dark bottom-right, black edge with notched corners."""

    d = ImageDraw.Draw(img)

    d.rectangle((x, y, x + w - 1, y + h - 1), fill=(198, 198, 198, 255))

    d.rectangle((x, y, x + w - 1, y + h - 1), outline=(0, 0, 0, 255))

    for cx, cy in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):

        img.putpixel((cx, cy), (0, 0, 0, 0))

    d.line((x + 1, y + 1, x + w - 3, y + 1), fill=(255, 255, 255, 255))

    d.line((x + 1, y + 2, x + w - 4, y + 2), fill=(255, 255, 255, 255))

    d.line((x + 1, y + 1, x + 1, y + h - 3), fill=(255, 255, 255, 255))

    d.line((x + 2, y + 1, x + 2, y + h - 4), fill=(255, 255, 255, 255))

    d.line((x + 2, y + h - 3, x + w - 2, y + h - 3), fill=(85, 85, 85, 255))

    d.line((x + 3, y + h - 2, x + w - 2, y + h - 2), fill=(85, 85, 85, 255))

    d.line((x + w - 3, y + 3, x + w - 3, y + h - 3), fill=(85, 85, 85, 255))

    d.line((x + w - 2, y + 3, x + w - 2, y + h - 2), fill=(85, 85, 85, 255))





def inset(img, x, y, w, h, fill=(139, 139, 139)):

    d = ImageDraw.Draw(img)

    d.rectangle((x, y, x + w - 1, y + h - 1), fill=fill + (255,))

    d.line((x, y, x + w - 1, y), fill=(55, 55, 55, 255))

    d.line((x, y, x, y + h - 1), fill=(55, 55, 55, 255))

    d.line((x + 1, y + h - 1, x + w - 1, y + h - 1), fill=(255, 255, 255, 255))

    d.line((x + w - 1, y + 1, x + w - 1, y + h - 1), fill=(255, 255, 255, 255))





def button(img, x, y, w, h, label, hover=False, disabled=False):

    d = ImageDraw.Draw(img)

    base = (160, 160, 160) if hover else (112, 112, 112)

    if disabled:

        base = (80, 80, 80)

    d.rectangle((x, y, x + w - 1, y + h - 1), fill=base + (255,))

    d.rectangle((x, y, x + w - 1, y + h - 1), outline=(0, 0, 0, 255))

    d.line((x + 1, y + 1, x + w - 2, y + 1), fill=(208, 208, 208, 255) if not disabled else (120, 120, 120, 255))

    d.line((x + 1, y + 1, x + 1, y + h - 2), fill=(208, 208, 208, 255) if not disabled else (120, 120, 120, 255))

    d.line((x + 1, y + h - 2, x + w - 2, y + h - 2), fill=(58, 58, 58, 255))

    d.line((x + w - 2, y + 2, x + w - 2, y + h - 2), fill=(58, 58, 58, 255))

    col = (255, 255, 160) if hover else (224, 224, 224)

    if disabled:

        col = (160, 160, 160)

    tw = text_width(label)

    draw_text(img, x + (w - tw) // 2, y + (h - 8) // 2 + 1, label, col, shadow=True)





def sprite(img, name, x, y, scale=1):

    s = Image.open(os.path.join(OUT, 'items', name + '.png')).convert('RGBA')

    if scale != 1:

        s = s.resize((16 * scale, 16 * scale), Image.NEAREST)

    img.alpha_composite(s, (x, y))





def lock(img, x, y, col=(90, 90, 98)):

    d = ImageDraw.Draw(img)

    d.rectangle((x + 1, y, x + 5, y + 3), outline=col + (255,))

    d.rectangle((x, y + 3, x + 6, y + 8), fill=col + (255,))

    img.putpixel((x + 3, y + 5), (200, 200, 205, 255))





def paper(img, x, y, w, h, band, band_text, tint=(236, 232, 218), selected=False, locked=False, text_col=(60, 56, 50)):

    d = ImageDraw.Draw(img)

    if locked:

        tint = (176, 174, 168)

        band = (110, 110, 116)

    d.rectangle((x + 1, y + 1, x + w, y + h), fill=(88, 64, 40, 255))        # a shadow on the cork

    d.rectangle((x, y, x + w - 1, y + h - 1), fill=tint + (255,))

    d.rectangle((x, y, x + w - 1, y + 9), fill=band + (255,))

    draw_text(img, x + 3, y + 1, band_text, (255, 255, 255), shadow=False)

    if selected:

        d.rectangle((x - 2, y - 2, x + w + 1, y + h + 1), outline=(255, 255, 255, 255))

        d.rectangle((x - 1, y - 1, x + w, y + h), outline=(250, 200, 60, 255))

    return text_col





def cork(img, x, y, w, h):

    import random

    r = random.Random(7)

    d = ImageDraw.Draw(img)

    d.rectangle((x, y, x + w - 1, y + h - 1), fill=(172, 128, 82, 255))

    for _ in range(w * h // 5):

        px, py = x + r.randrange(w), y + r.randrange(h)

        img.putpixel((px, py), r.choice([(148, 106, 66, 255), (194, 150, 100, 255), (160, 116, 72, 255)]))

    d.rectangle((x, y, x + w - 1, y + h - 1), outline=(78, 50, 32, 255))

    d.rectangle((x + 1, y + 1, x + w - 2, y + h - 2), outline=(112, 74, 46, 255))





def pin(img, x, y, col=(190, 40, 40)):

    img.putpixel((x, y), col + (255,))

    img.putpixel((x + 1, y), tuple(min(255, c + 60) for c in col) + (255,))





INK = (60, 56, 50)

DIMINK = (118, 112, 104)

CRIM = (156, 30, 38)

NAVY = (34, 42, 64)





# --- the mission board --------------------------------------------------------------------------------------------------



MISSIONS = [

    # band, title, place, rank, yen, proof, party, lines

    (CRIM, 'DEVIL SIGHTED', 'near Oakridge', '4th Class', '8,000', "Devil's horn", '1-4', ['A devil was seen at', 'the village edge.', 'It took two goats.']),

    (NAVY, 'ESCORT', 'to Pinehaven', '4th Class', '5,500', 'Safe arrival', '1-2', ['A clerk must reach the', 'next outpost before', 'dark.']),

    (CRIM, 'CLEAR A NEST', 'old quarry', '3rd Class', '21,000', 'Three eyes', '2-4', ['Bats nest in the quarry', 'and the road is closed.']),

    ((70, 70, 78), 'MISSING SQUAD', 'a hotel', '2nd Class', '60,000', 'Survivors', '2-4', ['A squad went in and', 'never came out.']),

]





def mission_board():

    W, H = 256, 178

    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))

    panel(img, 0, 0, W, H)

    draw_text(img, 8, 8, 'Public Safety  -  Mission Board', (64, 64, 64))

    # rank and yen top right

    sprite(img, 'rank_badge', W - 24, 3)

    right_text(img, W - 27, 6, '3rd Class', (64, 64, 64))

    right_text(img, W - 27, 16, '¥ 18,400', (80, 100, 60))

    # the left: the sheets pinned on cork

    cork(img, 8, 28, 118, 142)

    sel = 0

    ys = [33, 66, 99, 132]

    for i, m in enumerate(MISSIONS):

        band, title, place, rank, yen, proof, party, lines = m

        locked = i == 3

        tc = paper(img, 14, ys[i], 106, 30, band, title, selected=(i == sel), locked=locked)

        draw_text(img, 17, ys[i] + 12, place, tc if not locked else DIMINK)

        draw_text(img, 17, ys[i] + 21, '¥ ' + yen, (96, 36, 36) if not locked else DIMINK)

        right_text(img, 117, ys[i] + 21, rank, tc if not locked else DIMINK)

        pin(img, 14 + 52, ys[i], (190, 40, 40) if i % 2 == 0 else (50, 90, 190))

        if locked:

            lock(img, 108, ys[i] + 11)

    # the right: the selected sheet in full

    band, title, place, rank, yen, proof, party, lines = MISSIONS[sel]

    inset(img, 132, 28, 116, 142, fill=(126, 126, 126))

    paper(img, 137, 33, 106, 108, band, title)

    d = ImageDraw.Draw(img)

    draw_text(img, 141, 46, place, INK, bold=True)

    for k, ln in enumerate(lines):

        draw_text(img, 141, 58 + k * 9, ln, (84, 80, 72))

    d.line((141, 86, 238, 86), fill=(176, 172, 160, 255))

    draw_text(img, 141, 90, 'Rank', DIMINK)

    right_text(img, 238, 90, rank, INK)

    draw_text(img, 141, 100, 'Reward', DIMINK)

    right_text(img, 238, 100, '¥ ' + yen, (150, 40, 40))

    draw_text(img, 141, 110, 'Proof', DIMINK)

    right_text(img, 238, 110, proof, INK)

    draw_text(img, 141, 120, 'Hunters', DIMINK)

    right_text(img, 238, 120, party, INK)

    draw_text(img, 141, 131, 'Marked on your map', (74, 100, 150))

    pin(img, 188, 33, (190, 40, 40))

    # stamp

    button(img, 137, 146, 70, 20, 'Take mission', hover=True)

    button(img, 211, 146, 32, 20, 'Close')

    return img





# --- the rank card -----------------------------------------------------------------------------------------------------



def head_portrait(scale=4):

    sk = Image.open(os.path.join(HERE, 'skins', 'public_safety', 'tier_2nd.png')).convert('RGBA')

    head = sk.crop((8, 8, 16, 16))

    hat = sk.crop((40, 8, 48, 16))

    head.alpha_composite(hat)

    return head.resize((8 * scale, 8 * scale), Image.NEAREST)





def rank_card():

    W, H = 232, 184

    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))

    panel(img, 0, 0, W, H)

    d = ImageDraw.Draw(img)

    # the card: pale stock with a navy band

    d.rectangle((8, 8, W - 9, H - 9), fill=(232, 228, 212, 255))

    d.rectangle((8, 8, W - 9, H - 9), outline=(40, 44, 58, 255))

    d.rectangle((9, 9, W - 10, 30), fill=NAVY + (255,))

    # the emblem: the badge sprite on the band, the title

    sprite(img, 'rank_badge', 13, 10)

    draw_text(img, 34, 12, 'PUBLIC SAFETY', (255, 255, 255), shadow=True)

    draw_text(img, 34, 21, 'Devil Hunter Licence', (190, 196, 210))

    d.line((9, 31, W - 10, 31), fill=(156, 30, 38, 255))

    # the photo

    d.rectangle((14, 38, 14 + 38, 38 + 38), fill=(120, 140, 160, 255), outline=(40, 44, 58, 255))

    img.alpha_composite(head_portrait(4), (17, 41))

    draw_text(img, 14, 80, 'No. 0207-1124', DIMINK)

    # name and rank

    draw_text(img, 62, 38, 'Hunter', DIMINK)

    draw_text(img, 62, 47, 'Kortev', INK, bold=True)

    draw_text(img, 62, 60, 'Rank', DIMINK)

    draw_text(img, 62, 69, '2nd Class', (150, 40, 40), bold=True)

    sprite(img, 'rank_badge', W - 34, 40, 1)

    sprite(img, 'rank_badge', W - 50, 56, 1) if False else None

    # next rank

    draw_text(img, 14, 92, 'To 1st Class', DIMINK)

    right_text(img, W - 14, 92, '4 / 10 missions', INK)

    d.rectangle((14, 102, W - 15, 107), fill=(40, 40, 40, 255))

    d.rectangle((15, 103, 15 + int((W - 31) * 0.4), 106), fill=(110, 200, 60, 255))

    d.line((15, 103, 15 + int((W - 31) * 0.4), 103), fill=(170, 232, 120, 255))

    # power resistance, stacked

    draw_text(img, 14, 114, 'Power resistance', DIMINK)

    right_text(img, W - 14, 114, '34%', (40, 80, 150))

    bar_x, bar_w = 14, W - 28

    d.rectangle((bar_x, 124, bar_x + bar_w - 1, 131), fill=(40, 40, 40, 255))

    unit = (bar_w - 2) / 60.0                       # the bar runs to 60 %

    x = bar_x + 1

    for pct, col in ((18, NAVY_L := (70, 90, 150)), (10, (176, 50, 58)), (6, (186, 190, 200))):

        d.rectangle((x, 125, x + int(pct * unit) - 1, 130), fill=col + (255,))

        x += int(pct * unit)

    # the legend

    lx = 14

    for label, col in (('Rank +18', (70, 90, 150)), ('Contracts +10', (176, 50, 58)), ('Suit +6', (186, 190, 200))):

        d.rectangle((lx, 135, lx + 4, 139), fill=col + (255,), outline=(40, 40, 40, 255))

        draw_text(img, lx + 7, 134, label, (84, 80, 72))

        lx += 7 + text_width(label) + 8

    # the stats

    d.line((14, 147, W - 15, 147), fill=(190, 184, 166, 255))

    stats = [('Missions done', '23'), ('Devils slain', '41'), ('Yen', '¥ 48,200')]

    sx = 14

    for label, val in stats:

        draw_text(img, sx, 153, label, DIMINK)

        draw_text(img, sx, 164, val, INK, bold=True)

        sx += 76

    return img





def save(img, name):

    big = img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)

    bg = Image.new('RGBA', big.size, (46, 46, 52, 255))

    bg.alpha_composite(big)

    p = os.path.join(OUT, name)

    bg.convert('RGB').save(p)

    print('wrote', p, bg.size)





def main():

    os.makedirs(OUT, exist_ok=True)

    save(mission_board(), 'screen_mission_board.png')

    save(rank_card(), 'screen_rank_card.png')





if __name__ == '__main__':

    main()

