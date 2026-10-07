"""Synthesises Chitty Chitty Bang Bang's sounds, with the building blocks of gen_sounds.py (NumPy, SciPy, ffmpeg).

All of them are mono, to be placed in the world at the car. The running sounds are loops: everything in them repeats
exactly over the loop (firings wrap round its end, filters run as if the loop went on for ever), so they go round
without a seam while the game bends their pitch.

The engine is modelled rather than imitated: a big old four-cylinder whose every firing is a pressure pulse and a rush
of hot gas out of its cylinder, through that cylinder's own length of header into a long flexible pipe that booms at
its own pitches, with the valves ticking, the chassis rattling and the carburettor breathing on top, heard outdoors
(off the ground and whatever is about). It fires unevenly, in pairs, a hard firing and a softer one hard on its heels
and then a gap, so it runs chit-ty chit-ty chit-ty: the noise she is named for. Nothing in it is quite regular: every
firing comes a little early or late, stronger or weaker, now and then one barely catches. It comes in three loops,
ticking over, pulling at middling revs and working hard, which the game crossfades by revs instead of stretching one
sound over the whole range.

The car is named for the noise she makes starting: two sputtering coughs (chitty, chitty) and two backfires (bang,
bang). The start-up is that over the whirr of the starter, and a backfire the same in miniature. Their bangs land where
ChittyEntity puffs smoke out of the exhaust (START_BANG_1 and START_BANG_2 ticks into the start-up, and five and ten
ticks into a backfire).

Usage: python3 tools/gen_chitty_sounds.py [--wav DIR] [name ...]
"""
import os
import sys
import zlib

import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
from gen_sounds import (SR, attack_decay, bp, brown, curve, hp, limit, loudness, lp, master, mono,  # noqa: E402
                        ns, pink, reverb, sat, spaces, sweep_filter, white)

# Each loop is a whole number of engine cycles (two turns of the crank), so its firings repeat exactly. The game plays
# them at these revs (ChittySound.IDLE_RPM, LOW_RPM, HIGH_RPM) and bends their pitch in between.
CYLINDERS = 4
# When each cylinder fires in the cycle, and how hard: in pairs, a stressed firing and a weaker one close behind it
# (chit-ty), twice a cycle.
FIRING = [(0.00, 1.00), (0.17, 0.58), (0.50, 0.95), (0.67, 0.55)]
ENGINE_LOOPS = {
    # name: (samples per cycle, cycles, load)
    'chitty_engine_idle': (12027, 12, 0.35),   # 440 rpm, off load: soft, lumpy, every firing heard
    'chitty_engine_low': (4811, 26, 0.75),     # 1100 rpm, pulling
    'chitty_engine_high': (2405, 52, 1.0),     # 2200 rpm, working hard
}
# The propellers on the wing masts: two, a little out of step, beating once a second over a two second loop.
FLIGHT_LOOP = 2 * SR
WIND_LOOP = 6 * SR
ROTOR_PASSES = (40, 42)


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

def tick(rng):
    """A valve or tappet clicking."""
    n = ns(0.012)
    t = np.arange(n) / SR
    return hp(white(n), 3000) * np.exp(-t / 0.0012) * rng.uniform(0.6, 1.0)


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

def waveguide(x, delay, gain, cutoff=800.0):
    """A pipe: what goes in comes back after `delay` samples (there and back), scaled by `gain` (negative off an open
    end) and duller each time round (a one-pole low-pass at `cutoff` in the loop), so it booms low but does not ring
    high."""
    d = int(delay)
    k = np.exp(-2 * np.pi * cutoff / SR)
    a = np.zeros(d + 1)
    a[0], a[1] = 1.0, -k
    a[d] -= gain * (1 - k)
    return signal.lfilter([1.0, -k], a, x)


