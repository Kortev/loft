"""Renders the Eternity Devil look sheet (Chainsaw Man mod): the devil, the corridor at four stages of rot, the lobby, the
outside at night, the hunt's wallpaper eye and bleeding wall. Blender, background:

    blender --background --python tools/eternity_look_scenes.py -- [--only name,name] [--quality 1]

Writes work/eternity_look_v2/shot_*.png; tools/eternity_look_sheet.py puts them on the sheets. Everything is nearest-filtered
16-pixel blocks lit by a few warm lamps, the way the game would show it; the devil is `build_devil()` of
tools/eternity_devil_model.py, the block textures are tools/hotel_textures.py packed into one atlas.
"""
import math
import os
import sys

import bpy
import bmesh
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_devil_model as dm  # noqa: E402
import hotel_textures as ht  # noqa: E402
from PIL import Image  # noqa: E402

OUT = dm.OUT
TILES = None
QUALITY = 1.0


# --- the scene kit ------------------------------------------------------------------------------------------------

def reset(w, h, samples=48, bg=(0.012, 0.011, 0.014)):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = max(8, int(samples * QUALITY))
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 5
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = 100
    sc.render.image_settings.file_format = 'PNG'
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    sc.display_settings.display_device = 'sRGB'
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (*bg, 1)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 1.0
    sc.world = world
    return sc


def camera(pos, target, lens=24, roll=0.0):
    cam = bpy.data.cameras.new('cam')
    cam.lens = lens
    cam.clip_start = 0.05
    cam.clip_end = 200
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


def sun(direction, energy, colour=(1, 1, 1)):
    li = bpy.data.lights.new('s', 'SUN')
    li.energy = energy
    li.color = colour
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
    """All the hotel's 16 x 16 tiles on one image, nearest-filtered."""

    def __init__(self):
        tiles = ht.build()
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
        self.path = os.path.join(OUT, 'blocks_atlas.png')
        img.save(self.path)
        self.img = bpy.data.images.load(self.path)
        self.img.colorspace_settings.name = 'sRGB'
        self.mats = {}

    def uv(self, name, u, v):
        """u, v in 0..1 over the tile, v up."""
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
    """A mesh of blocks (unit cubes with a tile per face) in one object per material, solid neighbours culling the faces
    between them. Coordinates are block corners; (0,0,0) is the corner of the block at x 0, y 0, z 0."""

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
        self.solid = {}         # (x, y, z) -> {face: tile}
        self.extra = []         # (emit, [quads]) for things that are not full cubes

    def fill(self, x0, y0, z0, x1, y1, z1, tiles):
        """Blocks from (x0,y0,z0) to (x1,y1,z1) inclusive; `tiles` is a tile name or {face: name} (key 'side' covers the four)."""
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
        """`emit_tiles` maps a tile name to its emission strength."""
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
                corners = [(x + p[0], y + p[1], z + p[2]) for p in (a, b, c, d)]
                self.quad(bm, uvl, corners, tile)
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
        """A free box (corner coordinates in blocks, not on the grid): its own object."""
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


def player(pos, facing=(0, -1, 0), scale=1.0):
    """A Minecraft-player-sized figure (2 blocks with the legs): boxes in flat colours, for scale."""
    col = {'skin': (0.78, 0.6, 0.48), 'shirt': (0.2, 0.55, 0.62), 'legs': (0.18, 0.2, 0.55), 'hair': (0.2, 0.12, 0.07)}
    parts = [
        ('legs', (-0.25, -0.125, 0.0), (0.25, 0.125, 0.75)),
        ('shirt', (-0.25, -0.125, 0.75), (0.25, 0.125, 1.5)),
        ('shirt', (-0.5, -0.125, 0.75), (-0.25, 0.125, 1.5)),
        ('shirt', (0.25, -0.125, 0.75), (0.5, 0.125, 1.5)),
        ('skin', (-0.25, -0.25, 1.5), (0.25, 0.25, 2.0)),
        ('hair', (-0.27, -0.27, 1.8), (0.27, 0.27, 2.02)),
    ]
    ang = math.atan2(facing[1], facing[0]) + math.pi / 2
    m = Matrix.Translation(pos) @ Matrix.Rotation(ang, 4, 'Z')
    for k, lo, hi in parts:
        bm = bmesh.new()
        bmesh.ops.create_cube(bm, size=1.0)
        for v in bm.verts:
            v.co = Vector(((lo[0] if v.co.x < 0 else hi[0]) * scale, (lo[1] if v.co.y < 0 else hi[1]) * scale, (lo[2] if v.co.z < 0 else hi[2]) * scale))
        me = bpy.data.meshes.new('p')
        bm.to_mesh(me)
        bm.free()
        mat = bpy.data.materials.new('p')
        mat.diffuse_color = (*col[k], 1)
        mat.use_nodes = True
        mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (*col[k], 1)
        mat.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 1
        me.materials.append(mat)
        ob = bpy.data.objects.new('player', me)
        ob.matrix_world = m
        bpy.context.scene.collection.objects.link(ob)


