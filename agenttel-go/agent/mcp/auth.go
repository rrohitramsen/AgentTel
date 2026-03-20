// Package mcp provides authentication and role-based permissions for the MCP server.
package mcp

import (
	"errors"
	"strings"
)

// ToolPermission represents a permission level required to invoke a tool.
type ToolPermission int

const (
	PermissionRead      ToolPermission = iota
	PermissionDiagnose
	PermissionRemediate
	PermissionAdmin
)

// String returns the human-readable name of the permission.
func (p ToolPermission) String() string {
	switch p {
	case PermissionRead:
		return "read"
	case PermissionDiagnose:
		return "diagnose"
	case PermissionRemediate:
		return "remediate"
	case PermissionAdmin:
		return "admin"
	default:
		return "unknown"
	}
}

// AgentIdentity identifies an authenticated agent making MCP requests.
type AgentIdentity struct {
	AgentID   string
	Role      string
	SessionID string
}

// AuthConfig configures API key authentication for the MCP server.
// When nil or empty, authentication is disabled (backward compatible).
type AuthConfig struct {
	// APIKeys maps bearer tokens to agent identities.
	APIKeys map[string]AgentIdentity
	// ValidateKey is an optional custom validator. Called when a key is not
	// found in APIKeys. Return nil to reject the key.
	ValidateKey func(key string) (*AgentIdentity, error)
}

// ErrUnauthorized is returned when authentication fails.
var ErrUnauthorized = errors.New("unauthorized")

// resolveKey looks up a bearer token and returns the associated identity.
// It checks the static APIKeys map first, then falls back to ValidateKey.
func (c *AuthConfig) resolveKey(key string) (*AgentIdentity, error) {
	if c == nil {
		return nil, ErrUnauthorized
	}
	key = strings.TrimSpace(key)
	if key == "" {
		return nil, ErrUnauthorized
	}

	if c.APIKeys != nil {
		if identity, ok := c.APIKeys[key]; ok {
			return &identity, nil
		}
	}

	if c.ValidateKey != nil {
		return c.ValidateKey(key)
	}

	return nil, ErrUnauthorized
}

// ToolPermissionRegistry maps roles to granted permissions and tools to
// required permission levels. When a tool has no explicit requirement,
// PermissionRead is assumed.
type ToolPermissionRegistry struct {
	rolePermissions map[string][]ToolPermission
	toolPermissions map[string]ToolPermission
}

// NewToolPermissionRegistry creates a registry pre-populated with default
// role-to-permission mappings:
//
//	observer     -> [READ]
//	diagnostician -> [READ, DIAGNOSE]
//	remediator   -> [READ, DIAGNOSE, REMEDIATE]
//	admin        -> [READ, DIAGNOSE, REMEDIATE, ADMIN]
func NewToolPermissionRegistry() *ToolPermissionRegistry {
	r := &ToolPermissionRegistry{
		rolePermissions: make(map[string][]ToolPermission),
		toolPermissions: make(map[string]ToolPermission),
	}

	r.rolePermissions["observer"] = []ToolPermission{PermissionRead}
	r.rolePermissions["diagnostician"] = []ToolPermission{PermissionRead, PermissionDiagnose}
	r.rolePermissions["remediator"] = []ToolPermission{PermissionRead, PermissionDiagnose, PermissionRemediate}
	r.rolePermissions["admin"] = []ToolPermission{PermissionRead, PermissionDiagnose, PermissionRemediate, PermissionAdmin}

	// Default tool permission mappings
	for _, name := range []string{
		"get_service_health", "get_operation_baselines", "get_anomalies",
		"get_slo_status", "get_dependency_health", "get_topology",
		"get_incident_context", "get_error_classification", "get_deployment_info",
		"get_slo_report", "get_executive_summary", "get_trend_analysis",
		"get_playbook", "get_session",
	} {
		r.toolPermissions[name] = PermissionRead
	}

	for _, name := range []string{
		"verify_remediation_effect", "create_session", "add_session_entry",
		"suggest_remediation", "get_change_correlation",
	} {
		r.toolPermissions[name] = PermissionDiagnose
	}

	for _, name := range []string{
		"execute_remediation", "list_remediation_actions",
	} {
		r.toolPermissions[name] = PermissionRemediate
	}

	return r
}

// SetRolePermissions sets the permissions granted to a role, replacing any
// existing permissions for that role.
func (r *ToolPermissionRegistry) SetRolePermissions(role string, perms []ToolPermission) {
	r.rolePermissions[strings.ToLower(role)] = perms
}

// SetToolPermission sets the required permission level for a specific tool.
func (r *ToolPermissionRegistry) SetToolPermission(toolName string, perm ToolPermission) {
	r.toolPermissions[toolName] = perm
}

// IsAllowed checks whether the given agent identity has permission to invoke
// the named tool. Unknown tools default to PermissionRead.
func (r *ToolPermissionRegistry) IsAllowed(identity *AgentIdentity, toolName string) bool {
	if identity == nil {
		return false
	}

	role := strings.ToLower(identity.Role)
	granted, ok := r.rolePermissions[role]
	if !ok {
		// Unknown role gets observer permissions
		granted = r.rolePermissions["observer"]
	}

	required, ok := r.toolPermissions[toolName]
	if !ok {
		required = PermissionRead
	}

	for _, p := range granted {
		if p == required {
			return true
		}
	}
	return false
}
