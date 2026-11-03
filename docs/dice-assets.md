# Dice sets: asset requirements

This guide is for designers who want to restyle the dice or build a custom **dice set**.

The app has two dice looks:

| Where | Look | What a set can replace |
|---|---|---|
| Palette (the "add a die" buttons) and the dice tray with **Quick roll on** | **2D** flat silhouettes | Each die's whole image, colours and font |
| Dice tray with **Quick roll off** (swipe to roll) | **3D**: shaded solids drawn by the app | Colours, edge width, a texture per face, the number font, and pips on the d6 |

The 3D shapes are drawn by the app's renderer. A set can't supply 3D models; it supplies the surface look.

Every item is optional. Anything a set leaves out, or gets wrong, falls back to the built-in look, so a set can be as small as a single colour.

> Sets can be uploaded as a ZIP, or built die by die, in **Settings → Dice → Dice sets** (see section 6). The app ships with one built-in set, `default`. Ready-made examples are in [`docs/dice-examples/`](dice-examples/).

---

## 1. Set folder layout

Built-in sets live in `app/src/main/assets/dice_sets/<set-id>/`. Uploaded sets are stored in the app's private files (`filesDir/dice_sets/<set-id>/`) with the same layout.

```
<set-id>/
├── manifest.json          required
├── 2d/                    optional: 2D silhouettes
│   ├── d4.png  d6.png  d8.png  d10.png  d12.png  d20.png
│   ├── d100.png           (the two-kite design, see 2.3)
│   └── dN.png             (custom dice: d3, d7, d30 …)
├── 3d/                    optional: face textures for the 3D dice
│   └── d6_face.png …
└── fonts/                 optional
    └── numbers.ttf
```

- `<set-id>` can use 1 to 40 characters: `a-z`, `0-9`, `_` and `-`.
- File names are free-form. The manifest refers to them by path.

---

## 2. 2D silhouettes

### 2.1 Image requirements

| Property | Requirement |
|---|---|
| Format | PNG or WebP (lossless preferred) |
| Size | **512 × 512 px**. Anything larger is scaled down to at most 1024 px. |
| Background | **Transparent** |
| Composition | The silhouette fills the canvas edge to edge, centred. Templates are in `docs/dice-templates/2d/`. |
| Text | **Leave the label area empty.** The app draws the die name (`d6`) or the rolled value (`5`) on top, in the set's `text` colour and font. |
| Outline | Draw it inside the image. When a 2D image is used, the app doesn't add its own outline. |
| Display sizes | 56 dp in the palette and 44 dp in the dice tray, which is roughly 150 and 120 px on a typical phone. Keep the details bold. |

### 2.2 Shapes and label safe areas

The red dashed boxes in the templates mark where the app places text. Areas are fractions of the canvas.

| Die | Default shape | Label centre | Notes |
|---|---|---|---|
| d4 | triangle, point up | 50% × 64% | the label sits low, where the triangle is wide |
| d6 | rounded square (14% corners) | centre | |
| d8 | diamond | centre | |
| d10 | kite: point at top, shoulders at 36–50% | 50% × 42% | |
| d12 | pentagon, point up | centre | |
| d20 | hexagon, point up | centre | the default draws a faint inner triangle |
| dN | circle | centre | used for every non-standard die; it's a sphere in 3D |
| d100 | **two smaller d10 kites side by side** | caption under the kites, 50% × 78% | see 2.3 |

### 2.3 d100 (percentile)

A d100 is shown the way tabletop players roll it: **two ten-sided dice**.
- The **tens** die reads `00, 10 … 90`.
- The **units** die reads `0 … 9`.

For example, 73 is shown as `70` + `3`, and 100 as `00` + `0`.

- **It must read as one roll, not two separate d10s.** The two kites are drawn smaller, overlapping, on one rounded pill.
- **Palette:** `2d/d100.png` should show that grouped pair, with the caption area below it left empty. The app writes "d100" there.
- **Results:** each d100 result is drawn as the grouped pair, e.g. `70` + `3`, in both 2D and 3D. The tens die uses the **d100** style and the units die uses the **d10** style, so a set can give them different colours.

---

## 3. 3D dice

### 3.1 What the renderer does

