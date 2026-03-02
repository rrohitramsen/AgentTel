import { useMemo } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, riskColors } from '../styles/theme';
import { detectFeedbackEvents } from '../feedback/detector';
import { triggerLabels } from '../feedback/types';
import type { FeedbackEvent } from '../feedback/types';

interface GapData {
  events: FeedbackEvent[];
  healthRaw: string;
}

function parseGaps(raw: string): GapData {
  const events = detectFeedbackEvents(raw, '');
  return { events, healthRaw: raw };
}

export function CoverageGaps() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const { data, loading, error, lastUpdated } = useMcpTool(
    client, 'get_service_health', { format: 'text' }, config.pollIntervals.coverageGaps, parseGaps,
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Coverage Gaps</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            What is NOT instrumented — gaps that limit autonomous operation
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
          {data.events.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.md }}>
              {data.events.map((event, idx) => (
                <div
                  key={idx}
                  className="card-hover"
                  style={{
                    ...cardStyle,
                    borderLeft: `3px solid ${riskColors[event.riskLevel]}`,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }}>
                    <span style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }}>
                      {triggerLabels[event.trigger]}
                    </span>
                    <span
                      style={{
                        fontSize: '10px',
                        padding: '2px 8px',
                        borderRadius: '4px',
                        backgroundColor: `${riskColors[event.riskLevel]}22`,
                        color: riskColors[event.riskLevel],
                        fontWeight: font.weight.semibold,
                        textTransform: 'uppercase',
                      }}
                    >
                      {event.riskLevel} risk
                    </span>
                  </div>
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.xs }}>
                    Target: <span style={{ color: colors.text, fontFamily: font.mono }}>{event.target}</span>
                  </div>
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted }}>{event.reasoning}</div>
                </div>
              ))}
            </div>
          ) : (
            <div style={{ ...cardStyle, textAlign: 'center', padding: spacing['3xl'], color: colors.textDim }}>
              <div style={{ fontSize: '32px', marginBottom: spacing.md }}>{'\u2705'}</div>
              <div style={{ fontSize: font.size.md }}>No coverage gaps detected</div>
              <div style={{ fontSize: font.size.sm, marginTop: spacing.xs }}>
                All operations have baselines, runbooks, and health checks
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
