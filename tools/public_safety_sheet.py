"""Puts the Public Safety look (tools/public_safety_scenes.py, tools/public_safety_textures.py, tools/public_safety_screens.py) on
one sheet for kortev's phone.

    python tools/public_safety_sheet.py     # reads work/public_safety_look/, writes look_sheet.png and _look_sheet_small.png
"""
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, '..', 'work', 'public_safety_look'))

W = 2400
M = 24
G = 18
BG = (24, 24, 28)
FG = (236, 232, 224)
DIM = (172, 168, 160)

ROWS = [
    ('The outpost: by day and by night', [('shot_outside_day', 'by day'), ('shot_outside_night', 'by night: the windows and the sign lit')]),
    ('Inside: the front desk and the mission board', [('shot_front_desk', 'the front desk'), ('shot_board', 'the mission board, with hunters reading it')]),
    ('The captain and the staff', [('shot_office', "the captain's office, the captain at his desk"), ('shot_cast', 'captain, two clerks, a hunter on watch')]),
    ('The suit tiers: 4th Class, 2nd Class, Special Division', [('shot_tiers_front', 'front'), ('shot_tiers_back', 'back')]),
    ('The gear, in hand and on the table', [('shot_gear_hands', 'katana, throwing knives, pistol'), ('shot_gear_display', 'katana, knives, pistol, rank badge, 1K and 5K yen notes, devil-blood ration')]),
    ('The two screens at GUI scale 3', [('screen_mission_board', 'the mission board'), ('screen_rank_card', 'the rank card')]),
    ('The sprites and the block textures', [('items_sheet', '16x16 item sprites'), ('textures_sheet', 'the outpost block textures')]),
]


def font(size):
    for f in ('C:/Windows/Fonts/arialbd.ttf', 'C:/Windows/Fonts/arial.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def load(name):
    for n in (name, 'shot_' + name):
        p = os.path.join(OUT, n + '.png')
        if os.path.exists(p):
            return Image.open(p).convert('RGB')
    raise FileNotFoundError(name)


def main():
    f1, f2 = font(34), font(24)
    parts = []
    y = M
    for title, cells in ROWS:
        parts.append(('title', M, y, title))
        y += 46
        cw = (W - 2 * M - G * (len(cells) - 1)) // len(cells)
        imgs = []
        for name, cap in cells:
            im = load(name)
            im = im.resize((cw, round(im.height * cw / im.width)), Image.NEAREST if name.startswith('screen') or name in ('items_sheet', 'textures_sheet') else Image.LANCZOS)
            imgs.append((im, cap))
        h = max(i.height for i, _ in imgs)
        x = M
        for im, cap in imgs:
            parts.append(('img', x, y, im))
            parts.append(('cap', x + 8, y + im.height + 4, cap))
            x += cw + G
        y += h + 44
    sheet = Image.new('RGB', (W, y + M), BG)
    d = ImageDraw.Draw(sheet)
    for kind, x, yy, v in parts:
        if kind == 'img':
            sheet.paste(v, (x, yy))
        elif kind == 'title':
            d.text((x, yy), v, font=f1, fill=FG)
        else:
            d.text((x, yy), v, font=f2, fill=DIM)
    sheet.save(os.path.join(OUT, 'look_sheet.png'))
    small = sheet.resize((1200, round(sheet.height * 1200 / sheet.width)), Image.LANCZOS)
    small.save(os.path.join(OUT, '_look_sheet_small.png'))
    print(sheet.size, small.size)


if __name__ == '__main__':
    main()
