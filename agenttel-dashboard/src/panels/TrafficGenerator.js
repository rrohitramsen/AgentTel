import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState, useRef, useCallback } from 'react';
import { config } from '../config';
import { colors, font, spacing, radii, card as cardStyle } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
import { TraceLink } from '../components/TraceLink';
const journeySteps = [
    { label: 'Browse', icon: '\uD83D\uDED2' },
    { label: 'Product', icon: '\uD83D\uDCE6' },
    { label: 'Cart', icon: '\uD83D\uDED2' },
    { label: 'Checkout', icon: '\uD83D\uDCB3' },
    { label: 'Payment', icon: '\u2705' },
];
export function TrafficGenerator() {
    const [trafficConfig, setTrafficConfig] = useState({
        count: 10,
        delayMs: 500,
        amountMin: 10,
        amountMax: 500,
        errorInjection: 'off',
        frontendScenario: 'normal',
    });
    const [running, setRunning] = useState(false);
    const [results, setResults] = useState([]);
    const [progress, setProgress] = useState(0);
    const [currentStep, setCurrentStep] = useState(-1);
    const [frontendResult, setFrontendResult] = useState(null);
    const [phase, setPhase] = useState('idle');
    const abortRef = useRef(false);
    const successCount = results.filter((r) => r.status >= 200 && r.status < 300).length;
    const failCount = results.filter((r) => r.status >= 400 || r.error).length;
    const avgDuration = results.length > 0
        ? Math.round(results.reduce((sum, r) => sum + r.duration, 0) / results.length)
        : 0;
    const startTraffic = useCallback(async () => {
        setRunning(true);
        setResults([]);
        setProgress(0);
        setFrontendResult(null);
        abortRef.current = false;
        // Phase 1: Generate frontend telemetry spans
        setPhase('frontend');
        setCurrentStep(0);
        try {
            const frontendCount = Math.max(1, Math.ceil(trafficConfig.count / 2));
            const scenario = trafficConfig.errorInjection !== 'off' ? 'errors' : trafficConfig.frontendScenario;
            const res = await fetch(`${config.adminBaseUrl}/generate-frontend-telemetry`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    count: frontendCount,
                    scenario,
                    delay_ms: 100,
                }),
            });
            if (res.ok) {
                const data = await res.json();
                setFrontendResult(data);
            }
        }
        catch {
            // Frontend telemetry is best-effort — continue with backend
        }
        if (abortRef.current) {
            setRunning(false);
            setPhase('idle');
            return;
        }
        // Phase 2: Generate backend traffic
        setPhase('backend');
        for (let i = 0; i < trafficConfig.count; i++) {
            if (abortRef.current)
                break;
            const stepIndex = Math.min(Math.floor(((i + 1) / trafficConfig.count) * journeySteps.length), journeySteps.length - 1);
            setCurrentStep(stepIndex);
            const amount = trafficConfig.amountMin +
                Math.random() * (trafficConfig.amountMax - trafficConfig.amountMin);
            const roundedAmount = Math.round(amount * 100) / 100;
            const errorRate = trafficConfig.errorInjection === 'off' ? 0 : parseInt(trafficConfig.errorInjection) / 100;
            const shouldError = Math.random() < errorRate;
            const start = performance.now();
            try {
                const body = { amount: roundedAmount, currency: 'USD' };
                if (shouldError)
                    body.amount = -1;
                const res = await fetch(`${config.apiBaseUrl}/payments`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body),
                });
                const duration = Math.round(performance.now() - start);
                let responseData = { transactionId: 'unknown' };
                try {
                    responseData = await res.json();
                }
                catch { /* ignore */ }
                setResults((prev) => [{
                        id: i + 1, amount: roundedAmount, status: res.status,
                        transactionId: responseData.transactionId || 'N/A', duration, timestamp: new Date(),
                    }, ...prev]);
            }
            catch (err) {
                const duration = Math.round(performance.now() - start);
                setResults((prev) => [{
                        id: i + 1, amount: roundedAmount, status: 0, transactionId: 'N/A',
                        duration, timestamp: new Date(),
                        error: err instanceof Error ? err.message : 'Network error',
                    }, ...prev]);
            }
            setProgress(i + 1);
            if (i < trafficConfig.count - 1 && !abortRef.current) {
                await new Promise((resolve) => setTimeout(resolve, trafficConfig.delayMs));
            }
        }
        setCurrentStep(journeySteps.length - 1);
        setPhase('done');
        setRunning(false);
    }, [trafficConfig]);
    const stopTraffic = useCallback(() => {
        abortRef.current = true;
        setRunning(false);
    }, []);
    const clearResults = useCallback(() => {
        setResults([]);
        setProgress(0);
        setCurrentStep(-1);
        setFrontendResult(null);
        setPhase('idle');
    }, []);
    return (_jsxs("div", { children: [_jsxs("div", { style: { marginBottom: spacing['2xl'] }, children: [_jsx("h1", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }, children: "End-to-End Traffic Generator" }), _jsx("p", { style: { fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }, children: "Generate full-stack traffic: browser spans + backend API requests + distributed traces" })] }), _jsxs("div", { style: { ...cardStyle, marginBottom: spacing['2xl'] }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.md }, children: [_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Journey: Checkout Flow" }), phase !== 'idle' && phase !== 'done' && (_jsx("span", { style: {
                                    fontSize: font.size.xs,
                                    padding: '2px 8px',
                                    borderRadius: '4px',
                                    backgroundColor: phase === 'frontend' ? `${colors.info}22` : `${colors.primary}22`,
                                    color: phase === 'frontend' ? colors.info : colors.primaryLight,
                                    fontWeight: font.weight.semibold,
                                }, children: phase === 'frontend' ? 'Generating browser spans...' : 'Sending API requests...' })), phase === 'done' && (_jsx("span", { style: {
                                    fontSize: font.size.xs,
                                    padding: '2px 8px',
                                    borderRadius: '4px',
                                    backgroundColor: `${colors.success}22`,
                                    color: colors.success,
                                    fontWeight: font.weight.semibold,
                                }, children: "Complete" }))] }), _jsxs("div", { style: { display: 'flex', gap: spacing.xl, marginBottom: spacing.md }, children: [_jsxs("div", { style: {
                                    flex: 1,
                                    padding: spacing.md,
                                    borderRadius: radii.md,
                                    backgroundColor: phase === 'frontend' ? `${colors.info}11` : 'rgba(255,255,255,0.02)',
                                    border: `1px solid ${phase === 'frontend' ? `${colors.info}44` : colors.border}`,
                                    transition: 'all 0.3s ease',
                                }, children: [_jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: '0.5px' }, children: "Browser Layer" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.text }, children: "Page loads, clicks, Web Vitals" }), frontendResult && (_jsxs("div", { style: { fontSize: font.size.xs, color: colors.success, marginTop: spacing.xs }, children: [frontendResult.generated, " spans / ", frontendResult.journeys, " journeys"] }))] }), _jsx("div", { style: { display: 'flex', alignItems: 'center', color: colors.textDim, fontSize: '20px' }, children: '\u2192' }), _jsxs("div", { style: {
                                    flex: 1,
                                    padding: spacing.md,
                                    borderRadius: radii.md,
                                    backgroundColor: phase === 'backend' ? `${colors.primary}11` : 'rgba(255,255,255,0.02)',
                                    border: `1px solid ${phase === 'backend' ? `${colors.primary}44` : colors.border}`,
                                    transition: 'all 0.3s ease',
                                }, children: [_jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: '0.5px' }, children: "Backend Layer" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.text }, children: "POST /api/payments" }), progress > 0 && (_jsxs("div", { style: { fontSize: font.size.xs, color: colors.success, marginTop: spacing.xs }, children: [progress, "/", trafficConfig.count, " requests"] }))] }), _jsx("div", { style: { display: 'flex', alignItems: 'center', color: colors.textDim, fontSize: '20px' }, children: '\u2192' }), _jsxs("div", { style: {
                                    flex: 1,
                                    padding: spacing.md,
                                    borderRadius: radii.md,
                                    backgroundColor: phase === 'done' ? `${colors.success}11` : 'rgba(255,255,255,0.02)',
                                    border: `1px solid ${phase === 'done' ? `${colors.success}44` : colors.border}`,
                                    transition: 'all 0.3s ease',
                                }, children: [_jsx("div", { style: { fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: '0.5px' }, children: "Observability" }), _jsx("div", { style: { fontSize: font.size.sm, color: colors.text }, children: "Traces, metrics, SLOs" }), phase === 'done' && (_jsxs("div", { style: { display: 'flex', gap: spacing.sm, marginTop: spacing.xs }, children: [_jsx(TraceLink, { service: "checkout-web", label: "Frontend" }), _jsx(TraceLink, { service: "payment-service", label: "Backend" })] }))] })] }), (running || phase === 'done') && (_jsx("div", { className: "progress-bar", children: _jsx("div", { className: "progress-fill", style: {
                                width: phase === 'frontend' ? '20%' : `${20 + (progress / trafficConfig.count) * 80}%`,
                                transition: 'width 0.3s ease',
                            } }) }))] }), _jsxs("div", { className: "grid-2", style: { marginBottom: spacing['2xl'] }, children: [_jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }, children: "Configuration" }), _jsxs("div", { style: { display: 'flex', flexDirection: 'column', gap: spacing.md }, children: [_jsxs("label", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' }, children: [_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Number of requests" }), _jsx("input", { className: "input", type: "number", min: 1, max: 100, value: trafficConfig.count, onChange: (e) => setTrafficConfig((c) => ({ ...c, count: parseInt(e.target.value) || 1 })), disabled: running, style: { width: '80px', textAlign: 'right' } })] }), _jsxs("label", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' }, children: [_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Delay between (ms)" }), _jsx("input", { className: "input", type: "number", min: 100, max: 5000, step: 100, value: trafficConfig.delayMs, onChange: (e) => setTrafficConfig((c) => ({ ...c, delayMs: parseInt(e.target.value) || 500 })), disabled: running, style: { width: '80px', textAlign: 'right' } })] }), _jsxs("label", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' }, children: [_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Amount range ($)" }), _jsxs("div", { style: { display: 'flex', gap: spacing.sm, alignItems: 'center' }, children: [_jsx("input", { className: "input", type: "number", min: 1, value: trafficConfig.amountMin, onChange: (e) => setTrafficConfig((c) => ({ ...c, amountMin: parseInt(e.target.value) || 1 })), disabled: running, style: { width: '60px', textAlign: 'right' } }), _jsx("span", { style: { color: colors.textDim }, children: "to" }), _jsx("input", { className: "input", type: "number", min: 1, value: trafficConfig.amountMax, onChange: (e) => setTrafficConfig((c) => ({ ...c, amountMax: parseInt(e.target.value) || 500 })), disabled: running, style: { width: '60px', textAlign: 'right' } })] })] }), _jsxs("label", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' }, children: [_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Error injection" }), _jsxs("select", { className: "select", value: trafficConfig.errorInjection, onChange: (e) => setTrafficConfig((c) => ({ ...c, errorInjection: e.target.value })), disabled: running, children: [_jsx("option", { value: "off", children: "OFF" }), _jsx("option", { value: "5", children: "5%" }), _jsx("option", { value: "15", children: "15%" }), _jsx("option", { value: "30", children: "30%" })] })] }), _jsxs("label", { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' }, children: [_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: "Frontend scenario" }), _jsxs("select", { className: "select", value: trafficConfig.frontendScenario, onChange: (e) => setTrafficConfig((c) => ({ ...c, frontendScenario: e.target.value })), disabled: running, children: [_jsx("option", { value: "normal", children: "Normal" }), _jsx("option", { value: "slow", children: "Slow (2-6s loads)" }), _jsx("option", { value: "errors", children: "Errors (20% fail)" })] })] })] }), _jsxs("div", { style: { display: 'flex', gap: spacing.md, marginTop: spacing.xl }, children: [!running ? (_jsxs("button", { className: "btn btn-primary", onClick: startTraffic, children: ['\u25B6', " Start End-to-End Traffic"] })) : (_jsxs("button", { className: "btn btn-danger", onClick: stopTraffic, children: ['\u25A0', " Stop"] })), _jsx("button", { className: "btn btn-secondary", onClick: clearResults, disabled: running, children: "Clear" })] })] }), _jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }, children: "Live Results" }), _jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: spacing.md, marginBottom: spacing.lg }, children: [_jsx(StatusBadge, { status: running ? 'RUNNING' : phase === 'done' ? 'DONE' : 'IDLE' }), running && (_jsx("span", { style: { fontSize: font.size.sm, color: colors.textMuted }, children: phase === 'frontend' ? 'Generating browser spans...' : `${progress} / ${trafficConfig.count}` }))] }), _jsxs("div", { className: "grid-kpi", style: { marginBottom: spacing.lg }, children: [_jsxs("div", { style: { textAlign: 'center' }, children: [_jsx("div", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.success, fontFamily: font.mono }, children: successCount }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "Success" })] }), _jsxs("div", { style: { textAlign: 'center' }, children: [_jsx("div", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: failCount > 0 ? colors.error : colors.textDim, fontFamily: font.mono }, children: failCount }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "Failed" })] }), _jsxs("div", { style: { textAlign: 'center' }, children: [_jsxs("div", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text, fontFamily: font.mono }, children: [avgDuration, "ms"] }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "Avg Latency" })] }), frontendResult && (_jsxs("div", { style: { textAlign: 'center' }, children: [_jsx("div", { style: { fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.info, fontFamily: font.mono }, children: frontendResult.generated }), _jsx("div", { style: { fontSize: font.size.xs, color: colors.textMuted }, children: "Browser Spans" })] }))] }), results.length === 0 && !running && phase === 'idle' && (_jsx("div", { style: { color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.xl }, children: "Click \"Start End-to-End Traffic\" to generate browser + backend telemetry" })), phase === 'done' && (_jsxs("div", { style: {
                                    padding: spacing.md,
                                    backgroundColor: `${colors.success}11`,
                                    border: `1px solid ${colors.success}33`,
                                    borderRadius: radii.md,
                                    animation: 'fadeIn 0.2s ease-out',
                                }, children: [_jsx("div", { style: { fontSize: font.size.sm, color: colors.success, fontWeight: font.weight.semibold, marginBottom: spacing.sm }, children: "Traffic generated! Check these panels:" }), _jsx("div", { style: { display: 'flex', flexWrap: 'wrap', gap: spacing.sm, fontSize: font.size.xs }, children: [
                                            { label: 'Command Center', path: '/' },
                                            { label: 'Cross-Stack', path: '/cross-stack' },
                                            { label: 'Fleet Health', path: '/fleet' },
                                            { label: 'SLO Compliance', path: '/slo' },
                                            { label: 'Trends', path: '/trends' },
                                            { label: 'Monitor Agent', path: '/monitor' },
                                        ].map((link) => (_jsxs("a", { href: link.path, style: {
                                                padding: '3px 10px',
                                                borderRadius: '4px',
                                                backgroundColor: `${colors.primary}22`,
                                                color: colors.primaryLight,
                                                textDecoration: 'none',
                                                fontWeight: font.weight.semibold,
                                            }, children: [link.label, " ", '\u2192'] }, link.path))) })] }))] })] }), results.length > 0 && (_jsxs("div", { style: cardStyle, children: [_jsx("div", { style: { fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }, children: "Request Log" }), _jsx("div", { style: { overflowX: 'auto' }, children: _jsxs("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: font.size.md }, children: [_jsx("thead", { children: _jsx("tr", { children: ['#', 'Amount', 'Status', 'Latency', 'Transaction', 'Trace'].map((h) => (_jsx("th", { style: {
                                                padding: `${spacing.sm} ${spacing.md}`,
                                                textAlign: 'left',
                                                color: colors.textDim,
                                                borderBottom: `1px solid ${colors.border}`,
                                                fontWeight: font.weight.semibold,
                                                fontSize: font.size.xs,
                                                textTransform: 'uppercase',
                                                letterSpacing: '0.5px',
                                            }, children: h }, h))) }) }), _jsx("tbody", { children: results.slice(0, 50).map((r) => (_jsxs("tr", { className: "row-hover", style: { borderBottom: `1px solid ${colors.border}`, animation: 'fadeIn 0.2s ease-out' }, children: [_jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textDim, fontFamily: font.mono }, children: r.id }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontFamily: font.mono }, children: ["$", r.amount.toFixed(2)] }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}` }, children: _jsx(StatusBadge, { status: r.error ? 'error' : r.status >= 200 && r.status < 300 ? 'ok' : 'error', size: "sm" }) }), _jsxs("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }, children: [r.duration, "ms"] }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}`, color: colors.textDim, fontFamily: font.mono, fontSize: font.size.xs }, children: r.transactionId }), _jsx("td", { style: { padding: `${spacing.sm} ${spacing.md}` }, children: _jsx(TraceLink, { service: "payment-service", operation: "POST /api/payments", label: "View" }) })] }, r.id))) })] }) })] }))] }));
}
