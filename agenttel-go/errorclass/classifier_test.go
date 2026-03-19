package errorclass

import (
	"testing"
)

func TestClassifyException_Timeout(t *testing.T) {
	c := NewClassifier()
	result := c.ClassifyException("java.net.SocketTimeoutException", "Read timed out")
	if result.Category != "dependency_timeout" {
		t.Errorf("expected dependency_timeout, got %s", result.Category)
	}
}

func TestClassifyException_ConnectionError(t *testing.T) {
	c := NewClassifier()
	result := c.ClassifyException("ConnectionRefusedError", "Connection refused")
	if result.Category != "connection_error" {
		t.Errorf("expected connection_error, got %s", result.Category)
	}
}

func TestClassifyException_CodeBug(t *testing.T) {
	c := NewClassifier()
	result := c.ClassifyException("NullPointerException", "null pointer dereference")
	if result.Category != "code_bug" {
		t.Errorf("expected code_bug, got %s", result.Category)
	}
}

func TestClassifyException_RateLimited(t *testing.T) {
	c := NewClassifier()
	result := c.ClassifyException("ThrottlingException", "Rate limit exceeded")
	if result.Category != "rate_limited" {
		t.Errorf("expected rate_limited, got %s", result.Category)
	}
}

func TestClassifyException_DependencyInference(t *testing.T) {
	c := NewClassifier()
	result := c.ClassifyException("PSQLException", "Connection to postgres timed out")
	if result.Category != "dependency_timeout" {
		t.Errorf("expected dependency_timeout, got %s", result.Category)
	}
	if result.Dependency != "postgres" {
		t.Errorf("expected dependency=postgres, got %s", result.Dependency)
	}
}

func TestClassifyHTTPStatus(t *testing.T) {
	tests := []struct {
		code     int
		expected string
	}{
		{400, "data_validation"},
		{401, "auth_failure"},
		{403, "auth_failure"},
		{429, "rate_limited"},
		{408, "dependency_timeout"},
		{502, "connection_error"},
		{503, "connection_error"},
		{504, "dependency_timeout"},
		{500, "code_bug"},
	}

	c := NewClassifier()
	for _, tt := range tests {
		result := c.ClassifyHTTPStatus(tt.code, "test-dep")
		if result.Category != tt.expected {
			t.Errorf("status %d: expected %s, got %s", tt.code, tt.expected, result.Category)
		}
	}
}
