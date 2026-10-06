"""Synthesises Chitty Chitty Bang Bang's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All of them are mono, to be placed in the world at the car. The engine and the propeller are loops: everything in
them repeats exactly over the loop (pulses wrap round its end, noise is filtered as if it ran forever), so they go
round without a seam while the game bends their pitch with the car's speed.

The car is named for the noise her engine made: two sputters and two bangs. The start-up is that, after a turn of the
crank; a backfire is the same in miniature. Their bangs land where ChittyEntity puffs smoke out of the exhaust
(START_BANG_1 and START_BANG_2 ticks into the start-up, and five and ten ticks into a backfire).

Usage: python3 tools/gen_chitty_sounds.py [--wav DIR] [name ...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from gen_sounds import (SR, Mix, attack_decay, bp, brown, curve, hp, limit, loudness, lp, master, mono,  # noqa: E402
                        ns, pink, reverb, sat, sine, spaces, sweep_filter, white)

# The engine fires 30 times a second at tick-over (a four-cylinder at 900 rpm); 64 firings make the loop.
FIRE = 30
ENGINE_LOOP = 64 * SR // FIRE
# The propeller's blades pass 42 times a second; two seconds of them.
BLADES = 42
FLIGHT_LOOP = 2 * SR


def rng_for(name):
    return np.random.default_rng(zlib.crc32(('chitty:' + name).encode()))


def place(buf, at, x):
    """Adds x into buf at sample `at`, wrapping round the end (for loops)."""
    idx = (at + np.arange(len(x))) % len(buf)
    np.add.at(buf, idx, x)


def periodic(x, fn):
    """fn (a filter) applied to x as if x repeated for ever: three copies in, the middle one out."""
    n = len(x)
    return fn(np.concatenate([x, x, x]))[n:2 * n]


def loop_noise(n, make, fn):
    return periodic(make(n), fn)


# --- pieces ------------------------------------------------------------------------------------------

def pop(rng, strength=1.0, low=62.0):
    """One firing out of a big old exhaust: a thump, a puff of breath and a little crack."""
    n = ns(0.07)
    t = np.arange(n) / SR
    thump = np.sin(2 * np.pi * rng.uniform(0.92, 1.08) * low * t) * np.exp(-t / 0.013)
    puff = lp(white(n), 1100) * np.exp(-t / 0.009)
    crack = hp(white(n), 2600) * np.exp(-t / 0.0016)
    return (thump * 0.9 + puff * 0.8 + crack * 0.25) * strength


def chit(rng, strength=1.0):
    """A sneezy sputter: a misfire coughing breath out of the pipe."""
    n = ns(0.09)
    t = np.arange(n) / SR
    breath = bp(white(n), 700, 4200) * attack_decay(n, 0.004, 0.025)
    knock = np.sin(2 * np.pi * rng.uniform(120, 160) * t) * np.exp(-t / 0.01)
    return (breath * 1.1 + knock * 0.5) * strength


def tick(rng):
    """A valve or tappet clicking."""
    n = ns(0.012)
    t = np.arange(n) / SR
    return hp(white(n), 3000) * np.exp(-t / 0.0012) * rng.uniform(0.6, 1.0)


def bang(rng, size=1.0):
    """A backfire: a fat thump, a blast of noise, a crack, and the pipe ringing."""
    n = ns(0.7)
    t = np.arange(n) / SR
    thump = sine(curve(n, [(0, 110 / size), (0.05, 60 / size), (0.7, 38)], 'log')) * attack_decay(n, 0.0008, 0.07)
    blast = lp(white(n), 3000) * attack_decay(n, 0.0005, 0.025)
    crack = hp(white(n), 1600) * np.exp(-t / 0.005)
    ring = bp(white(n), 170, 240) * attack_decay(n, 0.002, 0.11)
    y = thump * 1.2 + blast * 0.9 + crack * 0.7 + ring * 0.6
    return sat(y * 1.6, 2.2)


def clunk(rng, low=85.0, bright=1.0):
    """A heavy mechanism coming home: a thump and the ring of the metal."""
    n = ns(0.5)
    t = np.arange(n) / SR
    thump = np.sin(2 * np.pi * low * t) * np.exp(-t / 0.03)
    ring = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * np.exp(-t / tau) * a
               for f, tau, a in ((610, 0.14, 0.3), (1430, 0.09, 0.2), (2870, 0.05, 0.12)))
    hit = hp(white(n), 1200) * np.exp(-t / 0.004)
    return thump * 1.0 + ring * bright + hit * 0.5


def ratchet(rng, n, start, end, rate0, rate1, gain=1.0):
    """Pawl clicks speeding up (or slowing down) between two times."""
    out = np.zeros(n)
    s = start
    while s < end:
        u = (s - start) / max(end - start, 1e-6)
        c = tick(rng) * 0.8 + bp(white(ns(0.012)), 2500, 6000) * np.exp(-np.arange(ns(0.012)) / SR / 0.002) * 0.6
        i = ns(s)
        k = min(len(c), n - i)
        if k > 0:
            out[i:i + k] += c[:k] * gain
        s += 1.0 / (rate0 + (rate1 - rate0) * u)
    return out


def snap(rng, strength=1.0):
    """Doped canvas whipping taut."""
    n = ns(0.12)
    t = np.arange(n) / SR
    whip = bp(white(n), 300, 2400) * attack_decay(n, 0.002, 0.03)
    thud = np.sin(2 * np.pi * rng.uniform(110, 150) * t) * np.exp(-t / 0.02)
    return (whip * 1.2 + thud * 0.7) * strength


def outdoors(x, wet=0.25):
    outdoor = spaces()[0]
    return mono(reverb(x, outdoor, wet=wet))[:len(x) + ns(1.5)]


# --- the sounds ----------------------------------------------------------------------------------------

def chitty_engine():
    """Ticking over: lumpy firing, two strong and two soft in each turn, the pipe booming with it, tappets clicking,
    the crankcase rumbling."""
    rng = rng_for('engine')
    n = ENGINE_LOOP
    gap = SR // FIRE
    pulses = np.zeros(n)
    accents = [1.0, 0.55, 0.9, 0.5, 1.0, 0.62, 0.85, 0.45]
    lean = [0, 18, -12, 25, 4, -20, 10, 30]  # samples early or late: an old engine's uneven beat
    for k in range(n // gap):
        place(pulses, k * gap + lean[k % 8], pop(rng, accents[k % 8]))
    # The big pipe booms at its own pitches.
    body = periodic(pulses, lambda x: x + 0.7 * bp(x, 95, 140, 2) + 0.4 * bp(x, 230, 300, 2))
    clicks = np.zeros(n)
    for k in range(n // (gap * 2)):
        place(clicks, k * gap * 2 + gap // 3, tick(rng))
    rumble = loop_noise(n, brown, lambda x: lp(x, 160)) * 0.25
    hiss = loop_noise(n, white, lambda x: bp(x, 900, 3500)) * 0.03
    hiss *= 0.6 + 0.4 * np.sin(2 * np.pi * FIRE * np.arange(n) / SR) ** 2
    y = body + clicks * 0.35 + rumble + hiss
    return sat(y * 1.4, 1.6)


def chitty_flight():
    """In the air: the propeller's thrum, the engine working harder underneath, wind over the wings."""
    rng = rng_for('flight')
    n = FLIGHT_LOOP
    gap = SR // BLADES
    thrum = np.zeros(n)
    for k in range(n // gap):
        m = ns(0.03)
        t = np.arange(m) / SR
        whum = (lp(white(m), 500) * 0.6 + np.sin(2 * np.pi * 2 * BLADES * t) * 0.8) * np.sin(np.pi * t / t[-1]) ** 2
        place(thrum, k * gap, whum * (0.85 + 0.15 * np.sin(2 * np.pi * k / 21)))
    thrum = periodic(thrum, lambda x: lp(x, 900))
    engine = np.zeros(n)
    egap = SR // 45
    for k in range(n // egap):
        place(engine, k * egap, pop(rng, 0.7 if k % 2 else 0.5, low=75.0))
    engine = periodic(engine, lambda x: lp(x, 1500))
    wind = loop_noise(n, pink, lambda x: bp(x, 250, 2600))
    tt = np.arange(n) / SR
    wind *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * tt) + 0.1 * np.sin(2 * np.pi * 1.5 * tt + 1.0)
    whistle = loop_noise(n, white, lambda x: bp(x, 1800, 2100)) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.0 * tt))
    y = thrum * 1.0 + engine * 0.45 + wind * 0.55 + whistle * 0.15
    return sat(y * 1.2, 1.4)


