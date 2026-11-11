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
  sound       synthesised only (no recordings, no music), stereo, as stems (generator functions snd_*): night air and far crickets, a
              drone that creeps in, drops out twice and comes back lower and closer, a dissonant cluster rising to the eye's snap and cut
              dead, the hands (thud, fingers, drag) panned with the picture where the game played its steps, wet foley at your ear, the
              breath with a moan in it coming closer every shot, the sleeping villager, Pochita's growl and whimper, the lunge and the hit.
    py -3.12 tools/tomato_creepy.py teaser     # the teaser (teaser.mp4, teaser_sheet.jpg) from the kept frames
    py -3.12 tools/tomato_creepy.py resound    # only the sound again, and creepy.mp4 encoded with it (same picture)
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


def title_frame(k, length=None):
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
    L = (length or TITLE) * FPS
    a = min(1.0, k / 10.0) * (1.0 if k < L - 14 else max(0.0, (L - k) / 14.0))
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


# ---------------------------------------------------------------- the sound (second pass: stereo, dynamics, foley)
#
# Each element is made by a generator function below (mono, or stereo where it is wide by nature), so the good ones can become game sounds
# later; score() places them in the film, panned and given more or less room, as stereo stems.

def _snd():
    sys.path.insert(0, HERE)
    import gen_chainsaw_sounds as cs  # noqa: E402
    import gen_sounds as g  # noqa: E402
    return cs, g


def _env(m, attack, release):
    t = np.arange(m) / 44100.0
    e = np.minimum(1.0, t / max(attack, 1e-4))
    return e * np.clip((m / 44100.0 - t) / max(release, 1e-4), 0.0, 1.0)


def snd_night_air(seconds, rng):
    """Night air, wide: two slow, independent gusts of soft noise (stereo)."""
    cs, g = _snd()
    from gen_sounds import lp, bp
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    out = []
    for side in range(2):
        x = lp(g.pink(n), 900.0) * 0.6 + bp(g.white(n), 300.0, 2500.0) * 0.05
        x *= 0.55 + 0.45 * np.sin(2 * np.pi * (0.07 + 0.02 * side) * t + side * 1.7) ** 2
        out.append(x)
    return np.array(out) * 0.08


def snd_cricket(seconds, f0, period, rng):
    """One cricket far off: trills of three pulses."""
    n = int(seconds * 44100)
    x = np.zeros(n)
    at = rng.uniform(0.0, period)
    while at < seconds - 0.1:
        m = int(0.075 * 44100)
        tt = np.arange(m) / 44100.0
        pulses = (np.sin(2 * np.pi * 52.0 * tt) > 0.25) * np.sin(np.pi * tt / 0.075)
        c = np.sin(2 * np.pi * f0 * tt) * pulses
        i = int(at * 44100)
        x[i:i + m] += c[:max(0, n - i)]
        at += period * rng.uniform(0.85, 1.15)
    return x


def snd_drone(seconds, base=41.2):
    """The drone: two low tones beating, a tritone, and upper harmonics (saturated) so a phone's speaker still carries it."""
    cs, g = _snd()
    from gen_sounds import lp, bp, sat
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    low = np.sin(2 * np.pi * base * t) + 0.8 * np.sin(2 * np.pi * base * 1.046 * t + 1.0) + 0.3 * np.sin(2 * np.pi * base * 1.414 * t)
    grit = sat(low * 0.9, 2.5)   # its own harmonics at 120-400 Hz
    body = bp(grit, 110.0, 520.0) * 0.9 + lp(low, 120.0) * 0.6
    rumble = bp(g.brown(n), 90.0, 380.0) * 3.0 * (0.7 + 0.3 * np.sin(2 * np.pi * 0.13 * t))
    return body + rumble


def snd_cluster(seconds, rise=1.0):
    """A thin dissonant cluster of high tones, rising slowly (the tension), each tone with its own slow wobble."""
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    x = np.zeros(n)
    k = 1.0 + (rise - 1.0) * (t / seconds) ** 1.5
    for j, f in enumerate((1840.0, 1953.0, 2069.0, 2611.0, 2770.0)):
        ph = 2 * np.pi * np.cumsum(f * k * (1.0 + 0.004 * np.sin(2 * np.pi * (0.3 + 0.17 * j) * t))) / 44100.0
        x += np.sin(ph) * (0.6 + 0.4 * np.sin(2 * np.pi * (0.11 + 0.07 * j) * t + j)) ** 2
    return x / 5.0