- **Solids:** d4 tetrahedron, d6 cube, d8 octahedron, d10 pentagonal trapezohedron, d12 dodecahedron, d20 icosahedron. A custom dN is drawn as a **shaded sphere** with the rolled number on it, so face textures don't apply to dN. A d100 is drawn as two smaller d10s on a shared pill, as in 2.3.
- **d10 faces read `0`–`9`.** A roll of 10 shows `0`, like real d10s, but it still counts as 10 in the total. Opposite faces add up to 9.
- **Shading:** each face gets the set's `fill` colour, darkened by a fixed light that comes from the upper left. Edges are stroked with `edge` at `edgeWidth`.
- **Numbers:** they are printed on every face that points toward the viewer, warped onto that face. Opposite faces add up to N+1 (the d10 is the exception, see above), and 6 and 9 are underlined.
- **Resting position:** the rolled value ends up on the face pointing toward the viewer, upright. The tray is seen from a fixed slight angle so the dice show their depth.

### 3.2 Face textures (optional)

A face texture is an image painted onto **every face** of that die type, under the number.

| Property | Requirement |
|---|---|
| Format | PNG or WebP, **square, 512 × 512 px** recommended |
| Mapping | The image square maps to a square around the face centre. Its half-width is the face's **circumradius**, the distance from the centre to the farthest corner. Anything outside the face outline is clipped. |
| Orientation | The top of the image is the face's "up". On triangles, pentagons and kites it points to a corner. On the d6 it points to an edge. |
| Number area | Keep the dashed **number safe circle** readable: avoid strong detail or high contrast there. |
| Transparency | Allowed. The `fill` colour and shading show through. |
| Templates | `docs/dice-templates/3d/<die>_face.svg` show the exact face outline, the centre, the number safe circle and the up arrow. |

These are the face shapes:

| Die | Face |
|---|---|
| d4, d8, d20 | triangle |
| d6 | square |
| d10, d100 | kite |
| d12 | pentagon |

### 3.3 Fonts and pips

- `font` is a TTF or OTF file used for every number and label, in both 2D and 3D. Bold, high-contrast faces work best. Digits must include `0` to `9`.
- `"pips": true` on **d6** draws dots instead of numbers on the 3D die. The 2D die still shows the number.

---

## 4. `manifest.json`

```json
{
  "formatVersion": 1,
  "id": "wood_set",
  "name": "Wood",
  "style": {
    "fill": "#8D6E63",
    "edge": "#3E2723",
    "text": "#FFF8E1",
    "edgeWidth": 1.5,
    "font": "fonts/numbers.ttf"
  },
  "dice": {
    "d6":   { "image2d": "2d/d6.png", "faceTexture3d": "3d/d6_face.png", "pips": true },
    "d20":  { "fill": "#5D4037", "image2d": "2d/d20.png" },
    "d100": { "fill": "#4E342E", "image2d": "2d/d100.png" },
    "dN":   { "image2d": "2d/dN.png" }
  }
}
```

### 4.1 Fields

| Field | Type | Meaning |
|---|---|---|
| `formatVersion` | int, required | Must be `1`. |
| `id` | string, required | Same as the folder name. |
| `name` | string, required | Shown to users once set selection exists. |
| `style` | object | Defaults for every die. |
| `dice` | object | Per-die overrides keyed `d4`, `d6`, `d8`, `d10`, `d12`, `d20`, `d100` or `dN`. A field that's left out inherits from `style`. |

Each style object (in `style` or in a `dice` entry) can have:

| Field | Type | Meaning |
|---|---|---|
| `fill` | colour | Body colour of the die. |
| `edge` | colour | Outline colour. Also used for the d100 caption. |
| `text` | colour | Numbers and labels. |
| `edgeWidth` | number, 0–8 | Outline width in dp. |
| `font` | path | `.ttf` or `.otf` |
| `image2d` | path | `.png` or `.webp`, replaces the 2D silhouette. Per die only; it isn't inherited from `style`. |
| `faceTexture3d` | path | `.png` or `.webp`, see 3.2. |
| `pips` | bool | d6 only. |

Colours can be written as `#RRGGBB`, as `#AARRGGBB` (with alpha), or as `"theme"`. `"theme"` uses the app's Material colours, so it follows light and dark mode and dynamic colour.

Paths are relative to the set folder. They can't be absolute or contain `..`, `.` or backslashes.

