"""Synthesises the Leech Devil's sounds (NumPy, SciPy, ffmpeg; the building blocks of gen_sounds.py and gen_chainsaw_sounds.py).
No words, no recordings. All mono.

- hum1..hum3: a soft sing-song hum through a shut mouth while it circles you: a woman's voice humming a few notes up and down,
  slow vibrato, a wet throat under it. Short phrases, so that one cut off at a tell is soon over.
- tell: the tongue's tell: a wet rising slurp as the tongue coils back, rising in pitch.
- tongue: the tongue lancing out: a whip of air and a wet crack.
- smack: the latch's tell: a sucker opening with a smack.
- whip: the tentacle whipping out.
- suck: one pulse of it drinking: a wet suck and a gulp.
- tear: the arm cut loose: a wet rip and a pop as the sucker comes off.
- shriek: hurt: a shrill, broken shriek, a woman's voice gone wrong.
- paw: a front sucker pawing the ground before the trample: a heavy wet thump.
- trample: the run: heavy wet slaps coming fast.
- skid: skidding to a stop through the dirt.
- step: a wet slap of a sucker foot coming down.
- death: a long gurgling wheeze running down.
- taste: the tongue tasting the air: small wet flicks, quiet.

Usage: py -3.12 tools/gen_leech_sounds.py [name ...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_sounds as g  # noqa: E402
import gen_chainsaw_sounds as cs  # noqa: E402
from gen_sounds import SR, bp, curve, hp, limit, loudness, lp, mono, ns, sat, white  # noqa: E402

g.OUT = os.path.join(os.path.dirname(__file__), '..', 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'sounds')
place, flesh, taper, gulp, slap_hit = cs.place, cs.flesh, cs.taper, cs.gulp, cs.slap_hit


def voice(n, f0, formants=((300, 1.0), (900, 0.35), (2600, 0.12)), breath=0.08, vib=5.2, vib_depth=0.012, shimmer=0.0):
    """A voice on the pitch curve f0 (Hz, an array n long): a glottal buzz shaped by formants (resonant band passes)."""
    t = np.arange(n) / SR
    f = f0 * (1.0 + vib_depth * np.sin(2 * np.pi * vib * t + g.rng.uniform(0, 6)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    # a soft glottal pulse: odd and even harmonics falling off
    src = np.zeros(n)
    for k in range(1, 18):
        src += np.sin(k * ph + 0.3 * k) / k ** 1.25
    src += white(n) * breath
    if shimmer:
        src *= 1.0 + shimmer * lp(white(n), 30.0)
    out = np.zeros(n)
    for fc, a in formants:
        out += bp(src, fc * 0.85, fc * 1.18) * a
    return out


def notes(n, seq):
    """A pitch curve through (time s, Hz) points with short glides."""
    pts = []
    for i, (at, hz) in enumerate(seq):
        if i:
            pts.append((at, seq[i - 1][1]))
        pts.append((at + 0.06, hz))
    return curve(n, [(0.0, seq[0][1])] + pts)


def hum(seq, seconds):
    n = ns(seconds)
    t = np.arange(n) / SR
    f0 = notes(n, seq)
    x = voice(n, f0, formants=((260, 1.0), (520, 0.4), (2400, 0.06)), breath=0.05, vib_depth=0.015)
    # a second voice a hair off, softly: not quite one throat
    x += voice(n, f0 * 1.006, formants=((280, 0.6), (600, 0.2)), breath=0.03, vib=4.7) * 0.35
    env = np.minimum(t / 0.12, 1.0) * np.clip((seconds - t) / 0.3, 0, 1)
    x = lp(x, 2200.0) * env
    # wet: a little spit crackle and a throat gurgle under it
    for j in range(10):
        x = place(x, g.rng.uniform(0.05, seconds - 0.1), bp(white(ns(0.006)), 1500.0, 6000.0) * np.exp(-np.arange(ns(0.006)) / SR / 0.0015)
                  * g.rng.uniform(0.05, 0.15))
    x += lp(g.brown(n), 140.0) * env * 0.4
    return taper(x, 0.15)


def leech_hum1():
    return hum([(0.0, 392.0), (0.32, 440.0), (0.62, 392.0), (0.9, 330.0)], 1.35)


def leech_hum2():
    return hum([(0.0, 330.0), (0.28, 392.0), (0.55, 494.0), (0.86, 440.0)], 1.3)


def leech_hum3():
    return hum([(0.0, 440.0), (0.25, 392.0), (0.5, 440.0), (0.75, 523.0), (1.0, 440.0)], 1.45)


def leech_tell():
    """A wet rising slurp: the tongue drawn back in, the mouth gaping, rising in pitch to a held breath."""
    seconds = 0.85
    n = ns(seconds)
    t = np.arange(n) / SR
    f = curve(n, [(0.0, 180.0), (0.7, 900.0), (seconds, 1100.0)])
    slurp = g.sweep_filter(white(n), 'bandpass', f, order=2, width=0.5) * np.minimum(t / 0.05, 1.0) * 1.6
    x = slurp
    for j in range(26):
        at = g.rng.uniform(0.02, seconds - 0.05)
        k = at / seconds
        x += gulp(n, at, g.rng.uniform(0.02, 0.05), 200 + 600 * k, 500 + 1500 * k, g.rng.uniform(0.2, 0.5))
    x += voice(n, curve(n, [(0.0, 140.0), (seconds, 260.0)]), formants=((700, 0.6), (1200, 0.3)), breath=0.4) * np.minimum(t / 0.3, 1.0) * 0.3
    return taper(x, 0.05)


def leech_tongue():
    """The tongue lancing out: a whip of air and a wet crack."""
    n = ns(0.5)
    t = np.arange(n) / SR
    whoosh = g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 3000.0), (0.12, 700.0), (0.5, 300.0)]), order=2, width=0.8)
    whoosh *= np.minimum(t / 0.01, 1.0) * np.exp(-t / 0.09) * 1.2
    crack = bp(white(n), 900.0, 6000.0) * np.exp(-np.maximum(t - 0.07, 0) / 0.008) * (t > 0.07) * 2.0
    x = whoosh + crack + flesh(n, 0.2, 1.4) * 0.5
    return taper(x, 0.1)


def leech_smack():
    """A sucker opening wide: lips unsticking with a wet smack."""
    n = ns(0.4)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for at, lv in ((0.0, 1.0), (0.05, 0.5)):
        m = ns(0.05)
        tt = np.arange(m) / SR
        x = place(x, at, bp(white(m), 600.0, 4500.0) * np.exp(-tt / 0.006) * lv * 2.0)
    x += gulp(n, 0.06, 0.08, 500, 180, 0.7)
    x += flesh(n, 0.15, 2.0) * 0.3
    return taper(x, 0.1)


def leech_whip():
    """The tentacle whipping out: a fast fleshy swish."""
    n = ns(0.45)
    t = np.arange(n) / SR
    x = g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 400.0), (0.1, 2400.0), (0.45, 500.0)]), order=2, width=0.9)
    x *= np.sin(np.pi * np.clip(t / 0.3, 0, 1)) * 1.4
    x += lp(white(n), 250.0) * np.sin(np.pi * np.clip(t / 0.3, 0, 1)) * 0.8
    return taper(x, 0.08)


def leech_suck():
    """One pulse of drinking: a wet suck in, a gulp along the arm."""
    seconds = 0.5
    n = ns(seconds)
    t = np.arange(n) / SR
    suck = g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 1500.0), (0.3, 500.0), (seconds, 300.0)]), order=2, width=0.6)
    suck *= np.sin(np.pi * np.clip(t / 0.32, 0, 1)) ** 0.7 * 1.3
    x = suck + gulp(n, 0.25, 0.12, 260, 110, 0.9) + gulp(n, 0.3, 0.08, 180, 90, 0.6)
    x += lp(g.brown(n), 120.0) * np.sin(np.pi * np.clip((t - 0.2) / 0.3, 0, 1)) * 0.8
    return taper(x, 0.08)


def leech_tear():
    """The arm cut loose: a wet rip, then the sucker popping off."""
    n = ns(0.7)
    t = np.arange(n) / SR
    x = flesh(n, 0.35, 2.2) * 1.2
    pop = np.zeros(n)
    m = ns(0.08)
    tt = np.arange(m) / SR
    pop = place(pop, 0.22, (bp(white(m), 300.0, 3000.0) * np.exp(-tt / 0.01) + np.sin(2 * np.pi * 120 * tt) * np.exp(-tt / 0.03)) * 1.6)
    x += pop + gulp(n, 0.24, 0.1, 400, 120, 0.6)
    return taper(x, 0.15)


def leech_shriek():
    """Hurt: a shrill shriek, a woman's voice broken up and gone wrong, wet at the end."""
    seconds = 0.75
    n = ns(seconds)
    t = np.arange(n) / SR
    f0 = curve(n, [(0.0, 700.0), (0.08, 1150.0), (0.45, 1050.0), (seconds, 600.0)])
    x = voice(n, f0, formants=((1100, 1.0), (2400, 0.7), (3400, 0.4)), breath=0.35, vib=11.0, vib_depth=0.03, shimmer=0.5)
    x += voice(n, f0 * 1.5, formants=((2600, 0.5),), breath=0.2, vib=13.0, vib_depth=0.04) * 0.4
    env = np.minimum(t / 0.02, 1.0) * np.exp(-np.maximum(t - 0.4, 0) / 0.15)
    x = sat(x * env * 2.2, 1.6) * 0.8
    x += flesh(n, 0.2, 1.5) * 0.3
    return taper(x, 0.1)


