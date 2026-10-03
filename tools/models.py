"""Builds the uplink feed's meshes in Blender and exports them as .ssm files for the mod.

Needs Blender's Python module (pip install "bpy==4.5.*"). Run from the repository root:

    python tools/models.py                 # writes the .ssm meshes
    python tools/models.py --preview DIR   # also renders a Cycles preview of each mesh into DIR

.ssm layout (little endian): b"SSM1", int32 vertex count, int32 index count, then per vertex
float32 x, y, z, float32 u, v, uint8 ambient occlusion, glow, material, 255, int8 nx, ny, nz, 0;
then uint32 triangle indices. Coordinates are converted to Minecraft's Y-up axes.
"""
import math
import os
import random
import struct
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Vector, noise
from mathutils.bvhtree import BVHTree

OUT = 'src/client/resources/assets/shootingstar/meshes'

# Material ids, read by the feed's mesh shader.
HULL, FIN, NOSE, FRAME, PANEL, ROCK, FOIL, SOLAR, DISH = range(9)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


class Builder:
    """A bmesh with per-face material and glow layers."""

    def __init__(self):
        self.bm = bmesh.new()
        self.mat = self.bm.faces.layers.int.new('mat')
        self.glow = self.bm.faces.layers.float.new('glow')

    def face(self, verts, mat, glow=0.0):
        f = self.bm.faces.new(verts)
        f[self.mat] = mat
        f[self.glow] = glow
        return f

    def vert(self, x, y, z):
        return self.bm.verts.new((x, y, z))

    def box(self, center, size, mat, glow=0.0, rot=0.0):
        """Axis-aligned box (optionally rotated about the Blender Y axis, which is the round's axis)."""
        cx, cy, cz = center
        sx, sy, sz = (s / 2 for s in size)
        c, s = math.cos(rot), math.sin(rot)
        vs = []
        for dx, dy, dz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                           (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]:
            x, z = dx * sx, dz * sz
            vs.append(self.vert(cx + x * c - z * s, cy + dy * sy, cz + x * s + z * c))
        for q in [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]:
            self.face([vs[i] for i in q], mat, glow)
        return vs

    def to_object(self, name):
        me = bpy.data.meshes.new(name)
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-5)
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        self.bm.normal_update()
        self.bm.to_mesh(me)
        self.bm.free()
        obj = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(obj)
        return obj


# --- the round ---------------------------------------------------------------------------------

def build_round():
    """The Gungnir round: a slender dart, nose along Blender -Y (Minecraft +Z), four swept fins."""
    b = Builder()
    seg = 48
    bands = [2.6, 1.2, -0.2, -1.6]  # glowing coil-contact rings
    profile = [(5.0, 0.0), (4.75, 0.045), (4.45, 0.11), (4.05, 0.19), (3.6, 0.255), (3.15, 0.3), (2.8, 0.32)]
    for band in bands:
        profile += [(band + 0.09, 0.32), (band + 0.06, 0.296), (band - 0.06, 0.296), (band - 0.09, 0.32)]
    profile += [(-3.9, 0.32), (-4.3, 0.3), (-4.75, 0.245), (-4.95, 0.235), (-5.0, 0.2), (-5.0, 0.12), (-4.9, 0.1),
                (-4.9, 0.0)]
    rings = []
    for z, r in profile:
        if r == 0.0:
            rings.append([b.vert(0, -z, 0)])
            continue
        rings.append([b.vert(r * math.cos(2 * math.pi * i / seg), -z, r * math.sin(2 * math.pi * i / seg))
                      for i in range(seg)])
    for k in range(len(rings) - 1):
        a, c = rings[k], rings[k + 1]
        za, zc = profile[k][0], profile[k + 1][0]
        zm = (za + zc) / 2
        glow = 0.0
        mat = HULL
        if zm > 3.9:
            mat = NOSE
        for band in bands:
            if abs(zm - band) < 0.065:
                glow = 1.0
        if zm < -4.92:
            glow = 0.85  # drive recess at the base
        for i in range(seg):
            j = (i + 1) % seg
            if len(a) == 1:
                b.face([a[0], c[j], c[i]], mat, glow)
            elif len(c) == 1:
                b.face([a[i], a[j], c[0]], mat, glow)
            else:
                b.face([a[i], a[j], c[j], c[i]], mat, glow)
    # Four swept fins in a cross, with glowing leading edges.
    for k in range(4):
        ang = k * math.pi / 2
        ca, sa = math.cos(ang), math.sin(ang)

        def at(radius, zz, side):
            t = 0.035 if radius < 0.6 else 0.022
            return b.vert(radius * ca - side * t * sa, -zz, radius * sa + side * t * ca)

        root_front, root_back, tip_front, tip_back = -2.55, -4.78, -3.95, -4.88
        r0, r1 = 0.29, 1.55
        v = [at(r0, root_front, -1), at(r0, root_back, -1), at(r1, tip_back, -1), at(r1, tip_front, -1),
             at(r0, root_front, 1), at(r0, root_back, 1), at(r1, tip_back, 1), at(r1, tip_front, 1)]
        b.face([v[0], v[1], v[2], v[3]], FIN)
        b.face([v[7], v[6], v[5], v[4]], FIN)
        b.face([v[0], v[3], v[7], v[4]], FIN, 1.0)   # leading edge
        b.face([v[3], v[2], v[6], v[7]], FIN, 0.6)   # tip
        b.face([v[2], v[1], v[5], v[6]], FIN)        # trailing edge
        b.face([v[1], v[0], v[4], v[5]], FIN)
    obj = b.to_object('round')
    smooth(obj, 35)
    return obj


