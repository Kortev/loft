"""The Tomato Devil's horror short (plan 3.4c): cut, graded and scored from the cinematic take's frames.

    ./gradlew runSelftest -Pselftest=tomato -Ptarget=cinematic -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 ...
    py -3.12 tools/tomato_creepy.py            # build/selftest/capture -> <main checkout>/work/tomato/creepy.mp4 and its sheets
    py -3.12 tools/tomato_creepy.py sheet      # only the stills (creepy_sheet.jpg, grade_before_after.jpg) from the cut

The take (TomatoCinematic) writes cinematic_marks.json beside its video: each shot's first and last frame. CUT below picks the part of each
shot that goes in. Everything after the game is done here, on the frames, with numpy/PIL and ffmpeg:

  the grade   cold and drained: every colour but red pulled toward grey, greens and blues most; the blacks lifted a little and teal; the red of
              the devil and its juice kept strong (it is the only colour in the frame); a gentle S-curve, the whole a little darker.
  effects     a soft glow on what is bright (the eyeshine, the lanterns, the moon); a dark vignette; a slight colour fringe toward the edges;
              film grain; a slow focus breath on the close-ups; a few frames of flicker on the lunge and a white flash on the hit.
  sound       synthesised only (no recordings, no music): a low drone, the night's crickets (they stop when it comes), Pochita's growl, a heartbeat
              that stops dead when the eye finds you, the hands' patter (placed where the game played its steps), the wet breath, one hard
              hit on the cut to black, a ringing after it.
"""
import json
import math
import os
import subprocess
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..'))
MAIN = os.path.abspath(os.path.join(ROOT, '..', '..', '..')) if '.claude' in ROOT else ROOT
CAPTURE = os.path.join(ROOT, 'build', 'selftest', 'capture')
OUT = os.path.join(MAIN, 'work', 'tomato')
TMP = os.path.join(ROOT, 'build', 'creepy')
# Every frame of every shot's take (not only what the short uses), ungraded and graded, kept for later cuts (the teaser): <n>.jpg by the
# capture's frame number; the sound's stems beside them.
FRAMES = os.path.join(MAIN, 'work', 'tomato', 'creepy_frames')
W, H = 1080, 1920
FPS = 30

# The cut: (shot, seconds into the shot where it starts, seconds long, close-up). A black beat goes before shot 8, the title after it.
CUT = [
    (1, 1.0, 3.8, False),
    (2, 1.0, 4.0, False),
    (3, 1.4, 4.4, False),
    (4, 3.0, 4.6, False),
    (5, 1.0, 5.0, False),
    (6, 1.2, 5.0, True),
    (7, 0.6, 4.4, True),
    (8, 0.2, 2.84, False),
]
# The grade's own settings per shot: exposure, and a crush of the darks (shot 2: the far body sinks into the night, the eyes stay).
SHOT_GRADE = {1: {'exposure': 0.9}, 2: {'exposure': 0.95, 'crush': 0.24}, 3: {'exposure': 0.82}, 4: {'exposure': 0.95}, 5: {'exposure': 0.88},
              6: {'exposure': 0.72}, 7: {'exposure': 0.72}, 8: {'exposure': 0.9}}
CLOSE = {6, 7}
BLACK = 0.9          # the beat of black before the last shot
TITLE = 2.6          # the title card after it
TITLE_TEXT = 'The Tomato Devil'


# ---------------------------------------------------------------- the grade

def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


_VIG = {}


def vignette():
    if 'v' not in _VIG:
        y, x = np.mgrid[0:H, 0:W].astype(np.float32)
        dx = (x - W / 2) / (W / 2)
        dy = (y - H / 2) / (H / 2)
        r = np.sqrt(dx * dx * 1.0 + dy * dy * 0.75)
        _VIG['v'] = (1.0 - 0.62 * smoothstep(0.45, 1.25, r))[..., None].astype(np.float32)
    return _VIG['v']


