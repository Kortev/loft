"""The grown Tomato Devil's veins (TomatoGrowthLook): a 64x64 texture of dark veins branching out from the middle, laid over the skin
round its oldest seams from growth 3 on. Drawn at 4x and shrunk, so the lines are soft-edged; the alpha fades out toward the edges so the
decal has no visible border.

    python tools/tomato_veins.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '..', 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'textures',
                   'entity', 'tomato_veins.png')
SIZE = 64
K = 4


def main():
    rnd = random.Random(9)
    big = SIZE * K
    alpha = Image.new('L', (big, big), 0)
    d = ImageDraw.Draw(alpha)

    def vein(x, y, ang, width, length):
        while length > 0 and width > 0.6:
            step = rnd.uniform(5, 9) * K / 2
            ang += rnd.uniform(-0.45, 0.45)
            nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
            d.line([(x, y), (nx, ny)], fill=255, width=max(1, int(width * K)))
            d.ellipse([nx - width * K / 2, ny - width * K / 2, nx + width * K / 2, ny + width * K / 2], fill=255)
            x, y = nx, ny
            length -= step
            width *= 0.93
            if rnd.random() < 0.22:
                vein(x, y, ang + rnd.choice((-1, 1)) * rnd.uniform(0.5, 1.0), width * 0.7, length * 0.6)

    c = big / 2
    roots = 6
    for i in range(roots):
        a = i * 2 * math.pi / roots + rnd.uniform(-0.3, 0.3)
        vein(c + rnd.uniform(-6, 6), c + rnd.uniform(-6, 6), a, 3.2, big * 0.55)
    alpha = alpha.filter(ImageFilter.GaussianBlur(K * 0.6))
    # fade toward the edges (a round decal)
    px = alpha.load()
    for y in range(big):
        for x in range(big):
            r = math.hypot(x - c, y - c) / (big / 2)
            f = max(0.0, min(1.0, (1.0 - r) / 0.3))
            px[x, y] = int(px[x, y] * f)
    alpha = alpha.resize((SIZE, SIZE), Image.LANCZOS)
    img = Image.new('RGBA', (SIZE, SIZE))
    ap = alpha.load()
    ip = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            a = ap[x, y]
            # dark at the core of a vein, a bruised purple-red at its edges
            k = a / 255.0
            ip[x, y] = (int(70 - 40 * k), int(8 - 4 * k), int(30 - 6 * k), min(255, int(a * 1.15)))
    img.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
