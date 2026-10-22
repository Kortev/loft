"""Lists the places in a film master where the picture changes all at once by itself, the only places a Short is cut:
python tools/cuts.py build/film/kortev.mp4 [threshold]

A cut made at one of these (a camera cut in the feed, the death screen, a warp's flash, a blackout, a chest or the
inventory opening) reads as part of the game, where a cut in the middle of someone turning or walking reads as a jump.
Prints each one's time and how big a change it is (0-100), and the stretches of black and white, which can be cut
anywhere inside. The default threshold is 18 (a camera cut in the feed is 30 and more). Needs ffmpeg and numpy."""
import re
import subprocess
import sys

import numpy as np

video = sys.argv[1]
threshold = float(sys.argv[2]) if len(sys.argv) > 2 else 18.0
log = subprocess.run(['ffmpeg', '-hide_banner', '-i', video, '-vf', 'scale=270:-2,scdet=threshold=%g' % threshold, '-an', '-f', 'null', '-'],
                     capture_output=True, text=True).stderr
changes = [(float(t), float(s)) for s, t in re.findall(r'lavfi\.scd\.score:\s*([\d.]+),\s*lavfi\.scd\.time:\s*([\d.]+)', log)]
print('changes (time, score):')
for t, s in changes:
    print('  %7.2f  %5.1f' % (t, s))

# Stretches of a plain screen: black, or white.
W, H = 64, 114
raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', video, '-vf', 'scale=%d:%d,format=gray' % (W, H), '-f', 'rawvideo', '-'],
                     capture_output=True, check=True).stdout
f = np.frombuffer(raw, np.uint8).reshape(-1, H, W)[:, 10:-14, :].astype(float)
mean = f.mean(axis=(1, 2))
spread = f.std(axis=(1, 2))
for name, plain in (('black', (mean < 14) & (spread < 10)), ('white', (mean > 225) & (spread < 18))):
    runs = []
    start = None
    for i, p in enumerate(plain):
        if p and start is None:
            start = i
        if not p and start is not None:
            runs.append((start, i))
            start = None
    if start is not None:
        runs.append((start, len(plain)))
    print('%s (from, to):' % name)
    for a, b in runs:
        if b - a >= 3:
            print('  %7.2f  %7.2f' % (a / 30.0, b / 30.0))
