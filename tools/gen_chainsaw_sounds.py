"""Synthesises Chainsaw Man's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All are mono: the game places them on whoever makes them, and they are heard by everyone round about.

- the cord: the starter rope ripped out of his chest in one long pull, the ratchet inside rattling under it, a wet
  tearing at the start where it comes out of him, and the rope snapping back in;
- the start: the engine catching off the pull, a few coughing strokes, then firing and roaring up to a scream as the
  saws burst out (a wet tearing burst of flesh and wire in the middle of it), and dropping back to its idle;
- the revive: a friend pulling the cord of someone who is down (gripping the handle, then a sharp tug and a cough at 1 s, a
  harder tug and a sputter at 2 s, tension swelling into 3 s) and then the yank (the start's sound from tick 10 on, with the rope's
  crack in front and a wet spray: the limbs growing back);
- the idle: what he sounds like while the form is on, a loop: a two-stroke engine putting along unevenly, the chains
  ticking round slack on their bars;
- the stop: the engine coughing, stumbling and dying as he turns back;
- the fight: a slash (the engine snarling up as a saw whips through the air), a hit (the saw biting into flesh, the
  engine bogging under it), the grind (a loop: the engine screaming under load, the chain chewing), the chain fired
  out of the arm (links rattling out fast, the hook biting), the frenzy kicking in (the engine shrieking up into a
  roar) and its loop (flat out, screaming), going down (a wet collapse as the engine splutters out), and coming back
  up (a gulp of blood, the heart and the engine catching).
- Forever (the Eternity contract's power), all synthesised: the lock (wet squelches as the eyes open, a crowd of many whispering voices, detuned and
  formant-filtered with no words, over a deep clock tick that stops), a stored hit (a muffled thud with a ticking echo) and the release (teeth biting
  shut, the line rushing in, a heavy boom, time rushing back as a reversed whoosh).
- Beam the shark (the ult), a huge wild shark fiend, heavy, wet and animal. These are built on pitched, filtered and layered
  vanilla Minecraft sounds (the loom asset cache, read at generation time only: the .ogg files are our own and nothing
  reads a vanilla file at runtime), with synthesised sub-bass, impacts and debris under them: bursting up out of the ground
  (stone and deepslate cracking, an explosion pulled down low, the throw of earth, rubble raining; pitched low it is the breach
  crash), his footfall (an iron step dropped an octave, a thud, dirt and gravel), swimming through the ground (a deep rumble,
  a dragon's growl dropped and levelled, dirt ripping past; made to overlap into a smooth roar), his bite (a ravager's jaws, an
  iron fist, wet flesh, crunching bone, a thump), the dive (the earth closing over him, mud and debris trickling), his screech of joy
  when he is called up (a breath in, a ravager's roar with dolphin, hoglin, warden and wolf in it, a mad cackle, a gravel throat),
  his teeth chattering (clacks, a little wet), the ambush's slam (the loudest: explosions, a heavy fall, deepslate, a
  sub-bass drop, a long rain of stone) and the throw (a snap, a wet crack, a whip of air, a thump).

A two-stroke engine is built the way it sounds: one sharp pop of exhaust at every firing, rung through the muffler and
the body, as fast as the engine turns, with no two pops quite alike; and over it the buzz of the metal and the chain.

Usage: py tools/gen_chainsaw_sounds.py [--wav DIR] [name ...]
The Beam sounds need ffmpeg (on PATH, or its folder in FFMPEG_DIR) and the loom asset cache (~/.gradle/caches/fabric-loom/assets).
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


def form_revive_pull():
    """A friend pulling the cord of someone who is down (Downed.FEED, 3 s), as a pull-start: he grips the handle (creaks and the
    pawls ticking), then three pulls, each harder: at 1.0 s a sharp short tug (the rope zipping out and snapping back, a cough of
    the engine), at 2.0 s a harder one (a louder cough and a sputter), and a swell of tension into 3.0 s, where the yank begins
    (form_revive_start). The tugs land on ReviveCord.TUGS (ticks 20 and 40)."""
    length = 3.0
    n = ns(length)
    t = np.arange(n) / SR
    x = np.zeros(n)

    def tug(at, hard):
        # The rope zipping out over the 3 ticks before the top, snapping back over the 2 after: a zip, a crack, a wet start.
        z = g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 900.0), (at - 0.15, 900.0), (at, 3200.0), (length, 3200.0)]), order=2, width=0.8)
        env = curve(n, [(0.0, 0.0), (at - 0.15, 0.0), (at - 0.14, 0.7 * hard), (at, 1.0 * hard), (at + 0.01, 0.0), (length, 0.0)])
        out = z * env * 0.9
        out += ratchet(n, at - 0.15, 0.15, 90.0, 160.0) * hard
        back = curve(n, [(0.0, 0.0), (at + 0.01, 0.0), (at + 0.012, 1.0), (at + 0.1, 0.0), (length, 0.0)])
        out += bp(white(n), 1200.0, 4200.0) * back * 0.6 * hard
        out += np.roll(flesh(n, 0.18, wet=1.0), ns(at - 0.15)) * 0.5 * hard
        return out

    x += tug(1.0, 0.8)
    x += tug(2.0, 1.0)
    # The engine's coughs after each tug: two pops, then a longer stumbling sputter.
    pts = [(0.0, 0.0), (1.015, 0.0), (1.02, 9.0), (1.2, 12.0), (1.25, 0.0),
           (2.015, 0.0), (2.02, 11.0), (2.5, 19.0), (2.58, 0.0), (length, 0.0)]
    lvl = [(0.0, 0.0), (1.015, 0.0), (1.02, 0.8), (1.25, 0.6), (1.26, 0.0), (2.015, 0.0), (2.02, 1.3), (2.58, 1.0), (2.59, 0.0), (length, 0.0)]
    x += engine(n, curve(n, pts), curve(n, lvl), skip=0.25, jitter=0.4)
    # Gripping the handle between: the rope creaking, the pawls ticking now and then.
    x += ratchet(n, 0.05, 0.85, 7.0, 4.0) * 0.4
    x += ratchet(n, 1.2, 0.7, 6.0, 5.0) * 0.4
    creak = np.sin(2 * np.pi * np.cumsum((180.0 + 60.0 * np.sin(2 * np.pi * 1.7 * t)) / SR)) * curve(n, [(0.0, 0.0), (0.3, 0.05), (0.85, 0.05), (1.0, 0.0), (1.3, 0.0), (1.6, 0.06), (1.9, 0.0), (length, 0.0)])
    x += creak * 0.5
    # The tension swelling into the yank: the starter's pawls racing, a low drone rising.
    x += ratchet(n, 2.62, 0.38, 20.0, 110.0) * 1.4
    drone = np.sin(2 * np.pi * np.cumsum((70.0 + 90.0 * np.clip((t - 2.5) / 0.5, 0.0, 1.0)) / SR)) * curve(n, [(0.0, 0.0), (2.5, 0.0), (3.0, 0.6)])
    x += drone * 0.9
    return x * np.minimum(1.0, (n - np.arange(n)) / ns(0.02))


def form_revive_start():
    """The friend's yank: the transformation's start from tick FormTimeline.REVIVE_SKIP on (the engine catching, coughing,
    bursting out and roaring), with a hard crack of the cord snapping back and a wet spray at the front of it: the limbs
    growing back in blood."""
    skip = 10 / 20.0
    body = form_start()
    x = body[ns(skip):].copy()
    n = len(x)
    # The crack of the rope letting go, the handle knocking back against him.
    snap = np.zeros(n)
    snap[:ns(0.012)] = white(ns(0.012)) * np.linspace(1.0, 0.0, ns(0.012))
    x += hp(snap, 800.0) * 1.8
    back = curve(n, [(0.0, 0.0), (0.004, 1.0), (0.18, 0.0), (n / SR, 0.0)])
    x += bp(white(n), 1200.0, 4200.0) * back * 0.9
    knock = np.zeros(n)
    knock[ns(0.16)] = 1.0
    x += lp(np.convolve(knock, np.sin(2 * np.pi * 180 * np.arange(ns(0.05)) / SR) * np.exp(-np.arange(ns(0.05)) / ns(0.01)))[:n], 1200.0) * 0.7
    # The limbs coming back out of their stumps in a spray of blood.
    x += np.roll(flesh(n, 0.7, wet=1.6), ns(0.04)) * 1.2
    x += np.roll(flesh(n, 0.4, wet=1.3), ns(0.2)) * 0.7
    x[:ns(0.002)] *= np.linspace(0.0, 1.0, ns(0.002))
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


def form_crunch():
    """The saw biting hard into someone (a player, or a finisher): a heavy thump, bone cracking under the teeth, the
    engine bogging right down and a wet rip after."""
    n = ns(0.6)
    t = np.arange(n) / SR
    # The thump of it landing, low.
    thump = np.sin(2 * np.pi * (55.0 + 40.0 * np.exp(-t / 0.03)) * t) * np.exp(-t / 0.07) * 1.6
    # Bone giving: a few hard cracks close together.
    crack = np.zeros(n)
    for at, size in ((0.004, 1.0), (0.022, 0.7), (0.041, 0.85), (0.07, 0.45)):
        m = ns(0.03)
        k = np.arange(m) / SR
        start = ns(at)
        crack[start:start + m] += (bp(white(m), 1500.0, 6500.0) * np.exp(-k / 0.006) * size)[:n - start]
    rate = curve(n, [(0.0, 160.0), (0.04, 70.0), (0.3, 105.0), (0.6, 140.0)])
    level = curve(n, [(0.0, 1.3), (0.35, 0.7), (0.6, 0.0)])
    x = thump + crack * 1.8
    x += engine(n, rate, level, buzz=0.7) * 0.6
    x += flesh(n, 0.45, wet=1.8) * 1.3
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
# to 495; the anime's voices are their own sounds, over this), leaps (495), goes into you (520), the cord (566), your heart's six beats
# (550, 562, 572, 580, 586, 591), you get up (596), standing, waiting to pull the cord (626).
MERGE_LAND = 7 / 20.0
MERGE_DARK = 60 / 20.0
MERGE_COMING = 90 / 20.0
MERGE_SNIFF = 140 / 20.0
MERGE_CLIMB = 175 / 20.0
MERGE_TALK = 190 / 20.0
MERGE_LEAP = 495 / 20.0
MERGE_MONTAGE = 311 / 20.0
MERGE_MONTAGE_END = 397 / 20.0
MERGE_HEART = 540 / 20.0
MERGE_WAKE = 572 / 20.0
MERGE_GASP = 600 / 20.0
MERGE_SINK = 520 / 20.0
MERGE_SUNK = 546 / 20.0
MERGE_PLUNGE = 540 / 20.0
MERGE_BEATS = [574 / 20.0, 584 / 20.0, 592 / 20.0]
MERGE_END = 626 / 20.0
MERGE_RISE_AT = 608 / 20.0


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
    x += hum * curve(n, [(0.0, 0.0), (MERGE_DARK, 0.0), (MERGE_DARK + 1.5, 1.0), (MERGE_TALK, 0.8), (MERGE_LEAP, 0.9),
                         (MERGE_PLUNGE, 1.4), (MERGE_PLUNGE + 0.05, 0.0), (MERGE_END + 0.5, 0.0)])
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
    # As he dives: the hum swelling. The plunge: a deep boom, a wet tearing, and the cord ripping out of you.
    boom = np.zeros(n)
    boom[ns(MERGE_PLUNGE)] = 1.0
    kb = np.arange(ns(1.4))
    x += np.convolve(boom, np.sin(2 * np.pi * np.linspace(70.0, 28.0, len(kb)) * kb / SR) * np.exp(-kb / ns(0.4)))[:n] * 2.4
    x += np.roll(flesh(n, 0.9, wet=2.0), ns(MERGE_PLUNGE)) * 1.4
    x += ratchet(n, MERGE_SUNK, 0.25, 140.0, 60.0) * 0.6
    # The heart starting in the black: three beats, closer and harder, the last with the engine's first cough in it.
    for i, at in enumerate(MERGE_BEATS):
        for dub, gain in ((0.0, 1.0), (0.15, 0.6)):
            kk = np.arange(ns(0.18))
            thud = np.sin(2 * np.pi * 50.0 * kk / SR) * np.exp(-kk / ns(0.045)) * (1.4 + 0.5 * i) * gain
            start = ns(at + dub)
            x[start:start + len(thud)] += thud[:n - start]
    # No music in the dream (kortev: it should feel empty, just you talking to him): only the hum of the dark, above.
    # The leap: air rushing, rising, into the boom.
    whoosh = bp(white(n), 500.0, 3500.0)
    x += whoosh * curve(n, [(0.0, 0.0), (MERGE_LEAP + 0.35, 0.0), (MERGE_LEAP + 0.85, 0.12), (MERGE_PLUNGE - 0.05, 0.3), (MERGE_PLUNGE, 0.0),
                            (MERGE_END + 0.5, 0.0)])
    # Inside your chest: the first two beats deep and muffled, as from inside.
    for at in MERGE_BEATS[:2]:
        kk = np.arange(ns(0.5))
        thump = np.sin(2 * np.pi * 36.0 * kk / SR) * np.exp(-kk / ns(0.12)) * 1.6
        start = ns(at)
        x[start:start + len(thump)] += thump[:n - start]
    # Waking: the world's sound rushing back in, and a gasp.
    x += lp(white(n), 1400.0) * curve(n, [(0.0, 0.0), (MERGE_WAKE, 0.0), (MERGE_GASP - 0.05, 0.14), (MERGE_GASP + 0.1, 0.05),
                                         (MERGE_RISE_AT, 0.02), (MERGE_END + 0.5, 0.0)])
    m = ns(0.38)
    kk = np.arange(m) / SR
    gasp = bp(white(m), 700.0, 3200.0) * np.sin(np.pi * np.minimum(kk / 0.38, 1.0)) ** 0.6 * (0.6 + 0.4 * kk / 0.38)
    start = ns(MERGE_GASP - 0.05)
    x[start:start + m] += (gasp * 0.5)[:n - start]
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


def debris(n, at, length, rate, size=1.0, level=1.0):
    """Stones and grit clattering: {rate} small thuds and clicks a second for {length} s from {at} s, tailing off."""
    out = np.zeros(n)
    t = at
    while t < at + length:
        t += g.rng.exponential(1.0 / rate)
        if t >= at + length:
            break
        k = (1.0 - (t - at) / length) ** 1.5
        out = place(out, t, g.rock(size * g.rng.uniform(0.5, 1.4)) * k * level)
    return out


# ---------------------------------------------------------------------------------------------------------------
# Vanilla material. Beam's sounds are built on pitched, filtered and layered Minecraft sounds, decoded here from the
# loom asset cache and baked into our own .ogg files: nothing in the game reads a vanilla file for them at runtime.
# ---------------------------------------------------------------------------------------------------------------
ASSETS = os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'fabric-loom', 'assets')
ASSET_INDEX = '1.21.1-17.json'
VANILLA_CACHE = os.path.join(os.path.dirname(__file__), '..', 'work', 'beam_sounds', '_vanilla')
_vanilla = {}
_index = []


def _ffmpeg_on_path():
    import shutil
    if shutil.which('ffmpeg') is None:
        scratch = os.environ.get('FFMPEG_DIR')
        if scratch:
            os.environ['PATH'] = scratch + os.pathsep + os.environ['PATH']


def vanilla(key):
    """The vanilla sound {key} (like 'mob/ravager/roar1', under minecraft/sounds, without .ogg) as mono floats at SR, decoded on demand
    with ffmpeg and cached on disk (work/beam_sounds/_vanilla)."""
    if key in _vanilla:
        return _vanilla[key]
    import json
    import subprocess
    import wave
    os.makedirs(VANILLA_CACHE, exist_ok=True)
    path = os.path.join(VANILLA_CACHE, key.replace('/', '__') + '.wav')
    if not os.path.exists(path):
        if not _index:
            with open(os.path.join(ASSETS, 'indexes', ASSET_INDEX)) as f:
                _index.append(json.load(f)['objects'])
        h = _index[0]['minecraft/sounds/' + key + '.ogg']['hash']
        _ffmpeg_on_path()
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', os.path.join(ASSETS, 'objects', h[:2], h), '-ac', '1', '-ar', str(SR), path], check=True)
    with wave.open(path) as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768.0
    _vanilla[key] = x / max(np.abs(x).max(), 1e-6)
    return _vanilla[key]


def vclip(key, pitch=1.0, hi=None, lo=None, gain=1.0, start=0.0, length=None, onset=False, fin=0.003, fout=0.03):
    """A piece of a vanilla sound: from {start} s (or from where it first gets loud, {onset}) for {length} s of the result, pitched by
    {pitch} (resampled: lower is slower and deeper), low-passed at {hi} Hz (so what is pitched up is not fizzy), high-passed at {lo}."""
    x = vanilla(key)
    if onset:
        start += np.argmax(np.abs(x) > 0.1) / SR
    x = x[ns(start):]
    if len(x) < 8:
        return np.zeros(8)
    x = np.interp(np.arange(0, len(x) - 1, pitch), np.arange(len(x)), x)
    if length is not None:
        x = x[:ns(length)]
    if hi:
        x = lp(x, hi)
    if lo:
        x = hp(x, lo)
    m = len(x)
    a, b = min(ns(fin), m // 2), min(ns(fout), m // 2)
    if a:
        x[:a] *= np.linspace(0.0, 1.0, a)
    if b:
        x[m - b:] *= np.linspace(1.0, 0.0, b) ** 1.5
    return x * gain


def pick(*keys):
    return keys[g.rng.integers(len(keys))]


def flat(x, win=0.06):
    """x with its loudness evened out, so a growl keeps its voice but does not pulse (for sounds that are played over and over)."""
    env = lp(np.abs(x), 1.0 / win)
    env = np.maximum(env, 0.15 * env.max())
    y = x / env
    return y / max(np.abs(y).max(), 1e-6)


def drift(n, rate=5.0, depth=0.25):
    """A slow, random wobble around 1, for loudness that never repeats a beat."""
    w = lp(g.rng.normal(0.0, 1.0, n), rate)
    return 1.0 + depth * w / max(np.abs(w).max(), 1e-6)


def trickle(n, at, length, rate, level=1.0, pitch=(0.6, 1.1)):
    """Earth and stone raining down: {rate} bits a second for {length} s from {at} s, thinning out, each a vanilla clip of gravel, dirt
    or stone pitched down, a few of our own pebbles among them."""
    out = np.zeros(n)
    t = at
    while True:
        t += g.rng.exponential(1.0 / rate)
        if t >= at + length:
            break
        k = (1.0 - (t - at) / length) ** 1.4
        if g.rng.random() < 0.25:
            out = place(out, t, g.rock(g.rng.uniform(0.6, 1.4)) * k * level * 0.7)
            continue
        key = pick('dig/gravel1', 'dig/gravel2', 'dig/gravel3', 'dig/gravel4', 'block/rooted_dirt/break1', 'block/rooted_dirt/break2',
                   'block/rooted_dirt/step1', 'block/rooted_dirt/step2', 'dig/stone1', 'dig/stone2', 'block/mud/break1', 'block/mud/break2')
        p = g.rng.uniform(*pitch)
        out = place(out, t, vclip(key, pitch=p, hi=3800, length=0.22, gain=k * level * g.rng.uniform(0.35, 0.9), fout=0.05))
    return out


def sub(n, points, tau, drive=2.0, at=0.0):
    """A sub-bass thump: a sine sweeping through {points} (time, Hz) dying with time constant {tau} s, driven by {drive}."""
    t = np.arange(n) / SR
    f = curve(n, points)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-np.maximum(t - at, 0.0) / tau) * (t >= at)
    return sat(x * drive, 1.6)


def taper(x, seconds, power=1.0):
    """x faded to nothing over its last {seconds} s."""
    k = min(ns(seconds), len(x))
    x[len(x) - k:] *= np.linspace(1.0, 0.0, k) ** power
    return x


def beam_burst():
    """The summon: the earth cracks and a huge shark throws himself up out of it. A crack of stone and deepslate, a boom of explosion
    pulled down low, the thud of something enormous, a heavy throw of earth and then rubble raining back. Played low (pitch 0.7) with
    the dive it is the crash of him landing."""
    n = ns(1.6)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += sub(n, [(0.0, 95.0), (0.12, 55.0), (0.4, 34.0), (1.6, 28.0)], 0.2, drive=2.0) * 0.85
    # The earth splitting: stone and deepslate breaking, dripstone snapping, a hard crack on top.
    x = place(x, 0.0, vclip(pick('dig/stone1', 'dig/stone2', 'dig/stone3'), pitch=0.7, hi=5000, gain=1.0, onset=True, length=0.5))
    x = place(x, 0.0, vclip(pick('block/deepslate/break1', 'block/deepslate/break2'), pitch=0.8, hi=5500, gain=0.9, onset=True, length=0.9, fout=0.15))
    x = place(x, 0.01, vclip('block/dripstone/break2', pitch=0.75, hi=5000, gain=0.6, onset=True, length=0.4))
    x += bp(white(n), 800.0, 5200.0) * np.exp(-t / 0.035) * 0.9
    # The boom: an explosion with the top taken off, and a heavy fall under it.
    x = place(x, 0.0, vclip('random/explode1', pitch=0.7, hi=700, gain=1.1, onset=True, length=1.3, fout=0.4))
    x = place(x, 0.0, vclip('damage/fallbig', pitch=0.5, hi=400, gain=1.2, length=0.4))
    # The throw of earth: a rumbling roar swelling at once and sinking away.
    swell = np.minimum(t / 0.05, 1.0) * np.exp(-t / 0.5)
    x += g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 1500.0), (0.25, 550.0), (1.2, 170.0), (1.6, 140.0)]), order=2, width=1.6) * swell * 1.8
    x += lp(white(n), 150.0) * swell * 2.4
    # The rubble coming down.
    x += trickle(n, 0.2, 1.3, 36.0, level=0.9, pitch=(0.55, 1.0))
    return taper(x, 0.3)


def beam_step():
    """One footfall of a huge animal: a big iron step dropped an octave and more, a low thud, dirt and gravel scattering, over in
    four tenths of a second (it plays about three times a second while he walks)."""
    n = ns(0.42)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x = place(x, 0.0, vclip(pick('mob/irongolem/walk1', 'mob/irongolem/walk2', 'mob/irongolem/walk3', 'mob/irongolem/walk4'), pitch=0.55,
                            hi=1200, gain=1.0, onset=True, length=0.4, fout=0.12))
    x += sub(n, [(0.0, 90.0), (0.06, 60.0), (0.4, 40.0)], 0.07, drive=2.2) * 1.1
    x += lp(g.brown(n), 160.0) * np.exp(-t / 0.05) * 1.2
    # The ground giving way under the weight: dirt and gravel thrown about.
    x = place(x, 0.005, vclip(pick('block/rooted_dirt/step1', 'block/rooted_dirt/step2', 'block/rooted_dirt/step3', 'block/mud/step1'), pitch=0.7,
                              hi=3200, gain=0.8, onset=True, length=0.3, fout=0.1))
    x = place(x, 0.02, vclip(pick('dig/gravel1', 'dig/gravel2', 'dig/gravel3', 'dig/gravel4'), pitch=0.85, hi=3800, gain=0.55, onset=True,
                             length=0.3, fout=0.1))
    return taper(x, 0.12, 1.5)


def beam_swim():
    """Swimming through the ground, played again every 0.3 s (pitch and loudness following his speed), each over the end of the one
    before: a deep thick rumble of earth, a dragon's growl dropped far down and levelled out, dirt and gravel ripping past. Every layer
    is steady and the whole fades up and down in a sine, so the overlapping copies run into one smooth roar with no beat."""
    seconds = 0.6
    n = ns(seconds)
    t = np.arange(n) / SR
    env = np.sin(np.pi * t / seconds)
    x = lp(g.brown(n), 110.0) * 3.4 * drift(n, 4.0, 0.08)
    x += lp(white(n), 240.0) * 1.3 * drift(n, 6.0, 0.1)
    # The growl: steady, deep, with the grain of a big throat.
    growl = vclip(pick('mob/enderdragon/growl1', 'mob/enderdragon/growl2'), pitch=0.5, hi=520, lo=40, length=seconds, start=0.4, fin=0.0, fout=0.0)
    growl2 = vclip('mob/warden/angry_1', pitch=0.55, hi=480, lo=40, length=seconds, start=0.5, fin=0.0, fout=0.0)
    for gr, k in ((growl, 1.5), (growl2, 1.1)):
        gr = np.pad(flat(gr), (0, max(0, n - len(gr))))[:n]
        x += gr * k
    # Dirt and gravel torn past: dense, small, even.
    dirt = np.zeros(n)
    tt = 0.0
    while tt < seconds:
        tt += g.rng.exponential(1.0 / 45.0)
        dirt = place(dirt, tt, vclip(pick('dig/gravel1', 'dig/gravel2', 'dig/gravel3', 'dig/gravel4', 'block/rooted_dirt/step1', 'block/rooted_dirt/step2',
                                          'block/mud/step2'), pitch=g.rng.uniform(0.7, 1.0), hi=2600, length=0.2, gain=g.rng.uniform(0.12, 0.35), fout=0.06))
    x += dirt * 0.9 * drift(n, 5.0, 0.08)
    return x * env


def beam_bite():
    """A big wet chomp: jaws as wide as a door slamming shut (a ravager's bite and an iron fist, dropped low), wet flesh and crunching
    bone, a deep thump. Under 0.6 s, with no tail, so it can come six times in a second."""
    n = ns(0.58)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x = place(x, 0.0, vclip(pick('mob/ravager/bite1', 'mob/ravager/bite2', 'mob/ravager/bite3'), pitch=0.85, hi=4800, gain=1.0, onset=True, length=0.5, fout=0.1))
    x = place(x, 0.0, vclip(pick('mob/irongolem/hit1', 'mob/irongolem/hit2', 'mob/irongolem/hit3'), pitch=0.7, hi=3000, gain=0.7, onset=True, length=0.35, fout=0.1))
    x = place(x, 0.015, vclip(pick('mob/dolphin/eat1', 'mob/dolphin/eat2', 'mob/dolphin/eat3'), pitch=0.8, hi=4500, gain=0.9, onset=True, length=0.4, fout=0.1))
    x = place(x, 0.03, vclip(pick('block/bone_block/break1', 'block/bone_block/break2', 'block/bone_block/break3'), pitch=0.9, hi=5200, gain=0.6, onset=True,
                             length=0.2, fout=0.06))
    x += sub(n, [(0.0, 120.0), (0.08, 65.0), (0.4, 45.0)], 0.09, drive=2.0) * 1.0
    x += np.roll(flesh(n, 0.3, wet=1.4), ns(0.02)) * 0.45
    x += lp(white(n), 420.0) * np.exp(-np.maximum(t - 0.04, 0.0) / 0.1) * 0.9 * (t > 0.04)
    return taper(x, 0.16, 1.4)


def beam_dive():
    """The earth closing over him: a sinking rumble of mud and dirt, the ground heaving shut (a warden's burrowing, lowered), debris
    trickling in behind. Clean when played up a fifth or more for coming out of the ground."""
    n = ns(1.5)
    t = np.arange(n) / SR
    env = np.minimum(t / 0.06, 1.0) * np.exp(-t / 0.55)
    x = np.zeros(n)
    x = place(x, 0.0, vclip('mob/warden/dig', pitch=0.8, hi=1500, lo=45, gain=1.0, start=0.6, length=1.4, fout=0.5))
    x = place(x, 0.0, vclip(pick('block/mud/break1', 'block/mud/break2', 'block/mud/break3'), pitch=0.7, hi=2800, gain=0.9, onset=True, length=0.5))
    x = place(x, 0.04, vclip(pick('block/rooted_dirt/break1', 'block/rooted_dirt/break2'), pitch=0.65, hi=2800, gain=0.8, onset=True, length=0.5))
    x += g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 1300.0), (0.3, 480.0), (1.0, 140.0), (1.5, 100.0)]), order=2, width=1.4) * env * 1.4
    x += sub(n, [(0.0, 70.0), (0.5, 42.0), (1.5, 30.0)], 0.45, drive=1.6) * 0.8
    x += lp(white(n), 150.0) * env * 1.6
    x += trickle(n, 0.1, 1.3, 26.0, level=0.8, pitch=(0.65, 1.0))
    return taper(x, 0.25)


def beam_screech():
    """Beam's cry of joy when he is called up: a big breath in, then a roar from a thing with too many lungs. A ravager's roar pitched up
    with a dolphin's whoop, a hoglin's squeal and a warden's rage riding it, a mad cackle of short cries after, a gravel throat and a
    wolf's snarl under all of it."""
    n = ns(1.85)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # The breath in, then the cry.
    inhale = bp(white(n), 700.0, 4200.0) * np.minimum(t / 0.16, 1.0) ** 2 * (t < 0.17) * 0.5
    x += inhale * np.minimum((0.17 - t) / 0.02, 1.0).clip(0.0, 1.0)
    x = place(x, 0.16, vclip(pick('mob/ravager/roar1', 'mob/ravager/roar2'), pitch=1.55, hi=6500, gain=1.0, start=0.05, length=1.0, fout=0.12))
    x = place(x, 0.2, vclip('mob/warden/agitated_2', pitch=1.1, hi=3500, gain=0.65, start=0.1, length=0.9, fout=0.15))
    x = place(x, 0.3, vclip('mob/dolphin/jump1', pitch=1.35, hi=7500, gain=0.6, onset=True, length=0.75, fout=0.15))
    x = place(x, 0.55, vclip('mob/hoglin/angry2', pitch=1.45, hi=6000, gain=0.7, onset=True, length=0.6, fout=0.1))
    x = place(x, 0.16, vclip('mob/wolf/growl1', pitch=0.9, hi=3000, gain=0.45, start=0.1, length=1.0, fout=0.2))
    # The cackle: short ravager, zoglin and hoglin cries, tumbling over each other and pitched wildly.
    for i in range(7):
        key = pick('mob/ravager/hurt1', 'mob/ravager/hurt2', 'mob/ravager/idle2', 'mob/zoglin/angry1', 'mob/zoglin/hurt1', 'mob/hoglin/hurt2')
        x = place(x, 1.06 + i * 0.1 + g.rng.uniform(-0.01, 0.01), vclip(key, pitch=g.rng.uniform(1.6, 2.3), hi=6500, gain=0.75 - i * 0.05, onset=True, length=0.13,
                                                                   fout=0.04))
    # The throat: gravel and grit dragged along under the voice, and the ground shaking as he comes out.
    x += trickle(n, 0.1, 1.6, 22.0, level=0.5, pitch=(0.5, 0.8))
    x += lp(white(n), 130.0) * np.exp(-t / 0.4) * 1.0 * (t > 0.05)
    x += bp(white(n), 250.0, 1400.0) * 0.12 * np.minimum(t / 0.2, 1.0) * np.exp(-t / 1.0)
    x = lp(x, 8500.0)
    return taper(sat(x * 0.8, 1.4), 0.25)


def beam_chatter():
    """His teeth clattering, a quick run of hard clacks, bony and sharp, with a little wet of his mouth under them. Quiet and short:
    it comes every few seconds while he stands about."""
    n = ns(0.5)
    x = np.zeros(n)
    for i in range(5):
        at = 0.015 + i * 0.085 + g.rng.uniform(-0.007, 0.007)
        k = ns(0.04)
        tt = np.arange(k) / SR
        clack = bp(white(k), 1500.0, 6500.0) * np.exp(-tt / 0.005) * 1.1
        x = place(x, at, clack * g.rng.uniform(0.7, 1.1))
        x = place(x, at, vclip(pick('block/bone_block/step1', 'block/bone_block/step2', 'block/bone_block/step3', 'block/bone_block/step4'), pitch=g.rng.uniform(1.15, 1.5),
                               hi=6000, gain=g.rng.uniform(0.7, 1.0), onset=True, length=0.07, fout=0.02))
    x = place(x, 0.0, vclip('mob/dolphin/eat2', pitch=1.1, hi=3000, gain=0.35, onset=True, length=0.45, fout=0.2))
    return taper(x, 0.12, 1.5)


def beam_slam():
    """The ambush: Beam erupting from under a mob, the loudest sound he makes. A long boom with the ground splitting on top of it,
    a heavy fall, deepslate and stone breaking, a sub-bass drop, a roar of earth flung into the air and a long rain of gravel and stone."""
    n = ns(2.3)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += sub(n, [(0.0, 85.0), (0.1, 50.0), (0.5, 30.0), (2.3, 22.0)], 0.4, drive=2.6) * 1.0
    # The boom: two explosions, one slowed right down.
    x = place(x, 0.0, vclip('random/explode2', pitch=0.55, hi=650, gain=1.2, onset=True, length=2.0, fout=0.6))
    x = place(x, 0.0, vclip('random/explode1', pitch=0.85, hi=2600, gain=0.7, onset=True, length=1.2, fout=0.4))
    x = place(x, 0.0, vclip('damage/fallbig', pitch=0.4, hi=350, gain=1.3, length=0.5))
    # The ground splitting: stone, deepslate and dripstone, broad and hard.
    x = place(x, 0.0, vclip('block/deepslate/break1', pitch=0.65, hi=5500, gain=1.0, onset=True, length=1.0, fout=0.2))
    x = place(x, 0.03, vclip('block/deepslate/break3', pitch=0.55, hi=5000, gain=0.9, onset=True, length=1.0, fout=0.2))
    x = place(x, 0.0, vclip('dig/stone2', pitch=0.6, hi=5500, gain=1.0, onset=True, length=0.6))
    x = place(x, 0.05, vclip('block/dripstone/break1', pitch=0.7, hi=5500, gain=0.7, onset=True, length=0.4))
    crack = bp(white(n), 500.0, 6500.0) * np.exp(-t / 0.04)
    x += crack * 1.1
    # The smash: flesh and bone taking the whole of his head.
    x = place(x, 0.01, vclip('mob/hoglin/attack1', pitch=0.6, hi=3500, gain=0.6, onset=True, length=0.5))
    x += np.roll(flesh(n, 0.5, wet=1.6), ns(0.012)) * 0.8
    # The earth flung up and falling back.
    swell = np.minimum(t / 0.05, 1.0) * np.exp(-t / 0.8)
    x += g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 2400.0), (0.3, 850.0), (1.5, 210.0), (2.3, 150.0)]), order=2, width=1.6) * swell * 1.8
    x += lp(white(n), 170.0) * swell * 2.6
    x += trickle(n, 0.2, 2.0, 48.0, level=1.1, pitch=(0.5, 1.0))
    return taper(x, 0.4)


