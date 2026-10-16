"""The whole event from all three takes at once, for a long video: python tools/full_event.py

Woomerd | Kortev | gerald_2009 side by side in 16:9, each as its take is (9:16, uncut), in sync, their names over them
and their three soundtracks mixed; levelled to -14 LUFS as the Shorts are. Writes build/film/full-event.mp4. The takes
are lined up on the impact, which each one's capture logs (a take's frames start a tick or so apart from another's).
Needs ffmpeg and Pillow."""
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


def contact(who):
    """When the falling block lands in {@code who}'s take."""
    sounds = json.load(open(os.path.join(FILM, who + '-capture', 'sounds.json')))
    return next(s['time'] for s in sounds if 'gap_contact' in s['file'])


# Each take's lag behind gerald's.
LAG = {who: contact(who) - contact('gerald') for who in ('woomerd', 'kortev', 'gerald')}
ORDER = [('woomerd', 'Woomerd'), ('kortev', 'Kortev'), ('gerald', 'gerald_2009')]
W, H = 1920, 1080
PW, PH = 564, 1002               # each panel, 9:16 as the takes are
TOP = H - PH                     # the strip their names go in
GAP = (W - 3 * PW) // 4

font = ImageFont.truetype('C:/Windows/Fonts/ariblk.ttf', 34)
head = Image.new('RGBA', (W, TOP), (0, 0, 0, 0))
d = ImageDraw.Draw(head)
for k, (_, name) in enumerate(ORDER):
    cx = GAP + k * (PW + GAP) + PW / 2
    d.text((cx, TOP / 2), name, font=font, fill='white', anchor='mm', stroke_width=3, stroke_fill='black')
head.save(os.path.join(TMP, 'names.png'))

dur = END - START
cmd = ['ffmpeg', '-y', '-loglevel', 'error']
for who, _ in ORDER:
    cmd += ['-ss', '%.3f' % (START + LAG[who]), '-t', '%.3f' % dur, '-i', os.path.join(FILM, who + '.mp4')]
cmd += ['-loop', '1', '-i', os.path.join(TMP, 'names.png')]
g = ['color=c=black:s=%dx%d:r=30:d=%.3f[bg]' % (W, H, dur)]
last = 'bg'
for k in range(3):
    g.append('[%d:v]setpts=PTS-STARTPTS,scale=%d:%d:flags=lanczos,setsar=1[p%d]' % (k, PW, PH, k))
    g.append('[%s][p%d]overlay=x=%d:y=%d[v%d]' % (last, k, GAP + k * (PW + GAP), TOP, k))
    last = 'v%d' % k
g.append('[%s][3:v]overlay=x=0:y=0:shortest=1[vout]' % last)
g.append(';'.join('[%d:a]asetpts=PTS-STARTPTS[a%d]' % (k, k) for k in range(3)))
g.append('[a0][a1][a2]amix=inputs=3:normalize=0:duration=first,volume=0.6[aout]')
mixed = os.path.join(TMP, 'mixed.mkv')
cmd += ['-filter_complex', ';'.join(g), '-map', '[vout]', '-map', '[aout]', '-t', '%.3f' % dur,
        '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', '30', '-c:a', 'pcm_s16le', mixed]
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
