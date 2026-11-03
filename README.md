<h1 align="center">Dice Familiar</h1>

<p align="center">
  An Android app for tabletop players: roll dice in 3D, and later draw cards, pick from lists and generate random numbers.
</p>

---

## Features

| Feature | Status |
|---|---|
| 🎲 **Dice roller** | ✅ Done |
| ⚙️ **Settings** | ✅ Done |
| 🔢 Random number generator | 🚧 Planned |
| 📋 Pool picker (named lists with images, CSV/ZIP import & export) | 🚧 Planned |
| 🃏 Card drawer (52 cards, jokers, multiple decks) | 🚧 Planned |
| 📡 LAN play: ask another player's phone to roll | 🚧 Planned (the request/response layer is ready) |

### Dice roller
- **Dice panel:** tap a die to add it to the formula, and long-press to remove one. The panel offers d4, d6, d8, d10, d12, d20, d100 and custom dN dice.
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
- **Dice sets:** colours, fonts, 2D images and 3D face textures can be replaced by a designer. See [docs/dice-assets.md](docs/dice-assets.md).

### Settings
- **Sounds, Animations, Haptic feedback, Quick roll**
- Settings is a tab in the bottom bar.
- The app reopens on the last tab you used.

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
| Persistence | DataStore (settings, last-used values); Room (planned for pools and history) |
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
├── domain/        pure Kotlin: dice (parser, roller, editor), random, remote, settings
├── data/          DataStore repositories, the dice-set loader
└── di/            Hilt modules
docs/              designer guide for dice sets + SVG templates
tools/             generators: dice-set templates, roll sound
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

| Script | Purpose |
|---|---|
| `tools/gen_dice_templates.py` | regenerate the designer SVG templates in `docs/dice-templates/` |
| `tools/gen_dice_sound.py` | regenerate the roll sound `res/raw/dice_roll.ogg` |

---

## Contributing
- **One feature means one branch** (`feature/<name>`), **one squashed commit** and **one pull request** into `master`.
- **Never commit to `master` directly.** Rebase onto `master` instead of merging it.
- Before opening a PR, run `./gradlew test lint`.

The full workflow and conventions are in [CLAUDE.md](CLAUDE.md).

## License
No license has been chosen yet, so all rights are reserved by the author until one is added.
