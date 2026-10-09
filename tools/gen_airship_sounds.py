"""Synthesises Baron Bomburst's airship's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All of them are mono, to be placed in the world at the airship. The running sounds are loops whose every part repeats
exactly over the loop, so they go round without a seam while the game bends their pitch (AirshipSound).

Each is modelled on its source:
- the engine: a slow two-cylinder engine in the stern of the gondola putting along, each firing a soft thump through
  its exhaust, the belts slapping over their pulleys and the two propellers beating the air, each a little out of step
  with the other;
- the wind: air streaming past the envelope and singing in the rigging wires;
- the creak: the great envelope working against its rigging, the cords and the frame stick-slipping and groaning;
- the winch: the grapple's drum turning on its ratchet, pawl clicking;
- the grab: the iron grapple's tines biting, a clank and its rope snapping taut;
- the ladder: the rope ladder unrolling over the side, its wooden rungs knocking;
- the bomb: the rack's catch letting go and the bomb whistling down;
- the load: a bomb set into the rack.

Usage: python3 tools/gen_airship_sounds.py [--wav DIR] [name ...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from gen_sounds import (SR, attack_decay, bp, curve, hp, limit, loudness, lp, master, mono, ns, pink,  # noqa: E402
                        reverb, sat, spaces, sweep_filter, white)

# The airship is part of the Chitty mod: her sounds go into its jar's assets.
g.OUT = os.path.join(os.path.dirname(__file__), '..', 'chitty', 'src', 'main', 'resources', 'assets', 'shootingstar', 'sounds')

ENGINE_LOOP = 4 * SR
WIND_LOOP = 6 * SR
# Firings and propeller beats per loop: whole numbers, so the loop joins up.
FIRINGS = 36            # 9 a second: a two-cylinder engine at about 540 rpm
PROP_BEATS = (40, 43)   # two two-bladed propellers at about 300 rpm, not quite together


def rng_for(name):
    return np.random.default_rng(zlib.crc32(('airship:' + name).encode()))


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


def ring(freqs, n, taus, gains):
    t = np.arange(n) / SR
    return sum(g_ * np.sin(2 * np.pi * f * t) * np.exp(-t / tau) for f, tau, g_ in zip(freqs, taus, gains))


# --- the loops -----------------------------------------------------------------------------------------

def airship_engine():
    rng = rng_for('engine')
    n = ENGINE_LOOP
    buf = np.zeros(n)
    tt = np.arange(n) / SR
    # The firings: a soft low thump each, the two cylinders a little unlike.
    k = ns(0.09)
    t = np.arange(k) / SR
    for i in range(FIRINGS):
        at = int(i * n / FIRINGS + rng.normal(0, ns(0.004)))
        strength = (1.0 if i % 2 == 0 else 0.8) * rng.uniform(0.85, 1.1)
        thump = (np.sin(2 * np.pi * 62 * t) * 0.8 + np.sin(2 * np.pi * 118 * t) * 0.35) * np.exp(-t / 0.03)
        thump += lp(white(k), 600) * np.exp(-t / 0.012) * 0.5
        place(buf, at, thump * strength)
    buf = periodic(buf, lambda x: lp(x, 900, 2))
    # The propellers beating the air: whooshes, each beat a swell of filtered noise.
    air = loop_noise(n, pink, lambda x: bp(x, 180, 1400))
    beats = sum(0.5 + 0.5 * np.cos(2 * np.pi * b * tt / (n / SR) + ph) ** 2 for b, ph in zip(PROP_BEATS, (0.0, 1.1)))
    props = air * beats * 0.35
    # The belts slapping over the pulleys, and a light mechanical chatter.
    slap = loop_noise(n, white, lambda x: bp(x, 900, 3500))
    slap *= (0.6 + 0.4 * np.sin(2 * np.pi * 18 * tt / (n / SR)) ** 8) * 0.06
    y = sat(buf * 1.2, 1.4) + props + slap
    return outdoors_loop(y, 0.15)


def outdoors_loop(y, wet):
    """A loop through the outdoor reverb as if it went on for ever."""
    outdoor = spaces()[0]
    return periodic(y, lambda x: mono(reverb(x, outdoor, wet=wet)))


def airship_wind():
    n = WIND_LOOP
    tt = np.arange(n) / SR
    rush = loop_noise(n, pink, lambda x: bp(x, 120, 900))
    hiss = loop_noise(n, white, lambda x: bp(x, 2000, 6000))
    gust = (1.0 + 0.3 * np.sin(2 * np.pi * 2 * tt / (n / SR)) + 0.15 * np.sin(2 * np.pi * 5 * tt / (n / SR) + 0.7))
    # The rigging wires singing: narrow tones that come and go with the gusts.
    sing = sum(np.sin(2 * np.pi * f * tt + 3 * np.sin(2 * np.pi * w * tt / (n / SR))) * a
               for f, w, a in ((640, 3, 0.05), (955, 4, 0.035), (1310, 7, 0.02)))
    sing *= np.clip(gust - 1.0, 0.0, None) * 3.0
    return (rush * 0.9 + hiss * 0.1) * gust + sing


# --- one-shots ------------------------------------------------------------------------------------------

def airship_creak():
    """Stick-slip: the cords and frame catching and letting go faster and slower, through wood's and rope's
    resonances, with a soft flap of the envelope's cloth under it."""
    rng = rng_for('creak')
    n = ns(1.8)
    m = np.zeros(n)
    rate = curve(n, [(0, 90), (0.5, 210), (1.0, 140), (1.5, 70), (1.8, 60)], 'linear')
    phase = np.cumsum(rate) / SR
    ticks = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    for at in ticks:
        k = ns(0.01)
        m[at:at + k] += hp(white(k), 200)[:len(m[at:at + k])] * rng.uniform(0.4, 1.0)
    body = sum(bp(m, f * 0.92, f * 1.08) * gain for f, gain in ((310, 1.0), (660, 0.7), (1240, 0.35)))
    env = attack_decay(n, 0.15, 0.9)
    k = ns(0.6)
    flap = lp(white(k), 160) * attack_decay(k, 0.05, 0.25) * 0.8
    y = body * env
    y[ns(0.3):ns(0.3) + k] += flap
    return master(outdoors(y, 0.25), peak=0.95)


