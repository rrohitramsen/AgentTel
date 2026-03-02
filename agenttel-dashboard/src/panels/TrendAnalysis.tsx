import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
import { TrendChart } from '../components/TrendChart';
import { MetricCard } from '../components/MetricCard';

interface TrendMetric {
  label: string;
  current: string;
  trend: 'up' | 'down' | 'stable';
  data: { value: number }[];
}

interface ParsedTrends {
  metrics: TrendMetric[];
  raw: string;
}

function parseTrend(s: string): 'up' | 'down' | 'stable' {
  const lower = s.toLowerCase();
  if (lower === 'increasing') return 'up';
  if (lower === 'decreasing') return 'down';
  return 'stable';
}

function parseTrends(raw: string): ParsedTrends {
  const metrics: TrendMetric[] = [];
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
          const dataPoints: { value: number }[] = [];
          if (minMatch) dataPoints.push({ value: parseInt(minMatch[1]) });
          if (avgMatch) dataPoints.push({ value: parseInt(avgMatch[1]) });
          if (maxMatch) dataPoints.push({ value: parseInt(maxMatch[1]) });
          dataPoints.push({ value: parseInt(curMatch[1]) });
          metrics.push({
            label: currentLabel,
            current: `${curMatch[1]}ms`,
            trend: trendMatch ? parseTrend(trendMatch[1]) : 'stable',
            data: dataPoints,
          });
          currentLabel = '';
        }
      } else if (currentLabel === 'ERROR RATE') {
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
      } else if (currentLabel === 'THROUGHPUT') {
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
  const { data, loading, error, lastUpdated } = useMcpTool(
    client,
    'get_trend_analysis',
    { operation_name: 'POST /api/payments', window_minutes: '30' },
    config.pollIntervals.trendAnalysis,
    parseTrends,
  );
  const [showRaw, setShowRaw] = useState(false);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Trend Analysis</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Latency, error rate, and throughput trends for POST /api/payments
          </p>
        </div>
        {lastUpdated && (
          <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
            Updated {lastUpdated.toLocaleTimeString()}
          </span>
        )}
      </div>

      {loading && !data && <div className="skeleton" style={{ height: '150px' }} />}

      {error && (
        <div style={{ ...cardStyle, borderColor: colors.error, color: colors.error }}>{error}</div>
      )}

      {data && (
        <>
          {data.metrics.length > 0 && (
            <div className="grid-3" style={{ marginBottom: spacing['2xl'] }}>
              {data.metrics.map((m) => (
                <MetricCard key={m.label} label={m.label} value={m.current} trend={m.trend}>
                  {m.data.length > 1 && (
                    <TrendChart
                      data={m.data}
                      height={40}
                      color={m.trend === 'up' && m.label.includes('Error') ? colors.error : colors.primaryLight}
                    />
                  )}
                </MetricCard>
              ))}
            </div>
          )}

          {/* Always show the full response in a structured view */}
          <div style={cardStyle}>
            <div style={{ fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.text, marginBottom: spacing.md }}>
              Full Analysis
            </div>
            <pre
              style={{
                fontFamily: font.mono,
                fontSize: font.size.sm,
                color: colors.textMuted,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                margin: 0,
                lineHeight: 1.6,
              }}
            >
              {data.raw}
            </pre>
          </div>
        </>
      )}
    </div>
  );
}
