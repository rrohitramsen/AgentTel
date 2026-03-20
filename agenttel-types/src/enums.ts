/** Service criticality tier. */
export enum ServiceTier {
  CRITICAL = 'critical',
  STANDARD = 'standard',
  INTERNAL = 'internal',
  EXPERIMENTAL = 'experimental',
}

/** Error classification categories for automated triage. */
export enum ErrorCategory {
  DEPENDENCY_TIMEOUT = 'dependency_timeout',
  CONNECTION_ERROR = 'connection_error',
  CODE_BUG = 'code_bug',
  RATE_LIMITED = 'rate_limited',
  AUTH_FAILURE = 'auth_failure',
  RESOURCE_EXHAUSTION = 'resource_exhaustion',
  DATA_VALIDATION = 'data_validation',
  UNKNOWN = 'unknown',
}

/** Type of external dependency. */
export enum DependencyType {
  INTERNAL_SERVICE = 'internal_service',
  EXTERNAL_API = 'external_api',
  DATABASE = 'database',
  MESSAGE_BROKER = 'message_broker',
  CACHE = 'cache',
  OBJECT_STORE = 'object_store',
  IDENTITY_PROVIDER = 'identity_provider',
}

/** How critical a dependency is. */
export enum DependencyCriticality {
  REQUIRED = 'required',
  DEGRADED = 'degraded',
  OPTIONAL = 'optional',
}

/** Health state of a dependency. */
export enum DependencyState {
  HEALTHY = 'healthy',
  DEGRADED = 'degraded',
  UNHEALTHY = 'unhealthy',
  UNKNOWN = 'unknown',
}

/** Source of baseline data. */
export enum BaselineSource {
  STATIC = 'static',
  ROLLING_7D = 'rolling_7d',
  ML_MODEL = 'ml_model',
  SLO = 'slo',
}

/** Escalation severity level. */
export enum EscalationLevel {
  AUTO_RESOLVE = 'auto_resolve',
  NOTIFY_TEAM = 'notify_team',
  PAGE_ONCALL = 'page_oncall',
  INCIDENT_COMMANDER = 'incident_commander',
}

/** How a consumer calls a service. */
export enum ConsumptionPattern {
  SYNC = 'sync',
  ASYNC = 'async',
  BATCH = 'batch',
  STREAMING = 'streaming',
}

/** Known incident failure patterns. */
export enum IncidentPattern {
  CASCADE_FAILURE = 'cascade_failure',
  MEMORY_LEAK = 'memory_leak',
  THUNDERING_HERD = 'thundering_herd',
  COLD_START = 'cold_start',
  ERROR_RATE_SPIKE = 'error_rate_spike',
  LATENCY_DEGRADATION = 'latency_degradation',
}

/** Type of service level objective. */
export enum SLOType {
  AVAILABILITY = 'availability',
  LATENCY_P99 = 'latency_p99',
  LATENCY_P50 = 'latency_p50',
  ERROR_RATE = 'error_rate',
}

/** Severity of an SLO alert. */
export enum AlertSeverity {
  INFO = 'info',
  WARNING = 'warning',
  CRITICAL = 'critical',
}

/** Role of an AI agent in an orchestration. */
export enum AgentType {
  SINGLE = 'single',
  ORCHESTRATOR = 'orchestrator',
  WORKER = 'worker',
  EVALUATOR = 'evaluator',
  CRITIC = 'critic',
  ROUTER = 'router',
}

/** Type of reasoning step. */
export enum StepType {
  THOUGHT = 'thought',
  ACTION = 'action',
  OBSERVATION = 'observation',
  EVALUATION = 'evaluation',
  REVISION = 'revision',
}

/** Multi-agent coordination patterns. */
export enum OrchestrationPattern {
  REACT = 'react',
  SEQUENTIAL = 'sequential',
  PARALLEL = 'parallel',
  HANDOFF = 'handoff',
  ORCHESTRATOR_WORKERS = 'orchestrator_workers',
  EVALUATOR_OPTIMIZER = 'evaluator_optimizer',
  GROUP_CHAT = 'group_chat',
  SWARM = 'swarm',
  HIERARCHICAL = 'hierarchical',
}

/** Outcome of an agent invocation. */
export enum InvocationStatus {
  RUNNING = 'running',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
}

/** Guardrail action when triggered. */
export enum GuardrailAction {
  BLOCK = 'block',
  WARN = 'warn',
  MODIFY = 'modify',
  ESCALATE = 'escalate',
}

/** Agent memory operation type. */
export enum MemoryOperation {
  READ = 'read',
  WRITE = 'write',
  DELETE = 'delete',
  SEARCH = 'search',
}

/** Human-in-the-loop checkpoint type. */
export enum HumanCheckpointType {
  APPROVAL = 'approval',
  REVIEW = 'review',
  INPUT = 'input',
  OVERRIDE = 'override',
}

/** Evaluation scorer type. */
export enum EvalType {
  LLM_JUDGE = 'llm_judge',
  RULE = 'rule',
  HUMAN = 'human',
  CUSTOM = 'custom',
}

/** Where an agentic error originated. */
export enum ErrorSource {
  LLM = 'llm',
  TOOL = 'tool',
  CODE = 'code',
  INPUT = 'input',
  SYSTEM = 'system',
}
