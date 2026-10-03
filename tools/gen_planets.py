"""Generates the feed's planet maps (equirectangular, 1024x512):
earth.png, earth_clouds.png and jupiter.png under assets/shootingstar/textures/feed/."""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
from geo import ISLANDS, LAKES, NORTH_AMERICA  # noqa: E402
from noise import fbm3, sphere_coords  # noqa: E402

W, H = 1024, 512
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'shootingstar', 'textures', 'feed')


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = t[..., None] if np.ndim(t) == 2 else t
    return a * (1 - t) + b * t


def land_mask():
    scale = 4
    img = Image.new('L', (W * scale, H * scale), 0)
    draw = ImageDraw.Draw(img)

    def poly(points, fill):
        pts = [((lon + 180) / 360 * W * scale, (90 - lat) / 180 * H * scale) for lon, lat in points]
        draw.polygon(pts, fill=fill)

    poly(NORTH_AMERICA, 255)
    for points in ISLANDS.values():
        poly(points, 255)
    for points in LAKES:
        poly(points, 0)
    img = img.filter(ImageFilter.GaussianBlur(scale * 0.8)).resize((W, H), Image.LANCZOS)
    return np.asarray(img, dtype=np.float64) / 255.0


def warp_mask(mask, x, y, z):
    """Roughen the coastlines by sampling the mask through a noise displacement."""
    dx = (fbm3(x * 6, y * 6, z * 6, 5, seed=11) - 0.5) * 9.0
    dy = (fbm3(x * 6 + 7, y * 6, z * 6, 5, seed=12) - 0.5) * 9.0
    yy, xx = np.mgrid[0:H, 0:W]
    sx = np.clip(np.round(xx + dx).astype(int) % W, 0, W - 1)
    sy = np.clip(np.round(yy + dy).astype(int), 0, H - 1)
    return mask[sy, sx]


def earth():
    x, y, z, lon, lat = sphere_coords(W, H)
    mask = warp_mask(land_mask(), x, y, z)
    land = smoothstep(0.42, 0.58, mask)

    # Ocean: deep blue, lighter over the continental shelves.
    shelf = np.asarray(Image.fromarray((land * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6)), dtype=np.float64) / 255
    depth_noise = fbm3(x * 3, y * 3, z * 3, 4, seed=3)
    deep = np.array([8, 28, 66], dtype=np.float64)
    mid = np.array([16, 52, 102], dtype=np.float64)
    shallow = np.array([34, 96, 142], dtype=np.float64)
    ocean = mix(mix(deep, mid, np.clip(depth_noise * 1.3 - 0.2, 0, 1)), shallow, np.clip(shelf * 1.6, 0, 1))

    # Land colour by climate band plus noise.
    alat = np.abs(lat)
    n = fbm3(x * 5, y * 5, z * 5, 6, seed=21)
    n2 = fbm3(x * 12, y * 12, z * 12, 4, seed=22)
    tropical = np.array([34, 74, 32], dtype=np.float64)
    temperate = np.array([78, 98, 54], dtype=np.float64)
    boreal = np.array([38, 62, 44], dtype=np.float64)
    tundra = np.array([112, 106, 86], dtype=np.float64)
    desert = np.array([196, 164, 108], dtype=np.float64)
    steppe = np.array([150, 136, 92], dtype=np.float64)
    ice = np.array([236, 240, 246], dtype=np.float64)

    col = mix(tropical, temperate, smoothstep(14, 30, alat + (n - 0.5) * 10))
    col = mix(col, boreal, smoothstep(46, 56, alat + (n - 0.5) * 8))
    col = mix(col, tundra, smoothstep(60, 68, alat + (n - 0.5) * 6))

    lonw = lon + (fbm3(x * 3, y * 3, z * 3, 4, seed=25) - 0.5) * 22
    latw = lat + (fbm3(x * 3 + 9, y * 3, z * 3, 4, seed=26) - 0.5) * 14

    def region(lon0, lon1, lat0, lat1, soft=8.0):
        return (smoothstep(lon0 - soft, lon0 + soft, lonw) * (1 - smoothstep(lon1 - soft, lon1 + soft, lonw))
                * smoothstep(lat0 - soft, lat0 + soft, latw) * (1 - smoothstep(lat1 - soft, lat1 + soft, latw)))

    dry = np.maximum.reduce([
        region(-17, 33, 14, 31) * 1.1,            # Sahara
        region(35, 58, 14, 31),                   # Arabia
        region(118, 145, -31, -19),               # Australian interior
        region(-117, -103, 25, 37) * 0.8,         # SW United States / Mexico
        region(-72, -68, -28, -18),               # Atacama
        region(88, 118, 37, 46) * 0.8,            # Gobi
        region(15, 25, -27, -18) * 0.8,           # Kalahari
        region(53, 72, 24, 34) * 0.7,             # Iran
    ])
    dry = np.clip(dry * (0.55 + 0.9 * fbm3(x * 7, y * 7, z * 7, 5, seed=24)), 0, 1)
    col = mix(col, steppe, smoothstep(0.15, 0.45, dry))
    col = mix(col, desert, smoothstep(0.45, 0.8, dry))
    # Soil and relief: gentle brightness variation plus a few darker highland patches.
    highland = smoothstep(0.58, 0.75, fbm3(x * 4, y * 4, z * 4, 5, seed=31))
    col = col * (0.88 + 0.2 * n2)[..., None]
    col = mix(col, np.array([104, 88, 66], dtype=np.float64), highland * 0.45)
    # Ice caps: Antarctica, Greenland and the high Arctic islands.
    arctic = region(-80, -10, 58, 90, 4) * smoothstep(62, 70, lat + (n - 0.5) * 8)
    icy = np.maximum(smoothstep(-64, -68, lat), arctic)
    col = mix(col, ice, np.clip(icy, 0, 1))

    rgb = mix(ocean, col, land)
    # Arctic sea ice.
    pack = smoothstep(76, 84, lat + (n - 0.5) * 6) * (1 - land)
    rgb = mix(rgb, np.array([210, 222, 232], dtype=np.float64), pack * 0.9)
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB')


