// Package enums defines typed string constants for all AgentTel domain enumerations.
package enums

// ServiceTier indicates the criticality tier of a service.
type ServiceTier string

const (
	ServiceTierCritical     ServiceTier = "critical"
	ServiceTierStandard     ServiceTier = "standard"
	ServiceTierInternal     ServiceTier = "internal"
	ServiceTierExperimental ServiceTier = "experimental"
)

// ErrorCategory classifies errors for automated triage.
type ErrorCategory string

const (
	ErrorCategoryDependencyTimeout  ErrorCategory = "dependency_timeout"
	ErrorCategoryConnectionError    ErrorCategory = "connection_error"
	ErrorCategoryCodeBug            ErrorCategory = "code_bug"
	ErrorCategoryRateLimited        ErrorCategory = "rate_limited"
	ErrorCategoryAuthFailure        ErrorCategory = "auth_failure"
	ErrorCategoryResourceExhaustion ErrorCategory = "resource_exhaustion"
	ErrorCategoryDataValidation     ErrorCategory = "data_validation"
	ErrorCategoryUnknown            ErrorCategory = "unknown"
)

// DependencyType identifies the kind of external dependency.
type DependencyType string

const (
	DependencyTypeInternalService  DependencyType = "internal_service"
	DependencyTypeExternalAPI      DependencyType = "external_api"
	DependencyTypeDatabase         DependencyType = "database"
	DependencyTypeMessageBroker    DependencyType = "message_broker"
	DependencyTypeCache            DependencyType = "cache"
	DependencyTypeObjectStore      DependencyType = "object_store"
	DependencyTypeIdentityProvider DependencyType = "identity_provider"
)

// DependencyCriticality indicates how critical a dependency is.
type DependencyCriticality string

const (
	DependencyCriticalityRequired DependencyCriticality = "required"
	DependencyCriticalityDegraded DependencyCriticality = "degraded"
	DependencyCriticalityOptional DependencyCriticality = "optional"
)

// DependencyState indicates the health state of a dependency.
type DependencyState string

const (
	DependencyStateHealthy   DependencyState = "healthy"
	DependencyStateDegraded  DependencyState = "degraded"
	DependencyStateUnhealthy DependencyState = "unhealthy"
	DependencyStateUnknown   DependencyState = "unknown"
)

// BaselineSource indicates the origin of baseline data.
type BaselineSource string

const (
	BaselineSourceStatic    BaselineSource = "static"
	BaselineSourceRolling7D BaselineSource = "rolling_7d"
	BaselineSourceMLModel   BaselineSource = "ml_model"
	BaselineSourceSLO       BaselineSource = "slo"
)

// EscalationLevel indicates the severity of escalation.
type EscalationLevel string

const (
	EscalationLevelAutoResolve       EscalationLevel = "auto_resolve"
	EscalationLevelNotifyTeam        EscalationLevel = "notify_team"
	EscalationLevelPageOnCall        EscalationLevel = "page_oncall"
	EscalationLevelIncidentCommander EscalationLevel = "incident_commander"
)

// ConsumptionPattern describes how a consumer calls a service.
type ConsumptionPattern string

const (
	ConsumptionPatternSync     ConsumptionPattern = "sync"
	ConsumptionPatternAsync    ConsumptionPattern = "async"
	ConsumptionPatternBatch    ConsumptionPattern = "batch"
	ConsumptionPatternStreaming ConsumptionPattern = "streaming"
)

// IncidentPattern identifies known failure patterns detectable from telemetry.
type IncidentPattern string

const (
	IncidentPatternCascadeFailure     IncidentPattern = "cascade_failure"
	IncidentPatternMemoryLeak         IncidentPattern = "memory_leak"
	IncidentPatternThunderingHerd     IncidentPattern = "thundering_herd"
	IncidentPatternColdStart          IncidentPattern = "cold_start"
	IncidentPatternErrorRateSpike     IncidentPattern = "error_rate_spike"
	IncidentPatternLatencyDegradation IncidentPattern = "latency_degradation"
)

// SLOType identifies the type of service level objective.
type SLOType string

