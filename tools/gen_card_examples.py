"""Generate the example card decks in docs/card-examples/ (deterministic, original artwork).

Usage: python3 tools/gen_card_examples.py [OUT_DIR]   (default: docs/card-examples)
Needs Pillow. Rerunning without changes gives byte-identical files.

- omens.zip        54 cards, ZIP with cards.csv + images + back.png. Four original suits:
                   Rams (in place of hearts), Crows (spades), Books (clubs), Masks (diamonds),
                   values 1-13, plus a Red and a Black Joker.
- runes.zip        16 cards, ZIP of images only (names come from the file names).
- tarot-major.csv  22 cards, a plain CSV (text cards, no images).
"""
import io
import math
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

W, H = 500, 700
ZIP_DATE = (2026, 1, 1, 0, 0, 0)
PAPER = (246, 238, 222)
INK = (40, 32, 26)

SUITS = [
    # name, colour, glyph
    ("Rams", (168, 36, 42), "ram"),
    ("Crows", (30, 30, 36), "crow"),
    ("Books", (32, 62, 120), "book"),
    ("Masks", (104, 44, 120), "mask"),
]


def font(size):
    return ImageFont.load_default(size=size)


# --- Original suit glyphs, drawn from simple shapes inside the box (cx, cy, s) -------------------

def ram(d, cx, cy, s, c):
    # A ram's head from the front: a long tapering snout, ears, and thick spiral horns curling down.
    w = max(3, int(s * 0.16))
    for side in (-1, 1):
        hx = cx + side * s * 0.42
        box = [hx - s * 0.32, cy - s * 0.5, hx + s * 0.32, cy + s * 0.14]
        start, end = (180, 450) if side < 0 else (90, 360)
        d.arc(box, start, end, fill=c, width=w)
        d.ellipse([hx - s * 0.1, cy - s * 0.24, hx + s * 0.1, cy - s * 0.04], fill=c)
        d.polygon([(cx + side * s * 0.18, cy - s * 0.18), (cx + side * s * 0.5, cy + s * 0.1), (cx + side * s * 0.22, cy + s * 0.05)], fill=c)
    d.polygon([
        (cx - s * 0.24, cy - s * 0.42), (cx + s * 0.24, cy - s * 0.42), (cx + s * 0.2, cy + s * 0.2),
        (cx + s * 0.12, cy + s * 0.55), (cx - s * 0.12, cy + s * 0.55), (cx - s * 0.2, cy + s * 0.2),
    ], fill=c)
    d.ellipse([cx - s * 0.15, cy - s * 0.18, cx - s * 0.06, cy - s * 0.09], fill=PAPER)
    d.ellipse([cx + s * 0.06, cy - s * 0.18, cx + s * 0.15, cy - s * 0.09], fill=PAPER)
    d.ellipse([cx - s * 0.08, cy + s * 0.4, cx - s * 0.02, cy + s * 0.46], fill=PAPER)
    d.ellipse([cx + s * 0.02, cy + s * 0.4, cx + s * 0.08, cy + s * 0.46], fill=PAPER)


def crow(d, cx, cy, s, c):
    # A crow in profile: body, head, beak, tail and a raised wing.
    d.ellipse([cx - s * 0.45, cy - s * 0.15, cx + s * 0.3, cy + s * 0.35], fill=c)
    d.ellipse([cx + s * 0.12, cy - s * 0.42, cx + s * 0.5, cy - s * 0.04], fill=c)
    d.polygon([(cx + s * 0.46, cy - s * 0.28), (cx + s * 0.75, cy - s * 0.2), (cx + s * 0.46, cy - s * 0.14)], fill=c)
    d.polygon([(cx - s * 0.35, cy + s * 0.1), (cx - s * 0.8, cy + s * 0.32), (cx - s * 0.7, cy + s * 0.05)], fill=c)
    d.polygon([(cx - s * 0.2, cy), (cx + s * 0.05, cy - s * 0.7), (cx + s * 0.15, cy)], fill=c)
    d.ellipse([cx + s * 0.3, cy - s * 0.3, cx + s * 0.38, cy - s * 0.22], fill=PAPER)
    d.line([(cx - s * 0.05, cy + s * 0.3), (cx - s * 0.1, cy + s * 0.55)], fill=c, width=max(2, int(s * 0.06)))
    d.line([(cx + s * 0.1, cy + s * 0.3), (cx + s * 0.12, cy + s * 0.55)], fill=c, width=max(2, int(s * 0.06)))


