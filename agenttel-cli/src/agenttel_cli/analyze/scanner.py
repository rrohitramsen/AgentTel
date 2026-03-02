"""Source scanner — recursively walks a Java source tree and reads file contents."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SourceFile:
    """A Java source file with its path and content."""

    path: Path
    content: str


class SourceScanner:
    """Walks a Java source tree recursively, reads .java files, and returns their contents."""

    JAVA_EXTENSION = ".java"

    def scan(self, root: Path) -> list[SourceFile]:
        """Scan *root* recursively for .java files.

        Parameters
        ----------
        root:
            The root directory to scan.

        Returns
        -------
        list[SourceFile]
            Sorted list of source files found under *root*.
        """
        if not root.is_dir():
            return []

        source_files: list[SourceFile] = []

        for java_path in sorted(root.rglob(f"*{self.JAVA_EXTENSION}")):
            if not java_path.is_file():
                continue
            try:
                content = java_path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                # Skip files that cannot be read.
                continue
            source_files.append(SourceFile(path=java_path, content=content))

        return source_files
