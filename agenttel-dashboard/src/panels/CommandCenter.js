import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useMemo, useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, radii, card as cardStyle, statusColors, riskColors } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
import { MetricCard } from '../components/MetricCard';
import { SloGauge } from '../components/SloGauge';
import { TraceLink } from '../components/TraceLink';
// ── Parsers ───────────────────────────────────────────────────
function parseHealth(raw) {
    const lines = raw.split('\n');
    let service = 'unknown';
    let status = 'unknown';
    const operations = [];
    const slos = [];
    let totalRequests = 0;
    for (const line of lines) {
        const serviceMatch = line.match(/^SERVICE:\s*(\S+)\s*\|\s*STATUS:\s*(\w+)/i);
        if (serviceMatch) {
            service = serviceMatch[1];
            status = serviceMatch[2].toLowerCase();
        }
        const opMatch = line.match(/^\s{2}(\S+(?:\s+\S+)*?):\s+err=([0-9.]+)%\s+p50=(\d+)ms\s+p99=(\d+)ms/);
        if (opMatch) {
            operations.push({ name: opMatch[1], errorRate: opMatch[2], p50: opMatch[3], p99: opMatch[4] });
        }
        const sloMatch = line.match(/^\s{2}(\S+):\s+budget=([0-9.]+)%\s+burn=([0-9.]+)x/);
        if (sloMatch) {
            slos.push({
                name: sloMatch[1],
                budget: parseFloat(sloMatch[2]),
                burnRate: parseFloat(sloMatch[3]),
                status: parseFloat(sloMatch[2]) > 50 ? 'healthy' : parseFloat(sloMatch[2]) > 20 ? 'at_risk' : 'violated',
            });
        }
        const reqMatch = line.match(/(\d+)\s+total\s+requests/i);
        if (reqMatch)
            totalRequests = parseInt(reqMatch[1]);
    }
    return { service, status, operations, slos, totalRequests };
}
function parseAgentActions(raw) {
    if (raw.includes('No recent agent actions'))
        return [];
    return raw.split('\n').filter((l) => l.trim().length > 0);
}
function parseSuggestions(raw) {
    try {
        const parsed = JSON.parse(raw);
        if (parsed.suggestions)
            return parsed.suggestions;
    }
    catch {
        // not JSON
    }
    return [];
}
// ── Agent Badge Component ─────────────────────────────────────
function AgentBadge({ agent }) {
    const labels = { monitor: 'Monitor Agent', instrument: 'Instrument Agent', mcp: 'MCP Server' };
    return (_jsx("span", { className: `agent-badge agent-badge-${agent}`, children: labels[agent] }));
}
// ── Section Header Component ──────────────────────────────────
function SectionHeader({ title, subtitle, linkTo, linkLabel }) {
    return (_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: spacing.lg }, children: [_jsxs("div", { children: [_jsx("h2", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, letterSpacing: '-0.01em' }, children: title }), subtitle && (_jsx("p", { style: { fontSize: font.size.sm, color: colors.textDim, marginTop: '2px' }, children: subtitle }))] }), linkTo && (_jsxs(Link, { to: linkTo, style: { fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }, children: [linkLabel || 'View all', " ", '\u2192'] }))] }));
}
// ── Main Component ────────────────────────────────────────────
export function CommandCenter() {
    const mcpClient = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const instrumentClient = useMemo(() => new McpClient(config.instrumentBaseUrl), []);
    // Data fetching
    const health = useMcpTool(mcpClient, 'get_service_health', {}, config.pollIntervals.commandCenter, parseHealth);
    const summary = useMcpTool(mcpClient, 'get_executive_summary', {}, config.pollIntervals.executiveSummary, (r) => r);
    const agentActions = useMcpTool(mcpClient, 'get_recent_agent_actions', {}, config.pollIntervals.monitorDecisions, parseAgentActions);
    const suggestions = useMcpTool(instrumentClient, 'suggest_improvements', { config_path: './agenttel.yml' }, config.pollIntervals.suggestions, parseSuggestions);
    // Agent manager status
    const [agentStatus, setAgentStatus] = useState(null);
    const fetchAgentStatus = useCallback(async () => {
        try {
            const res = await fetch(`${config.adminBaseUrl}/status`);
            if (res.ok)
                setAgentStatus(await res.json());
        }
        catch {
            // agent manager not running
        }
    }, []);
    useEffect(() => {
        fetchAgentStatus();
        const id = window.setInterval(fetchAgentStatus, config.pollIntervals.agentStatus);
        return () => window.clearInterval(id);
    }, [fetchAgentStatus]);
    // Derived metrics
    const avgError = health.data?.operations.length
        ? health.data.operations.reduce((s, o) => s + parseFloat(o.errorRate), 0) / health.data.operations.length
        : 0;
    const avgP50 = health.data?.operations.length
        ? Math.round(health.data.operations.reduce((s, o) => s + parseInt(o.p50), 0) / health.data.operations.length)
        : 0;
    const sloHealthy = health.data?.slos.filter((s) => s.status === 'healthy').length || 0;
    const sloTotal = health.data?.slos.length || 0;
    const activeAgents = [
        agentStatus?.monitor.running,
        agentStatus?.instrument.running,
        agentStatus?.backend.running,
    ].filter(Boolean).length;
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['3xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['3xl'], fontWeight: font.weight.bold, color: colors.text, letterSpacing: '-0.02em' }, children: "Command Center" }), _jsx("p", { style: { fontSize: font.size.lg, color: colors.textMuted, marginTop: spacing.xs }, children: "Agent-ready observability at a glance" })] }), _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.lg }, children: [agentStatus && (_jsxs("div", { style: {
                                    display: 'flex', alignItems: 'center', gap: spacing.sm,
                                    padding: `${spacing.xs} ${spacing.md}`,
                                    backgroundColor: colors.surfaceElevated,
                                    borderRadius: radii.md,
                                    border: `1px solid ${colors.border}`,
                                }, children: [_jsx("span", { style: {
                                            width: '6px', height: '6px', borderRadius: '50%',
                                            backgroundColor: activeAgents > 0 ? colors.success : colors.textDim,
                                            boxShadow: activeAgents > 0 ? `0 0 6px ${colors.success}` : 'none',
                                        } }), _jsxs("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: [activeAgents, "/3 agents active"] })] })), health.lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", new Date(health.lastUpdated).toLocaleTimeString()] }))] })] }), _jsx(SectionHeader, { title: "System Status", subtitle: "Live health, performance, and SLO compliance" }), _jsxs("div", { className: "grid-kpi", style: { marginBottom: spacing.xl }, children: [_jsx(MetricCard, { label: "Service Status", value: health.data?.status.toUpperCase() || '-', color: health.data ? statusColors[health.data.status] : undefined, subtitle: health.data?.service }), _jsx(MetricCard, { label: "Avg Latency", value: health.data ? `${avgP50}ms` : '-', color: avgP50 > 200 ? colors.warning : colors.text, subtitle: "p50 across operations" }), _jsx(MetricCard, { label: "Error Rate", value: health.data ? `${avgError.toFixed(2)}%` : '-', color: avgError > 1 ? colors.error : avgError > 0 ? colors.warning : colors.success, subtitle: "avg across operations" }), _jsx(MetricCard, { label: "SLO Health", value: sloTotal > 0 ? `${sloHealthy}/${sloTotal}` : '-', color: sloHealthy === sloTotal ? colors.success : colors.warning, subtitle: "targets met" })] }), _jsxs("div", { className: "grid-2", style: { marginBottom: spacing['3xl'] }, children: [_jsxs("div", { style: cardStyle, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }, children: [_jsx("span", { style: { fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }, children: "Operations" }), _jsxs(Link, { to: "/fleet", style: { fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }, children: ["Fleet ", '\u2192'] })] }), health.loading && !health.data && _jsx("div", { className: "skeleton", style: { height: '80px' } }), health.data?.operations.length === 0 && (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, padding: spacing.lg, textAlign: 'center' }, children: "No operations yet. Generate traffic to see data." })), health.data?.operations.map((op) => (_jsxs("div", { className: "row-hover", style: {
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                    padding: `${spacing.sm} ${spacing.md}`,
                                    borderBottom: `1px solid ${colors.borderSubtle}`,
                                }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm }, children: [_jsx(StatusBadge, { status: parseFloat(op.errorRate) > 1 ? 'degraded' : 'healthy', size: "sm" }), _jsx("span", { style: { fontSize: font.size.md, color: colors.text, fontFamily: font.mono }, children: op.name })] }), _jsxs("div", { style: { display: 'flex', gap: spacing.lg, alignItems: 'center' }, children: [_jsxs("span", { style: { fontSize: font.size.sm, color: colors.textMuted, fontFamily: font.mono }, children: ["p50: ", op.p50, "ms"] }), _jsxs("span", { style: { fontSize: font.size.sm, color: colors.textMuted, fontFamily: font.mono }, children: ["p99: ", op.p99, "ms"] }), _jsxs("span", { style: {
                                                    fontSize: font.size.sm, fontFamily: font.mono,
                                                    color: parseFloat(op.errorRate) > 1 ? colors.error : colors.textDim,
                                                }, children: ["err: ", op.errorRate, "%"] }), _jsx(TraceLink, { service: "payment-service", operation: op.name, label: "Traces" })] })] }, op.name))), health.error && (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, padding: spacing.lg, textAlign: 'center' }, children: "Waiting for service connection..." }))] }), _jsxs("div", { style: cardStyle, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }, children: [_jsx("span", { style: { fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }, children: "SLO Compliance" }), _jsxs(Link, { to: "/slo", style: { fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }, children: ["Details ", '\u2192'] })] }), health.loading && !health.data && _jsx("div", { className: "skeleton", style: { height: '80px' } }), _jsx("div", { style: { display: 'flex', flexWrap: 'wrap', gap: spacing.md }, children: health.data?.slos.map((s) => (_jsx(SloGauge, { name: s.name, budgetPercent: s.budget, burnRate: s.burnRate, status: s.status }, s.name))) }), health.data?.slos.length === 0 && (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, padding: spacing.lg, textAlign: 'center' }, children: "No SLO data available yet." }))] })] }), _jsx(SectionHeader, { title: "Agent Insights", subtitle: "What each agent discovered from your telemetry", linkTo: "/monitor", linkLabel: "Monitor details" }), _jsx("div", { className: "grid-3", style: { marginBottom: spacing.lg }, children: [
                    {
                        name: 'Monitor Agent',
                        badge: 'monitor',
                        running: agentStatus?.monitor.running ?? false,
                        description: 'Watches service health, detects anomalies, executes remediation',
                        detail: agentStatus?.monitor.running
                            ? `Running for ${formatUptime(agentStatus.monitor.uptime_seconds)}`
                            : 'Start from Agents panel with API key',
                    },
                    {
                        name: 'Instrument Agent',
                        badge: 'instrument',
                        running: agentStatus?.instrument.running ?? false,
                        description: 'Analyzes code, generates instrumentation, suggests improvements',
                        detail: agentStatus?.instrument.running
                            ? `${agentStatus.instrument.tools?.length || 0} tools available`
                            : 'Not connected',
                    },
                    {
                        name: 'Backend MCP',
                        badge: 'mcp',
                        running: agentStatus?.backend.running ?? false,
                        description: '9 telemetry tools for health, SLOs, incidents, and trends',
                        detail: agentStatus?.backend.running
                            ? `${agentStatus.backend.tools?.length || 0} tools registered`
                            : 'Not connected',
                    },
                ].map((agent) => (_jsxs("div", { className: "card-hover", style: cardStyle, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }, children: [_jsx(AgentBadge, { agent: agent.badge }), _jsx(StatusBadge, { status: agent.running ? 'running' : 'stopped', size: "sm" })] }), _jsx("div", { style: { fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text, marginBottom: spacing.xs }, children: agent.name }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.sm, lineHeight: 1.5 }, children: agent.description }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim }, children: agent.detail })] }, agent.name))) }), _jsxs("div", { style: { ...cardStyle, marginBottom: spacing['3xl'] }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm }, children: [_jsx("span", { style: { fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }, children: "Recent Activity" }), _jsx(AgentBadge, { agent: "monitor" })] }), _jsxs(Link, { to: "/monitor", style: { fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }, children: ["Full timeline ", '\u2192'] })] }), agentActions.loading && !agentActions.data && _jsx("div", { className: "skeleton", style: { height: '60px' } }), agentActions.data && agentActions.data.length > 0 ? (agentActions.data.slice(0, 5).map((line, i) => {
                        const isDetect = line.includes('DETECT') || line.includes('degraded') || line.includes('anomaly');
                        const isAction = line.includes('ACTION') || line.includes('RESOLVE') || line.includes('execute');
                        const isError = line.includes('ERROR') || line.includes('[err]');
                        return (_jsx("div", { className: "row-hover", style: {
                                padding: `${spacing.sm} ${spacing.md}`,
                                borderBottom: i < Math.min(agentActions.data.length, 5) - 1 ? `1px solid ${colors.borderSubtle}` : 'none',
                                fontSize: font.size.sm,
                                color: isError ? colors.error : isDetect ? colors.warning : isAction ? colors.success : colors.textMuted,
                                fontFamily: font.mono,
                                lineHeight: 1.5,
                            }, children: line }, i));
                    })) : (_jsxs("div", { style: { color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.lg }, children: ["No agent activity yet. Start the monitor from the", ' ', _jsx(Link, { to: "/agents", style: { color: colors.primaryLight, textDecoration: 'none' }, children: "Agents" }), ' ', "panel to see decisions here."] }))] }), _jsx(SectionHeader, { title: "Recommended Actions", subtitle: "Prioritized improvements from the instrument agent", linkTo: "/suggestions", linkLabel: "All suggestions" }), _jsxs("div", { style: { ...cardStyle, marginBottom: spacing['3xl'] }, children: [suggestions.loading && !suggestions.data && _jsx("div", { className: "skeleton", style: { height: '80px' } }), suggestions.data && suggestions.data.length > 0 ? (suggestions.data.slice(0, 4).map((item, idx) => (_jsxs("div", { className: "row-hover", style: {
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            padding: `${spacing.md} ${spacing.md}`,
                            borderBottom: idx < Math.min(suggestions.data.length, 4) - 1 ? `1px solid ${colors.borderSubtle}` : 'none',
                            borderLeft: `3px solid ${riskColors[item.risk_level] || colors.textMuted}`,
                        }, children: [_jsxs("div", { style: { flex: 1 }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: '2px' }, children: [_jsx("span", { style: { fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }, children: item.trigger.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()) }), _jsx(AgentBadge, { agent: "instrument" }), _jsx("span", { style: {
                                                    fontSize: '10px', padding: '1px 6px', borderRadius: '3px',
                                                    backgroundColor: `${riskColors[item.risk_level] || colors.textMuted}15`,
                                                    color: riskColors[item.risk_level] || colors.textMuted,
                                                    fontWeight: font.weight.medium,
                                                }, children: item.risk_level })] }), _jsxs("div", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: [item.target, " \u00B7 ", item.reasoning] })] }), item.auto_applicable && (_jsx(Link, { to: "/suggestions", className: "btn btn-outline", style: { textDecoration: 'none', fontSize: font.size.sm, padding: '4px 12px' }, children: "Review" }))] }, idx)))) : suggestions.error ? (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.lg }, children: "Connect instrument agent to see recommendations." })) : (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.lg }, children: "No improvement suggestions at this time." }))] }), _jsxs("div", { className: "grid-2", style: { marginBottom: spacing['3xl'] }, children: [_jsxs("div", { children: [_jsx(SectionHeader, { title: "Quick Actions" }), _jsxs("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.md }, children: [_jsx(Link, { to: "/traffic", style: { textDecoration: 'none' }, children: _jsxs("div", { className: "card-hover", style: {
                                                ...cardStyle,
                                                display: 'flex', alignItems: 'center', gap: spacing.md,
                                                background: `linear-gradient(135deg, ${colors.primaryDim}, transparent)`,
                                                borderColor: 'rgba(139, 124, 246, 0.12)',
                                            }, children: [_jsx("div", { style: {
                                                        width: '36px', height: '36px', borderRadius: radii.md,
                                                        backgroundColor: colors.primaryDim, display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                        fontSize: '16px',
                                                    }, children: '\u25B6' }), _jsxs("div", { children: [_jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }, children: "Generate End-to-End Traffic" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Browser spans + backend requests + distributed traces" })] })] }) }), _jsx(Link, { to: "/agents", style: { textDecoration: 'none' }, children: _jsxs("div", { className: "card-hover", style: {
                                                ...cardStyle,
                                                display: 'flex', alignItems: 'center', gap: spacing.md,
                                            }, children: [_jsx("div", { style: {
                                                        width: '36px', height: '36px', borderRadius: radii.md,
                                                        backgroundColor: colors.infoDim, display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                        fontSize: '16px',
                                                    }, children: '\u2699' }), _jsxs("div", { children: [_jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }, children: "Manage Agents" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Start/stop monitor, configure API key, view logs" })] })] }) }), _jsx(Link, { to: "/cross-stack", style: { textDecoration: 'none' }, children: _jsxs("div", { className: "card-hover", style: {
                                                ...cardStyle,
                                                display: 'flex', alignItems: 'center', gap: spacing.md,
                                            }, children: [_jsx("div", { style: {
                                                        width: '36px', height: '36px', borderRadius: radii.md,
                                                        backgroundColor: colors.successDim, display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                        fontSize: '16px',
                                                    }, children: '\u2194' }), _jsxs("div", { children: [_jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }, children: "Cross-Stack View" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Frontend-backend correlation with AgentTel enrichment" })] })] }) })] })] }), _jsxs("div", { children: [_jsx(SectionHeader, { title: "Executive Summary", linkTo: "/summary", linkLabel: "Full view" }), _jsxs("div", { style: cardStyle, children: [summary.loading && !summary.data && _jsx("div", { className: "skeleton", style: { height: '150px' } }), summary.data ? (_jsx("pre", { style: {
                                            fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted,
                                            whiteSpace: 'pre-wrap', lineHeight: 1.6, margin: 0,
                                            maxHeight: '240px', overflow: 'auto',
                                        }, children: summary.data })) : summary.error ? (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.lg }, children: "Generate traffic to see executive summary." })) : null] })] })] }), _jsx(SectionHeader, { title: "What AgentTel Adds", subtitle: "Standard OpenTelemetry vs AgentTel-enriched telemetry" }), _jsxs("div", { className: "grid-2", style: { marginBottom: spacing['2xl'] }, children: [_jsxs("div", { style: {
                            ...cardStyle,
                            borderColor: 'rgba(255, 255, 255, 0.05)',
                            opacity: 0.85,
                        }, children: [_jsx("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.lg }, children: _jsx("span", { style: {
                                        fontSize: font.size.xs, padding: '2px 8px', borderRadius: '4px',
                                        backgroundColor: 'rgba(255, 255, 255, 0.05)', color: colors.textMuted,
                                        fontWeight: font.weight.semibold, textTransform: 'uppercase', letterSpacing: '0.5px',
                                    }, children: "Standard OTel" }) }), _jsxs("div", { style: { fontFamily: font.mono, fontSize: font.size.xs, lineHeight: 1.8, color: colors.textDim }, children: [_jsx("div", { children: _jsx(Attr, { name: "service.name", value: "payment-service" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.method", value: "POST" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.route", value: "/api/payments" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.status_code", value: "200" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.response_time_ms", value: "45" }) }), _jsx("div", { style: { marginTop: spacing.md, color: colors.textDim, fontSize: font.size.xs, fontFamily: font.sans }, children: "Raw spans with basic request metadata. No context for automated decisions." })] })] }), _jsxs("div", { style: {
                            ...cardStyle,
                            borderColor: 'rgba(139, 124, 246, 0.2)',
                            background: `linear-gradient(135deg, ${colors.surface}, rgba(139, 124, 246, 0.03))`,
                        }, children: [_jsx("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.lg }, children: _jsx("span", { style: {
                                        fontSize: font.size.xs, padding: '2px 8px', borderRadius: '4px',
                                        backgroundColor: colors.primaryDim, color: colors.primaryLight,
                                        fontWeight: font.weight.semibold, textTransform: 'uppercase', letterSpacing: '0.5px',
                                    }, children: "AgentTel Enriched" }) }), _jsxs("div", { style: { fontFamily: font.mono, fontSize: font.size.xs, lineHeight: 1.8 }, children: [_jsx("div", { children: _jsx(Attr, { name: "service.name", value: "payment-service" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.method", value: "POST" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.route", value: "/api/payments" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.status_code", value: "200" }) }), _jsx("div", { children: _jsx(Attr, { name: "http.response_time_ms", value: "45" }) }), _jsxs("div", { style: { borderTop: `1px solid ${colors.borderSubtle}`, marginTop: spacing.sm, paddingTop: spacing.sm }, children: [_jsx("div", { children: _jsx(Attr, { name: "agenttel.baseline.p50_ms", value: "32", highlight: true }) }), _jsx("div", { children: _jsx(Attr, { name: "agenttel.baseline.p99_ms", value: "120", highlight: true }) }), _jsx("div", { children: _jsx(Attr, { name: "agenttel.anomaly.score", value: "0.12", highlight: true }) }), _jsx("div", { children: _jsx(Attr, { name: "agenttel.anomaly.status", value: "normal", highlight: true }) }), _jsx("div", { children: _jsx(Attr, { name: "agenttel.decision.suggested_action", value: "none", highlight: true }) }), _jsx("div", { children: _jsx(Attr, { name: "agenttel.slo.budget_remaining", value: "98.5%", highlight: true }) }), _jsx("div", { children: _jsx(Attr, { name: "agenttel.topology.upstream", value: "checkout-web", highlight: true }) })] }), _jsx("div", { style: { marginTop: spacing.md, color: colors.textSecondary, fontSize: font.size.xs, fontFamily: font.sans }, children: "Enriched with baselines, anomaly detection, SLO tracking, and topology \u2014 everything agents need for autonomous decisions." })] })] })] })] }));
}
// ── Helper: Attribute display ─────────────────────────────────
function Attr({ name, value, highlight }) {
    return (_jsxs("span", { children: [_jsx("span", { style: { color: highlight ? colors.primaryLight : colors.textDim }, children: name }), _jsx("span", { style: { color: colors.textDim }, children: ": " }), _jsx("span", { style: { color: highlight ? colors.primary : colors.textMuted }, children: value })] }));
}
function formatUptime(seconds) {
    if (!seconds)
        return '-';
    if (seconds < 60)
        return `${seconds}s`;
    if (seconds < 3600)
        return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}
