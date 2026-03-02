import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, statusColors } from '../styles/theme';
import { SloGauge } from '../components/SloGauge';
import { StatusBadge } from '../components/StatusBadge';
function parseSloReport(raw) {
    const slos = [];
    const lines = raw.split('\n');
    let currentName = '';
    let currentStatus = '';
    for (const line of lines) {
        // "  [HEALTHY] payment-availability"
        const nameMatch = line.match(/^\s*\[(\w+)\]\s+(.+)/);
        if (nameMatch) {
            currentStatus = nameMatch[1].toLowerCase();
            currentName = nameMatch[2].trim();
            continue;
        }
        // "    Target: 99.90%  Actual: 100.00%  Budget: 100.00%  Burn: 0.00x  Requests: 124  Failed: 0"
        if (currentName) {
            const detailMatch = line.match(/Target:\s*([0-9.]+)%\s+Actual:\s*([0-9.]+)%\s+Budget:\s*([0-9.]+)%\s+Burn:\s*([0-9.]+)x/);
            if (detailMatch) {
                slos.push({
                    name: currentName,
                    target: `${detailMatch[1]}%`,
                    actual: `${detailMatch[2]}%`,
                    budget: parseFloat(detailMatch[3]),
                    burnRate: parseFloat(detailMatch[4]),
                    status: currentStatus,
                });
                currentName = '';
            }
        }
    }
    return { slos, raw };
}
export function SloCompliance() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_slo_report', { format: 'text' }, config.pollIntervals.sloCompliance, parseSloReport);
    const [showRaw, setShowRaw] = useState(false);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "SLO Compliance" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Service level objective budgets, burn rates, and compliance status" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '120px' } }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: error })), data && (_jsxs(_Fragment, { children: [data.slos.length > 0 ? (_jsx("div", { className: "grid-auto", style: { marginBottom: spacing['2xl'] }, children: data.slos.map((slo) => (_jsx(SloGauge, { name: slo.name, budgetPercent: slo.budget, burnRate: slo.burnRate, status: slo.status }, slo.name))) })) : (_jsx("div", { style: { ...cardStyle, marginBottom: spacing['2xl'] }, children: _jsx("pre", { style: { fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap', margin: 0, lineHeight: 1.6 }, children: data.raw }) })), data.slos.length > 0 && (_jsxs("div", { style: { ...cardStyle, marginBottom: spacing['2xl'] }, children: [_jsx("div", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }, children: "Detail" }), _jsxs("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: font.size.md }, children: [_jsx("thead", { children: _jsx("tr", { children: ['SLO', 'Status', 'Target', 'Actual', 'Budget', 'Burn Rate'].map((h) => (_jsx("th", { style: {
                                                    padding: `${spacing.sm} ${spacing.md}`,
                                                    textAlign: 'left',
                                                    color: colors.textDim,
                                                    borderBottom: `1px solid ${colors.border}`,
                                                    fontWeight: font.weight.semibold,
                                                    fontSize: font.size.xs,
                                                    textTransform: 'uppercase',
                                                }, children: h }, h))) }) }), _jsx("tbody", { children: data.slos.map((slo) => (_jsxs("tr", { className: "row-hover", style: { borderBottom: `1px solid ${colors.border}` }, children: [_jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontWeight: font.weight.medium }, children: slo.name }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}` }, children: _jsx(StatusBadge, { status: slo.status, size: "sm" }) }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }, children: slo.target }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontFamily: font.mono }, children: slo.actual }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}` }, children: _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm }, children: [_jsx("div", { className: "progress-bar", style: { width: '60px' }, children: _jsx("div", { className: "progress-fill", style: { width: `${Math.min(slo.budget, 100)}%`, background: statusColors[slo.status] || colors.primary } }) }), _jsxs("span", { style: { fontFamily: font.mono, fontSize: font.size.sm, color: colors.text }, children: [slo.budget.toFixed(1), "%"] })] }) }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: slo.burnRate > 1 ? colors.warning : colors.textMuted, fontFamily: font.mono, fontWeight: font.weight.semibold }, children: [slo.burnRate.toFixed(1), "x"] })] }, slo.name))) })] })] })), _jsxs("button", { className: "collapsible-header", onClick: () => setShowRaw(!showRaw), children: [showRaw ? '\u25BC' : '\u25B6', " Raw MCP Response"] }), showRaw && (_jsx("pre", { style: { ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap' }, children: data.raw }))] }))] }));
}