# --- the devil shots ----------------------------------------------------------------------------------------------

def shot_devil_views(w=900, h=650):
    """The devil on its own, front-on and three-quarter, with a player for scale."""
    out = []
    for name, cam_pos, tgt in (('devil_front', (0, 12.5, 2.0), (0, 0, 2.0)), ('devil_three', (7.0, 8.5, 2.6), (0, -2.0, 1.8))):
        reset(w, h, 48, bg=(0.07, 0.06, 0.07))
        dev = dm.build_devil(OUT, jaw_open=0.3, lid_open=0.5)
        bm = None
        # a plain floor and back so the shape reads
        atlas = BlockAtlas()
        b = Blocks(atlas)
        b.fill(-8, -14, -1, 8, 10, -1, 'lobby_floor')
        b.build('floor')
        player((3.9, 2.2, 0.0), (0, -1, 0))
        area((0, 6, 7), 9, 900, (1.0, 0.86, 0.7), (0, -0.4, -1))
        area((-6, 8, 3), 6, 300, (0.8, 0.85, 1.0), (0.6, -0.8, -0.2))
        point((0, 3.5, 2.4), 120, (1.0, 0.72, 0.45))
        camera(cam_pos, tgt, 30)
        render(os.path.join(OUT, 'shot_%s.png' % name))
        out.append(name)
    return out


# --- the corridor -------------------------------------------------------------------------------------------------

FLOORS = {
    # floor: (stage, ceiling-light tile pattern, lamp tile pattern, light watts for a lit one)
    1: ('clean', ('lit',), ('lit',), 70),
    4: ('stained', ('dim',), ('dim',), 46),
    6: ('damp', ('dim', 'flicker', 'dim', 'off'), ('flicker', 'dim'), 52),
    7: ('veins', ('dim', 'dim', 'flicker'), ('dim', 'off'), 50),
    8: ('wrong', ('lit',), ('lit',), 30),
}
LIGHT_TILES = {'ceiling_light_lit': 1.6, 'ceiling_light_dim': 1.0, 'ceiling_light_flicker': 1.2, 'wall_lamp_lit': 0.35, 'wall_lamp_dim': 0.25, 'wall_lamp_flicker': 0.3, 'lift_indicator_8': 1.2, 'lift_indicator_1': 1.2}
LIGHT_COL = {1: (1.0, 0.78, 0.5), 4: (1.0, 0.7, 0.42), 6: (0.95, 0.72, 0.45), 7: (1.0, 0.62, 0.42), 8: (1.0, 0.86, 0.66)}