def beam_launch():
    """Throwing the rider, or flinging a mob: the snap of his head with a wet crack and a low thump behind it, and a whip of air
    tearing past, high to low."""
    n = ns(0.9)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x = place(x, 0.0, vclip('mob/irongolem/throw', pitch=0.85, hi=6500, gain=1.0, onset=True, length=0.3, fout=0.08))
    x = place(x, 0.0, vclip('mob/hoglin/attack1', pitch=0.75, hi=4500, gain=0.65, onset=True, length=0.35, fout=0.1))
    x += bp(white(n), 1500.0, 7500.0) * np.exp(-t / 0.012) * 1.3
    x += np.roll(flesh(n, 0.2, wet=1.2), ns(0.01)) * 0.5
    x += sub(n, [(0.0, 150.0), (0.1, 62.0), (0.9, 48.0)], 0.12, drive=1.9) * 1.0
    air = np.minimum(t / 0.1, 1.0) * np.exp(-np.maximum(t - 0.1, 0.0) / 0.26)
    x += g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 5500.0), (0.25, 2600.0), (0.9, 500.0)]), order=2, width=1.3) * air * 2.4
    x += bp(white(n), 300.0, 1400.0) * air * 0.5
    return taper(x, 0.25)



# ---- Forever, the Eternity contract's power: the Eternity Devil. The anime gives it a voice of many people speaking at once, so the
# lock is a crowd of whispered voices (many detuned, formant-filtered, breath with no words) over a deep clock tick that stops, with wet
# squelches as its eyes open; a stored hit is a muffled thud with a ticking echo; the release is teeth biting shut, a heavy boom and
# time rushing back (a reversed whoosh). Nothing vanilla and nothing ripped: all synthesised.

