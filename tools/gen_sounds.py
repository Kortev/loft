"""Synthesises the mod's sound effects and encodes them as mono Ogg Vorbis (needs ffmpeg)."""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'shootingstar', 'sounds')
rng = np.random.default_rng(1234)


def t(seconds):
    return np.arange(int(seconds * SR)) / SR


def env(n, attack, release, total=None):
    """Linear attack, exponential-ish release envelope over n samples."""
    e = np.ones(n)
    a = int(attack * SR)
    if a > 0:
        e[:a] = np.linspace(0, 1, a)
    r = int(release * SR)
    if r > 0:
        e[-r:] *= np.linspace(1, 0, r) ** 2
    return e


def decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def sweep(f0, f1, seconds, curve='exp'):
    n = int(seconds * SR)
    if curve == 'exp':
        f = f0 * (f1 / f0) ** (np.arange(n) / n)
    else:
        f = np.linspace(f0, f1, n)
    return np.sin(2 * np.pi * np.cumsum(f) / SR), f


def lowpass(x, cutoff):
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 / np.sqrt(1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spec, len(x))


def highpass(x, cutoff):
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 - 1 / np.sqrt(1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spec, len(x))


def bandsweep(x, f0, f1, width=0.6):
    """Crude time-varying band-pass via overlapping FFT blocks."""
    block = 2048
    hop = block // 2
    out = np.zeros(len(x) + block)
    win = np.hanning(block)
    nblocks = max(1, (len(x) - block) // hop + 1)
    for i in range(nblocks):
        seg = x[i * hop:i * hop + block]
        if len(seg) < block:
            seg = np.pad(seg, (0, block - len(seg)))
        frac = i / max(1, nblocks - 1)
        fc = f0 * (f1 / f0) ** frac
        spec = np.fft.rfft(seg * win)
        freqs = np.fft.rfftfreq(block, 1 / SR)
        spec *= np.exp(-(np.log((freqs + 1) / fc) / width) ** 2)
        out[i * hop:i * hop + block] += np.fft.irfft(spec, block)
    return out[:len(x)]


def brown(n):
    b = np.cumsum(rng.standard_normal(n))
    b -= np.convolve(b, np.ones(2001) / 2001, mode='same')
    return b / (np.max(np.abs(b)) + 1e-9)


def white(n):
    return rng.standard_normal(n)


def norm(x, peak=0.89):
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def pad(x, seconds):
    return np.concatenate([x, np.zeros(int(seconds * SR))])


def write(name, x, drive=1.0, cutoff=None):
    os.makedirs(OUT, exist_ok=True)
    if cutoff:
        x = lowpass(x, cutoff)
    x = norm(x, 1.0)
    if drive > 1.0:
        # Soft saturation raises the body of the sound under the initial transient.
        x = np.tanh(drive * x) / np.tanh(drive)
    x = norm(x)
    # Fade the last few ms to avoid clicks.
    f = min(len(x), int(0.01 * SR))
    x[-f:] *= np.linspace(1, 0, f)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        path = tmp.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    target = os.path.join(OUT, name + '.ogg')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', path, '-c:a', 'libvorbis', '-q:a', '4', '-ac', '1', target], check=True)
    os.unlink(path)
    print('wrote', name, '%.2fs' % (len(x) / SR))


def tone(freq, seconds, harmonics=(1.0, 0.0, 0.3)):
    tt = t(seconds)
    out = np.zeros_like(tt)
    for k, a in enumerate(harmonics, start=1):
        out += a * np.sin(2 * np.pi * freq * k * tt)
    return out


def uplink_lock():
    b1 = tone(1760, 0.07) * env(int(0.07 * SR), 0.004, 0.02)
    gap = np.zeros(int(0.04 * SR))
    b2 = tone(2350, 0.09) * env(int(0.09 * SR), 0.004, 0.03)
    thunk, _ = sweep(240, 90, 0.18)
    thunk *= decay(len(thunk), 0.05)
    s = np.concatenate([b1, gap, b2, np.zeros(int(0.03 * SR)), thunk * 0.8])
    return pad(s, 0.25)


def uplink_denied():
    tt = t(0.12)
    sq = np.sign(np.sin(2 * np.pi * 180 * tt)) * 0.6 + np.sin(2 * np.pi * 360 * tt) * 0.2
    p = sq * env(len(tt), 0.005, 0.03)
    return pad(np.concatenate([p, np.zeros(int(0.06 * SR)), p]), 0.1)


def feed_zoom():
    n = int(1.3 * SR)
    whoosh = bandsweep(white(n), 3200, 260, 0.7) * np.sin(np.linspace(0, np.pi, n)) ** 1.5
    drop, _ = sweep(420, 70, 1.3)
    drop *= decay(n, 0.5) * 0.35
    return whoosh + drop


def feed_relay():
    n = int(1.6 * SR)
    rise, f = sweep(90, 950, 1.25)
    rise = rise + 0.4 * np.sin(2 * np.cumsum(2 * np.pi * f) / SR) + 0.06 * np.sign(rise)
    rise *= np.linspace(0.2, 1, len(rise)) ** 1.5
    rise = np.concatenate([rise, np.zeros(n - len(rise))])
    swell = bandsweep(white(n), 400, 4000, 0.9) * np.linspace(0, 1, n) ** 2
    boom, _ = sweep(110, 38, 0.35)
    boom *= decay(len(boom), 0.12)
    tail = np.zeros(n)
    start = int(1.22 * SR)
    tail[start:start + len(boom)] += boom[:n - start] * 2.0
    s = rise * 0.55 + swell * 0.5 + tail
    s[start:] *= decay(n - start, 0.25) * 0.8 + 0.2
    return s


def feed_wake():
    n = int(2.5 * SR)
    tt = t(2.5)
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * 3.0 * tt)
    drone = (np.sin(2 * np.pi * 55 * tt) + 0.6 * np.sin(2 * np.pi * 110 * tt) + 0.3 * np.sin(2 * np.pi * 165 * tt)) * trem
    hum = np.sign(np.sin(2 * np.pi * 120 * tt)) * 0.15 + np.sin(2 * np.pi * 240 * tt) * 0.12
    whine, _ = sweep(380, 1700, 2.5)
    swell = np.linspace(0.15, 1.0, n) ** 1.3
    s = drone * 0.6 + hum * swell + whine * 0.18 * swell
    return s * env(n, 0.15, 0.25)