def corridor(floor, y_from, y_to, devil=False, end_wall=True, lights=True):
    """A corridor of the plan's size (3 wide, 3 high, doors every 6), floor top at z = 0, air at x -1..1, running along +Y."""
    stage, ceil_pat, lamp_pat, watts = FLOORS[floor]
    atlas = BlockAtlas()
    b = Blocks(atlas)
    carpet = 'carpet_%s' % stage
    edge = 'carpet_edge_%s' % stage
    paper = 'wallpaper_%s' % stage
    wains = 'wainscot_%s' % stage
    for y in range(y_from, y_to + 1):
        b.set(-1, y, -1, {'all': edge})
        b.set(0, y, -1, {'all': carpet})
        b.set(1, y, -1, {'all': edge})
        n = (y - y_from) // 4
        ci = ceil_pat[n % len(ceil_pat)]
        for x in (-1, 0, 1):
            tile = 'ceiling_light_%s' % ci if (x == 0 and y % 4 == 1) else 'ceiling_tile'
            b.set(x, y, 3, {'all': 'ceiling_tile', 'bottom': tile})
        for x in (-2, 2):
            door = y % 6 == 2
            lamp = y % 6 == 5
            b.set(x, y, -1, {'all': wains})
            b.set(x, y, 0, {'all': 'door_bottom' if door else wains})
            li = lamp_pat[(y // 6) % len(lamp_pat)]
            b.set(x, y, 1, {'all': 'door_top_%s' % ('208' if floor == 1 else '404' if floor == 4 else '602' if floor == 6 else '701' if floor == 7 else '802') if door else ('wall_lamp_%s' % li if lamp and li != 'off' else paper)})
            b.set(x, y, 2, {'all': paper})
            b.set(x, y, 3, {'all': 'ceiling_tile'})
    if end_wall:
        for x in (-2, -1, 0, 1, 2):
            for z in range(-1, 4):
                b.set(x, y_from - 1, z, {'all': paper if z > 0 else wains})
        # a lift at the end of the corridor
        b.set(0, y_from - 1, 0, {'all': 'lift_door_l'})
        b.set(1, y_from - 1, 0, {'all': 'lift_door_r'})
        b.set(0, y_from - 1, 1, {'all': 'lift_door_l'})
        b.set(1, y_from - 1, 1, {'all': 'lift_door_r'})
        b.set(0, y_from - 1, 2, {'all': 'lift_indicator_8' if floor == 8 else 'lift_indicator_1'})
        b.set(1, y_from - 1, 2, {'all': 'wall_lamp_lit' if stage in ('clean',) else paper})
        b.set(-1, y_from - 1, 1, {'all': 'lift_buttons'})
    if devil:
        # the walls round the devil are broken open: it bursts out of them either side and through the end
        for y in range(-6, 3):
            for x in (-2, 2):
                for z in (0, 1, 2):
                    b.solid.pop((x, y, z), None)
    b.build('hotel', emit_tiles=LIGHT_TILES)
    if lights:
        col = LIGHT_COL[floor]
        for y in range(max(y_from, 1), y_to + 1):
            if y % 4 == 1:
                n = (y - y_from) // 4
                ci = ceil_pat[n % len(ceil_pat)]
                w = {'lit': watts, 'dim': watts * 0.55, 'flicker': watts * 0.3, 'off': 0}[ci]
                if w:
                    point((0.5, y + 0.5, 3.0), w, col, 0.3)
            if y % 6 == 5:
                li = lamp_pat[(y // 6) % len(lamp_pat)]
                w = {'lit': 28, 'dim': 10, 'flicker': 7, 'off': 0}[li] * watts / 60
                if w:
                    for x in (-1.6, 2.6):
                        point((x + (0.45 if x < 0 else -0.45), y + 0.5, 1.7), w, col, 0.1)
    return atlas, b


def put_devil(jaw=0.55, lid=0.5, at=(0.5, 0.0, 0.0)):
    dev = dm.build_devil(OUT, jaw_open=jaw, lid_open=lid)
    dev['pulse'].location = dm.CENTRE + Vector(at)
    bpy.context.view_layer.update()
    return dev


def shot_corridors(w=880, h=600):
    for f in (1, 4, 6, 7, 8):
        reset(w, h, 40)
        corridor(f, 0, 16)
        camera((0.5, 12.0, 1.6), (0.5, 0.0, 1.45), 20)
        render(os.path.join(OUT, 'shot_corridor_f%d.png' % f))


def shot_key(w=1100, h=760):
    reset(w, h, 90)
    corridor(8, -9, 20, devil=True)
    dev = put_devil(0.8, 0.5)
    point((0.5, 0.6, 1.0), 6, (1.0, 0.2, 0.15), 0.4)      # the maw's own red
    camera((0.5, 6.0, 1.6), (0.5, 0.0, 1.55), 23)
    render(os.path.join(OUT, 'shot_key.png'))


def shot_devil_closeups(w=900, h=600):
    reset(w, h, 80, bg=(0.02, 0.018, 0.02))
    dev = put_devil(0.7, 0.55, (0, 0, 0))
    area((0, 6, 5), 6, 700, (1.0, 0.82, 0.62), (0, -1, -0.6))
    point((0.8, 2.2, 1.3), 90, (1.0, 0.3, 0.25), 0.3)
    camera((0.9, 4.4, 1.9), (0.1, 0.0, 2.2), 32)
    render(os.path.join(OUT, 'shot_mouth_eyes.png'))
    # the heart: the flesh over it cut away, the jaw wide
    dm.pose(dev, 0.85, 0.5)
    for o in dev['parts']:
        if o.name == 'heart_cover':
            o.hide_render = True
    reset_lights = [o for o in bpy.data.objects if o.type == 'LIGHT']
    for o in reset_lights:
        bpy.data.objects.remove(o)
    area((0, 5, 4), 4, 500, (1.0, 0.85, 0.7), (0, -1, -0.6))
    point((0.0, 0.9, 1.5), 120, (1.0, 0.45, 0.35), 0.2)
    point((0.0, -0.35, 1.2), 40, (1.0, 0.45, 0.4), 0.15)
    camera((0.0, 2.6, 0.5), (0.0, -1.0, 0.95), 36)
    render(os.path.join(OUT, 'shot_heart.png'))


# --- lobby, outside, the hunt -------------------------------------------------------------------------------------

def shot_lobby(w=900, h=600):
    reset(w, h, 90)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    X0, X1, Y0, Y1 = 0, 12, 0, 10
    CZ = 5                                   # a tall lobby: the ceiling's block at z 5
    for x in range(X0, X1 + 1):
        for y in range(Y0, Y1 + 1):
            b.set(x, y, -1, {'all': 'lobby_floor'})
            lit = (x % 4 == 2 and y % 4 == 2)
            b.set(x, y, CZ, {'all': 'ceiling_tile', 'bottom': 'ceiling_light_lit' if lit else 'ceiling_tile'})
    for z in range(0, CZ):
        for x in range(X0, X1 + 1):
            b.set(x, Y1 + 1, z, {'all': 'lobby_wall'})
            b.set(x, Y0 - 1, z, {'all': 'lobby_wall'})
        for y in range(Y0, Y1 + 1):
            b.set(X0 - 1, y, z, {'all': 'lobby_wall'})
            b.set(X1 + 1, y, z, {'all': 'lobby_wall'})
    # the lift in the right wall: two doors, the floor indicator over them, a call button beside
    for z in (0, 1):
        b.set(X1 + 1, 5, z, {'all': 'lift_door_l'})
        b.set(X1 + 1, 6, z, {'all': 'lift_door_r'})
    b.set(X1 + 1, 5, 2, {'all': 'lift_indicator_1'})
    b.set(X1 + 1, 6, 2, {'all': 'wall_lamp_lit'})
    b.set(X1 + 1, 4, 1, {'all': 'lift_buttons'})
    # the reception: a long desk in front of the back wall, the key board and the sign behind
    for x in range(3, 10):
        b.set(x, 7, 0, {'all': 'desk_front', 'top': 'desk_top'})
    b.set(3, 8, 0, {'all': 'desk_front', 'top': 'desk_top'})
    b.set(9, 8, 0, {'all': 'desk_front', 'top': 'desk_top'})
    for x in (5, 6, 7):
        for z in (1, 2):
            b.set(x, Y1 + 1, z, {'all': 'key_board'})
        b.set(x, Y1 + 1, 0, {'all': 'wainscot_clean'})
    # the entrance: glass doors in the front wall
    for x in range(4, 8):
        b.set(x, Y0 - 1, 0, {'all': 'entrance_door_bottom'})
        b.set(x, Y0 - 1, 1, {'all': 'entrance_door_top'})
    # a seating bench (carpet blocks) by the left wall
    for y in range(2, 6):
        b.set(0, y, 0, {'all': 'carpet_clean'})
        b.set(0, y, 1, {'all': 'carpet_edge_clean'})
    b.build('lobby', emit_tiles={'ceiling_light_lit': 1.6, 'entrance_door_top': 0.8, 'entrance_door_bottom': 0.8, 'lift_indicator_1': 1.2, 'wall_lamp_lit': 0.4})
    # the sign over the key board, brass-framed and lit
    for i in range(3):
        b.box(5.0 + i, Y1 + 0.88, 3.1, 6.0 + i, Y1 + 1.0, 4.1, {'all': 'sign_reception_%d' % i}, emit=1.0)
    # the bell on the desk (a brass dome), a lamp at each end
    b.box(6.1, 6.9, 1.0, 6.8, 7.5, 1.45, {'all': 'bell'}, emit=0.0)
    for x in (3.4, 9.0):
        b.box(x, 6.9, 1.0, x + 0.5, 7.4, 1.5, {'all': 'wall_lamp_lit', 'top': 'ceiling_light_lit'}, emit=0.6)
    for x in (2, 6, 10):
        for y in (2, 6, 8):
            point((x + 0.5, y + 0.5, 4.8), 70, (1.0, 0.82, 0.58), 0.3)
    point((6.5, 6.2, 1.8), 40, (1.0, 0.75, 0.5), 0.3)
    camera((8.2, 1.2, 1.7), (6.3, 9.0, 1.6), 22)
    render(os.path.join(OUT, 'shot_lobby.png'))


def building(atlas, b, nx=24, floors=8, lit_pattern=None):
    """The hotel's front (a slice of it: the entrance and the rows of windows either side), y = 0 is the wall's face."""
    for f in range(floors):
        for x in range(nx):
            for k in range(4):
                z = f * 4 + k
                if f == 0:
                    tile = 'entrance_door_top' if (9 <= x <= 14 and k in (0, 1)) else 'window_lit' if (k in (0, 1, 2) and (x < 8 or x > 15)) else 'facade_tile'
                    if 9 <= x <= 14 and k == 0:
                        tile = 'entrance_door_bottom'
                    if 9 <= x <= 14 and k == 1:
                        tile = 'entrance_door_top'
                    if 9 <= x <= 14 and k > 1:
                        tile = 'facade_tile'
                    if k == 3:
                        tile = 'facade_tile'
                else:
                    if k in (1, 2) and x % 3 != 0:
                        lit = lit_pattern(f, x) if lit_pattern else False
                        tile = 'window_lit' if lit else 'window_night'
                    else:
                        tile = 'facade_concrete' if f > 1 else 'facade_tile'
                b.set(x, 0, z, {'all': 'facade_concrete', 'north': tile})
        # a thin floor-slab band
    # the roof edge
    for x in range(nx):
        b.set(x, 0, floors * 4, {'all': 'facade_concrete', 'north': 'facade_concrete'})


def shot_outside(w=900, h=1000):
    reset(w, h, 70, bg=(0.012, 0.016, 0.034))
    atlas = BlockAtlas()
    b = Blocks(atlas)
    nx = 24
    # ground: grass, pavement, a little road
    for x in range(-14, nx + 14):
        for y in range(-30, 6):
            t = 'asphalt' if y < -20 else 'pavement' if y < -2 else 'grass'
            if -8 <= y < 0 and 7 <= x <= 16:
                t = 'pavement'
            b.set(x, y, -1, {'all': t})
    building(atlas, b, nx, 8, lambda f, x: (f * 7 + x * 3) % 10 < 3 and f < 7)
    # the entrance canopy
    b.build('hotel', emit_tiles={'window_lit': 1.1, 'entrance_door_top': 1.1, 'entrance_door_bottom': 1.1})
    b.box(6.5, -4.0, 3.0, 17.5, 0.0, 3.5, {'all': 'canopy_top', 'bottom': 'canopy_under', 'north': 'canopy_edge', 'west': 'canopy_edge', 'east': 'canopy_edge'}, emit=0.0)
    ob = b.box(6.5, -4.0, 2.97, 17.5, 0.0, 3.0, {'all': 'canopy_under'}, emit=1.6)
    for i in range(8):
        b.box(8.0 + i, -0.12, 4.0, 9.0 + i, 0.0, 5.0, {'all': 'sign_hotel_%d' % i}, emit=1.4)
    for sx in (6.8, 17.0):                                                 # canopy posts
        b.box(sx, -3.7, 0.0, sx + 0.4, -3.3, 3.0, {'all': 'facade_concrete'})
    # light: moon, the canopy's, the lobby's, a street lamp
    sun((-0.5, 0.7, -0.6), 0.5, (0.55, 0.65, 1.0))
    point((12, -3, 2.6), 380, (1.0, 0.82, 0.55), 0.4)
    point((12, -1, 1.5), 260, (1.0, 0.8, 0.5), 0.4)
    point((4, -6, 5.5), 900, (1.0, 0.8, 0.5), 0.6)
    camera((12.0, -27, 3.2), (12.0, 0, 11.0), 24)
    render(os.path.join(OUT, 'shot_outside.png'))


def hunt_scene(tiles_at, cam, bulge=None, w=760, h=560):
    reset(w, h, 90)
    atlas = BlockAtlas()
    b = Blocks(atlas)
    paper, wains = 'wallpaper_stained', 'wainscot_stained'
    for y in range(-2, 9):
        b.set(-1, y, -1, {'all': 'carpet_edge_stained'})
        b.set(0, y, -1, {'all': 'carpet_stained'})
        b.set(1, y, -1, {'all': 'carpet_edge_stained'})
        for x in (-1, 0, 1):
            b.set(x, y, 3, {'all': 'ceiling_tile', 'bottom': 'ceiling_light_dim' if (x == 0 and y % 4 == 1) else 'ceiling_tile'})
        for x in (-2, 2):
            b.set(x, y, 0, {'all': wains})
            b.set(x, y, 1, {'all': paper})
            b.set(x, y, 2, {'all': paper})
            b.set(x, y, 3, {'all': 'ceiling_tile'})
    for x in (-2, -1, 0, 1, 2):
        for z in range(-1, 4):
            b.set(x, -3, z, {'all': paper})
    for (x, y, z), tile in tiles_at.items():
        b.set(x, y, z, {'all': tile})
    b.build('hotel', emit_tiles={'ceiling_light_dim': 1.0})
    if bulge:
        yc, zc = bulge
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=14, v_segments=10, radius=1.0)
        uvl = bm.loops.layers.uv.verify()
        for v in bm.verts:
            v.co = Vector((-1.0 + v.co.x * 0.3, yc + v.co.y * 0.52, zc + v.co.z * 0.72))
        for f in bm.faces:
            for lp in f.loops:
                co = lp.vert.co
                lp[uvl].uv = atlas.uv('wall_bulge', 0.5 + (co.y - yc) / 1.04, 0.5 + (co.z - zc) / 1.44)
        me = bpy.data.meshes.new('bulge')
        bm.to_mesh(me)
        bm.free()
        me.materials.append(atlas.material(0.0))
        ob = bpy.data.objects.new('bulge', me)
        bpy.context.scene.collection.objects.link(ob)
    point((0.5, 1.5, 2.9), 55, (1.0, 0.7, 0.45), 0.3)
    point((0.5, 5.5, 2.9), 55, (1.0, 0.7, 0.45), 0.3)
    point((0.5, 3.0, 1.0), 14, (1.0, 0.8, 0.6), 0.3)
    camera(cam[0], cam[1], 24)


def shot_hunt(w=760, h=560):
    # an eye that opens in the wallpaper: three stages along the wall (the wall at x = -2, its face at x = -1)
    hunt_scene({(-2, 2, 1): 'wallpaper_eye_1', (-2, 3, 1): 'wallpaper_eye_3', (-2, 4, 2): 'wallpaper_eye_2'}, ((0.9, 5.6, 1.5), (-1.0, 3.0, 1.3)))
    render(os.path.join(OUT, 'shot_hunt_eye.png'))
    # a wall that bleeds when cut
    hunt_scene({(2, 2, 1): 'wall_bleed_0', (2, 3, 1): 'wall_bleed_1', (2, 3, 0): 'wainscot_veins'}, ((-0.1, 5.4, 1.5), (2.0, 2.8, 1.2)))
    render(os.path.join(OUT, 'shot_hunt_bleed.png'))
    # a wall that breathes: the paper pushed out in a bulge
    hunt_scene({}, ((0.9, 6.2, 1.5), (-1.0, 3.3, 1.4)), bulge=(3.5, 1.5))
    render(os.path.join(OUT, 'shot_hunt_breathe.png'))


SHOTS = {
    'devil_views': shot_devil_views,
    'corridors': shot_corridors,
    'key': shot_key,
    'closeups': shot_devil_closeups,
    'lobby': shot_lobby,
    'outside': shot_outside,
    'hunt': shot_hunt,
}


def main():
    global QUALITY
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    only = None
    if '--only' in argv:
        only = argv[argv.index('--only') + 1].split(',')
    if '--quality' in argv:
        QUALITY = float(argv[argv.index('--quality') + 1])
    for name, fn in SHOTS.items():
        if only is None or name in only:
            fn()


if __name__ == '__main__':
    main()
