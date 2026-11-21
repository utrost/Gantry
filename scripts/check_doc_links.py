#!/usr/bin/env python3
"""Check repository Markdown's local file/image links and heading anchors, offline."""
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


def prose(text):
    return re.sub(r"(?ms)^(`{3,}|~{3,})[^\n]*\n.*?^\1[ \t]*$", "", text)


def anchors(path):
    text = prose(path.read_text(encoding="utf-8"))
    result, seen = set(), {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", text, re.M):
        heading = re.sub(r"<[^>]+>", "", heading).lower()
        slug = "".join(c for c in heading if c.isalnum() or c in "_- ").replace(" ", "-")
        duplicate = seen.get(slug, 0)
        seen[slug] = duplicate + 1
        result.add(slug + (f"-{duplicate}" if duplicate else ""))
    result.update(re.findall(r'<a\s+(?:name|id)=["\']([^"\']+)', text))
    return result


def check(paths):
    errors, count = [], 0
    for path in paths:
        text = prose(path.read_text(encoding="utf-8"))
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            target = target.strip().split(' "')[0].strip("<>")
            url = urlsplit(target)
            if url.scheme or url.netloc:
                continue
            count += 1
            destination = path.parent / unquote(url.path) if url.path else path
            if not destination.exists():
                errors.append(f"{path}: missing local target {target}")
            elif url.fragment and destination.suffix.lower() == ".md" and unquote(url.fragment) not in anchors(destination):
                errors.append(f"{path}: missing heading anchor {target}")
    return count, errors


def main():
    root = Path(__file__).resolve().parent.parent
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "*.md"], cwd=root, text=True).splitlines()
    paths = sorted({root / name for name in names})
    count, errors = check(paths)
    for error in errors:
        print(error, file=sys.stderr)
    print(f"Checked {len(paths)} Markdown files and {count} local file/image/heading references; {len(errors)} errors.")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