def snd_hand(rng, big=False):
    """A hand coming down: a soft thud of a palm, the fingers after it one by one, a faint drag of skin on the ground."""
    cs, g = _snd()
    from gen_sounds import lp, bp, hp
    n = int(0.6 * 44100)
    x = np.zeros(n)
    m = int(0.12 * 44100)
    tt = np.arange(m) / 44100.0
    f = (95.0 if not big else 70.0) * rng.uniform(0.9, 1.1)
    thud = np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.03) + 0.5 * np.sin(2 * np.pi * f * 2.7 * tt) * np.exp(-tt / 0.015)
    thud += lp(g.white(m), 1200.0) * np.exp(-tt / 0.012) * 1.2
    x[:m] += thud
    at = 0.04 + rng.uniform(0.0, 0.02)
    for q in range(4):
        k = int(0.02 * 44100)
        tk = np.arange(k) / 44100.0
        tap = bp(g.white(k), 900.0, 4200.0) * np.exp(-tk / 0.004) + np.sin(2 * np.pi * rng.uniform(380, 520) * tk) * np.exp(-tk / 0.006) * 0.5
        i = int(at * 44100)
        x[i:i + k] += tap * (0.55 - 0.08 * q)
        at += rng.uniform(0.035, 0.07)
    k = int(0.22 * 44100)
    tk = np.arange(k) / 44100.0
    drag = bp(g.white(k), 1500.0, 6000.0) * np.sin(np.pi * tk / 0.22) ** 2 * (0.6 + 0.4 * np.sin(2 * np.pi * 31 * tk)) * 0.18
    i = int((at + 0.02) * 44100)
    x[i:i + k] += drag[:max(0, n - i)]
    x += cs.flesh(n, 0.08, 1.0) * 0.12
    return x


def snd_squelch(rng, level=1.0):
    """A tiny wet squelch: an eye rolling in its socket."""
    cs, g = _snd()
    from gen_sounds import bp
    n = int(0.18 * 44100)
    x = cs.gulp(n, 0.0, rng.uniform(0.04, 0.07), rng.uniform(250, 420), rng.uniform(600, 1100), 0.7)
    k = int(0.05 * 44100)
    tk = np.arange(k) / 44100.0
    x[:k] += bp(g.white(k), 1200.0, 5000.0) * np.exp(-tk / 0.01) * 0.3
    return x * level


def snd_click(rng):
    """Teeth: a blunt click."""
    cs, g = _snd()
    return cs.tomato_click()


def snd_lip_smack(rng):
    """The lips coming unstuck: a wet smack and a peel."""
    cs, g = _snd()
    from gen_sounds import bp, lp
    n = int(0.35 * 44100)
    t = np.arange(n) / 44100.0
    x = bp(g.white(n), 600.0, 3500.0) * np.exp(-t / 0.012) * 1.2
    x += lp(g.white(n), 500.0) * np.exp(-t / 0.03) * 0.6
    x += cs.flesh(n, 0.25, 1.8) * 0.5
    return x


def snd_strand(rng):
    """A strand of spit stretching and snapping: a thin wet tick, falling."""
    cs, g = _snd()
    from gen_sounds import bp
    n = int(0.08 * 44100)
    t = np.arange(n) / 44100.0
    f = rng.uniform(2200, 3600) * np.exp(-t / 0.02)
    x = np.sin(2 * np.pi * np.cumsum(f) / 44100.0) * np.exp(-t / 0.012)
    x += bp(g.white(n), 3000.0, 9000.0) * np.exp(-t / 0.004) * 0.6
    return x


