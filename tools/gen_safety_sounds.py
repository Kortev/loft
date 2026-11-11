"""Synthesises Public Safety's sounds (chainsawman/, the safety/ lane), with the building blocks of gen_sounds.py and
gen_chainsaw_sounds.py (NumPy, SciPy, ffmpeg). All mono, all synthesised.

- paper: a sheet pulled off a board, a crisp rustle and a snap;
- ring: the green public phone: the handset lifted off its hook (a plastic clack), the ringing at the other end (Japan's
  ringback, a 400 Hz tone warbling at 16 Hz, twice), a click as someone answers, and a voice murmuring through the line (one
  formant-filtered voice, no words, squeezed into a telephone's band);
- cash: paid: an envelope slid out (paper), a handful of coins falling on each other, and a small bright bell;
- tick: one click of the yen counting up;
- dial: nothing to report: the handset up, a plain 400 Hz dial tone, the handset down.

Usage: py tools/gen_safety_sounds.py [--wav DIR] [name ...]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_chainsaw_sounds as cs  # noqa: E402  (sets the output folder to chainsawman's sounds)
import gen_sounds as g  # noqa: E402
from gen_sounds import SR, bp, hp, limit, loudness, lp, mono, ns, white  # noqa: E402

place = cs.place


def clack(level=1.0, f=1800.0):
    """A hard plastic knock: a short noise burst through a body resonance."""
    m = ns(0.05)
    t = np.arange(m) / SR
    x = bp(white(m), f * 0.6, f * 1.6) * np.exp(-t / 0.006) + np.sin(2 * np.pi * 320.0 * t) * np.exp(-t / 0.012) * 0.6
    return x * level


def phone_band(x):
    return lp(hp(x, 320.0), 3200.0)


def tone(seconds, f, warble=0.0, level=1.0):
    n = ns(seconds)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * f * t)
    if warble:
        x *= 0.6 + 0.4 * np.sin(2 * np.pi * warble * t)
    env = np.minimum(t / 0.01, 1.0) * np.minimum((seconds - t) / 0.01, 1.0)
    return x * env * level


def safety_paper():
    n = ns(0.55)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for j in range(9):
        at = g.rng.uniform(0.0, 0.32)
        m = ns(g.rng.uniform(0.03, 0.09))
        crinkle = bp(white(m), 2500.0, 9000.0) * np.exp(-np.arange(m) / m * 3.0) * g.rng.uniform(0.3, 0.8)
        x = place(x, at, crinkle)
    # The tear's snap.
    x = place(x, 0.34, hp(white(ns(0.04)), 1500.0) * np.exp(-np.arange(ns(0.04)) / SR / 0.008) * 1.4)
    return x * np.minimum(t / 0.01, 1.0)


def safety_ring():
    seconds = 2.8
    n = ns(seconds)
    x = np.zeros(n)
    x = place(x, 0.0, clack(1.0, 1500.0))
    x = place(x, 0.06, clack(0.6, 2200.0))
    # Ringback: one second's burr, and the answer.
    x = place(x, 0.25, phone_band(tone(1.0, 400.0, 16.0, 0.35)))
    x = place(x, 1.38, clack(0.5, 2600.0))
    # The voice: one person, low, through the line (the yen is paid as it stops: PaydayHud.PAID).
    voice = cs.crowd_voice(1.2, voices=1, f0=(120.0, 140.0), level=1.0, breath=0.1)
    voice = phone_band(voice) * 1.8
    x = place(x, 1.5, voice)
    x += phone_band(white(n)) * 0.012
    return x


def safety_cash():
    seconds = 1.6
    n = ns(seconds)
    x = np.zeros(n)
    # The envelope sliding out.
    m = ns(0.25)
    slide = bp(white(m), 1200.0, 6000.0) * np.sin(np.pi * np.arange(m) / m) * 0.5
    x = place(x, 0.0, slide)
    # Coins: small metallic pings landing on each other.
    for j in range(14):
        at = 0.18 + g.rng.uniform(0.0, 0.45) * (j / 14.0) ** 0.7
        f = g.rng.uniform(3200.0, 6200.0)
        m = ns(0.18)
        tt = np.arange(m) / SR
        ping = (np.sin(2 * np.pi * f * tt) + 0.5 * np.sin(2 * np.pi * f * 1.47 * tt)) * np.exp(-tt / g.rng.uniform(0.02, 0.06))
        x = place(x, at, ping * g.rng.uniform(0.15, 0.4))
        x = place(x, at, clack(0.15, 4000.0))
    # The bell.
    x = place(x, 0.62, g.chime(2093.0, ns(0.9), tau=0.35) * 0.7)
    x = place(x, 0.62, g.chime(2637.0, ns(0.9), tau=0.3) * 0.4)
    return x


def safety_tick():
    m = ns(0.06)
    t = np.arange(m) / SR
    return place(np.sin(2 * np.pi * 2400.0 * t) * np.exp(-t / 0.01), 0.0, clack(0.4, 3000.0)) * 0.8


def safety_dial():
    seconds = 1.5
    n = ns(seconds)
    x = np.zeros(n)
    x = place(x, 0.0, clack(1.0, 1500.0))
    x = place(x, 0.15, phone_band(tone(1.05, 400.0, 0.0, 0.3)))
    x = place(x, 1.25, clack(1.0, 1300.0))
    x = place(x, 1.28, clack(0.7, 1900.0))
    return x


SOUNDS = {
    'safety_paper': safety_paper,
    'safety_ring': safety_ring,
    'safety_cash': safety_cash,
    'safety_tick': safety_tick,
    'safety_dial': safety_dial,
}
LEVELS = {'safety_paper': -15, 'safety_ring': -16, 'safety_cash': -12, 'safety_tick': -20, 'safety_dial': -18}


def make(name):
    g.rng = cs.rng_for(name)
    x = mono(SOUNDS[name]())
    x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
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
