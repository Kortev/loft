"""Builds the Eternity Devil in Blender (Chainsaw Man mod): the look kortev is asked to approve before anything goes in the game.

Run from the repository root with Blender (Pillow comes from the folder named in ETERNITY_PIL, see hotel_textures.py):

    blender --background --python tools/eternity_devil_model.py -- --out work/eternity_look_v2

It writes DIR/eternity_devil.blend, DIR/eternity_devil_atlas.png (the texture, 16 pixels to a block, painted flat the way
Minecraft textures are) and DIR/eternity_devil.glb. The scene renderer (tools/eternity_look_scenes.py) imports `build_devil()`.

Blender units are blocks. The devil faces +Y with +X on its right and +Z up: the floor of the corridor is Z = 0, the mass
spans X -3..3 (the corridor is 3 wide, so it bursts into the rooms either side), Z 0..4 (a block into the ceiling slab)
and Y -10..0 (its face at Y = 0, the viewer on +Y).

One meaty mass with a face, not a pile of lumps: a single rounded block of flesh pushed about by low folds and creases, with
longer growths hugging the walls, floor and ceiling towards you, and a big central mouth with seven smaller ones.

Every part that moves in the game is its own object with its origin on its pivot, parented to the Empty `mass_pulse` (scale it
a few percent, slowly, to breathe):
  mouth_NN_jaw     the lower half of a mouth (with the teeth on it; the big one's tongue too): rotate about local X to open it
  mouth_NN_upper   the upper half (static, so its own object)
  eye_NN           an eyeball: turn it
  eye_NN_lid_up / eye_NN_lid_dn   its lids: rotate about local X to blink (0 shut, ~0.5 rad wide)
  heart            deep in the big mouth, behind
  heart_cover      the flesh over it: hide it when the flesh is cut away
  body, growth_NN  the mass itself
Empties mark where each mouth is (mouth_NN_at), with the local axes the parts use.
"""
import math
import os
import random
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Matrix, Vector, noise
from mathutils.bvhtree import BVHTree

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hotel_textures as ht  # noqa: E402  (also puts Pillow on the path inside Blender)
from PIL import Image, ImageDraw  # noqa: E402

OUT = os.path.abspath(os.path.join(HERE, '..', 'work', 'eternity_look_v2'))

# --- layout (blocks) -----------------------------------------------------------------------------------------------

HX, HY, HZ = 3.4, 5.0, 2.2            # half sizes of the mass
CENTRE = Vector((0.0, -5.0, 2.0))
ROUND = 0.5                          # the rounding of its edges
PX = 16                               # pixels to a block

# (x, z, rx, rz, depth) the mouths on the face; the big one first.
MOUTHS = [
    ('front', 0.0, 1.0, 1.25, 0.74, 1.8),
    ('front', -1.95, 0.95, 0.52, 0.26, 0.5),
    ('front', 1.95, 1.75, 0.46, 0.24, 0.5),
    ('front', -1.75, 3.3, 0.42, 0.22, 0.45),
    ('front', 1.8, 3.3, 0.4, 0.2, 0.45),
    ('front', -2.35, 2.15, 0.36, 0.2, 0.4),
    ('right', -1.6, 1.8, 0.7, 0.28, 0.5),       # on the +X side, (y, z)
    ('left', -2.6, 2.6, 0.55, 0.24, 0.45),      # on the -X side
]

# --- the atlas ---------------------------------------------------------------------------------------------------

PAL = {
    'grey': ((188, 130, 128), (176, 118, 120), (200, 142, 138), (140, 90, 94)),
    'pink': ((200, 104, 96), (188, 94, 90), (214, 122, 110), (140, 60, 64)),
    'red': ((172, 60, 64), (156, 48, 58), (190, 74, 76), (108, 30, 42)),
}
INK = (34, 12, 18)
TOOTH = (238, 228, 204)
TOOTH_SHADE = (200, 188, 160)
EYE_SIZES = (6, 8, 10, 12, 16, 20)

TILE = 32
PAD = 6


class Atlas:
    """The texture: flat painted tiles, each stored with a wrapped border so a face can run over its edge."""

    def __init__(self):
        self.size = 256
        self.img = Image.new('RGB', (self.size, self.size), (255, 0, 255))
        self.at = {}
        self.cursor = [PAD, PAD, 0]  # x, y, row height

    def _alloc(self, w, h, pad):
        cx, cy, rh = self.cursor
        if cx + w + 2 * pad > self.size:
            cx, cy, rh = PAD, cy + rh, 0
        origin = (cx + pad, cy + pad)
        self.cursor = [cx + w + 2 * pad + 2, cy, max(rh, h + 2 * pad + 2)]
        return origin

    def tile(self, name, img, pad=PAD):
        """A tile that repeats: pasted with its wrapped border round it."""
        w, h = img.size
        ox, oy = self._alloc(w, h, pad)
        for dy in range(-pad, h + pad):
            for dx in range(-pad, w + pad):
                self.img.putpixel((ox + dx, oy + dy), img.getpixel((dx % w, dy % h)))
        self.at[name] = (ox, oy, w, h)

    def sprite(self, name, img):
        w, h = img.size
        ox, oy = self._alloc(w, h, 1)
        self.img.paste(img, (ox, oy))
        self.at[name] = (ox, oy, w, h)

    def uv(self, name, u, v, wrap=False):
        """u, v in pixels from the tile's top-left (v down) -> Blender uv."""
        ox, oy, w, h = self.at[name]
        return ((ox + u) / self.size, 1.0 - (oy + v) / self.size)


