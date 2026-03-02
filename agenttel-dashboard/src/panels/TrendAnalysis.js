import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
import { TrendChart } from '../components/TrendChart';
import { MetricCard } from '../components/MetricCard';
function parseTrend(s) {
    const lower = s.toLowerCase();
    if (lower === 'increasing')
        return 'up';
    if (lower === 'decreasing')
        return 'down';
    return 'stable';
}
function parseTrends(raw) {
    const metrics = [];
    const lines = raw.split('\n');
    let currentLabel = '';
    for (const line of lines) {
        // Section headers: "LATENCY P50:", "LATENCY P99:", "ERROR RATE:", "THROUGHPUT:"
        const headerMatch = line.match(/^(LATENCY P50|LATENCY P99|ERROR RATE|THROUGHPUT):/);
        if (headerMatch) {
            currentLabel = headerMatch[1];
            continue;
        }
        // Detail lines start with "  " (2 spaces)
        if (currentLabel && line.match(/^\s{2}/)) {
            if (currentLabel.startsWith('LATENCY')) {
                // "  Current: 2ms  Avg: 52ms  Min: 2ms  Max: 70ms  Trend: STABLE →"
                const curMatch = line.match(/Current:\s*(\d+)ms/);
                if (curMatch) {
                    const trendMatch = line.match(/Trend:\s*(\w+)/);
                    const avgMatch = line.match(/Avg:\s*(\d+)ms/);
                    const minMatch = line.match(/Min:\s*(\d+)ms/);
                    const maxMatch = line.match(/Max:\s*(\d+)ms/);
                    const dataPoints = [];
                    if (minMatch)
                        dataPoints.push({ value: parseInt(minMatch[1]) });
                    if (avgMatch)
                        dataPoints.push({ value: parseInt(avgMatch[1]) });
                    if (maxMatch)
                        dataPoints.push({ value: parseInt(maxMatch[1]) });
                    dataPoints.push({ value: parseInt(curMatch[1]) });
                    metrics.push({
                        label: currentLabel,
                        current: `${curMatch[1]}ms`,
                        trend: trendMatch ? parseTrend(trendMatch[1]) : 'stable',
                        data: dataPoints,
                    });
                    currentLabel = '';
                }
            }
            else if (currentLabel === 'ERROR RATE') {
                // "  Current: 0.00%  Avg: 0.00%  Trend: STABLE →"
                const curMatch = line.match(/Current:\s*([0-9.]+)%/);
                if (curMatch) {
                    const trendMatch = line.match(/Trend:\s*(\w+)/);
                    const val = parseFloat(curMatch[1]);
                    metrics.push({
                        label: 'Error Rate',
                        current: `${curMatch[1]}%`,
                        trend: val > 1 ? 'up' : trendMatch ? parseTrend(trendMatch[1]) : 'stable',
                        data: [{ value: val }],
                    });
                    currentLabel = '';
                }
            }
            else if (currentLabel === 'THROUGHPUT') {
                // "  Total requests: 124"
                const tputMatch = line.match(/Total requests:\s*(\d+)/);
                if (tputMatch) {
                    metrics.push({
                        label: 'Throughput',
                        current: `${tputMatch[1]} reqs`,
                        trend: 'stable',
                        data: [{ value: parseInt(tputMatch[1]) }],
                    });
                    currentLabel = '';
                }
            }
        }
        // "OVERALL: STABLE — no significant change"
        if (line.match(/^OVERALL:/)) {
            currentLabel = '';
        }
    }
    return { metrics, raw };
}
export function TrendAnalysis() {
    const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
    const { data, loading, error, lastUpdated } = useMcpTool(client, 'get_trend_analysis', { operation_name: 'POST /api/payments', window_minutes: '30' }, config.pollIntervals.trendAnalysis, parseTrends);
    const [showRaw, setShowRaw] = useState(false);
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Trend Analysis" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Latency, error rate, and throughput trends for POST /api/payments" })] }), lastUpdated && (_jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Updated ", lastUpdated.toLocaleTimeString()] }))] }), loading && !data && _jsx("div", { className: "skeleton", style: { height: '150px' } }), error && (_jsx("div", { style: { ...cardStyle, borderColor: colors.error, color: colors.error }, children: error })), data && (_jsxs(_Fragment, { children: [data.metrics.length > 0 && (_jsx("div", { className: "grid-3", style: { marginBottom: spacing['2xl'] }, children: data.metrics.map((m) => (_jsx(MetricCard, { label: m.label, value: m.current, trend: m.trend, children: m.data.length > 1 && (_jsx(TrendChart, { data: m.data, height: 40, color: m.trend === 'up' && m.label.includes('Error') ? colors.error : colors.primaryLight })) }, m.label))) })), _jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.md }, children: "Full Analysis" }), _jsx("pre", { style: {
                                    fontFamily: font.mono,
                                    fontSize: font.size.sm,
                                    color: colors.textMuted,
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-word',
                                    margin: 0,
                                    lineHeight: 1.6,
                                }, children: data.raw })] })] }))] }));
}
