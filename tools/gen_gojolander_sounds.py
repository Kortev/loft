"""Synthesises Unlimited Void's sounds for Gojolander, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

Those heard from inside the Domain (and the caster's own) are stereo, played as they are heard; the ".world" ones,
for everyone outside it, are mono so the game can place them where it is. The void and the flood are loops whose ends
are crossfaded into their starts, so they go round without a seam for as long as the Domain holds.

Each is timed to the Domain's timeline (DomainTimeline, in ticks of 1/20 s):
- the sign (from tick 0): a held breath drawn in, the soft tick of two fingers crossing, and a low swell rising
  under a glassy shimmer as the ring of ink hisses out over the ground;
- the close (from tick 18, RISE - 4): the air drawn in from every side, faster and faster, until at tick 30 (CLOSE,
  0.6 s in) the barrier shuts: a deep, soft blow and a glassy ring, and at tick 40 (VOID, 1.1 s in) all of it is
  drawn away into silence;
- the void: an endless shimmering hum as wide as the sky, a low chord beating slowly in it, high air, and now and
  then a far, clear glint of light;
- the flood: what the caught hear, everything there is at once: a roaring rush, chattering data, voices that are not
  voices, a ringing in the ears;
- the collapse: the barrier cracking all over, then shattering, and the world's air rushing back in;
- the denied: a soft dead click.

Usage: python tools/gen_gojolander_sounds.py [--wav DIR] [name ...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from gen_sounds import (SR, attack_decay, bp, chime, crush, curve, decorrelated, hp, limit, loudness, lp, master,  # noqa: E402
                        mono, ns, pan, pink, reverb, sat, sine, spaces, stereo, sweep_filter, white)

g.OUT = os.path.join(os.path.dirname(__file__), '..', 'gojolander', 'src', 'main', 'resources', 'assets', 'gojolander',
                     'sounds')

# The Domain's beats, in seconds from the start of each sound (DomainTimeline: SIGN 0, RISE 22, CLOSE 30, VOID 40).
CLOSE_AT = (30 - 18) / 20.0
VOID_AT = (40 - 18) / 20.0
LOOP_VOID = 8.0
LOOP_FLOOD = 6.0


def rng_for(name):
    return np.random.default_rng(zlib.crc32(name.encode()))


def place(buf, at, x):
    """Adds x (mono or stereo) into the stereo buffer buf at {at} seconds."""
    x = stereo(x)
    i = ns(at)
    k = min(x.shape[1], buf.shape[1] - i)
    if k > 0:
        buf[:, i:i + k] += x[:, :k]
    return buf


def looped(x, loop, overlap):
    """The first {loop} seconds of x with what comes after them crossfaded over their start: a seamless loop."""
    n = ns(loop)
    m = ns(overlap)
    x = stereo(x)
    head = x[:, :n].copy()
    tail = x[:, n:n + m]
    k = np.linspace(0.0, 1.0, m)
    head[:, :m] = head[:, :m] * np.sin(k * np.pi / 2) + tail * np.cos(k * np.pi / 2)
    return head


def inhale(n, start, length):
    """A breath drawn in through the teeth: band-passed air rising in pitch and loudness, cut off at its top."""
    t = np.arange(n) / SR
    env = np.clip((t - start) / length, 0, 1) ** 1.6 * (t < start + length)
    freq = curve(n, [(start, 900.0), (start + length, 2600.0)])
    air = sweep_filter(white(n), 'bandpass', freq, order=2, width=1.2)
    return air * env


def swell(n, f0, f1, start, length, hold=0.4):
    """A low tone rising out of nothing: a soft sine and its octave, gliding up a little, with a slow beat."""
    freq = curve(n, [(0.0, f0), (start, f0), (start + length, f1), (start + length + hold, f1)])
    env = curve(n, [(0.0, 0.0), (start, 0.0), (start + length, 1.0), (start + length + hold, 0.9)]) ** 2
    beat = 1.0 + 0.15 * np.sin(2 * np.pi * 3.1 * np.arange(n) / SR)
    return (sine(freq) + 0.35 * sine(freq * 2.0) + 0.12 * sine(freq * 3.01)) * env * beat


def shimmer(n, start, length, base=1760.0, count=9):
    """Glass faintly singing: a cluster of high crystal partials coming in one by one, drifting very slightly."""
    out = np.zeros((2, n))
    notes = base * 2 ** (np.array([0, 3, 7, 10, 12, 15, 19, 22, 24]) / 12.0)
    for k in range(count):
        at = start + length * k / count
        m = n - ns(at)
        if m <= 0:
            continue
        f = notes[k % len(notes)] * (1.0 + g.rng.normal(0, 0.002))
        y = chime(f, m, tau=1.2, bright=0.35) * attack_decay(m, 0.15, 1.6)
        out[:, ns(at):] += pan(y, g.rng.uniform(-0.8, 0.8)) * 0.18
    return out


def finger_tick(n):
    """Two fingers crossing, close to the ear: skin on skin, a tiny dry tick."""
    t = np.arange(n) / SR
    return hp(white(n), 2500) * np.exp(-t / 0.004) * 0.6 + bp(white(n), 600, 1800) * np.exp(-t / 0.01) * 0.4


def ring_hiss(n, start, length):
    """The ring of ink spreading over the ground: a dry, rushing hiss moving outward and fading."""
    env = attack_decay(n, 0.12, length * 0.6, start)
    return sweep_filter(pink(n), 'bandpass', curve(n, [(start, 3000.0), (start + length, 1200.0)]), order=2, width=1.5) * env


def domain_sign():
    n = ns(2.2)
    out = np.zeros((2, n))
    place(out, 0.0, pan(inhale(ns(0.6), 0.0, 0.42), 0.0) * 0.5)
    place(out, 0.22, pan(finger_tick(ns(0.05)), 0.25) * 0.9)
    out += pan(swell(n, 32.0, 46.0, 0.25, 0.85), 0.0) * 0.9
    out += shimmer(n, 0.3, 0.8)
    out += decorrelated(n, lambda m: ring_hiss(m, 0.3, 1.0), 0.8) * 0.25
    _, _, _, space = spaces()
    return master(reverb(out, space, wet=0.35), peak=0.95)


def domain_sign_world():
    n = ns(2.4)
    y = swell(n, 34.0, 48.0, 0.2, 0.9) * 0.8 + ring_hiss(n, 0.25, 1.1) * 0.7 + mono(shimmer(n, 0.3, 0.8)) * 0.6
    outdoor, _, _, _ = spaces()
    return master(reverb(y, outdoor, wet=0.3), peak=0.95)


def suck(n, until):
    """Air drawn in from every side, faster and faster, stopping dead at {until} seconds."""
    t = np.arange(n) / SR
    k = np.clip(t / until, 0, 1)
    env = k ** 3 * (t < until)
    freq = curve(n, [(0.0, 300.0), (until, 6500.0)], 'log')
    return sweep_filter(white(n), 'lowpass', freq, order=2) * env


def shut(n, at, depth=1.0):
    """The barrier shutting: a deep soft blow (felt more than heard) and the glassy ring of its closing."""
    t = np.arange(n) / SR - at
    on = t >= 0
    tt = np.maximum(t, 0)
    sub = np.sin(2 * np.pi * (28.0 * tt + 16.0 * (1 - np.exp(-tt / 0.08)))) * np.exp(-tt / 0.5) * on
    body = lp(white(n), 260) * np.exp(-tt / 0.12) * on
    glass = np.zeros(n)
    for f in (523.0, 1046.0 * 1.003, 1568.0, 2349.0):
        glass += chime(f, n, tau=1.6, bright=0.4) * on
    return sub * 1.2 * depth + body * 0.6 * depth + glass * 0.12


def domain_close():
    n = ns(3.4)
    out = decorrelated(n, lambda m: suck(m, CLOSE_AT), 0.9) * 0.55
    out += pan(shut(n, CLOSE_AT), 0.0)
    _, hall, _, space = spaces()
    out = reverb(out, space, wet=0.45)
    # At the void, everything is drawn away into silence: the tail is pulled down hard, leaving only a breath of it.
    k = curve(out.shape[1], [(0.0, 1.0), (VOID_AT - 0.08, 1.0), (VOID_AT + 0.1, 0.08), (3.0, 0.0)])
    return master(out * k, peak=0.95)


def domain_close_world():
    n = ns(4.0)
    y = suck(n, CLOSE_AT) * 0.4 + shut(n, CLOSE_AT, 1.3)
    outdoor, _, _, _ = spaces()
    return master(reverb(y, outdoor, wet=0.35), peak=0.95)


def domain_void():
    """The void: made longer than its loop, and its end crossfaded over its start."""
    total = LOOP_VOID + 2.0
    n = ns(total)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    # A low chord, each note a pair of slightly mistuned sines beating slowly, spread wide.
    for f, gain, where in ((55.0, 0.5, 0.0), (82.4, 0.32, -0.4), (110.0, 0.28, 0.4), (138.6, 0.16, -0.7), (164.8, 0.12, 0.7)):
        pair = sine(f, n) + sine(f * 1.0021, n, phase=1.3)
        tremble = 1.0 + 0.12 * np.sin(2 * np.pi * (0.13 + 0.05 * where) * t)
        out += pan(pair * gain * tremble, where)
    # High air, as wide as the sky, breathing slowly.
    air = decorrelated(n, lambda m: hp(pink(m), 5000), 1.0) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.11 * t))
    out += air * 0.05
    # A ghostly choir: formant-filtered noise on a few long notes.
    for f, where in ((440.0, -0.5), (659.3, 0.5), (554.4, 0.0)):
        voice = bp(white(n), f * 0.97, f * 1.03, order=2) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.07 * t + where * 3))
        out += pan(voice, where) * 0.25
    # Far glints of light: now and then a clear, high bell, far off.
    for _ in range(9):
        at = g.rng.uniform(0.0, total - 1.0)
        m = n - ns(at)
        f = 2093.0 * 2 ** (g.rng.choice([0, 2, 4, 7, 9, 12, 14]) / 12.0)
        out[:, ns(at):] += pan(chime(f, m, tau=2.4, bright=0.3), g.rng.uniform(-0.9, 0.9)) * 0.06
    _, _, _, space = spaces()
    out = reverb(out, space, wet=0.5)[:, :n]
    out = looped(out, LOOP_VOID, 1.5)
    out = out - out.mean(axis=1, keepdims=True)
    return out


def chatter(n):
    """Data pouring past: crushed bursts and beeps, on and off with hard edges, never two the same."""
    y = np.zeros(n)
    i = 0
    while i < n:
        m = ns(g.rng.uniform(0.004, 0.05))
        if g.rng.random() < 0.55:
            seg = crush(white(m) * 0.5, g.rng.uniform(1.5, 4.0), g.rng.uniform(2.0, 30.0))
        else:
            seg = np.sign(np.sin(2 * np.pi * g.rng.uniform(400, 7000) * np.arange(m) / SR)) * 0.4
        k = min(m, n - i)
        y[i:i + k] += seg[:k] * (g.rng.random() < 0.7)
        i += m + ns(g.rng.uniform(0.0, 0.03))
    return y


def babble(n):
    """Voices that are not voices: noise through gliding vowel formants, many at once."""
    y = np.zeros(n)
    for _ in range(5):
        f1 = curve(n, [(s, g.rng.uniform(300, 900)) for s in np.arange(0.0, n / SR + 0.3, 0.25)])
        y += sweep_filter(white(n), 'bandpass', f1, order=2, width=0.4)
    return y


def domain_flood():
    total = LOOP_FLOOD + 1.5
    n = ns(total)
    t = np.arange(n) / SR
    out = decorrelated(n, lambda m: lp(pink(m), 2400), 0.9) * 0.6  # the roar
    out += decorrelated(n, lambda m: sweep_filter(white(m), 'bandpass', 2000 + 1500 * np.sin(2 * np.pi * 0.7 * np.arange(m) / SR),
                                                  order=2, width=1.0), 0.8) * 0.25
    out += decorrelated(n, chatter, 1.0) * 0.22
    out += decorrelated(n, babble, 1.0) * 0.3
    whine = sine(8800.0 + 120.0 * np.sin(2 * np.pi * 0.3 * t), n) * 0.035
    out += pan(whine, 0.0)
    # The pulse in the ears.
    beat = np.zeros(n)
    for at in np.arange(0.0, total, 0.62):
        beat += lp(white(n), 90) * attack_decay(n, 0.01, 0.07, at) + lp(white(n), 90) * attack_decay(n, 0.01, 0.06, at + 0.18) * 0.6
    out += pan(beat, 0.0) * 0.9
    out = sat(out * 1.4, 1.6)
    out = looped(out, LOOP_FLOOD, 1.0)
    return out - out.mean(axis=1, keepdims=True)


def cracks(n, start, length, rate):
    """Glass cracking all over: sharp ticks and splits, closer and closer together."""
    out = np.zeros((2, n))
    tt = start
    while tt < start + length:
        tt += g.rng.exponential(1.0 / (rate * (1.0 + 3.0 * (tt - start) / length)))
        i = ns(tt)
        if i >= n:
            break
        m = ns(0.03)
        k = min(m, n - i)
        x = np.arange(m) / SR
        tick = hp(white(m), 3000) * np.exp(-x / g.rng.uniform(0.001, 0.006)) + chime(g.rng.uniform(2500, 6000), m, tau=0.03)
        out[:, i:i + k] += pan(tick[:k], g.rng.uniform(-1, 1)) * g.rng.uniform(0.2, 0.7)
    return out


def shards(n, at, count=90):
    """The barrier shattering: a burst of glass, then its pieces falling and tinkling away all round."""
    out = np.zeros((2, n))
    burst = hp(white(n), 1800) * attack_decay(n, 0.002, 0.18, at)
    out += decorrelated(n, lambda m: hp(white(m), 1800) * attack_decay(m, 0.002, 0.18, at), 1.0) * 0.6
    for _ in range(count):
        tt = at + g.rng.exponential(0.35)
        i = ns(tt)
        if i >= n:
            continue
        m = n - i
        y = chime(g.rng.uniform(1800, 7500), m, tau=g.rng.uniform(0.05, 0.3), bright=0.5)
        out[:, i:] += pan(y, g.rng.uniform(-1, 1)) * g.rng.uniform(0.04, 0.2)
    return out + stereo(burst) * 0.2


def rush_back(n, at):
    """The world's air rushing back in: a broad whoosh outward, settling into a soft wind."""
    env = attack_decay(n, 0.08, 0.9, at)
    return sweep_filter(pink(n), 'lowpass', curve(n, [(at, 7000.0), (at + 1.5, 900.0)], 'log'), order=2) * env


