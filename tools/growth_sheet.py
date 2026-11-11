"""Sheets for the grown Tomato Devil (devil/Growth): growth_steps.jpg from TomatoSelfTest -Ptarget=growths' six stills, and a contact
sheet of a film (one frame every few seconds, timestamped), both at most 1000 px wide.

    python tools/growth_sheet.py steps <screenshots dir> <out.jpg>
    python tools/growth_sheet.py film <video.mp4> <out.jpg> [every seconds] [columns]
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont


def font(size):
    for f in ('arialbd.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def steps(shots, out):
    tiles = []
    for g in range(6):
        im = Image.open(os.path.join(shots, 'tomato_growth_%d.png' % g)).convert('RGB')
        w, h = im.size
        # the middle of the frame, a little taller than wide
        cw = int(h * 0.82)
        im = im.crop(((w - cw) // 2, 0, (w + cw) // 2, h))
        tiles.append(im)
    cols, rows = 3, 2
    tw = 1000 // cols
    th = int(tiles[0].size[1] * tw / tiles[0].size[0])
    sheet = Image.new('RGB', (tw * cols, th * rows), (12, 12, 12))
    d = ImageDraw.Draw(sheet)
    f = font(30)
    for i, t in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * th
        sheet.paste(t.resize((tw - 4, th - 4), Image.LANCZOS), (x + 2, y + 2))
        d.rectangle([x + 8, y + 8, x + 58, y + 50], fill=(0, 0, 0))
        d.text((x + 22, y + 11), str(i), fill=(255, 255, 255), font=f)
    sheet.save(out, quality=88)
    print('wrote', out, sheet.size)


def film(video, out, every=3.0, cols=6):
    every = float(every)
    cols = int(cols)
    tmp = tempfile.mkdtemp()
    subprocess.run(['ffmpeg', '-v', 'error', '-i', video, '-vf', 'fps=1/%g,scale=240:-1' % every, os.path.join(tmp, 'f%04d.jpg')], check=True)
    frames = sorted(os.listdir(tmp))
    ims = [Image.open(os.path.join(tmp, n)) for n in frames]
    tw = 1000 // cols
    th = int(ims[0].size[1] * tw / ims[0].size[0])
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new('RGB', (tw * cols, th * rows), (20, 20, 20))
    d = ImageDraw.Draw(sheet)
    f = font(14)
    for i, im in enumerate(ims):
        x, y = (i % cols) * tw, (i // cols) * th
        sheet.paste(im.resize((tw - 2, th - 2), Image.LANCZOS), (x + 1, y + 1))
        label = '%.1f s' % (i * every + every / 2)
        d.rectangle([x + 2, y + 2, x + 52, y + 20], fill=(0, 0, 0))
        d.text((x + 5, y + 3), label, fill=(255, 255, 255), font=f)
    sheet.save(out, quality=85)
    print('wrote', out, sheet.size, len(ims), 'frames')


if __name__ == '__main__':
    if sys.argv[1] == 'steps':
        steps(sys.argv[2], sys.argv[3])
    else:
        film(*sys.argv[2:])
