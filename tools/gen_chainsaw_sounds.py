"""Synthesises Chainsaw Man's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All are mono: the game places them on whoever makes them, and they are heard by everyone round about.

- the cord: the starter rope ripped out of his chest in one long pull, the ratchet inside rattling under it, a wet
  tearing at the start where it comes out of him, and the rope snapping back in;
- the start: the engine catching off the pull, a few coughing strokes, then firing and roaring up to a scream as the
  saws burst out (a wet tearing burst of flesh and wire in the middle of it), and dropping back to its idle;
- the idle: what he sounds like while the form is on, a loop: a two-stroke engine putting along unevenly, the chains
  ticking round slack on their bars;
- the stop: the engine coughing, stumbling and dying as he turns back;
- the fight: a slash (the engine snarling up as a saw whips through the air), a hit (the saw biting into flesh, the
  engine bogging under it), the grind (a loop: the engine screaming under load, the chain chewing), the chain fired
  out of the arm (links rattling out fast, the hook biting), the frenzy kicking in (the engine shrieking up into a
  roar) and its loop (flat out, screaming), going down (a wet collapse as the engine splutters out), and coming back
  up (a gulp of blood, the heart and the engine catching).

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


def loop(x, length, overlap=0.4):
    """{x} (longer than {length} s by {overlap} s) cut to {length} s, its tail crossfaded into its head so it loops."""
    m = ns(length)
    k = ns(overlap)
    head = x[:m].copy()
    w = np.linspace(0.0, 1.0, k)
    head[:k] = head[:k] * np.sin(w * np.pi / 2) + x[m:m + k] * np.cos(w * np.pi / 2)
    return head


def whoosh(n, at, length, lo=500.0, hi=2600.0):
    """Air torn by something fast: a swell of noise sweeping up through a band."""
    m = ns(length)
    t = np.linspace(0.0, 1.0, m)
    swept = g.sweep_filter(white(m), 'bandpass', lo + (hi - lo) * t, order=2, width=0.9)
    env = np.sin(np.pi * np.minimum(t * 1.4, 1.0)) ** 1.5
    out = np.zeros(n)
    out[ns(at):ns(at) + m] = (swept * env)[:n - ns(at)]
    return out


def form_slash():
    n = ns(0.55)
    rate = curve(n, [(0.0, 60.0), (0.06, 190.0), (0.22, 210.0), (0.4, 120.0), (0.55, 70.0)])
    level = curve(n, [(0.0, 0.5), (0.06, 1.2), (0.3, 1.0), (0.55, 0.0)])
    x = engine(n, rate, level, buzz=0.45)
    x += hp(white(n), 3500.0) * curve(n, [(0.0, 0.0), (0.05, 0.9), (0.35, 0.5), (0.55, 0.0)]) * 0.5
    x += whoosh(n, 0.04, 0.3) * 1.3
    return x


def form_hit():
    n = ns(0.45)
    rate = curve(n, [(0.0, 200.0), (0.05, 90.0), (0.25, 130.0), (0.45, 150.0)])
    level = curve(n, [(0.0, 1.2), (0.3, 0.8), (0.45, 0.0)])
    x = engine(n, rate, level, buzz=0.6) * 0.7
    x += flesh(n, 0.35, wet=1.5) * 1.4
    # The teeth catching bone: a short crunch.
    x += bp(white(n), 900.0, 4000.0) * curve(n, [(0.0, 0.0), (0.01, 1.0), (0.08, 0.2), (0.45, 0.0)]) * 0.8
    return x


def form_grind():
    n = ns(1.5 + 0.4)
    t = np.arange(n) / SR
    rate = 175.0 + 18.0 * np.sin(2 * np.pi * 3.1 * t) + lp(white(n), 6.0) * 120.0
    level = 1.0 + 0.2 * np.sin(2 * np.pi * 5.3 * t)
    x = engine(n, rate, level, jitter=0.1, buzz=0.7)
    # The chain chewing: a coarse grating, pulsing with the load.
    chew = bp(white(n), 700.0, 5000.0) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 7.0 * t)))
    x += chew * 0.55
    wet = lp(white(n), 500.0) * (0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t) ** 2)
    x += wet * 0.6
    return loop(x, 1.5)


def form_chain():
    n = ns(0.8)
    x = np.zeros(n)
    # The links running out: fast clinks, slowing, over a hiss of the chain through the air.
    x += ratchet(n, 0.0, 0.32, 260.0, 120.0) * 1.2
    x += whoosh(n, 0.0, 0.3, 900.0, 3800.0) * 0.8
    # The hook biting home, and the chain going taut with a jangle.
    bite = np.zeros(n)
    bite[ns(0.33)] = 1.0
    ring = g.chime(1250.0, ns(0.4), tau=0.12, bright=0.7)
    x += np.convolve(bite, ring)[:n] * 0.5
    x += bp(white(n), 1500.0, 6000.0) * curve(n, [(0.0, 0.0), (0.33, 0.0), (0.34, 0.9), (0.5, 0.0), (0.8, 0.0)]) * 0.6
    return x * np.minimum(1.0, (n - np.arange(n)) / ns(0.05))


def roar(n, at, length, pitch=95.0):
    """A roar from the throat: a growl of rough pulses through the formants of an open mouth."""
    m = ns(length)
    t = np.arange(m) / SR
    f = pitch * (1.0 + 0.08 * np.sin(2 * np.pi * 4.0 * t)) * (1.0 + lp(white(m), 8.0) * 0.6)
    phase = np.cumsum(f / SR)
    pulses = (np.diff(np.floor(phase), prepend=0.0) > 0).astype(float) * g.rng.uniform(0.6, 1.2, m)
    voice = np.convolve(pulses, np.exp(-np.arange(ns(0.006)) / ns(0.0015)))[:m]
    voice = bp(voice, 500.0, 900.0) * 1.2 + bp(voice, 1100.0, 1500.0) * 0.8 + bp(voice, 2300.0, 2900.0) * 0.5
    voice += bp(white(m), 400.0, 2500.0) * 0.35
    env = curve(m, [(0.0, 0.0), (0.08, 1.0), (length - 0.35, 0.9), (length, 0.0)])
    out = np.zeros(n)
    out[ns(at):ns(at) + m] = sat(voice * env * 2.0, 2.0)[:n - ns(at)]
    return out


def form_frenzy():
    n = ns(2.2)
    rate = curve(n, [(0.0, 60.0), (0.15, 160.0), (0.4, 240.0), (1.6, 250.0), (2.2, 200.0)])
    level = curve(n, [(0.0, 0.6), (0.2, 1.3), (1.7, 1.3), (2.2, 0.0)])
    x = engine(n, rate, level, buzz=0.6)
    x += hp(white(n), 3200.0) * curve(n, [(0.0, 0.0), (0.2, 1.0), (1.8, 1.0), (2.2, 0.0)]) * 0.4
    x += roar(n, 0.2, 1.6) * 1.1
    return x


def form_frenzy_loop():
    n = ns(2.0 + 0.4)
    t = np.arange(n) / SR
    rate = 240.0 + 12.0 * np.sin(2 * np.pi * 1.7 * t) + lp(white(n), 5.0) * 80.0
    x = engine(n, rate, jitter=0.08, buzz=0.65)
    x += hp(white(n), 3800.0) * 0.3
    return loop(x, 2.0)


def form_down():
    n = ns(1.6)
    x = np.zeros(n)
    x += flesh(n, 0.5, wet=1.8) * 1.3
    # The body hitting the ground.
    thud = np.zeros(n)
    thud[ns(0.12)] = 1.0
    k = np.arange(ns(0.15))
    x += np.convolve(thud, np.sin(2 * np.pi * 70.0 * k / SR) * np.exp(-k / ns(0.04)))[:n] * 1.6
    # The engine spluttering out.
    rate = curve(n, [(0.0, 120.0), (0.3, 40.0), (0.8, 12.0), (1.0, 0.0), (1.6, 0.0)])
    level = curve(n, [(0.0, 0.8), (0.9, 0.5), (1.0, 0.0), (1.6, 0.0)])
    x += engine(n, rate, level, skip=0.3) * 0.7
    return x


def form_revive():
    n = ns(1.3)
    x = np.zeros(n)
    # A gulp: a wet swallow, low.
    k = np.arange(ns(0.18))
    gulp = lp(white(len(k)), 350.0) * np.sin(np.pi * k / len(k)) * 2.0
    gulp += np.sin(2 * np.pi * np.linspace(220.0, 120.0, len(k)) * k / SR) * np.exp(-k / ns(0.06))
    x[ns(0.05):ns(0.05) + len(k)] += gulp
    # The heart: two beats.
    for at in (0.3, 0.52):
        beat = np.zeros(n)
        beat[ns(at)] = 1.0
        kk = np.arange(ns(0.12))
        x += np.convolve(beat, np.sin(2 * np.pi * 55.0 * kk / SR) * np.exp(-kk / ns(0.03)))[:n] * 1.8
    # And the engine catching.
    rate = curve(n, [(0.0, 0.0), (0.7, 0.0), (0.72, 14.0), (0.9, 60.0), (1.3, 48.0)])
    level = curve(n, [(0.0, 0.0), (0.7, 0.0), (0.75, 1.0), (1.3, 0.0)])
    x += engine(n, rate, level) * 0.8
    return x


# Pochita becoming your heart (MergeTimeline, ticks of 1/20 s): struck down (0, hitting the ground at 7), the grey
# quiet (8), the world going out (60), he comes (90), sniffs and whimpers at you (140), hops up (175), the talk (190
# to 505; the anime's voices are their own sounds, over this), bows (505), goes into you (520), the cord (534), your heart's six beats
# (550, 562, 572, 580, 586, 591), you get up (596), standing, waiting to pull the cord (626).
MERGE_LAND = 7 / 20.0
MERGE_DARK = 60 / 20.0
MERGE_COMING = 90 / 20.0
MERGE_SNIFF = 140 / 20.0
MERGE_CLIMB = 175 / 20.0
MERGE_TALK = 190 / 20.0
MERGE_BOW = 505 / 20.0
MERGE_SINK = 520 / 20.0
MERGE_SUNK = 534 / 20.0
MERGE_BEATS = [550 / 20.0, 562 / 20.0, 572 / 20.0, 580 / 20.0, 586 / 20.0, 591 / 20.0]
MERGE_END = 626 / 20.0


def whimper(n, at, length=0.35, pitch=900.0):
    """A small dog's whimper: a thin rising and falling whine through the nose."""
    m = ns(length)
    t = np.arange(m) / SR
    f = pitch * (1.0 + 0.25 * np.sin(np.pi * t / length)) * (1.0 + 0.02 * np.sin(2 * np.pi * 7.0 * t))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * np.sin(4 * np.pi * np.cumsum(f) / SR)
    env = np.sin(np.pi * np.minimum(t / length, 1.0)) ** 1.5
    out = np.zeros(n)
    out[ns(at):ns(at) + m] = (bp(tone, 600.0, 3000.0) * env)[:n - ns(at)]
    return out


