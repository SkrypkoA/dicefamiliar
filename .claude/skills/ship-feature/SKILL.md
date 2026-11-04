---
name: ship-feature
description: Package a finished Dice Familiar feature (or a tooling change) into one squashed commit and a pull request (checks, review, squash, rebase, push, PR with milestone and Closes #N for features, project board). Use when the user says the feature is done and asks to commit, push, open a PR or "ship it".
disable-model-invocation: true
---

# Ship a feature

Follows the CLAUDE.md **Git Workflow Policy**. The git-guard hook (`.claude/hooks/guard_git.py`) blocks commits on master, pushes to master, plain force pushes and merging master into the branch.

Arguments: `$ARGUMENTS` (optional: feature name, milestone, issue numbers).

## 1. Gather

- Branch: `git branch --show-current`. If on `master` with uncommitted work, create `feature/<kebab-name>` first.
- **Tooling change?** (`tooling/<name>` branch, or only `.claude/`, build/CI scripts or docs changed): skip milestones and issues entirely. No `--milestone`, no `Closes #N`, commit message `Tooling: <short description>`, board Feature *Tooling & workflow*. Run only the checks that match what changed. Then continue from step 3.
- Milestone: `gh api repos/{owner}/{repo}/milestones?state=all --jq '.[] | "\(.number) \(.state) \(.title)"'`. Pick the feature's milestone. If it's closed, reopen it; if there is none, propose a name and create it after the user agrees.
- Issues this PR finishes: `gh issue list --milestone "<milestone>" --state open`. If the work has no issue, propose creating one (it's what `Closes #N` closes).

## 2. Check (ask first)

- **Ask the user before running the full suite.** Once they agree, delegate it to the `gradle-runner` agent with "user approved: `./gradlew test lint`". Add `connectedAndroidTest` (with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`) when UI code changed and `adb devices` shows a device.
- Delegate a review to the `code-reviewer` agent. Fix BLOCKER/MAJOR findings (or agree with the user to skip them) before going on.
- If the LAN protocol, `DiceRollPayload`, `RollOutcome` or dice rules changed, make sure `lan-protocol-keeper` has run.
- Check that CLAUDE.md and README.md describe the new behaviour.

## 3. Squash, update master, rebase

```bash
git fetch origin
git reset --soft $(git merge-base origin/master HEAD)
git commit -m "<Feature name>: <short description>

<optional body: bullet list of what changed>

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git checkout master && git pull --ff-only origin master && git checkout feature/<name>
git rebase master
```

If the rebase brought in new commits, rerun the related tests (and the full check, after asking).

## 4. Push and open the PR

```bash
git push -u origin feature/<name>          # --force-with-lease only if pushed before
gh pr create --base master --head feature/<name> \
  --title "<Feature name>" --milestone "<milestone>" --body "<what was added>

## How to test
<steps>

Closes #N

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

## 5. Project board

The GitHub Project is https://github.com/users/SkrypkoA/projects/1 (number 1, owner SkrypkoA). Fields: **Status** (Todo / In Progress / Done), **Feature** and **Milestone**.

- Add the PR and any new issues: `gh project item-add 1 --owner SkrypkoA --url <url>`.
- Set Feature and Status with `gh project item-edit` (look up ids with `gh project field-list 1 --owner SkrypkoA --format json`). PR and its issues are **In Progress** until merged.
- If a draft item tracks this work, convert or remove it.

## 6. Before merge

- **Ask whether to build the APK.** If yes, use the `build-apk` skill.
- Merge only when the user asks. After the merge: the issues close through `Closes #N`; set their board items to **Done**; when every issue of the milestone is closed, close the milestone.
