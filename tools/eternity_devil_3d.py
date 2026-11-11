"""The Eternity Devil's 3D look, phase A: builds the five meshes and renders the offline previews.

    python tools/eternity_devil_3d.py shaft  [tag]    # shaft floor: build + previews (tag = pass name, e.g. pass1)
    python tools/eternity_devil_3d.py avatar [tag]
    python tools/eternity_devil_3d.py core|head|growing [tag]
    python tools/eternity_devil_3d.py build           # write all the meshes + json to the game's resources
    python tools/eternity_devil_3d.py clips           # the turntable clips (shaft floor, avatar), parts moving
    python tools/eternity_devil_3d.py sheet           # the phone sheet _3d_small.png

Previews go to <main checkout>/work/eternity_look_3d/ (git-ignored). Assets go to OUT_ASSETS, under new names so they do not
collide with v5's eternity_devil_mesh.bin.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_3d_kit as K  # noqa: E402
import eternity_3d_scenes as SCN  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..'))
MAIN = os.path.abspath(os.path.join(ROOT, '..', '..', '..')) if '.claude' in ROOT else ROOT
OUT = os.path.join(MAIN, 'work', 'eternity_look_3d')
OUT_ASSETS = os.path.join(ROOT, 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'models', 'entity')

# the game's look: a warm hotel lamp above, a dim red glow from the pit; the orange-red rim only on edges
LIGHTS = [((0.1, 1.0, 0.2), (1.0, 0.9, 0.76), 0.7), ((0.5, 0.3, -0.8), (0.8, 0.76, 0.72), 0.22)]
RIMS = [((0.8, 0.45, 0.0), (255, 118, 52), 0.85), ((-0.7, 0.4, 0.5), (255, 100, 46), 0.7), ((0.0, -1.0, 0.0), (210, 52, 24), 0.55)]


def faces_of(model, tag='devil'):
    return [K.Faces(g, pivot=m['pivot'], anim=m['anim'], tag=tag) for g, m in zip(model.grids, model.meta)]


def label(im, text, size=15):
    img = Image.fromarray(im)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, img.width, size + 6), fill=(0, 0, 0))
    d.text((4, 2), text, fill=(236, 230, 220), font=_font(size))
    return np.array(img)


def _font(size):
    from PIL import ImageFont
    for f in ('C:/Windows/Fonts/arialbd.ttf', 'C:/Windows/Fonts/arial.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def sheet(tiles, cols, pad=4, bg=(10, 8, 12)):
    h, w = tiles[0].shape[:2]
    rows = (len(tiles) + cols - 1) // cols
    out = np.zeros((rows * (h + pad) + pad, cols * (w + pad) + pad, 3), np.uint8)
    out[:] = bg
    for i, t in enumerate(tiles):
        r, c = divmod(i, cols)
        out[pad + r * (h + pad):pad + r * (h + pad) + h, pad + c * (w + pad):pad + c * (w + pad) + w] = t
    return out


def six_angles(items, centre, dist, size=(300, 300), fov=45, ambient=0.5, t=0.0, lights=LIGHTS, rims=RIMS, extras=None, ups=None,
               views=None):
    """Six views round `centre` (yaw, pitch in degrees) at distance `dist`."""
    views = views or [(0, 12), (60, 22), (120, 12), (180, 14), (250, 28), (0, 70)]
    tiles = []
    for yaw, pitch in views:
        a, p = math.radians(yaw), math.radians(pitch)
        pos = np.array(centre) + dist * np.array([math.cos(a) * math.cos(p), math.sin(p), -math.sin(a) * math.cos(p)])
        cam = K.Camera(pos, centre, fov=fov, size=size)
        im = K.render(items, cam, lights, rims, ambient=ambient, t_ticks=t)
        tiles.append(label(im, 'yaw %d  pitch %d' % (yaw, pitch), 12))
    return sheet(tiles, 3)


# ---------------------------------------------------------------- the shaft floor

def shaft_scene_items(model, shaft_top=17.5):
    items = faces_of(model, 'devil')
    walls = SCN.hotel_shaft(top=shaft_top, bottom=0.0, open_at=None)
    items += [K.Faces(g, tag='hotel') for g in walls]
    return items


def shaft_views(model, tag):
    os.makedirs(OUT, exist_ok=True)
    items = shaft_scene_items(model)
    shade = SCN.depth_shade(top=11.0, floor_y=1.2)
    out = {}
    # the ledge view: the player stands on a ledge about 6.5 blocks above the mass, looking down, the doorway at the bottom edge
    cam = K.Camera((0.0, 6.6, -0.45), (0.0, 1.1, 0.08), up=(0, 0, 1), fov=70, size=(360, 640))
    im = K.render(items, cam, LIGHTS, RIMS, ambient=0.5, t_ticks=0, shade_fn=shade)
    K.save(im, os.path.join(OUT, 'shaft_%s.png' % tag), 3)
    out['ledge'] = im
    # from the top of a 16-block shaft, through a doorway
    cam = K.Camera((0.0, 15.6, -1.3), (0.0, 1.5, 0.3), up=(0, 0, 1), fov=70, size=(360, 640))
    im2 = K.render(items, cam, LIGHTS, RIMS, ambient=0.5, t_ticks=0, shade_fn=shade)
    K.save(im2, os.path.join(OUT, 'shaft_top_%s.png' % tag), 3)
    out['top'] = im2
    # a player about 12 blocks over the mass, looking steeply down (360x640)
    cam = K.Camera((0.0, 13.2, -0.9), (0.0, 1.2, 0.1), up=(0, 0, 1), fov=60, size=(360, 640))
    im3 = K.render(items, cam, LIGHTS, RIMS, ambient=0.5, t_ticks=0, shade_fn=shade)
    K.save(im3, os.path.join(OUT, 'shaft_over12_%s.png' % tag), 2)
    out['over12'] = im3
    # six angles of the mass on its own
    sx = six_angles(faces_of(model), (0.0, 1.7, 0.0), 6.0, fov=48, views=[(0, 60), (40, 28), (130, 20), (210, 16), (290, 35), (0, 88)])
    K.save(sx, os.path.join(OUT, 'shaft_angles_%s.png' % tag), 2)
    out['angles'] = sx
    return out


# ---------------------------------------------------------------- the avatar

def corridor_shade(p, tag):
    if tag != 'hotel':
        return np.ones(len(p))
    return 1.3 * (0.3 + 0.7 * np.clip(p[:, 0] / 9.0, 0, 1))


def avatar_views(model, tag):
    os.makedirs(OUT, exist_ok=True)
    items = faces_of(model, 'devil')
    items += [K.Faces(g, tag='hotel') for g in SCN.corridor(length=12.0, x0=0.0)]
    out = {}
    lights = [((0.6, 0.9, 0.0), (1.0, 0.88, 0.72), 0.55), ((0.9, 0.2, 0.0), (0.9, 0.78, 0.66), 0.3)]
    rims = [((0.2, 0.2, 1.0), (255, 110, 48), 1.0), ((0.2, 0.2, -1.0), (255, 100, 44), 1.0), ((0.3, 1.0, 0.0), (255, 128, 60), 0.5)]
    cam = K.Camera((8.6, 1.62, 0.0), (0.0, 2.55, 0.0), fov=62, size=(360, 640))
    im = K.render(items, cam, lights, rims, ambient=0.42, shade_fn=corridor_shade, rim_gain=1.0)
    K.save(im, os.path.join(OUT, 'avatar_%s.png' % tag), 3)
    out['corridor'] = im
    sx = six_angles(faces_of(model), (1.3, 2.5, 0.0), 11.0, fov=50, lights=lights, rims=rims, ambient=0.42,
                    views=[(0, 8), (35, 15), (75, 10), (-40, 25), (0, 62), (140, 20)])
    K.save(sx, os.path.join(OUT, 'avatar_angles_%s.png' % tag), 2)
    out['angles'] = sx
    return out


# ---------------------------------------------------------------- core, head, growing mass: 6 angles each

SMALL = {
    'core': ('eternity_3d_core', 'build', (0.0, 0.0, 0.0), 3.4, 38, 0.5),
    'head': ('eternity_3d_stages', 'build_head', (0.0, 1.1, 0.0), 6.2, 42, 0.5),
    'growing': ('eternity_3d_stages', 'build_growing', (0.5, 1.3, 0.0), 7.6, 44, 0.5),
}


def small_views(name, model, tag):
    mod, fn, centre, dist, fov, amb = SMALL[name]
    out = {}
    lights = [((0.5, 0.8, 0.3), (1.0, 0.9, 0.78), 0.6), ((0.8, 0.0, -0.5), (0.8, 0.74, 0.7), 0.3)]
    rims = [((0.2, 0.2, 1.0), (255, 110, 48), 0.95), ((0.2, 0.2, -1.0), (255, 100, 44), 0.95), ((0.3, 1.0, 0.0), (255, 128, 60), 0.5),
            ((0, -1, 0), (200, 40, 20), 0.4)]
    sx = six_angles(faces_of(model), centre, dist, size=(300, 380), fov=fov, ambient=amb, lights=lights, rims=rims,
                    views=[(0, 6), (40, 14), (-40, 14), (100, 10), (180, 12), (0, 55)])
    K.save(sx, os.path.join(OUT, '%s_angles_%s.png' % (name, tag)), 2)
    out['angles'] = sx
    # a front view with the faces lit as the game would (for the sheet)
    cam = K.Camera(np.array(centre) + np.array([dist * 0.9, 0.05 * dist, 0.0]), centre, fov=fov, size=(360, 360))
    out['front'] = K.render(faces_of(model), cam, lights, rims, ambient=amb)
    K.save(out['front'], os.path.join(OUT, '%s_front_%s.png' % (name, tag)), 2)
    return out


# ---------------------------------------------------------------- build, clips, sheet

BUILDS = [
    ('shaft', 'eternity_3d_shaft', 'build', 'eternity_shaft_mesh.bin', 'eternity_shaft_parts.json',
     'The shaft floor: the mass at the bottom of the tilted corridor. Made by tools/eternity_devil_3d.py (eternity_3d_shaft.py).'),
    ('avatar', 'eternity_3d_avatar', 'build', 'eternity_avatar_mesh.bin', 'eternity_avatar_parts.json',
     'The corridor-wall avatar. Made by tools/eternity_devil_3d.py (eternity_3d_avatar.py).'),
    ('core', 'eternity_3d_room', 'build_core16', 'eternity_core_mesh.bin', 'eternity_core_parts.json',
     'The core, a figure 8, at 1/16 block like the room. Made by tools/eternity_devil_3d.py (eternity_3d_core.py, eternity_3d_room.build_core16).'),
    ('room', 'eternity_3d_room', 'build_for_game', 'eternity_room_mesh.bin', 'eternity_room_parts.json',
     'The arena inside the devil: walls, floor, ceiling, throat, the mouth hollow, collars, stumps, limbs. Made by tools/eternity_devil_3d.py (eternity_3d_room.py).'),
    ('head', 'eternity_3d_stages', 'build_head', 'eternity_head_mesh.bin', 'eternity_head_parts.json',
     'The lone head, stage 1. Made by tools/eternity_devil_3d.py (eternity_3d_stages.py).'),
    ('growing', 'eternity_3d_stages', 'build_growing', 'eternity_growing_mesh.bin', 'eternity_growing_parts.json',
     'The growing mass, stage 2. Made by tools/eternity_devil_3d.py (eternity_3d_stages.py).'),
]


def build_model(name):
    import importlib
    for (n, mod, fn, *_rest) in BUILDS:
        if n == name:
            return getattr(importlib.import_module(mod), fn)()
    raise KeyError(name)


def build_all():
    rows = []
    for (n, mod, fn, mesh, parts, note) in BUILDS:
        model = build_model(n)
        q, counts = model.write(OUT_ASSETS, mesh, parts, note=note)
        rows.append((n, mesh, len(model.grids), q, q * 4, sum(g.n for g in model.grids), os.path.getsize(os.path.join(OUT_ASSETS, mesh))))
    print('%-8s %-28s %6s %8s %9s %8s %9s' % ('model', 'mesh', 'parts', 'quads', 'vertices', 'voxels', 'bytes'))
    for r in rows:
        print('%-8s %-28s %6d %8d %9d %8d %9d' % r)


def clip(model, name, frames=72, size=(270, 360), centre=(0, 1.5, 0), cam_fn=None, swing=None, k=3, fps=20, lights=LIGHTS, rims=RIMS,
         ambient=0.5, extra_items=None, shade_fn=None):
    import subprocess
    import shutil
    d = os.path.join(OUT, '_frames_' + name)
    os.makedirs(d, exist_ok=True)
    items = faces_of(model) + (extra_items or [])
    c = np.array(centre, float)
    for i in range(frames):
        t = i * 2.2
        yaw = (swing * math.sin(2 * math.pi * i / frames)) if swing else 360.0 * i / frames
        R = K.rotmat((0, 1, 0), yaw)
        ex = {'devil': (R, c - R @ c)}
        cam = cam_fn(i, frames, size)
        im = K.render(items, cam, lights, rims, ambient=ambient, t_ticks=t, extra=ex, shade_fn=shade_fn)
        K.save(im, os.path.join(d, 'f%03d.png' % i), k)
    out = os.path.join(OUT, name + '.mp4')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', os.path.join(d, 'f%03d.png'), '-pix_fmt', 'yuv420p',
                    '-vf', 'pad=ceil(iw/2)*2:ceil(ih/2)*2', out], check=True)
    shutil.rmtree(d, ignore_errors=True)
    return out


def clips():
    m = build_model('shaft')
    clip(m, 'turntable_shaft', centre=(0, 1.6, 0),
         cam_fn=lambda i, n, s: K.Camera((7.4, 5.4, 0.0), (0, 1.5, 0), fov=46, size=s))
    a = build_model('avatar')
    lights = [((0.6, 0.9, 0.0), (1.0, 0.88, 0.72), 0.55), ((0.9, 0.2, 0.0), (0.9, 0.78, 0.66), 0.3)]
    rims = [((0.2, 0.2, 1.0), (255, 110, 48), 1.0), ((0.2, 0.2, -1.0), (255, 100, 44), 1.0), ((0.3, 1.0, 0.0), (255, 128, 60), 0.5)]
    clip(a, 'turntable_avatar', centre=(1.3, 2.5, 0.0), swing=62.0, size=(270, 360), lights=lights, rims=rims, ambient=0.42,
         cam_fn=lambda i, n, s: K.Camera((11.5, 2.2, 0.0), (1.3, 2.5, 0.0), fov=44, size=s))


def final_sheet(shaft_tag='pass5', avatar_tag='pass2', small_tag='pass2'):
    from PIL import Image as I
    def ld(n, w=None):
        im = I.open(os.path.join(OUT, n)).convert('RGB')
        if w:
            im = im.resize((w, int(im.height * w / im.width)), I.LANCZOS)
        return im
    W = 1200
    canvas = I.new('RGB', (W, 1180), (10, 8, 12))
    dr = ImageDraw.Draw(canvas)
    f = _font(15)
    dr.text((10, 6), 'The Eternity Devil in 3D (phase A): shaft floor, corridor-wall avatar, head, growing mass, core', fill=(236, 230, 220), font=f)
    hero = ld('shaft_%s.png' % shaft_tag, 560)
    canvas.paste(hero, (6, 30))
    x0 = 576
    av = ld('avatar_%s.png' % avatar_tag, 300)
    canvas.paste(av, (x0, 50))
    dr.text((x0, 32), 'corridor-wall avatar', fill=(220, 210, 200), font=f)
    hd = ld('head_front_%s.png' % small_tag, 300)
    canvas.paste(hd, (x0 + 310, 50))
    dr.text((x0 + 310, 32), 'the lone head', fill=(220, 210, 200), font=f)
    gr = ld('growing_front_%s.png' % small_tag, 300)
    canvas.paste(gr, (x0 + 310, 360))
    dr.text((x0 + 310, 342), 'the growing mass', fill=(220, 210, 200), font=f)
    co = ld('core_front_%s.png' % small_tag, 420)
    canvas.paste(co, (x0, 610))
    dr.text((x0, 592), 'the core', fill=(220, 210, 200), font=f)
    top = ld('shaft_top_%s.png' % shaft_tag, 190)
    canvas.paste(top, (x0 + 430, 610))
    dr.text((x0 + 430, 592), 'from the shaft top', fill=(220, 210, 200), font=f)
    canvas.save(os.path.join(OUT, '_3d_small.png'))
    # the shaft beside the concept it must match
    c = ld('../eternity_look_v9/shaft_pass3.png', 420) if os.path.exists(os.path.join(OUT, '..', 'eternity_look_v9', 'shaft_pass3.png')) else None
    if c is not None:
        mine = ld('shaft_%s.png' % shaft_tag, 420)
        cv = I.new('RGB', (860, mine.height + 30), (10, 8, 12))
        d2 = ImageDraw.Draw(cv)
        d2.text((6, 6), 'concept (v9 pass 3)', fill=(236, 230, 220), font=f)
        d2.text((436, 6), '3D mesh (%s)' % shaft_tag, fill=(236, 230, 220), font=f)
        cv.paste(c, (6, 28))
        cv.paste(mine, (436, 28))
        cv.save(os.path.join(OUT, '_shaft_vs_concept_small.png'))


def phone(im, width=540):
    img = Image.fromarray(im)
    h = int(img.height * width / img.width)
    return np.array(img.resize((width, h), Image.LANCZOS))


def main():
    args = sys.argv[1:] or ['shaft']
    cmd = args[0]
    tag = args[1] if len(args) > 1 else 'pass1'
    if cmd == 'shaft':
        import eternity_3d_shaft as SH
        model = SH.build()
        print('shaft: %d parts, %d voxels' % (len(model.grids), sum(g.n for g in model.grids)))
        out = shaft_views(model, tag)
        K.save(phone(out['ledge'], 360), os.path.join(OUT, '_phone_shaft_%s.png' % tag))
    if cmd == 'build':
        build_all()
    if cmd == 'sheet':
        final_sheet()
    if cmd == 'clips':
        clips()
    if cmd in SMALL:
        import importlib
        mod, fn = SMALL[cmd][0], SMALL[cmd][1]
        model = getattr(importlib.import_module(mod), fn)()
        print('%s: %d parts, %d voxels' % (cmd, len(model.grids), sum(g.n for g in model.grids)))
        small_views(cmd, model, tag)
    if cmd == 'avatar':
        import eternity_3d_avatar as AV
        model = AV.build()
        print('avatar: %d parts, %d voxels' % (len(model.grids), sum(g.n for g in model.grids)))
        out = avatar_views(model, tag)
        K.save(phone(out['corridor'], 360), os.path.join(OUT, '_phone_avatar_%s.png' % tag))


if __name__ == '__main__':
    main()
