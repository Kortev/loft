"""Dev helper: downloads public-domain NASA maps, resizes them and prints them into the log as base64 JPEG."""
import base64
import hashlib
import io
import re
import sys
import urllib.request

from PIL import Image

Image.MAX_IMAGE_PIXELS = None
UA = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36'}
SVS = 'https://svs.gsfc.nasa.gov/vis/a000000/a004800/a004851/'
EO = 'https://eoimages.gsfc.nasa.gov/images/imagerecords/'

ASSETS = {
    'earth_day': ([EO + '74000/74092/world.200407.3x5400x2700.jpg',
                   EO + '73000/73909/world.topo.bathy.200412.3x5400x2700.jpg',
                   EO + '74000/74117/world.200408.3x5400x2700.jpg'], (4096, 2048), 'RGB', 88),
    'earth_clouds': ([EO + '57000/57747/cloud_combined_2048.jpg',
                      EO + '57000/57747/cloud_combined_8192.tif'], (2048, 1024), 'L', 90),
    'earth_night': ([EO + '144000/144898/BlackMarble_2016_01deg.jpg',
                     EO + '79000/79765/dnb_land_ocean_ice.2012.3600x1800.jpg'], (2048, 1024), 'RGB', 88),
    'jupiter': (['https://assets.science.nasa.gov/content/dam/science/psd/photojournal/pia/pia07/pia07782/PIA07782.jpg',
                 'https://assets.science.nasa.gov/content/dam/science/psd/photojournal/pia/pia07/pia07782/PIA07782.tif',
                 'https://photojournal.jpl.nasa.gov/jpeg/PIA07782.jpg'], (2048, 1024), 'RGB', 90),
    'stars': ([SVS + 'starmap_2020_4k_gal_print.jpg', SVS + 'starmap_2020_4k_print.jpg',
               SVS + 'starmap_2020_8k_gal_print.jpg', SVS + 'milkyway_2020_4k_gal_print.jpg'], (4096, 2048), 'RGB', 90),
}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


def emit(filename, data):
    print('=== FILE %s %d %s ===' % (filename, len(data), hashlib.sha256(data).hexdigest()))
    enc = base64.b64encode(data).decode('ascii')
    for i in range(0, len(enc), 4000):
        print(enc[i:i + 4000])
    print('=== END ===')


def stars():
    """Milky Way background tone-mapped to JPEG, plus a point catalogue of the brightest stars."""
    import os
    os.environ['OPENCV_IO_ENABLE_OPENEXR'] = '1'
    import cv2
    import numpy as np
    import struct
    for f in ['milkyway_2020_4k_gal.exr', 'hiptyc_2020_8k_gal.exr']:
        open('/tmp/' + f, 'wb').write(get(SVS + f))
    mw = cv2.imread('/tmp/milkyway_2020_4k_gal.exr', cv2.IMREAD_UNCHANGED)[:, :, :3][:, :, ::-1].astype(np.float64)
    lum = mw.mean(axis=2)
    print('milky way', mw.shape, 'percentiles', [float(np.percentile(lum, q)) for q in (50, 90, 99, 99.9, 100)])
    k = -np.log(0.08) / np.percentile(lum, 99.9)
    out = 1.0 - np.exp(-mw * k)
    out = np.where(out <= 0.0031308, out * 12.92, 1.055 * np.power(np.clip(out, 0, 1), 1 / 2.4) - 0.055)
    img = Image.fromarray(np.clip(out * 255 + 0.5, 0, 255).astype(np.uint8), 'RGB')
    buf = io.BytesIO()
    img.save(buf, 'JPEG', quality=92, optimize=True)
    emit('milkyway.jpg', buf.getvalue())

    st = cv2.imread('/tmp/hiptyc_2020_8k_gal.exr', cv2.IMREAD_UNCHANGED)[:, :, :3][:, :, ::-1].astype(np.float64)
    h, w = st.shape[:2]
    l = st.sum(axis=2)
    peaks = (l == cv2.dilate(l.astype(np.float32), np.ones((3, 3), np.uint8)).astype(np.float64)) & (l > 0)
    ys, xs = np.nonzero(peaks)
    flux = np.zeros(len(xs))
    rgb = np.zeros((len(xs), 3))
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            yy = np.clip(ys + dy, 0, h - 1)
            xx = (xs + dx) % w
            flux += l[yy, xx]
            rgb += st[yy, xx]
    order = np.argsort(-flux)[:14000]
    print('stars found', len(xs), 'kept', len(order), 'flux range', float(flux[order[0]]), float(flux[order[-1]]))
    data = bytearray(struct.pack('<i', len(order)))
    fmax = flux[order[0]]
    for i in order:
        lon = (xs[i] + 0.5) / w * 2 * np.pi - np.pi
        lat = np.pi / 2 - (ys[i] + 0.5) / h * np.pi
        x, y, z = np.cos(lat) * np.cos(lon), np.sin(lat), np.cos(lat) * np.sin(lon)
        c = rgb[i] / max(rgb[i].max(), 1e-9)
        mag = 2.5 * np.log10(fmax / flux[i])
        data += struct.pack('<fffBBBB', x, y, z, *[int(min(255, max(0, v * 255))) for v in c], int(min(255, mag * 25)))
    emit('stars.bin', bytes(data))


name = sys.argv[1]
if name == 'stars_exr':
    stars()
    sys.exit(0)
urls, size, mode, quality = ASSETS[name]
if name == 'jupiter':
    for page_url in ['https://photojournal.jpl.nasa.gov/catalog/PIA07782',
                     'https://science.nasa.gov/photojournal/cassinis-best-maps-of-jupiter/']:
        try:
            page = get(page_url).decode('utf-8', 'replace')
            links = sorted(set(re.findall(r'(?:href|src)="([^"]+\.(?:jpg|tif|png))"', page)))
            print(page_url, 'links:', *links[:40], sep='\n  ')
            urls = urls + [l for l in links if '07782' in l]
        except Exception as e:
            print('page failed', page_url, e)
if name == 'stars':
    try:
        page = get('https://svs.gsfc.nasa.gov/4851/').decode('utf-8', 'replace')
        links = sorted(set(re.findall(r'href="([^"]+\.(?:jpg|png|tif|exr))"', page)))
        print('svs links:', *links, sep='\n  ')
        urls = urls + ['https://svs.gsfc.nasa.gov' + l if l.startswith('/') else l
                       for l in links if 'print' in l and l.endswith('.jpg')]
    except Exception as e:
        print('svs page failed:', e)

for url in urls:
    try:
        raw = get(url)
        print('got', url, len(raw), 'bytes, starts with', raw[:12])
        img = Image.open(io.BytesIO(raw))
        img.load()
        print('downloaded %s: %d bytes, %s %s' % (url, len(raw), img.size, img.mode))
        break
    except Exception as e:
        print('failed %s: %s' % (url, e))
else:
    sys.exit('no source worked for ' + name)

img = img.convert(mode).resize(size, Image.LANCZOS)
buf = io.BytesIO()
img.save(buf, 'JPEG', quality=quality, optimize=True)
emit(name + '.jpg', buf.getvalue())
