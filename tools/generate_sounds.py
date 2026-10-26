#!/usr/bin/env python3
"""Sintetiza (100% original) la banda sonora del ritual y el sonido de la cuchara.
Requiere numpy y ffmpeg con libvorbis.  Uso: python3 tools/generate_sounds.py
Linea de tiempo (s desde el inicio del ritual): golpe grave 0 -> anillo en el cielo 1-4 -> cubo cae 5
-> impacto 7.5 -> oscuridad total 9.5 -> subida 17-29.8 -> IMPACTO 29.85 -> arpegio de regreso 31.5-35 -> fade 38."""
import numpy as np, wave, subprocess, os
SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/revivalcampfire/sounds')
rng = np.random.default_rng(42)

def ss(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)

def noise_band(n, lo, hi):
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    mask = np.exp(-0.5 * ((np.log(np.maximum(f, 1)) - np.log(np.sqrt(lo * hi))) / (0.5 * np.log(hi / lo))) ** 2)
    y = np.fft.irfft(X * mask, n)
    return y / (np.abs(y).max() + 1e-9)

def hp_noise(n, fc):
    x = rng.standard_normal(n)
    X = np.fft.rfft(x); f = np.fft.rfftfreq(n, 1 / SR)
    X *= ss(fc * 0.7, fc * 1.3, f)
    y = np.fft.irfft(X, n)
    return y / (np.abs(y).max() + 1e-9)

class Mix:
    def __init__(self, dur):
        self.n = int(SR * dur)
        self.t = np.arange(self.n) / SR
        self.L = np.zeros(self.n); self.R = np.zeros(self.n)
    def add(self, sig, t0, pan=0.5):
        i = int(t0 * SR)
        if i >= self.n: return
        s = sig[: self.n - i]
        self.L[i:i + len(s)] += s * np.cos(pan * np.pi / 2)
        self.R[i:i + len(s)] += s * np.sin(pan * np.pi / 2)
    def addst(self, l, r, t0=0):
        i = int(t0 * SR); m = min(len(l), self.n - i)
        self.L[i:i + m] += l[:m]; self.R[i:i + m] += r[:m]

def bell(freq, dur=2.2, amp=0.2):
    t = np.arange(int(SR * dur)) / SR
    s = np.zeros_like(t)
    for p, a in zip([1, 2.76, 5.4, 8.93], [1, .5, .25, .12]):
        s += a * np.sin(2 * np.pi * freq * p * t) * np.exp(-t * (2.2 + p * 0.9))
    s *= np.minimum(1, t * 400)
    return s * amp

def boom(f0=75, f1=30, dur=3.0, amp=0.9, k=3.0, decay=1.4):
    t = np.arange(int(SR * dur)) / SR
    f = f1 + (f0 - f1) * np.exp(-t * k)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) * np.exp(-t * decay)
    nz = noise_band(len(t), 40, 400) * np.exp(-t * 5) * 0.6
    return (s + nz) * amp * np.minimum(1, t * 200)

def whoosh(dur, lo, hi, amp):
    n = int(SR * dur); t = np.arange(n) / SR
    return noise_band(n, lo, hi) * np.sin(np.pi * t / dur) ** 2 * amp