def airship_winch():
    """The drum turning a few notches on its ratchet: the pawl clicking over the teeth, the gears grumbling."""
    rng = rng_for('winch')
    n = ns(0.55)
    m = np.zeros(n)
    for i in range(9):
        at = ns(0.02 + i * 0.055) + int(rng.normal(0, ns(0.002)))
        k = ns(0.04)
        click = ring((2300, 3900, 5600), k, (0.012, 0.008, 0.005), (0.6, 0.4, 0.2)) + hp(white(k), 3000) * np.exp(-np.arange(k) / SR / 0.003)
        m[at:at + k] += click * rng.uniform(0.7, 1.0)
    tt = np.arange(n) / SR
    whine = bp(white(n), 300, 700) * 0.2 * attack_decay(n, 0.05, 0.4)
    gear = np.sin(2 * np.pi * 145 * tt) * 0.08 * attack_decay(n, 0.04, 0.35)
    return master(outdoors(m + whine + gear, 0.15), peak=0.95)


def airship_grab():
    """The tines biting: an iron clank, ringing, and the rope snapping taut."""
    n = ns(1.2)
    t = np.arange(n) / SR
    clank = ring((540, 1270, 2210, 3480), n, (0.35, 0.22, 0.12, 0.07), (0.7, 0.5, 0.35, 0.2))
    hit = hp(white(ns(0.03)), 1200) * np.exp(-np.arange(ns(0.03)) / SR / 0.006)
    m = clank * 0.8
    m[:len(hit)] += hit
    k = ns(0.25)
    snap = sweep_filter(white(k), 'bandpass', curve(k, [(0, 1800), (0.25, 600)], 'log'), width=0.6) * attack_decay(k, 0.004, 0.06)
    m[ns(0.12):ns(0.12) + k] += snap * 0.7
    return master(outdoors(m * np.exp(-t / 0.8), 0.25), peak=0.97)


