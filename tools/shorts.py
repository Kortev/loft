"""Cuts a Short from a film master (tools/film.py): python tools/shorts.py edl.json

The EDL (edit decision list) is JSON:

    {"source": "build/film/kortev.mp4", "out": "build/film/kortev-short.mp4", "width": 1080, "height": 1920,
     "segments": [{"from": 12.0, "to": 14.5, "speed": 1.0, "punch": 0.0}, ...],
     "captions": [{"at": 0.0, "dur": 1.6, "text": "Woomerd blew up my *house*", "style": "hook", "y": 0.2}, ...],
     "sfx": [{"at": 0.0, "sound": "hit", "gain": 1.0}, ...],
     "audio": "build/film/kortev-game.wav",
     "band": {"height": 1350, "center": 1060},
     "banner": {"text": "the revenge was NOT proportional 😭🙏", "y": 270, "size": 64},
     "music": [{"file": "build/film/music/a.mp3", "at": 0.0, "from": 12.0, "dur": 20.0, "db": -16, "fade_in": 0.3,
                "fade_out": 1.5}, ...],
     "crf": 18, "preset": "medium"}

Segments are played in order: "from"/"to" are seconds of the source, "speed" speeds it up (sound too), "punch" pushes
in over the segment (0.1 = 10%); each is cut hard, with a few milliseconds' fade of its sound either side. Captions and
sfx are optional: tools/shorts/*.json, the series' raw POV cuts, use neither. Captions are timed in seconds of the output; "style" is hook (biggest), cap or small,
"y" is the height of its middle (0 top, 1 bottom), and words in *stars* are yellow. "sfx" are sounds laid over the
game's, at seconds of the output: "pop" (a caption landing), "whoosh" (a cut or a speed-up) or "hit" (a low thud), made
here, so nobody owns them; or "file", a sound file. The sound is levelled to -14 LUFS, as YouTube plays it. The
captions' font is Inter ExtraBold if it is installed, else Arial Black or the nearest bold font found (SHORTS_FONT=path
picks one). "riser" is a made sound too: noise and a tone climbing over two and a half seconds, ending dead, to put just
before a big moment (its end at the moment).

"band" crops the picture to a band across it, "height" tall (from the middle of the source's picture), and stands it
on black with its middle "center" from the top; "banner" is one line of plain white text (wrapped if it must be, its
emoji in colour) on the whole Short, its middle "y" from the top: the series' black-bars-and-a-caption look.

"audio" is the sound to cut instead of the source's own: the same take's sound without Minecraft's music (film.py's
<who>-game.wav), for a Short with music of its own. "music" lays tracks under it: each from "from" seconds into its
file, "dur" long, at "at" seconds of the output, "db" loud (-16 if not said), faded in and out; and ducked under the
game's sound, so it gives way whenever something loud happens and comes back after. Needs ffmpeg, numpy and Pillow."""
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
# The banner's: a plain sans like the one YouTube's own text tool puts on a Short, and colour emoji.
PLAIN = next((f for f in [os.environ.get('SHORTS_PLAIN', ''), 'C:/Windows/Fonts/segoeui.ttf', '/System/Library/Fonts/SFNS.ttf',
                          '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'] if f and os.path.exists(f)), None)
EMOJI = next((f for f in ['C:/Windows/Fonts/seguiemj.ttf', '/System/Library/Fonts/Apple Color Emoji.ttc',
                          '/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf'] if os.path.exists(f)), None)


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


def banner_runs(line):
    """The line as runs of plain text and of emoji."""
    out = []
    for ch in line:
        e = ord(ch) > 0x2600
        if out and out[-1][1] == e:
            out[-1][0] += ch
        else:
            out.append([ch, e])
    return out


