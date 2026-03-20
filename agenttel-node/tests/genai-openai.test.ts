import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GenAiSpanBuilder, type ChatResult } from '../src/genai/span-builder.js';
import * as attrs from '../src/attributes.js';

// Mock span that records setAttribute calls
function createMockSpan() {
  const attributes = new Map<string, unknown>();
  const events: Array<{ name: string; attributes?: Record<string, unknown> }> = [];
  return {
    setAttribute: vi.fn((key: string, value: unknown) => {
      attributes.set(key, value);
    }),
    setAttributes: vi.fn(),
    recordException: vi.fn((err: Error) => {
      events.push({ name: 'exception', attributes: { 'exception.message': err.message } });
    }),
    end: vi.fn(),
    isRecording: vi.fn(() => true),
    _attributes: attributes,
    _events: events,
  };
}

// Mock tracer that returns mock spans
function createMockTracer() {
  const mockSpan = createMockSpan();
  const mockTracer = {
    startSpan: vi.fn(() => mockSpan),
  };
  return {
    mockTracer,
    span: mockSpan,
  };
}

describe('GenAI OpenAI Instrumentation', () => {
  it('creates span with correct model and system attributes', () => {
    const { mockTracer, span } = createMockTracer();
    const builder = new GenAiSpanBuilder(mockTracer as any);

    const result = builder.startChatSpan('gpt-4o', 'openai');

    expect(mockTracer.startSpan).toHaveBeenCalledWith(
      'chat gpt-4o',
      expect.objectContaining({
        attributes: expect.objectContaining({
          [attrs.GENAI_OPERATION_NAME]: 'chat',
          [attrs.GENAI_SYSTEM]: 'openai',
          [attrs.GENAI_REQUEST_MODEL]: 'gpt-4o',
        }),
      }),
    );
  });

  it('extracts prompt_tokens and completion_tokens', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'gpt-4o',
      responseId: 'chatcmpl-abc123',
      inputTokens: 100,
      outputTokens: 50,
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_INPUT_TOKENS, 100);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_OUTPUT_TOKENS, 50);
    expect(span.end).toHaveBeenCalled();
  });

  it('calculates cost for known models', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'gpt-4o',
      responseId: 'chatcmpl-123',
      inputTokens: 1_000_000,
      outputTokens: 1_000_000,
    });

    // gpt-4o: input=2.50/M, output=10.00/M => 12.50
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_COST_USD, 12.50);
  });

  it('handles errors by recording exception', () => {
    const span = createMockSpan();
    const error = new Error('rate limit exceeded');

    GenAiSpanBuilder.endSpanError(span as any, error);

    expect(span.recordException).toHaveBeenCalledWith(error);
    expect(span.end).toHaveBeenCalled();
  });

  it('handles streaming with accumulated tokens', () => {
    const span = createMockSpan();

    // Simulate accumulated tokens from streaming chunks
    GenAiSpanBuilder.endSpanSuccess(span as any, {
      responseModel: 'gpt-4o',
      responseId: 'chatcmpl-stream',
      inputTokens: 200,
      outputTokens: 300,
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_INPUT_TOKENS, 200);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_USAGE_OUTPUT_TOKENS, 300);
  });

  it('handles null client gracefully', () => {
    // GenAiSpanBuilder doesn't require a client, so passing undefined
    // tracer should fall back to default
    expect(() => new GenAiSpanBuilder(undefined)).not.toThrow();
  });

  it('handles completion_tokens_details.reasoning_tokens via setTokenDetails', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.setTokenDetails(span as any, {
      reasoningTokens: 500,
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_REASONING_TOKENS, 500);
  });

  it('handles prompt_tokens_details.cached_tokens via setTokenDetails', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.setTokenDetails(span as any, {
      cachedReadTokens: 250,
    });

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_CACHED_READ_TOKENS, 250);
  });

  it('sets request parameters (temperature, maxTokens, topP)', () => {
    const span = createMockSpan();

    GenAiSpanBuilder.setRequestParams(span as any, 0.7, 4096, 0.9);

    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_REQUEST_TEMPERATURE, 0.7);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_REQUEST_MAX_TOKENS, 4096);
    expect(span.setAttribute).toHaveBeenCalledWith(attrs.GENAI_REQUEST_TOP_P, 0.9);
  });
});
