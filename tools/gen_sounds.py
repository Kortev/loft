"""Synthesises the mod's sound effects and encodes them as Ogg Vorbis (needs NumPy, SciPy and ffmpeg).

Everything is built from noise, oscillators, filters and convolution reverb, the way a sound designer
would layer recorded elements: a transient, a body, a tail and the space around it. Sounds heard by
the shooter alone (the uplink feed and the cinematic close-ups) are stereo; sounds placed in the world
for everyone are mono, because Minecraft only positions mono sounds.

Usage: python3 tools/gen_sounds.py [--wav DIR] [name ...]
"""
import os
import subprocess
import sys
import tempfile
import wave
import zlib

import numpy as np
from scipy import signal
from scipy.ndimage import maximum_filter1d, uniform_filter1d

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'shootingstar', 'sounds')
rng = np.random.default_rng(20261003)


# =============================================================================================
# Building blocks
# =============================================================================================

def ns(seconds):
    return int(round(seconds * SR))


def times(seconds):
    return np.arange(ns(seconds)) / SR


def white(n):
    return rng.standard_normal(n)


def pink(n):
    """1/f noise, shaped in the frequency domain."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    spec /= np.sqrt(f)
    y = np.fft.irfft(spec, n)
    return y / (np.std(y) + 1e-12)


def brown(n):
    """1/f^2 noise without its drift."""
    y = np.cumsum(rng.standard_normal(n))
    y = hp(y, 18)
    return y / (np.std(y) + 1e-12)


def _sos(kind, freq, order):
    nyq = SR * 0.5
    if isinstance(freq, (tuple, list)):
        freq = [min(max(f, 10.0), nyq * 0.95) for f in freq]
    else:
        freq = min(max(freq, 10.0), nyq * 0.95)
    return signal.butter(order, freq, kind, fs=SR, output='sos')


def lp(x, freq, order=4):
    return signal.sosfilt(_sos('lowpass', freq, order), x)


def hp(x, freq, order=4):
    return signal.sosfilt(_sos('highpass', freq, order), x)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos('bandpass', (lo, hi), order), x)


def sweep_filter(x, kind, freq_curve, order=2, width=1.0, block=128):
    """Time-varying Butterworth filter: coefficients follow freq_curve (Hz per sample), state carried
    across blocks so the sweep is smooth. For band-pass, width is the band in octaves."""
    out = np.zeros_like(x)
    zi = None
    for i in range(0, len(x), block):
        f = float(freq_curve[min(i, len(freq_curve) - 1)])
        if kind == 'bandpass':
            k = 2 ** (width / 2)
            sos = _sos('bandpass', (f / k, f * k), order)
        else:
            sos = _sos(kind, f, order)
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        out[i:i + block], zi = signal.sosfilt(sos, x[i:i + block], zi=zi)
    return out


def resonator(x, freq_curve, q=30.0, block=64):
    """A ringing band-pass (two-pole resonance) that can glide: whistles, shrieks, tonal hums."""
    out = np.zeros_like(x)
    y1 = y2 = 0.0
    for i in range(0, len(x), block):
        f = float(freq_curve[min(i, len(freq_curve) - 1)])
        w = 2 * np.pi * f / SR
        r = np.exp(-w / (2 * q))
        a1 = -2 * r * np.cos(w)
        a2 = r * r
        g = (1 - r * r) * 0.5
        seg = x[i:i + block]
        o = np.empty_like(seg)
        for j in range(len(seg)):
            y = g * seg[j] - a1 * y1 - a2 * y2
            y2, y1 = y1, y
            o[j] = y
        out[i:i + block] = o
    return out


def comb(x, freq, feedback=0.7):
    """Feedback comb filter: the hollow ring of a pipe or a gap, at freq and all its harmonics."""
    d = max(1, int(round(SR / freq)))
    a = np.zeros(d + 1)
    a[0], a[d] = 1.0, -feedback
    return signal.lfilter([1.0 - feedback], a, x)


def curve(n, points, kind='linear'):
    """Piecewise curve over n samples through (time in s, value) points."""
    t = np.arange(n) / SR
    pt = np.array([p[0] for p in points])
    pv = np.array([p[1] for p in points], float)
    if kind == 'log':
        return np.exp(np.interp(t, pt, np.log(np.maximum(pv, 1e-9))))
    return np.interp(t, pt, pv)


def decay(n, tau, delay=0.0):
    t = np.arange(n) / SR - delay
    return np.where(t < 0, 0.0, np.exp(-np.maximum(t, 0) / tau))


def attack_decay(n, attack, tau, delay=0.0):
    t = np.arange(n) / SR - delay
    rise = np.clip(t / max(attack, 1e-4), 0, 1)
    return np.where(t < 0, 0.0, rise * rise * (3 - 2 * rise) * np.exp(-np.maximum(t - attack, 0) / tau))


def fade(x, fade_in=0.0, fade_out=0.0):
    x = x.copy()
    n = x.shape[-1]
    a = ns(fade_in)
    b = ns(fade_out)
    if a:
        x[..., :a] *= np.linspace(0, 1, a) ** 2
    if b:
        x[..., n - b:] *= np.linspace(1, 0, b) ** 2
    return x


def sine(freq, n=None, phase=0.0):
    """Sine whose frequency may change per sample."""
    if np.isscalar(freq):
        freq = np.full(n, float(freq))
    return np.sin(2 * np.pi * np.cumsum(freq) / SR + phase)


def saw(freq, n=None, phase=None):
    """Band-limited sawtooth (polyBLEP)."""
    if np.isscalar(freq):
        freq = np.full(n, float(freq))
    dt = freq / SR
    p = (np.cumsum(dt) + (rng.random() if phase is None else phase)) % 1.0
    y = 2 * p - 1
    # polyBLEP at the wrap
    m = p < dt
    tt = p[m] / dt[m]
    y[m] -= tt + tt - tt * tt - 1
    m = p > 1 - dt
    tt = (p[m] - 1) / dt[m]
    y[m] -= tt * tt + tt + tt + 1
    return y


def supersaw(freq, n, voices=7, detune=0.012, spread=0.8, mono_below=160.0):
    """Detuned saw stack, returned stereo. The bass is summed to the middle, so beating voices cannot
    leave one side louder than the other."""
    out = np.zeros((2, n))
    base = freq if not np.isscalar(freq) else np.full(n, float(freq))
    for v in range(voices):
        k = (v - (voices - 1) / 2) / max(1, (voices - 1) / 2)
        y = saw(base * (1 + detune * k + rng.normal(0, detune * 0.1)), n)
        out += pan(y, k * spread)
    out /= voices
    if mono_below:
        low = lp(out.mean(axis=0), mono_below, 4)
        out = np.vstack([hp(c, mono_below, 4) + low for c in out])
    return out


def sat(x, drive=2.0):
    return np.tanh(drive * x) / np.tanh(drive)


def crush(x, bits=8.0, hold=1.0):
    """Digital damage: each value held for `hold` samples (a lower sample rate, aliasing and all) and rounded
    to a few bits. Either may change over time."""
    n = x.shape[-1]
    step = np.floor(np.cumsum(1.0 / np.maximum(np.broadcast_to(np.asarray(hold, float), (n,)), 1.0)))
    new = np.concatenate([[True], np.diff(step) > 0])
    idx = np.maximum.accumulate(np.where(new, np.arange(n), 0))
    q = 2.0 ** (np.asarray(bits, float) - 1)
    return np.round(x[..., idx] * q) / q


def norm(x, peak=0.98):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def rms_norm(x, level=0.2):
    r = np.sqrt(np.mean(x ** 2))
    return x * (level / r) if r > 0 else x


def pan(x, position):
    """Equal-power pan of a mono signal; position -1 (left) .. 1 (right), scalar or per sample."""
    a = (np.asarray(position) + 1) * np.pi / 4
    return np.vstack([x * np.cos(a), x * np.sin(a)])


def stereo(x):
    return x if x.ndim == 2 else np.vstack([x, x])


def mono(x):
    return x if x.ndim == 1 else x.mean(axis=0)


def decorrelated(n, make, amount=1.0):
    """Two channels from the same recipe with independent randomness (wide, not phasey)."""
    left = make(n)
    right = make(n)
    mid = (left + right) / 2
    return np.vstack([mid + (left - mid) * amount, mid + (right - mid) * amount])


def doppler(x, distance, speed_of_sound=343.0):
    """Delays x by distance/c at each sample (distance in metres per sample), giving the pitch bend
    of a source flying past."""
    delay = distance / speed_of_sound * SR
    idx = np.arange(len(x)) - delay
    return np.interp(idx, np.arange(len(x)), x, left=0.0, right=0.0)


def grains(n, rate, make, start=0.0, end=None, gain_curve=None, spread=1.0):
    """Scatter short sounds in time (a Poisson process whose rate may vary): debris, crackle, rain."""
    out = np.zeros((2, n))
    end = n / SR if end is None else end
    t = start
    while t < end:
        r = rate(t) if callable(rate) else rate
        if r <= 0:
            t += 0.01
            continue
        t += rng.exponential(1.0 / r)
        if t >= end:
            break
        g = make()
        i = ns(t)
        if i >= n:
            break
        k = min(len(g), n - i)
        level = gain_curve(t) if gain_curve else 1.0
        out[:, i:i + k] += pan(g[:k], rng.uniform(-spread, spread)) * level
    return out


def varispeed(x, rate):
    """Plays x back at a speed that changes over time (rate[i] input samples per output sample), like a tape
    machine slowing down or spinning up: pitch and tempo move together."""
    phase = np.concatenate([[0.0], np.cumsum(rate)[:-1]])
    phase = np.minimum(phase, len(x) - 1)
    return np.interp(phase, np.arange(len(x)), x)


def loudness(x, window=0.4):
    """Loudest short-term level in dB: RMS over the loudest 400 ms."""
    m = x.mean(axis=0) if x.ndim == 2 else x
    w = ns(window)
    if len(m) <= w:
        return 20 * np.log10(np.sqrt(np.mean(m ** 2)) + 1e-12)
    power = uniform_filter1d(m ** 2, size=w)
    return 10 * np.log10(power.max() + 1e-12)


def limit(x, ceiling=0.89, release=0.06):
    """Look-ahead peak limiter to an absolute ceiling (-1 dBFS), channels linked: shaves the transients that
    stick out once a sound is brought up to its level, leaving the level itself alone."""
    det = np.abs(x).max(axis=0) if x.ndim == 2 else np.abs(x)
    peak = maximum_filter1d(det, size=max(3, ns(0.006)))
    g = np.minimum(1.0, ceiling / np.maximum(peak, 1e-9))
    g = np.minimum(g, uniform_filter1d(g, size=max(3, ns(release))))
    g = uniform_filter1d(g, size=max(3, ns(0.003)))
    y = x * g
    return y * min(1.0, ceiling / max(np.abs(y).max(), 1e-9))


def rock(size=1.0):
    """One stone landing: a dull thud and a gritty click."""
    n = ns(0.12 * size + 0.03)
    t = np.arange(n) / SR
    f = rng.uniform(90, 260) / size
    thud = np.sin(2 * np.pi * f * t) * np.exp(-t / (0.018 * size))
    click = hp(white(n), rng.uniform(1500, 4000)) * np.exp(-t / rng.uniform(0.002, 0.008))
    return (thud * 0.8 + click * rng.uniform(0.3, 0.9)) * min(rng.lognormal(-1.0, 0.6), 1.2)


def crackle_pop():
    n = ns(0.012)
    t = np.arange(n) / SR
    return hp(white(n), 2500) * np.exp(-t / rng.uniform(0.0006, 0.003)) * min(rng.lognormal(-1.6, 0.7), 0.8)


def spark_zap():
    n = ns(0.03)
    t = np.arange(n) / SR
    f = rng.uniform(2000, 7000)
    return (np.sign(np.sin(2 * np.pi * f * t)) * 0.3 + hp(white(n), 3000)) * np.exp(-t / 0.006) * rng.uniform(0.2, 1)


def chime(freq, n, tau=0.4, ratios=(1.0, 2.32, 4.25, 6.63), bright=0.45):
    """A struck crystal: inharmonic partials, each higher one quieter and shorter (none above 18 kHz)."""
    t = np.arange(n) / SR
    y = np.zeros(n)
    for k, r in enumerate(ratios):
        if freq * r < 18000:
            partial = np.sin(2 * np.pi * freq * r * t + rng.uniform(0, 2 * np.pi))
            y += bright ** k * partial * np.exp(-t / (tau / (1 + 1.5 * k)))
    return y * np.clip(t / 0.0006, 0, 1)


def glitch():
    """One fault in a digital signal: a few ms of noise crushed to a couple of bits, or a stuck square tone,
    switched on and off with hard edges."""
    n = ns(rng.uniform(0.002, 0.025))
    if rng.random() < 0.6:
        y = crush(white(n) * 0.5, rng.uniform(1.0, 3.0), rng.uniform(3.0, 40.0))
    else:
        y = np.sign(np.sin(2 * np.pi * rng.uniform(300, 6000) * np.arange(n) / SR + 0.1))
    edge = np.minimum(1.0, np.minimum(np.arange(n), np.arange(n)[::-1]) / 12.0)
    return y * edge * min(rng.lognormal(-1.0, 0.6), 1.0)


# --- spaces ------------------------------------------------------------------------------------

def impulse(seconds, early=(), damping=4000.0, low_extra=1.6, density=1.0, pre=0.01):
    """Stereo impulse response: discrete early reflections (time, gain, pan) and a diffuse tail that
    loses its highs faster than its lows."""
    n = ns(seconds + pre + 0.05)
    t = np.arange(n) / SR
    ir = np.zeros((2, n))
    for when, gain, where in early:
        i = ns(pre + when)
        if i < n:
            tap = pan(np.array([gain]), where)
            ir[:, i] += tap[:, 0]
    tau = seconds / 6.9
    env = np.exp(-np.maximum(t - pre, 0) / tau) * (t > pre)
    tail = decorrelated(n, lambda m: white(m))
    lows = np.vstack([lp(c, 500) for c in tail]) * np.exp(-np.maximum(t - pre, 0) / (tau * low_extra)) * (t > pre)
    highs = np.vstack([hp(c, 500) for c in tail]) * env
    # high frequencies die away sooner: a falling low-pass over the high band
    cutoff = damping * np.exp(-np.maximum(t - pre, 0) / (tau * 1.5)) + 300
    highs = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=1) for c in highs])
    ir += (lows * 0.9 + highs * 0.7) * 0.06 * density
    return ir


OUTDOOR = None
HALL = None
METAL = None
SPACE = None


def spaces():
    global OUTDOOR, HALL, METAL, SPACE
    if OUTDOOR is None:
        # Open ground with hills: a few strong, late echoes off ridges and a long soft tail.
        echoes = [(0.07, 0.35, -0.6), (0.16, 0.28, 0.7), (0.41, 0.22, -0.2), (0.73, 0.18, 0.8), (1.2, 0.14, -0.8),
                  (1.9, 0.10, 0.3), (2.7, 0.07, -0.4)]
        OUTDOOR = impulse(5.5, echoes, damping=2500, low_extra=2.0, density=0.6)
        HALL = impulse(3.2, [(0.03, 0.4, -0.3), (0.05, 0.35, 0.4), (0.09, 0.25, 0.0)], damping=6000, density=1.2)
        METAL = impulse(1.8, [(0.011, 0.5, -0.5), (0.017, 0.45, 0.5), (0.026, 0.4, 0.0), (0.041, 0.3, -0.3)],
                        damping=9000, low_extra=1.0, density=1.6)
        SPACE = impulse(4.5, [(0.12, 0.2, -0.7), (0.21, 0.2, 0.7)], damping=5000, low_extra=1.3, density=0.8)
    return OUTDOOR, HALL, METAL, SPACE


def reverb(x, ir, wet=0.3, dry=1.0):
    x = stereo(x)
    n = x.shape[1]
    out = np.zeros((2, n + ir.shape[1]))
    out[:, :n] += x * dry
    for c in range(2):
        wetc = signal.fftconvolve(x[c], ir[c])[:out.shape[1]]
        out[c, :len(wetc)] += wetc * wet
    return out


class Mix:
    """A stereo canvas to lay sounds on by time."""

    def __init__(self, seconds):
        self.buf = np.zeros((2, ns(seconds)))

    def add(self, x, at=0.0, gain=1.0, position=None):
        if x.ndim == 1:
            x = pan(x, 0.0 if position is None else position) * (np.sqrt(2) if position is None else 1.0)
        i = ns(at)
        n = self.buf.shape[1]
        if i >= n:
            return self
        k = min(x.shape[1], n - i)
        self.buf[:, i:i + k] += x[:, :k] * gain
        return self

    def out(self):
        return self.buf


def dynamics(x, threshold_db=-20.0, ratio=3.0, attack=0.004, release=0.12, limit_db=-1.0):
    """Compressor then look-ahead limiter, both linked across channels: tames the spikes (cracks,
    pops, the odd loud grain) so the body of the sound comes up when it is normalised."""
    st = x.ndim == 2
    det = np.abs(x).max(axis=0) if st else np.abs(x)
    env = uniform_filter1d(maximum_filter1d(det, size=max(3, ns(attack))), size=max(3, ns(release)))
    level = 20 * np.log10(env + 1e-9)
    over = np.maximum(level - threshold_db, 0.0)
    gain = 10 ** (-over * (1 - 1 / ratio) / 20)
    gain = uniform_filter1d(gain, size=max(3, ns(attack)))
    y = x * gain
    det = np.abs(y).max(axis=0) if st else np.abs(y)
    peak = maximum_filter1d(det, size=max(3, ns(0.005)))
    ceiling = 10 ** (limit_db / 20) * np.max(det)
    g2 = np.minimum(1.0, ceiling / np.maximum(peak, 1e-9))
    g2 = uniform_filter1d(g2, size=max(3, ns(0.003)))
    return y * np.minimum(g2, 1.0)


def master(x, peak=0.95, drive=1.0, low_cut=25.0, squash=0.0):
    """Final polish: rumble filter, gentle saturation, peak level, click-free ends."""
    x = stereo(x) if x.ndim == 2 else x
    if x.ndim == 2:
        x = np.vstack([hp(c, low_cut, 2) for c in x])
    else:
        x = hp(x, low_cut, 2)
    x = norm(x, 1.0)
    if squash > 0:
        x = dynamics(x, threshold_db=-6.0 - 18.0 * squash, ratio=1.5 + 4.0 * squash)
        x = norm(x, 1.0)
    if drive > 1.0:
        x = sat(x, drive)
    x = norm(x, peak)
    # Trim the silent end of a reverb tail.
    level = np.abs(x).max(axis=0) if x.ndim == 2 else np.abs(x)
    loud = np.nonzero(level > peak * 0.001)[0]
    if len(loud):
        end = min(x.shape[-1], loud[-1] + ns(0.05))
        x = x[..., :end]
    return fade(x, 0.002, 0.03)


def cut(x, at, total=None):
    """Stops x dead at `at` seconds (a 3 ms ramp, no tail) and pads it with silence to `total` seconds. Goes
    after master(), whose closing fade would soften the edge, so mix a little past the cut."""
    total = at if total is None else total
    k = min(x.shape[-1], ns(at))
    y = np.zeros(x.shape[:-1] + (ns(total),))
    y[..., :k] = x[..., :k]
    r = min(k, ns(0.003))
    y[..., k - r:k] *= np.linspace(1.0, 0.0, r)
    return y


# =============================================================================================
# The sounds
# =============================================================================================

def uplink_lock():
    """Targeting computer: two crisp beeps, the lock chirp, a sub thump, the satellite slewing round
    and a squelch of radio static."""
    m = Mix(1.6)
    for at, f in ((0.0, 1568.0), (0.11, 2093.0)):
        n = ns(0.075)
        t = np.arange(n) / SR
        beep = (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t)) * attack_decay(n, 0.003, 0.05)
        beep = np.round(beep * 24) / 24  # a hint of digital grit
        m.add(pan(beep, -0.15 if at == 0 else 0.15), at, 0.5)
    chirp = sine(curve(ns(0.06), [(0, 900), (0.06, 2600)], 'log')) * attack_decay(ns(0.06), 0.002, 0.04)
    m.add(chirp, 0.23, 0.35)
    n = ns(0.9)
    thump = sine(curve(n, [(0, 78), (0.5, 36)], 'log')) * attack_decay(n, 0.004, 0.22)
    thump += lp(white(n), 180) * attack_decay(n, 0.002, 0.05) * 0.4
    m.add(sat(thump, 1.6), 0.24, 0.9)
    n = ns(0.55)
    servo = saw(curve(n, [(0, 150), (0.4, 330), (0.55, 300)], 'log'), n) * (0.8 + 0.2 * np.sin(2 * np.pi * 28 * times(0.55)))
    servo = lp(servo, 1400) * attack_decay(n, 0.08, 0.25)
    m.add(pan(servo, curve(n, [(0, 0.4), (0.55, -0.4)])), 0.32, 0.18)
    n = ns(0.16)
    gate = (rng.random(n // 220 + 1) > 0.35).repeat(220)[:n]
    m.add(decorrelated(n, lambda k: bp(white(k), 1800, 6500)) * gate * attack_decay(n, 0.004, 0.06), 0.62, 0.22)
    _, hall, _, _ = spaces()
    return master(reverb(m.out(), hall, wet=0.18), peak=0.9, squash=0.3)


def uplink_denied():
    n = ns(0.12)
    t = np.arange(n) / SR
    buzz = (np.sign(np.sin(2 * np.pi * 180 * t)) * 0.5 + np.sin(2 * np.pi * 360 * t) * 0.25) * attack_decay(n, 0.004, 0.06)
    buzz = lp(buzz, 3500)
    x = np.concatenate([buzz, np.zeros(ns(0.06)), buzz, np.zeros(ns(0.12))])
    return master(x, peak=0.8)


def camera_rise():
    """Wind tearing past as the camera climbs over the target, swelling into the feed."""
    total = 1.7
    n = ns(total)
    body = decorrelated(n, pink)
    centre = curve(n, [(0, 250), (0.9, 900), (1.2, 2400), (1.25, 2400), (1.7, 1200)], 'log')
    wind = np.vstack([sweep_filter(c, 'bandpass', centre * (1 + 0.15 * np.sin(2 * np.pi * 0.9 * times(total) + k)),
                                   order=2, width=1.6) for k, c in enumerate(body)])
    gust = 0.75 + 0.25 * np.sin(2 * np.pi * 2.3 * times(total)) * np.sin(2 * np.pi * 0.7 * times(total) + 1)
    swell = curve(n, [(0, 0.05), (0.3, 0.25), (1.0, 0.7), (1.2, 1.0), (1.24, 1.0), (1.7, 0.0)])
    m = Mix(total)
    m.add(wind * gust * swell, 0, 1.0)
    low = lp(brown(n), 140) * curve(n, [(0, 0), (1.0, 0.6), (1.2, 1.0), (1.7, 0)])
    m.add(low, 0, 0.6)
    return master(m.out(), peak=0.8, squash=0.3)


def feed_zoom():
    """The feed connects: a modem handshake and static, then a deep whoosh as the view pulls back
    from orbit."""
    m = Mix(2.8)
    t0 = 0.0
    for k in range(9):
        f = 1200 if k % 2 == 0 else 2200
        n = ns(0.018)
        tone = np.sin(2 * np.pi * f * np.arange(n) / SR) * np.hanning(n)
        m.add(tone, t0, 0.18, position=-0.2 + 0.05 * k)
        t0 += 0.022
    n = ns(0.35)
    m.add(decorrelated(n, lambda k: bp(white(k), 1500, 7000)) * attack_decay(n, 0.01, 0.1), 0.18, 0.25)
    n = ns(2.4)
    w = decorrelated(n, pink)
    centre = curve(n, [(0, 4200), (0.5, 1600), (1.6, 260), (2.4, 120)], 'log')
    whoosh = np.vstack([sweep_filter(c, 'bandpass', centre, order=2, width=2.0) for c in w])
    whoosh *= curve(n, [(0, 0), (0.25, 1.0), (1.0, 0.6), (2.4, 0)])
    m.add(whoosh, 0.3, 0.8)
    drop = sine(curve(n, [(0, 220), (1.6, 42)], 'log')) * attack_decay(n, 0.05, 0.7)
    m.add(drop, 0.3, 0.5)
    _, _, _, space = spaces()
    return master(reverb(m.out(), space, wet=0.25), peak=0.85, squash=0.3)


def feed_ambience():
    """The bed under the whole feed: a dark, slowly breathing drone that follows the shots (orbit,
    relay, wake, loading, laps, release, the journey in, re-entry) and quiet telemetry chatter on top."""
    total = 18.2
    n = ns(total)
    t = times(total)
    # Sections relative to the start of the feed (StrikeTimeline: ORBIT=0, RELAY=1.4, WAKE=2.6, LOADING=4.8,
    # LAPS=5.8, RELEASE=10.0, DEBRIS=11.5, TRANSIT=12.7, TERMINAL=15.1, REENTRY=16.5, INBOUND=18.2). The bed drops
    # away for the slow-motion release, so the slowed sounds have the space to themselves, and sits under the
    # cruise once the camera chases the round down.
    level = curve(n, [(0, 0), (1.2, 0.55), (2.5, 0.6), (2.8, 0.85), (4.6, 0.8), (5.0, 0.55), (5.8, 0.6), (9.8, 1.0),
                      (10.0, 0.08), (10.9, 0.08), (11.3, 0.45), (12.7, 0.5), (13.2, 0.35), (15.1, 0.45), (16.5, 0.7),
                      (18.1, 0.0), (18.2, 0.0)])
    bright = curve(n, [(0, 260), (2.6, 380), (4.8, 520), (5.8, 400), (9.9, 2400), (10.0, 300), (11.5, 600), (12.7, 700),
                       (15.1, 800), (16.5, 1000), (18.2, 1800)], 'log')
    m = Mix(total)
    for f, g, det in ((55.0, 1.0, 0.004), (82.41, 0.6, 0.006), (110.0, 0.35, 0.008)):
        stack = supersaw(f * (1 + 0.003 * np.sin(2 * np.pi * 0.05 * t)), n, voices=5, detune=det, spread=0.9)
        stack = np.vstack([sweep_filter(c, 'lowpass', bright * (1 + 0.2 * np.sin(2 * np.pi * 0.11 * t + k)), order=2)
                           for k, c in enumerate(stack)])
        m.add(stack * level, 0, g)
    sub = sine(np.full(n, 41.2)) * level * 0.5
    m.add(sub, 0, 0.3)
    # A pad an octave and a fifth up, so the bed is still there on small speakers.
    for f, g in ((220.0, 0.5), (329.6, 0.35), (440.0, 0.18)):
        pad = supersaw(f * (1 + 0.002 * np.sin(2 * np.pi * 0.07 * t)), n, voices=5, detune=0.006, spread=1.0)
        pad = np.vstack([sweep_filter(c, 'lowpass', np.minimum(bright * 2.2, 5000), order=2) for c in pad])
        m.add(pad * level * curve(n, [(0, 0), (2.6, 0.4), (5.8, 0.6), (9.9, 1.0), (10.0, 0.1), (11.5, 0.5), (12.7, 0.6),
                                      (15.1, 0.7), (18.2, 0.8)]), 0, g)
    shimmer = decorrelated(n, lambda k: sine(np.full(k, 1318.5) * (1 + 0.002 * rng.standard_normal() )))
    shimmer = shimmer * (0.5 + 0.5 * np.sin(2 * np.pi * 0.23 * t)) * level * 0.04
    m.add(shimmer, 0, 1.0)
    # Telemetry: soft blips and a little radio crackle, now and then.
    for at in np.arange(0.8, total - 0.5, 1.37):
        k = ns(0.05)
        blip = np.sin(2 * np.pi * rng.choice([2637, 3136, 2349]) * np.arange(k) / SR) * attack_decay(k, 0.002, 0.02)
        m.add(blip, at + rng.uniform(-0.2, 0.2), 0.05, position=rng.uniform(-0.8, 0.8))
    m.add(grains(n, lambda s: 6 + 30 * (s > 16.5), crackle_pop, spread=0.9) * 0.25, 0, 1.0)
    _, _, _, space = spaces()
    x = reverb(m.out(), space, wet=0.35)
    # Beating voices can leave one side louder over a whole take; even it out.
    r = np.sqrt(np.mean(x ** 2, axis=1, keepdims=True))
    x *= r.mean() / np.maximum(r, 1e-9)
    return master(x, peak=0.5, squash=0.3)


def feed_relay():
    """The relay charges with a rising whine, fires its beam (crack and sizzle) and the view jumps
    after it in a Doppler whoosh."""
    m = Mix(2.6)
    n = ns(0.42)
    f = curve(n, [(0, 180), (0.42, 2900)], 'log')
    whine = (sine(f) + 0.3 * saw(f * 0.5, n)) * curve(n, [(0, 0.05), (0.42, 1.0)]) ** 1.5
    m.add(whine, 0.0, 0.35, position=0.1)
    buzz = lp(saw(np.full(n, 100.0), n), 1200) * curve(n, [(0, 0), (0.42, 0.6)])
    m.add(buzz, 0.0, 0.25)
    n = ns(0.5)
    crack = hp(white(n), 900) * attack_decay(n, 0.0005, 0.02)
    m.add(decorrelated(n, lambda k: hp(white(k), 900)) * attack_decay(n, 0.0005, 0.02), 0.4, 1.0)
    sizzle = bp(white(n), 2500, 9000) * (rng.random(n) > 0.6) * attack_decay(n, 0.005, 0.18)
    m.add(sizzle, 0.42, 0.5, position=0.3)
    zap = sine(curve(ns(0.08), [(0, 6000), (0.08, 900)], 'log')) * attack_decay(ns(0.08), 0.001, 0.03)
    m.add(zap, 0.4, 0.4)
    n = ns(1.6)
    w = pink(n)
    centre = curve(n, [(0, 6500), (0.6, 1400), (1.6, 180)], 'log')
    whoosh = sweep_filter(w, 'bandpass', centre, order=2, width=1.8) * attack_decay(n, 0.12, 0.45)
    dist = curve(n, [(0, 40), (0.5, 4), (1.6, 60)])
    tone = doppler(sine(np.full(n, 900.0)) * attack_decay(n, 0.1, 0.4), dist * 8)
    m.add(pan(whoosh + tone * 0.2, curve(n, [(0, -0.9), (1.6, 0.9)])), 0.55, 0.9)
    m.add(sine(curve(n, [(0, 90), (1.0, 35)], 'log')) * attack_decay(n, 0.05, 0.4), 0.55, 0.5)
    _, _, _, space = spaces()
    return master(reverb(m.out(), space, wet=0.3), peak=0.9, squash=0.4)


def feed_wake():
    """THE SHOOTING STAR: a huge brass-like hit as the title lands, then the ring powering up: mains
    hum swelling, coil banks slamming in one after another, arcs crackling."""
    total = 4.6
    m = Mix(total)
    # The title hit.
    n = ns(3.2)
    t = times(3.2)
    for f, g in ((49.0, 1.0), (98.0, 0.6), (146.8, 0.35)):
        brass = supersaw(np.full(n, f), n, voices=7, detune=0.01, spread=0.7)
        cutoff = curve(n, [(0, 180), (0.12, 1800), (0.6, 700), (3.2, 200)], 'log')
        brass = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in brass])
        m.add(sat(brass * attack_decay(n, 0.04, 1.1) * 2.0, 1.5), 0.1, g)
    boom = sine(curve(n, [(0, 62), (1.0, 31)], 'log')) * attack_decay(n, 0.01, 0.8)
    m.add(boom, 0.1, 0.9)
    m.add(decorrelated(ns(0.3), lambda k: lp(white(k), 3000)) * attack_decay(ns(0.3), 0.001, 0.05), 0.1, 0.35)
    # Mains hum and transformer growl swelling up.
    n = ns(total)
    t = times(total)
    hum = sum(a * np.sin(2 * np.pi * 50 * h * t + h) for h, a in ((1, 1.0), (2, 0.6), (3, 0.4), (4, 0.25), (6, 0.12)))
    hum *= 1 + 0.1 * np.sin(2 * np.pi * 4 * t)
    hum *= curve(n, [(0, 0), (0.6, 0.1), (3.6, 0.6), (4.2, 0.8), (4.6, 0)])
    m.add(stereo(lp(hum, 900)) * np.array([[1.0], [0.92]]), 0, 0.5)
    # Coil banks engaging: heavy electromagnetic thunks, accelerating.
    for k, at in enumerate((0.9, 1.45, 1.92, 2.3, 2.6, 2.84, 3.04, 3.2, 3.33)):
        n = ns(0.6)
        tt = np.arange(n) / SR
        f0 = 55 * (1.06 ** k)
        thunk = np.sin(2 * np.pi * f0 * tt) * np.exp(-tt / 0.12)
        clang = sum(a * np.sin(2 * np.pi * f * (1 + 0.04 * k) * tt) * np.exp(-tt / d) for f, a, d in
                    ((320, 0.5, 0.25), (847, 0.35, 0.15), (1490, 0.25, 0.09), (2311, 0.12, 0.05)))
        click = hp(white(n), 2000) * np.exp(-tt / 0.004)
        hit = sat(thunk * 1.2 + clang * 0.5 + click * 0.4, 1.4)
        m.add(hit, at, 0.55 + 0.04 * k, position=(-0.6 if k % 2 else 0.6) * (1 - k / 10))
    m.add(grains(ns(total), lambda s: 0 if s < 1.0 else 4 + 14 * (s - 1.0), spark_zap, spread=0.9), 0, 0.3)
    _, hall, metal, space = spaces()
    return master(reverb(m.out(), hall, wet=0.32), peak=0.95, drive=1.3, squash=0.2)


def feed_load():
    """The round seats in the breech: servos, a hydraulic hiss, the clamp slamming shut and the
    latches locking, ringing round the steel."""
    m = Mix(2.0)
    n = ns(0.3)
    servo = saw(curve(n, [(0, 640), (0.3, 1150)], 'log'), n)
    m.add(lp(servo, 2500) * attack_decay(n, 0.03, 0.12), 0.0, 0.18, position=-0.3)
    n = ns(0.4)
    hiss = decorrelated(n, lambda k: hp(white(k), 2800)) * attack_decay(n, 0.02, 0.12)
    m.add(hiss, 0.12, 0.3)
    n = ns(1.2)
    t = np.arange(n) / SR
    thud = np.sin(2 * np.pi * curve(n, [(0, 70), (0.2, 45)], 'log') * t) * np.exp(-t / 0.09)
    bell = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / d) for f, a, d in
               ((412, 0.5, 0.55), (1089, 0.4, 0.35), (1777, 0.3, 0.22), (2563, 0.2, 0.12), (3710, 0.12, 0.07)))
    trans = hp(white(n), 1200) * np.exp(-t / 0.006)
    m.add(sat(thud * 1.4 + bell * 0.6 + trans * 0.5, 1.6), 0.45, 1.0)
    for at, f in ((0.78, 3200), (0.86, 2600)):
        k = ns(0.05)
        click = hp(white(k), f) * np.exp(-np.arange(k) / SR / 0.004) + np.sin(2 * np.pi * f / 2 * np.arange(k) / SR) * \
            np.exp(-np.arange(k) / SR / 0.01) * 0.4
        m.add(click, at, 0.35, position=0.4 if f > 3000 else -0.4)
    _, _, metal, _ = spaces()
    return master(reverb(m.out(), metal, wet=0.45), peak=0.92, squash=0.3)


LAPS_SECONDS = 4.2


def lap_velocity(s):
    """Velocity as a fraction of c, s seconds into the laps."""
    return 0.0183 + (0.9612 - 0.0183) * np.clip(s / LAPS_SECONDS, 0, 1)


def coil_rate(s):
    """Coils the round passes per second, s seconds into the laps: the feed's own rate for the barrel
    (Shots.boreRate), so each coil's crack lands on the coil's flash."""
    p = np.clip(s / LAPS_SECONDS, 0, 1)
    return 20.0 * (0.25 + 9.75 * p ** 1.6)


def feed_coils():
    """The laps: each coil the round passes fires with a crack and a thump, one at a time at first, then
    faster and faster until the hits run together into a buzz that climbs with the electromagnetic whine and
    the roar of the barrel."""
    total = LAPS_SECONDS + 0.05
    n = ns(total)
    t = times(total)
    v = lap_velocity(t)
    f = 160 + 1500 * v ** 1.2
    m = Mix(total)
    whine = sine(f * (1 + 0.004 * np.sin(2 * np.pi * 6 * t))) + 0.35 * sine(2 * f) + 0.2 * saw(f * 0.5, n)
    m.add(stereo(whine * (0.15 + 0.85 * v)) * np.array([[1.0], [0.97]]), 0, 0.2)
    # The coils, in step with the picture: a hit every time the round passes one. Each hit decays in a fixed
    # time, so the first ones are separate cracks and the later ones overlap into a buzz at the coil rate.
    rate = coil_rate(t)
    phase = np.cumsum(rate) / SR
    since = (phase % 1.0) / rate
    k = np.floor(phase)
    thump = np.sin(2 * np.pi * (55 + 40 * np.exp(-since / 0.02)) * since) * np.exp(-since / 0.045)
    crack = hp(white(n), 2400) * np.exp(-since / 0.005)
    ring = np.sin(2 * np.pi * 1850 * since + k) * np.exp(-since / 0.03) * 0.5
    # Early hits ring out on their own; once they run together the buzz takes over and they thin.
    alone = np.clip(1.6 - rate / 50.0, 0.25, 1.3)
    hits = (thump * 0.9 + crack * 0.6 + ring * 0.45) * alone
    m.add(pan(hits, 0.2 * np.sin(2 * np.pi * phase / 7.0)), 0, 1.0)
    buzz = lp(saw(rate, n), 2500) * np.clip((rate - 40) / 120.0, 0, 1)
    m.add(stereo(buzz), 0, 0.12)
    roar = decorrelated(n, pink)
    roar = np.vstack([sweep_filter(c, 'lowpass', 300 + 4500 * v ** 1.5, order=2) for c in roar]) * v ** 1.5
    m.add(roar, 0, 0.35)
    m.add(grains(n, lambda s: 1 + 40 * lap_velocity(s), spark_zap, spread=1.0), 0, 0.2)
    out = m.out() * curve(n, [(0, 0), (0.01, 1), (total - 0.05, 1), (total, 0)])
    _, hall, _, _ = spaces()
    return master(reverb(out, hall, wet=0.2), peak=0.8, squash=0.3)


def feed_lap():
    """The round rips past the camera: a Doppler whoosh from left to right."""
    n = ns(0.8)
    t = times(0.8)
    dist = np.sqrt(((t - 0.3) * 120) ** 2 + 2.0 ** 2)
    body = pink(n)
    whoosh = sweep_filter(body, 'bandpass', curve(n, [(0, 4000), (0.3, 1500), (0.8, 450)], 'log'), order=2, width=1.6)
    whoosh *= 1.0 / (0.15 + dist / 10)
    tone = doppler(sine(np.full(n, 1250.0)) + 0.4 * sine(np.full(n, 2500.0)), dist) * (1.0 / (0.2 + dist / 8))
    x = pan(norm(whoosh) + norm(tone) * 0.35, np.clip((t - 0.3) * 4, -1, 1))
    return master(fade(x, 0.0, 0.2), peak=0.85)


def feed_release():
    """The release, in step with the shot (1.5 s): the flash racing up the barrel towards the camera; time
    slowing to a crawl, so everything sinks into a drone; the muzzle blast as a vast slowed-down boom with the
    arcs sizzling in slow motion; the sabot's bolts and its petals ringing as they fly apart; a reverse swell;
    and real time cracking back as the spear is gone."""
    total = 1.62
    m = Mix(total)
    # 1. Up the barrel in real time, then the tape slows: whine and coil pulses sink into a growl.
    n = ns(0.62)
    rate = curve(n, [(0, 1.0), (0.16, 1.0), (0.34, 0.07), (0.62, 0.04)], 'log')
    src_len = int(np.sum(rate)) + SR // 10
    st = np.arange(src_len) / SR
    whine_f = 700 * np.exp(st * 6.5)
    whine = sine(np.minimum(whine_f, 4200)) + 0.35 * saw(np.minimum(whine_f, 4200) * 0.5, src_len)
    pulse_rate = 30 * np.exp(st * 7.0)
    pulses = np.exp(-((np.cumsum(pulse_rate) / SR) % 1.0) * 7.0)
    thumps = lp(white(src_len) * 0.5 + saw(np.full(src_len, 110.0), src_len), 900) * pulses
    src = whine * 0.4 + thumps
    slowed = varispeed(src, rate)
    slowed = sweep_filter(slowed, 'lowpass', curve(n, [(0, 9000), (0.2, 6000), (0.4, 900), (0.62, 400)], 'log'), order=2)
    m.add(stereo(slowed * curve(n, [(0, 0.3), (0.16, 1.0), (0.3, 0.8), (0.5, 0.35), (0.62, 0.0)])) *
          np.array([[1.0], [0.92]]), 0.0, 0.55)
    # 2. The slow-motion bed: a deep hum and dark air.
    n = ns(0.8)
    t = times(0.8)
    hum = sine(np.full(n, 46.0) * (1 + 0.01 * np.sin(2 * np.pi * 1.3 * t))) + 0.5 * sine(np.full(n, 92.5))
    air = decorrelated(n, lambda k: lp(pink(k), 380))
    bed = stereo(hum * 0.6) + air
    m.add(bed * curve(n, [(0, 0), (0.12, 1.0), (0.55, 0.7), (0.8, 0.0)]), 0.2, 0.3)
    # 3. Arcs sizzling, slowed: zaps played back at a fifth of their speed.
    n = ns(0.4)
    zaps = grains(int(n * 0.2) + SR // 20, lambda q: 45, spark_zap, spread=1.0)
    zaps = np.vstack([varispeed(c, np.full(n, 0.2)) for c in zaps])
    zaps = np.vstack([lp(c, 2200) for c in zaps])
    m.add(zaps * curve(n, [(0, 0), (0.05, 1.0), (0.3, 1.0), (0.4, 0.0)]), 0.3, 0.6)
    # 4. The muzzle blast: a slowed-down thunderclap, mostly sub and dark noise, ringing on.
    n = ns(0.8)
    sub = sine(curve(n, [(0, 42), (0.4, 22), (0.8, 16)], 'log')) * attack_decay(n, 0.025, 0.26)
    m.add(sat(sub * 1.8, 1.6), 0.44, 1.0)
    body = decorrelated(n, lambda k: white(k) * 0.4 + brown(k) * 0.9)
    cutoff = curve(n, [(0, 1600), (0.15, 700), (0.8, 120)], 'log')
    body = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in body]) * attack_decay(n, 0.02, 0.2)
    m.add(sat(body * 2.5, 2.0), 0.44, 0.85)
    # 5. The sabot: three deep pops of the bolts, then the petals ringing as they part.
    for k, at in enumerate((0.715, 0.735, 0.765)):
        q = ns(0.3)
        tt = np.arange(q) / SR
        pop = np.sin(2 * np.pi * (75 - 8 * k) * tt) * np.exp(-tt / 0.06) + lp(white(q), 700) * np.exp(-tt / 0.02) * 0.6
        m.add(sat(pop * 1.3, 1.4), at, 0.55, position=(-0.5, 0.1, 0.6)[k])
    for k, side in enumerate((-0.6, 0.2, 0.7)):
        q = ns(1.0)
        tt = np.arange(q) / SR
        f0 = 150 + 23 * k
        clang = sum(a * np.sin(2 * np.pi * f0 * r * tt + k) * np.exp(-tt / d) for r, a, d in
                    ((1.0, 0.6, 0.5), (2.76, 0.45, 0.35), (5.4, 0.3, 0.22), (8.93, 0.18, 0.12)))
        m.add(clang * 0.5, 0.75 + 0.02 * k, 0.45, position=side)
    # 6. A reverse swell sucking everything back up to speed...
    n = ns(0.26)
    rev = pink(n) * np.exp(-np.arange(n) / SR / 0.07)
    rev = sweep_filter(rev[::-1], 'bandpass', curve(n, [(0, 300), (0.26, 5000)], 'log'), order=2, width=1.6)
    m.add(stereo(norm(rev)) * np.array([[0.95], [1.0]]), 0.76, 0.7)
    # 7. ...and real time back with a crack: the spear is gone, a Doppler whoosh dropping away into the
    # distance and the barrel's coils ringing down.
    q = ns(0.04)
    m.add(decorrelated(q, lambda k: hp(white(k), 1200)) * attack_decay(q, 0.0003, 0.006), 1.02, 1.6)
    q = ns(0.25)
    snap = sine(curve(q, [(0, 120), (0.25, 50)], 'log')) * attack_decay(q, 0.002, 0.05)
    m.add(sat(snap * 1.5, 1.5), 1.02, 0.7)
    q = ns(0.6)
    tt = np.arange(q) / SR
    dist = 3.0 + tt * 900
    whoosh = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 3200), (0.6, 260)], 'log'), order=2, width=1.6)
    whoosh *= 1.0 / (0.2 + dist / 60)
    m.add(pan(norm(whoosh), curve(q, [(0, 0.0), (0.6, 0.25)])), 1.02, 0.55)
    q = ns(0.55)
    ring = (sine(np.full(q, 880.0)) + 0.4 * sine(np.full(q, 1320.0))) * attack_decay(q, 0.005, 0.18)
    m.add(stereo(ring), 1.03, 0.12)
    _, _, _, space = spaces()
    return master(reverb(m.out(), space, wet=0.28)[:, :ns(total)], peak=0.97, drive=1.3)


def feed_strike():
    """The spear goes through a boulder without slowing: a whip-crack, a crunch of rock, a deep thud through the
    spear, and the debris and grit tearing back past the camera from side to side."""
    total = 0.95
    m = Mix(total)
    q = ns(0.02)
    m.add(decorrelated(q, lambda k: hp(white(k), 1500)) * attack_decay(q, 0.0002, 0.003), 0.0, 1.2)
    q = ns(0.06)
    zip_ = sine(curve(q, [(0, 4200), (0.06, 900)], 'log')) * attack_decay(q, 0.0005, 0.012)
    m.add(stereo(zip_), 0.001, 0.45)
    q = ns(0.5)
    tt = np.arange(q) / SR
    thud = np.sin(2 * np.pi * curve(q, [(0, 90), (0.5, 38)], 'log') * tt) * np.exp(-tt / 0.07)
    m.add(sat(thud * 1.6, 1.5), 0.0, 0.85)
    crunch = decorrelated(q, lambda k: bp(white(k), 300, 4200)) * attack_decay(q, 0.001, 0.035)
    m.add(sat(crunch * 2.2, 1.7), 0.002, 0.7)
    # The debris tearing past: a fast sweep down in pitch that crosses from one side to the other.
    q = ns(0.42)
    rush = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 5200), (0.42, 500)], 'log'), order=2, width=1.8)
    rush *= attack_decay(q, 0.006, 0.12)
    m.add(pan(norm(rush), curve(q, [(0, -0.2), (0.12, 0.85), (0.42, 0.9)])), 0.012, 0.7)
    n = ns(total)
    m.add(grains(n, lambda s: 420 * np.exp(-s / 0.12) + 4, lambda: rock(rng.uniform(0.12, 0.4)), end=0.55), 0.008, 0.5)
    hiss = decorrelated(n, lambda k: hp(white(k), 3800)) * attack_decay(n, 0.004, 0.16)
    m.add(hiss, 0.01, 0.3)
    _, _, _, space = spaces()
    return master(reverb(m.out(), space, wet=0.25)[:, :ns(total)], peak=0.95, drive=1.5)


def feed_cruise():
    """The bed under the journey in, from the chase out of the muzzle to the re-entry (5.7 s): a deep rushing
    drone and the spear's hum, the rush of the belt, muffled under the transfer plot, then a rising tension as
    the seeker hunts for the target, handing over to the re-entry roar."""
    total = 5.7
    n = ns(total)
    t = times(total)
    # Relative to the cue (RELEASE + 24): DEBRIS 0.3, the rock 0.85, TRANSIT 1.5, TERMINAL 3.9, the Moon 4.27,
    # the lock 4.8, REENTRY 5.3.
    m = Mix(total)
    swell = curve(n, [(0, 0.0), (0.25, 1.0), (1.5, 1.0), (1.8, 0.7), (3.7, 0.7), (4.0, 0.9), (5.2, 1.0), (5.7, 0.0)])
    bright = curve(n, [(0, 900), (0.3, 2600), (1.4, 2200), (1.7, 420), (3.8, 520), (4.1, 1400), (5.3, 3200), (5.7, 2000)],
                   'log')
    for f, g in ((36.71, 1.0), (55.0, 0.55)):
        drone = supersaw(f * (1 + 0.004 * np.sin(2 * np.pi * 0.37 * t)), n, voices=5, detune=0.006, spread=0.8)
        drone = np.vstack([sweep_filter(c, 'lowpass', bright * 0.5, order=2) for c in drone])
        m.add(drone * swell, 0, g)
    m.add(sine(np.full(n, 36.71)) * swell, 0, 0.45)
    # The rush: something going by unimaginably fast, fluttering.
    flutter = 0.75 + 0.25 * np.sin(2 * np.pi * 7.3 * t) * np.sin(2 * np.pi * 0.9 * t + 1.0)
    rush = decorrelated(n, pink)
    rush = np.vstack([sweep_filter(c, 'bandpass', bright * (1 + 0.15 * np.sin(2 * np.pi * 0.5 * t + k)), order=2, width=2.2)
                      for k, c in enumerate(rush)])
    m.add(rush * swell * flutter, 0, 0.55)
    # The spear's hum, rising with the tension as the seeker hunts.
    rise = curve(n, [(0, 1.0), (3.9, 1.0), (5.3, 1.12), (5.7, 1.12)], 'log')
    hum = sine(220.0 * rise * (1 + 0.003 * np.sin(2 * np.pi * 5.5 * t))) + 0.4 * sine(330.0 * rise) + 0.12 * sine(1760.0 * rise)
    m.add(stereo(hum * swell * curve(n, [(0, 0.5), (1.5, 0.5), (1.8, 0.3), (3.9, 0.3), (5.3, 0.8), (5.7, 0.8)])), 0, 0.12)
    # A riser under the seeker.
    q = ns(1.5)
    riser = saw(curve(q, [(0, 110), (1.5, 440)], 'log'), q) + 0.5 * saw(curve(q, [(0, 164.8), (1.5, 659.3)], 'log'), q)
    riser = sweep_filter(riser, 'lowpass', curve(q, [(0, 300), (1.5, 2600)], 'log'), order=2)
    m.add(stereo(riser * curve(q, [(0, 0), (1.1, 0.6), (1.4, 1.0), (1.5, 0.0)])), 3.85, 0.16)
    out = m.out() * curve(n, [(0, 1), (total - 0.02, 1), (total, 0)])
    _, _, _, space = spaces()
    return master(reverb(out, space, wet=0.2)[:, :n], peak=0.85, squash=0.25)


def feed_transit():
    """The transfer plot (2.4 s): a data wipe as it comes up, a blip for every label, sonar pings, the ETA readout
    whirring down, and a rising whoosh as the plot dives in on Earth."""
    total = 2.6
    n = ns(total)
    m = Mix(total)
    q = ns(0.35)
    wipe = pink(q) * np.exp(-np.arange(q) / SR / 0.1)
    wipe = sweep_filter(wipe[::-1], 'bandpass', curve(q, [(0, 300), (0.35, 6000)], 'log'), order=2, width=1.4)
    m.add(stereo(norm(wipe)) * np.array([[1.0], [0.9]]), 0.0, 0.45)
    q = ns(0.09)
    chirp = sine(curve(q, [(0, 1200), (0.09, 2400)], 'log')) * attack_decay(q, 0.002, 0.04)
    m.add(chirp, 0.06, 0.25)
    # The labels: the round's tag, Earth, Jupiter, the Sun and Mars, the close pass, the Moon.
    for at, f, side in ((0.1, 2093.0, 0.0), (0.25, 2637.0, 0.3), (0.35, 2349.0, -0.4), (0.45, 1976.0, 0.1),
                        (0.48, 2637.0, -0.2), (0.82, 3136.0, 0.25), (0.88, 3136.0, 0.25), (2.05, 2349.0, 0.0)):
        k = ns(0.06)
        blip = (np.sin(2 * np.pi * f * np.arange(k) / SR) + 0.3 * np.sin(4 * np.pi * f * np.arange(k) / SR))
        m.add(blip * attack_decay(k, 0.002, 0.018), at, 0.2, position=side)
    # Sonar pings as the plot tracks the round.
    for at in (0.15, 0.75, 1.35, 1.95):
        k = ns(0.5)
        ping = np.sin(2 * np.pi * 1568.0 * np.arange(k) / SR) * attack_decay(k, 0.003, 0.12)
        m.add(ping, at, 0.12)
    # Mars sliding by, close: a soft low swell.
    q = ns(0.7)
    by = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 300), (0.35, 700), (0.7, 250)], 'log'), order=2, width=1.2)
    m.add(pan(by * curve(q, [(0, 0), (0.35, 1.0), (0.7, 0)]), curve(q, [(0, -0.5), (0.7, 0.5)])), 0.8, 0.25)
    # The ETA readout whirring down: faint clicks, faster and faster.
    def tick():
        k = ns(0.004)
        return hp(white(k), 3000) * np.exp(-np.arange(k) / SR / 0.0008)
    m.add(grains(n, lambda s: 10 + 30 * (s / total) ** 2, tick, start=0.3, end=2.3, spread=0.3), 0, 0.08)
    # Diving in on Earth.
    q = ns(0.7)
    dive = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 200), (0.7, 3500)], 'log'), order=2, width=1.6)
    dive *= curve(q, [(0, 0), (0.55, 1.0), (0.7, 0.0)])
    m.add(stereo(norm(dive)), 1.85, 0.5)
    q = ns(0.6)
    tone = sine(curve(q, [(0, 300), (0.6, 1200)], 'log')) * curve(q, [(0, 0), (0.5, 1.0), (0.6, 0)])
    m.add(tone, 1.95, 0.08)
    _, hall, _, _ = spaces()
    return master(reverb(m.out(), hall, wet=0.22)[:, :n], peak=0.85)


def feed_locate():
    """The seeker (1.5 s): it comes up with a chirp; the Moon swoops past in a deep whoosh as it leaves the frame;
    a search growl that climbs as the brackets close on the target; the lock, a hard click and a steady high
    tone; and a whoosh as the camera pulls back out of the spear's point."""
    total = 1.55
    n = ns(total)
    m = Mix(total)
    for at, f in ((0.0, 880.0), (0.05, 1320.0)):
        k = ns(0.05)
        m.add(np.sin(2 * np.pi * f * np.arange(k) / SR) * attack_decay(k, 0.002, 0.02), at, 0.25)
    q = ns(0.12)
    gate = (rng.random(q // 200 + 1) > 0.4).repeat(200)[:q]
    m.add(decorrelated(q, lambda k: bp(white(k), 1500, 6000)) * gate * attack_decay(q, 0.003, 0.05), 0.02, 0.18)
    # The Moon: a deep whoosh that peaks as it sweeps out past the upper left.
    q = ns(0.75)
    tt = np.arange(q) / SR
    dist = np.sqrt(((tt - 0.48) * 60) ** 2 + 4.0 ** 2)
    moon = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 260), (0.48, 900), (0.75, 180)], 'log'), order=2, width=1.6)
    moon *= 1.0 / (0.2 + dist / 8.0)
    low = sine(curve(q, [(0, 70), (0.48, 52), (0.75, 34)], 'log')) * np.exp(-((tt - 0.48) / 0.14) ** 2)
    m.add(pan(norm(moon) + 0.6 * low, curve(q, [(0, 0.0), (0.48, -0.45), (0.75, -0.85)])), 0.0, 0.7)
    # The search growl: a buzzing tone, pulsing, climbing as the brackets close.
    q = ns(0.78)
    tt = np.arange(q) / SR
    f = curve(q, [(0, 420), (0.78, 760)], 'log')
    growl = np.sign(sine(f)) * 0.5 + sine(f * 2.0) * 0.3
    growl = lp(growl, 3000) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * curve(q, [(0, 9), (0.78, 22)]) * tt)))
    m.add(stereo(growl * curve(q, [(0, 0), (0.1, 0.7), (0.78, 1.0)])), 0.12, 0.13)
    # The lock.
    q = ns(0.02)
    m.add(decorrelated(q, lambda k: hp(white(k), 2500)) * attack_decay(q, 0.0003, 0.004), 0.9, 0.5)
    q = ns(0.4)
    tt = np.arange(q) / SR
    thump = np.sin(2 * np.pi * curve(q, [(0, 95), (0.4, 45)], 'log') * tt) * np.exp(-tt / 0.08)
    m.add(sat(thump * 1.4, 1.4), 0.9, 0.6)
    q = ns(0.55)
    lock = (sine(np.full(q, 1150.0)) + 0.25 * sine(np.full(q, 2300.0))) * curve(q, [(0, 0), (0.01, 1.0), (0.35, 0.8), (0.55, 0)])
    m.add(stereo(lock), 0.9, 0.12)
    # Pulling back out of the point.
    q = ns(0.6)
    pull = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 2600), (0.6, 380)], 'log'), order=2, width=1.6)
    pull *= curve(q, [(0, 0), (0.15, 1.0), (0.6, 0)])
    m.add(pan(norm(pull), curve(q, [(0, 0.0), (0.6, 0.6)])), 0.95, 0.4)
    _, hall, _, _ = spaces()
    return master(reverb(m.out(), hall, wet=0.2)[:, :n], peak=0.9)