def reversed_swell(n, at, length, lo=120.0, hi=3800.0, level=1.0):
    """Air sucked backwards: noise sweeping up through a band and swelling into its end (a whoosh played in reverse)."""
    m = ns(length)
    t = np.linspace(0.0, 1.0, m)
    swept = g.sweep_filter(white(m), 'bandpass', lo * (hi / lo) ** (t ** 1.4), order=2, width=0.8)
    env = (t ** 2.2) * (1.0 - np.exp(-(1.0 - t) / 0.02))
    out = np.zeros(n)
    k = min(m, n - ns(at))
    out[ns(at):ns(at) + k] = (swept * env * level)[:k]
    return out


VOWELS = ((800.0, 1200.0, 2500.0), (450.0, 1900.0, 2600.0), (300.0, 2200.0, 3000.0), (500.0, 900.0, 2500.0), (320.0, 800.0, 2400.0), (650.0, 1700.0, 2500.0))


def whisper_voice(n, start, length, level):
    """One whispering voice: breath through three vowel formants (each syllable another vowel, the formants shifted for this voice), in
    syllables of a speaking pace with small gaps. No words."""
    out = np.zeros(n)
    shift = g.rng.uniform(0.82, 1.25)
    pos = start
    while pos < start + length:
        syl = g.rng.uniform(0.07, 0.2)
        gap = g.rng.uniform(0.015, 0.11)
        f1, f2, f3 = VOWELS[g.rng.integers(len(VOWELS))]
        m = ns(syl)
        noise = white(m)
        y = bp(noise, f1 * shift * 0.85, f1 * shift * 1.17) * 1.0 + bp(noise, f2 * shift * 0.88, f2 * shift * 1.13) * 0.9 + bp(noise, f3 * shift * 0.9, f3 * shift * 1.1) * 0.5
        y *= np.sin(np.pi * np.arange(m) / m) ** 1.4
        out = place(out, pos, y * level * g.rng.uniform(0.6, 1.0))
        pos += syl + gap
    return out


