import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState, useEffect, useCallback, useRef } from 'react';
import { config } from '../config';
import { colors, font, spacing, radii, card as cardStyle } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
// Frontend telemetry generation is in TrafficGenerator.tsx
// ── Helpers ──────────────────────────────────────────────────────────
function formatUptime(seconds) {
    if (!seconds)
        return '-';
    if (seconds < 60)
        return `${seconds}s`;
    if (seconds < 3600)
        return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}
async function adminFetch(path, options) {
    const res = await fetch(`${config.adminBaseUrl}${path}`, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
    });
    if (!res.ok) {
        const body = await res.json().catch(() => ({ error: res.statusText }));
        throw new Error(body.error || `HTTP ${res.status}`);
    }
    return res.json();
}
// ── Component ────────────────────────────────────────────────────────
export function Agents() {
    // Status polling
    const [status, setStatus] = useState(null);
    const [statusError, setStatusError] = useState(null);
    // Monitor config
    const [apiKey, setApiKey] = useState('');
    const [showKey, setShowKey] = useState(false);
    const [model, setModel] = useState('claude-sonnet-4-5-20250929');
    const [interval, setInterval_] = useState(10);
    const [dryRun, setDryRun] = useState(false);
    const [monitorLoading, setMonitorLoading] = useState(false);
    const [monitorError, setMonitorError] = useState(null);
    // Monitor logs
    const [logs, setLogs] = useState([]);
    const logRef = useRef(null);
    // Poll status
    const fetchStatus = useCallback(async () => {
        try {
            const data = await adminFetch('/status');
            setStatus(data);
            setStatusError(null);
        }
        catch (e) {
            setStatusError(e instanceof Error ? e.message : 'Failed to connect');
        }
    }, []);
    useEffect(() => {
        fetchStatus();
        const id = window.setInterval(fetchStatus, config.pollIntervals.agentStatus);
        return () => window.clearInterval(id);
    }, [fetchStatus]);
    // Poll logs when monitor is running
    useEffect(() => {
        if (!status?.monitor.running)
            return;
        const fetchLogs = async () => {
            try {
                const data = await adminFetch('/monitor-logs');
                setLogs(data.logs || []);
            }
            catch {
                // ignore
            }
        };
        fetchLogs();
        const id = window.setInterval(fetchLogs, 2000);
        return () => window.clearInterval(id);
    }, [status?.monitor.running]);
    // Auto-scroll logs
    useEffect(() => {
        if (logRef.current) {
            logRef.current.scrollTop = logRef.current.scrollHeight;
        }
    }, [logs]);
    // Start monitor
    const handleStartMonitor = async () => {
        if (!apiKey.trim()) {
            setMonitorError('API key is required');
            return;
        }
        setMonitorLoading(true);
        setMonitorError(null);
        try {
            await adminFetch('/start-monitor', {
                method: 'POST',
                body: JSON.stringify({
                    api_key: apiKey,
                    model,
                    interval,
                    dry_run: dryRun,
                }),
            });
            setLogs([]);
            await fetchStatus();
        }
        catch (e) {
            setMonitorError(e instanceof Error ? e.message : 'Failed to start');
        }
        finally {
            setMonitorLoading(false);
        }
    };
    // Stop monitor
    const handleStopMonitor = async () => {
        setMonitorLoading(true);
        setMonitorError(null);
        try {
            await adminFetch('/stop-monitor', { method: 'POST' });
            await fetchStatus();
        }
        catch (e) {
            setMonitorError(e instanceof Error ? e.message : 'Failed to stop');
        }
        finally {
            setMonitorLoading(false);
        }
    };
    const monitorRunning = status?.monitor.running ?? false;
    return (_jsxs("div", { children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }, children: [_jsxs("div", { children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "Agent Management" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Configure, start/stop agents and generate telemetry for demos" })] }), status && (_jsxs("div", { style: { display: 'flex', gap: spacing.md, alignItems: 'center' }, children: [_jsx(StatusBadge, { status: statusError ? 'degraded' : 'healthy', size: "sm" }), _jsxs("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: ["Manager ", statusError ? 'disconnected' : 'connected'] })] }))] }), statusError && !status && (_jsxs("div", { style: { ...cardStyle, borderColor: colors.error, marginBottom: spacing.lg }, children: [_jsx("div", { style: { color: colors.error, fontWeight: font.weight.semibold }, children: "Agent Manager Not Available" }), _jsx("div", { style: { color: colors.textMuted, fontSize: font.size.sm, marginTop: spacing.xs }, children: "Make sure the agenttel-manager service is running. It should start automatically with docker compose." }), _jsx("div", { style: { color: colors.textDim, fontSize: font.size.xs, marginTop: spacing.sm, fontFamily: font.mono }, children: statusError })] })), _jsxs("div", { style: { ...cardStyle, marginBottom: spacing.lg }, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.lg }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md }, children: [_jsx("span", { style: { fontSize: '20px' }, children: '\u2699' }), _jsx("span", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text }, children: "Monitor Agent" })] }), _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md }, children: [_jsx(StatusBadge, { status: monitorRunning ? 'healthy' : 'unknown', size: "sm" }), _jsx("span", { style: { fontSize: font.size.sm, color: monitorRunning ? colors.success : colors.textDim }, children: monitorRunning ? 'RUNNING' : 'STOPPED' }), monitorRunning && status?.monitor.uptime_seconds && (_jsx("span", { style: { fontSize: font.size.xs, color: colors.textDim, fontFamily: font.mono }, children: formatUptime(status.monitor.uptime_seconds) }))] })] }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.lg }, children: "Autonomous SRE agent that watches service health, diagnoses incidents with Claude AI, and executes remediation." }), _jsxs("div", { style: { marginBottom: spacing.md }, children: [_jsx("label", { style: { display: 'block', fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.xs, fontWeight: font.weight.semibold }, children: "Anthropic API Key" }), _jsxs("div", { style: { display: 'flex', gap: spacing.sm }, children: [_jsx("input", { className: "input", type: showKey ? 'text' : 'password', value: apiKey, onChange: (e) => setApiKey(e.target.value), placeholder: "sk-ant-api03-...", disabled: monitorRunning, style: { flex: 1, fontFamily: font.mono, fontSize: font.size.sm } }), _jsx("button", { className: "btn btn-secondary", onClick: () => setShowKey(!showKey), style: { padding: `0 ${spacing.md}`, fontSize: font.size.sm, minWidth: '40px' }, children: showKey ? '\u25C9' : '\u25CE' })] }), _jsxs("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginTop: spacing.xs, display: 'flex', alignItems: 'center', gap: spacing.xs }, children: [_jsx("span", { style: { color: colors.warning }, children: '*' }), "Key is held in memory only. Never saved to disk. Gone when the service stops."] })] }), _jsxs("div", { style: { display: 'flex', gap: spacing.lg, marginBottom: spacing.lg, flexWrap: 'wrap' }, children: [_jsxs("div", { children: [_jsx("label", { style: { display: 'block', fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs }, children: "Model" }), _jsxs("select", { className: "select", value: model, onChange: (e) => setModel(e.target.value), disabled: monitorRunning, style: { fontSize: font.size.sm }, children: [_jsx("option", { value: "claude-sonnet-4-5-20250929", children: "Claude Sonnet 4.5" }), _jsx("option", { value: "claude-haiku-4-5-20251001", children: "Claude Haiku 4.5" }), _jsx("option", { value: "claude-opus-4-6", children: "Claude Opus 4.6" })] })] }), _jsxs("div", { children: [_jsx("label", { style: { display: 'block', fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs }, children: "Watch Interval" }), _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.xs }, children: [_jsx("input", { className: "input", type: "number", value: interval, onChange: (e) => setInterval_(Math.max(5, parseInt(e.target.value) || 10)), disabled: monitorRunning, style: { width: '70px', fontSize: font.size.sm }, min: 5 }), _jsx("span", { style: { fontSize: font.size.xs, color: colors.textDim }, children: "seconds" })] })] }), _jsxs("div", { children: [_jsx("label", { style: { display: 'block', fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs }, children: "Dry Run" }), _jsx("button", { className: `btn ${dryRun ? 'btn-primary' : 'btn-secondary'}`, onClick: () => setDryRun(!dryRun), disabled: monitorRunning, style: { fontSize: font.size.sm, padding: `4px ${spacing.md}` }, children: dryRun ? 'ON' : 'OFF' })] })] }), _jsxs("div", { style: { display: 'flex', gap: spacing.sm, alignItems: 'center', marginBottom: spacing.md }, children: [!monitorRunning ? (_jsx("button", { className: "btn btn-primary", onClick: handleStartMonitor, disabled: monitorLoading || !apiKey.trim(), style: { minWidth: '140px' }, children: monitorLoading ? 'Starting...' : '\u25B6 Start Monitor' })) : (_jsx("button", { className: "btn btn-danger", onClick: handleStopMonitor, disabled: monitorLoading, style: { minWidth: '140px' }, children: monitorLoading ? 'Stopping...' : '\u25A0 Stop Monitor' })), monitorError && (_jsx("span", { style: { fontSize: font.size.sm, color: colors.error }, children: monitorError })), status?.monitor.last_error && !monitorRunning && (_jsxs("span", { style: { fontSize: font.size.sm, color: colors.warning }, children: ["Last: ", status.monitor.last_error] }))] }), (monitorRunning || logs.length > 0) && (_jsxs("div", { children: [_jsxs("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs, fontWeight: font.weight.semibold }, children: ["Live Log ", monitorRunning && _jsx("span", { className: "status-pulse", style: { display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', backgroundColor: colors.success, marginLeft: spacing.xs } })] }), _jsx("pre", { ref: logRef, style: {
                                    backgroundColor: colors.bg,
                                    border: `1px solid ${colors.border}`,
                                    borderRadius: radii.md,
                                    padding: spacing.md,
                                    fontFamily: font.mono,
                                    fontSize: font.size.xs,
                                    color: colors.textMuted,
                                    maxHeight: '200px',
                                    overflow: 'auto',
                                    margin: 0,
                                    lineHeight: 1.6,
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-word',
                                }, children: logs.length > 0
                                    ? logs.map((line, i) => {
                                        const isErr = line.includes('[err]') || line.includes('ERROR') || line.includes('error');
                                        const isWarn = line.includes('DETECT') || line.includes('WARNING') || line.includes('degraded');
                                        const isAction = line.includes('ACTION') || line.includes('RESOLVE') || line.includes('execute');
                                        return (_jsx("div", { style: {
                                                color: isErr ? colors.error : isWarn ? colors.warning : isAction ? colors.success : colors.textMuted,
                                            }, children: line }, i));
                                    })
                                    : 'Waiting for output...' })] }))] }), _jsxs("div", { className: "grid-2", style: { marginBottom: spacing.lg }, children: [_jsxs("div", { className: "card-hover", style: cardStyle, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm }, children: [_jsx("span", { style: { fontSize: '16px' }, children: '\u2728' }), _jsx("span", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text }, children: "Instrument Agent" })] }), _jsx(StatusBadge, { status: status?.instrument.running ? 'healthy' : 'unknown', size: "sm" })] }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.sm }, children: "MCP server for code analysis and instrumentation generation" }), status?.instrument.running ? (_jsxs(_Fragment, { children: [_jsxs("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm }, children: ["Port 8082 \u00B7 ", status.instrument.tools?.length || 0, " tools registered"] }), _jsx("div", { style: { display: 'flex', flexWrap: 'wrap', gap: spacing.xs }, children: status.instrument.tools?.map((t) => (_jsx("span", { style: {
                                                fontSize: font.size.xs,
                                                padding: '2px 8px',
                                                borderRadius: '4px',
                                                backgroundColor: `${colors.primary}22`,
                                                color: colors.primaryLight,
                                                fontFamily: font.mono,
                                            }, children: t }, t))) })] })) : (_jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim }, children: "Not connected" }))] }), _jsxs("div", { className: "card-hover", style: cardStyle, children: [_jsxs("div", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm }, children: [_jsx("span", { style: { fontSize: '16px' }, children: '\u25C9' }), _jsx("span", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text }, children: "Backend MCP Server" })] }), _jsx(StatusBadge, { status: status?.backend.running ? 'healthy' : 'unknown', size: "sm" })] }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.sm }, children: "9 telemetry tools for health, SLOs, incidents, and remediation" }), status?.backend.running ? (_jsxs(_Fragment, { children: [_jsxs("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm }, children: ["Port 8081 \u00B7 ", status.backend.tools?.length || 0, " tools registered"] }), _jsx("div", { style: { display: 'flex', flexWrap: 'wrap', gap: spacing.xs }, children: status.backend.tools?.map((t) => (_jsx("span", { style: {
                                                fontSize: font.size.xs,
                                                padding: '2px 8px',
                                                borderRadius: '4px',
                                                backgroundColor: `${colors.info}22`,
                                                color: colors.info,
                                                fontFamily: font.mono,
                                            }, children: t }, t))) })] })) : (_jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim }, children: "Not connected" }))] })] }), _jsx("div", { className: "card-hover", style: { ...cardStyle, marginBottom: spacing.lg }, children: _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md }, children: [_jsx("span", { style: { fontSize: '16px' }, children: '\u2B22' }), _jsx("span", { style: { fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text }, children: "OpenTelemetry Collector" }), _jsx(StatusBadge, { status: status?.collector.running ? 'healthy' : 'unknown', size: "sm" }), _jsx("span", { style: { fontSize: font.size.sm, color: colors.textDim, marginLeft: 'auto' }, children: status?.collector.running ? 'Receiving spans on port 4318' : 'Not connected' })] }) })] }));
}