def feed_reentry():
    """Re-entry (1.7 s): the thin hiss of the upper air, a roar building as the shock layer forms, buffeting,
    crackle and a rising shriek, a thump as the spear punches into the cloud deck, then the whiteout swell."""
    total = 1.72
    n = ns(total)
    t = times(total)
    grow = curve(n, [(0, 0.06), (0.45, 0.15), (1.1, 0.85), (1.35, 1.0), (1.6, 1.0), (1.7, 0.0), (1.72, 0.0)])
    m = Mix(total)
    thin = decorrelated(n, lambda k: hp(white(k), 5000)) * curve(n, [(0, 0.3), (0.5, 0.5), (1.0, 0.2), (1.7, 0)])
    m.add(thin, 0, 0.12)
    roar = decorrelated(n, lambda k: brown(k) * 0.8 + pink(k) * 0.5)
    roar = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 250), (0.45, 400), (1.35, 3600), (1.7, 5000)], 'log'),
                                   order=2) for c in roar])
    buffet = 1.0 + 0.35 * lp(rng.standard_normal(n), 14) / 0.12
    m.add(roar * grow * np.clip(buffet, 0.4, 1.6), 0, 0.9)
    shriek = resonator(white(n), curve(n, [(0, 900), (1.35, 2700)], 'log'), q=40) * grow ** 2.2
    m.add(stereo(norm(shriek)) * np.array([[1.0], [0.85]]), 0, 0.22)
    m.add(grains(n, lambda s: 15 + 500 * np.clip((s - 0.4) / 1.0, 0, 1) ** 2, crackle_pop, end=1.6), 0, 0.55)
    q = ns(0.45)
    tt = np.arange(q) / SR
    whomp = np.sin(2 * np.pi * curve(q, [(0, 70), (0.45, 32)], 'log') * tt) * np.exp(-tt / 0.12)
    whomp += lp(white(q), 600) * np.exp(-tt / 0.05) * 0.7
    m.add(sat(whomp * 1.3, 1.5), 1.3, 0.8)
    swell = decorrelated(ns(0.4), lambda k: pink(k)) * curve(ns(0.4), [(0, 0), (0.33, 1.0), (0.4, 0.0)])
    m.add(np.vstack([lp(c, 6000) for c in swell]), 1.3, 0.5)
    return master(m.out()[:, :n], peak=0.92, drive=1.6, squash=0.3)