def fringe(img, amount):
    """A colour fringe toward the edges: red scaled out a little, blue in (about the middle)."""
    r, g, b = img.split()

    def scale(ch, k):
        w2, h2 = int(round(W * k)), int(round(H * k))
        s = ch.resize((w2, h2), Image.BILINEAR)
        if k >= 1:
            l, t = (w2 - W) // 2, (h2 - H) // 2
            return s.crop((l, t, l + W, t + H))
        out = ch.copy()
        out.paste(s, ((W - w2) // 2, (H - h2) // 2))
        return out
    return Image.merge('RGB', (scale(r, 1.0 + amount), g, scale(b, 1.0 - amount * 0.6)))


def grade(frame, index, info):
    """One frame (H x W x 3 uint8) graded; info: dict of per-frame extras (focus blur, flicker, flash, seed)."""
    rng = np.random.default_rng(info['seed'])
    img = Image.fromarray(frame)
    if info.get('blur', 0.0) > 0.05:
        img = img.filter(ImageFilter.GaussianBlur(info['blur']))
    if info.get('zoom', 1.0) != 1.0:
        k = info['zoom']
        w2, h2 = int(W * k), int(H * k)
        img = img.resize((w2, h2), Image.BICUBIC).crop(((w2 - W) // 2, (h2 - H) // 2, (w2 - W) // 2 + W, (h2 - H) // 2 + H))
    x = np.asarray(img).astype(np.float32) / 255.0
    r, g, b = x[..., 0], x[..., 1], x[..., 2]
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    mx = np.maximum(g, b)
    # How red a pixel is: red well over green and blue (the devil, its juice; a lantern's orange only partly).
    red = smoothstep(0.18, 0.55, (r - mx) / (r + 0.04)) * smoothstep(0.03, 0.12, r)
    red = red[..., None]
    # Everything drained toward a cold grey; the red kept, a little deeper.
    grey = y[..., None]
    drained = grey + (x - grey) * 0.22
    drained = drained * np.array([0.93, 1.0, 1.06], np.float32)
    deep = x * np.array([1.08, 0.86, 0.86], np.float32)
    deep = grey + (deep - grey) * 1.15
    x = drained * (1.0 - red) + deep * red
    # Exposure and an S-curve (a little darker overall), the blacks lifted and teal.
    x = np.clip(x * info.get('exposure', 0.9), 0, 1)
    if info.get('crush', 0.0) > 0:
        c = info['crush']
        x = np.clip((x - c) / (1.0 - c), 0, 1) ** 0.9
    x = x * x * (3.0 - 2.0 * x) * 0.55 + x * 0.45
    lift = np.array([0.020, 0.036, 0.042], np.float32)
    x = lift + x * (1.0 - lift)
    # The glow: what is bright, blurred wide and added back.
    yy = 0.2126 * x[..., 0] + 0.7152 * x[..., 1] + 0.0722 * x[..., 2]
    hi = (smoothstep(0.42, 0.85, yy)[..., None] * x)
    small = Image.fromarray((np.clip(hi, 0, 1) * 255).astype(np.uint8)).resize((W // 6, H // 6), Image.BILINEAR)
    glow = np.asarray(small.filter(ImageFilter.GaussianBlur(7)).resize((W, H), Image.BILINEAR)).astype(np.float32) / 255.0
    glow2 = np.asarray(small.filter(ImageFilter.GaussianBlur(2)).resize((W, H), Image.BILINEAR)).astype(np.float32) / 255.0
    x = 1.0 - (1.0 - x) * (1.0 - glow * 0.75) * (1.0 - glow2 * 0.35)
    # The vignette.
    x = x * vignette()
    # Flicker and flash (the lunge).
    x = x * info.get('flicker', 1.0)
    if info.get('flash', 0.0) > 0:
        x = x + (1.0 - x) * info['flash']
    if info.get('fade', 1.0) < 1.0:
        x = x * info['fade']
    # The grain: monochrome, clumped a little (made at half size), strongest in the mids.
    gs = rng.standard_normal((H // 2, W // 2)).astype(np.float32)
    gi = Image.fromarray(np.clip(gs * 40 + 128, 0, 255).astype(np.uint8)).resize((W, H), Image.BILINEAR)
    gn = (np.asarray(gi).astype(np.float32) - 128.0) / 40.0
    yy = 0.2126 * x[..., 0] + 0.7152 * x[..., 1] + 0.0722 * x[..., 2]
    amt = 0.030 + 0.035 * (1.0 - np.abs(yy - 0.4) * 1.6).clip(0, 1)
    x = x + (gn * amt)[..., None]
    out = Image.fromarray((np.clip(x, 0, 1) * 255 + 0.5).astype(np.uint8))
    out = fringe(out, 0.0035)
    return np.asarray(out)


# ---------------------------------------------------------------- the cut

def marks():
    with open(os.path.join(CAPTURE, 'cinematic_marks.json')) as f:
        m = json.load(f)
    begin = {e['shot']: e['frame'] for e in m if e['edge'] == 'begin'}
    end = {e['shot']: e['frame'] for e in m if e['edge'] == 'end'}
    return begin, end


def base_info(shot, src):
    """The grade of a source frame of a shot (what the kept graded frames have): the shot's settings, and the focus breath on close-ups."""
    info = dict(SHOT_GRADE.get(shot, {}), seed=src, shot=shot)
    if shot in CLOSE:
        # the focus breathing: soft, sharp, soft again, slowly; the frame swelling with it
        ph = math.sin(2 * math.pi * (src / FPS) / 3.4)
        info['blur'] = max(0.0, 1.4 * ph)
        info['zoom'] = 1.0 + 0.008 * (0.5 + 0.5 * ph)
    return info


def plan():
    """Every frame of the film: (source frame or None for black/title, kind, info: the extras laid over the graded frame)."""
    begin, end = marks()
    frames = []
    t = 0
    for (shot, off, length, close) in CUT:
        if shot == 8:
            for k in range(int(round(BLACK * FPS))):
                frames.append((None, 'black', {'seed': 9000 + t, 'shot': 0}))
                t += 1
        f0 = begin[shot] + int(round(off * FPS))
        n = int(round(length * FPS))
        if f0 + n > end[shot]:
            n = end[shot] - f0
            print('shot %d cut short to %.2f s' % (shot, n / FPS))
        for k in range(n):
            info = {'shot': shot}
            if shot == 1 and k < 18:
                info['fade'] = k / 18.0
            if shot == 8:
                left = n - 1 - k
                if left < 9:
                    info['flicker'] = [1.0, 1.7, 0.5, 1.4, 0.3, 1.9, 0.6, 2.2, 1.0][8 - left]
                if left == 0:
                    info['flash'] = 0.85
            frames.append((f0 + k, 'film', info))
            t += 1
    for k in range(int(round(TITLE * FPS))):
        frames.append((None, 'title', {'seed': 5000 + k, 'shot': 9, 'k': k}))
    return frames


def frame_path(kind, src):
    return os.path.join(FRAMES, kind, '%05d.jpg' % src)


def _grade_job(job):
    src, shot, buf = job
    fr = np.frombuffer(buf, np.uint8).reshape(H, W, 3)
    Image.fromarray(fr).save(frame_path('ungraded', src), quality=94)
    Image.fromarray(grade(fr, src, base_info(shot, src))).save(frame_path('graded', src), quality=94)
    return src


def grade_all(only=None):
    """Every frame of every shot's take, ungraded and graded, into FRAMES (in batches: a frame is 6 MB)."""
    begin, end = marks()
    shot_of = {}
    for sh in begin:
        if only and sh not in only:
            continue
        for f in range(begin[sh], end[sh]):
            shot_of[f] = sh
    os.makedirs(os.path.join(FRAMES, 'ungraded'), exist_ok=True)
    os.makedirs(os.path.join(FRAMES, 'graded'), exist_ok=True)
    with open(os.path.join(FRAMES, 'shots.json'), 'w') as f:
        json.dump({'fps': FPS, 'shots': {str(sh): {'first': begin[sh], 'last': end[sh] - 1} for sh in sorted(begin)},
                   'events': [e for e in json.load(open(os.path.join(CAPTURE, 'cinematic_marks.json'))) if e['edge'] == 'event']}, f, indent=1)
    batch = []
    done = 0
    with Pool(8) as pool:
        for i, fr in read_frames(os.path.join(CAPTURE, 'video.mp4')):
            if i in shot_of:
                batch.append((i, shot_of[i], fr.tobytes()))
            if len(batch) >= 32:
                done += len(pool.map(_grade_job, batch))
                batch = []
                print('  graded %d/%d' % (done, len(shot_of)))
        if batch:
            pool.map(_grade_job, batch)


_FONT = {}


def title_frame(k):
    if 'img' not in _FONT:
        img = Image.new('RGB', (W, H), (0, 0, 0))
        d = ImageDraw.Draw(img)
        font = None
        for name in ('C:/Windows/Fonts/georgia.ttf', 'C:/Windows/Fonts/times.ttf', 'C:/Windows/Fonts/arial.ttf'):
            if os.path.exists(name):
                font = ImageFont.truetype(name, 92)
                break
        font = font or ImageFont.load_default()
        bb = d.textbbox((0, 0), TITLE_TEXT, font=font)
        d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], (H - (bb[3] - bb[1])) / 2 - bb[1]), TITLE_TEXT, font=font, fill=(236, 232, 226))
        _FONT['img'] = np.asarray(img)
    a = min(1.0, k / 10.0) * (1.0 if k < TITLE * FPS - 14 else max(0.0, (TITLE * FPS - k) / 14.0))
    # (the text breathes a touch, and once it jumps)
    jit = 0.88 if k in (23, 24) else 1.0
    return (_FONT['img'] * a * jit).astype(np.uint8)


def overlay(x, info):
    """The film's own extras over a graded frame: a fade in, the lunge's flicker and its flash."""
    if 'fade' not in info and 'flicker' not in info and 'flash' not in info:
        return x
    y = x.astype(np.float32) / 255.0
    y = y * info.get('flicker', 1.0) * info.get('fade', 1.0)
    if info.get('flash', 0.0) > 0:
        y = y + (1.0 - y) * info['flash']
    return (np.clip(y, 0, 1) * 255 + 0.5).astype(np.uint8)


def read_frames(path):
    """All the video's frames, one at a time (rgb24)."""
    p = subprocess.Popen(['ffmpeg', '-v', 'error', '-i', path, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], stdout=subprocess.PIPE)
    size = W * H * 3
    i = 0
    while True:
        buf = p.stdout.read(size)
        if len(buf) < size:
            break
        yield i, np.frombuffer(buf, np.uint8).reshape(H, W, 3)
        i += 1
    p.wait()


def render(frames):
    """The short, from the graded frames: to build/creepy/graded.mp4 (near lossless; the delivered file is encoded from it)."""
    os.makedirs(TMP, exist_ok=True)
    total = len(frames)
    print('%d frames, %.1f s' % (total, total / FPS))
    raw = os.path.join(TMP, 'graded.mp4')
    enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-',
                            '-c:v', 'libx264', '-preset', 'medium', '-crf', '12', '-pix_fmt', 'yuv420p', raw], stdin=subprocess.PIPE)
    for (src, kind, info) in frames:
        if kind == 'black':
            out = grade(np.zeros((H, W, 3), np.uint8), 0, dict(info, exposure=1.0))
        elif kind == 'title':
            out = grade(title_frame(info['k']), 0, dict(info, exposure=1.0))
        else:
            out = overlay(np.asarray(Image.open(frame_path('graded', src)).convert('RGB')), info)
        enc.stdin.write(out.tobytes())
    enc.stdin.close()
    enc.wait()


def sheet_sources(frames):
    """For each shot, a frame of its part of the film (source frame numbers)."""
    out = []
    for shot in range(1, 9):
        idx = [j for j, (src, kind, info) in enumerate(frames) if info.get('shot') == shot and src is not None]
        if idx:
            out.append(frames[idx[min(len(idx) - 1, int(len(idx) * SHEET_AT.get(shot, 0.6)))]][0])
    return out


SHEET_AT = {1: 0.8, 2: 0.6, 3: 0.12, 4: 0.6, 5: 0.75, 6: 0.55, 7: 0.85, 8: 0.85}


# ---------------------------------------------------------------- the sound

def sound(frames):
    sys.path.insert(0, HERE)
    import gen_chainsaw_sounds as cs  # noqa: E402
    import gen_sounds as g  # noqa: E402
    from gen_sounds import SR, bp, hp, lp, sat, white, curve  # noqa: E402
    g.rng = np.random.default_rng(3401)
    total = len(frames) / FPS
    n = int(total * SR) + SR
    t = np.arange(n) / SR
    stems = {k: np.zeros(n) for k in ('drone', 'night', 'pochita', 'heart', 'hands', 'breath', 'eye', 'hit')}
    stem = ['night']

    def place(x, at, gain=1.0):
        i = int(at * SR)
        k = min(len(x), n - i)
        if k > 0 and i >= 0:
            stems[stem[0]][i:i + k] += x[:k] * gain

    # When each shot starts and ends in the film.
    starts, ends = {}, {}
    for j, (src, kind, info) in enumerate(frames):
        s = info.get('shot')
        starts.setdefault(s, j / FPS)
        ends[s] = (j + 1) / FPS
    snap = starts[6] + SNAP_IN_SHOT6
    hit = ends[8] - 1.0 / FPS
    # The drone: two low tones beating, a tritone over them, rumble; rising toward the eye, gone at its snap, back under the mouth, gone in the black.
    drone = (np.sin(2 * np.pi * 41.2 * t) + 0.8 * np.sin(2 * np.pi * 43.1 * t + 1.0) + 0.25 * np.sin(2 * np.pi * 58.3 * t)
             + 0.12 * np.sin(2 * np.pi * 116.5 * t + 0.4 * np.sin(2 * np.pi * 0.07 * t)))
    drone = sat(drone * 0.5, 1.4) + lp(g.brown(n), 110.0) * 2.5
    whine = np.sin(2 * np.pi * (2960.0 + 25.0 * np.sin(2 * np.pi * 0.23 * t)) * t) * 0.025 + bp(white(n), 2500, 3600) * 0.01
    env = np.interp(t, [0, 1.5, starts[3], starts[5], snap - 0.05, snap, starts[7] - 0.4, starts[7] + 1.0, ends[7], ends[7] + 0.05, starts[8], hit, hit + 0.01, total],
                    [0, 0.35, 0.45, 0.6, 0.95, 0.0, 0.0, 0.4, 0.5, 0.0, 0.0, 1.0, 0.0, 0.0])
    wenv = np.interp(t, [0, starts[4], snap - 0.05, snap, starts[8], hit, hit + 0.01], [0, 0.2, 1.0, 0.0, 0.0, 1.2, 0.0])
    stems['drone'] += drone * env * 0.22 + whine * wenv
    # The night: wind, and crickets that stop as it comes near (in shot 3).
    wind = lp(g.pink(n), 500.0) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.11 * t)) * 0.05
    stems['night'] += wind * np.interp(t, [0, 1.0, snap - 0.1, snap, ends[7], ends[7] + 0.1], [0, 1, 1, 0.0, 0.0, 0.0])
    cr_stop = starts[3] + 1.2
    for c, (f0, period, pan) in enumerate(((4400.0, 0.71, 1.0), (4780.0, 0.93, 0.7))):
        at = 0.3 + c * 0.2
        while at < cr_stop:
            m = int(0.09 * SR)
            tt = np.arange(m) / SR
            pulses = (np.sin(2 * np.pi * 55.0 * tt) > 0.2) * np.sin(np.pi * tt / 0.09)
            chirp = np.sin(2 * np.pi * f0 * tt) * pulses * 0.035 * pan
            place(chirp, at, 1.0 if at < cr_stop - 0.8 else 0.4)
            at += period * (1.0 + 0.08 * np.sin(at * 3.1))
    stem[0] = 'pochita'
    # Pochita: he stops and growls at the dark, a small engine idling low and rough.
    gn = int(2.6 * SR)
    gt = np.arange(gn) / SR
    growl = cs.engine(gn, curve(gn, [(0.0, 14.0), (0.6, 19.0), (2.0, 17.0), (2.6, 12.0)]), curve(gn, [(0.0, 0.0), (0.3, 0.6), (2.1, 0.5), (2.6, 0.0)]),
                      body=(70.0, 900.0), buzz=0.5)
    growl += sat(np.sin(2 * np.pi * np.cumsum(curve(gn, [(0, 95.0), (1.0, 88.0), (2.6, 80.0)])) / SR) * 1.5, 2.0) * bp(white(gn), 80, 900) * 0.4 \
        * np.interp(gt, [0, 0.3, 2.2, 2.6], [0, 1, 0.8, 0])
    place(lp(growl, 1400.0), starts[1] + GROWL_IN_SHOT1, 0.55)
    stem[0] = 'heart'
    # The heartbeat: from the glints on, slow, quicker as it comes; it stops dead at the eye's snap.
    at = starts[2] + 0.4
    while at < snap - 0.25:
        k = (at - starts[2]) / max(0.1, snap - starts[2])
        bpm = 52 + 46 * k * k
        for dt, lv in ((0.0, 1.0), (0.26, 0.6)):
            m = int(0.25 * SR)
            tt = np.arange(m) / SR
            thump = np.sin(2 * np.pi * (52.0 - 18.0 * tt / 0.25) * tt) * np.exp(-tt / 0.07) + lp(white(m), 200.0) * np.exp(-tt / 0.02) * 0.5
            place(thump, at + dt, 0.42 * lv * (0.6 + 0.6 * k))
        at += 60.0 / bpm
    stem[0] = 'hands'
    # The hands: where the game played a step, a palm coming down and its fingers after it, one by one.
    steps = game_sounds('tomato_step')
    for shot in (3, 4, 5, 8):
        for (vt, gain) in steps:
            ft = film_time(frames, vt)
            if ft is None or frames_shot(frames, ft) != shot:
                continue
            lv = {3: 1.0, 4: 0.35, 5: 0.25, 8: 1.0}[shot] * max(0.3, gain)
            place(cs.tomato_step(), ft, 0.32 * lv)
            for q in range(4):
                m = int(0.03 * SR)
                tt = np.arange(m) / SR
                tap = bp(white(m), 700.0, 3200.0) * np.exp(-tt / 0.006) + cs.flesh(m, 0.03, 1.0) * 0.3
                place(tap, ft + 0.05 + q * (0.045 if shot == 3 else 0.02), 0.12 * lv * (1.0 - 0.15 * q))
    stem[0] = 'breath'
    # Its breath: through the glass at the window (muffled), close and wet at the mouth (slowed with the picture).
    br = cs.tomato_breath()
    place(lp(br, 900.0), starts[5] + 1.6, 0.22)
    # (cut off before the snap: after it, nothing)
    place(br[:int(1.6 * SR)] * np.linspace(1.0, 0.0, int(1.6 * SR)) ** 0.5, starts[6] + 0.2, 0.18)
    place(br[:int(1.6 * SR)], starts[7] - 0.1, 0.45)
    slow = np.interp(np.arange(0, len(br) * 1.0, 0.55), np.arange(len(br)), br)
    slow = lp(slow, 2500.0)
    place(slow, starts[7] + MOUTH_OPENS_IN_SHOT7, 0.6)
    for k in range(10):
        place(cs.gulp(int(0.2 * SR), 0.0, 0.08, 80, 260, 0.6), starts[7] + MOUTH_OPENS_IN_SHOT7 + 0.3 + k * 0.28, 0.25)
    stem[0] = 'eye'
    # The eye: wet little sounds as it rolls; then, as it finds you, one soft click of teeth and nothing.
    for k, dt in enumerate((0.4, 1.1, 1.5, 2.2)):
        place(cs.gulp(int(0.15 * SR), 0.0, 0.05, 300 + 40 * k, 700, 0.5), starts[6] + dt, 0.12)
    place(cs.tomato_click(), snap + 0.02, 0.5)
    stem[0] = 'breath'
    # The black: one breath in, held.
    place(hp(cs.reversed_swell(int(0.9 * SR), 0.0, 0.85, 300.0, 3000.0, 0.6), 200.0), starts[8] - BLACK + 0.0, 0.25)
    stem[0] = 'hit'
    # The rush: hands slapping faster and louder, a swell into the hit.
    place(cs.reversed_swell(int(1.6 * SR), 0.0, 1.55, 120.0, 4200.0, 1.0), hit - 1.55, 0.55)
    # The hit: a deep boom, a crack, the slap of its hands; a ring after it under the title.
    m = int(3.0 * SR)
    tt = np.arange(m) / SR
    boom = cs.sub(m, [(0.0, 120.0), (0.05, 60.0), (0.5, 32.0), (3.0, 24.0)], 0.6, drive=3.0) * 1.6
    boom += lp(white(m), 3500.0) * np.exp(-tt / 0.03) * 2.0
    sl = cs.tomato_slap()[:m]
    boom[:len(sl)] += sl * 1.2
    place(sat(boom * 0.8, 1.6), hit, 0.9)
    ring = np.sin(2 * np.pi * 2650.0 * tt) * np.exp(-tt / 1.4) * 0.03
    place(ring, hit + 0.05, 1.0)
    mix = sum(stems.values())
    scale = 0.89 / max(1e-6, np.abs(mix).max())
    import wave

    def save(x, path):
        with wave.open(path, 'wb') as wv:
            wv.setnchannels(1)
            wv.setsampwidth(2)
            wv.setframerate(SR)
            wv.writeframes((np.clip(x[:int(total * SR)] * scale, -1, 1) * 32767).astype('<i2').tobytes())
    # the stems at the mix's own scale (they add up to it), for later cuts
    os.makedirs(os.path.join(FRAMES, 'stems'), exist_ok=True)
    for k, x in stems.items():
        save(x, os.path.join(FRAMES, 'stems', k + '.wav'))
    path = os.path.join(TMP, 'score.wav')
    save(mix, path)
    save(mix, os.path.join(FRAMES, 'stems', 'mix.wav'))
    return path


# Where, in the cut, things happen (seconds into the shot's part of the film): set from the take.
SNAP_IN_SHOT6 = 2.0
GROWL_IN_SHOT1 = 1.3
MOUTH_OPENS_IN_SHOT7 = 1.2


def game_sounds(name):
    try:
        with open(os.path.join(CAPTURE, 'sounds.json')) as f:
            log = json.load(f)
    except OSError:
        return []
    return [(e['time'], e['gain']) for e in log if name in e['file']]


def film_time(frames, video_seconds):
    """Where a moment of the take (video seconds) is in the film, or None if cut out."""
    src = int(round(video_seconds * FPS))
    for j, (s, kind, info) in enumerate(frames):
        if s == src:
            return j / FPS
    return None


def frames_shot(frames, ft):
    j = int(ft * FPS)
    return frames[j][2].get('shot') if 0 <= j < len(frames) else None


# ---------------------------------------------------------------- the sheets and the file

def sheets(frames):
    srcs = sheet_sources(frames)
    film_t = {}
    for j, (s_, kind, info) in enumerate(frames):
        if s_ is not None:
            film_t.setdefault(s_, j / FPS)
    tw, th = 300, 533
    cells = []
    for k, src in enumerate(srcs):
        gr = Image.open(frame_path('graded', src)).convert('RGB').resize((tw, th), Image.LANCZOS)
        un = Image.open(frame_path('ungraded', src)).convert('RGB')
        clean = gr.copy()
        d = ImageDraw.Draw(gr)
        d.rectangle((0, 0, tw, 24), fill=(0, 0, 0))
        d.text((6, 6), 'shot %d   %.1f s' % (k + 1, film_t[src]), fill=(255, 255, 255))
        cells.append((gr, un, clean))
    sheet = Image.new('RGB', (tw * 4, th * 2), (0, 0, 0))
    for k, (gr, un, clean) in enumerate(cells):
        sheet.paste(gr, ((k % 4) * tw, (k // 4) * th))
    sheet.save(os.path.join(OUT, 'creepy_sheet.jpg'), quality=88)
    # Before and after: three shots, the game's frame beside the graded one.
    bw, bh = 166, 296
    ba = Image.new('RGB', (bw * 6, bh + 24), (0, 0, 0))
    d = ImageDraw.Draw(ba)
    for i, k in enumerate((1, 3, 5)):
        gr, un, clean = cells[k]
        ba.paste(un.resize((bw, bh), Image.LANCZOS), (i * 2 * bw, 24))
        ba.paste(clean.resize((bw, bh), Image.LANCZOS), (i * 2 * bw + bw, 24))
        d.text((i * 2 * bw + 4, 6), 'shot %d: game' % (k + 1), fill=(255, 255, 255))
        d.text((i * 2 * bw + bw + 4, 6), 'graded', fill=(255, 255, 255))
    ba.save(os.path.join(OUT, 'grade_before_after.jpg'), quality=88)


def finish(score, frames, kbps=1850):
    raw = os.path.join(TMP, 'graded.mp4')
    out = os.path.join(OUT, 'creepy.mp4')
    common = ['-c:v', 'libx264', '-preset', 'slow', '-tune', 'grain', '-b:v', '%dk' % kbps, '-pix_fmt', 'yuv420p']
    log = os.path.join(TMP, 'pass')
    subprocess.check_call(['ffmpeg', '-v', 'error', '-y', '-i', raw] + common + ['-pass', '1', '-passlogfile', log, '-an', '-f', 'mp4', os.devnull])
    subprocess.check_call(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-i', score] + common + ['-pass', '2', '-passlogfile', log, '-c:a', 'aac', '-b:a', '128k',
                                                                                             '-shortest', '-movflags', '+faststart', out])
    print('%s: %.2f MB' % (out, os.path.getsize(out) / 1e6))


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'all'
    os.makedirs(OUT, exist_ok=True)
    fr = plan()
    if what in ('all', 'frames'):
        # (frames 2 6: only those shots' frames graded again)
        grade_all([int(a) for a in sys.argv[2:]] or None)
    if what in ('all', 'cut'):
        render(fr)
    if what in ('all', 'cut', 'sound'):
        sc = sound(fr)
    if what in ('all', 'cut', 'sheet'):
        sheets(fr)
    if what in ('all', 'cut'):
        finish(sc, fr)