def clock_tick(n, at, level=1.0, pitch=1.0):
    """A deep clock's tick: a dry knock and a low ring."""
    m = ns(0.22)
    t = np.arange(m) / SR
    y = np.sin(2 * np.pi * 86.0 * pitch * t) * np.exp(-t / 0.06) * 1.6
    y += np.sin(2 * np.pi * 172.0 * pitch * t) * np.exp(-t / 0.03) * 0.6
    y += bp(white(m), 500.0, 2400.0) * np.exp(-t / 0.006) * 1.4
    y *= np.minimum(t / 0.0008, 1.0)
    out = np.zeros(n)
    k = min(m, n - ns(at))
    out[ns(at):ns(at) + k] = (y * level)[:k]
    return out


def forever_lock():
    """The lock: wet squelches as the eyes open, then a crowd of voices whispering together over a deep clock tick that stops; 3 s of it."""
    seconds = 3.2
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # Eyes opening one after another (eight): a wet suck and a slap each.
    for j in range(8):
        at = 0.1 + 0.07 * j + g.rng.uniform(0.0, 0.02)
        x = place(x, at, flesh(ns(0.3), 0.2, 1.2)[:ns(0.3)] * g.rng.uniform(0.35, 0.6))
        x = place(x, at, np.sin(2 * np.pi * np.cumsum(curve(ns(0.1), [(0.0, 190.0), (0.1, 70.0)])) / SR) * np.exp(-np.arange(ns(0.1)) / SR / 0.025) * 0.5)
    # The crowd.
    crowd = np.zeros(n)
    for v in range(34):
        crowd += whisper_voice(n, g.rng.uniform(0.0, 0.5), seconds - 0.2, 0.55)
    swell = np.minimum(t / 0.7, 1.0) ** 1.5 * np.clip((seconds - t) / 0.25, 0.0, 1.0)
    x += crowd * swell * 1.9
    # The clock, until it stops: ticks a little slower than a second hand, then nothing.
    at = 0.12
    gap = 0.55
    while at < 2.55:
        x += clock_tick(n, at, 0.85)
        at += gap
        gap *= 1.012
    return taper(x, 0.05)