def inbound(stereo_out=True):
    """The round screams down from the upper right: a tearing shriek dropping in pitch, a roar of
    torn air growing as it nears, crackle all round it."""
    total = 1.3
    n = ns(total)
    t = times(total)
    # It builds right up to the instant of impact (1.2 s in) and cuts dead: the moment of silence before the
    # boom arrives is what makes the boom.
    near = curve(n, [(0, 0.1), (0.8, 0.45), (1.15, 1.0), (1.195, 1.0), (1.205, 0.0), (1.3, 0.0)]) ** 1.3
    m = Mix(total)
    q = ns(0.28)
    riser = pink(q) * np.exp(-np.arange(q) / SR / 0.08)
    riser = sweep_filter(riser[::-1], 'bandpass', curve(q, [(0, 400), (0.28, 7000)], 'log'), order=2, width=1.4)
    m.add(stereo(norm(riser)) * np.array([[1.0], [0.95]]), 1.2 - 0.28, 0.5)
    shriek = resonator(white(n), curve(n, [(0, 2400), (1.2, 900)], 'log') * (1 + 0.02 * np.sin(2 * np.pi * 13 * t)), q=25)
    shriek2 = resonator(white(n), curve(n, [(0, 3500), (1.2, 1400)], 'log'), q=18)
    scream = norm(shriek) + 0.6 * norm(shriek2)
    where = curve(n, [(0, 0.75), (1.2, -0.1)])
    m.add(pan(scream * near, where), 0, 0.6)
    roar = pink(n) * 0.6 + brown(n) * 0.6
    roar = sweep_filter(roar, 'lowpass', curve(n, [(0, 600), (1.2, 4000)], 'log'), order=2)
    m.add(pan(norm(roar) * near, where * 0.7), 0, 0.8)
    m.add(grains(n, lambda s: 30 + 300 * s, crackle_pop, end=1.195, spread=0.8), 0, 0.18)
    x = master(m.out() * curve(n, [(0, 1), (1.198, 1), (1.205, 0), (1.3, 0)]), peak=0.92, drive=1.5, squash=0.5)
    return x if stereo_out else master(mono(x), peak=0.92)


def impact(close=True):
    """The hit. A hard crack, a ground-shaking sub thump, the roar of the blast, rock raining down for
    seconds, and the boom rolling back off the hills. close=False is the version for everyone else in
    the world: less crack, more boom."""
    total = 10.0
    n = ns(total)
    m = Mix(total)
    if close:
        k = ns(0.05)
        crack = decorrelated(k, lambda q: hp(white(q), 600)) * attack_decay(k, 0.0002, 0.006)
        m.add(crack, 0.0, 1.0)
    # Sub thump: the ground itself moving.
    k = ns(3.0)
    sub = sine(curve(k, [(0, 58), (0.25, 38), (3.0, 22)], 'log')) * attack_decay(k, 0.004, 0.9)
    m.add(sat(sub * 1.6, 1.8), 0.0, 1.0)
    # The blast: noise whose brightness collapses as it rolls away.
    k = ns(4.0)
    body = decorrelated(k, lambda q: white(q) * 0.5 + pink(q) * 0.8)
    cutoff = curve(k, [(0, 8000 if close else 2500), (0.15, 2500 if close else 1200), (1.0, 700), (4.0, 150)], 'log')
    body = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in body])
    body *= attack_decay(k, 0.003, 0.7)
    m.add(sat(body * 3.0, 2.5), 0.0, 0.9)
    # The punch you feel in your chest: a short, fat burst in the low mids.
    k = ns(1.2)
    punch = decorrelated(k, lambda q: bp(white(q), 90, 900, 2))
    m.add(sat(punch * attack_decay(k, 0.002, 0.22) * 4.0, 2.0), 0.0, 0.8)
    # A second, deeper pressure wave a moment later.
    k = ns(2.5)
    m.add(sine(curve(k, [(0, 45), (2.5, 20)], 'log')) * attack_decay(k, 0.06, 0.8), 0.12, 0.7)
    # Debris raining down all round: a dense first second, thinning out.
    rate = (lambda s: 0 if s < 0.4 else 260 * np.exp(-(s - 0.4) / 1.6) + 8) if close else \
        (lambda s: 0 if s < 0.8 else 120 * np.exp(-(s - 0.8) / 2.0) + 4)
    debris = grains(n, rate, lambda: rock(rng.uniform(0.6, 2.2)), start=0.3, end=8.0, spread=1.0)
    debris = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 7000), (2.0, 4500), (8.0, 1800)], 'log'), order=2)
                        for c in debris])
    m.add(debris, 0, 0.5 if close else 0.3)
    # The long rumble.
    k = ns(total)
    rumble = decorrelated(k, brown)
    rumble = np.vstack([lp(c, 120) for c in rumble])
    rumble *= curve(k, [(0, 0), (0.2, 1.0), (2.0, 0.6), (6.0, 0.22), (total, 0.0)])
    rumble *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.4 * times(total))
    m.add(rumble, 0, 0.55)
    outdoor, _, _, _ = spaces()
    x = reverb(m.out(), outdoor, wet=0.5 if close else 0.8)[:, :n]
    if close:
        # Deafened: after the first instant the world goes dull and the ears ring, and hearing comes back over a
        # few seconds.
        cutoff = curve(n, [(0, 20000), (0.16, 20000), (0.24, 600), (1.2, 1400), (3.8, 15000), (total, 20000)], 'log')
        x = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in x])
        tt = times(total)
        ring = np.sin(2 * np.pi * 3700 * tt) + 0.6 * np.sin(2 * np.pi * 3709 * tt)
        ring *= curve(n, [(0, 0), (0.2, 0), (0.3, 1.0), (1.0, 0.8), (3.4, 0.0), (total, 0.0)])
        x = x + stereo(ring) * np.array([[0.045], [0.04]])
    x = master(x, peak=0.98, drive=2.2, squash=0.5)
    return x if close else master(mono(x), peak=0.98)


def rumble(close=True):
    """The shock wave reaching you: a slam of pressure, a howling gust full of grit and gravel, the
    ground rumbling on."""
    total = 5.5
    n = ns(total)
    t = times(total)
    m = Mix(total)
    k = ns(0.8)
    slam = sine(curve(k, [(0, 34), (0.8, 22)], 'log')) * attack_decay(k, 0.008, 0.18)
    m.add(sat(slam * 1.5, 1.6), 0, 1.0)
    gust = decorrelated(n, pink)
    centre = curve(n, [(0, 1800), (0.4, 900), (2.5, 400), (total, 250)], 'log')
    gust = np.vstack([sweep_filter(c, 'bandpass', centre * (1 + 0.2 * np.sin(2 * np.pi * (1.3 + q) * t)), width=2.4)
                      for q, c in enumerate(gust)])
    gust *= attack_decay(n, 0.04, 1.2) * (0.8 + 0.2 * np.sin(2 * np.pi * 3.1 * t))
    m.add(gust, 0, 0.9 if close else 0.5)
    m.add(grains(n, lambda s: 400 * np.exp(-s / 0.7) + 10, lambda: rock(rng.uniform(0.2, 0.7)), end=3.0), 0,
          0.35 if close else 0.15)
    low = decorrelated(n, brown)
    low = np.vstack([lp(c, 100) for c in low]) * curve(n, [(0, 0), (0.1, 1), (3.0, 0.5), (total, 0)])
    m.add(low, 0, 0.8)
    outdoor, _, _, _ = spaces()
    x = reverb(m.out(), outdoor, wet=0.35)[:, :n]
    if close:
        # The shooter's ears are still recovering from the blast when the shock wave arrives.
        x = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 2600), (0.8, 8000), (2.2, 19000), (total, 20000)], 'log'),
                                    order=2) for c in x])
    x = master(x, peak=0.95, drive=1.8, squash=0.35)
    return x if close else master(mono(x), peak=0.95)


def aftermath(close=True):
    """After the blast: the crater burning with a low roar and crackle, stones tumbling down the
    walls, wind dragging the smoke."""
    total = 12.0
    n = ns(total)
    t = times(total)
    m = Mix(total)
    roar = decorrelated(n, lambda k: brown(k) * 0.7 + pink(k) * 0.5)
    roar = np.vstack([lp(c, 500) for c in roar])
    flicker = 0.7 + 0.3 * lp(rng.standard_normal(n), 3) / 0.05
    roar *= np.clip(flicker, 0.3, 1.3)
    m.add(roar, 0, 0.6)
    m.add(grains(n, lambda s: 25, crackle_pop, spread=0.8), 0, 0.7)
    m.add(grains(n, lambda s: 1.5, lambda: rock(rng.uniform(0.8, 2.0)), spread=0.9), 0, 0.35)
    wind = decorrelated(n, pink)
    wind = np.vstack([sweep_filter(c, 'bandpass', 500 * (1 + 0.4 * np.sin(2 * np.pi * 0.13 * t + q)), width=2.0)
                      for q, c in enumerate(wind)])
    m.add(wind, 0, 0.25)
    out = m.out() * curve(n, [(0, 0), (1.5, 1.0), (8.0, 0.7), (total, 0.0)])
    outdoor, _, _, _ = spaces()
    x = master(reverb(out, outdoor, wet=0.3)[:, :n], peak=0.6, squash=0.3)
    return x if close else master(mono(x), peak=0.6)


# --- Ω-00 Ginnungagap ---------------------------------------------------------------------------
# The Genesis Key's 40 s sequence, scored as one piece from the key turning to the song in the black: the key turns
# and the camera rises into the feed; Bifröst wakes in orbit and its window tears open; the camera dives through into
# the other universe and pulls back out of it until it is a map, and a block of it is selected and lifts out of it;
# back outside the gate the block is drawn through and the window shuts like an old screen; the bridge lances down and
# the block drops into it; the chase down into the cloud deck; the block coming down on the target; contact, the
# impact frames, the burst and its collapse, the erasure, and nothing. Under the feed one bed hands over to the next on
# the beats, the way Gungnir's ambience runs under its feed; each sound fills its slot to the tick, and the ones that
# end on a cut stop dead on it.
#
# It is in a key of its own: A, the Genesis Key's glass chord (A, C#, E), with the other universe's lydian colour over
# it (B, D#, G#). The tritone, A against D#, is the dread of the block coming down.

# GapTimeline's beats, in ticks from the key turning. The sounds are cut to fit them, so if the timeline changes, change
# it here too and make the Ginnungagap sounds again.
GAP = dict(KEY=0, RISE=42, FEED=66, GATE=92, OPEN=136, MAP=184, CUT=268, SEND=310, FALL=336, INBOUND=404, CONTACT=428,
           BLAST=458, COLLAPSE=518, ERASURE=528, NOTHING=628, RETURN=728, END=808)


def gap_at(beat, ticks=0, since='KEY'):
    """Seconds from the beat `since` to `ticks` after `beat`."""
    return (GAP[beat] + ticks - GAP[since]) / 20.0


# The other universe's chord, A major 9 sharp 11 (A, C#, E, G#, B, D#), spread from the bottom up; its stars glint in
# the top of it.
UNIVERSE = (110.0, 164.81, 246.94, 277.18, 311.13, 415.30, 440.0, 554.37, 659.26, 830.61, 987.77, 1244.51, 1318.51,
            1661.22, 1975.53, 2217.46, 2489.02, 2637.02, 3322.44, 3520.0, 3951.07, 4434.92, 4978.03, 5274.04)
STARS = UNIVERSE[13:]

VAST = None
GLASS = None
CLOSE = None
WORLD = None


def gap_spaces():
    """Ginnungagap's spaces, from a seed of their own so that a sound comes out the same whatever is made with it: the
    vastness of orbit and of the other universe, a bright glass hall, the close metal of the key's lock, and the open
    ground round the target."""
    global rng, VAST, GLASS, CLOSE, WORLD
    if VAST is None:
        outer, rng = rng, np.random.default_rng(4096113)
        VAST = impulse(7.0, [(0.19, 0.16, -0.7), (0.34, 0.14, 0.7), (0.62, 0.1, -0.2), (1.1, 0.07, 0.4)], damping=3200,
                       low_extra=1.4, density=0.7)
        GLASS = impulse(3.0, [(0.021, 0.35, -0.4), (0.037, 0.3, 0.45), (0.058, 0.22, 0.0)], damping=9000, low_extra=1.0,
                        density=1.1)
        CLOSE = impulse(1.6, [(0.011, 0.5, -0.5), (0.017, 0.45, 0.5), (0.026, 0.4, 0.0), (0.041, 0.3, -0.3)],
                        damping=9000, low_extra=1.0, density=1.5)
        WORLD = impulse(5.5, [(0.07, 0.35, -0.6), (0.16, 0.28, 0.7), (0.41, 0.22, -0.2), (0.73, 0.18, 0.8),
                              (1.2, 0.14, -0.8), (1.9, 0.10, 0.3), (2.7, 0.07, -0.4)], damping=2500, low_extra=2.0,
                        density=0.6)
        rng = outer
    return VAST, GLASS, CLOSE, WORLD


def glass(notes, n, glide=1.0, detune=0.0015):
    """The Genesis Key's voice: pure partials, each beating slowly against a detuned twin across on the other side.
    notes are (Hz, gain) pairs; glide scales their pitch, sample by sample if need be."""
    g = np.broadcast_to(np.asarray(glide, float), (n,))
    out = np.zeros((2, n))
    for f, gain in notes:
        a = sine(f * (1 - detune) * g, phase=rng.uniform(0, 2 * np.pi))
        b = sine(f * (1 + detune) * g, phase=rng.uniform(0, 2 * np.pi))
        out += np.vstack([a * 0.75 + b * 0.25, b * 0.75 + a * 0.25]) * gain
    return out


# Formants of a sung vowel, (Hz, gain).
AH = ((750.0, 1.0), (1150.0, 0.55), (2800.0, 0.25))
OO = ((330.0, 1.0), (850.0, 0.45), (2300.0, 0.15))


def choir(freq, n, vowel=OO, voices=6, glide=1.0):
    """A choir far off on one note: detuned saws, each with its own slow vibrato and its own place between the two
    sides, sung through a vowel's formants."""
    t = np.arange(n) / SR
    g = np.broadcast_to(np.asarray(glide, float), (n,))
    out = np.zeros((2, n))
    for v in range(voices):
        k = (v - (voices - 1) / 2) / ((voices - 1) / 2)
        vibrato = 1 + 0.0045 * np.sin(2 * np.pi * rng.uniform(4.6, 5.8) * t + rng.uniform(0, 2 * np.pi))
        out += pan(saw(freq * g * vibrato * (1 + 0.005 * k), n), 0.8 * k)
    out /= voices
    return sum(np.vstack([bp(c, f / 1.18, f * 1.18) for c in out]) * a for f, a in vowel)


