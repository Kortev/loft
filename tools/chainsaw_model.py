"""Builds Chainsaw Man's form in Blender: the head, the saws out of the forearms and the cord on the chest.

Needs Blender's Python module (py -3.11 -m pip install "bpy==4.5.*" numpy pillow). Run from the repository root:

    py -3.11 tools/chainsaw_model.py --out DIR --renders   # preview renders and DIR/chainsaw.blend, for approval
    py -3.11 tools/chainsaw_model.py --game                # bakes the game's mesh and texture (chainsawman/)

Add --only name,name to render some of them, and CSM_SAMPLES=24 for a quick look.

Blender units are the player's pixels, sixteen to a block. The form is built on a stand-in of the player model as it
stands, feet at the origin, facing -y, z up: legs to 12, body to 24, head to 32, arms 4 wide either side of the body.
The stand-in is only there to judge the parts against; in the game they go on the real player, whose head is hidden
while the form is on.

The design follows the anime (season 1 and the Reze arc), from stills kortev chose; the head is measured off the
side-on one. It is a chainsaw's engine with a face. The back half is the engine: a dark iron block with a round plate on
each side, its top cut in notches like a cog and a dark socket in it, a screw by it and a ribbed hinge low down; the
rear handle stands off its back in a D, a pole stands up out of its top behind the bar, and a coiled cable runs down
where it meets the visor. Over the front is the orange visor, the engine's cover, shaped like a prow: a ridge along its
top, its front curving down to a point, four curved vent slots on each side round its back corner, the inner two his
eye, burning yellow. Under it the mouth: long straight white teeth hanging from the visor nearly to the jaw, short ones
standing up between them, round the front and back along the sides. The jaw is an orange plate under the teeth, a V
from the front, its front turned down into a point. The neck, and the cheeks behind the side teeth, are bound in dark
cords laid like brickwork. The bar comes straight out of the top of the visor's front, long, pale and bloodied, its
chain all spikes. A bar comes out of the middle of each forearm and runs on past the fist. A starter cord
comes out of the middle of the chest and hangs with its T handle at the belly.
"""
import math
import os
import random
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Matrix, Vector
import numpy as np
from PIL import Image, ImageDraw

# --- layout (pixels; feet at the origin, facing -y, z up) ---------------------------------------------------------

# The head is laid out from the side-on still (the bar to the right): measured there, then scaled so it stands 11
# pixels from the jaw's point to the top of the engine. y is forward-negative, z up; the back of the visor is y = 0.
#
# The engine block, the back half of the head: half its width, its back and front, bottom and top, how round its edges.
ENGINE = (3.4, 5.7, -1.5, 26.1, 33.4)
ENGINE_ROUND = 0.45
# Its outline seen from the side, going round from the top at the front: the top rising to a crown and sloping off to
# the back, the back full and round, tucked under at the bottom. It narrows toward its top.
ENGINE_PROFILE = [(-1.5, 32.9), (0.8, 33.4), (2.6, 33.7), (3.6, 33.5), (4.9, 32.8), (5.6, 31.6), (5.7, 28.6), (5.3, 27.0),
                  (4.4, 26.1), (-1.5, 26.1)]
# The armour plates over its top, stepped one behind the other, and how wide they are.
TOP_PLATES = [[(1.3, 33.5), (2.0, 34.0), (2.9, 34.1), (3.2, 33.75), (2.7, 33.3), (1.3, 33.2)],
              [(3.1, 33.95), (4.3, 33.6), (5.3, 32.85), (5.0, 32.35), (3.3, 33.2)]]
TOP_PLATE_HALF = 2.3
# The ribs down the back of the neck, under the engine: how many, the top one's height, how far apart.
RIBS = (5, 25.7, 0.72)
# The short chain down each side behind the visor, from a curl under the screw to the back of the jaw: its path (y, z)
# and how far apart its links are.
CHAIN_PATH = [(1.4, 29.5), (1.85, 29.55), (1.98, 29.15), (1.8, 28.75), (1.7, 27.8), (1.6, 26.4), (1.3, 25.0)]
CHAIN_PITCH = 0.36
# How thick the covers on its sides are, and how many fins the cylinder on its back has.
COVER_T = 0.3
FINS = 5
# The plate on each side of the engine with its round socket (an eye-like hole) and a screw below and in front of it:
# the socket's middle (y, z), the plate's radius and how many notches its top edge has, the socket's radius.
SOCKET = (3.0, 30.4)
SIDE_PLATE_R = 2.05
SIDE_PLATE_NOTCHES = 7
SOCKET_R = 0.85
SCREW = (1.75, 29.0, 0.28)
# The rear handle, a D standing out from the back of the engine (y, z points round it), and its thickness.
REAR_HANDLE = [(5.0, 32.5), (6.4, 32.5), (7.0, 31.8), (7.0, 28.9), (6.4, 28.2), (5.0, 28.2)]
HANDLE_R = 0.45
# The pole standing up out of the top of the head behind the bar: its foot (y, z), its tilt back from upright
# (degrees), length and radius.
POLE = (1.4, 33.2, 32.0, 5.0, 0.48)
# The coiled cable down the join of the engine and the visor: its top and bottom (z), at y, and its coil's radius.
# The visor, seen from the side, from the spike at its top back, round its front to its point, back along its bottom
# edge and up its back edge; the points of its outer edge (top and front) are the first OUTER of them, and along those
# its middle stands up in a ridge RIDGE high. It is the shell: it comes back over the front of the engine and is wider
# than it, so the engine sits inside it.
VISOR = [(0.7, 33.8), (-0.6, 33.7), (-1.9, 33.35), (-3.0, 32.75), (-3.9, 31.95), (-4.7, 30.95), (-5.4, 29.8),
         (-6.0, 28.4), (-6.4, 27.0), (-4.5, 27.4), (-2.5, 27.75), (-0.9, 28.05), (1.35, 28.25), (1.45, 28.8),
         (1.1, 29.6), (1.2, 31.2), (1.05, 32.6)]
VISOR_OUTER = 9
VISOR_RIDGE = 0.55
# Which of its points are its front point, and the back of the mouth under it.
VISOR_POINT = 8
VISOR_MOUTH = 11
# Its half width at the back and at the front point: it narrows to a prow.
VISOR_HALF = (4.15, 1.3)
# The vent slots on its sides: the corner they curve round (y, z), their radii and width, and how far round they go
# (radians from straight up toward the front). The inner two are the eye.
VENT_C = (-0.9, 27.75)
VENT_R = (1.35, 2.35, 3.35, 4.35)
VENT_W = 0.36
VENT_SWEEP = (-0.05, 1.55)
# The jaw plate, seen from the side: a flat top under the teeth, its front turned down into a point. Its half width at
# the top and at the point: seen from the front it is a V.
JAW = [(1.2, 24.75), (-3.9, 24.75), (-4.9, 21.9), (-3.0, 23.85), (1.2, 24.15)]
JAW_HALF = (2.9, 0.35)
# The teeth: the line they stand on (the front row's y and half width, the sides' half width and how far back they go),
# how far apart, and their length and half thickness. The upper hang from under the visor nearly to the jaw.
TEETH_FRONT = (-4.0, 2.4)
TEETH_SIDE = (2.85, -0.9)
TOOTH_STEP = 0.7
UPPER_TOOTH = (2.5, 0.2)
LOWER_TOOTH = (1.5, 0.17)
# The neck of cords, under the engine and down into the collar: half width, back, front, bottom, top.
NECK = (3.0, 3.6, -1.2, 22.4, 26.6)
# How many wires make the neck, and how many are in each cheek.
NECK_WIRES = 40
CHEEK_WIRES = 6
# The head's saw: where the bar's root is (inside the visor), its length, height and thickness, and its tilt up.
HEAD_BAR_ROOT = Vector((0.0, 0.0, 30.9))
HEAD_BAR = (30.0, 4.2, 0.75)
HEAD_BAR_TILT = 0.0
# The arms' saws: length, height and thickness; where they leave the forearm, measured down the arm from the shoulder.
ARM_BAR = (19.0, 3.4, 0.6)
ARM_BAR_ROOT = 3.5
# The chain's cutters: how far apart they are round the bar, and how far their spikes stand out, as part of the
# bar's height.
CUTTER_PITCH = 0.85
SPIKE = 0.3
# How many frames of each chain are built: one for the renders; the game's copy has CHAIN_FRAMES_GAME, shown in turn.
CHAIN_FRAMES = 1
CHAIN_FRAMES_GAME = 4
# The cord: where it leaves the chest, how far it hangs, and the T handle's length and thickness.
CORD_ROOT = Vector((0.0, -2.05, 20.5))
CORD_DROP = 4.2
HANDLE = (3.2, 0.42)

SAMPLES = int(os.environ.get('CSM_SAMPLES', '64'))

MATS = {}


class Graph:
    """A small helper for building a material's nodes: each call adds a node, wires its inputs (a number or another
    node's output) and returns its main output."""

    def __init__(self, mat):
        self.nt = mat.node_tree
        self.p = self.nt.nodes['Principled BSDF']
        self.co = self.nt.nodes.new('ShaderNodeTexCoord').outputs['Object']

    def feed(self, socket, value):
        if isinstance(value, (int, float)):
            socket.default_value = value
        elif isinstance(value, tuple):
            socket.default_value = value if len(value) == 4 or len(socket.default_value) == 3 else (*value, 1.0)
        else:
            self.nt.links.new(value, socket)

    def math(self, op, a, b=0.0):
        n = self.nt.nodes.new('ShaderNodeMath')
        n.operation = op
        self.feed(n.inputs[0], a)
        self.feed(n.inputs[1], b)
        return n.outputs[0]

    def mix(self, fac, a, b):
        n = self.nt.nodes.new('ShaderNodeMix')
        n.data_type = 'RGBA'
        self.feed(n.inputs[0], fac)
        self.feed(n.inputs[6], a)
        self.feed(n.inputs[7], b)
        return n.outputs[2]

    def lerp(self, fac, a, b):
        n = self.nt.nodes.new('ShaderNodeMix')
        n.data_type = 'FLOAT'
        self.feed(n.inputs[0], fac)
        self.feed(n.inputs[2], a)
        self.feed(n.inputs[3], b)
        return n.outputs[0]

    def mapped(self, scale=(1.0, 1.0, 1.0), rotation=(0.0, 0.0, 0.0)):
        n = self.nt.nodes.new('ShaderNodeMapping')
        self.feed(n.inputs['Vector'], self.co)
        n.inputs['Scale'].default_value = scale
        n.inputs['Rotation'].default_value = rotation
        return n.outputs[0]

    def noise(self, scale, detail=6.0, rough=0.55, vector=None, distortion=0.0):
        n = self.nt.nodes.new('ShaderNodeTexNoise')
        self.feed(n.inputs['Vector'], vector if vector is not None else self.co)
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        n.inputs['Distortion'].default_value = distortion
        return n.outputs['Fac']

    def step(self, value, lo, hi):
        """0 below {@code lo}, 1 above {@code hi}, smooth between."""
        n = self.nt.nodes.new('ShaderNodeMapRange')
        n.interpolation_type = 'SMOOTHSTEP'
        self.feed(n.inputs['Value'], value)
        n.inputs['From Min'].default_value = lo
        n.inputs['From Max'].default_value = hi
        return n.outputs['Result']

    def ao(self, distance, inside=False):
        """How open the surface is round about (1 open, 0 shut in); inside, how far from a convex edge."""
        n = self.nt.nodes.new('ShaderNodeAmbientOcclusion')
        n.inside = inside
        n.samples = 12
        n.inputs['Distance'].default_value = distance
        return n.outputs['AO']

    def bump(self, height, strength, distance=0.1):
        n = self.nt.nodes.new('ShaderNodeBump')
        self.feed(n.inputs['Height'], height)
        n.inputs['Strength'].default_value = strength
        n.inputs['Distance'].default_value = distance
        self.feed(self.p.inputs['Normal'], n.outputs['Normal'])

    def out(self, name, value):
        self.feed(self.p.inputs[name], value)


