"""Detect REST endpoints from Spring Boot source files."""

from __future__ import annotations

import re
from dataclasses import dataclass

from .scanner import SourceFile

# Matches Spring web annotations:
#   @GetMapping("/foo")  @PostMapping(value="/bar", ...)
#   @RequestMapping(value = "/baz", method = RequestMethod.GET)
_MAPPING_PATTERN = re.compile(
    r'@(Get|Post|Put|Delete|Patch|Request)Mapping\s*\('
    r'(?:[^)]*?(?:value\s*=\s*)?["\']([^"\']+)["\'])?'
    r'[^)]*\)',
)

# Matches class-level @RequestMapping("/prefix")
_CLASS_MAPPING_PATTERN = re.compile(
    r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']\s*\)',
)

# Matches the method or class declaration following the annotation
_METHOD_DECL_PATTERN = re.compile(
    r'(?:public|private|protected)?\s+\w[\w<>,\s]*\s+(\w+)\s*\(',
)

_CLASS_DECL_PATTERN = re.compile(
    r'class\s+(\w+)',
)

# Maps annotation prefix to HTTP method
_ANNOTATION_TO_METHOD = {
    "Get": "GET",
    "Post": "POST",
    "Put": "PUT",
    "Delete": "DELETE",
    "Patch": "PATCH",
}


@dataclass
class Endpoint:
    """A detected REST endpoint."""

    method: str
    path: str
    class_name: str
    method_name: str


class EndpointDetector:
    """Detects REST endpoints from Spring Web annotations in Java source."""

    def detect(self, source_files: list[SourceFile]) -> list[Endpoint]:
        endpoints: list[Endpoint] = []
        for sf in source_files:
            endpoints.extend(self._detect_in_file(sf))
        return endpoints

    def _detect_in_file(self, sf: SourceFile) -> list[Endpoint]:
        results: list[Endpoint] = []
        lines = sf.content.split("\n")

        # Detect class name and class-level prefix
        class_name = "Unknown"
        class_prefix = ""
        class_match = _CLASS_DECL_PATTERN.search(sf.content)
        if class_match:
            class_name = class_match.group(1)

        prefix_match = _CLASS_MAPPING_PATTERN.search(sf.content)
        if prefix_match:
            class_prefix = prefix_match.group(1).rstrip("/")

        for i, line in enumerate(lines):
            mapping_match = _MAPPING_PATTERN.search(line)
            if not mapping_match:
                continue

            annotation_type = mapping_match.group(1)
            path_value = mapping_match.group(2) or ""

            # Determine HTTP method
            if annotation_type == "Request":
                # Look for method = RequestMethod.XXX
                method_match = re.search(r'method\s*=\s*RequestMethod\.(\w+)', line)
                http_method = method_match.group(1) if method_match else "GET"
            else:
                http_method = _ANNOTATION_TO_METHOD.get(annotation_type, "GET")

            # Build full path
            full_path = class_prefix + "/" + path_value.lstrip("/") if path_value else class_prefix or "/"
            if not full_path.startswith("/"):
                full_path = "/" + full_path

            # Find method name from subsequent lines
            method_name = "unknown"
            for j in range(i + 1, min(i + 5, len(lines))):
                method_match = _METHOD_DECL_PATTERN.search(lines[j])
                if method_match:
                    method_name = method_match.group(1)
                    break

            results.append(Endpoint(
                method=http_method,
                path=full_path,
                class_name=class_name,
                method_name=method_name,
            ))

        return results
