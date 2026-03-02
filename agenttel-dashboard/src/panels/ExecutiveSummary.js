import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useMemo } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
function parseSummary(raw) {
    const sections = [];
    const lines = raw.split('\n');
    let currentTitle = '';
    let currentContent = [];
    for (const line of lines) {
        const headerMatch = line.match(/^##\s+(.+)/) || line.match(/^===\s*(.+?)\s*===/);
        if (headerMatch) {
            if (currentTitle)
                sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
            currentTitle = headerMatch[1];
            currentContent = [];
        }
        else {
            currentContent.push(line);
        }
    }
    if (currentTitle)
        sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
    if (sections.length === 0) {
        sections.push({ title: 'Summary', content: raw.trim() });
    }
    return { sections, raw };
}
export function ExecutiveSummary() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_executive_summary', {}, config.pollIntervals.executiveSummary, parseSummary);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Executive Summary" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "~300 token LLM-optimized overview of system state" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '200px' } }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: error })), data && (_jsx("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.lg }, children: data.sections.map((section, i) => (_jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.primaryLight, marginBottom: spacing.md }, children: section.title }), _jsx("pre", { style: {
                                fontFamily: font.mono,
                                fontSize: font.size.sm,
                                color: colors.text,
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-word',
                                margin: 0,
                                lineHeight: 1.7,
                            }, children: section.content })] }, i))) }))] }));
}
