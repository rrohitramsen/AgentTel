import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
import { TraceLink } from '../components/TraceLink';
function parseCrossStack(raw) {
    const sections = [];
    const lines = raw.split('\n');
    let currentTitle = '';
    let currentContent = [];
    let frontendService = 'checkout-web';
    let backendService = 'payment-service';
    for (const line of lines) {
        const frontMatch = line.match(/frontend.*?service[=:]\s*(\S+)/i);
        if (frontMatch)
            frontendService = frontMatch[1];
        const backMatch = line.match(/backend.*?service[=:]\s*(\S+)/i);
        if (backMatch)
            backendService = backMatch[1];
        const sectionMatch = line.match(/^##\s+(.+)/) || line.match(/^---\s*(.+?)\s*---/);
        if (sectionMatch) {
            if (currentTitle)
                sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
            currentTitle = sectionMatch[1];
            currentContent = [];
        }
        else {
            currentContent.push(line);
        }
    }
    if (currentTitle)
        sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
    if (sections.length === 0)
        sections.push({ title: 'Cross-Stack Context', content: raw.trim() });
    return { frontendService, backendService, sections, raw };
}
export function CrossStackView() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_cross_stack_context', { operation_name: 'POST /api/payments' }, config.pollIntervals.crossStackView, parseCrossStack);
    const [showRaw, setShowRaw] = useState(false);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Cross-Stack View" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Frontend-to-backend trace correlation via W3C Trace Context" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '150px' } }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: error })), data && (_jsxs(_Fragment, { children: [_jsx("div", { style: { ...cardStyle, marginBottom: spacing['2xl'] }, children: _jsxs("div", { style: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: spacing.xl, padding: spacing.lg }, children: [_jsxs("div", { style: { textAlign: 'center' }, children: [_jsx("div", { style: {
                                                width: '80px',
                                                height: '80px',
                                                borderRadius: '50%',
                                                background: `linear-gradient(135deg, ${colors.primary}, ${colors.primaryLight})`,
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                fontSize: '28px',
                                                marginBottom: spacing.sm,
                                            }, children: '\uD83C\uDF10' }), _jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }, children: data.frontendService }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "Browser" }), _jsx(TraceLink, { service: data.frontendService, label: "Traces", style: { marginTop: spacing.xs } })] }), _jsxs("div", { style: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: spacing.xs }, children: [_jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "traceparent" }), _jsx("div", { style: { width: '120px', height: '2px', background: `linear-gradient(90deg, ${colors.primary}, ${colors.info})` } }), _jsx("div", { style: { fontSize: '16px' }, children: '\u2192' })] }), _jsxs("div", { style: { textAlign: 'center' }, children: [_jsx("div", { style: {
                                                width: '80px',
                                                height: '80px',
                                                borderRadius: '50%',
                                                background: `linear-gradient(135deg, ${colors.info}, ${colors.primaryLight})`,
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                fontSize: '28px',
                                                marginBottom: spacing.sm,
                                            }, children: '\u2699' }), _jsx("div", { style: { fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }, children: data.backendService }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "Backend" }), _jsx(TraceLink, { service: data.backendService, label: "Traces", style: { marginTop: spacing.xs } })] })] }) }), _jsx("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.md }, children: data.sections.map((section, i) => (_jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.primaryLight, marginBottom: spacing.md }, children: section.title }), _jsx("pre", { style: {
                                        fontFamily: font.mono,
                                        fontSize: font.size.sm,
                                        color: colors.text,
                                        whiteSpace: 'pre-wrap',
                                        margin: 0,
                                        lineHeight: 1.6,
                                    }, children: section.content })] }, i))) }), _jsxs("button", { className: "collapsible-header", onClick: () => setShowRaw(!showRaw), style: { marginTop: spacing.md }, children: [showRaw ? '\u25BC' : '\u25B6', " Raw MCP Response"] }), showRaw && (_jsx("pre", { style: { ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap' }, children: data.raw }))] }))] }));
}
