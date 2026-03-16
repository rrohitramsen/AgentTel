"""Error classification into categories."""

from __future__ import annotations

from agenttel.enums import ErrorCategory


class ErrorClassifier:
    """Classifies exceptions and error messages into ErrorCategory."""

    # Map exception type names to categories
    _EXCEPTION_MAP: dict[str, ErrorCategory] = {
        "TimeoutError": ErrorCategory.DEPENDENCY_TIMEOUT,
        "asyncio.TimeoutError": ErrorCategory.DEPENDENCY_TIMEOUT,
        "httpx.TimeoutException": ErrorCategory.DEPENDENCY_TIMEOUT,
        "requests.exceptions.Timeout": ErrorCategory.DEPENDENCY_TIMEOUT,
        "ConnectionError": ErrorCategory.CONNECTION_ERROR,
        "ConnectionRefusedError": ErrorCategory.CONNECTION_ERROR,
        "ConnectionResetError": ErrorCategory.CONNECTION_ERROR,
        "httpx.ConnectError": ErrorCategory.CONNECTION_ERROR,
        "requests.exceptions.ConnectionError": ErrorCategory.CONNECTION_ERROR,
        "PermissionError": ErrorCategory.AUTH_FAILURE,
        "ValueError": ErrorCategory.DATA_VALIDATION,
        "TypeError": ErrorCategory.CODE_BUG,
        "AttributeError": ErrorCategory.CODE_BUG,
        "KeyError": ErrorCategory.CODE_BUG,
        "IndexError": ErrorCategory.CODE_BUG,
        "ZeroDivisionError": ErrorCategory.CODE_BUG,
        "MemoryError": ErrorCategory.RESOURCE_EXHAUSTION,
        "OSError": ErrorCategory.RESOURCE_EXHAUSTION,
    }

    # Keyword patterns in error messages
    _MESSAGE_PATTERNS: list[tuple[list[str], ErrorCategory]] = [
        (["timeout", "timed out", "deadline exceeded"], ErrorCategory.DEPENDENCY_TIMEOUT),
        (["connection refused", "connection reset", "connection closed", "unreachable"], ErrorCategory.CONNECTION_ERROR),
        (["rate limit", "too many requests", "throttl"], ErrorCategory.RATE_LIMITED),
        (["unauthorized", "forbidden", "authentication", "auth failed", "403", "401"], ErrorCategory.AUTH_FAILURE),
        (["out of memory", "disk full", "no space", "resource exhausted"], ErrorCategory.RESOURCE_EXHAUSTION),
        (["validation", "invalid", "malformed", "bad request", "422"], ErrorCategory.DATA_VALIDATION),
    ]

    def classify_exception(self, exc: BaseException) -> ErrorCategory:
        """Classify an exception into an error category."""
        exc_type = type(exc).__name__
        # Check direct type name
        if exc_type in self._EXCEPTION_MAP:
            return self._EXCEPTION_MAP[exc_type]
        # Check fully qualified type name
        fqn = f"{type(exc).__module__}.{exc_type}"
        if fqn in self._EXCEPTION_MAP:
            return self._EXCEPTION_MAP[fqn]
        # Fall back to message-based classification
        return self.classify_message(str(exc))

    def classify_message(self, message: str) -> ErrorCategory:
        """Classify an error message into a category."""
        msg_lower = message.lower()
        for patterns, category in self._MESSAGE_PATTERNS:
            if any(p in msg_lower for p in patterns):
                return category
        return ErrorCategory.UNKNOWN

    def is_transient(self, category: ErrorCategory) -> bool:
        """Determine if an error category is typically transient."""
        return category in {
            ErrorCategory.DEPENDENCY_TIMEOUT,
            ErrorCategory.CONNECTION_ERROR,
            ErrorCategory.RATE_LIMITED,
            ErrorCategory.RESOURCE_EXHAUSTION,
        }
