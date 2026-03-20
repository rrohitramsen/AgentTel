import { describe, it, expect } from 'vitest';
import { ErrorClassifier } from '../src/error/classifier.js';

describe('ErrorClassifier', () => {
  const c = new ErrorClassifier();

  it('classifies timeout exception', () => {
    const result = c.classifyException('java.net.SocketTimeoutException', 'Read timed out');
    expect(result.category).toBe('dependency_timeout');
  });

  it('classifies connection error', () => {
    const result = c.classifyException('ConnectionRefusedError', 'Connection refused');
    expect(result.category).toBe('connection_error');
  });

  it('classifies code bug', () => {
    const result = c.classifyException('NullPointerException', 'null pointer dereference');
    expect(result.category).toBe('code_bug');
  });

  it('classifies rate limited', () => {
    const result = c.classifyException('ThrottlingException', 'Rate limit exceeded');
    expect(result.category).toBe('rate_limited');
  });

  it('infers dependency from exception', () => {
    const result = c.classifyException('PSQLException', 'Connection to postgres timed out');
    expect(result.category).toBe('dependency_timeout');
    expect(result.dependency).toBe('postgres');
  });

  describe('HTTP status classification', () => {
    const cases = [
      { code: 400, expected: 'data_validation' },
      { code: 401, expected: 'auth_failure' },
      { code: 403, expected: 'auth_failure' },
      { code: 429, expected: 'rate_limited' },
      { code: 408, expected: 'dependency_timeout' },
      { code: 502, expected: 'connection_error' },
      { code: 503, expected: 'connection_error' },
      { code: 504, expected: 'dependency_timeout' },
      { code: 500, expected: 'code_bug' },
    ];

    for (const { code, expected } of cases) {
      it(`classifies HTTP ${code} as ${expected}`, () => {
        const result = c.classifyHTTPStatus(code, 'test-dep');
        expect(result.category).toBe(expected);
      });
    }
  });
});