def forever_hit():
    """A stored hit: a muffled thud, and a ticking echo that dies away behind it."""
    n = ns(0.9)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * np.cumsum(curve(n, [(0.0, 120.0), (0.12, 55.0)])) / SR) * np.exp(-t / 0.07) * 1.8
    x += lp(white(n), 420.0) * np.exp(-t / 0.05) * 2.2
    x *= np.minimum(t / 0.001, 1.0)
    for k, (at, level) in enumerate(((0.16, 0.5), (0.3, 0.32), (0.46, 0.2), (0.64, 0.1))):
        tick = bp(white(ns(0.03)), 900.0 - 80.0 * k, 3200.0 - 400.0 * k) * np.exp(-np.arange(ns(0.03)) / SR / 0.006) * 2.0
        x = place(x, at, tick * level)
    return taper(x, 0.15)


def forever_release():
    """The release: teeth biting shut all at once (a crunch and a clack), the line rushing in, a heavy boom, and time rushing back (a
    reversed whoosh) with a clock speeding up and stopping in it."""
    n = ns(3.0)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # Teeth: a hail of dry clacks and a wet crunch.
    for k in range(26):
        at = g.rng.uniform(0.0, 0.07)
        clack = bp(white(ns(0.03)), 1800.0, 7500.0) * np.exp(-np.arange(ns(0.03)) / SR / 0.004) * g.rng.uniform(0.5, 1.0)
        x = place(x, at, clack * 1.2)
    crunch = bp(white(ns(0.25)), 300.0, 2600.0) * np.exp(-np.arange(ns(0.25)) / SR / 0.05) * 2.2
    x = place(x, 0.0, crunch)
    # The line snapping in (the first 0.2 s), a sub swelling into the boom.
    x += reversed_swell(n, 0.0, 0.22, 200.0, 7500.0, 2.0)
    x += reversed_swell(n, 0.0, 0.2, 80.0, 1600.0, 1.2)
    # The boom at the blow (0.2 s).
    b = 0.2
    after = np.maximum(t - b, 0.0)
    x += sub(n, [(0.0, 150.0), (b, 130.0), (b + 0.1, 52.0), (b + 1.6, 34.0)], 0.7, drive=2.4) * 1.5 * (t >= b)
    x += lp(g.brown(n), 170.0) * np.exp(-after / 0.45) * 3.0 * (t >= b)
    x += lp(white(n), 1700.0) * np.exp(-after / 0.08) * 1.6 * (t >= b)
    x += hp(white(n), 4500.0) * np.exp(-after / 0.025) * 0.9 * (t >= b)
    # Time rushing back: a long reversed whoosh from the blow.
    x += reversed_swell(n, b + 0.15, 1.5, 150.0, 4200.0, 0.9)
    # A clock speeding up, then stopped.
    at = b + 0.5
    gap = 0.32
    for k in range(7):
        x += clock_tick(n, at, 0.5 * (1.0 - 0.05 * k), 1.0 + 0.04 * k)
        at += gap
        gap *= 0.72
    return taper(x, 0.6)


# ---- The Eternity Devil's hotel and the devil itself (all synthesised): the night lobby's hum, the crowd whispering (and speaking), the
# walls bleeding, a phone ringing in an empty room, a door breathing, the loop breaking, and the devil: growing out of the walls, biting,
# slamming a tendril down, regrowing, hurt, dying in a collapse of many voices.

def crowd_voice(seconds, voices=14, f0=(95.0, 230.0), level=1.0, bend=(1.0, 1.0), breath=0.25):
    """Many people speaking at once: one shared rhythm of syllables and vowels, each voice its own pitch (a buzz of a vocal cord shaped by
    three vowel formants, a little vibrato) and its own breath; {bend} runs the pitch (and the formants) from one multiple to another over
    the sound (a collapsing groan)."""
    n = ns(seconds)
    out = np.zeros(n)
    sched = []
    pos = 0.06
    while pos < seconds - 0.25:
        syl = g.rng.uniform(0.12, 0.3)
        sched.append((pos, syl, VOWELS[g.rng.integers(len(VOWELS))]))
        pos += syl + g.rng.uniform(0.02, 0.1)
    for v in range(voices):
        base = g.rng.uniform(*f0)
        shift = g.rng.uniform(0.85, 1.2)
        off = g.rng.uniform(0.0, 0.05)
        for at, syl, (f1, f2, f3) in sched:
            m = ns(syl)
            mult = bend[0] + (bend[1] - bend[0]) * min(1.0, (at + syl / 2.0) / seconds)
            f = base * mult * (1.0 + 0.07 * np.sin(2 * np.pi * np.arange(m) / SR * 5.5 + g.rng.uniform(0.0, 6.28)))
            src = (np.cumsum(f) / SR) % 1.0 * 2.0 - 1.0
            src = src + breath * white(m)
            s = shift * mult
            y = bp(src, f1 * s * 0.9, f1 * s * 1.12) + bp(src, f2 * s * 0.9, f2 * s * 1.12) * 0.7 + bp(src, f3 * s * 0.92, f3 * s * 1.1) * 0.35
            y *= np.sin(np.pi * np.arange(m) / m) ** 1.2
            if at + off + syl < seconds:
                out = place(out, at + off, y * level * g.rng.uniform(0.6, 1.0))
    return out


def hotel_hum():
    """The night lobby: a fluorescent tube's hum (the mains and its harmonics, beating a little) over the breath of a ventilation shaft and
    a tick now and then; loops."""
    n = ns(6.4)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k, (f, a) in enumerate(((100.0, 1.0), (200.0, 0.6), (300.0, 0.35), (400.0, 0.2), (600.0, 0.12))):
        # Whole cycles over the loop so the hum is the same at both ends.
        f = round(f * 6.0) / 6.0
        x += np.sin(2 * np.pi * f * t + 0.3 * k) * a * (1.0 + 0.12 * np.sin(2 * np.pi * (k + 1) / 6.0 * t))
    x = sat(x * 0.8, 1.2)
    air = lp(white(n), 260.0) * 3.0 + bp(white(n), 700.0, 1500.0) * 0.5
    air *= 1.0 + 0.3 * np.sin(2 * np.pi * t / 6.0 * 2.0)
    x = x * 0.7 + air
    for at in (1.3, 3.9, 4.6):
        x = place(x, at, bp(white(ns(0.02)), 1200.0, 5000.0) * np.exp(-np.arange(ns(0.02)) / SR / 0.004) * 0.9)
    return loop(x, 6.0, 0.4)


def hotel_whisper():
    """The crowd's whisper in the corridor: many voices breathing words, swelling and falling away."""
    seconds = 3.0
    n = ns(seconds)
    t = np.arange(n) / SR
    crowd = np.zeros(n)
    for v in range(26):
        crowd += whisper_voice(n, g.rng.uniform(0.0, 0.5), seconds - 0.5, 0.55)
    swell = np.sin(np.pi * np.clip(t / seconds, 0.0, 1.0)) ** 1.3
    return crowd * swell * 1.9


def hotel_voice():
    """The devil speaking: a crowd of voices in one rhythm, low and wrong, with wet breath under it."""
    seconds = 2.6
    n = ns(seconds)
    t = np.arange(n) / SR
    x = crowd_voice(seconds, voices=16, f0=(80.0, 200.0), level=0.9)
    x += lp(white(n), 500.0) * (0.4 + 0.3 * np.sin(2 * np.pi * 3.0 * t)) * 0.4
    for j in range(5):
        x = place(x, g.rng.uniform(0.0, seconds - 0.5), flesh(ns(0.3), 0.2, 1.0)[:ns(0.3)] * 0.2)
    return taper(x * np.minimum(t / 0.15, 1.0), 0.2)


def hotel_bleed():
    """The walls bleeding: wallpaper tearing, plaster cracking, flesh pushing through with wet squelches, thick dripping, a low groan."""
    seconds = 3.4
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # The groan of the building.
    x += sub(n, [(0.0, 48.0), (1.5, 62.0), (seconds, 38.0)], 5.0, drive=1.6) * 0.9 * np.minimum(t / 0.5, 1.0)
    # Paper tearing and plaster cracking along the walls.
    for j in range(26):
        at = g.rng.uniform(0.05, seconds - 0.6)
        m = ns(g.rng.uniform(0.05, 0.3))
        rip = bp(white(m), 1500.0, 6500.0) * np.exp(-np.arange(m) / m * 4.0) * g.rng.uniform(0.3, 0.9)
        x = place(x, at, rip)
        x = place(x, at, lp(white(ns(0.06)), 800.0) * np.exp(-np.arange(ns(0.06)) / SR / 0.012) * g.rng.uniform(0.4, 1.2))
    # Flesh pushing through.
    for j in range(14):
        at = g.rng.uniform(0.2, seconds - 0.8)
        x = place(x, at, flesh(ns(0.5), g.rng.uniform(0.2, 0.45), 1.3)[:ns(0.5)] * g.rng.uniform(0.3, 0.7))
    # Drops, falling in pitch.
    for j in range(22):
        at = g.rng.uniform(0.6, seconds - 0.2)
        m = ns(0.07)
        f0 = g.rng.uniform(300.0, 700.0)
        drop = np.sin(2 * np.pi * np.cumsum(curve(m, [(0.0, f0), (0.07, f0 * 0.45)])) / SR) * np.exp(-np.arange(m) / SR / 0.02)
        x = place(x, at, drop * g.rng.uniform(0.15, 0.4))
    return taper(x, 0.4)


def hotel_phone():
    """An old telephone ringing in a room down the hall: the two-tone bell, rung in bursts, muffled by the door."""
    n = ns(2.1)
    x = np.zeros(n)
    for start in (0.05, 1.05):
        for k in range(2):
            at = start + 0.45 * k
            m = ns(0.38)
            tt = np.arange(m) / SR
            ring = (np.sin(2 * np.pi * 1020.0 * tt) + np.sin(2 * np.pi * 1280.0 * tt) * 0.8) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 21.0 * tt)))
            ring *= np.minimum(tt / 0.004, 1.0) * np.minimum((0.38 - tt) / 0.01, 1.0)
            x = place(x, at, ring * 0.5)
    x = lp(sat(x, 1.5), 2600.0) * 1.2 + lp(white(n), 120.0) * 0.1
    return x