def airship_ladder():
    """The rope ladder unrolling over the side: the ropes hissing out, the rungs knocking against each other and the hull."""
    rng = rng_for('ladder')
    n = ns(1.6)
    m = np.zeros(n)
    rustle = bp(white(n), 500, 3200) * curve(n, [(0, 0.0), (0.1, 0.5), (1.1, 0.4), (1.6, 0.0)], 'linear') * 0.3
    m += rustle
    s = 0.08
    while s < 1.35:
        k = ns(0.06)
        knock = ring((380 * rng.uniform(0.9, 1.15), 870 * rng.uniform(0.9, 1.1)), k, (0.03, 0.015), (0.8, 0.4))
        m[ns(s):ns(s) + k] += knock * rng.uniform(0.4, 0.9)
        s += rng.uniform(0.06, 0.16)
    return master(outdoors(m, 0.2), peak=0.95)


def airship_bomb_drop():
    """The rack's catch letting go, then the bomb's whistle falling away."""
    n = ns(2.0)
    t = np.arange(n) / SR
    m = np.zeros(n)
    k = ns(0.06)
    m[:k] += ring((1500, 2600), k, (0.02, 0.012), (0.7, 0.4)) + hp(white(k), 2000) * np.exp(-np.arange(k) / SR / 0.005) * 0.5
    freq = curve(n, [(0, 1700), (2.0, 650)], 'log')
    phase = np.cumsum(freq) / SR * 2 * np.pi
    whistle = np.sin(phase) * curve(n, [(0, 0.0), (0.15, 0.5), (1.6, 0.4), (2.0, 0.0)], 'linear')
    breath = bp(white(n), 900, 2600) * 0.08 * curve(n, [(0, 0.0), (0.2, 1.0), (2.0, 0.3)], 'linear')
    m += whistle * 0.5 + breath
    return master(outdoors(m, 0.2), peak=0.95)


def airship_load():
    """A bomb set down into the rack: a dull iron clunk and the catch clicking shut."""
    n = ns(0.5)
    m = ring((180, 420, 980), n, (0.08, 0.05, 0.03), (0.8, 0.5, 0.25))
    k = ns(0.03)
    m[ns(0.12):ns(0.12) + k] += ring((2400, 4100), k, (0.01, 0.006), (0.5, 0.3))
    return master(outdoors(m, 0.12), peak=0.95)


SOUNDS = {
    'airship_engine': airship_engine,
    'airship_wind': airship_wind,
    'airship_creak': airship_creak,
    'airship_winch': airship_winch,
    'airship_grab': airship_grab,
    'airship_ladder': airship_ladder,
    'airship_bomb_drop': airship_bomb_drop,
    'airship_load': airship_load,
}
LOOPS = ('airship_engine', 'airship_wind')
# Loudness (loudest 400 ms, dB).
LEVELS = {'airship_engine': -18, 'airship_wind': -20, 'airship_creak': -17, 'airship_winch': -17, 'airship_grab': -13,
          'airship_ladder': -16, 'airship_bomb_drop': -14, 'airship_load': -16}


# How long each one-shot runs at most (seconds): the outdoor reverb's long tail is cut short and faded, so that the
# winch, clicking over and over as the grapple goes down, does not pile up its echoes.
LENGTHS = {'airship_creak': 2.6, 'airship_winch': 1.0, 'airship_grab': 2.2, 'airship_ladder': 2.4, 'airship_bomb_drop': 2.6,
           'airship_load': 0.9}


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