def snd_breath(rng, seconds=3.4, moan=0.25, rasp=1.0):
    """Its breath, its voice: a slow ragged wet breath in and out, a faint almost-human moan on the out-breath (formants, no words)."""
    cs, g = _snd()
    from gen_sounds import bp, lp, sat
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    tin = seconds * 0.38
    inhale = np.clip(np.sin(np.pi * np.clip(t / tin, 0, 1)), 0, 1) ** 1.4
    exhale = np.clip(np.sin(np.pi * np.clip((t - tin - 0.25) / (seconds - tin - 0.3), 0, 1)), 0, 1) ** 1.1
    # ragged: the air catches, a few times
    catch = 1.0 - 0.6 * (np.sin(2 * np.pi * 7.3 * t) > 0.82) * (np.sin(2 * np.pi * 0.9 * t) > 0.2)
    air = (bp(g.white(n), 400.0, 2600.0) * inhale * 0.9 + bp(g.white(n), 200.0, 1500.0) * exhale * 0.8) * catch
    x = air * rasp
    # the moan: a low buzz through two formants (an "oh" sliding toward "uh"), riding the out-breath
    f0 = 92.0 * (1.0 + 0.06 * np.sin(2 * np.pi * 0.4 * t)) * (1.0 + 0.01 * np.sin(2 * np.pi * 5.3 * t))
    ph = 2 * np.pi * np.cumsum(f0) / 44100.0
    buzz = sat(np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.4 * np.sin(3 * ph) + 0.3 * np.sin(4 * ph), 1.5)
    voice = bp(buzz, 380.0, 650.0) * 1.0 + bp(buzz, 780.0, 1100.0) * 0.6 + bp(buzz, 2300.0, 2800.0) * 0.15
    x += voice * exhale ** 1.5 * moan
    # the wet: spit crackling, thicker as it goes out; gulps of juice
    for j in range(60):
        at = rng.uniform(0.05, seconds - 0.1)
        k = int(0.008 * 44100)
        i = int(at * 44100)
        w = 0.35 + 0.65 * float(np.interp(at, t, exhale + inhale))
        x[i:i + k] += bp(g.white(k), 1500.0, 7000.0) * np.exp(-np.arange(k) / 44100.0 / 0.0015) * rng.uniform(0.1, 0.5) * w
    for j in range(4):
        x += cs.gulp(n, rng.uniform(tin + 0.3, seconds - 0.3), 0.06, 80, 220, 0.3)
    x += lp(g.brown(n), 200.0) * exhale * 0.8
    return x


def snd_sleeper(seconds, rng):
    """The villager asleep: slow soft breaths through the nose, a little snore on each."""
    cs, g = _snd()
    from gen_sounds import bp
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    cyc = (t % 3.6) / 3.6
    env = np.sin(np.pi * np.clip(cyc / 0.45, 0, 1)) ** 2 * 0.8 + np.sin(np.pi * np.clip((cyc - 0.5) / 0.4, 0, 1)) ** 2
    snore = bp(np.sign(np.sin(2 * np.pi * 38.0 * t)) * 0.3 + g.white(n) * 0.2, 200.0, 900.0) * np.sin(np.pi * np.clip((cyc - 0.5) / 0.4, 0, 1)) ** 4
    return (bp(g.white(n), 300.0, 2000.0) * env * 0.5 + snore * 0.5)


def snd_growl(rng):
    """Pochita growling at the dark: a small engine idling low and rough, a growl in it."""
    cs, g = _snd()
    from gen_sounds import SR, bp, lp, sat, curve
    gn = int(2.0 * SR)
    gt = np.arange(gn) / SR
    x = cs.engine(gn, curve(gn, [(0.0, 14.0), (0.6, 20.0), (1.6, 17.0), (2.0, 12.0)]), curve(gn, [(0.0, 0.0), (0.25, 0.6), (1.6, 0.5), (2.0, 0.0)]),
                  body=(110.0, 1400.0), buzz=0.5)
    x += sat(np.sin(2 * np.pi * np.cumsum(curve(gn, [(0, 150.0), (1.0, 140.0), (2.0, 120.0)])) / SR) * 1.5, 2.0) * bp(g.white(gn), 120, 1400) * 0.5 \
        * np.interp(gt, [0, 0.3, 1.7, 2.0], [0, 1, 0.8, 0])
    return lp(x, 2500.0)


def snd_whimper(rng):
    """Pochita backing a step: a small frightened whimper."""
    cs, g = _snd()
    n = int(0.7 * 44100)
    return cs.whimper(n, 0.0, 0.32, 1050.0) + cs.whimper(n, 0.36, 0.28, 980.0) * 0.6


def snd_shriek_inhale(seconds=0.9):
    """The lunge: a harsh wet shriek drawn in, rising (formants and noise, run backwards)."""
    cs, g = _snd()
    from gen_sounds import bp, sat
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    f = 300.0 + 900.0 * (t / seconds) ** 2
    ph = 2 * np.pi * np.cumsum(f * (1.0 + 0.03 * g.white(n))) / 44100.0
    tone = sat(np.sin(ph) + 0.5 * np.sin(2.01 * ph) + 0.3 * np.sin(3.03 * ph), 2.5)
    x = bp(tone, 700.0, 4000.0) * 0.7 + bp(g.white(n), 1500.0, 7000.0) * 0.6
    x += cs.flesh(n, seconds * 0.9, 2.0) * 0.4
    return x * (t / seconds) ** 1.6


