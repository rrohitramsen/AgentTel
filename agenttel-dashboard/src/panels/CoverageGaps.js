import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, riskColors } from '../styles/theme';
import { detectFeedbackEvents } from '../feedback/detector';
import { triggerLabels } from '../feedback/types';
function parseGaps(raw) {
    const events = detectFeedbackEvents(raw, '');
    return { events, healthRaw: raw };
}
export function CoverageGaps() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_service_health', { format: 'text' }, config.pollIntervals.coverageGaps, parseGaps);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Coverage Gaps" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "What is NOT instrumented \u2014 gaps that limit autonomous operation" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '150px' } }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: error })), data && (_jsx(_Fragment, { children: data.events.length > 0 ? (_jsx("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.md }, children: data.events.map((event, idx) => (_jsxs("div", { className: "card-hover", style: {
                            ...cardStyle,
                            borderLeft: `3px solid ${riskColors[event.riskLevel]}`,
                        }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }, children: [_jsx("span", { style: { fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }, children: triggerLabels[event.trigger] }), _jsxs("span", { style: {
                                            fontSize: '10px',
                                            padding: '2px 8px',
                                            borderRadius: '4px',
                                            backgroundColor: `${riskColors[event.riskLevel]}22`,
                                            color: riskColors[event.riskLevel],
                                            fontWeight: font.weight.semibold,
                                            textTransform: 'uppercase',
                                        }, children: [event.riskLevel, " risk"] })] }), _jsxs("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.xs }, children: ["Target: ", _jsx("span", { style: { color: colors.text, fontFamily: font.mono }, children: event.target })] }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: event.reasoning })] }, idx))) })) : (_jsxs("div", { style: { ...cardStyle, textAlign: 'center', padding: spacing['3xl'], color: colors.textDim }, children: [_jsx("div", { style: { fontSize: '32px', marginBottom: spacing.md }, children: '\u2705' }), _jsx("div", { style: { fontSize: font.size.md }, children: "No coverage gaps detected" }), _jsx("div", { style: { fontSize: font.size.sm, marginTop: spacing.xs }, children: "All operations have baselines, runbooks, and health checks" })] })) }))] }));
}
