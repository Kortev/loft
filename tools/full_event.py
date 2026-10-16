"""The whole event from all three takes at once, for a long video: python tools/full_event.py

Woomerd | Kortev | gerald_2009 side by side in 16:9, each as its take is (9:16, uncut), in sync, their names over them
and their three soundtracks mixed; levelled to -14 LUFS as the Shorts are. Writes build/film/full-event.mp4. The takes
are lined up on the impact, which each one's capture logs (a take's frames start a tick or so apart from another's).

gerald's panel is nudged where the script has them do something on the very tick Woomerd does (their screens shut
together, they turn in the void together), which side by side looks scripted: held a moment on a still frame, then
caught up at another still moment or a little fast, before the next thing everyone sees at once. Their sound is out
of the mix while their picture is off its clock (the others' made up for it), and so is the Nether's before they come
through the portal, which until then is the game's portal screen. Needs ffmpeg and Pillow."""
import atexit
import json
import os
import shutil
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

FILM = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'build', 'film')
TMP = tempfile.mkdtemp(prefix='full_event_')
atexit.register(shutil.rmtree, TMP, True)
OUT = os.path.join(FILM, 'full-event.mp4')
START, END = 2.0, 111.5          # on gerald's clock: the TNT going down, to gerald's death screen and the taunt
FPS = 30


def contact(who):
    """When the falling block lands in {@code who}'s take."""
    sounds = json.load(open(os.path.join(FILM, who + '-capture', 'sounds.json')))
    return next(s['time'] for s in sounds if 'gap_contact' in s['file'])


# Each take's lag behind gerald's.
LAG = {who: contact(who) - contact('gerald') for who in ('woomerd', 'kortev', 'gerald')}
ORDER = [('woomerd', 'Woomerd'), ('kortev', 'Kortev'), ('gerald', 'gerald_2009')]
# How each panel is played, on gerald's clock: ('play', from, to[, speed]) or ('hold', at, for). The panels' lengths
# must all come to END - START.
PIECES = {
    'woomerd': [('play', START, END)],
    'kortev': [('play', START, END)],
    'gerald': [
        ('hold', 7.30, 7.267 - START),       # the portal screen, until they come through
        ('play', 7.267, 26.5),
        ('hold', 26.5, 0.7),                  # the inventory up a moment longer than Woomerd's chest
        ('play', 26.5, 33.0, 6.5 / 5.8),      # and back in step before the block comes down
        ('play', 33.0, 46.9),
        ('hold', 46.9, 0.6),                  # in the void: a moment longer before looking about
        ('play', 46.9, 58.4),
        ('play', 59.0, END),                  # back in step where nothing in their view moves
    ],
}
# Output times (from START) gerald's sound is out of the mix for, and where the others are turned up to make up for
# what they all hear (the drone, the void's music): not in the inventory, where gerald's is silent anyway.
OUT_OF_STEP = [(24.5, 31.0), (44.9, 57.0)]
MADE_UP = [(27.35, 31.0), (44.9, 57.0)]
BEFORE_PORTAL = 7.267 - START

W, H = 1920, 1080
PW, PH = 564, 1002               # each panel, 9:16 as the takes are
TOP = H - PH                     # the strip their names go in
GAP = (W - 3 * PW) // 4
dur = END - START
for who, pieces in PIECES.items():
    total = sum(p[2] if p[0] == 'hold' else (p[2] - p[1]) / (p[3] if len(p) > 3 else 1.0) for p in pieces)
    assert abs(total - dur) < 0.02, (who, total, dur)


def envelope(points):
    """A gain over output time t, through (time, gain) points in order, flat before the first and after the last."""
    expr = '%g' % points[-1][1]
    for (t0, g0), (t1, g1) in reversed(list(zip(points, points[1:]))):
        seg = '%g' % g0 if g0 == g1 else '(%g+(%g)*(t-%g)/%g)' % (g0, g1 - g0, t0, t1 - t0)
        expr = 'if(lt(t,%g),%s,%s)' % (t1, seg, expr)
    return "'if(lt(t,%g),%g,%s)'" % (points[0][0], points[0][1], expr)


def windows(spans, inside, outside, ramp=0.08):
    points = [(0.0, outside)]
    for a, b in spans:
        points += [(a - ramp, outside), (a, inside), (b, inside), (b + ramp, outside)]
    return envelope(points)


