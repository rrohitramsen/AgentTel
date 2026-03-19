import { trace, type Span, type Tracer, SpanKind, context as otelContext } from '@opentelemetry/api';
import * as attrs from '../attributes.js';
import { calculateCost } from './cost.js';

export interface ChatResult {
  responseModel: string;
  responseId: string;
  inputTokens: number;
  outputTokens: number;
  finishReasons?: string[];
}

export interface TokenDetails {
  reasoningTokens?: number;
  cachedReadTokens?: number;
  cachedWriteTokens?: number;
}

/** Creates properly-formed GenAI spans following OTel semantic conventions. */
export class GenAiSpanBuilder {
  private readonly tracer: Tracer;

  constructor(tracerOrName?: Tracer | string) {
    this.tracer = typeof tracerOrName === 'string'
      ? trace.getTracer(tracerOrName)
      : tracerOrName ?? trace.getTracer('agenttel.genai');
  }

  /** Create a new GenAI chat span. */
  startChatSpan(model: string, system: string): Span {
    const span = this.tracer.startSpan(`chat ${model}`, {
      kind: SpanKind.CLIENT,
      attributes: {
        [attrs.GENAI_OPERATION_NAME]: 'chat',
        [attrs.GENAI_SYSTEM]: system,
        [attrs.GENAI_REQUEST_MODEL]: model,
      },
    });
    return span;
  }

  /** Set optional request parameters on a span. */
  static setRequestParams(span: Span, temperature?: number, maxTokens?: number, topP?: number): void {
    if (temperature !== undefined) span.setAttribute(attrs.GENAI_REQUEST_TEMPERATURE, temperature);
    if (maxTokens !== undefined) span.setAttribute(attrs.GENAI_REQUEST_MAX_TOKENS, maxTokens);
    if (topP !== undefined) span.setAttribute(attrs.GENAI_REQUEST_TOP_P, topP);
  }

  /** Complete a GenAI span with response data and cost calculation. */
  static endSpanSuccess(span: Span, result: ChatResult): void {
    span.setAttribute(attrs.GENAI_RESPONSE_MODEL, result.responseModel);
    span.setAttribute(attrs.GENAI_RESPONSE_ID, result.responseId);
    span.setAttribute(attrs.GENAI_USAGE_INPUT_TOKENS, result.inputTokens);
    span.setAttribute(attrs.GENAI_USAGE_OUTPUT_TOKENS, result.outputTokens);

    if (result.finishReasons?.length) {
      span.setAttribute(attrs.GENAI_RESPONSE_FINISH_REASONS, result.finishReasons);
    }

    const cost = calculateCost(result.responseModel, result.inputTokens, result.outputTokens);
    if (cost > 0) {
      span.setAttribute(attrs.GENAI_COST_USD, cost);
    }

    span.end();
  }

  /** Complete a GenAI span with an error. */
  static endSpanError(span: Span, error: Error): void {
    span.recordException(error);
    span.end();
  }

  /** Set extended token usage details. */
  static setTokenDetails(span: Span, details: TokenDetails): void {
    if (details.reasoningTokens) span.setAttribute(attrs.GENAI_REASONING_TOKENS, details.reasoningTokens);
    if (details.cachedReadTokens) span.setAttribute(attrs.GENAI_CACHED_READ_TOKENS, details.cachedReadTokens);
    if (details.cachedWriteTokens) span.setAttribute(attrs.GENAI_CACHED_WRITE_TOKENS, details.cachedWriteTokens);
  }

  /** Set time-to-first-token metric. */
  static setTimeToFirstToken(span: Span, ttftMs: number): void {
    span.setAttribute(attrs.GENAI_TIME_TO_FIRST_TOKEN_MS, ttftMs);
  }

  /** Set the GenAI framework attribute. */
  static setFramework(span: Span, framework: string): void {
    span.setAttribute(attrs.GENAI_FRAMEWORK, framework);
  }
}