def firing(rng, strength, load):
    """One cylinder letting go into its exhaust: a sharp pressure pulse and the suck after it, a rush of hot gas, and the
    knock of it through the block."""
    n = ns(0.06)
    t = np.arange(n) / SR
    # The valve cracks open: a steep front, then the pressure bleeding away over a few milliseconds.
    rise, fall = 0.0004, 0.0016 + 0.0012 * (1 - load)
    pulse = (1 - np.exp(-t / rise)) * np.exp(-t / fall)
    pulse /= pulse.max()
    tt = (t - 0.004).clip(0)
    suck = (tt / 0.004) * np.exp(1 - tt / 0.004) * (t > 0.004)
    gas = bp(white(n), 400, 3800) * np.exp(-t / (0.004 + 0.004 * load))
    knock = (np.sin(2 * np.pi * rng.uniform(270, 300) * t) * 0.5 + np.sin(2 * np.pi * rng.uniform(510, 560) * t) * 0.35
             + np.sin(2 * np.pi * rng.uniform(880, 960) * t) * 0.2) * np.exp(-t / 0.012)
    return (pulse - 0.45 * suck + gas * (0.18 + 0.25 * load) + knock * 0.06) * strength


def delayed(x, seconds):
    d = ns(seconds)
    return np.concatenate([np.zeros(d), x[:len(x) - d]])


def heard_outdoors(x):
    """As a microphone a few metres off hears it: straight, off the ground just after, and fainter and duller off what
    is about, with the air taking a little of the top."""
    y = x + 0.45 * lp(delayed(x, 0.0035), 3500, 2)
    for at, gain, cut in ((0.019, 0.20, 2200), (0.037, 0.13, 1600), (0.061, 0.09, 1200), (0.094, 0.06, 900)):
        y = y + gain * lp(delayed(x, at), cut, 2)
    return lp(y, 9000, 2)


def puff(rng, strength, load):
    """The hot gas behind a firing rushing down the pipe: noise, not a tone, and every one different."""
    n = ns(0.07)
    t = np.arange(n) / SR
    env = (1 - np.exp(-t / 0.0015)) * np.exp(-t / (0.012 + 0.02 * load * rng.uniform(0.7, 1.3)))
    return lp(white(n), rng.uniform(1300, 2200), 2) * env * strength


def spit(rng, stressed, strength, load):
    """What comes out of the end of the pipe: on a hard firing a spitting "ch" of gas, on the soft one after it a
    crisper "t". Both carry a little of the pipe's bright ring, which reads as "i"."""
    n = ns(0.05)
    t = np.arange(n) / SR
    if stressed:
        x = bp(white(n), 2200, 7500) * (1 - np.exp(-t / 0.001)) * np.exp(-t / rng.uniform(0.010, 0.016)) * 0.55
    else:
        x = hp(white(n), 3200, 2) * np.exp(-t / rng.uniform(0.0015, 0.0025)) * 0.55
    ring = bp(white(n), 2100, 2700, 2) * np.exp(-t / 0.01) * 0.35
    return (x + ring) * strength * (0.6 + 0.4 * (1 - load))


def rattle(rng):
    """Something on the chassis shaken against something else: a few quick ticks."""
    n = ns(0.03)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for _ in range(rng.integers(1, 4)):
        i = rng.integers(0, n - ns(0.006))
        k = ns(0.006)
        x[i:i + k] += bp(white(k), rng.uniform(1200, 2000), rng.uniform(3000, 5000)) * np.exp(-np.arange(k) / SR / 0.0012)
    return x * rng.uniform(0.3, 1.0)