font = ImageFont.truetype('C:/Windows/Fonts/ariblk.ttf', 34)
head = Image.new('RGBA', (W, TOP), (0, 0, 0, 0))
d = ImageDraw.Draw(head)
for k, (_, name) in enumerate(ORDER):
    cx = GAP + k * (PW + GAP) + PW / 2
    d.text((cx, TOP / 2), name, font=font, fill='white', anchor='mm', stroke_width=3, stroke_fill='black')
head.save(os.path.join(TMP, 'names.png'))

cmd = ['ffmpeg', '-y', '-loglevel', 'error']
for who, _ in ORDER:
    cmd += ['-i', os.path.join(FILM, who + '.mp4')]
cmd += ['-loop', '1', '-i', os.path.join(TMP, 'names.png')]
g = ['color=c=black:s=%dx%d:r=%d:d=%.3f[bg]' % (W, H, FPS, dur)]
last = 'bg'
for k, (who, _) in enumerate(ORDER):
    pieces = PIECES[who]
    lag = LAG[who]
    g.append('[%d:v]split=%d%s' % (k, len(pieces), ''.join('[s%d_%d]' % (k, j) for j in range(len(pieces)))))
    for j, p in enumerate(pieces):
        if p[0] == 'hold':
            g.append('[s%d_%d]trim=start=%.4f:end=%.4f,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=%.4f,'
                     'trim=end=%.4f,fps=%d[q%d_%d]' % (k, j, p[1] + lag, p[1] + lag + 1.0 / FPS, p[2], p[2], FPS, k, j))
        else:
            speed = p[3] if len(p) > 3 else 1.0
            g.append('[s%d_%d]trim=start=%.4f:end=%.4f,setpts=(PTS-STARTPTS)/%.6f,fps=%d[q%d_%d]'
                     % (k, j, p[1] + lag, p[2] + lag, speed, FPS, k, j))
    g.append('%sconcat=n=%d:v=1:a=0,scale=%d:%d:flags=lanczos,setsar=1[p%d]'
             % (''.join('[q%d_%d]' % (k, j) for j in range(len(pieces))), len(pieces), PW, PH, k))
    g.append('[%s][p%d]overlay=x=%d:y=%d:eof_action=repeat[v%d]' % (last, k, GAP + k * (PW + GAP), TOP, k))
    last = 'v%d' % k
    # The sound, uncut and on the shared clock: gerald's out where their picture is off it, the others up to make up.
    if who == 'gerald':
        gain = windows([(-1.0, BEFORE_PORTAL)] + OUT_OF_STEP, 0.0, 1.0)
    else:
        gain = windows(MADE_UP, 1.5, 1.0, ramp=0.25)
    g.append('[%d:a]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS,volume=volume=%s:eval=frame[a%d]'
             % (k, START + lag, END + lag, gain, k))
g.append('[%s][3:v]overlay=x=0:y=0:shortest=1[vout]' % last)
g.append('[a0][a1][a2]amix=inputs=3:normalize=0:duration=first,volume=0.6[aout]')
mixed = os.path.join(TMP, 'mixed.mkv')
cmd += ['-filter_complex', ';'.join(g), '-map', '[vout]', '-map', '[aout]', '-t', '%.3f' % dur,
        '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', str(FPS), '-c:a', 'pcm_s16le', mixed]
subprocess.run(cmd, check=True)
report = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', mixed, '-vn', '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11:print_format=json',
                         '-f', 'null', '-'], capture_output=True, text=True).stderr
m = json.loads(report[report.rindex('{'):report.rindex('}') + 1])
level = ('loudnorm=I=-12.6:TP=-1:LRA=11:measured_I=%s:measured_TP=%s:measured_LRA=%s:measured_thresh=%s:offset=%s:linear=true,'
         'aresample=192000,alimiter=limit=0.72:level=0:attack=1:release=60,aresample=48000'
         % (m['input_i'], m['input_tp'], m['input_lra'], m['input_thresh'], m['target_offset']))
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', mixed, '-map', '0:v', '-map', '0:a', '-c:v', 'copy', '-af', level,
                '-c:a', 'aac', '-b:a', '320k', '-movflags', '+faststart', OUT], check=True)
print('wrote', OUT, '(mix was %s LUFS)' % m['input_i'])
