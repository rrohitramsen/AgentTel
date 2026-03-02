from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field

from ..mcp.client import McpClient
from ..mcp.models import (
    ExecutiveSummary,
    IncidentContext,
    OperationHealth,
    RemediationAction,
    ServiceHealth,
    SloReport,
)
from .watcher import WatchResult

logger = logging.getLogger(__name__)


@dataclass
class InvestigationContext:
    """Aggregated context gathered during investigation."""
    watch_result: WatchResult
    incident_contexts: list[IncidentContext] = field(default_factory=list)
    slo_reports: list[SloReport] = field(default_factory=list)
    executive_summary: ExecutiveSummary = field(default_factory=ExecutiveSummary)
    available_actions: list[RemediationAction] = field(default_factory=list)

    def to_text(self) -> str:
        """Serialize the investigation context to a human-readable text block."""
        parts: list[str] = []

        # Overall health
        health = self.watch_result.service_health
        parts.append(f"=== Service Health: {health.service} ===")
        parts.append(f"Overall status: {health.overall_status}")
        if health.timestamp:
            parts.append(f"Timestamp: {health.timestamp}")
        parts.append("")

        # Anomalous operations
        if self.watch_result.anomalous_operations:
            parts.append("=== Anomalous Operations ===")
            for op in self.watch_result.anomalous_operations:
                parts.append(
                    f"- {op.operation}: status={op.status}, "
                    f"error_rate={op.error_rate:.4f}, "
                    f"p99_latency={op.latency_p99_ms:.1f}ms, "
                    f"throughput={op.throughput_rpm:.1f}rpm"
                )
                if op.details:
                    parts.append(f"  Details: {op.details}")
            parts.append("")

        # Incident contexts
        for ctx in self.incident_contexts:
            parts.append(f"=== Incident Context: {ctx.operation} ===")
            parts.append(f"Summary: {ctx.summary}")
            if ctx.related_changes:
                parts.append(f"Related changes: {', '.join(ctx.related_changes)}")
            if ctx.error_samples:
                parts.append("Error samples:")
                for sample in ctx.error_samples[:5]:
                    parts.append(f"  - {sample}")
            if ctx.timeline:
                parts.append("Timeline:")
                for event in ctx.timeline[:10]:
                    parts.append(f"  - {event}")
            parts.append("")

        # SLO reports
        for slo in self.slo_reports:
            parts.append(f"=== SLO Report: {slo.operation} ===")
            parts.append(
                f"Target: {slo.slo_target}, Current: {slo.current_value}, "
                f"Budget remaining: {slo.budget_remaining}, Status: {slo.status}"
            )
            if slo.details:
                parts.append(f"Details: {slo.details}")
            parts.append("")

        # Executive summary
        if self.executive_summary.summary:
            parts.append("=== Executive Summary ===")
            parts.append(self.executive_summary.summary)
            if self.executive_summary.top_issues:
                parts.append("Top issues:")
                for issue in self.executive_summary.top_issues:
                    parts.append(f"  - {issue}")
            if self.executive_summary.recommendations:
                parts.append("Recommendations:")
                for rec in self.executive_summary.recommendations:
                    parts.append(f"  - {rec}")
            parts.append("")

        # Available actions
        if self.available_actions:
            parts.append("=== Available Remediation Actions ===")
            for action in self.available_actions:
                parts.append(
                    f"- [{action.id}] {action.name} ({action.action_type}): "
                    f"{action.description} [risk: {action.risk_level}]"
                )
            parts.append("")

        return "\n".join(parts)


class Investigator:
    """Gathers full context when degradation is detected."""

    def __init__(self, mcp_client: McpClient) -> None:
        self._mcp = mcp_client

    async def investigate(self, watch_result: WatchResult) -> InvestigationContext:
        """Gather incident context for all anomalous operations in parallel."""
        context = InvestigationContext(watch_result=watch_result)

        anomalous_ops = watch_result.anomalous_operations
        operation_names = [op.operation for op in anomalous_ops if op.operation]

        if not operation_names:
            logger.info("No specific anomalous operations to investigate")
            return context

        logger.info("Investigating %d anomalous operations: %s", len(operation_names), operation_names)

        # Gather per-operation data in parallel
        incident_tasks = [
            self._mcp.get_incident_context(op) for op in operation_names
        ]
        slo_tasks = [
            self._mcp.get_slo_report(op) for op in operation_names
        ]
        action_tasks = [
            self._mcp.list_remediation_actions(op) for op in operation_names
        ]

        # Also get the executive summary
        exec_task = self._mcp.get_executive_summary()

        all_results = await asyncio.gather(
            asyncio.gather(*incident_tasks, return_exceptions=True),
            asyncio.gather(*slo_tasks, return_exceptions=True),
            asyncio.gather(*action_tasks, return_exceptions=True),
            exec_task,
            return_exceptions=True,
        )

        # Unpack incident contexts
        if not isinstance(all_results[0], BaseException):
            for result in all_results[0]:
                if not isinstance(result, BaseException):
                    context.incident_contexts.append(result)
                else:
                    logger.warning("Failed to get incident context: %s", result)

        # Unpack SLO reports
        if not isinstance(all_results[1], BaseException):
            for result in all_results[1]:
                if not isinstance(result, BaseException):
                    context.slo_reports.append(result)
                else:
                    logger.warning("Failed to get SLO report: %s", result)

        # Unpack remediation actions
        if not isinstance(all_results[2], BaseException):
            for result in all_results[2]:
                if not isinstance(result, BaseException) and isinstance(result, list):
                    context.available_actions.extend(result)
                elif isinstance(result, BaseException):
                    logger.warning("Failed to list remediation actions: %s", result)

        # Executive summary
        if not isinstance(all_results[3], BaseException):
            context.executive_summary = all_results[3]
        else:
            logger.warning("Failed to get executive summary: %s", all_results[3])

        logger.info(
            "Investigation complete: %d incident contexts, %d SLO reports, %d actions",
            len(context.incident_contexts),
            len(context.slo_reports),
            len(context.available_actions),
        )

        return context