def hotel_breath():
    """A door breathing: a long slow breath in through a gap, a creak of old wood, a long breath out."""
    n = ns(3.4)
    t = np.arange(n) / SR
    x = np.zeros(n)
    inn = np.exp(-((t - 0.9) / 0.55) ** 2)
    out = np.exp(-((t - 2.4) / 0.6) ** 2)
    x += bp(white(n), 350.0, 1300.0) * inn * 1.6
    x += bp(white(n), 250.0, 900.0) * out * 2.0
    # The hinge.
    creak = np.sin(2 * np.pi * np.cumsum(curve(n, [(0.0, 310.0), (1.6, 520.0), (3.4, 300.0)]) * (1.0 + 0.05 * np.sin(2 * np.pi * 38.0 * t))) / SR)
    x += creak * np.exp(-((t - 1.55) / 0.5) ** 2) * 0.35 * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 14.0 * t)))
    x += sub(n, [(0.0, 60.0), (3.4, 50.0)], 20.0, drive=1.2) * 0.2 * np.minimum(t / 0.4, 1.0)
    return taper(x, 0.3)


def hotel_loop_break():
    """The loop breaking: a glassy crack, the lights failing (their hum sliding down and out), a deep drop, the room settling."""
    seconds = 4.0
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += reversed_swell(n, 0.0, 0.35, 300.0, 9000.0, 1.6)
    for j in range(30):
        at = 0.32 + g.rng.uniform(0.0, 0.12)
        m = ns(0.05)
        x = place(x, at, hp(white(m), 3500.0) * np.exp(-np.arange(m) / SR / 0.008) * g.rng.uniform(0.3, 1.0) * 1.4)
    x = place(x, 0.32, bp(white(ns(0.5)), 600.0, 5000.0) * np.exp(-np.arange(ns(0.5)) / SR / 0.1) * 1.8)
    b = 0.32
    x += sub(n, [(0.0, 140.0), (b, 120.0), (b + 0.15, 45.0), (b + 2.5, 30.0)], 0.9, drive=2.4) * 1.5 * (t >= b)
    # The lights dying: a hum falling from 100 Hz, stuttering.
    hum = np.sin(2 * np.pi * np.cumsum(curve(n, [(0.0, 100.0), (b, 100.0), (b + 2.4, 24.0), (seconds, 20.0)])) / SR)
    stutter = np.clip(np.sin(2 * np.pi * np.cumsum(curve(n, [(0.0, 7.0), (seconds, 2.0)])) / SR) * 3.0 + 1.5, 0.0, 1.0)
    x += sat(hum * 1.6, 1.4) * np.exp(-np.maximum(t - b, 0.0) / 1.1) * stutter * 0.5 * (t >= b)
    x += lp(g.brown(n), 160.0) * np.exp(-np.maximum(t - b, 0.0) / 0.9) * 2.0 * (t >= b)
    return taper(x, 0.8)


def devil_grow():
    """The devil growing out of the walls: plaster and wallpaper giving way, wet flesh pushing out in lumps, a groan of many voices rising
    out of it and a deep, long thud as it fills the end of the corridor."""
    seconds = 4.4
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += sub(n, [(0.0, 40.0), (2.0, 58.0), (seconds, 34.0)], 6.0, drive=1.5) * np.minimum(t / 0.6, 1.0) * 0.9
    for j in range(36):
        at = g.rng.uniform(0.0, seconds - 1.0)
        x = place(x, at, flesh(ns(0.6), g.rng.uniform(0.25, 0.55), 1.4)[:ns(0.6)] * g.rng.uniform(0.3, 0.8) * (0.5 + at / seconds))
    for j in range(20):
        at = g.rng.uniform(0.0, seconds - 0.5)
        m = ns(g.rng.uniform(0.05, 0.25))
        x = place(x, at, bp(white(m), 1200.0, 6000.0) * np.exp(-np.arange(m) / m * 4.0) * g.rng.uniform(0.3, 0.8))
    voices = crowd_voice(seconds, voices=18, f0=(70.0, 170.0), level=0.8, bend=(0.7, 1.15))
    x += voices * np.minimum(t / 1.4, 1.0) ** 1.4 * 1.2
    b = 3.3
    x += sub(n, [(0.0, 150.0), (b, 120.0), (b + 0.12, 46.0), (b + 1.0, 30.0)], 0.5, drive=2.4) * 1.6 * (t >= b)
    x += lp(g.brown(n), 160.0) * np.exp(-np.maximum(t - b, 0.0) / 0.4) * 3.0 * (t >= b)
    return taper(x, 0.7)


def devil_bite():
    """A mouth biting: a wet lunge, a hail of uneven teeth clacking shut, a crunch, a low thump."""
    n = ns(0.75)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += reversed_swell(n, 0.0, 0.14, 300.0, 4500.0, 0.9)
    b = 0.14
    for k in range(18):
        at = b + g.rng.uniform(0.0, 0.05)
        clack = bp(white(ns(0.03)), 1500.0, 7000.0) * np.exp(-np.arange(ns(0.03)) / SR / 0.004) * g.rng.uniform(0.5, 1.0)
        x = place(x, at, clack * 1.1)
    x = place(x, b, bp(white(ns(0.3)), 250.0, 2400.0) * np.exp(-np.arange(ns(0.3)) / SR / 0.06) * 2.0)
    x += sub(n, [(0.0, 130.0), (b, 110.0), (b + 0.08, 60.0), (0.7, 45.0)], 0.1, drive=2.0) * 1.1 * (t >= b)
    x = place(x, b + 0.02, flesh(ns(0.4), 0.25, 1.4)[:ns(0.4)] * 0.5)
    return taper(x, 0.2, 1.4)


def devil_slam():
    """A tendril slamming down the corridor: the whip of it, a wet heavy slap on the floor, the boom through the building."""
    n = ns(1.2)
    t = np.arange(n) / SR
    x = np.zeros(n)
    m = ns(0.25)
    sw = g.sweep_filter(white(m), 'bandpass', 400.0 * (9.0) ** np.linspace(0.0, 1.0, m), order=2, width=0.8)
    x = place(x, 0.0, sw * np.hanning(m) * 1.2)
    b = 0.24
    x += sub(n, [(0.0, 120.0), (b, 110.0), (b + 0.1, 42.0), (1.2, 30.0)], 0.35, drive=2.4) * 1.5 * (t >= b)
    x += lp(white(n), 900.0) * np.exp(-np.maximum(t - b, 0.0) / 0.05) * 2.6 * (t >= b)
    x += lp(g.brown(n), 220.0) * np.exp(-np.maximum(t - b, 0.0) / 0.3) * 2.0 * (t >= b)
    x = place(x, b, flesh(ns(0.5), 0.3, 1.5)[:ns(0.5)] * 0.9)
    return taper(x, 0.3)


def devil_regen():
    """Flesh knitting back: a run of wet squelches and clicks that rise in pitch, bubbling."""
    n = ns(1.5)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j in range(18):
        at = 0.05 + j * 0.07 + g.rng.uniform(0.0, 0.03)
        m = ns(0.12)
        f0 = 120.0 + 18.0 * j
        blip = np.sin(2 * np.pi * np.cumsum(curve(m, [(0.0, f0), (0.12, f0 * 1.9)])) / SR) * np.exp(-np.arange(m) / SR / 0.03)
        x = place(x, at, blip * g.rng.uniform(0.3, 0.7))
        x = place(x, at, lp(white(m), 900.0) * np.exp(-np.arange(m) / SR / 0.02) * 0.8)
    x += flesh(n, 1.2, 1.2) * 0.4
    return taper(x * np.minimum(t / 0.05, 1.0), 0.4)


def devil_hurt():
    """Hit: a wet grunt of many voices and a squelch."""
    seconds = 0.55
    n = ns(seconds)
    t = np.arange(n) / SR
    x = crowd_voice(seconds, voices=10, f0=(90.0, 220.0), level=0.9, bend=(1.1, 0.8))
    x += flesh(n, 0.35, 1.4) * 0.7
    return taper(x * np.minimum(t / 0.01, 1.0), 0.2)


def devil_death():
    """The death: the voices of everyone it ever held sinking into one collapsing groan, flesh deflating, a last long sub as the building
    exhales."""
    seconds = 6.0
    n = ns(seconds)
    t = np.arange(n) / SR
    x = crowd_voice(seconds, voices=24, f0=(100.0, 260.0), level=0.8, bend=(1.2, 0.35)) * np.exp(-t / 3.2) * 1.4
    x += crowd_voice(seconds, voices=12, f0=(60.0, 120.0), level=0.7, bend=(1.0, 0.5)) * np.exp(-t / 2.8)
    for j in range(24):
        at = g.rng.uniform(0.2, 4.2)
        x = place(x, at, flesh(ns(0.7), g.rng.uniform(0.3, 0.6), 1.5)[:ns(0.7)] * g.rng.uniform(0.3, 0.8) * np.exp(-at / 3.0))
    x += sub(n, [(0.0, 100.0), (2.0, 55.0), (seconds, 28.0)], 2.8, drive=1.8) * 1.2
    x += lp(g.brown(n), 180.0) * np.exp(-t / 1.6) * 2.0
    return taper(x, 1.2)


def hotel_seal():
    """A contract sealed: paper rustling, a hard thud of a stamp and a faint long ring after it."""
    n = ns(1.2)
    t = np.arange(n) / SR
    x = bp(white(n), 2000.0, 8000.0) * np.exp(-t / 0.08) * 0.5 * (t < 0.3)
    b = 0.3
    x += sub(n, [(0.0, 130.0), (b, 110.0), (b + 0.06, 60.0), (1.2, 50.0)], 0.12, drive=2.0) * 1.4 * (t >= b)
    x += lp(white(n), 1200.0) * np.exp(-np.maximum(t - b, 0.0) / 0.03) * 1.8 * (t >= b)
    x += np.sin(2 * np.pi * 440.0 * t) * np.exp(-np.maximum(t - b, 0.0) / 0.35) * 0.1 * (t >= b)
    return taper(x, 0.2)


def hotel_heartbeat():
    """The devil's heart in the wall: a slow double beat, lub-dub, a heavy thump through plaster with a wet knock in it."""
    seconds = 1.1
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for at, level in ((0.0, 1.0), (0.3, 0.7)):
        x += sub(n, [(0.0, 78.0), (at + 0.02, 78.0), (at + 0.16, 42.0), (seconds, 36.0)], 0.11, drive=2.2, at=at) * level * 1.2
        k = np.maximum(t - at, 0.0)
        x += lp(white(n), 260.0) * np.exp(-k / 0.04) * (t >= at) * 1.1 * level
        x += bp(white(n), 900.0, 2600.0) * np.exp(-k / 0.012) * (t >= at) * 0.35 * level
    return taper(x, 0.1)


