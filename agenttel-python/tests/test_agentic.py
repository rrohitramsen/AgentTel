"""Tests for agentic observability."""

from agenttel.agentic.cost import AgentCostAggregator
from agenttel.agentic.quality import LoopDetector, QualityTracker
from agenttel.agentic.guardrail import GuardrailRecorder
from agenttel.enums import GuardrailAction


class TestAgentCostAggregator:
    def test_record_and_summary(self):
        agg = AgentCostAggregator()
        agg.record(input_tokens=1000, output_tokens=500, cost_usd=0.01)
        agg.record(input_tokens=2000, output_tokens=1000, cost_usd=0.02)

        summary = agg.summary()
        assert summary["total_input_tokens"] == 3000
        assert summary["total_output_tokens"] == 1500
        assert summary["llm_calls"] == 2
        assert summary["total_cost_usd"] == 0.03

    def test_reset(self):
        agg = AgentCostAggregator()
        agg.record(input_tokens=1000, output_tokens=500)
        agg.reset()
        assert agg.summary()["llm_calls"] == 0


class TestLoopDetector:
    def test_no_loop(self):
        detector = LoopDetector(max_repetitions=3)
        for action in ["search", "analyze", "report"]:
            result = detector.record_action(action)
            assert not result.detected

    def test_detect_repeated_action(self):
        detector = LoopDetector(max_repetitions=3)
        detector.record_action("search")
        detector.record_action("search")
        result = detector.record_action("search")
        assert result.detected
        assert result.pattern == "search"

    def test_reset(self):
        detector = LoopDetector(max_repetitions=3)
        detector.record_action("search")
        detector.record_action("search")
        detector.reset()
        result = detector.record_action("search")
        assert not result.detected


class TestQualityTracker:
    def test_goal_tracking(self):
        tracker = QualityTracker()
        tracker.record_goal(True)
        tracker.record_goal(True)
        tracker.record_goal(False)

        summary = tracker.summary()
        assert summary["goals_attempted"] == 3
        assert summary["goals_achieved"] == 2
        assert abs(summary["success_rate"] - 2 / 3) < 0.01


class TestGuardrailRecorder:
    def test_record_event(self):
        recorder = GuardrailRecorder()
        event = recorder.record(
            name="content-filter",
            action=GuardrailAction.BLOCK,
            reason="PII detected",
        )
        assert event.name == "content-filter"
        assert event.action == GuardrailAction.BLOCK
        assert recorder.get_block_count() == 1

    def test_multiple_events(self):
        recorder = GuardrailRecorder()
        recorder.record("filter-1", GuardrailAction.BLOCK, "reason 1")
        recorder.record("filter-2", GuardrailAction.WARN, "reason 2")
        recorder.record("filter-3", GuardrailAction.LOG, "reason 3")

        assert len(recorder.get_events()) == 3
        assert recorder.get_block_count() == 1
