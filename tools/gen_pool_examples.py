"""Generate the example pools in docs/pool-examples/ (deterministic, original artwork).

Usage: python3 tools/gen_pool_examples.py [OUT_DIR]   (default: docs/pool-examples)
Needs Pillow. Rerunning without changes gives byte-identical files.

- classes.zip  13 fantasy character classes: items.csv (name,image,color) + images/ (one original
               emblem per class). Every item has an image, so the pool shows images only.
- drinks.csv   24 non-alcoholic drinks with colours and no images, so the pool shows names only.
"""
import io
import math
import os
import sys
import zipfile

from PIL import Image, ImageDraw

SIZE = 256
ZIP_DATE = (2026, 1, 1, 0, 0, 0)
WHITE = (250, 248, 242)

CLASSES = [
    # name, colour, emblem
    ("Warrior", "#8D5A3B", "sword"),
    ("Paladin", "#E07BB0", "shield"),
    ("Hunter", "#8DBA5A", "bow"),
    ("Rogue", "#E8D44D", "dagger"),
    ("Priest", "#D9D9D9", "sun"),
    ("Shaman", "#2F6FD6", "bolt"),
    ("Mage", "#5BC4E8", "star"),
    ("Warlock", "#7B5BC4", "eye"),
    ("Monk", "#2EC28A", "fist"),
    ("Druid", "#E8822E", "leaf"),
    ("Death Knight", "#B3263A", "skull"),
    ("Demon Hunter", "#9E3FB5", "horns"),
    ("Evoker", "#2E9C8A", "wing"),
]

DRINKS = [
    ("Lemonade", "#F4E04D"),
    ("Iced tea", "#B5651D"),
    ("Green tea", "#8DB360"),
    ("Black tea", "#5C3317"),
    ("Coffee", "#4B2E1E"),
    ("Hot chocolate", "#6F4E37"),
    ("Orange juice", "#FFA500"),
    ("Apple juice", "#E8C547"),
    ("Tomato juice", "#C0392B"),
    ("Cranberry juice", "#9B1B30"),
    ("Grape juice", "#5B2A6E"),
    ("Pineapple juice", "#F7D358"),
    ("Coconut water", "#F5F1E3"),
    ("Sparkling water", "#CDE8F2"),
    ("Mineral water", "#A9D3E8"),
    ("Milk", "#FFFFFF"),
    ("Milkshake", "#F7C6D9"),
    ("Kombucha", "#C9A55B"),
    ("Ginger ale", "#E8C07D"),
    ("Root beer", "#3D1F0F"),
    ("Kvass", "#7A4A1E"),
    ("Mint lemonade", "#9FD99B"),
    ("Smoothie", "#E05A7A"),
    ("Cola", "#2B1A12"),
]


def rgb(hex_colour):
    h = hex_colour.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ink_for(bg):
    r, g, b = bg
    return (24, 24, 28) if (0.299 * r + 0.587 * g + 0.114 * b) > 150 else WHITE


# --- Original emblems, drawn from simple shapes around the centre (c) with scale (s) -------------

def sword(d, c, s, k):
    x, y = c
    d.polygon([(x, y - s), (x + s * 0.12, y - s * 0.8), (x + s * 0.12, y + s * 0.3), (x - s * 0.12, y + s * 0.3), (x - s * 0.12, y - s * 0.8)], fill=k)
    d.rectangle([x - s * 0.45, y + s * 0.3, x + s * 0.45, y + s * 0.42], fill=k)
    d.rectangle([x - s * 0.07, y + s * 0.42, x + s * 0.07, y + s * 0.8], fill=k)
    d.ellipse([x - s * 0.13, y + s * 0.78, x + s * 0.13, y + s * 1.0], fill=k)


def shield(d, c, s, k):
    x, y = c
    pts = [(x - s * 0.7, y - s * 0.8), (x + s * 0.7, y - s * 0.8), (x + s * 0.7, y)]
    pts += [(x + s * 0.7 * math.cos(a), y + s * 0.95 * math.sin(a)) for a in [i * math.pi / 16 for i in range(1, 16)]]
    pts += [(x - s * 0.7, y)]
    d.polygon(pts, outline=k, width=max(4, int(s * 0.1)))
    d.rectangle([x - s * 0.08, y - s * 0.6, x + s * 0.08, y + s * 0.7], fill=k)
    d.rectangle([x - s * 0.45, y - s * 0.2, x + s * 0.45, y - s * 0.04], fill=k)


