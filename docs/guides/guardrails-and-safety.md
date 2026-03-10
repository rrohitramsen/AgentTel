# Guardrails & Safety

AgentTel provides six safety mechanisms for agent observability: guardrail recording, human-in-the-loop checkpoints, loop detection, error classification, quality tracking, and max-step limits. Each creates structured span data that gives operators visibility into how safety controls are functioning.

## Overview

| Feature | Class | What It Tracks |
|---------|-------|----------------|
| Guardrails | `GuardrailRecorder` / `AgentInvocation.guardrail()` | Named guardrail activations with action and reason |
| Human Checkpoints | `HumanCheckpointScope` | Human decisions with wait time measurement |
| Loop Detection | `LoopDetector` | Repeated identical tool calls |
| Error Classification | `AgentInvocation.classifyError()` | Error source, category, retryability |
| Quality Signals | `QualityTracker` | Goal achievement, interventions, loop status, eval score |
| Max Steps | `AgentInvocation.maxSteps()` | Step count limit for runaway prevention |

---

## Guardrails

Guardrails are safety checks that can block, warn, log, or escalate when conditions are met.

### Recording on an Invocation

```java
try (AgentInvocation inv = tracer.invoke("Generate response")) {
    String output = llm.generate(prompt);

    if (containsPii(output)) {
        inv.guardrail("pii-filter", GuardrailAction.BLOCK,
            "PII detected in output: email address");
        inv.complete(false);
        return sanitize(output);
    }

    inv.complete(true);
}
```

### GuardrailRecorder (Standalone)

For guardrails that need to fire outside of an invocation scope:

```java
GuardrailRecorder recorder = new GuardrailRecorder(
    openTelemetry.getTracer("my-agent"));

// Records a guardrail span as a child of the current context
recorder.record("content-policy", GuardrailAction.WARN,
    "Response mentions competitor products");

// With explicit parent context
recorder.record("budget-limit", GuardrailAction.ESCALATE,
    "Session cost exceeded $10", parentContext);
```

### GuardrailAction Enum

| Value | Description | Typical Use |
|-------|-------------|-------------|
| `block` | Prevent the action from proceeding | PII in output, harmful content |
| `warn` | Allow but flag for review | Borderline content, high cost |
| `log` | Record silently for audit | Policy violations that don't need blocking |
| `escalate` | Hand off to human for decision | Budget exceeded, uncertain safety |

**Span output:**

```
agenttel.agentic.guardrail
  agenttel.agentic.guardrail.triggered = true
  agenttel.agentic.guardrail.name      = "pii-filter"
  agenttel.agentic.guardrail.action    = "block"
  agenttel.agentic.guardrail.reason    = "PII detected in output: email address"
```

---

## Human Checkpoints

Track human-in-the-loop interactions with automatic wait time measurement.

```java
try (AgentInvocation inv = tracer.invoke("Execute remediation")) {
    inv.step(StepType.THOUGHT, "Recommending deployment rollback");

    // Approval gate
    try (HumanCheckpointScope checkpoint =
            inv.humanCheckpoint(HumanCheckpointType.APPROVAL,
                "Approve rollback of payment-service from v2.1.0 to v2.0.9")) {

        // This blocks until the human responds
        ApprovalResult result = awaitApproval();
        checkpoint.decision(result.approved() ? "approved" : "rejected");

        // wait_ms is automatically computed from scope creation to decision()
    }

    if (approved) {
        inv.step(StepType.ACTION, "Executing rollback");
        inv.complete(true);
    } else {
        inv.complete(InvocationStatus.HUMAN_INTERVENED);
    }
}
```

### HumanCheckpointType Enum

| Value | Description | Example |
|-------|-------------|---------|
| `approval` | Binary yes/no gate | "Approve deployment rollback?" |
| `feedback` | Free-form human input | "How should we handle this edge case?" |
| `correction` | Human corrects agent output | "The correct amount is $45.00, not $54.00" |
| `decision` | Multi-option choice | "Should we: (a) retry, (b) rollback, (c) escalate?" |

**Span output:**

```
agenttel.agentic.human_input
  agenttel.agentic.human.checkpoint_type = "approval"
  agenttel.agentic.human.decision        = "approved"
  agenttel.agentic.human.wait_ms         = 45230
```

!!! info
    The `quality.human_interventions` counter on the parent `invoke_agent` span is automatically incremented each time `humanCheckpoint()` is called.

---

## Loop Detection

`LoopDetector` identifies stuck reasoning loops where an agent calls the same tool with the same arguments repeatedly.

```java
LoopDetector loopDetector = new LoopDetector(3);  // threshold: 3 identical calls

try (AgentInvocation inv = tracer.invoke("Process data")) {
    while (hasMoreWork()) {
        String toolName = agent.nextToolCall();
        String argsHash = hashArgs(agent.nextToolArgs());

        // Check for loops before executing
        if (loopDetector.recordCall(toolName, argsHash, inv.span())) {
            inv.guardrail("loop-detector", GuardrailAction.BLOCK,
                "Detected loop: " + toolName + " called " + 3 + " times");
            inv.complete(InvocationStatus.FAILURE);
            break;
        }

        try (ToolCallScope tool = inv.toolCall(toolName)) {
            executeTool(toolName, agent.nextToolArgs());
            tool.success();
        }
    }
}

// Reset between invocations
loopDetector.reset();
```

When the threshold is reached, `LoopDetector` automatically sets on the parent span:

- `agenttel.agentic.quality.loop_detected` = `true`
- `agenttel.agentic.quality.loop_iterations` = count