def snd_hit():
    """The hit: a deep boom with its harmonics up where a phone hears them, a crack, the slap of its hands."""
    cs, g = _snd()
    from gen_sounds import lp, bp, sat
    m = int(2.6 * 44100)
    tt = np.arange(m) / 44100.0
    boom = cs.sub(m, [(0.0, 130.0), (0.05, 70.0), (0.5, 40.0), (2.6, 30.0)], 0.5, drive=3.0) * 1.4
    boom += bp(sat(boom * 2.0, 3.0), 120.0, 600.0) * 0.8
    boom += lp(g.white(m), 5000.0) * np.exp(-tt / 0.025) * 2.2
    sl = cs.tomato_slap()[:m]
    boom[:len(sl)] += sl * 1.3
    return sat(boom * 0.7, 1.4)


def snd_ring(seconds=2.6):
    """A soft ring under the name: two high sines, a slow beat, fading."""
    n = int(seconds * 44100)
    t = np.arange(n) / 44100.0
    return (np.sin(2 * np.pi * 2650.0 * t) + 0.6 * np.sin(2 * np.pi * 2657.0 * t)) * np.exp(-t / 1.3) * np.minimum(1.0, t / 0.05)


# ---- placing them

def _pan(x, pan):
    """Mono to stereo, equal power; pan -1 left .. 1 right (a number or a curve over x)."""
    p = (np.asarray(pan, float) + 1.0) * np.pi / 4.0
    return np.array([x * np.cos(p), x * np.sin(p)])


def _room(st, amount, length=1.4, seed=7):
    """A room / the night's space round a sound: a decaying noise tail, different left and right. amount 0 is dry (at your ear)."""
    if amount <= 0:
        return st
    from scipy.signal import fftconvolve
    rng = np.random.default_rng(seed)
    m = int(length * 44100)
    tt = np.arange(m) / 44100.0
    out = np.zeros((2, st.shape[1] + m - 1))
    for c in range(2):
        ir = rng.standard_normal(m) * np.exp(-tt / (length / 5.0))
        ir[0] = 0
        ir = ir / np.sqrt((ir ** 2).sum())
        wet = fftconvolve(st[c], ir)
        out[c, :st.shape[1]] += st[c] * (1.0 - 0.5 * amount)
        out[c] += wet * amount * 0.7
    return out


