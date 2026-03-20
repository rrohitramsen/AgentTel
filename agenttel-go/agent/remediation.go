package agent

import (
	"context"
	"fmt"
	"sync"
)

// RemediationAction represents a registered remediation action.
type RemediationAction struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	RiskLevel   string `json:"risk_level"` // "low", "medium", "high"
	RequiresApproval bool `json:"requires_approval"`
}

// RemediationResult holds the outcome of executing a remediation.
type RemediationResult struct {
	Action  string `json:"action"`
	Success bool   `json:"success"`
	Message string `json:"message"`
}

// RemediationHandler is the function executed for a remediation action.
type RemediationHandler func(ctx context.Context, params map[string]interface{}) (RemediationResult, error)

// RemediationRegistry manages available remediation actions.
type RemediationRegistry struct {
	mu       sync.RWMutex
	actions  map[string]RemediationAction
	handlers map[string]RemediationHandler
}

// NewRemediationRegistry creates a new remediation registry.
func NewRemediationRegistry() *RemediationRegistry {
	return &RemediationRegistry{
		actions:  make(map[string]RemediationAction),
		handlers: make(map[string]RemediationHandler),
	}
}

// Register adds a remediation action with its handler.
func (r *RemediationRegistry) Register(action RemediationAction, handler RemediationHandler) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.actions[action.Name] = action
	r.handlers[action.Name] = handler
}

// ListActions returns all registered remediation actions.
func (r *RemediationRegistry) ListActions() []RemediationAction {
	r.mu.RLock()
	defer r.mu.RUnlock()
	actions := make([]RemediationAction, 0, len(r.actions))
	for _, a := range r.actions {
		actions = append(actions, a)
	}
	return actions
}

// Execute runs a remediation action by name.
func (r *RemediationRegistry) Execute(ctx context.Context, name string, params map[string]interface{}) (RemediationResult, error) {
	r.mu.RLock()
	handler, ok := r.handlers[name]
	r.mu.RUnlock()

	if !ok {
		return RemediationResult{}, fmt.Errorf("unknown remediation action: %s", name)
	}

	return handler(ctx, params)
}
