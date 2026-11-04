#!/usr/bin/env python3
"""PreToolUse hook (Bash): enforces the CLAUDE.md Git Workflow Policy.

Blocks (exit 2, reason on stderr, shown to Claude):
- committing, merging, cherry-picking or reverting while on master
- pushing to master, or any push while on master
- force pushes other than --force-with-lease
- merging or pulling master into a feature branch (rebase instead)
- staging anything from local-examples/ (private, never committed)

The command is split on && || ; and newlines, and `git checkout/switch <branch>`
segments are tracked, so `git checkout master && git pull --ff-only origin master
&& git checkout feature/x` (step 3 of the workflow) is allowed.
"""
import json
import re
import subprocess
import sys

MAIN = "master"
# A segment counts only if it *starts* with git (optionally after "(", VAR=value or `command`),
# so git text inside quoted strings, PR bodies, heredocs or echo is ignored.
GIT = r"^\s*\(?\s*(?:[A-Za-z_]\w*=\S*\s+)*(?:command\s+)?git\s+(?:-C\s+\S+\s+)?"
END = r"(?![\w-])"  # whole subcommand: "merge" but not "merge-base"


def current_branch(cwd):
    try:
        out = subprocess.run(
            ["git", "symbolic-ref", "--short", "-q", "HEAD"],
            cwd=cwd, capture_output=True, text=True, timeout=5,
        )
        return out.stdout.strip() or None
    except Exception:
        return None


def check(segment, branch):
    s = segment.strip()
    if re.search(GIT + r"add" + END + r".*local-examples", s):
        return "local-examples/ holds private third-party samples; never stage or commit it."
    if re.search(GIT + r"push" + END + r".*\s(--force(?!-with-lease)\b|-f\b)", s) or re.search(GIT + r"push" + END + r".*\s\+\S", s):
        return "Plain force push is not allowed. Use --force-with-lease, and only on a feature branch that was pushed before."
    if re.search(GIT + r"push" + END + r".*\s(?:HEAD:)?(?:refs/heads/)?" + MAIN + r"\b", s):
        return "Never push to master. Push the feature branch and open a PR."
    if branch == MAIN:
        if re.search(GIT + r"(commit|merge|cherry-pick|revert|am)" + END, s):
            return "You are on master. Create a feature branch first: git checkout -b feature/<name>."
        if re.search(GIT + r"push" + END + r"", s):
            return "You are on master. Never push from master; push the feature branch instead."
    else:
        if re.search(GIT + r"merge" + END + r".*\b(?:origin/)?" + MAIN + r"\b", s):
            return "Never merge master into a feature branch. Rebase instead: git rebase master."
        if re.search(GIT + r"pull" + END + r"(?!.*--rebase).*\b" + MAIN + r"\b", s):
            return "Don't pull master into a feature branch. Update master separately, then git rebase master."
    return None


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 0
    if data.get("tool_name") != "Bash":
        return 0
    command = (data.get("tool_input") or {}).get("command") or ""
    if "git" not in command:
        return 0
    branch = current_branch(data.get("cwd") or ".")
    for segment in re.split(r"&&|\|\||;|\n", command):
        switch = re.search(GIT + r"(?:checkout|switch)\s+(?:-[bcBC]\s+)?([^\s-][^\s]*)\s*$", segment.strip())
        if switch:
            branch = switch.group(1)
            continue
        reason = check(segment, branch)
        if reason:
            print(f"Blocked by .claude/hooks/guard_git.py (CLAUDE.md Git Workflow Policy): {reason}", file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
