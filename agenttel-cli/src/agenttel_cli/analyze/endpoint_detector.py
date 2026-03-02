"""Endpoint detector — finds Spring Web MVC/WebFlux endpoint annotations in Java source."""

from __future__ import annotations

import re
from dataclasses import dataclass

from agenttel_cli.analyze.scanner import SourceFile


@dataclass(frozen=True)
class DetectedEndpoint:
    """A single HTTP endpoint detected from source code."""

    method: str
    path: str
    class_name: str
    method_name: str


# Mapping from annotation name to HTTP method.
_MAPPING_ANNOTATIONS: dict[str, str] = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
    "RequestMapping": "REQUEST",  # resolved per-instance below
}

# Regex: captures the annotation name and its optional value/path attribute.
# Handles forms like:
#   @GetMapping("/foo")
#   @GetMapping(value = "/foo")
#   @GetMapping(path = "/foo")
#   @RequestMapping(value = "/foo", method = RequestMethod.GET)
#   @GetMapping  (no path — defaults to "")
_ANNOTATION_PATTERN = re.compile(
    r"@("
    + "|".join(_MAPPING_ANNOTATIONS.keys())
    + r")"
    r"(?:\s*\(\s*"
    r"(?:"
    r'(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"'  # group 2: path from value/path/positional
    r")?"
    r"[^)]*"
    r"\))?"
)

# Regex to extract the HTTP method from @RequestMapping(method = RequestMethod.XXX).
_REQUEST_METHOD_PATTERN = re.compile(
    r"method\s*=\s*RequestMethod\.(\w+)"
)

# Regex: captures the class-level @RequestMapping path.
_CLASS_REQUEST_MAPPING = re.compile(
    r"@RequestMapping\s*\(\s*"
    r'(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"'
    r"[^)]*\)"
)

# Regex: extracts the class name from a class declaration.
_CLASS_NAME_PATTERN = re.compile(
    r"(?:public\s+)?class\s+(\w+)"
)

# Regex: extracts the method name that immediately follows the annotation block.
# Looks for a return-type + method-name pattern after the annotation.
_METHOD_NAME_PATTERN = re.compile(
    r"(?:public|protected|private)?\s*"
    r"(?:static\s+)?"
    r"(?:[\w<>\[\],\s]+?)\s+"  # return type (non-greedy)
    r"(\w+)\s*\("  # method name
)


class EndpointDetector:
    """Detects Spring MVC/WebFlux endpoint annotations in Java source files."""

    def detect(self, source_files: list[SourceFile]) -> list[DetectedEndpoint]:
        """Scan all *source_files* and return detected endpoints.

        Parameters
        ----------
        source_files:
            Java source files to analyse.

        Returns
        -------
        list[DetectedEndpoint]
            All HTTP endpoints found in the source.
        """
        endpoints: list[DetectedEndpoint] = []

        for source_file in source_files:
            endpoints.extend(self._detect_in_file(source_file))

        return endpoints

    def _detect_in_file(self, source_file: SourceFile) -> list[DetectedEndpoint]:
        content = source_file.content

        # Determine the class name.
        class_match = _CLASS_NAME_PATTERN.search(content)
        class_name = class_match.group(1) if class_match else "UnknownClass"

        # Determine class-level request mapping prefix.
        class_prefix = ""
        class_mapping_match = _CLASS_REQUEST_MAPPING.search(content)
        if class_mapping_match:
            class_prefix = class_mapping_match.group(1).rstrip("/")

        endpoints: list[DetectedEndpoint] = []
        lines = content.split("\n")

        for line_idx, line in enumerate(lines):
            for match in _ANNOTATION_PATTERN.finditer(line):
                annotation_name = match.group(1)
                raw_path = match.group(2) or ""

                # Skip class-level @RequestMapping (it is used as a prefix, not an endpoint).
                if annotation_name == "RequestMapping" and self._is_class_level(lines, line_idx):
                    continue

                # Resolve HTTP method.
                http_method = _MAPPING_ANNOTATIONS[annotation_name]
                if http_method == "REQUEST":
                    http_method = self._resolve_request_method(line)

                # Build the full path.
                path = self._normalize_path(class_prefix, raw_path)

                # Find the Java method name following this annotation.
                method_name = self._find_method_name(lines, line_idx)

                endpoints.append(
                    DetectedEndpoint(
                        method=http_method,
                        path=path,
                        class_name=class_name,
                        method_name=method_name,
                    )
                )

        return endpoints

    @staticmethod
    def _is_class_level(lines: list[str], annotation_line_idx: int) -> bool:
        """Return True if the annotation at *annotation_line_idx* is on a class declaration."""
        for subsequent_line in lines[annotation_line_idx + 1 :]:
            stripped = subsequent_line.strip()
            if not stripped or stripped.startswith("@") or stripped.startswith("//"):
                continue
            return "class " in stripped
        return False

    @staticmethod
    def _resolve_request_method(line: str) -> str:
        """Extract the HTTP method from a @RequestMapping annotation line."""
        method_match = _REQUEST_METHOD_PATTERN.search(line)
        if method_match:
            return method_match.group(1).upper()
        return "GET"  # Default for @RequestMapping without explicit method.

    @staticmethod
    def _normalize_path(prefix: str, path: str) -> str:
        """Combine a class-level prefix with a method-level path."""
        if not path:
            full = prefix or "/"
        else:
            path = path if path.startswith("/") else f"/{path}"
            full = f"{prefix}{path}" if prefix else path
        return full if full.startswith("/") else f"/{full}"

    @staticmethod
    def _find_method_name(lines: list[str], annotation_line_idx: int) -> str:
        """Find the Java method name declared after the annotation."""
        search_window = "\n".join(lines[annotation_line_idx + 1 : annotation_line_idx + 6])
        method_match = _METHOD_NAME_PATTERN.search(search_window)
        return method_match.group(1) if method_match else "unknownMethod"