def vnoise(seed, n):
    """A tileable value-noise field n x n (values 0..1), smooth between lattice points."""
    r = random.Random(seed)
    cell = 8
    g = [[r.random() for _ in range(n // cell)] for _ in range(n // cell)]
    f = [[0.0] * n for _ in range(n)]
    m = n // cell
    for y in range(n):
        for x in range(n):
            gx, gy = x / cell, y / cell
            x0, y0 = int(gx) % m, int(gy) % m
            x1, y1 = (x0 + 1) % m, (y0 + 1) % m
            tx, ty = gx - int(gx), gy - int(gy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] * (1 - tx) + g[y0][x1] * tx
            b = g[y1][x0] * (1 - tx) + g[y1][x1] * tx
            f[y][x] = a * (1 - ty) + b * ty
    return f


def flesh_tile(kind, seed):
    """32 x 32 of raw meat: three flat tones in blotches, dark creases with a light edge under them, a vein or two, pores."""
    base, mid, light, dark = PAL[kind]
    r = random.Random(seed)
    f1 = vnoise(seed, TILE)
    img = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            v = f1[y][x]
            c = light if v > 0.66 else base if v > 0.36 else mid
            if r.random() < 0.10:
                c = ht.shade(c, 1 + r.uniform(-0.07, 0.07))
            img.putpixel((x, y), c)
    px = img.load()

    def put(x, y, c):
        px[x % TILE, y % TILE] = c

    # a crease: a wandering dark line with a lit edge under it
    for _ in range(2):
        x, y = r.randrange(TILE), r.randrange(TILE)
        dx = r.choice((1, 1, 2))
        for _i in range(r.randint(8, 14)):
            put(x, y, dark)
            put(x, y + 1, light)
            x += dx
            y += r.choice((-1, 0, 0, 1)) if r.random() < 0.5 else 0
    # veins: thin and raw
    for _ in range(2):
        x, y = r.randrange(TILE), r.randrange(TILE)
        vx, vy = r.choice((-1, 1)), r.choice((-1, 0, 1))
        for _i in range(r.randint(6, 12)):
            put(x, y, (170, 54, 70) if kind != 'red' else (204, 80, 92))
            x += vx
            y += vy if r.random() < 0.7 else r.choice((-1, 0, 1))
    for _ in range(14):
        put(r.randrange(TILE), r.randrange(TILE), dark)       # pores
    return img


def crease_tile(seed):
    r = random.Random(seed)
    img = Image.new('RGB', (TILE, TILE), (112, 38, 46))
    for y in range(TILE):
        for x in range(TILE):
            if r.random() < 0.25:
                img.putpixel((x, y), (92, 28, 38))
            if r.random() < 0.05:
                img.putpixel((x, y), (150, 56, 62))
    return img


def lip_tile(seed):
    r = random.Random(seed)
    f = vnoise(seed, TILE)
    img = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            c = (186, 70, 76) if f[y][x] > 0.4 else (166, 56, 64)
            if y % 8 == 0:
                c = (214, 118, 112)           # wet lines across the lips
            if r.random() < 0.08:
                c = (140, 44, 54)
            img.putpixel((x, y), c)
    return img


def throat_tile(seed):
    r = random.Random(seed)
    f = vnoise(seed, TILE)
    img = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            v = f[y][x]
            c = (66, 14, 26) if v > 0.55 else (46, 8, 18) if v > 0.3 else (32, 4, 12)
            if r.random() < 0.06:
                c = (128, 40, 52)
            img.putpixel((x, y), c)
    return img


def tongue_tile(seed):
    r = random.Random(seed)
    img = Image.new('RGB', (16, 16), (196, 82, 92))
    for y in range(16):
        for x in range(16):
            if r.random() < 0.2:
                img.putpixel((x, y), (176, 66, 78))
            if (x + y) % 5 == 0 and r.random() < 0.7:
                img.putpixel((x, y), (214, 112, 114))
    for y in range(16):
        img.putpixel((8, y), (130, 40, 54))
    return img


def heart_tile(seed):
    r = random.Random(seed)
    f = vnoise(seed, TILE)
    img = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            v = f[y][x]
            c = (170, 34, 50) if v > 0.6 else (138, 24, 42) if v > 0.32 else (108, 16, 36)
            if r.random() < 0.08:
                c = ht.shade(c, 1.15)
            img.putpixel((x, y), c)
    px = img.load()
    for _ in range(4):                           # lighter vessels across the muscle
        x, y = r.randrange(TILE), r.randrange(TILE)
        dx, dy = r.choice((-1, 1)), r.choice((-1, 0, 1))
        for _i in range(r.randint(8, 14)):
            px[x % TILE, y % TILE] = (214, 84, 96)
            px[(x + 1) % TILE, y % TILE] = (120, 14, 34)
            x += dx
            y += dy if r.random() < 0.6 else r.choice((-1, 0, 1))
    for _ in range(6):
        px[r.randrange(TILE), r.randrange(TILE)] = (236, 140, 140)   # wet highlights
    return img


def tooth_tile():
    img = Image.new('RGB', (8, 8), TOOTH)
    px = img.load()
    for y in range(8):
        for x in range(8):
            if y >= 5:
                px[x, y] = TOOTH_SHADE if y < 7 else (214, 150, 140)      # the gum line at the root
            if x == 7:
                px[x, y] = TOOTH_SHADE
            if x == 0 and y < 5:
                px[x, y] = (252, 246, 228)
    for y in range(8):
        px[7, y] = (168, 154, 128)
    return img


def lid_tile():
    """The lids, by bands from the rim: a dark line, a wet pink one, then flesh."""
    img = Image.new('RGB', (8, 8))
    bands = [(86, 30, 38), (232, 146, 134), (216, 124, 112), (204, 108, 98), (196, 100, 92), (190, 92, 88), (180, 84, 84), (170, 76, 78)]
    for y in range(8):
        for x in range(8):
            img.putpixel((x, y), bands[y])
    return img


def eye_tile(n, seed, red=False):
    """A round eye n pixels across: white, a dark red iris, a black pupil, a few red veins, ink round the edge."""
    r = random.Random(seed)
    img = Image.new('RGB', (n, n), (170, 128, 124))
    px = img.load()
    c = n / 2.0
    iris_r = max(1.6, n * 0.27)
    pupil_r = max(0.9, n * 0.12)
    for y in range(n):
        for x in range(n):
            d = ((x + 0.5 - c) ** 2 + (y + 0.5 - c) ** 2) ** 0.5
            if d > c:
                continue
            if d > c - 1.0 and n >= 8:
                px[x, y] = (170, 128, 124) if d > c - 0.7 else (214, 190, 182)
                continue
            col = (244, 236, 220)
            if x + y > n * 1.1:
                col = (222, 210, 196)
            if d < iris_r:
                col = (112, 18, 30) if d > iris_r * 0.62 else (140, 24, 36)
                if d > iris_r - 0.8:
                    col = (70, 10, 20)
            if d < pupil_r:
                col = (20, 4, 10)
            px[x, y] = col
    if n >= 10:
        px[int(c - iris_r * 0.3), int(c - iris_r * 0.4)] = (255, 255, 255)
        for _ in range(3 if n < 16 else 6):          # bloodshot
            ang = r.uniform(0, 6.283)
            for k in range(int(iris_r) + 1, int(c) - 1):
                x, y = int(c + math.cos(ang) * k), int(c + math.sin(ang) * k)
                if 0 <= x < n and 0 <= y < n and px[x, y][0] > 200 and px[x, y][1] > 200:
                    px[x, y] = (206, 120, 118)
    elif n >= 6:
        px[int(c) - 1, int(c) - 1] = (150, 26, 38)
    return img


def make_atlas():
    a = Atlas()
    a.tile('grey', flesh_tile('grey', 11))
    a.tile('pink', flesh_tile('pink', 12))
    a.tile('red', flesh_tile('red', 13))
    a.tile('crease', crease_tile(14))
    a.tile('lip', lip_tile(15))
    a.tile('throat', throat_tile(16))
    a.tile('heart', heart_tile(17))
    a.tile('tongue', tongue_tile(18), pad=2)
    a.sprite('tooth', tooth_tile())
    a.sprite('lid', lid_tile())
    for i, n in enumerate(EYE_SIZES):
        a.sprite('eye%d' % n, eye_tile(n, 30 + i))
    return a


# --- bmesh helpers -------------------------------------------------------------------------------------------------

def fbm(p, octaves=3, lac=2.1, gain=0.5):
    total, amp, f = 0.0, 1.0, 1.0
    norm = 0.0
    for _ in range(octaves):
        total += noise.noise(p * f) * amp
        norm += amp
        amp *= gain
        f *= lac
    return total / norm


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


class Ctx:
    """The scene state: the atlas, its material, the objects made so far."""

    def __init__(self, atlas, atlas_path):
        self.atlas = atlas
        self.coll = bpy.context.scene.collection
        self.mat = make_material(atlas_path)
        self.parts = []

    def add(self, name, bm, matrix=None):
        me = bpy.data.meshes.new(name)
        bm.to_mesh(me)
        bm.free()
        me.materials.append(self.mat)
        for p in me.polygons:
            p.use_smooth = False
        ob = bpy.data.objects.new(name, me)
        self.coll.objects.link(ob)
        if matrix is not None:
            ob.matrix_world = matrix
        self.parts.append(ob)
        return ob


def make_material(path):
    img = bpy.data.images.load(path)
    img.colorspace_settings.name = 'sRGB'
    m = bpy.data.materials.new('devil')
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.95
    for k in ('Specular IOR Level', 'Specular'):
        if k in bsdf.inputs:
            bsdf.inputs[k].default_value = 0.0
    return m


def planar(f, lp, a, name, ppb=PX):
    """Planar uv for one loop by the face's main axis, taken in tile-sized cells so the tile repeats."""
    n = f.normal
    ax = max(range(3), key=lambda i: abs(n[i]))
    _ox, _oy, w, h = a.at[name]

    def pos(co):
        return (co.x, -co.z) if ax == 1 else (co.y, -co.z) if ax == 0 else (co.x, co.y)
    cx, cy = pos(f.calc_center_median())
    u0 = math.floor(cx * ppb / w) * w
    v0 = math.floor(cy * ppb / h) * h
    x, y = pos(lp.vert.co)
    u, v = x * ppb - u0, y * ppb - v0
    lim = PAD - 1.5 if w >= TILE else 0.5
    return a.uv(name, max(-lim, min(w + lim, u)), max(-lim, min(h + lim, v)))


def box_uv(bm, atlas, tile, choose=None):
    """Planar uv over the whole mesh, each face on the tile `choose(face)` picks."""
    uv = bm.loops.layers.uv.verify()
    bm.faces.ensure_lookup_table()
    for f in bm.faces:
        name = choose(f) if choose else tile
        for lp in f.loops:
            lp[uv].uv = planar(f, lp, atlas, name)


def rounded_block(hx, hy, hz, cuts, r):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2.0)
    bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=cuts, use_grid_fill=True)
    core = Vector((hx - r, hy - r, hz - r))
    norms = {}
    for v in bm.verts:
        p = Vector((v.co.x * hx, v.co.y * hy, v.co.z * hz))
        q = Vector((max(-core.x, min(core.x, p.x)), max(-core.y, min(core.y, p.y)), max(-core.z, min(core.z, p.z))))
        d = p - q
        if d.length > 1e-6:
            n = d.normalized()
            v.co = q + n * r
        else:
            n = Vector((0, 1, 0))
            v.co = p
        norms[v.index] = n
    bm.verts.ensure_lookup_table()
    return bm, norms


def frame(kind):
    """The (N, U, R) axes of a mouth on the face or on a side."""
    if kind == 'front':
        N = Vector((0, 1, 0))
    elif kind == 'right':
        N = Vector((1, 0, 0))
    else:
        N = Vector((-1, 0, 0))
    U = Vector((0, 0, 1))
    R = N.cross(U)
    return N, U, R


def mouth_centre(m):
    kind, a, b, rx, rz, depth = m
    if kind == 'front':
        return Vector((a, 0.0, b))
    if kind == 'right':
        return Vector((HX, a, b))
    return Vector((-HX, a, b))


def mouth_local(m, p):
    """A world point in a mouth's (a along R, b along U, h along N)."""
    N, U, R = frame(m[0])
    d = p - mouth_centre(m)
    return d.dot(R), d.dot(U), d.dot(N)


# --- the mass -------------------------------------------------------------------------------------------------------

def lumps(p, seed_off):
    """The folds: broad swells, mid wrinkles and sharp creases, stretched across the face more than up it."""
    q = Vector((p.x * 0.7, p.y * 0.7, p.z * 1.0)) + seed_off
    swell = fbm(q * 0.9, 2) * 0.55
    wr = fbm(q * 2.3 + Vector((7, 3, 1)), 2) * 0.1
    cr = noise.noise(Vector((p.x * 0.9, p.y * 0.9, p.z * 1.5)) + seed_off + Vector((2.2, 5.1, 9.4)))
    crease = max(0.0, 1.0 - abs(cr) * 5.0)
    return swell + wr - 0.22 * crease, crease


def build_mass(ctx, mouths):
    seed_off = Vector((3.1, 1.7, 5.3))
    bm, norms = rounded_block(HX, HY, HZ, 33, ROUND)
    for v in bm.verts:
        v.co += CENTRE
    pit = {}
    crease_of = {}
    for v in bm.verts:
        p = v.co.copy()
        n = norms[v.index]
        # near a mouth the skin lies flat (so the mouth sits on a true plane) and rises into a lip round it
        flat = 0.0
        for m in mouths:
            a, b, h = mouth_local(m, p)
            e = (a / m[3]) ** 2 + (b / m[4]) ** 2
            if abs(h) < 1.5:
                flat = max(flat, smooth((2.6 - e) / 1.2))
        amp, cr = lumps(p, seed_off)
        crease_of[v.index] = cr * (1 - flat)
        disp = amp * (1 - flat)
        if p.y < -9.0 or p.z < 0.2 or p.z > 3.8:
            disp *= 0.3
        v.co += n * disp
        for m in mouths:
            a, b, h = mouth_local(m, v.co)
            N = frame(m[0])[0]
            e = (a / m[3]) ** 2 + (b / m[4]) ** 2
            if abs(h) < 1.2 and N.dot(n) > 0.5:
                w = smooth((1.12 - e) / 0.5)
                if w > 0:
                    v.co -= N * (m[5] * (0.15 + 0.85 * w) * w if e < 1.12 else 0)
                    pit[v.index] = max(pit.get(v.index, 0), w)
                if 1.0 < e < 2.0:
                    v.co += N * 0.16 * math.sin((e - 1.0) * math.pi) * (1 if True else 0)
                    pit[v.index] = max(pit.get(v.index, 0), 0.03)
    bm.normal_update()
    bm.faces.ensure_lookup_table()

    def choose(f):
        vs = [v.index for v in f.verts]
        pw = [pit.get(i, 0) for i in vs]
        if min(pw) > 0.12:
            return 'throat'
        if sum(pw) / 4 > 0.02:
            return 'lip'
        if sum(crease_of[i] for i in vs) / 4 > 0.45:
            return 'crease'
        c = f.calc_center_median()
        nv = noise.noise(Vector((c.x * 0.15, c.y * 0.15, c.z * 0.2)) + Vector((4.4, 1.1, 8.7)))
        return 'grey' if nv < -0.34 else 'pink' if nv < 0.22 else 'red'
    box_uv(bm, ctx.atlas, None, choose=choose)
    return ctx.add('body', bm)


def build_growth(ctx, name, centre, half, taper, seed, front_round=0.5):
    """A long lump of the same flesh running along a wall, the floor or the ceiling towards you, narrowing as it comes."""
    bm, norms = rounded_block(half[0], half[1], half[2], 10, min(front_round, half[0] * 0.9, half[2] * 0.9))
    off = Vector((seed * 1.3, seed * 0.7, seed * 2.1))
    for v in bm.verts:
        t = (v.co.y / half[1] + 1) / 2          # 0 at the back, 1 at the front
        k = 1 - taper * t
        v.co.x *= k
        v.co.z *= k
        v.co += centre
        amp, _cr = lumps(v.co, off)
        v.co += norms[v.index] * amp * 0.9
    bm.normal_update()
    cr = {}

    def choose(f):
        c = f.calc_center_median()
        nv = noise.noise(Vector((c.x * 0.4, c.y * 0.4, c.z * 0.5)) + off)
        return 'pink' if nv < 0.1 else 'red'
    box_uv(bm, ctx.atlas, None, choose=choose)
    return ctx.add(name, bm)


# --- mouths ---------------------------------------------------------------------------------------------------------

LIP, TOOTH_F, TONGUE_F, THROAT_F, HEART_F = 0, 1, 2, 3, 4


def local_matrix(m, origin_h=0.0):
    N, U, R = frame(m[0])
    M = Matrix(((R.x, U.x, N.x, 0), (R.y, U.y, N.y, 0), (R.z, U.z, N.z, 0), (0, 0, 0, 1)))
    return Matrix.Translation(mouth_centre(m) + N * origin_h) @ M


def new_face(bm, verts, tag):
    try:
        f = bm.faces.new(verts)
    except ValueError:
        return None
    f.material_index = tag
    return f


def half_disc(bm, rx, rz, h_back, h_front, upper, z):
    """One jaw's lip: an arch (a half-ring, domed) and a thin strip across the chord that the teeth stand on, the throat
    showing through the middle. Local (a, b, h + z)."""
    sgn = 1 if upper else -1
    m = 10
    ang = [math.pi * i / m for i in range(m + 1)]
    outer = [(math.cos(t) * rx, sgn * math.sin(t) * rz) for t in ang]
    inner = [(math.cos(t) * rx * 0.84, sgn * math.sin(t) * rz * 0.76) for t in ang]

    off = 0.32 * rz if upper else 0.0     # the upper lip sits higher, so the open mouth shows deep into the throat

    def V(x, y, h):
        return bm.verts.new((x, y + off, h + z))
    ob = [V(x, y, h_back) for x, y in outer]
    of = [V(x, y, h_front - 0.14) for x, y in outer]
    inf = [V(x, y, h_front + 0.02) for x, y in inner]
    ib = [V(x, y, h_back) for x, y in inner]
    for i in range(m):
        if upper:
            new_face(bm, (ob[i], ob[i + 1], of[i + 1], of[i]), LIP)            # the outer wall
            new_face(bm, (of[i], of[i + 1], inf[i + 1], inf[i]), LIP)          # the lip's front
            new_face(bm, (inf[i], inf[i + 1], ib[i + 1], ib[i]), LIP)          # the inner wall
        else:
            new_face(bm, (ob[i + 1], ob[i], of[i], of[i + 1]), LIP)
            new_face(bm, (of[i + 1], of[i], inf[i], inf[i + 1]), LIP)
            new_face(bm, (inf[i + 1], inf[i], ib[i], ib[i + 1]), LIP)
    # the strip across the chord
    t = 0.13
    x0, x1 = -rx, rx
    hb, hf = h_back, h_front - 0.14
    c = {}
    for ia, xx in enumerate((x0, x1)):
        for ib_, bb in enumerate((0.0, sgn * t)):
            for ih, hh in enumerate((hb, hf)):
                c[(ia, ib_, ih)] = V(xx, bb, hh)
    quads = (
        ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1), LIP),      # the front
        ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0), LIP),      # the outer side (away from the other jaw)
        ((0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0), TONGUE_F),  # the gum, facing the other jaw
        ((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1), LIP),
        ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0), LIP),
    )
    for *k, tag in quads:
        vs = [c[i] for i in k]
        new_face(bm, vs if upper else list(reversed(vs)), tag)