def book(d, cx, cy, s, c):
    # An open book: two pages and a spine.
    for side in (-1, 1):
        x0, x1 = (cx - s * 0.7, cx - s * 0.03) if side < 0 else (cx + s * 0.03, cx + s * 0.7)
        d.polygon([(x0, cy - s * 0.4), (x1, cy - s * 0.3), (x1, cy + s * 0.45), (x0, cy + s * 0.35)], fill=c)
        for k in range(3):
            y = cy - s * 0.18 + k * s * 0.18
            a, b = (x0 + s * 0.1, x1 - s * 0.1)
            d.line([(a, y), (b, y + s * 0.05 * side * 0)], fill=PAPER, width=max(2, int(s * 0.04)))
    d.rectangle([cx - s * 0.04, cy - s * 0.32, cx + s * 0.04, cy + s * 0.47], fill=PAPER)


def mask(d, cx, cy, s, c):
    # A masquerade mask: a wide band with almond eye holes and a ribbon.
    d.polygon([
        (cx - s * 0.75, cy - s * 0.15), (cx - s * 0.4, cy - s * 0.4), (cx, cy - s * 0.25),
        (cx + s * 0.4, cy - s * 0.4), (cx + s * 0.75, cy - s * 0.15), (cx + s * 0.55, cy + s * 0.3),
        (cx + s * 0.15, cy + s * 0.25), (cx, cy + s * 0.05), (cx - s * 0.15, cy + s * 0.25), (cx - s * 0.55, cy + s * 0.3),
    ], fill=c)
    for side in (-1, 1):
        ex = cx + side * s * 0.32
        d.ellipse([ex - s * 0.18, cy - s * 0.15, ex + s * 0.18, cy + s * 0.05], fill=PAPER)
    d.line([(cx - s * 0.75, cy - s * 0.15), (cx - s * 0.95, cy + s * 0.45)], fill=c, width=max(2, int(s * 0.05)))
    d.line([(cx + s * 0.75, cy - s * 0.15), (cx + s * 0.95, cy + s * 0.45)], fill=c, width=max(2, int(s * 0.05)))


GLYPHS = {"ram": ram, "crow": crow, "book": book, "mask": mask}


def frame(d, colour):
    d.rounded_rectangle([12, 12, W - 13, H - 13], radius=28, outline=colour, width=6)
    d.rounded_rectangle([26, 26, W - 27, H - 27], radius=20, outline=colour, width=2)


def suit_card(value, suit_name, colour, glyph):
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    frame(d, colour)
    f = font(58)
    for x, y, anchor in ((48, 44, "la"), (W - 48, H - 44, "rd")):
        d.text((x, y), str(value), font=f, fill=colour, anchor=anchor)
    GLYPHS[glyph](d, 70 + 18, 150, 30, colour)
    GLYPHS[glyph](d, W - 88, H - 150, 30, colour)
    # Centre: a big glyph; higher values get a ring of small marks, so cards read at a glance.
    GLYPHS[glyph](d, W / 2, H / 2, 120, colour)
    for k in range(min(value, 13)):
        a = -math.pi / 2 + 2 * math.pi * k / max(1, min(value, 13))
        x, y = W / 2 + math.cos(a) * 185, H / 2 + math.sin(a) * 225
        d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=colour)
    d.text((W / 2, H - 70), suit_name.upper(), font=font(26), fill=colour, anchor="mm")
    return img


def joker(red):
    colour = (168, 36, 42) if red else (30, 30, 36)
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    frame(d, colour)
    # A jester's star of four suit glyphs.
    for (dx, dy), (_, c, g) in zip(((0, -150), (150, 0), (0, 150), (-150, 0)), SUITS):
        GLYPHS[g](d, W / 2 + dx, H / 2 + dy, 55, colour)
    d.regular_polygon((W / 2, H / 2, 70), 5, rotation=0, fill=colour)
    d.text((W / 2, 70), "JOKER", font=font(54), fill=colour, anchor="mm")
    return img


