"""Generate the Dice familiar app icon: a small fairy dragon shaped like a gecko (seen from above).

Usage: python3 tools/gen_app_icon.py [--preview out_dir]

One shape list drives every output so they always match:
- app/src/main/res/drawable/ic_launcher_foreground.xml   (adaptive icon foreground, 108x108dp)
- app/src/main/res/drawable/ic_launcher_background.xml   (adaptive icon background)
- app/src/main/res/drawable/ic_launcher_monochrome.xml   (themed icon, Android 13+)
- with --preview: SVG files for checking the artwork (rasterize with ImageMagick).
Legacy PNG icons for API 24-25 are rendered from the SVG by tools/render_legacy_icons.sh.

Everything important stays inside the adaptive-icon safe circle (radius ~33 around 54,54).
"""
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Palette
BG = "#2B2350"
BG_GLOW = "#3D3373"
SPARKLE = "#FFE9A8"
BODY = "#7ED69A"
BODY_DARK = "#1E5A43"
SPOTS = "#4DB477"
TOE = "#A5E8B8"
WING = "#C9F0FF"
WING_EDGE = "#7FC8EC"
EYE = "#FFE07A"
PUPIL = "#1B1B2F"
HORN = "#F4B95A"
OUTLINE_W = 2.4


def ellipse(cx, cy, rx, ry):
    return (
        f"M{cx - rx:.2f},{cy:.2f} "
        f"A{rx:.2f},{ry:.2f} 0 1,0 {cx + rx:.2f},{cy:.2f} "
        f"A{rx:.2f},{ry:.2f} 0 1,0 {cx - rx:.2f},{cy:.2f} Z"
    )


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def polyline(points):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in points)


def mirror_x(points):
    return [(108 - x, y) for x, y in points]


# A shape: dict(d=..., fill=..., fill_alpha=1, stroke=None, width=0, mono=True/False/alpha)
def fill(d, color, alpha=1.0, mono=1.0):
    return dict(d=d, fill=color, alpha=alpha, stroke=None, width=0, mono=mono)


def stroke(d, color, width, alpha=1.0, mono=1.0):
    return dict(d=d, fill=None, alpha=alpha, stroke=color, width=width, mono=mono)


# ---------------- geometry ----------------
HEAD = (54, 37, 10.5, 8.6)
TORSO = (54, 55, 8.2, 12.5)
LEGS = [  # shoulder, elbow, foot
    [(48.5, 48), (41, 46), (36.5, 40.5)],
    [(49, 62), (41.5, 65), (37.5, 71)],
]
LEG_W = 3.8
TAIL = [  # (path, width) pieces getting thinner toward the curled tip
    ("M54,66 C54,72 56,76 61,78", 6.0),
    ("M61,78 C66,80 71,78 72,73.5", 4.6),
    ("M72,73.5 C73,69.5 69.5,67.4 67.2,69.2", 3.0),
]


def toe_pads(elbow, foot):
    dx, dy = foot[0] - elbow[0], foot[1] - elbow[1]
    base = math.atan2(dy, dx)
    pads = []
    for deg in (-55, -18, 18, 55):
        a = base + math.radians(deg)
        pads.append((foot[0] + 3.1 * math.cos(a), foot[1] + 3.1 * math.sin(a)))
    return pads


def all_legs():
    legs = []
    for leg in LEGS:
        legs.append(leg)
        legs.append(mirror_x(leg))
    return legs


def wing_left():
    # Two-lobed fairy wing from the shoulder: a big upper lobe and a smaller lower lobe.
    return (
        "M50.5,47 "
        "C44,40 36,31.5 30.5,30.5 "
        "C26,29.8 24.6,34.5 27.4,38.4 "
        "C30,42 35.5,45.5 40,47.6 "
        "C35,48.6 29.6,51.6 29.2,55 "
        "C28.9,58 32.4,59.2 36,57.8 "
        "C41,55.8 46.5,51.5 50.5,49 Z"
    )


def wing_veins_left():
    return "M49.5,47.6 C42,42 35,36 29.5,33.6 M49.5,48.6 C43,50.6 36,53.6 31,56.4"


