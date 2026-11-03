"""A tiny, dependency-free TrueType writer for an original seven-segment ("LED display") font.

Usage: python3 tools/segment_font.py out.ttf
Used by tools/gen_dice_examples.py for the neon example skin (fonts/segments.ttf).

Covers what dice labels need: 0-9, d/D, n/N, ?, + and -. Any other character falls back to the
system font. Glyphs are slanted hexagonal segments; every outline is a simple polygon, so the
writer only emits the required tables (head, hhea, maxp, OS/2, hmtx, cmap, loca, glyf, name,
post) with straight on-curve points. The output is deterministic.

The font is original work, released with this project.
"""
import math
import struct
import sys

UPM = 1000
ADVANCE = 680
LEFT, RIGHT, BOTTOM, TOP = 90, 520, 0, 700
MID = (BOTTOM + TOP) // 2
THICK = 86
GAP = 14
SLANT = math.tan(math.radians(7))
FAMILY = "Dice Segments"

# Segment centre lines: horizontal (a, g, d) span between the vertical centre lines.
XL, XR = LEFT + THICK / 2, RIGHT - THICK / 2
YT, YB = TOP - THICK / 2, BOTTOM + THICK / 2


def _h(y):
    return [(XL + GAP, y), (XL + GAP + THICK / 2, y + THICK / 2), (XR - GAP - THICK / 2, y + THICK / 2),
            (XR - GAP, y), (XR - GAP - THICK / 2, y - THICK / 2), (XL + GAP + THICK / 2, y - THICK / 2)]


def _v(x, y0, y1):
    return [(x, y0 + GAP), (x + THICK / 2, y0 + GAP + THICK / 2), (x + THICK / 2, y1 - GAP - THICK / 2),
            (x, y1 - GAP), (x - THICK / 2, y1 - GAP - THICK / 2), (x - THICK / 2, y0 + GAP + THICK / 2)]


SEGMENTS = {
    "a": _h(YT), "g": _h(MID), "d": _h(YB),
    "f": _v(XL, MID, YT), "b": _v(XR, MID, YT),
    "e": _v(XL, YB, MID), "c": _v(XR, YB, MID),
    # extra: a short vertical bar through the middle, for "+"
    "p": _v((XL + XR) / 2, MID - 150, MID + 150),
}

CHARS = {
    "0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc",
    "5": "afgcd", "6": "afgedc", "7": "abc", "8": "abcdefg", "9": "abcdfg",
    "d": "bcdeg", "D": "bcdeg", "n": "ceg", "N": "ceg", "?": "abge", "-": "g", "+": "gp",
}


def _slant(points):
    return [(round(x + y * SLANT), round(y)) for x, y in points]


def _clockwise(points):
    area = sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]))
    return points if area < 0 else points[::-1]  # TrueType fills clockwise outlines (y up)


def _glyph(contours):
    """glyf entry for simple glyphs made of straight, on-curve contours."""
    if not contours:
        return b"", (0, 0, 0, 0)
    pts = [p for c in contours for p in c]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    bbox = (min(xs), min(ys), max(xs), max(ys))
    data = struct.pack(">h4h", len(contours), *bbox)
    end = -1
    for c in contours:
        end += len(c)
        data += struct.pack(">H", end)
    data += struct.pack(">H", 0)  # no instructions
    data += bytes([0x01] * len(pts))  # on-curve, 16-bit coordinates
    px = py = 0
    xd, yd = b"", b""
    for x, y in pts:
        xd += struct.pack(">h", x - px)
        yd += struct.pack(">h", y - py)
        px, py = x, y
    data += xd + yd
    return data + b"\0" * (-len(data) % 4), bbox


def _checksum(data):
    data += b"\0" * (-len(data) % 4)
    return sum(struct.unpack(f">{len(data) // 4}I", data)) & 0xFFFFFFFF


def _cmap(mapping):
    codes = sorted(mapping)
    segs = [(c, c, (mapping[c] - c) & 0xFFFF) for c in codes] + [(0xFFFF, 0xFFFF, 1)]
    n = len(segs)
    search = 2 * 2 ** int(math.log2(n))
    body = struct.pack(">HHHH", n * 2, search, int(math.log2(n)), n * 2 - search)
    body += b"".join(struct.pack(">H", e) for _, e, _ in segs) + b"\0\0"
    body += b"".join(struct.pack(">H", s) for s, _, _ in segs)
    body += b"".join(struct.pack(">H", d) for _, _, d in segs)
    body += b"\0\0" * n
    sub = struct.pack(">HHH", 4, 6 + len(body), 0) + body
    return struct.pack(">HHHHIHHI", 0, 2, 0, 3, 20, 3, 1, 20) + sub


