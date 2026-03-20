/**
 * OpenAI SDK instrumentation wrapper.
 *
 * Monkey-patches `client.chat.completions.create()` to automatically create
 * OpenTelemetry spans with GenAI semantic attributes for every chat completion.
 *
 * Usage:
 *   import OpenAI from 'openai';
 *   import { instrumentOpenAI } from '@agenttel/node';
 *
 *   const client = instrumentOpenAI(new OpenAI());
 *   const response = await client.chat.completions.create({ model: 'gpt-4o', ... });
 */

import { type Span } from '@opentelemetry/api';
import { GenAiSpanBuilder, type ChatResult } from './span-builder.js';

const SYSTEM = 'openai';

/**
 * Instrument an OpenAI client for automatic tracing.
 *
 * Wraps `chat.completions.create()` to create gen_ai chat spans with
 * full attribute instrumentation including token usage, cost, and streaming.
 *
 * @param client - An OpenAI client instance.
 * @returns The instrumented client (same reference, mutated in place).
 */
export function instrumentOpenAI<T extends Record<string, any>>(client: T): T {
  if (!client) return client;

  const chat = client.chat;
  if (!chat?.completions?.create) return client;

  const spanBuilder = new GenAiSpanBuilder();
  const originalCreate = chat.completions.create.bind(chat.completions);

  chat.completions.create = async function tracedCreate(...args: any[]) {
    const params = args[0] ?? {};
    const model: string = params.model ?? 'unknown';

    const span = spanBuilder.startChatSpan(model, SYSTEM);

    // Set optional request parameters.
    GenAiSpanBuilder.setRequestParams(
      span,
      params.temperature ?? undefined,
      params.max_tokens ?? params.max_completion_tokens ?? undefined,
      params.top_p ?? undefined,
    );

    try {
      const response = await originalCreate(...args);

      // Handle streaming responses.
      if (params.stream) {
        return wrapStream(response, span, model);
      }

      // Non-streaming: extract response data and finalize span.
      finalizeSpan(span, response, model);
      return response;
    } catch (err) {
      GenAiSpanBuilder.endSpanError(span, err instanceof Error ? err : new Error(String(err)));
      throw err;
    }
  };

  return client;
}

/**
 * Extract response data and end the span for a non-streaming response.
 */
function finalizeSpan(span: Span, response: any, requestModel: string): void {
  let inputTokens = 0;
  let outputTokens = 0;
  let reasoningTokens = 0;
  let cachedTokens = 0;
  let finishReason: string | undefined;
  let responseId: string | undefined;
  let responseModel = requestModel;

  if (response.usage) {
    inputTokens = response.usage.prompt_tokens ?? 0;
    outputTokens = response.usage.completion_tokens ?? 0;

    // Extended usage fields.
    if (response.usage.completion_tokens_details) {
      reasoningTokens = response.usage.completion_tokens_details.reasoning_tokens ?? 0;
    }
    if (response.usage.prompt_tokens_details) {
      cachedTokens = response.usage.prompt_tokens_details.cached_tokens ?? 0;
    }
  }

  if (response.id) responseId = response.id;
  if (response.model) responseModel = response.model;
  if (response.choices?.length > 0) {
    finishReason = response.choices[0].finish_reason ?? undefined;
  }

  // Set extended token details.
  if (reasoningTokens > 0 || cachedTokens > 0) {
    GenAiSpanBuilder.setTokenDetails(span, {
      reasoningTokens: reasoningTokens || undefined,
      cachedReadTokens: cachedTokens || undefined,
    });
  }

  const result: ChatResult = {
    responseModel,
    responseId: responseId ?? '',
    inputTokens,
    outputTokens,
    finishReasons: finishReason ? [finishReason] : undefined,
  };

  GenAiSpanBuilder.endSpanSuccess(span, result);
}

/**
 * Wrap a streaming response (async iterable) to accumulate token usage
 * across chunks and end the span when the stream completes.
 */
function wrapStream(stream: any, span: Span, requestModel: string): AsyncIterable<any> {
  let inputTokens = 0;
  let outputTokens = 0;
  let finishReason: string | undefined;
  let responseModel = requestModel;

  const originalIterator = stream[Symbol.asyncIterator]
    ? stream[Symbol.asyncIterator].bind(stream)
    : null;

  if (!originalIterator) {
    // Not an async iterable, end the span and return as-is.
    GenAiSpanBuilder.endSpanSuccess(span, {
      responseModel,
      responseId: '',
      inputTokens: 0,
      outputTokens: 0,
    });
    return stream;
  }

  const wrappedIterable: AsyncIterable<any> = {
    [Symbol.asyncIterator]() {
      const iterator = originalIterator();
      return {
        async next() {
          try {
            const result = await iterator.next();
            if (result.done) {
              // Stream complete: finalize span with accumulated data.
              GenAiSpanBuilder.endSpanSuccess(span, {
                responseModel,
                responseId: '',
                inputTokens,
                outputTokens,
                finishReasons: finishReason ? [finishReason] : undefined,
              });
              return result;
            }

            const chunk = result.value;

            // Accumulate data from stream chunks.
            if (chunk.model) responseModel = chunk.model;
            if (chunk.usage) {
              inputTokens = chunk.usage.prompt_tokens ?? inputTokens;
              outputTokens = chunk.usage.completion_tokens ?? outputTokens;
            }
            if (chunk.choices?.length > 0) {
              const fr = chunk.choices[0].finish_reason;
              if (fr) finishReason = fr;
            }

            return result;
          } catch (err) {
            GenAiSpanBuilder.endSpanError(
              span,
              err instanceof Error ? err : new Error(String(err)),
            );
            throw err;
          }
        },
      };
    },
  };

  // Preserve any non-iterator properties from the original stream (e.g., controller, response).
  return Object.assign(wrappedIterable, stream);
}