def chitty_start():
    """The crank turned twice, two sputters (chitty, chitty), two bangs, and she catches and ticks over."""
    rng = rng_for('start')
    total = 2.8
    n = ns(total)
    m = np.zeros(n)

    def add(x, at, gain=1.0):
        i = ns(at)
        k = min(len(x), n - i)
        m[i:i + k] += x[:k] * gain

    # Two turns of the starting handle: a ratchet and the engine turning over against compression.
    m += ratchet(rng, n, 0.0, 0.32, 18, 26, 0.5)
    for at in (0.08, 0.22):
        add(pop(rng, 0.35, low=45.0), at)
    # Chit-ty, chit-ty.
    for at, s in ((0.40, 1.0), (0.47, 0.7), (0.56, 1.0), (0.63, 0.75)):
        add(chit(rng, s), at, 0.9)
    # Bang, bang.
    add(bang(rng, 1.0), 0.75, 1.0)
    add(bang(rng, 1.08), 1.0, 1.0)
    # She catches: firing picks up from a stumble to a steady tick-over and fades under the running engine.
    s = 1.06
    k = 0
    while s < total - 0.05:
        u = min(1.0, (s - 1.06) / 0.6)
        add(pop(rng, (0.8 + 0.2 * (k % 2 == 0)) * (1.0 - 0.5 * max(0.0, (s - 2.0) / 0.8))), s, 0.7)
        s += 1.0 / (14 + (FIRE - 14) * u) * rng.uniform(0.9, 1.1)
        k += 1
    m = m + 0.5 * bp(m, 95, 140, 2)
    return master(outdoors(m, 0.22), peak=0.98, drive=1.3)