def form_merge():
    n = ns(MERGE_END + 0.5)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # Struck down: the body hitting the ground, wet; then a ringing in the ears over the grey.
    hit = np.zeros(n)
    hit[ns(MERGE_LAND)] = 1.0
    k = np.arange(ns(0.25))
    x += np.convolve(hit, np.sin(2 * np.pi * 55.0 * k / SR) * np.exp(-k / ns(0.06)))[:n] * 2.0
    x += np.roll(flesh(n, 0.35, wet=1.7), ns(MERGE_LAND - 0.01)) * 0.8
    ring = np.sin(2 * np.pi * 3150.0 * t) * 0.06 + np.sin(2 * np.pi * 3720.0 * t) * 0.03
    x += ring * curve(n, [(0.0, 0.0), (MERGE_LAND + 0.1, 0.0), (MERGE_LAND + 0.5, 1.0), (MERGE_DARK, 0.7), (MERGE_DARK + 1.5, 0.0),
                          (MERGE_END + 0.5, 0.0)])
    # The dark: a low hum, two tones slowly beating against each other, under everything until the heart starts.
    hum = (np.sin(2 * np.pi * 55.0 * t) + 0.7 * np.sin(2 * np.pi * 55.7 * t) + 0.25 * np.sin(2 * np.pi * 110.4 * t)) * 0.12
    x += hum * curve(n, [(0.0, 0.0), (MERGE_DARK, 0.0), (MERGE_DARK + 1.5, 1.0), (MERGE_TALK, 0.8), (MERGE_BOW, 0.9),
                         (MERGE_SINK, 1.4), (MERGE_SINK + 0.05, 0.0), (MERGE_END + 0.5, 0.0)])
    # His steps coming out of the dark, a limp in them; sniffing and two whimpers; the scramble up.
    steps = int((MERGE_SNIFF - MERGE_COMING) / 0.12)
    for i in range(steps):
        at = MERGE_COMING + i * 0.12 + (0.04 if i % 2 else 0.0)
        pad = np.zeros(n)
        pad[ns(at)] = (0.35 + 0.35 * (i % 2)) * min(1.0, 0.25 + i / steps)
        x += lp(np.convolve(pad, white(ns(0.03)) * np.exp(-np.arange(ns(0.03)) / ns(0.006)))[:n], 1800.0) * 0.8
    for i in range(5):
        sniff = np.zeros(n)
        start = ns(MERGE_SNIFF + 0.1 + i * 0.13)
        burst = bp(white(ns(0.07)), 1800.0, 6000.0) * np.hanning(ns(0.07)) * 0.35
        sniff[start:start + len(burst)] = burst[:n - start]
        x += sniff
    x += whimper(n, MERGE_SNIFF + 0.85) * 0.5
    x += whimper(n, MERGE_SNIFF + 1.3, 0.45, 820.0) * 0.45
    x += bp(white(n), 600.0, 2500.0) * curve(n, [(0.0, 0.0), (MERGE_CLIMB, 0.0), (MERGE_CLIMB + 0.05, 0.4), (MERGE_CLIMB + 0.35, 0.0),
                                                (MERGE_END + 0.5, 0.0)])
    # As he bows: the hum swelling. The sink: a deep boom, a wet tearing, and the cord ripping out of you.
    boom = np.zeros(n)
    boom[ns(MERGE_SINK)] = 1.0
    kb = np.arange(ns(1.4))
    x += np.convolve(boom, np.sin(2 * np.pi * np.linspace(70.0, 28.0, len(kb)) * kb / SR) * np.exp(-kb / ns(0.4)))[:n] * 2.4
    x += np.roll(flesh(n, 0.9, wet=2.0), ns(MERGE_SINK)) * 1.4
    x += ratchet(n, MERGE_SUNK, 0.25, 140.0, 60.0) * 0.6
    # The heart starting: six beats, closer and harder, the last with the engine's first cough in it.
    for i, at in enumerate(MERGE_BEATS):
        for dub, gain in ((0.0, 1.0), (0.15, 0.6)):
            beat = np.zeros(n)
            beat[ns(at + dub)] = 1.0
            kk = np.arange(ns(0.18))
            x += np.convolve(beat, np.sin(2 * np.pi * 50.0 * kk / SR) * np.exp(-kk / ns(0.045)))[:n] * (1.2 + 0.28 * i) * gain
    rate = curve(n, [(0.0, 0.0), (MERGE_BEATS[-1] - 0.01, 0.0), (MERGE_BEATS[-1], 14.0), (MERGE_END - 0.1, 24.0), (MERGE_END + 0.5, 0.0)])
    level = curve(n, [(0.0, 0.0), (MERGE_BEATS[-1], 0.0), (MERGE_BEATS[-1] + 0.02, 0.8), (MERGE_END, 0.5), (MERGE_END + 0.5, 0.0)])
    x += engine(n, rate, level, skip=0.3) * 0.6
    return x

