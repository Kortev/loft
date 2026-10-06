"""Builds yggdrasil.bin, the world tree that stands in the void and puts the world back (needs NumPy).

Not a model but light, drawn the way the universes are: tens of thousands of points, each a sprite. The trunk is a
braid of filaments twisting round each other; it splits into limbs and those into finer and finer branches,
an ash spreading wide, the filaments thinning as they go; at their tips the crown, clusters of stars; the nine
worlds hang in it as bright orbs; and the roots run out over the floor of the void, one long root reaching to where
the shooter stands. Every point knows when it grows (so the tree can grow from the roots up) and how far it is
along the tree from the foot of the trunk (so light can run up and down it).

The tree is one unit tall, its foot at the origin, +Y up, the long root running out along +Z (one unit long).

Layout (little-endian): int32 count, then per point float32 x, y, z, uint8 r, g, b, brightness, uint16 size (1e-4),
uint16 grow (0..65535), uint16 along (1e-4 units of tree), uint8 kind (0 filament, 1 star, 2 world, 3 haze, 4 the long root, stretched by the game), uint8 seed
(24 bytes).

Usage: python3 tools/gen_yggdrasil.py
"""
import os
import struct

import numpy as np

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'client', 'resources', 'assets', 'shootingstar', 'textures', 'feed',
                   'yggdrasil.bin')
rng = np.random.default_rng(9)

points = []  # x, y, z, r, g, b, brightness, size, grow, along, kind


def rot(axis, angle):
    axis = axis / np.linalg.norm(axis)
    a = np.cos(angle / 2)
    b, c, d = -axis * np.sin(angle / 2)
    return np.array([[a * a + b * b - c * c - d * d, 2 * (b * c + a * d), 2 * (b * d - a * c)],
                     [2 * (b * c - a * d), a * a + c * c - b * b - d * d, 2 * (c * d + a * b)],
                     [2 * (b * d + a * c), 2 * (c * d - a * b), a * a + d * d - b * b - c * c]])


def frame(d):
    d = d / np.linalg.norm(d)
    u = np.cross(d, [0.0, 0.0, 1.0] if abs(d[2]) < 0.9 else [1.0, 0.0, 0.0])
    u /= np.linalg.norm(u)
    return u, np.cross(d, u)


def mix(c0, c1, t):
    return tuple(a + (b - a) * t for a, b in zip(c0, c1))


VIOLET = (0.62, 0.42, 1.0)
PALE = (0.9, 0.86, 1.0)
CYAN = (0.45, 0.85, 1.0)
GOLD = (1.0, 0.78, 0.45)
PINK = (1.0, 0.55, 0.85)


def strands(path, radius, count, twist, grow0, grow1, along0, colour, bright, size, spacing=0.0016, taper=0.0, kind=0):
    """Filaments braided round a centre line: path is (n, 3) points, radius (n,) the braid's radius along it."""
    seg = np.linalg.norm(np.diff(path, axis=0), axis=1)
    s = np.concatenate([[0.0], np.cumsum(seg)])
    total = s[-1]
    n = max(2, int(total / spacing))
    t = np.linspace(0.0, total, n)
    centre = np.stack([np.interp(t, s, path[:, k]) for k in range(3)], axis=1)
    rad = np.interp(t, s, radius)
    tangent = np.gradient(centre, axis=0)
    for j in range(count):
        offset = j / count * 2 * np.pi + rng.uniform(0, 0.4)
        wobble = rng.uniform(0.7, 1.0)
        turn = offset + t * twist * (1.0 if j % 2 == 0 else -0.6)
        for i in range(n):
            u, v = frame(tangent[i])
            p = centre[i] + (u * np.cos(turn[i]) + v * np.sin(turn[i])) * rad[i] * wobble
            k = t[i] / total
            c = mix(colour[0], colour[1], k)
            core = 0.6 + 0.4 * np.sin(turn[i] * 3.0 + j) ** 2
            points.append((*p, *c, bright * core * (1.0 - taper * k), size * (0.7 + 0.6 * rng.random()), grow0 + (grow1 - grow0) * k,
                           along0 + t[i], kind))
    return total


def curve(start, direction, length, bend, droop, steps=24):
    """A branch's centre line: on from start, turning a little each step and sagging under its own weight."""
    p = np.array(start, float)
    d = np.array(direction, float) / np.linalg.norm(direction)
    axis = np.cross(d, rng.normal(size=3))
    out = [p.copy()]
    for _ in range(steps):
        d = rot(axis, bend / steps) @ d
        d[1] -= droop / steps
        d /= np.linalg.norm(d)
        p = p + d * length / steps
        out.append(p.copy())
    return np.array(out), d


TIPS = []


