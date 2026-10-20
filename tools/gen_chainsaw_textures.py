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


def main():
    preview = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    out = {(ROOT, 'item/pochita.png'): draw(POCHITA), (CLIENT_ROOT, 'entity/form_flesh.png'): draw(FLESH),
           (CLIENT_ROOT, 'entity/form_chain.png'): draw(CHAIN),
           (CLIENT_ROOT, 'entity/pochita.png'): pochita_entity()}
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