def hotel_cut():
    """A cut wall bleeding: a wet slice, a thick spurt and a few heavy drops."""
    seconds = 0.9
    n = ns(seconds)
    t = np.arange(n) / SR
    x = flesh(n, 0.45, 1.6) * 1.2
    x += bp(white(n), 1800.0, 6000.0) * np.exp(-t / 0.03) * 0.5
    x += lp(white(n), 500.0) * np.exp(-t / 0.2) * 1.2
    for at in (0.35, 0.52, 0.71):
        m = ns(0.08)
        f0 = g.rng.uniform(260.0, 520.0)
        drop = np.sin(2 * np.pi * np.cumsum(curve(m, [(0.0, f0), (0.08, f0 * 0.4)])) / SR) * np.exp(-np.arange(m) / SR / 0.022)
        x = place(x, at, drop * 0.45)
    return taper(x, 0.2)


def hotel_knock():
    """A knock on a wall that is only a wall: a dull thud and a dry crack of plaster, nothing behind it."""
    n = ns(0.45)
    t = np.arange(n) / SR
    x = sub(n, [(0.0, 120.0), (0.05, 85.0), (0.45, 70.0)], 0.07, drive=1.8) * 1.2
    x += lp(white(n), 900.0) * np.exp(-t / 0.02) * 1.5
    x += bp(white(n), 2500.0, 7000.0) * np.exp(-t / 0.006) * 0.4
    return taper(x, 0.1)


# ---- Contract bonds, "Not yet" and the heart's levels (all synthesised).

def forever_bond_up():
    """A bond deepening: the Eternity Devil's crowd speaking one line together (no words), rising out of silence over a deep clock that
    ticks slower and slower and stops, and a low swell under the last syllable; 2.4 s."""
    seconds = 2.4
    n = ns(seconds)
    t = np.arange(n) / SR
    x = crowd_voice(seconds, voices=18, f0=(85.0, 210.0), level=0.9, bend=(1.0, 0.92)) * (np.minimum(t / 0.5, 1.0) ** 1.4) * np.clip((seconds - t) / 0.4, 0.0, 1.0) * 1.6
    at = 0.1
    gap = 0.4
    while at < 1.9:
        x += clock_tick(n, at, 0.8 * (1.0 - at / 3.2))
        at += gap
        gap *= 1.35
    x += sub(n, [(0.0, 60.0), (1.4, 58.0), (seconds, 40.0)], 1.2, drive=1.4) * np.minimum(t / 1.2, 1.0) * np.clip((seconds - t) / 0.5, 0.0, 1.0) * 0.8
    return taper(x, 0.3)


def forever_not_yet():
    """Not yet: the world's sound sucked in round you (a reversed swell), a single deep clock tick, then the hush of time held; 1.1 s."""
    n = ns(1.1)
    t = np.arange(n) / SR
    x = reversed_swell(n, 0.0, 0.42, 150.0, 5200.0, 1.8)
    x += reversed_swell(n, 0.0, 0.4, 60.0, 900.0, 1.4)
    x = place(x, 0.4, clock_tick(ns(0.6) , 0.0, 1.6, 0.9))
    hush = lp(white(n), 500.0) * np.exp(-np.maximum(t - 0.4, 0.0) / 0.35) * (t >= 0.4) * 0.5
    return taper(x + hush, 0.2)


def forever_step_out():
    """Stepping back into time: a soft boom from the middle, a brief rush of air and ticks resuming; 1.0 s."""
    n = ns(1.0)
    t = np.arange(n) / SR
    x = sub(n, [(0.0, 120.0), (0.08, 55.0), (0.9, 36.0)], 0.5, drive=2.0) * 1.1
    x += lp(g.brown(n), 220.0) * np.exp(-t / 0.2) * 2.0
    x += lp(white(n), 2400.0) * np.exp(-t / 0.06) * 1.0
    x += reversed_swell(n, 0.0, 0.1, 400.0, 6000.0, 0.8)
    for k, at in enumerate((0.3, 0.5, 0.66)):
        x += clock_tick(n, at, 0.4 + 0.1 * k, 1.1 + 0.06 * k)
    return taper(x, 0.3)


def form_heart_level():
    """The heart levelling up: a long roar through the throat, a wet burst of blood, a deep thud that shakes, the engine screaming over it; 2.6 s."""
    n = ns(2.6)
    t = np.arange(n) / SR
    x = np.zeros(n)
    x += roar(n, 0.05, 2.0, 82.0) * 1.0
    x += roar(n, 0.15, 1.6, 120.0) * 0.5
    x += sub(n, [(0.0, 110.0), (0.12, 48.0), (1.8, 32.0)], 0.8, drive=2.4) * 1.5
    x += lp(g.brown(n), 160.0) * np.exp(-t / 0.5) * 3.0
    x += flesh(n, 0.7, 1.4) * 0.9
    x += engine(n, curve(n, [(0.0, 20.0), (0.4, 70.0), (1.8, 70.0), (2.6, 30.0)]), curve(n, [(0.0, 0.0), (0.3, 0.8), (1.8, 0.8), (2.6, 0.0)]), skip=0.05) * 0.8
    return taper(x, 0.3)



# ---------------------------------------------------------------- the Tomato Devil (devil/), all synthesised

def slap_hit(n, at, level=1.0, pitch=1.0):
    """A palm slapping down: a sharp crack of skin, a wet smack, a soft thump under it."""
    m = ns(0.25)
    t = np.arange(m) / SR
    crack = bp(white(m), 1200.0 * pitch, 6500.0) * np.exp(-t / 0.006) * 1.6
    smack = bp(white(m), 300.0 * pitch, 2200.0) * np.exp(-t / 0.035) * 1.2
    thump = np.sin(2 * np.pi * 85.0 * pitch * t) * np.exp(-t / 0.05) * 1.3
    x = np.zeros(n)
    return place(x, at, (crack + smack + thump) * level)


def gulp(n, at, length, f0, f1, level=1.0):
    """A bubble rising through thick juice: a short sine bending up, its own little pop at the end."""
    m = ns(length)
    t = np.arange(m) / SR
    f = np.linspace(f0, f1, m)
    b = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / length) ** 0.7
    return place(np.zeros(n), at, b * level)


def tomato_step():
    """One hand coming down: a wet slap of a palm on the ground, short."""
    n = ns(0.3)
    x = slap_hit(n, 0.005, 0.8, 1.0 + g.rng.uniform(-0.1, 0.1))
    x += flesh(n, 0.12, 1.0) * 0.25
    return taper(x, 0.08)


def tomato_gurgle():
    """A gurgle deep in it: bubbles of juice in a big wet throat over a low groan."""
    seconds = 1.4
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j in range(22):
        at = g.rng.uniform(0.0, seconds - 0.2)
        x += gulp(n, at, g.rng.uniform(0.04, 0.12), g.rng.uniform(90, 200), g.rng.uniform(220, 520), g.rng.uniform(0.3, 0.8))
    groan = np.sin(2 * np.pi * np.cumsum(curve(n, [(0.0, 70.0), (0.7, 62.0), (seconds, 50.0)])) / SR)
    groan = lp(sat(groan * 2.0 + lp(white(n), 300.0) * 0.6, 1.5), 500.0) * np.sin(np.pi * t / seconds) * 0.8
    x += groan + lp(white(n), 700.0) * np.sin(np.pi * t / seconds) * 0.3
    return taper(x, 0.3)


def tomato_slap():
    """The slap landing: four palms slamming down almost at once, a wet crack, a thud through the ground."""
    n = ns(0.9)
    t = np.arange(n) / SR
    x = np.zeros(n)
    m = ns(0.12)
    sw = g.sweep_filter(white(m), 'bandpass', 500.0 * 6.0 ** np.linspace(0.0, 1.0, m), order=2, width=0.8)
    x = place(x, 0.0, sw * np.hanning(m) * 0.8)
    for k, at in enumerate((0.11, 0.125, 0.135, 0.15)):
        x += slap_hit(n, at, 1.0, 0.85 + 0.08 * k)
    x += sub(n, [(0.0, 90.0), (0.12, 80.0), (0.2, 40.0), (0.9, 30.0)], 0.18, drive=2.2, at=0.12) * 1.3
    x = place(x, 0.12, flesh(ns(0.4), 0.25, 1.5)[:ns(0.4)] * 0.6)
    return taper(x, 0.3)


def tomato_tuck():
    """Tucking in and rocking back: skin creaking under strain, a long wet breath in, the joints popping."""
    seconds = 0.95
    n = ns(seconds)
    t = np.arange(n) / SR
    x = reversed_swell(n, 0.0, 0.85, 200.0, 2600.0, 0.6)
    creak = np.sin(2 * np.pi * np.cumsum(curve(n, [(0.0, 140.0), (seconds, 260.0)])) / SR)
    creak = bp(sat(creak * 3.0, 2.0) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 30.0 * t))), 200.0, 2500.0) * 0.4 * np.minimum(t / 0.2, 1.0)
    x += creak
    for at in (0.2, 0.45, 0.62, 0.8):
        x = place(x, at, bp(white(ns(0.02)), 1500.0, 6000.0) * np.exp(-np.arange(ns(0.02)) / SR / 0.003) * 1.2)
    x += flesh(n, 0.8, 1.2) * 0.3
    return taper(x, 0.12)


def tomato_roll():
    """Rolling: a heavy wet rumble going over the ground, squelching each time round."""
    seconds = 1.3
    n = ns(seconds)
    t = np.arange(n) / SR
    rumble = lp(g.brown(n), 180.0) * 3.0 * (0.7 + 0.3 * np.sin(2 * np.pi * 3.0 * t))
    grit = bp(white(n), 800.0, 3000.0) * 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 6.0 * t) ** 2)
    x = rumble + grit
    for at in (0.05, 0.4, 0.75, 1.1):
        x = place(x, at, flesh(ns(0.3), 0.2, 1.3)[:ns(0.3)] * 0.6)
        x += slap_hit(n, at, 0.4, 0.6)
    env = np.minimum(t / 0.06, 1.0)
    return taper(x * env, 0.35)


