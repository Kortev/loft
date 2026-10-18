"""A Short that cuts between the three takes of the same moment: python tools/povcut.py tools/shorts/pov.json

Like a Short's edit list (tools/shorts.py: "band", "banner", "out", "crf", "preset"), but in "shots", each from one
take ("pov": "kortev" | "woomerd" | "gerald") or two stacked, the first on top ("stack": [..., ...]), "from" and "to"
on gerald's clock (the takes are lined up on the impact, as tools/full_event.py does). Time only skips where the shot
changes to someone else. Each shot is named in small text at the top of its picture; its sound is that take's own (the
two of a stack mixed). "sfx" are placed by when they happen on that clock ("src", less "lead": a riser's climax, a
boom's onset) and must fall in a shot. The shots are put together into one picture, which tools/shorts.py then crops
to the band, captions and levels. Needs ffmpeg and Pillow."""
import atexit
import json
import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILM = os.path.join(ROOT, 'build', 'film')
W, H, FPS = 1080, 1920, 30
NAMES = {'kortev': 'kortev', 'woomerd': 'woomerd', 'gerald': 'gerald'}


def contact(who):
    sounds = json.load(open(os.path.join(FILM, who + '-capture', 'sounds.json')))
    return next(s['time'] for s in sounds if 'gap_contact' in s['file'])


def label(text, path, width):
    font = ImageFont.truetype('C:/Windows/Fonts/ariblk.ttf', 40)
    img = Image.new('RGBA', (width, 70), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((28, 35), text, font=font, fill='white', anchor='lm', stroke_width=3, stroke_fill='black')
    img.save(path)


def main():
    edl = json.load(open(sys.argv[1], encoding='utf-8'))
    tmp = tempfile.mkdtemp(prefix='povcut_')
    atexit.register(shutil.rmtree, tmp, True)
    lag = {who: contact(who) - contact('gerald') for who in NAMES}
    band = edl.get('band', {'height': H})
    top = (H - band['height']) // 2           # the band's top in the source picture, which tools/shorts.py keeps
    half = band['height'] // 2
    for who, name in NAMES.items():
        label(name, os.path.join(tmp, who + '.png'), W)
    parts = []
    for n, shot in enumerate(edl['shots']):
        dur = shot['to'] - shot['from']
        povs = shot['stack'] if 'stack' in shot else [shot['pov']]
        cmd = ['ffmpeg', '-y', '-loglevel', 'error']
        for who in povs:
            cmd += ['-ss', '%.4f' % (shot['from'] + lag[who]), '-t', '%.4f' % dur, '-i', os.path.join(FILM, who + '.mp4')]
        for who in povs:
            cmd += ['-i', os.path.join(tmp, who + '.png')]
        k = len(povs)
        g = ['color=c=black:s=%dx%d:r=%d:d=%.4f[bg]' % (W, H, FPS, dur)]
        if k == 1:
            g.append('[0:v]setpts=PTS-STARTPTS,fps=%d[c0]' % FPS)
            g.append('[bg][c0]overlay=0:0[m0]')
            g.append('[m0][1:v]overlay=0:%d[vout]' % (top + 12))
            g.append('[0:a]asetpts=PTS-STARTPTS[mix]')
        else:
            # Two at once: the middle of each picture (round the crosshair), one over the other in the band.
            ch = half - 3
            for j in range(2):
                g.append('[%d:v]setpts=PTS-STARTPTS,fps=%d,crop=%d:%d:0:%d[c%d]' % (j, FPS, W, ch, (H - ch) // 2, j))
            g.append('[bg][c0]overlay=0:%d[m0]' % top)
            g.append('[m0][c1]overlay=0:%d[m1]' % (top + half + 3))
            g.append('[m1][2:v]overlay=0:%d[m2]' % (top + 12))
            g.append('[m2][3:v]overlay=0:%d[vout]' % (top + half + 15))
            g.append('[0:a][1:a]amix=inputs=2:normalize=0,volume=0.75[mix]')
        g.append('[mix]aresample=48000,aformat=channel_layouts=stereo,afade=t=in:d=0.012,afade=t=out:st=%.4f:d=0.012[aout]'
                 % max(0.0, dur - 0.012))
        part = os.path.join(tmp, 'shot%02d.mkv' % n)
        cmd += ['-filter_complex', ';'.join(g), '-map', '[vout]', '-map', '[aout]', '-t', '%.4f' % dur, '-r', str(FPS),
                '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '14', '-pix_fmt', 'yuv420p', '-c:a', 'pcm_s16le', part]
        subprocess.run(cmd, check=True)
        parts.append(part)
    listing = os.path.join(tmp, 'parts.txt')
    with open(listing, 'w') as f:
        f.writelines("file '%s'\n" % p.replace('\\', '/') for p in parts)
    master = os.path.join(tmp, 'master.mkv')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', listing, '-c', 'copy', master], check=True)
    total = float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', master],
                                 capture_output=True, text=True).stdout)

    def out_time(src):
        t = 0.0
        for shot in edl['shots']:
            if shot['from'] <= src <= shot['to']:
                return t + src - shot['from']
            t += shot['to'] - shot['from']
        raise SystemExit('%s is in no shot' % src)

    short = {key: edl[key] for key in ('out', 'band', 'banner', 'crf', 'preset') if key in edl}
    short['source'] = master
    short['segments'] = [{'from': 0.0, 'to': round(total, 3)}]
    short['sfx'] = [{'at': round(out_time(fx['src']) - fx.get('lead', 0.0), 3), 'file': fx['file'], 'gain': fx.get('gain', 1.0)}
                    for fx in edl.get('sfx', [])]
    path = os.path.join(tmp, 'short.json')
    json.dump(short, open(path, 'w', encoding='utf-8'), ensure_ascii=False)
    subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'shorts.py'), path], check=True, cwd=ROOT)
    for fx in short['sfx']:
        print('  %s at %.2f s' % (os.path.basename(fx['file']), fx['at']))


if __name__ == '__main__':
    main()