def bow(d, c, s, k):
    x, y = c
    w = max(5, int(s * 0.1))
    d.arc([x - s * 1.2, y - s * 0.9, x + s * 0.4, y + s * 0.9], -60, 60, fill=k, width=w)
    top = (x - s * 0.4 + s * 0.8 * math.cos(math.radians(-60)), y + s * 0.9 * math.sin(math.radians(-60)))
    bot = (top[0], y - (top[1] - y))
    d.line([top, bot], fill=k, width=max(2, int(s * 0.03)))
    d.line([(x - s * 0.8, y), (x + s * 0.75, y)], fill=k, width=max(3, int(s * 0.05)))
    d.polygon([(x + s * 0.95, y), (x + s * 0.7, y - s * 0.12), (x + s * 0.7, y + s * 0.12)], fill=k)


def dagger(d, c, s, k):
    # Two crossed daggers, blades up.
    x, y = c
    for side in (-1, 1):
        ang = math.radians(38 * side)

        def rot(px, py):
            return (x + px * math.cos(ang) - py * math.sin(ang), y + px * math.sin(ang) + py * math.cos(ang))
        d.polygon([rot(0, -s * 1.0), rot(s * 0.11, -s * 0.75), rot(s * 0.11, s * 0.25), rot(-s * 0.11, s * 0.25), rot(-s * 0.11, -s * 0.75)], fill=k)
        d.polygon([rot(-s * 0.32, s * 0.25), rot(s * 0.32, s * 0.25), rot(s * 0.32, s * 0.36), rot(-s * 0.32, s * 0.36)], fill=k)
        d.polygon([rot(-s * 0.07, s * 0.36), rot(s * 0.07, s * 0.36), rot(s * 0.07, s * 0.75), rot(-s * 0.07, s * 0.75)], fill=k)
        px, py = rot(0, s * 0.82)
        d.ellipse([px - s * 0.1, py - s * 0.1, px + s * 0.1, py + s * 0.1], fill=k)


def sun(d, c, s, k):
    x, y = c
    for i in range(12):
        a = i * math.pi / 6
        d.polygon([
            (x + s * 0.95 * math.cos(a), y + s * 0.95 * math.sin(a)),
            (x + s * 0.5 * math.cos(a + 0.2), y + s * 0.5 * math.sin(a + 0.2)),
            (x + s * 0.5 * math.cos(a - 0.2), y + s * 0.5 * math.sin(a - 0.2)),
        ], fill=k)
    d.ellipse([x - s * 0.45, y - s * 0.45, x + s * 0.45, y + s * 0.45], fill=k)


def bolt(d, c, s, k):
    x, y = c
    d.polygon([(x + s * 0.2, y - s), (x - s * 0.5, y + s * 0.1), (x - s * 0.05, y + s * 0.1),
               (x - s * 0.25, y + s), (x + s * 0.5, y - s * 0.15), (x + s * 0.05, y - s * 0.15)], fill=k)


def star(d, c, s, k):
    x, y = c
    pts = []
    for i in range(10):
        r = s if i % 2 == 0 else s * 0.4
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((x + r * math.cos(a), y + r * math.sin(a)))
    d.polygon(pts, fill=k)


def eye(d, c, s, k, bg):
    x, y = c
    d.chord([x - s, y - s * 0.9, x + s, y + s * 0.5], 20, 160, fill=k)
    d.chord([x - s, y - s * 0.5, x + s, y + s * 0.9], 200, 340, fill=k)
    d.ellipse([x - s * 0.3, y - s * 0.3, x + s * 0.3, y + s * 0.3], fill=bg)
    d.ellipse([x - s * 0.1, y - s * 0.25, x + s * 0.1, y + s * 0.25], fill=k)


def fist(d, c, s, k, bg):
    # A yin-yang-like balance circle.
    x, y = c
    d.ellipse([x - s, y - s, x + s, y + s], fill=k)
    d.pieslice([x - s * 0.9, y - s * 0.9, x + s * 0.9, y + s * 0.9], 90, 270, fill=bg)
    d.ellipse([x - s * 0.45, y - s * 0.9, x + s * 0.45, y], fill=bg)
    d.ellipse([x - s * 0.45, y, x + s * 0.45, y + s * 0.9], fill=k)
    d.ellipse([x - s * 0.12, y - s * 0.57, x + s * 0.12, y - s * 0.33], fill=k)
    d.ellipse([x - s * 0.12, y + s * 0.33, x + s * 0.12, y + s * 0.57], fill=bg)


def leaf(d, c, s, k, bg):
    x, y = c
    d.chord([x - s * 1.1, y - s, x + s * 0.5, y + s], -60, 60, fill=k)
    d.chord([x - s * 0.5, y - s, x + s * 1.1, y + s], 120, 240, fill=k)
    d.line([(x, y - s * 0.8), (x, y + s)], fill=bg, width=max(3, int(s * 0.06)))
    for i in range(3):
        yy = y - s * 0.4 + i * s * 0.35
        d.line([(x, yy + s * 0.2), (x - s * 0.25, yy)], fill=bg, width=max(2, int(s * 0.04)))
        d.line([(x, yy + s * 0.2), (x + s * 0.25, yy)], fill=bg, width=max(2, int(s * 0.04)))