# --- the accelerator coil ----------------------------------------------------------------------

def build_coil():
    """One accelerator coil: an octagonal frame of eight segments with glowing panels, a strut and a
    length of spine. The beam axis is Blender Y (Minecraft Z); the coil sits at the origin and the
    spine spans one coil spacing (1.0) so that consecutive coils join up."""
    b = Builder()
    r_in, r_out, depth, gap = 0.70, 1.0, 0.22, math.radians(2.5)
    inset, recess = 0.045, 0.02
    for i in range(8):
        a0 = i * math.pi / 4 - math.pi / 8 + gap / 2
        a1 = (i + 1) * math.pi / 4 - math.pi / 8 - gap / 2

        def p(r, a, y):
            return b.vert(r * math.cos(a), y, r * math.sin(a))

        fi0, fi1, fo0, fo1 = p(r_in, a0, depth / 2), p(r_in, a1, depth / 2), p(r_out, a0, depth / 2), p(r_out, a1, depth / 2)
        bi0, bi1, bo0, bo1 = p(r_in, a0, -depth / 2), p(r_in, a1, -depth / 2), p(r_out, a0, -depth / 2), p(r_out, a1, -depth / 2)
        b.face([fo0, fo1, bo1, bo0], FRAME)          # outer
        b.face([fi1, fi0, bi0, bi1], PANEL, 1.0)     # inner face glows toward the beam
        b.face([bi0, bo0, fo0, fi0], FRAME)          # side cuts at the gaps
        b.face([fi1, fo1, bo1, bi1], FRAME)
        # Front and back faces with a recessed glowing panel.
        for y, sign in ((depth / 2, 1), (-depth / 2, -1)):
            ri, ro = r_in + inset, r_out - inset
            ai0, ai1 = a0 + inset / r_out * 1.4, a1 - inset / r_out * 1.4
            outer = [p(r_in, a0, y), p(r_in, a1, y), p(r_out, a1, y), p(r_out, a0, y)]
            inner = [p(ri, ai0, y), p(ri, ai1, y), p(ro, ai1, y), p(ro, ai0, y)]
            deep = [p(ri, ai0, y - sign * recess), p(ri, ai1, y - sign * recess),
                    p(ro, ai1, y - sign * recess), p(ro, ai0, y - sign * recess)]
            for k in range(4):
                n = (k + 1) % 4
                b.face([outer[k], outer[n], inner[n], inner[k]], FRAME)
                b.face([inner[k], inner[n], deep[n], deep[k]], FRAME)
            b.face(deep, PANEL, 1.0)
        # Mount blocks on four sides.
        if i % 2 == 0:
            mid = (a0 + a1) / 2
            c = Vector((math.cos(mid) * (r_out + 0.06), 0, math.sin(mid) * (r_out + 0.06)))
            b.box(c, (0.16, 0.3, 0.12), FRAME, rot=-mid + math.pi / 2)
    # Strut from the bottom mount down to the spine, and the spine itself.
    b.box((0, 0, -1.33), (0.07, 0.07, 0.5), FRAME)
    b.box((0, 0, -1.6), (0.14, 1.0, 0.12), FRAME)
    b.box((0, 0, -1.6), (0.15, 0.08, 0.13), PANEL, 0.6)  # status light on the spine
    obj = b.to_object('coil')
    smooth(obj, 30)
    return obj


# --- asteroids ---------------------------------------------------------------------------------