def new_material(name, color, metal=0.0, rough=0.5, emit=None, sss=0.0):
    """A plain principled material; the worn ones below build on it."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    p = mat.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*color, 1.0)
    p.inputs['Metallic'].default_value = metal
    p.inputs['Roughness'].default_value = rough
    if sss:
        p.inputs['Subsurface Weight'].default_value = sss
        p.inputs['Subsurface Radius'].default_value = (1.0, 0.3, 0.2)
        p.inputs['Subsurface Scale'].default_value = 0.3
    if emit:
        p.inputs['Emission Color'].default_value = (*emit[0], 1.0)
        p.inputs['Emission Strength'].default_value = emit[1]
    MATS[name] = mat
    return mat


def scratches(g, density=0.72):
    """Fine scratches every way: three sets of long thin streaks, each a noise stretched along its own direction."""
    marks = None
    for rot in ((0.3, 0.9, 0.2), (1.2, -0.4, 0.8), (-0.6, 0.2, 1.7)):
        streak = g.step(g.noise(2.2, 3.0, 0.5, g.mapped((0.6, 0.6, 30.0), rot)), density, density + 0.012)
        marks = streak if marks is None else g.math('MAXIMUM', marks, streak)
    return marks


def worn_paint(name, paint, under, paint_rough=0.42, under_metal=1.0, chips=0.3, scratched=0.72,
               grime=(0.025, 0.018, 0.012)):
    """Paint over metal, used hard: chipped back to the metal along its edges, scratched through all over, its colour
    a little uneven, and grime gathered in every corner and seam."""
    mat = new_material(name, paint)
    g = Graph(mat)
    edge = g.math('SUBTRACT', 1.0, g.ao(0.18, inside=True))
    chipped = g.step(g.math('MULTIPLY', edge, g.noise(2.6, 12.0, 0.72)), chips, chips + 0.04)
    wear = g.math('MAXIMUM', chipped, g.math('MULTIPLY', scratches(g, scratched), 0.85))
    shut = g.step(g.math('SUBTRACT', 1.0, g.ao(0.7)), 0.05, 0.7)
    tone = g.mix(g.noise(0.9, 4.0), tuple(c * 0.78 for c in paint), tuple(min(c * 1.12, 1.0) for c in paint))
    tone = g.mix(g.step(g.noise(0.35, 8.0, 0.7), 0.55, 0.75), tone, tuple(c * 0.6 for c in paint))
    color = g.mix(wear, tone, under)
    color = g.mix(g.math('MULTIPLY', shut, 0.8), color, grime)
    g.out('Base Color', color)
    g.out('Metallic', g.math('MULTIPLY', wear, under_metal))
    g.out('Roughness', g.math('ADD', g.math('MULTIPLY', wear, -0.12), g.math('ADD', paint_rough, g.math('MULTIPLY', shut, 0.25))))
    g.bump(g.math('ADD', g.math('MULTIPLY', wear, -1.0), g.math('MULTIPLY', g.noise(14.0, 6.0), 0.3)), 0.35, 0.05)
    return mat


def steel(name, color, rough=0.3, blood_amount=0.0, grime=(0.03, 0.025, 0.02)):
    """Bare steel, brushed and scratched, dirty in its corners, and bloodied: splashes, specks and drips, wet."""
    mat = new_material(name, color, metal=0.95)
    g = Graph(mat)
    brushed = g.noise(3.0, 2.0, 0.5, g.mapped((0.4, 25.0, 0.4)))
    marks = scratches(g, 0.7)
    shut = g.step(g.math('SUBTRACT', 1.0, g.ao(0.6)), 0.05, 0.6)
    base = g.mix(brushed, tuple(c * 0.85 for c in color), color)
    base = g.mix(marks, base, tuple(min(c * 1.25, 1.0) for c in color))
    base = g.mix(g.math('MULTIPLY', shut, 0.85), base, grime)
    rough_out = g.math('ADD', rough, g.math('MULTIPLY', g.math('SUBTRACT', brushed, 0.5), 0.2))
    if blood_amount:
        splash = g.step(g.noise(0.16, 14.0, 0.62), 1.0 - blood_amount, 1.02 - blood_amount)
        specks = g.step(g.noise(3.5, 2.0, 0.5), 0.74, 0.76)
        drips = g.step(g.noise(0.9, 3.0, 0.5, g.mapped((1.0, 1.0, 0.12))), 0.69, 0.7)
        wet = g.math('MAXIMUM', splash, g.math('MAXIMUM', g.math('MULTIPLY', specks, splash), drips))
        dark = g.noise(1.2, 4.0)
        blood = g.mix(dark, (0.3, 0.0, 0.004), (0.6, 0.01, 0.012))
        base = g.mix(wet, base, blood)
        rough_out = g.lerp(wet, rough_out, 0.08)
        g.out('Metallic', g.math('SUBTRACT', 0.95, g.math('MULTIPLY', wet, 0.95)))
        g.out('Coat Weight', g.math('MULTIPLY', wet, 0.6))
    g.out('Base Color', base)
    g.out('Roughness', rough_out)
    g.bump(g.math('MULTIPLY', marks, -1.0), 0.15, 0.03)
    return mat


def wire_material(name):
    """The wires he is bound with: dark rubbery cords, each wrapped round and round in short tight bands, some lighter
    and some near black, oily where the light catches them."""
    mat = new_material(name, (0.09, 0.07, 0.06), rough=0.55)
    g = Graph(mat)
    wave = g.nt.nodes.new('ShaderNodeTexWave')
    wave.wave_type = 'BANDS'
    wave.bands_direction = 'Z'
    wave.inputs['Scale'].default_value = 1.6
    wave.inputs['Distortion'].default_value = 1.5
    wave.inputs['Detail'].default_value = 3.0
    g.feed(wave.inputs['Vector'], g.co)
    bands = g.step(wave.outputs['Fac'], 0.2, 0.45)
    tone = g.mix(g.noise(3.5, 2.0), (0.05, 0.036, 0.032), (0.17, 0.13, 0.11))
    shut = g.step(g.math('SUBTRACT', 1.0, g.ao(0.5)), 0.05, 0.6)
    color = g.mix(bands, (0.012, 0.008, 0.008), tone)
    g.out('Base Color', g.mix(shut, color, (0.005, 0.004, 0.004)))
    g.out('Roughness', g.math('ADD', 0.35, g.math('MULTIPLY', bands, 0.3)))
    g.bump(bands, 0.6, 0.06)
    return mat


def bone(name):
    """The teeth: off-white, yellowed and dirty toward their roots and wherever they crowd together, a little glossy."""
    mat = new_material(name, (0.8, 0.78, 0.7), rough=0.32, sss=0.12)
    g = Graph(mat)
    shut = g.step(g.math('SUBTRACT', 1.0, g.ao(0.8)), 0.05, 0.75)
    color = g.mix(g.noise(5.0, 4.0), (0.74, 0.71, 0.62), (0.86, 0.85, 0.8))
    g.out('Base Color', g.mix(shut, color, (0.32, 0.25, 0.15)))
    g.bump(g.noise(20.0, 4.0), 0.15, 0.02)
    return mat


def worn_rubber(name, color):
    """Black rubber or plastic, scuffed pale where it is handled, dusty in its corners."""
    mat = new_material(name, color, rough=0.6)
    g = Graph(mat)
    edge = g.step(g.math('SUBTRACT', 1.0, g.ao(0.15, inside=True)), 0.25, 0.5)
    scuff = g.math('MULTIPLY', edge, g.noise(3.0, 8.0, 0.7))
    g.out('Base Color', g.mix(scuff, color, tuple(c * 3.5 + 0.03 for c in color)))
    g.out('Roughness', g.math('ADD', 0.5, g.math('MULTIPLY', scuff, 0.3)))
    g.bump(g.noise(18.0, 6.0), 0.2, 0.02)
    return mat


def make_materials():
    # His head: the engine black paint over steel; the visor and jaw orange paint, chipped and scratched; the eye
    # burning yellow in its slots; the rear handle and pole black rubber.
    worn_paint('engine', (0.018, 0.019, 0.022), (0.32, 0.31, 0.3), paint_rough=0.55, chips=0.3, scratched=0.68)
    worn_paint('visor', (0.78, 0.22, 0.025), (0.42, 0.4, 0.38), paint_rough=0.55, chips=0.2, scratched=0.66)
    new_material('slot', (0.006, 0.005, 0.005), rough=0.8)
    new_material('eye', (1.0, 0.75, 0.15), emit=((1.0, 0.6, 0.08), 22.0))
    bone('tooth')
    new_material('gum', (0.5, 0.1, 0.12), rough=0.22, sss=0.35)
    MATS['gum'].node_tree.nodes['Principled BSDF'].inputs['Coat Weight'].default_value = 0.5
    new_material('throat', (0.012, 0.002, 0.003), rough=0.25, sss=0.2)
    # Torn flesh where the arms' saws come through: wet, dark red.
    new_material('wound', (0.32, 0.01, 0.015), rough=0.22, sss=0.3)
    wire_material('cords')
    # The saws: the bar bloodied steel; the chain dark oily steel; the housings black paint.
    steel('bar', (0.84, 0.84, 0.82), rough=0.3, blood_amount=0.44)
    steel('chain', (0.16, 0.16, 0.17), rough=0.32, blood_amount=0.2)
    steel('links', (0.38, 0.37, 0.36), rough=0.38)
    worn_paint('housing', (0.025, 0.025, 0.028), (0.3, 0.3, 0.3), chips=0.34)
    new_material('cord', (0.42, 0.40, 0.36), rough=0.8)
    worn_rubber('handle', (0.022, 0.022, 0.024))
    worn_rubber('hose', (0.014, 0.014, 0.015))
    steel('bolt', (0.45, 0.44, 0.42), rough=0.35)
    # The stand-in: Denji's work clothes (white shirt, black tie and trousers), and skin.
    new_material('shirt', (0.78, 0.78, 0.76), rough=0.85)
    new_material('tie', (0.02, 0.02, 0.025), rough=0.6)
    new_material('trousers', (0.03, 0.03, 0.035), rough=0.8)
    new_material('hand', (0.75, 0.52, 0.40), rough=0.6, sss=0.2)


# --- building blocks ----------------------------------------------------------------------------------------------

def link(obj, part):
    bpy.context.scene.collection.objects.link(obj)
    obj['part'] = part
    return obj


def mesh_obj(name, bm, mat_names, part):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in mat_names:
        me.materials.append(MATS[m])
    obj = bpy.data.objects.new(name, me)
    return link(obj, part)


def box(name, center, half, mat, part, bevel=0.0, segments=2):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=Vector(half) * 2.0, verts=bm.verts)
    bmesh.ops.translate(bm, vec=Vector(center), verts=bm.verts)
    obj = mesh_obj(name, bm, [mat], part)
    if bevel:
        mod = obj.modifiers.new('bevel', 'BEVEL')
        mod.width = bevel
        mod.segments = segments
        mod.limit_method = 'NONE'
    return obj


def stadium(length, height, steps=12):
    """The outline of a chainsaw bar on its side, from its root (u = 0) out to its rounded nose: (u, v) points round it,
    anticlockwise, the root end square, the nose a half circle."""
    r = height / 2.0
    pts = [(0.0, -r), (length - r, -r)]
    for i in range(1, steps):
        a = -math.pi / 2 + math.pi * i / steps
        pts.append((length - r + r * math.cos(a), r * math.sin(a)))
    pts += [(length - r, r), (0.0, r)]
    return pts


def perimeter(length, height, pitch, inset=0.0, phase=0.0):
    """Points evenly spaced round the bar's running edge (not the root end), with the way the chain runs there:
    (u, v, du, dv). The chain goes out along the top, round the nose and back along the bottom; {@code phase} (0 to 1)
    moves every point that far on toward the next."""
    r = height / 2.0 - inset
    straight = length - height / 2.0
    total = 2 * straight + math.pi * r
    out = []
    n = int(total / pitch)
    for i in range(n):
        s = (i + 0.5 + phase) * total / n % total
        if s < straight:
            out.append((s, r, 1.0, 0.0))
        elif s < straight + math.pi * r:
            a = math.pi / 2 - (s - straight) / r
            out.append((straight + r * math.cos(a), r * math.sin(a), math.sin(a), -math.cos(a)))
        else:
            out.append((straight - (s - straight - math.pi * r), -r, -1.0, 0.0))
    return out


def saw(name, size, frame, part, housing=True):
    """A chainsaw bar of {@code size} (length, height, thickness): the plate, the chain of spiked cutters round its
    edge, and (if {@code housing}) the drive housing it comes out of. {@code frame} takes the bar's own space (u along
    the bar from its root, v across it, w through it) to the model's."""
    length, height, thick = size
    bm = bmesh.new()
    # The plate, a stadium extruded through its thickness.
    outline = stadium(length, height)
    front = [bm.verts.new(frame @ Vector((u, v, -thick / 2))) for u, v in outline]
    back = [bm.verts.new(frame @ Vector((u, v, thick / 2))) for u, v in outline]
    bm.faces.new(front[::-1])
    bm.faces.new(back)
    for i in range(len(outline)):
        j = (i + 1) % len(outline)
        bm.faces.new((front[i], front[j], back[j], back[i]))
    plate = mesh_obj(name + '_bar', bm, ['bar'], part + '_bar')
    grows(plate, frame)

    # The chain: a cutter at each pitch round the edge, each a sawtooth raking the way the chain runs, on a link that
    # rides in the bar's edge. For the game, CHAIN_FRAMES of them, each moved a step further round, shown in turn to
    # run the chain.
    for frame_k in range(CHAIN_FRAMES):
        chain(name, size, frame, part, frame_k)
    if not housing:
        return plate
    housing_of(name, size, frame, part)
    return plate