def back():
    img = Image.new("RGB", (W, H), (58, 30, 24))
    d = ImageDraw.Draw(img)
    gold = (214, 170, 92)
    d.rounded_rectangle([18, 18, W - 19, H - 19], radius=24, outline=gold, width=5)
    for i in range(-H, W, 34):
        d.line([(i, 0), (i + H, H)], fill=(78, 42, 32), width=3)
        d.line([(i + H, 0), (i, H)], fill=(78, 42, 32), width=3)
    for (dx, dy), (_, _, g) in zip(((0, -110), (110, 0), (0, 110), (-110, 0)), SUITS):
        GLYPHS[g](d, W / 2 + dx, H / 2 + dy, 42, gold)
    d.ellipse([W / 2 - 40, H / 2 - 40, W / 2 + 40, H / 2 + 40], outline=gold, width=5)
    return img


def png(img):
    out = io.BytesIO()
    img.save(out, "PNG", optimize=True)
    return out.getvalue()


def write_zip(path, files):
    with zipfile.ZipFile(path, "w") as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[name])


def omens():
    files = {"back.png": png(back())}
    rows = ["name,value,suit,image"]
    for suit_name, colour, glyph in SUITS:
        for value in range(1, 14):
            image = f"images/{suit_name.lower()}_{value:02d}.png"
            files[image] = png(suit_card(value, suit_name, colour, glyph))
            rows.append(f"{value} of {suit_name},{value},{suit_name},{image}")
    for red in (True, False):
        image = f"images/joker_{'red' if red else 'black'}.png"
        files[image] = png(joker(red))
        rows.append(f"{'Red' if red else 'Black'} Joker,Joker,,{image}")
    files["cards.csv"] = ("\n".join(rows) + "\n").encode()
    return files


RUNES = [
    ("01_sun", [((0.5, 0.15), (0.5, 0.85)), ((0.2, 0.35), (0.8, 0.65)), ((0.8, 0.35), (0.2, 0.65))]),
    ("02_moon", [((0.35, 0.15), (0.35, 0.85)), ((0.35, 0.15), (0.7, 0.4)), ((0.7, 0.4), (0.35, 0.6))]),
    ("03_river", [((0.2, 0.3), (0.8, 0.3)), ((0.2, 0.5), (0.8, 0.5)), ((0.2, 0.7), (0.8, 0.7))]),
    ("04_mountain", [((0.15, 0.8), (0.5, 0.2)), ((0.5, 0.2), (0.85, 0.8))]),
    ("05_tree", [((0.5, 0.15), (0.5, 0.85)), ((0.5, 0.3), (0.25, 0.15)), ((0.5, 0.3), (0.75, 0.15)), ((0.5, 0.5), (0.3, 0.35)), ((0.5, 0.5), (0.7, 0.35))]),
    ("06_flame", [((0.3, 0.85), (0.5, 0.15)), ((0.5, 0.15), (0.7, 0.85)), ((0.35, 0.6), (0.65, 0.6))]),
    ("07_key", [((0.5, 0.15), (0.5, 0.85)), ((0.5, 0.7), (0.7, 0.7)), ((0.5, 0.82), (0.65, 0.82))]),
    ("08_gate", [((0.3, 0.15), (0.3, 0.85)), ((0.7, 0.15), (0.7, 0.85)), ((0.3, 0.15), (0.7, 0.15))]),
    ("09_storm", [((0.6, 0.15), (0.35, 0.5)), ((0.35, 0.5), (0.65, 0.5)), ((0.65, 0.5), (0.4, 0.85))]),
    ("10_shield", [((0.25, 0.2), (0.75, 0.2)), ((0.25, 0.2), (0.5, 0.85)), ((0.75, 0.2), (0.5, 0.85))]),
    ("11_eye", [((0.15, 0.5), (0.5, 0.25)), ((0.5, 0.25), (0.85, 0.5)), ((0.85, 0.5), (0.5, 0.75)), ((0.5, 0.75), (0.15, 0.5))]),
    ("12_hand", [((0.5, 0.85), (0.5, 0.3)), ((0.5, 0.45), (0.3, 0.2)), ((0.5, 0.45), (0.7, 0.2))]),
    ("13_star", [((0.5, 0.15), (0.5, 0.85)), ((0.15, 0.5), (0.85, 0.5)), ((0.25, 0.25), (0.75, 0.75)), ((0.75, 0.25), (0.25, 0.75))]),
    ("14_road", [((0.35, 0.15), (0.35, 0.85)), ((0.65, 0.15), (0.65, 0.85)), ((0.35, 0.5), (0.65, 0.5))]),
    ("15_seed", [((0.5, 0.15), (0.5, 0.6)), ((0.3, 0.6), (0.7, 0.6)), ((0.3, 0.6), (0.5, 0.85)), ((0.7, 0.6), (0.5, 0.85))]),
    ("16_crown", [((0.2, 0.7), (0.2, 0.3)), ((0.2, 0.3), (0.4, 0.5)), ((0.4, 0.5), (0.5, 0.25)), ((0.5, 0.25), (0.6, 0.5)), ((0.6, 0.5), (0.8, 0.3)), ((0.8, 0.3), (0.8, 0.7)), ((0.2, 0.7), (0.8, 0.7))]),
]


