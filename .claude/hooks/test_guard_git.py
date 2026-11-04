#!/usr/bin/env python3
"""Self-test for guard_git.py: python3 -I .claude/hooks/test_guard_git.py

Runs the hook on sample commands as if the current branch were a feature branch
(or master, where noted). The branch is faked through a tiny git repo in a temp dir.
"""
import json
import os
import subprocess
import sys
import tempfile

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "guard_git.py")

ALLOW = [
    "git status",
    'git commit -m "Feature: x"',
    "git checkout master && git pull --ff-only origin master && git checkout feature/x",
    "git rebase master",
    "git push -u origin feature/x",
    "git push --force-with-lease origin feature/x",
    "git reset --soft $(git merge-base origin/master HEAD)",
    "git log --oneline master..HEAD",
    "./gradlew testDebugUnitTest --tests '*NotationEditorTest'",
    # git text that is data, not a command
    'gh pr create --title T --body "Blocks git merge master and git push origin master"',
    'echo "never run: git push --force origin x"',
    "cat <<'EOF'\n- `git merge master` on a feature branch is blocked\nEOF",
]
BLOCK = [
    "git push origin master",
    "git push origin HEAD:master",
    "git push --force origin feature/x",
    "git push -f",
    "git push origin +feature/x",
    "git merge master",
    "git merge origin/master",
    "git pull origin master",
    "git checkout master && git commit -m oops",
    "git add -f local-examples/sample",
    "cd app && git merge master",
    "GIT_TRACE=1 git push origin master",
    "(git merge origin/master)",
]
BLOCK_ON_MASTER = ["git commit -m oops", "git push -u origin master", "git merge feature/x"]


def run(command, cwd):
    payload = json.dumps({"tool_name": "Bash", "cwd": cwd, "tool_input": {"command": command}})
    return subprocess.run([sys.executable, "-I", HOOK], input=payload, capture_output=True, text=True).returncode


def repo(branch):
    d = tempfile.mkdtemp()
    subprocess.run(["git", "init", "-q", "-b", branch, d], check=True)
    return d


def main():
    feature, master = repo("feature/x"), repo("master")
    failures = []
    for c in ALLOW:
        if run(c, feature) != 0:
            failures.append(f"should allow: {c}")
    for c in BLOCK:
        if run(c, feature) != 2:
            failures.append(f"should block: {c}")
    for c in BLOCK_ON_MASTER:
        if run(c, master) != 2:
            failures.append(f"should block on master: {c}")
    total = len(ALLOW) + len(BLOCK) + len(BLOCK_ON_MASTER)
    for f in failures:
        print("FAIL", f)
    print(f"{total - len(failures)}/{total} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
