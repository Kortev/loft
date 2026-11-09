"""Puts the Eternity look renders (tools/eternity_look_scenes.py) on one sheet for kortev's phone.

    python tools/eternity_look_sheet.py        # reads work/eternity_look_v2/shot_*.png, writes look_sheet.png and _look_sheet_small.png
"""
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, '..', 'work', 'eternity_look_v2'))

W = 2400
M = 20
G = 14
BG = (22, 20, 24)
FG = (232, 226, 218)
DIM = (170, 164, 158)


def font(size):
    for f in ('C:/Windows/Fonts/arialbd.ttf', 'C:/Windows/Fonts/arial.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def shot(name):
    return Image.open(os.path.join(OUT, 'shot_%s.png' % name)).convert('RGB')


def fit(img, w, h=None):
    if h is None:
        h = round(img.height * w / img.width)
    return img.resize((w, h), Image.LANCZOS)


def main():
    parts = []     # (x, y, image, caption)
    y = M
    f1, f2 = font(30), font(22)

    def cap(img, x, y, text):
        parts.append((x, y, img, text))
        return y + img.height + 40

    # row 1: the key image and the close-ups on the left, the devil on its own on the right
    lw = 1360
    rw = W - 2 * M - lw - G
    key = fit(shot('key'), lw)
    y1 = cap(key, M, y, '1. THE EYE-LEVEL VIEW: the devil in the floor-8 corridor, 6 blocks away, eye height 1.6')
    mouth = fit(shot('mouth_eyes'), (lw - G) // 2)
    heart = fit(shot('heart'), (lw - G) // 2)
    cap(mouth, M, y1 + G, '3. close on the big mouth and a cluster of eyes')
    y1b = cap(heart, M + mouth.width + G, y1 + G, '4. the heart, the flesh over it cut away')
    fr = fit(shot('devil_front'), rw)
    y2 = cap(fr, M + lw + G, y, '2a. front-on, a 2-block player for scale')
    th = fit(shot('devil_three'), rw)
    y2 = cap(th, M + lw + G, y2 + G, '2b. three-quarter (6 wide x 4 high x 10 deep)')
    y = max(y1b, y2) + G

    # row 2 and 3: the corridor through the rot
    cw = (W - 2 * M - 2 * G) // 3
    row = [('corridor_f1', '5. floor 1 (clean, lit)'), ('corridor_f4', '5. floor 4 (stained, dim)'), ('corridor_f6', '5. floor 6 (peeling, damp, a lamp flickering)')]
    yy = y
    for i, (n, c) in enumerate(row):
        im = fit(shot(n), cw)
        yy2 = cap(im, M + i * (cw + G), y, c)
    y = yy2 + G
    row = [('corridor_f7', '5. floor 7 (veins of flesh under the wallpaper)'), ('corridor_f8', '5. floor 8 (clean again, only wrong)'), ('lobby', '6. the lobby: desk, bell, key board, sign, lift')]
    for i, (n, c) in enumerate(row):
        im = fit(shot(n), cw)
        yy2 = cap(im, M + i * (cw + G), y, c)
    y = yy2 + G

    # row 4: the outside and the hunt
    ow = 560
    out = fit(shot('outside'), ow)
    cap(out, M, y, '7. outside, at night')
    hw = (W - 2 * M - ow - 4 * G) // 3
    x = M + ow + G
    hy = y
    for n, c in (('hunt_eye', '8. an eye opening in the wallpaper'), ('hunt_bleed', '8. a wall that bleeds when cut'), ('hunt_breathe', '8. a wall that breathes')):
        im = fit(shot(n), hw)
        hy2 = cap(im, x, y, c)
        x += hw + G
    H = max(y + out.height + 40, hy2) + M
    sheet = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(sheet)
    for x, y, im, c in parts:
        d.text((x, y), c, font=f2, fill=FG)
        sheet.paste(im, (x, y + 30))
    sheet.save(os.path.join(OUT, 'look_sheet.png'))
    small = fit(sheet, 1200)
    small.save(os.path.join(OUT, '_look_sheet_small.png'))
    print(sheet.size, small.size)


if __name__ == '__main__':
    main()
