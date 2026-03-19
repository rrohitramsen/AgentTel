module github.com/AgentTel/AgentTel/examples/go-service-example

go 1.22

require (
	go.agenttel.dev/agenttel v0.1.0
	go.opentelemetry.io/otel v1.33.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp v1.33.0
	go.opentelemetry.io/otel/sdk v1.33.0
)

replace go.agenttel.dev/agenttel => ../../agenttel-go
