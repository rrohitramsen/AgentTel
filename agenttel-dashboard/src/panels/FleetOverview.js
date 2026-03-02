import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, statusColors } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
import { MetricCard } from '../components/MetricCard';
import { TraceLink } from '../components/TraceLink';
function parseHealth(raw) {
    const lines = raw.split('\n');
    let service = 'unknown';
    let status = 'unknown';
    const operations = [];
    const dependencies = [];
    for (const line of lines) {
        // SERVICE: payments-platform | STATUS: HEALTHY | 2026-...
        const serviceMatch = line.match(/^SERVICE:\s*(\S+)\s*\|\s*STATUS:\s*(\w+)/i);
        if (serviceMatch) {
            service = serviceMatch[1];
            status = serviceMatch[2].toLowerCase();
        }
        // "  POST /api/payments: err=0.0% p50=2ms p99=78ms" or "  GET: err=0.0% p50=20ms p99=59ms"
        const opMatch = line.match(/^\s{2}(\S+(?:\s+\S+)*?):\s+err=([0-9.]+)%\s+p50=(\d+)ms\s+p99=(\d+)ms/);
        if (opMatch) {
            operations.push({
                name: opMatch[1],
                status: parseFloat(opMatch[2]) > 1 ? 'degraded' : 'healthy',
                errorRate: opMatch[2],
                p50: opMatch[3],
                p99: opMatch[4],
                throughput: '-',
            });
        }
    }
    return { service, status, operations, dependencies, raw };
}
export function FleetOverview() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_service_health', { format: 'text' }, config.pollIntervals.fleetOverview, parseHealth);
    const [showRaw, setShowRaw] = useState(false);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Fleet Health" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Service health, operations, and dependencies" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '200px' } }), error && (_jsxs("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: [_jsx("strong", { children: "Connection Error:" }), " ", error, _jsxs("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.sm }, children: ["Make sure the MCP server is running at ", config.mcpBaseUrl] })] })), data && (_jsxs(_Fragment, { children: [_jsxs("div", { className: "card-hover", style: {
                            ...cardStyle,
                            borderLeft: `3px solid ${statusColors[data.status] || colors.textMuted}`,
                            marginBottom: spacing['2xl'],
                        }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.lg }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md }, children: [_jsx("span", { style: { fontSize: font.size.xl, fontWeight: font.weight.bold, color: colors.text }, children: data.service }), _jsx(StatusBadge, { status: data.status })] }), _jsx(TraceLink, { service: data.service, label: "View Traces" })] }), _jsxs("div", { className: "grid-kpi", children: [_jsx(MetricCard, { label: "Operations", value: String(data.operations.length || 'N/A'), subtitle: "tracked endpoints" }), _jsx(MetricCard, { label: "Dependencies", value: String(data.dependencies.length || 'N/A'), subtitle: "external services" }), _jsx(MetricCard, { label: "Avg Error Rate", value: data.operations.length
                                            ? `${(data.operations.reduce((s, o) => s + parseFloat(o.errorRate), 0) / data.operations.length).toFixed(2)}%`
                                            : 'N/A', color: data.operations.some((o) => parseFloat(o.errorRate) > 1) ? colors.error : colors.success })] })] }), data.operations.length > 0 && (_jsxs("div", { style: { ...cardStyle, marginBottom: spacing['2xl'] }, children: [_jsx("div", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }, children: "Operations" }), _jsx("div", { style: { overflowX: 'auto' }, children: _jsxs("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: font.size.md }, children: [_jsx("thead", { children: _jsx("tr", { children: ['Operation', 'Status', 'Error Rate', 'P50', 'P99', 'Throughput', ''].map((h) => (_jsx("th", { style: {
                                                        padding: `${spacing.sm} ${spacing.md}`,
                                                        textAlign: 'left',
                                                        color: colors.textDim,
                                                        borderBottom: `1px solid ${colors.border}`,
                                                        fontWeight: font.weight.semibold,
                                                        fontSize: font.size.xs,
                                                        textTransform: 'uppercase',
                                                        letterSpacing: '0.5px',
                                                    }, children: h }, h))) }) }), _jsx("tbody", { children: data.operations.map((op) => (_jsxs("tr", { className: "row-hover", style: { borderBottom: `1px solid ${colors.border}` }, children: [_jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontFamily: font.mono, fontSize: font.size.sm }, children: op.name }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}` }, children: _jsx(StatusBadge, { status: op.status, size: "sm" }) }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: parseFloat(op.errorRate) > 1 ? colors.error : colors.text, fontFamily: font.mono }, children: [op.errorRate, "%"] }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }, children: [op.p50, "ms"] }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }, children: [op.p99, "ms"] }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }, children: [op.throughput, " ops"] }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}` }, children: _jsx(TraceLink, { service: "payment-service", operation: op.name, label: "Traces" }) })] }, op.name))) })] }) })] })), data.dependencies.length > 0 && (_jsxs("div", { style: { ...cardStyle, marginBottom: spacing['2xl'] }, children: [_jsx("div", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }, children: "Dependencies" }), _jsx("div", { className: "grid-auto", children: data.dependencies.map((dep) => (_jsxs("div", { className: "card-hover", style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.sm }, children: dep.name }), _jsxs("div", { style: { display: 'flex', gap: spacing.xl, fontSize: font.size.sm, color: colors.textMuted }, children: [_jsxs("span", { children: ["err: ", _jsxs("span", { style: { color: parseFloat(dep.errorRate) > 1 ? colors.error : colors.text }, children: [dep.errorRate, "%"] })] }), _jsxs("span", { children: ["lat: ", dep.latency, "ms"] }), _jsxs("span", { children: [dep.calls, " calls"] })] })] }, dep.name))) })] })), _jsxs("button", { className: "collapsible-header", onClick: () => setShowRaw(!showRaw), children: [showRaw ? '\u25BC' : '\u25B6', " Raw MCP Response"] }), showRaw && (_jsx("pre", { style: { ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }, children: data.raw }))] }))] }));
}
