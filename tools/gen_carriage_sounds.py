"""Synthesises the Child Catcher's carriage's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All of them are mono, to be placed in the world at the carriage. Its horse is the game's own, and so are its hooves
(CarriageEntity plays the horse's own steps), its door (an iron door's) and the chain of its padlock. These are hers:
- the roll: a loop of her iron-tyred wooden wheels grinding over the ground, the deck and springs creaking and the cage
  rattling on the dray, every part repeating exactly over the loop so that it goes round without a seam while the game
  bends its pitch with her speed (CarriageSound);
- the whip: the lash whistling through the air and cracking;
- the disguise going up: the trader's cloths shaken out and hung over the bars, his stock set up on the roof;
- the disguise coming off: the cloths flapping off her all at once, the barrel and the chest tumbling into the road.

Usage: python3 tools/gen_carriage_sounds.py [--wav DIR] [name ...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from gen_sounds import (SR, attack_decay, bp, brown, curve, hp, limit, loudness, lp, master, mono, ns, pink,  # noqa: E402
                        reverb, spaces, sweep_filter, white)

# The carriage is part of the Chitty mod: its sounds go into its jar's assets.
g.OUT = os.path.join(os.path.dirname(__file__), '..', 'chitty', 'src', 'main', 'resources', 'assets', 'shootingstar', 'sounds')

ROLL_LOOP = 4 * SR
# Knocks per loop from each pair of wheels (a joint in the tyre, a loose spoke): whole numbers, so the loop joins up.
REAR_KNOCKS = 5
FRONT_KNOCKS = 6


def rng_for(name):
    return np.random.default_rng(zlib.crc32(('carriage:' + name).encode()))


def place(buf, at, x):
    """Adds x into buf at sample `at`, wrapping round the end (for loops)."""
    idx = (at + np.arange(len(x))) % len(buf)
    np.add.at(buf, idx, x)


def periodic(x, fn):
    n = len(x)
    return fn(np.concatenate([x, x, x]))[n:2 * n]


def loop_noise(n, make, fn):
    return periodic(make(n), fn)


def outdoors(x, wet=0.2):
    outdoor = spaces()[0]
    return reverb(x, outdoor, wet=wet)


def outdoors_loop(y, wet):
    """A loop through the outdoor reverb as if it went on for ever."""
    outdoor = spaces()[0]
    return periodic(y, lambda x: mono(reverb(x, outdoor, wet=wet)))


def ring(freqs, n, taus, gains):
    t = np.arange(n) / SR
    return sum(g_ * np.sin(2 * np.pi * f * t) * np.exp(-t / tau) for f, tau, g_ in zip(freqs, taus, gains))


def knock(rng, k, pitch=1.0):
    """A knock of wood on wood: a short hollow ring and a tap of noise."""
    t = np.arange(k) / SR
    body = ring((210 * pitch * rng.uniform(0.9, 1.1), 520 * pitch * rng.uniform(0.9, 1.1), 1150 * pitch), k,
                (0.04, 0.025, 0.012), (0.8, 0.5, 0.25))
    tap = bp(white(k), 600, 3500) * np.exp(-t / 0.004)
    return body + tap * 0.6


# --- the loop ------------------------------------------------------------------------------------------------

def carriage_roll():
    rng = rng_for('roll')
    n = ROLL_LOOP
    tt = np.arange(n) / SR
    # The rumble of iron tyres on the ground, and the grit crunching under them.
    rumble = loop_noise(n, brown, lambda x: lp(x, 140, 2)) * 1.4
    grit = np.zeros(n)
    for _ in range(900):
        k = ns(0.004)
        at = int(rng.integers(0, n))
        place(grit, at, hp(white(k), 2500) * np.exp(-np.arange(k) / SR / 0.0015) * rng.uniform(0.1, 0.5))
    grit = periodic(grit, lambda x: bp(x, 1800, 7000)) * 0.35
    # A joint in each tyre knocking once a turn (the small front wheels turn faster), the dray thudding with it.
    thuds = np.zeros(n)
    for knocks, pitch in ((REAR_KNOCKS, 0.8), (FRONT_KNOCKS, 1.0)):
        for i in range(knocks):
            at = int(i * n / knocks + rng.normal(0, ns(0.01)))
            k = ns(0.12)
            place(thuds, at, knock(rng, k, pitch) * rng.uniform(0.5, 0.8))
    thuds = periodic(thuds, lambda x: lp(x, 1500, 2))
    # The cage rattling on its bolts: light iron chatter, coming and going.
    chatter = np.zeros(n)
    for _ in range(140):
        k = ns(0.03)
        at = int(rng.integers(0, n))
        place(chatter, at, ring((1900 * rng.uniform(0.9, 1.1), 3300 * rng.uniform(0.9, 1.1)), k, (0.012, 0.008), (0.4, 0.25))
              * rng.uniform(0.2, 0.7))
    chatter *= 0.6 + 0.4 * np.sin(2 * np.pi * 3 * tt / (n / SR)) ** 2
    # The springs and the deck creaking, slowly.
    creak = loop_noise(n, white, lambda x: bp(x, 380, 760)) * (0.5 + 0.5 * np.sin(2 * np.pi * 2 * tt / (n / SR) + 0.4)) ** 6 * 0.25
    y = rumble + grit + thuds * 0.7 + chatter * 0.25 + creak
    return outdoors_loop(y, 0.12)


# --- one-shots ------------------------------------------------------------------------------------------------

def carriage_whip():
    """The lash whistling round and out, and its crack: the tip breaking the sound barrier, a sharp clap that rings
    off the countryside."""
    n = ns(1.2)
    m = np.zeros(n)
    k = ns(0.32)
    whoosh = sweep_filter(white(k), 'bandpass', curve(k, [(0, 500), (0.32, 2600)], 'log'), width=0.5)
    whoosh *= curve(k, [(0, 0.0), (0.22, 0.5), (0.32, 0.9)], 'linear')
    m[:k] += whoosh * 0.35
    at = k
    c = ns(0.012)
    t = np.arange(c) / SR
    # The crack: an N-shaped pressure wave, all the way up the spectrum.
    nwave = np.where(t < 0.0015, 1.0, np.where(t < 0.003, -0.9, 0.0)) * np.exp(-t / 0.004)
    m[at:at + c] += nwave * 1.2 + hp(white(c), 1500) * np.exp(-t / 0.002) * 0.7
    tail = ns(0.15)
    m[at:at + tail] += bp(white(tail), 1000, 6000) * np.exp(-np.arange(tail) / SR / 0.02) * 0.25
    return master(outdoors(m, 0.35), peak=0.98)


def flap(rng, k):
    """Cloth shaken out: a soft rush of air with the weave's hiss in it, rising and falling."""
    t = np.arange(k) / SR
    env = np.sin(np.pi * np.clip(t / (k / SR), 0, 1)) ** 2
    return (bp(pink(k), 300, 2500) * 0.6 + bp(white(k), 2500, 7000) * 0.15) * env * rng.uniform(0.6, 1.0)