### 4.2 Validation and fallback

`DiceSetValidator` checks the manifest when it loads:
- **The whole set is skipped,** and the built-in look is used, when the JSON is invalid, `formatVersion` is unsupported, or required fields are missing.
- **Only the faulty field is skipped,** and it falls back, when a colour is malformed, a die key is unknown, a path or extension is bad, `edgeWidth` is out of range, or `pips` is set on a die other than d6.
- **A missing or undecodable file** also falls back.

Every problem is written to logcat under the tag `DiceSets`.

---

## 5. Checklist for a new set

1. Copy `dice_sets/default/` to `dice_sets/<your-id>/` and change `id` and `name`.
2. Set the `style` colours, and test them in light and dark theme.
3. *(optional)* Export 2D silhouettes from the `docs/dice-templates/2d/` templates. Keep the label areas clear.
4. *(optional)* Export face textures from `docs/dice-templates/3d/`. Keep the number safe circle calm.
5. *(optional)* Add a font and reference it in `style.font`.
6. Validate the set: the unit test `DiceSetValidatorTest` shows the rules. A broken field only shows up as a fallback plus a logcat warning.
7. Zip the set folder and upload it in **Settings → Dice sets → Upload ZIP** (section 6). It becomes the active set.

To regenerate the templates:

```bash
python3 tools/gen_dice_templates.py docs/dice-templates
```

---

## 6. Uploading a set in the app

**Settings → Dice → Dice sets** lists the built-in and uploaded sets. The selected one is used everywhere and is remembered (`dice.active_set` in DataStore). The **?** button shows a short version of this guide.

### 6.1 ZIP pack (Upload ZIP)

- The ZIP holds the set folder from section 1. One wrapper folder around it is fine (e.g. `wood/manifest.json`).
- **Files are matched by name**, case-insensitively, with or without a manifest:

  | File | Becomes |
  |---|---|
  | `d6.png`, `2d/d6.png` (or `.webp`) | `dice.d6.image2d` |
  | `d6_face.png`, `3d/d6_face.png` | `dice.d6.faceTexture3d` (not for `dN`) |
  | first `*.ttf` / `*.otf` at the root or in `fonts/` | `style.font` |

- **`manifest.json` is optional.** Without it, the colours follow the app theme and the set is named after the ZIP file. With it, the manifest only needs what it changes (for example just colours): the conventions above fill in the rest, and fields the manifest sets explicitly always win.
- Dark images need a light `text` colour in the manifest; the theme's number colour is dark in light mode.
- **id:** taken from the manifest (or the file name), made safe (`a-z 0-9 _ -`). An `id` used by a built-in set gets a suffix (`default_2`); an `id` of an uploaded set **replaces** it, so re-uploading updates a set.
- **Limits:** at most 300 entries and 50 MB unpacked; images up to 10 MB, fonts up to 5 MB, the manifest up to 256 KB. Oversized and unsupported files are skipped and counted in the result message, as are files the manifest names but the ZIP lacks.
- **Rejected:** files that aren't ZIPs (checked by the `PK` signature), entries with `..` or absolute paths (zip-slip), and an invalid manifest or `formatVersion`. Nothing is changed when an upload fails.

### 6.2 Building a set die by die (New set)

- **New set** creates an empty set that follows the app theme, and opens the editor.
- Tap a die to upload or remove its **2D image** and **3D face texture** with the Android Photo Picker (dN has no face texture).
- Picked images (PNG, WebP or JPEG) are decoded, scaled down to at most 1024 px, and stored as `2d/<die>.png` and `3d/<die>_face.png`. JPEG has no transparency, so prefer PNG for 2D images.
- The editor also works on uploaded sets. Built-in sets can't be edited.

### 6.3 Examples

`docs/dice-examples/` is generated by `python3 tools/gen_dice_examples.py` (needs Pillow) from the same shapes as the templates. The unit test `DiceSetExamplesTest` checks that both packs import cleanly.

- `wood.zip`: a full pack with a manifest, colours, 2D images for all eight dice, face textures, pips on the d6 and a darker d20.
- `neon.zip`: a manifest with colours only (dark body, cyan, pink and green edges and numbers); four images and the seven-segment font `fonts/segments.ttf` (generated by `tools/segment_font.py`) are found by file name.
