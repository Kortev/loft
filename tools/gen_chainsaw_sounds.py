"""Synthesises Chainsaw Man's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All are mono: the game places them on whoever makes them, and they are heard by everyone round about.

- the cord: the starter rope ripped out of his chest in one long pull, the ratchet inside rattling under it, a wet
  tearing at the start where it comes out of him, and the rope snapping back in;
- the start: the engine catching off the pull, a few coughing strokes, then firing and roaring up to a scream as the
  saws burst out (a wet tearing burst of flesh and wire in the middle of it), and dropping back to its idle;
- the idle: what he sounds like while the form is on, a loop: a two-stroke engine putting along unevenly, the chains
  ticking round slack on their bars;
- the stop: the engine coughing, stumbling and dying as he turns back.

A two-stroke engine is built the way it sounds: one sharp pop of exhaust at every firing, rung through the muffler and
the body, as fast as the engine turns, with no two pops quite alike; and over it the buzz of the metal and the chain.

Usage: py tools/gen_chainsaw_sounds.py [--wav DIR] [name ...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from gen_sounds import SR, bp, curve, hp, limit, loudness, lp, mono, ns, sat, white  # noqa: E402

g.OUT = os.path.join(os.path.dirname(__file__), '..', 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman',
                     'sounds')

# The transformation's beats (FormTimeline, ticks of 1/20 s, from the press): the yank (4 to 12), the cord snapping back
# (13), the engine catching (14) and coughing (17), the head bursting (20), the arms (23), the roar (36).
YANK_FROM = 4 / 20.0
YANK_TO = 12 / 20.0
SNAP_AT = 13 / 20.0
CATCH_AT = 14 / 20.0
COUGH_AT = 17 / 20.0
BURST_AT = 20 / 20.0
ARMS_AT = 23 / 20.0
ROAR_AT = 36 / 20.0
LOOP_IDLE = 2.0


def rng_for(name):
    return np.random.default_rng(zlib.crc32(name.encode()))


def place(buf, at, x):
    """Adds the mono x into buf at {at} seconds."""
    i = ns(at)
    k = min(len(x), len(buf) - i)
    if k > 0:
        buf[i:i + k] += x[:k]
    return buf


def engine(n, rate, level=None, jitter=0.18, skip=0.0, body=(90.0, 1400.0), buzz=0.35):
    """A two-stroke engine: a pop of exhaust at every firing, {rate} firings a second (a curve over the n samples), each
    a little early or late and some missed ({skip}: the chance), rung through the muffler and the body; and the buzz of
    metal over it, shaped by the same firings."""
    rate = np.broadcast_to(rate, (n,))
    level = np.ones(n) if level is None else np.broadcast_to(level, (n,))
    phase = np.cumsum(rate / SR)
    fire = np.nonzero(np.diff(np.floor(phase)) > 0)[0] + 1
    pulses = np.zeros(n)
    for i in fire:
        if g.rng.random() < skip:
            continue
        j = i + int(g.rng.normal(0.0, jitter / max(rate[i], 1.0) * SR * 0.25))
        if 0 <= j < n:
            pulses[j] += level[i] * g.rng.uniform(0.7, 1.15)
    # One pop: a sharp crack of noise and a thump under it, gone in a few milliseconds.
    k = np.arange(ns(0.03))
    pop = white(len(k)) * np.exp(-k / ns(0.004)) + np.sin(2 * np.pi * 95.0 * k / SR) * np.exp(-k / ns(0.009)) * 1.4
    x = np.convolve(pulses, pop)[:n]
    # The muffler and the body it rings through.
    x = bp(x, *body) * 2.0 + bp(x, 300.0, 700.0, order=2) * 1.2
    # The metal's buzz: noise high up, opened and shut by each firing.
    env = lp(np.abs(x), 60.0)
    metal = hp(white(n), 2500.0) * env * buzz * 6.0
    return sat(x * 1.6, 1.4) + metal


def flesh(n, length, wet=1.0):
    """A wet tearing: flesh and wire giving, a rip of filtered noise full of little wet clicks."""
    m = ns(length)
    t = np.arange(m) / SR
    env = np.minimum(t / 0.015, 1.0) * np.exp(-t / (length * 0.35))
    rip = bp(white(m), 500.0, 3200.0) * env
    clicks = (g.rng.random(m) < 900.0 / SR) * g.rng.uniform(-1, 1, m)
    clicks = bp(np.convolve(clicks, np.exp(-np.arange(ns(0.004)) / ns(0.0008)))[:m], 800.0, 5000.0) * env * 3.0 * wet
    squelch = lp(white(m), 380.0) * env * 2.5 * wet
    out = np.zeros(n)
    out[:m] = rip + clicks + squelch
    return out


def ratchet(n, start, length, rate0, rate1):
    """The starter's pawls rattling over their ratchet as the rope runs out, faster and then slower."""
    m = ns(length)
    rate = np.linspace(rate0, rate1, m)
    phase = np.cumsum(rate / SR)
    ticks = np.zeros(m)
    ticks[np.nonzero(np.diff(np.floor(phase)) > 0)[0] + 1] = 1.0
    click = white(ns(0.006)) * np.exp(-np.arange(ns(0.006)) / ns(0.0012))
    x = bp(np.convolve(ticks, click)[:m], 1800.0, 7000.0) * 1.4
    out = np.zeros(n)
    out[ns(start):ns(start) + m] = x[:n - ns(start)]
    return out


