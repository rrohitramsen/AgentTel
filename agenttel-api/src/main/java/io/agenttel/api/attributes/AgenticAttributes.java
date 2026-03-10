package io.agenttel.api.attributes;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Attribute key constants for agent observability semantic conventions.
 *
 * <p>These attributes instrument the AI agent runtime itself — lifecycle, reasoning,
 * orchestration patterns, cost, quality, and safety. Distinct from {@link AgentTelAttributes}
 * which covers the services that agents interact with.
 *
 * <p>Namespace: {@code agenttel.agentic.*}
 */
public final class AgenticAttributes {
    private AgenticAttributes() {}

    // --- Agent Identity ---
    public static final AttributeKey<String> AGENT_NAME =
            AttributeKey.stringKey("agenttel.agentic.agent.name");
    public static final AttributeKey<String> AGENT_TYPE =
            AttributeKey.stringKey("agenttel.agentic.agent.type");
    public static final AttributeKey<String> AGENT_FRAMEWORK =
            AttributeKey.stringKey("agenttel.agentic.agent.framework");
    public static final AttributeKey<String> AGENT_VERSION =
            AttributeKey.stringKey("agenttel.agentic.agent.version");

    // --- Invocation ---
    public static final AttributeKey<String> INVOCATION_ID =
            AttributeKey.stringKey("agenttel.agentic.invocation.id");
    public static final AttributeKey<String> INVOCATION_GOAL =
            AttributeKey.stringKey("agenttel.agentic.invocation.goal");
    public static final AttributeKey<String> INVOCATION_STATUS =
            AttributeKey.stringKey("agenttel.agentic.invocation.status");
    public static final AttributeKey<Long> INVOCATION_STEPS =
            AttributeKey.longKey("agenttel.agentic.invocation.steps");
    public static final AttributeKey<Long> INVOCATION_MAX_STEPS =
            AttributeKey.longKey("agenttel.agentic.invocation.max_steps");

    // --- Task Tracking ---
    public static final AttributeKey<String> TASK_ID =
            AttributeKey.stringKey("agenttel.agentic.task.id");
    public static final AttributeKey<String> TASK_NAME =
            AttributeKey.stringKey("agenttel.agentic.task.name");
    public static final AttributeKey<String> TASK_STATUS =
            AttributeKey.stringKey("agenttel.agentic.task.status");
    public static final AttributeKey<String> TASK_PARENT_ID =
            AttributeKey.stringKey("agenttel.agentic.task.parent_id");
    public static final AttributeKey<Long> TASK_DEPTH =
            AttributeKey.longKey("agenttel.agentic.task.depth");

    // --- Step / Reasoning ---
    public static final AttributeKey<Long> STEP_NUMBER =
            AttributeKey.longKey("agenttel.agentic.step.number");
    public static final AttributeKey<String> STEP_TYPE =
            AttributeKey.stringKey("agenttel.agentic.step.type");
    public static final AttributeKey<Long> STEP_ITERATION =
            AttributeKey.longKey("agenttel.agentic.step.iteration");
    public static final AttributeKey<String> STEP_TOOL_NAME =
            AttributeKey.stringKey("agenttel.agentic.step.tool_name");
    public static final AttributeKey<String> STEP_TOOL_STATUS =
            AttributeKey.stringKey("agenttel.agentic.step.tool_status");

    // --- Orchestration ---
    public static final AttributeKey<String> ORCHESTRATION_PATTERN =
            AttributeKey.stringKey("agenttel.agentic.orchestration.pattern");
    public static final AttributeKey<String> ORCHESTRATION_COORDINATOR_ID =
            AttributeKey.stringKey("agenttel.agentic.orchestration.coordinator_id");
    public static final AttributeKey<Long> ORCHESTRATION_STAGE =
            AttributeKey.longKey("agenttel.agentic.orchestration.stage");
    public static final AttributeKey<Long> ORCHESTRATION_TOTAL_STAGES =
            AttributeKey.longKey("agenttel.agentic.orchestration.total_stages");
    public static final AttributeKey<Long> ORCHESTRATION_PARALLEL_BRANCHES =
            AttributeKey.longKey("agenttel.agentic.orchestration.parallel_branches");
    public static final AttributeKey<String> ORCHESTRATION_AGGREGATION =
            AttributeKey.stringKey("agenttel.agentic.orchestration.aggregation");

