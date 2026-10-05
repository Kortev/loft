"""Builds universe.bin, the universe in Ginnungagap's block (needs NumPy and SciPy).

A cosmic web: the galaxies of a universe are not scattered evenly but lie on the walls between great empty
voids, thickest along the edges where walls meet (the filaments) and thickest of all where filaments meet
(the clusters). The voids here are the cells of a jittered Voronoi diagram; a galaxy is kept with a
probability that rises the nearer it lies to a wall, an edge and a corner of it. The universe is the cube
from -1 to 1. The first galaxy is the big spiral the feed starts beside; the rest are shuffled, so the first
few thousand of them are a fair sample of the whole, for universes seen from far off.

Layout (little-endian): int32 galaxies, int32 glows; then each galaxy as float32 x, y, z, int8 pole x, y, z
(times 127), uint8 type (0 spiral, 1 elliptical), uint16 radius (in units of 1e-5), uint8 shape, uint8 r, g, b,
uint8 brightness, uint8 spare (24 bytes); then each glow, the faint light of the web itself seen from far off,
as float32 x, y, z, uint16 radius (1e-5), uint8 r, g, b, uint8 brightness, uint8 spare, uint8 spare (20 bytes).

Usage: python3 tools/gen_universe.py
"""
import os
import struct

import numpy as np
from scipy.spatial import cKDTree

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'shootingstar', 'textures', 'feed',
                   'universe.bin')
GALAXIES = 100_000
GLOWS = 14_000
# The big spiral the feed starts beside: where it is, which way it faces, how big it is.
HERO = np.array([0.12, 0.05, -0.12])
HERO_POLE = np.array([0.45, 1.0, 0.55]) / np.linalg.norm([0.45, 1.0, 0.55])
HERO_RADIUS = 0.012

rng = np.random.default_rng(4096113)


VOIDS = 7


def voids():
    """One void centre in each cell of a 7 x 7 x 7 grid over the cube, and a ring of cells round it so the
    web runs right out to the faces."""
    cells = np.arange(-1, VOIDS + 1)
    ix, iy, iz = np.meshgrid(cells, cells, cells, indexing='ij')
    size = 2.0 / VOIDS
    corner = np.stack([ix, iy, iz], -1).reshape(-1, 3) * size - 1.0
    return corner + size * 0.5 + rng.uniform(-0.42, 0.42, corner.shape) * size


CENTRES = voids()
TREE = cKDTree(CENTRES)


def web(p):
    """How near each point is to a wall, a filament and a cluster of the web (each 1 on it, falling to 0)."""
    dist, near = TREE.query(p, k=4)
    nd = dist * dist
    c = CENTRES[near]

    def wall(k):
        # Distance from the point to the bisecting plane between its nearest void and its k-th nearest.
        return (nd[:, k] - nd[:, 0]) / (2.0 * np.linalg.norm(c[:, k] - c[:, 0], axis=1))

    d12, d13, d14 = wall(1), wall(2), wall(3)
    # Off a wall, off the edge where three cells meet, off the corner where four do.
    return (np.exp(-d12 / 0.006), np.exp(-np.sqrt(d12 * d12 + d13 * d13) / 0.007),
            np.exp(-np.sqrt(d12 * d12 + d13 * d13 + d14 * d14) / 0.016))


def sample(count, weight):
    """Rejection-samples count points of the cube with probability weight(wall, filament, cluster) (at most 1)."""
    points, clusters = [], []
    have = 0
    while have < count:
        p = rng.uniform(-1.0, 1.0, (400_000, 3))
        w, f, n = web(p)
        keep = rng.uniform(0.0, 1.0, len(p)) < weight(w, f, n)
        points.append(p[keep])
        clusters.append(n[keep])
        have += keep.sum()
    return np.concatenate(points)[:count], np.concatenate(clusters)[:count]


def unit(v):
    return v / np.linalg.norm(v, axis=-1, keepdims=True)