def _name():
    names = {1: FAMILY, 2: "Regular", 3: f"{FAMILY} Regular 1.0", 4: f"{FAMILY} Regular",
             5: "Version 1.0", 6: FAMILY.replace(" ", "") + "-Regular"}
    strings, records, offset = b"", b"", 0
    for nid, text in names.items():
        raw = text.encode("utf-16-be")
        records += struct.pack(">6H", 3, 1, 0x409, nid, len(raw), offset)
        strings += raw
        offset += len(raw)
    return struct.pack(">3H", 0, len(names), 6 + 12 * len(names)) + records + strings


def build():
    glyphs = [("notdef", [[(60, 0), (60, 700), (560, 700), (560, 0)], [(120, 60), (500, 60), (500, 640), (120, 640)]]),
              ("space", [])]
    glyphs[0] = ("notdef", [_clockwise(glyphs[0][1][0]), _clockwise(glyphs[0][1][1])[::-1]])
    for ch, segs in CHARS.items():
        glyphs.append((ch, [_clockwise(_slant(SEGMENTS[s])) for s in segs]))
    mapping = {0x20: 1}
    mapping.update({ord(ch): i + 2 for i, ch in enumerate(CHARS)})

    glyf, loca, hmtx, boxes = b"", [0], b"", []
    for _, contours in glyphs:
        data, bbox = _glyph(contours)
        glyf += data
        loca.append(len(glyf))
        hmtx += struct.pack(">Hh", ADVANCE, bbox[0])
        boxes.append(bbox)
    real = [b for b in boxes if b != (0, 0, 0, 0)]
    x_min, y_min = min(b[0] for b in real), min(b[1] for b in real)
    x_max, y_max = max(b[2] for b in real), max(b[3] for b in real)
    max_points = max(sum(len(c) for c in cs) for _, cs in glyphs)
    max_contours = max(len(cs) for _, cs in glyphs)
    n = len(glyphs)

    tables = {
        b"head": struct.pack(">IIIIHHqqhhhhHHhhh", 0x00010000, 0x00010000, 0, 0x5F0F3CF5, 0x000B, UPM,
                             0, 0, x_min, y_min, x_max, y_max, 0, 8, 2, 1, 0),
        b"hhea": struct.pack(">I3hH3h3h4hhH", 0x00010000, 800, -200, 0, ADVANCE, min(b[0] for b in real),
                             min(ADVANCE - b[2] for b in real), x_max, 1, 0, 0, 0, 0, 0, 0, 0, n),
        b"maxp": struct.pack(">IHHHHHHHHHHHHHH", 0x00010000, n, max_points, max_contours, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0),
        b"OS/2": struct.pack(">HhHHHhhhhhhhhhhh10s4I4sHHHhhhHHIIhhHHH", 4, ADVANCE, 400, 5, 0,
                             650, 600, 0, 75, 650, 600, 0, 350, 50, 300, 0, bytes(10), 1, 0, 0, 0, b"DFAM",
                             0x0040, 0x20, max(mapping), 800, -200, 0, 800, 200, 1, 0, 500, TOP, 0, 0x20, 0),
        b"hmtx": hmtx,
        b"cmap": _cmap(mapping),
        b"loca": struct.pack(f">{len(loca)}I", *loca),
        b"glyf": glyf,
        b"name": _name(),
        b"post": struct.pack(">IihhIIIII", 0x00030000, -7 * 65536, -100, 50, 1, 0, 0, 0, 0),
    }

    tags = sorted(tables)
    num = len(tags)
    search = 16 * 2 ** int(math.log2(num))
    header = struct.pack(">IHHHH", 0x00010000, num, search, int(math.log2(num)), num * 16 - search)
    offset = 12 + 16 * num
    directory, body = b"", b""
    for tag in tags:
        data = tables[tag]
        directory += struct.pack(">4sIII", tag, _checksum(data), offset, len(data))
        padded = data + b"\0" * (-len(data) % 4)
        body += padded
        offset += len(padded)
    font = bytearray(header + directory + body)
    head_offset = 12 + 16 * tags.index(b"head")
    head_at = struct.unpack(">I", font[head_offset + 8:head_offset + 12])[0]
    struct.pack_into(">I", font, head_at + 8, (0xB1B0AFBA - _checksum(bytes(font))) & 0xFFFFFFFF)
    return bytes(font)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "segments.ttf"
    with open(out, "wb") as f:
        f.write(build())
    print("Font written to", out)