def teeth(bm, rx, rng, upper, h_front, scale, z, rz_t=0.0):
    """A row of uneven human teeth along the chord, pointing at the other jaw."""
    sgn = 1 if upper else -1
    n = max(4, int(2 * rx / (0.1 * scale)))
    for i in range(n):
        x = -rx * 0.94 + (2 * rx * 0.94) * (i + 0.5) / n
        w = (2 * rx * 0.94 / n) * rng.uniform(0.66, 0.92)
        ht_ = rng.uniform(0.11, 0.27) * scale * (1 - 0.35 * abs(x) / rx)
        if rng.random() < 0.12:
            ht_ *= 0.45                                  # a broken one
        if rng.random() < 0.08:
            ht_ *= 1.5                                   # a long one
        d = rng.uniform(0.07, 0.11) * scale + 0.02
        tilt = rng.uniform(-0.3, 0.3)
        hf = h_front - 0.14 - rng.uniform(0, 0.05)
        pts = {}
        for ix, sx in enumerate((-w / 2, w / 2)):
            for ih, sh in enumerate((0, -d)):
                for iy, sy in enumerate((0, ht_)):
                    pts[(ix, ih, iy)] = bm.verts.new((x + sx + (tilt * sy if iy else 0), -sgn * sy + (0.32 * rz_t if upper else 0.0), hf + sh + z))
        for fk in (
            ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
            ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
            ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)),
            ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
            ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
        ):
            vs = [pts[k] for k in fk]
            new_face(bm, vs if upper else list(reversed(vs)), TOOTH_F)


