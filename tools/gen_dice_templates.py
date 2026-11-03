"""Generate SVG templates for dice-set designers (see docs/dice-assets.md).

Usage: python3 tools/gen_dice_templates.py docs/dice-templates

The shape tables (SHAPES_2D, FACES_3D) are also imported by tools/gen_dice_examples.py.

Geometry mirrors the app:
- 2D silhouettes: ui/dice/DiceShapes.kt (fractions of a square canvas) and label offsets in DiceIcon.kt.
- 3D face templates: ui/dice/render3d/DieModel.kt + FaceTemplate in Dice3DCanvas.kt. A face texture
  is drawn into the square [-r, r] around the face centre, r = the face's circumradius, with the
  face's "up" toward the top of the image.
"""
import math
import os
import sys

SIZE = 512
OUT = "docs/dice-templates"


def svg(name, body, title):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" '
            f'viewBox="0 0 {SIZE} {SIZE}">\n<title>{title}</title>\n'
            f'<rect width="{SIZE}" height="{SIZE}" fill="#ffffff"/>\n'
            f'<path d="M0 0H{SIZE}V{SIZE}H0Z" fill="none" stroke="#bbbbbb" stroke-dasharray="6 6"/>\n'
            f"{body}</svg>\n"
        )


def poly(points, **attrs):
    d = "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in points) + " Z"
    extra = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in attrs.items())
    return f'<path d="{d}" {extra}/>\n'


def regular(n, rot_deg, cx=0.5, cy=0.5, r=0.48):
    return [
        (cx + r * math.cos(math.radians(rot_deg + 360 * i / n)), cy + r * math.sin(math.radians(rot_deg + 360 * i / n)))
        for i in range(n)
    ]


OUTLINE = dict(fill="#e3ecfb", stroke="#2f5b9a", stroke_width=3)
SAFE = dict(fill="none", stroke="#e53935", stroke_width=2, stroke_dasharray="8 6")


def label_box(cx, cy, w, h, text):
    x, y = (cx - w / 2) * SIZE, (cy - h / 2) * SIZE
    return (
        f'<rect x="{x:.1f}" y="{y:.1f}" width="{w * SIZE:.1f}" height="{h * SIZE:.1f}" fill="none" '
        f'stroke="#e53935" stroke-width="2" stroke-dasharray="8 6"/>\n'
        f'<text x="{cx * SIZE:.1f}" y="{(cy + h / 2) * SIZE + 22:.1f}" font-family="sans-serif" font-size="18" '
        f'fill="#e53935" text-anchor="middle">{text}</text>\n'
    )


# ---------- 2D silhouettes (DiceShapes.kt) ----------
SHAPES_2D = {
    "d4": ([(0.5, 0.04), (0.97, 0.92), (0.03, 0.92)], (0.5, 0.64)),
    "d6": (None, (0.5, 0.5)),
    "d8": ([(0.5, 0.02), (0.98, 0.5), (0.5, 0.98), (0.02, 0.5)], (0.5, 0.5)),
    "d10": ([(0.5, 0.02), (0.98, 0.36), (0.98, 0.5), (0.5, 0.98), (0.02, 0.5), (0.02, 0.36)], (0.5, 0.42)),
    "d12": (regular(5, -90), (0.5, 0.5)),
    "d20": (regular(6, -90), (0.5, 0.5)),
    "dN": ("circle", (0.5, 0.5)),
}

# d100 = two d10 kites scaled to 46% at x = 12% and 42%, top at 10%; caption centred at 78%.
D100_KITE_OFFSETS = (0.12, 0.42)
D100_KITE_SCALE = 0.46
D100_KITE_TOP = 0.1

def write_2d():
    for key, (shape, (lx, ly)) in SHAPES_2D.items():
        if shape is None:
            r = 0.14 * SIZE
            body = f'<rect x="0" y="0" width="{SIZE}" height="{SIZE}" rx="{r}" fill="#e3ecfb" stroke="#2f5b9a" stroke-width="3"/>\n'
        elif shape == "circle":
            body = f'<circle cx="{SIZE / 2}" cy="{SIZE / 2}" r="{SIZE / 2 - 2}" fill="#e3ecfb" stroke="#2f5b9a" stroke-width="3"/>\n'
        else:
            body = poly([(x * SIZE, y * SIZE) for x, y in shape], **OUTLINE)
        body += label_box(lx, ly, 0.5, 0.26, "label safe area (app draws text)")
        svg(f"2d/{key}.svg", body, f"{key} 2D silhouette template")

    # d100: two smaller overlapping d10 kites on one pill, caption below (DiceIcon.kt PercentilePair).
    kite = SHAPES_2D["d10"][0]
    body = '<rect x="20" y="40" width="472" height="270" rx="135" fill="#eef1f6"/>\n'
    for ox in D100_KITE_OFFSETS:
        body += poly([((ox + x * D100_KITE_SCALE) * SIZE, (D100_KITE_TOP + y * D100_KITE_SCALE) * SIZE) for x, y in kite], **OUTLINE)
    body += label_box(0.5, 0.78, 0.5, 0.14, "caption safe area")
    svg("2d/d100.svg", body, "d100 2D template (two d10 kites)")


