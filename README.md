<h1 align="center">Dice Familiar</h1>

<p align="center">
  An Android app for tabletop players: roll dice in 3D and generate random numbers, and later draw cards and pick from lists.
</p>

<p align="center">
  <a href="https://github.com/SkrypkoA/dicefamiliar/raw/master/apk/dicefamiliar.apk"><b>📥 Download the app (APK)</b></a>
  &nbsp;·&nbsp;
  <a href="docs/dice-examples/"><b>🎨 Dice skins examples</b></a>
</p>

<p align="center">
  <sub>Install the APK on Android 7.0+ (allow installs from unknown sources). Then upload a skin from <code>docs/dice-examples/</code> in Settings → Dice sets → Upload ZIP. <a href="#custom-dice-skins">How it works</a>.</sub>
</p>

---

## Contents

- [Features](#features)
  - [Dice roller](#dice-roller)
  - [Random number generator](#random-number-generator)
  - [Themes](#themes)
  - [Settings](#settings)
- [Custom dice skins](#custom-dice-skins)
  - [Option 1: upload a ZIP pack](#option-1-upload-a-zip-pack)
  - [Option 2: build a set in the app](#option-2-build-a-set-in-the-app)
  - [Image requirements](#image-requirements)
  - [Try the examples](#try-the-examples)
- [Requirements](#requirements)
- [Build and run](#build-and-run)
- [Tests and checks](#tests-and-checks)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Dice notation](#dice-notation)
- [Tools](#tools)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature | Status |
|---|---|
| 🎲 **Dice roller** | ✅ Done |
| ⚙️ **Settings** | ✅ Done |
| 🎨 **Themes:** Classic, Tavern, Cyberpunk | ✅ Done |
| 🔢 **Random number generator** | ✅ Done |
| 📋 Pool picker (named lists with images, CSV/ZIP import & export) | 🚧 Planned |
| 🃏 Card drawer (52 cards, jokers, multiple decks) | 🚧 Planned |
| 📡 LAN play: ask another player's phone to roll | 🚧 Planned (the request/response layer is ready) |

### Dice roller
- **Dice panel:** tap a die to add it to the formula, and **long-press a die to remove one**. Tap **?** for a short guide. The panel offers d4, d6, d8, d10, d12, d20, d100 and custom dN dice.
  - **d100** is shown as a percentile pair: a tens d10 (`00`–`90`) plus a units d10 (`0`–`9`).
  - **dN:** the first tap asks for the number of sides. Type it, or swipe up or down on the number. After that, each tap adds the same die.
- **Formula:** `2d6+1d4+3`, `4d6kh3` (keep the highest 3) and `2d20kl1` (keep the lowest). The field is hidden behind the **ƒx** button.
- **Advantage / Disadvantage** is available whenever exactly one die is selected, of any type. It rolls two dice and keeps the higher or the lower.
- **Roll by swiping the tray:** the dice tumble in **3D** in the direction you swiped and land on the rolled faces. With **Quick roll** on, a Roll button and flat 2D dice are used instead.
- **Results:**
  - The sum appears under the tray, e.g. `2d6+1d4+3 = 13`.
  - Dropped dice are faded.
  - Recent rolls open from the history button.
- **Sound, haptics and animations**, each of which can be switched off.
- **Dice skins (dice sets):** upload a ZIP pack, or build your own set by uploading an image for each die. See [Custom dice skins](#custom-dice-skins).

### Random number generator
- **Range and count:** type Min and Max, tap their ▲▼ arrows, or swipe up or down on a field to step it. Both ends are included, and negative numbers are allowed. Then choose how many numbers you want (1–1000). The first time, the range is **1–100**.
- **Integer or Decimal:** in decimal mode, choose 1–6 decimal places. `0` to `1` with 2 places gives values like `0.07` or `1.00`.
- **Allow duplicates:** when it's off, every number is different. If the range has fewer values than you asked for, the Count field says so.
- **Errors are shown under the field** as you type, for example *Must be at least Min*.
- **Remembers your choices:** the range, count, mode and options you used last, and your last result, are still there when you reopen the app.
- **Results:**
  - One number is shown large.
  - Several numbers are shown as chips, with their sum.
  - **Copy** puts them on the clipboard and **Share** sends them to any app, as text such as `7, 42, 13`.
  - **Recent results** keeps the last 20 in a history sheet, which you can clear.
- **Sound, haptics and animations** follow the app settings. Every value is equally likely, and the generator uses `SecureRandom`.

### Themes
Pick one in **Settings → Appearance**. Each card shows a small preview of the theme.

| Theme | Look |
|---|---|
| **Classic** | Clean Material design. Light, dark or following the system, with wallpaper colours on Android 12+. |
| **Tavern** | Warm fantasy. Parchment and leather, gold trim, serif titles, and bone dice rolled on **green billiard cloth** framed in wood. Its icons are a magic wand, a treasure chest and a spellbook. |
| **Cyberpunk** | Bright neon on black. Cyan, magenta and yellow, monospace type, cut corners, and dice on a **glowing neon grid**. Its icons are a chip, storage, a terminal and a bolt. |

Every screen also has a **?** button that explains how to use it, for example how to add and remove dice.

### Settings
- **Appearance:** the theme, plus dark mode and dynamic colours for Classic.
- **Sounds, Animations, Haptic feedback, Quick roll**
- **Dice sets:** choose the active skin, upload a ZIP, create, edit and delete your own sets. The **?** button lists the image requirements.
- Settings is a tab in the bottom bar.
- The app reopens on the last tab you used.

## Custom dice skins

A **dice set** (skin) changes how the dice look: 2D images for the palette and Quick roll, 3D face textures for the swipe tray, colours and a number font. Every part is optional; anything a set leaves out keeps the default look.

Everything is in **Settings → Dice → Dice sets**:

| Control | What it does |
|---|---|
| Radio list | picks the active set; the Dice tab updates right away |
| **Upload ZIP** | imports a whole pack (the system file picker opens) |
| **New set** | creates an empty set and opens the editor |
| ✏️ / 🗑️ | edits or deletes one of your sets (built-in sets can't be changed) |
| **?** | shows the image requirements |

### Option 1: upload a ZIP pack

```
my_dice.zip
├── manifest.json        optional: name, colours, per-die settings
├── 2d/                  2D images, one per die
│   ├── d6.png
│   └── d20.png
├── 3d/                  3D face textures
│   └── d20_face.png
└── fonts/
    └── numbers.ttf      optional
```

- **Files are matched by name** (case-insensitive): `d6.png` or `2d/d6.png` → 2D image, `d6_face.png` or `3d/d6_face.png` → 3D face texture, the first `.ttf`/`.otf` at the root or in `fonts/` → number font. The dice are `d4 d6 d8 d10 d12 d20 d100 dN`.
- **`manifest.json` is optional.** Without it the number colours follow the app theme and the set is named after the ZIP file. Add one to set the name, colours (body, outline, **numbers**), outline width and pips on the d6 (format: [docs/dice-assets.md §4](docs/dice-assets.md#4-manifestjson)). It only needs what you want to change: files named as above are still found, and anything the manifest sets explicitly wins.
- **Dark dice need light numbers:** if your images are dark, set `"text"` in the manifest, otherwise the theme's dark numbers may be hard to read.
- Zipping the set **folder** is fine: one top-level wrapper folder is ignored.
- The uploaded set becomes active. Uploading a pack with the same `id` again updates it; an `id` that clashes with a built-in set gets a suffix (`default_2`).

### Option 2: build a set in the app

1. **New set**, give it a name.
2. Tap a die in the grid.
3. **Upload** a **2D image** and/or a **3D face texture** (Android Photo Picker). **Replace** or **Remove** them any time.

Badges under each die show which images it has. Picked images are copied into the app (re-encoded as PNG, at most 1024 px), so the originals can be deleted. JPEG is accepted, but has no transparency.

### Image requirements

| | 2D image | 3D face texture |
|---|---|---|
| Format | PNG or WebP, **transparent** background | PNG or WebP, square |
| Size | 512 × 512 px | 512 × 512 px |
| Content | the die silhouette, edge to edge, centred | painted on **every** face; the image's top is the face's top |
| Leave clear | the **label area**: the app writes the die name or the rolled number on top | the **centre**, so numbers stay readable |
| Notes | shown at about 56 dp: keep details bold. d100 is two small d10s with a caption below | not used for dN (spheres in 3D) |

**Limits:** 10 MB per image, 5 MB per font, a ZIP up to 50 MB unpacked and 300 files. Other file types are skipped, and archives with unsafe paths (`..`, absolute) are rejected.

The exact shapes, label areas and face outlines are in the SVG templates in [`docs/dice-templates/`](docs/dice-templates/), and the full designer guide is [docs/dice-assets.md](docs/dice-assets.md).

### Try the examples

[`docs/dice-examples/`](docs/dice-examples/) has two ready packs, generated by `tools/gen_dice_examples.py`:

| Pack | Shows |
|---|---|
| `wood.zip` | a full pack: `manifest.json`, colours, 2D images for all 8 dice, 3D face textures, pips on the d6, a darker d20 |
| `neon.zip` | a short manifest with colours only (glowing cyan, pink and green numbers on dark dice); the images and an LED-style number font (`fonts/segments.ttf`) are found by file name |

```bash
adb push docs/dice-examples/wood.zip /sdcard/Download/
adb push docs/dice-examples/neon.zip /sdcard/Download/
```

Then **Settings → Dice sets → Upload ZIP** and pick the file from Downloads.

---

## Requirements
- **Android 7.0+** (minSdk 24), targeting API 37
- To build it:
  - **JDK 17 or newer.** The JDK bundled with Android Studio works.
  - **Android SDK** with platform 37
  - Android Studio is optional; the Gradle wrapper is included.

## Build and run

```bash
git clone https://github.com/SkrypkoA/dicefamiliar.git
cd dicefamiliar
./gradlew assembleDebug     # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install on a connected phone or emulator
```

If `java` isn't on your `PATH`, point Gradle at Android Studio's JDK, for example:

```bash
export JAVA_HOME=/snap/android-studio/current/jbr   # path depends on how Android Studio was installed
```

## Tests and checks

```bash
./gradlew test                    # unit tests (domain logic, ViewModels, repositories)
./gradlew lint                    # Android lint
./gradlew connectedAndroidTest    # Compose UI tests on a connected device
```

**Xiaomi / HyperOS phones:**
- Turn on **Install via USB** and **USB debugging (Security settings)** in Developer options. Otherwise the test APK install is cancelled.
- If Android Studio is debugging the app at the same time, the tests are interrupted. Stop that session first.

---

## Tech stack

| Area | Library |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM: `ViewModel` + immutable `StateFlow` UI state, single activity |
| Navigation | Navigation Compose with type-safe routes; a bottom bar with one tab per feature |
| Dependency injection | Hilt (KSP) |
| Persistence | DataStore (settings, last-used values, active dice set); app files for user dice sets; Room (planned for pools and history) |
| Images | Coil 3 |
| Serialization | kotlinx.serialization (used for the LAN wire format) |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`), AGP 9.5 |
| Tests | JUnit 4, kotlinx-coroutines-test, Compose UI tests |

**Design rules:**
- **All randomness goes through one injectable `RandomSource`.** The app uses `SecureRandom`, and tests use a seeded source.
- **Domain logic is pure Kotlin.** This covers the notation parser, roller, formula editor, roll modes and remote actions. None of it depends on Android, and all of it is unit-tested.
- **Unbiased algorithms:** `nextInt(from, until)` for ranges and Fisher–Yates for shuffling.

## Project structure

```
app/src/main/java/com/skrypko/dicefamiliar/
├── ui/            Compose screens + ViewModels (dice, settings, navigation, theme, common)
│   └── dice/
│       └── render3d/   pseudo-3D dice: convex meshes, shading, numbers on faces
├── domain/        pure Kotlin: dice (parser, roller, editor), number (parser, generator), random, remote, settings
├── data/          DataStore repositories, the dice-set loader
└── di/            Hilt modules
docs/              designer guide for dice sets, SVG templates, example packs
tools/             generators: dice-set templates and examples, sounds
```

## Dice notation

| Notation | Meaning |
|---|---|
| `d20`, `1d20` | one twenty-sided die |
| `3d6+2` | three d6 plus 2 |
| `2d6+1d4-1` | several groups and a modifier |
| `4d6kh3` | roll 4d6 and keep the highest 3 |
| `2d20kl1` | roll 2d20 and keep the lowest |
| `d100` | percentile die (shown as a tens d10 and a units d10) |

**Limits:**
- 1–100 dice per group, and at most 200 dice in total
- 2–1000 sides
- at most 20 terms
- a modifier of at most ±10000

## Tools

Generators for the app's assets. Run them from the repository root with Python 3.

| Script | Generates | Needs |
|---|---|---|
| `tools/gen_dice_templates.py` | designer SVG templates in `docs/dice-templates/` | Python 3 |
| `tools/gen_dice_examples.py` | example dice skins `docs/dice-examples/wood.zip` and `neon.zip` | Python 3 + [Pillow](https://pypi.org/project/pillow/) |
| `tools/segment_font.py` | the seven-segment number font used by `neon.zip` (`python3 tools/segment_font.py out.ttf`) | Python 3 |
| `tools/gen_dice_sound.py` | the roll sound `res/raw/dice_roll.ogg` | Python 3 + ffmpeg |
| `tools/gen_number_sound.py` | the number generator chime `res/raw/number_tick.ogg` | Python 3 + ffmpeg |
| `tools/gen_app_icon.py` | the adaptive launcher icon in `res/drawable/` | Python 3 |
| `tools/render_legacy_icons.sh` | legacy launcher PNGs for API 24–25 in `res/mipmap-*/` | Python 3 + ImageMagick 7 (`magick`) |

**Dice-set templates** (after changing a die shape in `DiceShapes.kt` or `DieModel.kt`, update the tables in the script first):

```bash
python3 tools/gen_dice_templates.py docs/dice-templates
```

**Example dice skins** (they reuse the template shapes, so regenerate them after the templates):

```bash
pip install pillow                       # once
python3 tools/gen_dice_examples.py       # writes docs/dice-examples/*.zip
python3 tools/gen_dice_examples.py /tmp/skins   # or to another folder
./gradlew test --tests '*DiceSetExamplesTest'   # check that both packs still import cleanly
```

The output is reproducible: rerunning without changes gives byte-identical ZIPs, so `git status` shows only real changes. To make your own pack, copy `wood_pack()` or `neon_pack()` in the script, or simply zip a folder laid out as in [Custom dice skins](#custom-dice-skins).

**Roll sound** (synthesised with a fixed seed, then encoded to Ogg Vorbis):

```bash
python3 tools/gen_dice_sound.py /tmp/dice_roll.wav
ffmpeg -y -i /tmp/dice_roll.wav -c:a libvorbis -q:a 4 app/src/main/res/raw/dice_roll.ogg
```

**Number chime** (a short two-note bell, deterministic):

```bash
python3 tools/gen_number_sound.py /tmp/number_tick.wav
ffmpeg -y -i /tmp/number_tick.wav -c:a libvorbis -q:a 4 app/src/main/res/raw/number_tick.ogg
```

**App icon** (one shape list drives the adaptive, monochrome and legacy icons):

```bash
python3 tools/gen_app_icon.py                     # writes ic_launcher_{foreground,background,monochrome}.xml
python3 tools/gen_app_icon.py --preview /tmp/icon # optional: SVG previews to check the artwork
tools/render_legacy_icons.sh                      # PNG/WebP icons for Android 7.0–7.1
```

---

## Contributing
- **One feature means one branch** (`feature/<name>`), **one squashed commit** and **one pull request** into `master`.
- **Never commit to `master` directly.** Rebase onto `master` instead of merging it.
- Before opening a PR, run `./gradlew test lint`.

The full workflow and conventions are in [CLAUDE.md](CLAUDE.md).

## License
No license has been chosen yet, so all rights are reserved by the author until one is added.
