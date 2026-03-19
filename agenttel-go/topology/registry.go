// Package topology provides a service topology metadata registry.
package topology

import (
	"encoding/json"
	"sync"

	"go.agenttel.dev/agenttel/models"
)

// Registry holds service topology metadata: team, tier, domain, dependencies, consumers.
type Registry struct {
	mu sync.RWMutex

	team          string
	tier          string
	domain        string
	onCallChannel string
	repoURL       string

	dependencies map[string]models.DependencyDescriptor
	consumers    []models.ConsumerDescriptor
}

// NewRegistry creates an empty topology registry.
func NewRegistry() *Registry {
	return &Registry{
		dependencies: make(map[string]models.DependencyDescriptor),
	}
}

// SetTeam sets the owning team.
func (r *Registry) SetTeam(team string)                { r.mu.Lock(); r.team = team; r.mu.Unlock() }
func (r *Registry) SetTier(tier string)                { r.mu.Lock(); r.tier = tier; r.mu.Unlock() }
func (r *Registry) SetDomain(domain string)            { r.mu.Lock(); r.domain = domain; r.mu.Unlock() }
func (r *Registry) SetOnCallChannel(channel string)    { r.mu.Lock(); r.onCallChannel = channel; r.mu.Unlock() }
func (r *Registry) SetRepoURL(url string)              { r.mu.Lock(); r.repoURL = url; r.mu.Unlock() }

func (r *Registry) Team() string          { r.mu.RLock(); defer r.mu.RUnlock(); return r.team }
func (r *Registry) Tier() string          { r.mu.RLock(); defer r.mu.RUnlock(); return r.tier }
func (r *Registry) Domain() string        { r.mu.RLock(); defer r.mu.RUnlock(); return r.domain }
func (r *Registry) OnCallChannel() string { r.mu.RLock(); defer r.mu.RUnlock(); return r.onCallChannel }
func (r *Registry) RepoURL() string       { r.mu.RLock(); defer r.mu.RUnlock(); return r.repoURL }

// RegisterDependency adds or replaces a dependency descriptor.
func (r *Registry) RegisterDependency(dep models.DependencyDescriptor) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.dependencies[dep.Name] = dep
}

// RegisterConsumer adds a consumer descriptor.
func (r *Registry) RegisterConsumer(consumer models.ConsumerDescriptor) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.consumers = append(r.consumers, consumer)
}

// GetDependency returns a dependency by name.
func (r *Registry) GetDependency(name string) (models.DependencyDescriptor, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	d, ok := r.dependencies[name]
	return d, ok
}

// Dependencies returns all registered dependencies.
func (r *Registry) Dependencies() []models.DependencyDescriptor {
	r.mu.RLock()
	defer r.mu.RUnlock()
	deps := make([]models.DependencyDescriptor, 0, len(r.dependencies))
	for _, d := range r.dependencies {
		deps = append(deps, d)
	}
	return deps
}

// Consumers returns all registered consumers.
func (r *Registry) Consumers() []models.ConsumerDescriptor {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]models.ConsumerDescriptor, len(r.consumers))
	copy(out, r.consumers)
	return out
}

// SerializeDependenciesJSON returns dependencies as a JSON string.
func (r *Registry) SerializeDependenciesJSON() string {
	deps := r.Dependencies()
	data, err := json.Marshal(deps)
	if err != nil {
		return "[]"
	}
	return string(data)
}

// SerializeConsumersJSON returns consumers as a JSON string.
func (r *Registry) SerializeConsumersJSON() string {
	consumers := r.Consumers()
	data, err := json.Marshal(consumers)
	if err != nil {
		return "[]"
	}
	return string(data)
}
