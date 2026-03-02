import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
function parseActions(raw) {
    const actions = [];
    const lines = raw.split('\n');
    for (const line of lines) {
        const match = line.match(/^\s*\[([^\]]+)\]\s*(?:\[(\w+)\])?\s*(.+)/);
        if (match) {
            actions.push({
                timestamp: match[1],
                type: match[2] || 'info',
                detail: match[3],
            });
        }
    }
    return { actions, raw };
}
const typeColors = {
    check: colors.info,
    detect: colors.warning,
    action: colors.primary,
    resolve: colors.success,
    error: colors.error,
    info: colors.textMuted,
};
export function MonitorDecisions() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_recent_agent_actions', {}, config.pollIntervals.monitorDecisions, parseActions);
    const [showRaw, setShowRaw] = useState(false);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Monitor Agent" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Recent AI agent actions, decisions, and reasoning" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '200px' } }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: error })), data && (_jsxs(_Fragment, { children: [_jsx("div", { style: cardStyle, children: data.actions.length > 0 ? (_jsxs("div", { style: { position: 'relative' }, children: [_jsx("div", { style: {
                                        position: 'absolute',
                                        left: '7px',
                                        top: '8px',
                                        bottom: '8px',
                                        width: '2px',
                                        backgroundColor: colors.border,
                                    } }), data.actions.map((action, i) => {
                                    const color = typeColors[action.type.toLowerCase()] || colors.textMuted;
                                    return (_jsxs("div", { style: {
                                            display: 'flex',
                                            gap: spacing.lg,
                                            padding: `${spacing.md} 0`,
                                            paddingLeft: spacing['2xl'],
                                            position: 'relative',
                                            animation: 'slideIn 0.2s ease-out',
                                        }, children: [_jsx("div", { style: {
                                                    position: 'absolute',
                                                    left: '3px',
                                                    top: '16px',
                                                    width: '10px',
                                                    height: '10px',
                                                    borderRadius: '50%',
                                                    backgroundColor: color,
                                                    boxShadow: `0 0 6px ${color}`,
                                                } }), _jsxs("div", { style: { flex: 1 }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md, marginBottom: spacing.xs }, children: [_jsx("span", { style: { fontSize: font.size.xs, color: colors.textDim, fontFamily: font.mono }, children: action.timestamp }), action.type !== 'info' && (_jsx("span", { style: {
                                                                    fontSize: '10px',
                                                                    padding: '1px 6px',
                                                                    borderRadius: '3px',
                                                                    backgroundColor: `${color}22`,
                                                                    color,
                                                                    fontWeight: font.weight.semibold,
                                                                    textTransform: 'uppercase',
                                                                }, children: action.type }))] }), _jsx("div", { style: { fontSize: font.size.md, color: colors.text }, children: action.detail })] })] }, i));
                                })] })) : (_jsxs("div", { style: { textAlign: 'center', padding: spacing['3xl'], color: colors.textDim }, children: [_jsx("div", { style: { fontSize: '32px', marginBottom: spacing.md }, children: '\uD83E\uDD16' }), _jsx("div", { style: { fontSize: font.size.md }, children: "No agent activity yet" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textDim, marginTop: spacing.xs }, children: "Start the monitor agent to see decisions here" })] })) }), _jsxs("button", { className: "collapsible-header", onClick: () => setShowRaw(!showRaw), style: { marginTop: spacing.md }, children: [showRaw ? '\u25BC' : '\u25B6', " Raw MCP Response"] }), showRaw && (_jsx("pre", { style: { ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap' }, children: data.raw }))] }))] }));
}