    // --- Handoff / Delegation ---
    public static final AttributeKey<String> HANDOFF_FROM_AGENT =
            AttributeKey.stringKey("agenttel.agentic.handoff.from_agent");
    public static final AttributeKey<String> HANDOFF_TO_AGENT =
            AttributeKey.stringKey("agenttel.agentic.handoff.to_agent");
    public static final AttributeKey<String> HANDOFF_REASON =
            AttributeKey.stringKey("agenttel.agentic.handoff.reason");
    public static final AttributeKey<Long> HANDOFF_CHAIN_DEPTH =
            AttributeKey.longKey("agenttel.agentic.handoff.chain_depth");

    // --- Cost Aggregation ---
    public static final AttributeKey<Double> COST_TOTAL_USD =
            AttributeKey.doubleKey("agenttel.agentic.cost.total_usd");
    public static final AttributeKey<Long> COST_INPUT_TOKENS =
            AttributeKey.longKey("agenttel.agentic.cost.input_tokens");
    public static final AttributeKey<Long> COST_OUTPUT_TOKENS =
            AttributeKey.longKey("agenttel.agentic.cost.output_tokens");
    public static final AttributeKey<Long> COST_LLM_CALLS =
            AttributeKey.longKey("agenttel.agentic.cost.llm_calls");

    // --- Quality Signals ---
    public static final AttributeKey<Boolean> QUALITY_GOAL_ACHIEVED =
            AttributeKey.booleanKey("agenttel.agentic.quality.goal_achieved");
    public static final AttributeKey<Long> QUALITY_HUMAN_INTERVENTIONS =
            AttributeKey.longKey("agenttel.agentic.quality.human_interventions");
    public static final AttributeKey<Boolean> QUALITY_LOOP_DETECTED =
            AttributeKey.booleanKey("agenttel.agentic.quality.loop_detected");
    public static final AttributeKey<Long> QUALITY_LOOP_ITERATIONS =
            AttributeKey.longKey("agenttel.agentic.quality.loop_iterations");
    public static final AttributeKey<Double> QUALITY_EVAL_SCORE =
            AttributeKey.doubleKey("agenttel.agentic.quality.eval_score");

    // --- Guardrail / Safety ---
    public static final AttributeKey<Boolean> GUARDRAIL_TRIGGERED =
            AttributeKey.booleanKey("agenttel.agentic.guardrail.triggered");
    public static final AttributeKey<String> GUARDRAIL_NAME =
            AttributeKey.stringKey("agenttel.agentic.guardrail.name");
    public static final AttributeKey<String> GUARDRAIL_ACTION =
            AttributeKey.stringKey("agenttel.agentic.guardrail.action");
    public static final AttributeKey<String> GUARDRAIL_REASON =
            AttributeKey.stringKey("agenttel.agentic.guardrail.reason");

    // --- Memory Access ---
    public static final AttributeKey<String> MEMORY_OPERATION =
            AttributeKey.stringKey("agenttel.agentic.memory.operation");
    public static final AttributeKey<String> MEMORY_STORE_TYPE =
            AttributeKey.stringKey("agenttel.agentic.memory.store_type");
    public static final AttributeKey<Long> MEMORY_ITEMS =
            AttributeKey.longKey("agenttel.agentic.memory.items");

    // --- Human-in-the-Loop ---
    public static final AttributeKey<String> HUMAN_CHECKPOINT_TYPE =
            AttributeKey.stringKey("agenttel.agentic.human.checkpoint_type");
    public static final AttributeKey<Long> HUMAN_WAIT_MS =
            AttributeKey.longKey("agenttel.agentic.human.wait_ms");
    public static final AttributeKey<String> HUMAN_DECISION =
            AttributeKey.stringKey("agenttel.agentic.human.decision");

    // --- Code Execution ---
    public static final AttributeKey<String> CODE_LANGUAGE =
            AttributeKey.stringKey("agenttel.agentic.code.language");
    public static final AttributeKey<String> CODE_STATUS =
            AttributeKey.stringKey("agenttel.agentic.code.status");
    public static final AttributeKey<Long> CODE_EXIT_CODE =
            AttributeKey.longKey("agenttel.agentic.code.exit_code");
    public static final AttributeKey<Boolean> CODE_SANDBOXED =
            AttributeKey.booleanKey("agenttel.agentic.code.sandboxed");

