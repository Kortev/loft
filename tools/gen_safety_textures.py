"""Textures for Public Safety (chainsawman/, the safety/ lane), drawn pixel by pixel: the notice board's poster, the green public
phone, and the hunter's licence.

Run from the repository root: py tools/gen_safety_textures.py [--preview DIR]

- the poster (192x192, drawn across a block and three quarters): old white paper, a red header with DEVIL HUNTERS / WANTED in
  white, PUBLIC SAFETY in heavy black print under it, lines of small print, a stamp, pin heads at the corners;
- the phone: a 1990s Japanese public phone, light green, a little black display, a grey keypad, the coin slot, the handset
  hanging on its left side;
- the licence: a black card with a white face square and a red line.
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join('chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'textures')

# A 5x7 capital font, enough for the poster's words.
FONT = {
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'B': ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'],
    'C': ['.###.', '#...#', '#....', '#....', '#....', '#...#', '.###.'],
    'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'F': ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
    'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'I': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
    'L': ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
    'N': ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
    'P': ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
    'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
    'U': ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'V': ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
    'W': ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '##.##', '#...#'],
    'Y': ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'],
    ' ': ['.....'] * 7,
}


def text_width(s, scale, gap=1):
    return len(s) * (5 + gap) * scale - gap * scale


def draw_text(img, s, cx, y, scale, color, bold=False, gap=1):
    """{s} centred on {cx}, its top at {y}, each font pixel {scale} wide; bold thickens each stroke one pixel to the right."""
    x0 = cx - text_width(s, scale, gap) // 2
    for i, ch in enumerate(s):
        g = FONT[ch]
        gx = x0 + i * (5 + gap) * scale
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#':
                    for dy in range(scale):
                        for dx in range(scale + (1 if bold else 0)):
                            px, py = gx + c * scale + dx, y + r * scale + dy
                            if 0 <= px < img.width and 0 <= py < img.height:
                                img.putpixel((px, py), color)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def poster():
    rnd = random.Random(1997)
    n = 192
    img = Image.new('RGBA', (n, n))
    paper = (238, 233, 220)
    for y in range(n):
        for x in range(n):
            # Old paper: a little grain, darker toward the edges, a faint fold across the middle.
            edge = min(x, y, n - 1 - x, n - 1 - y)
            k = 1.0 - max(0, 10 - edge) * 0.012 + rnd.uniform(-0.025, 0.025)
            if abs(y - n // 2) < 1:
                k -= 0.05
            img.putpixel((x, y), shade(paper, k))
    red = (196, 28, 24)
    for y in range(8, 82):
        for x in range(8, n - 8):
            img.putpixel((x, y), shade(red, 1.0 + rnd.uniform(-0.04, 0.04)))
    white = (250, 248, 242, 255)
    draw_text(img, 'DEVIL HUNTERS', n // 2, 16, 2, white, bold=True)
    draw_text(img, 'WANTED', n // 2, 40, 4, white, bold=True)
    black = (20, 18, 18, 255)
    draw_text(img, 'PUBLIC', n // 2, 92, 3, black, bold=True)
    draw_text(img, 'SAFETY', n // 2, 120, 3, black, bold=True)
    for x in range(20, n - 20):
        img.putpixel((x, 146), black)
        img.putpixel((x, 147), black)
    # Small print: rows of short dark words.
    for row in range(4):
        y = 154 + row * 6
        x = 22 if row else 34
        while x < n - 24:
            w = rnd.randint(4, 12)
            for dx in range(min(w, n - 24 - x)):
                img.putpixel((x + dx, y), (70, 66, 64, 255))
                if rnd.random() < 0.5:
                    img.putpixel((x + dx, y + 1), (110, 104, 100, 255))
            x += w + 3
    # A red stamp in the corner, a ring with a bar.
    cx, cy, r = n - 30, 160, 11
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r - 1, cx + r + 2):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r - 2 <= d <= r or d < r - 3 and abs(y - cy) <= 1:
                if rnd.random() < 0.85:
                    img.putpixel((x, y), (176, 30, 28, 255))
    # Pin heads at the corners.
    for px, py in ((5, 5), (n - 6, 5), (5, n - 6), (n - 6, n - 6)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 4:
                    img.putpixel((px + dx, py + dy), (60, 60, 64, 255) if dx + dy > 0 else (150, 150, 158, 255))
    return img


GREEN = (118, 178, 96)
DARK_GREEN = (70, 124, 58)


def phone_front():
    """32x32 on the body's 10x14 face: (seen from the front) the handset's cradle on the left, the display, keypad and coin slot."""
    rnd = random.Random(7)
    img = Image.new('RGBA', (32, 32))
    for y in range(32):
        for x in range(32):
            k = 1.0 + rnd.uniform(-0.03, 0.03) - (0.12 if x in (0, 31) or y in (0, 31) else 0.0)
            img.putpixel((x, y), shade(GREEN, k))
    # The cradle's recess on the left, where the handset hangs.
    for y in range(3, 29):
        for x in range(1, 9):
            img.putpixel((x, y), shade(DARK_GREEN, 1.0 + rnd.uniform(-0.03, 0.03)))
    # The display: black glass with a dim green number.
    for y in range(3, 9):
        for x in range(11, 29):
            img.putpixel((x, y), (16, 22, 18, 255))
    for x in range(20, 27):
        img.putpixel((x, 5), (90, 150, 80, 255))
        img.putpixel((x, 6), (60, 110, 60, 255))
    # The coin slot and the card slot.
    for x in range(14, 26):
        img.putpixel((x, 11), (30, 30, 30, 255))
    for x in range(12, 28):
        img.putpixel((x, 13), (200, 200, 196, 255))
    # The keypad: twelve grey keys.
    for row in range(4):
        for col in range(3):
            kx, ky = 13 + col * 5, 16 + row * 3
            for dy in range(2):
                for dx in range(4):
                    img.putpixel((kx + dx, ky + dy), (214, 214, 210, 255) if dy == 0 else (160, 160, 158, 255))
    # The coin return at the bottom.
    for y in range(28, 31):
        for x in range(16, 24):
            img.putpixel((x, y), (40, 50, 40, 255))
    return img


def phone_side():
    rnd = random.Random(8)
    img = Image.new('RGBA', (32, 32))
    for y in range(32):
        for x in range(32):
            img.putpixel((x, y), shade(GREEN, 0.92 + rnd.uniform(-0.03, 0.03)))
    return img


def phone_handset():
    """The handset: green, an earpiece at the top and a mouthpiece at the bottom (front, then sides, then ends)."""
    rnd = random.Random(9)
    img = Image.new('RGBA', (32, 32))
    for y in range(32):
        for x in range(32):
            img.putpixel((x, y), shade(GREEN, 1.06 + rnd.uniform(-0.03, 0.03)))
    for y in list(range(1, 6)) + list(range(26, 31)):
        for x in range(1, 11):
            img.putpixel((x, y), shade(DARK_GREEN, 0.85))
    for y in range(8, 24):
        for x in range(3, 9):
            img.putpixel((x, y), shade(GREEN, 0.95))
    return img


def phone_plate():
    rnd = random.Random(10)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade((96, 98, 100), 1.0 + rnd.uniform(-0.05, 0.05)))
    for px, py in ((3, 1), (12, 1), (3, 14), (12, 14)):
        img.putpixel((px, py), (170, 170, 176, 255))
    return img


def licence():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(1, 15):
            img.putpixel((x, y), (20, 20, 20, 255))
    for x in range(1, 15):
        img.putpixel((x, 3), (210, 210, 210, 255))
        img.putpixel((x, 12), (70, 70, 70, 255))
    for y in range(5, 11):
        for x in range(2, 7):
            img.putpixel((x, y), (236, 232, 224, 255))
    img.putpixel((4, 7), (200, 150, 120, 255))
    for x in range(8, 14):
        img.putpixel((x, 6), (208, 32, 26, 255))
    for x in range(8, 13):
        img.putpixel((x, 8), (200, 200, 200, 255))
    for x in range(8, 12):
        img.putpixel((x, 10), (140, 140, 140, 255))
    return img


def main():
    preview = None
    if '--preview' in sys.argv:
        preview = sys.argv[sys.argv.index('--preview') + 1]
    out = {
        'block/poster': poster(),
        'block/phone_front': phone_front(),
        'block/phone_side': phone_side(),
        'block/phone_handset': phone_handset(),
        'block/phone_plate': phone_plate(),
        'item/licence': licence(),
    }
    for name, img in out.items():
        path = os.path.join(ROOT, name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        print('wrote', path)
        if preview:
            os.makedirs(preview, exist_ok=True)
            k = max(1, 512 // img.width)
            img.resize((img.width * k, img.height * k), Image.NEAREST).save(os.path.join(preview, name.replace('/', '_') + '.png'))


if __name__ == '__main__':
    main()