def struck(freq, seconds, tau, ratios=(1.0, 2.32, 4.25, 6.63), bright=0.45):
    """A struck crystal (chime) let ring out to the end of its time rather than stopped there with a click."""
    y = chime(freq, ns(seconds), tau=tau, ratios=ratios, bright=bright)
    k = max(1, len(y) // 4)
    y[-k:] *= np.linspace(1.0, 0.0, k) ** 2
    return y


def twinkle():
    """A star far off: a soft glint high in the other universe's chord, its onset rounded so it shines rather than
    clicks."""
    q = ns(1.2)
    y = struck(rng.choice(STARS), 1.2, tau=rng.uniform(0.2, 0.5), ratios=(1.0, 2.76), bright=0.12)
    return y * np.clip(np.arange(q) / ns(rng.uniform(0.004, 0.02)), 0, 1) * min(rng.lognormal(-0.8, 0.5), 1.0)


def inhale(seconds, lo=300.0, hi=6000.0, tau=None):
    """A swell sucked up into whatever comes next: decaying noise played backwards, its band sweeping up as it goes."""
    q = ns(seconds)
    y = pink(q) * np.exp(-np.arange(q) / SR / (tau or seconds / 3))
    return norm(sweep_filter(y[::-1], 'bandpass', curve(q, [(0, lo), (seconds, hi)], 'log'), order=2, width=1.6))


def gate_hum(n, glide=1.0):
    """Bifröst's power: the harmonics of a hum on A, turning slowly from one side to the other."""
    t = np.arange(n) / SR
    g = np.broadcast_to(np.asarray(glide, float), (n,))
    hum = sum(a * sine(55.0 * h * g, phase=h) for h, a in ((1, 0.6), (2, 1.0), (3, 0.55), (4, 0.4), (6, 0.2), (8, 0.1)))
    turn = 2 * np.pi * 0.45 * t
    return np.vstack([hum * (0.75 + 0.25 * np.sin(turn)), hum * (0.75 + 0.25 * np.cos(turn))])


def block_hum(n, glide=1.0):
    """The block of the other universe: the bottom of its chord, humming."""
    return glass(((110.0, 0.6), (164.81, 0.4), (220.0, 0.3), (311.13, 0.12)), n, glide)


def gap_key():
    """The Genesis Key turns (3.3 s, first person, in step with KeyTurn): a crystalline shimmer rising as the key comes
    up and wakes; the tumblers lifting as it slides into the lock; the click as it starts to turn (1.5 s), the bolt
    grinding round and the deep clunk as it goes home (1.85 s), each with a jolt of bad signal; then a bright, glassy,
    detuned swell climbing out of the clunk as the camera leaves, breaking up into digital crackle in the whiteout of
    the clouds, cut dead at 3.3 s as the feed comes up."""
    total = gap_at('FEED')
    turn, home = 1.5, 1.85
    white_from = gap_at('FEED', -8)
    span = total + 0.1
    n = ns(span)
    m = Mix(span)
    # 1. The key wakes: high glassy partials beating against their detuned twins, twinkling, rising a little.
    q = ns(1.9)
    glide = curve(q, [(0, 1.0), (1.2, 1.06), (1.9, 1.07)], 'log')
    wake = curve(q, [(0, 0.0), (0.5, 0.25), (1.2, 1.0), (1.55, 0.35), (1.9, 0.0)]) ** 1.3
    shimmer = np.zeros((2, q))
    for f, g in ((1760.0, 0.5), (2637.0, 0.45), (3520.0, 0.35), (4434.9, 0.2), (5274.0, 0.18)):
        flicker = lp(rng.standard_normal(q), 7)
        flicker = 0.65 + 0.35 * np.clip(flicker / np.std(flicker), -2, 2) / 2
        pair = np.vstack([sine(f * glide * 0.9985), sine(f * glide * 1.0015)])
        shimmer += (pair * 0.8 + pair[::-1] * 0.2) * flicker * g
    m.add(shimmer * wake, 0.0, 0.3)
    notes = (2217.5, 2637.0, 2960.0, 3520.0, 3951.1, 4434.9, 5274.0, 5919.9)
    m.add(grains(q, lambda s: 0 if s > 1.45 else 4 + 30 * (s / 1.2) ** 2,
                 lambda: struck(rng.choice(notes), 0.3, tau=0.15, bright=0.3) * rng.uniform(0.2, 1.0), spread=0.9),
          0.0, 0.12)
    air = decorrelated(q, pink)
    air = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 2500), (1.2, 7000), (1.9, 6000)], 'log'), order=2,
                                  width=1.5) for c in air])
    m.add(air * wake, 0.0, 0.06)
    hum = sine(110.0 * glide) + 0.4 * sine(164.8 * glide)
    m.add(stereo(hum * wake * curve(q, [(0, 0.0), (1.2, 1.0), (1.9, 0.0)])), 0.0, 0.04)
    # 2. The lock, rung round the metal of the key: the tumblers lifting as it slides in, the click as it starts to
    # turn, the bolt grinding round, and the deep clunk as it goes home.
    lock = Mix(span)
    for k, at in enumerate((1.26, 1.31, 1.355, 1.395, 1.43)):
        q = ns(0.03)
        tt = np.arange(q) / SR
        tick = hp(white(q), 3500) * np.exp(-tt / 0.0008)
        tick += 0.3 * np.sin(2 * np.pi * (2900 + 300 * k) * tt) * np.exp(-tt / 0.006)
        lock.add(tick, at, 0.1 + 0.04 * k, position=0.15)
    q = ns(0.3)
    tt = np.arange(q) / SR
    click = hp(white(q), 2200) * np.exp(-tt / 0.002) + 0.6 * np.sin(2 * np.pi * 210 * tt) * np.exp(-tt / 0.012)
    click += sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / d) for f, a, d in
                 ((2380, 0.45, 0.035), (3870, 0.3, 0.022), (5610, 0.2, 0.012)))
    lock.add(sat(click * 1.2, 1.3), turn, 0.55, position=0.12)
    q = ns(home - turn - 0.03)
    grind = bp(white(q), 350, 2600) * (0.55 + 0.45 * np.sign(sine(curve(q, [(0, 40), (q / SR, 75)]))))
    lock.add(grind * curve(q, [(0, 0.1), (q / SR - 0.03, 1.0), (q / SR, 0.0)]), turn + 0.015, 0.13, position=0.1)
    q = ns(1.3)
    tt = np.arange(q) / SR
    thud = sine(curve(q, [(0, 96), (0.25, 46)], 'log')) * attack_decay(q, 0.002, 0.12)
    body = bp(white(q), 110, 900) * attack_decay(q, 0.001, 0.035)
    clack = hp(white(q), 1500) * attack_decay(q, 0.0002, 0.005)
    ring = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / d) for f, a, d in
               ((231.0, 0.5, 0.45), (617.0, 0.4, 0.3), (1163.0, 0.3, 0.2), (1811.0, 0.2, 0.12), (2690.0, 0.1, 0.07)))
    lock.add(sat(thud * 1.5 + body * 0.9 + clack * 0.5 + ring * 0.35, 1.6), home, 1.0, position=0.08)
    lock.add(sine(curve(q, [(0, 58), (1.3, 36)], 'log')) * attack_decay(q, 0.008, 0.2), home, 0.25)
    # Each a jolt of bad signal, as the picture glitches with them.
    for at, size in ((turn, 0.45), (home, 0.6)):
        m.add(grains(ns(0.3), lambda s: 160 * np.exp(-s / 0.05), glitch, end=0.25, spread=0.8), at, 0.5 * size)
    # 3. The swell: a glassy chord of detuned sines over a bright saw stack, an octave's climb to the cut, a reversed
    # wash sucking up into it and a sub rising under it; crushed and dropping out more and more in the whiteout.
    a = home + 0.01
    q = ns(span - a)
    rise = curve(q, [(0, 1.0), (0.8, 1.25), (total - a, 2.0), (span - a, 2.0)], 'log')
    env = curve(q, [(0, 0.0), (0.5, 0.2), (total - a, 1.0), (span - a, 1.0)]) ** 1.7
    chord = np.zeros((2, q))
    for f, g in ((880.0, 0.6), (1318.5, 0.45), (1760.0, 0.35), (2217.5, 0.25)):
        pair = np.vstack([sine(f * rise * 0.996), sine(f * rise * 1.004)])
        chord += (pair * 0.75 + pair[::-1] * 0.25) * g + stereo(sine(f * rise * 2.32)) * g * 0.15
    saws = np.zeros((2, q))
    for f, g in ((440.0, 0.5), (659.3, 0.4), (880.0, 0.3)):
        saws += supersaw(f * rise, q, voices=7, detune=0.01, spread=1.0, mono_below=0) * g
    saws = np.vstack([sweep_filter(hp(c, 350), 'lowpass', curve(q, [(0, 1200), (total - a, 12000), (span - a, 12000)],
                                                                    'log'), order=2) for c in saws])
    swell = rms_norm(chord, 0.2) + rms_norm(saws, 0.15)
    crushed = crush(norm(swell), curve(q, [(0, 8.0), (total - a, 3.0), (span - a, 3.0)]),
                    curve(q, [(0, 1.0), (total - a, 7.0), (span - a, 7.0)]))
    wreck = curve(q, [(0, 0.0), (white_from - 0.3 - a, 0.0), (total - a, 0.7), (span - a, 0.7)])
    swell = swell * (1 - wreck) + crushed * np.max(np.abs(swell)) * wreck
    blocks = q // 441 + 1
    drop = rng.random(blocks) < np.interp(np.arange(blocks) * 441 / SR + a, [white_from - 0.2, total], [0.0, 0.3])
    swell *= 1.0 - uniform_filter1d(drop.repeat(441)[:q].astype(float), 30) * 0.85
    m.add(swell * env, a, 2.4)
    k = ns(total - a)
    wash = pink(k) * np.exp(-np.arange(k) / SR / 0.45)
    wash = sweep_filter(wash[::-1], 'bandpass', curve(k, [(0, 500), (total - a, 9000)], 'log'), order=2, width=1.6)
    m.add(stereo(norm(wash)) * np.array([[1.0], [0.92]]), a, 0.6)
    m.add(sine(curve(q, [(0, 40), (total - a, 75), (span - a, 75)], 'log')) * env, a, 0.1)
    # 4. Digital crackle, through the whiteout to the cut.
    m.add(grains(n, lambda s: 20 + 400 * ((s - 2.5) / (total - 2.5)) ** 2, glitch, start=2.5, end=total, spread=0.9),
          0, 0.7)
    _, hall, close, _ = gap_spaces()
    x = reverb(m.out(), hall, wet=0.25)[:, :n] + reverb(lock.out(), close, wet=0.35)[:, :n]
    return cut(master(x, peak=0.95, squash=0.15), total)


def gap_ambience():
    """The bed under the feed, from out of the clouds to the dive (5.9 s, FEED to MAP): orbit's dark drone on A,
    breathing slowly, with a cold glass pad high over it and now and then a glint of telemetry; Bifröst's power
    humming in as the camera finds it, turning from side to side and brightening as its emitters light; a breath
    drawn in before the window tears, and the other universe's colour in the pad once it is open; and the whole of it
    rising and sucked forward as the camera dives through the window, cut dead at the flash."""
    total = gap_at('MAP', since='FEED')
    gate = gap_at('GATE', since='FEED')
    lit = gap_at('GATE', 40, since='FEED')
    tear = gap_at('OPEN', 8, since='FEED')
    dive = gap_at('OPEN', 34, since='FEED')
    span = total + 0.1
    n = ns(span)
    t = times(span)
    m = Mix(span)
    level = curve(n, [(0, 0.0), (0.7, 0.5), (gate, 0.55), (gate + 0.6, 0.75), (lit, 0.95), (tear - 0.5, 0.9),
                      (tear, 0.75), (tear + 0.4, 0.9), (dive, 0.85), (total, 1.3), (span, 1.3)])
    bright = curve(n, [(0, 220), (gate, 280), (lit, 600), (tear, 520), (tear + 0.3, 900), (dive, 800), (total, 3000),
                       (span, 3000)], 'log')
    glide = curve(n, [(0, 1.0), (dive, 1.0), (total, 1.06), (span, 1.06)], 'log')
    for f, g, det in ((55.0, 1.0, 0.004), (82.41, 0.55, 0.006), (110.0, 0.3, 0.008)):
        stack = supersaw(f * glide * (1 + 0.003 * np.sin(2 * np.pi * 0.06 * t)), n, voices=5, detune=det, spread=0.9)
        stack = np.vstack([sweep_filter(c, 'lowpass', bright * (1 + 0.2 * np.sin(2 * np.pi * 0.13 * t + k)), order=2)
                           for k, c in enumerate(stack)])
        m.add(stack * level, 0, g)
    m.add(stereo(sine(55.0 * glide) * level), 0, 0.25)
    # Bifröst's power, from the moment the camera finds the gate.
    power = curve(n, [(0, 0.0), (gate, 0.0), (gate + 0.7, 0.45), (lit, 1.0), (span, 1.1)])
    m.add(np.vstack([lp(c, 1200) for c in gate_hum(n, glide)]) * power * level, 0, 0.1)
    # A cold pad high over it, the other universe's colour in it once the window is open.
    pad = glass(((880.0, 0.3), (1318.51, 0.22), (1975.53, 0.1)), n, glide)
    pad += glass(((1661.22, 0.16), (2489.02, 0.08)), n, glide) * curve(n, [(0, 0.0), (tear, 0.0), (tear + 0.6, 1.0),
                                                                         (span, 1.0)])
    m.add(pad * (0.6 + 0.4 * np.sin(2 * np.pi * 0.17 * t - 1.0)) * level *
          curve(n, [(0, 0.0), (1.0, 0.6), (lit, 1.0), (span, 1.0)]), 0, 0.05)
    air = decorrelated(n, pink)
    air = np.vstack([sweep_filter(c, 'bandpass', bright * 2.5, order=2, width=2.0) for c in air])
    m.add(air * level * (0.7 + 0.3 * np.sin(2 * np.pi * 0.23 * t)), 0, 0.05)
    # Telemetry: now and then a glint of glass.
    for at in np.arange(0.9, dive, 1.13):
        q = ns(0.12)
        m.add(struck(rng.choice((2637.02, 3322.44, 3520.0, 3951.07)), 0.12, tau=0.03, ratios=(1.0, 2.76), bright=0.2),
              at + rng.uniform(-0.15, 0.15), 0.05, position=rng.uniform(-0.7, 0.7))
    # The breath drawn in before the window tears.
    m.add(stereo(inhale(0.6, 200, 3000)) * np.array([[1.0], [0.92]]), tear - 0.6, 0.12)
    vast = gap_spaces()[0]
    x = reverb(m.out(), vast, wet=0.3)[:, :n]
    # Beating voices can leave one side louder over a whole take; even it out.
    r = np.sqrt(np.mean(x ** 2, axis=1, keepdims=True))
    x *= r.mean() / np.maximum(r, 1e-9)
    return cut(master(x, peak=0.5, squash=0.3), total)


def gap_wake():
    """Bifröst wakes (2.6 s from GATE+10, in step with the feed's sweep): its 4,096 emitters lighting round the frame
    both ways, two runs of glass ticks going round from the bottom to the top, one in each ear, quicker and higher as
    they go, over a charge that climbs to the moment the last is lit (1.5 s) and blooms; and the title, GINNUNGAGAP,
    landing as it fades in (0.25 s): a swell sucked up into a vast struck crystal, a deep boom and a glass choir on the
    other universe's chord, ringing on into the tear."""
    total = 2.6
    hit = 0.25
    lit = 1.5
    n = ns(total)
    m = Mix(total)
    # The emitters.
    for side in (-1.0, 1.0):
        s = 0.0
        while True:
            s += rng.exponential(1.0 / (16 + 90 * (s / lit) ** 1.6))
            if s >= lit:
                break
            p = s / lit
            k = min(len(STARS) - 1, int(p * (len(STARS) - 3)) + int(rng.integers(0, 3)))
            ping = struck(STARS[k], 0.2, tau=0.035, ratios=(1.0, 2.76), bright=0.25)
            m.add(pan(ping, side * 0.9 * np.sin(np.pi * p)) * (0.35 + 0.65 * p), s, 0.3)
    # The charge climbing under them, sucked up into the bloom as the last is lit.
    q = ns(lit)
    m.add(glass(((440.0, 1.0), (659.26, 0.5)), q, curve(q, [(0, 0.5), (lit, 1.0)], 'log')) *
          curve(q, [(0, 0.0), (lit, 1.0)]) ** 2, 0, 0.12)
    m.add(stereo(inhale(lit / 2, 600, 7000)), lit / 2, 0.15)
    q = ns(total - lit)
    bloom = glass(((880.0, 0.6), (1318.51, 0.5), (1975.53, 0.35), (2489.02, 0.25), (3322.44, 0.15)), q)
    m.add(bloom * attack_decay(q, 0.02, 0.5), lit, 0.35)
    m.add(sine(curve(q, [(0, 110), (0.4, 55)], 'log')) * attack_decay(q, 0.005, 0.25), lit, 0.35)
    # The title.
    m.add(stereo(inhale(hit, 300, 6000)), 0.0, 0.5)
    q = ns(total - hit)
    tq = np.arange(q) / SR
    boom = sine(curve(q, [(0, 62), (0.9, 27.5)], 'log')) * attack_decay(q, 0.008, 0.7)
    m.add(sat(boom * 1.6, 1.6), hit, 0.9)
    crystal = sum(a * np.sin(2 * np.pi * 110.0 * r * tq + rng.uniform(0, 2 * np.pi)) * np.exp(-tq / (1.6 / (1 + 0.8 * k)))
                  for k, (r, a) in enumerate(((1.0, 1.0), (2.76, 0.6), (5.4, 0.45), (8.93, 0.3), (13.34, 0.2),
                                              (18.64, 0.12))))
    m.add(crystal * np.clip(tq / 0.002, 0, 1), hit, 0.35)
    m.add(decorrelated(q, lambda k: hp(white(k), 3000)) * attack_decay(q, 0.001, 0.12), hit, 0.3)
    swell = curve(q, [(0, 0.0), (0.12, 1.0), (0.6, 0.6), (1.3, 0.45), (total - hit, 0.0)])
    sung = sum(choir(f, q, AH) for f in (110.0, 164.81, 246.94))
    m.add(sat(sung * swell * 2.0, 1.4), hit, 0.6)
    m.add(glass(((440.0, 0.5), (659.26, 0.4), (987.77, 0.3), (1244.51, 0.22), (1661.22, 0.12)), q) * swell, hit, 0.3)
    vast = gap_spaces()[0]
    x = reverb(m.out(), vast, wet=0.35)[:, :n]
    return master(fade(x, 0.0, 0.5), peak=0.95, drive=1.2, squash=0.2)


def gap_tear():
    """The window tears open and the camera dives through it (2.0 s, OPEN+8 to MAP): a crack of breaking glass and a
    deep drop as the window splits open from the middle, a ripping that runs out to both sides as it widens; through
    it, the other universe's breath, a high glass choir and a soft wind of starlight; and from 1.3 s, as the camera
    dives, all of it rushing up in pitch, loudness and width, faster and faster, cut dead at the flash."""
    total = gap_at('MAP', -8, since='OPEN')
    opened = 16 / 20.0
    dive = 26 / 20.0
    span = total + 0.1
    n = ns(span)
    t = times(span)
    m = Mix(span)
    # The crack and the drop.
    q = ns(0.6)
    tq = np.arange(q) / SR
    snap = hp(white(q), 1500) * np.exp(-tq / 0.013)
    zing = sine(curve(q, [(0, 9000), (0.25, 600), (0.6, 400)], 'log')) * attack_decay(q, 0.0005, 0.06)
    boom = sine(curve(q, [(0, 75), (0.6, 38)], 'log')) * attack_decay(q, 0.002, 0.09)
    m.add(sat((snap * 0.8 + zing * 0.5 + boom * 0.6) * 2.2, 1.5), 0.0, 0.8)
    q = ns(span)
    m.add(sat(sine(curve(q, [(0, 88), (0.5, 50), (span, 26)], 'log')) * attack_decay(q, 0.03, 0.8) * 1.6, 1.6), 0.02, 0.4)
    # The rip: a fast, uneven train of tiny ruptures, like cloth tearing, running out from the middle to both sides.
    for side in (-1.0, 1.0):
        wob = lp(rng.standard_normal(n), 25)
        jitter = lp(rng.standard_normal(n), 400)
        rate = curve(n, [(0, 420), (0.4, 260), (opened, 200), (span, 120)], 'log')
        rate = rate * np.exp(0.3 * wob / np.std(wob) + 0.6 * jitter / np.std(jitter))
        phase = np.cumsum(rate) / SR
        k = phase.astype(int)
        size = rng.lognormal(0.0, 0.7, k[-1] + 1) * (rng.random(k[-1] + 1) > 0.2)
        rip = white(n) * np.exp(-(phase - k) / rate / 0.0012) * size[k]
        rip = sweep_filter(rip, 'bandpass', curve(n, [(0, 4200), (opened, 1800), (span, 1400)], 'log'), order=2, width=2.0)
        env = curve(n, [(0, 0.0), (0.02, 1.0), (0.5, 0.75), (opened, 0.4), (1.2, 0.0), (span, 0.0)])
        m.add(pan(rms_norm(rip, 0.2) * env, side * curve(n, [(0, 0.0), (opened, 0.85), (span, 0.85)])), 0, 0.7)

    def ice():
        """Glass splitting: a click and the falling whistle of the crack racing through it."""
        q = ns(rng.uniform(0.04, 0.14))
        f = curve(q, [(0, rng.uniform(4500, 10000)), (q / SR, rng.uniform(500, 1500))], 'log')
        y = sine(f) * attack_decay(q, 0.0003, q / SR / 3) + hp(white(q), 3000) * np.exp(-np.arange(q) / SR / 0.0012)
        return y * min(rng.lognormal(-1.0, 0.6), 1.2)
    m.add(grains(n, lambda s: 18 + 90 * np.exp(-s / 0.5), ice, end=opened, spread=0.9), 0, 0.2)
    # Through the window: the other universe's breath, rising an octave as the camera dives, faster and faster.
    rise = curve(n, [(0, 1.0), (dive, 1.0), (dive + 0.35, 1.15), (total, 2.0), (span, 2.0)], 'log')
    breath = glass(((1318.51, 0.5), (1661.22, 0.4), (1975.53, 0.35), (2489.02, 0.25), (2637.02, 0.2)), n, rise)
    breath += sum(choir(f, n, OO, voices=5, glide=rise) for f in (329.63, 493.88)) * 2.5
    shine = curve(n, [(0, 0.0), (0.3, 0.0), (0.9, 0.6), (dive, 0.7), (total, 1.0), (span, 1.0)])
    m.add(breath * shine * (0.85 + 0.15 * np.sin(2 * np.pi * 3.1 * t)), 0, 0.32)
    wind = decorrelated(n, pink)
    centre = curve(n, [(0, 700), (dive, 900), (total, 5000), (span, 5000)], 'log')
    wind = np.vstack([sweep_filter(c, 'bandpass', centre * (1 + 0.15 * np.sin(2 * np.pi * 0.8 * t + k)), order=2,
                                   width=1.8) for k, c in enumerate(wind)])
    m.add(wind * curve(n, [(0, 0.0), (0.4, 0.15), (dive, 0.2), (total, 1.0), (span, 1.0)]) ** 1.5, 0, 0.4)
    # The dive: a rush rising and widening as the camera goes in.
    q = ns(total - dive)
    p = np.linspace(0, 1, q)
    left, right = pink(q), pink(q)
    mid = (left + right) / 2
    rush = np.vstack([mid + (c - mid) * (0.3 + 0.7 * p) for c in (left, right)])
    rush = np.vstack([sweep_filter(c, 'bandpass', 250.0 * 28.0 ** (p ** 1.6), order=2, width=1.6) for c in rush])
    m.add(rush * p ** 2, dive, 2.2)
    m.add(stereo(inhale(total - dive, 300, 8000, tau=0.15)), dive, 1.2)
    m.add(stereo(sine(curve(q, [(0, 40), (total - dive, 90)], 'log')) * p ** 2), dive, 0.4)
    vast = gap_spaces()[0]
    x = reverb(m.out(), vast, wet=0.15)[:, :n]
    return cut(master(x, peak=0.95, drive=1.3, squash=0.2), total)


