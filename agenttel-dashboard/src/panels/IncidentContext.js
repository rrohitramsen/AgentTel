import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
function parseIncident(raw) {
    let severity = 'unknown';
    let summary = '';
    const sections = [];
    const lines = raw.split('\n');
    let currentTitle = '';
    let currentContent = [];
    for (const line of lines) {
        const sevMatch = line.match(/SEVERITY:\s*(\w+)/i);
        if (sevMatch)
            severity = sevMatch[1].toLowerCase();
        const sumMatch = line.match(/SUMMARY:\s*(.+)/i);
        if (sumMatch)
            summary = sumMatch[1];
        const sectionMatch = line.match(/^##\s+(.+)/);
        if (sectionMatch) {
            if (currentTitle)
                sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
            currentTitle = sectionMatch[1];
            currentContent = [];
        }
        else if (currentTitle) {
            currentContent.push(line);
        }
    }
    if (currentTitle)
        sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
    return { severity, summary, sections, raw };
}
const severityColors = {
    high: colors.error,
    critical: colors.critical,
    medium: colors.warning,
    low: colors.info,
};
export function IncidentContext() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const [operationName, setOperationName] = useState('POST /api/payments');
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [showRaw, setShowRaw] = useState(false);
    const fetchIncident = async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await client.callTool('get_incident_context', { operation_name: operationName });
            setData(parseIncident(result));
        }
        catch (e) {
            setError(e instanceof Error ? e.message : 'Failed to fetch');
        }
        finally {
            setLoading(false);
        }
    };
    return (_jsxs("div", { children: [_jsxs("div", { style: { marginBottom: spacing['2xl'] }, children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Incident Context" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Structured incident packages optimized for LLM reasoning" })] }), _jsxs("div", { style: { display: 'flex', gap: spacing.sm, marginBottom: spacing['2xl'] }, children: [_jsx("input", { className: "input", type: "text", value: operationName, onChange: (e) => setOperationName(e.target.value), placeholder: "Operation name (e.g. POST /api/payments)", style: { flex: 1 }, onKeyDown: (e) => e.key === 'Enter' && fetchIncident() }), _jsx("button", { className: "btn btn-primary", onClick: fetchIncident, disabled: loading, children: loading ? 'Loading...' : 'Get Context' })] }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error, marginBottom: spacing.lg }, children: error })), data && (_jsxs(_Fragment, { children: [(data.severity !== 'unknown' || data.summary) && (_jsxs("div", { style: {
                            ...cardStyle,
                            borderLeft: `3px solid ${severityColors[data.severity] || colors.textMuted}`,
                            marginBottom: spacing.lg,
                        }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md, marginBottom: spacing.sm }, children: [_jsx(StatusBadge, { status: data.severity }), _jsx("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: "Incident" })] }), data.summary && (_jsx("div", { style: { fontSize: font.size.lg, color: colors.text, fontWeight: font.weight.medium }, children: data.summary }))] })), data.sections.length > 0 ? (_jsx("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.md, marginBottom: spacing.lg }, children: data.sections.map((section, i) => (_jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.primaryLight, marginBottom: spacing.md }, children: section.title }), _jsx("pre", { style: {
                                        fontFamily: font.mono,
                                        fontSize: font.size.sm,
                                        color: colors.text,
                                        whiteSpace: 'pre-wrap',
                                        margin: 0,
                                        lineHeight: 1.6,
                                    }, children: section.content })] }, i))) })) : (_jsx("div", { style: cardStyle, children: _jsx("pre", { style: { fontFamily: font.mono, fontSize: font.size.sm, color: colors.text, whiteSpace: 'pre-wrap', margin: 0, lineHeight: 1.6 }, children: data.raw }) })), data.sections.length > 0 && (_jsxs(_Fragment, { children: [_jsxs("button", { className: "collapsible-header", onClick: () => setShowRaw(!showRaw), children: [showRaw ? '\u25BC' : '\u25B6', " Raw MCP Response"] }), showRaw && (_jsx("pre", { style: { ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap' }, children: data.raw }))] }))] })), !data && !loading && !error && (_jsxs("div", { style: { ...cardStyle, textAlign: 'center', padding: spacing['3xl'], color: colors.textDim }, children: [_jsx("div", { style: { fontSize: '32px', marginBottom: spacing.md }, children: '\u26A0' }), _jsx("div", { style: { fontSize: font.size.md }, children: "Enter an operation name and click \"Get Context\"" }), _jsx("div", { style: { fontSize: font.size.sm, marginTop: spacing.xs }, children: "View structured incident packages for any tracked operation" })] }))] }));
}