def score(frames, total=None, key=None):
    """The short's soundtrack, as stereo stems (in FRAMES/stems) and their mix. key: where things happen (film seconds)."""
    from gen_sounds import lp, hp, bp, limit
    rng = np.random.default_rng(3402)
    total = total or len(frames) / FPS
    n = int(total * 44100) + 44100
    t = np.arange(n) / 44100.0
    names = ('air', 'crickets', 'drone', 'cluster', 'hands', 'foley', 'breath', 'pochita', 'villager', 'lunge')
    stems = {k: np.zeros((2, n)) for k in names}

    def put(name, x, at, gain=1.0, pan=0.0, room=0.0):
        st = x if np.ndim(x) == 2 else _pan(x, pan)
        st = _room(st, room, seed=int(at * 100) % 97)
        i = int(at * 44100)
        k = min(st.shape[1], n - i)
        if k > 0 and i >= 0:
            stems[name][:, i:i + k] += st[:, :k] * gain

    def curve_at(points):
        return np.interp(t, [p[0] for p in points], [p[1] for p in points])

    # When each shot starts and ends in the film.
    starts, ends = {}, {}
    for j, (src, kind, info) in enumerate(frames):
        s = info.get('shot')
        starts.setdefault(s, j / FPS)
        ends[s] = (j + 1) / FPS
    k = key or {}
    snap = starts[6] + SNAP_IN_SHOT6
    rise = starts[5] + 1.0
    wide = starts[6] + 3.8
    mouth = starts[7] + MOUTH_OPENS_IN_SHOT7
    slap = starts[8] + 1.9
    hit = ends[8] - 1.0 / FPS
    cut1 = starts[3] + 1.6     # the crickets stop dead, the drone with them: it is near
    # Night air: wide, quiet; gone at the snap, back faintly under the mouth, gone in the black.
    air = snd_night_air(total + 2.0, rng)[:, :n]
    stems['air'] += air * curve_at([(0, 0.0), (1.2, 1.0), (snap - 0.05, 1.0), (snap, 0.0), (starts[7] + 0.5, 0.0), (starts[7] + 1.5, 0.5),
                                    (ends[7], 0.5), (ends[7] + 0.05, 0.0), (total, 0.0)])
    # Crickets: far off, three of them, left, right, behind; they stop all at once.
    for f0, per, pan in ((4400.0, 0.71, -0.7), (4780.0, 0.93, 0.6), (5200.0, 1.21, 0.1)):
        c = np.zeros(n)
        cc = snd_cricket(total + 1.0, f0, per, rng)[:n]
        c[:len(cc)] = cc
        c = c * (t < cut1)
        put('crickets', c * 0.02, 0.0, pan=pan, room=0.5)
    # The drone: creeps in during shot 2, cut dead with the crickets; back lower and closer in shot 4, out again as shot 5 opens, back under
    # the rise, gone at the snap; low and close under the mouth; gone in the black; a surge in the lunge.
    dr = snd_drone(total + 2.0)[:n]
    dr2 = snd_drone(total + 2.0, base=36.7)[:n]
    env1 = curve_at([(0, 0), (starts[2], 0.0), (ends[2], 0.35), (cut1 - 0.01, 0.45), (cut1, 0.0), (total, 0.0)])
    env2 = curve_at([(0, 0), (starts[4] - 0.01, 0.0), (starts[4] + 0.6, 0.55), (ends[4], 0.7), (ends[4] + 0.02, 0.0), (rise, 0.0), (rise + 1.5, 0.8),
                     (snap - 0.03, 1.0), (snap, 0.0), (starts[7] + 0.6, 0.0), (starts[7] + 1.8, 0.6), (ends[7] - 0.01, 0.75), (ends[7], 0.0),
                     (starts[8], 0.0), (starts[8] + 0.3, 0.6), (hit, 1.3), (hit + 0.01, 0.0), (total, 0.0)])
    put('drone', dr * env1 * 0.10, 0.0, pan=0.0)
    put('drone', dr2 * env2 * 0.10, 0.0, pan=0.0)
    # The cluster: rises through shots 4 and 5, cut dead at the snap.
    cl = snd_cluster(snap - starts[4] + 0.5, rise=1.12)
    clenv = np.interp(np.arange(len(cl)) / 44100.0, [0, 1.0, ends[4] - starts[4], snap - starts[4] - 0.02, snap - starts[4]], [0, 0.15, 0.4, 1.0, 0.0])
    st = np.array([cl * clenv, np.roll(cl, 441) * clenv])
    put('cluster', st * 0.035, starts[4])
    # Pochita: the growl as he stops, then a small whimper as he backs a step.
    put('pochita', snd_growl(rng), starts[1] + GROWL_IN_SHOT1 - 0.4, 0.36, pan=0.15, room=0.3)
    put('pochita', snd_whimper(rng), starts[1] + GROWL_IN_SHOT1 + 1.7, 0.22, pan=0.15, room=0.3)
    # The hands: where the game played a step: thud, fingers, drag; panned with the picture; dry and loud close, roomy far.
    steps = game_sounds('tomato_step')
    for (vt, gain) in steps:
        ft = film_time(frames, vt)
        if ft is None:
            continue
        shot = frames_shot(frames, ft)
        if shot == 3:
            u = (ft - starts[3]) / (ends[3] - starts[3])
            put('hands', snd_hand(rng, big=u > 0.7), ft, 0.9 * max(0.4, gain), pan=-0.3 - 0.6 * u, room=0.08)
        elif shot == 4:
            u = (ft - starts[4]) / (ends[4] - starts[4])
            put('hands', snd_hand(rng), ft, 0.35, pan=-0.8 + 1.6 * u, room=0.5)
        elif shot == 8:
            u = (ft - starts[8]) / (ends[8] - starts[8])
            put('hands', snd_hand(rng, big=True), ft, 0.4 + 0.8 * u, pan=rng.uniform(-0.5, 0.5) * (0.4 + u), room=0.4 - 0.35 * u)
    # The lunge: hands slapping in faster and louder over what the game played, a harsh wet shriek drawn in, the hit.
    at = starts[8] + 0.3
    gap = 0.32
    while at < slap:
        u = (at - starts[8]) / (slap - starts[8])
        put('lunge', snd_hand(rng, big=True), at, 0.3 + 0.7 * u, pan=rng.uniform(-0.6, 0.6), room=0.3 - 0.25 * u)
        at += gap
        gap = max(0.11, gap * 0.88)
    put('lunge', snd_shriek_inhale(hit - slap), slap, 0.9, pan=0.0, room=0.1)
    put('lunge', snd_hit(), hit, 2.2, pan=0.0)
    put('lunge', np.array([snd_ring(), np.roll(snd_ring(), 300)]) * 0.04, hit + 0.15)
    # The breath, its voice: a little closer every time. Far and roomy in the trees (shot 2), nearer in shot 4, muffled through the
    # glass at the window over the sleeping villager, close under the eye, at your ear at the mouth.
    put('breath', lp(snd_breath(rng, 3.4, 0.15), 1800.0), starts[2] + 0.6, 0.10, pan=0.0, room=0.9)
    put('breath', snd_breath(rng, 3.6, 0.2), starts[4] + 1.0, 0.16, pan=-0.2, room=0.6)
    put('villager', snd_sleeper(ends[5] - starts[5], rng), starts[5], 0.06, pan=-0.35, room=0.15)
    put('breath', lp(snd_breath(rng, 3.4, 0.3), 700.0), rise + 0.4, 0.5, pan=0.25, room=0.35)
    br = snd_breath(rng, 1.8, 0.3)
    put('breath', br * np.linspace(1.0, 0.0, len(br)) ** 0.6, starts[6] + 0.1, 0.35, pan=0.1, room=0.1)
    put('breath', snd_breath(rng, 4.0, 0.45, rasp=1.3), mouth - 0.6, 0.42, pan=0.0)
    # The black: one breath drawn in, close.
    inh = snd_breath(rng, 1.6, 0.1)[:int(0.62 * 1.6 * 44100)]
    put('breath', inh, starts[8] - BLACK + 0.05, 0.6, pan=0.0)
    # Foley at your ear: the eye rolling wet, the click as it finds you; the lips peeling, strands snapping, teeth.
    for dt in (0.25, 0.7, 1.05, 1.5, 1.8):
        put('foley', snd_squelch(rng), starts[6] + dt, 0.5, pan=rng.uniform(-0.25, 0.25))
    put('foley', snd_click(rng), snap + 0.01, 0.9, pan=0.0)
    put('foley', snd_click(rng), mouth - 0.9, 0.5, pan=-0.15)
    put('foley', snd_lip_smack(rng), mouth, 0.55, pan=0.0)
    for j in range(7):
        put('foley', snd_strand(rng), mouth + 0.25 + j * rng.uniform(0.15, 0.35), 0.25, pan=rng.uniform(-0.4, 0.4))
    put('foley', snd_click(rng), mouth + 2.6, 0.4, pan=0.2)
    # The mix: summed, a little high-passed (nothing below 30 Hz), limited to -1 dB.
    mix = sum(stems.values())
    mix = np.array([hp(mix[0], 30.0), hp(mix[1], 30.0)])
    gain = 0.89 / max(1e-6, np.abs(mix).max())
    gain = min(gain * 1.6, 4.0)   # bring it up, then let the limiter take the peaks (the hit)
    out = limit(mix[:, :int(total * 44100)] * gain, ceiling=0.83)  # (-1.6 dB: the AAC encode overshoots a little)
    return stems, out, gain, dict(starts=starts, ends=ends, snap=snap, hit=hit)