def gap_map():
    """Inside the other universe and back out of it (4.2 s, MAP to CUT): the violet flash going in, then a hush, vast
    and still, as the camera drifts in over the face of the galaxy, the other universe's chord breathing wide over the
    slow weight of the galaxy and stars glinting all round; from 0.8 s the long pull back, a rush rising and widening
    and an endless climb under it, the chord opening upwards and the stars thickening as the neighbouring galaxies come
    in, until the whole universe is a lattice and a run of glass lights across it; at 2.1 s the block in the middle is
    selected and everything else dims to it; from 2.3 s it lifts out of the lattice, the tritone coming up under its
    held note, a pulse quickening; from 2.5 s it is crushed down, grinding, snapping home at 3.5 s with a deep
    implosion; then the rush as the camera swings round to the gate, into the cut."""
    total = gap_at('CUT', since='MAP')
    drift = 16 / 20.0
    select = 42 / 20.0
    shown = 46 / 20.0
    lift = 46 / 20.0
    span = total + 0.1
    n = ns(span)
    t = times(span)
    vast = gap_spaces()[0]
    # The flash going in, gone in three ticks: a soft breath of light, the pressure let go.
    arrive = Mix(span)
    q = ns(1.0)
    arrive.add(decorrelated(q, lambda k: hp(pink(k), 1800)) * attack_decay(q, 0.002, 0.05), 0, 0.2)
    arrive.add(glass(((3520.0, 0.5), (4434.92, 0.35), (5274.04, 0.3)), q) * attack_decay(q, 0.003, 0.3), 0, 0.06)
    # The other universe: all of it dims once the block is selected.
    sky = Mix(span)
    voices = [(f, 0.0) for f in UNIVERSE[:6]] + list(zip(UNIVERSE[6:17], np.linspace(1.1, 2.6, 11)))
    chord = np.zeros((2, n))
    for f, start in voices:
        come = curve(n, [(0, 0.0), (start, 0.0), (start + (0.6 if start == 0 else 0.4), 1.0), (span, 1.0)])
        breathe = 0.7 + 0.3 * np.sin(2 * np.pi * rng.uniform(0.1, 0.25) * t + rng.uniform(0, 2 * np.pi))
        chord += glass(((f, 0.35 * (110.0 / f) ** 0.35),), n) * come * breathe
    chord += sum(choir(f, n, OO, voices=4) for f in (329.63, 415.30)) * 0.8 * \
        curve(n, [(0, 0.0), (0.6, 0.3), (select - 0.2, 0.6), (shown, 0.8), (span, 0.8)])
    sky.add(chord * curve(n, [(0, 0.0), (0.3, 0.14), (drift, 0.16), (select - 0.2, 0.6), (select, 1.0), (span, 1.0)]), 0, 0.5)
    # The weight of the galaxy, slow, going as the camera pulls away from it.
    weight = sine(55.0 * (1 + 0.002 * np.sin(2 * np.pi * 0.2 * t))) + 0.5 * sine(np.full(n, 82.41))
    sky.add(stereo(weight * (0.75 + 0.25 * np.sin(2 * np.pi * 0.35 * t))) *
            curve(n, [(0, 0.0), (0.3, 1.0), (drift, 1.0), (1.8, 0.0), (span, 0.0)]), 0, 0.08)
    # Stars, thickening as the neighbouring galaxies come in.
    sky.add(grains(n, lambda s: 3.0 if s < drift else 3.0 + 45.0 * ((s - drift) / (select - drift)) ** 2, twinkle,
                   end=select, spread=1.0), 0, 0.2)
    # The pull back: a rush rising and widening, faster and faster, over a climb that never arrives.
    q = ns(shown - drift + 0.3)
    p = np.clip(np.arange(q) / SR / (shown - drift), 0, 1)
    left, right = pink(q), pink(q)
    mid = (left + right) / 2
    rush = np.vstack([mid + (c - mid) * (0.25 + 0.75 * p) for c in (left, right)])
    rush = np.vstack([sweep_filter(c, 'bandpass', 160.0 * 37.5 ** (p ** 1.7), order=2, width=1.8) for c in rush])
    sky.add(rush * p ** 1.8 * curve(q, [(0, 1.0), (shown - drift, 1.0), (q / SR, 0.0)]), drift, 0.35)
    q = ns(select - drift)
    octaves = 1.5 * (np.arange(q) / SR / (select - drift)) ** 1.6
    climb = np.zeros(q)
    for k in range(6):
        place = (k + octaves) % 6.0
        climb += np.exp(-((place - 3.0) / 1.4) ** 2) * sine(55.0 * 2.0 ** place)
    sky.add(stereo(climb * curve(q, [(0, 0.0), (0.4, 0.5), (select - drift, 1.0)])), drift, 0.06)
    # The lattice lit up across, a run of glass from one side to the other.
    run = np.arange(select - 0.6, select - 0.02, 0.045)
    for i, at in enumerate(run):
        ping = struck(UNIVERSE[min(len(UNIVERSE) - 1, 9 + i)], 0.5, tau=0.12, ratios=(1.0, 2.76), bright=0.2)
        sky.add(pan(ping, -0.8 + 1.6 * i / max(1, len(run) - 1)), at, 0.06 * (0.5 + 0.5 * i / len(run)))
    # Selected: the rest of it dims to the block over six ticks.
    s = reverb(sky.out(), vast, wet=0.45)[:, :n]
    s = s * curve(n, [(0, 1.0), (select, 1.0), (select + 0.3, 0.18), (span, 0.12)])
    s = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 14000), (select, 14000), (select + 0.3, 1000), (span, 800)],
                                                     'log'), order=2) for c in s])
    # The block lifting out of the lattice, slowly and then faster (GapShots: its height goes as smoothstep to the
    # power 1.6), still going at the cut: its held note, the tritone coming up under it, a pulse quickening, and all of
    # it rising.
    up = Mix(span)
    q = ns(span - select)
    tq = np.arange(q) / SR
    u = np.clip((tq - (lift - select)) / (total - lift), 0, 1)
    height = (u * u * (3 - 2 * u)) ** 1.6
    pitch = 1.0 + 0.5 * height
    up.add(glass(((1760.0, 0.6), (2637.02, 0.35)), q, pitch) * curve(q, [(0, 0.0), (0.1, 0.5), (q / SR, 1.0)]), select,
           0.09)
    for f, g in ((55.0, 1.0), (77.78, 0.8)):
        stack = supersaw(f * pitch, q, voices=5, detune=0.01, spread=0.8)
        stack = np.vstack([sweep_filter(c, 'lowpass', 180 + 2400 * height, order=2) for c in stack])
        up.add(stack * (0.15 + 0.85 * height) * curve(q, [(0, 0.0), (0.3, 1.0), (q / SR, 1.0)]), select, 0.45 * g)
    # Lifting: air streaming past it, rising and quickening with it.
    left, right = pink(q), pink(q)
    mid = (left + right) / 2
    stream = np.vstack([mid + (c - mid) * (0.3 + 0.7 * height) for c in (left, right)])
    stream = np.vstack([sweep_filter(c, 'bandpass', 200.0 + 3300.0 * height, order=2, width=1.6) for c in stream])
    up.add(stream * height ** 1.2, select, 0.5)
    q = ns(0.4)
    tt = np.arange(q) / SR
    loose = sine(curve(q, [(0, 120), (0.4, 60)], 'log')) * attack_decay(q, 0.002, 0.08)
    up.add(loose + hp(white(q), 2500) * np.exp(-tt / 0.004) * 0.3, lift, 0.3)
    up.add(struck(1244.51, 0.4, tau=0.1, ratios=(1.0, 2.76), bright=0.3), lift, 0.06, position=0.2)
    for at in (lift + 0.35, lift + 0.7, lift + 0.92, lift + 1.04):
        beat = np.sin(2 * np.pi * (40 + 30 * np.exp(-tt / 0.02)) * tt) * np.exp(-tt / 0.1)
        up.add(sat(beat * 1.4, 1.4), at, 0.3)
    up.add(stereo(inhale(total - lift, 250, 4000, tau=0.6)), lift, 0.5)
    # Crushed down (2.5 to 3.5 s): its two trillion galaxies packed in, a deep grinding crunch closing up on itself,
    # snapping home with a hard, deep implosion; then the camera swinging round (from 3.6 s), a rush tearing up past it,
    # faster and faster into the cut.
    crunch_at, crushed_at, drag_at = 50 / 20.0, 70 / 20.0, 72 / 20.0
    q = ns(1.2)
    tq = np.arange(q) / SR
    snap = np.sin(2 * np.pi * curve(q, [(0, 70), (1.2, 26)], 'log') * tq) * np.exp(-tq / 0.25)
    snap += hp(white(q), 2000) * np.exp(-tq / 0.01) * 0.5
    up.add(stereo(sat(snap * 1.6, 1.5)), crushed_at, 0.6)
    q = ns(total - crunch_at)
    grind = crush(brown(q) * 0.5 + white(q) * 0.2, 4.0, curve(q, [(0, 2.0), (q / SR, 18.0)], 'log'))
    grind = sweep_filter(lp(grind, 3000), 'lowpass', curve(q, [(0, 2500), (q / SR, 300)], 'log'))
    up.add(stereo(rms_norm(grind, 0.2) * curve(q, [(0, 0.0), (0.3, 1.0), (q / SR, 0.7)])), crunch_at, 0.5)
    q = ns(total - drag_at)
    rush = decorrelated(q, pink)
    rush = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 300), (q / SR, 5000)], 'log'), width=1.5) for c in rush])
    up.add(rms_norm(rush, 0.2) * curve(q, [(0, 0.0), (q / SR, 1.0)]) ** 2, drag_at, 0.9)
    x = s + reverb(up.out(), vast, wet=0.2)[:, :n] + reverb(arrive.out(), vast, wet=0.15)[:, :n]
    return cut(master(x, peak=0.9, squash=0.2), total)


def gap_lock():
    """The block selected (1.2 s, MAP+42): a crisp, cold two-note chime, E7 rising to A7, each a pure partial over a
    faint glassy one against a detuned twin that makes it shiver, a needle of a transient and a small, soft knock under
    the first; a soft blip each time the box blinks back on; and the outline humming bright and high."""
    total = 1.2
    n = ns(total)
    m = Mix(total)
    for at, f, side in ((0.0, 2637.02, -0.15), (0.07, 3520.0, 0.15)):
        q = ns(total - at)
        note = struck(f, total - at, tau=0.35, ratios=(1.0, 2.76, 5.4), bright=0.3) + \
            0.5 * struck(f * 1.002, total - at, tau=0.3, ratios=(1.0,))
        note += hp(white(q), 6000) * np.exp(-np.arange(q) / SR / 0.0005) * 0.4
        m.add(note, at, 0.5, position=side)
    q = ns(0.25)
    m.add(sine(curve(q, [(0, 180), (0.25, 90)], 'log')) * attack_decay(q, 0.002, 0.04), 0.0, 0.12)
    # The box blinking back on (GapShots: on at 42, 46 and 50).
    for at in (0.2, 0.4):
        m.add(struck(3520.0, 0.1, tau=0.02, ratios=(1.0, 2.76), bright=0.2), at, 0.12, position=0.1)
    hold = glass(((3520.0, 0.5), (5274.04, 0.25)), n, detune=0.0008)
    m.add(hold * curve(n, [(0, 0.0), (0.1, 1.0), (0.6, 0.5), (total, 0.0)]) *
          (0.8 + 0.2 * np.sin(2 * np.pi * 9 * times(total))), 0, 0.06)
    glass_hall = gap_spaces()[1]
    return master(fade(reverb(m.out(), glass_hall, wet=0.3)[:, :n], 0.0, 0.3), peak=0.9)


def gap_extract():
    """Back outside the gate, the block drawn through (2.25 s, CUT to SEND+3): the rush of the drag carried on over the
    cut and dying away as the gate's hum swells in, the camera backing out through the window ahead of the block; the selection blinking; a deep groan stretching up as the block
    is pulled into the window, glass straining and the air sucked after it, then the break as it comes through (1.0 s),
    a thump, a pop and a ring running out across the window; the block humming once it is out; the window shutting like
    an old screen (from 1.55 s), a whine falling away and a thud as the picture folds to a bar, a tick as the point goes
    out; and a breath sucked in to the bridge, cut dead as it fires."""
    total = gap_at('SEND', 3, since='CUT')
    pull = 0.0
    through = 20 / 20.0
    shut, gone = 31 / 20.0, 39 / 20.0
    span = total + 0.1
    n = ns(span)
    m = Mix(span)
    # The rush of the drag carried on over the cut, falling away as the gate's hum swells in under it.
    q = ns(1.0)
    rush = decorrelated(q, pink)
    rush = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 5000), (1.0, 600)], 'log'), width=1.5) for c in rush])
    m.add(rms_norm(rush, 0.2) * np.exp(-np.arange(q) / SR / 0.3), 0, 0.8)
    m.add(gate_hum(n) * curve(n, [(0, 0.0), (0.6, 1.0), (gone, 1.0), (gone + 0.1, 0.6), (span, 0.6)]), 0, 0.16)
    low = supersaw(55.0, n, voices=5, detune=0.004, spread=0.9)
    m.add(np.vstack([lp(c, 400) for c in low]) * curve(n, [(0, 0.0), (0.6, 1.0), (span, 1.0)]), 0, 0.16)
    window = glass(((1318.51, 0.4), (1661.22, 0.3), (1975.53, 0.25), (2489.02, 0.15)), n)
    m.add(window * curve(n, [(0, 1.0), (shut, 1.0), (gone, 0.0), (span, 0.0)]) ** 1.5, 0, 0.05)
    # The selection blinking (GapShots: on, off, on, off, on, a tenth of a second each).
    for at in (0.0, 0.2, 0.4):
        m.add(struck(3520.0, 0.1, tau=0.02, ratios=(1.0, 2.76), bright=0.2), at, 0.08, position=0.1)
    # The pull: a groan stretching up, glass straining, the air sucked after it.
    q = ns(through - pull + 0.8)
    tq = np.arange(q) / SR
    stretch = curve(q, [(0, 62), (through - pull, 120), (q / SR, 130)], 'log')
    groan = resonator(brown(q), stretch * (1 + 0.012 * np.sin(2 * np.pi * 6 * tq)), q=25)
    tone = sine(stretch) + 0.4 * sine(stretch * 2.01)
    strain = curve(q, [(0, 0.0), (through - pull - 0.05, 1.0), (through - pull + 0.05, 0.3), (q / SR, 0.0)])
    m.add(stereo(sat((norm(groan) + 0.5 * tone) * strain * 1.5, 1.5)), pull, 0.35)

    def creak():
        k = ns(rng.uniform(0.01, 0.04))
        return resonator(white(k), np.full(k, rng.uniform(900, 3000)), q=40) * np.exp(-np.arange(k) / SR / 0.006) * \
            rng.uniform(0.3, 1.0)
    m.add(grains(n, lambda s: 10 + 60 * (s - pull) / (through - pull), creak, start=pull, end=through, spread=0.6), 0,
          0.15)
    m.add(stereo(inhale(through - pull, 200, 4000)), pull, 0.35)
    # The break as it comes through, and the square of light running out across the window from its edges.
    q = ns(0.8)
    tq = np.arange(q) / SR
    thump = sine(curve(q, [(0, 90), (0.4, 40)], 'log')) * attack_decay(q, 0.002, 0.15)
    m.add(sat(thump * 1.5 + hp(white(q), 2000) * np.exp(-tq / 0.003) * 0.6, 1.5), through, 0.8)
    m.add(struck(1244.51, 0.8, tau=0.4, ratios=(1.0, 2.0, 2.76), bright=0.4), through, 0.15)
    q = ns(0.3)
    ripple = np.vstack([sweep_filter(white(q), 'bandpass', curve(q, [(0, 2500), (0.3, 9000)], 'log'), order=2, width=1.2)
                        for _ in range(2)])
    m.add(ripple * attack_decay(q, 0.01, 0.07), through - 0.05, 0.25)
    # The block, out: the universe in it humming.
    q = ns(span - through)
    m.add(block_hum(q) * curve(q, [(0, 0.0), (0.3, 1.0), (span - through, 1.0)]), through, 0.25)
    # The window shutting like an old screen: a whine falling away, a thud as the picture folds to a bar, static, and a
    # tick and a flash as the point goes out.
    q = ns(gone - shut + 0.05)
    whine = sine(curve(q, [(0, 6200), (q / SR, 300)], 'log')) + 0.3 * saw(curve(q, [(0, 3100), (q / SR, 150)], 'log'), q)
    m.add(stereo(lp(whine, 9000) * curve(q, [(0, 0.0), (0.01, 1.0), (q / SR, 0.0)]) ** 1.3), shut, 0.18)
    q = ns(0.5)
    tq = np.arange(q) / SR
    fold = sine(curve(q, [(0, 75), (0.5, 34)], 'log')) * attack_decay(q, 0.003, 0.1)
    m.add(sat(fold * 1.4 + lp(white(q), 500) * np.exp(-tq / 0.02) * 0.6, 1.4), shut + 0.02, 0.6)
    m.add(decorrelated(q, lambda k: bp(white(k), 1500, 7000)) * attack_decay(q, 0.002, 0.05), shut, 0.2)
    q = ns(0.3)
    m.add(struck(4434.92, 0.3, tau=0.05, ratios=(1.0,), bright=0.2) + hp(white(q), 5000) * np.exp(-np.arange(q) / SR / 0.001),
          gone, 0.2)
    # A breath sucked in to the bridge.
    m.add(stereo(inhale(total - gone, 400, 9000, tau=0.12)), gone, 0.5)
    vast = gap_spaces()[0]
    x = reverb(m.out(), vast, wet=0.25)[:, :n]
    return cut(master(x, peak=0.95, drive=1.2, squash=0.2), total)


def gap_send():
    """The bridge (1.6 s, from SEND+3): a crack of light and a vast falling zap as Bifröst lances 1,600 km down to the
    target in a fifth of a second, a deep boom, and the beam settling into a bright, chorused hum with its rainbow
    shimmering at the edges; the block dropping into it with a falling whistle (0.25 s); then, as the camera pulls out
    to the whole bridge seen from far off, all of it far away and dimmed, the block a faint whistle going down."""
    total = 1.6
    reach = 4 / 20.0
    drop = 5 / 20.0
    far0, far1 = 0.2, 0.6
    n = ns(total)
    t = times(total)
    near = Mix(total)
    # The lance.
    q = ns(0.08)
    near.add(decorrelated(q, lambda k: hp(white(k), 1200)) * attack_decay(q, 0.0003, 0.01), 0.0, 1.8)
    q = ns(0.5)
    zap_f = curve(q, [(0, 3500), (reach, 70), (0.5, 55)], 'log')
    zap = sine(zap_f) + 0.5 * saw(zap_f, q) + norm(resonator(white(q), zap_f, q=18)) * 0.6
    near.add(stereo(sat(zap * attack_decay(q, 0.001, 0.12) * 1.5, 1.5)), 0.0, 0.5)
    boom = sine(curve(n, [(0, 60), (0.6, 26)], 'log')) * attack_decay(n, 0.005, 0.5)
    near.add(sat(boom * 1.8, 1.8), 0.02, 1.2)
    # The beam: chorused and bright, its colours shimmering at the edges.
    beam = supersaw(np.full(n, 110.0), n, voices=7, detune=0.012, spread=0.9) * 0.6 + \
        supersaw(np.full(n, 164.81), n, voices=7, detune=0.012, spread=0.9) * 0.4
    beam = np.vstack([sweep_filter(hp(c, 90), 'lowpass', curve(n, [(0, 9000), (0.15, 4000), (total, 2500)], 'log'),
                                   order=2) for c in beam])
    fringe = glass(((2217.46, 0.5), (2637.02, 0.4), (3322.44, 0.35), (4434.92, 0.25)), n, detune=0.004)
    near.add((beam + fringe * 0.4 * (0.7 + 0.3 * np.sin(2 * np.pi * 5.5 * t))) * attack_decay(n, 0.01, 0.6), 0.0, 0.22)
    # The block dropping in.
    q = ns(0.5)
    fall = sine(curve(q, [(0, 2200), (0.5, 320)], 'log')) * attack_decay(q, 0.01, 0.15)
    whoosh = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 4000), (0.5, 400)], 'log'), order=2, width=1.6)
    near.add(stereo(fall * 0.4 + norm(whoosh) * attack_decay(q, 0.02, 0.12) * 0.6), drop, 0.4)
    # Far off: all of it, its echo too, dims and dulls as the camera pulls out to the whole bridge.
    vast = gap_spaces()[0]
    x = reverb(near.out(), vast, wet=0.25)[:, :n] * curve(n, [(0, 1.0), (far0, 1.0), (far1, 0.22), (total, 0.15)])
    dull = curve(n, [(0, 18000), (far0, 18000), (far1, 1600), (total, 1200)], 'log')
    m = Mix(total)
    m.add(np.vstack([sweep_filter(c, 'lowpass', dull, order=2) for c in x]), 0, 1.0)
    q = ns(total - far1)
    m.add(sine(curve(q, [(0, 1800), (q / SR, 1250)], 'log')) * curve(q, [(0, 0.0), (0.2, 1.0), (q / SR, 0.0)]), far1,
          0.03)
    return master(fade(reverb(m.out(), vast, wet=0.04)[:, :n], 0.0, 0.3), peak=0.97, drive=1.3, squash=0.2)


def gap_fall():
    """The chase down the bridge (3.4 s, FALL to INBOUND): the block falling away under the camera, humming, its
    universe turning in it, the bridge's light fading overhead and a thin rush of high air thickening; the plasma
    lighting at 2.15 s with a thud and building fast, a roar opening up, buffeting and crackling, a violet shriek
    climbing; cloud tearing past from 2.4 s, a punch into the cloud deck (2.85 s) and the white swelling over
    everything, which goes soft and still for the last moments."""
    total = gap_at('INBOUND', since='FALL')
    heat0, heat1 = 2.15, 2.7
    clouds = 2.4
    deck0, deck1 = 2.85, 3.2
    n = ns(total)
    t = times(total)
    m = Mix(total)
    m.add(block_hum(n, curve(n, [(0, 1.0), (total, 1.04)], 'log')) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.7 * t)) *
          curve(n, [(0, 0.8), (heat0, 1.0), (deck0, 1.0), (deck1, 0.0), (total, 0.0)]), 0, 0.35)
    m.add(glass(((2217.46, 0.4), (3322.44, 0.3), (4434.92, 0.2)), n, detune=0.004) *
          curve(n, [(0, 1.0), (1.6, 0.0), (total, 0.0)]), 0, 0.05)
    heat = curve(n, [(0, 0.0), (heat0, 0.0), (heat1, 1.0), (deck1, 1.0), (total, 0.4)]) ** 1.5
    air = decorrelated(n, lambda k: pink(k) * 0.6 + brown(k) * 0.6)
    cutoff = curve(n, [(0, 900), (heat0, 1300), (heat1, 3500), (deck0, 6000), (deck1, 900), (total, 700)], 'log')
    air = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in air])
    thick = curve(n, [(0, 0.06), (heat0 - 0.6, 0.12), (heat0, 0.2), (heat1, 1.0), (deck1, 1.0), (total, 0.35)])
    buffet = np.clip(1.0 + 0.35 * lp(rng.standard_normal(n), 14) / 0.12 * heat, 0.4, 1.6)
    m.add(air * thick * buffet, 0, 0.8)
    m.add(decorrelated(n, lambda k: hp(white(k), 4000)) * curve(n, [(0, 0.4), (heat0, 1.0), (heat1, 0.3), (total, 0.0)]),
          0, 0.1)
    q = ns(0.8)
    m.add(sat(sine(curve(q, [(0, 62), (0.8, 38)], 'log')) * attack_decay(q, 0.04, 0.3) * 1.4, 1.4), heat0, 0.35)
    shriek = resonator(white(n), curve(n, [(0, 900), (heat0, 900), (deck0, 2700), (total, 2700)], 'log'), q=40)
    m.add(stereo(norm(shriek)) * np.array([[1.0], [0.85]]) * heat ** 1.5 *
          curve(n, [(0, 1.0), (deck1, 1.0), (total, 0.2)]), 0, 0.2)
    m.add(grains(n, lambda s: 15 + 450 * np.clip((s - heat0) / (deck0 - heat0), 0, 1) ** 2, crackle_pop, start=heat0,
                 end=deck1), 0, 0.5)
    # Cloud tearing past.
    for k, at in enumerate((clouds, clouds + 0.17, clouds + 0.3, clouds + 0.4, clouds + 0.48)):
        q = ns(0.35)
        puff = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 1800), (0.35, 500)], 'log'), order=2, width=1.6)
        side = (-1) ** k
        m.add(pan(norm(puff) * attack_decay(q, 0.06, 0.08), curve(q, [(0, 0.3 * side), (0.35, 0.9 * side)])), at, 0.25)
    # The deck, and the white.
    q = ns(0.45)
    tt = np.arange(q) / SR
    whomp = np.sin(2 * np.pi * curve(q, [(0, 70), (0.45, 32)], 'log') * tt) * np.exp(-tt / 0.12)
    whomp += lp(white(q), 600) * np.exp(-tt / 0.05) * 0.7
    m.add(sat(whomp * 1.3, 1.5), deck0, 0.8)
    q = ns(deck1 - deck0 + 0.05)
    swell = decorrelated(q, pink) * curve(q, [(0, 0.0), (deck1 - deck0, 1.0), (q / SR, 0.0)])
    m.add(np.vstack([lp(c, 6000) for c in swell]), deck0, 0.5)
    # The camera whipping round to each new angle (0.8, 1.55, 2.35 s): a short swish across, left then right.
    for k, at in enumerate((0.8, 1.55, 2.35)):
        q = ns(0.3)
        swish = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 700), (0.12, 3200), (0.3, 900)], 'log'), order=2, width=1.4)
        side = 1 if k % 2 == 0 else -1
        m.add(pan(norm(swish) * attack_decay(q, 0.09, 0.07), curve(q, [(0, -0.8 * side), (0.3, 0.8 * side)])), at - 0.12, 0.2)
    # Tearing past the side camera (2.0 s): a Doppler roar across the picture and the crack of its shock as it goes.
    q = ns(0.9)
    tt = np.arange(q) / SR - 0.35
    dist = np.sqrt((tt * 120.0) ** 2 + 8.0 ** 2)
    roar = sweep_filter(pink(q) + brown(q) * 0.5, 'bandpass', curve(q, [(0, 1400), (0.35, 2600), (0.9, 300)], 'log'), order=2, width=2.0)
    roar *= 1.0 / (0.3 + dist / 20.0)
    m.add(pan(norm(roar), np.clip(tt * 3.0, -0.95, 0.95)), 2.0 - 0.35, 0.45)
    q = ns(0.5)
    tt = np.arange(q) / SR
    crack = hp(white(q), 900) * np.exp(-tt / 0.02) + np.sin(2 * np.pi * 55 * tt) * np.exp(-tt / 0.15) * 0.8
    m.add(stereo(sat(crack * 1.5, 1.6)), 2.0 + 0.03, 0.4)
    x = m.out() * curve(n, [(0, 1.0), (deck1, 1.0), (total, 0.35)])
    x = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 18000), (deck1, 18000), (total, 900)], 'log'), order=2)
                   for c in x])
    vast = gap_spaces()[0]
    return master(reverb(x, vast, wet=0.15)[:, :n], peak=0.92, drive=1.5, squash=0.3)


