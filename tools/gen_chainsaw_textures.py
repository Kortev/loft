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


def box(img, u, v, w, h, d, side, top=None, bottom=None, front=None):
    """Paints a model box's faces where the game's box at texture (u, v), w x h x d, looks for them: {side} everywhere,
    {top}, {bottom} on those faces, and {front(x, y)} (a colour or None) over its front face."""
    faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'west': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
             'east': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
    for name, (x0, y0, fw, fh) in faces.items():
        colour = top if name == 'top' and top else bottom if name == 'bottom' and bottom else side
        for y in range(fh):
            for x in range(fw):
                c = front(x, y) if name == 'front' and front else None
                img.putpixel((x0 + x, y0 + y), c or colour)


def pochita_entity():
    """Pochita the animal (client PochitaModel's boxes): orange, shaded under, a face, the blade with its teeth."""
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    fur, under, paw = PALETTE['O'], PALETTE['o'], PALETTE['p']
    box(img, 0, 0, 6, 5, 8, fur, bottom=under)
    face = {(1, 2): PALETTE['k'], (4, 2): PALETTE['k'], (2, 4): PALETTE['n'], (3, 4): PALETTE['n']}
    box(img, 28, 0, 6, 6, 5, fur, bottom=under, front=lambda x, y: face.get((x, y)))
    box(img, 20, 14, 2, 3, 2, under, bottom=paw)
    box(img, 30, 14, 1, 1, 3, PALETTE['t'])
    # The blade, its teeth along the top and bottom of each side.
    box(img, 0, 14, 1, 2, 8, PALETTE['B'], top=PALETTE['S'], bottom=PALETTE['S'])
    for x0 in (0, 9):
        for x in range(8):
            img.putpixel((x0 + x, 22), PALETTE['S'] if x % 2 == 0 else PALETTE['s'])
            img.putpixel((x0 + x, 23), PALETTE['S'] if x % 2 == 1 else PALETTE['s'])
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