def tomato_thud():
    """Into a wall: a big soft thud, the body squashing, a squelch and juice spattering."""
    n = ns(1.2)
    t = np.arange(n) / SR
    x = sub(n, [(0.0, 110.0), (0.05, 70.0), (0.3, 38.0), (1.2, 28.0)], 0.3, drive=2.6) * 1.6
    x += lp(white(n), 900.0) * np.exp(-t / 0.04) * 2.0
    x += flesh(n, 0.5, 1.8) * 0.9
    for j in range(14):
        at = g.rng.uniform(0.05, 0.6)
        x += gulp(n, at, 0.03, 300, 900, 0.25)
    return taper(x, 0.4)


def tomato_spit():
    """The seeds spat: a wet hawk up the throat and five quick pops."""
    n = ns(0.8)
    t = np.arange(n) / SR
    x = reversed_swell(n, 0.0, 0.18, 300.0, 3000.0, 0.7)
    x += lp(white(n), 1400.0) * np.exp(-np.maximum(t - 0.18, 0) / 0.08) * (t >= 0.18) * 1.2
    for k in range(5):
        at = 0.19 + k * 0.012
        x += gulp(n, at, 0.025, 500 + 60 * k, 1600, 0.9)
        x = place(x, at, bp(white(ns(0.015)), 1000.0, 5000.0) * np.exp(-np.arange(ns(0.015)) / SR / 0.003))
    x += flesh(n, 0.3, 1.6) * 0.4
    return taper(x, 0.25)


def tomato_hurt():
    """Cut: a wet squelch, juice spraying, a short gurgle of pain."""
    n = ns(0.55)
    t = np.arange(n) / SR
    x = flesh(n, 0.35, 1.8) * 1.0
    x += bp(white(n), 2500.0, 8000.0) * np.exp(-t / 0.12) * 0.5
    for j in range(8):
        x += gulp(n, g.rng.uniform(0.08, 0.35), 0.05, 120, 360, 0.5)
    return taper(x * np.minimum(t / 0.004, 1.0), 0.15)


def tomato_burst():
    """Bursting: a deep pop, the skin tearing open, juice and pulp splattering everywhere, the seeds thrown."""
    seconds = 2.0
    n = ns(seconds)
    t = np.arange(n) / SR
    x = sub(n, [(0.0, 140.0), (0.04, 60.0), (0.4, 30.0), (seconds, 24.0)], 0.45, drive=3.0) * 1.8
    x += lp(white(n), 2500.0) * np.exp(-t / 0.05) * 2.4
    x += flesh(n, 0.7, 2.0) * 1.2
    for j in range(40):
        at = g.rng.uniform(0.05, 1.4)
        x = place(x, at, bp(white(ns(0.04)), 600.0, 4000.0) * np.exp(-np.arange(ns(0.04)) / SR / 0.01) * g.rng.uniform(0.2, 0.7) * np.exp(-at / 0.8))
    return taper(x, 0.6)


def tomato_hiss():
    """A seed burning: a sharp hiss and crackle as the juice boils off."""
    seconds = 1.1
    n = ns(seconds)
    t = np.arange(n) / SR
    hiss = hp(white(n), 3000.0) * np.minimum(t / 0.02, 1.0) * np.exp(-t / 0.5) * 1.2
    x = hiss
    for j in range(40):
        at = g.rng.uniform(0.0, 0.9)
        x = place(x, at, bp(white(ns(0.006)), 2000.0, 9000.0) * np.exp(-np.arange(ns(0.006)) / SR / 0.0012) * g.rng.uniform(0.4, 1.2))
    for j in range(6):
        x += gulp(n, g.rng.uniform(0.0, 0.5), 0.04, 200, 700, 0.4)
    return taper(x, 0.4)


def tomato_swell():
    """A seed swelling: a slow wet creak of something growing, bubbling, a heartbeat thump in it."""
    seconds = 2.0
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j in range(26):
        x += gulp(n, g.rng.uniform(0.0, seconds - 0.2), g.rng.uniform(0.05, 0.14), g.rng.uniform(70, 150), g.rng.uniform(160, 380), g.rng.uniform(0.3, 0.7))
    x += flesh(n, 1.8, 1.4) * 0.3
    for at in (0.3, 0.55, 1.3, 1.55):
        x += sub(n, [(0.0, 70.0), (seconds, 50.0)], 0.08, drive=2.0, at=at) * 0.9
    return taper(x * np.minimum(t / 0.3, 1.0), 0.4)


def pochita_bark():
    """Pochita smelling a devil: two short barks, a small dog's, with an engine's rasp under them."""
    n = ns(0.75)
    x = np.zeros(n)
    for at, pitch in ((0.02, 620.0), (0.27, 680.0)):
        m = ns(0.14)
        t = np.arange(m) / SR
        f = pitch * (1.15 - 0.45 * np.minimum(t / 0.14, 1.0))
        ph = 2 * np.pi * np.cumsum(f) / SR
        tone = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.35 * np.sin(3 * ph) + 0.25 * white(m)
        env = np.minimum(t / 0.008, 1.0) * np.exp(-t / 0.05)
        x = place(x, at, sat(bp(tone, 350.0, 3500.0) * env * 2.0, 1.5))
    rate = curve(n, [(0.0, 26.0), (0.75, 22.0)])
    x += engine(n, rate, curve(n, [(0.0, 0.0), (0.05, 0.4), (0.6, 0.25), (0.75, 0.0)]), body=(140.0, 2200.0)) * 0.25
    return taper(x, 0.1)

SOUNDS = {
    'tomato_step': tomato_step,
    'tomato_gurgle': tomato_gurgle,
    'tomato_slap': tomato_slap,
    'tomato_tuck': tomato_tuck,
    'tomato_roll': tomato_roll,
    'tomato_thud': tomato_thud,
    'tomato_spit': tomato_spit,
    'tomato_hurt': tomato_hurt,
    'tomato_burst': tomato_burst,
    'tomato_hiss': tomato_hiss,
    'tomato_swell': tomato_swell,
    'pochita_bark': pochita_bark,
    'pochita_tame': pochita_tame,
    'forever_lock': forever_lock,
    'forever_hit': forever_hit,
    'forever_release': forever_release,
    'forever_bond_up': forever_bond_up,
    'forever_not_yet': forever_not_yet,
    'forever_step_out': forever_step_out,
    'form_heart_level': form_heart_level,
    'hotel_hum': hotel_hum,
    'hotel_whisper': hotel_whisper,
    'hotel_voice': hotel_voice,
    'hotel_bleed': hotel_bleed,
    'hotel_phone': hotel_phone,
    'hotel_breath': hotel_breath,
    'hotel_loop_break': hotel_loop_break,
    'hotel_seal': hotel_seal,
    'hotel_heartbeat': hotel_heartbeat,
    'hotel_cut': hotel_cut,
    'hotel_knock': hotel_knock,
    'devil_grow': devil_grow,
    'devil_bite': devil_bite,
    'devil_slam': devil_slam,
    'devil_regen': devil_regen,
    'devil_hurt': devil_hurt,
    'devil_death': devil_death,
    'beam_burst': beam_burst,
    'beam_step': beam_step,
    'beam_swim': beam_swim,
    'beam_bite': beam_bite,
    'beam_dive': beam_dive,
    'beam_screech': beam_screech,
    'beam_chatter': beam_chatter,
    'beam_slam': beam_slam,
    'beam_launch': beam_launch,
    'form_merge': form_merge,
    'form_cord': form_cord,
    'form_start': form_start,
    'form_revive_pull': form_revive_pull,
    'form_revive_start': form_revive_start,
    'form_idle': form_idle,
    'form_stop': form_stop,
    'form_slash': form_slash,
    'form_hit': form_hit,
    'form_crunch': form_crunch,
    'form_grind': form_grind,
    'form_chain': form_chain,
    'form_frenzy': form_frenzy,
    'form_frenzy_loop': form_frenzy_loop,
    'form_down': form_down,
    'form_revive': form_revive,
}
LOOPS = ('form_idle', 'form_grind', 'form_frenzy_loop', 'hotel_hum')
# Loudness (loudest 400 ms, dB).
LEVELS = {'tomato_step': -16, 'tomato_gurgle': -15, 'tomato_slap': -9, 'tomato_tuck': -13, 'tomato_roll': -12, 'tomato_thud': -9, 'tomato_spit': -11, 'tomato_hurt': -12, 'tomato_burst': -8, 'tomato_hiss': -13, 'tomato_swell': -13, 'pochita_bark': -12,
          'hotel_hum': -27, 'hotel_whisper': -19, 'hotel_voice': -12, 'hotel_bleed': -14, 'hotel_phone': -16, 'hotel_breath': -22, 'hotel_loop_break': -9, 'hotel_seal': -14, 'hotel_heartbeat': -10, 'hotel_cut': -12, 'hotel_knock': -15,
          'devil_grow': -9, 'devil_bite': -10, 'devil_slam': -8, 'devil_regen': -16, 'devil_hurt': -13, 'devil_death': -8, 'forever_lock': -13, 'forever_hit': -12, 'forever_release': -7, 'forever_bond_up': -11, 'forever_not_yet': -11, 'forever_step_out': -12, 'form_heart_level': -8, 'form_crunch': -10, 'pochita_tame': -14, 'form_merge': -12, 'form_cord': -14, 'form_start': -10, 'form_revive_pull': -14, 'form_revive_start': -10, 'form_idle': -20, 'form_stop': -16, 'form_slash': -13, 'form_hit': -12,
          'form_grind': -15, 'form_chain': -14, 'form_frenzy': -10, 'form_frenzy_loop': -17, 'form_down': -13,
          'form_revive': -14, 'beam_burst': -9, 'beam_step': -12, 'beam_swim': -13, 'beam_bite': -10, 'beam_dive': -13, 'beam_screech': -9, 'beam_chatter': -16, 'beam_slam': -6, 'beam_launch': -10}


def make(name):
    g.rng = rng_for(name)
    x = mono(SOUNDS[name]())
    x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
    if name in LOOPS:
        return np.clip(x, -0.95, 0.95)
    y = limit(x)
    if name.startswith('beam_'):
        # The limiter shaves the peaks of a sound brought up to its level: bring it up more until what is left reaches the level.
        gain = 1.0
        for _ in range(6):
            gain *= 10 ** ((LEVELS[name] - loudness(y)) / 20)
            y = limit(x * gain)
            if abs(loudness(y) - LEVELS[name]) < 0.3:
                break
    return y


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