def form_cord():
    n = ns(1.2)
    x = np.zeros(n)
    # The rope: a rough zip of fibre over metal, rising as it is yanked out and stopping hard.
    pull = curve(n, [(0.0, 0.0), (YANK_FROM, 0.0), (YANK_FROM + 0.03, 1.0), (YANK_TO - 0.04, 0.85), (YANK_TO, 0.0), (1.2, 0.0)])
    zip_ = g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 900.0), (YANK_FROM, 900.0), (YANK_TO, 2600.0), (1.2, 2600.0)]),
                          order=2, width=0.8)
    x += zip_ * pull * 0.9
    x += ratchet(n, YANK_FROM + 0.02, YANK_TO - YANK_FROM - 0.02, 60.0, 140.0)
    # Out of his chest: wet as it starts.
    x += np.roll(flesh(n, 0.22, wet=1.2), ns(YANK_FROM)) * 0.8
    # The rope snapping back in, and the handle knocking against him.
    back = curve(n, [(0.0, 0.0), (SNAP_AT - 0.02, 0.0), (SNAP_AT, 0.7), (SNAP_AT + 0.2, 0.0), (1.2, 0.0)])
    x += bp(white(n), 1200.0, 4000.0) * back * 0.5
    knock = np.zeros(n)
    knock[ns(SNAP_AT + 0.22)] = 1.0
    x += lp(np.convolve(knock, np.sin(2 * np.pi * 180 * np.arange(ns(0.05)) / SR) * np.exp(-np.arange(ns(0.05)) / ns(0.01)))[:n],
            1200.0) * 0.6
    return x * np.minimum(1.0, (n - np.arange(n)) / ns(0.05))


def form_start():
    n = ns(4.4)
    # How fast the engine fires: it catches, misses, coughs and catches again, screams up as the saws come out, howls
    # louder still in the roar, and drops back to its idle.
    rate = curve(n, [(0.0, 0.0), (CATCH_AT - 0.01, 0.0), (CATCH_AT, 10.0), (CATCH_AT + 0.08, 2.0), (COUGH_AT, 12.0),
                     (BURST_AT - 0.05, 40.0), (BURST_AT, 140.0), (ARMS_AT + 0.2, 165.0), (ROAR_AT - 0.1, 150.0),
                     (ROAR_AT, 215.0), (ROAR_AT + 0.7, 225.0), (ROAR_AT + 1.3, 110.0), (ROAR_AT + 1.8, 50.0), (4.4, 46.0)])
    level = curve(n, [(0.0, 0.0), (CATCH_AT, 0.9), (CATCH_AT + 0.08, 0.3), (COUGH_AT, 0.9), (BURST_AT, 1.15),
                      (ROAR_AT - 0.1, 1.05), (ROAR_AT, 1.45), (ROAR_AT + 0.8, 1.4), (ROAR_AT + 1.6, 0.7), (4.4, 0.62)])
    x = engine(n, rate, level)
    # The chains whipping round as it screams.
    chain = hp(white(n), 3500.0) * curve(n, [(0.0, 0.0), (BURST_AT - 0.05, 0.0), (BURST_AT, 1.0), (ROAR_AT, 1.0),
                                             (ROAR_AT + 0.8, 1.2), (ROAR_AT + 1.6, 0.35), (4.4, 0.3)])
    x += chain * 0.35
    # The saws tearing out of him: the head, then the arms.
    x += np.roll(flesh(n, 0.6, wet=1.4), ns(BURST_AT - 0.03)) * 1.3
    x += np.roll(flesh(n, 0.45, wet=1.2), ns(ARMS_AT - 0.03)) * 1.0
    return x


def form_idle():
    n = ns(LOOP_IDLE + 0.5)
    rate = 46.0 + 4.0 * np.sin(2 * np.pi * 0.7 * np.arange(n) / SR) + lp(white(n), 3.0) * 30.0
    x = engine(n, rate, skip=0.04, buzz=0.25)
    x += hp(white(n), 4000.0) * 0.06
    m = ns(LOOP_IDLE)
    k = ns(0.5)
    head = x[:m].copy()
    w = np.linspace(0.0, 1.0, k)
    head[:k] = head[:k] * np.sin(w * np.pi / 2) + x[m:m + k] * np.cos(w * np.pi / 2)
    return head


def form_stop():
    n = ns(1.6)
    rate = curve(n, [(0.0, 46.0), (0.4, 30.0), (0.8, 14.0), (1.1, 6.0), (1.2, 0.0), (1.6, 0.0)])
    level = curve(n, [(0.0, 0.8), (0.9, 0.7), (1.2, 0.0), (1.6, 0.0)])
    x = engine(n, rate, level, skip=0.2)
    # Last shudder: the metal settling.
    x += hp(white(n), 3000.0) * curve(n, [(0.0, 0.0), (1.1, 0.0), (1.15, 0.3), (1.6, 0.0)])
    return x


SOUNDS = {
    'form_cord': form_cord,
    'form_start': form_start,
    'form_idle': form_idle,
    'form_stop': form_stop,
}
LOOPS = ('form_idle',)
# Loudness (loudest 400 ms, dB).
LEVELS = {'form_cord': -14, 'form_start': -10, 'form_idle': -20, 'form_stop': -16}


def make(name):
    g.rng = rng_for(name)
    x = mono(SOUNDS[name]())
    x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
    if name in LOOPS:
        return np.clip(x, -0.95, 0.95)
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