def banner_png(text, size, W, path):
    """The banner: plain white text, its emoji in colour, centred; a little smaller to keep to one line, else wrapped."""
    room = W - 90
    probe = ImageDraw.Draw(Image.new('RGBA', (W, 10)))
    for k in range(0, 9):
        s = int(size * (1.0 - 0.025 * k))
        font = ImageFont.truetype(PLAIN, s) if PLAIN else ImageFont.load_default(s)
        emoji = ImageFont.truetype(EMOJI, s) if EMOJI and EMOJI.endswith('.ttf') else font
        if sum(probe.textlength(t, font=emoji if e else font) for t, e in banner_runs(text)) <= room:
            break
    size = s

    runs = banner_runs
    measure = lambda line: sum(probe.textlength(t, font=emoji if e else font) for t, e in runs(line))
    lines = ['']
    for word in text.split(' '):
        test = (lines[-1] + ' ' + word).strip()
        if measure(test) > room and lines[-1]:
            lines.append(word)
        else:
            lines[-1] = test
    lh = int(size * 1.3)
    H = lh * len(lines) + 20
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for k, line in enumerate(lines):
        x = (W - measure(line)) / 2
        for t, e in runs(line):
            f = emoji if e else font
            # Both fonts on one baseline: the emoji font's own sits its pictures higher than the text.
            d.text((x, 10 + k * lh + font.getmetrics()[0]), t, font=f, fill=(255, 255, 255, 255), embedded_color=e, anchor='ls')
            x += d.textlength(t, font=f)
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
    if name == 'riser':
        n = int(2.5 * RATE)
        t = np.arange(n) / RATE
        k = t / t[-1]
        # Noise through a band climbing from low to high, a tone climbing under it, swelling to the end and cut dead.
        noise = rng.standard_normal(n)
        y = np.zeros(n)
        low = band = 0.0
        for i in range(n):
            f = 200 + 5800 * k[i] ** 2
            q = 2 * np.sin(np.pi * f / RATE)
            low += q * band
            high = noise[i] - low - 0.5 * band
            band += q * high
            y[i] = band
        y /= np.abs(y).max()
        tone = np.sin(2 * np.pi * np.cumsum(110 * 2 ** (3 * k)) / RATE)
        y = (0.6 * y + 0.4 * tone) * k ** 2.2
        return 0.5 * y * np.minimum(1, (n - np.arange(n)) / (0.004 * RATE))
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
    edl = json.load(open(sys.argv[1], encoding='utf-8'))
    W, H = edl.get('width', 1080), edl.get('height', 1920)
    tmp = tempfile.mkdtemp(prefix='shorts_')
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-i', edl['source']]
    # The sound cut with the picture: the source's own, or the same take's from elsewhere (without its music).
    src_a = '0:a'
    n = 1
    if edl.get('audio'):
        cmd += ['-i', edl['audio']]
        src_a = '1:a'
        n = 2
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
        a = ('[%s]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS,aformat=channel_layouts=stereo,aresample=48000,%s,'
             'afade=t=in:d=0.012,afade=t=out:st=%.4f:d=0.012[a%d]'
             % (src_a, seg['from'], seg['to'], atempo(sp), max(0.0, out - 0.012), i))
        graph += [v, a]
        labels.append('[v%d][a%d]' % (i, i))
    graph.append('%sconcat=n=%d:v=1:a=1[cv][ca]' % (''.join(labels), len(labels)))
    last = 'cv'
    band = edl.get('band')
    if band:
        h = band['height']
        graph.append('[cv]crop=%d:%d:0:%d,pad=%d:%d:0:%d:black[bv]' % (W, h, (H - h) // 2, W, H, band.get('center', H // 2) - h // 2))
        last = 'bv'
    if edl.get('banner'):
        b = edl['banner']
        png = os.path.join(tmp, 'banner.png')
        bh = banner_png(b['text'], b.get('size', 64), W, png)
        cmd += ['-loop', '1', '-i', png]
        graph.append("[%s][%d:v]overlay=0:%d:shortest=1[bn]" % (last, n, int(b.get('y', 270) - bh / 2)))
        last = 'bn'
        n += 1
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
    cues = edl.get('music', [])
    if cues:
        tracks = []
        for j, m in enumerate(cues):
            cmd += ['-i', m['file']]
            fin = m.get('fade_in', 0.3)
            fout = m.get('fade_out', 1.5)
            graph.append('[%d:a]atrim=start=%.3f:end=%.3f,asetpts=PTS-STARTPTS,aformat=channel_layouts=stereo,aresample=48000,'
                         'afade=t=in:d=%.3f,afade=t=out:st=%.3f:d=%.3f,volume=%.2fdB,adelay=%d:all=1[m%d]'
                         % (n, m.get('from', 0.0), m.get('from', 0.0) + m['dur'], fin, max(0.0, m['dur'] - fout), fout,
                            m.get('db', -16.0), int(round(m['at'] * 1000)), j))
            tracks.append('[m%d]' % j)
            n += 1
        graph.append('%samix=inputs=%d:normalize=0:dropout_transition=0[music]' % (''.join(tracks), len(tracks)))
        # Ducked under the game: when the game is loud the music gives way, and comes back after.
        graph.append('[%s]asplit=2[game][key]' % audio)
        graph.append('[music][key]sidechaincompress=threshold=0.04:ratio=6:attack=25:release=450:makeup=1[ducked]')
        graph.append('[game][ducked]amix=inputs=2:normalize=0:duration=first:dropout_transition=0[scored]')
        audio = 'scored'
    graph.append('[%s]aresample=48000[aout]' % audio)
    # The picture and the sound as mixed, then the sound measured and levelled to -14 LUFS in a second pass (one pass
    # fell short of it on these, a couple of dB quiet, which YouTube never turns up), a limiter last to keep it under
    # -1.5 dBFS. The picture is only encoded once.
    mixed = os.path.join(tmp, 'mixed.mkv')
    cmd += ['-filter_complex', ';'.join(graph), '-map', '[%s]' % last, '-map', '[aout]',
            '-c:v', 'libx264', '-preset', edl.get('preset', 'medium'), '-crf', str(edl.get('crf', 18)), '-pix_fmt', 'yuv420p',
            '-c:a', 'pcm_s16le', '-r', '30', mixed]
    subprocess.run(cmd, check=True)
    target = 'I=-14:TP=-1.5:LRA=11'
    report = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', mixed, '-vn', '-af', 'loudnorm=%s:print_format=json' % target,
                             '-f', 'null', '-'], capture_output=True, text=True).stderr
    m = json.loads(report[report.rindex('{'):report.rindex('}') + 1])
    # Aimed a touch over -14 into the limiter, which takes a little off; limited at four times the rate, so the peaks
    # between samples (and the AAC encoder's own overshoot) stay under 0 dBFS too.
    level = ('loudnorm=I=-13.4:TP=-1:LRA=11:measured_I=%s:measured_TP=%s:measured_LRA=%s:measured_thresh=%s:offset=%s:linear=true,'
             'aresample=192000,alimiter=limit=0.72:level=0:attack=1:release=60,aresample=48000'
             % (m['input_i'], m['input_tp'], m['input_lra'], m['input_thresh'], m['target_offset']))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', mixed, '-map', '0:v', '-map', '0:a', '-c:v', 'copy', '-af', level,
                    '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', edl['out']], check=True)
    print('wrote', edl['out'], '(was %s LUFS)' % m['input_i'])


if __name__ == '__main__':
    main()
