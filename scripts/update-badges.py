#!/usr/bin/env python3
"""Recomputes the numbers the READMEs show as badges — never type them by hand.

    python3 scripts/update-badges.py          # rewrite README.md, README.de.md, .github/repo-stats.json
    python3 scripts/update-badges.py --check  # exit 1 if anything is stale (what ReadmeBadgesTest also checks)

Counted exactly like ReadmeBadgesTest counts them:
  - version     versionName from app/build.gradle.kts
  - loc_main    non-blank lines of every .kt file under app/src/main
  - loc_test    non-blank lines of every .kt file under app/src/test
  - tests       number of @Test annotations under app/src/test
The badges in both READMEs are recognised by their shields.io label; .github/repo-stats.json feeds
the product page's repo_stats strip (flat object of non-negative integers).
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
READMES = [ROOT / "README.md", ROOT / "README.de.md"]


def kt_files(sub):
    return sorted((ROOT / sub).rglob("*.kt"))


def non_blank(files):
    return sum(1 for f in files for line in f.read_text(encoding="utf-8").splitlines() if line.strip())


def count_tests(files):
    return sum(len(re.findall(r"^\s*@Test\b", f.read_text(encoding="utf-8"), re.M)) for f in files)


def version():
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    return re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)


def k(n):
    """9876 -> 9.9k, 950 -> 950 (the same as ReadmeBadgesTest.k)."""
    return str(n) if n < 1000 else f"{n / 1000:.1f}k"


def stats():
    return {
        "version": version(),
        "loc_main": non_blank(kt_files("app/src/main")),
        "loc_test": non_blank(kt_files("app/src/test")),
        "tests": count_tests(kt_files("app/src/test")),
    }


def badge_values(s):
    """shields.io label (URL-encoded as it appears in the README) -> message."""
    return {
        "version": s["version"],
        "unit%20tests": str(s["tests"]),
        "lines%20of%20code": k(s["loc_main"]),
        "test%20code": k(s["loc_test"]),
    }


def rewrite(text, values):
    for label, msg in values.items():
        # https://img.shields.io/badge/<label>-<message>-<color>
        text = re.sub(rf"(img\.shields\.io/badge/{re.escape(label)}-)[^-?\"]+(-)",
                      lambda m: m.group(1) + msg.replace("-", "--") + m.group(2), text)
    return text


def main():
    s = stats()
    values = badge_values(s)
    repo_stats = {"loc_main": s["loc_main"], "loc_test": s["loc_test"], "tests": s["tests"]}
    stale = []
    for readme in READMES:
        if not readme.exists():
            continue
        old = readme.read_text(encoding="utf-8")
        new = rewrite(old, values)
        if new != old:
            stale.append(readme.name)
            if "--check" not in sys.argv:
                readme.write_text(new, encoding="utf-8")
    stats_file = ROOT / ".github/repo-stats.json"
    new_json = json.dumps(repo_stats, indent=2) + "\n"
    if not stats_file.exists() or stats_file.read_text(encoding="utf-8") != new_json:
        stale.append(str(stats_file.relative_to(ROOT)))
        if "--check" not in sys.argv:
            stats_file.parent.mkdir(exist_ok=True)
            stats_file.write_text(new_json, encoding="utf-8")
    print(f"version {s['version']} · {s['tests']} unit tests · {k(s['loc_main'])} lines of code · "
          f"{k(s['loc_test'])} test code")
    if stale:
        print(("stale: " if "--check" in sys.argv else "updated: ") + ", ".join(stale))
        if "--check" in sys.argv:
            sys.exit(1)


if __name__ == "__main__":
    main()
