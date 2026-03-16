"""Agentic observability semantic attributes (70+ attributes)."""

# Agent Identity
AGENT_NAME = "agenttel.agent.name"
AGENT_TYPE = "agenttel.agent.type"
AGENT_FRAMEWORK = "agenttel.agent.framework"
AGENT_VERSION = "agenttel.agent.version"

# Invocation
INVOCATION_ID = "agenttel.invocation.id"
INVOCATION_GOAL = "agenttel.invocation.goal"
INVOCATION_STATUS = "agenttel.invocation.status"
INVOCATION_STEPS = "agenttel.invocation.steps"
INVOCATION_DURATION_MS = "agenttel.invocation.duration_ms"
INVOCATION_ERROR_SOURCE = "agenttel.invocation.error_source"
INVOCATION_ERROR_MESSAGE = "agenttel.invocation.error_message"

# Task
TASK_ID = "agenttel.task.id"
TASK_NAME = "agenttel.task.name"
TASK_STATUS = "agenttel.task.status"
TASK_PARENT_ID = "agenttel.task.parent_id"
TASK_DEPTH = "agenttel.task.depth"
TASK_PRIORITY = "agenttel.task.priority"

# Step
STEP_NUMBER = "agenttel.step.number"
STEP_TYPE = "agenttel.step.type"
STEP_ITERATION = "agenttel.step.iteration"
STEP_DESCRIPTION = "agenttel.step.description"
STEP_TOOL_NAME = "agenttel.step.tool_name"
STEP_TOOL_STATUS = "agenttel.step.tool_status"
STEP_TOOL_INPUT = "agenttel.step.tool_input"
STEP_TOOL_OUTPUT = "agenttel.step.tool_output"
STEP_TOOL_DURATION_MS = "agenttel.step.tool_duration_ms"

# Orchestration
ORCHESTRATION_PATTERN = "agenttel.orchestration.pattern"
ORCHESTRATION_COORDINATOR_ID = "agenttel.orchestration.coordinator_id"
ORCHESTRATION_STAGE = "agenttel.orchestration.stage"
ORCHESTRATION_STAGE_COUNT = "agenttel.orchestration.stage_count"
ORCHESTRATION_PARALLEL_BRANCHES = "agenttel.orchestration.parallel_branches"

# Handoff
HANDOFF_FROM_AGENT = "agenttel.handoff.from_agent"
HANDOFF_TO_AGENT = "agenttel.handoff.to_agent"
HANDOFF_REASON = "agenttel.handoff.reason"
HANDOFF_CHAIN_DEPTH = "agenttel.handoff.chain_depth"
HANDOFF_CONTEXT_KEYS = "agenttel.handoff.context_keys"

# Cost
COST_TOTAL_USD = "agenttel.cost.total_usd"
COST_INPUT_TOKENS = "agenttel.cost.input_tokens"
COST_OUTPUT_TOKENS = "agenttel.cost.output_tokens"
COST_REASONING_TOKENS = "agenttel.cost.reasoning_tokens"
COST_LLM_CALLS = "agenttel.cost.llm_calls"

# Quality
QUALITY_GOAL_ACHIEVED = "agenttel.quality.goal_achieved"
QUALITY_HUMAN_INTERVENTIONS = "agenttel.quality.human_interventions"
QUALITY_LOOP_DETECTED = "agenttel.quality.loop_detected"
QUALITY_CONFIDENCE = "agenttel.quality.confidence"
QUALITY_ITERATIONS = "agenttel.quality.iterations"

# Guardrail
GUARDRAIL_TRIGGERED = "agenttel.guardrail.triggered"
GUARDRAIL_NAME = "agenttel.guardrail.name"
GUARDRAIL_ACTION = "agenttel.guardrail.action"
GUARDRAIL_REASON = "agenttel.guardrail.reason"
GUARDRAIL_INPUT_VIOLATION = "agenttel.guardrail.input_violation"
GUARDRAIL_OUTPUT_VIOLATION = "agenttel.guardrail.output_violation"

# Memory
MEMORY_OPERATION = "agenttel.memory.operation"
MEMORY_STORE_TYPE = "agenttel.memory.store_type"
MEMORY_ITEMS = "agenttel.memory.items"
MEMORY_QUERY = "agenttel.memory.query"
MEMORY_HIT = "agenttel.memory.hit"

# Human-in-the-Loop
HUMAN_CHECKPOINT_TYPE = "agenttel.human.checkpoint_type"
HUMAN_WAIT_MS = "agenttel.human.wait_ms"
HUMAN_DECISION = "agenttel.human.decision"
HUMAN_FEEDBACK = "agenttel.human.feedback"

# Code Execution
CODE_LANGUAGE = "agenttel.code.language"
CODE_STATUS = "agenttel.code.status"
CODE_EXIT_CODE = "agenttel.code.exit_code"
CODE_SANDBOXED = "agenttel.code.sandboxed"
CODE_DURATION_MS = "agenttel.code.duration_ms"
CODE_OUTPUT_SIZE = "agenttel.code.output_size"

# Evaluation
EVAL_SCORER_NAME = "agenttel.eval.scorer_name"
EVAL_CRITERIA = "agenttel.eval.criteria"
EVAL_SCORE = "agenttel.eval.score"
EVAL_FEEDBACK = "agenttel.eval.feedback"
EVAL_TYPE = "agenttel.eval.type"
EVAL_MAX_SCORE = "agenttel.eval.max_score"

# Retrieval
RETRIEVAL_QUERY = "agenttel.retrieval.query"
RETRIEVAL_STORE_TYPE = "agenttel.retrieval.store_type"
RETRIEVAL_DOCUMENT_COUNT = "agenttel.retrieval.document_count"
RETRIEVAL_RELEVANCE_SCORE = "agenttel.retrieval.relevance_score"

# Conversation
CONVERSATION_ID = "agenttel.conversation.id"
CONVERSATION_TURN = "agenttel.conversation.turn"
CONVERSATION_MESSAGE_COUNT = "agenttel.conversation.message_count"
CONVERSATION_ROLE = "agenttel.conversation.role"