def lumpy_ball(bm, centre, radii, seed, subdiv, amp, tag):
    geom = bmesh.ops.create_icosphere(bm, subdivisions=subdiv, radius=1.0)
    off = Vector((seed, seed * 0.5, seed * 1.7))
    for v in geom['verts']:
        n = v.co.normalized()
        d = 1 + amp * noise.noise(n * 1.8 + off)
        v.co = Vector((n.x * radii[0] * d, n.y * radii[1] * d, n.z * radii[2] * d)) + centre
    for f in geom['faces'] if 'faces' in geom else []:
        f.material_index = tag
    vs = set(geom['verts'])
    for f in bm.faces:
        if all(v in vs for v in f.verts):
            f.material_index = tag


def uv_tagged(ctx, bm):
    """Teeth take the whole tooth tile, the gum and the tongue their own, the lips the lip tile."""
    a = ctx.atlas
    uv = bm.loops.layers.uv.verify()
    bm.normal_update()
    for f in bm.faces:
        t = f.material_index
        if t == TOOTH_F:
            for i, lp in enumerate(f.loops):
                u, v = ((1, 1), (6, 1), (6, 6), (1, 6))[i % 4]
                lp[uv].uv = a.uv('tooth', u, v)
        else:
            name = {LIP: 'lip', TONGUE_F: 'tongue', THROAT_F: 'throat', HEART_F: 'heart'}[t]
            for lp in f.loops:
                lp[uv].uv = planar(f, lp, a, name)


