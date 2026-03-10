package io.agenttel.agentic.cost;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * SpanProcessor that aggregates LLM costs from child spans up to parent
 * agent invocation and session spans.
 *
 * <p>Tracks {@code agenttel.genai.cost_usd} from GenAI spans and rolls up
 * totals into {@code agenttel.agentic.cost.*} attributes on parent spans.
 */
public class AgentCostAggregator implements SpanProcessor {

    private static final AttributeKey<Double> GENAI_COST_USD =
            AttributeKey.doubleKey("agenttel.genai.cost_usd");

    private final Map<String, CostAccumulator> accumulators = new ConcurrentHashMap<>();

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // No-op on start
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        String spanName = span.getName();

        // When a GenAI span ends, aggregate its cost to the parent
        Double cost = span.getAttribute(GENAI_COST_USD);
        if (cost != null && cost > 0) {
            String parentSpanId = span.getParentSpanContext().getSpanId();
            if (parentSpanId != null && !parentSpanId.equals("0000000000000000")) {
                String traceId = span.getSpanContext().getTraceId();
                accumulators.computeIfAbsent(traceId, k -> new CostAccumulator())
                        .add(cost, getInputTokens(span), getOutputTokens(span),
                             getReasoningTokens(span), getCachedReadTokens(span),
                             getCachedWriteTokens(span));
            }
        }

        // When an invocation or session span ends, apply accumulated costs
        if ("invoke_agent".equals(spanName) || "agenttel.agentic.session".equals(spanName)) {
            String traceId = span.getSpanContext().getTraceId();
            CostAccumulator acc = accumulators.remove(traceId);
            if (acc != null && span instanceof ReadWriteSpan rwSpan) {
                rwSpan.setAttribute(AgenticAttributes.COST_TOTAL_USD, acc.totalCost());
                rwSpan.setAttribute(AgenticAttributes.COST_INPUT_TOKENS, acc.inputTokens());
                rwSpan.setAttribute(AgenticAttributes.COST_OUTPUT_TOKENS, acc.outputTokens());
                rwSpan.setAttribute(AgenticAttributes.COST_LLM_CALLS, acc.llmCalls());
                rwSpan.setAttribute(AgenticAttributes.COST_REASONING_TOKENS, acc.reasoningTokens());
                rwSpan.setAttribute(AgenticAttributes.COST_CACHED_READ_TOKENS, acc.cachedReadTokens());
                rwSpan.setAttribute(AgenticAttributes.COST_CACHED_WRITE_TOKENS, acc.cachedWriteTokens());
            }
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    private long getInputTokens(ReadableSpan span) {
        Long tokens = span.getAttribute(AttributeKey.longKey("gen_ai.usage.input_tokens"));
        return tokens != null ? tokens : 0L;
    }

    private long getOutputTokens(ReadableSpan span) {
        Long tokens = span.getAttribute(AttributeKey.longKey("gen_ai.usage.output_tokens"));
        return tokens != null ? tokens : 0L;
    }

    private long getReasoningTokens(ReadableSpan span) {
        Long tokens = span.getAttribute(AttributeKey.longKey("agenttel.genai.reasoning_tokens"));
        return tokens != null ? tokens : 0L;
    }

    private long getCachedReadTokens(ReadableSpan span) {
        Long tokens = span.getAttribute(AttributeKey.longKey("agenttel.genai.cached_read_tokens"));
        return tokens != null ? tokens : 0L;
    }

    private long getCachedWriteTokens(ReadableSpan span) {
        Long tokens = span.getAttribute(AttributeKey.longKey("agenttel.genai.cached_write_tokens"));
        return tokens != null ? tokens : 0L;
    }

    /**
     * Thread-safe cost accumulator for a single trace.
     */
    static class CostAccumulator {
        private final DoubleAdder cost = new DoubleAdder();
        private final LongAdder inputTok = new LongAdder();
        private final LongAdder outputTok = new LongAdder();
        private final LongAdder calls = new LongAdder();
        private final LongAdder reasoningTok = new LongAdder();
        private final LongAdder cachedReadTok = new LongAdder();
        private final LongAdder cachedWriteTok = new LongAdder();

        void add(double costUsd, long inputTokens, long outputTokens,
                 long reasoningTokens, long cachedReadTokens, long cachedWriteTokens) {
            cost.add(costUsd);
            inputTok.add(inputTokens);
            outputTok.add(outputTokens);
            calls.increment();
            reasoningTok.add(reasoningTokens);
            cachedReadTok.add(cachedReadTokens);
            cachedWriteTok.add(cachedWriteTokens);
        }

        double totalCost() { return cost.sum(); }
        long inputTokens() { return inputTok.sum(); }
        long outputTokens() { return outputTok.sum(); }
        long llmCalls() { return calls.sum(); }
        long reasoningTokens() { return reasoningTok.sum(); }
        long cachedReadTokens() { return cachedReadTok.sum(); }
        long cachedWriteTokens() { return cachedWriteTok.sum(); }
    }
}
