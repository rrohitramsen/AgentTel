package agentic

import (
	"context"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	agattr "go.agenttel.dev/agenttel-go/attributes"
	"go.agenttel.dev/agenttel-go/enums"
)

// SequentialOrchestrator runs agent stages in sequence.
type SequentialOrchestrator struct {
	tracer trace.Tracer
}

// NewSequentialOrchestrator creates a sequential orchestrator.
func NewSequentialOrchestrator(tracer trace.Tracer) *SequentialOrchestrator {
	return &SequentialOrchestrator{tracer: tracer}
}

// Run executes stages sequentially, creating spans for each.
func (o *SequentialOrchestrator) Run(ctx context.Context, coordinatorID string, stages []string, fn func(ctx context.Context, stage string, index int) error) error {
	ctx, span := o.tracer.Start(ctx, "orchestration.sequential")
	defer span.End()

	span.SetAttributes(
		attribute.String(agattr.AgenticOrchestrationPattern, string(enums.OrchestrationPatternSequential)),
		attribute.String(agattr.AgenticOrchestrationCoordinatorID, coordinatorID),
		attribute.Int64(agattr.AgenticOrchestrationTotalStages, int64(len(stages))),
	)

	for i, stage := range stages {
		stageCtx, stageSpan := o.tracer.Start(ctx, "orchestration.stage."+stage)
		stageSpan.SetAttributes(attribute.Int64(agattr.AgenticOrchestrationStage, int64(i+1)))

		if err := fn(stageCtx, stage, i); err != nil {
			stageSpan.RecordError(err)
			stageSpan.End()
			return err
		}
		stageSpan.End()
	}

	return nil
}

// ParallelOrchestrator runs agent branches in parallel.
type ParallelOrchestrator struct {
	tracer trace.Tracer
}

// NewParallelOrchestrator creates a parallel orchestrator.
func NewParallelOrchestrator(tracer trace.Tracer) *ParallelOrchestrator {
	return &ParallelOrchestrator{tracer: tracer}
}

// Run executes branches in parallel, creating spans for each.
func (o *ParallelOrchestrator) Run(ctx context.Context, coordinatorID string, branches []string, fn func(ctx context.Context, branch string) error) []error {
	ctx, span := o.tracer.Start(ctx, "orchestration.parallel")
	defer span.End()

	span.SetAttributes(
		attribute.String(agattr.AgenticOrchestrationPattern, string(enums.OrchestrationPatternParallel)),
		attribute.String(agattr.AgenticOrchestrationCoordinatorID, coordinatorID),
		attribute.Int64(agattr.AgenticOrchestrationParallelBranches, int64(len(branches))),
	)

	errs := make([]error, len(branches))
	done := make(chan int, len(branches))

	for i, branch := range branches {
		go func(idx int, b string) {
			branchCtx, branchSpan := o.tracer.Start(ctx, "orchestration.branch."+b)
			defer branchSpan.End()

			if err := fn(branchCtx, b); err != nil {
				branchSpan.RecordError(err)
				errs[idx] = err
			}
			done <- idx
		}(i, branch)
	}

	for range branches {
		<-done
	}

	return errs
}

// EvalLoopOrchestrator runs an evaluate-optimize loop.
type EvalLoopOrchestrator struct {
	tracer  trace.Tracer
	maxIter int
}

// NewEvalLoopOrchestrator creates an eval-loop orchestrator.
func NewEvalLoopOrchestrator(tracer trace.Tracer, maxIterations int) *EvalLoopOrchestrator {
	if maxIterations <= 0 {
		maxIterations = 10
	}
	return &EvalLoopOrchestrator{tracer: tracer, maxIter: maxIterations}
}

// Run executes the evaluate-optimize loop.
func (o *EvalLoopOrchestrator) Run(ctx context.Context, coordinatorID string, fn func(ctx context.Context, iteration int) (done bool, err error)) error {
	ctx, span := o.tracer.Start(ctx, "orchestration.eval_loop")
	defer span.End()

	span.SetAttributes(
		attribute.String(agattr.AgenticOrchestrationPattern, string(enums.OrchestrationPatternEvaluatorOptimizer)),
		attribute.String(agattr.AgenticOrchestrationCoordinatorID, coordinatorID),
	)

	for i := 0; i < o.maxIter; i++ {
		iterCtx, iterSpan := o.tracer.Start(ctx, "orchestration.eval_iteration")
		iterSpan.SetAttributes(attribute.Int64(agattr.AgenticStepIteration, int64(i+1)))

		done, err := fn(iterCtx, i)
		if err != nil {
			iterSpan.RecordError(err)
			iterSpan.End()
			return err
		}
		iterSpan.End()

		if done {
			break
		}
	}

	return nil
}