def pochita_tame():
    """Pochita yours: two happy yips, up at the end, and his engine's little purr under them."""
    n = ns(1.1)
    x = np.zeros(n)
    for at, length, pitch in ((0.02, 0.12, 1050.0), (0.2, 0.16, 1250.0)):
        m = ns(length)
        t = np.arange(m) / SR
        # A yip: a quick upward bend, bright, cut off short.
        f = pitch * (0.8 + 0.4 * np.minimum(t / length * 1.6, 1.0))
        tone = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.45 * np.sin(4 * np.pi * np.cumsum(f) / SR)
        env = np.minimum(t / 0.01, 1.0) * np.exp(-t / (length * 0.45))
        yip = bp(tone + 0.15 * white(m), 500.0, 4000.0) * env
        x[ns(at):ns(at) + m] += yip[:n - ns(at)]
    rate = curve(n, [(0.0, 0.0), (0.38, 0.0), (0.4, 18.0), (0.8, 24.0), (1.1, 20.0)])
    level = curve(n, [(0.0, 0.0), (0.38, 0.0), (0.45, 0.5), (0.8, 0.35), (1.1, 0.0)])
    x += engine(n, rate, level, body=(140.0, 2200.0)) * 0.5
    return x


SOUNDS = {
    'pochita_tame': pochita_tame,
    'form_merge': form_merge,
    'form_cord': form_cord,
    'form_start': form_start,
    'form_idle': form_idle,
    'form_stop': form_stop,
    'form_slash': form_slash,
    'form_hit': form_hit,
    'form_grind': form_grind,
    'form_chain': form_chain,
    'form_frenzy': form_frenzy,
    'form_frenzy_loop': form_frenzy_loop,
    'form_down': form_down,
    'form_revive': form_revive,
}
LOOPS = ('form_idle', 'form_grind', 'form_frenzy_loop')
# Loudness (loudest 400 ms, dB).
LEVELS = {'pochita_tame': -14, 'form_merge': -12, 'form_cord': -14, 'form_start': -10, 'form_idle': -20, 'form_stop': -16, 'form_slash': -13, 'form_hit': -12,
          'form_grind': -15, 'form_chain': -14, 'form_frenzy': -10, 'form_frenzy_loop': -17, 'form_down': -13,
          'form_revive': -14}


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
