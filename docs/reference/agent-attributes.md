# Agent Attributes Reference

Complete reference for all `agenttel.agentic.*` attributes used to instrument AI agent runtimes. These attributes capture agent lifecycle, reasoning traces, orchestration patterns, cost aggregation, quality signals, and safety guardrails. They are distinct from the core `agenttel.*` attributes (see [attribute-dictionary.md](attribute-dictionary.md)) which cover the backend services that agents interact with.

All attributes live in the `agenttel.agentic.*` namespace and are defined as `AttributeKey` constants in `io.agenttel.api.attributes.AgenticAttributes`.

> **Quick navigation:** [Agent Identity](#agent-identity) | [Invocation](#invocation) | [Task Tracking](#task-tracking) | [Step / Reasoning](#step--reasoning) | [Orchestration](#orchestration) | [Handoff / Delegation](#handoff--delegation) | [Cost Aggregation](#cost-aggregation) | [Quality Signals](#quality-signals) | [Guardrail / Safety](#guardrail--safety) | [Memory Access](#memory-access) | [Human-in-the-Loop](#human-in-the-loop) | [Code Execution](#code-execution) | [Evaluation / Scoring](#evaluation--scoring) | [Error Classification](#error-classification) | [Agent Capabilities](#agent-capabilities) | [Conversation / Message Tracking](#conversation--message-tracking) | [Retrieval (RAG)](#retrieval-rag) | [Reranker](#reranker) | [Span Name Reference](#span-name-reference) | [Java Constant Reference](#java-constant-reference)

---

## Alphabetical Index

All `agenttel.agentic.*` attributes sorted alphabetically. Click any key to jump to its category.

| Attribute Key | Type | Category |
|---------------|------|----------|
| `agenttel.agentic.agent.framework` | String | [Agent Identity](#agent-identity) |
| `agenttel.agentic.agent.name` | String | [Agent Identity](#agent-identity) |
| `agenttel.agentic.agent.type` | String | [Agent Identity](#agent-identity) |
| `agenttel.agentic.agent.version` | String | [Agent Identity](#agent-identity) |
| `agenttel.agentic.capability.system_prompt_hash` | String | [Agent Capabilities](#agent-capabilities) |
| `agenttel.agentic.capability.tool_count` | Long | [Agent Capabilities](#agent-capabilities) |
| `agenttel.agentic.capability.tools` | String[] | [Agent Capabilities](#agent-capabilities) |
| `agenttel.agentic.code.exit_code` | Long | [Code Execution](#code-execution) |
| `agenttel.agentic.code.language` | String | [Code Execution](#code-execution) |
| `agenttel.agentic.code.sandboxed` | Boolean | [Code Execution](#code-execution) |
| `agenttel.agentic.code.status` | String | [Code Execution](#code-execution) |
| `agenttel.agentic.conversation.id` | String | [Conversation / Message Tracking](#conversation--message-tracking) |
| `agenttel.agentic.conversation.message_count` | Long | [Conversation / Message Tracking](#conversation--message-tracking) |
| `agenttel.agentic.conversation.speaker_role` | String | [Conversation / Message Tracking](#conversation--message-tracking) |
| `agenttel.agentic.conversation.turn` | Long | [Conversation / Message Tracking](#conversation--message-tracking) |
| `agenttel.agentic.cost.cached_read_tokens` | Long | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.cost.cached_write_tokens` | Long | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.cost.input_tokens` | Long | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.cost.llm_calls` | Long | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.cost.output_tokens` | Long | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.cost.reasoning_tokens` | Long | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.cost.total_usd` | Double | [Cost Aggregation](#cost-aggregation) |
| `agenttel.agentic.error.category` | String | [Error Classification](#error-classification) |
| `agenttel.agentic.error.retryable` | Boolean | [Error Classification](#error-classification) |
| `agenttel.agentic.error.source` | String | [Error Classification](#error-classification) |
| `agenttel.agentic.eval.criteria` | String | [Evaluation / Scoring](#evaluation--scoring) |
| `agenttel.agentic.eval.feedback` | String | [Evaluation / Scoring](#evaluation--scoring) |
| `agenttel.agentic.eval.score` | Double | [Evaluation / Scoring](#evaluation--scoring) |
| `agenttel.agentic.eval.scorer_name` | String | [Evaluation / Scoring](#evaluation--scoring) |
| `agenttel.agentic.eval.type` | String | [Evaluation / Scoring](#evaluation--scoring) |
| `agenttel.agentic.guardrail.action` | String | [Guardrail / Safety](#guardrail--safety) |
| `agenttel.agentic.guardrail.name` | String | [Guardrail / Safety](#guardrail--safety) |
| `agenttel.agentic.guardrail.reason` | String | [Guardrail / Safety](#guardrail--safety) |
| `agenttel.agentic.guardrail.triggered` | Boolean | [Guardrail / Safety](#guardrail--safety) |
| `agenttel.agentic.handoff.chain_depth` | Long | [Handoff / Delegation](#handoff--delegation) |
| `agenttel.agentic.handoff.from_agent` | String | [Handoff / Delegation](#handoff--delegation) |
| `agenttel.agentic.handoff.reason` | String | [Handoff / Delegation](#handoff--delegation) |
| `agenttel.agentic.handoff.to_agent` | String | [Handoff / Delegation](#handoff--delegation) |
| `agenttel.agentic.human.checkpoint_type` | String | [Human-in-the-Loop](#human-in-the-loop) |
| `agenttel.agentic.human.decision` | String | [Human-in-the-Loop](#human-in-the-loop) |
| `agenttel.agentic.human.wait_ms` | Long | [Human-in-the-Loop](#human-in-the-loop) |
| `agenttel.agentic.invocation.goal` | String | [Invocation](#invocation) |
| `agenttel.agentic.invocation.id` | String | [Invocation](#invocation) |
| `agenttel.agentic.invocation.max_steps` | Long | [Invocation](#invocation) |
| `agenttel.agentic.invocation.status` | String | [Invocation](#invocation) |
| `agenttel.agentic.invocation.steps` | Long | [Invocation](#invocation) |
| `agenttel.agentic.memory.items` | Long | [Memory Access](#memory-access) |
| `agenttel.agentic.memory.operation` | String | [Memory Access](#memory-access) |
| `agenttel.agentic.memory.store_type` | String | [Memory Access](#memory-access) |
| `agenttel.agentic.orchestration.aggregation` | String | [Orchestration](#orchestration) |
| `agenttel.agentic.orchestration.coordinator_id` | String | [Orchestration](#orchestration) |
| `agenttel.agentic.orchestration.parallel_branches` | Long | [Orchestration](#orchestration) |
| `agenttel.agentic.orchestration.pattern` | String | [Orchestration](#orchestration) |
| `agenttel.agentic.orchestration.stage` | Long | [Orchestration](#orchestration) |
| `agenttel.agentic.orchestration.total_stages` | Long | [Orchestration](#orchestration) |
| `agenttel.agentic.quality.eval_score` | Double | [Quality Signals](#quality-signals) |
| `agenttel.agentic.quality.goal_achieved` | Boolean | [Quality Signals](#quality-signals) |
| `agenttel.agentic.quality.human_interventions` | Long | [Quality Signals](#quality-signals) |
| `agenttel.agentic.quality.loop_detected` | Boolean | [Quality Signals](#quality-signals) |
| `agenttel.agentic.quality.loop_iterations` | Long | [Quality Signals](#quality-signals) |
| `agenttel.agentic.reranker.input_documents` | Long | [Reranker](#reranker) |
| `agenttel.agentic.reranker.model` | String | [Reranker](#reranker) |
| `agenttel.agentic.reranker.output_documents` | Long | [Reranker](#reranker) |
| `agenttel.agentic.reranker.top_score` | Double | [Reranker](#reranker) |
| `agenttel.agentic.retrieval.document_count` | Long | [Retrieval (RAG)](#retrieval-rag) |
| `agenttel.agentic.retrieval.query` | String | [Retrieval (RAG)](#retrieval-rag) |
| `agenttel.agentic.retrieval.relevance_score_avg` | Double | [Retrieval (RAG)](#retrieval-rag) |
| `agenttel.agentic.retrieval.relevance_score_min` | Double | [Retrieval (RAG)](#retrieval-rag) |
| `agenttel.agentic.retrieval.store_type` | String | [Retrieval (RAG)](#retrieval-rag) |
| `agenttel.agentic.retrieval.top_k` | Long | [Retrieval (RAG)](#retrieval-rag) |
| `agenttel.agentic.step.iteration` | Long | [Step / Reasoning](#step--reasoning) |
| `agenttel.agentic.step.number` | Long | [Step / Reasoning](#step--reasoning) |
| `agenttel.agentic.step.tool_name` | String | [Step / Reasoning](#step--reasoning) |
| `agenttel.agentic.step.tool_status` | String | [Step / Reasoning](#step--reasoning) |
| `agenttel.agentic.step.type` | String | [Step / Reasoning](#step--reasoning) |
| `agenttel.agentic.task.depth` | Long | [Task Tracking](#task-tracking) |
| `agenttel.agentic.task.id` | String | [Task Tracking](#task-tracking) |
| `agenttel.agentic.task.name` | String | [Task Tracking](#task-tracking) |
| `agenttel.agentic.task.parent_id` | String | [Task Tracking](#task-tracking) |
| `agenttel.agentic.task.status` | String | [Task Tracking](#task-tracking) |

---

## Agent Identity {#agent-identity}

Attributes that identify the agent instance and its framework context. Set on the root invocation span and inherited by child spans.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.agent.name` | String | Agent identifier (e.g., `"research-assistant"`, `"code-reviewer"`) |
| `agenttel.agentic.agent.type` | String | Agent archetype. See [Agent Type values](#agent-type-values) |
| `agenttel.agentic.agent.framework` | String | Framework name (e.g., `"langchain4j"`, `"spring-ai"`, `"autogen"`) |
| `agenttel.agentic.agent.version` | String | Agent version string (e.g., `"1.2.0"`, `"2024-03-beta"`) |

### Agent Type Values {#agent-type-values}

| Value | Description |
|-------|-------------|
| `single` | Standalone agent operating independently |
| `orchestrator` | Agent that coordinates other agents |
| `worker` | Agent that executes tasks assigned by an orchestrator |
| `evaluator` | Agent that evaluates outputs from other agents |
| `critic` | Agent that critiques and suggests improvements |
| `router` | Agent that routes requests to appropriate agents |

---

## Invocation {#invocation}

Attributes that describe a single agent invocation -- from goal to completion. Set on the root span created by `AgentTracer.invoke()`.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.invocation.id` | String | Unique invocation identifier (UUID or application-defined) |
| `agenttel.agentic.invocation.goal` | String | Goal description given to the agent (e.g., `"Summarize quarterly earnings"`) |
| `agenttel.agentic.invocation.status` | String | Terminal status. See [Invocation Status values](#invocation-status-values) |
| `agenttel.agentic.invocation.steps` | Long | Total number of steps executed during this invocation |
| `agenttel.agentic.invocation.max_steps` | Long | Maximum steps guardrail -- agent stops if this limit is reached |

### Invocation Status Values {#invocation-status-values}

| Value | Description |
|-------|-------------|
| `success` | Agent completed the goal successfully |
| `failure` | Agent failed to complete the goal |
| `timeout` | Agent exceeded its time or step limit |
| `escalated` | Agent escalated to a human or higher-level agent |
| `human_intervened` | A human intervened during execution |

---

## Task Tracking {#task-tracking}

Attributes for tracking task decomposition within an invocation. Tasks can nest arbitrarily deep, forming a tree structure.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.task.id` | String | Task identifier (unique within the invocation) |
| `agenttel.agentic.task.name` | String | Human-readable task name (e.g., `"extract-entities"`, `"validate-schema"`) |
| `agenttel.agentic.task.status` | String | Task status. See [Task Status values](#task-status-values) |
| `agenttel.agentic.task.parent_id` | String | Parent task ID for nested task hierarchies |
| `agenttel.agentic.task.depth` | Long | Depth level in the task decomposition tree (root = 0) |

### Task Status Values {#task-status-values}

| Value | Description |
|-------|-------------|
| `in_progress` | Task is currently being executed |
| `completed` | Task finished successfully |
| `failed` | Task failed |
| `delegated` | Task was delegated to another agent |

---

## Step / Reasoning {#step--reasoning}

Attributes that capture individual reasoning steps within an agent's execution loop (e.g., ReAct thought-action-observation cycles).

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.step.number` | Long | Sequential step number within the invocation (1-indexed) |
| `agenttel.agentic.step.type` | String | Step type in the reasoning loop. See [Step Type values](#step-type-values) |
| `agenttel.agentic.step.iteration` | Long | Iteration number when the agent loops (e.g., retry or refinement cycle) |
| `agenttel.agentic.step.tool_name` | String | Name of the tool called during this step (e.g., `"web_search"`, `"calculator"`) |
| `agenttel.agentic.step.tool_status` | String | Outcome of the tool call. See [Tool Status values](#tool-status-values) |

### Step Type Values {#step-type-values}

| Value | Description |
|-------|-------------|
| `thought` | Agent reasoning / chain-of-thought |
| `action` | Agent taking an action (tool call, API request) |
| `observation` | Agent processing the result of an action |
| `evaluation` | Agent evaluating progress toward the goal |
| `revision` | Agent revising its approach based on evaluation |

### Tool Status Values {#tool-status-values}

| Value | Description |
|-------|-------------|
| `success` | Tool executed successfully |
| `error` | Tool returned an error |
| `timeout` | Tool call timed out |

---

## Orchestration {#orchestration}

Attributes describing multi-agent orchestration patterns. Set on session-level spans created by `AgentTracer.orchestrate()`.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.orchestration.pattern` | String | Orchestration pattern. See [Orchestration Pattern values](#orchestration-pattern-values) |
| `agenttel.agentic.orchestration.coordinator_id` | String | Identifier of the coordinating agent |
| `agenttel.agentic.orchestration.stage` | Long | Current stage number (for sequential patterns) |
| `agenttel.agentic.orchestration.total_stages` | Long | Total number of stages (for sequential patterns) |
| `agenttel.agentic.orchestration.parallel_branches` | Long | Number of parallel branches executing concurrently |
| `agenttel.agentic.orchestration.aggregation` | String | Strategy for aggregating results from parallel branches (e.g., `"merge"`, `"vote"`, `"first_success"`) |

### Orchestration Pattern Values {#orchestration-pattern-values}

| Value | Description |
|-------|-------------|
| `react` | ReAct (Reasoning + Acting) single-agent loop |
| `sequential` | Agents execute in a predefined sequence (pipeline) |
| `parallel` | Agents execute concurrently on the same input |
| `handoff` | Agent-to-agent handoff (one at a time) |
| `orchestrator_workers` | Central orchestrator dispatches tasks to worker agents |
| `evaluator_optimizer` | Iterative loop between a generator and evaluator agent |
| `group_chat` | Multiple agents converse in a shared context |
| `swarm` | Decentralized multi-agent collaboration |
| `hierarchical` | Tree-structured agent delegation |

---

## Handoff / Delegation {#handoff--delegation}

Attributes that track agent-to-agent handoffs and delegation chains.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.handoff.from_agent` | String | Name of the source agent initiating the handoff |
| `agenttel.agentic.handoff.to_agent` | String | Name of the target agent receiving the handoff |
| `agenttel.agentic.handoff.reason` | String | Reason for the handoff (e.g., `"requires domain expertise"`, `"capability mismatch"`) |
| `agenttel.agentic.handoff.chain_depth` | Long | Depth in the handoff chain (0 = original agent, 1 = first handoff, etc.) |

---

## Cost Aggregation {#cost-aggregation}

Attributes for tracking cumulative LLM costs across an agent invocation. Typically set on the root invocation span after all LLM calls complete.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.cost.total_usd` | Double | Total cost in US dollars for the invocation |
| `agenttel.agentic.cost.input_tokens` | Long | Total input (prompt) tokens consumed across all LLM calls |
| `agenttel.agentic.cost.output_tokens` | Long | Total output (completion) tokens generated across all LLM calls |
| `agenttel.agentic.cost.llm_calls` | Long | Number of LLM API calls made during the invocation |
| `agenttel.agentic.cost.reasoning_tokens` | Long | Extended thinking / chain-of-thought tokens (e.g., Claude extended thinking, OpenAI o1 reasoning) |
| `agenttel.agentic.cost.cached_read_tokens` | Long | Tokens served from prompt cache reads |
| `agenttel.agentic.cost.cached_write_tokens` | Long | Tokens written to prompt cache |

---

## Quality Signals {#quality-signals}

Attributes that capture quality and reliability indicators for the agent's execution.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.quality.goal_achieved` | Boolean | Whether the agent achieved its stated goal |
| `agenttel.agentic.quality.human_interventions` | Long | Number of times a human intervened during execution |
| `agenttel.agentic.quality.loop_detected` | Boolean | Whether the agent was detected to be looping |
| `agenttel.agentic.quality.loop_iterations` | Long | Number of loop iterations detected before breaking |
| `agenttel.agentic.quality.eval_score` | Double | Evaluation score for the invocation outcome (0.0 = worst, 1.0 = best) |

---

## Guardrail / Safety {#guardrail--safety}

Attributes that record guardrail activations and safety interventions.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.guardrail.triggered` | Boolean | Whether a guardrail was activated |
| `agenttel.agentic.guardrail.name` | String | Name of the guardrail that triggered (e.g., `"pii-filter"`, `"cost-limit"`) |
| `agenttel.agentic.guardrail.action` | String | Action taken. See [Guardrail Action values](#guardrail-action-values) |
| `agenttel.agentic.guardrail.reason` | String | Explanation of why the guardrail triggered |

### Guardrail Action Values {#guardrail-action-values}

| Value | Description |
|-------|-------------|
| `block` | Request or action was blocked entirely |
| `warn` | Warning was emitted but execution continued |
| `log` | Event was logged for audit without interrupting execution |
| `escalate` | Issue was escalated to a human or higher-level agent |

---

## Memory Access {#memory-access}

Attributes for tracking agent interactions with external memory stores (vector databases, key-value stores, etc.).

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.memory.operation` | String | Memory operation type. See [Memory Operation values](#memory-operation-values) |
| `agenttel.agentic.memory.store_type` | String | Type of memory store (e.g., `"vector"`, `"relational"`, `"key_value"`, `"graph"`) |
| `agenttel.agentic.memory.items` | Long | Number of items read, written, or deleted |

### Memory Operation Values {#memory-operation-values}

| Value | Description |
|-------|-------------|
| `read` | Reading from memory |
| `write` | Writing to memory |
| `delete` | Deleting from memory |
| `search` | Searching / querying memory |

---

## Human-in-the-Loop {#human-in-the-loop}

Attributes for tracking human checkpoints and interventions within agent workflows.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.human.checkpoint_type` | String | Type of human checkpoint. See [Checkpoint Type values](#checkpoint-type-values) |
| `agenttel.agentic.human.wait_ms` | Long | Time the agent waited for human input, in milliseconds |
| `agenttel.agentic.human.decision` | String | The decision or input provided by the human |

### Checkpoint Type Values {#checkpoint-type-values}

| Value | Description |
|-------|-------------|
| `approval` | Human must approve before the agent proceeds |
| `feedback` | Human provides feedback on agent output |
| `correction` | Human corrects agent output or behavior |
| `decision` | Human makes a decision the agent cannot make autonomously |

---

## Code Execution {#code-execution}

Attributes for tracking code execution steps (e.g., Python sandboxes, shell commands, REPL interactions).

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.code.language` | String | Programming language of the executed code (e.g., `"python"`, `"javascript"`, `"bash"`) |
| `agenttel.agentic.code.status` | String | Execution outcome. See [Code Status values](#code-status-values) |
| `agenttel.agentic.code.exit_code` | Long | Process exit code (0 = success) |
| `agenttel.agentic.code.sandboxed` | Boolean | Whether the code ran in a sandboxed / isolated environment |

### Code Status Values {#code-status-values}

| Value | Description |
|-------|-------------|
| `success` | Code executed successfully |
| `error` | Code execution produced an error |
| `timeout` | Code execution timed out |

---

## Evaluation / Scoring {#evaluation--scoring}

Attributes for evaluation and scoring steps, where an evaluator agent or heuristic grades an output.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.eval.scorer_name` | String | Identifier of the evaluation scorer (e.g., `"faithfulness"`, `"relevance"`, `"toxicity"`) |
| `agenttel.agentic.eval.criteria` | String | Description of the evaluation criteria applied |
| `agenttel.agentic.eval.score` | Double | Evaluation score (0.0 = worst, 1.0 = best) |
| `agenttel.agentic.eval.feedback` | String | Free-text feedback from the evaluator |
| `agenttel.agentic.eval.type` | String | Evaluation method. See [Evaluation Type values](#evaluation-type-values) |

### Evaluation Type Values {#evaluation-type-values}

| Value | Description |
|-------|-------------|
| `llm_judge` | LLM used as a judge to evaluate output quality |
| `heuristic` | Rule-based or algorithmic evaluation |
| `human` | Human evaluator |
| `custom` | Custom evaluation logic |

---

## Error Classification {#error-classification}

Attributes that classify errors by their source and recoverability, enabling targeted retry and escalation policies.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.error.source` | String | Origin of the error. See [Error Source values](#error-source-values) |
| `agenttel.agentic.error.category` | String | Application-defined error category (e.g., `"rate_limit"`, `"invalid_input"`, `"permission_denied"`) |
| `agenttel.agentic.error.retryable` | Boolean | Whether the error is eligible for automatic retry |

### Error Source Values {#error-source-values}

| Value | Description |
|-------|-------------|
| `llm` | Error from the LLM provider (e.g., rate limit, context length exceeded) |
| `tool` | Error from a tool execution |
| `agent` | Error in agent logic itself |
| `guardrail` | Error triggered by a guardrail |
| `timeout` | Timeout error |
| `network` | Network connectivity error |

---

## Agent Capabilities {#agent-capabilities}

Attributes describing what the agent is configured to do -- its available tools and system prompt version. Useful for correlating behavior changes with configuration changes.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.capability.tools` | String[] | List of tool names available to the agent (e.g., `["web_search", "calculator", "code_interpreter"]`) |
| `agenttel.agentic.capability.tool_count` | Long | Number of tools available to the agent |
| `agenttel.agentic.capability.system_prompt_hash` | String | Hash of the system prompt, for tracking prompt version changes (e.g., SHA-256 prefix) |

---

## Conversation / Message Tracking {#conversation--message-tracking}

Attributes for tracking multi-turn conversations and message flow within agent interactions.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.conversation.id` | String | Conversation identifier (groups multiple turns) |
| `agenttel.agentic.conversation.turn` | Long | Turn number within the conversation (1-indexed) |
| `agenttel.agentic.conversation.message_count` | Long | Number of messages exchanged in this turn |
| `agenttel.agentic.conversation.speaker_role` | String | Role of the speaker. See [Speaker Role values](#speaker-role-values) |

### Speaker Role Values {#speaker-role-values}

| Value | Description |
|-------|-------------|
| `agent` | Message from the agent |
| `user` | Message from the user |
| `system` | System message (e.g., system prompt, context injection) |

---

## Retrieval (RAG) {#retrieval-rag}

Attributes for Retrieval-Augmented Generation (RAG) operations, tracking document retrieval quality and parameters.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.retrieval.query` | String | The query string used for retrieval |
| `agenttel.agentic.retrieval.store_type` | String | Type of vector or document store (e.g., `"pinecone"`, `"weaviate"`, `"elasticsearch"`, `"pgvector"`) |
| `agenttel.agentic.retrieval.document_count` | Long | Number of documents retrieved |
| `agenttel.agentic.retrieval.top_k` | Long | The K parameter used for top-K retrieval |
| `agenttel.agentic.retrieval.relevance_score_avg` | Double | Average relevance score across retrieved documents (0.0 - 1.0) |
| `agenttel.agentic.retrieval.relevance_score_min` | Double | Minimum relevance score among retrieved documents (0.0 - 1.0) |

---

## Reranker {#reranker}

Attributes for reranking operations that refine retrieval results using a cross-encoder or other reranking model.

| Key | Type | Values / Description |
|-----|------|----------------------|
| `agenttel.agentic.reranker.model` | String | Reranker model name (e.g., `"cohere-rerank-v3"`, `"bge-reranker-large"`) |
| `agenttel.agentic.reranker.input_documents` | Long | Number of documents passed to the reranker |
| `agenttel.agentic.reranker.output_documents` | Long | Number of documents returned after reranking |
| `agenttel.agentic.reranker.top_score` | Double | Highest relevance score from the reranker (0.0 - 1.0) |

---

## Span Name Reference {#span-name-reference}

Standard span names created by the AgentTel agentic tracing API, along with their entry points and the attribute categories typically set on each span.

| Span Name | API Entry Point | Primary Attribute Categories |
|-----------|-----------------|------------------------------|
| `invoke_agent` | `AgentTracer.invoke()` | Agent Identity, Invocation, Quality Signals, Cost Aggregation |
| `agenttel.agentic.step` | `AgentInvocation.step()` / `AgentInvocation.beginStep()` | Step / Reasoning |
| `agenttel.agentic.tool_call` | `AgentInvocation.toolCall()` | Step (`step.tool_name`, `step.tool_status`) |
| `agenttel.agentic.task` | `AgentInvocation.task()` | Task Tracking |
| `agenttel.agentic.handoff` | `AgentInvocation.handoff()` | Handoff / Delegation |
| `agenttel.agentic.human_input` | `AgentInvocation.humanCheckpoint()` | Human-in-the-Loop |
| `agenttel.agentic.code_execution` | `AgentInvocation.codeExecution()` | Code Execution |
| `agenttel.agentic.evaluate` | `AgentInvocation.evaluate()` | Evaluation / Scoring |
| `agenttel.agentic.retriever` | `AgentInvocation.retrieve()` | Retrieval (RAG) |
| `agenttel.agentic.reranker` | `AgentInvocation.rerank()` | Reranker |
| `agenttel.agentic.guardrail` | `AgentInvocation.guardrail()` | Guardrail / Safety |
| `agenttel.agentic.memory` | `AgentTracer.memory()` | Memory Access |
| `agenttel.agentic.session` | `AgentTracer.orchestrate()` | Orchestration |

---

## Java Constant Reference {#java-constant-reference}

All attributes are defined as `AttributeKey` constants in `io.agenttel.api.attributes.AgenticAttributes`. The naming convention is `AgenticAttributes.CATEGORY_FIELD`.

| Java Constant | Attribute Key |
|---------------|---------------|
| `AgenticAttributes.AGENT_NAME` | `agenttel.agentic.agent.name` |
| `AgenticAttributes.AGENT_TYPE` | `agenttel.agentic.agent.type` |
| `AgenticAttributes.AGENT_FRAMEWORK` | `agenttel.agentic.agent.framework` |
| `AgenticAttributes.AGENT_VERSION` | `agenttel.agentic.agent.version` |
| `AgenticAttributes.INVOCATION_ID` | `agenttel.agentic.invocation.id` |
| `AgenticAttributes.INVOCATION_GOAL` | `agenttel.agentic.invocation.goal` |
| `AgenticAttributes.INVOCATION_STATUS` | `agenttel.agentic.invocation.status` |
| `AgenticAttributes.INVOCATION_STEPS` | `agenttel.agentic.invocation.steps` |
| `AgenticAttributes.INVOCATION_MAX_STEPS` | `agenttel.agentic.invocation.max_steps` |
| `AgenticAttributes.TASK_ID` | `agenttel.agentic.task.id` |
| `AgenticAttributes.TASK_NAME` | `agenttel.agentic.task.name` |
| `AgenticAttributes.TASK_STATUS` | `agenttel.agentic.task.status` |
| `AgenticAttributes.TASK_PARENT_ID` | `agenttel.agentic.task.parent_id` |
| `AgenticAttributes.TASK_DEPTH` | `agenttel.agentic.task.depth` |
| `AgenticAttributes.STEP_NUMBER` | `agenttel.agentic.step.number` |
| `AgenticAttributes.STEP_TYPE` | `agenttel.agentic.step.type` |
| `AgenticAttributes.STEP_ITERATION` | `agenttel.agentic.step.iteration` |
| `AgenticAttributes.STEP_TOOL_NAME` | `agenttel.agentic.step.tool_name` |
| `AgenticAttributes.STEP_TOOL_STATUS` | `agenttel.agentic.step.tool_status` |
| `AgenticAttributes.ORCHESTRATION_PATTERN` | `agenttel.agentic.orchestration.pattern` |
| `AgenticAttributes.ORCHESTRATION_COORDINATOR_ID` | `agenttel.agentic.orchestration.coordinator_id` |
| `AgenticAttributes.ORCHESTRATION_STAGE` | `agenttel.agentic.orchestration.stage` |
| `AgenticAttributes.ORCHESTRATION_TOTAL_STAGES` | `agenttel.agentic.orchestration.total_stages` |
| `AgenticAttributes.ORCHESTRATION_PARALLEL_BRANCHES` | `agenttel.agentic.orchestration.parallel_branches` |
| `AgenticAttributes.ORCHESTRATION_AGGREGATION` | `agenttel.agentic.orchestration.aggregation` |
| `AgenticAttributes.HANDOFF_FROM_AGENT` | `agenttel.agentic.handoff.from_agent` |
| `AgenticAttributes.HANDOFF_TO_AGENT` | `agenttel.agentic.handoff.to_agent` |
| `AgenticAttributes.HANDOFF_REASON` | `agenttel.agentic.handoff.reason` |
| `AgenticAttributes.HANDOFF_CHAIN_DEPTH` | `agenttel.agentic.handoff.chain_depth` |
| `AgenticAttributes.COST_TOTAL_USD` | `agenttel.agentic.cost.total_usd` |
| `AgenticAttributes.COST_INPUT_TOKENS` | `agenttel.agentic.cost.input_tokens` |
| `AgenticAttributes.COST_OUTPUT_TOKENS` | `agenttel.agentic.cost.output_tokens` |
| `AgenticAttributes.COST_LLM_CALLS` | `agenttel.agentic.cost.llm_calls` |
| `AgenticAttributes.COST_REASONING_TOKENS` | `agenttel.agentic.cost.reasoning_tokens` |
| `AgenticAttributes.COST_CACHED_READ_TOKENS` | `agenttel.agentic.cost.cached_read_tokens` |
| `AgenticAttributes.COST_CACHED_WRITE_TOKENS` | `agenttel.agentic.cost.cached_write_tokens` |
| `AgenticAttributes.QUALITY_GOAL_ACHIEVED` | `agenttel.agentic.quality.goal_achieved` |
| `AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS` | `agenttel.agentic.quality.human_interventions` |
| `AgenticAttributes.QUALITY_LOOP_DETECTED` | `agenttel.agentic.quality.loop_detected` |
| `AgenticAttributes.QUALITY_LOOP_ITERATIONS` | `agenttel.agentic.quality.loop_iterations` |
| `AgenticAttributes.QUALITY_EVAL_SCORE` | `agenttel.agentic.quality.eval_score` |
| `AgenticAttributes.GUARDRAIL_TRIGGERED` | `agenttel.agentic.guardrail.triggered` |
| `AgenticAttributes.GUARDRAIL_NAME` | `agenttel.agentic.guardrail.name` |
| `AgenticAttributes.GUARDRAIL_ACTION` | `agenttel.agentic.guardrail.action` |
| `AgenticAttributes.GUARDRAIL_REASON` | `agenttel.agentic.guardrail.reason` |
| `AgenticAttributes.MEMORY_OPERATION` | `agenttel.agentic.memory.operation` |
| `AgenticAttributes.MEMORY_STORE_TYPE` | `agenttel.agentic.memory.store_type` |
| `AgenticAttributes.MEMORY_ITEMS` | `agenttel.agentic.memory.items` |
| `AgenticAttributes.HUMAN_CHECKPOINT_TYPE` | `agenttel.agentic.human.checkpoint_type` |
| `AgenticAttributes.HUMAN_WAIT_MS` | `agenttel.agentic.human.wait_ms` |
| `AgenticAttributes.HUMAN_DECISION` | `agenttel.agentic.human.decision` |
| `AgenticAttributes.CODE_LANGUAGE` | `agenttel.agentic.code.language` |
| `AgenticAttributes.CODE_STATUS` | `agenttel.agentic.code.status` |
| `AgenticAttributes.CODE_EXIT_CODE` | `agenttel.agentic.code.exit_code` |
| `AgenticAttributes.CODE_SANDBOXED` | `agenttel.agentic.code.sandboxed` |
| `AgenticAttributes.EVAL_SCORER_NAME` | `agenttel.agentic.eval.scorer_name` |
| `AgenticAttributes.EVAL_CRITERIA` | `agenttel.agentic.eval.criteria` |
| `AgenticAttributes.EVAL_SCORE` | `agenttel.agentic.eval.score` |
| `AgenticAttributes.EVAL_FEEDBACK` | `agenttel.agentic.eval.feedback` |
| `AgenticAttributes.EVAL_TYPE` | `agenttel.agentic.eval.type` |
| `AgenticAttributes.ERROR_SOURCE` | `agenttel.agentic.error.source` |
| `AgenticAttributes.ERROR_CATEGORY` | `agenttel.agentic.error.category` |
| `AgenticAttributes.ERROR_RETRYABLE` | `agenttel.agentic.error.retryable` |
| `AgenticAttributes.CAPABILITY_TOOLS` | `agenttel.agentic.capability.tools` |
| `AgenticAttributes.CAPABILITY_TOOL_COUNT` | `agenttel.agentic.capability.tool_count` |
| `AgenticAttributes.CAPABILITY_SYSTEM_PROMPT_HASH` | `agenttel.agentic.capability.system_prompt_hash` |
| `AgenticAttributes.CONVERSATION_ID` | `agenttel.agentic.conversation.id` |
| `AgenticAttributes.CONVERSATION_TURN` | `agenttel.agentic.conversation.turn` |
| `AgenticAttributes.CONVERSATION_MESSAGE_COUNT` | `agenttel.agentic.conversation.message_count` |
| `AgenticAttributes.CONVERSATION_SPEAKER_ROLE` | `agenttel.agentic.conversation.speaker_role` |
| `AgenticAttributes.RETRIEVAL_QUERY` | `agenttel.agentic.retrieval.query` |
| `AgenticAttributes.RETRIEVAL_STORE_TYPE` | `agenttel.agentic.retrieval.store_type` |
| `AgenticAttributes.RETRIEVAL_DOCUMENT_COUNT` | `agenttel.agentic.retrieval.document_count` |
| `AgenticAttributes.RETRIEVAL_TOP_K` | `agenttel.agentic.retrieval.top_k` |
| `AgenticAttributes.RETRIEVAL_RELEVANCE_SCORE_AVG` | `agenttel.agentic.retrieval.relevance_score_avg` |
| `AgenticAttributes.RETRIEVAL_RELEVANCE_SCORE_MIN` | `agenttel.agentic.retrieval.relevance_score_min` |
| `AgenticAttributes.RERANKER_MODEL` | `agenttel.agentic.reranker.model` |
| `AgenticAttributes.RERANKER_INPUT_DOCUMENTS` | `agenttel.agentic.reranker.input_documents` |
| `AgenticAttributes.RERANKER_OUTPUT_DOCUMENTS` | `agenttel.agentic.reranker.output_documents` |
| `AgenticAttributes.RERANKER_TOP_SCORE` | `agenttel.agentic.reranker.top_score` |