def clouds():
    x, y, z, lon, lat = sphere_coords(W, H)
    wx = fbm3(x * 2.5, y * 2.5, z * 2.5, 4, seed=41) - 0.5
    wy = fbm3(x * 2.5 + 3, y * 2.5, z * 2.5, 4, seed=42) - 0.5
    wz = fbm3(x * 2.5, y * 2.5 + 5, z * 2.5, 4, seed=43) - 0.5
    n = fbm3((x + wx * 0.9) * 4, (y + wy * 0.5) * 7, (z + wz * 0.9) * 4, 6, seed=44)
    alat = np.abs(lat)
    # Busy tropics, clear subtropics, stormy mid-latitudes.
    band = 0.85 + 0.25 * np.exp(-(alat / 8) ** 2) - 0.22 * np.exp(-((alat - 24) / 8) ** 2) + 0.2 * np.exp(-((alat - 55) / 12) ** 2)
    density = smoothstep(0.42, 0.68, n * band)
    alpha = np.clip(density * 0.95, 0, 1)
    rgba = np.zeros((H, W, 4), dtype=np.uint8)
    shade = 236 + 19 * smoothstep(0.5, 0.9, n)
    rgba[..., 0] = np.clip(shade, 0, 255)
    rgba[..., 1] = np.clip(shade, 0, 255)
    rgba[..., 2] = np.clip(shade + 4, 0, 255)
    rgba[..., 3] = (alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, 'RGBA')


