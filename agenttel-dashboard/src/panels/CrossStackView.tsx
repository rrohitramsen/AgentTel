import { useMemo, useState } from 'react';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, card as cardStyle } from '../styles/theme';
import { TraceLink } from '../components/TraceLink';

interface ParsedCrossStack {
  frontendService: string;
  backendService: string;
  sections: { title: string; content: string }[];
  raw: string;
}

function parseCrossStack(raw: string): ParsedCrossStack {
  const sections: { title: string; content: string }[] = [];
  const lines = raw.split('\n');
  let currentTitle = '';
  let currentContent: string[] = [];
  let frontendService = 'checkout-web';
  let backendService = 'payment-service';

  for (const line of lines) {
    const frontMatch = line.match(/frontend.*?service[=:]\s*(\S+)/i);
    if (frontMatch) frontendService = frontMatch[1];
    const backMatch = line.match(/backend.*?service[=:]\s*(\S+)/i);
    if (backMatch) backendService = backMatch[1];

    const sectionMatch = line.match(/^##\s+(.+)/) || line.match(/^---\s*(.+?)\s*---/);
    if (sectionMatch) {
      if (currentTitle) sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
      currentTitle = sectionMatch[1];
      currentContent = [];
    } else {
      currentContent.push(line);
    }
  }
  if (currentTitle) sections.push({ title: currentTitle, content: currentContent.join('\n').trim() });
  if (sections.length === 0) sections.push({ title: 'Cross-Stack Context', content: raw.trim() });

  return { frontendService, backendService, sections, raw };
}

export function CrossStackView() {
  const client = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const { data, loading, error, lastUpdated } = useMcpTool(
    client,
    'get_cross_stack_context',
    { operation_name: 'POST /api/payments' },
    config.pollIntervals.crossStackView,
    parseCrossStack,
  );
  const [showRaw, setShowRaw] = useState(false);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['2xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['2xl'], fontWeight: font.weight.bold, color: colors.text }}>Cross-Stack View</h1>
          <p style={{ fontSize: font.size.sm, color: colors.textMuted, marginTop: spacing.xs }}>
            Frontend-to-backend trace correlation via W3C Trace Context
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
          {/* Visual Flow */}
          <div style={{ ...cardStyle, marginBottom: spacing['2xl'] }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: spacing.xl, padding: spacing.lg }}>
              <div style={{ textAlign: 'center' }}>
                <div
                  style={{
                    width: '80px',
                    height: '80px',
                    borderRadius: '50%',
                    background: `linear-gradient(135deg, ${colors.primary}, ${colors.primaryLight})`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '28px',
                    marginBottom: spacing.sm,
                  }}
                >
                  {'\uD83C\uDF10'}
                </div>
                <div style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }}>
                  {data.frontendService}
                </div>
                <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>Browser</div>
                <TraceLink service={data.frontendService} label="Traces" style={{ marginTop: spacing.xs }} />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: spacing.xs }}>
                <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>traceparent</div>
                <div style={{ width: '120px', height: '2px', background: `linear-gradient(90deg, ${colors.primary}, ${colors.info})` }} />
                <div style={{ fontSize: '16px' }}>{'\u2192'}</div>
              </div>

              <div style={{ textAlign: 'center' }}>
                <div
                  style={{
                    width: '80px',
                    height: '80px',
                    borderRadius: '50%',
                    background: `linear-gradient(135deg, ${colors.info}, ${colors.primaryLight})`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '28px',
                    marginBottom: spacing.sm,
                  }}
                >
                  {'\u2699'}
                </div>
                <div style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }}>
                  {data.backendService}
                </div>
                <div style={{ fontSize: font.size.xs, color: colors.textMuted }}>Backend</div>
                <TraceLink service={data.backendService} label="Traces" style={{ marginTop: spacing.xs }} />
              </div>
            </div>
          </div>

          {/* Detail Sections */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.md }}>
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
