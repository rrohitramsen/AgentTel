// Package errorclass classifies span errors into categories for agent triage.
package errorclass

import (
	"strings"

	"go.agenttel.dev/agenttel/enums"
	"go.agenttel.dev/agenttel/models"
)

// Classifier categorizes errors from exception types and HTTP status codes.
type Classifier struct{}

// NewClassifier creates an ErrorClassifier.
func NewClassifier() *Classifier {
	return &Classifier{}
}

// ClassifyException classifies an error by exception type name and message.
func (c *Classifier) ClassifyException(exceptionType, message string) models.ErrorClassification {
	lower := strings.ToLower(exceptionType + " " + message)
	dep := inferDependency(lower)

	category := enums.ErrorCategoryUnknown

	switch {
	case containsAny(lower, "timeout", "timedout", "timed out", "deadline exceeded"):
		category = enums.ErrorCategoryDependencyTimeout
	case containsAny(lower, "connection refused", "connection reset", "connect error", "econnrefused", "econnreset"):
		category = enums.ErrorCategoryConnectionError
	case containsAny(lower, "oom", "out of memory", "resource exhausted", "too many open files"):
		category = enums.ErrorCategoryResourceExhaustion
	case containsAny(lower, "validation", "invalid", "malformed", "parse error"):
		category = enums.ErrorCategoryDataValidation
	case containsAny(lower, "auth", "unauthorized", "forbidden", "permission denied"):
		category = enums.ErrorCategoryAuthFailure
	case containsAny(lower, "rate limit", "throttl", "429"):
		category = enums.ErrorCategoryRateLimited
	case containsAny(lower, "null pointer", "nil pointer", "index out of", "assertion"):
		category = enums.ErrorCategoryCodeBug
	}

	return models.ErrorClassification{
		Category:      string(category),
		RootException: exceptionType,
		Dependency:    dep,
	}
}

// ClassifyHTTPStatus classifies an error by HTTP status code.
func (c *Classifier) ClassifyHTTPStatus(statusCode int, dependency string) models.ErrorClassification {
	category := enums.ErrorCategoryUnknown

	switch {
	case statusCode == 400:
		category = enums.ErrorCategoryDataValidation
	case statusCode == 401 || statusCode == 403:
		category = enums.ErrorCategoryAuthFailure
	case statusCode == 429:
		category = enums.ErrorCategoryRateLimited
	case statusCode == 408 || statusCode == 504:
		category = enums.ErrorCategoryDependencyTimeout
	case statusCode == 502 || statusCode == 503:
		category = enums.ErrorCategoryConnectionError
	case statusCode >= 500:
		category = enums.ErrorCategoryCodeBug
	}

	return models.ErrorClassification{
		Category:   string(category),
		Dependency: dependency,
	}
}

func containsAny(s string, substrs ...string) bool {
	for _, sub := range substrs {
		if strings.Contains(s, sub) {
			return true
		}
	}
	return false
}

func inferDependency(lower string) string {
	dbKeywords := []string{"postgres", "mysql", "redis", "mongo", "cassandra", "dynamodb", "sql"}
	for _, kw := range dbKeywords {
		if strings.Contains(lower, kw) {
			return kw
		}
	}
	return ""
}