def feed_load():
    servo, _ = sweep(780, 1250, 0.22)
    servo *= env(len(servo), 0.02, 0.05) * 0.25
    n = int(0.75 * SR)
    tt = t(0.75)
    thump = np.sin(2 * np.pi * 62 * tt) * decay(n, 0.07)
    clank = sum(a * np.sin(2 * np.pi * f * tt) * decay(n, d) for f, a, d in
                ((523, 0.5, 0.18), (1307, 0.35, 0.12), (2213, 0.25, 0.08), (3301, 0.15, 0.05)))
    click = highpass(white(n), 2500) * decay(n, 0.01) * 0.6
    return np.concatenate([servo, thump + clank * 0.6 + click])


def feed_lap():
    n = int(0.5 * SR)
    zip_, f = sweep(1100, 260, 0.5)
    buzz = np.sign(zip_) * 0.08
    noise = bandsweep(white(n), 2600, 500, 0.6) * 0.5
    shape = np.exp(-((np.arange(n) / n - 0.35) / 0.18) ** 2)
    return (zip_ * 0.6 + buzz + noise) * shape


def feed_release():
    n = int(1.1 * SR)
    crack = highpass(white(n), 1800) * decay(n, 0.015) * 1.5
    tt = t(1.1)
    boom = np.sin(2 * np.pi * np.cumsum(55 * np.exp(-np.arange(n) / (0.3 * SR)) + 30) / SR) * decay(n, 0.25)
    whoosh = bandsweep(white(n), 3000, 300, 0.8) * decay(n, 0.35) * 0.6
    return crack + boom * 1.2 + whoosh


def feed_reentry():
    n = int(1.6 * SR)
    roar = lowpass(brown(n) * 1.0 + white(n) * 0.15, 900)
    roar *= np.linspace(0.2, 1, n) ** 1.2
    crackle = np.zeros(n)
    for _ in range(140):
        i = rng.integers(int(0.2 * SR), n - 200)
        crackle[i:i + 120] += rng.standard_normal(120) * np.exp(-np.arange(120) / 25) * rng.uniform(0.2, 1)
    s = norm(roar) + highpass(crackle, 1500) * 0.35
    return s * env(n, 0.05, 0.1)


def strike_inbound():
    n = int(1.5 * SR)
    tt = t(1.5)
    f = 2600 * (500 / 2600) ** ((tt / 1.5) ** 0.8)
    f = f * (1 + 0.012 * np.sin(2 * np.pi * 9 * tt))
    phase = 2 * np.pi * np.cumsum(f) / SR
    scream = np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    roar = lowpass(white(n), 1500) * np.linspace(0, 1, n) ** 2
    s = scream * np.linspace(0.3, 1, n) * 0.6 + norm(roar) * 0.6
    return s * env(n, 0.08, 0.01)


def strike_impact():
    n = int(6.0 * SR)
    crack = highpass(white(n), 1200) * decay(n, 0.02) * 2.5
    f = 70 * np.exp(-np.arange(n) / (0.6 * SR)) + 32
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * decay(n, 1.4)
    blast = lowpass(white(n), 2200) * decay(n, 0.5)
    rumble = lowpass(brown(n), 160) * decay(n, 2.2) * (0.8 + 0.2 * np.sin(2 * np.pi * 0.7 * t(6.0)))
    debris = np.zeros(n)
    for _ in range(260):
        i = rng.integers(int(0.15 * SR), int(3.5 * SR))
        length = rng.integers(60, 400)
        burst = rng.standard_normal(length) * np.exp(-np.arange(length) / (length / 4))
        debris[i:i + length] += burst * rng.uniform(0.1, 0.6) * np.exp(-i / (1.2 * SR))
    s = crack + norm(sub) * 1.3 + norm(blast) * 0.9 + norm(rumble) * 0.9 + highpass(debris, 900) * 0.4
    return s * env(n, 0.0, 0.5)


def strike_rumble():
    n = int(4.5 * SR)
    rumble = lowpass(brown(n), 110)
    rumble *= env(n, 0.35, 1.5) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * t(4.5)))
    f = 50 * np.exp(-np.arange(n) / (0.8 * SR)) + 28
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * decay(n, 1.2) * env(n, 0.2, 0.5)
    return norm(rumble) + norm(sub) * 0.8


def main():
    for name, fn, drive, cutoff in (
            ('uplink_lock', uplink_lock, 1.0, None), ('uplink_denied', uplink_denied, 1.0, 5000),
            ('feed_zoom', feed_zoom, 1.5, None), ('feed_relay', feed_relay, 1.5, 7000), ('feed_wake', feed_wake, 1.5, 6000),
            ('feed_load', feed_load, 1.0, None), ('feed_lap', feed_lap, 1.5, 7000), ('feed_release', feed_release, 2.0, None),
            ('feed_reentry', feed_reentry, 2.0, None), ('strike_inbound', strike_inbound, 1.8, 8000),
            ('strike_impact', strike_impact, 3.0, None), ('strike_rumble', strike_rumble, 2.5, None)):
        write(name, fn(), drive, cutoff)


if __name__ == '__main__':
    main()