    // --- Evaluation / Scoring ---
    public static final AttributeKey<String> EVAL_SCORER_NAME =
            AttributeKey.stringKey("agenttel.agentic.eval.scorer_name");
    public static final AttributeKey<String> EVAL_CRITERIA =
            AttributeKey.stringKey("agenttel.agentic.eval.criteria");
    public static final AttributeKey<Double> EVAL_SCORE =
            AttributeKey.doubleKey("agenttel.agentic.eval.score");
    public static final AttributeKey<String> EVAL_FEEDBACK =
            AttributeKey.stringKey("agenttel.agentic.eval.feedback");
    public static final AttributeKey<String> EVAL_TYPE =
            AttributeKey.stringKey("agenttel.agentic.eval.type");

    // --- Token Details (aggregated at invocation/session) ---
    public static final AttributeKey<Long> COST_REASONING_TOKENS =
            AttributeKey.longKey("agenttel.agentic.cost.reasoning_tokens");
    public static final AttributeKey<Long> COST_CACHED_READ_TOKENS =
            AttributeKey.longKey("agenttel.agentic.cost.cached_read_tokens");
    public static final AttributeKey<Long> COST_CACHED_WRITE_TOKENS =
            AttributeKey.longKey("agenttel.agentic.cost.cached_write_tokens");

    // --- Error Classification ---
    public static final AttributeKey<String> ERROR_SOURCE =
            AttributeKey.stringKey("agenttel.agentic.error.source");
    public static final AttributeKey<String> ERROR_CATEGORY =
            AttributeKey.stringKey("agenttel.agentic.error.category");
    public static final AttributeKey<Boolean> ERROR_RETRYABLE =
            AttributeKey.booleanKey("agenttel.agentic.error.retryable");

    // --- Agent Capabilities ---
    @SuppressWarnings("unchecked")
    public static final AttributeKey<java.util.List<String>> CAPABILITY_TOOLS =
            AttributeKey.stringArrayKey("agenttel.agentic.capability.tools");
    public static final AttributeKey<Long> CAPABILITY_TOOL_COUNT =
            AttributeKey.longKey("agenttel.agentic.capability.tool_count");
    public static final AttributeKey<String> CAPABILITY_SYSTEM_PROMPT_HASH =
            AttributeKey.stringKey("agenttel.agentic.capability.system_prompt_hash");

    // --- Conversation / Message Tracking ---
    public static final AttributeKey<String> CONVERSATION_ID =
            AttributeKey.stringKey("agenttel.agentic.conversation.id");
    public static final AttributeKey<Long> CONVERSATION_TURN =
            AttributeKey.longKey("agenttel.agentic.conversation.turn");
    public static final AttributeKey<Long> CONVERSATION_MESSAGE_COUNT =
            AttributeKey.longKey("agenttel.agentic.conversation.message_count");
    public static final AttributeKey<String> CONVERSATION_SPEAKER_ROLE =
            AttributeKey.stringKey("agenttel.agentic.conversation.speaker_role");

    // --- Retrieval ---
    public static final AttributeKey<String> RETRIEVAL_QUERY =
            AttributeKey.stringKey("agenttel.agentic.retrieval.query");
    public static final AttributeKey<String> RETRIEVAL_STORE_TYPE =
            AttributeKey.stringKey("agenttel.agentic.retrieval.store_type");
    public static final AttributeKey<Long> RETRIEVAL_DOCUMENT_COUNT =
            AttributeKey.longKey("agenttel.agentic.retrieval.document_count");
    public static final AttributeKey<Long> RETRIEVAL_TOP_K =
            AttributeKey.longKey("agenttel.agentic.retrieval.top_k");
    public static final AttributeKey<Double> RETRIEVAL_RELEVANCE_SCORE_AVG =
            AttributeKey.doubleKey("agenttel.agentic.retrieval.relevance_score_avg");
    public static final AttributeKey<Double> RETRIEVAL_RELEVANCE_SCORE_MIN =
            AttributeKey.doubleKey("agenttel.agentic.retrieval.relevance_score_min");

    // --- Reranker ---
    public static final AttributeKey<String> RERANKER_MODEL =
            AttributeKey.stringKey("agenttel.agentic.reranker.model");
    public static final AttributeKey<Long> RERANKER_INPUT_DOCUMENTS =
            AttributeKey.longKey("agenttel.agentic.reranker.input_documents");
    public static final AttributeKey<Long> RERANKER_OUTPUT_DOCUMENTS =
            AttributeKey.longKey("agenttel.agentic.reranker.output_documents");
    public static final AttributeKey<Double> RERANKER_TOP_SCORE =
            AttributeKey.doubleKey("agenttel.agentic.reranker.top_score");
}
