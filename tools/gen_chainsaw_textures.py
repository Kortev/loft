"""Textures for the Chainsaw Man mod (chainsawman/), drawn pixel by pixel.

Run from the repository root: py tools/gen_chainsaw_textures.py [--preview DIR]

--preview also writes each one blown up 16 times, to look at.
"""
import os
import sys

from PIL import Image

ROOT = os.path.join('chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'textures')
CLIENT_ROOT = os.path.join('chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman', 'textures')

# Pochita side on, facing right as an item does: a small round orange dog with the chainsaw blade standing out of his
# forehead and out over his nose (grey bar, teeth along its edges), a stub tail, a black eye and nose, little paws.
POCHITA = [
    '................',
    '................',
    '.........SsSsSs.',
    '........SBBBBBBS',
    '.......SBBBBBBBs',
    '.......oSSsSSsS.',
    '......oOOOOOo...',
    '.t...oOOOOkOOo..',
    'tooooOOOOOOOOOn.',
    '.oOOOOOOOOOOOo..',
    '.oOOOOOOOOOOOo..',
    '.oOOOOOOOOOOOo..',
    '..oOOOOOOOOOo...',
    '..pp.pp..pp.pp..',
    '..pp.pp..pp.pp..',
    '................',
]

# The inside of a head split open by the saw (FormFeature draws it on each half's cut face): the form's wound colours,
# raw red under a dark ink edge, with darker flesh and pink.
FLESH = [
    'iiiiiiii',
    'iWWWwWWi',
    'iWgWWWWi',
    'iWWwwWgi',
    'iwWwwWWi',
    'iWWWWgWi',
    'iWgWWWwi',
    'iiiiiiii',
]

# A link of the chain the arm fires (ChainRender draws one on each link's quad, along the chain): a steel ring round
# its hole, inked.
CHAIN = [
    '.iiiiiiii.',
    'iLLLLLLLLi',
    'iLs....sLi',
    'iLLLLLLLLi',
    '.iiiiiiii.',
]

PALETTE = {
    '.': (0, 0, 0, 0),
    'O': (236, 128, 38, 255),   # his fur
    'o': (176, 82, 22, 255),    # its shaded edge
    'p': (150, 66, 18, 255),    # paws
    't': (196, 98, 28, 255),    # tail
    'k': (20, 16, 14, 255),     # eye
    'n': (34, 22, 18, 255),     # nose
    'B': (176, 182, 188, 255),  # the blade
    'S': (92, 96, 104, 255),    # its edge and teeth
    's': (210, 214, 220, 255),  # a tooth catching the light
    'H': (40, 40, 44, 255),     # the cord's handle
    'c': (120, 112, 100, 255),  # the cord
    'W': (160, 18, 28, 255),    # raw flesh (the model's wound)
    'w': (108, 10, 18, 255),    # its darker heart
    'g': (196, 74, 86, 255),    # pink (the model's gum)
    'i': (50, 4, 8, 255),       # the ink round it
    'L': (150, 150, 156, 255),  # the chain's steel (the model's links)
    's': (108, 108, 114, 255),  # its shadow
}


def draw(rows):
    img = Image.new('RGBA', (len(rows[0]), len(rows)))
    for y, row in enumerate(rows):
        assert len(row) == img.width, (y, row)
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


# Pochita the animal, painted for the client PochitaModel's boxes (its texture corners and sizes, mirrored here), from
# kortev's stills: his orange, the reddish brown on his underside, rear and low down his sides; big eyes at the front
# corners of his head (a black pupil with a glint, a crescent of white), seen from the front and the side alike; a
# "w" of a muzzle over an open mouth with a fang and his tongue; toes on his paws; the bar pale grey with a glint
# along it and its chain dark top and bottom, the end rounded; black handles and tail with a few light marks; the
# open triangle of the starter's grip.
POCHITA_COLOURS = {
    'O': (229, 118, 46, 255),   # his orange
    'S': (142, 62, 47, 255),    # its shadow, the reddish brown
    'D': (96, 40, 32, 255),     # lines: toes, the muzzle's w
    'K': (22, 20, 26, 255),     # pupils
    'W': (250, 250, 247, 255),  # the whites, glints, the fang
    'M': (92, 24, 28, 255),     # inside his mouth
    'T': (232, 128, 140, 255),  # his tongue
    'G': (190, 196, 201, 255),  # the bar
    'g': (232, 236, 240, 255),  # the glint along it
    'C': (58, 60, 68, 255),     # the chain
    'H': (34, 34, 42, 255),     # the handles, the tail
    'h': (120, 124, 136, 255),  # a light mark on a handle
    '.': (0, 0, 0, 0),
}

# His eye, as it looks from his left side (front to the right): round and white, the big black pupil looking ahead
# (wrapping round the corner onto his face, so it shows from the front too).
EYE = [
    'OWWO',
    'WWKK',
    'WWKK',
    'OWWO',
]


def faces(u, v, w, h, d):
    """Where a model box's faces are in the texture, as the game lays them out: top, bottom, then round the sides, the
    side at x min ('west', its front edge at its right), the front (-z), the side at x max ('east', front at its left),
    the back."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'west': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'east': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


def paint(img, u, v, w, h, d, colour):
    """Paints every face of the box at (u, v): {colour(face, x, y, fw, fh)} gives each pixel's key in POCHITA_COLOURS."""
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                img.putpixel((x0 + x, y0 + y), POCHITA_COLOURS[colour(name, x, y, fw, fh)])


def fur(face, x, y, fw, fh, under=0.35):
    """His fur: orange, reddish brown underneath and on the lowest part of his sides and back."""
    if face == 'bottom':
        return 'S'
    if face == 'top':
        return 'O'
    return 'S' if y >= fh * (1.0 - under) else 'O'


def pochita_entity():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def head(face, x, y, fw, fh):
        # The eyes at its front corners: on each side face's front end, and wrapping round onto the front.
        if face in ('west', 'east') and 2 <= y <= 5:
            col = x if face == 'west' else fw - 1 - x
            ex = col - (fw - 5)
            if 0 <= ex < 4:
                c = EYE[y - 2][ex]
                if c != 'O':
                    return c
        if face == 'front' and 3 <= y <= 4 and x in (0, fw - 1):
            return 'K'
        return fur(face, x, y, fw, fh, under=0.3)

    paint(img, 0, 0, 9, 10, 6, head)
    paint(img, 0, 16, 8, 9, 9, lambda f, x, y, fw, fh: fur(f, x, y, fw, fh, under=0.4))
    paint(img, 30, 0, 7, 1, 4, lambda f, x, y, fw, fh: 'O')
    paint(img, 30, 5, 6, 1, 6, lambda f, x, y, fw, fh: 'O')
    paint(img, 34, 16, 6, 7, 1, lambda f, x, y, fw, fh: 'S' if f == 'back' and y >= 2 else fur(f, x, y, fw, fh, under=0.6))

    def muzzle(face, x, y, fw, fh):
        if face == 'front':
            return ['OOOOO', 'DODOD', 'OMMMO', 'OWTMO'][y][x]
        return fur(face, x, y, fw, fh, under=0.5)

    paint(img, 48, 16, 5, 4, 2, muzzle)

    def leg(face, x, y, fw, fh):
        if face == 'front' and y == fh - 1:
            return 'D' if x == 1 else 'S'
        if face == 'bottom':
            return 'S'
        return 'S' if y == fh - 1 else 'O'

    paint(img, 34, 24, 3, 3, 3, leg)

    def bar(face, x, y, fw, fh):
        if face in ('west', 'east'):
            front = (x == fw - 1) if face == 'west' else (x == 0)
            if front and y in (0, fh - 1):
                return '.'
            if y in (0, fh - 1) or front:
                return 'C'
            return 'g' if y == 1 and 2 <= (x if face == 'west' else fw - 1 - x) <= fw - 3 else 'G'
        return 'C'

    paint(img, 0, 34, 1, 5, 13, bar)
    paint(img, 46, 24, 1, 1, 1, lambda f, x, y, fw, fh: 'C')

    def handle_grip(face, x, y, fw, fh):
        return 'h' if face in ('top', 'front', 'back') and x in (fw - 3, fw - 2) and (face != 'top' or y == 0) else 'H'

    paint(img, 28, 34, 1, 6, 2, lambda f, x, y, fw, fh: 'H')
    paint(img, 34, 34, 9, 1, 2, handle_grip)
    paint(img, 34, 38, 2, 2, 4, lambda f, x, y, fw, fh: 'H')
    paint(img, 46, 38, 1, 1, 3, lambda f, x, y, fw, fh: 'H')
    paint(img, 54, 38, 1, 4, 1, lambda f, x, y, fw, fh: 'H')

    def grip(face, x, y, fw, fh):
        # The open triangle, its point down onto the tail.
        return ['HHHHHHH', '.H...H.', '..H.H..', '...H...'][y][x] if face in ('west', 'east') else '.'

    paint(img, 28, 44, 0, 4, 7, grip)
    return img


# The pool of blood spreading from under you as you die (MergeCinematic): flat, ragged at its edge, a darker heart,
# a glint of wet; transparent round it.
POOL = [
    '................',
    '.....RRR..R.....',
    '...RRRRRRRRR..R.',
    '..RRRRRRRRRRRR..',
    '.RRRRrrrrRRRRRR.',
    '.RRRrrrrrrrRRRR.',
    'RRRrrrrrrrrrRRR.',
    '.RRrrrrrrwrrrRR.',
    '.RRrrrrrrrrrrRRR',
    'RRRRrrrrrrrrRRR.',
    '.RRRrrrrrrrRRRR.',
    '..RRRRrrrrRRRR..',
    '.R.RRRRRRRRRR...',
    '....RRRRRRR..R..',
    '.......RR.......',
    '................',
]


def pool():
    colours = {'.': (0, 0, 0, 0), 'R': (150, 14, 20, 255), 'r': (104, 6, 12, 255), 'w': (214, 90, 96, 255)}
    img = Image.new('RGBA', (16, 16))
    for y, row in enumerate(POOL):
        for x, c in enumerate(row):
            img.putpixel((x, y), colours[c])
    return img


def flare():
    """The eyes catching light at the roar (FormFeature.flare): a pixel star, drawn added onto the picture, so black is
    nothing: a white-hot middle, yellow and orange rays out to the four sides."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    levels = [(0.85, (255, 246, 214)), (0.62, (255, 210, 100)), (0.4, (240, 140, 40)), (0.2, (150, 64, 12)), (0.07, (60, 22, 4))]
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            core = max(0.0, 1.0 - (dx * dx + dy * dy) ** 0.5 / 3.4)
            ray = 0.0
            for along, across in ((dx, dy), (dy, dx)):
                width = 1.0 if across < 1.0 else 0.3 if across < 2.0 else 0.0
                ray = max(ray, max(0.0, 1.0 - along / 9.0) * width)
            v = max(core, ray)
            for at, colour in levels:
                if v >= at:
                    img.putpixel((x, y), colour + (255,))
                    break
    return img


def trail():
    """The smear behind a slashing saw (SawTrails): along it (u) from the tip's newest place to its oldest, across it
    (v) from the tip's edge in toward the bar. White at the edge going to blood red, fading in steps both ways."""
    across = [(255, 255, 255, 235), (255, 238, 230, 215), (252, 196, 186, 185), (226, 70, 70, 150), (176, 22, 28, 110),
              (124, 10, 16, 70), (84, 6, 10, 36), (0, 0, 0, 0)]
    img = Image.new('RGBA', (16, 8))
    for y, (r, g, b, a) in enumerate(across):
        for x in range(16):
            fade = max(0.0, 1.0 - (x // 2) / 8.0) ** 1.3
            img.putpixel((x, y), (r, g, b, int(a * fade)))
    return img


# The chainsaw head as boxes (FormHeadModel, a proposal beside the mesh head: kortev asked for a cleaner head that fits
# the game's style better, as Pochita's boxes do), painted for its boxes' texture corners and sizes, mirrored here. The
# orange visor over the front, its ridge and its prow; the eye's vents curving round the back of each of its sides, the
# inner ones burning yellow; an open mouth of long white teeth meeting; the orange jaw stepping down to its point; the
# black engine behind with a round socket on each side and fins across its back; the D handle and the pole; the neck
# bound in dark cords.
HEAD_COLOURS = {
    'O': (236, 122, 34, 255),   # the visor's orange
    'o': (255, 166, 74, 255),   # its light edge
    'S': (178, 78, 22, 255),    # its shadow
    'D': (112, 44, 14, 255),    # its darkest, the ink
    'K': (30, 30, 36, 255),     # the engine
    'k': (58, 60, 70, 255),     # its light edges, the fins
    'G': (128, 132, 142, 255),  # steel: the socket's ring
    'W': (246, 244, 236, 255),  # teeth
    'w': (200, 196, 186, 255),  # their shade
    'M': (58, 10, 14, 255),     # the mouth's dark
    'R': (150, 24, 30, 255),    # the gum
    'Y': (150, 104, 30, 255),   # the eye, unlit (the glow layer lights it)
    'B': (70, 44, 34, 255),     # the neck's cords
    'b': (40, 26, 22, 255),     # between them
    '.': (0, 0, 0, 0),
}
# The side of the visor (6 by 5, its front at the right): the vents curving round its back, outer two dark, inner two
# the eye.
HEAD_VENTS = [
    'KOOOOO',
    'OKOOOO',
    'YOKOOO',
    'OYOKOO',
    'YOYOKS',
]
# Where the eye's pixels are lit (the glow layer drawn over the head, as a spider's eyes are).
HEAD_GLOW = (255, 214, 90, 255)


def head_paint(img, glow, u, v, w, h, d, colour):
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = colour(name, x, y, fw, fh)
                img.putpixel((x0 + x, y0 + y), HEAD_COLOURS[c])
                if c == 'Y':
                    glow.putpixel((x0 + x, y0 + y), HEAD_GLOW)


def form_head():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def visor(face, x, y, fw, fh):
        if face in ('west', 'east'):
            # The vents curving round the back of each side, the inner two the eye (drawn with the front at the right,
            # as the west face lies; the east face is its mirror).
            col = x if face == 'west' else fw - 1 - x
            # (Its top row is the top's, over it.)
            return HEAD_VENTS[y + 1][col]
        if face == 'top':
            # Only its edges show round the top over it: lit, a bevel.
            return 'o'
        if face == 'front':
            return 'S' if y == fh - 1 else 'O'
        if face == 'bottom':
            return 'D'
        return 'S'

    head_paint(img, glow, 0, 14, 10, 4, 6, visor)

    def visor_top(face, x, y, fw, fh):
        if face in ('west', 'east'):
            col = x if face == 'west' else fw - 1 - x
            return 'S' if col == 0 else 'O'
        if face == 'top':
            return 'o' if y == fh - 1 else 'O'
        if face == 'back':
            return 'S'
        return 'O'

    head_paint(img, glow, 32, 44, 8, 1, 5, visor_top)
    head_paint(img, glow, 32, 0, 2, 1, 4, lambda f, x, y, fw, fh: 'o' if f == 'top' else 'O')
    head_paint(img, glow, 24, 44, 2, 1, 2, lambda f, x, y, fw, fh: 'o' if f == 'top' else 'S' if f == 'back' else 'O')
    head_paint(img, glow, 50, 0, 6, 3, 1, lambda f, x, y, fw, fh: 'o' if f == 'top' else 'O')
    head_paint(img, glow, 0, 50, 4, 1, 1, lambda f, x, y, fw, fh: 'S' if f == 'bottom' else 'O')

    def mouth(face, x, y, fw, fh):
        if face in ('top', 'bottom', 'back'):
            return 'M'
        # Long teeth meeting: the upper hanging from the gum, the lower standing between them.
        rows = ['RRRRRRRR', 'WMWMWMWM', 'WwWwWwWw', 'WwWwWwWw', 'MwMwMwMw']
        return rows[y][x % 8]

    head_paint(img, glow, 0, 26, 8, 5, 5, mouth)

    def jaw(face, x, y, fw, fh):
        if face == 'bottom':
            return 'S'
        return 'O'

    head_paint(img, glow, 0, 36, 8, 1, 4, jaw)
    head_paint(img, glow, 26, 36, 4, 1, 2, lambda f, x, y, fw, fh: 'S' if f == 'bottom' else 'O')
    head_paint(img, glow, 38, 36, 2, 1, 1, lambda f, x, y, fw, fh: 'S' if f == 'bottom' else 'O')

    def engine(face, x, y, fw, fh):
        if face == 'back':
            # Fins across the back.
            return 'k' if y % 2 == 1 and 1 <= x < fw - 1 else 'K'
        if face == 'top':
            return 'k' if y == 0 else 'K'
        return 'K'

    head_paint(img, glow, 0, 0, 9, 7, 6, engine)
    head_paint(img, glow, 0, 44, 7, 1, 5, lambda f, x, y, fw, fh: 'k' if f == 'top' and y == 0 or f == 'back' else 'K')
    head_paint(img, glow, 38, 40, 7, 1, 3, lambda f, x, y, fw, fh: 'k' if f == 'top' else 'K')

    def socket(face, x, y, fw, fh):
        if face in ('west', 'east'):
            ring = ['.GG.', 'GKKG', 'GKKG', 'GKKG', '.GG.'][y][x]
            return 'K' if ring == '.' else ring
        return 'K'

    head_paint(img, glow, 32, 14, 1, 5, 4, socket)
    head_paint(img, glow, 44, 14, 1, 1, 3, lambda f, x, y, fw, fh: 'K')
    head_paint(img, glow, 52, 14, 1, 6, 1, lambda f, x, y, fw, fh: 'k' if f == 'back' and y in (1, 4) else 'K')
    head_paint(img, glow, 56, 14, 1, 3, 1, lambda f, x, y, fw, fh: 'K')
    head_paint(img, glow, 26, 26, 6, 1, 5, lambda f, x, y, fw, fh: 'b' if x % 2 == 1 else 'B')
    return img, glow


# The engine's roar as the manga letters it, slammed over the impact frames (FormHud.lettering): ヴ, ゥ, ン, each in a
# 20 by 20 cell of the texture, heavy black strokes in a white outline with a blood-red shadow behind.
ROAR_GLYPHS = [
    [
        '................',
        '.......##..#.#..',
        '.......##...#.#.',
        '..############..',
        '..############..',
        '..##........##..',
        '..##........##..',
        '..##.......##...',
        '...........##...',
        '..........##....',
        '.........###....',
        '........###.....',
        '......####......',
        '...#####........',
        '...###..........',
        '................',
    ],
    [
        '................',
        '................',
        '................',
        '................',
        '.......##.......',
        '.......##.......',
        '....########....',
        '....########....',
        '....##....##....',
        '..........##....',
        '.........##.....',
        '........###.....',
        '......####......',
        '.....###........',
        '................',
        '................',
    ],
    [
        '................',
        '................',
        '.###............',
        '..####..........',
        '....###.....###.',
        '............###.',
        '...........###..',
        '..........###...',
        '.........###....',
        '........###.....',
        '.......###......',
        '.##..####.......',
        '..######........',
        '...###..........',
        '................',
        '................',
    ],
]


def roar():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for i, glyph in enumerate(ROAR_GLYPHS):
        ink = {(x + 2, y + 2) for y, row in enumerate(glyph) for x, c in enumerate(row) if c == '#'}
        edge = {(x + dx, y + dy) for x, y in ink for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - ink
        shadow = {(x + 2, y + 2) for x, y in ink | edge} - ink - edge
        for pixels, colour in ((shadow, (120, 8, 14, 255)), (edge, (250, 248, 240, 255)), (ink, (14, 12, 16, 255))):
            for x, y in pixels:
                if 0 <= x < 20 and 0 <= y < 20:
                    img.putpixel((i * 20 + x, y), colour)
    return img


# The chain the arm fires (ChainRender), as the anime draws it: the saw's own chain, seen from the side, one link's
# length to the texture: a cutter tooth hooked up over the links, the link plates riveted, a drive tang hanging under;
# dark steel, lit along its top, flecked with blood. It repeats along the chain.
SAW_CHAIN = [
    '..........KKK...',
    '........KKHSSK..',
    '.......KHSSSSK..',
    'KKKKKKKKKKKKKKKK',
    'KSSHSSSKKSSHSSSK',
    'KShSSRSKKShSSSSK',
    'KKKKKKKKKKKKKKKK',
    '...KSSK...KSSK..',
]


def saw_chain():
    colours = {'.': (0, 0, 0, 0), 'K': (26, 26, 32, 255), 'S': (96, 98, 108, 255), 'H': (176, 180, 190, 255), 'h': (210, 212, 220, 255),
               'R': (128, 12, 16, 255)}
    img = Image.new('RGBA', (16, 8))
    for y, row in enumerate(SAW_CHAIN):
        for x, c in enumerate(row):
            img.putpixel((x, y), colours[c])
    return img


# The dream of the door (DreamHud, season 1): a dark corridor running away to a door at its end, light leaking out
# round it, and Pochita on the floor in front of it, looking back at you.
DREAM_POCHITA = [
    '....GG....',
    '....GG....',
    '..OOOOOO..',
    '.OOWWOOOO.',
    '.OOWKOOOO.',
    '.OOOOOOOO.',
    '..OSSSSO..',
    '..K.KK.K..',
]


def dream_door():
    w, h = 64, 40
    img = Image.new('RGBA', (w, h), (0, 0, 0, 255))
    vx, vy = 32, 19
    door = (27, 8, 37, 29)
    for y in range(h):
        for x in range(w):
            inside = door[0] <= x <= door[2] and door[1] <= y <= door[3]
            if inside:
                continue
            # Walls, floor and ceiling of the corridor, darker the nearer they are (the light is at the door).
            d = max(abs(x - vx) / 32.0, abs(y - vy) / 20.0)
            shade = int(46 * (1.0 - d) ** 1.4)
            floor = y > door[3] and abs(x - vx) <= (y - vy) * 1.6
            ceiling = y < door[1] and abs(x - vx) <= (vy - y) * 1.6
            if floor:
                c = (shade + 8, shade + 4, shade + 2)
            elif ceiling:
                c = (shade // 2, shade // 2, shade // 2 + 4)
            else:
                c = (shade // 2 + 2, shade // 2 + 4, shade // 2 + 10)
            img.putpixel((x, y), c + (255,))
    for y in range(door[1], door[3] + 1):
        for x in range(door[0], door[2] + 1):
            edge = x in (door[0], door[2]) or y == door[1]
            panel = (door[0] + 2 <= x <= door[2] - 2) and (door[1] + 2 <= y <= door[1] + 8 or door[1] + 11 <= y <= door[3] - 2)
            c = (74, 58, 46) if edge else (58, 44, 36) if panel else (66, 50, 40)
            img.putpixel((x, y), c + (255,))
    # The light leaking out down its side and under it; the knob.
    for y in range(door[1], door[3] + 1):
        img.putpixel((door[2] + 1, y), (255, 226, 150, 255))
        img.putpixel((door[2] + 2, y), (150, 110, 60, 255))
    for x in range(door[0], door[2] + 3):
        img.putpixel((x, door[3] + 1), (200, 160, 90, 255))
    img.putpixel((door[2] - 2, (door[1] + door[3]) // 2), (210, 190, 120, 255))
    colours = {'O': (229, 118, 46, 255), 'S': (142, 62, 47, 255), 'W': (250, 250, 247, 255), 'K': (22, 20, 26, 255), 'G': (170, 176, 182, 255)}
    for y, row in enumerate(DREAM_POCHITA):
        for x, c in enumerate(row):
            if c in colours:
                img.putpixel((15 + x, 30 + y), colours[c])
    return img


# Blood on the lens from a close kill (LensBlood): three splats, each 16 by 16, dark red with a wet glint, ragged.
LENS_SPLATS = [
    [
        '......RR........',
        '.....RRRR...R...',
        '..R.RRRRRR......',
        '...RRRrrRRRR....',
        '..RRRrrrrRRRR.R.',
        '.RRRrrrrrrRRR...',
        '..RRrrrwrrRRRR..',
        '.RRRrrrrrrrRR...',
        '..RRRrrrrrRRR...',
        '...RRRrrRRRR....',
        '....RRRRRRR..R..',
        '.R...RRRRR......',
        '......RR........',
        '......RR........',
        '.......R........',
        '................',
    ],
    [
        '................',
        '...R.....RR.....',
        '........RRRR....',
        '.....RRRRRRRR...',
        '....RRRrrrRRRR..',
        '...RRRrrwrrRRR..',
        '...RRrrrrrrRR...',
        '..RRRRrrrrRRR.R.',
        '...RRRRRRRRR....',
        '.R...RRRRRR.....',
        '......RRR.......',
        '......RR........',
        '.......R........',
        '.......R........',
        '................',
        '................',
    ],
    [
        '................',
        '..........R.....',
        '...RR....RRR....',
        '..RRRR..RRRRR...',
        '..RRRRRRRrrRR...',
        '...RRrrrrrrRRR..',
        '...RRrrwrrrRR...',
        '....RRrrrrRRR...',
        '.....RRRRRRR..R.',
        '......RRRR......',
        '.......RR.......',
        '.......RR.......',
        '........R.......',
        '................',
        '................',
        '................',
    ],
]


def lens_blood():
    colours = {'.': (0, 0, 0, 0), 'R': (98, 4, 10, 250), 'r': (134, 10, 16, 255), 'w': (210, 96, 96, 255)}
    img = Image.new('RGBA', (48, 16))
    for i, splat in enumerate(LENS_SPLATS):
        for y, row in enumerate(splat):
            for x, c in enumerate(row):
                img.putpixel((i * 16 + x, y), colours[c])
    return img


# The fight's sound effects, lettered as the manga does (Sfx): katakana in heavy black strokes, white outline,
# blood-red shadow, each glyph in a 20 by 20 cell, in this order: ブ シ ャ ッ ザ ュ ギ リ ド ク ン (the small ones drawn
# small, low in their cells). Kills are ブシャッ, the overhead chop landing ザシュッ, grinding into something ギャリギャリ,
# your heart starting in the merge ドクン.
SFX_GLYPHS = [
    [  # bu
        '...........#.#..',
        '............#.#.',
        '..##########....',
        '..##########....',
        '..........##....',
        '.........##.....',
        '.........##.....',
        '........##......',
        '.......##.......',
        '......##........',
        '....###.........',
        '..###...........',
        '.##.............',
        '................',
        '................',
        '................',
    ],
    [  # shi
        '................',
        '.##.............',
        '..##............',
        '...#........##..',
        '............##..',
        '##.........##...',
        '.##.......##....',
        '..#......##.....',
        '........##......',
        '.......##.......',
        '.....###........',
        '..####..........',
        '..##............',
        '................',
        '................',
        '................',
    ],
    [  # small ya
        '................',
        '................',
        '................',
        '................',
        '......##........',
        '......##........',
        '..###########...',
        '.......##..##...',
        '.......##.##....',
        '........##......',
        '........##......',
        '.........##.....',
        '.........##.....',
        '................',
        '................',
        '................',
    ],
    [  # small tsu
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '..#..#....##....',
        '..##.##...##....',
        '...#..#..##.....',
        '........##......',
        '.......##.......',
        '.....###........',
        '...###..........',
        '................',
        '................',
        '................',
    ],
    [  # za
        '............#.#.',
        '.............#.#',
        '....##....##....',
        '....##....##....',
        '################',
        '################',
        '....##....##....',
        '....##....##....',
        '....##....##....',
        '..........##....',
        '.........##.....',
        '........##......',
        '.......##.......',
        '.....###........',
        '...###..........',
        '................',
    ],
    [  # small yu
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '..#######.......',
        '..#######.......',
        '........##......',
        '........##......',
        '........##......',
        '.############...',
        '.############...',
        '................',
        '................',
        '................',
    ],
    [  # gi
        '............#.#.',
        '.....##......#.#',
        '.....##.........',
        '..##########....',
        '..##########....',
        '......##........',
        '.############...',
        '.############...',
        '.......##.......',
        '........##......',
        '........##......',
        '.........##.....',
        '.........##.....',
        '................',
        '................',
        '................',
    ],
    [  # ri
        '................',
        '..##......##....',
        '..##......##....',
        '..##......##....',
        '..##......##....',
        '..##......##....',
        '..##......##....',
        '..........##....',
        '.........##.....',
        '........##......',
        '.......##.......',
        '.....###........',
        '...###..........',
        '................',
        '................',
        '................',
    ],
]


SFX_GLYPHS += [
    [  # do
        '.....##....#.#..',
        '.....##.....#.#.',
        '.....##.........',
        '.....##.........',
        '.....####.......',
        '.....#####......',
        '.....##..###....',
        '.....##....###..',
        '.....##.....##..',
        '.....##.........',
        '.....##.........',
        '.....##.........',
        '.....##.........',
        '.....##.........',
        '................',
        '................',
    ],
    [  # ku
        '.....##.........',
        '.....##.........',
        '....##########..',
        '...##.......##..',
        '..##........##..',
        '.##........##...',
        '...........##...',
        '..........##....',
        '.........##.....',
        '........##......',
        '......###.......',
        '....###.........',
        '..###...........',
        '................',
        '................',
        '................',
    ],
    [  # n
        '................',
        '.##.............',
        '..###...........',
        '....##......##..',
        '............##..',
        '...........##...',
        '..........##....',
        '.........##.....',
        '........##......',
        '......###.......',
        '....###.........',
        '.####...........',
        '................',
        '................',
        '................',
        '................',
    ],
]


def sfx():
    img = Image.new('RGBA', (20 * len(SFX_GLYPHS), 20), (0, 0, 0, 0))
    for i, glyph in enumerate(SFX_GLYPHS):
        ink = {(x + 2, y + 2) for y, row in enumerate(glyph) for x, c in enumerate(row) if c == '#'}
        edge = {(x + dx, y + dy) for x, y in ink for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - ink
        shadow = {(x + 1, y + 1) for x, y in ink | edge} - ink - edge
        for pixels, colour in ((shadow, (120, 8, 14, 255)), (edge, (250, 248, 240, 255)), (ink, (14, 12, 16, 255))):
            for x, y in pixels:
                if 0 <= x < 20 and 0 <= y < 20:
                    img.putpixel((i * 20 + x, y), colour)
    return img


# The merge's dream (MergeCinematic): its floor, a pool of light in the black.
def dream_floor():
    """The dream's floor: black, a pool of warm light in the middle, falling off in steps."""
    img = Image.new('RGBA', (64, 64))
    for y in range(64):
        for x in range(64):
            d = ((x - 31.5) ** 2 + (y - 31.5) ** 2) ** 0.5 / 32.0
            k = max(0.0, 1.0 - d / 0.25)
            k = round(k * k * 6) / 6.0
            img.putpixel((x, y), (int(8 + 200 * k), int(6 + 186 * k), int(10 + 160 * k), 255))
    return img


# The torn end of an arm lost in the fight (client Stumps): raw flesh on the left, wet and clotted, and the bone on the
# right, its marrow showing at the middle of the cut.
STUMP = [
    'ddRrRdRrIIiIIiII',
    'RrPRrRdRIiIIIIiI',
    'rRRpRrRrIIiMMiII',
    'dRrRRdRrIiMmmMiI',
    'RpRrdRPrIiMmmMIi',
    'rRdRrRrdIIiMMiII',
    'RrRPrRRrIiIIiIII',
    'dRrRdRrdIIIiIIiI',
]
STUMP_COLOURS = {
    'R': (156, 20, 26, 255),    # raw flesh
    'r': (118, 10, 16, 255),    # darker
    'd': (66, 4, 8, 255),       # clotted
    'P': (214, 102, 104, 255),  # wet, the light on it
    'p': (190, 70, 74, 255),
    'I': (236, 226, 200, 255),  # bone
    'i': (200, 184, 152, 255),  # its shade
    'M': (150, 44, 36, 255),    # marrow
    'm': (112, 24, 22, 255),
}


def stump():
    img = Image.new('RGBA', (16, 8))
    for y, row in enumerate(STUMP):
        for x, ch in enumerate(row):
            img.putpixel((x, y), STUMP_COLOURS[ch])
    return img


def main():
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    out = {(ROOT, 'item/pochita.png'): draw(POCHITA), (CLIENT_ROOT, 'entity/form_flesh.png'): draw(FLESH),
           (CLIENT_ROOT, 'entity/form_chain.png'): draw(CHAIN),
           (CLIENT_ROOT, 'entity/pochita.png'): pochita_entity(), (CLIENT_ROOT, 'entity/blood_pool.png'): pool(),
           (CLIENT_ROOT, 'entity/form_flare.png'): flare(), (CLIENT_ROOT, 'entity/form_trail.png'): trail(),
           (CLIENT_ROOT, 'gui/form_roar.png'): roar(), (CLIENT_ROOT, 'entity/form_saw_chain.png'): saw_chain(),
           (CLIENT_ROOT, 'gui/dream_door.png'): dream_door(), (CLIENT_ROOT, 'gui/lens_blood.png'): lens_blood(),
           (CLIENT_ROOT, 'gui/form_sfx.png'): sfx(), (CLIENT_ROOT, 'entity/dream_floor.png'): dream_floor(),
           (CLIENT_ROOT, 'entity/form_stump.png'): stump()}
    head, head_glow = form_head()
    out[(CLIENT_ROOT, 'entity/form_head.png')] = head
    out[(CLIENT_ROOT, 'entity/form_head_glow.png')] = head_glow
    for (root, name), img in out.items():
        path = os.path.join(root, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        if preview:
            os.makedirs(preview, exist_ok=True)
            img.resize((img.width * 16, img.height * 16), Image.NEAREST).save(
                os.path.join(preview, os.path.basename(name)))
        print('wrote', path)


if __name__ == '__main__':
    main()
