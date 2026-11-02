"""Synthesize a short dice-clatter sound (seeded, reproducible).

Usage: python3 tools/gen_dice_sound.py out.wav
Then: ffmpeg -y -i out.wav -c:a libvorbis -q:a 4 app/src/main/res/raw/dice_roll.ogg
"""
import math
import random
import struct
import sys
import wave

RATE = 44100
DURATION = 0.7
rng = random.Random(20261102)

samples = [0.0] * int(RATE * DURATION)
# A tumble: clicks get sparser and quieter as the dice settle.
t = 0.0
energy = 1.0
while t < DURATION - 0.05 and energy > 0.05:
    freq = rng.uniform(1800, 4200)
    length = int(RATE * rng.uniform(0.008, 0.02))
    start = int(t * RATE)
    for i in range(length):
        if start + i >= len(samples):
            break
        env = math.exp(-i / (length / 5))
        tone = math.sin(2 * math.pi * freq * i / RATE)
        noise = rng.uniform(-1, 1)
        samples[start + i] += energy * env * (0.6 * tone + 0.4 * noise)
    t += rng.uniform(0.02, 0.07) * (1.6 - energy)
    energy *= rng.uniform(0.82, 0.93)

peak = max(abs(s) for s in samples) or 1.0
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.8 * 32767)) for s in samples))
