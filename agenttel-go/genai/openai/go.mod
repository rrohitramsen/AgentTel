module go.agenttel.dev/agenttel/genai/openai

go 1.22

require (
	go.agenttel.dev/agenttel v0.1.0
	github.com/sashabaranov/go-openai v1.36.1
	go.opentelemetry.io/otel v1.33.0
	go.opentelemetry.io/otel/trace v1.33.0
)

replace go.agenttel.dev/agenttel => ../../
