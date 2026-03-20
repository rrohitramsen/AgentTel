/**
 * Anthropic SDK instrumentation wrapper.
 *
 * Monkey-patches `client.messages.create()` to automatically create
 * OpenTelemetry spans with GenAI semantic attributes for every message request.
 *
 * Usage:
 *   import Anthropic from '@anthropic-ai/sdk';
 *   import { instrumentAnthropic } from '@agenttel/node';
 *
 *   const client = instrumentAnthropic(new Anthropic());
 *   const msg = await client.messages.create({ model: 'claude-sonnet-4-20250514', ... });
 */

import { type Span } from '@opentelemetry/api';
import { GenAiSpanBuilder, type ChatResult } from './span-builder.js';

const SYSTEM = 'anthropic';

/**
 * Instrument an Anthropic client for automatic tracing.
 *
 * Wraps `messages.create()` to create gen_ai chat spans with full attribute
 * instrumentation including token usage, cost, cache metrics, and streaming.
 *
 * @param client - An Anthropic client instance.
 * @returns The instrumented client (same reference, mutated in place).
 */
export function instrumentAnthropic<T extends Record<string, any>>(client: T): T {
  if (!client) return client;

  const messages = client.messages;
  if (!messages?.create) return client;

  const spanBuilder = new GenAiSpanBuilder();
  const originalCreate = messages.create.bind(messages);

  messages.create = async function tracedCreate(...args: any[]) {
    const params = args[0] ?? {};
    const model: string = params.model ?? 'unknown';

    const span = spanBuilder.startChatSpan(model, SYSTEM);

    // Set optional request parameters.
    GenAiSpanBuilder.setRequestParams(
      span,
      params.temperature ?? undefined,
      params.max_tokens ?? undefined,
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
 * Extract Anthropic response data and end the span.
 */
function finalizeSpan(span: Span, response: any, requestModel: string): void {
  let inputTokens = 0;
  let outputTokens = 0;
  let cacheReadTokens = 0;
  let cacheCreateTokens = 0;
  let finishReason: string | undefined;
  let responseId: string | undefined;
  let responseModel = requestModel;

  if (response.usage) {
    inputTokens = response.usage.input_tokens ?? 0;
    outputTokens = response.usage.output_tokens ?? 0;
    cacheReadTokens = response.usage.cache_read_input_tokens ?? 0;
    cacheCreateTokens = response.usage.cache_creation_input_tokens ?? 0;
  }

  if (response.id) responseId = response.id;
  if (response.model) responseModel = response.model;
  if (response.stop_reason) finishReason = response.stop_reason;

  // Set cache token details.
  if (cacheReadTokens > 0 || cacheCreateTokens > 0) {
    GenAiSpanBuilder.setTokenDetails(span, {
      cachedReadTokens: cacheReadTokens || undefined,
      cachedWriteTokens: cacheCreateTokens || undefined,
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
 * Wrap an Anthropic streaming response to accumulate token usage from
 * SSE events (message_start, content_block_delta, message_delta) and
 * end the span when the stream completes.
 */
function wrapStream(stream: any, span: Span, requestModel: string): AsyncIterable<any> {
  let inputTokens = 0;
  let outputTokens = 0;
  let cacheReadTokens = 0;
  let cacheCreateTokens = 0;
  let finishReason: string | undefined;
  let responseModel = requestModel;
  let responseId = '';

  const originalIterator = stream[Symbol.asyncIterator]
    ? stream[Symbol.asyncIterator].bind(stream)
    : null;

  if (!originalIterator) {
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
              if (cacheReadTokens > 0 || cacheCreateTokens > 0) {
                GenAiSpanBuilder.setTokenDetails(span, {
                  cachedReadTokens: cacheReadTokens || undefined,
                  cachedWriteTokens: cacheCreateTokens || undefined,
                });
              }

              GenAiSpanBuilder.endSpanSuccess(span, {
                responseModel,
                responseId,
                inputTokens,
                outputTokens,
                finishReasons: finishReason ? [finishReason] : undefined,
              });
              return result;
            }

            const event = result.value;
            accumulateEvent(event);
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

  function accumulateEvent(event: any): void {
    const eventType = event.type ?? '';

    switch (eventType) {
      case 'message_start': {
        const msg = event.message;
        if (msg) {
          if (msg.model) responseModel = msg.model;
          if (msg.id) responseId = msg.id;
          if (msg.usage) {
            inputTokens = msg.usage.input_tokens ?? 0;
            cacheReadTokens = msg.usage.cache_read_input_tokens ?? 0;
            cacheCreateTokens = msg.usage.cache_creation_input_tokens ?? 0;
          }
        }
        break;
      }

      case 'message_delta': {
        if (event.usage) {
          outputTokens = event.usage.output_tokens ?? outputTokens;
        }
        if (event.delta?.stop_reason) {
          finishReason = event.delta.stop_reason;
        }
        break;
      }

      // content_block_start, content_block_delta, content_block_stop, message_stop
      // are passed through without accumulation.
    }
  }

  // Preserve any non-iterator properties from the original stream.
  return Object.assign(wrappedIterable, stream);
}