def build_asteroid(index):
    rnd = random.Random(1000 + index)
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=4, radius=1.0)
    stretch = Vector((rnd.uniform(0.75, 1.3), rnd.uniform(0.65, 1.0), rnd.uniform(0.7, 1.2)))
    offset = Vector((rnd.uniform(-50, 50), rnd.uniform(-50, 50), rnd.uniform(-50, 50)))
    craters = [(Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized(), rnd.uniform(0.18, 0.42))
               for _ in range(rnd.randint(3, 7))]
    for v in bm.verts:
        d = v.co.normalized()
        h = 1.0 + 0.28 * noise.fractal(d * 1.3 + offset, 1.0, 2.0, 5) + 0.05 * noise.noise(d * 9.0 + offset)
        for c, size in craters:
            ang = d.angle(c)
            if ang < size:
                t = ang / size
                h -= 0.14 * size * (1.0 - t * t) * 2.2
            elif ang < size * 1.35:
                h += 0.05 * size * (1.0 - (ang - size) / (size * 0.35))
        v.co = Vector((d.x * stretch.x, d.y * stretch.y, d.z * stretch.z)) * h
    b = Builder()
    b.bm.free()
    b.bm = bm
    b.mat = bm.faces.layers.int.new('mat')
    b.glow = bm.faces.layers.float.new('glow')
    for f in bm.faces:
        f[b.mat] = ROCK
    obj = b.to_object('asteroid%d' % index)
    smooth(obj, 60)
    return obj


# --- relay satellite ---------------------------------------------------------------------------

def build_relay():
    """The orbital relay that bounces the uplink to Jupiter. Dish faces Blender -Y (Minecraft +Z)."""
    b = Builder()
    b.box((0, 0, 0), (0.7, 0.9, 0.7), FOIL)
    b.box((0, 0.5, 0), (0.5, 0.12, 0.5), FRAME)
    for side in (-1, 1):
        b.box((side * 0.75, 0, 0), (0.5, 0.06, 0.06), FRAME)
        for k in range(3):
            b.box((side * (1.1 + k * 0.72), 0, 0), (0.68, 0.025, 0.9), SOLAR)
    # Dish: a shallow paraboloid on a short mast.
    b.box((0, -0.55, 0), (0.08, 0.3, 0.08), FRAME)
    seg, rings = 32, 6
    center = b.vert(0, -0.75, 0)
    verts = []
    for k in range(1, rings + 1):
        r = 0.62 * k / rings
        y = -0.75 - 0.32 * (r / 0.62) ** 2
        verts.append([b.vert(r * math.cos(2 * math.pi * i / seg), y, r * math.sin(2 * math.pi * i / seg))
                      for i in range(seg)])
    for i in range(seg):
        j = (i + 1) % seg
        b.face([center, verts[0][j], verts[0][i]], DISH)
        for k in range(rings - 1):
            b.face([verts[k][i], verts[k][j], verts[k + 1][j], verts[k + 1][i]], DISH)
    b.box((0, -1.2, 0), (0.06, 0.5, 0.06), FRAME)
    b.box((0, -1.47, 0), (0.1, 0.06, 0.1), PANEL, 1.0)  # emitter
    obj = b.to_object('relay')
    smooth(obj, 30)
    return obj


# --- helpers -----------------------------------------------------------------------------------

def smooth(obj, angle):
    me = obj.data
    me.shade_smooth()
    me.set_sharp_from_angle(angle=math.radians(angle))


def ambient_occlusion(obj, samples=96, reach=None):
    """Per-corner hemisphere occlusion from ray casts against the mesh itself."""
    me = obj.data
    bvh = BVHTree.FromObject(obj, bpy.context.evaluated_depsgraph_get())
    size = max(obj.dimensions)
    reach = reach or size * 0.35
    rnd = random.Random(7)
    dirs = []
    for _ in range(samples):
        u, v = rnd.random(), rnd.random()
        r, phi = math.sqrt(u), 2 * math.pi * v
        dirs.append(Vector((r * math.cos(phi), r * math.sin(phi), math.sqrt(max(0.0, 1 - u)))))
    cache = {}
    result = []
    normals = me.corner_normals
    for loop in me.loops:
        n = Vector(normals[loop.index].vector)
        key = (loop.vertex_index, round(n.x, 3), round(n.y, 3), round(n.z, 3))
        if key not in cache:
            p = me.vertices[loop.vertex_index].co
            t = n.orthogonal().normalized()
            bt = n.cross(t)
            hit = 0.0
            for d in dirs:
                w = t * d.x + bt * d.y + n * d.z
                loc, _, _, dist = bvh.ray_cast(p + n * (size * 0.002), w, reach)
                if loc is not None:
                    hit += 1.0 - (dist / reach) ** 2
            cache[key] = max(0.0, 1.0 - hit / samples * 1.15)
        result.append(cache[key])
    return result


