package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"os"
	"os/signal"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.24.0"
	"go.opentelemetry.io/otel/trace"

	agenttel "go.agenttel.dev/agenttel"
	agmw "go.agenttel.dev/agenttel/middleware/http"
)

func main() {
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	// Load AgentTel config
	cfg, err := agenttel.LoadConfigFile("agenttel.yml")
	if err != nil {
		log.Printf("Warning: could not load agenttel.yml: %v (using defaults)", err)
		cfg = agenttel.DefaultConfig()
	}

	// Set up OTel exporter
	endpoint := envOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "localhost:4318")
	exporter, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpoint(endpoint),
		otlptracehttp.WithInsecure(),
	)
	if err != nil {
		log.Fatalf("Failed to create OTLP exporter: %v", err)
	}

	// Build AgentTel engine
	engine := agenttel.NewEngineBuilder(cfg).Build()

	// Create TracerProvider with AgentTel processor
	res, _ := resource.Merge(
		resource.Default(),
		resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceName(cfg.Topology.ServiceName),
			semconv.ServiceVersion("1.0.0"),
			attribute.String("agenttel.topology.team", cfg.Topology.Team),
			attribute.String("agenttel.topology.tier", cfg.Topology.Tier),
		),
	)

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithSpanProcessor(engine.Processor()),
		sdktrace.WithResource(res),
	)
	defer tp.Shutdown(ctx)
	otel.SetTracerProvider(tp)

	tracer := otel.Tracer("go-payment-service")

	// Set up HTTP routes with AgentTel middleware
	mux := http.NewServeMux()

	mux.HandleFunc("POST /api/payments", func(w http.ResponseWriter, r *http.Request) {
		_, span := tracer.Start(r.Context(), "POST /api/payments", trace.WithSpanKind(trace.SpanKindServer))
		defer span.End()

		// Simulate processing
		time.Sleep(time.Duration(30+rand.Intn(40)) * time.Millisecond)

		// Simulate occasional errors
		if rand.Float64() < 0.02 {
			span.RecordError(fmt.Errorf("payment processing failed: insufficient funds"))
			http.Error(w, `{"error":"insufficient_funds"}`, http.StatusPaymentRequired)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"id":     fmt.Sprintf("pay_%d", rand.Intn(100000)),
			"status": "completed",
			"amount": 99.99,
		})
	})

	mux.HandleFunc("GET /api/payments/{id}", func(w http.ResponseWriter, r *http.Request) {
		_, span := tracer.Start(r.Context(), "GET /api/payments/{id}", trace.WithSpanKind(trace.SpanKindServer))
		defer span.End()

		id := r.PathValue("id")
		time.Sleep(time.Duration(10+rand.Intn(15)) * time.Millisecond)

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"id":     id,
			"status": "completed",
			"amount": 99.99,
		})
	})

	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	})

	// Wrap with AgentTel middleware
	handler := agmw.Middleware(mux,
		agmw.WithBaselineProvider(engine.BaselineProvider()),
		agmw.WithTopology(engine.TopologyRegistry()),
	)

	port := envOrDefault("PORT", "8080")
	srv := &http.Server{Addr: ":" + port, Handler: handler}

	go func() {
		<-ctx.Done()
		shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer shutdownCancel()
		srv.Shutdown(shutdownCtx)
	}()

	log.Printf("Go Payment Service starting on :%s", port)
	log.Printf("  POST /api/payments      - Create payment")
	log.Printf("  GET  /api/payments/{id} - Get payment")
	log.Printf("  GET  /health            - Health check")

	if err := srv.ListenAndServe(); err != http.ErrServerClosed {
		log.Fatalf("Server error: %v", err)
	}
}

func envOrDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
