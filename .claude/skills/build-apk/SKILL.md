---
name: build-apk
description: Build the Dice Familiar debug APK, put it in apk/dicefamiliar.apk inside the feature's squashed commit and keep the README download link. Use only after the user said yes to "build the APK?" before a PR merge.
disable-model-invocation: true
---

# Build the APK for a PR

Only run this after the user agreed to an APK for this PR. Never build or commit an APK unprompted.

1. Make sure you're on the feature branch (not `master`) and the branch is already squashed into one commit.
2. Build: `./gradlew assembleDebug` (or delegate to `gradle-runner`). On failure, stop and report.
3. Copy: `cp app/build/outputs/apk/debug/app-debug.apk apk/dicefamiliar.apk`.
4. README: the top of `README.md` must keep the centred links block:
   - **📥 Download the app (APK)** → `https://github.com/SkrypkoA/dicefamiliar/raw/master/apk/dicefamiliar.apk`
   - **🎨 Dice skins examples** → `docs/dice-examples/`
   - **🃏 Card deck examples** → `docs/card-decks.md`
   Add any that are missing; don't duplicate them.
5. Fold it into the single feature commit and update the PR:
   ```bash
   git add apk/dicefamiliar.apk README.md
   git commit --amend --no-edit
   git push --force-with-lease origin feature/<name>
   ```
6. Report the APK size and the commit hash.
