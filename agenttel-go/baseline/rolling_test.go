package baseline

import (
	"math"
	"testing"
)

func TestRollingWindow_BasicStats(t *testing.T) {
	w := NewRollingWindow(100)

	values := []float64{10, 20, 30, 40, 50}
	for _, v := range values {
		w.Record(v)
	}

	snap := w.Snapshot()

	if snap.SampleCount != 5 {
		t.Errorf("expected 5 samples, got %d", snap.SampleCount)
	}
	if math.Abs(snap.Mean-30.0) > 0.01 {
		t.Errorf("expected mean=30, got %f", snap.Mean)
	}
	if snap.P50 != 30.0 {
		t.Errorf("expected P50=30, got %f", snap.P50)
	}
}

func TestRollingWindow_ErrorRate(t *testing.T) {
	w := NewRollingWindow(100)

	for i := 0; i < 8; i++ {
		w.Record(float64(i * 10))
	}
	w.RecordError()
	w.RecordError()

	snap := w.Snapshot()
	if snap.SampleCount != 10 {
		t.Errorf("expected 10 samples, got %d", snap.SampleCount)
	}
	if math.Abs(snap.ErrorRate-0.2) > 0.01 {
		t.Errorf("expected error rate=0.2, got %f", snap.ErrorRate)
	}
}

func TestRollingWindow_RingBufferOverflow(t *testing.T) {
	w := NewRollingWindow(5)

	for i := 0; i < 10; i++ {
		w.Record(float64(i * 10))
	}

	if w.Size() != 5 {
		t.Errorf("expected size=5 (capped), got %d", w.Size())
	}
}

func TestRollingWindow_EmptySnapshot(t *testing.T) {
	w := NewRollingWindow(100)
	snap := w.Snapshot()

	if !snap.IsEmpty() {
		t.Error("expected empty snapshot")
	}
}

func TestRollingProvider_MinSamples(t *testing.T) {
	p := NewRollingProvider(100, 5)

	// Not enough samples
	for i := 0; i < 3; i++ {
		p.RecordLatency("op1", float64(i*10))
	}
	if _, ok := p.GetBaseline("op1"); ok {
		t.Error("should not return baseline with only 3 samples (min=5)")
	}

	// Add more
	for i := 0; i < 3; i++ {
		p.RecordLatency("op1", float64(i*10))
	}
	if _, ok := p.GetBaseline("op1"); !ok {
		t.Error("should return baseline with 6 samples (min=5)")
	}
}

func TestRollingProvider_GetSnapshot(t *testing.T) {
	p := NewRollingProvider(100, 1)

	p.RecordLatency("op1", 100)
	p.RecordLatency("op1", 200)

	snap, ok := p.GetSnapshot("op1")
	if !ok {
		t.Fatal("expected snapshot")
	}
	if snap.SampleCount != 2 {
		t.Errorf("expected 2 samples, got %d", snap.SampleCount)
	}

	_, ok = p.GetSnapshot("nonexistent")
	if ok {
		t.Error("should not find snapshot for nonexistent operation")
	}
}