def grows(obj, frame):
    """Marks {@code obj} as part of a saw that grows out of its root along its length in the transformation: the bar's
    root and the way it runs, in the object's own space (frame: the bar's space to the object's)."""
    obj['grow_root'] = list(frame @ Vector((0.0, 0.0, 0.0)))
    obj['grow_axis'] = list(frame.to_3x3() @ Vector((1.0, 0.0, 0.0)))


def chain(name, size, frame, part, frame_k):
    """One frame of a bar's chain (see saw)."""
    length, height, thick = size
    bm = bmesh.new()
    for k, (u, v, du, dv) in enumerate(perimeter(length, height, CUTTER_PITCH, phase=frame_k / CHAIN_FRAMES)):
        along = Vector((du, dv, 0.0))
        out = Vector((-dv, du, 0.0))  # away from the bar
        base = Vector((u, v, 0.0))
        # The link: a flat block on the edge.
        link_half = (CUTTER_PITCH * 0.5, 0.42, thick * 0.62)
        corners = []
        for a in (-1, 1):
            for b in (-1, 1):
                for c in (-1, 1):
                    corners.append(base + along * a * link_half[0] + out * (b * link_half[1] + 0.12) + Vector((0, 0, c * link_half[2])))
        lv = [bm.verts.new(frame @ p) for p in corners]
        for quad in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
            bm.faces.new([lv[q] for q in quad])
        # The cutter: a sawtooth on the link, raking forward the way the chain runs, as thick as the link.
        w = thick * 0.5
        rise = 0.52
        tv = [bm.verts.new(frame @ p) for p in (
            base - along * 0.45 + out * rise + Vector((0, 0, -w)),
            base - along * 0.45 + out * rise + Vector((0, 0, w)),
            base + along * 0.3 + out * rise + Vector((0, 0, -w)),
            base + along * 0.3 + out * rise + Vector((0, 0, w)),
            base + along * 0.5 + out * (rise + SPIKE * height) + Vector((0, 0, -w * 0.4)),
            base + along * 0.5 + out * (rise + SPIKE * height) + Vector((0, 0, w * 0.4)),
        )]
        for face in ((0, 2, 4), (1, 5, 3), (0, 4, 5, 1), (2, 3, 5, 4), (0, 1, 3, 2)):
            bm.faces.new([tv[t] for t in face])
    grows(mesh_obj(name + '_chain%d' % frame_k, bm, ['chain'], part + '_chain%d' % frame_k), frame)


def housing_of(name, size, frame, part):
    """The dark block a bar comes out of, wider than the bar."""
    length, height, thick = size
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=Vector((2.2, height + 0.9, thick + 1.5)), verts=bm.verts)
    bmesh.ops.translate(bm, vec=Vector((-0.4, 0.0, 0.0)), verts=bm.verts)
    bmesh.ops.transform(bm, matrix=frame, verts=bm.verts)
    housing = mesh_obj(name + '_housing', bm, ['housing'], part)
    mod = housing.modifiers.new('bevel', 'BEVEL')
    mod.width = 0.35
    mod.segments = 2