def leech_paw():
    """A front sucker pawing the ground: a heavy wet thump and a scrape."""
    n = ns(0.45)
    t = np.arange(n) / SR
    x = slap_hit(n, 0.0, 1.0, 0.7)
    x += np.sin(2 * np.pi * 55.0 * t) * np.exp(-t / 0.08) * 1.2
    x += bp(white(n), 800.0, 4000.0) * np.exp(-np.maximum(t - 0.08, 0) / 0.05) * (t > 0.08) * 0.4
    return taper(x, 0.1)


def leech_trample():
    """The run: heavy wet slaps coming fast, a rumble under them."""
    seconds = 0.9
    n = ns(seconds)
    t = np.arange(n) / SR
    x = lp(g.brown(n), 180.0) * 1.5
    for at in (0.0, 0.11, 0.24, 0.35, 0.48, 0.59, 0.72, 0.83):
        x += slap_hit(n, at, g.rng.uniform(0.6, 0.9), g.rng.uniform(0.6, 0.8))
    return taper(x, 0.08)


def leech_skid():
    """Skidding to a stop: dirt tearing under it, slowing, a wet slump at the end."""
    seconds = 1.0
    n = ns(seconds)
    t = np.arange(n) / SR
    env = np.clip(1.0 - t / 0.8, 0, 1) ** 0.8
    x = g.sweep_filter(white(n), 'bandpass', curve(n, [(0.0, 1800.0), (0.8, 500.0), (seconds, 400.0)]), order=2, width=1.0) * env * 1.1
    x += lp(g.brown(n), 200.0) * env * 1.2
    for j in range(40):
        at = g.rng.uniform(0.0, 0.75)
        x = place(x, at, bp(white(ns(0.01)), 1000.0, 5000.0) * np.exp(-np.arange(ns(0.01)) / SR / 0.002) * g.rng.uniform(0.2, 0.6))
    x += slap_hit(n, 0.78, 0.7, 0.6)
    return taper(x, 0.1)


