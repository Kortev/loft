"""Cuts a Short from a film master (tools/film.py): python tools/shorts.py edl.json

The EDL (edit decision list) is JSON:

    {"source": "build/film/kortev.mp4", "out": "build/film/kortev-short.mp4", "width": 1080, "height": 1920,
     "segments": [{"from": 12.0, "to": 14.5, "speed": 1.0, "punch": 0.0}, ...],
     "captions": [{"at": 0.0, "dur": 1.6, "text": "Woomerd blew up my *house*", "style": "hook", "y": 0.2}, ...],
     "crf": 18, "preset": "medium"}

Segments are played in order: "from"/"to" are seconds of the source, "speed" speeds it up (sound too), "punch" pushes
in over the segment (0.1 = 10%). Captions are timed in seconds of the output; "style" is hook (biggest), cap or small,
"y" is the height of its middle (0 top, 1 bottom), and words in *stars* are yellow. The sound is levelled to -14 LUFS,
as YouTube plays it. The captions' font is Inter ExtraBold if it is installed, else Arial Black or the nearest bold
font found (SHORTS_FONT=path picks one). Needs ffmpeg and Pillow."""
import json
import os
import subprocess
import sys
import tempfile
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
        a = '[0:a]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS,%s[a%d]' % (seg['from'], seg['to'], atempo(sp), i)
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
    graph.append('[ca]loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[aout]')
    cmd += ['-filter_complex', ';'.join(graph), '-map', '[%s]' % last, '-map', '[aout]',
            '-c:v', 'libx264', '-preset', edl.get('preset', 'medium'), '-crf', str(edl.get('crf', 18)), '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', '-r', '30', edl['out']]
    subprocess.run(cmd, check=True)
    print('wrote', edl['out'])


if __name__ == '__main__':
    main()
