import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';

interface AgentAction {
  timestamp: string;
  type: string;
  detail: string;
}

interface ParsedActions {
  actions: AgentAction[];
  raw: string;
}

function parseActions(raw: string): ParsedActions {
  const actions: AgentAction[] = [];
  const lines = raw.split('\n');

  for (const line of lines) {
    const match = line.match(/^\s*\[([^\]]+)\]\s*(?:\[(\w+)\])?\s*(.+)/);
    if (match) {
      actions.push({
        timestamp: match[1],
        type: match[2] || 'info',
        detail: match[3],
      });
    }
  }

  return { actions, raw };
}

const typeColors: Record<string, string> = {
  check: colors.info,
  detect: colors.warning,
  action: colors.primary,
  resolve: colors.success,
  error: colors.error,
  info: colors.textMuted,
};

export function MonitorDecisions() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const { data, loading, error, lastUpdated } = useMcpTool(
    client, 'get_recent_agent_actions', {}, config.pollIntervals.monitorDecisions, parseActions,
  );
  const [showRaw, setShowRaw] = useState(false);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Monitor Agent</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Recent AI agent actions, decisions, and reasoning
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
        <div style={{ ...cardStyle, borderColor: colors.error, color: colors.error }}>{error}</div>
      )}

      {data && (
        <>
          {/* Timeline */}
          <div style={cardStyle}>
            {data.actions.length > 0 ? (
              <div style={{ position: 'relative' }}>
                {/* Timeline line */}
                <div
                  style={{
                    position: 'absolute',
                    left: '7px',
                    top: '8px',
                    bottom: '8px',
                    width: '2px',
                    backgroundColor: colors.border,
                  }}
                />

                {data.actions.map((action, i) => {
                  const color = typeColors[action.type.toLowerCase()] || colors.textMuted;
                  return (
                    <div
                      key={i}
                      style={{
                        display: 'flex',
                        gap: spacing.lg,
                        padding: `${spacing.md} 0`,
                        paddingLeft: spacing['2xl'],
                        position: 'relative',
                        animation: 'slideIn 0.2s ease-out',
                      }}
                    >
                      {/* Timeline dot */}
                      <div
                        style={{
                          position: 'absolute',
                          left: '3px',
                          top: '16px',
                          width: '10px',
                          height: '10px',
                          borderRadius: '50%',
                          backgroundColor: color,
                          boxShadow: `0 0 6px ${color}`,
                        }}
                      />

                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md, marginBottom: spacing.xs }}>
                          <span style={{ fontSize: font.size.xs, color: colors.textDim, fontFamily: font.mono }}>
                            {action.timestamp}
                          </span>
                          {action.type !== 'info' && (
                            <span
                              style={{
                                fontSize: '10px',
                                padding: '1px 6px',
                                borderRadius: '3px',
                                backgroundColor: `${color}22`,
                                color,
                                fontWeight: font.weight.semibold,
                                textTransform: 'uppercase',
                              }}
                            >
                              {action.type}
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: font.size.md, color: colors.text }}>{action.detail}</div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: spacing['3xl'], color: colors.textDim }}>
                <div style={{ fontSize: '32px', marginBottom: spacing.md }}>{'\uD83E\uDD16'}</div>
                <div style={{ fontSize: font.size.md }}>No agent activity yet</div>
                <div style={{ fontSize: font.size.sm, color: colors.textDim, marginTop: spacing.xs }}>
                  Start the monitor agent to see decisions here
                </div>
              </div>
            )}
          </div>

          <button className="collapsible-header" onClick={() => setShowRaw(!showRaw)} style={{ marginTop: spacing.md }}>
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