def mirror_path(d):
    """Mirror an absolute-coordinate path (M/C/L/Z only) across x = 54."""
    # Mirror every x in "x,y" pairs.
    result = []
    parts = d.split()
    for p in parts:
        cmd = ""
        if p[0].isalpha():
            cmd, p = p[0], p[1:]
        if "," in p:
            x, y = p.split(",")
            result.append(f"{cmd}{108 - float(x):.2f},{y}")
        else:
            result.append(cmd + p)
    return " ".join(result)


def body_shapes(outline):
    """Body parts as one silhouette. outline=True gives the dark outline pass (wider strokes)."""
    extra = OUTLINE_W if outline else 0
    color = BODY_DARK if outline else BODY
    shapes = []
    for d, w in TAIL:
        shapes.append(stroke(d, color, w + extra))
    for leg in all_legs():
        shapes.append(stroke(polyline(leg), color, LEG_W + extra))
    for leg in all_legs():
        for (x, y) in toe_pads(leg[1], leg[2]):
            r = 1.55 + extra / 2
            shapes.append(fill(circle(x, y, r), BODY_DARK if outline else TOE))
    shapes.append(fill(ellipse(*TORSO[:2], TORSO[2] + extra / 2, TORSO[3] + extra / 2), color))
    shapes.append(fill(ellipse(54, 45.5, 4.6 + extra / 2, 4.5 + extra / 2), color))  # neck
    shapes.append(fill(ellipse(*HEAD[:2], HEAD[2] + extra / 2, HEAD[3] + extra / 2), color))
    return shapes


def foreground():
    shapes = []
    # Wings (behind the body), translucent.
    wl = wing_left()
    for d in (wl, mirror_path(wl)):
        shapes.append(fill(d, WING, alpha=0.82, mono=0.55))
        shapes.append(stroke(d, WING_EDGE, 1.0, mono=0))
    for d in (wing_veins_left(), mirror_path(wing_veins_left())):
        shapes.append(stroke(d, WING_EDGE, 0.8, alpha=0.9, mono=0))
    # Horns behind the head.
    horn_l = "M48.8,31 C46.6,28.2 45.4,25.4 45.6,22.6 C47.6,24.6 49.8,27 51.4,29.6 Z"
    for d in (horn_l, mirror_path(horn_l)):
        shapes.append(fill(d, HORN))
        shapes.append(stroke(d, BODY_DARK, 1.0, mono=0))
    # Body silhouette: outline pass then fill pass so overlaps don't show seams.
    shapes += [dict(s, mono=0) for s in body_shapes(outline=True)]
    shapes += body_shapes(outline=False)
    # Back spots / spine.
    for (x, y, r) in [(54, 47.5, 1.3), (54, 52.5, 1.5), (54, 57.5, 1.5), (54, 62.5, 1.2),
                      (50.2, 55, 1.1), (57.8, 55, 1.1), (50.8, 60.5, 0.9), (57.2, 60.5, 0.9)]:
        shapes.append(fill(circle(x, y, r), SPOTS, mono=0))
    # Big gecko eyes on the sides of the head, with slit pupils and a highlight.
    for ex in (46.2, 61.8):
        shapes.append(fill(circle(ex, 35.2, 4.1), BODY_DARK, mono=0))
        shapes.append(fill(circle(ex, 35.2, 3.3), EYE, mono=0))
        shapes.append(fill(ellipse(ex, 35.4, 0.95, 2.6), PUPIL, mono=0))
        shapes.append(fill(circle(ex - 1.1, 34.0, 0.8), "#FFFFFF", mono=0))
    # Nostrils and a small smile at the snout.
    for nx in (51.6, 56.4):
        shapes.append(fill(circle(nx, 30.6, 0.55), BODY_DARK, mono=0))
    shapes.append(stroke("M50.2,40.6 C52.4,42.2 55.6,42.2 57.8,40.6", BODY_DARK, 0.9, mono=0))
    # Blush cheeks.
    for cx in (48.4, 59.6):
        shapes.append(fill(ellipse(cx, 40.2, 1.5, 0.9), "#FF9FB3", alpha=0.75, mono=0))
    return shapes