def engine(cycle, cycles, load, seed):
    """`cycles` turns of a four-cylinder engine, `cycle` samples each, at `load` (0 coasting to 1 flat out)."""
    rng = rng_for('engine:%d' % seed)
    n = cycle * cycles
    gap = cycle / CYLINDERS
    headers = [118, 131, 142, 156]                 # samples there and back: 0.6 to 0.8 m of pipe at 450 m/s
    pipe = int(SR * 2 * 3.3 / 450)                 # 3.3 m of flexible pipe down the side
    wobble = 0.05 * (1 - load) + 0.015
    # Working hard the pairs even out a little, though the lope never goes.
    contrast = 1.0 - 0.45 * load
    per_cyl = [np.zeros(n) for _ in range(CYLINDERS)]
    spits = np.zeros(n)
    shakes = np.zeros(n)
    for c in range(cycles):
        for k, (when, hard) in enumerate(FIRING):
            at = int(c * cycle + when * cycle + rng.normal(0, wobble * gap * 0.25))
            strength = (1.0 - (1.0 - hard) * contrast) * rng.uniform(0.8, 1.15) * (0.55 + 0.45 * load)
            # Ticking over, a cylinder now and then barely fires.
            if load < 0.5 and rng.random() < 0.06:
                strength *= 0.3
            place(per_cyl[k], at, firing(rng, strength, load))
            place(per_cyl[k], at, puff(rng, strength * (0.5 + 0.4 * load), load))
            place(spits, at + pipe // 2 + headers[k] // 2, spit(rng, hard > 0.8, strength, load))
            if rng.random() < 0.35:
                place(shakes, at + ns(rng.uniform(0.004, 0.02)), rattle(rng) * strength)
    out = np.zeros(n)
    for k in range(CYLINDERS):
        out += periodic(per_cyl[k], lambda x, d=headers[k]: waveguide(x, d, -0.45, 2400.0))
    # The long pipe, open at its end, booms at odd multiples of a quarter wave (about 34, 102, 170 Hz); being flexible
    # and corrugated it is not one clean length, so its ring is smeared rather than a pure tone.
    out = (periodic(out, lambda x: waveguide(x, pipe, -0.5, 600.0))
           + 0.45 * periodic(out, lambda x: waveguide(x, int(pipe * 1.09), -0.42, 480.0))
           + 0.25 * periodic(out, lambda x: waveguide(x, int(pipe * 0.93), -0.4, 520.0)))
    # What comes out of the end of a pipe loses its lowest lows and its fizz.
    out = periodic(out, lambda x: lp(hp(x, 40, 1), 4800, 2))
    out = out / (np.abs(out).max() + 1e-9)
    # The block and the valves: a click for each valve closing, ringing in the tappet cover.
    valves = np.zeros(n)
    for c in range(cycles):
        for k, (when, hard) in enumerate(FIRING):
            for off in (0.30, 0.62):
                m = ns(0.01)
                tt = np.arange(m) / SR
                click = hp(white(m), 2500) * np.exp(-tt / 0.0009) * rng.uniform(0.4, 1.0)
                place(valves, int(c * cycle + (when + off * 0.25) * cycle + rng.normal(0, 20)), click)
    valves = periodic(valves, lambda x: x + 0.6 * bp(x, 3100, 4200, 2))
    # The carburettor gulping air, pulsing with the firings; the whole car humming and shaking underneath.
    tt = np.arange(n) / SR
    beat = 0.5 + 0.5 * np.cos(2 * np.pi * tt * SR / gap)
    intake = loop_noise(n, white, lambda x: bp(x, 300, 1600)) * beat ** 2 * (0.05 + 0.12 * load)
    rumble = loop_noise(n, brown, lambda x: lp(x, 120)) * 0.06
    spits /= np.abs(spits).max() + 1e-9
    shakes /= np.abs(shakes).max() + 1e-9
    y = (out + spits * (0.22 - 0.08 * load) + shakes * 0.05 + valves * (0.05 - 0.025 * load) + intake * 0.5
         + rumble * 0.3)
    y = periodic(y, heard_outdoors)
    return sat(y * (1.0 + 0.3 * load), 1.1 + 0.5 * load)


def chitty_engine_idle():
    return engine(*ENGINE_LOOPS['chitty_engine_idle'], seed=1)


def chitty_engine_low():
    return engine(*ENGINE_LOOPS['chitty_engine_low'], seed=2)


def chitty_engine_high():
    return engine(*ENGINE_LOOPS['chitty_engine_high'], seed=3)


def chitty_flight():
    """In the air: the two propellers on the wing masts chopping round flat overhead, a little out of step with each
    other, and the wind over the wings and through the wires."""
    rng = rng_for('flight')
    n = FLIGHT_LOOP
    chop = np.zeros(n)
    for r, passes in enumerate(ROTOR_PASSES):
        gap = n / passes
        for k in range(passes):
            m = ns(0.05)
            tt = np.arange(m) / SR
            # A blade going by: a soft thump of displaced air and a swish.
            thump = np.sin(2 * np.pi * (70 + 15 * r) * tt) * np.sin(np.pi * tt / tt[-1]) ** 3
            swish = bp(white(m), 500, 2600) * np.sin(np.pi * tt / tt[-1]) ** 2
            place(chop, int(k * gap), (thump * 0.9 + swish * 0.45) * rng.uniform(0.85, 1.05))
    chop = periodic(chop, lambda x: lp(x, 2400, 2))
    tt = np.arange(n) / SR
    wind = loop_noise(n, pink, lambda x: bp(x, 180, 2400))
    wind *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.5 * tt) + 0.08 * np.sin(2 * np.pi * 2.0 * tt + 1.0)
    whistle = loop_noise(n, white, lambda x: bp(x, 1500, 1700)) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.0 * tt))
    y = chop * 1.0 + wind * 0.5 + whistle * 0.12
    return sat(y * 1.2, 1.3)


