"""Builds Baron Bomburst's Vulgarian airship, from Chitty Chitty Bang Bang, in Blender.

Needs Blender's Python module (pip install "bpy==4.5.*", on Python 3.11). Run from the repository root:

    python tools/airship_model.py --out DIR              # writes DIR/airship.blend
    python tools/airship_model.py --out DIR --renders    # also renders her flying, from the ground and close up
    python tools/airship_model.py --out DIR --textures   # also writes the painted textures as PNGs

Blender units are blocks (metres), as for Chitty (tools/chitty_model.py, whose helpers this uses). The airship faces
+Y with +X on her right and +Z up. The origin is under the middle of the gondola's keel: she lands on her gondola, and
that is where she is boarded.

The film's airship was a real one, built to fly in 1967: a copy of a 1904 Lebaudy, 112 feet long and 30 across (34
and 9 blocks here). Her envelope is short and deep, pointed at both ends with the tips set a little high. Its ends are
banded in Vulgaria's colours, purple, black and purple out to a black tip, each band parted by a white stripe, and on
each flank are Vulgaria's arms: a black griffin with its wings raised, standing behind a gold shield.

Under the envelope is a flat platform of dark lattice, and under that, about two fifths of the way back from the nose,
hangs the gondola: small (four blocks long, standing room for eight), a black box carved and gilded with the Baron's
B at the bow, a raked bow, a grey engine section with louvres at the stern and a heavy black rail along the top. Two
propellers stand out behind its stern corners on shafts, each turned by a belt from the engine over a spoked pulley.
A coil of rope hangs over the side at the bow, and a rope ladder from the middle. A narrow girder runs back from over
the gondola to the tail, which hangs under the back of the envelope: a white tailplane with Vulgaria's stripes on
its elevator, and dark triangular fins above and below it ending in the rudder.

Parts that move in the game are objects of their own with their origins on their pivots: the two propellers, the
rudder and elevator, the wheel, the hook, the ladder (one rung, stacked by the game), its roll, the coil of rope and
each of the six bombs. Empties mark the eight places to stand, where the hook's rope comes out under the gondola,
the top of the ladder and the exhaust.
"""
import math
import os
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
import numpy as np
from mathutils import Matrix, Vector
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chitty_model as cm  # noqa: E402  (materials, meshes, lofts, tubes and the render scene)

# --- layout (blocks) ---------------------------------------------------------------------------------

ENV_LENGTH = 34.0
ENV_R = 4.55            # the envelope's radius over its straight middle
GONDOLA_AT = 0.39       # how far back from the nose the gondola hangs, as a fraction of the length
ENV_BELLY = 0.93        # the lower half is a little flatter than the top, over the platform
ENV_STRAIGHT = 0.12     # the straight middle, as a fraction of the half length; the rest tapers to the tips
ENV_TIP_RISE = 0.30     # how far above the axis the tips are, in radii
ENV_NOSE = GONDOLA_AT * ENV_LENGTH
ENV_TAIL = ENV_NOSE - ENV_LENGTH
ENV_MID_Y = (ENV_NOSE + ENV_TAIL) / 2

# The platform under the envelope: a flat frame of dark lattice, pointed at both ends.
PLATFORM = dict(front=3.6, back=-12.6, half_width=3.0, z=3.25)
ENV_Z = PLATFORM['z'] + 0.1 + ENV_R * ENV_BELLY     # the envelope's axis

# The gondola: a black box with a raked bow, the engine section at the stern. Along y its top runs from STERN to BOW;
# its bottom from the foot of the bow (BOW_FOOT) back to where the engine section's floor slopes up (ENGINE_FRONT).
GON_BOW = 2.1
GON_STERN = -2.1
GON_BOW_FOOT = 1.55
GON_ENGINE_FRONT = -1.0
GON_STERN_RISE = 0.45       # how high the bottom has sloped up at the stern
GON_HALF_WIDTH = 0.95
GON_TOP = 1.0
GON_FLOOR = 0.18
BEAM = 0.14                 # the heavy black rail along the top, square
BEAM_OVER = 0.35            # how far it runs on past each end
BAR_Z = 2.3                 # the long bars over the crew's heads, from which the gondola hangs

# The narrow girder from over the gondola back to the tail, and the tail itself.
GIRDER = dict(front=2.0, back=-15.0, half_width=0.4, depth=0.7, top=PLATFORM['z'] - 0.1, tail_top=2.35)
TAIL = dict(lead=-13.8, hinge=-18.6, trail=-19.9, z=1.85, up=2.3, down=2.1, span=2.8)

# The two propellers behind the stern corners, each on a shaft run aft from its bearings, turned by a belt over a
# spoked pulley.
PROP = dict(x=1.85, y=-2.95, z=2.0, r=1.05, bearing_y=-1.3, pulley_y=-2.35, pulley_r=0.36)

WHEEL = Vector((0.0, 1.72, 1.05))
LINE_OUT = Vector((0.0, 0.25, 0.0))    # where the hook's rope comes out under the keel
LINE_MAX = 32.0             # how far the hook's rope and the rope ladder both let down
LADDER_PITCH = 0.32         # from one rung of the ladder to the next
ROPE_SIDE = -1              # the coil and the ladder hang over her left side, as the film shows them
GATE_Y = -0.15              # where the ladder hangs, from the middle of that side
LADDER_TOP = Vector((ROPE_SIDE * (GON_HALF_WIDTH + 0.1), GATE_Y, GON_TOP + BEAM))
COIL_Y = 1.05               # the coil of rope over that side, by the bow
BOMB_RACK_Y = GON_ENGINE_FRONT + 0.16

# Where everyone stands: the pilot at the wheel, then two, two and three across.
PLACES = [(0.0, 1.3), (-0.45, 0.75), (0.45, 0.75), (-0.45, 0.2), (0.45, 0.2), (-0.55, -0.4), (0.0, -0.4),
          (0.55, -0.4)]

EMBLEM_HEIGHT = 5.4         # Vulgaria's arms on the envelope's flank
EMBLEM_Y = -1.6

# Vulgaria's colours.
BLACK = (22, 20, 24)
PURPLE = (92, 36, 104)
WHITE = (236, 232, 218)
GOLD = (214, 166, 72)
SHIELD_GOLD = (222, 170, 40)


# --- painted textures ----------------------------------------------------------------------------------

_EMBLEM = {}


def emblem(height=1680):
    """Vulgaria's arms as the airship wears them, drawn by vulgaria_arms.py from a frame of the film: a black griffin
    rearing up behind a shield quartered gold and black, facing left. RGBA, clear round it."""
    if height not in _EMBLEM:
        import vulgaria_arms
        _EMBLEM[height] = vulgaria_arms.render(height)
    return _EMBLEM[height]


ENV_TEX = (3072, 2048)      # pixels along the envelope (v) and round it (u)


