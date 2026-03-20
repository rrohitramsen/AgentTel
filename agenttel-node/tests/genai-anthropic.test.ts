import { describe, it, expect, vi } from 'vitest';
import { GenAiSpanBuilder } from '../src/genai/span-builder.js';
import * as attrs from '../src/attributes.js';

function createMockSpan() {
  const attributes = new Map<string, unknown>();
  return {
    setAttribute: vi.fn((key: string, value: unknown) => {
      attributes.set(key, value);
    }),
    setAttributes: vi.fn(),
    recordException: vi.fn(),
    end: vi.fn(),
    isRecording: vi.fn(() => true),
    _attributes: attributes,
  };
}

describe('GenAI Anthropic Instrumentation', () => {
  it('creates span with anthropic system attribute', () => {
    const mockSpan = createMockSpan();
    const tracer = { startSpan: vi.fn(() => mockSpan) };
    const builder = new GenAiSpanBuilder(tracer as any);

    builder.startChatSpan('claude-opus-4', 'anthropic');

    expect(tracer.startSpan).toHaveBeenCalledWith(
      'chat claude-opus-4',
      expect.objectContaining({
        attributes: expect.objectContaining({
          [attrs.GENAI_SYSTEM]: 'anthropic',
          [attrs.GENAI_REQUEST_MODEL]: 'claude-opus-4',
        }),
      }),
    );
  });

  it('extracts input_tokens and output_tokens', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'claude-opus-4',
      responseId: 'msg_abc123',
      inputTokens: 500,
      outputTokens: 200,
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_INPUT_TOKENS, 500);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_OUTPUT_TOKENS, 200);
  });

  it('handles stop_reason via finishReasons', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'claude-opus-4',
      responseId: 'msg_123',
      inputTokens: 100,
      outputTokens: 50,
      finishReasons: ['end_turn'],
    });

    expect(span.setAttribute).toHaveBeenCalledWith(
      attrs.GENAI_RESPONSE_FINISH_REASONS,
      ['end_turn'],
    );
  });

  it('handles cache_read_input_tokens via setTokenDetails', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.setTokenDetails(span as any, {
      cachedReadTokens: 300,
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_CACHED_READ_TOKENS, 300);
  });

  it('handles errors by recording exception', () => {
    const span = createMockSpan();
    const error = new Error('overloaded_error');

    GenAiSpanBuilder.endSpanError(span as any, error);

    expect(span.recordException).toHaveBeenCalledWith(error);
    expect(span.end).toHaveBeenCalled();
  });

  it('handles streaming SSE events with accumulated tokens', () => {
    const span = createMockSpan();

    // After streaming completes, finalize span with accumulated data
    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'claude-opus-4',
      responseId: 'msg_stream_456',
      inputTokens: 1000,
      outputTokens: 800,
      finishReasons: ['end_turn'],
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_INPUT_TOKENS, 1000);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_OUTPUT_TOKENS, 800);
    expect(span.end).toHaveBeenCalled();
  });

  it('handles null client gracefully', () => {
    expect(() => new GenAiSpanBuilder(undefined)).not.toThrow();
  });

  it('calculates cost for known Anthropic models', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'claude-opus-4',
      responseId: 'msg_cost',
      inputTokens: 1_000_000,
      outputTokens: 1_000_000,
    });

    // claude-opus-4: input=15.00/M, output=75.00/M => 90.00
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_COST_USD, 90.0);
  });
});