def carriage_disguise_on():
    """The trader's cloths shaken out and hung over the bars, one after another, their rings ticking on the iron; the
    barrel and the chest set down on the roof."""
    rng = rng_for('disguise_on')
    n = ns(1.6)
    m = np.zeros(n)
    s = 0.02
    for i in range(3):
        k = ns(0.3)
        m[ns(s):ns(s) + k] += flap(rng, k)
        kk = ns(0.03)
        m[ns(s + 0.22):ns(s + 0.22) + kk] += ring((2600, 4100), kk, (0.01, 0.006), (0.25, 0.15))
        s += rng.uniform(0.3, 0.36)
    for at in (1.12, 1.36):
        k = ns(0.15)
        m[ns(at):ns(at) + k] += knock(rng, k, 0.75) * 0.8
    return master(outdoors(m, 0.18), peak=0.95)


def carriage_disguise_off():
    """Everything off her at once: the cloths torn off the bars and flapping down, the barrel and the chest knocking
    and bouncing on the road, the lanterns rattling after them."""
    rng = rng_for('disguise_off')
    n = ns(1.9)
    m = np.zeros(n)
    # The wrench: the cloths torn off the bars, flapping as they fall.
    k = ns(0.25)
    m[:k] += sweep_filter(white(k), 'bandpass', curve(k, [(0, 1200), (0.25, 500)], 'log'), width=0.5) * attack_decay(k, 0.01, 0.12) * 0.3
    for at in (0.05, 0.2, 0.32):
        kk = ns(0.35)
        m[ns(at):ns(at) + kk] += flap(rng, kk) * 0.8
    # Down they come, each landing and bouncing, more and more scattered.
    for i in range(8):
        at = 0.18 + rng.uniform(0, 0.55) + (0.1 if i > 8 else 0.0)
        kk = ns(0.16)
        m[ns(at):ns(at) + kk] += knock(rng, kk, rng.uniform(0.7, 1.2)) * rng.uniform(0.4, 1.0)
        if rng.uniform() < 0.6:
            bounce = at + rng.uniform(0.12, 0.3)
            m[ns(bounce):ns(bounce) + kk] += knock(rng, kk, rng.uniform(0.8, 1.2)) * rng.uniform(0.15, 0.4)
    # Sticks rattling: light, high, dry.
    for _ in range(18):
        at = 0.3 + rng.uniform(0, 0.9)
        kk = ns(0.03)
        m[ns(at):ns(at) + kk] += bp(white(kk), 2500, 6000) * np.exp(-np.arange(kk) / SR / 0.006) * rng.uniform(0.1, 0.3)
    # Sliding to a stop on the road.
    k = ns(0.6)
    m[ns(1.0):ns(1.0) + k] += bp(pink(k), 400, 2400) * attack_decay(k, 0.05, 0.25) * 0.2
    return master(outdoors(m, 0.2), peak=0.96)


SOUNDS = {
    'carriage_roll': carriage_roll,
    'carriage_whip': carriage_whip,
    'carriage_disguise_on': carriage_disguise_on,
    'carriage_disguise_off': carriage_disguise_off,
}
LOOPS = ('carriage_roll',)
# Loudness (loudest 400 ms, dB).
LEVELS = {'carriage_roll': -19, 'carriage_whip': -11, 'carriage_disguise_on': -17, 'carriage_disguise_off': -14}
# How long each one-shot runs at most (seconds): the outdoor reverb's long tail cut short and faded.
LENGTHS = {'carriage_whip': 1.6, 'carriage_disguise_on': 1.9, 'carriage_disguise_off': 2.4}


def make(name):
    g.rng = rng_for(name + ':noise')
    x = mono(SOUNDS[name]())
    if name in LOOPS:
        x = x - x.mean()
        x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
        return np.clip(x, -0.95, 0.95)
    n = min(len(x), ns(LENGTHS[name]))
    x = x[:n] * np.minimum(1.0, (n - np.arange(n)) / ns(0.25))
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