# ---------- 3D face templates (DieModel.kt) ----------
def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    length = math.sqrt(dot(a, a))
    return tuple(x / length for x in a)


def face_template(points, normal, square=False):
    c = tuple(sum(p[i] for p in points) / len(points) for i in range(3))
    if square:
        raw = sub(tuple((points[0][i] + points[1][i]) / 2 for i in range(3)), c)
    else:
        raw = sub(max(points, key=lambda p: dot(sub(p, c), sub(p, c))), c)
    up = norm(sub(raw, tuple(normal[i] * dot(raw, normal) for i in range(3))))
    right = cross(up, normal)
    pts = [(dot(sub(p, c), right), -dot(sub(p, c), up)) for p in points]
    # order counter-clockwise around the centre (screen coordinates)
    pts.sort(key=lambda q: math.atan2(q[1], q[0]))
    return pts


def regular_face(n, up_to_vertex=True):
    start = -90 if up_to_vertex else -90 + 180 / n
    return [(math.cos(math.radians(start + 360 * i / n)), math.sin(math.radians(start + 360 * i / n))) for i in range(n)]


def d10_face():
    a = 0.1
    c36 = math.cos(math.pi / 5)
    apex = (0.0, 0.0, a * (1 + c36) / (1 - c36))
    ring = [(math.cos(k * math.pi / 5), math.sin(k * math.pi / 5), a if k % 2 == 0 else -a) for k in range(10)]
    pts = [apex, ring[0], ring[1], ring[2]]
    n = norm(cross(sub(pts[1], pts[0]), sub(pts[3], pts[0])))
    if dot(n, pts[0]) < 0:
        n = tuple(-x for x in n)
    return face_template(pts, n)


FACES_3D = {
    "d4": regular_face(3),
    "d6": regular_face(4, up_to_vertex=False),
    "d8": regular_face(3),
    "d10": d10_face(),
    "d12": regular_face(5),
    "d20": regular_face(3),
}


def inradius(pts):
    best = float("inf")
    for i in range(len(pts)):
        (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
        best = min(best, abs(x1 * y2 - x2 * y1) / math.hypot(x2 - x1, y2 - y1))
    return best


def write_3d():
    for key, pts in FACES_3D.items():
        r = max(math.hypot(x, y) for x, y in pts)
        scale = (SIZE / 2) / r
        screen = [(SIZE / 2 + x * scale, SIZE / 2 + y * scale) for x, y in pts]
        ir = inradius(pts) * scale
        body = poly(screen, **OUTLINE)
        body += f'<circle cx="{SIZE / 2}" cy="{SIZE / 2}" r="{ir * 0.8:.1f}" fill="none" stroke="#e53935" stroke-width="2" stroke-dasharray="8 6"/>\n'
        body += f'<path d="M{SIZE / 2 - 10} {SIZE / 2}H{SIZE / 2 + 10}M{SIZE / 2} {SIZE / 2 - 10}V{SIZE / 2 + 10}" stroke="#e53935" stroke-width="2"/>\n'
        body += f'<path d="M{SIZE / 2} 30 l-12 18 h24 z" fill="#2f5b9a"/>\n'
        body += f'<text x="{SIZE / 2}" y="{SIZE - 14}" font-family="sans-serif" font-size="18" fill="#e53935" text-anchor="middle">{key} face · number safe circle · arrow = up</text>\n'
        svg(f"3d/{key}_face.svg", body, f"{key} 3D face texture template")
        if key == "d10":
            svg("3d/d100_face.svg", body.replace("d10 face", "d100 (tens) face"), "d100 tens die face template")


def main():
    global OUT
    OUT = sys.argv[1] if len(sys.argv) > 1 else OUT
    write_2d()
    write_3d()
    print("Templates written to", OUT)


if __name__ == "__main__":
    main()