def build_mouth(ctx, idx, m, rng):
    kind, a, b, rx, rz, depth = m
    base = local_matrix(m)
    big = idx == 0
    hinge_h = -(1.15 * rz + 0.3)
    h_back = -0.45 - rz * 0.4
    h_front = 0.2
    sc = 0.7 + 0.95 * min(1.0, rx / 1.1)
    bm = bmesh.new()
    half_disc(bm, rx * 1.04, rz * 1.04, h_back, h_front, True, 0.0)
    teeth(bm, rx, rng, True, h_front, sc, 0.0, rz * 1.04)
    uv_tagged(ctx, bm)
    upper = ctx.add('mouth_%02d_upper' % idx, bm, base)
    bm = bmesh.new()
    half_disc(bm, rx * 1.04, rz * 1.04, h_back, h_front, False, -hinge_h)
    teeth(bm, rx, rng, False, h_front, sc, -hinge_h)
    if big:
        lumpy_ball(bm, Vector((0.0, 0.1, -0.28 - hinge_h)), (rx * 0.5, 0.14, 0.42), 5.0, 1, 0.08, TONGUE_F)
    uv_tagged(ctx, bm)
    jaw = ctx.add('mouth_%02d_jaw' % idx, bm, local_matrix(m, hinge_h))
    jaw['kind'] = 'jaw'
    jaw['mouth'] = idx
    upper['kind'] = 'upper'
    return upper, jaw


