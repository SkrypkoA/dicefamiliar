# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**Dice Familiar** is an Android application for randomness-based utilities:

1. **Random Number Generator** – generate one or more random integers (or decimals) within a user-defined range.
2. **Pool Picker** – pick one or more random values from a user-defined list (with or without repetition).
3. **Dice Roller** – simulate rolling standard and custom dice (d4, d6, d8, d10, d12, d20, d100, dN), including dice notation like `3d6+2`.
4. **Card Drawer** – draw cards from a standard 52-card deck (optionally with jokers), with shuffle, draw, and reset.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (ViewModel + immutable UI state via `StateFlow`), single-activity
- **Navigation:** Navigation Compose (bottom navigation bar with one tab per feature)
- **Persistence:** DataStore (settings, last-used values) and Room (pools, pool items, history). Images picked by the user are copied into app-internal storage (`filesDir/pool_images/`); the database stores only the relative path.
- **Images:** Coil (`coil-compose`) for loading thumbnails
- **DI:** Hilt
- **Build:** Gradle Kotlin DSL (`*.gradle.kts`) with a version catalog (`gradle/libs.versions.toml`)
- **Min SDK:** 24, **Target/Compile SDK:** latest stable
- **Testing:** JUnit + kotlinx-coroutines-test for logic, Compose UI tests for screens

## Suggested Project Structure

```
app/src/main/java/com/skrypko/dicefamiliar/
├── DiceFamiliarApp.kt     # @HiltAndroidApp Application
├── MainActivity.kt
├── di/                    # Hilt modules (RandomSource, DataStore, later Room)
├── ui/
│   ├── navigation/        # NavHost, bottom bar, routes
│   ├── theme/             # Material 3 theme, colors, typography
│   ├── number/            # Random number screen + ViewModel
│   ├── pool/
│   │   ├── list/          # Pool list screen (+ FAB, edit buttons)
│   │   ├── edit/          # Edit Pool form, item editor, import/export dialogs
│   │   └── pick/          # Pick screen
│   ├── dice/              # Dice roller screen + ViewModel
│   └── cards/             # Card drawer screen + ViewModel
├── domain/
│   ├── random/            # RandomSource abstraction (injectable, seedable for tests)
│   ├── number/            # Number generation logic
│   ├── pool/              # Pool picking logic, validation, display mode
│   │   └── io/            # CSV/ZIP read & write, unique-name resolver
│   ├── dice/              # Dice model, notation parser, roller
│   └── cards/             # Card, Suit, Rank, Deck
└── data/                  # DataStore / Room repositories
```

## Core Rules

- **All randomness goes through a single injectable `RandomSource` interface.** Production uses `java.security.SecureRandom` (or `kotlin.random.Random`); tests use a seeded/fake implementation. Never call `Random` directly from UI or ViewModels.
- **Domain logic is pure Kotlin** with no Android dependencies, so it is fully unit-testable.
- Use unbiased algorithms: `nextInt(from, until)` for ranges, Fisher–Yates for shuffling, no modulo bias.
- Validate input (min ≤ max, non-empty pool, count limits, valid dice notation) and show clear error messages instead of crashing.

## Feature Details

### Random Number Generator
- Inputs: min, max, count, allow duplicates toggle, integer/decimal mode.
- If duplicates are disallowed, `count` must not exceed the range size.

### Pool Picker

Users can save multiple named pools and pick random items from them.

#### Pool list screen
- Shows all saved pools, with each pool's name and item count.
- A **"+" FAB** creates a new pool and opens the **Edit Pool** screen.
- Each pool row has an **edit (pencil) icon button on the right** that opens Edit Pool.
- Tapping a row opens the **Pick** screen. If the pool has no items, it opens **Edit Pool** instead.
- Deleting a pool asks for confirmation.

#### Edit Pool screen (form)
- Fields: the pool name, which is required, and the item list.
- Adding or editing an item: a name field, an optional image, and an optional color.
- **Unique item names:** names are compared after trimming and ignoring case. If a manually entered name already exists, the name field turns red (Material 3 `isError`) with the message *"An item named "X" already exists in this pool"*, and saving is blocked.
- Items can be edited and deleted. Reordering is optional.

