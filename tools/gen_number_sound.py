"""Synthesize a short two-note chime for the number generator (deterministic).

Usage: python3 tools/gen_number_sound.py out.wav
Then: ffmpeg -y -i out.wav -c:a libvorbis -q:a 4 app/src/main/res/raw/number_tick.ogg
"""
import math
import struct
import sys
import wave

RATE = 44100
DURATION = 0.28
# A quick rising fifth (E6 then B6), each note a soft bell: fundamental plus a quiet octave.
NOTES = [(0.0, 1318.5), (0.07, 1975.5)]

samples = [0.0] * int(RATE * DURATION)
for start, freq in NOTES:
    begin = int(start * RATE)
    for i in range(len(samples) - begin):
        t = i / RATE
        attack = min(1.0, t / 0.004)
        env = attack * math.exp(-t * 18)
        tone = math.sin(2 * math.pi * freq * t) + 0.25 * math.sin(2 * math.pi * freq * 2 * t)
        samples[begin + i] += env * tone

peak = max(abs(s) for s in samples) or 1.0
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.7 * 32767)) for s in samples))