def build_heart(ctx, m):
    kind, a, b, rx, rz, depth = m
    bm = bmesh.new()
    lumpy_ball(bm, Vector((0, 0, 0)), (0.56, 0.48, 0.6), 9.0, 2, 0.16, HEART_F)
    lumpy_ball(bm, Vector((0.28, 0.0, 0.5)), (0.13, 0.13, 0.34), 3.0, 1, 0.1, HEART_F)       # the vessels going up
    lumpy_ball(bm, Vector((-0.18, 0.05, 0.52)), (0.11, 0.11, 0.3), 4.0, 1, 0.1, HEART_F)
    lumpy_ball(bm, Vector((-0.4, 0.0, -0.2)), (0.1, 0.1, 0.3), 6.0, 1, 0.1, HEART_F)
    uv_tagged(ctx, bm)
    heart = ctx.add('heart', bm, local_matrix(m, -depth * 0.6) @ Matrix.Translation((0, -0.05, 0)))
    heart['kind'] = 'heart'
    # the flesh over it: hide it when the flesh is cut away
    bm = bmesh.new()
    lumpy_ball(bm, Vector((0, 0, 0)), (rx * 0.82, rz * 0.8, 0.26), 7.0, 2, 0.1, THROAT_F)
    uv_tagged(ctx, bm)
    cover = ctx.add('heart_cover', bm, local_matrix(m, -depth * 0.6 + 0.5))
    cover['kind'] = 'cover'
    return heart, cover


