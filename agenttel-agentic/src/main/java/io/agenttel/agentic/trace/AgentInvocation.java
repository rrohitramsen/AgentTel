package io.agenttel.agentic.trace;

import io.agenttel.agentic.ErrorSource;
import io.agenttel.agentic.EvalType;
import io.agenttel.agentic.GuardrailAction;
import io.agenttel.agentic.HumanCheckpointType;
import io.agenttel.agentic.InvocationStatus;
import io.agenttel.agentic.StepType;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AutoCloseable scope wrapping an agent invocation span ({@code invoke_agent}).
 *
 * <p>Manages the invocation lifecycle including step tracking, tool calls,
 * task decomposition, handoffs, guardrails, and human checkpoints.
 */
public class AgentInvocation implements AutoCloseable {

    private final Span span;
    private final Scope scope;
    private final Tracer tracer;
    private final String agentName;
    private final AtomicLong stepCounter = new AtomicLong(0);
    private final AtomicLong humanInterventions = new AtomicLong(0);

    public AgentInvocation(Span span, Context parentContext, Tracer tracer, String agentName) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
        this.tracer = tracer;
        this.agentName = agentName;
    }

    /**
     * Returns the underlying OTel span.
     */
    public Span span() {
        return span;
    }

    /**
     * Returns the agent name for this invocation.
     */
    public String agentName() {
        return agentName;
    }

    // --- Step tracking ---

    /**
     * Records a simple step (fire-and-forget, no scope returned).
     */
    public void step(StepType type, String description) {
        long stepNum = stepCounter.incrementAndGet();
        Span stepSpan = tracer.spanBuilder("agenttel.agentic.step")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.STEP_NUMBER, stepNum)
                .setAttribute(AgenticAttributes.STEP_TYPE, type.getValue())
                .startSpan();
        stepSpan.addEvent(description);
        stepSpan.end();
    }

    /**
     * Begins a scoped step that the caller must close.
     */
    public StepScope beginStep(StepType type) {
        long stepNum = stepCounter.incrementAndGet();
        Span stepSpan = tracer.spanBuilder("agenttel.agentic.step")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.STEP_NUMBER, stepNum)
                .setAttribute(AgenticAttributes.STEP_TYPE, type.getValue())
                .startSpan();
        return new StepScope(stepSpan, Context.current().with(span));
    }

    /**
     * Begins a scoped step with an iteration number (for loops).
     */
    public StepScope beginStep(StepType type, long iteration) {
        long stepNum = stepCounter.incrementAndGet();
        Span stepSpan = tracer.spanBuilder("agenttel.agentic.step")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.STEP_NUMBER, stepNum)
                .setAttribute(AgenticAttributes.STEP_TYPE, type.getValue())
                .setAttribute(AgenticAttributes.STEP_ITERATION, iteration)
                .startSpan();
        return new StepScope(stepSpan, Context.current().with(span));
    }

    // --- Tool calls ---

    /**
     * Begins a scoped tool call span.
     */
    public ToolCallScope toolCall(String toolName) {
        stepCounter.incrementAndGet();
        Span toolSpan = tracer.spanBuilder("agenttel.agentic.tool_call")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.STEP_TOOL_NAME, toolName)
                .startSpan();
        return new ToolCallScope(toolSpan, Context.current().with(span));
    }

    // --- Task decomposition ---

    /**
     * Begins a scoped task span for task decomposition tracking.
     */
    public TaskScope task(String name) {
        String taskId = UUID.randomUUID().toString();
        Span taskSpan = tracer.spanBuilder("agenttel.agentic.task")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.TASK_ID, taskId)
                .setAttribute(AgenticAttributes.TASK_NAME, name)
                .setAttribute(AgenticAttributes.TASK_STATUS, "in_progress")
                .setAttribute(AgenticAttributes.TASK_DEPTH, 0L)
                .startSpan();
        return new TaskScope(taskSpan, Context.current().with(span), tracer, taskId, 0);
    }

    // --- Handoffs ---

    /**
     * Begins a scoped handoff span when this agent delegates to another.
     */
    public HandoffScope handoff(String targetAgent, String reason) {
        Span handoffSpan = tracer.spanBuilder("agenttel.agentic.handoff")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.HANDOFF_FROM_AGENT, agentName)
                .setAttribute(AgenticAttributes.HANDOFF_TO_AGENT, targetAgent)
                .setAttribute(AgenticAttributes.HANDOFF_REASON, reason)
                .startSpan();
        return new HandoffScope(handoffSpan, Context.current().with(span));
    }

    /**
     * Begins a handoff span with chain depth tracking.
     */
    public HandoffScope handoff(String targetAgent, String reason, long chainDepth) {
        Span handoffSpan = tracer.spanBuilder("agenttel.agentic.handoff")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.HANDOFF_FROM_AGENT, agentName)
                .setAttribute(AgenticAttributes.HANDOFF_TO_AGENT, targetAgent)
                .setAttribute(AgenticAttributes.HANDOFF_REASON, reason)
                .setAttribute(AgenticAttributes.HANDOFF_CHAIN_DEPTH, chainDepth)
                .startSpan();
        return new HandoffScope(handoffSpan, Context.current().with(span));
    }

    // --- Human-in-the-Loop ---

    /**
     * Begins a human checkpoint span (approval, feedback, correction, decision).
     */
    public HumanCheckpointScope humanCheckpoint(HumanCheckpointType type, String description) {
        humanInterventions.incrementAndGet();
        Span checkpointSpan = tracer.spanBuilder("agenttel.agentic.human_input")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.HUMAN_CHECKPOINT_TYPE, type.getValue())
                .startSpan();
        checkpointSpan.addEvent(description);
        return new HumanCheckpointScope(checkpointSpan, Context.current().with(span));
    }

    /**
     * Convenience: begins a human checkpoint from a string type.
     */
    public HumanCheckpointScope humanCheckpoint(String type, String description) {
        return humanCheckpoint(HumanCheckpointType.fromValue(type), description);
    }

    // --- Code Execution ---

    /**
     * Begins a scoped code execution span.
     */
    public CodeExecutionScope codeExecution(String language) {
        return codeExecution(language, false);
    }

    /**
     * Begins a scoped code execution span with sandbox tracking.
     */
    public CodeExecutionScope codeExecution(String language, boolean sandboxed) {
        stepCounter.incrementAndGet();
        Span codeSpan = tracer.spanBuilder("agenttel.agentic.code_execution")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.CODE_LANGUAGE, language)
                .setAttribute(AgenticAttributes.CODE_SANDBOXED, sandboxed)
                .startSpan();
        return new CodeExecutionScope(codeSpan, Context.current().with(span));
    }

    // --- Evaluation / Scoring ---

    /**
     * Begins a first-class evaluation span with a scorer name and criteria.
     */
    public EvaluationScope evaluate(String scorerName, String criteria) {
        return evaluate(scorerName, criteria, EvalType.CUSTOM);
    }

    /**
     * Begins a first-class evaluation span with scorer name, criteria, and type.
     */
    public EvaluationScope evaluate(String scorerName, String criteria, EvalType evalType) {
        Span evalSpan = tracer.spanBuilder("agenttel.agentic.evaluate")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.EVAL_SCORER_NAME, scorerName)
                .setAttribute(AgenticAttributes.EVAL_CRITERIA, criteria)
                .setAttribute(AgenticAttributes.EVAL_TYPE, evalType.getValue())
                .startSpan();
        return new EvaluationScope(evalSpan, Context.current().with(span));
    }

    // --- Retrieval (RAG) ---

    /**
     * Begins a retriever span for RAG document retrieval.
     */
    public RetrieverScope retrieve(String query, String storeType, long topK) {
        stepCounter.incrementAndGet();
        Span retSpan = tracer.spanBuilder("agenttel.agentic.retriever")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.RETRIEVAL_QUERY, query)
                .setAttribute(AgenticAttributes.RETRIEVAL_STORE_TYPE, storeType)
                .setAttribute(AgenticAttributes.RETRIEVAL_TOP_K, topK)
                .startSpan();
        return new RetrieverScope(retSpan, Context.current().with(span));
    }

    /**
     * Begins a reranker span for document reranking.
     */
    public RerankerScope rerank(String model, long inputDocuments) {
        Span rerankSpan = tracer.spanBuilder("agenttel.agentic.reranker")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.RERANKER_MODEL, model)
                .setAttribute(AgenticAttributes.RERANKER_INPUT_DOCUMENTS, inputDocuments)
                .startSpan();
        return new RerankerScope(rerankSpan, Context.current().with(span));
    }

    // --- Error Classification ---

    /**
     * Records a classified error on this invocation.
     */
    public void classifyError(ErrorSource source, String category, boolean retryable) {
        span.setAttribute(AgenticAttributes.ERROR_SOURCE, source.getValue());
        span.setAttribute(AgenticAttributes.ERROR_CATEGORY, category);
        span.setAttribute(AgenticAttributes.ERROR_RETRYABLE, retryable);
    }

    /**
     * Records a classified error from a string source.
     */
    public void classifyError(String source, String category, boolean retryable) {
        classifyError(ErrorSource.fromValue(source), category, retryable);
    }

    // --- Agent Capabilities ---

    /**
     * Sets the tools available to this agent.
     */
    public AgentInvocation tools(List<String> toolNames) {
        span.setAttribute(AgenticAttributes.CAPABILITY_TOOLS, toolNames);
        span.setAttribute(AgenticAttributes.CAPABILITY_TOOL_COUNT, (long) toolNames.size());
        return this;
    }

    /**
     * Sets a hash of the system prompt for version tracking.
     */
    public AgentInvocation systemPromptHash(String hash) {
        span.setAttribute(AgenticAttributes.CAPABILITY_SYSTEM_PROMPT_HASH, hash);
        return this;
    }

    // --- Conversation Tracking ---

    /**
     * Sets conversation context on this invocation.
     */
    public AgentInvocation conversation(String conversationId, long turn) {
        span.setAttribute(AgenticAttributes.CONVERSATION_ID, conversationId);
        span.setAttribute(AgenticAttributes.CONVERSATION_TURN, turn);
        return this;
    }

    /**
     * Sets the message count for this conversation turn.
     */
    public AgentInvocation messageCount(long count) {
        span.setAttribute(AgenticAttributes.CONVERSATION_MESSAGE_COUNT, count);
        return this;
    }

    // --- Guardrails ---

    /**
     * Records a guardrail activation event on this invocation span.
     */
    public void guardrail(String name, GuardrailAction action, String reason) {
        Span guardrailSpan = tracer.spanBuilder("agenttel.agentic.guardrail")
                .setParent(Context.current().with(span))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.GUARDRAIL_TRIGGERED, true)
                .setAttribute(AgenticAttributes.GUARDRAIL_NAME, name)
                .setAttribute(AgenticAttributes.GUARDRAIL_ACTION, action.getValue())
                .setAttribute(AgenticAttributes.GUARDRAIL_REASON, reason)
                .startSpan();
        guardrailSpan.end();
    }

    /**
     * Convenience: records a guardrail from a string action.
     */
    public void guardrail(String name, String action, String reason) {
        guardrail(name, GuardrailAction.fromValue(action), reason);
    }

    // --- Completion ---

    /**
     * Marks this invocation as completed with a goal achievement flag.
     */
    public void complete(boolean goalAchieved) {
        span.setAttribute(AgenticAttributes.INVOCATION_STATUS,
                goalAchieved ? InvocationStatus.SUCCESS.getValue() : InvocationStatus.FAILURE.getValue());
        span.setAttribute(AgenticAttributes.INVOCATION_STEPS, stepCounter.get());
        span.setAttribute(AgenticAttributes.QUALITY_GOAL_ACHIEVED, goalAchieved);
        span.setAttribute(AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS, humanInterventions.get());
        if (goalAchieved) {
            span.setStatus(StatusCode.OK);
        } else {
            span.setStatus(StatusCode.ERROR, "Agent did not achieve goal");
        }
    }

    /**
     * Marks this invocation with a specific terminal status.
     */
    public void complete(InvocationStatus status) {
        span.setAttribute(AgenticAttributes.INVOCATION_STATUS, status.getValue());
        span.setAttribute(AgenticAttributes.INVOCATION_STEPS, stepCounter.get());
        span.setAttribute(AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS, humanInterventions.get());
        if (status == InvocationStatus.SUCCESS) {
            span.setAttribute(AgenticAttributes.QUALITY_GOAL_ACHIEVED, true);
            span.setStatus(StatusCode.OK);
        } else {
            span.setStatus(StatusCode.ERROR, status.getValue());
        }
    }

    /**
     * Sets the maximum steps guardrail for this invocation.
     */
    public AgentInvocation maxSteps(long maxSteps) {
        span.setAttribute(AgenticAttributes.INVOCATION_MAX_STEPS, maxSteps);
        return this;
    }

    /**
     * Returns the current step count.
     */
    public long stepCount() {
        return stepCounter.get();
    }

    @Override
    public void close() {
        // Set final step count if not already set via complete()
        span.setAttribute(AgenticAttributes.INVOCATION_STEPS, stepCounter.get());
        scope.close();
        span.end();
    }
}