def gap_drone():
    """The block coming down (5.75 s, from the bridge firing to contact): a vast dissonant drone, two detuned stacks a
    tritone apart, A against D#, coming up out of the bridge's hum and rising and swelling as the block falls, glassy at
    the top as it nears; sub pulses quickening like a racing heart; the air pressing down; cut dead at the instant of
    contact. It plays whether the feed is up or not."""
    total = gap_at('CONTACT', -3, since='SEND')
    span = total + 0.1
    n = ns(span)
    t = times(span)
    m = Mix(span)
    rise = curve(n, [(0, 1.0), (2.4, 1.06), (4.3, 1.22), (total, 1.5), (span, 1.5)], 'log')
    grow = curve(n, [(0, 0.0), (1.0, 0.1), (2.9, 0.28), (4.8, 0.62), (total, 1.0), (span, 1.0)])
    bright = curve(n, [(0, 240), (2.9, 650), (4.8, 1700), (total, 4800), (span, 4800)], 'log')
    for f, g in ((55.0, 0.8), (77.78, 0.7), (110.0, 0.7), (155.56, 0.6), (220.0, 0.4), (311.13, 0.35)):
        stack = supersaw(f * rise * (1 + 0.003 * np.sin(2 * np.pi * 0.13 * t + f)), n, voices=7, detune=0.014, spread=0.9)
        stack = np.vstack([sweep_filter(c, 'lowpass', bright * (1 + 0.15 * np.sin(2 * np.pi * 0.21 * t + k)), order=2)
                           for k, c in enumerate(stack)])
        m.add(stack * grow, 0, g)
    # The same tritone higher up, coming in as it nears: harsh, then glassy.
    for f, g in ((440.0, 0.3), (622.25, 0.25)):
        stack = supersaw(f * rise, n, voices=5, detune=0.008, spread=1.0, mono_below=0)
        stack = np.vstack([sweep_filter(hp(c, 300), 'lowpass', bright * 1.6, order=2) for c in stack])
        m.add(stack * grow ** 2.5, 0, g)
    for f in (1760.0, 2489.0):
        pair = np.vstack([sine(f * rise * 0.998), sine(f * rise * 1.002)])
        m.add(pair * grow ** 3 * (0.6 + 0.4 * sine(curve(n, [(0, 4), (total, 11), (span, 11)]))), 0, 0.04)
    # The sub pulses, quickening.
    rate = curve(n, [(0, 0.8), (2.4, 1.4), (4.3, 3.0), (total, 9.0), (span, 9.0)], 'log')
    phase = np.cumsum(rate) / SR
    since = (phase % 1.0) / rate
    pulse = np.sin(2 * np.pi * (36 + 34 * np.exp(-since / 0.025)) * since) * np.exp(-since / np.minimum(0.22, 0.35 / rate))
    m.add(stereo(sat(pulse * 1.6, 1.5)) * (0.1 + 0.9 * grow), 0, 0.6)
    # The weight of it: the ground rumbling and the air pressing down.
    low = decorrelated(n, brown)
    m.add(np.vstack([lp(c, 140) for c in low]) * grow, 0, 0.3)
    q = ns(span - 2.4)
    press = decorrelated(q, pink)
    press = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 250), (total - 2.4, 4500), (q / SR, 4500)], 'log'),
                                    order=2, width=2.0) for c in press])
    m.add(press * curve(q, [(0, 0), (total - 2.4, 1.0), (q / SR, 1.0)]) ** 2, 2.4, 0.35)
    vast = gap_spaces()[0]
    x = reverb(m.out(), vast, wet=0.25)[:, :n]
    return cut(master(x, peak=0.95, drive=1.4, squash=0.15), total)


def gap_inbound():
    """The block coming down on the target (1.2 s, INBOUND to contact), heard in the world: out of the white of the
    clouds, the bridge standing over the target and humming through everything, and the block coming down it, a roar
    of torn air growing and a whistle falling as it nears, the air sucked up into it at the last; cut dead at
    contact."""
    total = gap_at('CONTACT', since='INBOUND')
    span = total + 0.1
    n = ns(span)
    t = times(span)
    m = Mix(span)
    near = curve(n, [(0, 0.15), (0.6, 0.4), (1.1, 0.9), (total, 1.0), (span, 1.0)]) ** 1.3
    # The bridge.
    for f, g in ((55.0, 1.0), (77.78, 0.8), (110.0, 0.5)):
        stack = supersaw(np.full(n, f), n, voices=7, detune=0.012, spread=0.9)
        m.add(np.vstack([lp(c, 900) for c in stack]) * (0.5 + 0.5 * near), 0, 0.25 * g)
    # The block: a roar growing, a whistle falling.
    roar = decorrelated(n, lambda k: pink(k) * 0.6 + brown(k) * 0.7)
    roar = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 500), (total, 4500), (span, 4500)], 'log'), order=2)
                      for c in roar])
    m.add(roar * near, 0, 0.8)
    whistle = resonator(white(n), curve(n, [(0, 2600), (total, 850), (span, 850)], 'log') *
                        (1 + 0.015 * np.sin(2 * np.pi * 11 * t)), q=25)
    m.add(stereo(norm(whistle) * near), 0, 0.35)
    m.add(stereo(inhale(0.3, 500, 8000, tau=0.08)), total - 0.3, 0.5)
    m.add(grains(n, lambda s: 20 + 250 * s, crackle_pop, end=total, spread=0.8), 0, 0.2)
    # Out of the white: the world comes up out of a muffle.
    x = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 700), (0.4, 9000), (span, 12000)], 'log'), order=2)
                   for c in m.out()])
    world = gap_spaces()[3]
    x = reverb(x, world, wet=0.2)[:, :n]
    return cut(master(x, peak=0.92, drive=1.5, squash=0.5), total)


def gap_swap():
    """A block trading places with its twin in the other universe (0.5 s, placed in the world): an enderman's
    'vworp', a warbling, flanged pitch bend, and a glassy blip as the twin arrives."""
    total = 0.5
    n = ns(total)
    t = times(total)
    bend = curve(n, [(0, 240), (0.08, 1100), (0.2, 640), (0.4, 170), (total, 150)], 'log')
    warble = 1 + 0.04 * np.sin(2 * np.pi * 23 * t)
    src = saw(bend * 0.5 * warble, n) * 0.7 + white(n) * 0.3
    x = norm(sweep_filter(src, 'bandpass', bend * 2.0, order=2, width=1.2)) + 0.45 * sine(bend * warble)
    # Flanged: against a copy of itself delayed by a sweeping 0.3 to 3 ms.
    delay = curve(n, [(0, 0.0032), (0.22, 0.0003), (total, 0.0022)]) * SR
    x = x + 0.85 * np.interp(np.arange(n) - delay, np.arange(n), x, left=0.0)
    x *= curve(n, [(0, 0.0), (0.02, 1.0), (0.18, 0.85), (0.42, 0.0), (total, 0.0)])
    m = Mix(total)
    m.add(x, 0.0, 0.6)
    m.add(chime(3136.0, ns(0.25), tau=0.06, ratios=(1.0, 2.32, 4.25), bright=0.35), 0.15, 0.35)
    _, hall, _, _ = spaces()
    y = mono(reverb(m.out(), hall, wet=0.12))[:n]
    return cut(master(fade(y, 0.0, 0.05), peak=0.9), total)


def gap_contact():
    """Contact (1.0 s): every other sound has stopped dead, and in the instant of white before the first frame there is
    one tiny, very high, pure crystalline tink."""
    total = 1.0
    n = ns(total)
    m = Mix(total)
    q = ns(0.9)
    tink = chime(5274.0, q, tau=0.22, ratios=(1.0, 2.32), bright=0.1)
    twin = chime(5274.0 * 1.0012, q, tau=0.18, ratios=(1.0,))
    strike = hp(white(q), 9000) * np.exp(-np.arange(q) / SR / 0.0003)
    m.add(pan(tink + 0.1 * strike, -0.08) + pan(twin, 0.15) * 0.35, 0.005, 1.0)
    glass_hall = gap_spaces()[1]
    x = fade(reverb(m.out(), glass_hall, wet=0.15)[:, :n], 0.0, 0.35)
    return cut(master(x, peak=0.9), total)


# The impact frames' cuts after the first, in seconds from contact, and what each frame is (GapFrames: ticks 3, 6, 9,
# 13, 21, 25 and 28; the first frame comes up out of two ticks of white). The picture cuts on the same beats.
GAP_FRAMES = ((0.15, 'crack'), (0.30, 'heavy'), (0.45, 'wide'), (0.65, 'split'), (1.05, 'heavy'), (1.25, 'double'),
              (1.40, 'slab'))


def gap_impact():
    """The impact frames (1.5 s, contact to the burst): a tenth of a second of white and silence for the contact's
    tink, then on the first frame a colossal slam, a sub, a crack, a blast of distorted noise and crackle crushed to a
    couple of bits; a hit on every cut after it, each in its frame's own way (the black frame's glass shattering, a
    heavy hit, the wide shot's deep far boom, three at once for the split screen, the low shot, a double, the slab),
    the roar under them dropping out just before every cut so each is heard and sticking through the split screen; the
    ears ringing; cut dead as it bursts."""
    total = gap_at('BLAST', since='CONTACT')
    slam = 2 / 20.0
    span = total + 0.1
    n = ns(span)
    t = times(span)
    _, _, _, world = gap_spaces()
    # The roar under every frame: distorted and dark, the drone's tritone still in it. It ducks under every hit.
    bed = Mix(span)
    roar = decorrelated(n, lambda q: brown(q) * 0.8 + pink(q) * 0.6)
    roar = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 7000), (0.6, 1800), (span, 2600)], 'log'), order=2)
                      for c in roar])
    bed.add(sat(roar * 2.5, 2.2), 0, 0.3)
    chest = decorrelated(n, lambda q: bp(white(q), 120, 700, 2))
    bed.add(sat(rms_norm(chest, 0.25) * 3.0, 2.0), 0, 0.22)
    for f, g in ((55.0, 0.5), (77.78, 0.4), (110.0, 0.3)):
        stack = supersaw(np.full(n, f), n, voices=7, detune=0.016, spread=0.9)
        bed.add(sat(np.vstack([lp(c, 1500) for c in stack]) * 2.0, 1.8), 0, g * 0.45)
    bed.add(grains(n, lambda s: 30, glitch, spread=1.0), 0, 0.3)
    duck = np.ones(n)
    for i, at in enumerate((slam,) + tuple(at for at, _ in GAP_FRAMES)):
        duck *= 1 - (0.85 if i == 0 else 0.5) * np.exp(-np.maximum(t - at, 0) / 0.12) * (t >= at)
    m = Mix(span)
    far = Mix(span)
    m.add(bed.out() * duck * (t >= slam), 0, 1.0)
    # The slam: the ground's sub, a crack, a blast of distorted noise and crackle crushed to a couple of bits.
    q = ns(3.0)
    m.add(sat(sine(curve(q, [(0, 64), (0.3, 34), (3.0, 21)], 'log')) * attack_decay(q, 0.003, 0.9) * 2.0, 2.0), slam, 1.4)
    q = ns(1.2)
    punch = decorrelated(q, lambda k: bp(white(k), 90, 900, 2))
    m.add(sat(rms_norm(punch, 0.3) * attack_decay(q, 0.002, 0.22) * 4.0, 2.0), slam, 0.9)
    q = ns(0.05)
    m.add(decorrelated(q, lambda k: hp(white(k), 700)) * attack_decay(q, 0.0008, 0.007), slam, 1.2)
    q = ns(1.5)
    burst = decorrelated(q, lambda k: white(k) * 0.6 + pink(k) * 0.8)
    burst = np.vstack([sweep_filter(c, 'lowpass', curve(q, [(0, 9000), (0.2, 2500), (1.5, 300)], 'log'), order=2)
                       for c in burst])
    m.add(sat(burst * attack_decay(q, 0.002, 0.3) * 3.0, 2.0), slam, 1.4)
    crackle = crush(decorrelated(q, white) * 0.6, curve(q, [(0, 2.0), (1.5, 4.0)]), curve(q, [(0, 30), (1.5, 6)]))
    m.add(np.vstack([bp(c, 300, 7000) for c in crackle]) * attack_decay(q, 0.002, 0.25), slam, 0.7)

    def heavy(size=1.0, low=85.0):
        """A sub thump, a crack, a distorted body and a shard of glass ringing."""
        q = ns(0.6)
        thump = sine(curve(q, [(0, low), (0.3, 34), (0.6, 30)], 'log')) * attack_decay(q, 0.002, 0.14)
        body = bp(white(q), 90, 900) * attack_decay(q, 0.001, 0.08)
        crack = hp(white(q), 1200) * attack_decay(q, 0.0002, 0.006)
        shard = struck(rng.uniform(900, 1500), 0.6, tau=0.25, ratios=(1.0, 2.32, 4.25, 6.63), bright=0.6)
        return (sat(thump * 1.3 + body * 1.6 + crack * 0.8, 2.0) + shard * 0.15) * size

    def light():
        """A bright slash: a crack, grit and a tick."""
        q = ns(0.3)
        crack = hp(white(q), 2400) * attack_decay(q, 0.0002, 0.012)
        grit = bp(crush(white(q) * 0.5, 2.0, 10.0), 600, 8000) * attack_decay(q, 0.0005, 0.03)
        tick = sine(curve(q, [(0, 160), (0.1, 90), (0.3, 80)], 'log')) * attack_decay(q, 0.001, 0.03)
        return crack * 0.8 + grit * 0.5 + tick * 0.6
    for i, (at, kind) in enumerate(GAP_FRAMES):
        side = 0.3 * (-1) ** i
        if kind == 'crack':
            m.add(light(), at, 0.9, position=side)
            for _ in range(7):
                m.add(struck(rng.uniform(2200, 6000), 0.3, tau=rng.uniform(0.03, 0.1), bright=0.4),
                      at + rng.uniform(0.0, 0.03), 0.12, position=rng.uniform(-0.8, 0.8))
        elif kind == 'heavy':
            m.add(heavy(), at, 1.3, position=side)
        elif kind == 'wide':
            far.add(heavy(0.7, 60.0), at, 1.2, position=side)
            q = ns(0.8)
            far.add(sat(sine(curve(q, [(0, 50), (0.8, 22)], 'log')) * attack_decay(q, 0.004, 0.3) * 1.6, 1.6), at, 0.9)
        elif kind == 'split':
            for k, (dt, where) in enumerate(((0.0, -0.6), (0.035, 0.0), (0.07, 0.6))):
                m.add(heavy(0.8) if k == 1 else light(), at + dt, 1.1 if k == 1 else 0.8, position=where)
        elif kind == 'double':
            for dt in (0.0, 0.045):
                m.add(heavy(0.85), at + dt, 1.2, position=side)
        else:
            m.add(heavy(1.3, 70.0), at, 1.4)
            q = ns(0.6)
            m.add(sat(sine(curve(q, [(0, 48), (0.6, 24)], 'log')) * attack_decay(q, 0.003, 0.25) * 1.8, 1.8), at, 0.9)
    x = reverb(m.out(), world, wet=0.3)[:, :n] + reverb(far.out(), world, wet=0.7)[:, :n]
    # The cuts: nothing until the slam, and every frame drops out just before the next so each cut is heard. The split
    # screen sticks: after its three hits its first instants repeat, faster and faster.
    edges = (slam,) + tuple(at for at, _ in GAP_FRAMES) + (total,)
    gate = np.zeros(n)
    for i in range(len(edges) - 1):
        a, b = ns(edges[i]), ns(edges[i + 1])
        seg = np.arange(b - a) / SR
        gate[a:b] = 1.0 if i == len(edges) - 2 else seg < seg[-1] - 0.03
        if i >= 1 and GAP_FRAMES[i - 1][1] == 'split':
            parts = np.array_split(np.arange(a + ns(0.13), b), 3)
            for part, size in zip(parts, (ns(0.06), ns(0.03), ns(0.015))):
                piece = x[:, a:a + size] * np.minimum(1.0, np.minimum(np.arange(size), np.arange(size)[::-1]) / ns(0.001))
                x[:, part] = np.tile(piece, len(part) // size + 1)[:, :len(part)]
    gate[ns(total):] = 1.0
    x = norm(x * uniform_filter1d(gate, ns(0.001)))
    ring = np.vstack([sine(np.full(n, 3520.0)), sine(np.full(n, 3527.0))])
    x += ring * curve(n, [(0, 0.0), (0.2, 0.0), (0.45, 1.0), (span, 1.0)]) * 0.045
    return cut(master(x, peak=0.98, drive=2.2, squash=0.8), total)


def gap_blast():
    """The burst (3.5 s, BLAST to ERASURE): that universe bursting out of its block, a deep boom and a blast of noise,
    its chord blown out wide and ringing on, its column of light roaring up and pulsing as the picture's does, its stars
    streaming out all round in falling glints, the front racing out over the camera and the ground shaking; the ears
    still ringing from the frames. At 3.0 s it falls back in on itself: everything sucked back in, faster and faster
    and rising, the stars drawn in after it, cut dead at 3.5 s as the black opens."""
    total = gap_at('ERASURE', since='BLAST')
    collapse = gap_at('COLLAPSE', since='BLAST')
    span = total + 0.1
    n = ns(span)
    t = times(span)
    _, _, _, world = gap_spaces()
    m = Mix(span)
    # The burst.
    q = ns(3.0)
    m.add(sat(sine(curve(q, [(0, 48), (0.4, 26), (3.0, 18)], 'log')) * attack_decay(q, 0.004, 1.0) * 2.0, 2.0), 0, 1.2)
    q = ns(1.2)
    punch = decorrelated(q, lambda k: bp(white(k), 90, 900, 2))
    m.add(sat(rms_norm(punch, 0.3) * attack_decay(q, 0.002, 0.25) * 4.0, 2.0), 0, 0.8)
    q = ns(3.0)
    blast = decorrelated(q, lambda k: white(k) * 0.5 + pink(k) * 0.8)
    blast = np.vstack([sweep_filter(c, 'lowpass', curve(q, [(0, 9000), (0.2, 2600), (1.2, 700), (3.0, 220)], 'log'),
                                    order=2) for c in blast])
    m.add(sat(blast * attack_decay(q, 0.003, 0.6) * 3.0, 2.2), 0, 0.9)
    # Its chord blown out: that universe ringing on.
    held = curve(n, [(0, 0.0), (0.06, 1.0), (1.0, 0.75), (collapse, 0.55), (total, 0.0), (span, 0.0)])
    chord = np.zeros((2, n))
    for f, g in ((55.0, 1.0), (82.41, 0.7), (110.0, 0.6), (164.81, 0.45), (246.94, 0.3), (311.13, 0.25), (415.30, 0.2)):
        stack = supersaw(np.full(n, f), n, voices=7, detune=0.012, spread=1.0)
        chord += np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 6000), (0.5, 2500), (span, 1400)], 'log'), order=2)
                            for c in stack]) * g
    m.add(sat(chord * held * 1.5, 1.6), 0, 0.35)
    m.add(glass(((880.0, 0.4), (1318.51, 0.35), (1975.53, 0.3), (2489.02, 0.2), (3322.44, 0.15)), n) * held, 0, 0.15)
    # Its column of light, roaring up and pulsing as the picture's does (1 + 0.12 sin 2.3t + 0.06 sin 5.1t, t in ticks).
    tick = t * 20.0
    pulse = 1.0 + 0.35 * np.sin(2.3 * tick) + 0.18 * np.sin(5.1 * tick)
    column = decorrelated(n, pink)
    column = np.vstack([sweep_filter(c, 'bandpass', curve(n, [(0, 600), (0.5, 1400), (span, 1100)], 'log'), order=2,
                                     width=1.4) for c in column])
    column += supersaw(np.full(n, 220.0), n, voices=7, detune=0.02, spread=1.0) * 0.5
    m.add(column * pulse * curve(n, [(0, 0.0), (0.5, 1.0), (1.0, 0.8), (collapse, 0.6), (total, 0.0), (span, 0.0)]), 0,
          0.25)

    # Its stars streaming out all round.
    def streak():
        q = ns(rng.uniform(0.08, 0.25))
        f = curve(q, [(0, rng.choice(STARS) * 1.5), (q / SR, rng.uniform(600, 1500))], 'log')
        return sine(f) * attack_decay(q, 0.002, q / SR / 3) * min(rng.lognormal(-1.0, 0.5), 1.0)
    m.add(grains(n, lambda s: 140 * np.exp(-s / 1.0) + 15, streak, start=0.03, end=collapse, spread=1.0), 0, 0.15)
    # The front racing out over the camera, and the ground shaking.
    q = ns(2.5)
    front = decorrelated(q, pink)
    front = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 1800), (0.6, 800), (2.5, 300)], 'log'), order=2,
                                    width=2.4) for c in front])
    m.add(front * attack_decay(q, 0.35, 0.7), 0.3, 0.6)
    q = ns(2.0)
    shake = np.vstack([lp(c, 110) for c in decorrelated(q, brown)])
    m.add(shake * attack_decay(q, 0.01, 0.45) * (0.7 + 0.3 * np.sin(2 * np.pi * 9 * times(2.0))), 0, 0.7)
    # The ears, still ringing from the frames.
    ring = np.vstack([sine(np.full(n, 3520.0)), sine(np.full(n, 3527.0))])
    m.add(ring * curve(n, [(0, 1.0), (1.4, 0.0), (span, 0.0)]), 0, 0.02)

    # The collapse: everything sucked back in, faster and faster and rising, the stars drawn in after it.
    def drawn():
        q = ns(rng.uniform(0.06, 0.15))
        f = curve(q, [(0, rng.uniform(500, 1200)), (q / SR, rng.choice(STARS))], 'log')
        return sine(f) * curve(q, [(0, 0.0), (q / SR * 0.8, 1.0), (q / SR, 0.0)]) * min(rng.lognormal(-1.0, 0.5), 1.0)
    m.add(grains(n, lambda s: 30 + 300 * ((s - collapse) / (total - collapse)) ** 2, drawn, start=collapse, end=total,
                 spread=1.0), 0, 0.15)
    q = ns(total - collapse)
    m.add(stereo(inhale(total - collapse, 120, 6000, tau=0.18)), collapse, 1.0)
    m.add(stereo(sine(curve(q, [(0, 60), (q / SR, 600)], 'log')) * curve(q, [(0, 0.0), (q / SR, 1.0)]) ** 2), collapse,
          0.3)
    # Its galaxies flying out past the camera: a dozen swooshes going by on one side or the other, falling as they pass.
    for i in range(12):
        at = 0.25 + 2.6 * (i / 11) ** 1.4 + rng.uniform(-0.05, 0.05)
        length = rng.uniform(0.35, 0.7)
        q = ns(length)
        f = curve(q, [(0, rng.uniform(2500, 5000)), (length, rng.uniform(250, 600))], 'log')
        y = sweep_filter(pink(q), 'bandpass', f, width=1.2) * np.sin(np.pi * np.arange(q) / q) ** 2
        side = 1.0 if i % 2 else -1.0
        where = curve(q, [(0, -0.8 * side), (length, 0.9 * side)])
        m.add(np.vstack([y * np.sqrt((1 - where) / 2), y * np.sqrt((1 + where) / 2)]), at, 0.25 * rng.uniform(0.6, 1.0))
    x = reverb(m.out(), world, wet=0.4)[:, :n]
    return cut(master(x, peak=0.97, drive=1.5, squash=0.3), total)


