"""Builds Chainsaw Man's form in Blender: the head, the saws out of the forearms and the cord on the chest.

Needs Blender's Python module (py -3.11 -m pip install "bpy==4.5.*" numpy pillow). Run from the repository root:

    py -3.11 tools/chainsaw_model.py --out DIR --renders   # preview renders and DIR/chainsaw.blend, for approval

Add --only name,name to render some of them, and CSM_SAMPLES=24 for a quick look.

Blender units are the player's pixels, sixteen to a block. The form is built on a stand-in of the player model as it
stands, feet at the origin, facing -y, z up: legs to 12, body to 24, head to 32, arms 4 wide either side of the body.
The stand-in is only there to judge the parts against; in the game they go on the real player, whose head is hidden
while the form is on.

The design follows the anime (season 1 and the Reze arc). The head is all mouth and saw: a rounded dark skull with a
grin of jagged teeth that runs from cheek to cheek, and a chainsaw bar standing out of the forehead, long, tipped a
little up. A chainsaw bar comes out of the back of each forearm, edge outward, and runs on past the fist. A starter
cord comes out of the middle of the chest and hangs with its T handle at the belly.
"""
import math
import os
import random
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Matrix, Vector

# --- layout (pixels; feet at the origin, facing -y, z up) ---------------------------------------------------------

# The head: its centre, half its width, depth and height, and how round its corners are.
HEAD_C = Vector((0.0, -0.3, 28.6))
HEAD_HALF = (4.9, 5.0, 5.0)
HEAD_ROUND = 2.6
# The mouth: the middle of the grin on the face, half its width and height, and how far round the cheeks it goes.
MOUTH_C = Vector((0.0, -5.4, 26.6))
MOUTH_HALF = (4.6, 3.2, 1.75)
# The teeth: how many along each row, how long they are and how thick at the root.
TEETH = 13
TOOTH_LEN = (1.5, 2.1)
TOOTH_R = 0.42
# The forehead's saw: where the bar leaves the head, its length, height and thickness, and its tilt up (degrees).
HEAD_BAR_ROOT = Vector((0.0, -3.6, 31.4))
HEAD_BAR = (15.0, 3.0, 0.7)
HEAD_BAR_TILT = 9.0
# The arms' saws: length, height and thickness; where they leave the forearm, measured down the arm from the shoulder.
ARM_BAR = (13.0, 2.4, 0.6)
ARM_BAR_ROOT = 6.0
# The chain's cutters: how far apart they are round the bar.
CUTTER_PITCH = 0.8
# The cord: where it leaves the chest, how far it hangs, and the T handle's length and thickness.
CORD_ROOT = Vector((0.0, -2.05, 20.5))
CORD_DROP = 4.2
HANDLE = (3.2, 0.42)

SAMPLES = int(os.environ.get('CSM_SAMPLES', '64'))

MATS = {}