def build_main():
    D = 38.0
    m = Mix(D); t = m.t
    climax = 29.85
    pre = 1 - ss(climax - 0.05, climax, t)            # todo lo previo se corta en el impacto
    post = ss(climax + 0.4, climax + 1.8, t) * (1 - ss(33.5, 37.8, t))
    swell = (0.25 + 0.75 * ss(8, 28, t))

    # drone grave + pad abierto (estereo con detune)
    drone = (np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 82.41 * t + 1)) * 0.20
    drone *= ss(0, 3.5, t) * swell * pre + post * 0.6
    m.L += drone; m.R += drone
    for f in [110, 164.81, 220, 329.63, 440]:
        for side, det in ((0, -0.35), (1, 0.35)):
            tr = 0.75 + 0.25 * np.sin(2 * np.pi * (0.13 + 0.03 * f / 440) * t + f)
            s = sum(np.sin(2 * np.pi * (f + det) * h * t) / h for h in (1, 2, 3)) * 0.028 * tr
            s *= (ss(1, 6, t) * swell * pre + post * 0.8)
            (m.R if side else m.L).__iadd__(s)

    # brillo agudo creciente
    rate = 5 + 7 * ss(12, 28, t)
    ph = 2 * np.pi * np.cumsum(rate) / SR
    for f in [880, 1318.5, 1760, 2637]:
        s = np.sin(2 * np.pi * f * t) * (0.6 + 0.4 * np.sin(ph + f)) * 0.012 * ss(11, 27, t) * pre
        m.L += s; m.R += s * 0.9

    # golpe inicial al usar la cuchara
    m.add(boom(80, 32, 3.2, 0.85), 0.0)
    m.add(whoosh(2.2, 200, 4000, 0.22), 0.1, 0.4)
    # anillo en el cielo: destellos suaves
    for i, f in enumerate([1760, 1318.51, 1760]):
        m.add(bell(f, 2.2, 0.09), 0.8 + i * 0.7, 0.3 + 0.2 * i)
    # el cubo cae (5.0 s), impacta (7.5 s) y crece hasta oscurecerlo todo (7.5 -> 9.5 s)
    n = int(SR * 2.5)
    m.add(noise_band(n, 300, 6000) * (np.arange(n) / n) ** 2 * 0.28, 5.0, 0.5)
    m.add(boom(70, 28, 2.8, 0.85, 3, 1.5), 7.5)
    m.add(whoosh(2.2, 100, 5000, 0.34), 7.6, 0.5)
    m.add(boom(60, 26, 3.0, 0.7, 3, 1.4), 9.5)

    # subida (17 -> 29.8): glissando + ruido que abre brillo
    g = np.clip((t - 17) / 12.8, 0, 1)
    fr = 180 * np.exp(np.log(9) * g ** 1.5)
    ph = 2 * np.pi * np.cumsum(fr) / SR
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * np.cumsum(3 + 14 * g ** 2) / SR)
    riser = (np.sin(ph) + 0.4 * np.sin(2 * ph)) * 0.05 * ss(17, 29.8, t) ** 2 * trem * pre
    m.L += riser; m.R += riser * 0.85
    for i, (lo, hi) in enumerate([(300, 700), (700, 1600), (1600, 3500), (3500, 8000), (8000, 16000)]):
        c = 19 + i * 2.1
        env = np.exp(-0.5 * ((t - c) / 3.2) ** 2) * ss(17, 29.8, t) * pre
        nz = noise_band(m.n, lo, hi) * env * 0.10
        m.L += nz; m.R += np.roll(nz, 300)

    # campanitas (mas densas al acercarse al clima)
    scale = [880, 987.77, 1108.73, 1318.51, 1479.98, 1760]
    tt = 3.0
    while tt < 29.6:
        m.add(bell(rng.choice(scale), 2.4, rng.uniform(0.05, 0.12)), tt, rng.uniform(0.1, 0.9))
        tt += rng.exponential(1 / (0.4 + 2.5 * float(ss(15, 29, tt))))

    # tension: silencio breve antes del impacto
    duck = 1 - 0.9 * ss(29.5, climax - 0.03, t) * (1 - ss(climax - 0.03, climax, t))
    m.L *= duck; m.R *= duck

    # IMPACTO + estallido brillante
    m.add(boom(55, 24, 4.5, 1.0, 2.2, 0.9), climax)
    m.add(hp_noise(int(SR * 3.5), 3000) * np.exp(-np.arange(int(SR * 3.5)) / SR * 1.1) * 0.5, climax, 0.5)
    m.add(whoosh(2.5, 200, 12000, 0.35) * np.exp(-np.arange(int(SR * 2.5)) / SR * 1.2), climax + 0.05, 0.5)
    for f in [880, 1318.51, 1760, 2217.46, 2637]:
        m.add(bell(f, 3.5, 0.13), climax + 0.02, rng.uniform(0.2, 0.8))

    # arpegio de regreso a la vida
    for i, f in enumerate([880, 1108.73, 1318.51, 1760, 2217.46]):
        m.add(bell(f, 3.0, 0.11), 31.6 + i * 0.55, 0.5 + 0.3 * np.sin(i))

    fade = 1 - ss(36.8, 38.0, t)
    m.L *= fade; m.R *= fade
    pk = max(np.abs(m.L).max(), np.abs(m.R).max())
    return m.L / pk * 0.89, m.R / pk * 0.89

def build_spoon():
    m = Mix(1.8)
    m.add(boom(120, 50, 0.9, 0.6, 6, 5), 0.0)
    for i, f in enumerate([880, 1318.51, 1760]):
        m.add(bell(f, 1.6, 0.25), 0.05 + i * 0.09, 0.5)
    m.add(whoosh(1.2, 1000, 9000, 0.12), 0.0)
    s = (m.L + m.R) / 2
    return s / np.abs(s).max() * 0.85

def build_revived():
    m = Mix(3.2)
    m.add(boom(90, 45, 1.6, 0.35, 5, 2.5), 0.0)
    for i, f in enumerate([880, 1108.73, 1318.51, 1760]):
        m.add(bell(f, 2.6, 0.2), 0.05 + i * 0.16, 0.35 + 0.1 * i)
    m.add(hp_noise(int(SR * 1.5), 5000) * np.exp(-np.arange(int(SR * 1.5)) / SR * 2.4) * 0.22, 0.0, 0.5)
    s = (m.L + m.R) / 2
    return s / np.abs(s).max() * 0.85

def write_ogg(name, chans):
    wav = os.path.join(OUT, name + '.wav')
    data = np.stack(chans, axis=1) if len(chans) > 1 else chans[0]
    pcm = (np.clip(data, -1, 1) * 32767).astype('<i2')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(len(chans)); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(wav)

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    # ritual_main.ogg y revived.ogg son los audios propios del autor; este script ya no los sobreescribe.
    # (para volver a la version sintetizada: write_ogg('ritual_main', list(build_main())) / write_ogg('revived', [build_revived()]))
    write_ogg('spoon_use', [build_spoon()])
    print('ok')
