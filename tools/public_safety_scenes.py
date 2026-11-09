"""Renders the Public Safety look (Chainsaw Man mod): the outpost by day and night, the front desk, the mission board, the captain's
office, the three suit tiers and the gear. Blender, background:

    blender --background --python tools/public_safety_scenes.py -- [--only name,name] [--quality 1]

Writes work/public_safety_look/shot_*.png; tools/public_safety_sheet.py puts them on the sheets. Everything is nearest-filtered
16-pixel blocks (tools/public_safety_textures.py) and 64 x 64 skins on the real player model (tools/public_safety_skins.py), lit the
way the game would show it. The gear is the 16 x 16 sprites extruded one pixel deep, as Minecraft draws a held item.
"""
import math
import os
import sys

import bpy
import bmesh
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.append(os.environ.get('ETERNITY_PIL', r'C:\Users\gamek\blender_pil'))
import public_safety_textures as pt  # noqa: E402
from PIL import Image  # noqa: E402

OUT = pt.OUT
SKINS = os.path.abspath(os.path.join(HERE, 'skins', 'public_safety'))
QUALITY = 1.0
PX = 0.9375 / 16.0            # one skin pixel in blocks


# --- the scene kit ------------------------------------------------------------------------------------------------

def reset(w, h, samples=64, bg=(0.012, 0.011, 0.014), bg_strength=1.0):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = max(8, int(samples * QUALITY))
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 6
    sc.cycles.transparent_max_bounces = 12
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = 100
    sc.render.image_settings.file_format = 'PNG'
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    sc.display_settings.display_device = 'sRGB'
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (*bg, 1)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = bg_strength
    sc.world = world
    return sc


def camera(pos, target, lens=24, roll=0.0):
    cam = bpy.data.cameras.new('cam')
    cam.lens = lens
    cam.clip_start = 0.05
    cam.clip_end = 300
    ob = bpy.data.objects.new('cam', cam)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = pos
    d = Vector(target) - Vector(pos)
    ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    bpy.context.scene.camera = ob
    return ob


def point(pos, energy, colour=(1.0, 0.78, 0.5), radius=0.12):
    li = bpy.data.lights.new('l', 'POINT')
    li.energy = energy
    li.color = colour
    li.shadow_soft_size = radius
    ob = bpy.data.objects.new('l', li)
    ob.location = pos
    bpy.context.scene.collection.objects.link(ob)
    return ob


def sun(direction, energy, colour=(1, 1, 1), angle=0.03):
    li = bpy.data.lights.new('s', 'SUN')
    li.energy = energy
    li.color = colour
    li.angle = angle
    ob = bpy.data.objects.new('s', li)
    ob.rotation_euler = Vector(direction).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.scene.collection.objects.link(ob)
    return ob


def area(pos, size, energy, colour=(1, 1, 1), direction=(0, 0, -1)):
    li = bpy.data.lights.new('a', 'AREA')
    li.energy = energy
    li.size = size
    li.color = colour
    ob = bpy.data.objects.new('a', li)
    ob.location = pos
    ob.rotation_euler = Vector(direction).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.scene.collection.objects.link(ob)
    return ob


def render(path):
    sc = bpy.context.scene
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('rendered', path)


# --- the block atlas ----------------------------------------------------------------------------------------------