def gap_erase():
    """Reality deleted (5.0 s, ERASURE to NOTHING), block by block outward from the contact: a hollow thud and a flash
    as the black opens; a deep rushing roar of static, the grinding crumble of a signal falling apart and a sucking
    undertone pulling it all in, growing as the black comes on over everything towards the camera; as the last of the
    picture goes black (from 4.5 s) it all winds down like a stopped tape, and at 5.0 s it is gone."""
    total = gap_at('NOTHING', since='ERASURE')
    black = gap_at('NOTHING', -10, since='ERASURE')
    wind = black + 0.2
    span = total + 0.3
    n = ns(span)
    m = Mix(span)
    points = [(0, 0.02), (1.0, 0.07), (2.4, 0.25), (3.5, 0.55), (4.3, 0.9), (black, 1.0), (span, 1.0)]
    grow = curve(n, points)

    def level(s):
        return float(np.interp(s, *zip(*points)))
    # The black opening: a hollow thud and a flash.
    q = ns(0.8)
    tq = np.arange(q) / SR
    thud = sine(curve(q, [(0, 70), (0.8, 30)], 'log')) * attack_decay(q, 0.003, 0.25)
    hollow = comb(white(q) * np.exp(-tq / 0.08), 110.0, 0.85)
    m.add(sat(thud * 1.5 + lp(hollow, 2000) * 0.5, 1.5), 0, 0.45)
    m.add(decorrelated(q, lambda k: hp(pink(k), 2500)) * attack_decay(q, 0.002, 0.06), 0, 0.15)
    # The roar of static, opening up as it grows, over a deep rush.
    static = decorrelated(n, lambda q: pink(q) * 0.8 + white(q) * 0.3)
    static = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 600), (2.9, 2600), (black, 9000), (span, 9000)], 'log'),
                                     order=2) for c in static])
    m.add(rms_norm(static, 0.2) * grow, 0, 0.5)
    deep = rms_norm(np.vstack([lp(c, 160) for c in decorrelated(n, brown)]), 0.2)
    m.add(deep * grow, 0, 0.45)
    # Grinding: the roar itself crushed harder and harder.
    grind = crush(norm(static + deep), curve(n, [(0, 6.0), (black, 2.5), (span, 2.5)]),
                  curve(n, [(0, 2.0), (black, 24.0), (span, 24.0)], 'log'))
    m.add(rms_norm(np.vstack([bp(c, 150, 7000) for c in grind]), 0.2) * grow ** 2, 0, 0.35)

    # The crumble: grains of the signal coming apart, faster and faster. Noise only, never a tone, so it reads as
    # damage rather than as anything alive.
    def crumble():
        q = ns(rng.uniform(0.008, 0.06))
        y = crush(white(q) * 0.4, rng.uniform(1.0, 3.5), rng.uniform(2.0, 30.0))
        y = bp(y, rng.uniform(80, 400), rng.uniform(2000, 9000))
        return y * attack_decay(q, 0.001, q / SR / 2) * min(rng.lognormal(-0.8, 0.5), 1.2)

    def fault():
        q = ns(rng.uniform(0.004, 0.03))
        y = bp(crush(white(q) * 0.5, rng.uniform(1.0, 2.5), rng.uniform(4.0, 40.0)), 60, rng.uniform(1500, 5000))
        edge = np.minimum(1.0, np.minimum(np.arange(q), np.arange(q)[::-1]) / 12.0)
        return y * edge * min(rng.lognormal(-1.0, 0.6), 1.0)
    m.add(grains(n, lambda s: 12 + 420 * (s / black) ** 2, crumble, end=total, gain_curve=level, spread=1.0), 0, 0.5)
    m.add(grains(n, lambda s: 20 + 220 * (s / black) ** 1.5, fault, end=total, gain_curve=level, spread=1.0), 0, 0.3)
    # The world's own sound being eaten: a slice of the roar stuck and repeated, crushed harder each time round, like a
    # file being torn up while it plays; more and more of them as the black comes on.
    body = norm(static + deep)
    for u in np.sort(rng.uniform(0.0, 1.0, 70)):
        at = black * u ** 0.6
        size = ns(rng.uniform(0.012, 0.045))
        src = ns(at)
        if src + size >= n:
            continue
        piece = body[:, src:src + size]
        repeats = int(rng.integers(3, 9))
        stuck = np.concatenate([crush(piece, 6.0 - 4.0 * k / repeats, 1.0 + 6.0 * k / repeats) for k in range(repeats)], axis=1)
        stuck = stuck * np.minimum(1.0, np.minimum(np.arange(stuck.shape[1]), np.arange(stuck.shape[1])[::-1]) / 30.0)
        m.add(stuck, at, 0.25 * (0.3 + 0.7 * level(at)))
    # Under it all, the signal shredding: a low digital buzz crushed to almost nothing, swelling with the black.
    hum = crush(np.sign(sine(curve(n, [(0, 62.0), (black, 41.0), (span, 41.0)], 'log'))) * 0.5, 3.0, 8.0)
    m.add(np.vstack([lp(hum, 1800), lp(hum, 1800)]) * grow ** 2, 0, 0.1)
    # The sucking undertone: the air pulled in by swells played backwards, closer and closer together, the last and
    # biggest ending as the picture goes black, and a deep tone sinking under them.
    for end_at, length in ((1.1, 1.0), (2.1, 0.85), (2.85, 0.7), (3.45, 0.6), (3.95, 0.5), (4.3, 0.4), (4.55, 0.3),
                           (black, 1.1)):
        q = ns(length)
        swell = decorrelated(q, pink) * np.exp(-np.arange(q) / SR / (length / 3))
        swell = np.vstack([sweep_filter(c, 'lowpass', curve(q, [(0, 3000), (length, 150)], 'log'), order=2) for c in swell])
        m.add(rms_norm(swell[:, ::-1], 0.2), end_at - length, 0.5 * (0.15 + 0.85 * (end_at / black) ** 2))
    # The world flaking off behind the front: wisps of air lifting, more and more of them, each a breath of noise whose
    # band climbs as it rises. Wide bands and slow, so they are air and never a whistle.
    def wisp():
        length = rng.uniform(0.4, 0.9)
        q = ns(length)
        f = curve(q, [(0, rng.uniform(300, 700)), (length, rng.uniform(1500, 3000))], 'log')
        return sweep_filter(white(q), 'bandpass', f, width=2.0) * np.sin(np.pi * np.arange(q) / q) ** 2 * 0.3
    m.add(grains(n, lambda s: 3 + 30 * (s / black), wisp, end=total, gain_curve=level, spread=1.0), 0, 0.3)
    # The black itself spreading: a front of noise that starts as a point in the middle and opens out to both ears as it
    # grows, its band sinking and its weight building as it comes on over the camera...
    front = decorrelated(n, pink)
    opening = curve(n, [(0, 0.0), (1.2, 0.15), (black, 1.0), (span, 1.0)])
    front = front.mean(axis=0, keepdims=True) * (1 - opening) + front * opening
    centre = curve(n, [(0, 3500), (black, 220), (span, 220)], 'log')
    front = np.vstack([sweep_filter(c, 'bandpass', centre, order=2, width=1.4) for c in front])
    m.add(rms_norm(front, 0.2) * grow ** 1.5, 0, 0.55)
    # ...and the ground deleted ring after ring, each a low thump further out to one side or the other than the last,
    # lower and nearer together as the rings widen.
    for i in range(14):
        at = 0.25 + (black - 0.5) * (i / 13) ** 1.3
        q = ns(0.5)
        f0 = 110.0 - 70.0 * at / black
        thump = sine(curve(q, [(0, f0), (0.5, f0 * 0.5)], 'log')) * attack_decay(q, 0.004, 0.12)
        pan = (1 if i % 2 else -1) * min(1.0, 0.1 + at / black)
        ring = np.vstack([thump * np.sqrt((1 - pan) / 2), thump * np.sqrt((1 + pan) / 2)])
        m.add(ring, at, 0.18 + 0.4 * at / black)
    q = ns(total)
    m.add(sine(curve(q, [(0, 70), (total, 26)], 'log')) * curve(q, [(0, 0.1), (black, 1.0), (total, 1.0)]), 0, 0.15)
    vast = gap_spaces()[0]
    x = reverb(m.out(), vast, wet=0.2)[:, :n]
    # As the last of the picture goes black, everything winds down like a stopped tape; at 5.0 s it is gone.
    a = ns(wind)
    rate = curve(ns(0.4), [(0, 1.0), (0.25, 0.06), (0.4, 0.04)], 'log')
    x = np.concatenate([x[:, :a], np.vstack([varispeed(c[a:], rate) for c in x])], axis=1)
    return cut(master(x, peak=0.95, squash=0.2), total)


def gap_void():
    """Nothing (9.0 s, NOTHING to END): after the cut, near silence; the ears ringing faintly, high and thin, dying
    away; a heartbeat, very low and slow, barely there and slowing, one of them as the camera comes back into the
    shooter's eyes (5.0 s) and the last under the song coming in; and the faintest air of the dark, gone by the time
    the song is up."""
    total = gap_at('END', since='NOTHING')
    back = gap_at('RETURN', since='NOTHING')
    song = back + 1.0
    n = ns(total)
    t = times(total)
    m = Mix(total)
    drift = 1 + 0.0006 * np.sin(2 * np.pi * 0.05 * t)
    whine = np.vstack([sine(7040.0 * drift), sine(7043.5 * drift)])
    m.add(whine * curve(n, [(0, 0.0), (0.3, 0.0), (0.8, 1.0), (back, 0.3), (song + 0.5, 0.0), (total, 0.0)]), 0, 0.05)
    for at, size in ((0.9, 1.0), (2.8, 0.85), (back, 0.75), (back + 2.3, 0.35)):
        for dt, g in ((0.0, 1.0), (0.28, 0.6)):
            q = ns(1.4)
            tq = np.arange(q) / SR
            beat = (sine(np.full(q, 34.0)) + 0.35 * sine(np.full(q, 68.0))) * (1 - np.exp(-tq / 0.03)) * np.exp(-tq / 0.18)
            m.add(beat, at + dt, size * g)
    air = decorrelated(n, lambda k: lp(brown(k), 160))
    m.add(air * curve(n, [(0, 0.0), (1.0, 1.0), (song, 1.0), (total - 0.5, 0.0), (total, 0.0)]), 0, 0.02)
    return cut(master(fade(m.out(), 0.0, 1.0), peak=0.9), total)


def gap_rebuild():
    """Reality remade (32 s, from the key used again): a soft loading hum and a run of glass tones as the loading
    screen comes up; Yggdrasil growing up out of the hole, a vast low choir on D swelling, a shimmer climbing and the
    tick of light forming like frost; its nine worlds lighting three at a time, a bell for each (4.6 to 5.9 s); a
    rising choir as light gathers in it, a deep pulse as it goes out of its foot and a rush down the long root to the
    shooter, landing in a huge sweep of air (11 s); then the world assembling itself out of the hole, grains of it
    falling into place faster and faster over a D major chord building in glass, the black's own sound played
    backwards and sucked away; resolving at 26 s into a full choir and chimes as the last of it is back, the tree
    drawing back down into the hole in a long falling breath; dying away by 32 s."""
    total = 32.0
    n = ns(total)
    t = times(total)
    vast, glass_hall, _, world = gap_spaces()
    m = Mix(total)
    # Loading: a soft hum and a run of glass tones climbing.
    m.add(glass(((146.83, 0.6), (220.0, 0.4), (293.66, 0.3)), n) * curve(n, [(0, 0.0), (1.5, 0.5), (8.0, 0.3), (11.0, 0.0),
                                                                              (total, 0.0)]), 0, 0.25)
    for i, f in enumerate((587.33, 880.0, 1174.66, 1760.0)):
        m.add(struck(f, 2.0, tau=0.5), 0.4 + 0.3 * i, 0.12, position=-0.5 + 0.33 * i)
    # Yggdrasil: a vast low choir on D and A, swelling as it grows up out of the hole.
    for f, g in ((73.42, 1.0), (110.0, 0.7), (146.83, 0.5)):
        m.add(choir(f, n, vowel=OO, voices=6) * curve(n, [(0, 0.0), (2.0, 0.0), (7.0, 1.0), (11.0, 0.8), (14.0, 0.4),
                                                          (26.0, 0.6), (30.0, 0.0), (total, 0.0)]), 0, 0.25 * g)
    # Growing (1 to 9 s): a shimmer of high partials climbing as it rises, and the tick of light forming, like frost.
    grow = curve(n, [(0, 0.0), (1.0, 0.0), (5.0, 1.0), (9.0, 0.6), (11.0, 0.0), (total, 0.0)])
    climb = curve(n, [(0, 1.0), (1.0, 1.0), (9.0, 1.5), (total, 1.5)], 'log')
    shimmer = sum(sine(f * climb) * (0.5 + 0.5 * np.sin(2 * np.pi * (3.0 + k) * t + k)) * a
                  for k, (f, a) in enumerate(((1174.66, 0.3), (1760.0, 0.25), (2349.32, 0.2), (2637.0, 0.12))))
    m.add(stereo(shimmer) * grow, 0, 0.06)
    def tick():
        q = ns(rng.uniform(0.01, 0.03))
        return hp(white(q), rng.uniform(3000, 7000)) * attack_decay(q, 0.0005, q / SR / 4) * rng.uniform(0.3, 1.0)
    m.add(grains(n, lambda s: 0 if s < 1.0 or s > 10.0 else 40 + 160 * np.sin(np.pi * (s - 1.0) / 9.0), tick, start=1.0, end=10.0),
          0, 0.12)
    # The nine worlds lighting, three at a time (4.6, 5.2, 5.9 s): a soft breath, then each a struck bell over a low swell.
    m.add(stereo(inhale(1.0, 150, 3000, tau=0.3)), 3.7, 0.4)
    bells = ((587.33, 739.99, 880.0), (880.0, 1108.73, 1318.51), (1174.66, 1479.98, 1760.0))
    for level, (at, notes) in enumerate(zip((4.6, 5.2, 5.9), bells)):
        q = ns(2.0)
        m.add(sat(sine(curve(q, [(0, 55 + 10 * level), (2.0, 35)], 'log')) * attack_decay(q, 0.01, 0.5), 1.2), at, 0.35)
        for i, f in enumerate(notes):
            m.add(struck(f, 4.0, tau=1.3, bright=0.35), at + 0.07 * i, 0.11, position=-0.7 + 0.7 * i)
    # The light gathering in it (8 s): a rising choir and a breath drawn in; a deep pulse as it goes out of its foot
    # (9.7 s) and rushes down the long root, landing at the shooter's feet with the sweep and the boom (11 s).
    m.add(choir(293.66, ns(3.0), vowel=AH, voices=6, glide=curve(ns(3.0), [(0, 0.75), (3.0, 1.0)])) *
          curve(ns(3.0), [(0, 0.0), (2.5, 1.0), (3.0, 0.0)]), 8.0, 0.3)
    q = ns(1.4)
    tq = np.arange(q) / SR
    m.add(stereo(sat(np.sin(2 * np.pi * 41.2 * tq) * attack_decay(q, 0.02, 0.5) * 1.4, 1.3)), 9.7, 0.55)
    q = ns(1.3)
    rush = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 300), (1.3, 2500)], 'log'), order=2, width=1.6)
    m.add(stereo(norm(rush) * curve(q, [(0, 0.0), (1.1, 1.0), (1.3, 0.0)])), 9.7, 0.25)
    m.add(stereo(inhale(0.8, 300, 6000, tau=0.25)), 10.2, 0.6)
    q = ns(2.5)
    sweep = decorrelated(q, pink)
    sweep = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 6000), (2.5, 200)], 'log'), width=2.0) for c in sweep])
    m.add(rms_norm(sweep, 0.2) * attack_decay(q, 0.02, 0.7), 11.0, 0.9)
    q = ns(2.0)
    m.add(sat(sine(curve(q, [(0, 50), (2.0, 25)], 'log')) * attack_decay(q, 0.004, 0.6) * 1.5, 1.4), 11.0, 0.6)
    # The world assembling: grains of it falling into place, faster and faster, then fewer as it is done.
    def place():
        q = ns(rng.uniform(0.02, 0.08))
        y = bp(white(q), rng.uniform(100, 400), rng.uniform(1500, 5000))
        # Played backwards: each comes in out of nothing and lands with a tap.
        return (y * attack_decay(q, 0.001, q / SR / 3))[::-1] * min(rng.lognormal(-0.8, 0.5), 1.2)
    rate = lambda s: 0 if s < 11.5 else 10 + 380 * np.sin(np.pi * min(1.0, (s - 11.5) / 15.0)) ** 2
    m.add(grains(n, rate, place, start=11.5, end=27.0, spread=1.0), 0, 0.4)
    # The black's sound sucked away backwards under it.
    erase = make('gap_erase')
    back = erase[:, ::-1] * curve(erase.shape[1], [(0, 0.0), (1.0, 0.6), (erase.shape[1] / SR, 1.0)])
    m.add(back, 26.0 - erase.shape[1] / SR, 0.35)
    # A D major chord building in glass from the sweep to the resolve.
    chord = ((146.83, 0.5), (185.0, 0.4), (220.0, 0.4), (293.66, 0.35), (369.99, 0.25), (440.0, 0.2), (587.33, 0.15))
    m.add(glass(chord, n) * curve(n, [(0, 0.0), (11.0, 0.0), (26.0, 0.8), (29.0, 1.0), (total, 0.0)]), 0, 0.35)
    # Resolve: the full choir and chimes as the last of it is back.
    for f in (146.83, 185.0, 220.0, 293.66):
        m.add(choir(f, ns(6.0), vowel=AH, voices=5) * curve(ns(6.0), [(0, 0.0), (0.8, 1.0), (6.0, 0.0)]), 26.0, 0.18)
    for i, f in enumerate((1174.66, 1479.98, 1760.0, 2349.32)):
        m.add(struck(f, 5.0, tau=1.5, bright=0.3), 26.0 + 0.12 * i, 0.08, position=-0.6 + 0.4 * i)
    # The tree drawing back down into the hole (24 to 29 s): a long falling breath, the shimmer run backwards.
    q = ns(5.0)
    down = sweep_filter(pink(q), 'bandpass', curve(q, [(0, 3000), (5.0, 180)], 'log'), order=2, width=1.8)
    m.add(stereo(norm(down) * curve(q, [(0, 0.0), (1.5, 1.0), (5.0, 0.0)])), 24.0, 0.12)
    x = reverb(m.out(), vast, wet=0.35)[:, :n]
    return master(fade(x, 0.0, 2.0), peak=0.92)


# --- Þ-01 Mjölnir ------------------------------------------------------------------------------------
# The hammer raised and the call leaving it; the camera rising into the storm; the feed's bed, the planet's own radio
# noise of lightning (sferics, tweeks, whistlers) over a drone pulsing with the Earth-ionosphere cavity's 7.83 Hz; the
# charge drawn in; MJÖLNIR, an anvil the size of a storm; the stepped leader; out in the world the storm gathering and
# the air buzzing with charge; the stroke and its restrikes; thunder rolling off the hills; the arcs; and the hiss of
# fused ground shedding its charge. In D, the lowest notes the drone can hold.

# ThunderTimeline's beats, in ticks from the hammer going up. The sounds are cut to fit them, so if the timeline
# changes, change it here too and make the Mjölnir sounds again.
THUNDER = dict(RAISE=0, CALL=16, RISE=30, FEED=54, DRAW=116, FORGE=206, LEADER=276, INBOUND=326, STROKE=366)
# The channel strikes again this many ticks after the stroke (ThunderTimeline.RESTRIKES).
RESTRIKES = (5, 11, 19)


def thunder_at(beat, ticks=0, since='RAISE'):
    """Seconds from the beat `since` to `ticks` after `beat`."""
    return (THUNDER[beat] + ticks - THUNDER[since]) / 20.0


def crack(width_ms=4.0, bright=1.0):
    """The crack of a lightning channel close by: an N-wave of pressure (a sharp rise, a ramp down through zero, a sharp
    return) a few milliseconds long, with a spray of high noise off it."""
    n = ns(0.09)
    t = np.arange(n) / SR
    w = width_ms / 1000.0
    nwave = np.where(t < w, 1.0 - 2.0 * t / w, 0.0)
    nwave = lp(nwave, 12000, 2)
    spray = hp(white(n), 2500) * np.exp(-t / 0.01)
    body = lp(white(n), 900) * np.exp(-t / 0.02)
    return nwave * 0.9 + spray * 0.45 * bright + body * 0.4


def tear(seconds, rate, bright=1.0):
    """Close lightning tearing: a rattle of small cracks like cloth ripping, every branch of the channel going."""
    n = ns(seconds)

    def snap():
        k = ns(rng.uniform(0.004, 0.02))
        tt = np.arange(k) / SR
        return hp(white(k), rng.uniform(1200, 4000)) * np.exp(-tt / rng.uniform(0.001, 0.006)) * rng.uniform(0.3, 1.0)
    return grains(n, rate, snap, spread=0.7) * bright


def roll(seconds, swells, low=150.0, close=True):
    """Thunder rolling: low noise in long swells as the sound arrives from farther and farther along the channel and
    comes back off the hills, each swell a little later and softer."""
    n = ns(seconds)
    t = times(seconds)
    body = decorrelated(n, lambda k: brown(k) * 0.8 + pink(k) * 0.25)
    body = np.vstack([lp(c, low * (1.6 if close else 1.0), 2) for c in body])
    env = np.zeros(n)
    for at, gain, length in swells:
        env += gain * np.exp(-((t - at) / length) ** 2)
    env = np.minimum(env, 1.3)
    rumble = sine(curve(n, [(0, 46), (seconds, 28)], 'log')) * env * 0.3
    return body * env + stereo(rumble)


def sferic():
    """One sferic: the radio click of a lightning stroke a thousand kilometres off, with a little ring."""
    k = ns(0.02)
    tt = np.arange(k) / SR
    return bp(white(k), 2000, 9000) * np.exp(-tt / 0.0012) * min(rng.lognormal(-0.6, 0.6), 1.5)


def tweek():
    """A tweek: a sferic that has bounced round the ionosphere, a ping that falls to the cut-off near 1.8 kHz."""
    k = ns(0.12)
    f = curve(k, [(0, rng.uniform(3500, 6000)), (0.03, 2100), (0.12, 1750)], 'log')
    return sine(f) * attack_decay(k, 0.001, 0.035) * rng.uniform(0.3, 0.8)


def whistler(seconds=None):
    """A whistler: a lightning stroke's radio noise that has run out along the Earth's magnetic field to the far side of
    the planet and back, the high frequencies first, falling for a second or two."""
    seconds = seconds or rng.uniform(1.0, 2.2)
    k = ns(seconds)
    tt = np.arange(k) / SR + 0.05
    f = 900.0 + 5200.0 * (0.05 / tt) ** 0.6
    tone = sine(f) + 0.3 * sine(f * 1.01)
    return tone * curve(k, [(0, 0), (0.08, 1.0), (seconds * 0.5, 0.6), (seconds, 0.0)]) * 0.5


def corona(seconds, swell):
    """The air buzzing with charge: corona hiss off every point and edge, a crackle, and a hum at twice the
    mains-like rate the discharge pulses at."""
    n = ns(seconds)
    t = times(seconds)
    hiss = decorrelated(n, lambda k: hp(white(k), 3000))
    pulse = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * 120.0 * t)) * (0.7 + 0.3 * rng.standard_normal(n).clip(-1, 1))
    hum = lp(saw(np.full(n, 120.0), n), 1800) * 0.4 + lp(saw(np.full(n, 180.0), n), 1200) * 0.2
    m = hiss * pulse * swell * 0.5
    m += stereo(hum * swell)
    m += grains(n, lambda s: 40 + 400 * float(np.interp(s, t, swell)), crackle_pop, spread=0.9) * 0.9
    return m


def mjolnir_raise():
    """The hammer raised to call the storm: it swings up with a heavy whoosh and a creak of its grip, the charge builds in
    it as a climbing, crackling whine, and the call leaves it with a crack and an upward tearing roar, the sky answering
    with a clap of thunder."""
    total = thunder_at('RISE')
    call = thunder_at('CALL')
    m = Mix(total + 1.0)
    k = ns(0.55)
    swing = decorrelated(k, pink)
    swing = np.vstack([sweep_filter(c, 'bandpass', curve(k, [(0, 260), (0.3, 1300), (0.55, 420)], 'log'), order=2, width=1.4)
                       for c in swing]) * attack_decay(k, 0.12, 0.16)
    m.add(swing, 0.0, 0.7)
    for at in (0.04, 0.4):
        q = ns(0.14)
        creak = resonator(white(q) * attack_decay(q, 0.01, 0.04), curve(q, [(0, 190), (0.14, 150)], 'log'), q=10)
        m.add(norm(creak), at, 0.12, position=0.3)
    q = ns(call - 0.2)
    rise = curve(q, [(0, 0.0), (call - 0.2, 1.0)]) ** 1.6
    whine = resonator(white(q), curve(q, [(0, 520), (call - 0.2, 2900)], 'log'), q=40)
    m.add(stereo(norm(whine) * rise), 0.2, 0.35)
    buzz = bp(saw(curve(q, [(0, 55), (call - 0.2, 110)], 'log'), q), 80, 2400) * rise
    m.add(stereo(norm(buzz)), 0.2, 0.25)
    m.add(grains(q, lambda s: 20 + 600 * (s / (call - 0.2)) ** 2, crackle_pop, spread=0.6), 0.2, 0.45)
    # The call.
    m.add(stereo(crack(3.0)), call, 1.0)
    m.add(tear(0.4, lambda s: 900 * np.exp(-s / 0.12)), call, 0.7)
    q = ns(0.9)
    up = decorrelated(q, pink)
    up = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 700), (0.5, 5200), (0.9, 7000)], 'log'), order=2, width=1.6)
                    for c in up]) * attack_decay(q, 0.005, 0.25)
    m.add(up, call, 0.6)
    q = ns(1.2)
    thump = sine(curve(q, [(0, 70), (0.6, 34)], 'log')) * attack_decay(q, 0.004, 0.3)
    m.add(sat(thump * 1.4, 1.6), call, 0.9)
    m.add(roll(1.2, [(0.25, 1.0, 0.25), (0.6, 0.6, 0.3)], low=260), call + 0.05, 0.8)
    outdoor, _, _, _ = spaces()
    return master(reverb(m.out(), outdoor, wet=0.3)[:, :ns(total + 1.0)], peak=0.95, drive=1.4, squash=0.4)


def mjolnir_call():
    """The call heard out in the world, from the hammer: a crack, the tear of a bolt going up, and a clap of thunder
    rolling away."""
    total = 4.5
    m = Mix(total)
    m.add(stereo(crack(5.0, 0.7)), 0.0, 1.0)
    m.add(tear(0.4, lambda s: 600 * np.exp(-s / 0.15), 0.6), 0.0, 0.6)
    q = ns(1.4)
    thump = sine(curve(q, [(0, 60), (0.8, 30)], 'log')) * attack_decay(q, 0.006, 0.35)
    m.add(sat(thump * 1.4, 1.6), 0.02, 0.9)
    m.add(roll(4.2, [(0.2, 1.0, 0.25), (0.8, 0.7, 0.5), (1.9, 0.45, 0.8), (3.0, 0.2, 0.6)], close=False), 0.05, 1.0)
    outdoor, _, _, _ = spaces()
    return master(mono(reverb(m.out(), outdoor, wet=0.6)[:, :ns(total)]), peak=0.95, drive=1.3)


def mjolnir_denied():
    """The hammer refuses: a dull iron clunk and a fizzle of charge that will not hold."""
    total = 0.6
    m = Mix(total)
    q = ns(0.4)
    clunk = chime(130.0, q, tau=0.12, ratios=(1.0, 2.76, 5.4), bright=0.35) + lp(white(q), 600) * attack_decay(q, 0.001, 0.015)
    m.add(clunk, 0.0, 0.7)
    q = ns(0.3)
    m.add(grains(q, lambda s: 300 * np.exp(-s / 0.08), crackle_pop, spread=0.3), 0.04, 0.6)
    return master(mono(m.out()), peak=0.8)