### Constructor

| Parameter | Default | Description |
|-----------|---------|-------------|
| `threshold` | `3` | Number of identical calls before triggering |

---

## Error Classification

Classify errors by their source in the agent pipeline.

```java
try (AgentInvocation inv = tracer.invoke("Query knowledge base")) {
    try {
        var result = llmClient.complete(prompt);
        inv.complete(true);
    } catch (RateLimitException e) {
        inv.classifyError(ErrorSource.LLM, "rate_limited", true);
        inv.complete(false);
    } catch (ToolExecutionException e) {
        inv.classifyError(ErrorSource.TOOL, "tool_failure", true);
        inv.complete(false);
    } catch (GuardrailViolation e) {
        inv.classifyError(ErrorSource.GUARDRAIL, "content_policy", false);
        inv.complete(false);
    }
}
```

### ErrorSource Enum

| Value | Description | Typically Retryable? |
|-------|-------------|---------------------|
| `llm` | Error from LLM provider (rate limit, overloaded) | Yes |
| `tool` | Error from tool invocation | Depends |
| `agent` | Error in agent logic | No |
| `guardrail` | Guardrail blocked the operation | No |
| `timeout` | Operation timed out | Yes |
| `network` | Network connectivity issue | Yes |

**Span attributes set:**

```
agenttel.agentic.error.source    = "llm"
agenttel.agentic.error.category  = "rate_limited"
agenttel.agentic.error.retryable = true
```

---

## Quality Signals

`QualityTracker` aggregates quality metrics across an invocation and applies them to a span.

```java
QualityTracker quality = new QualityTracker();

try (AgentInvocation inv = tracer.invoke("Generate report")) {
    // Track events
    quality.setGoalAchieved(true);
    quality.recordHumanIntervention();
    quality.setEvalScore(0.85);

    // If loop detector fires
    quality.setLoopDetected(true);

    // Apply all signals to the invocation span at completion
    quality.applyTo(inv.span());
}
```

### QualityTracker Methods

| Method | Attribute Set |
|--------|--------------|
| `setGoalAchieved(boolean)` | `quality.goal_achieved` |
| `recordHumanIntervention()` | `quality.human_interventions` (incremented) |
| `setLoopDetected(boolean)` | `quality.loop_detected` |
| `setEvalScore(double)` | `quality.eval_score` |
| `applyTo(Span)` | Writes all tracked signals to the span |

### Read-Back Methods

| Method | Returns |
|--------|---------|
| `isGoalAchieved()` | Current goal status |
| `getHumanInterventions()` | Intervention count |
| `isLoopDetected()` | Loop detection status |

!!! tip
    `QualityTracker` is thread-safe (all fields use `Atomic*` types) and can be shared across threads in parallel orchestrations.

---

## Max Steps

Prevent runaway agents by setting a step limit on invocations.

```java
try (AgentInvocation inv = tracer.invoke("Process batch")) {
    inv.maxSteps(50);  // Sets agenttel.agentic.invocation.max_steps = 50

    while (hasMoreWork()) {
        if (inv.stepCount() >= 50) {
            inv.guardrail("max-steps", GuardrailAction.BLOCK,
                "Reached maximum step limit of 50");
            inv.complete(InvocationStatus.TIMEOUT);
            return;
        }

        inv.step(StepType.ACTION, "Processing item " + itemId);
        processItem(itemId);
    }

    inv.complete(true);
}
```

`maxSteps()` is declarative — it sets the attribute for observability but doesn't enforce the limit automatically. Use `stepCount()` to implement the enforcement logic.

---

## Combined Example

Putting it all together — an agent with guardrails, loop detection, human checkpoints, and quality tracking:

```java
LoopDetector loopDetector = new LoopDetector(3);
QualityTracker quality = new QualityTracker();

try (AgentInvocation inv = tracer.invoke("Resolve incident")) {
    inv.maxSteps(20);
    inv.tools(List.of("get_health", "get_logs", "execute_remediation"));

    // Step 1: Diagnose
    inv.step(StepType.THOUGHT, "Checking service health");

    try (ToolCallScope tool = inv.toolCall("get_health")) {
        tool.success();
    }

    inv.step(StepType.OBSERVATION, "Error rate elevated at 5.2%");

    // Step 2: Check for loops
    String argsHash = "service=payments";
    if (loopDetector.recordCall("get_health", argsHash, inv.span())) {
        quality.setLoopDetected(true);
    }

    // Step 3: Propose remediation
    inv.step(StepType.THOUGHT, "Recommending circuit breaker activation");

    // Step 4: Human approval
    try (HumanCheckpointScope cp =
            inv.humanCheckpoint(HumanCheckpointType.APPROVAL,
                "Activate circuit breaker for stripe-api?")) {
        cp.decision("approved");
        quality.recordHumanIntervention();
    }

    // Step 5: Execute
    try (ToolCallScope tool = inv.toolCall("execute_remediation")) {
        tool.success();
    }

    // Apply quality signals
    quality.setGoalAchieved(true);
    quality.setEvalScore(0.9);
    quality.applyTo(inv.span());

    inv.complete(true);
}

loopDetector.reset();
```

---

## Further Reading

- [Agent Observability](agent-observability.md) — core agent tracing APIs
- [Orchestration Patterns](orchestration-patterns.md) — multi-agent orchestration
- [Agent Cost Tracking](agent-cost-tracking.md) — cost-based budget guardrails
- [Agentic Attributes Reference](../reference/agent-attributes.md) — all guardrail and quality attributes
