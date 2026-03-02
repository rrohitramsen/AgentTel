import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, statusColors, statusBgColors } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
import { MetricCard } from '../components/MetricCard';
import { TraceLink } from '../components/TraceLink';

interface Operation {
  name: string;
  status: string;
  errorRate: string;
  p50: string;
  p99: string;
  throughput: string;
}

interface Dependency {
  name: string;
  errorRate: string;
  latency: string;
  calls: string;
}

interface ParsedHealth {
  service: string;
  status: string;
  operations: Operation[];
  dependencies: Dependency[];
  raw: string;
}

function parseHealth(raw: string): ParsedHealth {
  const lines = raw.split('\n');
  let service = 'unknown';
  let status = 'unknown';
  const operations: Operation[] = [];
  const dependencies: Dependency[] = [];

  for (const line of lines) {
    // SERVICE: payments-platform | STATUS: HEALTHY | 2026-...
    const serviceMatch = line.match(/^SERVICE:\s*(\S+)\s*\|\s*STATUS:\s*(\w+)/i);
    if (serviceMatch) {
      service = serviceMatch[1];
      status = serviceMatch[2].toLowerCase();
    }

    // "  POST /api/payments: err=0.0% p50=2ms p99=78ms" or "  GET: err=0.0% p50=20ms p99=59ms"
    const opMatch = line.match(/^\s{2}(\S+(?:\s+\S+)*?):\s+err=([0-9.]+)%\s+p50=(\d+)ms\s+p99=(\d+)ms/);
    if (opMatch) {
      operations.push({
        name: opMatch[1],
        status: parseFloat(opMatch[2]) > 1 ? 'degraded' : 'healthy',
        errorRate: opMatch[2],
        p50: opMatch[3],
        p99: opMatch[4],
        throughput: '-',
      });
    }
  }

  return { service, status, operations, dependencies, raw };
}

export function FleetOverview() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const { data, loading, error, lastUpdated } = useMcpTool(
    client, 'get_service_health', { format: 'text' }, config.pollIntervals.fleetOverview, parseHealth,
  );
  const [showRaw, setShowRaw] = useState(false);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Fleet Health</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Service health, operations, and dependencies
          </p>
        </div>
        {lastUpdated && (
          <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
            Updated {lastUpdated.toLocaleTimeString()}
          </span>
        )}
      </div>

      {loading && !data && <div className="skeleton" style={{ height: '200px' }} />}

      {error && (
        <div style={{ ...cardStyle, borderColor: colors.error, color: colors.error }}>
          <strong>Connection Error:</strong> {error}
          <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.sm }}>
            Make sure the MCP server is running at {config.mcpBaseUrl}
          </div>
        </div>
      )}

      {data && (
        <>
          {/* Service Status Card */}
          <div
            className="card-hover"
            style={{
              ...cardStyle,
              borderLeft: `3px solid ${statusColors[data.status] || colors.textMuted}`,
              marginBottom: spacing['2xl'],
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.lg }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
                <span style={{ fontSize: font.size.xl, fontWeight: font.weight.bold, color: colors.text }}>
                  {data.service}
                </span>
                <StatusBadge status={data.status} />
              </div>
              <TraceLink service={data.service} label="View Traces" />
            </div>

            <div className="grid-kpi">
              <MetricCard
                label="Operations"
                value={String(data.operations.length || 'N/A')}
                subtitle="tracked endpoints"
              />
              <MetricCard
                label="Dependencies"
                value={String(data.dependencies.length || 'N/A')}
                subtitle="external services"
              />
              <MetricCard
                label="Avg Error Rate"
                value={
                  data.operations.length
                    ? `${(data.operations.reduce((s, o) => s + parseFloat(o.errorRate), 0) / data.operations.length).toFixed(2)}%`
                    : 'N/A'
                }
                color={
                  data.operations.some((o) => parseFloat(o.errorRate) > 1) ? colors.error : colors.success
                }
              />
            </div>
          </div>

          {/* Operations Table */}
          {data.operations.length > 0 && (
            <div style={{ ...cardStyle, marginBottom: spacing['2xl'] }}>
              <div style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }}>
                Operations
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: font.size.md }}>
                  <thead>
                    <tr>
                      {['Operation', 'Status', 'Error Rate', 'P50', 'P99', 'Throughput', ''].map((h) => (
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
                    {data.operations.map((op) => (
                      <tr key={op.name} className="row-hover" style={{ borderBottom: `1px solid ${colors.border}` }}>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontFamily: font.mono, fontSize: font.size.sm }}>
                          {op.name}
                        </td>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}` }}>
                          <StatusBadge status={op.status} size="sm" />
                        </td>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: parseFloat(op.errorRate) > 1 ? colors.error : colors.text, fontFamily: font.mono }}>
                          {op.errorRate}%
                        </td>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }}>
                          {op.p50}ms
                        </td>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }}>
                          {op.p99}ms
                        </td>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }}>
                          {op.throughput} ops
                        </td>
                        <td style={{ padding: `${spacing.sm} ${spacing.md}` }}>
                          <TraceLink service="payment-service" operation={op.name} label="Traces" />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Dependencies */}
          {data.dependencies.length > 0 && (
            <div style={{ ...cardStyle, marginBottom: spacing['2xl'] }}>
              <div style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }}>
                Dependencies
              </div>
              <div className="grid-auto">
                {data.dependencies.map((dep) => (
                  <div key={dep.name} className="card-hover" style={cardStyle}>
                    <div style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.sm }}>
                      {dep.name}
                    </div>
                    <div style={{ display: 'flex', gap: spacing.xl, fontSize: font.size.sm, color: colors.textMuted }}>
                      <span>err: <span style={{ color: parseFloat(dep.errorRate) > 1 ? colors.error : colors.text }}>{dep.errorRate}%</span></span>
                      <span>lat: {dep.latency}ms</span>
                      <span>{dep.calls} calls</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Collapsible Raw */}
          <button className="collapsible-header" onClick={() => setShowRaw(!showRaw)}>
            {showRaw ? '\u25BC' : '\u25B6'} Raw MCP Response
          </button>
          {showRaw && (
            <pre style={{ ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {data.raw}
            </pre>
          )}
        </>
      )}
    </div>
  );
}