def thunder_rise():
    """Up into the storm: wind tearing past, the storm's dark roar closing round, thunder somewhere inside it."""
    total = thunder_at('FEED', since='RISE')
    n = ns(total + 0.6)
    t = times(total + 0.6)
    body = decorrelated(n, pink)
    centre = curve(n, [(0, 220), (0.7, 700), (total, 1600), (total + 0.6, 600)], 'log')
    wind = np.vstack([sweep_filter(c, 'bandpass', centre * (1 + 0.15 * np.sin(2 * np.pi * 1.1 * t + k)), order=2, width=1.8)
                      for k, c in enumerate(body)])
    m = Mix(total + 0.6)
    m.add(wind * curve(n, [(0, 0.05), (total * 0.8, 0.8), (total, 1.0), (total + 0.6, 0.0)]), 0, 0.9)
    low = decorrelated(n, brown)
    low = np.vstack([lp(c, 120) for c in low]) * curve(n, [(0, 0), (total, 1.0), (total + 0.6, 0)])
    m.add(low, 0, 0.7)
    m.add(roll(1.4, [(0.3, 1.0, 0.25), (0.8, 0.5, 0.3)], low=200, close=False), total * 0.3, 0.5)
    return master(m.out(), peak=0.8, squash=0.3)


def thunder_feed():
    """The bed under the storm feed: a dark drone breathing with the Earth-ionosphere cavity's own 7.83 Hz, and the radio
    sound of the planet's lightning over it, the clicks of sferics, the pings of tweeks and the falling whistles of
    whistlers, busier as the charge is drawn in and the storm winds up."""
    total = thunder_at('INBOUND', since='FEED')
    draw = thunder_at('DRAW', since='FEED')
    forge = thunder_at('FORGE', since='FEED')
    leader = thunder_at('LEADER', since='FEED')
    n = ns(total)
    t = times(total)
    level = curve(n, [(0, 0), (1.0, 0.6), (draw, 0.7), (forge, 1.0), (forge + 0.2, 0.45), (leader, 0.6), (total - 0.05, 0.8),
                      (total, 0.0)])
    bright = curve(n, [(0, 240), (draw, 320), (forge, 900), (forge + 0.2, 260), (leader, 500), (total, 1400)], 'log')
    schumann = 1.0 - 0.22 * (0.5 + 0.5 * np.sin(2 * np.pi * 7.83 * t))
    m = Mix(total)
    for f, g, det in ((36.71, 1.0, 0.004), (55.0, 0.55, 0.006), (73.42, 0.35, 0.008)):
        stack = supersaw(f * (1 + 0.003 * np.sin(2 * np.pi * 0.06 * t)), n, voices=5, detune=det, spread=0.9)
        stack = np.vstack([sweep_filter(c, 'lowpass', bright * (1 + 0.2 * np.sin(2 * np.pi * 0.13 * t + k)), order=2)
                           for k, c in enumerate(stack)])
        m.add(stack * level * schumann, 0, g)
    m.add(sine(np.full(n, 36.71)) * level * schumann * 0.5, 0, 0.3)
    busy = lambda s: float(np.interp(s, [0, draw, forge, leader, total], [25, 40, 220, 80, 140]))
    m.add(grains(n, busy, sferic, spread=0.95), 0, 0.22)
    m.add(grains(n, lambda s: busy(s) * 0.05, tweek, spread=0.8), 0, 0.12)
    at = 0.6
    while at < total - 1.0:
        m.add(whistler(), at, 0.05, position=rng.uniform(-0.7, 0.7))
        at += rng.uniform(1.2, 2.6)
    _, _, _, space = spaces()
    x = reverb(m.out(), space, wet=0.3)
    r = np.sqrt(np.mean(x ** 2, axis=1, keepdims=True))
    x *= r.mean() / np.maximum(r, 1e-9)
    return master(x, peak=0.5, squash=0.3)


def thunder_draw():
    """The charge drawn in across the planet: the radio crackle of its storms thickening into a roar as the ring closes,
    a whine climbing under it, and a ringing shimmer as the ring meets the target."""
    total = thunder_at('FORGE', since='DRAW')
    n = ns(total + 0.1)
    t = times(total + 0.1)
    m = Mix(total + 0.1)
    swell = curve(n, [(0, 0.1), (total * 0.7, 0.6), (total - 0.4, 1.0), (total, 1.0)]) ** 1.5
    crackle = grains(n, lambda s: 60 + 1400 * (s / total) ** 2.2, sferic, spread=1.0)
    crackle = np.vstack([sweep_filter(c, 'bandpass', curve(n, [(0, 1800), (total, 5200)], 'log'), order=2, width=2.0)
                         for c in crackle])
    m.add(crackle * swell, 0, 0.9)
    whine = resonator(white(n), curve(n, [(0, 260), (total, 2300)], 'log'), q=35)
    m.add(stereo(norm(whine) * swell), 0, 0.3)
    m.add(stereo(sine(curve(n, [(0, 30), (total, 58)], 'log')) * swell), 0, 0.5)
    # The ring meets the target.
    for i, f in enumerate((1174.66, 1760.0, 2349.32, 2637.0)):
        m.add(chime(f, ns(1.0), tau=0.6, bright=0.4), total - 0.4 + 0.03 * i, 0.12, position=-0.5 + 0.33 * i)
    _, _, _, space = spaces()
    x = master(reverb(m.out(), space, wet=0.25)[:, :n], peak=0.9, squash=0.4)
    return cut(x, total)


def thunder_forge():
    """MJÖLNIR: a hammer on an anvil the size of a storm, a long iron ring over a roll of thunder, and the vortex winding
    up with a howl."""
    total = thunder_at('LEADER', since='FORGE')
    hit = 8 / 20.0
    m = Mix(total)
    q = ns(total - hit)
    anvil = chime(146.83, q, tau=2.6, ratios=(1.0, 2.76, 5.4, 8.93, 13.34), bright=0.55)
    anvil += chime(220.0, q, tau=1.8, ratios=(1.0, 2.71, 5.32), bright=0.45) * 0.6
    clank = hp(white(ns(0.05)), 1500) * attack_decay(ns(0.05), 0.0005, 0.008)
    m.add(stereo(norm(anvil)) * np.array([[1.0], [0.97]]), hit, 0.55)
    m.add(stereo(clank), hit, 0.8)
    k = ns(1.6)
    boom = sine(curve(k, [(0, 55), (1.6, 27)], 'log')) * attack_decay(k, 0.004, 0.5)
    m.add(sat(boom * 1.5, 1.7), hit, 1.0)
    m.add(roll(total - hit, [(0.2, 1.0, 0.4), (1.0, 0.7, 0.6), (2.1, 0.5, 0.6)]), hit, 0.75)
    n = ns(total)
    howl = decorrelated(n, pink)
    howl = np.vstack([sweep_filter(c, 'bandpass', curve(n, [(0, 300), (total, 900)], 'log') * (1 + 0.25 * np.sin(2 * np.pi * 0.7 * times(total) + k)),
                                   order=2, width=1.0) for k, c in enumerate(howl)])
    m.add(howl * curve(n, [(0, 0.0), (hit, 0.2), (total, 0.7)]), 0, 0.35)
    outdoor, hall, _, _ = spaces()
    return master(reverb(m.out(), hall, wet=0.35)[:, :n], peak=0.95, drive=1.3, squash=0.35)


def thunder_leader():
    """The stepped leader feeling its way down, a jolt every fifty microseconds slowed to something you can count,
    quickening as it nears the ground; a hiss rising under it, and the rush of the ground coming up."""
    total = thunder_at('INBOUND', since='LEADER')
    n = ns(total)
    m = Mix(total)
    steps = 60
    shot = total - 0.2
    for k in range(1, steps + 1):
        # ThunderShots.leaderReach: the step count grows as 0.35 p + 0.65 p^2.
        p = (-0.35 + np.sqrt(0.35 ** 2 + 4 * 0.65 * k / steps)) / (2 * 0.65)
        at = p * shot
        q = ns(0.05)
        tt = np.arange(q) / SR
        zap = hp(white(q), 1800) * np.exp(-tt / 0.004) + np.sign(np.sin(2 * np.pi * rng.uniform(300, 900) * tt)) * np.exp(-tt / 0.01) * 0.2
        m.add(zap * (0.4 + 0.6 * p), at, 0.4, position=rng.uniform(-0.25, 0.25))
    hiss = decorrelated(n, lambda q: hp(white(q), 4000)) * curve(n, [(0, 0.1), (total, 1.0)]) ** 2
    m.add(hiss, 0, 0.25)
    q = ns(0.8)
    rush = decorrelated(q, pink)
    rush = np.vstack([sweep_filter(c, 'bandpass', curve(q, [(0, 400), (0.8, 6000)], 'log'), order=2, width=1.6) for c in rush])
    m.add(rush * curve(q, [(0, 0), (0.75, 1.0), (0.8, 1.0)]), total - 0.8, 0.7)
    _, hall, _, _ = spaces()
    x = master(reverb(m.out(), hall, wet=0.2)[:, :n], peak=0.9, squash=0.35)
    return cut(x, total)


def thunder_storm():
    """A storm gathering overhead, heard from under it: wind getting up, and thunder rolling inside the clouds, closer and
    more often as it winds up."""
    total = thunder_at('INBOUND', since='FEED')
    n = ns(total)
    t = times(total)
    m = Mix(total)
    wind = decorrelated(n, pink)
    wind = np.vstack([sweep_filter(c, 'bandpass', 380 * (1 + 0.35 * np.sin(2 * np.pi * 0.11 * t + k)), order=2, width=2.0)
                      for k, c in enumerate(wind)])
    m.add(wind * curve(n, [(0, 0.2), (total, 1.0)]), 0, 0.4)
    swells = []
    at = 0.5
    while at < total - 1.0:
        p = at / total
        swells.append((at, 0.35 + 0.65 * p, 0.3 + 0.5 * rng.random()))
        at += rng.uniform(2.5, 4.5) * (1.0 - 0.6 * p)
    m.add(roll(total, swells, low=220, close=False), 0, 1.0)
    outdoor, _, _, _ = spaces()
    return master(mono(reverb(m.out(), outdoor, wet=0.5)[:, :n]), peak=0.8, squash=0.3)


def thunder_charge(close=True):
    """The moment before the stroke: the air buzzing and crackling with charge, streamers hissing off everything, rising
    until the stroke cuts it dead."""
    total = thunder_at('STROKE', since='INBOUND')
    n = ns(total + 0.05)
    swell = curve(n, [(0, 0.1), (total * 0.6, 0.45), (total, 1.0), (total + 0.05, 1.0)]) ** 1.4
    x = corona(total + 0.05, swell)
    if not close:
        x = np.vstack([lp(c, 5000) for c in x])
    x = master(x, peak=0.9, squash=0.4)
    x = cut(x, total)
    return x if close else mono(x)


def thunder_stroke(close=True):
    """The bolt: a crack like the sky splitting, the rip of every branch going at once, the restrikes cracking again down
    the same channel, and a blast of thunder. Close up the ears ring after it, as they do after Gungnir's impact."""
    total = 6.0
    n = ns(total)
    m = Mix(total)
    m.add(stereo(crack(2.5 if close else 6.0, 1.0 if close else 0.5)), 0.0, 1.0)
    m.add(tear(0.45, lambda s: 2500 * np.exp(-s / 0.1), 1.0 if close else 0.5), 0.0, 0.8)
    for i, ticks in enumerate(RESTRIKES):
        m.add(stereo(crack(3.5, 0.7)), ticks / 20.0, 0.55 - 0.1 * i)
        m.add(tear(0.2, lambda s: 900 * np.exp(-s / 0.06), 0.6), ticks / 20.0, 0.4)
    k = ns(3.0)
    blast = decorrelated(k, lambda q: white(q) * 0.5 + pink(q) * 0.8)
    cutoff = curve(k, [(0, 9000 if close else 2500), (0.2, 2200), (1.0, 500), (3.0, 140)], 'log')
    blast = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in blast]) * attack_decay(k, 0.002, 0.55)
    m.add(sat(blast * 3.0, 2.4), 0.0, 0.9)
    sub = sine(curve(k, [(0, 62), (0.3, 40), (3.0, 24)], 'log')) * attack_decay(k, 0.003, 0.8)
    m.add(sat(sub * 1.6, 1.8), 0.0, 1.0)
    outdoor, _, _, _ = spaces()
    x = reverb(m.out(), outdoor, wet=0.45 if close else 0.75)[:, :n]
    if close:
        cutoff = curve(n, [(0, 20000), (0.12, 20000), (0.2, 700), (1.2, 1600), (3.6, 15000), (total, 20000)], 'log')
        x = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in x])
        tt = times(total)
        ring = np.sin(2 * np.pi * 4100 * tt) + 0.6 * np.sin(2 * np.pi * 4111 * tt)
        ring *= curve(n, [(0, 0), (0.15, 0), (0.25, 1.0), (1.0, 0.8), (3.2, 0.0), (total, 0.0)])
        x = x + stereo(ring) * np.array([[0.04], [0.045]])
    x = master(x, peak=0.98, drive=2.0, squash=0.5)
    return x if close else master(mono(x), peak=0.98)


def thunder_roll(close=True):
    """Thunder rolling away off the hills for seconds after the bolt, in long swells, each one farther off."""
    total = 9.0
    swells = [(0.3, 1.0, 0.5), (1.3, 0.85, 0.7), (2.6, 0.6, 0.9), (4.2, 0.4, 1.1), (6.0, 0.22, 1.2)]
    m = Mix(total)
    m.add(roll(total, swells, low=170 if close else 130, close=close), 0, 1.0)
    outdoor, _, _, _ = spaces()
    x = reverb(m.out(), outdoor, wet=0.4)[:, :ns(total)]
    x = master(fade(x, 0.0, 2.0), peak=0.95, drive=1.5, squash=0.3)
    return x if close else master(mono(x), peak=0.95)


def thunder_arc():
    """An arc jumping: a sharp snap and a buzzing crackle."""
    total = 0.5
    m = Mix(total)
    m.add(stereo(crack(1.5, 1.0)), 0.0, 0.8)
    q = ns(0.25)
    tt = np.arange(q) / SR
    buzz = np.sign(np.sin(2 * np.pi * 240 * tt)) * 0.3 + hp(white(q), 2500)
    m.add(buzz * attack_decay(q, 0.001, 0.06), 0.005, 0.5)
    m.add(tear(0.2, lambda s: 600 * np.exp(-s / 0.05), 0.7), 0.0, 0.5)
    return master(mono(m.out()), peak=0.95)


def thunder_aftermath(close=True):
    """After the bolt: the fused ground ticking and sizzling as it sheds its charge, fires crackling, the last of the
    storm grumbling as it unwinds, and wind."""
    total = 12.0
    n = ns(total)
    t = times(total)
    m = Mix(total)
    sizzle = decorrelated(n, lambda k: hp(white(k), 5000))
    sizzle *= (0.6 + 0.4 * np.abs(lp(rng.standard_normal(n), 6) / 0.05).clip(0, 1.5))
    m.add(sizzle * curve(n, [(0, 1.0), (total, 0.3)]), 0, 0.12)
    m.add(grains(n, lambda s: 60 * np.exp(-s / 6.0) + 10, sferic, spread=0.9), 0, 0.35)
    m.add(grains(n, lambda s: 22, crackle_pop, spread=0.8), 0, 0.6)
    m.add(roll(total, [(2.0, 0.35, 0.8), (6.5, 0.25, 1.0), (10.0, 0.15, 0.8)], low=120, close=False), 0, 0.7)
    wind = decorrelated(n, pink)
    wind = np.vstack([sweep_filter(c, 'bandpass', 450 * (1 + 0.4 * np.sin(2 * np.pi * 0.12 * t + k)), order=2, width=2.0)
                      for k, c in enumerate(wind)])
    m.add(wind, 0, 0.25)
    out = m.out() * curve(n, [(0, 0), (1.0, 1.0), (8.0, 0.7), (total, 0.0)])
    outdoor, _, _, _ = spaces()
    x = master(reverb(out, outdoor, wet=0.3)[:, :n], peak=0.6, squash=0.3)
    return x if close else master(mono(x), peak=0.6)


# Longest a sound may run (seconds): the feed's sounds must die away before the feed hands back to
# the world at INBOUND.
CAPS = {'uplink_lock': 2.5, 'camera_rise': 1.7, 'feed_zoom': 3.0, 'feed_ambience': 18.2, 'feed_relay': 3.4,
        'feed_wake': 4.2, 'feed_load': 2.4, 'feed_coils': 4.3, 'feed_release': 1.62, 'feed_strike': 0.95,
        'feed_cruise': 5.7, 'feed_transit': 2.6, 'feed_locate': 1.55, 'feed_reentry': 1.72,
        # The Ginnungagap sequence's slots.
        'gap_key': gap_at('FEED'), 'gap_ambience': gap_at('MAP', since='FEED'), 'gap_wake': 2.6,
        'gap_tear': gap_at('MAP', -8, since='OPEN'), 'gap_map': gap_at('CUT', since='MAP'), 'gap_lock': 1.2,
        'gap_extract': gap_at('SEND', 3, since='CUT'), 'gap_send': 1.6, 'gap_fall': gap_at('INBOUND', since='FALL'),
        'gap_drone': gap_at('CONTACT', -3, since='SEND'), 'gap_inbound': gap_at('CONTACT', since='INBOUND'),
        'gap_swap': 0.5, 'gap_contact': 1.0, 'gap_impact': gap_at('BLAST', since='CONTACT'),
        'gap_blast': gap_at('ERASURE', since='BLAST'), 'gap_erase': gap_at('NOTHING', since='ERASURE'),
        'gap_void': gap_at('END', since='NOTHING'), 'gap_rebuild': 32.0,
        # Mjölnir's slots.
        'mjolnir_raise': thunder_at('RISE'), 'thunder_rise': thunder_at('FEED', since='RISE'),
        'thunder_feed': thunder_at('INBOUND', since='FEED'), 'thunder_draw': thunder_at('FORGE', since='DRAW'),
        'thunder_forge': thunder_at('LEADER', since='FORGE'), 'thunder_leader': thunder_at('INBOUND', since='LEADER'),
        'thunder_storm': thunder_at('INBOUND', since='FEED'), 'thunder_charge': thunder_at('STROKE', since='INBOUND'),
        'thunder_charge_near': thunder_at('STROKE', since='INBOUND')}

# How loud each sound is (dB, the RMS of its loudest 400 ms). The game plays them at full volume, so this is
# the mix: the feed sits well down, the release and the re-entry come up, and the impact is far the loudest
# thing in the sequence, the way a film saves its loudest moment for the climax.
LEVELS = {'uplink_lock': -16, 'uplink_denied': -18, 'camera_rise': -21, 'feed_zoom': -22, 'feed_ambience': -27,
          'feed_relay': -20, 'feed_wake': -17, 'feed_load': -19, 'feed_coils': -21, 'feed_lap': -19,
          'feed_release': -11, 'feed_strike': -15, 'feed_cruise': -18, 'feed_transit': -22, 'feed_locate': -18,
          'feed_reentry': -13, 'strike_inbound': -14,
          'strike_inbound_near': -13, 'strike_impact': -7, 'strike_impact_near': -5, 'strike_rumble': -12,
          'strike_rumble_near': -11, 'strike_aftermath': -23, 'strike_aftermath_near': -20,
          # Ginnungagap: the key at the feed's level and its bed well under it, building through the title and the tear;
          # the other universe quiet, then swelling as the camera pulls back out of it; the bridge as big as Gungnir's
          # release, the fall and the block coming down rising under the drone; the contact tiny in the silence, the
          # impact frames far the loudest, the burst next, the erasure growing to its cut, then almost nothing.
          'gap_key': -17, 'gap_ambience': -26, 'gap_wake': -17, 'gap_tear': -16, 'gap_map': -18, 'gap_lock': -20,
          'gap_extract': -17, 'gap_send': -10, 'gap_fall': -14, 'gap_drone': -17, 'gap_inbound': -16, 'gap_swap': -18,
          'gap_rebuild': -16, 'gap_contact': -26, 'gap_impact': -6, 'gap_blast': -11.5, 'gap_erase': -14, 'gap_void': -38,
          # Mjölnir: the raise and the call loud and sudden; the feed's bed down under the radio crackle, the draw and the
          # leader building to the forge's anvil and the white; out in the world the storm and the charge rising to the
          # stroke, far the loudest thing, the roll after it, and the aftermath quiet.
          'mjolnir_raise': -13, 'mjolnir_call': -12, 'mjolnir_denied': -18, 'thunder_rise': -20, 'thunder_feed': -27,
          'thunder_draw': -17, 'thunder_forge': -11, 'thunder_leader': -15, 'thunder_storm': -18, 'thunder_charge': -16,
          'thunder_charge_near': -15, 'thunder_stroke': -6, 'thunder_stroke_near': -4.5, 'thunder_roll': -12,
          'thunder_roll_near': -11, 'thunder_arc': -12, 'thunder_aftermath': -23, 'thunder_aftermath_near': -20}

SOUNDS = {
    # name: (recipe, stereo?)
    'uplink_lock': (uplink_lock, True),
    'uplink_denied': (uplink_denied, False),
    'camera_rise': (camera_rise, True),
    'feed_zoom': (feed_zoom, True),
    'feed_ambience': (feed_ambience, True),
    'feed_relay': (feed_relay, True),
    'feed_wake': (feed_wake, True),
    'feed_load': (feed_load, True),
    'feed_coils': (feed_coils, True),
    'feed_lap': (feed_lap, True),
    'feed_release': (feed_release, True),
    'feed_strike': (feed_strike, True),
    'feed_cruise': (feed_cruise, True),
    'feed_transit': (feed_transit, True),
    'feed_locate': (feed_locate, True),
    'feed_reentry': (feed_reentry, True),
    'strike_inbound': (lambda: inbound(False), False),
    'strike_inbound_near': (lambda: inbound(True), True),
    'strike_impact': (lambda: impact(False), False),
    'strike_impact_near': (lambda: impact(True), True),
    'strike_rumble': (lambda: rumble(False), False),
    'strike_rumble_near': (lambda: rumble(True), True),
    'strike_aftermath': (lambda: aftermath(False), False),
    'strike_aftermath_near': (lambda: aftermath(True), True),
    'gap_key': (gap_key, True),
    'gap_ambience': (gap_ambience, True),
    'gap_wake': (gap_wake, True),
    'gap_tear': (gap_tear, True),
    'gap_map': (gap_map, True),
    'gap_lock': (gap_lock, True),
    'gap_extract': (gap_extract, True),
    'gap_send': (gap_send, True),
    'gap_fall': (gap_fall, True),
    'gap_drone': (gap_drone, True),
    'gap_inbound': (gap_inbound, True),
    'gap_swap': (gap_swap, False),
    'gap_contact': (gap_contact, True),
    'gap_impact': (gap_impact, True),
    'gap_blast': (gap_blast, True),
    'gap_erase': (gap_erase, True),
    'gap_void': (gap_void, True),
    'gap_rebuild': (gap_rebuild, True),
    'mjolnir_raise': (mjolnir_raise, True),
    'mjolnir_call': (mjolnir_call, False),
    'mjolnir_denied': (mjolnir_denied, False),
    'thunder_rise': (thunder_rise, True),
    'thunder_feed': (thunder_feed, True),
    'thunder_draw': (thunder_draw, True),
    'thunder_forge': (thunder_forge, True),
    'thunder_leader': (thunder_leader, True),
    'thunder_storm': (thunder_storm, False),
    'thunder_charge': (lambda: thunder_charge(False), False),
    'thunder_charge_near': (lambda: thunder_charge(True), True),
    'thunder_stroke': (lambda: thunder_stroke(False), False),
    'thunder_stroke_near': (lambda: thunder_stroke(True), True),
    'thunder_roll': (lambda: thunder_roll(False), False),
    'thunder_roll_near': (lambda: thunder_roll(True), True),
    'thunder_arc': (thunder_arc, False),
    'thunder_aftermath': (lambda: thunder_aftermath(False), False),
    'thunder_aftermath_near': (lambda: thunder_aftermath(True), True),
}


def write(name, x, wav_dir=None):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x, -1, 1)
    channels = 1 if x.ndim == 1 else 2
    pcm = (x.T if channels == 2 else x) * 32767
    pcm = pcm.astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        path = tmp.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    if wav_dir:
        os.makedirs(wav_dir, exist_ok=True)
        subprocess.run(['cp', path, os.path.join(wav_dir, name + '.wav')], check=True)
    target = os.path.join(OUT, name + '.ogg')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', path, '-c:a', 'libvorbis', '-q:a', '5', target], check=True)
    os.unlink(path)
    print('wrote %-22s %s %.2fs' % (name, 'stereo' if channels == 2 else 'mono  ', x.shape[-1] / SR))


def make(name):
    """One sound, held to its cap and brought to its level."""
    global rng
    recipe, is_stereo = SOUNDS[name]
    if name.startswith(('gap_', 'mjolnir_', 'thunder_')):
        # Ginnungagap's and Mjölnir's sounds draw on seeds of their own, so each comes out the same made alone or with the
        # rest.
        rng = np.random.default_rng(zlib.crc32(name.encode()))
    x = recipe()
    if not is_stereo:
        x = mono(x)
    cap = CAPS.get(name)
    if cap and x.shape[-1] > ns(cap):
        x = fade(x[..., :ns(cap)], 0.0, min(0.6, cap * 0.25))
    if name in LEVELS:
        x = limit(x * 10 ** ((LEVELS[name] - loudness(x)) / 20))
    return x


def main():
    args = sys.argv[1:]
    wav_dir = None
    if '--wav' in args:
        i = args.index('--wav')
        wav_dir = args[i + 1]
        del args[i:i + 2]
    names = args or list(SOUNDS)
    for name in names:
        write(name, make(name), wav_dir)


if __name__ == '__main__':
    main()
