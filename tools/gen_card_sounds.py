"""Synthesize the card sounds (seeded, reproducible): a single card flick and a riffle shuffle.

Usage: python3 tools/gen_card_sounds.py OUT_DIR
Then:  for s in card_flick card_shuffle; do
         ffmpeg -y -i OUT_DIR/$s.wav -c:a libvorbis -q:a 4 app/src/main/res/raw/$s.ogg; done
"""
import math
import os
import random
import struct
import sys
import wave

RATE = 44100


def _write(path, samples, level=0.8):
    peak = max(abs(s) for s in samples) or 1.0
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * level * 32767)) for s in samples))


def _snap(samples, start, length, rng, brightness):
    """A short paper snap: filtered noise with a fast attack and decay."""
    prev = 0.0
    for i in range(length):
        if start + i >= len(samples):
            break
        env = min(1.0, i / 40) * math.exp(-i / (length / 4))
        noise = rng.uniform(-1, 1)
        prev = prev + brightness * (noise - prev)  # one-pole low-pass: lower = duller
        samples[start + i] += env * (noise - prev * 0.6)


def flick(rng):
    samples = [0.0] * int(RATE * 0.12)
    _snap(samples, 0, int(RATE * 0.05), rng, 0.35)
    _snap(samples, int(RATE * 0.018), int(RATE * 0.06), rng, 0.15)  # the card landing
    return samples


def shuffle(rng):
    duration = 0.75
    samples = [0.0] * int(RATE * duration)
    # Riffle: ~45 card edges, accelerating, then a soft bridge (cards sliding together).
    t = 0.02
    gap = 0.018
    while t < 0.55:
        _snap(samples, int(t * RATE), int(RATE * rng.uniform(0.006, 0.012)), rng, rng.uniform(0.3, 0.5))
        t += gap * rng.uniform(0.7, 1.3)
        gap = max(0.007, gap * 0.97)
    start = int(0.55 * RATE)
    prev = 0.0
    for i in range(len(samples) - start):
        env = math.sin(math.pi * i / (len(samples) - start)) * 0.25
        noise = rng.uniform(-1, 1)
        prev = prev + 0.08 * (noise - prev)
        samples[start + i] += env * prev * 3
    return samples


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    _write(os.path.join(out, "card_flick.wav"), flick(random.Random(20261103)))
    _write(os.path.join(out, "card_shuffle.wav"), shuffle(random.Random(20261104)))
    print("Sounds written to", out)