#### Import (CSV or ZIP)
- An **Import** toolbar action opens the system file picker (`ActivityResultContracts.OpenDocument`). It accepts `text/csv`, `text/comma-separated-values`, `text/plain`, `application/zip` and `application/x-zip-compressed`. The type is detected from the file extension or MIME type; if neither is clear, it checks for the ZIP magic bytes `PK`.
- **CSV format**, which is the same file inside a ZIP and in a CSV export:
  - UTF-8, comma-separated, with RFC 4180 quoting.
  - Optional header row `name,image,color`. If there is no header, columns are read in that order.
  - `name` is required. `image` is a path relative to the root of the ZIP, for example `images/apple.png`. `color` is `#RRGGBB` or `#AARRGGBB`.
  - Blank lines are ignored. Rows with an empty name are skipped.
  - For a plain CSV import, the `image` column is ignored.
  ```csv
  name,image,color
  Apple,images/apple.png,#E53935
  Banana,images/banana.jpg,#FDD835
  Cherry,,#8E24AA
  ```
- **ZIP format:**
  ```
  pool.zip
  ├── items.csv        # required, at the archive root
  └── images/          # optional; files referenced from the CSV "image" column
      ├── apple.png
      └── banana.jpg
  ```
  - Allowed image types: PNG, JPG/JPEG and WEBP. Each image is limited to 10 MB.
  - Images are copied into app storage (`filesDir/pool_images/`).
  - If an image is missing or unsupported, the item is still imported without the image, and the count is reported in the summary.
  - **Safety:** reject entries with `..` or absolute paths (zip-slip). Cap the total uncompressed size at 200 MB and the number of entries at 1000. Stream entries and never extract outside the app's cache.
- **Hint:** the import dialog has a **"?" / info icon** that opens a help sheet. The sheet explains both formats and the allowed file types, and shows the CSV example and the ZIP tree above. The file picker is opened from that dialog, and the hint is also shown there in short form: *"CSV: one item per line (name[,image,color]). ZIP: items.csv + images/ folder (PNG, JPG, WEBP)."*
- After parsing, a dialog asks the user to **Extend** (append to the current items) or **Replace** (discard the current items, and delete their stored images). It shows how many items and images will be imported.
- **Duplicate names on import:** a name that is already in the list, or already earlier in the same file, gets ` #N` appended. N is the smallest number ≥ 2 that makes the name unique, for example `Apple`, `Apple #2`, `Apple #3`. No error is shown. When import finishes, a snackbar reports how many items were imported and renamed, and how many images were skipped.
- If the file can't be read, has no items, or a ZIP has no `items.csv`, show an error and leave the pool unchanged. The import is all-or-nothing: images are cleaned up if the import fails partway.

#### Export (CSV or ZIP)
- An **Export** toolbar action, which is disabled when the pool is empty.
- If **no item has an image**, export a CSV straight away (`name,color`, with the header).
- If **at least one item has an image**, a dialog lets the user choose:
  - **ZIP with images:** `items.csv` (`name,image,color`) plus an `images/` folder. Image file names come from the item names, made safe for file systems and unique.
  - **CSV only (no images):** `name,color`; images are dropped.
- The destination is chosen with `ActivityResultContracts.CreateDocument`. The suggested file name is `<pool-name>.csv` or `<pool-name>.zip`. A **Share** option (`FileProvider` plus `ACTION_SEND`) is also offered.
- An exported file can be imported again without losing anything (round trip). This is covered by a unit test.

#### Item image
- Any item can have an image attached, using the Android Photo Picker (`ActivityResultContracts.PickVisualMedia`, images only). The image can be replaced or removed.
- The image is copied into app storage, so it keeps working after the original file is gone. The copy is deleted when its item or pool is deleted.

#### Item color
- Each item can have a color chosen from a preset palette, or no color.
- **How it is drawn:** an item **with an image** uses the color as the **image border**. The item **name label** uses the color as its background in every case. Text color (black or white) is chosen automatically so the text stays readable against the color.

#### Display mode
The display mode is worked out from the whole pool, not item by item. It applies to the Edit Pool list, the item grid and the Pick screen results:

| Pool state | How items are shown |
|---|---|
| **No item has an image** | **Names only.** No thumbnail and no empty space for one; the name label uses the item's color as its background. |
| **At least one item has an image** (but not all) | **Thumbnail + name** for every item. Items without an image show a neutral placeholder (an image icon on a surface-variant background) in the same slot, so rows line up. The thumbnail border uses the item's color. |
| **Every item has an image** | **Images only**, no names, and each image still uses its border color. The name stays as the image's `contentDescription` for accessibility. |

- The mode is a computed property of the pool state (`PoolDisplayMode { NAMES_ONLY, THUMBNAIL_AND_NAME, IMAGES_ONLY }`) and is unit-tested.

#### Pick screen
- Options: number of picks, and with or without replacement. A "remove picked items from pool" option can be toggled.
- Results are shown with the item's image and color, as described above.

#### Data model (Room)
- `PoolEntity(id, name, createdAt)`
- `PoolItemEntity(id, poolId FK cascade, name, nameNormalized, imagePath?, colorArgb?, position)`, with a unique index on `(poolId, nameNormalized)`
- Pure-Kotlin domain helpers, each unit-tested: `CsvItemParser`/`CsvItemWriter`, `UniqueNameResolver` (adds the ` #N` suffix), `PoolValidator`, `PoolArchiveReader`/`PoolArchiveWriter` (ZIP, with zip-slip and size checks).

### Dice Roller
- Quick buttons for d4, d6, d8, d10, d12, d20, d100, plus custom dN.
- Support dice notation: `NdM`, `NdM+K`, `NdM-K`, multiple groups (`2d6+1d4+3`).
- Show individual results and total; optional advantage/disadvantage (roll 2d20, keep highest/lowest).
- Roll animation and optional haptic feedback.

### Card Drawer
- Standard 52-card deck, optional jokers, optional multiple decks.
- Actions: shuffle, draw N, return all & reshuffle; show remaining count and drawn cards.
- Deck state survives configuration changes (kept in ViewModel / SavedStateHandle).

### Shared
- History of recent results per feature (clearable).
- Copy result to clipboard / share.
- Light & dark theme, accessible content descriptions.

## Common Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device/emulator
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented/UI tests
./gradlew lint                   # Android lint
./gradlew ktlintCheck            # Code style (if ktlint plugin is configured)
```

## Git Workflow Policy

**Never commit or push directly to `master`.** All changes reach `master` only through a pull request from a feature branch. Never force-push to `master`.

When a feature is done, follow these steps in order:

1. **Create a feature branch** named after the feature, e.g. `feature/pool-csv-import`, using kebab-case. If the work is still uncommitted on `master`, create the branch first, so nothing is committed to `master`:
   ```bash
   git checkout -b feature/<feature-name>
   ```
2. **Squash all feature commits into one.** The commit message is the feature name followed by a short description:
   ```bash
   git fetch origin
   git reset --soft $(git merge-base origin/master HEAD)
   git commit -m "<Feature name>: <short description>"
   ```
3. **Pull the latest `master`:**
   ```bash
   git fetch origin master
   git checkout master && git pull --ff-only origin master && git checkout feature/<feature-name>
   ```
4. **Rebase the feature onto `master`:**
   ```bash
   git rebase master
   ```
   - If conflicts come up, resolve them, `git add` the files and run `git rebase --continue`.
   - Afterwards, run `./gradlew test lint` and make sure they pass.
5. **Only then push the feature branch to the remote.** Use `--force-with-lease` only if the branch was already pushed before:
   ```bash
   git push -u origin feature/<feature-name>
   ```
6. **Open a pull request** into `master`. The title is the feature name and the body is the feature description: what was added and how to test it.
   ```bash
   gh pr create --base master --head feature/<feature-name> \
     --title "<Feature name>" --body "<feature description>"
   ```

The rule is: one feature means one branch, one squashed commit and one PR. Never merge `master` into a feature branch; always rebase instead.

## Coding Conventions

- Follow official Kotlin coding conventions.
- Composables are stateless where possible; hoist state to ViewModels.
- Keep strings in `res/values/strings.xml` (no hard-coded UI text).
- Write unit tests for every domain class (parsers, rollers, deck, pickers) using a seeded `RandomSource`.