def new_material(name, color, metal=0.0, rough=0.5, emit=None, bumpy=0.0, sss=0.0):
    """A principled material, with a little noise bump if {@code bumpy} > 0 (skin, worn metal)."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*color, 1.0)
    p.inputs['Metallic'].default_value = metal
    p.inputs['Roughness'].default_value = rough
    if sss:
        p.inputs['Subsurface Weight'].default_value = sss
        p.inputs['Subsurface Radius'].default_value = (1.0, 0.25, 0.15)
        p.inputs['Subsurface Scale'].default_value = 0.4
    if emit:
        p.inputs['Emission Color'].default_value = (*emit[0], 1.0)
        p.inputs['Emission Strength'].default_value = emit[1]
    if bumpy:
        noise = nt.nodes.new('ShaderNodeTexNoise')
        noise.inputs['Scale'].default_value = 1.6
        noise.inputs['Detail'].default_value = 8.0
        bump = nt.nodes.new('ShaderNodeBump')
        bump.inputs['Strength'].default_value = bumpy
        bump.inputs['Distance'].default_value = 0.2
        nt.links.new(noise.outputs['Fac'], bump.inputs['Height'])
        nt.links.new(bump.outputs['Normal'], p.inputs['Normal'])
    MATS[name] = mat
    return mat


def make_materials():
    # His head: near-black, a little red in it, the skin wet and ridged.
    new_material('skin', (0.022, 0.012, 0.013), rough=0.58, bumpy=0.5, sss=0.05)
    new_material('gum', (0.22, 0.015, 0.02), rough=0.3, sss=0.3)
    new_material('throat', (0.012, 0.0, 0.002), rough=0.7)
    new_material('tooth', (0.78, 0.74, 0.62), rough=0.28, sss=0.1)
    # The saws: the bar's plate pale steel, worn; the chain darker; the housings dark iron.
    new_material('bar', (0.66, 0.67, 0.70), metal=0.75, rough=0.42, bumpy=0.06)
    new_material('bar_groove', (0.18, 0.18, 0.19), metal=1.0, rough=0.45)
    new_material('chain', (0.42, 0.42, 0.44), metal=0.8, rough=0.36)
    new_material('housing', (0.06, 0.06, 0.065), metal=0.6, rough=0.5, bumpy=0.1)
    new_material('cord', (0.42, 0.40, 0.36), rough=0.8)
    new_material('handle', (0.035, 0.035, 0.04), rough=0.55)
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


def perimeter(length, height, pitch, inset=0.0):
    """Points evenly spaced round the bar's running edge (not the root end), with the way the chain runs there:
    (u, v, du, dv). The chain goes out along the top, round the nose and back along the bottom."""
    r = height / 2.0 - inset
    straight = length - height / 2.0
    total = 2 * straight + math.pi * r
    out = []
    n = int(total / pitch)
    for i in range(n):
        s = (i + 0.5) * total / n
        if s < straight:
            out.append((s, r, 1.0, 0.0))
        elif s < straight + math.pi * r:
            a = math.pi / 2 - (s - straight) / r
            out.append((straight + r * math.cos(a), r * math.sin(a), math.sin(a), -math.cos(a)))
        else:
            out.append((straight - (s - straight - math.pi * r), -r, -1.0, 0.0))
    return out


def saw(name, size, frame, part):
    """A chainsaw bar of {@code size} (length, height, thickness): the plate, a groove down its middle, the chain of
    cutters round its edge, and the drive housing it comes out of. {@code frame} takes the bar's own space (u along
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
    plate = mesh_obj(name + '_bar', bm, ['bar'], part)

    # The groove, a dark strip a little proud of each face, along the middle of the bar.
    bm = bmesh.new()
    for w in (-thick / 2 - 0.02, thick / 2 + 0.02):
        inner = stadium(length - 0.6, height * 0.22, steps=8)
        vs = [bm.verts.new(frame @ Vector((u + 0.3, v, w))) for u, v in inner]
        f = bm.faces.new(vs if w > 0 else vs[::-1])
    mesh_obj(name + '_groove', bm, ['bar_groove'], part)

    # The chain: a cutter at each pitch round the edge, each a little wedge leaning the way the chain runs, its tooth
    # on alternate sides, on a link that rides in the bar's edge.
    bm = bmesh.new()
    for k, (u, v, du, dv) in enumerate(perimeter(length, height, CUTTER_PITCH)):
        along = Vector((du, dv, 0.0))
        out = Vector((dv, -du, 0.0))  # away from the bar
        base = Vector((u, v, 0.0))
        side = 1.0 if k % 2 else -1.0
        # The link: a flat block on the edge.
        link_half = (CUTTER_PITCH * 0.49, 0.3, thick * 0.34)
        corners = []
        for a in (-1, 1):
            for b in (-1, 1):
                for c in (-1, 1):
                    corners.append(base + along * a * link_half[0] + out * (b * link_half[1] + 0.12) + Vector((0, 0, c * link_half[2])))
        lv = [bm.verts.new(frame @ p) for p in corners]
        for quad in ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)):
            bm.faces.new([lv[q] for q in quad])
        # The tooth: a wedge on one side of the link, its point raking forward and out.
        w = side * thick * 0.42
        tv = [bm.verts.new(frame @ p) for p in (
            base - along * 0.38 + out * 0.32 + Vector((0, 0, w - 0.13)),
            base - along * 0.38 + out * 0.32 + Vector((0, 0, w + 0.13)),
            base + along * 0.3 + out * 0.36 + Vector((0, 0, w - 0.13)),
            base + along * 0.3 + out * 0.36 + Vector((0, 0, w + 0.13)),
            base + along * 0.45 + out * 1.0 + Vector((0, 0, w)),
        )]
        for tri in ((0, 2, 4), (3, 1, 4), (1, 0, 4), (2, 3, 4), (0, 1, 3, 2)):
            bm.faces.new([tv[t] for t in tri])
    chain = mesh_obj(name + '_chain', bm, ['chain'], part)

    # The housing at the root: a dark block the bar comes out of, wider than the bar.
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=Vector((2.2, height + 0.9, thick + 1.5)), verts=bm.verts)
    bmesh.ops.translate(bm, vec=Vector((-0.4, 0.0, 0.0)), verts=bm.verts)
    bmesh.ops.transform(bm, matrix=frame, verts=bm.verts)
    housing = mesh_obj(name + '_housing', bm, ['housing'], part)
    mod = housing.modifiers.new('bevel', 'BEVEL')
    mod.width = 0.35
    mod.segments = 2
    return plate