def chitty_wind():
    """The rush of air past her as she flies (or falls) fast, which the game swells with her speed: a deep roar of
    buffeting air under a hiss, gusting slowly, a long loop so the gusts do not repeat noticeably."""
    n = WIND_LOOP
    tt = np.arange(n) / SR
    roar = loop_noise(n, pink, lambda x: bp(x, 60, 500))
    rush = loop_noise(n, pink, lambda x: bp(x, 400, 3000))
    hiss = loop_noise(n, white, lambda x: bp(x, 3000, 8000))
    # Gusts: slow swells at whole numbers of cycles per loop, so the loop joins up.
    gust = (1.0 + 0.30 * np.sin(2 * np.pi * 2 * tt / (n / SR)) + 0.18 * np.sin(2 * np.pi * 5 * tt / (n / SR) + 1.3)
            + 0.10 * np.sin(2 * np.pi * 11 * tt / (n / SR) + 0.4))
    buffet = loop_noise(n, white, lambda x: lp(x, 6, 2))
    buffet = np.maximum(0.3, 1.0 + 0.25 * buffet / (np.std(buffet) + 1e-12))
    y = (roar * 1.0 * buffet + rush * 0.55 + hiss * 0.12) * gust
    return sat(y, 1.2)


def bang(rng, size=1.0):
    """A backfire: fuel going off in the pipe. A hard pressure wave out of the end, the pipe ringing with it, a crack
    and a puff of flame."""
    n = ns(0.5)
    t = np.arange(n) / SR
    # The wave: a steep front and a long shallow suck after it (an N-wave), wide enough to thump.
    w = 0.0035 * size
    wave = np.where(t < w, 1.0 - t / w * 1.6, 0.0) * (t >= 0) + np.where((t >= w) & (t < 3 * w), -0.6 * (1 - (t - w) / (2 * w)), 0.0)
    crack = hp(white(n), 1800) * np.exp(-t / 0.0025)
    flame = bp(white(n), 250, 2200) * attack_decay(n, 0.002, 0.045)
    x = wave * 1.6 + crack * 0.5 + flame * 0.45
    pipe = int(SR * 2 * 3.3 / 450)
    x = waveguide(x, pipe, -0.6, 900.0) + 0.5 * waveguide(x, 140, -0.5, 2500.0)
    x = lp(x, 7000, 2)
    return sat(x * 2.2, 2.6)