def branch(start, direction, length, radius, depth, grow0, along):
    path, end_dir = curve(start, direction, length, rng.uniform(-1.2, 1.2), 0.05 * depth)
    count = max(1, int(round(7 * radius / 0.02))) if depth < 4 else 1
    grow1 = grow0 + 0.1 + 0.02 * depth
    colour = (mix(PALE, VIOLET, 0.3 + 0.15 * depth), mix(VIOLET, CYAN, min(1.0, 0.3 * depth)))
    total = strands(path, np.linspace(radius, radius * 0.6, len(path)), count, 9.0 + 6 * depth, grow0, grow1, along, colour,
                    0.8 - 0.12 * depth, 0.0014 + radius * 0.02, taper=0.85 if depth >= 3 else 0.3)
    end = path[-1]
    if depth >= 4 or radius < 0.002:
        TIPS.append((end, end_dir, grow1, along + total))
        return
    kids = 3 if depth < 3 else 2
    for k in range(kids):
        spread = rng.uniform(0.35, 0.8)
        around = end_dir.copy()
        u, v = frame(end_dir)
        a = k / kids * 2 * np.pi + rng.uniform(0, 1.2)
        d = end_dir * np.cos(spread) + (u * np.cos(a) + v * np.sin(a)) * np.sin(spread)
        d[1] += 0.25
        branch(end, d, length * rng.uniform(0.62, 0.78), radius * 0.62, depth + 1, grow1 - 0.02, along + total)


def arc(p0, p1, lift, n=40):
    """A curved line from p0 to p1, bowed out by lift."""
    t = np.linspace(0.0, 1.0, n)[:, None]
    mid = (p0 + p1) / 2 + lift
    return (1 - t) ** 2 * p0 + 2 * (1 - t) * t * mid + t ** 2 * p1


def ring(y, radius, grow, along, colour, bright):
    n = int(2 * np.pi * radius / 0.0025)
    for i in range(n):
        a = i / n * 2 * np.pi
        p = np.array([np.cos(a) * radius, y + 0.004 * np.sin(a * 7.0), np.sin(a) * radius])
        dash = 0.5 + 0.9 * (np.sin(a * 24.0) > -0.2)
        points.append((*p, *colour, bright * dash, 0.0014, grow + 0.06 * i / n, along + radius, 0))


