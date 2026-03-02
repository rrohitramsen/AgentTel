import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, riskColors } from '../styles/theme';
import { triggerLabels, riskLabels } from '../feedback/types';
function parseSuggestions(raw) {
    try {
        const parsed = JSON.parse(raw);
        if (parsed.suggestions) {
            return {
                suggestions: parsed.suggestions.map((s) => ({
                    trigger: s.trigger,
                    risk_level: s.risk_level,
                    target: s.target,
                    current_value: s.current_value,
                    suggested_value: s.suggested_value,
                    reasoning: s.reasoning,
                    auto_applicable: s.auto_applicable,
                })),
                raw,
            };
        }
    }
    catch {
        // Fall through
    }
    return { suggestions: [], raw };
}
export function Suggestions() {
    const instrumentClient = useMemo(() => new McpClient(config.instrumentBaseUrl), []);
    const { data, loading, error, lastUpdated, refresh } = useMcpTool(instrumentClient, 'suggest_improvements', { config_path: './agenttel.yml' }, config.pollIntervals.suggestions, parseSuggestions);
    const [applyResult, setApplyResult] = useState(null);
    const [applying, setApplying] = useState(null);
    const handleApply = async (event) => {
        setApplying(event.target);
        try {
            const result = await instrumentClient.callTool('suggest_improvements', {
                config_path: './agenttel.yml',
                trigger: event.trigger,
                target: event.target,
            });
            setApplyResult(result);
            refresh();
        }
        catch (e) {
            setApplyResult(`Error: ${e instanceof Error ? e.message : 'Unknown'}`);
        }
        finally {
            setApplying(null);
        }
    };
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Suggestions" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Feedback engine recommendations to improve telemetry coverage" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '150px' } }), error && (_jsxs("div", { style: { ...cardStyle, borderColor: colors.error, marginBottom: spacing.lg }, children: [_jsx("div", { style: { color: colors.error, fontWeight: font.weight.semibold }, children: "Instrumentation Server" }), _jsx("div", { style: { color: colors.error, fontSize: font.size.sm, marginTop: spacing.xs }, children: error }), _jsxs("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.sm }, children: ["Make sure the instrument MCP server is running at ", config.instrumentBaseUrl] })] })), applyResult && (_jsxs("div", { style: {
                    ...cardStyle,
                    borderColor: colors.info,
                    marginBottom: spacing.lg,
                    animation: 'fadeIn 0.2s ease-out',
                }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }, children: [_jsx("span", { style: { fontSize: font.size.sm, fontWeight: font.weight.semibold, color: colors.info }, children: "Apply Result" }), _jsx("button", { className: "collapsible-header", onClick: () => setApplyResult(null), style: { padding: 0, fontSize: font.size.sm }, children: "Dismiss" })] }), _jsx("pre", { style: { margin: 0, whiteSpace: 'pre-wrap', fontFamily: font.mono, fontSize: font.size.sm, color: colors.text }, children: applyResult })] })), data && (_jsx("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.md }, children: data.suggestions.length > 0 ? (data.suggestions.map((event, idx) => {
                    const riskLevel = event.risk_level || 'medium';
                    const trigger = event.trigger || 'unknown';
                    return (_jsxs("div", { className: "card-hover", style: {
                            ...cardStyle,
                            borderLeft: `3px solid ${riskColors[riskLevel] || colors.textMuted}`,
                        }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }, children: [_jsx("span", { style: { fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }, children: triggerLabels[trigger] || trigger }), _jsxs("div", { style: { display: 'flex', gap: spacing.sm, alignItems: 'center' }, children: [_jsx("span", { style: {
                                                    fontSize: '10px',
                                                    padding: '2px 8px',
                                                    borderRadius: '4px',
                                                    backgroundColor: `${riskColors[riskLevel] || colors.textMuted}22`,
                                                    color: riskColors[riskLevel] || colors.textMuted,
                                                    fontWeight: font.weight.semibold,
                                                    textTransform: 'uppercase',
                                                }, children: riskLabels[riskLevel] || riskLevel }), event.auto_applicable && (_jsx("button", { className: "btn btn-primary", onClick: () => handleApply(event), disabled: applying === event.target, style: { padding: '4px 12px', fontSize: '11px' }, children: applying === event.target ? 'Applying...' : 'Apply' }))] })] }), _jsxs("div", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: ["Target: ", _jsx("span", { style: { color: colors.text, fontFamily: font.mono }, children: event.target })] }), event.current_value && (_jsxs("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: [_jsx("span", { style: { fontFamily: font.mono }, children: event.current_value }), event.suggested_value && (_jsxs(_Fragment, { children: [" ", '\u2192', " ", _jsx("span", { style: { color: colors.success, fontFamily: font.mono }, children: event.suggested_value })] }))] })), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: event.reasoning })] }, idx));
                })) : (_jsx("div", { style: cardStyle, children: _jsx("pre", { style: { whiteSpace: 'pre-wrap', fontSize: font.size.sm, color: colors.textMuted, margin: 0, fontFamily: font.mono, lineHeight: 1.6 }, children: data.raw }) })) }))] }));
}
