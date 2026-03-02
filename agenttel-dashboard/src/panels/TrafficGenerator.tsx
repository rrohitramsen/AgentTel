import { useState, useRef, useCallback } from 'react';
import { config } from '../config';
import { colors, font, spacing, radii, card as cardStyle } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
import { TraceLink } from '../components/TraceLink';

interface RequestResult {
  id: number;
  amount: number;
  status: number;
  transactionId: string;
  duration: number;
  timestamp: Date;
  error?: string;
}

interface TrafficConfig {
  count: number;
  delayMs: number;
  amountMin: number;
  amountMax: number;
  errorInjection: 'off' | '5' | '15' | '30';
  frontendScenario: 'normal' | 'slow' | 'errors';
}

interface FrontendTelemetryResult {
  generated: number;
  journeys: number;
  scenario: string;
  traces: string[];
  sent_to_collector: boolean;
}

const journeySteps = [
  { label: 'Browse', icon: '\uD83D\uDED2' },
  { label: 'Product', icon: '\uD83D\uDCE6' },
  { label: 'Cart', icon: '\uD83D\uDED2' },
  { label: 'Checkout', icon: '\uD83D\uDCB3' },
  { label: 'Payment', icon: '\u2705' },
];

export function TrafficGenerator() {
  const [trafficConfig, setTrafficConfig] = useState<TrafficConfig>({
    count: 10,
    delayMs: 500,
    amountMin: 10,
    amountMax: 500,
    errorInjection: 'off',
    frontendScenario: 'normal',
  });

  const [running, setRunning] = useState(false);
  const [results, setResults] = useState<RequestResult[]>([]);
  const [progress, setProgress] = useState(0);
  const [currentStep, setCurrentStep] = useState(-1);
  const [frontendResult, setFrontendResult] = useState<FrontendTelemetryResult | null>(null);
  const [phase, setPhase] = useState<'idle' | 'frontend' | 'backend' | 'done'>('idle');
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
        const data: FrontendTelemetryResult = await res.json();
        setFrontendResult(data);
      }
    } catch {
      // Frontend telemetry is best-effort — continue with backend
    }

    if (abortRef.current) { setRunning(false); setPhase('idle'); return; }

    // Phase 2: Generate backend traffic
    setPhase('backend');
    for (let i = 0; i < trafficConfig.count; i++) {
      if (abortRef.current) break;

      const stepIndex = Math.min(Math.floor(((i + 1) / trafficConfig.count) * journeySteps.length), journeySteps.length - 1);
      setCurrentStep(stepIndex);

      const amount = trafficConfig.amountMin +
        Math.random() * (trafficConfig.amountMax - trafficConfig.amountMin);
      const roundedAmount = Math.round(amount * 100) / 100;

      const errorRate = trafficConfig.errorInjection === 'off' ? 0 : parseInt(trafficConfig.errorInjection) / 100;
      const shouldError = Math.random() < errorRate;

      const start = performance.now();
      try {
        const body: Record<string, unknown> = { amount: roundedAmount, currency: 'USD' };
        if (shouldError) body.amount = -1;

        const res = await fetch(`${config.apiBaseUrl}/payments`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        });

        const duration = Math.round(performance.now() - start);
        let responseData = { transactionId: 'unknown' };
        try { responseData = await res.json(); } catch { /* ignore */ }

        setResults((prev) => [{
          id: i + 1, amount: roundedAmount, status: res.status,
          transactionId: responseData.transactionId || 'N/A', duration, timestamp: new Date(),
        }, ...prev]);
      } catch (err) {
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

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: spacing['2xl'] }}>
        <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>
          End-to-End Traffic Generator
        </h1>
        <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
          Generate full-stack traffic: browser spans + backend API requests + distributed traces
        </p>
      </div>

      {/* Journey Steps Visualization */}
      <div style={{ ...cardStyle, marginBottom: spacing['2xl'] }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.md }}>
          <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>Journey: Checkout Flow</span>
          {phase !== 'idle' && phase !== 'done' && (
            <span style={{
              fontSize: font.size.xs,
              padding: '2px 8px',
              borderRadius: '4px',
              backgroundColor: phase === 'frontend' ? `${colors.info}22` : `${colors.primary}22`,
              color: phase === 'frontend' ? colors.info : colors.primaryLight,
              fontWeight: font.weight.semibold,
            }}>
              {phase === 'frontend' ? 'Generating browser spans...' : 'Sending API requests...'}
            </span>
          )}
          {phase === 'done' && (
            <span style={{
              fontSize: font.size.xs,
              padding: '2px 8px',
              borderRadius: '4px',
              backgroundColor: `${colors.success}22`,
              color: colors.success,
              fontWeight: font.weight.semibold,
            }}>
              Complete
            </span>
          )}
        </div>

        {/* Two-layer pipeline: Frontend → Backend */}
        <div style={{ display: 'flex', gap: spacing.xl, marginBottom: spacing.md }}>
          {/* Frontend layer */}
          <div style={{
            flex: 1,
            padding: spacing.md,
            borderRadius: radii.md,
            backgroundColor: phase === 'frontend' ? `${colors.info}11` : 'rgba(0,0,0,0.02)',
            border: `1px solid ${phase === 'frontend' ? `${colors.info}44` : colors.border}`,
            transition: 'all 0.3s ease',
          }}>
            <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Browser Layer
            </div>
            <div style={{ fontSize: font.size.sm, color: colors.text }}>
              Page loads, clicks, Web Vitals
            </div>
            {frontendResult && (
              <div style={{ fontSize: font.size.xs, color: colors.success, marginTop: spacing.xs }}>
                {frontendResult.generated} spans / {frontendResult.journeys} journeys
              </div>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', color: colors.textDim, fontSize: '20px' }}>{'\u2192'}</div>

          {/* Backend layer */}
          <div style={{
            flex: 1,
            padding: spacing.md,
            borderRadius: radii.md,
            backgroundColor: phase === 'backend' ? `${colors.primary}11` : 'rgba(0,0,0,0.02)',
            border: `1px solid ${phase === 'backend' ? `${colors.primary}44` : colors.border}`,
            transition: 'all 0.3s ease',
          }}>
            <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Backend Layer
            </div>
            <div style={{ fontSize: font.size.sm, color: colors.text }}>
              POST /api/payments
            </div>
            {progress > 0 && (
              <div style={{ fontSize: font.size.xs, color: colors.success, marginTop: spacing.xs }}>
                {progress}/{trafficConfig.count} requests
              </div>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', color: colors.textDim, fontSize: '20px' }}>{'\u2192'}</div>

          {/* Traces */}
          <div style={{
            flex: 1,
            padding: spacing.md,
            borderRadius: radii.md,
            backgroundColor: phase === 'done' ? `${colors.success}11` : 'rgba(0,0,0,0.02)',
            border: `1px solid ${phase === 'done' ? `${colors.success}44` : colors.border}`,
            transition: 'all 0.3s ease',
          }}>
            <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Observability
            </div>
            <div style={{ fontSize: font.size.sm, color: colors.text }}>
              Traces, metrics, SLOs
            </div>
            {phase === 'done' && (
              <div style={{ display: 'flex', gap: spacing.sm, marginTop: spacing.xs }}>
                <TraceLink service="checkout-web" label="Frontend" />
                <TraceLink service="payment-service" label="Backend" />
              </div>
            )}
          </div>
        </div>

        {/* Progress bar */}
        {(running || phase === 'done') && (
          <div className="progress-bar">
            <div
              className="progress-fill"
              style={{
                width: phase === 'frontend' ? '20%' : `${20 + (progress / trafficConfig.count) * 80}%`,
                transition: 'width 0.3s ease',
              }}
            />
          </div>
        )}
      </div>

      <div className="grid-2" style={{ marginBottom: spacing['2xl'] }}>
        {/* Configuration */}
        <div style={cardStyle}>
          <div style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }}>
            Configuration
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.md }}>
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>Number of requests</span>
              <input
                className="input"
                type="number" min={1} max={100}
                value={trafficConfig.count}
                onChange={(e) => setTrafficConfig((c) => ({ ...c, count: parseInt(e.target.value) || 1 }))}
                disabled={running}
                style={{ width: '80px', textAlign: 'right' }}
              />
            </label>
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>Delay between (ms)</span>
              <input
                className="input"
                type="number" min={100} max={5000} step={100}
                value={trafficConfig.delayMs}
                onChange={(e) => setTrafficConfig((c) => ({ ...c, delayMs: parseInt(e.target.value) || 500 }))}
                disabled={running}
                style={{ width: '80px', textAlign: 'right' }}
              />
            </label>
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>Amount range ($)</span>
              <div style={{ display: 'flex', gap: spacing.sm, alignItems: 'center' }}>
                <input
                  className="input"
                  type="number" min={1}
                  value={trafficConfig.amountMin}
                  onChange={(e) => setTrafficConfig((c) => ({ ...c, amountMin: parseInt(e.target.value) || 1 }))}
                  disabled={running}
                  style={{ width: '60px', textAlign: 'right' }}
                />
                <span style={{ color: colors.textDim }}>to</span>
                <input
                  className="input"
                  type="number" min={1}
                  value={trafficConfig.amountMax}
                  onChange={(e) => setTrafficConfig((c) => ({ ...c, amountMax: parseInt(e.target.value) || 500 }))}
                  disabled={running}
                  style={{ width: '60px', textAlign: 'right' }}
                />
              </div>
            </label>
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>Error injection</span>
              <select
                className="select"
                value={trafficConfig.errorInjection}
                onChange={(e) => setTrafficConfig((c) => ({ ...c, errorInjection: e.target.value as TrafficConfig['errorInjection'] }))}
                disabled={running}
              >
                <option value="off">OFF</option>
                <option value="5">5%</option>
                <option value="15">15%</option>
                <option value="30">30%</option>
              </select>
            </label>
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>Frontend scenario</span>
              <select
                className="select"
                value={trafficConfig.frontendScenario}
                onChange={(e) => setTrafficConfig((c) => ({ ...c, frontendScenario: e.target.value as TrafficConfig['frontendScenario'] }))}
                disabled={running}
              >
                <option value="normal">Normal</option>
                <option value="slow">Slow (2-6s loads)</option>
                <option value="errors">Errors (20% fail)</option>
              </select>
            </label>
          </div>

          <div style={{ display: 'flex', gap: spacing.md, marginTop: spacing.xl }}>
            {!running ? (
              <button className="btn btn-primary" onClick={startTraffic}>
                {'\u25B6'} Start End-to-End Traffic
              </button>
            ) : (
              <button className="btn btn-danger" onClick={stopTraffic}>
                {'\u25A0'} Stop
              </button>
            )}
            <button className="btn btn-secondary" onClick={clearResults} disabled={running}>
              Clear
            </button>
          </div>
        </div>

        {/* Live Status */}
        <div style={cardStyle}>
          <div style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }}>
            Live Results
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md, marginBottom: spacing.lg }}>
            <StatusBadge status={running ? 'RUNNING' : phase === 'done' ? 'DONE' : 'IDLE'} />
            {running && (
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>
                {phase === 'frontend' ? 'Generating browser spans...' : `${progress} / ${trafficConfig.count}`}
              </span>
            )}
          </div>

          {/* Summary Metrics */}
          <div className="grid-kpi" style={{ marginBottom: spacing.lg }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.success, fontFamily: font.mono }}>
                {successCount}
              </div>
              <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>Success</div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: failCount > 0 ? colors.error : colors.textDim, fontFamily: font.mono }}>
                {failCount}
              </div>
              <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>Failed</div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text, fontFamily: font.mono }}>
                {avgDuration}ms
              </div>
              <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>Avg Latency</div>
            </div>
            {frontendResult && (
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.info, fontFamily: font.mono }}>
                  {frontendResult.generated}
                </div>
                <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>Browser Spans</div>
              </div>
            )}
          </div>

          {results.length === 0 && !running && phase === 'idle' && (
            <div style={{ color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.xl }}>
              Click "Start End-to-End Traffic" to generate browser + backend telemetry
            </div>
          )}

          {/* Post-run navigation hints */}
          {phase === 'done' && (
            <div style={{
              padding: spacing.md,
              backgroundColor: `${colors.success}11`,
              border: `1px solid ${colors.success}33`,
              borderRadius: radii.md,
              animation: 'fadeIn 0.2s ease-out',
            }}>
              <div style={{ fontSize: font.size.sm, color: colors.success, fontWeight: font.weight.semibold, marginBottom: spacing.sm }}>
                Traffic generated! Check these panels:
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: spacing.sm, fontSize: font.size.xs }}>
                {[
                  { label: 'Command Center', path: '/' },
                  { label: 'Cross-Stack', path: '/cross-stack' },
                  { label: 'Fleet Health', path: '/fleet' },
                  { label: 'SLO Compliance', path: '/slo' },
                  { label: 'Trends', path: '/trends' },
                  { label: 'Monitor Agent', path: '/monitor' },
                ].map((link) => (
                  <a
                    key={link.path}
                    href={link.path}
                    style={{
                      padding: '3px 10px',
                      borderRadius: '4px',
                      backgroundColor: `${colors.primary}22`,
                      color: colors.primaryLight,
                      textDecoration: 'none',
                      fontWeight: font.weight.semibold,
                    }}
                  >
                    {link.label} {'\u2192'}
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Request Log */}
      {results.length > 0 && (
        <div style={cardStyle}>
          <div style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }}>
            Request Log
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: font.size.md }}>
              <thead>
                <tr>
                  {['#', 'Amount', 'Status', 'Latency', 'Transaction', 'Trace'].map((h) => (
                    <th
                      key={h}
                      style={{
                        padding: `${spacing.sm} ${spacing.md}`,
                        textAlign: 'left',
                        color: colors.textDim,
                        borderBottom: `1px solid ${colors.border}`,
                        fontWeight: font.weight.semibold,
                        fontSize: font.size.xs,
                        textTransform: 'uppercase',
                        letterSpacing: '0.5px',
                      }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {results.slice(0, 50).map((r) => (
                  <tr
                    key={r.id}
                    className="row-hover"
                    style={{ borderBottom: `1px solid ${colors.border}`, animation: 'fadeIn 0.2s ease-out' }}
                  >
                    <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textDim, fontFamily: font.mono }}>{r.id}</td>
                    <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontFamily: font.mono }}>${r.amount.toFixed(2)}</td>
                    <td style={{ padding: `${spacing.sm} ${spacing.md}` }}>
                      <StatusBadge status={r.error ? 'error' : r.status >= 200 && r.status < 300 ? 'ok' : 'error'} size="sm" />
                    </td>
                    <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }}>{r.duration}ms</td>
                    <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textDim, fontFamily: font.mono, fontSize: font.size.xs }}>{r.transactionId}</td>
                    <td style={{ padding: `${spacing.sm} ${spacing.md}` }}>
                      <TraceLink service="payment-service" operation="POST /api/payments" label="View" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
