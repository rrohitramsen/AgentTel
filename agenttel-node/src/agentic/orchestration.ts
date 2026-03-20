import { trace, type Tracer, SpanKind } from '@opentelemetry/api';
import * as ag from '../agentic-attributes.js';
import { OrchestrationPattern } from '../enums-impl.js';

/** Runs agent stages in sequence. */
export class SequentialOrchestrator {
  private readonly tracer: Tracer;

  constructor(tracerOrName?: Tracer | string) {
    this.tracer = typeof tracerOrName === 'string'
      ? trace.getTracer(tracerOrName)
      : tracerOrName ?? trace.getTracer('agenttel.agentic');
  }

  async run(
    coordinatorId: string,
    stages: string[],
    fn: (stage: string, index: number) => Promise<void>,
  ): Promise<void> {
    const span = this.tracer.startSpan('orchestration.sequential', { kind: SpanKind.INTERNAL });
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_PATTERN, OrchestrationPattern.SEQUENTIAL);
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_COORDINATOR_ID, coordinatorId);
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_TOTAL_STAGES, stages.length);

    try {
      for (let i = 0; i < stages.length; i++) {
        const stageSpan = this.tracer.startSpan(`orchestration.stage.${stages[i]}`, { kind: SpanKind.INTERNAL });
        stageSpan.setAttribute(ag.AGENTIC_ORCHESTRATION_STAGE, i + 1);
        try {
          await fn(stages[i], i);
        } catch (err) {
          stageSpan.recordException(err as Error);
          stageSpan.end();
          throw err;
        }
        stageSpan.end();
      }
    } finally {
      span.end();
    }
  }
}

/** Runs agent branches in parallel. */
export class ParallelOrchestrator {
  private readonly tracer: Tracer;

  constructor(tracerOrName?: Tracer | string) {
    this.tracer = typeof tracerOrName === 'string'
      ? trace.getTracer(tracerOrName)
      : tracerOrName ?? trace.getTracer('agenttel.agentic');
  }

  async run(
    coordinatorId: string,
    branches: string[],
    fn: (branch: string) => Promise<void>,
  ): Promise<PromiseSettledResult<void>[]> {
    const span = this.tracer.startSpan('orchestration.parallel', { kind: SpanKind.INTERNAL });
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_PATTERN, OrchestrationPattern.PARALLEL);
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_COORDINATOR_ID, coordinatorId);
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_PARALLEL_BRANCHES, branches.length);

    try {
      const promises = branches.map(async (branch) => {
        const branchSpan = this.tracer.startSpan(`orchestration.branch.${branch}`, { kind: SpanKind.INTERNAL });
        try {
          await fn(branch);
        } catch (err) {
          branchSpan.recordException(err as Error);
          throw err;
        } finally {
          branchSpan.end();
        }
      });
      return Promise.allSettled(promises);
    } finally {
      span.end();
    }
  }
}

/** Runs an evaluate-optimize loop. */
export class EvalLoopOrchestrator {
  private readonly tracer: Tracer;
  private readonly maxIterations: number;

  constructor(maxIterations = 10, tracerOrName?: Tracer | string) {
    this.maxIterations = maxIterations;
    this.tracer = typeof tracerOrName === 'string'
      ? trace.getTracer(tracerOrName)
      : tracerOrName ?? trace.getTracer('agenttel.agentic');
  }

  async run(
    coordinatorId: string,
    fn: (iteration: number) => Promise<boolean>,
  ): Promise<void> {
    const span = this.tracer.startSpan('orchestration.eval_loop', { kind: SpanKind.INTERNAL });
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_PATTERN, OrchestrationPattern.EVALUATOR_OPTIMIZER);
    span.setAttribute(ag.AGENTIC_ORCHESTRATION_COORDINATOR_ID, coordinatorId);

    try {
      for (let i = 0; i < this.maxIterations; i++) {
        const iterSpan = this.tracer.startSpan('orchestration.eval_iteration', { kind: SpanKind.INTERNAL });
        iterSpan.setAttribute(ag.AGENTIC_STEP_ITERATION, i + 1);

        try {
          const done = await fn(i);
          iterSpan.end();
          if (done) break;
        } catch (err) {
          iterSpan.recordException(err as Error);
          iterSpan.end();
          throw err;
        }
      }
    } finally {
      span.end();
    }
  }
}
