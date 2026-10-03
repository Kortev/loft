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
    'jupiter': (['https://photojournal.jpl.nasa.gov/jpeg/PIA07782.jpg',
                 'https://photojournal.jpl.nasa.gov/tiff/PIA07782.tif'], (2048, 1024), 'RGB', 90),
    'stars': ([SVS + 'starmap_2020_4k_gal_print.jpg', SVS + 'starmap_2020_4k_print.jpg',
               SVS + 'starmap_2020_8k_gal_print.jpg', SVS + 'milkyway_2020_4k_gal_print.jpg'], (4096, 2048), 'RGB', 90),
}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


name = sys.argv[1]
urls, size, mode, quality = ASSETS[name]
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
img.save(buf, 'JPEG', quality=quality, optimize=True, progressive=False, subsampling=0 if name == 'stars' else 2)
data = buf.getvalue()
print('=== FILE %s.jpg %d %s ===' % (name, len(data), hashlib.sha256(data).hexdigest()))
enc = base64.b64encode(data).decode('ascii')
for i in range(0, len(enc), 4000):
    print(enc[i:i + 4000])
print('=== END ===')