def solid(name, profile, half, mat, part, bevel=0.0, ridge=None):
    """A solid seen from the side as {@code profile} ((y, z) points round it), {@code half(y, z)} wide either side of
    the middle there. If {@code ridge} is (n, height), the middle of its first n points' edge stands up that much,
    outward from the profile, in a ridge from end to end of them. Its edges are rounded by {@code bevel}."""
    n = len(profile)
    bm = bmesh.new()
    left = [bm.verts.new((-half(y, z), y, z)) for y, z in profile]
    right = [bm.verts.new((half(y, z), y, z)) for y, z in profile]
    mid = []
    for i, (y, z) in enumerate(profile):
        lift = Vector()
        if ridge and i < ridge[0]:
            a = Vector(profile[max(i - 1, 0)])
            b = Vector(profile[min(i + 1, n - 1)])
            t = (b - a).normalized()
            lift = Vector((0.0, t.y, -t.x)) * ridge[1]  # outward: the profile goes round clockwise seen from +x
            if i in (0, ridge[0] - 1):
                lift *= 0.4
        mid.append(bm.verts.new((0.0, y + lift.y, z + lift.z)))
    bm.faces.new(left)
    bm.faces.new(right[::-1])
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((left[i], mid[i], mid[j], left[j]))
        bm.faces.new((mid[i], right[i], right[j], mid[j]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    obj = mesh_obj(name, bm, [mat], part)
    if bevel:
        mod = obj.modifiers.new('bevel', 'BEVEL')
        mod.width = bevel
        mod.segments = 3 if bevel > 0.3 else 2
        mod.limit_method = 'ANGLE'
    return obj


def engine_half(y, z):
    """The engine's half width: full down its sides, drawing in over its top."""
    return ENGINE[0] - max(z - 32.9, 0.0) * 0.9


def visor_half(y, z):
    back, front = max(y for y, z in VISOR), VISOR[VISOR_POINT][0]
    f = min(max((back - y) / (back - front), 0.0), 1.0)
    return VISOR_HALF[0] + (VISOR_HALF[1] - VISOR_HALF[0]) * f ** 1.4


def jaw_half(y, z):
    top, tip = JAW[0][1], JAW[2][1]
    f = (top - z) / (top - tip)
    return JAW_HALF[0] + (JAW_HALF[1] - JAW_HALF[0]) * f


def cylinder(bm, a, b, r, segments=16):
    """A round bar from {@code a} to {@code b}, capped."""
    a, b = Vector(a), Vector(b)
    axis = b - a
    verts = bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=r, radius2=r, depth=axis.length)['verts']
    rot = Vector((0, 0, 1)).rotation_difference(axis.normalized()).to_matrix()
    bmesh.ops.rotate(bm, cent=Vector(), matrix=rot, verts=verts)
    bmesh.ops.translate(bm, vec=(a + b) / 2, verts=verts)


def cylinder_x(bm, x, y, z, r, depth, segments=32):
    """A disc on a side of the head, its axis across (x), {@code depth} thick, its inner face at {@code x}."""
    side = 1.0 if x > 0 else -1.0
    cylinder(bm, (x, y, z), (x + side * depth, y, z), r, segments)


def arc_strip(bm, x, center, r, width, a0, a1, steps=24):
    """A curved strip lying on the plane at {@code x}, round {@code center} (y, z) at radius {@code r}, from angle a0 to
    a1 (radians from straight up, turning toward the front)."""
    side = 1.0 if x > 0 else -1.0
    c = Vector((x, center[0], center[1]))
    inner, outer = [], []
    for i in range(steps + 1):
        a = a0 + (a1 - a0) * i / steps
        d = Vector((0.0, -math.sin(a), math.cos(a)))
        inner.append(bm.verts.new(c + d * (r - width / 2)))
        outer.append(bm.verts.new(c + d * (r + width / 2)))
    for i in range(steps):
        quad = [inner[i], inner[i + 1], outer[i + 1], outer[i]]
        bm.faces.new(quad if side < 0 else quad[::-1])


def tube(name, points, r, mat, part, cyclic=False):
    """A tube of radius {@code r} along a smooth curve through {@code points} (x, y, z)."""
    curve = bpy.data.curves.new(name, 'CURVE')
    curve.dimensions = '3D'
    spline = curve.splines.new('BEZIER')
    spline.bezier_points.add(len(points) - 1)
    for bp, p in zip(spline.bezier_points, points):
        bp.co = p
        bp.handle_left_type = bp.handle_right_type = 'AUTO'
    spline.use_cyclic_u = cyclic
    curve.bevel_depth = r
    curve.bevel_resolution = 4
    curve.use_fill_caps = True
    curve.materials.append(MATS[mat])
    return link(bpy.data.objects.new(name, curve), part)


def roof_z(y):
    """The underside of the visor at {@code y}, which the upper teeth hang from."""
    (y0, z0), (y1, z1) = VISOR[VISOR_MOUTH], VISOR[VISOR_POINT]
    return z0 + (z1 - z0) * (y - y0) / (y1 - y0)


def teeth_line():
    """Where the teeth stand, evenly spaced: forward along the left side, round the front corner, across the front and
    back along the right side."""
    front_y, front_x = TEETH_FRONT
    side_x, back_y = TEETH_SIDE
    corner = 0.9
    path = [(-side_x, back_y), (-side_x, front_y + corner), (-front_x, front_y), (front_x, front_y),
            (side_x, front_y + corner), (side_x, back_y)]
    lengths = [math.dist(path[i], path[i + 1]) for i in range(len(path) - 1)]
    total = sum(lengths)
    n = int(total / TOOTH_STEP)
    points = []
    for i in range(n + 1):
        s = i * total / n
        k = 0
        while k < len(lengths) - 1 and s > lengths[k]:
            s -= lengths[k]
            k += 1
        f = s / lengths[k]
        points.append((path[k][0] + (path[k + 1][0] - path[k][0]) * f, path[k][1] + (path[k + 1][1] - path[k][1]) * f))
    return points


def plate_x(name, outline, x, thick, mat, part, bevel=0.06):
    """A flat plate on a side of the head, {@code outline} ((y, z) points round it), its inner face at {@code x} and
    {@code thick} thick outward."""
    side = 1.0 if x > 0 else -1.0
    bm = bmesh.new()
    inner = [bm.verts.new((x, y, z)) for y, z in outline]
    outer = [bm.verts.new((x + side * thick, y, z)) for y, z in outline]
    bm.faces.new(inner)
    bm.faces.new(outer[::-1])
    n = len(outline)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((inner[i], outer[i], outer[j], inner[j]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    obj = mesh_obj(name, bm, [mat], part)
    if bevel:
        mod = obj.modifiers.new('bevel', 'BEVEL')
        mod.width = bevel
        mod.segments = 1
        mod.limit_method = 'ANGLE'
    return obj


def rounded_outline(y0, y1, z0, z1, r, steps=8):
    """A rectangle's outline in (y, z) with its corners rounded by {@code r}, going round from the bottom front."""
    pts = []
    for cy, cz, a0 in ((y1 - r, z0 + r, -90), (y1 - r, z1 - r, 0), (y0 + r, z1 - r, 90), (y0 + r, z0 + r, 180)):
        for i in range(steps + 1):
            a = math.radians(a0 + 90 * i / steps)
            pts.append((cy + r * math.cos(a), cz + r * math.sin(a)))
    return pts


def smooth_outline(points, steps=6):
    """A closed outline through {@code points}, its corners rounded off (Chaikin's cutting, three times)."""
    pts = list(points)
    for _ in range(3):
        out = []
        for i in range(len(pts)):
            a, b = Vector(pts[i]), Vector(pts[(i + 1) % len(pts)])
            out += [tuple(a * 0.75 + b * 0.25), tuple(a * 0.25 + b * 0.75)]
        pts = out
    return pts


def shrunk(outline, by, top):
    """{@code outline} drawn in toward its middle by {@code by}, and cut off flat at {@code top}."""
    cy = sum(y for y, z in outline) / len(outline)
    cz = sum(z for y, z in outline) / len(outline)
    out = []
    for y, z in outline:
        d = Vector((cy - y, cz - z))
        p = Vector((y, z)) + d.normalized() * by
        out.append((p.x, min(p.y, top)))
    return out


def notched(outline, keep, depth, every=2):
    """{@code outline} with every other short run of its points, where {@code keep(y, z)}, pulled in toward its middle by
    {@code depth}: the edge cut in notches like a cog's."""
    cy = sum(y for y, z in outline) / len(outline)
    cz = sum(z for y, z in outline) / len(outline)
    out = []
    for i, (y, z) in enumerate(outline):
        if keep(y, z) and (i // every) % 2:
            d = Vector((cy - y, cz - z)).normalized() * depth
            out.append((y + d.x, z + d.y))
        else:
            out.append((y, z))
    return out


def bolt(bm, x, y, z, r=0.2, axis='x'):
    """A hex bolt head on a face, standing out of it along {@code axis} ('x' on a side, 'y' on the back)."""
    a = Vector((x, y, z))
    side = 1.0 if (x if axis == 'x' else y) > 0 else -1.0
    d = Vector((side, 0, 0)) if axis == 'x' else Vector((0, side, 0))
    cylinder(bm, a - d * 0.05, a + d * 0.16, r, segments=6)


def build_engine_detail(half, back, front, bottom, top):
    """Everything on the engine besides its block: the side covers, cut in notches along their top and back like a
    cog, with a stepped plate, a ledge and a nub on each; the armour plates over the top; the plate on the back with its
    bolts, and the finned cylinder under it; bolts all over; the ribs down the back of the neck; and a hose down each
    side into the neck."""
    bm_bolts = bmesh.new()
    for side in (-1, 1):
        x = side * half
        cover = shrunk(smooth_outline(ENGINE_PROFILE), 0.5, 32.8)
        cover = notched(cover, lambda y, z: y > 1.0 and z > 29.6, 0.38, every=2)
        plate_x('cover', cover, x, COVER_T, 'engine', 'head')
        # A second, smaller plate over it behind the socket, its back edge stepped.
        step = [(3.0, 27.3), (4.6, 27.3), (4.6, 28.4), (4.95, 28.4), (4.95, 31.2), (4.4, 31.2), (4.4, 32.2), (3.0, 32.2)]
        plate_x('cover_step', step, x + side * COVER_T, 0.18, 'engine', 'head')
        # The ledge along the bottom of the cover, under the socket.
        ledge = [(-0.8, 26.95), (4.5, 26.95), (4.9, 27.35), (4.5, 27.75), (-0.8, 27.75)]
        plate_x('ledge', ledge, x + side * COVER_T, 0.32, 'engine', 'head')
        # The nub where the engine meets the visor.
        box('nub', (x + side * (COVER_T + 0.15), 1.6, 31.6), (0.18, 0.28, 0.3), 'engine', 'head', bevel=0.06, segments=1)
        for by, bz in ((front + 1.4, bottom + 1.1), (front + 1.4, top - 0.9), (back - 1.0, bottom + 1.1), (4.7, 29.0),
                       (4.15, 31.8), (3.3, 27.6)):
            bolt(bm_bolts, x + side * (COVER_T + (0.18 if by > 2.9 else 0.0)), by, bz, 0.17)

    # The armour plates over the top, the front one rising to a point, a step down to the one behind it.
    for k, plate in enumerate(TOP_PLATES):
        solid('top_plate', plate, lambda y, z, k=k: TOP_PLATE_HALF - 0.4 * k, 'engine', 'head', bevel=0.08)

    # The back: a plate with a bolt at each corner, behind the handle, and the cylinder's fins under it.
    plate = [(-2.6, 28.7), (2.6, 28.7), (2.9, 29.1), (2.9, 32.3), (2.5, 32.7), (-2.5, 32.7), (-2.9, 32.3), (-2.9, 29.1)]
    bm = bmesh.new()
    inner = [bm.verts.new((px, back, pz)) for px, pz in plate]
    outer = [bm.verts.new((px, back + 0.22, pz)) for px, pz in plate]
    bm.faces.new(inner[::-1])
    bm.faces.new(outer)
    for i in range(len(plate)):
        j = (i + 1) % len(plate)
        bm.faces.new((inner[i], inner[j], outer[j], outer[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh_obj('back_plate', bm, ['engine'], 'head')
    for bx, bz in ((-2.4, 29.2), (2.4, 29.2), (-2.4, 32.2), (2.4, 32.2)):
        bolt(bm_bolts, bx, back + 0.22, bz, 0.17, axis='y')
    box('cylinder', (0.0, back - 0.1, 27.3), (2.0, 0.5, 1.1), 'engine', 'head', bevel=0.15)
    for k in range(FINS):
        box('fin', (0.0, back + 0.45, 26.35 + k * 0.38), (2.5, 0.55, 0.08), 'engine', 'head', bevel=0.03, segments=1)

    # Bolts down the join of the engine and the visor.
    for side in (-1, 1):
        for bz in (29.0, 31.6):
            bolt(bm_bolts, side * (half + 0.05), 0.0, bz, 0.15)
    mesh_obj('bolts', bm_bolts, ['bolt'], 'head')

    # The ribs down the back of the neck, like a spine's, each a little narrower than the one above.
    count, z0, gap = RIBS
    for k in range(count):
        box('rib', (0.0, 3.25 - k * 0.12, z0 - k * gap), (2.75 - k * 0.1, 1.15, 0.22), 'engine', 'head', bevel=0.12)

    # A hose down each side, out from under the front of the engine, round and down into the neck.
    for side in (-1, 1):
        tube('hose', [(side * 2.9, -0.8, 27.6), (side * 3.9, 0.6, 26.6), (side * 3.5, 2.4, 25.0), (side * 2.7, 2.9, 23.2)],
             0.27, 'hose', 'head')


def wire_bundle(name, paths, radii, mat, part):
    """Many wires in one object: a tube of radius radii[i] along a smooth curve through each of {@code paths}."""
    curve = bpy.data.curves.new(name, 'CURVE')
    curve.dimensions = '3D'
    curve.bevel_depth = 1.0
    curve.bevel_resolution = 2
    curve.use_fill_caps = True
    for path, r in zip(paths, radii):
        spline = curve.splines.new('BEZIER')
        spline.bezier_points.add(len(path) - 1)
        for bp, p in zip(spline.bezier_points, path):
            bp.co = p
            bp.radius = r
            bp.handle_left_type = bp.handle_right_type = 'AUTO'
    curve.materials.append(MATS[mat])
    return link(bpy.data.objects.new(name, curve), part)


def build_wires():
    """The tendons: wires bound tight in a thick neck, out from under the engine and down into the collar, each a
    little twisted round it; and more in the cheeks, behind the side teeth, from under the visor down into the jaw."""
    rnd = random.Random(23)
    nh, nb, nf, nbot, ntop = NECK
    cy, ry, rx = (nb + nf) / 2, (nb - nf) / 2, nh
    paths, radii = [], []
    for i in range(NECK_WIRES):
        # Most round the outside, where they show; a few through the middle so it is not hollow.
        a = 2 * math.pi * (i + rnd.uniform(-0.3, 0.3)) / NECK_WIRES
        rr = 1.0 if i % 5 else rnd.uniform(0.35, 0.8)
        twist = rnd.uniform(0.25, 0.45)
        path = []
        for k, (z, bulge) in enumerate(((ntop + 0.3, 0.98), ((ntop * 2 + nbot) / 3, 1.06), ((ntop + nbot * 2) / 3, 1.04),
                                       (nbot, 0.9))):
            t = a + twist * k / 3
            path.append((math.cos(t) * rx * rr * bulge, cy + math.sin(t) * ry * rr * bulge, z + rnd.uniform(-0.1, 0.1)))
        paths.append(path)
        radii.append(rnd.uniform(0.26, 0.36))
    for side in (-1, 1):
        n = CHEEK_WIRES
        # Only at the back corners of the mouth, where the jaw hinges: the mouth itself is left open.
        for i in range(n):
            y = TEETH_SIDE[1] + 1.4 - 1.2 * i / (n - 1)
            x = side * (TEETH_SIDE[0] + 0.05 - 0.9 * (i / (n - 1)) ** 2 + rnd.uniform(-0.08, 0.05))
            top_z = roof_z(y) + 0.4
            jaw_z = JAW[0][1] - 0.3
            wob = rnd.uniform(-0.12, 0.12)
            paths.append([(x, y, top_z), (x + side * 0.12 + wob, y + rnd.uniform(-0.15, 0.15), (top_z + jaw_z) / 2),
                          (x, y + rnd.uniform(-0.1, 0.1), jaw_z)])
            radii.append(rnd.uniform(0.17, 0.23))
    wire_bundle('wires', paths, radii, 'cords', 'head')
    # The dark inside the bundle, so it does not show through between the wires.
    box('neck_core', (0.0, cy, (nbot + ntop) / 2), (rx * 0.8, ry * 0.8, (ntop - nbot) / 2), 'throat', 'head')


def chain_strip(side):
    """A short length of roller chain lying down the side of the head on {@code side}, along CHAIN_PATH: its links in
    turn wide (the outer plates, with a pin through each end) and narrow (the inner), each turned along the way it runs.
    It lies on the engine at the top and comes out onto the cords toward the jaw."""
    path = smooth_outline(CHAIN_PATH)[:-9]  # rounded, and left open at its end
    length = [0.0]
    for i in range(1, len(path)):
        length.append(length[-1] + math.dist(path[i - 1], path[i]))
    bm = bmesh.new()
    pins = bmesh.new()
    s = 0.0
    k = 0
    while s < length[-1]:
        i = max(j for j in range(len(path)) if length[j] <= s)
        i = min(i, len(path) - 2)
        f = (s - length[i]) / max(length[i + 1] - length[i], 1e-6)
        y = path[i][0] + (path[i + 1][0] - path[i][0]) * f
        z = path[i][1] + (path[i + 1][1] - path[i][1]) * f
        t = Vector((0.0, path[i + 1][0] - path[i][0], path[i + 1][1] - path[i][1])).normalized()
        # Out from the head: on the engine at the top, coming in onto the cords below.
        x = side * (ENGINE[0] + COVER_T + 0.08 - max(29.0 - z, 0.0) * 0.17)
        outer = k % 2 == 0
        w = 0.22 if outer else 0.15
        half_len = CHAIN_PITCH * 0.62
        n = Vector((side, 0.0, 0.0))
        across = t.cross(n).normalized()
        c = Vector((x, y, z)) + n * (0.1 if outer else 0.04)
        corners = [c + t * a * half_len + across * b * w + n * d * 0.05 for a in (-1, 1) for b in (-1, 1) for d in (-1, 1)]
        vs = [bm.verts.new(p) for p in corners]
        for quad in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
            bm.faces.new([vs[q] for q in quad])
        if outer:
            for a in (-1, 1):
                p = c + t * a * half_len * 0.8
                cylinder(pins, p - n * 0.1, p + n * 0.1, 0.075, segments=8)
        s += CHAIN_PITCH
        k += 1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh_obj('chain_strip', bm, ['links'], 'head')
    mesh_obj('chain_pins', pins, ['bolt'], 'head')


def build_head():
    """The engine with a face: the iron block with its side plates and sockets, hinges, rear handle, pole and cable;
    the orange visor with its vents and eye; the teeth; the jaw; the neck of cords; and the saw out of the front."""
    half, back, front, bottom, top = ENGINE
    head = solid('engine', ENGINE_PROFILE, engine_half, 'engine', 'head', bevel=ENGINE_ROUND)
    build_engine_detail(half, back, front, bottom, top)

    for side in (-1, 1):
        x = side * (half + COVER_T)
        sy, sz = SOCKET
        # The side plate: round, its top edge cut in notches like a cog, standing a little proud of the engine.
        bm = bmesh.new()
        ring = []
        steps = 64
        for i in range(steps):
            a = 2 * math.pi * i / steps
            r = SIDE_PLATE_R
            # Notches round the top half only.
            if math.sin(a) > 0.1 and int(i * SIDE_PLATE_NOTCHES * 2 / (steps / 2)) % 2:
                r += 0.32
            ring.append((sy + math.cos(a) * r, sz + math.sin(a) * r))
        inner = [bm.verts.new((x, y, z)) for y, z in ring]
        outer = [bm.verts.new((x + side * 0.22, y, z)) for y, z in ring]
        bm.faces.new(outer if side > 0 else outer[::-1])
        for i in range(steps):
            j = (i + 1) % steps
            quad = [inner[i], inner[j], outer[j], outer[i]]
            bm.faces.new(quad if side > 0 else quad[::-1])
        cylinder_x(bm, x + side * 0.22, sy, sz, SOCKET_R + 0.28, 0.18, 32)
        cylinder_x(bm, x + side * 0.22, SCREW[0], SCREW[1], SCREW[2], 0.16, 12)
        mesh_obj('side_plate', bm, ['engine'], 'head')
        # The socket's dark hole, and the screw's slot.
        bm = bmesh.new()
        cylinder_x(bm, x + side * 0.4, sy, sz, SOCKET_R, 0.02, 32)
        mesh_obj('socket', bm, ['slot'], 'head')
        bm = bmesh.new()
        arc_strip(bm, x + side * 0.42, (sy, sz), SOCKET_R + 0.06, 0.12, 0.0, 2 * math.pi, steps=48)
        mesh_obj('socket_lip', bm, ['bolt'], 'head')

    # The rear handle, and the pole on top.
    tube('rear_handle', [(0.0, y, z) for y, z in REAR_HANDLE], HANDLE_R, 'handle', 'head')
    py, pz, tilt, length, r = POLE
    t = math.radians(tilt)
    end = Vector((0.0, py + math.sin(t) * length, pz + math.cos(t) * length))
    bm = bmesh.new()
    cylinder(bm, (0.0, py, pz - 1.0), end, r)
    tip = bmesh.ops.create_uvsphere(bm, u_segments=16, v_segments=8, radius=r)['verts']
    bmesh.ops.translate(bm, vec=end, verts=tip)
    mesh_obj('pole', bm, ['handle'], 'head')
    # The collar the pole goes into.
    bm = bmesh.new()
    cylinder(bm, (0.0, py, pz - 0.4), (0.0, py + 0.1, pz + 0.4), r + 0.28, segments=16)
    mesh_obj('pole_collar', bm, ['engine'], 'head')
    # The chain down each side, behind the visor.
    for side in (-1, 1):
        chain_strip(side)

    # The visor, a prow-shaped cover with a ridge along its top, and its vents: the outer two dark, the inner two his
    # eye, burning.
    solid('visor', VISOR, visor_half, 'visor', 'head', bevel=0.12, ridge=(VISOR_OUTER, VISOR_RIDGE))
    for side in (-1, 1):
        for radii, mat in ((VENT_R[2:], 'slot'), (VENT_R[:2], 'eye')):
            bm = bmesh.new()
            for r in radii:
                arc_strip(bm, 0.0, VENT_C, r, VENT_W, *VENT_SWEEP)
            # Laid on the visor's side, which leans in toward the front.
            for v in bm.verts:
                v.co.x = side * (visor_half(v.co.y, v.co.z) + 0.02)
            mesh_obj('vents' if mat == 'slot' else 'eye', bm, [mat], 'head')

    # The jaw, a V seen from the front, its point down; the gum along its top; the dark of the mouth behind the teeth.
    solid('jaw', JAW, jaw_half, 'visor', 'head', bevel=0.12)
    jaw_top = JAW[0][1]
    box('gum', (0.0, (TEETH_FRONT[0] + 0.6) / 2, jaw_top + 0.06), (TEETH_SIDE[0] - 0.1, (0.6 - TEETH_FRONT[0]) / 2, 0.08),
        'gum', 'head')
    # The mouth is open behind the teeth: its inside sits well back from them, dark and wet, so it shows as a hole.
    box('throat', (0.0, -1.4, (jaw_top + 28.0) / 2), (TEETH_SIDE[0] - 0.75, 2.0, (28.0 - jaw_top) / 2), 'throat', 'head')

    build_wires()

    # The teeth: the upper hanging from under the visor nearly to the jaw, the lower standing up between them; long,
    # straight and thin, each a little uneven.
    rnd = random.Random(11)
    points = teeth_line()
    bm = bmesh.new()
    for k, (x, y) in enumerate(points):
        for upper, (length, half_t) in ((True, UPPER_TOOTH), (False, LOWER_TOOTH)):
            if upper:
                cx, cy = x, y
                z0 = roof_z(y) + 0.1
            elif k + 1 < len(points):
                cx, cy = (x + points[k + 1][0]) / 2, (y + points[k + 1][1]) / 2
                z0 = jaw_top
            else:
                continue
            sign = -1.0 if upper else 1.0
            ln = min(length * rnd.uniform(0.9, 1.08), abs(z0 - jaw_top) - 0.15) if upper else length * rnd.uniform(0.85, 1.1)
            lean = Vector((rnd.uniform(-0.06, 0.06), rnd.uniform(-0.1, 0.02), 0.0))
            root = [bm.verts.new((cx + dx, cy + dy, z0)) for dx, dy in
                    ((-half_t, -half_t * 0.7), (half_t, -half_t * 0.7), (half_t, half_t * 0.7), (-half_t, half_t * 0.7))]
            tip = bm.verts.new(Vector((cx, cy, z0 + sign * ln)) + lean)
            for i in range(4):
                bm.faces.new([root[i], root[(i + 1) % 4], tip])
            bm.faces.new(root)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh_obj('teeth', bm, ['tooth'], 'head')

    # The saw, out of the top of the visor's front, flat faces to the sides.
    tilt = math.radians(HEAD_BAR_TILT)
    along = Vector((0.0, -math.cos(tilt), math.sin(tilt)))
    up = Vector((0.0, math.sin(tilt), math.cos(tilt)))
    frame = Matrix.Translation(HEAD_BAR_ROOT) @ Matrix((
        (along.x, up.x, 1.0, 0.0), (along.y, up.y, 0.0, 0.0), (along.z, up.z, 0.0, 0.0), (0, 0, 0, 1)))
    saw('head_saw', HEAD_BAR, frame, 'head', housing=False)
    return head


def shoulder(side):
    """Where an arm hangs from (x outward on {@code side}, +1 for the left arm and -1 for the right)."""
    return Vector((side * 6.0, 0.0, 22.0))


def build_arm(side, pose):
    """An arm of the stand-in, and the saw out of the middle of its forearm, running down past the fist; all hung from
    an empty at the shoulder turned by {@code pose} (x, y, z degrees), so the arm can be posed."""
    pivot = bpy.data.objects.new('arm_' + ('left' if side > 0 else 'right'), None)
    link(pivot, 'arm')
    pivot.location = shoulder(side)
    pivot.rotation_euler = [math.radians(a) for a in pose]
    parts = []
    # The sleeve, and the hand below it, in the arm's own space (shoulder at the origin, hanging down -z).
    parts.append(box('sleeve', (0.0, 0.0, -4.5), (2.0, 2.0, 4.5), 'shirt', 'stand_in'))
    parts.append(box('hand', (0.0, 0.0, -10.75), (2.0, 2.0, 1.75), 'hand', 'stand_in'))
    # The saw: out of the middle of the arm from just below the shoulder, its width running front to back (its teeth
    # along the top and bottom of the arm when it is held out, its flat faces to the sides), its chain breaking out
    # through the front and back of the sleeve, running on down the arm, through the fist and far past it.
    root = Vector((0.0, 0.0, -ARM_BAR_ROOT))
    frame = Matrix.Translation(root) @ Matrix((
        (0.0, 0.0, 1.0, 0.0), (0.0, 1.0, 0.0, 0.0), (-1.0, 0.0, 0.0, 0.0), (0, 0, 0, 1)))
    before = set(bpy.context.scene.objects)
    part = 'arm_left' if side > 0 else 'arm_right'
    saw('arm_saw', ARM_BAR, frame, part, housing=False)
    # Where it comes through: the fist split in two along the middle finger, open and bloody down its front and back
    # and across its knuckles, and the sleeve torn open above it all the way up to where the blade bursts out.
    tear_top = -ARM_BAR_ROOT
    for sy in (-1.0, 1.0):
        box('gash', (0.0, sy * 2.03, -10.75), (0.45, 0.03, 1.75), 'wound', part)
        box('tear', (0.0, sy * 2.03, (tear_top - 9.0) / 2), (0.38, 0.03, (tear_top + 9.0) / 2), 'throat', part)
        box('tear_edge', (0.0, sy * 2.04, -9.0), (0.55, 0.03, 0.12), 'wound', part)
    box('gash', (0.0, 0.0, -12.53), (0.45, 2.0, 0.03), 'wound', part)
    parts += [o for o in bpy.context.scene.objects if o not in before]
    for o in parts:
        o.parent = pivot
    return pivot


def build_cord():
    """The starter cord: out of the middle of the chest, hanging, with its T handle crosswise at the bottom."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=0.16, radius2=0.16, depth=CORD_DROP)
    bmesh.ops.translate(bm, vec=CORD_ROOT + Vector((0.0, -0.15, -CORD_DROP / 2)), verts=bm.verts)
    mesh_obj('cord', bm, ['cord'], 'cord')
    # The grommet it comes out of.
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=0.55, radius2=0.45, depth=0.5)
    bmesh.ops.rotate(bm, cent=Vector(), matrix=Matrix.Rotation(math.pi / 2, 3, 'X'), verts=bm.verts)
    bmesh.ops.translate(bm, vec=CORD_ROOT, verts=bm.verts)
    mesh_obj('grommet', bm, ['housing'], 'cord')
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=HANDLE[1], radius2=HANDLE[1], depth=HANDLE[0])
    bmesh.ops.rotate(bm, cent=Vector(), matrix=Matrix.Rotation(math.pi / 2, 3, 'Y'), verts=bm.verts)
    bmesh.ops.translate(bm, vec=CORD_ROOT + Vector((0.0, -0.15, -CORD_DROP)), verts=bm.verts)
    handle = mesh_obj('handle', bm, ['handle'], 'cord')
    mod = handle.modifiers.new('bevel', 'BEVEL')
    mod.width = 0.12
    mod.segments = 2


def build_stand_in():
    """The player model as the game draws it, in Denji's work clothes: the body, the tie down its front, the legs."""
    box('body', (0.0, 0.0, 18.0), (4.0, 2.0, 6.0), 'shirt', 'stand_in')
    box('tie', (0.0, -2.03, 19.2), (0.55, 0.03, 4.6), 'tie', 'stand_in')
    box('collar_tie', (0.0, -2.05, 23.5), (0.8, 0.05, 0.4), 'tie', 'stand_in')
    for side in (-1, 1):
        box('leg', (side * 2.0, 0.0, 6.0), (2.0, 2.0, 6.0), 'trousers', 'stand_in')


def build(pose='stance'):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    MATS.clear()
    make_materials()
    build_stand_in()
    build_head()
    build_cord()
    if pose == 'stance':
        # Arms up and out in front of him, saws forward, as he comes at you.
        build_arm(1, (-62.0, 0.0, -14.0))
        build_arm(-1, (-48.0, 0.0, 18.0))
    else:
        build_arm(1, (0.0, 0.0, 0.0))
        build_arm(-1, (0.0, 0.0, 0.0))


# --- the game's copy -----------------------------------------------------------------------------------------------
#
# The parts are baked into one texture (their colours as the materials paint them, darkened a little where they are
# shut in) and written out as a mesh the client draws into an entity layer, lit by the game like the rest of the player.

GAME_ASSETS = os.path.join('chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman')
GAME_MESH = os.path.join(GAME_ASSETS, 'meshes', 'form.csm')
GAME_TEXTURE = os.path.join(GAME_ASSETS, 'textures', 'entity', 'form.png')
BAKE_SIZE = int(os.environ.get('CSM_BAKE_SIZE', '2048'))
BAKE_SAMPLES = int(os.environ.get('CSM_BAKE_SAMPLES', '24'))
# How dark the most shut-in corners get in the texture (the game's own lighting does the rest).
SHUT_IN = 0.72
# And then everything lifted and its colour strengthened a little: the game shades the form as well.
LIFT = (0.85, 1.08)
SATURATE = 1.2
# What each part is drawn with in the game: which bone it follows, and where that bone's pivot is in the build (the
# neck, for the head and the body; the shoulder for an arm).
GAME_PARTS = {'head': ('head', Vector((0.0, 0.0, 24.0))), 'cord': ('body', Vector((0.0, 0.0, 24.0))),
              'arm_left': ('left_arm', Vector((5.0, 0.0, 22.0))), 'arm_right': ('right_arm', Vector((-5.0, 0.0, 22.0))),
              'rope': ('body', Vector((0.0, 0.0, 24.0)))}


def game_part(part):
    """The bone-part an object's part name belongs to for the game: a bar (NAME_bar) and its chain's frames
    (NAME_chainK) go with NAME."""
    return part.split('_chain')[0].split('_bar')[0] if part else part
# Materials drawn at full brightness whatever the light: the eye.
GLOW = {'eye'}
# How finely the curved tubes (the wires, the handle, the hoses) are cut up in the game's copy.
GAME_CURVE_RESOLUTION = 4


def to_game(v, pivot):
    """The build's pixels (x to the player's left, y backward, z up, from the feet) to the bone's model space as the
    player model has it (pixels, x to the left, y down, the face toward -z), measured from the bone's pivot."""
    d = v - pivot
    return (d.x, -d.z, d.y)


def game_normal(n):
    return (n.x, -n.z, n.y)


def game_objects():
    """Every object of the game's parts, with the part it belongs to."""
    out = []
    for o in bpy.context.scene.objects:
        if o.type in ('MESH', 'CURVE') and game_part(o.get('part')) in GAME_PARTS and not o.hide_render:
            out.append((o, o['part']))
    return out


def world_mesh(o, deps):
    """The object as it is evaluated (modifiers applied, curves made tubes), in the build's space, its faces of more
    than four corners cut into triangles."""
    me = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
    me.transform(o.matrix_world)
    if o.matrix_world.to_3x3().determinant() < 0:
        me.flip_normals()
    bm = bmesh.new()
    bm.from_mesh(me)
    big = [f for f in bm.faces if len(f.verts) > 4]
    if big:
        bmesh.ops.triangulate(bm, faces=big)
    bm.to_mesh(me)
    bm.free()
    return me


def unwrap(objs):
    """Each object unwrapped on its own, then all their islands scaled alike and packed into one square."""
    view = bpy.context.view_layer
    for o in objs:
        bpy.ops.object.select_all(action='DESELECT')
        o.select_set(True)
        view.objects.active = o
        bpy.ops.object.mode_set(mode='EDIT')
        bpy.ops.mesh.select_all(action='SELECT')
        bpy.ops.uv.smart_project(angle_limit=math.radians(55), island_margin=0.0, area_weight=0.0,
                                 scale_to_bounds=False)
        bpy.ops.object.mode_set(mode='OBJECT')
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    view.objects.active = objs[0]
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.select_all(action='SELECT')
    bpy.ops.uv.average_islands_scale()
    bpy.ops.uv.pack_islands(rotate=True, margin_method='FRACTION', margin=0.002, shape_method='CONCAVE')
    bpy.ops.object.mode_set(mode='OBJECT')


def plain_metal():
    """Every material made plain (not metal) for a bake of colours; what was there, to put back."""
    kept = []
    for m in MATS.values():
        nt = m.node_tree
        socket = nt.nodes['Principled BSDF'].inputs['Metallic']
        source = socket.links[0].from_socket if socket.is_linked else None
        kept.append((nt, socket, socket.default_value, source))
        if source is not None:
            nt.links.remove(socket.links[0])
        socket.default_value = 0.0
    return kept


def restore_metal(kept):
    for nt, socket, value, source in kept:
        socket.default_value = value
        if source is not None:
            nt.links.new(source, socket)


def bake_into(kind, obj, filters=None):
    """Bakes {@code kind} of the object into a fresh image and returns its pixels (rows from the top)."""
    img = bpy.data.images.new('bake_' + kind.lower(), BAKE_SIZE, BAKE_SIZE, alpha=True, float_buffer=True)
    img.generated_color = (0, 0, 0, 0)
    for m in MATS.values():
        nt = m.node_tree
        node = nt.nodes.get('bake_target') or nt.nodes.new('ShaderNodeTexImage')
        node.name = 'bake_target'
        node.image = img
        nt.nodes.active = node
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    extra = {'pass_filter': filters} if filters else {}
    bpy.ops.object.bake(type=kind, margin=0, use_clear=False, **extra)
    px = np.array(img.pixels[:], np.float32).reshape(BAKE_SIZE, BAKE_SIZE, 4)[::-1]
    return px


def dilate(rgb, valid, steps=16):
    """Spreads each island's colour out past its edges, so filtering does not pull in the background."""
    rgb, valid = rgb.copy(), valid.copy()
    for _ in range(steps):
        acc = np.zeros_like(rgb)
        cnt = np.zeros(valid.shape, np.float32)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
            v = np.roll(np.roll(valid, dy, 0), dx, 1)
            acc += np.roll(np.roll(rgb, dy, 0), dx, 1) * v[..., None]
            cnt += v
        grow = (~valid) & (cnt > 0)
        rgb[grow] = acc[grow] / cnt[grow][:, None]
        valid = valid | grow
    rgb[~valid] = rgb[valid].mean(axis=0)
    return rgb


def linear_to_srgb(c):
    c = np.clip(c, 0.0, 1.0)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055)


# --- the game's look: painted, not baked ------------------------------------------------------------------------------
#
# The game's texture is not a render of the realistic materials (scratched, grimy, photographic: they fit neither
# Minecraft nor the anime). It is painted the way the anime draws him and the way Minecraft draws everything: flat
# colours, a light tone and a shadow tone for each, cut by a hard line where the light turns away; dark ink lines along
# every hard edge; the cords striped in bands; blood in flat red splats. And chunky: about TEXELS_PER_PIXEL texels to
# each pixel of a skin, so it sits with the skin under it.

# Each material's flat colours in the game (sRGB): light, shadow, ink.
TOON = {
    'visor': ((255, 132, 36), (214, 94, 26), (70, 24, 8)),
    'engine': ((52, 52, 58), (32, 32, 36), (8, 8, 10)),
    'housing': ((52, 52, 58), (32, 32, 36), (8, 8, 10)),
    'slot': ((18, 14, 14), (18, 14, 14), (8, 6, 6)),
    'eye': ((255, 214, 84), (255, 196, 60), (255, 160, 40)),
    'tooth': ((244, 242, 234), (204, 202, 194), (86, 84, 80)),
    'gum': ((196, 74, 86), (152, 52, 62), (70, 18, 24)),
    'throat': ((30, 8, 12), (22, 6, 8), (10, 2, 4)),
    'cords': ((88, 66, 58), (60, 44, 38), (22, 15, 13)),
    'bar': ((246, 246, 242), (236, 236, 232), (80, 80, 84)),
    'chain': ((62, 62, 68), (42, 42, 46), (12, 12, 14)),
    'links': ((150, 150, 156), (108, 108, 114), (30, 30, 32)),
    'bolt': ((150, 150, 156), (108, 108, 114), (30, 30, 32)),
    'handle': ((36, 36, 40), (24, 24, 28), (8, 8, 10)),
    'hose': ((30, 30, 32), (20, 20, 22), (6, 6, 8)),
    'cord': ((166, 154, 136), (128, 118, 104), (50, 44, 38)),
    'wound': ((160, 18, 28), (108, 10, 18), (50, 4, 8)),
}
# The blood: a bright red and its darker heart.
BLOOD = ((186, 18, 26), (120, 8, 14))
# How much of each material's surface is splashed with blood (the chance, roughly), and how big the splashes are
# (pixels).
BLOODY = {'bar': (0.42, 1.6), 'chain': (0.3, 1.0), 'tooth': (0.03, 0.8), 'visor': (0.1, 0.9), 'wound': (1.0, 1.0)}
# The light the shadow tone is cut against: from in front, above and to his left.
TOON_LIGHT = Vector((0.35, -0.55, 0.75)).normalized()
TOON_SHADOW_AT = 0.12
# Edges sharper than this (degrees between their faces) get an ink line.
INK_ANGLE = 38.0
TEXELS_PER_PIXEL = 2.5


def value_noise(p, scale, seed):
    """Smooth value noise at {@code p} (arrays of x, y, z), features about {@code scale} apart; 0 to 1."""
    q = [c / scale for c in p]
    i = [np.floor(c).astype(np.int64) for c in q]
    f = [c - np.floor(c) for c in q]
    f = [t * t * (3 - 2 * t) for t in f]

    def hashed(x, y, z):
        h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * 2654435761)
        h = (h ^ (h >> 13)) * 1274126177
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0

    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[0] if dx else 1 - f[0]) * (f[1] if dy else 1 - f[1]) * (f[2] if dz else 1 - f[2])
                out = out + w * hashed(i[0] + dx, i[1] + dy, i[2] + dz)
    return out


def toon_colour(mat, pos, normal):
    """The colours of texels of {@code mat} at build-space points {@code pos} (N x 3) facing {@code normal} (N x 3)."""
    light, shadow, ink = TOON.get(mat, ((200, 0, 200), (150, 0, 150), (40, 0, 40)))
    lit = normal @ np.array(TOON_LIGHT) > TOON_SHADOW_AT
    rgb = np.where(lit[:, None], np.array(light, np.float32), np.array(shadow, np.float32))
    if mat == 'cords':
        # Wrapped in bands, like the anime's brickwork of strands.
        band = (np.floor(pos[:, 2] * 2.2 + value_noise(pos.T, 0.6, 5) * 1.5) % 2).astype(bool)
        rgb = np.where(band[:, None], rgb * 0.68, rgb)
    if mat in BLOODY:
        amount, size = BLOODY[mat]
        n = value_noise(pos.T, size, 11) * 0.7 + value_noise(pos.T, size * 0.35, 12) * 0.3
        drip = value_noise(np.array([pos[:, 0], pos[:, 1], pos[:, 2] * 0.18]), size * 0.5, 13)
        splash = (n > 1.0 - amount * 0.75) | ((drip > 0.8) & (n > 1.0 - amount * 1.1))
        heart = n > 1.0 - amount * 0.45
        rgb = np.where(splash[:, None], np.array(BLOOD[0], np.float32), rgb)
        rgb = np.where((splash & heart)[:, None], np.array(BLOOD[1], np.float32), rgb)
    return rgb


def paint_toon(copies, size):
    """Paints the copies' faces into a size x size texture through their 'bake' UVs: each texel the colour of its
    material where it lands on the model, then ink along the hard edges. Returns the image (rows from the top)."""
    rgb = np.zeros((size, size, 3), np.float32)
    valid = np.zeros((size, size), bool)
    inks = []
    for c in copies:
        bm = bmesh.new()
        bm.from_mesh(c.data)
        uv_layer = bm.loops.layers.uv['bake']
        mats = [m.name.split('.')[0] for m in c.data.materials] or ['']
        # Ink: every boundary edge and every edge sharper than INK_ANGLE, in each face's own UVs.
        for e in bm.edges:
            faces = e.link_faces
            sharp = len(faces) < 2 or math.degrees(faces[0].normal.angle(faces[1].normal, 0.0)) > INK_ANGLE
            if not sharp:
                continue
            for f in faces:
                for loop in f.loops:
                    if loop.edge == e:
                        a = loop[uv_layer].uv
                        b = loop.link_loop_next[uv_layer].uv
                        inks.append(((a.x * size, (1 - a.y) * size), (b.x * size, (1 - b.y) * size),
                                     TOON.get(mats[f.material_index], ((0, 0, 0),) * 3)[2]))
        bmesh.ops.triangulate(bm, faces=bm.faces[:])
        for f in bm.faces:
            mat = mats[min(f.material_index, len(mats) - 1)]
            uv = np.array([[l[uv_layer].uv.x * size, (1 - l[uv_layer].uv.y) * size] for l in f.loops], np.float64)
            co = np.array([list(l.vert.co) for l in f.loops], np.float64)
            x0, y0 = np.floor(uv.min(axis=0)).astype(int)
            x1, y1 = np.ceil(uv.max(axis=0)).astype(int)
            x0, y0 = max(x0, 0), max(y0, 0)
            x1, y1 = min(x1, size - 1), min(y1, size - 1)
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            p = np.stack([gx.ravel(), gy.ravel()], axis=1)
            (ax, ay), (bx, by), (cx, cy) = uv
            det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(det) < 1e-12:
                continue
            w0 = ((by - cy) * (p[:, 0] - cx) + (cx - bx) * (p[:, 1] - cy)) / det
            w1 = ((cy - ay) * (p[:, 0] - cx) + (ax - cx) * (p[:, 1] - cy)) / det
            w2 = 1 - w0 - w1
            # A little slack, so texels on the triangles' shared edges are not left out.
            inside = (w0 >= -0.02) & (w1 >= -0.02) & (w2 >= -0.02)
            if not inside.any():
                continue
            w = np.stack([w0, w1, w2], axis=1)[inside]
            pos = w @ co
            normal = np.repeat(np.array([list(f.normal)]), len(pos), axis=0)
            col = toon_colour(mat, pos, normal)
            px = p[inside].astype(int)
            rgb[px[:, 1], px[:, 0]] = col
            valid[px[:, 1], px[:, 0]] = True
        bm.free()
    img = Image.fromarray(np.clip(dilate(rgb / 255.0, valid, steps=4) * 255 + 0.5, 0, 255).astype(np.uint8), 'RGB')
    draw = ImageDraw.Draw(img)
    for a, b, colour in inks:
        draw.line([a, b], fill=tuple(colour), width=1)
    return img


def toon_size(copies):
    """The texture's size (a power of two) that gives about TEXELS_PER_PIXEL texels to a pixel of the model."""
    model, uv = 0.0, 0.0
    for c in copies:
        me = c.data
        layer = me.uv_layers['bake'].data
        for poly in me.polygons:
            model += poly.area
            pts = [layer[li].uv for li in poly.loop_indices]
            uv += abs(sum(pts[i].x * pts[(i + 1) % len(pts)].y - pts[(i + 1) % len(pts)].x * pts[i].y
                          for i in range(len(pts)))) / 2
    want = TEXELS_PER_PIXEL * math.sqrt(model / max(uv, 1e-9))
    return int(2 ** round(math.log2(want)))


# Small, many-pieced parts (the chains, the teeth, the wires, the bolts...) are not unwrapped and painted: each of their
# faces takes one flat colour from a swatch, light or shadow as it faces, striped or bloodied as its place says. At their
# size that looks the same, and it leaves the texture to the big parts.
SWATCHED = {'chain', 'links', 'bolt', 'tooth', 'cords', 'handle', 'hose', 'gum', 'throat', 'slot', 'eye', 'cord',
            'wound'}
# The share of the texture's width the painted parts are packed into; the swatches go down the rest.
PAINTED_SHARE = 0.8
SWATCH_TEXELS = 4


def material_of(c):
    return c.data.materials[0].name.split('.')[0] if c.data.materials else ''


def swatch_tones(mat, pos, normal):
    """Which of a swatched material's tones each face takes: 0 light, 1 shadow, 2 blood, 3 dark blood."""
    rgb = toon_colour(mat, pos, normal)
    light, shadow, ink = TOON.get(mat, ((200, 0, 200),) * 3)
    tones = np.zeros(len(pos), int)
    for k, colour in enumerate((light, shadow, BLOOD[0], BLOOD[1])):
        tones[np.all(np.abs(rgb - np.array(colour, np.float32)) < 1, axis=1)] = k
    # The cords' dark bands are their own tone: the shadow's.
    if mat == 'cords':
        tones[np.all(np.abs(rgb - np.array(light, np.float32) * 0.68) < 1, axis=1)] = 1
        tones[np.all(np.abs(rgb - np.array(shadow, np.float32) * 0.68) < 1, axis=1)] = 1
    return tones


def assign_swatches(copies, size):
    """Points every face of the swatched copies at its tone's swatch; returns what to paint where ((x, y, colour) for
    each swatch) and where each swatch is ((material, tone) to its UV)."""
    mats = sorted({material_of(c) for c in copies})
    cells = {}
    paint = []
    x0 = int(size * PAINTED_SHARE) + 2
    per_row = max(1, (size - x0) // SWATCH_TEXELS)
    i = 0
    for mat in mats:
        light, shadow, ink = TOON.get(mat, ((200, 0, 200),) * 3)
        for k, colour in enumerate((light, shadow, BLOOD[0], BLOOD[1])):
            cx = x0 + (i % per_row) * SWATCH_TEXELS
            cy = (i // per_row) * SWATCH_TEXELS
            cells[(mat, k)] = ((cx + SWATCH_TEXELS / 2) / size, 1.0 - (cy + SWATCH_TEXELS / 2) / size)
            paint.append((cx, cy, colour))
            i += 1
    assert (i - 1) // per_row * SWATCH_TEXELS + SWATCH_TEXELS <= size, 'the swatches do not fit beside the painted parts'
    for c in copies:
        me = c.data
        mat = material_of(c)
        layer = me.uv_layers['bake'].data
        centres = np.array([list(p.center) for p in me.polygons])
        normals = np.array([list(p.normal) for p in me.polygons])
        if not len(centres):
            continue
        tones = swatch_tones(mat, centres, normals)
        for poly, tone in zip(me.polygons, tones):
            u, v = cells[(mat, int(tone))]
            for li in poly.loop_indices:
                layer[li].uv = (u, v)
    return paint, cells


def shrink_uvs(copies, share):
    """Scales the copies' packed UVs into the bottom-left {@code share} of the square."""
    for c in copies:
        layer = c.data.uv_layers['bake'].data
        co = np.empty(len(layer) * 2, np.float32)
        layer.foreach_get('uv', co)
        co = co * share
        co[1::2] += 1.0 - share
        layer.foreach_set('uv', co)


def export_game():
    """Paints the form and writes the game's mesh and texture.

    .csm (little endian): b"CSM2", int32 part count; per part its name and the bone it follows (each int16 length then
    UTF-8); a byte, 1 if the part is a saw that grows out in the transformation, then its root and the way it runs (six
    float32, pixels and a unit vector in the bone's model space); int32 quad count, and four corners per quad: float32
    x, y, z (pixels, in the bone's model space from its pivot), u, v, then int8 nx, ny, nz and uint8 flags (bit 0:
    glows). Triangles repeat their last corner."""
    import struct
    global CHAIN_FRAMES
    CHAIN_FRAMES = CHAIN_FRAMES_GAME
    build('rest')
    scene = bpy.context.scene
    for o in scene.objects:
        if o.type == 'CURVE':
            o.data.resolution_u = GAME_CURVE_RESOLUTION
            o.data.bevel_resolution = 1
    deps = bpy.context.evaluated_depsgraph_get()
    objs = game_objects()
    copies = []
    for o, part in objs:
        me = world_mesh(o, deps)
        me.uv_layers.new(name='bake')
        me.uv_layers.active = me.uv_layers['bake']
        me.uv_layers['bake'].active_render = True
        c = bpy.data.objects.new('bake:' + o.name, me)
        scene.collection.objects.link(c)
        c['game_part'] = part
        copies.append(c)
    # Where each growing part grows from, and which way, in the build's space.
    growth = {}
    for o, part in objs:
        if 'grow_root' in o:
            root = o.matrix_world @ Vector(o['grow_root'])
            axis = (o.matrix_world.to_3x3() @ Vector(o['grow_axis'])).normalized()
            growth[part] = (root, axis)
    for o, part in objs:
        o.hide_render = True
    painted = [c for c in copies if material_of(c) not in SWATCHED]
    swatched = [c for c in copies if material_of(c) in SWATCHED]
    unwrap(painted)
    shrink_uvs(painted, PAINTED_SHARE)
    size = toon_size(painted)
    swatches, cells = assign_swatches(swatched, size)
    # Read each copy's geometry before they are joined for the bake.
    parts = {}
    for c in copies:
        me = c.data
        uv = me.uv_layers['bake'].data
        normals = me.corner_normals
        part = c['game_part']
        for poly in me.polygons:
            mat = me.materials[poly.material_index].name if me.materials else ''
            flags = 1 if mat.split('.')[0] in GLOW else 0
            corners = []
            for li in poly.loop_indices:
                co = me.vertices[me.loops[li].vertex_index].co
                corners.append((co.copy(), tuple(uv[li].uv), Vector(normals[li].vector), flags))
            if len(corners) == 3:
                corners.append(corners[2])
            parts.setdefault(part, []).append(corners)

    # The cord stretched out of the chest in the transformation is drawn by the game itself: a part named rope, one
    # empty quad, gives it its colour in the texture (the handle's black, to stand out against the shirt).
    rope_uv = cells[('handle', 0)]
    parts['rope'] = [[(Vector(GAME_PARTS['rope'][1]), rope_uv, Vector((0.0, -1.0, 0.0)), 0)] * 4]

    # The texture, painted (see paint_toon), and the swatches beside it.
    img = paint_toon(painted, size)
    draw = ImageDraw.Draw(img)
    for x, y, colour in swatches:
        draw.rectangle([x, y, x + SWATCH_TEXELS - 1, y + SWATCH_TEXELS - 1], fill=tuple(colour))
    os.makedirs(os.path.dirname(GAME_TEXTURE), exist_ok=True)
    img.save(GAME_TEXTURE, optimize=True)
    print('texture %s, %d x %d (%d KB)' % (GAME_TEXTURE, size, size, os.path.getsize(GAME_TEXTURE) // 1024))

    os.makedirs(os.path.dirname(GAME_MESH), exist_ok=True)
    with open(GAME_MESH, 'wb') as f:
        f.write(b'CSM2' + struct.pack('<i', len(parts)))
        for name, quads in parts.items():
            bone, pivot = GAME_PARTS[game_part(name)]
            f.write(struct.pack('<h', len(name)) + name.encode() + struct.pack('<h', len(bone)) + bone.encode())
            if name in growth:
                root, axis = growth[name]
                f.write(struct.pack('<B3f3f', 1, *to_game(root, pivot), *game_normal(axis)))
            else:
                f.write(struct.pack('<B', 0))
            f.write(struct.pack('<i', len(quads)))
            buf = bytearray()
            for q in quads:
                for co, (u, v), n, flags in q:
                    x, y, z = to_game(co, pivot)
                    nx, ny, nz = game_normal(n.normalized())
                    buf += struct.pack('<5f3bB', x, y, z, u, 1.0 - v, int(round(nx * 127)), int(round(ny * 127)),
                                       int(round(nz * 127)), flags)
            f.write(buf)
            print('  %-6s on the %-4s %6d quads' % (name, bone, len(quads)))
    print('mesh %s (%d KB)' % (GAME_MESH, os.path.getsize(GAME_MESH) // 1024))


# --- preview renders ----------------------------------------------------------------------------------------------

def look_at(obj, target):
    obj.rotation_mode = 'XYZ'
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat('-Z', 'Y').to_euler()


def studio():
    """A plain dark-grey studio: a warm key light high on the left, a cold rim behind, a soft fill and a floor."""
    scene = bpy.context.scene
    world = bpy.data.worlds.new('studio')
    world.use_nodes = True
    # A dim sky for the metal to reflect: dark below the horizon, grey-blue above, brightest high up.
    nt = world.node_tree
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.45
    ramp.color_ramp.elements[0].color = (0.012, 0.012, 0.014, 1.0)
    ramp.color_ramp.elements[1].position = 1.0
    ramp.color_ramp.elements[1].color = (0.32, 0.36, 0.42, 1.0)
    ramp.color_ramp.elements.new(0.52).color = (0.09, 0.095, 0.11, 1.0)
    rng = nt.nodes.new('ShaderNodeMapRange')
    rng.inputs['From Min'].default_value = -1.0
    nt.links.new(nt.nodes.new('ShaderNodeTexCoord').outputs['Generated'], sep.inputs['Vector'])
    nt.links.new(sep.outputs['Z'], rng.inputs['Value'])
    nt.links.new(rng.outputs['Result'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], nt.nodes['Background'].inputs['Color'])
    nt.nodes['Background'].inputs['Strength'].default_value = 1.0
    scene.world = world
    for name, loc, size, energy, color in (('key', (-40, -48, 52), 30, 60000, (1.0, 0.94, 0.86)),
                                           ('rim', (34, 46, 40), 22, 42000, (0.65, 0.78, 1.0)),
                                           ('fill', (48, -40, 14), 40, 12000, (0.9, 0.92, 1.0))):
        light = bpy.data.objects.new(name, bpy.data.lights.new(name, 'AREA'))
        light.data.size = size
        light.data.energy = energy
        light.data.color = color
        light.location = loc
        look_at(light, (0, 0, 20))
        scene.collection.objects.link(light)
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=200)
    floor = bpy.data.objects.new('floor', bpy.data.meshes.new('floor'))
    bm.to_mesh(floor.data)
    bm.free()
    floor.data.materials.append(new_material('floor', (0.05, 0.05, 0.055), rough=0.9))
    scene.collection.objects.link(floor)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = SAMPLES
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Medium High Contrast'


SHOTS = [
    # name, pose, camera, target, lens, size
    ('hero', 'stance', (-44, -66, 34), (0, -8, 20), 55, (1000, 1250)),
    ('front', 'stance', (0, -80, 22), (0, 0, 19), 55, (1000, 1250)),
    ('side', 'rest', (-80, -4, 20), (0, -4, 18), 55, (1250, 1000)),
    ('head', 'stance', (-24, -30, 33), (0, -6, 28.5), 55, (1250, 1000)),
    ('head_side', 'stance', (-48, -4, 29), (0, -6, 28.5), 50, (1500, 900)),
    ('head_back', 'stance', (-28, 30, 34), (0, 0, 28.5), 55, (1250, 1000)),
    ('arm_saw', 'rest', (30, -26, 12), (7, 0, 10), 65, (1000, 1250)),
]


def renders(out, only):
    built = None
    for name, pose, loc, target, lens, size in SHOTS:
        if only and name not in only:
            continue
        if built != pose:
            build(pose)
            studio()
            built = pose
        scene = bpy.context.scene
        scene.render.resolution_x, scene.render.resolution_y = size
        cam = scene.camera
        cam.location = Vector(loc)
        look_at(cam, target)
        cam.data.lens = lens
        cam.data.clip_start, cam.data.clip_end = 0.5, 1000
        scene.render.filepath = os.path.join(out, name + '.png')
        bpy.ops.render.render(write_still=True)
        print('rendered', name, flush=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(os.path.join(out, 'chainsaw.blend')))


def main():
    args = sys.argv[1:]
    out = args[args.index('--out') + 1] if '--out' in args else 'build/chainsaw'
    only = args[args.index('--only') + 1].split(',') if '--only' in args else None
    os.makedirs(out, exist_ok=True)
    if '--renders' in args:
        renders(out, only)
    if '--game' in args:
        export_game()


if __name__ == '__main__':
    main()
