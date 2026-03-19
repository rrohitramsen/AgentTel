import { trace, type Span, type Tracer, SpanKind } from '@opentelemetry/api';
import * as ag from '../agentic-attributes.js';
import { AgentType, InvocationStatus, type StepType } from '../enums-impl.js';

export interface AgentTracerOptions {
  agentName: string;
  agentType?: AgentType;
  framework?: string;
  agentVersion?: string;
}

/** Creates agent invocation traces with step/task scopes. */
export class AgentTracer {
  private readonly tracer: Tracer;
  private readonly agentName: string;
  private readonly agentType: AgentType;
  private readonly framework?: string;
  private readonly agentVersion?: string;

  constructor(options: AgentTracerOptions, tracerOrName?: Tracer | string) {
    this.tracer = typeof tracerOrName === 'string'
      ? trace.getTracer(tracerOrName)
      : tracerOrName ?? trace.getTracer('agenttel.agentic');
    this.agentName = options.agentName;
    this.agentType = options.agentType ?? AgentType.SINGLE;
    this.framework = options.framework;
    this.agentVersion = options.agentVersion;
  }

  /** Start a new agent invocation. */
  invoke(goal: string): AgentInvocation {
    const span = this.tracer.startSpan('invoke_agent', { kind: SpanKind.INTERNAL });

    span.setAttribute(ag.AGENTIC_AGENT_NAME, this.agentName);
    span.setAttribute(ag.AGENTIC_AGENT_TYPE, this.agentType);
    span.setAttribute(ag.AGENTIC_INVOCATION_GOAL, goal);
    span.setAttribute(ag.AGENTIC_INVOCATION_STATUS, InvocationStatus.RUNNING);
    if (this.framework) span.setAttribute(ag.AGENTIC_AGENT_FRAMEWORK, this.framework);
    if (this.agentVersion) span.setAttribute(ag.AGENTIC_AGENT_VERSION, this.agentVersion);

    return new AgentInvocation(span, this.tracer);
  }
}

/** Represents an agent invocation scope. */
export class AgentInvocation {
  private readonly span: Span;
  private readonly tracer: Tracer;
  private stepCount = 0;

  constructor(span: Span, tracer: Tracer) {
    this.span = span;
    this.tracer = tracer;
  }

  /** Create a new reasoning step. */
  step(stepType: StepType, description?: string): StepScope {
    this.stepCount++;
    const span = this.tracer.startSpan('agenttel.agentic.step', { kind: SpanKind.INTERNAL });
    span.setAttribute(ag.AGENTIC_STEP_NUMBER, this.stepCount);
    span.setAttribute(ag.AGENTIC_STEP_TYPE, stepType);
    return new StepScope(span);
  }

  /** Create a new task scope. */
  task(taskName: string): TaskScope {
    const span = this.tracer.startSpan('agenttel.agentic.task', { kind: SpanKind.INTERNAL });
    span.setAttribute(ag.AGENTIC_TASK_NAME, taskName);
    span.setAttribute(ag.AGENTIC_TASK_STATUS, 'in_progress');
    span.setAttribute(ag.AGENTIC_TASK_DEPTH, 0);
    return new TaskScope(span, this.tracer, 0);
  }

  /** Complete with success. */
  end(): void {
    this.span.setAttribute(ag.AGENTIC_INVOCATION_STATUS, InvocationStatus.COMPLETED);
    this.span.setAttribute(ag.AGENTIC_INVOCATION_STEPS, this.stepCount);
    this.span.end();
  }

  /** Complete with error. */
  endError(error: Error): void {
    this.span.setAttribute(ag.AGENTIC_INVOCATION_STATUS, InvocationStatus.FAILED);
    this.span.setAttribute(ag.AGENTIC_INVOCATION_STEPS, this.stepCount);
    this.span.recordException(error);
    this.span.end();
  }

  getSpan(): Span { return this.span; }
}

/** A single reasoning step scope. */
export class StepScope {
  private readonly span: Span;

  constructor(span: Span) { this.span = span; }

  toolName(name: string): this { this.span.setAttribute(ag.AGENTIC_STEP_TOOL_NAME, name); return this; }
  toolStatus(status: string): this { this.span.setAttribute(ag.AGENTIC_STEP_TOOL_STATUS, status); return this; }
  error(err: Error): void { this.span.recordException(err); }
  end(): void { this.span.end(); }
  getSpan(): Span { return this.span; }
}

/** A task or subtask scope. */
export class TaskScope {
  private readonly span: Span;
  private readonly tracer: Tracer;
  private readonly depth: number;

  constructor(span: Span, tracer: Tracer, depth: number) {
    this.span = span;
    this.tracer = tracer;
    this.depth = depth;
  }

  subtask(name: string): TaskScope {
    const span = this.tracer.startSpan('agenttel.agentic.task', { kind: SpanKind.INTERNAL });
    span.setAttribute(ag.AGENTIC_TASK_NAME, name);
    span.setAttribute(ag.AGENTIC_TASK_STATUS, 'in_progress');
    span.setAttribute(ag.AGENTIC_TASK_DEPTH, this.depth + 1);
    return new TaskScope(span, this.tracer, this.depth + 1);
  }

  complete(): void { this.span.setAttribute(ag.AGENTIC_TASK_STATUS, 'completed'); }
  fail(reason: string): void {
    this.span.setAttribute(ag.AGENTIC_TASK_STATUS, 'failed');
    this.span.setAttribute('agenttel.agentic.task.failure_reason', reason);
  }
  end(): void { this.span.end(); }
  getSpan(): Span { return this.span; }
}
