"""Detect infrastructure dependencies from Java source files."""

from __future__ import annotations

import re
from dataclasses import dataclass

from .scanner import SourceFile

# Patterns that indicate infrastructure dependencies
_DEPENDENCY_PATTERNS: list[tuple[str, str, re.Pattern[str]]] = [
    ("database", "PostgreSQL", re.compile(r'postgresql|PgConnection|org\.postgresql')),
    ("database", "MySQL", re.compile(r'mysql|com\.mysql')),
    ("database", "MongoDB", re.compile(r'mongo|MongoClient|MongoTemplate')),
    ("database", "H2", re.compile(r'org\.h2|h2:mem|H2Console')),
    ("database", "JPA", re.compile(r'JpaRepository|EntityManager|@Entity')),
    ("cache", "Redis", re.compile(r'redis|RedisTemplate|Jedis|Lettuce')),
    ("cache", "Caffeine", re.compile(r'caffeine|@Cacheable')),
    ("queue", "Kafka", re.compile(r'kafka|KafkaTemplate|@KafkaListener')),
    ("queue", "RabbitMQ", re.compile(r'rabbit|RabbitTemplate|@RabbitListener')),
    ("queue", "SQS", re.compile(r'software\.amazon\.sqs|SqsClient')),
    ("http", "RestTemplate", re.compile(r'RestTemplate')),
    ("http", "WebClient", re.compile(r'WebClient\.builder')),
    ("http", "Feign", re.compile(r'@FeignClient')),
    ("storage", "S3", re.compile(r'S3Client|AmazonS3|software\.amazon.*s3')),
    ("search", "Elasticsearch", re.compile(r'elasticsearch|ElasticsearchClient')),
]


@dataclass
class Dependency:
    """A detected infrastructure dependency."""

    type: str
    name: str
    evidence: str


class DependencyDetector:
    """Detects infrastructure dependencies from Java source imports and usage."""

    def detect(self, source_files: list[SourceFile]) -> list[Dependency]:
        seen: set[tuple[str, str]] = set()
        results: list[Dependency] = []

        for sf in source_files:
            for dep_type, dep_name, pattern in _DEPENDENCY_PATTERNS:
                if (dep_type, dep_name) in seen:
                    continue

                match = pattern.search(sf.content)
                if match:
                    seen.add((dep_type, dep_name))
                    results.append(Dependency(
                        type=dep_type,
                        name=dep_name,
                        evidence=f"Found '{match.group()}' in {sf.path.name}",
                    ))

        return results