def galaxies():
    p, cluster = sample(GALAXIES - 1, lambda w, f, n: np.minimum(1.0, 0.0015 + 0.03 * w + 0.4 * f + 1.0 * n))
    n = len(p)
    # Clusters are full of ellipticals, the field of spirals.
    elliptical = rng.uniform(0, 1, n) < 0.12 + 0.55 * cluster
    # Small beside the gaps between them, as galaxies are: a few dozen of their own widths apart along a filament.
    radius = np.exp(rng.normal(np.log(0.0009), 0.4, n))
    radius[elliptical] *= 0.85
    # The giants at the hearts of clusters.
    giants = elliptical & (cluster > 0.75) & (rng.uniform(0, 1, n) < 0.25)
    radius[giants] *= 2.5
    radius = np.clip(radius, 0.0003, 0.004)
    pole = unit(rng.normal(size=(n, 3)))
    shape = np.where(elliptical, rng.uniform(0.0, 0.6, n), rng.uniform(0.0, 1.0, n))
    # Spirals blue-white, some violet, a few magenta or teal with new stars; ellipticals gold to orange.
    spiral = np.array([0.72, 0.8, 1.0])
    colour = np.tile(spiral, (n, 1))
    pick = rng.uniform(0, 1, n)
    colour[pick < 0.35] = [0.82, 0.72, 1.0]
    colour[(pick >= 0.35) & (pick < 0.45)] = [1.0, 0.55, 0.88]
    colour[(pick >= 0.45) & (pick < 0.52)] = [0.55, 0.95, 1.0]
    warm = rng.uniform(0, 1, n)
    colour[elliptical] = np.stack([np.ones(n), 0.78 + 0.12 * warm, 0.5 + 0.15 * warm], -1)[elliptical]
    colour *= rng.uniform(0.85, 1.0, (n, 1))
    bright = np.clip(rng.uniform(0.55, 1.0, n) * (radius / 0.0009) ** 0.5, 0.2, 1.0)
    order = rng.permutation(n)
    p, elliptical, radius, pole, shape, colour, bright = (a[order] for a in (p, elliptical, radius, pole, shape, colour, bright))
    hero = (HERO, False, HERO_RADIUS, HERO_POLE, 0.35, np.array([0.78, 0.8, 1.0]), 1.0)
    return [hero] + list(zip(p, elliptical, radius, pole, shape, colour, bright))


def glows():
    p, cluster = sample(GLOWS, lambda w, f, n: np.minimum(1.0, 0.002 * w + 0.3 * f + 1.0 * n))
    n = len(p)
    radius = rng.uniform(0.01, 0.024, n) * (1.0 + 0.8 * cluster)
    # The filaments blue-violet, the clusters where they meet warm.
    warm = np.clip((cluster - 0.35) / 0.5, 0.0, 1.0)[:, None]
    colour = (np.array([0.42, 0.42, 1.0]) * (1.0 - warm) + np.array([1.0, 0.6, 0.72]) * warm) * rng.uniform(0.7, 1.0, (n, 1))
    bright = rng.uniform(0.4, 1.0, n)
    return list(zip(p, radius, colour, bright))


def byte(x):
    return int(round(float(np.clip(x, 0.0, 1.0)) * 255))


def main():
    gal = galaxies()
    glow = glows()
    data = bytearray(struct.pack('<ii', len(gal), len(glow)))
    for p, elliptical, radius, pole, shape, colour, bright in gal:
        data += struct.pack('<fffbbbBHBBBBBB', *p, *(int(round(float(v) * 127)) for v in pole), 1 if elliptical else 0,
                            int(round(radius * 1e5)), byte(shape), *(byte(v) for v in colour), byte(bright), 0)
    for p, radius, colour, bright in glow:
        data += struct.pack('<fffHBBBBBB', *p, int(round(radius * 1e5)), *(byte(v) for v in colour), byte(bright), 0, 0)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'wb') as f:
        f.write(data)
    print('wrote %s: %d galaxies, %d glows, %d bytes' % (os.path.relpath(OUT), len(gal), len(glow), len(data)))


if __name__ == '__main__':
    main()
