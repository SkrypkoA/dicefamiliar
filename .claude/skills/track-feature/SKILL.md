---
name: track-feature
description: Set up GitHub tracking for a new Dice Familiar feature before coding starts - a milestone, the issues it splits into, and project board items with Status and Feature. Use when the user starts a new feature or asks to plan work into milestones/issues.
---

# Track a new feature

The rule: one milestone per feature, planned work split into issues inside it, every issue on the project board.

1. **Check what exists:**
   - Milestones: `gh api repos/{owner}/{repo}/milestones?state=all --jq '.[] | "\(.number) \(.state) \(.title)"'`
   - Open issues: `gh issue list --state open`
   - Board: `gh project item-list 1 --owner SkrypkoA --format json --limit 200` (look for a draft item for this feature).
2. **Propose to the user** (don't create yet): the milestone title (feature name, e.g. *Card drawer*), and 2–6 issues, each one shippable piece with a short body (what, acceptance, which CLAUDE.md section it touches). Domain logic, data, UI and LAN pieces are usually separate issues.
3. **After the user agrees, create them:**
   ```bash
   gh api repos/{owner}/{repo}/milestones -f title="<Feature>" -f description="<one line>"
   gh issue create --title "<Feature>: <piece>" --body "<body>" --milestone "<Feature>"
   ```
   Reopen a closed milestone instead of creating a duplicate (`gh api -X PATCH repos/{owner}/{repo}/milestones/<n> -f state=open`).
4. **Board:** add each issue (`gh project item-add 1 --owner SkrypkoA --url <issue url>`), set **Status** = Todo and **Feature** (ids from `gh project field-list 1 --owner SkrypkoA --format json`). If the Feature option doesn't exist, ask before adding it. Remove or convert a matching draft item.
5. **Branch:** `git checkout -b feature/<kebab-name>` from an up-to-date `master`.

For large features, plan the implementation with superpowers `brainstorming` → `writing-plans` (kept in the conversation or `.claude/plans/`, never committed).