def envelope_texture():
    """The envelope's doped cotton, laid out with v along her (tail at 0) and u round each section (0 at the top, a
    quarter on her right flank, three quarters on her left): seams between the panels; out from the white middle
    towards each end purple, black and purple bands and a black tip, each parted by a white stripe; and the arms on
    each flank, upright and facing the nose."""
    nv, nu = ENV_TEX
    tex = np.empty((nv, nu, 3), np.float32)
    tex[:] = np.array(WHITE, np.float32) / 255
    y = ENV_TAIL + (np.arange(nv) + 0.5) / nv * ENV_LENGTH
    # Each end: from the tip, the black tip, then purple, black and purple, white stripes between.
    stripe = 0.24
    bands = [(BLACK, 2.6), (PURPLE, 2.2), (BLACK, 2.4), (PURPLE, 2.4)]

    def paint(dist):
        out = np.full((nv, 3), np.nan, np.float32)
        edge = 0.0
        for colour, width in bands:
            rows = (dist >= edge) & (dist < edge + width)
            out[rows] = np.array(colour) / 255
            edge += width + stripe
        return out

    for dist in (ENV_NOSE - y, y - ENV_TAIL):
        c = paint(dist)
        rows = ~np.isnan(c[:, 0])
        tex[rows] = c[rows][:, None, :]
    # Seams: along her between the gores, and round her between the widths of cloth.
    seam = np.zeros((nv, nu), np.float32)
    seam[:, (np.arange(nu) % (nu // 24)) < 2] = 1
    seam[(np.arange(nv) % int(nv / ENV_LENGTH * 1.4)) < 2, :] = 1
    tex *= (1 - 0.07 * seam)[..., None]
    # The arms on each flank, facing the nose on both.
    e = emblem()
    circumference = 2 * math.pi * ENV_R * 0.965
    h_px = int(EMBLEM_HEIGHT / circumference * nu)
    w_px = int(EMBLEM_HEIGHT * e.width / e.height / ENV_LENGTH * nv)
    e = np.asarray(e.resize((w_px, h_px), Image.LANCZOS), np.float32) / 255     # (rows down, columns forward, RGBA)
    v0 = int((EMBLEM_Y - ENV_TAIL) / ENV_LENGTH * nv - w_px / 2)
    for u_mid, block in ((0.25, e[:, ::-1].transpose(1, 0, 2)), (0.75, e[::-1, ::-1].transpose(1, 0, 2))):
        u0 = int(u_mid * nu - h_px / 2)
        a = block[..., 3:4]
        tex[v0:v0 + w_px, u0:u0 + h_px] = tex[v0:v0 + w_px, u0:u0 + h_px] * (1 - a) + block[..., :3] * a
    rgba = np.concatenate([tex, np.ones((nv, nu, 1), np.float32)], axis=2)
    return rgba[::-1]   # top row first, as cm.image takes it


GON_PANEL = (2048, 660)     # the carved side from the engine section to the bow: 3.1 blocks along by 1.0 high


def gondola_carving():
    """Both carved sides, drawn by airship_carving.py, in one image: her right side on top, her left below, its B
    still reading forwards. (rgba, relief height, gilt mask)."""
    import airship_carving as ac
    assert ac.PANEL == GON_PANEL
    right, left = ac.side(True), ac.side(False)
    rgb = np.concatenate([right[0], left[0]], axis=0)
    rgba = np.concatenate([rgb, np.ones(rgb.shape[:2] + (1,), np.float32)], axis=2)
    return rgba, np.concatenate([right[1], left[1]], axis=0), np.concatenate([right[2], left[2]], axis=0)


def gondola_art():
    return gondola_carving()[0]


def engine_texture(size=512):
    """The engine section's sides: grey sheet metal, riveted round its edges, with two rows of four tall louvres."""
    img = Image.new('RGB', (size, size), (128, 128, 124))
    d = ImageDraw.Draw(img)
    for y in range(size):
        k = int(12 * math.sin(y / size * math.pi))
        d.line([(0, y), (size, y)], fill=(122 + k, 122 + k, 118 + k))
    for row in range(2):
        for i in range(4):
            x0 = size * (0.14 + 0.19 * i)
            y0 = size * (0.12 + 0.42 * row)
            d.rectangle([x0, y0, x0 + size * 0.09, y0 + size * 0.32], fill=(18, 18, 20))
            d.line([(x0, y0 + size * 0.32), (x0 + size * 0.09, y0 + size * 0.32)], fill=(170, 170, 166), width=3)
    for t in np.linspace(0.03, 0.97, 18):
        for x, y in ((t * size, size * 0.03), (t * size, size * 0.97), (size * 0.03, t * size), (size * 0.97, t * size)):
            d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(90, 90, 88))
    a = np.asarray(img, np.float32) / 255
    return np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], axis=2)


def lattice_texture(size=512, cells=8):
    """The platform under the envelope: a dark frame of girders crossed by netting."""
    img = Image.new('RGB', (size, size), (34, 30, 28))
    d = ImageDraw.Draw(img)
    step = size / cells
    for i in range(cells * 4):
        p = i * step / 2
        d.line([(p, 0), (p - size, size)], fill=(70, 62, 52), width=2)
        d.line([(p - size, 0), (p, size)], fill=(70, 62, 52), width=2)
    for i in range(cells + 1):
        d.line([(i * step, 0), (i * step, size)], fill=(120, 92, 54), width=7)
        d.line([(0, i * step), (size, i * step)], fill=(120, 92, 54), width=7)
    a = np.asarray(img, np.float32) / 255
    return np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], axis=2)


