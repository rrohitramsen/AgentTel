package io.agenttel.agent.mcp;

import io.agenttel.agent.context.AgentContextProvider;
import io.agenttel.agent.remediation.RemediationExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and configures an MCP server pre-loaded with AgentTel tools.
 * This is the primary setup API for exposing telemetry to AI agents.
 *
 * <p>Default tools registered:
 * <ul>
 *   <li>{@code get_service_health} — Current health summary</li>
 *   <li>{@code get_incident_context} — Full incident diagnosis</li>
 *   <li>{@code list_remediation_actions} — Available fixes</li>
 *   <li>{@code execute_remediation} — Execute an approved fix</li>
 *   <li>{@code get_recent_agent_actions} — Agent action audit trail</li>
 *   <li>{@code get_slo_report} — SLO compliance report</li>
 *   <li>{@code get_trend_analysis} — Operation trend analysis</li>
 *   <li>{@code get_executive_summary} — High-level executive summary</li>
 *   <li>{@code get_cross_stack_context} — Correlated frontend + backend context</li>
 * </ul>
 */
public class AgentTelMcpServerBuilder {

    private int port = 8081;
    private AgentContextProvider contextProvider;
    private RemediationExecutor remediationExecutor;

    public AgentTelMcpServerBuilder port(int port) {
        this.port = port;
        return this;
    }

    public AgentTelMcpServerBuilder contextProvider(AgentContextProvider contextProvider) {
        this.contextProvider = contextProvider;
        return this;
    }

    public AgentTelMcpServerBuilder remediationExecutor(RemediationExecutor remediationExecutor) {
        this.remediationExecutor = remediationExecutor;
        return this;
    }

    /**
     * Builds and returns a configured MCP server (not yet started).
     */
    public McpServer build() {
        McpServer server = new McpServer(port);
        registerDefaultTools(server);
        return server;
    }

