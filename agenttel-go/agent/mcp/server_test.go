package mcp

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"testing"
)

func TestInitialize(t *testing.T) {
	srv := NewServer("agenttel-test", "1.0.0")
	resp := srv.HandleRequest(context.Background(), []byte(`{"jsonrpc":"2.0","id":1,"method":"initialize"}`))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal response: %v", err)
	}

	if result.JSONRPC != "2.0" {
		t.Errorf("expected jsonrpc=2.0, got %s", result.JSONRPC)
	}
	if result.Error != nil {
		t.Errorf("unexpected error: %v", result.Error)
	}

	// Check result fields
	resultMap, ok := result.Result.(map[string]interface{})
	if !ok {
		t.Fatalf("expected result to be a map, got %T", result.Result)
	}

	if pv, ok := resultMap["protocolVersion"]; !ok || pv != "2024-11-05" {
		t.Errorf("expected protocolVersion=2024-11-05, got %v", pv)
	}

	serverInfo, ok := resultMap["serverInfo"].(map[string]interface{})
	if !ok {
		t.Fatal("expected serverInfo to be a map")
	}
	if serverInfo["name"] != "agenttel-test" {
		t.Errorf("expected server name=agenttel-test, got %v", serverInfo["name"])
	}
	if serverInfo["version"] != "1.0.0" {
		t.Errorf("expected server version=1.0.0, got %v", serverInfo["version"])
	}
}

func TestToolsList_Empty(t *testing.T) {
	srv := NewServer("test", "1.0")
	resp := srv.HandleRequest(context.Background(), []byte(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}
	if result.Error != nil {
		t.Errorf("unexpected error: %v", result.Error)
	}

	resultMap := result.Result.(map[string]interface{})
	tools := resultMap["tools"].([]interface{})
	if len(tools) != 0 {
		t.Errorf("expected 0 tools, got %d", len(tools))
	}
}

func TestToolsList_WithTools(t *testing.T) {
	srv := NewServer("test", "1.0")
	srv.RegisterTool(
		Tool{
			Name:        "get_health",
			Description: "Get service health",
			InputSchema: json.RawMessage(`{"type":"object"}`),
		},
		func(ctx context.Context, params json.RawMessage) (interface{}, error) {
			return map[string]string{"status": "ok"}, nil
		},
	)
	srv.RegisterTool(
		Tool{
			Name:        "get_metrics",
			Description: "Get service metrics",
			InputSchema: json.RawMessage(`{"type":"object"}`),
		},
		func(ctx context.Context, params json.RawMessage) (interface{}, error) {
			return nil, nil
		},
	)

	resp := srv.HandleRequest(context.Background(), []byte(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	resultMap := result.Result.(map[string]interface{})
	tools := resultMap["tools"].([]interface{})
	if len(tools) != 2 {
		t.Errorf("expected 2 tools, got %d", len(tools))
	}
}

func TestToolsCall_Success(t *testing.T) {
	srv := NewServer("test", "1.0")
	srv.RegisterTool(
		Tool{
			Name:        "greet",
			Description: "Greet someone",
			InputSchema: json.RawMessage(`{"type":"object","properties":{"name":{"type":"string"}}}`),
		},
		func(ctx context.Context, params json.RawMessage) (interface{}, error) {
			var p struct {
				Name string `json:"name"`
			}
			json.Unmarshal(params, &p)
			return map[string]string{"greeting": "Hello, " + p.Name}, nil
		},
	)

	reqBody := `{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"greet","arguments":{"name":"Alice"}}}`
	resp := srv.HandleRequest(context.Background(), []byte(reqBody))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	if result.Error != nil {
		t.Errorf("unexpected error: %v", result.Error)
	}

	resultMap := result.Result.(map[string]interface{})
	content := resultMap["content"].([]interface{})
	if len(content) != 1 {
		t.Fatalf("expected 1 content item, got %d", len(content))
	}

	item := content[0].(map[string]interface{})
	if item["type"] != "text" {
		t.Errorf("expected type=text, got %v", item["type"])
	}

	// Parse the text field to verify the greeting
	text := item["text"].(string)
	var greeting map[string]string
	if err := json.Unmarshal([]byte(text), &greeting); err != nil {
		t.Fatalf("failed to parse greeting JSON: %v", err)
	}
	if greeting["greeting"] != "Hello, Alice" {
		t.Errorf("expected greeting='Hello, Alice', got %q", greeting["greeting"])
	}
}

func TestToolsCall_Error(t *testing.T) {
	srv := NewServer("test", "1.0")
	srv.RegisterTool(
		Tool{Name: "fail_tool", Description: "Always fails"},
		func(ctx context.Context, params json.RawMessage) (interface{}, error) {
			return nil, fmt.Errorf("something went wrong")
		},
	)

	reqBody := `{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"fail_tool","arguments":{}}}`
	resp := srv.HandleRequest(context.Background(), []byte(reqBody))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	if result.Error == nil {
		t.Fatal("expected error response")
	}
	if result.Error.Code != -32000 {
		t.Errorf("expected error code -32000, got %d", result.Error.Code)
	}
	if result.Error.Message != "something went wrong" {
		t.Errorf("expected error message 'something went wrong', got %q", result.Error.Message)
	}
}

func TestToolsCall_UnknownTool(t *testing.T) {
	srv := NewServer("test", "1.0")

	reqBody := `{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"nonexistent","arguments":{}}}`
	resp := srv.HandleRequest(context.Background(), []byte(reqBody))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	if result.Error == nil {
		t.Fatal("expected error response")
	}
	if result.Error.Code != -32602 {
		t.Errorf("expected error code -32602, got %d", result.Error.Code)
	}
	if result.Error.Message != "Unknown tool: nonexistent" {
		t.Errorf("expected 'Unknown tool: nonexistent', got %q", result.Error.Message)
	}
}

func TestParseError(t *testing.T) {
	srv := NewServer("test", "1.0")

	resp := srv.HandleRequest(context.Background(), []byte(`{invalid json`))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	if result.Error == nil {
		t.Fatal("expected error response")
	}
	if result.Error.Code != -32700 {
		t.Errorf("expected error code -32700, got %d", result.Error.Code)
	}
	if result.Error.Message != "Parse error" {
		t.Errorf("expected 'Parse error', got %q", result.Error.Message)
	}
}

func TestConcurrentRegistration(t *testing.T) {
	srv := NewServer("test", "1.0")

	var wg sync.WaitGroup
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			toolName := fmt.Sprintf("tool_%d", idx)
			srv.RegisterTool(
				Tool{
					Name:        toolName,
					Description: fmt.Sprintf("Tool %d", idx),
					InputSchema: json.RawMessage(`{"type":"object"}`),
				},
				func(ctx context.Context, params json.RawMessage) (interface{}, error) {
					return map[string]int{"index": idx}, nil
				},
			)
		}(i)
	}
	wg.Wait()

	// Verify all tools were registered
	resp := srv.HandleRequest(context.Background(), []byte(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`))

	var result Response
	if err := json.Unmarshal(resp, &result); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	resultMap := result.Result.(map[string]interface{})
	tools := resultMap["tools"].([]interface{})
	if len(tools) != 50 {
		t.Errorf("expected 50 tools, got %d", len(tools))
	}
}
