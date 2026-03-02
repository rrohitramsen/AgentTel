import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle, statusColors } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';

interface IncidentSection {
  title: string;
  content: string;
}

interface ParsedIncident {
  severity: string;
  summary: string;
  sections: IncidentSection[];
  raw: string;
}

function parseIncident(raw: string): ParsedIncident {
  let severity = 'unknown';
  let summary = '';
  const sections: IncidentSection[] = [];
  const lines = raw.split('\n');
  let currentTitle = '';
  let currentContent: string[] = [];

  for (const line of lines) {
    const sevMatch = line.match(/SEVERITY:\s*(\w+)/i);
    if (sevMatch) severity = sevMatch[1].toLowerCase();

    const sumMatch = line.match(/SUMMARY:\s*(.+)/i);
    if (sumMatch) summary = sumMatch[1];

    const sectionMatch = line.match(/^##\s+(.+)/);
    if (sectionMatch) {
      if (currentTitle) sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
      currentTitle = sectionMatch[1];
      currentContent = [];
    } else if (currentTitle) {
      currentContent.push(line);
    }
  }
  if (currentTitle) sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });

  return { severity, summary, sections, raw };
}

const severityColors: Record<string, string> = {
  high: colors.error,
  critical: colors.critical,
  medium: colors.warning,
  low: colors.info,
};

export function IncidentContext() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const [operationName, setOperationName] = useState('POST /api/payments');
  const [data, setData] = useState<ParsedIncident | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showRaw, setShowRaw] = useState(false);

  const fetchIncident = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await client.callTool('get_incident_context', { operation_name: operationName });
      setData(parseIncident(result));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={{ marginBottom: spacing['2xl'] }}>
        <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>
          Incident Context
        </h1>
        <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
          Structured incident packages optimized for LLM reasoning
        </p>
      </div>

      {/* Search Bar */}
      <div style={{ display: 'flex', gap: spacing.sm, marginBottom: spacing['2xl'] }}>
        <input
          className="input"
          type="text"
          value={operationName}
          onChange={(e) => setOperationName(e.target.value)}
          placeholder="Operation name (e.g. POST /api/payments)"
          style={{ flex: 1 }}
          onKeyDown={(e) => e.key === 'Enter' && fetchIncident()}
        />
        <button className="btn btn-primary" onClick={fetchIncident} disabled={loading}>
          {loading ? 'Loading...' : 'Get Context'}
        </button>
      </div>

      {error && (
        <div style={{ ...cardStyle, borderColor: colors.error, color: colors.error, marginBottom: spacing.lg }}>
          {error}
        </div>
      )}

      {data && (
        <>
          {/* Incident Header */}
          {(data.severity !== 'unknown' || data.summary) && (
            <div
              style={{
                ...cardStyle,
                borderLeft: `3px solid ${severityColors[data.severity] || colors.textMuted}`,
                marginBottom: spacing.lg,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md, marginBottom: spacing.sm }}>
                <StatusBadge status={data.severity} />
                <span style={{ fontSize: font.size.xs, color: colors.textDim }}>Incident</span>
              </div>
              {data.summary && (
                <div style={{ fontSize: font.size.lg, color: colors.text, fontWeight: font.weight.medium }}>
                  {data.summary}
                </div>
              )}
            </div>
          )}

          {/* Incident Sections */}
          {data.sections.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.md, marginBottom: spacing.lg }}>
              {data.sections.map((section, i) => (
                <div key={i} style={cardStyle}>
                  <div style={{ fontSize: font.size.lg, fontWeight: font.weight.semibold, color: colors.primaryLight, marginBottom: spacing.md }}>
                    {section.title}
                  </div>
                  <pre
                    style={{
                      fontFamily: font.mono,
                      fontSize: font.size.sm,
                      color: colors.text,
                      whiteSpace: 'pre-wrap',
                      margin: 0,
                      lineHeight: 1.6,
                    }}
                  >
                    {section.content}
                  </pre>
                </div>
              ))}
            </div>
          ) : (
            <div style={cardStyle}>
              <pre style={{ fontFamily: font.mono, fontSize: font.size.sm, color: colors.text, whiteSpace: 'pre-wrap', margin: 0, lineHeight: 1.6 }}>
                {data.raw}
              </pre>
            </div>
          )}

          {data.sections.length > 0 && (
            <>
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
        </>
      )}

      {!data && !loading && !error && (
        <div style={{ ...cardStyle, textAlign: 'center', padding: spacing['3xl'], color: colors.textDim }}>
          <div style={{ fontSize: '32px', marginBottom: spacing.md }}>{'\u26A0'}</div>
          <div style={{ fontSize: font.size.md }}>Enter an operation name and click "Get Context"</div>
          <div style={{ fontSize: font.size.sm, marginTop: spacing.xs }}>
            View structured incident packages for any tracked operation
          </div>
        </div>
      )}
    </div>
  );
}