def runes():
    files = {}
    for name, strokes in RUNES:
        img = Image.new("RGB", (W, H), (34, 40, 52))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([14, 14, W - 15, H - 15], radius=26, outline=(150, 190, 220), width=4)
        box = (90, 150, W - 90, H - 170)
        for (a, b) in strokes:
            p = (box[0] + a[0] * (box[2] - box[0]), box[1] + a[1] * (box[3] - box[1]))
            q = (box[0] + b[0] * (box[2] - box[0]), box[1] + b[1] * (box[3] - box[1]))
            d.line([p, q], fill=(220, 236, 248), width=26)
            for x, y in (p, q):
                d.ellipse([x - 13, y - 13, x + 13, y + 13], fill=(220, 236, 248))
        d.text((W / 2, H - 80), name.split("_", 1)[1].upper(), font=font(40), fill=(150, 190, 220), anchor="mm")
        files[f"runes/{name}.png"] = png(img)
    return files


TAROT = [
    "The Fool", "The Magician", "The High Priestess", "The Empress", "The Emperor", "The Hierophant",
    "The Lovers", "The Chariot", "Strength", "The Hermit", "Wheel of Fortune", "Justice", "The Hanged Man",
    "Death", "Temperance", "The Devil", "The Tower", "The Star", "The Moon", "The Sun", "Judgement", "The World",
]
NUMERALS = ["0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
            "XVI", "XVII", "XVIII", "XIX", "XX", "XXI"]


def tarot_csv():
    rows = ["name,value,suit"] + [f"{name},{num},Major Arcana" for name, num in zip(TAROT, NUMERALS)]
    return ("\n".join(rows) + "\n").encode()


def main(out):
    os.makedirs(out, exist_ok=True)
    write_zip(os.path.join(out, "omens.zip"), omens())
    write_zip(os.path.join(out, "runes.zip"), runes())
    with open(os.path.join(out, "tarot-major.csv"), "wb") as f:
        f.write(tarot_csv())
    # Previews for the docs: a few cards side by side.
    o = omens()
    picks = ["images/rams_01.png", "images/crows_07.png", "images/books_12.png", "images/masks_13.png", "images/joker_red.png", "back.png"]
    sheet = Image.new("RGB", (len(picks) * 260 + 20, 380), (255, 255, 255))
    for i, name in enumerate(picks):
        card = Image.open(io.BytesIO(o[name])).resize((250, 350))
        sheet.paste(card, (10 + i * 260, 15))
    sheet.save(os.path.join(out, "omens-preview.png"), optimize=True)
    print("Example decks written to", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join("docs", "card-examples"))
