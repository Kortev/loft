"""Small vectorised value-noise helpers shared by the asset generators."""
import numpy as np


def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0xFFFFFF)


def value3(x, y, z, seed=0):
    """Trilinear value noise in [0, 1] with smoothstep interpolation."""
    x0, y0, z0 = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    fz = fz * fz * (3 - 2 * fz)
    x0, y0, z0 = x0.astype(np.int64), y0.astype(np.int64), z0.astype(np.int64)
    out = 0
    for dx in (0, 1):
        wx = fx if dx else 1 - fx
        for dy in (0, 1):
            wy = fy if dy else 1 - fy
            for dz in (0, 1):
                wz = fz if dz else 1 - fz
                out = out + wx * wy * wz * _hash(x0 + dx, y0 + dy, z0 + dz, seed)
    return out


def fbm3(x, y, z, octaves=5, seed=0, lacunarity=2.03, gain=0.5):
    total = 0.0
    amp = 1.0
    norm = 0.0
    for i in range(octaves):
        total = total + amp * value3(x, y, z, seed + i * 101)
        norm += amp
        x, y, z = x * lacunarity, y * lacunarity, z * lacunarity
        amp *= gain
    return total / norm


def sphere_coords(width, height):
    """Unit-sphere xyz for every pixel of an equirectangular map (lon -180..180, lat 90..-90)."""
    lon = (np.arange(width) + 0.5) / width * 2 * np.pi - np.pi
    lat = np.pi / 2 - (np.arange(height) + 0.5) / height * np.pi
    lon, lat = np.meshgrid(lon, lat)
    x = np.cos(lat) * np.cos(lon)
    y = np.sin(lat)
    z = np.cos(lat) * np.sin(lon)
    return x, y, z, np.degrees(lon), np.degrees(lat)