def cough(rng, strength=1.0, bright=1.0):
    """A sputter: a cylinder catching weakly as the engine turns over, a puff and a spit out of the pipe."""
    n = ns(0.12)
    t = np.arange(n) / SR
    x = np.zeros(n)
    f = firing(rng, 1.0, 0.2)
    x[:len(f)] = f
    spit = bp(white(n), 900 * bright, 5000) * attack_decay(n, 0.001, 0.02)
    breath = bp(white(n), 250, 1400) * attack_decay(n, 0.004, 0.05)
    x = x * 1.4 + spit * 0.7 + breath * 0.6
    pipe = int(SR * 2 * 3.3 / 450)
    return waveguide(x, pipe, -0.55, 900.0) * strength


def starter(n, until):
    """The starter motor whirring the engine over: a whine dipping on each compression stroke."""
    t = np.arange(n) / SR
    env = np.clip(t / 0.03, 0, 1) * np.clip((until - t) / 0.06, 0, 1)
    rpm = 170 + 50 * np.clip(t / until, 0, 1)
    strokes = rpm / 60 * CYLINDERS / 2
    phase = np.cumsum(strokes) / SR
    load = 0.5 + 0.5 * np.cos(2 * np.pi * phase)
    f = (300 + 120 * np.clip(t / until, 0, 1)) * (1 - 0.12 * load)
    whine = sum(np.sin(2 * np.pi * np.cumsum(f * h) / SR) / h ** 1.2 for h in (1, 2, 3, 4, 6))
    gears = bp(white(n), 1800, 5000) * (0.4 + 0.6 * load)
    chuff = bp(white(n), 150, 900) * load ** 3
    return (whine * 0.35 * (1 - 0.4 * load) + gears * 0.12 + chuff * 0.5) * env


def chitty_start():
    """Chit-ty, chit-ty over the whirr of the starter; bang, bang; and she catches, stumbles and ticks over."""
    rng = rng_for('start')
    total = 3.2
    n = ns(total)
    m = np.zeros(n)

    def add(x, at, gain=1.0):
        i = ns(at)
        k = min(len(x), n - i)
        m[i:i + k] += x[:k] * gain

    m += starter(n, 0.68) * 0.7
    for at, s, b in ((0.06, 1.0, 1.0), (0.17, 0.6, 0.8), (0.36, 1.0, 1.0), (0.47, 0.65, 0.8)):
        add(cough(rng, s, b), at, 0.7)
    # The bangs are what she is named for: well above everything else.
    add(bang(rng, 1.0), 0.75, 2.4)
    add(bang(rng, 1.12), 1.0, 2.4)
    # She catches: the idle engine fading in under the bangs' echoes, stumbling at first.
    cycle, cycles, load = ENGINE_LOOPS['chitty_engine_idle']
    idle = engine(cycle, cycles, 0.45, seed=7)
    k = n - ns(1.08)
    idle = np.tile(idle, 2)[:k]
    tt = np.arange(k) / SR
    idle *= np.clip(tt / 0.25, 0, 1) * (1.0 - 0.45 * np.clip((tt - 1.2) / 0.9, 0, 1))
    add(idle, 1.08, 0.4)
    return master(outdoors(m, 0.3), peak=0.98, drive=1.2)


def chitty_bang():
    """A backfire when the throttle comes off: a stumble, then bang, bang."""
    rng = rng_for('bang')
    total = 1.4
    n = ns(total)
    m = np.zeros(n)
    for at, s in ((0.02, 0.7), (0.11, 0.9), (0.18, 0.6)):
        c = cough(rng, s, 1.2)
        k = min(len(c), n - ns(at))
        m[ns(at):ns(at) + k] += c[:k]
    for at, size in ((0.25, 1.0), (0.50, 1.15)):
        b = bang(rng, size)
        k = min(len(b), n - ns(at))
        m[ns(at):ns(at) + k] += b[:k] * 1.1
    return master(outdoors(m, 0.35), peak=0.98, drive=1.3)


