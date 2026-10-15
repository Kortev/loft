"""Cuts a Short from a film master (tools/film.py): python tools/shorts.py edl.json

The EDL (edit decision list) is JSON:

    {"source": "build/film/kortev.mp4", "out": "build/film/kortev-short.mp4", "width": 1080, "height": 1920,
     "segments": [{"from": 12.0, "to": 14.5, "speed": 1.0, "punch": 0.0}, ...],
     "captions": [{"at": 0.0, "dur": 1.6, "text": "Woomerd blew up my *house*", "style": "hook", "y": 0.2}, ...],
     "sfx": [{"at": 0.0, "sound": "hit", "gain": 1.0}, ...],
     "crf": 18, "preset": "medium"}

Segments are played in order: "from"/"to" are seconds of the source, "speed" speeds it up (sound too), "punch" pushes
in over the segment (0.1 = 10%); each is cut hard, with a few milliseconds' fade of its sound either side. Captions and
sfx are optional: tools/shorts/*.json, the series' raw POV cuts, use neither. Captions are timed in seconds of the output; "style" is hook (biggest), cap or small,
"y" is the height of its middle (0 top, 1 bottom), and words in *stars* are yellow. "sfx" are sounds laid over the
game's, at seconds of the output: "pop" (a caption landing), "whoosh" (a cut or a speed-up) or "hit" (a low thud), made
here, so nobody owns them; or "file", a sound file. The sound is levelled to -14 LUFS, as YouTube plays it. The
captions' font is Inter ExtraBold if it is installed, else Arial Black or the nearest bold font found (SHORTS_FONT=path
picks one). Needs ffmpeg, numpy and Pillow."""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONTS = [os.environ.get('SHORTS_FONT', ''),
         '/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf',
         os.path.expanduser('~/AppData/Local/Microsoft/Windows/Fonts/Inter-ExtraBold.otf'),
         'C:/Windows/Fonts/Inter-ExtraBold.otf', 'C:/Windows/Fonts/ariblk.ttf', 'C:/Windows/Fonts/arialbd.ttf',
         os.path.expanduser('~/Library/Fonts/Inter-ExtraBold.otf'), '/Library/Fonts/Inter-ExtraBold.otf',
         '/System/Library/Fonts/Supplemental/Arial Black.ttf', '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
FONT = next((f for f in FONTS if f and os.path.exists(f)), None)


def wrap(draw, text, font, width):
    lines = []
    for para in text.split('\n'):
        words = para.split(' ')
        line = ''
        for w in words:
            test = (line + ' ' + w).strip()
            if draw.textlength(test, font=font) <= width or not line:
                line = test
            else:
                lines.append(line)
                line = w
        lines.append(line)
    return lines


def caption_png(text, style, W, path):
    size = {'hook': 92, 'cap': 74, 'small': 52}[style]
    font = ImageFont.truetype(FONT, size) if FONT else ImageFont.load_default(size)
    img = Image.new('RGBA', (W, 10), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lines = wrap(d, text, font, W - 120)
    lh = int(size * 1.18)
    H = lh * len(lines) + 40
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    stroke = max(6, size // 9)
    for i, line in enumerate(lines):
        # Words in *stars* go yellow.
        parts = []
        hot = False
        for chunk in line.split('*'):
            parts.append((chunk, hot))
            hot = not hot
        plain = line.replace('*', '')
        x = (W - d.textlength(plain, font=font)) / 2
        y = 20 + i * lh
        for chunk, hot in parts:
            if not chunk:
                continue
            color = (255, 221, 51, 255) if hot else (255, 255, 255, 255)
            d.text((x, y), chunk, font=font, fill=color, stroke_width=stroke, stroke_fill=(0, 0, 0, 255))
            x += d.textlength(chunk, font=font)
    img.save(path)
    return H


RATE = 48000


def synth(name):
    """One of the made sounds, as mono samples at RATE."""
    rng = np.random.default_rng(len(name))
    if name == 'pop':
        n = int(0.09 * RATE)
        t = np.arange(n) / RATE
        f = 380 + 700 * np.exp(-t / 0.018)
        y = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t / 0.028) * np.minimum(1, t / 0.002)
        return 0.5 * y
    if name == 'whoosh':
        n = int(0.5 * RATE)
        t = np.arange(n) / RATE
        noise = rng.standard_normal(n)
        # Noise through a band that sweeps up and back, swelling and dying away.
        y = np.zeros(n)
        low = band = 0.0
        for i in range(n):
            f = 400 + 2600 * np.sin(np.pi * t[i] / t[-1]) ** 2
            k = 2 * np.sin(np.pi * f / RATE)
            low += k * band
            high = noise[i] - low - 0.7 * band
            band += k * high
            y[i] = band
        y *= np.sin(np.pi * t / t[-1]) ** 2
        return 0.35 * y / np.abs(y).max()
    if name == 'hit':
        n = int(1.1 * RATE)
        t = np.arange(n) / RATE
        f = 34 + 60 * np.exp(-t / 0.06)
        body = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t / 0.35)
        click = rng.standard_normal(n) * np.exp(-t / 0.008) * 0.3
        return 0.8 * np.tanh(1.6 * (body + click))
    raise SystemExit('no such sound: ' + name)


def sound_file(fx, tmp, j):
    if 'file' in fx:
        return fx['file']
    path = os.path.join(tmp, 'fx%d.wav' % j)
    y = np.clip(synth(fx['sound']), -1, 1)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((y * 32767).astype(np.int16).tobytes())
    return path


def atempo(speed):
    filters = []
    while speed > 2.0:
        filters.append('atempo=2.0')
        speed /= 2.0
    while speed < 0.5:
        filters.append('atempo=0.5')
        speed /= 0.5
    filters.append('atempo=%.4f' % speed)
    return ','.join(filters)


def main():
    edl = json.load(open(sys.argv[1]))
    W, H = edl.get('width', 1080), edl.get('height', 1920)
    tmp = tempfile.mkdtemp(prefix='shorts_')
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-i', edl['source']]
    graph = []
    labels = []
    for i, seg in enumerate(edl['segments']):
        sp = seg.get('speed', 1.0)
        punch = seg.get('punch', 0.0)
        v = '[0:v]trim=start=%.4f:end=%.4f,setpts=(PTS-STARTPTS)/%.4f,scale=%d:%d,setsar=1' % (seg['from'], seg['to'], sp, W, H)
        if punch > 0:
            # A push in over the segment: from 1 to 1+punch, kept centred.
            dur = (seg['to'] - seg['from']) / sp
            v += (",scale=w='trunc(%d*(1+%.4f*min(t/%.4f\\,1))/2)*2':h='trunc(%d*(1+%.4f*min(t/%.4f\\,1))/2)*2':eval=frame,"
                  "crop=%d:%d" % (W, punch, dur, H, punch, dur, W, H))
        v += ',fps=30[v%d]' % i
        # A few milliseconds' fade either side of each cut, so a hard cut doesn't click.
        out = (seg['to'] - seg['from']) / sp
        a = ('[0:a]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS,%s,afade=t=in:d=0.012,afade=t=out:st=%.4f:d=0.012[a%d]'
             % (seg['from'], seg['to'], atempo(sp), max(0.0, out - 0.012), i))
        graph += [v, a]
        labels.append('[v%d][a%d]' % (i, i))
    graph.append('%sconcat=n=%d:v=1:a=1[cv][ca]' % (''.join(labels), len(labels)))
    last = 'cv'
    n = 1
    for j, c in enumerate(edl.get('captions', [])):
        png = os.path.join(tmp, 'c%d.png' % j)
        h = caption_png(c['text'], c.get('style', 'cap'), W, png)
        cmd += ['-loop', '1', '-i', png]
        y = int(c.get('y', 0.16) * H - h / 2)
        end = c['at'] + c.get('dur', 2.0)
        graph.append("[%s][%d:v]overlay=0:%d:enable='between(t,%.3f,%.3f)':shortest=1[o%d]" % (last, n, y, c['at'], end, j))
        last = 'o%d' % j
        n += 1
    audio = 'ca'
    effects = edl.get('sfx', [])
    if effects:
        mix = ['[ca]']
        for j, fx in enumerate(effects):
            cmd += ['-i', sound_file(fx, tmp, j)]
            graph.append('[%d:a]aformat=channel_layouts=stereo,aresample=48000,volume=%.3f,adelay=%d:all=1[fx%d]'
                         % (n, fx.get('gain', 1.0), int(round(fx['at'] * 1000)), j))
            mix.append('[fx%d]' % j)
            n += 1
        graph.append('%samix=inputs=%d:normalize=0:duration=first:dropout_transition=0[mixed]' % (''.join(mix), len(mix)))
        audio = 'mixed'
    graph.append('[%s]loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[aout]' % audio)
    cmd += ['-filter_complex', ';'.join(graph), '-map', '[%s]' % last, '-map', '[aout]',
            '-c:v', 'libx264', '-preset', edl.get('preset', 'medium'), '-crf', str(edl.get('crf', 18)), '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', '-r', '30', edl['out']]
    subprocess.run(cmd, check=True)
    print('wrote', edl['out'])


if __name__ == '__main__':
    main()
