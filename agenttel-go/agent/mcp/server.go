// Package mcp provides a Model Context Protocol (MCP) JSON-RPC 2.0 server
// for AI agent interaction with AgentTel observability data.
package mcp

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
)

// Tool describes an MCP tool available to agents.
type Tool struct {
	Name        string          `json:"name"`
	Description string          `json:"description"`
	InputSchema json.RawMessage `json:"inputSchema"`
}

// ToolHandler is the function that executes a tool call.
type ToolHandler func(ctx context.Context, params json.RawMessage) (interface{}, error)

// Request is a JSON-RPC 2.0 request.
type Request struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      interface{}     `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}

// Response is a JSON-RPC 2.0 response.
type Response struct {
	JSONRPC string      `json:"jsonrpc"`
	ID      interface{} `json:"id"`
	Result  interface{} `json:"result,omitempty"`
	Error   *RPCError   `json:"error,omitempty"`
}

// RPCError represents a JSON-RPC 2.0 error.
type RPCError struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

// contextKey is an unexported type for context keys in this package.
type contextKey int

const (
	// identityKey is the context key for AgentIdentity.
	identityKey contextKey = iota
)

// IdentityFromContext extracts the AgentIdentity from a context, if present.
func IdentityFromContext(ctx context.Context) *AgentIdentity {
	v, _ := ctx.Value(identityKey).(*AgentIdentity)
	return v
}

// Option configures a Server.
type Option func(*Server)

// WithAuth enables API key authentication on the server.
// When configured, HandleHTTPRequest will require a valid
// Authorization: Bearer <key> header.
func WithAuth(config AuthConfig) Option {
	return func(s *Server) { s.authConfig = &config }
}

// WithPermissions enables role-based tool permission checks.
// Requires WithAuth to be effective (identity must be resolved first).
func WithPermissions(registry *ToolPermissionRegistry) Option {
	return func(s *Server) { s.permissions = registry }
}

// Server is an MCP JSON-RPC 2.0 server.
type Server struct {
	mu          sync.RWMutex
	tools       map[string]Tool
	handlers    map[string]ToolHandler
	name        string
	version     string
	authConfig  *AuthConfig            // nil = no auth
	permissions *ToolPermissionRegistry // nil = no permission checks
}

// NewServer creates a new MCP server. Options are applied in order.
func NewServer(name, version string, opts ...Option) *Server {
	s := &Server{
		tools:    make(map[string]Tool),
		handlers: make(map[string]ToolHandler),
		name:     name,
		version:  version,
	}
	for _, opt := range opts {
		opt(s)
	}
	return s
}

// RegisterTool adds a tool to the server.
func (s *Server) RegisterTool(tool Tool, handler ToolHandler) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.tools[tool.Name] = tool
	s.handlers[tool.Name] = handler
}

// HandleRequest processes a JSON-RPC 2.0 request.
func (s *Server) HandleRequest(ctx context.Context, reqBytes []byte) []byte {
	var req Request
	if err := json.Unmarshal(reqBytes, &req); err != nil {
		return s.errorResponse(nil, -32700, "Parse error")
	}

	switch req.Method {
	case "initialize":
		return s.handleInitialize(req)
	case "tools/list":
		return s.handleToolsList(req)
	case "tools/call":
		return s.handleToolsCall(ctx, req)
	default:
		return s.errorResponse(req.ID, -32601, fmt.Sprintf("Method not found: %s", req.Method))
	}
}

// HandleHTTPRequest is an http.HandlerFunc-compatible method that extracts
// the Authorization: Bearer <key> header, authenticates the caller, checks
// permissions, and dispatches the JSON-RPC request.
//
// When no AuthConfig is set, it behaves identically to HandleRequest.
func (s *Server) HandleHTTPRequest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write(s.errorResponse(nil, -32700, "Failed to read request body"))
		return
	}

	ctx := r.Context()

	// Authenticate if auth is configured
	if s.authConfig != nil {
		authHeader := r.Header.Get("Authorization")
		key := strings.TrimPrefix(authHeader, "Bearer ")
		key = strings.TrimPrefix(key, "bearer ")

		identity, authErr := s.authConfig.resolveKey(key)
		if authErr != nil || identity == nil {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(s.errorResponse(nil, -32603, "Unauthorized: invalid or missing API key"))
			return
		}
		ctx = context.WithValue(ctx, identityKey, identity)
	}

	// Parse request to check permissions before dispatch
	if s.permissions != nil && s.authConfig != nil {
		var req Request
		if parseErr := json.Unmarshal(body, &req); parseErr == nil && req.Method == "tools/call" {
			var params struct {
				Name string `json:"name"`
			}
			if parseErr2 := json.Unmarshal(req.Params, &params); parseErr2 == nil {
				identity := IdentityFromContext(ctx)
				if identity != nil && !s.permissions.IsAllowed(identity, params.Name) {
					w.Header().Set("Content-Type", "application/json")
					w.WriteHeader(http.StatusOK)
					_, _ = w.Write(s.errorResponse(req.ID, -32603,
						fmt.Sprintf("Permission denied: role '%s' cannot access tool '%s'", identity.Role, params.Name)))
					return
				}
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(s.HandleRequest(ctx, body))
}

func (s *Server) handleInitialize(req Request) []byte {
	result := map[string]interface{}{
		"protocolVersion": "2024-11-05",
		"capabilities":    map[string]interface{}{"tools": map[string]interface{}{}},
		"serverInfo":      map[string]string{"name": s.name, "version": s.version},
	}
	return s.successResponse(req.ID, result)
}

func (s *Server) handleToolsList(req Request) []byte {
	s.mu.RLock()
	defer s.mu.RUnlock()
	tools := make([]Tool, 0, len(s.tools))
	for _, t := range s.tools {
		tools = append(tools, t)
	}
	return s.successResponse(req.ID, map[string]interface{}{"tools": tools})
}

func (s *Server) handleToolsCall(ctx context.Context, req Request) []byte {
	var params struct {
		Name      string          `json:"name"`
		Arguments json.RawMessage `json:"arguments"`
	}
	if err := json.Unmarshal(req.Params, &params); err != nil {
		return s.errorResponse(req.ID, -32602, "Invalid params")
	}

	s.mu.RLock()
	handler, ok := s.handlers[params.Name]
	s.mu.RUnlock()

	if !ok {
		return s.errorResponse(req.ID, -32602, fmt.Sprintf("Unknown tool: %s", params.Name))
	}

	result, err := handler(ctx, params.Arguments)
	if err != nil {
		return s.errorResponse(req.ID, -32000, err.Error())
	}

	return s.successResponse(req.ID, map[string]interface{}{
		"content": []map[string]interface{}{
			{"type": "text", "text": toJSON(result)},
		},
	})
}

func (s *Server) successResponse(id interface{}, result interface{}) []byte {
	resp := Response{JSONRPC: "2.0", ID: id, Result: result}
	data, _ := json.Marshal(resp)
	return data
}

func (s *Server) errorResponse(id interface{}, code int, message string) []byte {
	resp := Response{JSONRPC: "2.0", ID: id, Error: &RPCError{Code: code, Message: message}}
	data, _ := json.Marshal(resp)
	return data
}

func toJSON(v interface{}) string {
	data, err := json.Marshal(v)
	if err != nil {
		return fmt.Sprintf("%v", v)
	}
	return string(data)
}