const (
	SLOTypeAvailability SLOType = "availability"
	SLOTypeLatencyP99   SLOType = "latency_p99"
	SLOTypeLatencyP50   SLOType = "latency_p50"
	SLOTypeErrorRate    SLOType = "error_rate"
)

// AlertSeverity indicates the severity of an SLO alert.
type AlertSeverity string

const (
	AlertSeverityInfo     AlertSeverity = "info"
	AlertSeverityWarning  AlertSeverity = "warning"
	AlertSeverityCritical AlertSeverity = "critical"
)

// AgentType indicates the role of an AI agent in an orchestration.
type AgentType string

const (
	AgentTypeSingle       AgentType = "single"
	AgentTypeOrchestrator AgentType = "orchestrator"
	AgentTypeWorker       AgentType = "worker"
	AgentTypeEvaluator    AgentType = "evaluator"
	AgentTypeCritic       AgentType = "critic"
	AgentTypeRouter       AgentType = "router"
)

// StepType identifies the type of reasoning step.
type StepType string

const (
	StepTypeThought     StepType = "thought"
	StepTypeAction      StepType = "action"
	StepTypeObservation StepType = "observation"
	StepTypeEvaluation  StepType = "evaluation"
	StepTypeRevision    StepType = "revision"
)

// OrchestrationPattern identifies multi-agent coordination patterns.
type OrchestrationPattern string

const (
	OrchestrationPatternReact              OrchestrationPattern = "react"
	OrchestrationPatternSequential         OrchestrationPattern = "sequential"
	OrchestrationPatternParallel           OrchestrationPattern = "parallel"
	OrchestrationPatternHandoff            OrchestrationPattern = "handoff"
	OrchestrationPatternOrchestratorWorkers OrchestrationPattern = "orchestrator_workers"
	OrchestrationPatternEvaluatorOptimizer OrchestrationPattern = "evaluator_optimizer"
	OrchestrationPatternGroupChat          OrchestrationPattern = "group_chat"
	OrchestrationPatternSwarm              OrchestrationPattern = "swarm"
	OrchestrationPatternHierarchical       OrchestrationPattern = "hierarchical"
)

// InvocationStatus indicates the outcome of an agent invocation.
type InvocationStatus string

const (
	InvocationStatusRunning   InvocationStatus = "running"
	InvocationStatusCompleted InvocationStatus = "completed"
	InvocationStatusFailed    InvocationStatus = "failed"
	InvocationStatusCancelled InvocationStatus = "cancelled"
)

// GuardrailAction indicates what a guardrail did when triggered.
type GuardrailAction string

const (
	GuardrailActionBlock    GuardrailAction = "block"
	GuardrailActionWarn     GuardrailAction = "warn"
	GuardrailActionModify   GuardrailAction = "modify"
	GuardrailActionEscalate GuardrailAction = "escalate"
)

// MemoryOperation indicates the type of agent memory operation.
type MemoryOperation string

const (
	MemoryOperationRead   MemoryOperation = "read"
	MemoryOperationWrite  MemoryOperation = "write"
	MemoryOperationDelete MemoryOperation = "delete"
	MemoryOperationSearch MemoryOperation = "search"
)

// HumanCheckpointType indicates the type of human-in-the-loop checkpoint.
type HumanCheckpointType string

const (
	HumanCheckpointTypeApproval HumanCheckpointType = "approval"
	HumanCheckpointTypeReview   HumanCheckpointType = "review"
	HumanCheckpointTypeInput    HumanCheckpointType = "input"
	HumanCheckpointTypeOverride HumanCheckpointType = "override"
)

// EvalType indicates the type of evaluation scorer.
type EvalType string

const (
	EvalTypeLLMJudge EvalType = "llm_judge"
	EvalTypeRule     EvalType = "rule"
	EvalTypeHuman    EvalType = "human"
	EvalTypeCustom   EvalType = "custom"
)

// ErrorSource identifies where an agentic error originated.
type ErrorSource string

const (
	ErrorSourceLLM    ErrorSource = "llm"
	ErrorSourceTool   ErrorSource = "tool"
	ErrorSourceCode   ErrorSource = "code"
	ErrorSourceInput  ErrorSource = "input"
	ErrorSourceSystem ErrorSource = "system"
)
