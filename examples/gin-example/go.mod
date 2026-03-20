module github.com/AgentTel/AgentTel/examples/gin-example

go 1.24

require (
	go.agenttel.dev/agenttel v0.1.0
	github.com/gin-gonic/gin v1.10.0
	go.opentelemetry.io/otel v1.33.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp v1.33.0
	go.opentelemetry.io/otel/sdk v1.33.0
	go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin v0.58.0
)

replace go.agenttel.dev/agenttel => ../../agenttel-go