def write_wav(path, x):
    import wave
    x = np.atleast_2d(x)
    with wave.open(path, 'wb') as wv:
        wv.setnchannels(x.shape[0])
        wv.setsampwidth(2)
        wv.setframerate(44100)
        wv.writeframes((np.clip(x.T, -1, 1) * 32767).astype('<i2').tobytes())


def read_wav(path):
    import wave
    with wave.open(path) as wv:
        x = np.frombuffer(wv.readframes(wv.getnframes()), '<i2').astype(np.float64) / 32768.0
        return x.reshape(-1, wv.getnchannels()).T


def sound2(frames):
    stems, out, gain, marks_ = score(frames)
    d = os.path.join(FRAMES, 'stems')
    os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        if f.endswith('.wav'):
            os.remove(os.path.join(d, f))
    total = out.shape[1]
    for k, x in stems.items():
        write_wav(os.path.join(d, k + '.wav'), np.clip(x[:, :total] * gain, -1, 1))
    write_wav(os.path.join(d, 'mix.wav'), out)
    path = os.path.join(TMP, 'score.wav')
    os.makedirs(TMP, exist_ok=True)
    write_wav(path, out)
    plot_sound(out, marks_, os.path.join(OUT, 'creepy_sound.png'))
    return path


def plot_sound(x, mk, path, title='creepy.mp4: the sound'):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    m = x.mean(axis=0)
    sr = 44100
    w = int(0.1 * sr)
    tt = np.arange(0, len(m) - w, w) / sr
    def db(c):
        return np.array([20 * np.log10(np.sqrt(np.mean(c[i:i + w] ** 2)) + 1e-6) for i in range(0, len(c) - w, w)])
    peak = np.array([20 * np.log10(np.abs(m[i:i + w]).max() + 1e-6) for i in range(0, len(m) - w, w)])
    fig, ax = plt.subplots(2, 1, figsize=(10, 5.6), dpi=100, sharex=True, gridspec_kw={'height_ratios': [1.1, 1]})
    ax[0].plot(tt, db(x[0]), lw=0.9, color='#c0392b', label='left (RMS, 0.1 s)')
    ax[0].plot(tt, db(x[1]), lw=0.9, color='#2471a3', alpha=0.8, label='right (RMS, 0.1 s)')
    ax[0].plot(tt, peak, lw=0.6, color='#888', label='peak')
    ax[0].set_ylim(-70, 2)
    ax[0].axhline(-1, color='k', lw=0.5, ls=':')
    ax[0].set_ylabel('dBFS')
    ax[0].legend(loc='lower left', fontsize=7, ncol=3)
    ax[0].set_title(title, fontsize=10)
    ax[1].specgram(m + 1e-9, NFFT=2048, Fs=sr, noverlap=1024, cmap='magma', vmin=-130, vmax=-30)
    ax[1].set_yscale('symlog', linthresh=200)
    ax[1].set_ylim(30, 16000)
    ax[1].set_ylabel('Hz')
    ax[1].set_xlabel('seconds')
    ax[1].axhline(120, color='w', lw=0.4, ls=':')
    for a in ax:
        for s, t0 in mk['starts'].items():
            if s in range(1, 9):
                a.axvline(t0, color='g', lw=0.6)
        a.axvline(mk['snap'], color='orange', lw=0.8, ls='--')
        a.axvline(mk['hit'], color='orange', lw=0.8, ls='--')
    for s, t0 in mk['starts'].items():
        if s in range(1, 9):
            ax[0].text(t0 + 0.1, -8, 'shot %d' % s, fontsize=7)
    ax[0].text(mk['snap'] + 0.1, -16, 'snap', fontsize=7, color='darkorange')
    ax[0].text(mk['hit'] - 1.0, -16, 'hit', fontsize=7, color='darkorange')
    fig.tight_layout()
    fig.savefig(path)
    plt.close(fig)