def skull(d, c, s, k, bg):
    x, y = c
    d.ellipse([x - s * 0.75, y - s * 0.9, x + s * 0.75, y + s * 0.5], fill=k)
    d.rectangle([x - s * 0.4, y + s * 0.3, x + s * 0.4, y + s * 0.8], fill=k)
    d.ellipse([x - s * 0.5, y - s * 0.3, x - s * 0.12, y + s * 0.1], fill=bg)
    d.ellipse([x + s * 0.12, y - s * 0.3, x + s * 0.5, y + s * 0.1], fill=bg)
    d.polygon([(x, y + s * 0.15), (x - s * 0.1, y + s * 0.35), (x + s * 0.1, y + s * 0.35)], fill=bg)
    for i in range(-1, 2):
        d.line([(x + i * s * 0.18, y + s * 0.55), (x + i * s * 0.18, y + s * 0.8)], fill=bg, width=max(2, int(s * 0.05)))


def horns(d, c, s, k):
    x, y = c
    w = max(6, int(s * 0.16))
    for side in (-1, 1):
        box = [x + side * s * 0.1 - s * 0.6, y - s * 0.6, x + side * s * 0.1 + s * 0.6, y + s * 0.6]
        if side < 0:
            d.arc(box, 120, 250, fill=k, width=w)
        else:
            d.arc(box, 290, 60, fill=k, width=w)
        d.polygon([(x + side * s * 0.25, y - s * 0.5), (x + side * s * 0.75, y - s * 1.0), (x + side * s * 0.5, y - s * 0.4)], fill=k)
    d.polygon([(x - s * 0.3, y - s * 0.1), (x + s * 0.3, y - s * 0.1), (x, y + s * 0.7)], fill=k)


def wing(d, c, s, k):
    # A spread wing: feathers fanning up and left from a shoulder at the lower right.
    x, y = c
    bx, by = x + s * 0.55, y + s * 0.55
    for i in range(6):
        a = math.radians(185 + i * 17)
        r = s * (1.45 - i * 0.12)
        tip = (bx + r * math.cos(a), by + r * math.sin(a))
        side = (bx + r * 0.7 * math.cos(a + 0.22), by + r * 0.7 * math.sin(a + 0.22))
        d.polygon([(bx, by), tip, side], fill=k)
    d.ellipse([bx - s * 0.25, by - s * 0.25, bx + s * 0.25, by + s * 0.25], fill=k)


EMBLEMS = {
    "sword": sword, "shield": shield, "bow": bow, "dagger": dagger, "sun": sun, "bolt": bolt,
    "star": star, "eye": eye, "fist": fist, "leaf": leaf, "skull": skull, "horns": horns, "wing": wing,
}
NEEDS_BG = {"eye", "fist", "leaf", "skull"}


def emblem_png(colour, emblem):
    bg = rgb(colour)
    k = ink_for(bg)
    img = Image.new("RGB", (SIZE, SIZE), bg)
    d = ImageDraw.Draw(img)
    m = SIZE * 0.06
    d.ellipse([m, m, SIZE - m, SIZE - m], outline=k, width=max(3, SIZE // 48))
    c, s = (SIZE / 2, SIZE / 2), SIZE * 0.3
    fn = EMBLEMS[emblem]
    if emblem in NEEDS_BG:
        fn(d, c, s, k, bg)
    else:
        fn(d, c, s, k)
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def file_name(name):
    return name.lower().replace(" ", "_") + ".png"


def csv_field(value):
    return '"' + value.replace('"', '""') + '"' if any(ch in value for ch in ',"\n') else value


def write_zip(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in entries:
            info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join("docs", "pool-examples")
    os.makedirs(out, exist_ok=True)

    rows = ["name,image,color"]
    images = []
    for name, colour, emblem in CLASSES:
        path = "images/" + file_name(name)
        rows.append(",".join(csv_field(v) for v in (name, path, colour)))
        images.append((path, emblem_png(colour, emblem)))
    write_zip(os.path.join(out, "classes.zip"), [("items.csv", "\n".join(rows) + "\n")] + images)

    with open(os.path.join(out, "drinks.csv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("name,color\n")
        for name, colour in DRINKS:
            f.write(f"{csv_field(name)},{colour}\n")

    # A contact sheet of the class emblems for the docs.
    cols, cell = 7, 128
    sheet = Image.new("RGB", (cols * cell, 2 * cell), WHITE)
    for i, (_, colour, emblem) in enumerate(CLASSES):
        tile = Image.open(io.BytesIO(emblem_png(colour, emblem))).resize((cell - 8, cell - 8), Image.LANCZOS)
        sheet.paste(tile, ((i % cols) * cell + 4, (i // cols) * cell + 4))
    sheet.save(os.path.join(out, "classes-preview.png"), "PNG", optimize=True)


if __name__ == "__main__":
    main()
