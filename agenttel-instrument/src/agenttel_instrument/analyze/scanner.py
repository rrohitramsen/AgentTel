"""Recursively scan directories for Java source files."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass
class SourceFile:
    """A scanned source file with its path and content."""

    path: Path
    content: str


class SourceScanner:
    """Recursively scans a directory tree for Java source files."""

    def scan(self, root: Path) -> list[SourceFile]:
        """Scan *root* for ``*.java`` files, returning path + content pairs."""
        results: list[SourceFile] = []
        if not root.is_dir():
            return results

        for java_file in sorted(root.rglob("*.java")):
            if java_file.is_file():
                try:
                    content = java_file.read_text(encoding="utf-8")
                    results.append(SourceFile(path=java_file, content=content))
                except (OSError, UnicodeDecodeError):
                    # Skip unreadable files silently
                    pass
        return results