# ---------------------------------------------------------------- the teaser
#
# From the kept frames (no new filming): just looking at it. (capture frames, seconds, a slow push-in from zoom a to b)
TEASER = [
    ('the distance: its eyes far off in the trees (shot 2)', 380, 3.0, (1.0, 1.16)),
    ('eyes in the window (shot 5)', 1580, 1.5, None),
    ('every eye on you (shot 6, wide)', 1890, 1.5, None),
]
TEASER_BLACK = 0.4
TEASER_TITLE = 1.3


def teaser_frames():
    out = []
    for (label, f0, secs, push) in TEASER:
        n = int(round(secs * FPS))
        for k in range(n):
            out.append(('film', f0 + k, label, None if push is None else push[0] + (push[1] - push[0]) * (k / (n - 1)) ** 1.2))
    out += [('black', None, 'black', None)] * int(round(TEASER_BLACK * FPS))
    out += [('title', k, 'the name', None) for k in range(int(round(TEASER_TITLE * FPS)))]
    return out


def teaser_sound(total, t1, t2, t3, tb):
    """Its own mix, from the same generators: quiet night air, the drone low, the breath far then muffled at the window over the sleeper;
    nothing at all on every eye; a soft ring under the name."""
    from gen_sounds import lp, hp, limit
    rng = np.random.default_rng(3403)
    n = int(total * 44100)
    t = np.arange(n) / 44100.0
    out = np.zeros((2, n))

    def put(x, at, gain=1.0, pan=0.0, room=0.0):
        st = x if np.ndim(x) == 2 else _pan(x, pan)
        st = _room(st, room, seed=int(at * 100) % 97)
        i = int(at * 44100)
        k = min(st.shape[1], n - i)
        if k > 0:
            out[:, i:i + k] += st[:, :k] * gain
    air = snd_night_air(total + 1.0, rng)[:, :n]
    out += air * np.interp(t, [0, 0.6, t3 - 0.03, t3, total], [0.0, 1.0, 1.0, 0.0, 0.0])
    for f0, per, pan in ((4400.0, 0.71, -0.7), (4780.0, 0.93, 0.6)):
        c = snd_cricket(total + 1.0, f0, per, rng)[:n] * (t < t1 + 1.6)
        put(c * 0.02, 0.0, pan=pan, room=0.5)
    dr = snd_drone(total + 1.0, base=36.7)[:n]
    put(dr * np.interp(t, [0, 0.8, t2, t3 - 0.03, t3, total], [0.0, 0.25, 0.5, 0.8, 0.0, 0.0]) * 0.10, 0.0)
    cl = snd_cluster(t3 - t2 + 0.2, 1.08)
    ce = np.interp(np.arange(len(cl)) / 44100.0, [0, t3 - t2 - 0.03, t3 - t2], [0.1, 1.0, 0.0])
    put(np.array([cl * ce, np.roll(cl, 441) * ce]) * 0.03, t2)
    put(lp(snd_breath(rng, 3.0, 0.15), 1800.0), t1 + 0.3, 0.12, room=0.9)
    put(snd_sleeper(t3 - t2, rng), t2, 0.06, pan=-0.35, room=0.15)
    br = lp(snd_breath(rng, 2.6, 0.3), 700.0)
    put(br * np.interp(np.arange(len(br)) / 44100.0, [0, t3 - t2 - 0.2, t3 - t2], [1.0, 1.0, 0.0]), t2 + 0.05, 0.5, pan=0.25, room=0.35)
    put(np.array([snd_ring(total - tb), np.roll(snd_ring(total - tb), 300)]) * 0.012, tb + 0.15)
    mix = np.array([hp(out[0], 30.0), hp(out[1], 30.0)])
    g = 0.83 / max(1e-6, np.abs(mix).max()) * 1.3
    return limit(mix * g, ceiling=0.83)


