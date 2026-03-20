package main

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"os"
	"os/signal"
	"time"

	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.24.0"
	"go.opentelemetry.io/otel/trace"

	agenttel "go.agenttel.dev/agenttel"
	aggin "go.agenttel.dev/agenttel/middleware/gin"
)

func main() {
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	// Load AgentTel config
	cfg, err := agenttel.LoadConfig("agenttel.yml")
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
	engine, err := agenttel.New().WithConfig(cfg).Build()
	if err != nil {
		log.Fatalf("Failed to build AgentTel engine: %v", err)
	}

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
		sdktrace.WithSpanProcessor(engine.SpanProcessor()),
		sdktrace.WithResource(res),
	)
	defer tp.Shutdown(ctx)
	otel.SetTracerProvider(tp)

	// Configure AgentTel enrichment for Gin
	enrichCfg := &aggin.Config{}
	aggin.WithBaselineProvider(engine.BaselineProvider)(enrichCfg)
	aggin.WithTopology(engine.TopologyRegistry)(enrichCfg)

	// Set up Gin router
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Recovery())

	// Apply OTel instrumentation middleware
	r.Use(otelgin.Middleware(cfg.Topology.ServiceName))

	// Apply AgentTel enrichment via onRequest hook
	r.Use(func(c *gin.Context) {
		span := trace.SpanFromContext(c.Request.Context())
		routePattern := c.FullPath()
		if routePattern == "" {
			routePattern = c.Request.URL.Path
		}
		aggin.EnrichSpan(span, c.Request.Method, routePattern, enrichCfg)
		c.Next()
	})

	// Routes
	r.POST("/api/payments", createPayment)
	r.GET("/api/payments/:id", getPayment)
	r.GET("/health", healthCheck)

	port := envOrDefault("PORT", "8080")
	srv := &http.Server{Addr: ":" + port, Handler: r}

	go func() {
		<-ctx.Done()
		shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer shutdownCancel()
		srv.Shutdown(shutdownCtx)
	}()

	log.Printf("Gin Payment Service starting on :%s", port)
	log.Printf("  POST /api/payments      - Create payment")
	log.Printf("  GET  /api/payments/:id  - Get payment")
	log.Printf("  GET  /health            - Health check")

	if err := srv.ListenAndServe(); err != http.ErrServerClosed {
		log.Fatalf("Server error: %v", err)
	}
}

func createPayment(c *gin.Context) {
	// Simulate processing latency (30-70ms)
	time.Sleep(time.Duration(30+rand.Intn(40)) * time.Millisecond)

	// Simulate occasional errors (~2% rate)
	if rand.Float64() < 0.02 {
		span := trace.SpanFromContext(c.Request.Context())
		span.RecordError(fmt.Errorf("payment processing failed: insufficient funds"))
		c.JSON(http.StatusPaymentRequired, gin.H{"error": "insufficient_funds"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"id":     fmt.Sprintf("pay_%d", rand.Intn(100000)),
		"status": "completed",
		"amount": 99.99,
	})
}

func getPayment(c *gin.Context) {
	id := c.Param("id")

	// Simulate read latency (10-25ms)
	time.Sleep(time.Duration(10+rand.Intn(15)) * time.Millisecond)

	c.JSON(http.StatusOK, gin.H{
		"id":     id,
		"status": "completed",
		"amount": 99.99,
	})
}

func healthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

func envOrDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