def jupiter():
    x, y, z, lon, lat = sphere_coords(W, H)
    # Turbulence: bands are stretched east-west and sheared by the jets.
    t1 = fbm3(x * 3, y * 18, z * 3, 5, seed=51) - 0.5
    t2 = fbm3(x * 9, y * 40, z * 9, 4, seed=52) - 0.5
    shear = np.sin(np.radians(lat) * 9.0) * 6.0
    t3 = fbm3(x * 20, y * 90, z * 20, 3, seed=56) - 0.5
    wobble_lat = lat + t1 * 9.0 + t2 * 3.5 + t3 * 1.2 + np.sin(np.radians(lon * 3 + lat * 7)) * 1.2
    cream = np.array([236, 224, 204], dtype=np.float64)
    white = np.array([246, 240, 228], dtype=np.float64)
    tan = np.array([206, 172, 132], dtype=np.float64)
    brown = np.array([158, 106, 72], dtype=np.float64)
    rust = np.array([176, 104, 66], dtype=np.float64)
    polar = np.array([150, 140, 128], dtype=np.float64)

    # (lat_from, lat_to, colour) bands from north to south.
    bands = [
        (90, 48, polar), (48, 40, tan), (40, 35, cream), (35, 29, brown), (29, 24, cream), (24, 18, tan),
        (18, 8, rust), (8, -6, white), (-6, -19, brown), (-19, -27, cream), (-27, -33, tan),
        (-33, -38, cream), (-38, -46, tan), (-46, -90, polar),
    ]
    rgb = np.zeros((H, W, 3), dtype=np.float64)
    weight = np.zeros((H, W), dtype=np.float64)
    for hi, lo, colour in bands:
        w = smoothstep(lo - 1.6, lo + 1.6, wobble_lat) * (1 - smoothstep(hi - 1.6, hi + 1.6, wobble_lat))
        rgb += w[..., None] * colour
        weight += w
    rgb /= np.maximum(weight, 1e-6)[..., None]

    # Fine streaks, eddies and festoons.
    streak = fbm3(x * 6 + shear * 0.02, y * 60, z * 6, 4, seed=53)
    rgb *= (0.86 + 0.28 * streak)[..., None]
    eddies = smoothstep(0.6, 0.85, fbm3(x * 16, y * 34, z * 16, 4, seed=57))
    rgb = mix(rgb, np.array([132, 86, 60], dtype=np.float64), eddies * 0.35 * (1 - smoothstep(40, 55, np.abs(lat))))
    mottle = fbm3(x * 10, y * 10, z * 10, 5, seed=58)
    rgb = mix(rgb, np.array([118, 112, 108], dtype=np.float64), smoothstep(45, 60, np.abs(lat)) * (0.3 + 0.5 * mottle))
    festoon = smoothstep(0.62, 0.8, fbm3(x * 14, y * 30, z * 14, 3, seed=54)) * np.exp(-((lat - 7) / 3) ** 2)
    rgb = mix(rgb, np.array([120, 92, 82], dtype=np.float64), festoon * 0.7)

    # Great Red Spot near 22 S.
    dlon = ((lon - 40 + 180) % 360) - 180
    grs = ((dlon / 16.0) ** 2 + ((lat + 22.5) / 7.0) ** 2)
    swirl = fbm3(x * 20, y * 20, z * 20, 3, seed=55)
    core = smoothstep(1.0, 0.55, grs + (swirl - 0.5) * 0.4)
    rim = smoothstep(1.35, 1.0, grs) * (1 - core)
    rgb = mix(rgb, np.array([192, 96, 62], dtype=np.float64) * (0.85 + 0.3 * swirl)[..., None], core * 0.9)
    rgb = mix(rgb, np.array([236, 214, 190], dtype=np.float64), rim * 0.4)

    # A few white ovals in the southern temperate belt.
    for olon, olat in ((-80, -33), (-40, -34), (110, -32), (170, -40)):
        d = ((((lon - olon + 180) % 360) - 180) / 4.0) ** 2 + ((lat - olat) / 2.2) ** 2
        rgb = mix(rgb, white, smoothstep(1.0, 0.4, d) * 0.85)

    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB')


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (('earth', earth), ('earth_clouds', clouds), ('jupiter', jupiter)):
        fn().save(os.path.join(OUT, name + '.png'), optimize=True)
        with open(os.path.join(OUT, name + '.png.mcmeta'), 'w') as f:
            f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')
        print('wrote', name)


if __name__ == '__main__':
    main()