def build_head():
    """The skull: a rounded block, the grin cut into its face and the mouth behind it dark red, two rows of teeth."""
    head = box('head', HEAD_C, HEAD_HALF, 'skin', 'head', bevel=HEAD_ROUND, segments=5)
    head.data.materials.append(MATS['gum'])
    # The grin, cut out with a squashed sphere that reaches round the cheeks.
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=1.0, location=MOUTH_C)
    cutter = bpy.context.active_object
    cutter.scale = MOUTH_HALF
    cutter.hide_render = True
    cutter.hide_viewport = True
    bpy.context.view_layer.update()
    bpy.context.view_layer.objects.active = head
    head.select_set(True)
    bpy.ops.object.modifier_apply(modifier='bevel')
    mod = head.modifiers.new('mouth', 'BOOLEAN')
    mod.object = cutter
    mod.operation = 'DIFFERENCE'
    mod.solver = 'EXACT'
    mod.material_mode = 'TRANSFER'
    bpy.ops.object.modifier_apply(modifier='mouth')
    bpy.data.objects.remove(cutter)
    # Faces cut by the grin take the gum's colour.
    for poly in head.data.polygons:
        c = poly.center
        dx = (c.x - MOUTH_C.x) / MOUTH_HALF[0]
        dy = (c.y - MOUTH_C.y) / MOUTH_HALF[1]
        dz = (c.z - MOUTH_C.z) / MOUTH_HALF[2]
        if dx * dx + dy * dy + dz * dz < 1.08:
            poly.material_index = 1
    smooth = head.modifiers.new('smooth', 'SUBSURF')
    smooth.levels = 1
    smooth.render_levels = 2
    bpy.ops.object.shade_smooth()

    # The dark of the throat behind the teeth.
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1.0,
                                         location=MOUTH_C + Vector((0.0, 2.3, 0.0)))
    throat = bpy.context.active_object
    throat.scale = (MOUTH_HALF[0] * 0.92, 2.2, MOUTH_HALF[2] * 0.95)
    throat.data.materials.append(MATS['throat'])
    bpy.ops.object.shade_smooth()
    throat['part'] = 'head'

    # The teeth: an upper row hanging from the top of the grin and a lower row standing up from the bottom, set round
    # the curve of the face, each a little different, jagged and leaning, the two rows between each other.
    rnd = random.Random(7)
    bm = bmesh.new()
    for row, sign in (('upper', -1.0), ('lower', 1.0)):
        for i in range(TEETH):
            f = (i + (0.5 if sign > 0 else 0.0)) / (TEETH - 0.5) * 2.0 - 1.0
            if abs(f) > 1.0:
                continue
            a = f * 1.18  # how far round the cheek, radians from the front
            x = MOUTH_C.x + math.sin(a) * MOUTH_HALF[0] * 0.95
            y = MOUTH_C.y + (1.0 - math.cos(a)) * MOUTH_HALF[1] * 0.95 + 0.55
            z = MOUTH_C.z - sign * (MOUTH_HALF[2] - 0.15)
            length = rnd.uniform(*TOOTH_LEN) * (1.0 - 0.35 * abs(f))
            r = TOOTH_R * rnd.uniform(0.85, 1.15)
            lean = Vector((rnd.uniform(-0.25, 0.25), -0.35, sign))
            tip = Vector((x, y, z)) + lean.normalized() * length
            root = Vector((x, y, z)) - Vector((0, 0, sign)) * 0.3
            ring = []
            for k in range(5):
                t = 2 * math.pi * k / 5
                ring.append(bm.verts.new(root + Vector((math.cos(t) * r, math.sin(t) * r * 0.8, 0.0))))
            apex = bm.verts.new(tip)
            for k in range(5):
                tri = [ring[k], ring[(k + 1) % 5], apex]
                bm.faces.new(tri if sign < 0 else tri[::-1])
            bm.faces.new(ring[::-1] if sign < 0 else ring)
    teeth = mesh_obj('teeth', bm, ['tooth'], 'head')

    # The saw out of the forehead: out along -y, tipped up, flat faces to the sides.
    tilt = math.radians(HEAD_BAR_TILT)
    along = Vector((0.0, -math.cos(tilt), math.sin(tilt)))
    up = Vector((0.0, math.sin(tilt), math.cos(tilt)))
    frame = Matrix.Translation(HEAD_BAR_ROOT) @ Matrix((
        (along.x, up.x, 1.0, 0.0), (along.y, up.y, 0.0, 0.0), (along.z, up.z, 0.0, 0.0), (0, 0, 0, 1)))
    saw('head_saw', HEAD_BAR, frame, 'head')
    return head