def leech_step():
    """A sucker foot coming down: a wet slap, a little suction."""
    n = ns(0.3)
    x = slap_hit(n, 0.0, 0.8, 0.8 + g.rng.uniform(-0.1, 0.1))
    x += gulp(n, 0.03, 0.05, 300, 150, 0.25)
    return taper(x, 0.08)


def leech_death():
    """Dying: a long wheeze running down through a gurgle, the hum's voice in it once, then nothing."""
    seconds = 2.6
    n = ns(seconds)
    t = np.arange(n) / SR
    f0 = curve(n, [(0.0, 420.0), (0.5, 380.0), (1.6, 180.0), (seconds, 90.0)])
    v = voice(n, f0, formants=((500, 1.0), (1300, 0.4), (2600, 0.15)), breath=0.6, vib=6.0, vib_depth=0.03, shimmer=0.4)
    env = np.minimum(t / 0.05, 1.0) * np.clip((seconds - t) / 1.4, 0, 1)
    x = v * env * 0.8
    for j in range(40):
        at = g.rng.uniform(0.2, seconds - 0.3)
        x += gulp(n, at, g.rng.uniform(0.04, 0.1), g.rng.uniform(80, 160), g.rng.uniform(200, 420), g.rng.uniform(0.2, 0.5) * (1 - at / seconds))
    x += lp(g.brown(n), 150.0) * env * 0.8
    return taper(x, 0.3)


def leech_taste():
    """The tongue tasting the air: small wet flicks, quiet."""
    n = ns(0.6)
    x = np.zeros(n)
    for at in (0.0, 0.12, 0.22, 0.36):
        m = ns(0.05)
        tt = np.arange(m) / SR
        x = place(x, at, bp(white(m), 1200.0, 6000.0) * np.exp(-tt / 0.008) * g.rng.uniform(0.5, 1.0))
        x += gulp(n, at + 0.01, 0.03, 600, 1100, 0.15)
    return taper(x, 0.1)


SOUNDS = {k: v for k, v in globals().items() if k.startswith('leech_') and callable(v)}
LEVELS = {'leech_hum1': -19, 'leech_hum2': -19, 'leech_hum3': -19, 'leech_tell': -12, 'leech_tongue': -10, 'leech_smack': -12,
          'leech_whip': -13, 'leech_suck': -12, 'leech_tear': -10, 'leech_shriek': -10, 'leech_paw': -11, 'leech_trample': -11,
          'leech_skid': -12, 'leech_step': -16, 'leech_death': -11, 'leech_taste': -20}


def make(name):
    g.rng = np.random.default_rng(zlib.crc32(name.encode()))
    x = mono(SOUNDS[name]())
    x = x * 10 ** ((LEVELS[name] - loudness(x)) / 20)
    return limit(x)


def main():
    for name in sys.argv[1:] or list(SOUNDS):
        g.write(name, make(name))


if __name__ == '__main__':
    main()