def export(obj, path):
    me = obj.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.triangulate(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    ao = ambient_occlusion(obj)
    mat = me.attributes['mat'].data
    glow = me.attributes['glow'].data
    normals = me.corner_normals
    verts, index, lookup = [], [], {}
    for poly in me.polygons:
        for li in poly.loop_indices:
            loop = me.loops[li]
            co = me.vertices[loop.vertex_index].co
            n = normals[li].vector
            # Blender (x, y, z) with Z up -> Minecraft (x, z, -y) with Y up.
            rec = (round(co.x, 5), round(co.z, 5), round(-co.y, 5),
                   int(round(max(0.0, min(1.0, ao[li])) * 255)), int(round(glow[poly.index].value * 255)),
                   mat[poly.index].value,
                   int(round(n.x * 127)), int(round(n.z * 127)), int(round(-n.y * 127)))
            if rec not in lookup:
                lookup[rec] = len(verts)
                verts.append(rec)
            index.append(lookup[rec])
    with open(path, 'wb') as f:
        f.write(b'SSM1' + struct.pack('<ii', len(verts), len(index)))
        for x, y, z, a, g, m, nx, ny, nz in verts:
            f.write(struct.pack('<fffffBBBBbbbb', x, y, z, 0.0, 0.0, a, g, m, 255, nx, ny, nz, 0))
        f.write(struct.pack('<%dI' % len(index), *index))
    print('%-10s %6d vertices %6d triangles  %s' % (obj.name, len(verts), len(index) // 3, path))


def preview(objs, directory):
    """Cycles renders using the exported attributes, for checking the shapes."""
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 48
    scene.cycles.device = 'CPU'
    scene.render.resolution_x, scene.render.resolution_y = 640, 400
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs[0].default_value = (0.02, 0.025, 0.04, 1)
    scene.world = world
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 4.0
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(40))
    scene.collection.objects.link(sun)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    palette = {HULL: (0.05, 0.05, 0.055), FIN: (0.06, 0.055, 0.05), NOSE: (0.08, 0.07, 0.065),
               FRAME: (0.07, 0.07, 0.075), PANEL: (0.4, 0.15, 0.03), ROCK: (0.22, 0.19, 0.16),
               FOIL: (0.6, 0.45, 0.12), SOLAR: (0.03, 0.05, 0.12), DISH: (0.7, 0.7, 0.7)}
    for obj in objs:
        for other in objs:
            other.hide_render = other is not obj
        m = bpy.data.materials.new(obj.name)
        m.use_nodes = True
        nt = m.node_tree
        bsdf = nt.nodes['Principled BSDF']
        attr_mat = nt.nodes.new('ShaderNodeAttribute')
        attr_mat.attribute_name = 'mat'
        attr_glow = nt.nodes.new('ShaderNodeAttribute')
        attr_glow.attribute_name = 'glow'
        ramp = nt.nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.interpolation = 'CONSTANT'
        els = ramp.color_ramp.elements
        els[0].position, els[0].color = 0.0, (*palette[0], 1)
        els[1].position, els[1].color = 1.0 / 9, (*palette[1], 1)
        for k in range(2, 9):
            e = els.new(k / 9)
            e.color = (*palette[k], 1)
        scale = nt.nodes.new('ShaderNodeMath')
        scale.operation = 'DIVIDE'
        scale.inputs[1].default_value = 9.0
        nt.links.new(attr_mat.outputs['Fac'], scale.inputs[0])
        nt.links.new(scale.outputs[0], ramp.inputs[0])
        nt.links.new(ramp.outputs[0], bsdf.inputs['Base Color'])
        bsdf.inputs['Metallic'].default_value = 0.6
        bsdf.inputs['Roughness'].default_value = 0.45
        bsdf.inputs['Emission Color'].default_value = (1.0, 0.45, 0.1, 1)
        nt.links.new(attr_glow.outputs['Fac'], bsdf.inputs['Emission Strength'])
        obj.data.materials.clear()
        obj.data.materials.append(m)
        size = max(obj.dimensions)
        cam.location = Vector((size * 0.9, -size * 0.75, size * 0.55))
        cam.rotation_euler = (Vector((0, 0, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
        cam.data.lens = 45
        scene.render.filepath = os.path.join(directory, obj.name + '.png')
        bpy.ops.render.render(write_still=True)


def main():
    reset()
    os.makedirs(OUT, exist_ok=True)
    objs = [build_round(), build_coil(), build_relay()] + [build_asteroid(i) for i in range(4)]
    for obj in objs:
        export(obj, os.path.join(OUT, obj.name + '.ssm'))
    if '--preview' in sys.argv:
        preview(objs, sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
