import { useMemo } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';

function parseSummary(raw: string): { sections: { title: string; content: string }[]; raw: string } {
  const sections: { title: string; content: string }[] = [];
  const lines = raw.split('\n');
  let currentTitle = '';
  let currentContent: string[] = [];

  for (const line of lines) {
    const headerMatch = line.match(/^##\s+(.+)/) || line.match(/^===\s*(.+?)\s*===/);
    if (headerMatch) {
      if (currentTitle) sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
      currentTitle = headerMatch[1];
      currentContent = [];
    } else {
      currentContent.push(line);
    }
  }
  if (currentTitle) sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });

  if (sections.length === 0) {
    sections.push({ title: 'Summary', content: raw.trim() });
  }

  return { sections, raw };
}

export function ExecutiveSummary() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const { data, loading, error, lastUpdated } = useMcpTool(
    client, 'get_executive_summary', {}, config.pollIntervals.executiveSummary, parseSummary,
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Executive Summary</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            ~300 token LLM-optimized overview of system state
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
        <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.lg }}>
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
                  wordBreak: 'break-word',
                  margin: 0,
                  lineHeight: 1.7,
                }}
              >
                {section.content}
              </pre>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
