package baseline

import (
	"math"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"go.agenttel.dev/agenttel/models"
)

// RollingWindow is a lock-free ring buffer for latency and error tracking.
type RollingWindow struct {
	values    []float64
	times     []int64 // unix nano
	errors    []bool
	capacity  int
	writeIdx  atomic.Int64
	count     atomic.Int64
	mu        sync.RWMutex // for snapshot reads
}

// NewRollingWindow creates a ring buffer with the given capacity.
func NewRollingWindow(capacity int) *RollingWindow {
	if capacity <= 0 {
		capacity = 1000
	}
	return &RollingWindow{
		values:   make([]float64, capacity),
		times:    make([]int64, capacity),
		errors:   make([]bool, capacity),
		capacity: capacity,
	}
}

// Record adds a latency sample to the window.
func (w *RollingWindow) Record(value float64) {
	idx := int(w.writeIdx.Add(1)-1) % w.capacity
	w.mu.Lock()
	w.values[idx] = value
	w.times[idx] = time.Now().UnixNano()
	w.errors[idx] = false
	w.mu.Unlock()
	w.count.Add(1)
}

// RecordError adds an error sample.
func (w *RollingWindow) RecordError() {
	idx := int(w.writeIdx.Add(1)-1) % w.capacity
	w.mu.Lock()
	w.values[idx] = 0
	w.times[idx] = time.Now().UnixNano()
	w.errors[idx] = true
	w.mu.Unlock()
	w.count.Add(1)
}

// Size returns the number of samples recorded (capped at capacity).
func (w *RollingWindow) Size() int {
	n := int(w.count.Load())
	if n > w.capacity {
		return w.capacity
	}
	return n
}

// Snapshot returns a point-in-time statistical snapshot of the window.
func (w *RollingWindow) Snapshot() models.RollingSnapshot {
	w.mu.RLock()
	defer w.mu.RUnlock()

	n := w.Size()
	if n == 0 {
		return models.RollingSnapshot{}
	}

	// Collect non-error latency values
	latencies := make([]float64, 0, n)
	errorCount := 0
	var oldestTime int64 = math.MaxInt64

	for i := 0; i < n; i++ {
		if w.errors[i] {
			errorCount++
		} else if w.values[i] > 0 || w.times[i] > 0 {
			latencies = append(latencies, w.values[i])
		}
		if w.times[i] > 0 && w.times[i] < oldestTime {
			oldestTime = w.times[i]
		}
	}

	if len(latencies) == 0 {
		return models.RollingSnapshot{
			ErrorRate:   1.0,
			SampleCount: n,
			AgeMs:       ageMs(oldestTime),
		}
	}

	sort.Float64s(latencies)
	mean := mean(latencies)
	stddev := stddev(latencies, mean)

	return models.RollingSnapshot{
		Mean:        mean,
		Stddev:      stddev,
		P50:         percentile(latencies, 0.50),
		P95:         percentile(latencies, 0.95),
		P99:         percentile(latencies, 0.99),
		ErrorRate:   float64(errorCount) / float64(n),
		SampleCount: n,
		AgeMs:       ageMs(oldestTime),
	}
}

func mean(vals []float64) float64 {
	if len(vals) == 0 {
		return 0
	}
	sum := 0.0
	for _, v := range vals {
		sum += v
	}
	return sum / float64(len(vals))
}

func stddev(vals []float64, mean float64) float64 {
	if len(vals) < 2 {
		return 0
	}
	sumSq := 0.0
	for _, v := range vals {
		d := v - mean
		sumSq += d * d
	}
	return math.Sqrt(sumSq / float64(len(vals)))
}

func percentile(sorted []float64, p float64) float64 {
	if len(sorted) == 0 {
		return 0
	}
	idx := p * float64(len(sorted)-1)
	lower := int(math.Floor(idx))
	upper := int(math.Ceil(idx))
	if lower == upper || upper >= len(sorted) {
		return sorted[lower]
	}
	frac := idx - float64(lower)
	return sorted[lower]*(1-frac) + sorted[upper]*frac
}

func ageMs(oldestNano int64) int64 {
	if oldestNano == math.MaxInt64 || oldestNano == 0 {
		return 0
	}
	return (time.Now().UnixNano() - oldestNano) / int64(time.Millisecond)
}

// RollingProvider tracks rolling baselines per operation.
type RollingProvider struct {
	windows    map[string]*RollingWindow
	mu         sync.RWMutex
	windowSize int
	minSamples int
}

// NewRollingProvider creates a RollingProvider.
func NewRollingProvider(windowSize, minSamples int) *RollingProvider {
	if windowSize <= 0 {
		windowSize = 1000
	}
	if minSamples <= 0 {
		minSamples = 10
	}
	return &RollingProvider{
		windows:    make(map[string]*RollingWindow),
		windowSize: windowSize,
		minSamples: minSamples,
	}
}

// RecordLatency adds a latency sample for an operation.
func (r *RollingProvider) RecordLatency(operationName string, latencyMs float64) {
	r.getOrCreate(operationName).Record(latencyMs)
}

// RecordError adds an error sample for an operation.
func (r *RollingProvider) RecordError(operationName string) {
	r.getOrCreate(operationName).RecordError()
}

// GetBaseline returns a computed baseline if sufficient samples exist.
func (r *RollingProvider) GetBaseline(operationName string) (models.OperationBaseline, bool) {
	r.mu.RLock()
	w, ok := r.windows[operationName]
	r.mu.RUnlock()

	if !ok {
		return models.OperationBaseline{}, false
	}

	snap := w.Snapshot()
	if snap.SampleCount < r.minSamples {
		return models.OperationBaseline{}, false
	}

	return models.OperationBaseline{
		OperationName: operationName,
		LatencyP50Ms:  snap.P50,
		LatencyP99Ms:  snap.P99,
		ErrorRate:     snap.ErrorRate,
		Source:        "rolling_7d",
		UpdatedAt:     time.Now(),
	}, true
}

// GetSnapshot returns the raw rolling snapshot for an operation.
func (r *RollingProvider) GetSnapshot(operationName string) (models.RollingSnapshot, bool) {
	r.mu.RLock()
	w, ok := r.windows[operationName]
	r.mu.RUnlock()

	if !ok {
		return models.RollingSnapshot{}, false
	}
	return w.Snapshot(), true
}

func (r *RollingProvider) getOrCreate(operationName string) *RollingWindow {
	r.mu.RLock()
	w, ok := r.windows[operationName]
	r.mu.RUnlock()

	if ok {
		return w
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	// Double-check after acquiring write lock
	if w, ok = r.windows[operationName]; ok {
		return w
	}

	w = NewRollingWindow(r.windowSize)
	r.windows[operationName] = w
	return w
}
