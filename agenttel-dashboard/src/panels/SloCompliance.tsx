import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, statusColors } from '../styles/theme';
import { SloGauge } from '../components/SloGauge';
import { StatusBadge } from '../components/StatusBadge';

interface SloEntry {
  name: string;
  target: string;
  actual: string;
  budget: number;
  burnRate: number;
  status: string;
}

interface ParsedSloReport {
  slos: SloEntry[];
  raw: string;
}

function parseSloReport(raw: string): ParsedSloReport {
  const slos: SloEntry[] = [];
  const lines = raw.split('\n');
  let currentName = '';
  let currentStatus = '';

  for (const line of lines) {
    // "  [HEALTHY] payment-availability"
    const nameMatch = line.match(/^\s*\[(\w+)\]\s+(.+)/);
    if (nameMatch) {
      currentStatus = nameMatch[1].toLowerCase();
      currentName = nameMatch[2].trim();
      continue;
    }

    // "    Target: 99.90%  Actual: 100.00%  Budget: 100.00%  Burn: 0.00x  Requests: 124  Failed: 0"
    if (currentName) {
      const detailMatch = line.match(
        /Target:\s*([0-9.]+)%\s+Actual:\s*([0-9.]+)%\s+Budget:\s*([0-9.]+)%\s+Burn:\s*([0-9.]+)x/
      );
      if (detailMatch) {
        slos.push({
          name: currentName,
          target: `${detailMatch[1]}%`,
          actual: `${detailMatch[2]}%`,
          budget: parseFloat(detailMatch[3]),
          burnRate: parseFloat(detailMatch[4]),
          status: currentStatus,
        });
        currentName = '';
      }
    }
  }

  return { slos, raw };
}

export function SloCompliance() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const { data, loading, error, lastUpdated } = useMcpTool(
    client, 'get_slo_report', { format: 'text' }, config.pollIntervals.sloCompliance, parseSloReport,
  );
  const [showRaw, setShowRaw] = useState(false);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>SLO Compliance</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Service level objective budgets, burn rates, and compliance status
          </p>
        </div>
        {lastUpdated && (
          <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
            Updated {lastUpdated.toLocaleTimeString()}
          </span>
        )}
      </div>

      {loading && !data && <div className="skeleton" style={{ height: '120px' }} />}

      {error && (
        <div style={{ ...cardStyle, borderColor: colors.error, color: colors.error }}>{error}</div>
      )}

      {data && (
        <>
          {/* SLO Gauges Grid */}
          {data.slos.length > 0 ? (
            <div className="grid-auto" style={{ marginBottom: spacing['2xl'] }}>
              {data.slos.map((slo) => (
                <SloGauge
                  key={slo.name}
                  name={slo.name}
                  budgetPercent={slo.budget}
                  burnRate={slo.burnRate}
                  status={slo.status}
                />
              ))}
            </div>
          ) : (
            <div style={{ ...cardStyle, marginBottom: spacing['2xl'] }}>
              <pre style={{ fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap', margin: 0, lineHeight: 1.6 }}>
                {data.raw}
              </pre>
            </div>
          )}

          {/* SLO Detail Table */}
          {data.slos.length > 0 && (
            <div style={{ ...cardStyle, marginBottom: spacing['2xl'] }}>
              <div style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.lg }}>
                Detail
              </div>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: font.size.md }}>
                <thead>
                  <tr>
                    {['SLO', 'Status', 'Target', 'Actual', 'Budget', 'Burn Rate'].map((h) => (
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
                        }}
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.slos.map((slo) => (
                    <tr key={slo.name} className="row-hover" style={{ borderBottom: `1px solid ${colors.border}` }}>
                      <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontWeight: font.weight.medium }}>{slo.name}</td>
                      <td style={{ padding: `${spacing.sm} ${spacing.md}` }}><StatusBadge status={slo.status} size="sm" /></td>
                      <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.textMuted, fontFamily: font.mono }}>{slo.target}</td>
                      <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: colors.text, fontFamily: font.mono }}>{slo.actual}</td>
                      <td style={{ padding: `${spacing.sm} ${spacing.md}` }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
                          <div className="progress-bar" style={{ width: '60px' }}>
                            <div className="progress-fill" style={{ width: `${Math.min(slo.budget, 100)}%`, background: statusColors[slo.status] || colors.primary }} />
                          </div>
                          <span style={{ fontFamily: font.mono, fontSize: font.size.sm, color: colors.text }}>{slo.budget.toFixed(1)}%</span>
                        </div>
                      </td>
                      <td style={{ padding: `${spacing.sm} ${spacing.md}`, color: slo.burnRate > 1 ? colors.warning : colors.textMuted, fontFamily: font.mono, fontWeight: font.weight.semibold }}>
                        {slo.burnRate.toFixed(1)}x
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <button className="collapsible-header" onClick={() => setShowRaw(!showRaw)}>
            {showRaw ? '\u25BC' : '\u25B6'} Raw MCP Response
          </button>
          {showRaw && (
            <pre style={{ ...cardStyle, fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted, whiteSpace: 'pre-wrap' }}>
              {data.raw}
            </pre>
          )}
        </>
      )}
    </div>
  );
}
