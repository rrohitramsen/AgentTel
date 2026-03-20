import { ErrorCategory } from '../enums-impl.js';
import type { ErrorClassification } from '../interfaces.js';

/** Classifies errors from exception types and HTTP status codes. */
export class ErrorClassifier {
  /** Classify an error by exception type name and message. */
  classifyException(exceptionType: string, message: string): ErrorClassification {
    const lower = `${exceptionType} ${message}`.toLowerCase();
    const dep = inferDependency(lower);

    let category: ErrorCategory = ErrorCategory.UNKNOWN;

    if (containsAny(lower, 'timeout', 'timedout', 'timed out', 'deadline exceeded')) {
      category = ErrorCategory.DEPENDENCY_TIMEOUT;
    } else if (containsAny(lower, 'connection refused', 'connection reset', 'connect error', 'econnrefused', 'econnreset')) {
      category = ErrorCategory.CONNECTION_ERROR;
    } else if (containsAny(lower, 'oom', 'out of memory', 'resource exhausted', 'too many open files')) {
      category = ErrorCategory.RESOURCE_EXHAUSTION;
    } else if (containsAny(lower, 'validation', 'invalid', 'malformed', 'parse error')) {
      category = ErrorCategory.DATA_VALIDATION;
    } else if (containsAny(lower, 'auth', 'unauthorized', 'forbidden', 'permission denied')) {
      category = ErrorCategory.AUTH_FAILURE;
    } else if (containsAny(lower, 'rate limit', 'throttl', '429')) {
      category = ErrorCategory.RATE_LIMITED;
    } else if (containsAny(lower, 'null pointer', 'nil pointer', 'index out of', 'assertion')) {
      category = ErrorCategory.CODE_BUG;
    }

    return { category, rootException: exceptionType, dependency: dep };
  }

  /** Classify an error by HTTP status code. */
  classifyHTTPStatus(statusCode: number, dependency: string): ErrorClassification {
    let category: ErrorCategory = ErrorCategory.UNKNOWN;

    if (statusCode === 400) category = ErrorCategory.DATA_VALIDATION;
    else if (statusCode === 401 || statusCode === 403) category = ErrorCategory.AUTH_FAILURE;
    else if (statusCode === 429) category = ErrorCategory.RATE_LIMITED;
    else if (statusCode === 408 || statusCode === 504) category = ErrorCategory.DEPENDENCY_TIMEOUT;
    else if (statusCode === 502 || statusCode === 503) category = ErrorCategory.CONNECTION_ERROR;
    else if (statusCode >= 500) category = ErrorCategory.CODE_BUG;

    return { category, rootException: '', dependency };
  }
}

function containsAny(s: string, ...substrs: string[]): boolean {
  return substrs.some((sub) => s.includes(sub));
}

function inferDependency(lower: string): string {
  const keywords = ['postgres', 'mysql', 'redis', 'mongo', 'cassandra', 'dynamodb', 'sql'];
  for (const kw of keywords) {
    if (lower.includes(kw)) return kw;
  }
  return '';
}
