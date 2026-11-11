"""The Eternity hotel's corridor and stairwell blocks (Chainsaw Man mod, package 0.1g), all at 16 pixels to the block:

- the stairwell's spine wall with its handrails (model only: vanilla sandstone and dark oak),
- the floor sign on each landing (1 to 8), the lit exit sign over a stair door,
- the wall clock (its face: the hands are drawn by the client),
- the wall torn open as it bleeds toward the devil: ripped wallpaper, the lath under it, flesh pushing through,
- a white pixel for the client's pixel-by-pixel drawing (the eyes in the wallpaper, the flesh lumps, the clock hands).

Writes textures, block models and blockstates into chainsawman/src/main/resources/assets/chainsawman.
Run: python tools/gen_hotel_corridor.py
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman')
TEX = os.path.join(ASSETS, 'textures', 'block')
ETEX = os.path.join(ASSETS, 'textures', 'entity')
MODELS = os.path.join(ASSETS, 'models', 'block')
STATES = os.path.join(ASSETS, 'blockstates')

# The corridor's wallpaper is vanilla smooth sandstone: its palette, sampled from the game's own texture.
PAPER = [(227, 219, 176), (223, 212, 167), (218, 207, 163), (213, 196, 150)]
PAPER_SHADE = (198, 174, 113)
LATH = [(122, 86, 50), (106, 74, 42), (94, 64, 36)]
LATH_GAP = (40, 26, 16)
FLESH = [(140, 30, 36), (164, 46, 46), (184, 64, 58), (120, 24, 32)]
FLESH_LIGHT = (222, 120, 108)
FLESH_WET = (246, 196, 184)
FLESH_DARK = (78, 14, 22)
VEIN = (92, 22, 52)


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + '.png'))


def write(folder, name, data):
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name + '.json'), 'w', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def paper(rng):
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), PAPER[rng.choice([0, 0, 1, 1, 2, 3])] + (255,))
    return img


# ---- The torn wall.

def tear_mask(rng, holes, rmin, rmax):
    """Pixels torn open: a few jagged holes."""
    torn = [[False] * 16 for _ in range(16)]
    for _ in range(holes):
        cx, cy = rng.uniform(2, 13), rng.uniform(2, 13)
        r = rng.uniform(rmin, rmax)
        for y in range(16):
            for x in range(16):
                d = ((x + 0.5 - cx) ** 2 * 0.7 + (y + 0.5 - cy) ** 2 * 1.3) ** 0.5
                if d < r + rng.uniform(-0.9, 0.9):
                    torn[y][x] = True
    return torn


def lath_pixel(x, y, rng):
    # Laths: horizontal strips three pixels tall with a dark gap between them.
    if y % 4 == 3:
        return LATH_GAP
    return LATH[(x // 5 + y // 4) % 3] if rng.random() > 0.15 else LATH[2]


def torn_paper(seed):
    rng = random.Random(seed)
    img = paper(rng)
    torn = tear_mask(rng, 3, 1.6, 3.2)
    for y in range(16):
        for x in range(16):
            if torn[y][x]:
                img.putpixel((x, y), lath_pixel(x, y, rng) + (255,))
    # The ripped edge: the paper's torn lip catches the light above a hole and casts a shadow below it.
    for y in range(16):
        for x in range(16):
            if not torn[y][x]:
                below = y + 1 < 16 and torn[y + 1][x]
                above = y > 0 and torn[y - 1][x]
                side = (x + 1 < 16 and torn[y][x + 1]) or (x > 0 and torn[y][x - 1])
                if below:
                    img.putpixel((x, y), (240, 234, 204, 255))
                elif above or side:
                    img.putpixel((x, y), PAPER_SHADE + (255,))
    # A stain running down from the worst of it.
    for x in range(16):
        if rng.random() < 0.18:
            for y in range(rng.randint(8, 13), 16):
                if not torn[y][x]:
                    img.putpixel((x, y), (168, 112, 86, 255))
    return img


def torn_lath(seed):
    rng = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), lath_pixel(x, y, rng) + (255,))
    # Flesh bulging out between the laths, wet on top.
    for y in range(16):
        for x in range(16):
            if y % 4 == 3 and rng.random() < 0.75:
                img.putpixel((x, y), FLESH[rng.randrange(3)] + (255,))
                if y > 0 and rng.random() < 0.5:
                    img.putpixel((x, y - 1), FLESH[1] + (255,))
    blob = tear_mask(rng, 2, 1.8, 3.0)
    for y in range(16):
        for x in range(16):
            if blob[y][x]:
                img.putpixel((x, y), FLESH[rng.randrange(4)] + (255,))
    for y in range(1, 16):
        for x in range(16):
            if blob[y][x] and not blob[y - 1][x]:
                img.putpixel((x, y), FLESH_LIGHT + (255,))
                if rng.random() < 0.3:
                    img.putpixel((x, y), FLESH_WET + (255,))
    # Scraps of wallpaper still stuck at the edges.
    for x in range(16):
        for y in (0, 15):
            if rng.random() < 0.45:
                img.putpixel((x, y), PAPER[rng.randrange(3)] + (255,))
    return img


def wall_flesh(seed):
    rng = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    height = [[rng.random() for _ in range(16)] for _ in range(16)]
    # Smooth the noise a little into lobes.
    for _ in range(2):
        height = [[(height[y][x] * 2 + height[y][(x + 1) % 16] + height[y][(x - 1) % 16] + height[(y + 1) % 16][x] + height[(y - 1) % 16][x]) / 6.0
                   for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            h = height[y][x]
            up = height[(y - 1) % 16][x]
            c = FLESH[0] if h < 0.42 else FLESH[1] if h < 0.5 else FLESH[2]
            if h < 0.36:
                c = FLESH_DARK
            if h > up + 0.05:
                c = FLESH_LIGHT
            if h > up + 0.09 and rng.random() < 0.5:
                c = FLESH_WET
            img.putpixel((x, y), c + (255,))
    # A vein or two.
    for _ in range(2):
        x, y = rng.randrange(16), 0
        while y < 16:
            img.putpixel((x % 16, y), VEIN + (255,))
            y += 1
            x += rng.choice([-1, 0, 0, 1])
    return img


# ---- Signs and the clock.

DIGITS = {
    1: ['.#.', '##.', '.#.', '.#.', '.#.', '###'],
    2: ['##.', '..#', '..#', '.#.', '#..', '###'],
    3: ['##.', '..#', '.#.', '..#', '..#', '##.'],
    4: ['#.#', '#.#', '#.#', '###', '..#', '..#'],
    5: ['###', '#..', '##.', '..#', '..#', '##.'],
    6: ['.##', '#..', '##.', '#.#', '#.#', '.#.'],
    7: ['###', '..#', '..#', '.#.', '.#.', '.#.'],
    8: ['.#.', '#.#', '.#.', '#.#', '#.#', '.#.'],
}
LETTER_F = ['##', '#.', '##', '#.', '#.']
SIGN_EDGE = (22, 58, 34, 255)
SIGN_FILL = (38, 112, 62, 255)
SIGN_INK = (236, 240, 226, 255)


def floor_sign(n):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            img.putpixel((x, y), SIGN_EDGE if edge else SIGN_FILL)
    for j, row in enumerate(DIGITS[n]):
        for i, ch in enumerate(row):
            if ch == '#':
                img.putpixel((5 + i, 5 + j), SIGN_INK)
    for j, row in enumerate(LETTER_F):
        for i, ch in enumerate(row):
            if ch == '#':
                img.putpixel((9 + i, 6 + j), SIGN_INK)
    # The rest of the texture (the plate's sides): the edge colour.
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                img.putpixel((x, y), SIGN_EDGE)
    return img


def exit_sign(seed):
    rng = random.Random(seed)
    img = paper(rng)
    green = (40, 168, 86, 255)
    dark = (20, 86, 44, 255)
    white = (238, 250, 236, 255)
    for y in range(5, 11):
        for x in range(2, 14):
            img.putpixel((x, y), dark if x in (2, 13) or y in (5, 10) else green)
    # A running man (left) and an arrow into the door (right).
    man = ['.#..', '###.', '.#.#', '#.#.']
    for j, row in enumerate(man):
        for i, ch in enumerate(row):
            if ch == '#':
                img.putpixel((4 + i, 6 + j), white)
    for i, (x, y) in enumerate([(9, 7), (10, 7), (11, 7), (10, 6), (10, 8), (12, 7)]):
        img.putpixel((x, y), white)
    return img


CLOCK_RIM = (74, 44, 22, 255)
CLOCK_RIM_LIGHT = (196, 150, 64, 255)
CLOCK_FACE = (232, 223, 196, 255)
CLOCK_FACE_SHADE = (212, 200, 168, 255)
CLOCK_TICK = (40, 30, 24, 255)


def clock():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d <= 6.1:
                img.putpixel((x, y), CLOCK_RIM)
            if d <= 5.2:
                img.putpixel((x, y), CLOCK_FACE if (x + y) % 7 else CLOCK_FACE_SHADE)
    # The rim's brass catching the light top left.
    for (x, y) in [(5, 2), (6, 2), (4, 3), (3, 4), (2, 5), (2, 6), (7, 2)]:
        img.putpixel((x, y), CLOCK_RIM_LIGHT)
    # The hour marks: 12, 3, 6, 9 two pixels, the rest of the quarter none (the face is only 10 across).
    for (x, y) in [(7, 3), (8, 3), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)]:
        img.putpixel((x, y), CLOCK_TICK)
    for (x, y) in [(10, 4), (11, 5), (11, 10), (10, 11), (5, 11), (4, 10), (4, 5), (5, 4)]:
        img.putpixel((x, y), CLOCK_FACE_SHADE)
    # The case's sides (the texture's outer two pixels all round, outside the face) use the rim colour.
    for y in range(16):
        for x in range(16):
            if x < 2 or y < 2 or x > 13 or y > 13:
                img.putpixel((x, y), CLOCK_RIM)
    return img


# ---- Models.

def face(tex, uv, cull=None):
    f = {'uv': uv, 'texture': tex}
    if cull:
        f['cullface'] = cull
    return f


def spine_model(rail):
    els = [{
        'from': [0, 0, 0], 'to': [16, 16, 16],
        'faces': {d: face('#wall', [0, 0, 16, 16], d) for d in ('north', 'south', 'east', 'west', 'up', 'down')},
    }]
    wood = '#rail'
    s = 0.7071
    if rail == 'low':
        # The lower flight's rail on the +z face: up from the first step (x 0) to the half landing (x 24), then level to its end.
        els.append({'from': [0, 18 - s, 16], 'to': [24, 18 + s, 18],
                    'rotation': {'origin': [12, 18, 17], 'axis': 'z', 'angle': -45, 'rescale': True},
                    'faces': {d: face(wood, [0, 7, 16, 9]) for d in ('north', 'south', 'up', 'down', 'east', 'west')}})
        els.append({'from': [24, 29, 16], 'to': [32, 31, 18],
                    'faces': {d: face(wood, [0, 7, 8, 9]) for d in ('north', 'south', 'up', 'down', 'east', 'west')}})
        # Two brackets into the wall.
        for x, y in ((6, 10), (20, 24)):
            els.append({'from': [x, y, 16], 'to': [x + 1, y + 1, 17], 'faces': {d: face('#iron', [0, 0, 1, 1]) for d in ('north', 'south', 'up', 'down', 'east', 'west')}})
    elif rail == 'high':
        # The upper flight's rail on the -z face: from its first step (x 16) up to the floor's landing (x -8), then level.
        els.append({'from': [-8, 18 - s, -2], 'to': [16, 18 + s, 0],
                    'rotation': {'origin': [4, 18, -1], 'axis': 'z', 'angle': 45, 'rescale': True},
                    'faces': {d: face(wood, [0, 7, 16, 9]) for d in ('north', 'south', 'up', 'down', 'east', 'west')}})
        els.append({'from': [-16, 29, -2], 'to': [-8, 31, 0],
                    'faces': {d: face(wood, [0, 7, 8, 9]) for d in ('north', 'south', 'up', 'down', 'east', 'west')}})
        for x, y in ((9, 10), (-5, 24)):
            els.append({'from': [x, y, -1], 'to': [x + 1, y + 1, 0], 'faces': {d: face('#iron', [0, 0, 1, 1]) for d in ('north', 'south', 'up', 'down', 'east', 'west')}})
    return {
        'parent': 'block/block',
        'textures': {'particle': 'minecraft:block/sandstone_top', 'wall': 'minecraft:block/sandstone_top',
                     'rail': 'minecraft:block/dark_oak_planks', 'iron': 'minecraft:block/iron_block'},
        'elements': els,
    }


def plate_model(tex, x0, x1, y0, y1, depth):
    return {
        'parent': 'block/block',
        'ambientocclusion': False,
        'textures': {'particle': tex, 'face': tex},
        'elements': [{
            'from': [x0, y0, 16 - depth], 'to': [x1, y1, 16],
            'faces': {
                'north': face('#face', [x0, 16 - y1, x1, 16 - y0]),
                'south': face('#face', [x0, 16 - y1, x1, 16 - y0], 'south'),
                'east': face('#face', [0, 16 - y1, depth, 16 - y0]),
                'west': face('#face', [0, 16 - y1, depth, 16 - y0]),
                'up': face('#face', [x0, 0, x1, depth]),
                'down': face('#face', [x0, 0, x1, depth]),
            },
        }],
    }


ROT_PLATE = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
ROT_SPINE = {'east': 0, 'south': 90, 'west': 180, 'north': 270}


def variant(model, y):
    v = {'model': model}
    if y:
        v['y'] = y
    return v


def main():
    # Textures.
    save(torn_paper(11), TEX, 'hotel_torn_paper')
    save(torn_lath(12), TEX, 'hotel_torn_lath')
    save(wall_flesh(13), TEX, 'hotel_wall_flesh')
    save(exit_sign(14), TEX, 'hotel_exit_sign')
    save(clock(), TEX, 'hotel_clock')
    for n in range(1, 9):
        save(floor_sign(n), TEX, 'hotel_floor_sign_%d' % n)
    save(Image.new('RGBA', (16, 16), (255, 255, 255, 255)), ETEX, 'hotel_px')

    # Plain cubes.
    for name in ('hotel_torn_paper', 'hotel_torn_lath', 'hotel_wall_flesh', 'hotel_exit_sign'):
        write(MODELS, name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'chainsawman:block/' + name}})
        write(STATES, name, {'variants': {'': {'model': 'chainsawman:block/' + name}}})

    # The spine.
    for rail in ('none', 'low', 'high'):
        write(MODELS, 'hotel_spine_' + rail, spine_model(rail))
    write(STATES, 'hotel_spine', {'variants': {
        'facing=%s,rail=%s' % (f, r): variant('chainsawman:block/hotel_spine_' + r, y) for f, y in ROT_SPINE.items() for r in ('none', 'low', 'high')}})

    # The floor sign: a 10 x 10 plate a pixel thick.
    for n in range(1, 9):
        write(MODELS, 'hotel_floor_sign_%d' % n, plate_model('chainsawman:block/hotel_floor_sign_%d' % n, 3, 13, 3, 13, 1))
    write(STATES, 'hotel_floor_sign', {'variants': {
        'facing=%s,floor=%d' % (f, n): variant('chainsawman:block/hotel_floor_sign_%d' % n, y) for f, y in ROT_PLATE.items() for n in range(1, 9)}})

    # The clock: a 12 x 12 face two pixels deep.
    write(MODELS, 'hotel_clock', plate_model('chainsawman:block/hotel_clock', 2, 14, 2, 14, 2))
    write(STATES, 'hotel_clock', {'variants': {
        'facing=%s,stopped=%s' % (f, s): variant('chainsawman:block/hotel_clock', y) for f, y in ROT_PLATE.items() for s in ('false', 'true')}})


if __name__ == '__main__':
    main()