def domain_collapse():
    n = ns(3.6)
    out = cracks(n, 0.0, 0.3, 40.0)
    out += shards(n, 0.32)
    out += decorrelated(n, lambda m: rush_back(m, 0.34), 0.9) * 0.5
    out += pan(shut(n, 0.32, 0.6), 0.0) * 0.6
    _, hall, _, _ = spaces()
    return master(reverb(out, hall, wet=0.25), peak=0.95)


def domain_collapse_world():
    n = ns(4.0)
    y = mono(cracks(n, 0.0, 0.3, 30.0)) + mono(shards(n, 0.32, 60)) + rush_back(n, 0.34) * 0.4 + shut(n, 0.32, 1.0) * 0.8
    outdoor, _, _, _ = spaces()
    return master(reverb(y, outdoor, wet=0.3), peak=0.95)


def domain_denied():
    n = ns(0.35)
    t = np.arange(n) / SR
    thunk = np.sin(2 * np.pi * 180.0 * t) * np.exp(-t / 0.03) + lp(white(n), 900) * np.exp(-t / 0.015) * 0.5
    tick = chime(1320.0, n, tau=0.08, bright=0.3) * 0.15
    return master(thunk + tick, peak=0.9)


# Each: its recipe and whether it is stereo (heard as it is) or mono (placed in the world).
SOUNDS = {
    'domain_sign': (domain_sign, True),
    'domain_sign_world': (domain_sign_world, False),
    'domain_close': (domain_close, True),
    'domain_close_world': (domain_close_world, False),
    'domain_void': (domain_void, True),
    'domain_flood': (domain_flood, True),
    'domain_collapse': (domain_collapse, True),
    'domain_collapse_world': (domain_collapse_world, False),
    'domain_denied': (domain_denied, False),
}
LOOPS = ('domain_void', 'domain_flood')
# Loudness (loudest 400 ms, dB).
# How long each one-shot runs at most (seconds): the reverb's long tail is cut short and faded.
LENGTHS = {'domain_sign': 2.6, 'domain_sign_world': 3.0, 'domain_close': 2.9, 'domain_close_world': 4.2, 'domain_collapse': 3.6,
           'domain_collapse_world': 4.6, 'domain_denied': 0.35}
LEVELS = {'domain_sign': -16, 'domain_sign_world': -18, 'domain_close': -12, 'domain_close_world': -13, 'domain_void': -21,
          'domain_flood': -17, 'domain_collapse': -12, 'domain_collapse_world': -13, 'domain_denied': -20}


def make(name):
    g.rng = rng_for(name)
    recipe, is_stereo = SOUNDS[name]
    x = recipe()
    if not is_stereo:
        x = mono(x)
    x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
    if name in LOOPS:
        return np.clip(x, -0.95, 0.95)
    n = min(x.shape[-1], ns(LENGTHS[name]))
    x = x[..., :n] * np.minimum(1.0, (n - np.arange(n)) / ns(0.4))
    return limit(x)


def main():
    args = sys.argv[1:]
    wav_dir = None
    if '--wav' in args:
        i = args.index('--wav')
        wav_dir = args[i + 1]
        del args[i:i + 2]
    for name in args or list(SOUNDS):
        g.write(name, make(name), wav_dir)


if __name__ == '__main__':
    main()
