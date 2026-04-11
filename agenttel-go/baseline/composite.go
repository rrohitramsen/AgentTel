package baseline

import (
	"go.agenttel.dev/agenttel-go/models"
)

// CompositeProvider chains multiple providers with fallback semantics.
// The first provider to return a baseline wins.
type CompositeProvider struct {
	providers []Provider
}

// NewCompositeProvider creates a CompositeProvider from the given providers.
func NewCompositeProvider(providers ...Provider) *CompositeProvider {
	return &CompositeProvider{providers: providers}
}

// GetBaseline returns the first available baseline from the chain.
func (c *CompositeProvider) GetBaseline(operationName string) (models.OperationBaseline, bool) {
	for _, p := range c.providers {
		if b, ok := p.GetBaseline(operationName); ok {
			return b, true
		}
	}
	return models.OperationBaseline{}, false
}