def honk(f0, dur, rng):
    """One squeeze of a bulb horn: a brass reed slamming shut many times a second into a little horn. The pitch jumps
    up as the air arrives and sags as the bulb empties; the reed rattles at the edges."""
    k = ns(dur)
    t = np.arange(k) / SR
    f = f0 * (0.86 + 0.14 * (1 - np.exp(-t / 0.018))) * (1.0 - 0.06 * (t / dur) ** 2)
    f *= 1 + 0.006 * np.sin(2 * np.pi * 6.5 * t) + 0.004 * rng.standard_normal(k).cumsum() / np.sqrt(np.arange(1, k + 1))
    phase = np.cumsum(f) / SR % 1.0
    # The reed's air-flow: open most of the cycle, slammed shut for a moment, so rich in odd and even harmonics.
    flow = np.clip(np.sin(np.pi * np.clip(phase / 0.72, 0, 1)), 0, None) ** 1.5
    flow -= flow.mean()
    # The horn: a few resonances and a bright mouth.
    voice = (bp(flow, 520, 760, 2) * 1.3 + bp(flow, 1150, 1500, 2) * 1.0 + bp(flow, 2300, 3000, 2) * 0.55
             + bp(flow, 3600, 4600, 2) * 0.25 + flow * 0.25)
    rattle = bp(white(k), 2500, 7000) * (0.5 + 0.5 * np.sin(2 * np.pi * phase)) * 0.06
    env = np.clip(t / 0.015, 0, 1) ** 2 * np.clip((dur - t) / 0.035, 0, 1)
    return sat((voice + rattle) * env * 2.0, 2.2)


def chitty_horn():
    """Squeezing the serpent's bulb twice: parp, parp, and the rubber wheezing as it fills again."""
    rng = rng_for('horn')
    total = 1.2
    n = ns(total)
    m = np.zeros(n)
    for at, f0, dur in ((0.0, 392.0, 0.30), (0.40, 382.0, 0.40)):
        h = honk(f0, dur, rng)
        m[ns(at):ns(at) + len(h)] += h
    k = ns(0.25)
    wheeze = bp(white(k), 900, 3000) * attack_decay(k, 0.05, 0.08) * 0.12
    m[ns(0.88):ns(0.88) + k] += wheeze
    return master(outdoors(m, 0.15), peak=0.98)


def creak(rng, dur, f0=180.0, f1=320.0):
    """Wood and leather working against each other: a dry, stuttering squeal."""
    k = ns(dur)
    t = np.arange(k) / SR
    f = curve(k, [(0, f0), (dur, f1)], 'log') * (1 + 0.04 * np.sin(2 * np.pi * rng.uniform(6, 11) * t))
    phase = np.cumsum(f) / SR % 1.0
    pulses = np.clip(np.sin(2 * np.pi * phase), 0, None) ** 8
    stick = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * rng.uniform(13, 21) * t + rng.uniform(0, 6)))) * 0.7 + 0.3
    return bp(pulses * stick, 600, 3800, 2) * attack_decay(k, 0.05, dur * 0.6)


