import { describe, it, expect, vi } from 'vitest';
import { AgentTracer, AgentInvocation } from '../src/agentic/tracer.js';
import { AgentType, StepType, InvocationStatus } from '../src/enums-impl.js';
import * as ag from '../src/agentic-attributes.js';

// Create a mock span that captures setAttribute calls
function createMockSpan() {
  const attributes = new Map<string, unknown>();
  const events: Array<{ name: string }> = [];
  return {
    setAttribute: vi.fn((key: string, value: unknown) => {
      attributes.set(key, value);
    }),
    recordException: vi.fn((err: Error) => {
      events.push({ name: 'exception' });
    }),
    end: vi.fn(),
    _attributes: attributes,
    _events: events,
  };
}

// Create a mock tracer that returns predictable mock spans
function createMockTracer() {
  const spans: Array<ReturnType<typeof createMockSpan>> = [];
  const mockTracer = {
    startSpan: vi.fn(() => {
      const span = createMockSpan();
      spans.push(span);
      return span;
    }),
  };
  return {
    mockTracer,
    spans,
  };
}

describe('AgentTracer', () => {
  it('invoke creates span with agent attrs', () => {
    const { mockTracer, spans } = createMockTracer();

    const agentTracer = new AgentTracer(
      {
        agentName: 'slo-agent',
        agentType: AgentType.WORKER,
        framework: 'agenttel',
        agentVersion: '1.0',
      },
      mockTracer as any,
    );

    const invocation = agentTracer.invoke('Monitor SLO compliance');

    expect(mockTracer.startSpan).toHaveBeenCalledWith('invoke_agent', expect.any(Object));
    expect(spans).toHaveLength(1);

    const span = spans[0];
    expect(span.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_AGENT_NAME, 'slo-agent');
    expect(span.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_AGENT_TYPE, AgentType.WORKER);
    expect(span.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_INVOCATION_GOAL, 'Monitor SLO compliance');
    expect(span.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_INVOCATION_STATUS, InvocationStatus.RUNNING);
    expect(span.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_AGENT_FRAMEWORK, 'agenttel');
    expect(span.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_AGENT_VERSION, '1.0');
  });

  it('step creates span with step number and type', () => {
    const { mockTracer, spans } = createMockTracer();

    const agentTracer = new AgentTracer(
      { agentName: 'test-agent' },
      mockTracer as any,
    );

    const invocation = agentTracer.invoke('test goal');
    const step = invocation.step(StepType.THOUGHT, 'analyzing data');

    // Should have 2 spans: invocation + step
    expect(spans).toHaveLength(2);

    const stepSpan = spans[1];
    expect(stepSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_STEP_NUMBER, 1);
    expect(stepSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_STEP_TYPE, StepType.THOUGHT);
  });

  it('task creates span with task name and status', () => {
    const { mockTracer, spans } = createMockTracer();

    const agentTracer = new AgentTracer(
      { agentName: 'test-agent' },
      mockTracer as any,
    );

    const invocation = agentTracer.invoke('test goal');
    const task = invocation.task('analyze-metrics');

    expect(spans).toHaveLength(2);

    const taskSpan = spans[1];
    expect(taskSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_TASK_NAME, 'analyze-metrics');
    expect(taskSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_TASK_STATUS, 'in_progress');
    expect(taskSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_TASK_DEPTH, 0);
  });

  it('subtask increments depth', () => {
    const { mockTracer, spans } = createMockTracer();

    const agentTracer = new AgentTracer(
      { agentName: 'test-agent' },
      mockTracer as any,
    );

    const invocation = agentTracer.invoke('test goal');
    const parentTask = invocation.task('parent-task');
    const subtask = parentTask.subtask('child-task');

    // 3 spans: invocation, parent task, subtask
    expect(spans).toHaveLength(3);

    const subtaskSpan = spans[2];
    expect(subtaskSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_TASK_NAME, 'child-task');
    expect(subtaskSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_TASK_DEPTH, 1);
  });

  it('end sets completed status', () => {
    const { mockTracer, spans } = createMockTracer();

    const agentTracer = new AgentTracer(
      { agentName: 'test-agent' },
      mockTracer as any,
    );

    const invocation = agentTracer.invoke('test goal');
    invocation.step(StepType.THOUGHT);
    invocation.step(StepType.ACTION);
    invocation.end();

    const invSpan = spans[0];
    expect(invSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_INVOCATION_STATUS, InvocationStatus.COMPLETED);
    expect(invSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_INVOCATION_STEPS, 2);
    expect(invSpan.end).toHaveBeenCalled();
  });

  it('endError sets failed status and records exception', () => {
    const { mockTracer, spans } = createMockTracer();

    const agentTracer = new AgentTracer(
      { agentName: 'test-agent' },
      mockTracer as any,
    );

    const invocation = agentTracer.invoke('will fail');
    const error = new Error('LLM rate limit exceeded');
    invocation.endError(error);

    const invSpan = spans[0];
    expect(invSpan.setAttribute).toHaveBeenCalledWith(ag.AGENTIC_INVOCATION_STATUS, InvocationStatus.FAILED);
    expect(invSpan.recordException).toHaveBeenCalledWith(error);
    expect(invSpan.end).toHaveBeenCalled();
  });
});
