# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Contents

- [Project Overview](#project-overview)
- [Tech Stack](#tech-stack)
- [Suggested Project Structure](#suggested-project-structure)
- [Core Rules](#core-rules)
- [Feature Details](#feature-details)
  - [Random Number Generator](#random-number-generator)
  - [Pool Picker](#pool-picker)
    - [Pool list screen](#pool-list-screen)
    - [Edit Pool screen (form)](#edit-pool-screen-form)
    - [Import (CSV or ZIP)](#import-csv-or-zip)
    - [Export (CSV or ZIP)](#export-csv-or-zip)
    - [Item image](#item-image)
    - [Item color](#item-color)
    - [Display mode](#display-mode)
    - [Pick screen](#pick-screen)
    - [Data model (Room)](#data-model-room)
  - [Dice Roller](#dice-roller)
  - [Card Drawer](#card-drawer)
  - [Shared](#shared)
- [Common Commands](#common-commands)
- [Git Workflow Policy](#git-workflow-policy)
- [Coding Conventions](#coding-conventions)

## Project Overview

**Dice Familiar** is an Android application for randomness-based utilities:

1. **Random Number Generator** – generate one or more random integers (or decimals) within a user-defined range.
2. **Pool Picker** – pick one or more random values from a user-defined list (with or without repetition).
3. **Dice Roller** – simulate rolling standard and custom dice (d4, d6, d8, d10, d12, d20, d100, dN), including dice notation like `3d6+2`.
4. **Card Drawer** – a card table (draw pile, hand, discard) for 36 / 52 / 54-card decks, custom decks and uploaded decks, driven by gestures.

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
├── di/                    # Hilt modules (Random, DataStore, repositories, remote actions, dice)
├── ui/
│   ├── navigation/        # NavHost, bottom bar, routes
│   ├── theme/             # DiceFamiliarTheme, AppThemeSpec + AppThemes registry, themes/ (one file per theme), tray/ (tray painters)
│   ├── number/            # Random number screen + ViewModel
│   ├── pool/
│   │   ├── list/          # Pool list screen (+ FAB, edit buttons)
│   │   ├── edit/          # Edit Pool form, item editor, import/export dialogs
│   │   ├── pick/          # Pick screen
│   │   └── common/        # PoolItemView: one renderer for every display mode
│   ├── dice/              # Dice roller screen + ViewModel
│   ├── cards/             # Card table, deck list, custom deck editor, PlayingCard
│   ├── settings/          # App-wide Settings screen; dicesets/ = dice-set editor + help sheet
│   └── common/            # Shared UI: SoundPlayer, StepperNumberField, HelpButton/HelpSheet
├── domain/
│   ├── random/            # RandomSource abstraction (injectable, seedable for tests)
│   ├── number/            # Number generation logic
│   ├── pool/              # Pool, PoolPicker, PoolValidator, UniqueNameResolver, ItemColors, PoolDisplayMode
│   │   └── io/            # CsvItemParser/Writer, PoolArchiveReader/Writer, ImportFileTypes, SafeFileNames
│   ├── io/                # Shared RFC 4180 CsvReader (pool and card import)
│   ├── dice/              # Dice model, notation parser, roller, NotationEditor, DiceRollAction
│   ├── cards/             # CardFace, StandardCards, DeckPresets, CardTable; io/ = CSV + ZIP deck import
│   ├── remote/            # Feature-agnostic remote actions (LAN-ready): envelopes, registry, codec
│   └── settings/          # AppSettings (app-wide preferences)
└── data/                  # DataStore / Room repositories (settings/, dice/, number/, cards/, pool/, remote/)
```

## Core Rules

- **All randomness goes through a single injectable `RandomSource` interface.** Production uses `java.security.SecureRandom` (or `kotlin.random.Random`); tests use a seeded/fake implementation. Never call `Random` directly from UI or ViewModels.
- **Domain logic is pure Kotlin** with no Android dependencies, so it is fully unit-testable.
- Use unbiased algorithms: `nextInt(from, until)` for ranges, Fisher–Yates for shuffling, no modulo bias.
- Validate input (min ≤ max, non-empty pool, count limits, valid dice notation) and show clear error messages instead of crashing.

## Feature Details

### Random Number Generator
- **Inputs:**
  - min and max, both inclusive
  - count, 1–1000
  - an allow-duplicates toggle
  - integer or decimal mode; decimal mode has 1–6 decimal places, default 2
- **Limits:** |min| and |max| ≤ 1,000,000,000, and each field holds at most 20 characters. `.` and `,` are both accepted as the decimal separator.
- **Defaults on first launch:** 1–100, count 1, integer, duplicates allowed.
- **The last selected inputs and the last result are remembered** in DataStore (`NumberStateRepository`, keys `number.*`). The last result is stored as JSON.
- **Domain (`domain/number/`):**
  - `NumberInputParser` turns field text into a `NumberSpec`, or returns `NumberError` codes. Each code carries its field (MIN/MAX/COUNT), so the UI shows the message under the right field, live as the user types.
  - Without duplicates, `count` must not exceed the range size (`COUNT_EXCEEDS_RANGE`).
  - **Decimals are scaled integers.** `NumberSpec` holds min and max × 10^decimals, so decimal mode is integer generation: uniform, both ends included, and exact duplicate rules. `NumberFormatter` formats the result exactly, with no floating point.
  - **`NumberGenerator`:** `nextLong(min, max + 1)` per value. Without duplicates it uses Floyd's sampling (exactly `count` draws), then `RandomSource.shuffle`.
- **UI (`ui/number/`):**
  - The result is decided before the reveal. With animations on, the reveal is a pulsing "• • •" for `REVEAL_MS`; the UI never shows random digits.
  - Sound: `Sound.NUMBER_GENERATED` (`res/raw/number_tick.ogg`, from `tools/gen_number_sound.py`). There is a haptic confirm on the result and a reject haptic plus a shake when generating with invalid input.
  - Min, Max and How many are `StepperNumberField`s with `AFTER` arrows. `NumberStepper` handles steps: ±1 whole unit, a fraction rounds towards the step in integer mode, decimal mode keeps the fraction, values are clamped to the limits, and count stays within 1–1000.
  - One value is shown large (auto-sized). Several values are shown as chips, with their sum.
  - Copy uses `LocalClipboard`, with a snackbar only below API 33. Share uses `ACTION_SEND` in a chooser.
  - Recent results: the last 20, in memory, in a history sheet that can be cleared. The latest result stays on screen.

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
| **Every item has an image**, **Show names** on (default) | **Thumbnail + name**. |
| **Every item has an image**, **Show names** off | **Images only**, no names, and each image still uses its border color. The name stays as the image's `contentDescription` for accessibility. |

- **Show names** is a per-pool flag (`PoolEntity.showNames`, default on), switchable in Edit Pool and on the Pick screen. It can be turned off only when every item has an image (`PoolDisplayMode.canHideNames`); otherwise the switch is disabled with a note.
- The mode is computed from the pool state (`PoolDisplayMode.of(items, showNames)` → `NAMES_ONLY`, `THUMBNAIL_AND_NAME`, `IMAGES_ONLY`) and is unit-tested. One renderer, `ui/pool/common/PoolItemView` (`PoolItemTile`, `ItemThumbnail`, `ItemNameLabel`), draws every mode.

#### Pick screen
- **Options:**
  - **How many** is a `StepperNumberField`: up to the available items, or up to 100 with **Allow repeats**.
  - **Remove picked items** sets picked items aside (`PoolItemEntity.setAside`, persisted and non-destructive). "N of M left" is shown, and **Put back all** restores them.
  - The options are remembered per pool in DataStore (`pool.pick.<id>.*`, `PoolPickStateRepository`).
- **Picking:** `PoolPicker` (domain) uses `nextInt` per pick with repeats, and `RandomSource.shuffle` + take without. The result is decided before the "• • •" reveal; the sound is `NUMBER_GENERATED`, with haptics.
- **Results** are shown with the item's image and color, as described above: one large, several as a grid.
- **Copy, share and history:** copy, share as text, or share as image (`PickSnapshot` + `CaptureAsImage`), plus the last 20 picks in memory.
- **Navigation:** routes `PoolListRoute`, `PoolEditRoute(poolId = 0 → new)` and `PoolPickRoute(poolId)`, all under the Pool tab. Saving a pool opened from the list (if it has items) goes to its Pick screen.

#### Data model (Room)
- **Entities:**
  - `PoolEntity(id, name, createdAt, showNames)`
  - `PoolItemEntity(id, poolId FK cascade, name, nameNormalized, imagePath?, colorArgb?, position, setAside)`, with a unique index on `(poolId, nameNormalized)`
  - `AppDatabase` (`data/pool/PoolDatabase.kt`) is version 1, with its schema exported to `app/schemas/`. Bump the version and add a migration for any schema change.
- **`RoomPoolRepository`:**
  - `save(pool)` replaces the items in one transaction and deletes images the pool no longer uses.
  - `delete` also deletes the pool's images.
  - `readImport` stores the import's images right away; the caller discards them on cancel.
  - `cleanUpImages()` runs once per process and removes images no pool references.
- **`PoolImageStore`:** `filesDir/pool_images/<uuid>.<ext>`, scaled to ≤ 1024 px.
- **The Edit screen works on a draft:** images picked or imported during the edit are deleted if the edit is discarded.
- **Pure-Kotlin domain helpers** in `domain/pool/` and `domain/pool/io/`, each unit-tested:
  - `CsvItemParser`/`CsvItemWriter`
  - `UniqueNameResolver` (adds the ` #N` suffix)
  - `PoolValidator`, `ItemColors` (palette, parsing, contrast text), `PoolPicker`
  - `PoolArchiveReader`/`PoolArchiveWriter` (ZIP, with zip-slip and size checks), `SafeFileNames`, `ImportFileTypes`
- **CSV reading** uses the shared RFC 4180 `domain/io/CsvReader`, which card import uses too.
- **Examples:** `docs/pool-examples/` (`classes.zip`, `drinks.csv`) come from `tools/gen_pool_examples.py` and are checked by `PoolExamplesTest`. The full spec is in `docs/pool-import.md`.
- **Private samples:** `local-examples/` is gitignored and holds third-party sample pools for manual checks only. Never commit them or write tests for them.

### Dice Roller
- **Dice palette:** 2D die silhouettes with the name inside (d4 triangle, d6 square, d8 diamond, d10/d100 kite, d12 pentagon, d20 hexagon, custom dN circle). Tapping a die **adds it to the formula**; long-press removes one; a badge shows the count. For dN, the first tap asks for the number of sides: tap the field to type it, swipe up / down on it (or tap the chevrons) to increase / decrease it (2–1000). The dialog has no title. Cancel (outlined) and Add (filled) are buttons of the same size, placed in the bottom corners. Further taps add the same die, and the button shows it as e.g. `d7`. Only one custom value is allowed at a time: to choose another, remove all custom dice (long-press dN) or Clear. Large `−1`/`+1` buttons (centred in the panel) adjust the modifier, and an eraser button of the same size (Clear) empties the formula. Editing logic lives in pure `NotationEditor`.
- **Notation:** `NdM`, `NdM±K`, multiple groups (`2d6+1d4+3`), keep highest/lowest `kh`/`kl` (`4d6kh3`, `2d20kl`). Case-insensitive, whitespace ignored. Limits (`DiceLimits`): 1–100 dice per group, 2–1000 sides, ≤ 20 terms, ≤ 200 dice total, |modifier| ≤ 10000, ≤ 100 chars. The parser returns `NotationError` codes; the UI maps them to strings.
- **Normal / Advantage / Disadvantage** are shown only when the formula has exactly one die, of any type (modifiers allowed). They turn `1dN` into `2dNkh1` / `2dNkl1`. The rule is enforced in `RollMode` (domain), and with more dice the roll is normal.
- **Screen layout:** a collapsible **dice panel** at the top holds the palette, the formula field, the ƒx toggle, the `−1`/`+1` buttons, the eraser (Clear) button and the roll modes. A large chevron under it (no text) toggles it, and the state is persisted. When no dice are selected, the panel always opens on launch. The **dice tray takes all remaining height** (`weight(1f)`), and the 3D and 2D dice scale with it. Below it is the sum bar, with a history button that opens recent rolls in a bottom sheet. The Roll button appears only in Quick roll.
- **Formula field:** hidden by default and shown or hidden with the ƒx toggle (persisted). When it's off, nothing is drawn: no faded or read-only field.
- **Rolling:** by default the user **swipes the dice tray** (faster swipe = longer tumble; the result is never affected). With the **Quick roll** setting a Roll button replaces the swipe. The tray has an accessibility "Roll dice" action.
- **Tray:** with Quick roll **off**, the dice are pseudo-3D (`ui/dice/render3d`: convex meshes, Lambert shading, numbers warped onto faces). They tumble and land on the rolled faces, because the result is decided before the animation (`DiceUiState.pending`). With Quick roll **on**, they're 2D silhouettes. The sum (`notation = total`) is shown in a bar under the tray.
- **d100** is shown as a percentile pair: a tens d10 (`00`–`90`) plus a units d10 (`0`–`9`), smaller and grouped on one pill so they read as one roll. This applies to the palette icon, the 2D chips and the 3D tray. The result value is still 1–100.
- **d10** faces read `0`–`9`: a roll of 10 shows `0` but counts as 10. Custom **dN** dice are spheres in 3D.
- **Animation:** with Quick roll on, the 2D dice spin with "?" faces (no randomness in the UI), then the result chips appear. With animations off, the result is shown instantly. Sound and haptics follow the app settings.
- **LAN-ready:** rolls go through `DiceRollAction` (`dice.roll`, v1) and `DiceRollHandler` via the shared remote-action layer. The notation string is the wire format; the rolling device re-parses it as untrusted input and owns the randomness.

### Card Drawer
- **Model (`domain/cards/`):**
  - `CardFace(name, value, suit, image, standardId)`.
  - `StandardCards` gives ids like `AS`, `10H`, `JR`, `JB`, and the presets 36 (6–A), 52 and 54 (`all54` lists the **jokers first**).
  - `DeckPresets`: `preset-36`, `preset-52`, `preset-54`; the default is `preset-52`.
  - **`CardTable(drawPile, hand, discard)` holds card indices**, with the top card last. Its operations are draw, takeFromDiscard, discard(card), putOnDraw(card), discardHand, handToDraw, discardOntoDraw, shuffleDraw, shuffleDiscard and reset. They return a new table, and invalid ones are no-ops. Shuffles use `RandomSource.shuffle`.
- **Table screen (`ui/cards/CardTableScreen`):** the draw and discard piles sit on the theme's tray, with the hand below them.
  - **Gestures:**
    - tap the deck → draw; long-press it → shuffle
    - tap the discard → take its top card; long-press it → shuffle the discard
    - drag the discard onto the deck → `discardOntoDraw()`: the pile is turned over on top, **not shuffled**
    - drag a hand card onto a pile → **on top** of it (`putOnDraw(card)` / `discard(card)`); swipe it up ≥ 64 dp elsewhere → discard. A card drag must start mostly vertical; a sideways start scrolls the hand.
    - **long-press the hand** (detected in the Initial pass, so it wins over the cards' own gestures) → the whole hand follows the finger; dropped on a pile it goes **on top** (`handToDraw()` / `discardHand()`), elsewhere it springs back. There is no wide-swipe discard.
    - tap a card → full-screen view
  - **Hand layout:** a fan that fits the width, with room kept for tilt and arc. Bigger hands shrink the cards (down to `MIN_CARD` of the width) while keeping `MIN_VISIBLE` (30 %) of each card visible. After that the hand becomes a flat row that scrolls sideways (with fling, and new cards scroll into view).
  - **Moves never shuffle:** dropping a card, the hand or the discard pile on a pile puts them on top, with the flick sound and no shake. Only the long-press shuffles and Reset shuffle (shake plus riffle sound). While several cards are carried (the hand, or the discard pile), they are drawn as a small pile of the top 3 under the finger (`PileTargets.carried`, an overlay in the play area).
  - **Edges:** the play area clips its content at the table edge (inside the rim, if the tray has one).
  - **Piles:** each pile shakes on its own when shuffled. `PileTargets` holds both piles' bounds for every drop.
  - **Standard faces** (`ui/cards/CardFaceArt`): vector suit shapes (never emoji), pips in the traditional layout (`PipLayout`, unit-tested), and original double-headed court portraits for J, Q and K.
  - **Top bar** (collapsible with a chevron, persisted in `cards.panel_visible`):
    - the deck-list icon (the theme's cards icon)
    - Share: a menu with "Share as text" (`ACTION_SEND`) and "Share as image" (the hand on the theme's table, rendered off screen by `CaptureAsImage` and shared as a PNG through `shareImage`)
    - the buttons toggle (persisted in `cards.show_buttons`), which shows buttons for every action, including "Hand → deck"
    - Reset (with a confirm dialog)
  - "?" sits next to the chevron. Pile and card semantics expose every action for accessibility.
- **Decks:** a deck list (presets, then custom, then uploaded) and a custom deck editor that picks from the 54 cards, jokers first, with "All" per group. Both are routes under the Cards tab.
  - The selected deck is in DataStore (`cards.selected_deck`; an unknown id falls back to the default).
  - **Only the current deck's table is saved** (`cards.table`, JSON). Picking another deck starts it fresh and shuffled.
- **Upload** (Settings → Cards, `CardDecksSection`): up to **1000 cards** per deck. Uploaded decks live in `filesDir/card_decks/<id>/` (`deck.json` + `images/`); custom decks store only standard ids. The full spec is in `docs/card-decks.md`.
  - **`CardDeckArchiveReader`:**
    - a ZIP with a root `.csv` (`name,value,suit,image`) plus images, **or** images only (natural file order, the name taken from the file)
    - `back.*` is the card back
    - zip-slip rejected; ≤ 1200 entries, ≤ 200 MB unpacked, ≤ 10 MB per image, ≤ 1 MB per CSV
  - **A plain CSV** gives text cards (`CardCsvParser`, which uses the shared RFC 4180 `domain/io/CsvReader`).
  - Images larger than 1024 px are scaled down.
  - Example decks in `docs/card-examples/` come from `tools/gen_card_examples.py` (original art) and are checked by `CardExamplesTest`.
- **LAN (separate feature, not implemented):** a future `cards.draw` remote action will apply `CardTable.draw()` to the **host's** table and return the drawn `CardFace`.
- **Sounds:** `CARD_FLICK` and `CARD_SHUFFLE` (`tools/gen_card_sounds.py`). Haptics on draw, discard and shuffle.

### Shared
- **Settings** (`AppSettings`, DataStore): sounds, animations, haptic feedback (all on by default) and Quick roll (off). Settings is the fifth tab in the bottom navigation bar. The app has no top app bar and no screen titles, and it **reopens on the last selected tab** (`LastTabRepository` plus `NavigationViewModel`). New feature settings get their own section.
- **Remote actions** (`domain/remote/`, feature-agnostic, for future LAN play): a feature defines a `RemoteAction<Req, Res>` (`type`, `version`, serializers), implements a `RemoteActionHandler`, and registers it with Hilt `@Provides @IntoSet fun ...(): RemoteActionHandler<*, *>`. `RemoteActionRegistry` dispatches `ActionRequest` → `ActionResponse` (unknown type, wrong version or malformed payload give `REJECTED`). Local actions use `LocalActionExecutor`, which takes the same path a LAN request will. `Participant` (a stable id per device) is the identity. Transport is not implemented yet.
- **Dice sets** (skins): `manifest.json` plus optional 2D images, 3D face textures and a font. Built-in sets are in `assets/dice_sets/<id>/` (only `default`); user sets are in `filesDir/dice_sets/<id>/` with the same layout. They are parsed and validated by `DiceSetValidator` (`domain/dice/skin`) and loaded by `DiceSetRepository` (`DefaultDiceSetRepository`) with per-field fallback. The active set id is in DataStore (`dice.active_set`) and the Dice tab follows it live.
  - **Settings → Dice sets:** a radio list to pick the active set, **Upload ZIP** (`OpenDocument`), **New set**, edit and delete (with confirmation), and a **"?"** help sheet with the requirements (`ui/settings/dicesets/DiceSetHelpSheet`).
  - **ZIP upload:** `DiceSetArchiveReader` (zip-slip check, ≤ 300 entries, ≤ 50 MB unpacked, images ≤ 10 MB, fonts ≤ 5 MB, other files skipped, optional single wrapper folder). `DiceSetConventions` matches `d6.png` / `2d/d6.png` / `3d/d6_face.png` / `fonts/*.ttf`: it builds the manifest when there is none and fills in what a manifest leaves out (explicit manifest fields win). `DiceSetIds` builds safe, unique ids: a clash with a built-in set gets a suffix, and a clash with a user set replaces it.
  - **Editor** (`DiceSetEditRoute`, shown under the Settings tab): a grid of the 8 die keys. Tapping a die opens a sheet to upload, replace or remove its 2D image and 3D face texture (Photo Picker). Images are re-encoded to PNG at ≤ 1024 px. `DiceSetEditor` holds the pure manifest edits.
  - Designer requirements and templates are in **`docs/dice-assets.md`** and `docs/dice-templates/` (generated by `tools/gen_dice_templates.py`). Example packs are in `docs/dice-examples/` (generated by `tools/gen_dice_examples.py`, checked by `DiceSetExamplesTest`; neon's seven-segment font comes from `tools/segment_font.py`).
- **Themes** (`ui/theme/`, Settings → Appearance):
  - The current themes are **Classic** (Material You, light or dark, dynamic colours on Android 12+), **Tavern** (warm parchment and leather, serif titles, bone dice on a green felt tray) and **Cyberpunk** (neon on black, monospace, cut corners, neon grid tray).
  - **Themes are data, not code branches.** Each theme is one `AppThemeSpec` in `ui/theme/themes/`: id, name and description strings, a `light` and/or `dark` `ColorScheme`, typography, shapes, `ThemeIcons` (tab, Roll and Generate icons) and a `TrayPainter`.
  - **To add a theme:** write the spec, add it to `AppThemes.all`, and add `theme_<id>` and `theme_<id>_description` strings. Settings previews, the picker and the whole app pick it up automatically. Never write `when (theme)` anywhere else.
  - **Brightness:** with both palettes, the user's `DarkMode` (System / Light / Dark) applies; with one palette, the theme always has that brightness. `supportsDynamicColor` enables wallpaper colours.
  - **Trays:** `PlainTray`, `FeltTray` and `NeonGridTray` take colours as parameters, so new themes can reuse them. Play areas (the dice tray, the number result) use `Modifier.themedTray(shape)` and `trayColors()` for readable text. Decoration "noise" uses a fixed hash, never `RandomSource`.
  - **Dice colours:** a spec can set `dice = ThemeDiceColors(fill, edge, text)` (Tavern uses bone). Without it, dice are `primaryContainer` with `primary` edges. A dice set's own colours always win (`themeDiceColors()` is the fallback).
  - **Saving:** `AppSettings.themeId` (DataStore `settings.theme`; an unknown id falls back to the default theme), `darkMode` and `dynamicColor`. `MainActivity` waits for the settings before drawing, and sets the system-bar icon colours from the theme.
  - **Rules for new UI:** take colours from `MaterialTheme.colorScheme` and icons from `LocalThemeStyle.current.icons`. Don't hard-code colours or theme checks.
- **Screen help:** every feature screen has a small **"?"** `HelpButton` that opens a `HelpSheet` (`ui/common/ScreenHelp`), a list of `HelpTopic` items explaining how to use the screen (e.g. `DICE_HELP`, `NUMBER_HELP`). Add one to each new feature screen and keep it up to date when gestures change.
- **Number inputs:** use `StepperNumberField` (`ui/common`) for every numeric form field:
  - The user can type, tap the chevrons, or swipe up or down on the field (`DEFAULT_SWIPE_STEP` per step).
  - `arrows` sets where the chevrons go: `ABOVE_BELOW` (up above the field and down below it, as in the dN dialog), `AFTER` (a compact pair right of the field, as in Min/Max/How many) or `BEFORE` (left of the field).
  - The field is stateless. The caller owns the text and decides what one step means; for example, `NumberStepper` and `DiceLimits.stepSides` live in the domain.
- **Sharing:** every feature shares through `ShareMenuButton` (`ui/common/ShareMenu.kt`, with "Share as text" and "Share as image"). It is used for the dice roll (on the sum bar: `RollRecord.shareText()` and `RollSnapshot`), the number result (`NumberSnapshot`), the pool pick (`PickRecord.shareText()` and `PickSnapshot`) and the card hand (`HandSnapshot`). Each snapshot shows the result on the theme's tray.
- **Sharing images:** `ui/common/ShareImage.kt` — `CaptureAsImage` renders any composable off screen into a picture, and `shareImage` saves it in `cache/shared/` and shares it through the app's `FileProvider` (`${applicationId}.files`, `res/xml/file_paths.xml`). Reuse both for any feature that shares a picture. For other files (e.g. a pool export), write to `sharedCacheFile(context, name)` and send it with `shareFile(context, file, mime, title)`. `displayName(resolver, uri)` (`ui/common/ContentUris.kt`) gives a picked document's file name.
- **Sounds:** `SoundPlayer` (`ui/common`); assets are in `res/raw` (`dice_roll.ogg` is generated by `tools/gen_dice_sound.py`).
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

**Testing while a task runs:** run only the tests related to the issue or feature you are working on. Don't run the whole suite after every change.

```bash
./gradlew testDebugUnitTest --tests '*NotationEditorTest'            # one class
./gradlew testDebugUnitTest --tests 'com.skrypko.dicefamiliar.domain.dice.skin.*'   # one package
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.skrypko.dicefamiliar.ui.dice.DiceContentTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true    # one UI test class; keeps the app and its data installed
```

**At the end of the task, before creating the pull request,** run everything: `./gradlew test lint`, plus `./gradlew connectedAndroidTest` when UI code changed and a device is connected (add the `leaveApksInstalledAfterRun` flag above so the user's app data survives). All of it must pass before the PR is opened.

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
   - Afterwards, run the full check (`./gradlew test lint`, and `connectedAndroidTest` when UI changed) and make sure everything passes.
5. **Only then push the feature branch to the remote.** Use `--force-with-lease` only if the branch was already pushed before:
   ```bash
   git push -u origin feature/<feature-name>
   ```
6. **Open a pull request** into `master`. The title is the feature name and the body is the feature description: what was added and how to test it.
   - **Milestone:** every PR goes into the milestone of its feature (one milestone per feature: *Random number generator*, *Pool picker*, *Card drawer*, *LAN play*, …). List them with `gh api repos/{owner}/{repo}/milestones --jq '.[].title'`. If the feature has no milestone yet, create one first.
   - **Issues:** the planned work is split into issues inside each milestone. End the PR body with `Closes #N` for every issue the PR finishes, so they close on merge.
   ```bash
   gh pr create --base master --head feature/<feature-name> \
     --title "<Feature name>" --body "<feature description>

   Closes #N" \
     --milestone "<Feature milestone>"
   ```
   When every issue of a milestone is closed and its last PR is merged, close the milestone.

The rule is: one feature means one branch, one squashed commit and one PR. Never merge `master` into a feature branch; always rebase instead.

## Coding Conventions

- Follow official Kotlin coding conventions.
- Composables are stateless where possible; hoist state to ViewModels.
- Keep strings in `res/values/strings.xml` (no hard-coded UI text).
- Write unit tests for every domain class (parsers, rollers, deck, pickers) using a seeded `RandomSource`.