def chitty_wings_out():
    """The wings coming out, slowly and in stages, to match the game (2.4 s): the ratchet winds and the folded wings
    swing out from under the running boards with metal sliding and wood creaking; then the fans open, each panel's
    canvas snapping taut in turn; a clunk as they lock; the masts winding up and a spring settling."""
    rng = rng_for('wings_out')
    total = 2.9
    n = ns(total)
    m = ratchet(rng, n, 0.0, 1.0, 9, 26, 0.75)
    k = ns(1.0)
    slide = sweep_filter(white(k), 'bandpass', curve(k, [(0, 700), (1.0, 2200)], 'log'), width=1.0)
    m[ns(0.05):ns(0.05) + k] += slide * attack_decay(k, 0.2, 0.5) * 0.4
    c = creak(rng, 0.7, 170, 260)
    m[ns(0.3):ns(0.3) + len(c)] += c * 0.35
    for i in range(8):
        sn = snap(rng, 0.75 + 0.25 * (i % 2))
        at = ns(1.45 + i * 0.075 + rng.uniform(-0.01, 0.01))
        m[at:at + len(sn)] += sn
    c = clunk(rng, 80.0)
    m[ns(2.08):ns(2.08) + len(c)] += c * 1.1
    m += ratchet(rng, n, 1.3, 2.3, 30, 18, 0.35)
    k = ns(0.7)
    t = np.arange(k) / SR
    boing = np.sin(2 * np.pi * np.cumsum(260 + 60 * np.sin(2 * np.pi * 9 * t) * np.exp(-t / 0.2)) / SR) * np.exp(-t / 0.2)
    m[ns(2.12):ns(2.12) + k] += boing * 0.2
    return master(outdoors(m, 0.2), peak=0.98)


def chitty_wings_in():
    """Folding away, the same slowly backwards (2.4 s): the masts winding down, each panel of the fans folding shut with
    a rustle of canvas, the ratchet as the closed wings swing in under the running boards, and a clunk."""
    rng = rng_for('wings_in')
    total = 2.7
    n = ns(total)
    m = ratchet(rng, n, 0.0, 0.8, 20, 30, 0.3)
    for i in range(8):
        k = ns(0.12)
        rustle = bp(white(k), 400, 3000) * attack_decay(k, 0.01, 0.06)
        at = ns(0.25 + i * 0.08 + rng.uniform(-0.01, 0.01))
        m[at:at + k] += rustle * 0.7
    m += ratchet(rng, n, 1.1, 2.1, 26, 10, 0.75)
    c = creak(rng, 0.6, 250, 170)
    m[ns(1.3):ns(1.3) + len(c)] += c * 0.3
    c = clunk(rng, 90.0, 0.8)
    m[ns(2.2):ns(2.2) + len(c)] += c
    return master(outdoors(m, 0.2), peak=0.98)


def chitty_floats():
    """The raft, slowly (2 s): the wheels clunking round flat, then a rush of air rising in pitch as it blows up, the
    rubber squeaking taut, and a slosh as she settles on it."""
    rng = rng_for('floats')
    total = 2.6
    n = ns(total)
    m = ratchet(rng, n, 0.0, 0.7, 14, 22, 0.4)
    for at in (0.35, 0.75):
        c = clunk(rng, 110.0, 0.5)
        m[ns(at):ns(at) + len(c)] += c * 0.5
    k = ns(1.4)
    hiss = sweep_filter(white(k), 'bandpass', curve(k, [(0, 600), (1.4, 3000)], 'log'), width=1.2)
    m[ns(0.6):ns(0.6) + k] += hiss * attack_decay(k, 0.08, 3.0) * np.clip((1.4 - np.arange(k) / SR) / 0.06, 0, 1) * 0.7
    k = ns(0.9)
    t = np.arange(k) / SR
    squeak = np.sin(2 * np.pi * np.cumsum(curve(k, [(0, 360), (0.9, 900)], 'log')) / SR)
    squeak *= (0.5 + 0.5 * np.sin(2 * np.pi * 29 * t)) * attack_decay(k, 0.1, 0.4) * 0.22
    m[ns(1.0):ns(1.0) + k] += squeak
    k = ns(0.2)
    t = np.arange(k) / SR
    bloop = np.sin(2 * np.pi * np.cumsum(curve(k, [(0, 240), (0.2, 90)], 'log')) / SR) * np.exp(-t / 0.05)
    m[ns(2.02):ns(2.02) + k] += bloop * 0.8
    k = ns(0.55)
    slosh = lp(white(k), 1400) * attack_decay(k, 0.02, 0.25) * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * np.arange(k) / SR))
    m[ns(2.04):ns(2.04) + k] += slosh * 0.6
    return master(outdoors(m, 0.15), peak=0.98)


