"""Mixes the sounds logged during the self test's capture into its video and re-encodes it small
enough to hand around. Usage: mux-audio.py <capture dir> <output.mp4>"""
import json
import os
import subprocess
import sys

folder, output = sys.argv[1], sys.argv[2]
target = os.path.abspath(output)
video = os.path.join(folder, 'video.mp4')
if not os.path.exists(video):
    print('no video in', folder)
    sys.exit(0)
log = os.path.join(folder, 'sounds.json')
sounds = json.load(open(log)) if os.path.exists(log) else []
sounds = [s for s in sounds if os.path.exists(os.path.join(folder, 'sounds', s['file']))]
print(len(sounds), 'sounds')


def encode(crf):
    # Run from the capture folder, each sound file read once and split for each time it plays, the graph in a file:
    # with a couple of hundred sounds the command line was too long for Windows.
    files = sorted({s['file'] for s in sounds})
    index = {f: k + 1 for k, f in enumerate(files)}
    uses = {f: sum(1 for s in sounds if s['file'] == f) for f in files}
    cmd = ['ffmpeg', '-y', '-loglevel', 'warning', '-i', 'video.mp4']
    graph = []
    for f in files:
        cmd += ['-i', os.path.join('sounds', f)]
        k = index[f]
        outs = ''.join('[f%d_%d]' % (k, j) for j in range(uses[f]))
        # Mono world sounds and stereo close-ups mix on a common stereo bus.
        split = ',asplit=%d' % uses[f] if uses[f] > 1 else ''
        graph.append('[%d:a]aformat=channel_layouts=stereo,aresample=48000%s%s' % (k, split, outs))
    used = {f: 0 for f in files}
    labels = []
    for i, s in enumerate(sounds):
        k = index[s['file']]
        j = used[s['file']]
        used[s['file']] += 1
        delay = max(0, int(round(s['time'] * 1000)))
        rate = 48000 * max(0.25, min(4.0, s['pitch']))
        # A sound can start part way into its file and fade up (the song in the black does both).
        trim = ''
        if s.get('offset', 0) > 0:
            trim += 'atrim=start=%.3f,asetpts=PTS-STARTPTS,' % s['offset']
        if s.get('fade', 0) > 0:
            trim += 'afade=t=in:st=0:d=%.3f:curve=qua,' % s['fade']
        graph.append('[f%d_%d]asetrate=%.1f,aresample=48000,%svolume=%.3f,adelay=%d:all=1[s%d]'
                     % (k, j, rate, trim, s['gain'], delay, i))
        labels.append('[s%d]' % i)
    if sounds:
        # A plain sum, as the game mixes them, with a limiter only to catch overs: alimiter's default
        # auto-level would turn everything up to the ceiling and flatten the mix's dynamics.
        graph.append(''.join(labels) + 'amix=inputs=%d:normalize=0:dropout_transition=0,'
                     'alimiter=limit=0.89:level=0:attack=2:release=60,apad[aout]' % len(sounds))
        with open(os.path.join(folder, 'mix.txt'), 'w') as g:
            g.write(';\n'.join(graph))
        cmd += ['-/filter_complex', 'mix.txt', '-map', '0:v', '-map', '[aout]', '-c:a', 'aac', '-b:a', '160k', '-shortest']
    cmd += ['-c:v', 'libx264', '-preset', os.environ.get('MUX_PRESET', 'medium'), '-crf', str(crf), '-pix_fmt', 'yuv420p',
            '-movflags', '+faststart', target]
    subprocess.run(cmd, check=True, cwd=folder)
    return os.path.getsize(target)


# MUX_CRF: one encode at that quality, however big (a master to edit from); otherwise small enough to hand around.
if os.environ.get('MUX_CRF'):
    print('crf', os.environ['MUX_CRF'], '->', encode(int(os.environ['MUX_CRF'])), 'bytes')
else:
    for crf in (22, 25, 28, 31):
        size = encode(crf)
        print('crf', crf, '->', size, 'bytes')
        if size <= 14 * 1024 * 1024:
            break
