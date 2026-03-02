import { useState, useEffect, useCallback, useRef } from 'react';
import { config } from '../config';
import { colors, font, spacing, radii, card as cardStyle } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';

// ── Types ────────────────────────────────────────────────────────────

interface AgentInfo {
  running: boolean;
  pid?: number | null;
  uptime_seconds?: number | null;
  last_error?: string | null;
  url?: string;
  tools?: string[];
}

interface StatusResponse {
  monitor: AgentInfo;
  instrument: AgentInfo;
  backend: AgentInfo;
  collector: { running: boolean };
  frontend_telemetry: { generated_count: number };
}

// Frontend telemetry generation is in TrafficGenerator.tsx

// ── Helpers ──────────────────────────────────────────────────────────

function formatUptime(seconds: number | null | undefined): string {
  if (!seconds) return '-';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

async function adminFetch(path: string, options?: RequestInit) {
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
  const [status, setStatus] = useState<StatusResponse | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);

  // Monitor config
  const [apiKey, setApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [model, setModel] = useState('claude-sonnet-4-5-20250929');
  const [interval, setInterval_] = useState(10);
  const [dryRun, setDryRun] = useState(false);
  const [monitorLoading, setMonitorLoading] = useState(false);
  const [monitorError, setMonitorError] = useState<string | null>(null);

  // Monitor logs
  const [logs, setLogs] = useState<string[]>([]);
  const logRef = useRef<HTMLPreElement>(null);

  // Instrument Agent expandable state
  const [instrumentExpanded, setInstrumentExpanded] = useState(false);
  const [instrumentLogs, setInstrumentLogs] = useState<string[]>([]);
  const [instrumentConfig, setInstrumentConfig] = useState<any>(null);
  const instrumentLogRef = useRef<HTMLPreElement>(null);

  // Backend MCP expandable state
  const [backendExpanded, setBackendExpanded] = useState(false);
  const [backendLogs, setBackendLogs] = useState<string[]>([]);
  const [backendConfig, setBackendConfig] = useState<{ health: string | null; slo: string | null }>({ health: null, slo: null });
  const backendLogRef = useRef<HTMLPreElement>(null);


  // Poll status
  const fetchStatus = useCallback(async () => {
    try {
      const data = await adminFetch('/status');
      setStatus(data);
      setStatusError(null);
    } catch (e) {
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
    if (!status?.monitor.running) return;
    const fetchLogs = async () => {
      try {
        const data = await adminFetch('/monitor-logs');
        setLogs(data.logs || []);
      } catch {
        // ignore
      }
    };
    fetchLogs();
    const id = window.setInterval(fetchLogs, 2000);
    return () => window.clearInterval(id);
  }, [status?.monitor.running]);

  // Auto-scroll logs
  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [logs]);
  useEffect(() => {
    if (instrumentLogRef.current) instrumentLogRef.current.scrollTop = instrumentLogRef.current.scrollHeight;
  }, [instrumentLogs]);
  useEffect(() => {
    if (backendLogRef.current) backendLogRef.current.scrollTop = backendLogRef.current.scrollHeight;
  }, [backendLogs]);

  // Poll instrument logs when expanded
  useEffect(() => {
    if (!instrumentExpanded || !status?.instrument.running) return;
    const fetchLogs = async () => {
      try {
        const data = await adminFetch('/instrument-logs');
        setInstrumentLogs(data.logs || []);
      } catch { /* ignore */ }
    };
    fetchLogs();
    const id = window.setInterval(fetchLogs, 2000);
    return () => window.clearInterval(id);
  }, [instrumentExpanded, status?.instrument.running]);

  // Fetch instrument config when expanded
  useEffect(() => {
    if (!instrumentExpanded || !status?.instrument.running) return;
    adminFetch('/instrument-config').then(setInstrumentConfig).catch(() => {});
  }, [instrumentExpanded, status?.instrument.running]);

  // Poll backend activity when expanded
  useEffect(() => {
    if (!backendExpanded || !status?.backend.running) return;
    const fetchActivity = async () => {
      try {
        const data = await adminFetch('/backend-activity');
        setBackendLogs(data.logs || []);
      } catch { /* ignore */ }
    };
    fetchActivity();
    const id = window.setInterval(fetchActivity, 3000);
    return () => window.clearInterval(id);
  }, [backendExpanded, status?.backend.running]);

  // Fetch backend config when expanded
  useEffect(() => {
    if (!backendExpanded || !status?.backend.running) return;
    adminFetch('/backend-config').then(setBackendConfig).catch(() => {});
  }, [backendExpanded, status?.backend.running]);

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
    } catch (e) {
      setMonitorError(e instanceof Error ? e.message : 'Failed to start');
    } finally {
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
    } catch (e) {
      setMonitorError(e instanceof Error ? e.message : 'Failed to stop');
    } finally {
      setMonitorLoading(false);
    }
  };

  const monitorRunning = status?.monitor.running ?? false;

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>
            Agent Management
          </h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Configure, start/stop agents and generate telemetry for demos
          </p>
        </div>
        {status && (
          <div style={{ display: 'flex', gap: spacing.md, alignItems: 'center' }}>
            <StatusBadge status={statusError ? 'degraded' : 'healthy'} size="sm" />
            <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
              Manager {statusError ? 'disconnected' : 'connected'}
            </span>
          </div>
        )}
      </div>

      {statusError && !status && (
        <div style={{ ...cardStyle, borderColor: colors.error, marginBottom: spacing.lg }}>
          <div style={{ color: colors.error, fontWeight: font.weight.semibold }}>Agent Manager Not Available</div>
          <div style={{ color: colors.textMuted, fontSize: font.size.sm, marginTop: spacing.xs }}>
            Make sure the agenttel-manager service is running. It should start automatically with docker compose.
          </div>
          <div style={{ color: colors.textDim, fontSize: font.size.xs, marginTop: spacing.sm, fontFamily: font.mono }}>
            {statusError}
          </div>
        </div>
      )}

      {/* ── Monitor Agent Card ──────────────────────────────────────── */}
      <div style={{ ...cardStyle, marginBottom: spacing.lg }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.lg }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
            <span style={{ fontSize: '20px' }}>{'\u2699'}</span>
            <span style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text }}>
              Monitor Agent
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
            <StatusBadge status={monitorRunning ? 'healthy' : 'unknown'} size="sm" />
            <span style={{ fontSize: font.size.sm, color: monitorRunning ? colors.success : colors.textDim }}>
              {monitorRunning ? 'RUNNING' : 'STOPPED'}
            </span>
            {monitorRunning && status?.monitor.uptime_seconds && (
              <span style={{ fontSize: font.size.xs, color: colors.textDim, fontFamily: font.mono }}>
                {formatUptime(status.monitor.uptime_seconds)}
              </span>
            )}
          </div>
        </div>

        <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.lg }}>
          Autonomous SRE agent that watches service health, diagnoses incidents with Claude AI, and executes remediation.
        </p>

        {/* API Key Input */}
        <div style={{ marginBottom: spacing.md }}>
          <label style={{ display: 'block', fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.xs, fontWeight: font.weight.semibold }}>
            Anthropic API Key
          </label>
          <div style={{ display: 'flex', gap: spacing.sm }}>
            <input
              className="input"
              type={showKey ? 'text' : 'password'}
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="sk-ant-api03-..."
              disabled={monitorRunning}
              style={{ flex: 1, fontFamily: font.mono, fontSize: font.size.sm }}
            />
            <button
              className="btn btn-secondary"
              onClick={() => setShowKey(!showKey)}
              style={{ padding: `0 ${spacing.md}`, fontSize: font.size.sm, minWidth: '40px' }}
            >
              {showKey ? '\u25C9' : '\u25CE'}
            </button>
          </div>
          <div style={{ fontSize: font.size.xs, color: colors.textDim, marginTop: spacing.xs, display: 'flex', alignItems: 'center', gap: spacing.xs }}>
            <span style={{ color: colors.warning }}>{'*'}</span>
            Key is held in memory only. Never saved to disk. Gone when the service stops.
          </div>
        </div>

        {/* Config Row */}
        <div style={{ display: 'flex', gap: spacing.lg, marginBottom: spacing.lg, flexWrap: 'wrap' }}>
          <div>
            <label style={{ display: 'block', fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs }}>
              Model
            </label>
            <select
              className="select"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              disabled={monitorRunning}
              style={{ fontSize: font.size.sm }}
            >
              <option value="claude-sonnet-4-5-20250929">Claude Sonnet 4.5</option>
              <option value="claude-haiku-4-5-20251001">Claude Haiku 4.5</option>
              <option value="claude-opus-4-6">Claude Opus 4.6</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs }}>
              Watch Interval
            </label>
            <div style={{ display: 'flex', alignItems: 'center', gap: spacing.xs }}>
              <input
                className="input"
                type="number"
                value={interval}
                onChange={(e) => setInterval_(Math.max(5, parseInt(e.target.value) || 10))}
                disabled={monitorRunning}
                style={{ width: '70px', fontSize: font.size.sm }}
                min={5}
              />
              <span style={{ fontSize: font.size.xs, color: colors.textDim }}>seconds</span>
            </div>
          </div>
          <div>
            <label style={{ display: 'block', fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs }}>
              Dry Run
            </label>
            <button
              className={`btn ${dryRun ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setDryRun(!dryRun)}
              disabled={monitorRunning}
              style={{ fontSize: font.size.sm, padding: `4px ${spacing.md}` }}
            >
              {dryRun ? 'ON' : 'OFF'}
            </button>
          </div>
        </div>

        {/* Start / Stop */}
        <div style={{ display: 'flex', gap: spacing.sm, alignItems: 'center', marginBottom: spacing.md }}>
          {!monitorRunning ? (
            <button
              className="btn btn-primary"
              onClick={handleStartMonitor}
              disabled={monitorLoading || !apiKey.trim()}
              style={{ minWidth: '140px' }}
            >
              {monitorLoading ? 'Starting...' : '\u25B6 Start Monitor'}
            </button>
          ) : (
            <button
              className="btn btn-danger"
              onClick={handleStopMonitor}
              disabled={monitorLoading}
              style={{ minWidth: '140px' }}
            >
              {monitorLoading ? 'Stopping...' : '\u25A0 Stop Monitor'}
            </button>
          )}
          {monitorError && (
            <span style={{ fontSize: font.size.sm, color: colors.error }}>{monitorError}</span>
          )}
          {status?.monitor.last_error && !monitorRunning && (
            <span style={{ fontSize: font.size.sm, color: colors.warning }}>
              Last: {status.monitor.last_error}
            </span>
          )}
        </div>

        {/* Live Log */}
        {(monitorRunning || logs.length > 0) && (
          <div>
            <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs, fontWeight: font.weight.semibold }}>
              Live Log {monitorRunning && <span className="status-pulse" style={{ display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', backgroundColor: colors.success, marginLeft: spacing.xs }} />}
            </div>
            <pre
              ref={logRef}
              style={{
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
              }}
            >
              {logs.length > 0
                ? logs.map((line, i) => {
                    const isErr = line.includes('[err]') || line.includes('ERROR') || line.includes('error');
                    const isWarn = line.includes('DETECT') || line.includes('WARNING') || line.includes('degraded');
                    const isAction = line.includes('ACTION') || line.includes('RESOLVE') || line.includes('execute');
                    return (
                      <div key={i} style={{
                        color: isErr ? colors.error : isWarn ? colors.warning : isAction ? colors.success : colors.textMuted,
                      }}>
                        {line}
                      </div>
                    );
                  })
                : 'Waiting for output...'}
            </pre>
          </div>
        )}
      </div>

      {/* ── Instrument Agent Card (Expandable) ─────────────────────── */}
      <div style={{ ...cardStyle, marginBottom: spacing.lg }}>
        <div
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer', marginBottom: instrumentExpanded ? spacing.lg : 0 }}
          onClick={() => setInstrumentExpanded(!instrumentExpanded)}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
            <span style={{
              fontSize: '12px', color: colors.textDim, transition: 'transform 0.2s',
              transform: instrumentExpanded ? 'rotate(90deg)' : 'rotate(0deg)', display: 'inline-block',
            }}>{'\u25B6'}</span>
            <span style={{ fontSize: '16px' }}>{'\u2728'}</span>
            <span style={{ fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text }}>
              Instrument Agent
            </span>
            {status?.instrument.running && (
              <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
                Port 8082 &middot; {status.instrument.tools?.length || 0} tools registered
              </span>
            )}
          </div>
          <StatusBadge status={status?.instrument.running ? 'healthy' : 'unknown'} size="sm" />
        </div>

        {!instrumentExpanded && (
          <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.sm }}>
            MCP server for code analysis and instrumentation generation
          </div>
        )}

        {instrumentExpanded && (
          <div>
            <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.md }}>
              MCP server for code analysis and instrumentation generation
            </div>

            {/* Tool badges */}
            {status?.instrument.tools && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: spacing.xs, marginBottom: spacing.lg }}>
                {status.instrument.tools.map((t) => (
                  <span key={t} style={{
                    fontSize: font.size.xs, padding: '2px 8px', borderRadius: '4px',
                    backgroundColor: `${colors.primary}22`, color: colors.primaryLight, fontFamily: font.mono,
                  }}>{t}</span>
                ))}
              </div>
            )}

            {/* Activity Log */}
            <div style={{ marginBottom: spacing.lg }}>
              <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs, fontWeight: font.weight.semibold }}>
                Activity Log {status?.instrument.running && <span style={{ display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', backgroundColor: colors.success, marginLeft: spacing.xs }} />}
              </div>
              <LogViewer logs={instrumentLogs} logRef={instrumentLogRef} emptyMessage="No tool activity yet. Invoke suggest_improvements or apply_improvements to see output." />
            </div>

            {/* Managed Config */}
            {instrumentConfig?.config && (
              <div>
                <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs, fontWeight: font.weight.semibold }}>
                  Managed Config ({instrumentConfig.config_path})
                </div>
                <div style={{
                  backgroundColor: colors.bg, border: `1px solid ${colors.border}`, borderRadius: radii.md,
                  padding: spacing.md, fontFamily: font.mono, fontSize: font.size.xs, color: colors.textMuted, lineHeight: 1.8,
                }}>
                  <div>service: <span style={{ color: colors.text }}>{instrumentConfig.config.service}</span></div>
                  <div>team: <span style={{ color: colors.text }}>{instrumentConfig.config.team}</span> | tier: <span style={{ color: colors.text }}>{instrumentConfig.config.tier}</span></div>
                  {instrumentConfig.config.operations?.map((op: any) => (
                    <div key={op.name} style={{ marginTop: spacing.xs }}>
                      <span style={{ color: colors.primaryLight }}>{op.name}</span>
                      <span style={{ color: colors.textDim }}> [{op.profile}]</span>
                      {op.baseline && (
                        <span style={{ color: colors.textMuted }}>
                          {' '} p50: {op.baseline.p50}ms{op.baseline.p99 ? `, p99: ${op.baseline.p99}ms` : ''}
                        </span>
                      )}
                      {op.runbook_url && <span style={{ color: colors.success }}> (has runbook)</span>}
                      {!op.runbook_url && <span style={{ color: colors.warning }}> (no runbook)</span>}
                    </div>
                  ))}
                  {instrumentConfig.config.dependencies?.map((dep: any) => (
                    <div key={dep.name} style={{ marginTop: spacing.xs }}>
                      dep: <span style={{ color: colors.info }}>{dep.name}</span>
                      <span style={{ color: colors.textDim }}> ({dep.type}, {dep.criticality})</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Backend MCP Card (Expandable) ─────────────────────────── */}
      <div style={{ ...cardStyle, marginBottom: spacing.lg }}>
        <div
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer', marginBottom: backendExpanded ? spacing.lg : 0 }}
          onClick={() => setBackendExpanded(!backendExpanded)}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
            <span style={{
              fontSize: '12px', color: colors.textDim, transition: 'transform 0.2s',
              transform: backendExpanded ? 'rotate(90deg)' : 'rotate(0deg)', display: 'inline-block',
            }}>{'\u25B6'}</span>
            <span style={{ fontSize: '16px' }}>{'\u25C9'}</span>
            <span style={{ fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text }}>
              Backend MCP Server
            </span>
            {status?.backend.running && (
              <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
                Port 8081 &middot; {status.backend.tools?.length || 0} tools registered
              </span>
            )}
          </div>
          <StatusBadge status={status?.backend.running ? 'healthy' : 'unknown'} size="sm" />
        </div>

        {!backendExpanded && (
          <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.sm }}>
            9 telemetry tools for health, SLOs, incidents, and remediation
          </div>
        )}

        {backendExpanded && (
          <div>
            <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.md }}>
              9 telemetry tools for health, SLOs, incidents, and remediation
            </div>

            {/* Tool badges */}
            {status?.backend.tools && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: spacing.xs, marginBottom: spacing.lg }}>
                {status.backend.tools.map((t) => (
                  <span key={t} style={{
                    fontSize: font.size.xs, padding: '2px 8px', borderRadius: '4px',
                    backgroundColor: `${colors.info}22`, color: colors.info, fontFamily: font.mono,
                  }}>{t}</span>
                ))}
              </div>
            )}

            {/* Recent Agent Actions */}
            <div style={{ marginBottom: spacing.lg }}>
              <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs, fontWeight: font.weight.semibold }}>
                Recent Agent Actions {status?.backend.running && <span style={{ display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', backgroundColor: colors.success, marginLeft: spacing.xs }} />}
              </div>
              <LogViewer logs={backendLogs} logRef={backendLogRef} emptyMessage="No agent actions yet. Start the monitor agent to see remediation activity." />
            </div>

            {/* Service Configuration */}
            {(backendConfig.health || backendConfig.slo) && (
              <div>
                <div style={{ fontSize: font.size.xs, color: colors.textDim, marginBottom: spacing.xs, fontWeight: font.weight.semibold }}>
                  Service Configuration
                </div>
                <pre style={{
                  backgroundColor: colors.bg, border: `1px solid ${colors.border}`, borderRadius: radii.md,
                  padding: spacing.md, fontFamily: font.mono, fontSize: font.size.xs, color: colors.textMuted,
                  maxHeight: '200px', overflow: 'auto', margin: 0, lineHeight: 1.6, whiteSpace: 'pre-wrap',
                }}>
                  {backendConfig.health || ''}
                  {backendConfig.health && backendConfig.slo ? '\n\n' : ''}
                  {backendConfig.slo || ''}
                </pre>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── OTEL Collector Status ──────────────────────────────────── */}
      <div className="card-hover" style={{ ...cardStyle, marginBottom: spacing.lg }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
          <span style={{ fontSize: '16px' }}>{'\u2B22'}</span>
          <span style={{ fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text }}>
            OpenTelemetry Collector
          </span>
          <StatusBadge status={status?.collector.running ? 'healthy' : 'unknown'} size="sm" />
          <span style={{ fontSize: font.size.sm, color: colors.textDim, marginLeft: 'auto' }}>
            {status?.collector.running ? 'Receiving spans on port 4318' : 'Not connected'}
          </span>
        </div>
      </div>

      {/* Frontend telemetry generation is available in Generate Traffic panel */}
    </div>
  );
}

// ── Shared Log Viewer Component ──────────────────────────────────────

function LogViewer({ logs, logRef, emptyMessage }: {
  logs: string[];
  logRef: React.RefObject<HTMLPreElement>;
  emptyMessage?: string;
}) {
  return (
    <pre
      ref={logRef as React.LegacyRef<HTMLPreElement>}
      style={{
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
      }}
    >
      {logs.length > 0
        ? logs.map((line, i) => {
            const isErr = line.includes('ERROR') || line.includes('error') || line.includes('Failed');
            const isWarn = line.includes('WARNING') || line.includes('DETECT') || line.includes('degraded');
            const isAction = line.includes('Applied') || line.includes('IMPROVE') || line.includes('COMPLETED') || line.includes('execute') || line.includes('Registered');
            return (
              <div key={i} style={{
                color: isErr ? colors.error : isWarn ? colors.warning : isAction ? colors.success : colors.textMuted,
              }}>
                {line}
              </div>
            );
          })
        : (emptyMessage || 'No activity yet')}
    </pre>
  );
}