# --- eyes -----------------------------------------------------------------------------------------------------------

def build_eye(ctx, idx, pos, normal, size, roll):
    a = ctx.atlas
    r = size / (2.0 * PX)
    f = (normal * 0.62 + Vector((0, 1, 0)) * 0.38).normalized()
    rot = f.to_track_quat('Y', 'Z').to_matrix().to_4x4() @ Matrix.Rotation(roll, 4, 'Y')
    base = Matrix.Translation(pos + normal * 0.12 * r) @ rot
    name = 'eye%d' % size
    _ox, _oy, w, h = a.at[name]
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=7, radius=r)
    uv = bm.loops.layers.uv.verify()
    for fc in bm.faces:
        for lp in fc.loops:
            co = lp.vert.co
            lp[uv].uv = a.uv(name, 0.02 + (0.5 + co.x / (2 * r)) * (w - 0.04), 0.02 + (0.5 - co.z / (2 * r)) * (h - 0.04))
    ball = ctx.add('eye_%02d' % idx, bm, base)
    ball['kind'] = 'eye'
    ball['size'] = size
    out = [ball]
    R = r * 1.16
    for lname, sgn in (('lid_up', 1), ('lid_dn', -1)):
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=8, radius=R)
        drop = [v for v in bm.verts if v.co.z * sgn < -1e-4]
        bmesh.ops.delete(bm, geom=drop, context='VERTS')
        for v in bm.verts:
            v.co.y *= 0.7
        uv = bm.loops.layers.uv.verify()
        for fc in bm.faces:
            for lp in fc.loops:
                co = lp.vert.co
                lp[uv].uv = a.uv('lid', 0.5 + (co.x / R + 1) * 3.4, 0.5 + abs(co.z) / R * 7.0)
        lid = ctx.add('eye_%02d_%s' % (idx, lname), bm, base)
        lid['kind'] = lname
        out.append(lid)
    return out


def eye_zone_ok(p, rad, eyes, mouths):
    for q, rq in eyes:
        if (p - q).length < (rad + rq) * 1.32:
            return False
    for m in mouths:
        a, b, h = mouth_local(m, p)
        if abs(h) < 4.0:
            lim = 0.35 + rad * 1.3
            if (a / (m[3] + lim)) ** 2 + (b / (m[4] + lim)) ** 2 < 1:
                return False
    return True


def cast(bvhs, origin, direction):
    best = None
    for t in bvhs:
        loc, nor, idx, dist = t.ray_cast(origin, direction)
        if loc is not None and (best is None or dist < best[3]):
            best = (loc, nor, idx, dist)
    return best


def place_eyes(mass_objs, mouths, rng, count=40):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    bvhs = [BVHTree.FromObject(o, dg) for o in mass_objs]
    chosen = []
    placed = []
    sizes = [16] + [12] * 2 + [10] * 4 + [8] * 7 + [6] * 12
    for slot, size in enumerate(sizes[:count]):
        r = size / (2.0 * PX)
        xr = 1.55 if slot < 16 else 2.7
        for _try in range(500):
            mode = rng.random()
            if slot == 0:
                hit = cast(bvhs, Vector((0.0, 9, 2.4)), Vector((0, -1, 0)))
            elif mode < (0.95 if slot < 16 else 0.7):
                hit = cast(bvhs, Vector((rng.uniform(-xr, xr), 9, rng.uniform(0.3, 3.0 if slot < 16 else 3.65))), Vector((0, -1, 0)))
            elif mode < 1.01:
                sgn = rng.choice((-1, 1))
                hit = cast(bvhs, Vector((sgn * 9, rng.uniform(-8.5, 0.5), rng.uniform(0.35, 3.65))), Vector((-sgn, 0, 0)))
            else:
                o = rng.choice(mass_objs[1:])
                fc = rng.choice(o.data.polygons)
                c = o.matrix_world @ fc.center
                nrm = (o.matrix_world.to_3x3() @ fc.normal).normalized()
                hit = (c, nrm, 0, 0)
            if hit is None:
                continue
            loc, nrm, _i, _d = hit
            if loc.z < 0.25 + r or loc.z > 3.75 - r or loc.y < -9.2 or nrm.y < -0.4:
                continue
            if not eye_zone_ok(loc, r, placed, mouths):
                continue
            placed.append((loc.copy(), r))
            chosen.append((loc.copy(), nrm.normalized(), size))
            break
    return chosen


