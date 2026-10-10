"""Renders the self test's looping sounds (loops.json: each loop's level and pitch, as heard from the camera, tick by
tick) into whole tracks, and adds them to the capture's sound log so mux-audio.py mixes them in like any other sound.
Usage: render-loops.py <capture dir>"""
import json
import os
import subprocess
import sys
import wave

import numpy as np

RATE = 48000
folder = sys.argv[1]
path = os.path.join(folder, 'loops.json')
if not os.path.exists(path):
    print('no loops in', folder)
    sys.exit(0)
tracks = json.load(open(path))['tracks']
log_path = os.path.join(folder, 'sounds.json')
log = json.load(open(log_path)) if os.path.exists(log_path) else []
for track in tracks:
    points = np.array(track['points'], float)
    if len(points) < 2 or points[:, 1].max() <= 0.001:
        continue
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', os.path.join(folder, 'sounds', track['loop']), '-ac', '1',
                          '-ar', str(RATE), '-f', 'f32le', '-'], check=True, capture_output=True).stdout
    loop = np.frombuffer(raw, np.float32).astype(np.float64)
    n = int((points[-1, 0] + 0.5) * RATE)
    t = np.arange(n) / RATE
    gain = np.interp(t, points[:, 0], points[:, 1], left=0.0, right=0.0)
    pitch = np.interp(t, points[:, 0], points[:, 2])
    # Played back faster or slower as the game bends its pitch, going round the loop without a break.
    phase = np.cumsum(pitch) % len(loop)
    i0 = np.floor(phase).astype(int)
    frac = phase - i0
    y = (loop[i0] * (1 - frac) + loop[(i0 + 1) % len(loop)] * frac) * gain
    name = os.path.splitext(track['loop'])[0] + '_track.wav'
    with wave.open(os.path.join(folder, 'sounds', name), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((np.clip(y, -1, 1) * 32767).astype(np.int16).tobytes())
    log.append({'time': 0.0, 'file': name, 'gain': 1.0, 'pitch': 1.0, 'offset': 0.0, 'fade': 0.0})
    print('rendered', name, '%.1f s, peak gain %.2f' % (n / RATE, gain.max()))
json.dump(log, open(log_path, 'w'), indent=1)
