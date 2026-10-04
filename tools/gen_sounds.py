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


# Longest a sound may run (seconds): the feed's sounds must die away before the feed hands back to
# the world at INBOUND.
CAPS = {'uplink_lock': 2.5, 'camera_rise': 1.7, 'feed_zoom': 3.0, 'feed_ambience': 18.2, 'feed_relay': 3.4,
        'feed_wake': 4.2, 'feed_load': 2.4, 'feed_coils': 4.3, 'feed_release': 1.62, 'feed_strike': 0.95,
        'feed_cruise': 5.7, 'feed_transit': 2.6, 'feed_locate': 1.55, 'feed_reentry': 1.72}

# How loud each sound is (dB, the RMS of its loudest 400 ms). The game plays them at full volume, so this is
# the mix: the feed sits well down, the release and the re-entry come up, and the impact is far the loudest
# thing in the sequence, the way a film saves its loudest moment for the climax.
LEVELS = {'uplink_lock': -16, 'uplink_denied': -18, 'camera_rise': -21, 'feed_zoom': -22, 'feed_ambience': -27,
          'feed_relay': -20, 'feed_wake': -17, 'feed_load': -19, 'feed_coils': -21, 'feed_lap': -19,
          'feed_release': -11, 'feed_strike': -15, 'feed_cruise': -18, 'feed_transit': -22, 'feed_locate': -18,
          'feed_reentry': -13, 'strike_inbound': -14,
          'strike_inbound_near': -13, 'strike_impact': -7, 'strike_impact_near': -5, 'strike_rumble': -12,
          'strike_rumble_near': -11, 'strike_aftermath': -23, 'strike_aftermath_near': -20}

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
        if name in LEVELS:
            x = limit(x * 10 ** ((LEVELS[name] - loudness(x)) / 20))
        write(name, x, wav_dir)


if __name__ == '__main__':
    main()
