// Package mcp provides a Model Context Protocol (MCP) JSON-RPC 2.0 server
// for AI agent interaction with AgentTel observability data.
package mcp

import (
	"context"
	"encoding/json"
	"fmt"
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

// Server is an MCP JSON-RPC 2.0 server.
type Server struct {
	mu       sync.RWMutex
	tools    map[string]Tool
	handlers map[string]ToolHandler
	name     string
	version  string
}

// NewServer creates a new MCP server.
func NewServer(name, version string) *Server {
	return &Server{
		tools:    make(map[string]Tool),
		handlers: make(map[string]ToolHandler),
		name:     name,
		version:  version,
	}
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
