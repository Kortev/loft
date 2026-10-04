"""Mixes the sounds logged during the self test's capture into its video and re-encodes it small
enough to hand around. Usage: mux-audio.py <capture dir> <output.mp4>"""
import json
import os
import subprocess
import sys

folder, output = sys.argv[1], sys.argv[2]
video = os.path.join(folder, 'video.mp4')
if not os.path.exists(video):
    print('no video in', folder)
    sys.exit(0)
log = os.path.join(folder, 'sounds.json')
sounds = json.load(open(log)) if os.path.exists(log) else []
sounds = [s for s in sounds if os.path.exists(os.path.join(folder, 'sounds', s['file']))]
print(len(sounds), 'sounds')


def encode(crf):
    cmd = ['ffmpeg', '-y', '-loglevel', 'warning', '-i', video]
    graph = []
    labels = []
    for i, s in enumerate(sounds):
        cmd += ['-i', os.path.join(folder, 'sounds', s['file'])]
        delay = max(0, int(round(s['time'] * 1000)))
        rate = 48000 * max(0.25, min(4.0, s['pitch']))
        # Mono world sounds and stereo close-ups mix on a common stereo bus.
        graph.append('[%d:a]aformat=channel_layouts=stereo,aresample=48000,asetrate=%.1f,aresample=48000,volume=%.3f,'
                     'adelay=%d:all=1[s%d]' % (i + 1, rate, s['gain'], delay, i))
        labels.append('[s%d]' % i)
    if sounds:
        graph.append(''.join(labels) + 'amix=inputs=%d:normalize=0:dropout_transition=0,alimiter=limit=0.9,apad[aout]'
                     % len(sounds))
        cmd += ['-filter_complex', ';'.join(graph), '-map', '0:v', '-map', '[aout]', '-c:a', 'aac', '-b:a', '160k',
                '-shortest']
    cmd += ['-c:v', 'libx264', '-preset', 'medium', '-crf', str(crf), '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
            output]
    subprocess.run(cmd, check=True)
    return os.path.getsize(output)


for crf in (22, 25, 28, 31):
    size = encode(crf)
    print('crf', crf, '->', size, 'bytes')
    if size <= 14 * 1024 * 1024:
        break
