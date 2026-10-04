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
    relay, wake, loading, laps, release, re-entry) and quiet telemetry chatter on top."""
    total = 13.4
    n = ns(total)
    t = times(total)
    # Sections relative to the start of the feed (StrikeTimeline: ORBIT=0, RELAY=1.4, WAKE=2.6,
    # LOADING=4.8, LAPS=5.8, DEBRIS=10.8, TERMINAL=12.1).
    level = curve(n, [(0, 0), (1.2, 0.55), (2.5, 0.6), (2.8, 0.85), (4.6, 0.8), (5.0, 0.55), (5.8, 0.6), (10.6, 1.0),
                      (10.8, 0.15), (11.4, 0.45), (12.1, 0.7), (13.4, 0.0)])
    bright = curve(n, [(0, 260), (2.6, 380), (4.8, 520), (5.8, 400), (10.8, 2400), (10.85, 300), (12.1, 900), (13.4, 1800)],
                   'log')
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
        m.add(pad * level * curve(n, [(0, 0), (2.6, 0.4), (5.8, 0.6), (10.8, 1.0), (10.85, 0.2), (13.4, 0.8)]), 0, g)
    shimmer = decorrelated(n, lambda k: sine(np.full(k, 1318.5) * (1 + 0.002 * rng.standard_normal() )))
    shimmer = shimmer * (0.5 + 0.5 * np.sin(2 * np.pi * 0.23 * t)) * level * 0.04
    m.add(shimmer, 0, 1.0)
    # Telemetry: soft blips and a little radio crackle, now and then.
    for at in np.arange(0.8, total - 0.5, 1.37):
        k = ns(0.05)
        blip = np.sin(2 * np.pi * rng.choice([2637, 3136, 2349]) * np.arange(k) / SR) * attack_decay(k, 0.002, 0.02)
        m.add(blip, at + rng.uniform(-0.2, 0.2), 0.05, position=rng.uniform(-0.8, 0.8))
    m.add(grains(n, lambda s: 6 + 30 * (s > 10.8), crackle_pop, spread=0.9) * 0.25, 0, 1.0)
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


def lap_velocity(s):
    """Velocity as a fraction of c, s seconds into the laps (5 s long)."""
    return 0.0183 + (0.9612 - 0.0183) * np.clip(s / 5.0, 0, 1)


def feed_coils():
    """Five seconds of acceleration: the electromagnetic whine climbing with the round's speed and the
    coils firing faster and faster until they blur into a roar."""
    total = 5.1
    n = ns(total)
    t = times(total)
    v = lap_velocity(t)
    f = 160 + 1500 * v ** 1.2
    m = Mix(total)
    whine = sine(f * (1 + 0.004 * np.sin(2 * np.pi * 6 * t))) + 0.35 * sine(2 * f) + 0.2 * saw(f * 0.5, n)
    m.add(stereo(whine * (0.25 + 0.75 * v)) * np.array([[1.0], [0.97]]), 0, 0.22)
    # The coils: a pulse train whose rate follows the speed.
    rate = 3 + 55 * v ** 1.1
    phase = np.cumsum(rate) / SR
    pulse = np.exp(-((phase % 1.0) * 6) ** 1.0)
    thump = lp(white(n) * 0.4 + saw(np.full(n, 120.0), n), 600) * pulse
    m.add(pan(thump, 0.25 * np.sin(2 * np.pi * phase / 2)), 0, 0.5)
    roar = decorrelated(n, pink)
    roar = np.vstack([sweep_filter(c, 'lowpass', 300 + 4500 * v ** 1.5, order=2) for c in roar]) * v ** 1.5
    m.add(roar, 0, 0.5)
    m.add(grains(n, lambda s: 2 + 60 * lap_velocity(s), spark_zap, spread=1.0), 0, 0.25)
    out = m.out() * curve(n, [(0, 0), (0.15, 1), (total - 0.05, 1), (total, 0)])
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
    """Release: an electromagnetic thunderclap, a sub drop and a long ring, then asteroids tearing past
    as the round crosses the belt."""
    total = 3.8
    m = Mix(total)
    n = ns(0.06)
    m.add(decorrelated(n, lambda k: white(k)) * attack_decay(n, 0.0003, 0.008), 0.0, 1.0)
    zap = sine(curve(ns(0.05), [(0, 9000), (0.05, 900)], 'log')) * attack_decay(ns(0.05), 0.0005, 0.02)
    m.add(zap, 0.0, 0.5)
    n = ns(2.0)
    clap = decorrelated(n, white)
    cutoff = curve(n, [(0, 7000), (0.3, 1500), (2.0, 200)], 'log')
    clap = np.vstack([sweep_filter(c, 'lowpass', cutoff, order=2) for c in clap]) * attack_decay(n, 0.002, 0.35)
    m.add(sat(clap * 2.5, 2.0), 0.0, 0.8)
    n = ns(2.4)
    sub = sine(curve(n, [(0, 85), (1.4, 24)], 'log')) * attack_decay(n, 0.01, 0.9)
    m.add(sat(sub * 1.5, 1.5), 0.0, 1.0)
    for at, side in ((1.25, -1), (1.9, 1), (2.45, -1), (2.9, 1)):
        k = ns(0.7)
        tt = np.arange(k) / SR
        d = np.sqrt(((tt - 0.25) * 200) ** 2 + 6 ** 2)
        rush = sweep_filter(pink(k), 'bandpass', curve(k, [(0, 3000), (0.25, 900), (0.7, 250)], 'log'), width=1.5)
        rush *= 1 / (0.3 + d / 12)
        m.add(pan(norm(rush), side * np.clip((tt - 0.25) * 5, -1, 1)), at, 0.35)
    _, _, _, space = spaces()
    return master(reverb(m.out(), space, wet=0.45), peak=0.97, drive=1.4, squash=0.25)


def feed_reentry():
    """Re-entry: a plasma roar and crackle building to a scream, then the whiteout."""
    total = 1.5
    n = ns(total)
    t = times(total)
    grow = curve(n, [(0, 0.15), (1.2, 1.0), (1.22, 1.0), (1.5, 0.0)])
    roar = decorrelated(n, lambda k: brown(k) * 0.8 + pink(k) * 0.4)
    roar = np.vstack([sweep_filter(c, 'lowpass', curve(n, [(0, 350), (1.2, 3200)], 'log'), order=2) for c in roar])
    m = Mix(total)
    m.add(roar * grow, 0, 0.9)
    shriek = resonator(white(n), curve(n, [(0, 1100), (1.2, 2600)], 'log'), q=40) * grow ** 2
    m.add(stereo(norm(shriek)) * np.array([[1.0], [0.85]]), 0, 0.25)
    m.add(grains(n, lambda s: 20 + 400 * (s / 1.2) ** 2, crackle_pop, end=1.22), 0, 0.6)
    k = ns(0.3)
    burst = decorrelated(k, white) * attack_decay(k, 0.002, 0.08)
    m.add(burst, 1.2, 0.6)
    return master(m.out(), peak=0.9, drive=1.6, squash=0.35)


def inbound(stereo_out=True):
    """The round screams down from the upper right: a tearing shriek dropping in pitch, a roar of
    torn air growing as it nears, crackle all round it."""
    total = 1.3
    n = ns(total)
    t = times(total)
    near = curve(n, [(0, 0.1), (0.8, 0.45), (1.15, 1.0), (1.2, 1.0), (1.3, 0.0)]) ** 1.3
    m = Mix(total)
    shriek = resonator(white(n), curve(n, [(0, 2400), (1.2, 900)], 'log') * (1 + 0.02 * np.sin(2 * np.pi * 13 * t)), q=25)
    shriek2 = resonator(white(n), curve(n, [(0, 3500), (1.2, 1400)], 'log'), q=18)
    scream = norm(shriek) + 0.6 * norm(shriek2)
    where = curve(n, [(0, 0.75), (1.2, -0.1)])
    m.add(pan(scream * near, where), 0, 0.6)
    roar = pink(n) * 0.6 + brown(n) * 0.6
    roar = sweep_filter(roar, 'lowpass', curve(n, [(0, 600), (1.2, 4000)], 'log'), order=2)
    m.add(pan(norm(roar) * near, where * 0.7), 0, 0.8)
    m.add(grains(n, lambda s: 30 + 300 * s, crackle_pop, end=1.2, spread=0.8), 0, 0.18)
    x = master(m.out(), peak=0.92, drive=1.5, squash=0.5)
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
    x = reverb(m.out(), outdoor, wet=0.5 if close else 0.8)
    x = master(x[:, :n], peak=0.98, drive=2.2, squash=0.25)
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
    x = master(reverb(m.out(), outdoor, wet=0.35)[:, :n], peak=0.95, drive=1.8, squash=0.35)
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


# Longest a sound may run (seconds): the feed's sounds must die away before the feed hands back to
# the world at INBOUND.
CAPS = {'uplink_lock': 2.5, 'camera_rise': 1.7, 'feed_zoom': 3.0, 'feed_ambience': 13.4, 'feed_relay': 3.4,
        'feed_wake': 4.2, 'feed_load': 2.4, 'feed_coils': 5.4, 'feed_release': 2.6, 'feed_reentry': 1.5}

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
    'feed_reentry': (feed_reentry, True),
    'strike_inbound': (lambda: inbound(False), False),
    'strike_inbound_near': (lambda: inbound(True), True),
    'strike_impact': (lambda: impact(False), False),
    'strike_impact_near': (lambda: impact(True), True),
    'strike_rumble': (lambda: rumble(False), False),
    'strike_rumble_near': (lambda: rumble(True), True),
    'strike_aftermath': (lambda: aftermath(False), False),
    'strike_aftermath_near': (lambda: aftermath(True), True),
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


def main():
    args = sys.argv[1:]
    wav_dir = None
    if '--wav' in args:
        i = args.index('--wav')
        wav_dir = args[i + 1]
        del args[i:i + 2]
    names = args or list(SOUNDS)
    for name in names:
        recipe, is_stereo = SOUNDS[name]
        x = recipe()
        if not is_stereo:
            x = mono(x)
        cap = CAPS.get(name)
        if cap and x.shape[-1] > ns(cap):
            x = fade(x[..., :ns(cap)], 0.0, min(0.6, cap * 0.25))
        write(name, x, wav_dir)


if __name__ == '__main__':
    main()