def chitty_bang():
    """A backfire when the throttle comes off: a stumble, then bang, bang."""
    rng = rng_for('bang')
    total = 1.6
    n = ns(total)
    m = np.zeros(n)
    for at, s in ((0.02, 0.7), (0.10, 0.9), (0.17, 0.6)):
        c = chit(rng, s)
        m[ns(at):ns(at) + len(c)] += c
    for at, size in ((0.25, 1.0), (0.50, 1.1)):
        b = bang(rng, size)
        k = min(len(b), n - ns(at))
        m[ns(at):ns(at) + k] += b[:k] * 1.1
    return master(outdoors(m, 0.3), peak=0.98, drive=1.4)


def chitty_horn():
    """Squeezing the serpent's bulb: a reedy, nasal parp-parp, the rubber wheezing as it fills again."""
    rng = rng_for('horn')
    total = 1.3
    n = ns(total)
    m = np.zeros(n)
    for at, f0, dur in ((0.0, 318.0, 0.34), (0.44, 310.0, 0.42)):
        k = ns(dur)
        t = np.arange(k) / SR
        f = f0 * (1.0 + 0.07 * (1.0 - np.exp(-t / 0.025))) * (1.0 - 0.05 * t / dur) * (1.0 + 0.004 * np.sin(2 * np.pi * 7 * t))
        reed = sat(g.saw(f, k) * 2.6, 3.0)
        voice = bp(reed, 600, 1100) * 1.2 + bp(reed, 1500, 2300) * 0.8 + bp(reed, 250, 400) * 0.5
        env = attack_decay(k, 0.012, 1.0) * np.clip((dur - t) / 0.04, 0, 1)
        air = bp(white(k), 2000, 6000) * 0.06
        m[ns(at):ns(at) + k] += (voice + air) * env
    # The bulb filling back up.
    k = ns(0.25)
    wheeze = bp(white(k), 900, 3000) * attack_decay(k, 0.05, 0.08) * 0.15
    m[ns(0.95):ns(0.95) + k] += wheeze
    return master(outdoors(m, 0.15), peak=0.98)


def chitty_wings_out():
    """The wings coming out: a ratchet winding faster, metal sliding, each panel of the fan snapping open, a clunk as
    they lock and a spring settling."""
    rng = rng_for('wings_out')
    total = 1.8
    n = ns(total)
    m = ratchet(rng, n, 0.0, 0.5, 12, 40, 0.8)
    k = ns(0.6)
    slide = sweep_filter(white(k), 'bandpass', curve(k, [(0, 900), (0.6, 2600)], 'log'), width=1.0)
    m[ns(0.1):ns(0.1) + k] += slide * attack_decay(k, 0.1, 0.25) * 0.5
    for i in range(7):
        s = snap(rng, 0.8 + 0.2 * (i % 2))
        at = ns(0.5 + i * 0.055)
        m[at:at + len(s)] += s
    c = clunk(rng, 80.0)
    m[ns(0.95):ns(0.95) + len(c)] += c * 1.1
    k = ns(0.6)
    t = np.arange(k) / SR
    boing = np.sin(2 * np.pi * np.cumsum(260 + 60 * np.sin(2 * np.pi * 9 * t) * np.exp(-t / 0.2)) / SR) * np.exp(-t / 0.18)
    m[ns(1.0):ns(1.0) + k] += boing * 0.2
    return master(outdoors(m, 0.2), peak=0.98)


def chitty_wings_in():
    """Folding away: canvas rustling shut, the ratchet, a clunk."""
    rng = rng_for('wings_in')
    total = 1.4
    n = ns(total)
    k = ns(0.45)
    rustle = bp(white(k), 400, 3000) * attack_decay(k, 0.05, 0.15) * (0.6 + 0.4 * np.sin(2 * np.pi * 22 * np.arange(k) / SR))
    m = np.zeros(n)
    m[:k] += rustle * 0.8
    m += ratchet(rng, n, 0.3, 0.7, 36, 14, 0.7)
    c = clunk(rng, 90.0, 0.8)
    m[ns(0.72):ns(0.72) + len(c)] += c
    return master(outdoors(m, 0.2), peak=0.98)