    private void registerDefaultTools(McpServer server) {
        // Tool: get_service_health
        server.registerTool(
                new McpToolDefinition(
                        "get_service_health",
                        "Get current service health summary including operation metrics, dependency status, and SLO budget",
                        Map.of("format", new McpToolDefinition.ParameterDefinition("string",
                                "Output format: 'text' (default) or 'json'")),
                        List.of()
                ),
                args -> {
                    String format = args.getOrDefault("format", "text");
                    if ("json".equals(format)) {
                        return contextProvider.getHealthSummaryJson();
                    }
                    return contextProvider.getHealthSummary();
                }
        );

        // Tool: get_incident_context
        server.registerTool(
                new McpToolDefinition(
                        "get_incident_context",
                        "Get complete incident context for a specific operation — what's happening, what changed, what's affected, and what to do",
                        Map.of("operation_name", new McpToolDefinition.ParameterDefinition("string",
                                "The operation name to diagnose")),
                        List.of("operation_name")
                ),
                args -> {
                    String opName = args.get("operation_name");
                    if (opName == null || opName.isEmpty()) {
                        return "Error: operation_name is required";
                    }
                    return contextProvider.getIncidentContext(opName);
                }
        );

        // Tool: list_remediation_actions
        server.registerTool(
                new McpToolDefinition(
                        "list_remediation_actions",
                        "List available remediation actions for a specific operation",
                        Map.of("operation_name", new McpToolDefinition.ParameterDefinition("string",
                                "The operation name to get actions for")),
                        List.of("operation_name")
                ),
                args -> {
                    String opName = args.get("operation_name");
                    if (opName == null || opName.isEmpty()) {
                        return "Error: operation_name is required";
                    }
                    return contextProvider.getAvailableActions(opName);
                }
        );

        // Tool: execute_remediation
        if (remediationExecutor != null) {
            Map<String, McpToolDefinition.ParameterDefinition> execParams = new LinkedHashMap<>();
            execParams.put("action_name", new McpToolDefinition.ParameterDefinition("string",
                    "Name of the remediation action to execute"));
            execParams.put("reason", new McpToolDefinition.ParameterDefinition("string",
                    "Reason for executing this action"));
            execParams.put("approved_by", new McpToolDefinition.ParameterDefinition("string",
                    "Who approved this action (required for actions needing approval)"));

            server.registerTool(
                    new McpToolDefinition(
                            "execute_remediation",
                            "Execute a remediation action. Actions requiring approval need the approved_by field.",
                            execParams,
                            List.of("action_name", "reason")
                    ),
                    args -> {
                        String actionName = args.get("action_name");
                        String reason = args.getOrDefault("reason", "Agent-initiated remediation");
                        String approvedBy = args.get("approved_by");

                        RemediationExecutor.RemediationResult result;
                        if (approvedBy != null && !approvedBy.isEmpty()) {
                            result = remediationExecutor.executeApproved(actionName, reason, approvedBy);
                        } else {
                            result = remediationExecutor.execute(actionName, reason);
                        }

                        return String.format("Action: %s\nSuccess: %s\nMessage: %s\nTimestamp: %s\nDuration: %dms",
                                result.actionName(), result.success(), result.message(),
                                result.timestamp(), result.durationMs());
                    }
            );
        }

        // Tool: get_recent_agent_actions
        server.registerTool(
                new McpToolDefinition(
                        "get_recent_agent_actions",
                        "Get the audit trail of recent agent decisions and actions",
                        Map.of(),
                        List.of()
                ),
                args -> contextProvider.getRecentActions()
        );

        // --- Reporting Tools ---

        // Tool: get_slo_report
        server.registerTool(
                new McpToolDefinition(
                        "get_slo_report",
                        "Get SLO status report across all tracked operations including budget remaining, burn rate, and compliance",
                        Map.of("format", new McpToolDefinition.ParameterDefinition("string",
                                "Output format: 'text' (default) or 'json'")),
                        List.of()
                ),
                args -> {
                    String format = args.getOrDefault("format", "text");
                    return contextProvider.getSloReport(format);
                }
        );

        // Tool: get_trend_analysis
        Map<String, McpToolDefinition.ParameterDefinition> trendParams = new LinkedHashMap<>();
        trendParams.put("operation_name", new McpToolDefinition.ParameterDefinition("string",
                "Operation name to analyze trends for"));
        trendParams.put("window_minutes", new McpToolDefinition.ParameterDefinition("string",
                "Time window in minutes (default: 30)"));

        server.registerTool(
                new McpToolDefinition(
                        "get_trend_analysis",
                        "Get trend analysis for an operation over a time window — latency, error rate, and throughput trends with direction indicators",
                        trendParams,
                        List.of("operation_name")
                ),
                args -> {
                    String opName = args.get("operation_name");
                    if (opName == null || opName.isEmpty()) {
                        return "Error: operation_name is required";
                    }
                    int windowMinutes;
                    try {
                        windowMinutes = Integer.parseInt(args.getOrDefault("window_minutes", "30"));
                    } catch (NumberFormatException e) {
                        windowMinutes = 30;
                    }
                    return contextProvider.getTrendAnalysis(opName, windowMinutes);
                }
        );

        // Tool: get_executive_summary
        server.registerTool(
                new McpToolDefinition(
                        "get_executive_summary",
                        "Get a high-level executive summary of service health, top issues, and SLO status — optimized for LLM context windows (~300 tokens)",
                        Map.of(),
                        List.of()
                ),
                args -> contextProvider.getExecutiveSummary()
        );

        // Tool: get_cross_stack_context
        server.registerTool(
                new McpToolDefinition(
                        "get_cross_stack_context",
                        "Get correlated frontend and backend context for an operation — traces the full user-to-database path when agenttel-web is connected",
                        Map.of("operation_name", new McpToolDefinition.ParameterDefinition("string",
                                "Backend operation name to get cross-stack context for")),
                        List.of("operation_name")
                ),
                args -> {
                    String opName = args.get("operation_name");
                    if (opName == null || opName.isEmpty()) {
                        return "Error: operation_name is required";
                    }
                    return contextProvider.getCrossStackContext(opName);
                }
        );
    }
}
