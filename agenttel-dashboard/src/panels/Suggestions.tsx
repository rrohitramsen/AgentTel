import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, riskColors } from '../styles/theme';
import { triggerLabels, riskLabels } from '../feedback/types';
import type { FeedbackEvent } from '../api/types';

interface SuggestionData {
  suggestions: FeedbackEvent[];
  raw: string;
}

function parseSuggestions(raw: string): SuggestionData {
  try {
    const parsed = JSON.parse(raw);
    if (parsed.suggestions) {
      return {
        suggestions: parsed.suggestions.map((s: Record<string, unknown>) => ({
          trigger: s.trigger as string,
          risk_level: s.risk_level as string,
          target: s.target as string,
          current_value: s.current_value as string | undefined,
          suggested_value: s.suggested_value as string | undefined,
          reasoning: s.reasoning as string,
          auto_applicable: s.auto_applicable as boolean,
        })),
        raw,
      };
    }
  } catch {
    // Fall through
  }
  return { suggestions: [], raw };
}

export function Suggestions() {
  const instrumentClient = useMemo(() => new McpClient(config.instrumentBaseUrl), []);
  const { data, loading, error, lastUpdated, refresh } = useMcpTool(
    instrumentClient,
    'suggest_improvements',
    { config_path: './agenttel.yml' },
    config.pollIntervals.suggestions,
    parseSuggestions,
  );

  const [applyResult, setApplyResult] = useState<string | null>(null);
  const [applyingAll, setApplyingAll] = useState(false);

  const handleApplyAll = async () => {
    setApplyingAll(true);
    try {
      const result = await instrumentClient.callTool('apply_improvements', {
        config_path: './agenttel.yml',
      });
      const parsed = JSON.parse(result);
      const count = parsed.applied_count || 0;
      const lines: string[] = [];
      if (count > 0) {
        lines.push(`Auto-applied ${count} improvement${count > 1 ? 's' : ''}:`);
        for (const a of parsed.applied || []) lines.push(`  ${a}`);
      }
      if (parsed.pending_count > 0) {
        lines.push(`${parsed.pending_count} item${parsed.pending_count > 1 ? 's' : ''} need human review:`);
        for (const p of parsed.pending || []) lines.push(`  [${p.risk_level.toUpperCase()}] ${p.trigger}: ${p.target}`);
      }
      if (count === 0 && !parsed.pending_count) {
        lines.push('No changes needed at this time.');
      }
      setApplyResult(lines.join('\n'));
      refresh();
    } catch (e) {
      setApplyResult(`Error: ${e instanceof Error ? e.message : 'Unknown'}`);
    } finally {
      setApplyingAll(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Suggestions</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Feedback engine recommendations to improve telemetry coverage
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
          {data && data.suggestions.length > 0 && (
            <button
              className="btn btn-primary"
              onClick={handleApplyAll}
              disabled={applyingAll}
              style={{ padding: '6px 20px', fontSize: font.size.sm }}
            >
              {applyingAll ? 'Applying...' : 'Auto-fix from Live Traffic'}
            </button>
          )}
          {lastUpdated && (
            <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
              Updated {lastUpdated.toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      {loading && !data && <div className="skeleton" style={{ height: '150px' }} />}

      {error && (
        <div style={{ ...cardStyle, borderColor: colors.error, marginBottom: spacing.lg }}>
          <div style={{ color: colors.error, fontWeight: font.weight.semibold }}>Instrumentation Server</div>
          <div style={{ color: colors.error, fontSize: font.size.sm, marginTop: spacing.xs }}>{error}</div>
          <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.sm }}>
            Make sure the instrument MCP server is running at {config.instrumentBaseUrl}
          </div>
        </div>
      )}

      {applyResult && (
        <div
          style={{
            ...cardStyle,
            borderColor: colors.info,
            marginBottom: spacing.lg,
            animation: 'fadeIn 0.2s ease-out',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }}>
            <span style={{ fontSize: font.size.sm, fontWeight: font.weight.semibold, color: colors.info }}>Apply Result</span>
            <button
              className="collapsible-header"
              onClick={() => setApplyResult(null)}
              style={{ padding: 0, fontSize: font.size.sm }}
            >
              Dismiss
            </button>
          </div>
          <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: font.mono, fontSize: font.size.sm, color: colors.text }}>
            {applyResult}
          </pre>
        </div>
      )}

      {data && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.md }}>
          {data.suggestions.length > 0 ? (
            data.suggestions.map((event, idx) => {
              const riskLevel = event.risk_level || 'medium';
              const trigger = event.trigger || 'unknown';
              return (
                <div
                  key={idx}
                  className="card-hover"
                  style={{
                    ...cardStyle,
                    borderLeft: `3px solid ${riskColors[riskLevel] || colors.textMuted}`,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }}>
                    <span style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }}>
                      {triggerLabels[trigger as keyof typeof triggerLabels] || trigger}
                    </span>
                    <div style={{ display: 'flex', gap: spacing.sm, alignItems: 'center' }}>
                      <span
                        style={{
                          fontSize: '10px',
                          padding: '2px 8px',
                          borderRadius: '4px',
                          backgroundColor: `${riskColors[riskLevel] || colors.textMuted}22`,
                          color: riskColors[riskLevel] || colors.textMuted,
                          fontWeight: font.weight.semibold,
                          textTransform: 'uppercase',
                        }}
                      >
                        {riskLabels[riskLevel as keyof typeof riskLabels] || riskLevel}
                      </span>
                      <span style={{
                        fontSize: '10px', padding: '2px 8px', borderRadius: '4px',
                        backgroundColor: event.auto_applicable ? 'rgba(30, 142, 62, 0.08)' : 'rgba(0, 0, 0, 0.04)',
                        color: event.auto_applicable ? colors.success : colors.textDim,
                      }}>
                        {event.auto_applicable ? 'auto-fixable' : 'manual'}
                      </span>
                    </div>
                  </div>
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted }}>
                    Target: <span style={{ color: colors.text, fontFamily: font.mono }}>{event.target}</span>
                  </div>
                  {event.current_value && (
                    <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
                      <span style={{ fontFamily: font.mono }}>{event.current_value}</span>
                      {event.suggested_value && (
                        <> {'\u2192'} <span style={{ color: colors.success, fontFamily: font.mono }}>{event.suggested_value}</span></>
                      )}
                    </div>
                  )}
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>{event.reasoning}</div>
                </div>
              );
            })
          ) : (
            <div style={cardStyle}>
              <pre style={{ whiteSpace: 'pre-wrap', fontSize: font.size.sm, color: colors.textMuted, margin: 0, fontFamily: font.mono, lineHeight: 1.6 }}>
                {data.raw}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