def tree():
    # Not a tree so much as what a tree means here: the axis the worlds hang on, a braid of light standing up out of
    # the void and on up past where it can be seen, fading at both ends.
    ts = np.linspace(0.0, 1.0, 60)
    axis = np.stack([0.012 * np.sin(ts * 9.0), -0.15 + ts * 1.45, 0.012 * np.cos(ts * 7.0)], axis=1)
    radius = 0.03 + 0.03 * np.sin(ts * np.pi * 3.0) ** 2
    strands(axis, radius, 7, 9.0, 0.08, 0.3, 0.0, (mix(PALE, VIOLET, 0.4), mix(VIOLET, CYAN, 0.5)), 0.55, 0.0018)
    strands(axis, radius * 0.25, 3, 30.0, 0.06, 0.28, 0.0, (PALE, PALE), 1.4, 0.0026)
    # A haze of light round it all, the way the web of a universe glows from far off.
    for _ in range(7000):
        y = rng.uniform(-0.15, 1.25)
        p = np.array([0.0, y, 0.0]) + rng.normal(size=3) * np.array([0.2, 0.0, 0.2]) * (0.5 + 0.9 * np.sin(y * 3.0) ** 2)
        c = mix(VIOLET, CYAN, rng.random()) if rng.random() < 0.8 else PINK
        points.append((*p, *c, rng.uniform(0.02, 0.06), rng.uniform(0.02, 0.05), min(1.0, 0.1 + y * 0.4), y, 3))
    # Three levels on it, the nine worlds round them, three to a level on rings of their own, each reached by a limb
    # of light out of the axis that forks and forks again into fine tendrils.
    worlds = []
    for level, (y, r, g0, colour) in enumerate([(0.1, 0.5, 0.3, (VIOLET, CYAN)), (0.52, 0.58, 0.42, (PALE, VIOLET)),
                                                 (0.96, 0.42, 0.54, (PALE, GOLD))]):
        ring(y, r, g0, y, mix(colour[0], colour[1], 0.5), 0.55)
        ring(y + 0.004, r * 0.55, g0 + 0.04, y, colour[0], 0.35)
        for k in range(3):
            a = k / 3 * 2 * np.pi + level * 0.9 + rng.uniform(-0.2, 0.2)
            w = np.array([np.cos(a) * r, y + rng.uniform(-0.04, 0.06), np.sin(a) * r])
            worlds.append((w, g0 + 0.12, y + r, colour))
            start = np.array([0.0, y - 0.05, 0.0])
            path = arc(start, w, np.array([0.0, 0.07, 0.0]))
            strands(path, np.linspace(0.012, 0.004, len(path)), 4, 30.0, g0, g0 + 0.1, y, colour, 1.0, 0.0018)
            # Tendrils off it, forking out into nothing.
            for f in range(10):
                q = path[int(rng.integers(8, len(path) - 4))]
                d = (q - start) / np.linalg.norm(q - start) + rng.normal(scale=0.7, size=3)
                d[1] = abs(d[1]) * 0.6
                branch(q, d, rng.uniform(0.12, 0.26), 0.005, 3, g0 + 0.08, y + 0.2)
    # Threads between the worlds, level to level, looping out round the axis.
    for i, (w0, g0, al0, c0) in enumerate(worlds):
        for j in (i + 3, i + 4):
            if j < len(worlds):
                w1 = worlds[j][0]
                out = (w0 + w1) / 2
                out[1] = 0.0
                path = arc(w0, w1, out * 0.6, n=50)
                strands(path, np.full(len(path), 0.003), 2, 40.0, g0 + 0.1, g0 + 0.2, al0, (c0[1], worlds[j][3][0]), 0.6, 0.0013)
    # The worlds themselves, and a haze of stars round each.
    for k, (w, g0, al0, colour) in enumerate(worlds):
        points.append((*w, *[GOLD, CYAN, PINK][k % 3], 4.0, 0.02, g0, al0, 2))
        for _ in range(60):
            p = w + rng.normal(scale=0.06, size=3)
            points.append((*p, *colour[1], rng.uniform(0.05, 0.14), rng.uniform(0.03, 0.06), g0, al0, 3))
        for _ in range(140):
            p = w + rng.normal(scale=0.03, size=3)
            points.append((*p, *[GOLD, PALE, PINK, CYAN][int(rng.integers(4))], rng.uniform(0.4, 1.2), rng.uniform(0.0015, 0.004),
                           g0 + rng.uniform(0.0, 0.1), al0, 1))
    # The crown: no leaves, only the light thinning out into stars above the top level.
    for _ in range(5000):
        p = np.array([0.0, 1.05, 0.0]) + rng.normal(size=3) * np.array([0.38, 0.14, 0.38])
        points.append((*p, *[GOLD, PALE, CYAN][int(rng.integers(3))], rng.uniform(0.2, 0.9), rng.uniform(0.0012, 0.003),
                       min(1.0, 0.6 + rng.uniform(0.0, 0.3)), 1.1, 1))
    # The roots: a fork-and-fork-again net out over the floor of the void all round.
    def root(start, direction, length, radius, depth, grow0, along):
        path, end_dir = curve(start, direction, length, rng.uniform(-0.8, 0.8), 0.0, steps=30)
        path[:, 1] = -0.15 - 0.003 * depth
        grow1 = grow0 + 0.18 * length / 0.4
        total = strands(path, np.linspace(radius, radius * 0.4, len(path)), max(1, int(radius / 0.004)), 12.0, grow0, grow1, along,
                        (mix(PALE, VIOLET, 0.5), CYAN), 0.8, 0.0018)
        if depth < 4:
            for k in range(2):
                nd = rot(np.array([0.0, 1.0, 0.0]), rng.uniform(-0.9, 0.9)) @ end_dir
                root(path[-1], nd, length * 0.62, radius * 0.6, depth + 1, grow1 - 0.03, along + total)
    for k in range(7):
        a = k / 7 * 2 * np.pi + 0.35
        root(np.array([0.0, -0.15, 0.0]), np.array([np.sin(a), 0.0, np.cos(a)]), rng.uniform(0.25, 0.35), 0.016, 1, 0.02, 0.0)
    # The long root, wandering out to where the shooter stands. Laid out one unit long: the game stretches it (z and
    # how far along it is) out to them, however far that is.
    n = 80
    zs = np.linspace(0.0, 1.0, n)
    long_root = np.stack([0.03 * np.sin(zs * 9.0) * np.minimum(1.0, (1.0 - zs) * 4), np.full(n, -0.15), zs], axis=1)
    strands(long_root, np.linspace(0.012, 0.004, n), 5, 8.0, 0.15, 0.5, 0.0, (PALE, CYAN), 1.2, 0.0022, spacing=0.0015, kind=4)


def main():
    tree()
    data = bytearray(struct.pack('<i', len(points)))
    for x, y, z, r, g, b, bright, size, grow, along, kind in points:
        data += struct.pack('<fffBBBBHHHBB', x, y, z, int(r * 255), int(g * 255), int(b * 255), int(min(1.0, bright / 4.0) * 255),
                            int(min(65535, size * 1e4)), int(min(1.0, max(0.0, grow)) * 65535), int(min(65535, max(0.0, along) * 1e4)), kind,
                            int(rng.integers(256)))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'wb') as f:
        f.write(data)
    print('%d points, %d tips, %.1f MB' % (len(points), len(TIPS), len(data) / 1e6))


if __name__ == '__main__':
    main()