def tricolour_texture(size=256):
    """Purple, white and black in three bands across the chord, for the elevator."""
    tex = np.empty((size, size, 4), np.float32)
    tex[..., 3] = 1
    for i, c in enumerate((PURPLE, WHITE, BLACK)):
        tex[:, i * size // 3:(i + 1) * size // 3, :3] = np.array(c) / 255
    return tex


def deck_texture(size=512, planks=10):
    """Teak planks along the gondola, with dark seams."""
    rng = np.random.default_rng(5)
    tex = np.ones((size, size, 4), np.float32)
    base = np.array((150, 96, 52)) / 255
    for i in range(planks):
        k = 0.85 + 0.25 * rng.random()
        tex[:, i * size // planks:(i + 1) * size // planks, :3] = base * k
    tex[:, (np.arange(size) % (size // planks)) < 3, :3] *= 0.45
    grain = 0.06 * np.sin(np.arange(size)[:, None] / size * 90 + rng.random((1, size)) * 20)
    tex[..., :3] *= (1 + grain)[..., None]
    return np.clip(tex, 0, 1)


def write_textures(out):
    for name, rgba in (('envelope', envelope_texture()), ('gondola_side', gondola_art()), ('engine', engine_texture())):
        Image.fromarray((np.clip(rgba[..., :3], 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, name + '.png'))
    e = emblem()
    bg = Image.new('RGBA', e.size, WHITE + (255,))
    bg.alpha_composite(e)
    bg.convert('RGB').save(os.path.join(out, 'emblem.png'))


# --- materials -------------------------------------------------------------------------------------------

def gild(mat, height, gilt):
    """Make a carved material's gilding real: metallic where the gilt lies, raised by the carving's relief."""
    nt = mat.node_tree
    b = nt.nodes['Principled BSDF']

    def data(name, a):
        img = cm.image(name, np.concatenate([np.repeat(a[..., None], 3, axis=2), np.ones(a.shape + (1,))], axis=2))
        img.colorspace_settings.name = 'Non-Color'
        node = nt.nodes.new('ShaderNodeTexImage')
        node.image = img
        return node
    metal = nt.nodes.new('ShaderNodeMath')
    metal.operation = 'MULTIPLY'
    metal.inputs[1].default_value = 0.85
    nt.links.new(data(mat.name + '_gilt', gilt).outputs['Color'], metal.inputs[0])
    nt.links.new(metal.outputs['Value'], b.inputs['Metallic'])
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 1.0
    bump.inputs['Distance'].default_value = 0.006
    nt.links.new(data(mat.name + '_relief', height).outputs['Color'], bump.inputs['Height'])
    nt.links.new(bump.outputs['Normal'], b.inputs['Normal'])


def make_materials():
    m = cm.material
    m('envelope', WHITE, rough=0.85, spec=0.15, image=cm.image('envelope', envelope_texture()))
    m('fabric', WHITE, rough=0.85, spec=0.15)
    m('fin_dark', (46, 44, 48), rough=0.8, spec=0.15)
    m('tricolour', WHITE, rough=0.8, spec=0.15, image=cm.image('tricolour', tricolour_texture()))
    m('lattice', (34, 30, 28), rough=0.7, image=cm.image('lattice', lattice_texture()))
    m('bronze', (150, 104, 54), metal=1.0, rough=0.38)
    m('brass', (224, 174, 72), metal=1.0, rough=0.2)
    m('iron', (40, 40, 44), metal=0.8, rough=0.5)
    m('belt', (30, 24, 20), rough=0.7)
    m('cable', (44, 40, 36), rough=0.7)
    m('rope', (206, 192, 156), rough=0.95, spec=0.1)
    carved, height, gilt = gondola_carving()
    gild(m('gondola_side', (14, 13, 16), rough=0.3, coat=0.4, image=cm.image('gondola_side', carved)), height, gilt)
    m('engine', (128, 128, 124), metal=0.6, rough=0.45, image=cm.image('engine', engine_texture()))
    m('lacquer', (14, 13, 16), rough=0.3, coat=0.4)
    m('beam', (18, 17, 18), rough=0.45, coat=0.2)
    m('lining', (70, 34, 22), rough=0.4, coat=0.3)
    m('deck', (150, 96, 52), rough=0.55, image=cm.image('deck', deck_texture()))
    m('wood', (70, 40, 22), rough=0.4, coat=0.4)
    m('prop', (40, 30, 24), rough=0.35, coat=0.6)
    m('bomb', (24, 24, 26), metal=0.6, rough=0.45)


# --- the envelope ------------------------------------------------------------------------------------------

def envelope_profile(y):
    """The envelope's radius and the height of its axis at y: straight in the middle, tapering to points at both ends
    along a convex ogive, the axis rising a little towards each tip."""
    t = (y - ENV_MID_Y) / (ENV_LENGTH / 2)
    a = abs(t)
    if a <= ENV_STRAIGHT:
        return ENV_R * (1 - 0.02 * t * t), ENV_Z
    s = max(0.0, (1 - a) / (1 - ENV_STRAIGHT))     # 1 where the taper starts, 0 at the tip
    r = ENV_R * (1 - 0.02 * ENV_STRAIGHT ** 2) * (1 - (1 - s) ** 1.9) ** 0.85
    z = ENV_Z + ENV_R * ENV_TIP_RISE * (1 - s) ** 2
    return r, z


def envelope_section(r, zc, count=64):
    """A round section, a little flatter underneath; it starts at the top and goes round towards her right."""
    pts = []
    for i in range(count):
        t = math.pi / 2 - 2 * math.pi * i / count
        c, s = math.cos(t), math.sin(t)
        pts.append((r * c, zc + r * s * (1.0 if s > 0 else ENV_BELLY)))
    return pts


def build_envelope():
    m = cm.Mesh()
    n = 120
    ys = [ENV_MID_Y + ENV_LENGTH / 2 * math.sin(math.pi / 2 * (-1 + 2 * i / n)) * 0.9995 for i in range(n + 1)]
    sections = []
    for y in ys:
        r, zc = envelope_profile(y)
        sections.append((y, envelope_section(max(r, 0.02), zc)))
    rings = cm.loft(m, sections, 'envelope', v_scale=1 / ENV_LENGTH)
    # Close each end on its point.
    us = cm.perimeter_uv(sections[0][1])
    for ring, y_tip, v in ((rings[0], ENV_TAIL, 0.0), (rings[-1], ENV_NOSE, 1.0)):
        _, z_tip = envelope_profile(y_tip)
        tip = m.vert((0, y_tip, z_tip))
        k = len(ring)
        for i in range(k):
            j = (i + 1) % k
            m.face([ring[i], ring[j], tip], 'envelope', [(us[i], v), (us[i + 1], v), ((us[i] + us[i + 1]) / 2, v)])
    m.obj('envelope', smooth=40)


def platform_half_width(y):
    p = PLATFORM
    s = (y - p['back']) / (p['front'] - p['back'])
    return p['half_width'] * max(0.0, math.sin(math.pi * s)) ** 0.6


def build_platform():
    """The flat platform under the envelope: dark lattice and netting on a rim of girders, hung from the envelope's
    flanks by cables all along."""
    p = PLATFORM
    m = cm.Mesh()
    n = 40
    ys = [p['back'] + (p['front'] - p['back']) * i / n for i in range(n + 1)]
    top = []
    for y in ys:
        w = max(0.03, platform_half_width(y))
        top.append([m.vert((x, y, p['z'])) for x in (-w, w)])
    for a, b in zip(top, top[1:]):
        f = m.face([a[0], a[1], b[1], b[0]], 'lattice')
        cm.box_uv(m, [f], 0.25)
    o = m.obj('platform')
    cm.solidify(o, 0.08)
    m = cm.Mesh()
    rim = [Vector((platform_half_width(y), y, p['z'] - 0.04)) for y in ys]
    rim = rim + [Vector((-v.x, v.y, v.z)) for v in reversed(rim)]
    cm.tube(m, rim, 0.06, 'bronze', seg=8)
    m.obj('platform_rim', smooth=50)
    m = cm.Mesh()
    for y in ys[2:-2:2]:
        w = platform_half_width(y)
        r, zc = envelope_profile(y)
        for side in (-1, 1):
            a = math.radians(30)
            top_pt = Vector((side * r * math.cos(a), y, zc - r * math.sin(a) * ENV_BELLY))
            cm.tube(m, [top_pt, Vector((side * w, y, p['z']))], 0.022, 'cable', seg=5)
    m.obj('platform_cables')


# --- the frame over the gondola, the girder and the tail ---------------------------------------------------------

def build_frame():
    """Bronze tubes from the gondola's rail up to two long bars over the crew's heads, and from the bars up to the
    platform, in triangles as the film shows them; and rigging from the gondola's ends out to the platform."""
    m = cm.Mesh()
    y0, y1 = GON_STERN - BEAM_OVER, GON_BOW + BEAM_OVER
    for side in (-1, 1):
        x = side * GON_HALF_WIDTH
        cm.tube(m, [Vector((x, y0, BAR_Z)), Vector((x, y1, BAR_Z))], 0.05, 'bronze', seg=8)
        rail = [GON_STERN, -0.9, 0.3, 1.5, GON_BOW]
        bar = [-1.5, -0.3, 0.9, 2.1]
        for i, yr in enumerate(rail):
            for yb in (bar[i - 1] if i > 0 else None, bar[i] if i < len(bar) else None):
                if yb is not None:
                    cm.tube(m, [Vector((x, yr, GON_TOP + BEAM)), Vector((x, yb, BAR_Z))], 0.035, 'bronze', seg=6)
        for yb in (-2.2, -0.6, 1.0, 2.4):
            w = platform_half_width(yb) * 0.55
            cm.tube(m, [Vector((x, yb, BAR_Z)), Vector((side * w, yb + 0.6, PLATFORM['z']))], 0.035, 'bronze', seg=6)
            cm.tube(m, [Vector((x, yb, BAR_Z)), Vector((side * GIRDER['half_width'], yb, GIRDER['top']))], 0.03,
                    'bronze', seg=6)
        # Rigging from the ends of the rail out to the platform's rim.
        for ye, yp in ((y1, 3.2), (y0, -6.0), (0.0, 1.5), (0.0, -3.0)):
            cm.tube(m, [Vector((x, ye, GON_TOP + BEAM)), Vector((side * platform_half_width(yp), yp, PLATFORM['z']))],
                    0.014, 'cable', seg=4)
    for y in (y0 + 0.1, y1 - 0.1):
        cm.tube(m, [Vector((-GON_HALF_WIDTH, y, BAR_Z)), Vector((GON_HALF_WIDTH, y, BAR_Z))], 0.04, 'bronze', seg=6)
    m.obj('frame', smooth=50)


def girder_at(y):
    """The girder's top and its depth at y: under the platform as far as the platform's end, then down to the tail,
    tapering as it goes."""
    g = GIRDER
    if y >= PLATFORM['back']:
        return g['top'], g['depth']
    t = (PLATFORM['back'] - y) / (PLATFORM['back'] - g['back'])
    return g['top'] + (g['tail_top'] - g['top']) * t, g['depth'] * (1 - 0.35 * t)


def build_girder():
    """A narrow girder of three bronze chords laced with diagonals, from over the gondola back to the tail."""
    g = GIRDER
    m = cm.Mesh()
    n = 26
    ys = [g['front'] + (g['back'] - g['front']) * i / n for i in range(n + 1)]
    pts = []
    for y in ys:
        top, depth = girder_at(y)
        w = g['half_width'] * (depth / g['depth'])
        pts.append((Vector((0, y, top)), Vector((-w, y, top - depth)), Vector((w, y, top - depth))))
    for k in range(3):
        cm.tube(m, [p[k] for p in pts], 0.045, 'bronze', seg=8)
    for i, p in enumerate(pts):
        cm.tube(m, [p[1], p[2]], 0.025, 'bronze', seg=6)
        if i + 1 < len(pts):
            q = pts[i + 1]
            for a, b in ((p[0], q[1]), (p[0], q[2]), (p[1], q[0]) if i % 2 else (p[2], q[0])):
                cm.tube(m, [a, b], 0.022, 'bronze', seg=5)
    m.obj('girder', smooth=50)


def panel(m, corners, mat, uvs=None):
    """A flat panel of fabric (one face, drawn from either side) with a bronze rim."""
    vs = [m.vert(c) for c in corners]
    m.face(vs, mat, uvs or [(0, 0), (1, 0), (1, 1), (0, 1)][:len(vs)])
    cm.tube(m, list(corners) + [corners[0]], 0.035, 'bronze', seg=6)


def build_tail():
    """The tail at the girder's end, under the back of the envelope: a white tailplane with the elevator hinged
    behind it, painted in Vulgaria's stripes, and dark triangular fins above and below rising to their rear edge,
    where the rudder is hinged. The rudder and elevator turn about their hinges (their origins)."""
    T = TAIL
    m = cm.Mesh()
    z, lead, hinge = T['z'], T['lead'], T['hinge']
    panel(m, [Vector((-T['span'], lead, z)), Vector((T['span'], lead, z)), Vector((T['span'], hinge, z)),
              Vector((-T['span'], hinge, z))], 'fabric')
    panel(m, [Vector((0, lead, z)), Vector((0, hinge, z)), Vector((0, hinge, z + T['up']))], 'fin_dark',
          [(0, 0), (1, 0), (1, 1)])
    panel(m, [Vector((0, lead, z)), Vector((0, hinge, z - T['down'])), Vector((0, hinge, z))], 'fin_dark',
          [(0, 0), (1, 1), (1, 0)])
    # Bracing wires from the fins' tips to the tailplane's corners.
    for zz in (z + T['up'], z - T['down']):
        for side in (-1, 1):
            cm.tube(m, [Vector((0, hinge, zz)), Vector((side * T['span'], hinge, z))], 0.012, 'cable', seg=4)
    m.obj('tail_fixed')
    chord = T['trail'] - hinge
    m = cm.Mesh()
    panel(m, [Vector((0, 0, -T['down'])), Vector((0, chord, -T['down'])), Vector((0, chord, T['up'])),
              Vector((0, 0, T['up']))], 'fin_dark')
    o = m.obj('rudder', location=(0, hinge, z), part='rudder')
    o['axis'] = 'z'
    m = cm.Mesh()
    panel(m, [Vector((-T['span'], 0, 0)), Vector((-T['span'], chord, 0)), Vector((T['span'], chord, 0)),
              Vector((T['span'], 0, 0))], 'tricolour', [(0, 0), (1, 0), (1, 1), (0, 1)])
    o = m.obj('elevator', location=(0, hinge, z), part='elevator')
    o['axis'] = 'x'


# --- the gondola ---------------------------------------------------------------------------------------------

def gondola_outline():
    """Her side's outline in (y, z), from the top of the bow down the raked bow, aft along the bottom, up the
    engine section's sloping floor and the stern."""
    return [(GON_BOW, GON_TOP), (GON_BOW_FOOT, 0.0), (GON_ENGINE_FRONT, 0.0), (GON_STERN, GON_STERN_RISE),
            (GON_STERN, GON_TOP)]


def bottom_at(y):
    """The gondola's bottom at y."""
    if y > GON_BOW_FOOT:
        return GON_TOP * (y - GON_BOW_FOOT) / (GON_BOW - GON_BOW_FOOT)
    if y < GON_ENGINE_FRONT:
        return GON_STERN_RISE * (GON_ENGINE_FRONT - y) / (GON_ENGINE_FRONT - GON_STERN)
    return 0.0


def build_gondola():
    """The gondola: straight black sides carved and gilded from the bow back to the engine section, which is grey
    sheet metal with louvres and covered over; the bow raked, the bottom flat, the stern's floor sloping up. Lined in
    mahogany inside, with a planked floor and the heavy black rail along the top on iron brackets, running on past
    both ends."""
    m = cm.Mesh()
    hw = GON_HALF_WIDTH
    out = gondola_outline()
    H = GON_PANEL[1] / (2 * GON_PANEL[1])   # each side has half the carved image

    def side_uv(y, z, right):
        u = (y - GON_ENGINE_FRONT) / (GON_BOW - GON_ENGINE_FRONT)
        v = z / GON_TOP
        return (u if right else 1 - u, (0.5 + 0.5 * v) if right else 0.5 * v)

    def engine_uv(y, z, right):
        u = (y - GON_STERN) / (GON_ENGINE_FRONT - GON_STERN)
        return (u if right else 1 - u, z / GON_TOP)

    for side in (-1, 1):
        right = side > 0
        carved = [(GON_BOW, GON_TOP), (GON_BOW_FOOT, 0.0), (GON_ENGINE_FRONT, 0.0), (GON_ENGINE_FRONT, GON_TOP)]
        engine = [(GON_ENGINE_FRONT, GON_TOP), (GON_ENGINE_FRONT, 0.0), (GON_STERN, GON_STERN_RISE),
                  (GON_STERN, GON_TOP)]
        for poly, mat, uvf in ((carved, 'gondola_side', side_uv), (engine, 'engine', engine_uv)):
            pts = poly if right else list(reversed(poly))
            vs = [m.vert((side * hw, y, z)) for y, z in pts]
            m.face(vs, mat, [uvf(y, z, right) for y, z in pts])
    # The bow, the bottom, the engine's sloping floor and the stern, joining the two sides.
    for i in range(len(out)):
        (ya, za), (yb, zb) = out[i], out[(i + 1) % len(out)]
        if i == len(out) - 1:
            continue    # the top is open
        mat = 'engine' if ya <= GON_ENGINE_FRONT + 1e-6 and yb <= GON_ENGINE_FRONT + 1e-6 else 'lacquer'
        vs = [m.vert((-hw, ya, za)), m.vert((hw, ya, za)), m.vert((hw, yb, zb)), m.vert((-hw, yb, zb))]
        f = m.face(vs, mat)
        cm.box_uv(m, [f], 0.9)
    m.obj('gondola_hull')
    # The lining, the floor, the engine section's front wall and lid.
    m = cm.Mesh()
    k = 0.05
    bow_at_floor = GON_BOW_FOOT + (GON_BOW - GON_BOW_FOOT) * GON_FLOOR / GON_TOP
    for side in (-1, 1):
        lining = [(GON_BOW - k, GON_TOP), (bow_at_floor - k, GON_FLOOR), (GON_ENGINE_FRONT, GON_FLOOR),
                  (GON_ENGINE_FRONT, GON_TOP)]
        vs = [m.vert((side * (hw - k), y, z)) for y, z in (lining if side < 0 else list(reversed(lining)))]
        f = m.face(vs, 'lining')
        cm.box_uv(m, [f], 1.0)
    rake = math.atan2(GON_BOW - GON_BOW_FOOT, GON_TOP)
    zm = (GON_FLOOR + GON_TOP) / 2
    ym = GON_BOW_FOOT + (GON_BOW - GON_BOW_FOOT) * zm / GON_TOP - k
    cm.add_box(m, (0, ym, zm), (2 * hw - 0.1, 0.02, (GON_TOP - GON_FLOOR) / math.cos(rake)), 'lining',
               rot=Matrix.Rotation(-rake, 4, 'X'))
    floor_end = GON_BOW_FOOT + (GON_BOW - GON_BOW_FOOT) * (GON_FLOOR - 0.06) / GON_TOP - 0.02
    cm.add_box(m, (0, (GON_ENGINE_FRONT + floor_end) / 2, GON_FLOOR - 0.03),
               (2 * hw - 0.1, floor_end - GON_ENGINE_FRONT, 0.06), 'deck', uv_scale=0.5)
    cm.add_box(m, (0, GON_ENGINE_FRONT, (GON_FLOOR + GON_TOP) / 2), (2 * hw - 0.04, 0.05, GON_TOP - GON_FLOOR), 'engine',
               uv_scale=1 / (2 * hw))
    cm.add_box(m, (0, (GON_STERN + GON_ENGINE_FRONT) / 2, GON_TOP - 0.02),
               (2 * hw, GON_ENGINE_FRONT - GON_STERN, 0.04), 'engine', uv_scale=1 / (2 * hw))
    m.obj('gondola_inside')
    # The rail: a heavy square black beam down each side and across each end, on iron brackets, the side beams
    # running on past the ends.
    m = cm.Mesh()
    zb = GON_TOP + BEAM / 2 + 0.01
    y0, y1 = GON_STERN - BEAM_OVER, GON_BOW + BEAM_OVER
    for side in (-1, 1):
        cm.add_box(m, (side * (hw - BEAM / 2 + 0.03), (y0 + y1) / 2, zb), (BEAM, y1 - y0, BEAM), 'beam')
        for y in np.linspace(GON_STERN + 0.15, GON_BOW - 0.15, 7):
            cm.add_box(m, (side * (hw + 0.02), y, GON_TOP - 0.04), (0.03, 0.1, 0.22), 'iron')
        for y in (y0 + 0.06, y1 - 0.06):
            cm.add_box(m, (side * (hw - BEAM / 2 + 0.03), y, zb), (BEAM + 0.02, 0.04, BEAM + 0.02), 'iron')
    for y in (GON_STERN + BEAM / 2, GON_BOW - BEAM / 2):
        cm.add_box(m, (0, y, zb), (2 * hw, BEAM, BEAM), 'beam')
    m.obj('gondola_rail')
    for i, (x, y) in enumerate(PLACES):
        cm.empty('stand_%d' % i, (x, y, GON_FLOOR))
    cm.empty('line', tuple(LINE_OUT))
    cm.empty('exhaust', (0.6, GON_STERN + 0.3, GON_TOP + 0.5))
    m = cm.Mesh()
    pipe = [Vector((0.6, GON_STERN + 0.3, GON_TOP)), Vector((0.6, GON_STERN + 0.3, GON_TOP + 0.35)),
            Vector((0.6, GON_STERN + 0.15, GON_TOP + 0.5))]
    cm.tube(m, cm.catmull(pipe, per=5), 0.05, 'iron', seg=8)
    # The hatch the hook's rope goes down through, and a brass fairlead under the keel.
    cm.add_box(m, (0, LINE_OUT.y, GON_FLOOR + 0.005), (0.36, 0.36, 0.02), 'brass')
    cm.lathe(m, [(0.05, -0.04), (0.1, -0.04), (0.1, 0.02), (0.05, 0.02)], lambda k: 'brass', axis='z', seg=16,
             origin=tuple(LINE_OUT))
    m.obj('gondola_fittings', smooth=40)


def pipe(m, path, radius, mat, seg=12, flare=None, closed=False):
    """A round pipe along a path like cm.tube, but with its rings carried along the path without twisting, so it
    stays smooth however the path turns (cm.tube's rings flip where the path passes upright); closed joins its ends
    round into a ring."""
    pts = [Vector(p) for p in path]
    n = len(pts)

    def tangent(i):
        if closed:
            return (pts[(i + 1) % n] - pts[(i - 1) % n]).normalized()
        return (pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)]).normalized()
    t = tangent(0)
    ref = Vector((0, 0, 1)) if abs(t.z) < 0.9 else Vector((1, 0, 0))
    a = t.cross(ref).normalized()
    rings = []
    for i, p in enumerate(pts):
        t = tangent(i)
        a = (a - t * a.dot(t)).normalized()
        b = t.cross(a)
        r = radius * (flare(i / (n - 1)) if flare else 1.0)
        rings.append([m.vert(p + (a * math.cos(2 * math.pi * k / seg) + b * math.sin(2 * math.pi * k / seg)) * r)
                      for k in range(seg)])
    if closed:
        # Line the last ring up with the first, so they join without a twist.
        first, last = rings[0], rings[-1]
        shift = min(range(seg), key=lambda k: (last[k].co - first[0].co).length)
        rings.append(first[-shift:] + first[:-shift] if shift else first)
    for i in range(len(rings) - 1):
        for k in range(seg):
            j = (k + 1) % seg
            m.face([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]], mat)
    if not closed:
        m.face(list(reversed(rings[0])), mat)
        m.face(rings[-1], mat)


def rope(m, path, radius, mat, lay=5.5):
    """A three-strand rope along a path: three strands twisted round each other, a full turn every `lay` times the
    rope's thickness."""
    pts = [Vector(p) for p in path]
    # Resample finely enough for the twist, by length.
    dense = [pts[0]]
    for a, b in zip(pts, pts[1:]):
        n = max(1, int((b - a).length / (radius * 0.9)))
        dense += [a.lerp(b, (i + 1) / n) for i in range(n)]
    # Frames carried along the path without twisting of their own.
    t0 = (dense[1] - dense[0]).normalized()
    ref = Vector((0, 0, 1)) if abs(t0.z) < 0.9 else Vector((1, 0, 0))
    u = t0.cross(ref).normalized()
    frames, s = [], 0.0
    for i, p in enumerate(dense):
        t = (dense[min(i + 1, len(dense) - 1)] - dense[max(i - 1, 0)]).normalized()
        u = (u - t * u.dot(t)).normalized()
        if i:
            s += (p - dense[i - 1]).length
        frames.append((p, u, t.cross(u), s))
    for k in range(3):
        strand = []
        for p, a, b, s in frames:
            th = 2 * math.pi * (s / (lay * radius * 2) + k / 3)
            strand.append(p + (a * math.cos(th) + b * math.sin(th)) * radius * 0.46)
        pipe(m, strand, radius * 0.6, mat, seg=6)


def build_coil():
    """The coil of rope hung over her left side at the bow, as the film has it: a long hank of cream rope draped over
    the rail, its loops hanging down the carved side in U's, overlapping and splayed a little along her. It is the
    hook's rope, and grows smaller as the rope goes out."""
    m = cm.Mesh()
    rng = np.random.default_rng(11)
    for k in range(18):
        cy = -0.45 + 0.9 * k / 17 + rng.normal(0, 0.03)
        half = 0.32 + 0.12 * rng.random()          # half the width of the loop, along her
        drop = 0.62 + 0.14 * rng.random()          # how far it hangs below the rail
        x = ROPE_SIDE * (0.05 + 0.012 * (k % 5))
        loop = []
        for t in np.linspace(0, math.pi, 17):
            loop.append(Vector((x, cy + half * math.cos(t), -drop * math.sin(t) ** 0.8)))
        # Over the top of the beam and back.
        loop = [Vector((x - ROPE_SIDE * 0.12, cy + half, 0.06))] + loop + [Vector((x - ROPE_SIDE * 0.12, cy - half, 0.06))]
        rope(m, cm.catmull(loop, per=3), 0.038, 'rope')
    m.obj('coil', location=(ROPE_SIDE * GON_HALF_WIDTH, COIL_Y, GON_TOP + BEAM), part='coil', smooth=40)


def blade(m, length, root, tip, pitch0, pitch1, turn, thick=0.05):
    """One propeller blade out from the hub, turned `turn` radians about the shaft (y): a paddle, widest two thirds
    out, its pitch easing off from pitch0 at the root to pitch1 at the tip (degrees from the plane of the disc)."""
    n = 10
    rot = Matrix.Rotation(turn, 3, 'Y')
    rings = []
    for i in range(n + 1):
        t = i / n
        r = root + (length - root) * t
        chord = tip + (0.26 - tip) * math.sin(math.pi * min(1.0, t * 1.3)) if t < 0.95 else tip
        ang = math.radians(pitch0 + (pitch1 - pitch0) * t)
        along = Vector((0, math.sin(ang), math.cos(ang)))
        across = Vector((0, math.cos(ang), -math.sin(ang)))
        rings.append([m.vert(rot @ (Vector((r, 0, 0)) + along * c + across * d))
                      for c, d in ((-chord / 2, -thick / 2), (chord / 2, -thick / 2), (chord / 2, thick / 2),
                                   (-chord / 2, thick / 2))])
    for a, b in zip(rings, rings[1:]):
        for k in range(4):
            j = (k + 1) % 4
            m.face([a[k], a[j], b[j], b[k]], 'prop')
    m.face(list(reversed(rings[0])), 'prop')
    m.face(rings[-1], 'prop')


def build_props():
    """The two propellers behind the stern corners, pushing her along, each on a shaft run aft from two bearings on
    the frame, turned by a belt from the engine over a spoked iron pulley. The propellers and the pulleys turn about
    the shafts (y); the left one is the right one mirrored."""
    P = PROP
    for side, name in ((1, 'prop_r'), (-1, 'prop_l')):
        x = side * P['x']
        # The shaft, its bearings and struts.
        m = cm.Mesh()
        cm.tube(m, [Vector((x, P['bearing_y'], P['z'])), Vector((x, P['y'] + 0.15, P['z']))], 0.05, 'bronze', seg=10)
        for yb in (P['bearing_y'], P['y'] + 0.45):
            cm.lathe(m, [(0.0, -0.1), (0.1, -0.1), (0.1, 0.1), (0.0, 0.1)], lambda k: 'iron', axis='y', seg=12,
                     origin=(x, yb, P['z']))
            cm.tube(m, [Vector((x, yb, P['z'])), Vector((side * GON_HALF_WIDTH, yb, BAR_Z))], 0.035, 'bronze', seg=6)
        for a in ((side * GON_HALF_WIDTH, GON_ENGINE_FRONT, GON_TOP + BEAM), (side * GON_HALF_WIDTH, GON_STERN,
                                                                              GON_TOP + BEAM)):
            cm.tube(m, [Vector(a), Vector((x, P['y'] + 0.45 if a[1] < -1.5 else P['bearing_y'], P['z']))], 0.035,
                    'bronze', seg=6)
        cm.tube(m, [Vector((x, P['bearing_y'], P['z'])), Vector((side * GIRDER['half_width'], P['bearing_y'],
                                                                 GIRDER['top'] - GIRDER['depth']))], 0.03, 'bronze', seg=6)
        # The belt: from a small pulley on the engine section's side up over the big one, both runs straight.
        lo = Vector((side * (GON_HALF_WIDTH + 0.06), P['pulley_y'], 0.62))
        hi = Vector((x, P['pulley_y'], P['z']))
        d = (hi - lo)
        d.normalize()
        n = Vector((0, 1, 0)).cross(d)
        for r_hi, r_lo, s in ((P['pulley_r'], 0.1, 1), (P['pulley_r'], 0.1, -1)):
            cm.tube(m, [lo + n * r_lo * s, hi + n * r_hi * s], 0.02, 'belt', seg=4)
        cm.lathe(m, [(0.0, -0.05), (0.12, -0.05), (0.12, 0.05), (0.0, 0.05)], lambda k: 'iron', axis='y', seg=12,
                 origin=tuple(lo))
        m.obj(name + '_shaft', smooth=40)
        # The pulley: a rim, five spokes and a hub, turning with the propeller.
        m = cm.Mesh()
        r = P['pulley_r']
        rim = [Vector((math.cos(t) * r, 0, math.sin(t) * r)) for t in np.linspace(0, 2 * math.pi, 41)]
        cm.tube(m, rim, 0.04, 'iron', seg=8)
        for k in range(5):
            t = 2 * math.pi * k / 5
            cm.tube(m, [Vector((0, 0, 0)), Vector((math.cos(t) * r, 0, math.sin(t) * r))], 0.022, 'iron', seg=6)
        cm.lathe(m, [(0.0, -0.08), (0.07, -0.08), (0.07, 0.08), (0.0, 0.08)], lambda k: 'iron', axis='y', seg=12)
        m.obj(name + '_pulley', location=(x, P['pulley_y'], P['z']), part=name + '_pulley', smooth=40)
        # The propeller.
        m = cm.Mesh()
        for k in (0, 1):
            blade(m, P['r'], 0.1, 0.1, 60, 16, math.pi * k)
        cm.lathe(m, [(0.0, -0.14), (0.12, -0.12), (0.13, 0.0), (0.11, 0.1), (0.0, 0.13)], lambda k: 'iron', axis='y',
                 seg=16)
        o = m.obj(name, location=(x, P['y'], P['z']), part=name, smooth=30)
        if side < 0:
            o.scale.x = -1


def build_wheel():
    """The steering wheel at the bow, as on a Lebaudy: eight spokes round a brass hub on a brass post, facing the pilot.
    It turns as she is steered."""
    m = cm.Mesh()
    r = 0.28
    ring = [Vector((math.cos(a) * r, 0, math.sin(a) * r)) for a in np.linspace(0, 2 * math.pi, 41)]
    cm.tube(m, ring, 0.028, 'wood', seg=8)
    for k in range(8):
        a = 2 * math.pi * k / 8
        d = Vector((math.cos(a), 0, math.sin(a)))
        cm.tube(m, [d * 0.04, d * (r + 0.1)], 0.018, 'wood', seg=6)
    cm.lathe(m, [(0.0, -0.06), (0.06, -0.06), (0.06, 0.04), (0.0, 0.05)], lambda k: 'brass', axis='y', seg=16)
    m.obj('wheel', location=tuple(WHEEL), part='wheel', smooth=40)
    m = cm.Mesh()
    cm.lathe(m, [(0.0, GON_FLOOR), (0.1, GON_FLOOR), (0.045, GON_FLOOR + 0.08), (0.035, WHEEL.z - 0.06),
                 (0.06, WHEEL.z), (0.0, WHEEL.z + 0.02)], lambda k: 'brass', axis='z', seg=16,
             origin=(WHEEL.x, WHEEL.y + 0.1, 0))
    m.obj('wheel_post', smooth=40)


def build_bombs():
    """Six bombs stood nose down in a rack across the front of the engine section: black iron teardrops with four fins
    and a brass fuse on the nose, smaller than a block of TNT. Each is its own part, so the game can take them from the
    rack one by one."""
    m = cm.Mesh()
    inner = GON_HALF_WIDTH - 0.08
    for z in (GON_FLOOR + 0.1, GON_FLOOR + 0.38):
        cm.add_box(m, (0, BOMB_RACK_Y, z), (2 * inner, 0.22, 0.04), 'wood')
    m.obj('bomb_rack')
    for i in range(6):
        x = -0.7 + 0.28 * i
        m = cm.Mesh()
        cm.lathe(m, [(0.0, -0.25), (0.03, -0.24), (0.04, -0.21), (0.09, -0.13), (0.105, -0.04), (0.1, 0.05),
                     (0.065, 0.14), (0.036, 0.2), (0.036, 0.25), (0.0, 0.25)],
                 lambda k: 'brass' if k < 2 else 'bomb', axis='z', seg=16)
        for k in range(4):
            a = math.pi / 4 + k * math.pi / 2
            cm.add_box(m, (math.cos(a) * 0.075, math.sin(a) * 0.075, 0.2), (0.08, 0.01, 0.11), 'bomb',
                       rot=Matrix.Rotation(a, 4, 'Z'))
        m.obj('bomb_%d' % i, location=(x, BOMB_RACK_Y, GON_FLOOR + 0.33), part='bomb_%d' % i, smooth=40)


def build_hook():
    """The grapple: a forged iron grappling hook, a ring at the top where the rope ties on, a long shank, and at its
    foot a crown of four tines that curve out and up to barbed points. Its origin is the top of the ring."""
    m = cm.Mesh()
    ring = [Vector((0, math.sin(2 * math.pi * k / 48) * 0.11, -0.13 + math.cos(2 * math.pi * k / 48) * 0.11))
            for k in range(48)]
    pipe(m, ring, 0.032, 'iron', seg=10, closed=True)
    cm.lathe(m, [(0.0, -0.22), (0.05, -0.24), (0.05, -0.3), (0.045, -0.32), (0.055, -0.9), (0.075, -1.02),
                 (0.09, -1.08), (0.07, -1.16), (0.0, -1.18)], lambda k: 'iron', axis='z', seg=12)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        c, s = math.cos(a), math.sin(a)

        def at(r, z):
            return Vector((c * r, s * r, z))

        tine = [at(0.04, -1.1), at(0.22, -1.17), at(0.42, -1.12), at(0.56, -0.97), at(0.61, -0.78), at(0.57, -0.6),
                at(0.49, -0.5)]
        pipe(m, cm.catmull(tine, per=8), 0.055, 'iron', seg=12, flare=lambda t: 1.0 - 0.82 * t ** 1.6)
        # The barb: a spur turned back down the inside of the tine, below its point.
        pipe(m, [at(0.575, -0.66), at(0.5, -0.71), at(0.45, -0.78)], 0.022, 'iron', seg=6,
             flare=lambda t: 1.0 - 0.8 * t)
    m.obj('hook', part='hook', smooth=40)


def build_ladder():
    """The rope ladder from the middle of her left side, which lets down as far as the hook (LINE_MAX), as the film
    has it hanging there. It is one rung's length of it, two ropes and a wooden rung, which the game stacks down from
    the top as far as it is let out (the renders do the same with an array). It hangs from two iron hooks on the rail;
    drawn up, it lies in the bottom of the gondola, out of sight."""
    m = cm.Mesh()
    for side in (-0.2, 0.2):
        cm.tube(m, [Vector((0, side, 0)), Vector((0, side, -LADDER_PITCH))], 0.025, 'rope', seg=6)
    cm.add_box(m, (0, 0, -LADDER_PITCH / 2), (0.07, 0.46, 0.05), 'wood')
    o = m.obj('ladder', location=tuple(LADDER_TOP), part='ladder', smooth=40)
    o['pitch'] = LADDER_PITCH
    m = cm.Mesh()
    for y in (-0.2, 0.2):
        hook = [Vector((ROPE_SIDE * x, y, z)) for x, z in ((-0.06, 0.0), (0.02, 0.0), (0.06, -0.08), (0.02, -0.14))]
        cm.tube(m, cm.catmull(hook, per=4), 0.018, 'iron', seg=6)
    m.obj('ladder_hooks', location=tuple(LADDER_TOP))
    cm.empty('ladder_top', tuple(LADDER_TOP))


def build_rope():
    """The hook's rope, a unit long: the renders stretch it down to the hook (the game draws its own)."""
    m = cm.Mesh()
    cm.tube(m, [Vector((0, 0, 0)), Vector((0, 0, -1))], 0.03, 'rope', seg=8)
    m.obj('rope', location=tuple(LINE_OUT), part='rope')


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    make_materials()
    build_envelope()
    build_platform()
    build_frame()
    build_girder()
    build_tail()
    build_gondola()
    build_coil()
    build_props()
    build_wheel()
    build_bombs()
    build_hook()
    build_ladder()
    build_rope()


# --- poses -------------------------------------------------------------------------------------------------

def pose(spin=0.0, steer=0.0, climb=0.0, hook=None, ladder=0.0, bombs=6):
    """What the game's animation does: the propellers and their pulleys turned by spin (radians), the rudder and
    wheel by steer and the elevator by climb (-1 to 1), the hook let down that many blocks below the keel (None:
    drawn up under her), the ladder let down that many blocks (0: drawn up) and that many bombs left in
    the rack."""
    drop = 0.12 if hook is None else hook
    for o in bpy.data.objects:
        part = o.get('part', '')
        if part in ('prop_r', 'prop_l', 'prop_r_pulley', 'prop_l_pulley'):
            o.rotation_euler = (0, spin * (1 if part.startswith('prop_r') else -1), 0)
        elif part == 'rudder':
            o.rotation_euler = (0, 0, math.radians(-25) * steer)
        elif part == 'elevator':
            o.rotation_euler = (math.radians(20) * climb, 0, 0)
        elif part == 'wheel':
            o.rotation_euler = (0, math.radians(120) * steer, 0)
        elif part == 'coil':
            k = 1.0 - 0.55 * min(drop, LINE_MAX) / LINE_MAX
            o.scale = (1, k, k)
        elif part == 'hook':
            o.location = LINE_OUT + Vector((0, 0, -drop))
        elif part == 'rope':
            o.scale = (1, 1, max(drop, 0.01))
        elif part == 'ladder':
            rungs = int(round(min(ladder, LINE_MAX) / LADDER_PITCH))
            o.hide_render = o.hide_viewport = rungs == 0
            arr = o.modifiers.get('rungs') or o.modifiers.new('rungs', 'ARRAY')
            arr.use_relative_offset = False
            arr.use_constant_offset = True
            arr.constant_offset_displace = (0, 0, -LADDER_PITCH)
            arr.count = max(rungs, 1)
        elif part.startswith('bomb_'):
            o.hide_render = o.hide_viewport = int(part[5:]) >= bombs


# --- output ------------------------------------------------------------------------------------------------

def render(out, name, cam_loc, look_at, lens=35, lift=0.0, size=(1600, 900),
           samples=int(os.environ.get('AIRSHIP_SAMPLES', '64'))):
    """Renders her from cam_loc towards look_at, raised lift blocks off the ground."""
    scene = bpy.context.scene
    cm.render_scene_setup()
    scene.cycles.samples = samples
    scene.render.resolution_x, scene.render.resolution_y = size
    ground = bpy.data.objects['ground']
    ground.scale = (12, 12, 1)
    # A dull, darker field under her, so that her black and white are not tinted green by a bright lawn.
    cm.MATS['grass'].node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = cm.srgb((74, 84, 62))
    bpy.data.objects['water'].hide_render = True
    cam = scene.camera
    cam.data.clip_end = 2000
    for o in bpy.data.objects:
        if o.get('part') is not None and o.parent is None:
            o.delta_location = (0, 0, lift)
    cam.location = Vector(cam_loc)
    cam.rotation_euler = (Vector(look_at) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    scene.render.filepath = os.path.join(out, name + '.png')
    bpy.ops.render.render(write_still=True)
    for o in bpy.data.objects:
        o.delta_location = (0, 0, 0)


SHOTS = [
    # name, camera, look at, lens, lift, pose
    ('side', (56, -3.6, 14), (0, -3.6, 13.5), 34, 9.0, dict(spin=0.5)),
    ('film_flight', (-48, 22, 26), (0, -3.6, 13), 34, 9.0, dict(spin=1.0)),
    ('below', (-7, 15, 0.6), (0, -1.5, 11), 22, 9.0, dict(spin=0.9)),
    ('arms', (-17, -1.6, 17.4), (0, -1.6, 16.4), 40, 9.0, dict(spin=0.9)),
    ('three_quarter', (-32, 28, 22), (0, -4, 12), 30, 9.0, dict(spin=1.3, steer=0.4)),
    ('front', (5, 46, 11), (0, 0, 13), 32, 9.0, dict(spin=0.2)),
    ('rear', (-15, -44, 15), (0, -11, 11), 30, 9.0, dict(spin=0.8, steer=-0.5, climb=0.4)),
    ('tail', (10, -10, 12.6), (0, -17, 10.6), 30, 9.0, dict(spin=0.8, steer=-0.4, climb=0.3)),
    ('gondola', (-6.4, 0.9, 9.6), (0, -0.3, 10.2), 30, 9.0, dict(spin=0.3, ladder=1.6)),
    ('gondola_right', (6.4, 1.2, 10.2), (0, -0.1, 10.0), 30, 9.0, dict(spin=0.3)),
    ('props', (-4.6, -6.4, 11.8), (-1.6, -2.4, 10.8), 30, 9.0, dict(spin=0.6)),
    ('deck', (3.6, -4.6, 12.0), (0, 0.3, 9.4), 26, 9.0, dict(spin=0.6)),
    ('hook_ladder', (24, 16, 8.0), (0, 0, 11.0), 30, 20.0, dict(spin=0.4, hook=17.0, ladder=17.0, bombs=4)),
    ('grapple', (2.4, 3.1, 5.3), (0, 0.25, 5.5), 40, 9.0, dict(spin=0.3, hook=3.0)),
    ('landed', (14, 15, 2.6), (0, -1, 3.8), 28, 0.0, dict(spin=0.0)),
]


def main():
    args = sys.argv[1:]
    if '--out' not in args:
        print(__doc__)
        return
    out = args[args.index('--out') + 1]
    os.makedirs(out, exist_ok=True)
    if '--textures' in args:
        write_textures(out)
    build()
    pose()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'airship.blend'), check_existing=False)
    if '--renders' in args:
        only = args[args.index('--only') + 1].split(',') if '--only' in args else None
        for name, cam, at, lens, lift, p in SHOTS:
            if only and name not in only:
                continue
            pose(**p)
            render(out, name, cam, at, lens, lift)
        pose()


if __name__ == '__main__':
    main()
