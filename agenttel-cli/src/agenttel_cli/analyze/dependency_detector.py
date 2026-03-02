"""Dependency detector — identifies external dependencies from Java source code."""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

from agenttel_cli.analyze.scanner import SourceFile


class DependencyType(str, Enum):
    """Categorisation of a detected dependency."""

    DATABASE = "database"
    HTTP = "http"
    MESSAGING = "messaging"
    CACHE = "cache"
    EXTERNAL = "external"


@dataclass(frozen=True)
class DetectedDependency:
    """A dependency detected from source code analysis."""

    type: str  # One of DependencyType values.
    name: str
    evidence: str  # The import or annotation that triggered detection.


# Each entry maps a keyword (class name, annotation, or type reference) to
# (DependencyType, human-readable name).
_DATABASE_MARKERS: dict[str, tuple[DependencyType, str]] = {
    "JdbcTemplate": (DependencyType.DATABASE, "JDBC"),
    "JpaRepository": (DependencyType.DATABASE, "JPA"),
    "CrudRepository": (DependencyType.DATABASE, "JPA"),
    "R2dbcRepository": (DependencyType.DATABASE, "R2DBC"),
    "@Entity": (DependencyType.DATABASE, "JPA Entity"),
    "DataSource": (DependencyType.DATABASE, "DataSource"),
}

_HTTP_MARKERS: dict[str, tuple[DependencyType, str]] = {
    "RestTemplate": (DependencyType.HTTP, "RestTemplate"),
    "WebClient": (DependencyType.HTTP, "WebClient"),
    "HttpClient": (DependencyType.HTTP, "HttpClient"),
    "@FeignClient": (DependencyType.HTTP, "Feign"),
    "RestClient": (DependencyType.HTTP, "RestClient"),
}

_MESSAGING_MARKERS: dict[str, tuple[DependencyType, str]] = {
    "KafkaTemplate": (DependencyType.MESSAGING, "Kafka Producer"),
    "@KafkaListener": (DependencyType.MESSAGING, "Kafka Consumer"),
    "RabbitTemplate": (DependencyType.MESSAGING, "RabbitMQ Producer"),
    "@RabbitListener": (DependencyType.MESSAGING, "RabbitMQ Consumer"),
    "JmsTemplate": (DependencyType.MESSAGING, "JMS"),
}

_CACHE_MARKERS: dict[str, tuple[DependencyType, str]] = {
    "@Cacheable": (DependencyType.CACHE, "Spring Cache"),
    "RedisTemplate": (DependencyType.CACHE, "Redis"),
    "CacheManager": (DependencyType.CACHE, "CacheManager"),
}

_ALL_MARKERS: dict[str, tuple[DependencyType, str]] = {
    **_DATABASE_MARKERS,
    **_HTTP_MARKERS,
    **_MESSAGING_MARKERS,
    **_CACHE_MARKERS,
}

# Pattern to detect @Value("${...url}") — external service URL injection.
_EXTERNAL_URL_PATTERN = re.compile(
    r'@Value\s*\(\s*"\$\{([^}]*url[^}]*)\}"\s*\)',
    re.IGNORECASE,
)


class DependencyDetector:
    """Detects infrastructure and service dependencies from Java source files."""

    def detect(self, source_files: list[SourceFile]) -> list[DetectedDependency]:
        """Analyse all *source_files* and return detected dependencies.

        Parameters
        ----------
        source_files:
            Java source files to analyse.

        Returns
        -------
        list[DetectedDependency]
            Unique dependencies discovered across the source tree.
        """
        seen: set[tuple[str, str]] = set()
        dependencies: list[DetectedDependency] = []

        for source_file in source_files:
            for dep in self._detect_in_file(source_file):
                key = (dep.type, dep.name)
                if key not in seen:
                    seen.add(key)
                    dependencies.append(dep)

        return dependencies

    def _detect_in_file(self, source_file: SourceFile) -> list[DetectedDependency]:
        content = source_file.content
        results: list[DetectedDependency] = []

        # Check every marker against the file content.
        for marker, (dep_type, name) in _ALL_MARKERS.items():
            # For annotations (prefixed with @), search for the annotation token.
            # For class/interface names, check both import statements and usage.
            search_token = marker.lstrip("@")
            if search_token in content:
                evidence = self._find_evidence(content, marker, search_token)
                results.append(
                    DetectedDependency(type=dep_type.value, name=name, evidence=evidence)
                )

        # Detect external URL properties.
        for match in _EXTERNAL_URL_PATTERN.finditer(content):
            property_key = match.group(1)
            results.append(
                DetectedDependency(
                    type=DependencyType.EXTERNAL.value,
                    name=property_key,
                    evidence=match.group(0),
                )
            )

        return results

    @staticmethod
    def _find_evidence(content: str, marker: str, search_token: str) -> str:
        """Return the first line containing the marker as evidence."""
        for line in content.split("\n"):
            if search_token in line:
                return line.strip()
        return marker