def teaser():
    tf = teaser_frames()
    total = len(tf) / FPS
    raw = os.path.join(TMP, 'teaser_graded.mp4')
    enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-',
                            '-c:v', 'libx264', '-preset', 'medium', '-crf', '12', '-pix_fmt', 'yuv420p', raw], stdin=subprocess.PIPE)
    begin, end = marks()
    shot_of = lambda f: [sh for sh in begin if begin[sh] <= f < end[sh]][0]
    picks = {}
    for j, (kind, src, label, zoom) in enumerate(tf):
        if kind == 'film' and zoom is not None:
            # the push-in, graded from the game's frame (so the grain stays fine)
            fr = np.asarray(Image.open(frame_path('ungraded', src)).convert('RGB'))
            out = grade(fr, src, dict(base_info(shot_of(src), src), zoom=zoom))
        elif kind == 'film':
            out = np.asarray(Image.open(frame_path('graded', src)).convert('RGB'))
        elif kind == 'black':
            out = grade(np.zeros((H, W, 3), np.uint8), 0, {'seed': 7000 + j, 'exposure': 1.0})
        else:
            out = grade(title_frame(src, TEASER_TITLE), 0, {'seed': 8000 + j, 'exposure': 1.0})
        if label not in picks or (kind != 'black' and j - picks[label][0] == int(FPS * (0.7 if kind == 'title' else 1.0))):
            picks[label] = (j, out)
        enc.stdin.write(out.tobytes())
    enc.stdin.close()
    enc.wait()
    t1 = 0.0
    t2 = TEASER[0][2]
    t3 = t2 + TEASER[1][2]
    tb = t3 + TEASER[2][2] + TEASER_BLACK
    snd = teaser_sound(total, t1, t2, t3, tb)
    os.makedirs(os.path.join(FRAMES, 'stems'), exist_ok=True)
    wav = os.path.join(TMP, 'teaser.wav')
    write_wav(wav, snd)
    write_wav(os.path.join(FRAMES, 'stems', 'teaser_mix.wav'), snd)
    dst = os.path.join(OUT, 'teaser.mp4')
    subprocess.check_call(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-i', wav, '-c:v', 'libx264', '-preset', 'slow', '-tune', 'grain', '-crf', '24',
                           '-maxrate', '4000k', '-bufsize', '8000k', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k', '-shortest',
                           '-movflags', '+faststart', dst])
    print('%s: %.1f s, %.2f MB' % (dst, total, os.path.getsize(dst) / 1e6))
    # the sheet: one frame per cut, labelled
    cells = [(lab, picks[lab][0], picks[lab][1]) for lab in [c[0] for c in TEASER] + ['the name']]
    tw, th = 220, 391
    sheet = Image.new('RGB', (tw * len(cells), th), (0, 0, 0))
    for i, (lab, j, img) in enumerate(cells):
        im = Image.fromarray(img).resize((tw, th), Image.LANCZOS)
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, tw, 30), fill=(0, 0, 0))
        d.text((5, 3), '%d  %.1f s' % (i + 1, j / FPS), fill=(255, 255, 255))
        d.text((5, 16), lab[:34], fill=(200, 200, 200))
        sheet.paste(im, (i * tw, 0))
    sheet.save(os.path.join(OUT, 'teaser_sheet.jpg'), quality=88)


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
    if what == 'teaser':
        teaser()
    if what in ('all', 'frames'):
        # (frames 2 6: only those shots' frames graded again)
        grade_all([int(a) for a in sys.argv[2:]] or None)
    if what in ('all', 'cut'):
        render(fr)
    if what in ('all', 'cut', 'sound', 'resound'):
        sc = sound2(fr)
    if what in ('all', 'cut', 'sheet'):
        sheets(fr)
    if what in ('all', 'cut', 'resound'):
        finish(sc, fr)