def chitty_floats():
    """The floats blowing up: a rush of air rising in pitch, the rubber squeaking taut, a slosh as she settles."""
    rng = rng_for('floats')
    total = 2.0
    n = ns(total)
    m = np.zeros(n)
    k = ns(0.95)
    hiss = sweep_filter(white(k), 'bandpass', curve(k, [(0, 700), (0.95, 3200)], 'log'), width=1.2)
    m[:k] += hiss * attack_decay(k, 0.05, 2.0) * np.clip((0.95 - np.arange(k) / SR) / 0.05, 0, 1) * 0.7
    k = ns(0.7)
    t = np.arange(k) / SR
    squeak = np.sin(2 * np.pi * np.cumsum(curve(k, [(0, 380), (0.7, 920)], 'log')) / SR)
    squeak *= (0.5 + 0.5 * np.sin(2 * np.pi * 31 * t)) * attack_decay(k, 0.1, 0.3) * 0.25
    m[ns(0.25):ns(0.25) + k] += squeak
    k = ns(0.2)
    t = np.arange(k) / SR
    bloop = np.sin(2 * np.pi * np.cumsum(curve(k, [(0, 240), (0.2, 90)], 'log')) / SR) * np.exp(-t / 0.05)
    m[ns(0.97):ns(0.97) + k] += bloop * 0.8
    k = ns(0.9)
    slosh = lp(white(k), 1400) * attack_decay(k, 0.02, 0.25) * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * np.arange(k) / SR))
    m[ns(1.0):ns(1.0) + k] += slosh * 0.6
    return master(outdoors(m, 0.15), peak=0.98)


def chitty_crash():
    """Hitting something: crumpled metal, splintering wood, a hubcap ringing and bits rattling down."""
    rng = rng_for('crash')
    total = 1.8
    n = ns(total)
    m = np.zeros(n)
    k = ns(0.5)
    t = np.arange(k) / SR
    crunch = sum(bp(white(k), f * 0.9, f * 1.1) * np.exp(-t / tau)
                 for f, tau in ((420, 0.12), (980, 0.08), (1730, 0.06), (3100, 0.04)))
    m[:k] += sat(crunch * 3.0, 2.0)
    c = clunk(rng, 70.0, 1.2)
    m[:len(c)] += c
    for at in rng.uniform(0.02, 0.2, 6):
        k = ns(0.03)
        crack = hp(white(k), 1500) * np.exp(-np.arange(k) / SR / 0.004)
        m[ns(at):ns(at) + k] += crack * rng.uniform(0.3, 0.7)
    s = 0.25
    while s < 1.4:
        kk = ns(0.03)
        bit = bp(white(kk), 2000, 6000) * np.exp(-np.arange(kk) / SR / 0.006) * rng.uniform(0.1, 0.35) * (1.5 - s)
        m[ns(s):ns(s) + kk] += bit
        s += rng.exponential(0.06)
    k = ns(1.2)
    t = np.arange(k) / SR
    hubcap = (np.sin(2 * np.pi * 1870 * t) + 0.5 * np.sin(2 * np.pi * 4420 * t)) * np.exp(-t / 0.35) * 0.12
    hubcap *= 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * (5 + 9 * t) * t))
    m[ns(0.3):ns(0.3) + k] += hubcap
    return master(outdoors(m, 0.25), peak=0.98, drive=1.3)


SOUNDS = {
    'chitty_engine': chitty_engine,
    'chitty_flight': chitty_flight,
    'chitty_start': chitty_start,
    'chitty_bang': chitty_bang,
    'chitty_horn': chitty_horn,
    'chitty_wings_out': chitty_wings_out,
    'chitty_wings_in': chitty_wings_in,
    'chitty_floats': chitty_floats,
    'chitty_crash': chitty_crash,
}
LOOPS = ('chitty_engine', 'chitty_flight')
# Loudness (loudest 400 ms, dB): the bangs well above everything, the running loops under the rest.
LEVELS = {'chitty_engine': -19, 'chitty_flight': -20, 'chitty_start': -13, 'chitty_bang': -9, 'chitty_horn': -12,
          'chitty_wings_out': -15, 'chitty_wings_in': -17, 'chitty_floats': -16, 'chitty_crash': -11}


def make(name):
    g.rng = rng_for(name + ':noise')
    x = mono(SOUNDS[name]())
    if name in LOOPS:
        # No fades or trims: the end runs straight back into the start. Just set the level and keep it off the rails.
        x = x - x.mean()
        x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
        return np.clip(x, -0.95, 0.95)
    return limit(x * 10 ** ((LEVELS[name] - loudness(x)) / 20))


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