def chitty_floats_down():
    """The raft letting down: air sighing out of it, falling in pitch, the rubber flapping as it goes slack, and the
    wheels clunking back round."""
    rng = rng_for('floats_down')
    total = 2.5
    n = ns(total)
    m = np.zeros(n)
    k = ns(1.2)
    sigh = sweep_filter(white(k), 'bandpass', curve(k, [(0, 2600), (1.2, 500)], 'log'), width=1.2)
    m[:k] += sigh * attack_decay(k, 0.05, 0.8) * 0.6
    for at in rng.uniform(0.4, 1.1, 6):
        kk = ns(0.05)
        flap = lp(white(kk), 900) * attack_decay(kk, 0.003, 0.03)
        m[ns(at):ns(at) + kk] += flap * 0.5
    m += ratchet(rng, n, 1.2, 1.9, 22, 14, 0.4)
    c = clunk(rng, 110.0, 0.5)
    m[ns(1.95):ns(1.95) + len(c)] += c * 0.5
    return master(outdoors(m, 0.15), peak=0.98)


def chitty_eject():
    """The ejector seat: a trip clanks, a great spring lets go (twang), the air rushes and a cork-like pop."""
    rng = rng_for('eject')
    total = 1.4
    n = ns(total)
    m = np.zeros(n)
    c = clunk(rng, 140.0, 1.0)
    m[:len(c)] += c * 0.6
    k = ns(0.8)
    t = np.arange(k) / SR
    f = 95 * (1 + 2.5 * np.exp(-t / 0.05)) * (1 + 0.15 * np.sin(2 * np.pi * 14 * t) * np.exp(-t / 0.3))
    twang = sum(np.sin(2 * np.pi * np.cumsum(f * h) / SR) / h for h in (1, 2, 3, 5)) * np.exp(-t / 0.25)
    m[ns(0.04):ns(0.04) + k] += twang * 0.5
    k = ns(0.6)
    whoosh = sweep_filter(white(k), 'bandpass', curve(k, [(0, 400), (0.25, 2400), (0.6, 900)], 'log'), width=1.0)
    m[ns(0.05):ns(0.05) + k] += whoosh * attack_decay(k, 0.03, 0.3) * 0.6
    k = ns(0.08)
    t = np.arange(k) / SR
    pop = (np.sin(2 * np.pi * 520 * t) * 0.6 + hp(white(k), 1500) * 0.4) * np.exp(-t / 0.012)
    m[ns(0.06):ns(0.06) + k] += pop * 0.9
    return master(outdoors(m, 0.2), peak=0.98)


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
    'chitty_engine_idle': chitty_engine_idle,
    'chitty_engine_low': chitty_engine_low,
    'chitty_engine_high': chitty_engine_high,
    'chitty_flight': chitty_flight,
    'chitty_wind': chitty_wind,
    'chitty_start': chitty_start,
    'chitty_bang': chitty_bang,
    'chitty_horn': chitty_horn,
    'chitty_wings_out': chitty_wings_out,
    'chitty_wings_in': chitty_wings_in,
    'chitty_floats': chitty_floats,
    'chitty_floats_down': chitty_floats_down,
    'chitty_eject': chitty_eject,
    'chitty_crash': chitty_crash,
}
LOOPS = ('chitty_engine_idle', 'chitty_engine_low', 'chitty_engine_high', 'chitty_flight', 'chitty_wind')
# Loudness (loudest 400 ms, dB): the bangs well above everything, the running loops under the rest.
LEVELS = {'chitty_engine_idle': -19, 'chitty_engine_low': -17, 'chitty_engine_high': -16, 'chitty_flight': -20, 'chitty_wind': -18, 'chitty_start': -13, 'chitty_bang': -9, 'chitty_horn': -12,
          'chitty_wings_out': -15, 'chitty_wings_in': -17, 'chitty_floats': -16, 'chitty_floats_down': -18, 'chitty_eject': -13, 'chitty_crash': -11}


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