def sparkle(cx, cy, r):
    k = r * 0.28
    return (
        f"M{cx},{cy - r} C{cx + k},{cy - k} {cx + k},{cy - k} {cx + r},{cy} "
        f"C{cx + k},{cy + k} {cx + k},{cy + k} {cx},{cy + r} "
        f"C{cx - k},{cy + k} {cx - k},{cy + k} {cx - r},{cy} "
        f"C{cx - k},{cy - k} {cx - k},{cy - k} {cx},{cy - r} Z"
    )


def background():
    return [
        fill("M0,0 H108 V108 H0 Z", BG),
        fill(circle(54, 54, 34), BG_GLOW),
        fill(sparkle(31, 70, 3.2), SPARKLE, alpha=0.9),
        fill(sparkle(79, 46, 2.6), SPARKLE, alpha=0.85),
        fill(sparkle(77, 80, 1.8), SPARKLE, alpha=0.7),
        fill(sparkle(35, 28, 1.6), SPARKLE, alpha=0.6),
    ]


# ---------------- writers ----------------
FG_SCALE = 0.9  # keeps wing tips inside launcher masks


def vector_xml(shapes, mono=False, scale=1.0):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by tools/gen_app_icon.py - edit the script, not this file. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ]
    indent = "    "
    if scale != 1.0:
        lines.append(
            f'    <group android:pivotX="54" android:pivotY="54" '
            f'android:scaleX="{scale}" android:scaleY="{scale}">'
        )
        indent = "        "
    for s in shapes:
        alpha = s["alpha"]
        if mono:
            if not s["mono"]:
                continue
            alpha = s["mono"] * (1 if s["fill"] else 1)
        attrs = [f'android:pathData="{s["d"]}"']
        if s["fill"]:
            attrs.append(f'android:fillColor="{"#FFFFFFFF" if mono else s["fill"]}"')
            if alpha < 1:
                attrs.append(f'android:fillAlpha="{alpha:.2f}"')
        if s["stroke"]:
            attrs.append(f'android:strokeColor="{"#FFFFFFFF" if mono else s["stroke"]}"')
            attrs.append(f'android:strokeWidth="{s["width"]:.2f}"')
            attrs.append('android:strokeLineCap="round"')
            attrs.append('android:strokeLineJoin="round"')
            if alpha < 1:
                attrs.append(f'android:strokeAlpha="{alpha:.2f}"')
        lines.append(f"{indent}<path\n{indent}    " + f"\n{indent}    ".join(attrs) + " />")
    if scale != 1.0:
        lines.append("    </group>")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def svg(shapes, size=108, fg_from=None):
    """fg_from: index where foreground shapes start (they get the FG_SCALE transform)."""
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">']
    for i, s in enumerate(shapes):
        if fg_from is not None and i == fg_from:
            out.append(f'<g transform="translate(54 54) scale({FG_SCALE}) translate(-54 -54)">')
        a = s["alpha"]
        if s["fill"]:
            out.append(f'<path d="{s["d"]}" fill="{s["fill"]}" fill-opacity="{a}"/>')
        if s["stroke"]:
            out.append(
                f'<path d="{s["d"]}" fill="none" stroke="{s["stroke"]}" stroke-width="{s["width"]}" '
                f'stroke-linecap="round" stroke-linejoin="round" stroke-opacity="{a}"/>'
            )
    if fg_from is not None:
        out.append("</g>")
    out.append("</svg>")
    return "\n".join(out) + "\n"


def main():
    fg, bg = foreground(), background()
    drawable = os.path.join(RES, "drawable")
    with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector_xml(fg, scale=FG_SCALE))
    with open(os.path.join(drawable, "ic_launcher_background.xml"), "w") as f:
        f.write(vector_xml(bg))
    with open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector_xml(fg, mono=True, scale=FG_SCALE))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, "icon_full.svg"), "w") as f:
            f.write(svg(bg + fg, size=1080, fg_from=len(bg)))
    print("Icon drawables written")


if __name__ == "__main__":
    main()