class BlockAtlas:
    def __init__(self):
        tiles = pt.build()
        self.names = sorted(tiles)
        cols = 16
        rows = (len(self.names) + cols - 1) // cols
        self.W, self.H = cols * 16, rows * 16
        img = Image.new('RGB', (self.W, self.H), (255, 0, 255))
        self.pos = {}
        for i, n in enumerate(self.names):
            x, y = (i % cols) * 16, (i // cols) * 16
            img.paste(tiles[n], (x, y))
            self.pos[n] = (x, y)
        os.makedirs(OUT, exist_ok=True)
        self.path = os.path.join(OUT, 'blocks_atlas.png')
        img.save(self.path)
        self.img = bpy.data.images.load(self.path)
        self.img.colorspace_settings.name = 'sRGB'
        self.mats = {}

    def uv(self, name, u, v):
        x, y = self.pos[name]
        e = 0.02
        return ((x + e + u * (16 - 2 * e)) / self.W, 1 - (y + e + (1 - v) * (16 - 2 * e)) / self.H)

    def material(self, emit=0.0, rough=0.9):
        key = (emit, rough)
        if key in self.mats:
            return self.mats[key]
        m = bpy.data.materials.new('blocks%s' % (emit,))
        m.use_nodes = True
        nt = m.node_tree
        bsdf = nt.nodes['Principled BSDF']
        tex = nt.nodes.new('ShaderNodeTexImage')
        tex.image = self.img
        tex.interpolation = 'Closest'
        nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
        bsdf.inputs['Roughness'].default_value = rough
        for k in ('Specular IOR Level', 'Specular'):
            if k in bsdf.inputs:
                bsdf.inputs[k].default_value = 0.1
        if emit > 0:
            nt.links.new(tex.outputs['Color'], bsdf.inputs['Emission Color'])
            bsdf.inputs['Emission Strength'].default_value = emit
        self.mats[key] = m
        return m


class Blocks:
    FACES = {
        'top': ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1), (0, 0, 1)),
        'bottom': ((0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, -1)),
        'north': ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1), (0, -1, 0)),   # -Y
        'south': ((1, 1, 0), (0, 1, 0), (0, 1, 1), (1, 1, 1), (0, 1, 0)),    # +Y
        'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1), (-1, 0, 0)),    # -X
        'east': ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)),     # +X
    }

    def __init__(self, atlas):
        self.atlas = atlas
        self.solid = {}

    def fill(self, x0, y0, z0, x1, y1, z1, tiles):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, tiles)

    def set(self, x, y, z, tiles):
        if isinstance(tiles, str):
            tiles = {'all': tiles}
        self.solid[(x, y, z)] = tiles

    def clear(self, x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.solid.pop((x, y, z), None)

    def tile_for(self, tiles, face):
        if face in tiles:
            return tiles[face]
        if face in ('north', 'south', 'east', 'west') and 'side' in tiles:
            return tiles['side']
        return tiles.get('all')

    def quad(self, bm, uvl, corners, tile, flip=0):
        vs = [bm.verts.new(c) for c in corners]
        try:
            f = bm.faces.new(vs)
        except ValueError:
            return
        for i, lp in enumerate(f.loops):
            u, v = ((0, 0), (1, 0), (1, 1), (0, 1))[(i + flip) % 4]
            lp[uvl].uv = self.atlas.uv(tile, u, v)

    def build(self, name='blocks', emit_tiles=None):
        emit_tiles = emit_tiles or {}
        meshes = {}
        for (x, y, z), tiles in self.solid.items():
            for face, (a, b, c, d, nb) in self.FACES.items():
                if (x + nb[0], y + nb[1], z + nb[2]) in self.solid:
                    continue
                tile = self.tile_for(tiles, face)
                if tile is None:
                    continue
                key = emit_tiles.get(tile, 0.0)
                if key not in meshes:
                    bm = bmesh.new()
                    meshes[key] = (bm, bm.loops.layers.uv.verify())
                bm, uvl = meshes[key]
                self.quad(bm, uvl, [(x + p[0], y + p[1], z + p[2]) for p in (a, b, c, d)], tile)
        objs = []
        for key, (bm, uvl) in meshes.items():
            me = bpy.data.meshes.new(name)
            bm.to_mesh(me)
            bm.free()
            me.materials.append(self.atlas.material(key))
            ob = bpy.data.objects.new(name, me)
            bpy.context.scene.collection.objects.link(ob)
            objs.append(ob)
        return objs

    def box(self, x0, y0, z0, x1, y1, z1, tiles, emit=0.0):
        """A free box (corner coordinates in blocks): its own object. {'face': tile}; faces without a tile are left out."""
        bm = bmesh.new()
        uvl = bm.loops.layers.uv.verify()
        if isinstance(tiles, str):
            tiles = {'all': tiles}
        for face, (a, b, c, d, nb) in self.FACES.items():
            tile = self.tile_for(tiles, face)
            if tile is None:
                continue
            corners = [(x0 + (x1 - x0) * p[0], y0 + (y1 - y0) * p[1], z0 + (z1 - z0) * p[2]) for p in (a, b, c, d)]
            self.quad(bm, uvl, corners, tile)
        me = bpy.data.meshes.new('box')
        bm.to_mesh(me)
        bm.free()
        me.materials.append(self.atlas.material(emit))
        ob = bpy.data.objects.new('box', me)
        bpy.context.scene.collection.objects.link(ob)
        return ob


# --- the player model with a real skin ----------------------------------------------------------------------------

SKIN_PARTS = {
    # name: (skin box key, outer key, x range px, y range px, z range px, joint px)    (the character faces -Y; its right is -X)
    'head': ('head', 'head2', (-4, 4), (-4, 4), (24, 32), (0, 0, 24)),
    'body': ('body', 'body2', (-4, 4), (-2, 2), (12, 24), (0, 0, 24)),
    'rarm': ('rarm', 'rarm2', (-8, -4), (-2, 2), (12, 24), (-6, 0, 24)),
    'larm': ('larm', 'larm2', (4, 8), (-2, 2), (12, 24), (6, 0, 24)),
    'rleg': ('rleg', 'rleg2', (-4, 0), (-2, 2), (0, 12), (-2, 0, 12)),
    'lleg': ('lleg', 'lleg2', (0, 4), (-2, 2), (0, 12), (2, 0, 12)),
}


def skin_material(path, name):
    img = bpy.data.images.load(path)
    img.colorspace_settings.name = 'sRGB'
    img.alpha_mode = 'STRAIGHT'
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = 0.85
    for k in ('Specular IOR Level', 'Specular'):
        if k in bsdf.inputs:
            bsdf.inputs[k].default_value = 0.1
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    tr = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    out = nt.nodes['Material Output']
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    nt.links.new(tex.outputs['Alpha'], mix.inputs['Fac'])
    nt.links.new(tr.outputs['BSDF'], mix.inputs[1])
    nt.links.new(bsdf.outputs['BSDF'], mix.inputs[2])
    nt.links.new(mix.outputs['Shader'], out.inputs['Surface'])
    return m


def skin_box(mat, uv_origin, size, xr, yr, zr, joint, grow=0.0):
    """A box with the skin's UVs, its vertices relative to the joint, in blocks."""
    import public_safety_skins as sk
    w, h, d = size
    x0, x1 = xr[0] - grow, xr[1] + grow
    y0, y1 = yr[0] - grow, yr[1] + grow
    z0, z1 = zr[0] - grow, zr[1] + grow
    j = joint
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.verify()

    def P(x, y, z):
        return ((x - j[0]) * PX, (y - j[1]) * PX, (z - j[2]) * PX)

    u, v = uv_origin
    rects = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
             'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}
    # corners in order bottom-left, bottom-right, top-right, top-left of the face as seen from outside
    faces = {
        'front': [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
        'back': [(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)],
        'right': [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        'left': [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)],
        'top': [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
        'bottom': [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
    }
    for name, corners in faces.items():
        rx, ry, rw, rh = rects[name]
        vs = [bm.verts.new(P(*c)) for c in corners]
        f = bm.faces.new(vs)
        uvs = [(rx, ry + rh), (rx + rw, ry + rh), (rx + rw, ry), (rx, ry)]
        for lp, (px, py) in zip(f.loops, uvs):
            lp[uvl].uv = (px / 64.0, 1 - py / 64.0)
    me = bpy.data.meshes.new('skinbox')
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    for poly in me.polygons:
        poly.use_smooth = False
    return me


class Figure:
    """A player on its skin. pos is at the feet; yaw 0 faces -Y (towards a camera looking along +Y); angles in degrees: an arm's
    angle forward (negative about X is forwards, -90 is straight out), legs likewise; sit lowers the figure onto a seat."""

    def __init__(self, skin, pos, yaw=0.0, rarm=0.0, larm=0.0, legs=0.0, head=(0.0, 0.0), sit=False, name='fig'):
        self.pos = Vector(pos)
        self.yaw = yaw
        self.angles = {'rarm': rarm, 'larm': larm, 'rleg': -90 if sit else legs, 'lleg': -90 if sit else -legs}
        mat = skin_material(os.path.join(SKINS, skin + '.png'), name)
        self.base = Matrix.Translation(self.pos) @ Matrix.Rotation(math.radians(yaw), 4, 'Z')
        for part, (key, key2, xr, yr, zr, joint) in SKIN_PARTS.items():
            for k, grow in ((key, 0.0), (key2, 0.5 if part == 'head' else 0.25)):
                import public_safety_skins as sk
                uv, size = sk.PARTS[k]
                me = skin_box(mat, uv, size, xr, yr, zr, joint, grow)
                ob = bpy.data.objects.new('%s_%s' % (name, k), me)
                bpy.context.scene.collection.objects.link(ob)
                ob.matrix_world = self.part_matrix(part, head)
        self.hand_cache = None

    def part_matrix(self, part, head=(0.0, 0.0)):
        joint = SKIN_PARTS[part][5]
        m = self.base @ Matrix.Translation(Vector(joint) * PX)
        if part == 'head':
            m = m @ Matrix.Rotation(math.radians(head[0]), 4, 'Z') @ Matrix.Rotation(math.radians(head[1]), 4, 'X')
        elif part in self.angles:
            m = m @ Matrix.Rotation(math.radians(self.angles[part]), 4, 'X')
        return m

    def hand(self, which='rarm'):
        """The matrix of the right hand's palm: at the end of the arm, the arm's frame."""
        return self.part_matrix(which) @ Matrix.Translation(Vector((0, 0, -10.5)) * PX)


def sprite_object(name, grip, size=0.0625 * 0.9, thick=1.0, mat=None):
    """The 16 x 16 sprite extruded `thick` pixels deep, one box per opaque pixel (neighbours culled), UVs on each pixel's centre.
    Local frame: sprite x -> X, sprite up -> Z, depth along Y; origin at the grip pixel."""
    path = os.path.join(OUT, 'items', name + '.png')
    img = bpy.data.images.load(path)
    img.colorspace_settings.name = 'sRGB'
    img.alpha_mode = 'STRAIGHT'
    pil = Image.open(path).convert('RGBA')
    solid = {(x, y) for y in range(16) for x in range(16) if pil.getpixel((x, y))[3] > 0}
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = 0.5
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.verify()
    d = thick * size / 2
    s = size
    gx, gy = grip

    def V(x, y, z):
        return ((x - gx - 0.5) * s, z, (15 - y - gy_adj) * s)

    gy_adj = 15 - gy - 0.5 + 0.0
    gy_adj = 0
    for (px, py) in solid:
        uc = (px + 0.5) / 16.0
        vc = 1 - (py + 0.5) / 16.0
        x0, x1 = px - gx - 0.5, px - gx + 0.5
        z0, z1 = (15 - py) - (15 - gy) - 0.5, (15 - py) - (15 - gy) + 0.5
        x0, x1, z0, z1 = x0 * s, x1 * s, z0 * s, z1 * s
        quads = []
        quads.append([(x0, -d, z0), (x1, -d, z0), (x1, -d, z1), (x0, -d, z1)])      # front (-Y)
        quads.append([(x1, d, z0), (x0, d, z0), (x0, d, z1), (x1, d, z1)])          # back
        if (px - 1, py) not in solid:
            quads.append([(x0, d, z0), (x0, -d, z0), (x0, -d, z1), (x0, d, z1)])
        if (px + 1, py) not in solid:
            quads.append([(x1, -d, z0), (x1, d, z0), (x1, d, z1), (x1, -d, z1)])
        if (px, py - 1) not in solid:
            quads.append([(x0, -d, z1), (x1, -d, z1), (x1, d, z1), (x0, d, z1)])
        if (px, py + 1) not in solid:
            quads.append([(x0, d, z0), (x1, d, z0), (x1, -d, z0), (x0, -d, z0)])
        for q in quads:
            f = bm.faces.new([bm.verts.new(c) for c in q])
            for lp in f.loops:
                lp[uvl].uv = (uc, vc)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(m)
    ob = bpy.data.objects.new('item_' + name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


# --- the outpost ----------------------------------------------------------------------------------------------------

W, D = 13, 11          # x 0..12, y 0..10; the front is y = 0 (faces -Y)
SLAB, ROOF = 4, 8      # the slab between the floors, the roof slab
EMIT_DAY = {'fluoro': 3.0}
EMIT_NIGHT = {'fluoro': 3.0, 'window_lit': 1.0, 'door_top_lit': 1.0, 'door_bottom_lit': 1.0, 'lamp_top': 6.0}
for _i in range(7):
    EMIT_NIGHT['sign_main_%d' % _i] = 0.9
for _i in range(4):
    EMIT_NIGHT['sign_branch_%d' % _i] = 0.8
EMIT_NIGHT['emblem'] = 0.5

# which window blocks are lit at night
FRONT_WIN_G = (2, 3, 9, 10)
SIDE_WIN = (3, 4, 7, 8)
FRONT_WIN_U = (2, 3, 5, 6, 7, 9, 10)


def perimeter(x, y):
    return x in (0, W - 1) or y in (0, D - 1)


def wall(b, x, y, z, ext, inn):
    t = {'all': inn}
    if y == 0:
        t['north'] = ext
    if y == D - 1:
        t['south'] = ext
    if x == 0:
        t['west'] = ext
    if x == W - 1:
        t['east'] = ext
    b.set(x, y, z, t)


def build_outpost(b, night=False, lit_upper=(2, 3, 9, 10), tree=True):
    win_lit = 'window_lit' if night else 'window_day'
    win_off = 'window_dark' if night else 'window_day'
    # the ground: lawn, a pavement in front, the road
    b.fill(-14, -16, 0, 28, 26, 0, 'grass')
    b.fill(-14, -10, 0, 28, -6, 0, 'asphalt')
    b.fill(-3, -5, 0, 15, -1, 0, 'pavement')
    b.fill(-3, -5, 0, 15, -5, 0, {'all': 'pavement'})
    for x in range(-14, 29):                             # a kerb line
        b.set(x, -5, 0, {'all': 'wall_concrete_base', 'top': 'pavement'})
    # the floor of the ground storey and the steps
    for x in range(W):
        for y in range(D):
            b.set(x, y, 0, {'top': 'floor_vinyl', 'all': 'wall_concrete_base'})
    for x in (5, 6, 7):
        b.set(x, -1, 0, {'all': 'wall_concrete_base', 'top': 'pavement'})
    for x in (5, 6, 7):
        b.set(x, 0, 0, {'top': 'mat', 'all': 'wall_concrete_base'})
    # walls, two storeys
    for x in range(W):
        for y in range(D):
            if not perimeter(x, y):
                continue
            for z in (1, 2, 3):
                ext = 'wall_concrete_base' if z == 1 else 'wall_concrete'
                inn = 'wall_dado' if z == 1 else 'wall_plaster'
                wall(b, x, y, z, ext, inn)
            for z in (5, 6, 7):
                ext = 'wall_concrete'
                inn = 'wall_dado' if z == 5 else 'wall_plaster'
                wall(b, x, y, z, ext, inn)
    # windows
    for z in (2, 3):
        for x in FRONT_WIN_G:
            b.set(x, 0, z, {'all': 'wall_plaster', 'north': win_lit, 'south': 'window_day'})
        for x in (3, 4, 8, 9):
            b.set(x, D - 1, z, {'all': 'wall_plaster', 'south': win_off, 'north': 'window_day'})
        for y in SIDE_WIN:
            b.set(0, y, z, {'all': 'wall_plaster', 'west': win_lit if y in (3, 4) else win_off, 'east': 'window_day'})
            b.set(W - 1, y, z, {'all': 'wall_plaster', 'east': win_lit if y in (7, 8) else win_off, 'west': 'window_day'})
    for z in (6, 7):
        for x in FRONT_WIN_U:
            b.set(x, 0, z, {'all': 'wall_plaster', 'north': win_lit if x in lit_upper else win_off, 'south': 'window_day'})
        for x in (3, 4, 5, 6, 7, 8, 9):
            b.set(x, D - 1, z, {'all': 'wall_plaster', 'south': win_lit if x in lit_upper else win_off, 'north': 'window_day'})
        for y in SIDE_WIN:
            b.set(0, y, z, {'all': 'wall_plaster', 'west': win_lit if night and y in (3, 8) else win_off, 'east': 'window_day'})
            b.set(W - 1, y, z, {'all': 'wall_plaster', 'east': win_lit if night and y in (4, 7) else win_off, 'west': 'window_day'})
    # the door
    lit = '_lit' if night else ''
    for x in (5, 6, 7):
        b.set(x, 0, 1, {'all': 'wall_plaster', 'north': 'door_bottom' + lit, 'south': 'door_bottom_lit'})
        b.set(x, 0, 2, {'all': 'wall_plaster', 'north': 'door_top' + lit, 'south': 'door_top_lit'})
    # the sign over the door, the emblem plaques beside it
    for i, x in enumerate(range(3, 10)):
        b.set(x, 0, 3, {'all': 'wall_plaster', 'north': 'sign_main_%d' % i})
    b.set(4, 0, 2, {'all': 'wall_plaster', 'north': 'emblem'})
    b.set(8, 0, 2, {'all': 'wall_plaster', 'north': 'emblem'})
    # the slab between the floors, with the stair well, and the roof
    for x in range(W):
        for y in range(D):
            lights = (x, y) in ((3, 3), (8, 3), (3, 7), (8, 7), (6, 3), (6, 8))
            if (x, y) in {(a, c) for a in (1, 2) for c in (2, 3, 4)}:
                continue
            if perimeter(x, y):
                t = {'all': 'wall_plaster', 'top': 'floor_carpet', 'bottom': 'ceiling_panel'}
                for face, cond in (('north', y == 0), ('south', y == D - 1), ('west', x == 0), ('east', x == W - 1)):
                    if cond:
                        t[face] = 'wall_concrete_top'
                b.set(x, y, SLAB, t)
            else:
                b.set(x, y, SLAB, {'all': 'ceiling_panel', 'top': 'floor_carpet', 'bottom': 'fluoro' if lights else 'ceiling_panel'})
            if perimeter(x, y):
                t = {'all': 'wall_plaster', 'top': 'wall_concrete_top', 'bottom': 'ceiling_panel'}
                for face, cond in (('north', y == 0), ('south', y == D - 1), ('west', x == 0), ('east', x == W - 1)):
                    if cond:
                        t[face] = 'wall_concrete_top'
                b.set(x, y, ROOF, t)
                b.set(x, y, ROOF + 1, {'all': 'wall_concrete', 'top': 'wall_concrete_top'})
            else:
                b.set(x, y, ROOF, {'all': 'roof', 'bottom': 'fluoro' if lights else 'ceiling_panel'})
    # the stairs against the west wall
    for y, hgt in ((2, 1), (3, 2), (4, 3)):
        for x in (1, 2):
            for z in range(1, hgt + 1):
                b.set(x, y, z, {'top': 'stair_top', 'all': 'stair_side'} if z == hgt else {'all': 'stair_side'})
    # the roof: air units
    b.fill(2, 7, ROOF + 1, 3, 8, ROOF + 1, {'all': 'ac_side', 'top': 'ac_side'})
    b.fill(9, 2, ROOF + 1, 10, 3, ROOF + 1, {'all': 'ac_side', 'top': 'ac_side'})
    # the front desk: a counter, a phone and a bell
    for x in range(4, 10):
        top = 'counter_top_phone' if x == 5 else 'counter_top_papers' if x == 7 else 'counter_top_stamp' if x == 4 else 'counter_top_bell' if x == 9 else 'counter_top_plain'
        b.set(x, 5, 1, {'top': top, 'north': 'counter_front_emblem' if x == 6 else 'counter_front', 'all': 'counter_front'})
    for y in (5, 6):
        b.set(10, y, 1, {'top': 'counter_top_plain', 'all': 'counter_front', 'north': 'counter_front'})
    # filing cabinets and the clock along the back wall
    for x in (3, 4, 5, 8, 9):
        for z in (1, 2):
            b.set(x, D - 2, z, {'north': 'cabinet_front', 'side': 'cabinet_side', 'top': 'cabinet_top', 'bottom': 'cabinet_side'})
    # desks behind the counter
    for x in (3, 4):
        b.set(x, 7, 1, {'top': 'desk_top_papers' if x == 3 else 'desk_top_phone', 'north': 'desk_front', 'all': 'desk_front'})
    b.set(9, 7, 1, {'top': 'desk_top_lamp', 'north': 'desk_front', 'all': 'desk_front'})
    # a waiting bench by the door, a rug
    for y in (2, 3):
        b.box(10.1, y + 0.05, 1.0, 11.9, y + 0.95, 1.45, {'top': 'bench_top', 'all': 'bench_side'})
    # outside: the drinks machine, a lamp, trees, the flag
    b.box(11.1, -0.9, 1.0, 12.0, -0.1, 3.0, {'north': 'vending_front', 'all': 'pole', 'top': 'roof'})
    b.box(-1.5, -3.55, 1.0, -1.25, -3.3, 6.0, {'all': 'pole'})
    b.box(-1.5, -3.55, 6.0, 0.3, -3.3, 6.2, {'all': 'pole'})
    b.box(-0.3, -3.6, 5.78, 0.4, -3.25, 6.0, {'all': 'lamp_top', 'top': 'pole'}, emit=EMIT_NIGHT['lamp_top'] if night else 0.0)
    b.box(14.0, -2.5, 1.0, 14.18, -2.32, 9.2, {'all': 'pole'})
    b.box(11.0, -2.46, 7.4, 14.0, -2.36, 8.9, {'north': 'banner_0', 'south': 'banner_0', 'all': 'banner_0'})
    if tree:
        for (tx, ty) in ((-5, 6), (17, 8), (-4, -1)):
            b.fill(tx, ty, 1, tx, ty, 3, {'all': 'log_side', 'top': 'log_top'})
            b.fill(tx - 1, ty - 1, 4, tx + 1, ty + 1, 5, 'leaves')
            b.fill(tx, ty, 6, tx, ty, 6, 'leaves')
    return {}


def outpost_interior_fixtures(b, night=False):
    """Everything inside that is not a full block: the mission board, the clock, the notice, chairs, the captain's room."""
    # the mission board on the east wall, facing west: 3 wide (y 5 .. 2) and 2 tall
    for i in range(3):
        for j in range(2):
            y0 = 5 - i - 1
            b.box(11.94, y0, 2.5 - j + 0.0 + 0.0 + 1.0 - 0.0 if False else 1.5 + (1 - j), 12.0, y0 + 1, 1.5 + (1 - j) + 1,
                  {'west': 'board_%d_%d' % (i, j)})
    b.box(0.0, 8.0, 2.0, 0.06, 9.0, 3.0, {'east': 'clock'})
    b.box(11.94, 7.0, 2.0, 12.0, 8.0, 3.0, {'west': 'notice'})
    b.box(11.94, 8.0, 2.0, 12.0, 9.0, 3.0, {'west': 'phone_wall'})
    # a chair behind the counter
    for (cx, cy) in ((5, 6), (7, 6)):
        b.box(cx + 0.2, cy + 0.15, 1.0, cx + 0.8, cy + 0.75, 1.0 + 0.3, {'top': 'bench_top', 'all': 'bench_side'})
        b.box(cx + 0.2, cy + 0.65, 1.3, cx + 0.8, cy + 0.75, 1.9, {'all': 'bench_side', 'top': 'bench_top'})
    for (cx, cy) in ((3, 8), (4, 8)):
        pass
    # upstairs: the captain's office, x 6..11 and y 6..9, behind a partition along x = 5 and one along y = 5 (its door at x 8, 9)
    z0 = SLAB + 1
    for z in (z0, z0 + 1, z0 + 2):
        for y in range(5, 10):
            b.set(5, y, z, {'all': 'wall_plaster', 'east': 'wall_dado' if z == z0 else 'wall_plaster', 'west': 'wall_dado' if z == z0 else 'wall_plaster'})
        for x in range(5, 12):
            if x in (8, 9) and z < z0 + 2:
                continue
            b.set(x, 5, z, {'all': 'wall_plaster', 'north': 'wall_dado' if z == z0 else 'wall_plaster', 'south': 'wall_dado' if z == z0 else 'wall_plaster'})
    # the rug, the desk
    for x in range(7, 12):
        for y in (7, 8, 9):
            b.set(x, y, SLAB, {'all': 'ceiling_panel', 'top': 'rug', 'bottom': b.solid[(x, y, SLAB)].get('bottom', 'ceiling_panel')})
    for x in (8, 9, 10):
        b.set(x, 8, z0, {'top': 'captain_desk_top_plate' if x == 9 else 'captain_desk_top_papers' if x == 10 else 'captain_desk_top_plain',
                          'north': 'captain_desk_front', 'all': 'captain_desk_front'})
    # the files: a bookcase along the east wall, one in the back corner
    for y in (7, 8, 9):
        for z in (z0, z0 + 1):
            b.set(11, y, z, {'west': 'shelf_files', 'side': 'shelf_side', 'top': 'shelf_side'})
    for z in (z0, z0 + 1):
        b.set(6, 9, z, {'north': 'shelf_files', 'side': 'shelf_side', 'top': 'shelf_side'})
        b.set(7, 9, z, {'north': 'shelf_files', 'side': 'shelf_side', 'top': 'shelf_side'})
    # the map beside the window, the emblem over the desk, the flag by the shelves
    for i in range(2):
        b.box(8.0 + i + 2.0 * 0, 9.94, z0 + 2.0, 9.0 + i, 10.0, z0 + 3.0, {'north': 'map_%d_0' % i}) if False else None
    b.box(10.0, 9.94, z0 + 1.0, 11.0, 10.0, z0 + 2.0, {'north': 'emblem'})
    b.box(8.4, 9.9, z0 + 1.0, 9.6, 9.96, z0 + 3.0, {'north': 'clock'}) if False else None
    b.box(8.0, 9.94, z0 + 2.0, 9.0, 10.0, z0 + 3.0, {'north': 'clock'}) if False else None
    b.box(6.1, 8.6, z0 + 0.0, 6.2, 8.7, z0 + 2.8, {'all': 'pole'})
    b.box(6.2, 8.62, z0 + 1.8, 6.26, 9.5, z0 + 2.8, {'east': 'banner_0', 'west': 'banner_0', 'all': 'banner_0'})


def office_back_window(b):
    z0 = SLAB + 1
    for x in (7, 8):
        for z in (z0 + 1, z0 + 2):
            b.set(x, D - 1, z, {'all': 'wall_plaster', 'south': 'window_day', 'north': 'window_day'})


def lobby_lights(strength=1.0):
    for (x, y) in ((3, 3), (8, 3), (3, 7), (8, 7), (6, 3), (6, 8)):
        area((x + 0.5, y + 0.5, SLAB - 0.05), 1.6, 120 * strength, (0.95, 0.97, 1.0))


def office_lights(strength=1.0):
    for (x, y) in ((3, 3), (8, 3), (3, 7), (8, 7)):
        area((x + 0.5, y + 0.5, ROOF - 0.05), 1.6, 110 * strength, (1.0, 0.95, 0.85))


# --- the shots ---------------------------------------------------------------------------------------------------------

def world_day(w, h, samples=70):
    sc = reset(w, h, samples, bg=(0.50, 0.66, 0.92), bg_strength=1.1)
    sun((0.5, 0.75, -0.9), 3.4, (1.0, 0.96, 0.88), 0.05)
    return sc


def shot_outside_day(w=1400, h=900):
    world_day(w, h)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    build_outpost(b, False)
    b.build('outpost', EMIT_DAY)
    # two hunters on the pavement and the captain's van-less street keeps it plain
    Figure('tier_4th', (3.5, -2.5, 1.0), 25, name='h1')
    Figure('hunter_rookie', (9.4, -3.0, 1.0), -35, name='h2')
    camera((-6.0, -15.5, 6.0), (6.4, 2.0, 3.6), 26)
    render(os.path.join(OUT, 'shot_outside_day.png'))


def shot_outside_night(w=1400, h=900):
    sc = reset(w, h, 90, bg=(0.012, 0.018, 0.045), bg_strength=1.0)
    sun((-0.4, 0.6, -0.8), 0.18, (0.55, 0.65, 1.0), 0.1)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    build_outpost(b, True, lit_upper=(2, 3, 5, 6, 7, 10))
    b.build('outpost', EMIT_NIGHT)
    Figure('tier_4th', (3.5, -2.5, 1.0), 25, name='h1')
    Figure('hunter_rookie', (9.4, -3.0, 1.0), -35, name='h2')
    point((-0.2, -3.4, 5.6), 700, (1.0, 0.86, 0.6), 0.25)
    for (x, z) in ((2.5, 2.5), (9.5, 2.5), (2.5, 6.5), (6.5, 6.5), (9.5, 6.5)):
        point((x, -0.6, z), 70, (1.0, 0.82, 0.55), 0.3)
    point((6.5, -1.6, 2.4), 180, (1.0, 0.82, 0.55), 0.3)
    camera((-6.0, -15.5, 6.0), (6.4, 2.0, 3.6), 26)
    render(os.path.join(OUT, 'shot_outside_night.png'))


def lobby_scene(w, h):
    sc = reset(w, h, 90, bg=(0.5, 0.64, 0.9), bg_strength=1.0)
    sun((0.5, 0.75, -0.9), 2.0, (1.0, 0.96, 0.88), 0.05)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    build_outpost(b, False, tree=False)
    outpost_interior_fixtures(b)
    office_back_window(b)
    # open the front wall's far windows so daylight gets in
    b.build('outpost', EMIT_DAY)
    lobby_lights()
    office_lights(0.6)
    return b


def staff_lobby():
    Figure('clerk_woman', (5.5, 6.55, 1.0 + 0.3 - 12 * PX), 180 + 180, sit=True, name='cw')
    Figure('clerk_man', (7.5, 6.55, 1.0 + 0.3 - 12 * PX), 360, sit=True, name='cm')


def shot_front_desk(w=1300, h=850):
    lobby_scene(w, h)
    Figure('clerk_woman', (5.5, 6.45, 1.0), 0, name='cw', rarm=-25)
    Figure('clerk_man', (7.6, 6.5, 1.0), 0, name='cm')
    Figure('tier_2nd', (9.6, 3.4, 1.0), 235, name='h')
    camera((5.4, 1.3, 2.2), (7.0, 6.2, 2.0), 19)
    render(os.path.join(OUT, 'shot_front_desk.png'))


def shot_board(w=1300, h=850):
    lobby_scene(w, h)
    Figure('hunter_rookie', (10.3, 1.5, 1.0), 90 - 15, name='r', head=(-15, 0))
    Figure('tier_4th', (10.2, 5.7, 1.0), 90 + 25, name='r2')
    camera((4.6, 3.6, 2.5), (12.0, 3.5, 2.3), 24)
    render(os.path.join(OUT, 'shot_board.png'))


def shot_office(w=1300, h=850):
    lobby_scene(w, h)
    z0 = SLAB + 1
    Figure('captain', (9.5, 9.38, z0), 0, name='cap')
    camera((8.7, 6.15, z0 + 1.7), (9.45, 9.0, z0 + 1.1), 19)
    render(os.path.join(OUT, 'shot_office.png'))


def studio(w, h, key=(0.3, 0.2, 1.0)):
    reset(w, h, 90, bg=(0.10, 0.10, 0.12), bg_strength=1.0)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    b.fill(-8, -16, -1, 8, 4, -1, {'all': 'floor_vinyl'})
    b.fill(-6, 3, 0, 6, 3, 5, {'all': 'wall_plaster'})
    b.build('studio')
    area((0, -6, 6), 8, 650, (1.0, 0.96, 0.9), (0, 0.8, -0.8))
    area((-5, -2, 3), 4, 200, (0.8, 0.88, 1.0), (1, 0.3, -0.3))
    area((5, -2, 3), 4, 130, (1.0, 0.9, 0.8), (-1, 0.3, -0.3))
    area((0, 5, 5), 6, 120, (1.0, 0.95, 0.9), (0, -0.8, -0.6))
    return atlas, b


def shot_tiers_front(w=1400, h=850):
    studio(w, h)
    for i, s in enumerate(('tier_4th', 'tier_2nd', 'tier_special')):
        Figure(s, (-1.7 + i * 1.7, 0.0, 0.0), 0, name='t%d' % i)
    camera((0, -8.5, 1.0), (0, 0, 0.93), 58)
    render(os.path.join(OUT, 'shot_tiers_front.png'))


def shot_tiers_back(w=1400, h=850):
    studio(w, h)
    for i, s in enumerate(('tier_4th', 'tier_2nd', 'tier_special')):
        Figure(s, (-1.7 + i * 1.7, 0.0, 0.0), 180, name='t%d' % i)
    camera((0, -8.5, 1.0), (0, 0, 0.93), 58)
    # the back of the figures faces the camera: the wall behind is a plain one in front, so swap it
    render(os.path.join(OUT, 'shot_tiers_back.png'))


def shot_cast(w=1400, h=850):
    studio(w, h)
    for i, sk in enumerate(('captain', 'clerk_woman', 'clerk_man', 'hunter_rookie')):
        Figure(sk, (-2.4 + i * 1.6, 0.0, 0.0), 0 if i != 3 else -20, name='c%d' % i)
    camera((0, -8.0, 1.0), (0, 0, 0.93), 50)
    render(os.path.join(OUT, 'shot_cast.png'))


def shot_gear_hands(w=1400, h=850):
    studio(w, h)
    spec = [('katana', (2, 13), -55, 0.058), ('throwing_knives', (2, 12), -55, 0.052), ('pistol', (3, 9), -78, 0.045)]
    for i, (item, grip, ang, size) in enumerate(spec):
        fig = Figure('hunter_rookie', (-2.0 + i * 2.0, 0.0, 0.0), 50, rarm=ang, name='g%d' % i)
        ob = sprite_object(item, grip, size=size)
        hand = fig.hand('rarm')
        # sprite right -> forward along the arm, its depth across the arm
        rot = Matrix.Rotation(math.radians(-90), 4, 'Z')
        tilt = Matrix.Rotation(math.radians(-8 if item != 'pistol' else 0), 4, 'Y')
        ob.matrix_world = hand @ Matrix.Rotation(math.radians(-ang), 4, 'X') @ rot @ tilt
    camera((0, -8.5, 1.0), (0, 0, 0.93), 50)
    render(os.path.join(OUT, 'shot_gear_hands.png'))


def shot_gear_display(w=1400, h=600):
    reset(w, h, 90, bg=(0.07, 0.07, 0.09), bg_strength=1.0)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    b.fill(-8, -3, -1, 8, 3, -1, {'all': 'counter_top_plain'})
    b.build('table')
    names = [('katana', (8, 8)), ('throwing_knives', (8, 8)), ('pistol', (8, 8)), ('rank_badge', (8, 8)), ('yen_note_1000', (8, 8)),
             ('yen_note_5000', (8, 8)), ('devil_blood_ration', (8, 8))]
    for i, (n, grip) in enumerate(names):
        ob = sprite_object(n, grip, size=0.17, thick=1.0)
        ob.location = (-6.0 + i * 2.0, 0.0, 1.0)
        ob.rotation_euler = (0, math.radians(0), math.radians(-28))
    area((0, -5, 6), 10, 900, (1.0, 0.96, 0.9), (0, 0.6, -0.8))
    area((-6, -3, 3), 4, 250, (0.8, 0.88, 1.0), (1, 0.4, -0.3))
    camera((0, -11.5, 1.5), (0, 0, 1.0), 26)
    render(os.path.join(OUT, 'shot_gear_display.png'))


SHOTS = {
    'outside_day': shot_outside_day, 'outside_night': shot_outside_night, 'front_desk': shot_front_desk, 'board': shot_board,
    'office': shot_office, 'tiers_front': shot_tiers_front, 'tiers_back': shot_tiers_back, 'cast': shot_cast, 'gear_hands': shot_gear_hands,
    'gear_display': shot_gear_display,
}


def main():
    global QUALITY
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    only = list(SHOTS)
    if '--only' in argv:
        only = argv[argv.index('--only') + 1].split(',')
    if '--quality' in argv:
        QUALITY = float(argv[argv.index('--quality') + 1])
    for n in only:
        SHOTS[n]()


if __name__ == '__main__':
    main()