# --- assembly ---------------------------------------------------------------------------------------------------------

GROWTHS = [
    ('growth_01', Vector((-1.15, -0.9, 0.3)), (0.85, 2.3, 0.34), 0.35, 1.0),     # low ridges creeping along the floor
    ('growth_02', Vector((1.2, -0.7, 0.28)), (0.8, 2.1, 0.32), 0.35, 2.0),
    ('growth_03', Vector((-1.1, -1.0, 3.0)), (0.8, 2.3, 0.4), 0.35, 3.0),        # and along the ceiling
    ('growth_04', Vector((1.1, -0.9, 3.0)), (0.8, 2.1, 0.4), 0.35, 4.0),
    ('growth_05', Vector((-1.75, -0.6, 1.6)), (0.45, 2.3, 0.7), 0.35, 5.0),      # and up the walls, where they are broken
    ('growth_06', Vector((1.75, -0.4, 1.5)), (0.45, 2.1, 0.7), 0.35, 6.0),
]


def build_devil(out_dir=OUT, jaw_open=0.0, lid_open=0.5, seed=7):
    """Builds the devil into the current scene. Returns {'pulse': Empty, 'parts': [...]}; pose it with `pose`."""
    os.makedirs(out_dir, exist_ok=True)
    atlas = make_atlas()
    path = os.path.join(out_dir, 'eternity_devil_atlas.png')
    atlas.img.save(path)
    ctx = Ctx(atlas, path)
    rng = random.Random(seed)
    mass = [build_mass(ctx, MOUTHS)]
    for name, c, half, taper, sd in GROWTHS:
        mass.append(build_growth(ctx, name, c, half, taper, sd))
    parts = list(mass)
    for i, m in enumerate(MOUTHS):
        up, jaw = build_mouth(ctx, i, m, random.Random(100 + i))
        parts += [up, jaw]
    heart, cover = build_heart(ctx, MOUTHS[0])
    parts += [heart, cover]
    for i, (pos, nrm, size) in enumerate(place_eyes(mass, MOUTHS, rng)):
        parts += build_eye(ctx, i + 1, pos, nrm, size, rng.uniform(-0.35, 0.35))
    pulse = bpy.data.objects.new('mass_pulse', None)
    pulse.empty_display_type = 'PLAIN_AXES'
    pulse.location = CENTRE
    ctx.coll.objects.link(pulse)
    bpy.context.view_layer.update()
    inv = pulse.matrix_world.inverted()
    for i, m in enumerate(MOUTHS):
        e = bpy.data.objects.new('mouth_%02d_at' % i, None)
        e.empty_display_type = 'ARROWS'
        e.empty_display_size = 0.3
        ctx.coll.objects.link(e)
        e.matrix_world = local_matrix(m)
        e.parent = pulse
        e.matrix_parent_inverse = inv
    for o in parts:
        rest = o.matrix_world.copy()
        o.parent = pulse
        o.matrix_parent_inverse = inv
        o.matrix_basis = rest
        o['rest'] = [c for row in rest for c in row]
    dev = {'pulse': pulse, 'parts': parts, 'ctx': ctx}
    pose(dev, jaw_open, lid_open)
    return dev


def rest_of(o):
    r = o['rest']
    return Matrix((r[0:4], r[4:8], r[8:12], r[12:16]))


def pose(dev, jaw_open=0.0, lid_open=0.5, jaws=None, lids=None):
    """Opens the jaws (radians, about each jaw's local X) and the eyelids (0 shut, ~0.5 wide). `jaws` (by mouth index) and
    `lids` (by object name) override one at a time."""
    for o in dev['parts']:
        k = o.get('kind')
        if k == 'jaw':
            a = jaws.get(o['mouth'], jaw_open) if jaws else jaw_open
            o.matrix_basis = rest_of(o) @ Matrix.Rotation(a, 4, 'X')
        elif k in ('lid_up', 'lid_dn'):
            a = lids.get(o.name, lid_open) if lids else lid_open
            o.matrix_basis = rest_of(o) @ Matrix.Rotation(a if k == 'lid_up' else -a, 4, 'X')
    bpy.context.view_layer.update()


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    out = OUT
    if '--out' in argv:
        out = os.path.abspath(argv[argv.index('--out') + 1])
    dev = build_devil(out, jaw_open=0.35)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'eternity_devil.blend'))
    n = sum(len(o.data.polygons) for o in dev['parts'])
    eyes = len([o for o in dev['parts'] if o.get('kind') == 'eye'])
    print('parts', len(dev['parts']), 'faces', n, 'eyes', eyes, 'mouths', len(MOUTHS))


if __name__ == '__main__':
    main()