def shoulder(side):
    """Where an arm hangs from (x outward on {@code side}, +1 for the left arm and -1 for the right)."""
    return Vector((side * 6.0, 0.0, 22.0))


def build_arm(side, pose):
    """An arm of the stand-in, and the saw out of its forearm, edge outward, running down past the fist; all hung from
    an empty at the shoulder turned by {@code pose} (x, y, z degrees), so the arm can be posed."""
    pivot = bpy.data.objects.new('arm_' + ('left' if side > 0 else 'right'), None)
    link(pivot, 'arm')
    pivot.location = shoulder(side)
    pivot.rotation_euler = [math.radians(a) for a in pose]
    parts = []
    # The sleeve, and the hand below it, in the arm's own space (shoulder at the origin, hanging down -z).
    parts.append(box('sleeve', (0.0, 0.0, -4.5), (2.0, 2.0, 4.5), 'shirt', 'stand_in'))
    parts.append(box('hand', (0.0, 0.0, -10.75), (2.0, 2.0, 1.75), 'hand', 'stand_in'))
    # The saw: out of the back of the forearm, flat faces forward and back, running down the arm and past the fist.
    root = Vector((side * 2.6, 0.0, -ARM_BAR_ROOT))
    frame = Matrix.Translation(root) @ Matrix((
        (0.0, side * 1.0, 0.0, 0.0), (0.0, 0.0, 1.0, 0.0), (-1.0, 0.0, 0.0, 0.0), (0, 0, 0, 1)))
    before = set(bpy.context.scene.objects)
    saw('arm_saw', ARM_BAR, frame, 'arm')
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


# --- preview renders ----------------------------------------------------------------------------------------------

def look_at(obj, target):
    obj.rotation_mode = 'XYZ'
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat('-Z', 'Y').to_euler()


def studio():
    """A plain dark-grey studio: a warm key light high on the left, a cold rim behind, a soft fill and a floor."""
    scene = bpy.context.scene
    world = bpy.data.worlds.new('studio')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.05, 0.052, 0.058, 1.0)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 1.0
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
    ('hero', 'stance', (-38, -62, 34), (0, -4, 19), 60, (1000, 1250)),
    ('front', 'stance', (0, -80, 22), (0, 0, 19), 55, (1000, 1250)),
    ('side', 'rest', (-80, -4, 20), (0, -4, 18), 55, (1250, 1000)),
    ('head', 'stance', (-16, -30, 33), (0, -6, 29), 70, (1250, 1000)),
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


if __name__ == '__main__':
    main()
