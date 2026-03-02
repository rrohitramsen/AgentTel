import { useState, useEffect, useCallback, useRef } from 'react';
import { McpClient } from '../api/mcp-client';
import { colors, font, spacing, radii, card as cardStyle, riskColors } from '../styles/theme';

// ── Types ────────────────────────────────────────────────────

type MessageRole = 'agent' | 'user';
type MessageType = 'text' | 'finding' | 'summary';

interface ChatMessage {
  id: string;
  role: MessageRole;
  type: MessageType;
  content: string;
  timestamp: Date;
  finding?: Finding;
  findingIndex?: number;
  status?: 'pending' | 'approved' | 'skipped' | 'applied' | 'failed';
}

interface Finding {
  id: number;
  trigger: string;
  risk_level: string;
  target: string;
  current_value: string | null;
  suggested_value: string | null;
  reasoning: string;
  auto_applicable: boolean;
}

interface DryRunResult {
  config_path: string;
  dry_run: boolean;
  findings: Finding[];
  total_findings: number;
  by_risk: { low: number; medium: number; high: number };
  health_data_available: boolean;
}

type ConversationPhase = 'idle' | 'scanning' | 'presenting' | 'applying' | 'complete';

interface ImproveAgentProps {
  instrumentClient: McpClient;
  onRefreshSuggestions: () => void;
}

// ── Helpers ──────────────────────────────────────────────────

let msgCounter = 0;
function makeId(): string {
  return `msg-${++msgCounter}-${Date.now()}`;
}

function formatTrigger(trigger: string): string {
  return trigger.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

function buildDetailedExplanation(f: Finding): string {
  const explanations: Record<string, (f: Finding) => string> = {
    missing_baseline: (f) =>
      `Without baselines for "${f.target}", the anomaly detection engine cannot determine if ` +
      `response times are normal or degraded. Agents cannot make autonomous scaling or alerting ` +
      `decisions for this operation. Approving will set the baseline from observed traffic.`,
    stale_baseline: (f) =>
      `The configured baseline for "${f.target}" differs significantly from live traffic ` +
      `(${f.current_value} vs ${f.suggested_value}). Stale baselines cause false positives or ` +
      `missed anomalies. Approving will update the baseline to match current traffic patterns.`,
    missing_runbook: (f) =>
      `Operation "${f.target}" has no runbook URL. When an incident occurs, agents need runbooks ` +
      `to look up resolution steps. Without one, the monitor agent cannot suggest remediation. ` +
      `Approving will add a placeholder URL you should replace with your actual runbook.`,
    slo_burn_rate_high: (f) =>
      `The SLO "${f.target}" is burning error budget at ${f.current_value}. At this rate, you ` +
      `will exhaust your error budget soon. This may indicate baselines need recalibration or a ` +
      `real performance regression.`,
    uncovered_endpoint: (f) =>
      `Endpoint "${f.target}" is receiving traffic but has no agenttel.yml config entry. No ` +
      `baselines, no SLO coverage, and agents cannot monitor it.`,
  };
  return explanations[f.trigger]?.(f) || f.reasoning;
}

const WELCOME: ChatMessage = {
  id: 'welcome',
  role: 'agent',
  type: 'text',
  content:
    'I can analyze your agenttel.yml configuration and live traffic data to find improvement ' +
    'opportunities. Each finding will be presented for your approval before any changes are made.',
  timestamp: new Date(),
};

// ── Component ────────────────────────────────────────────────

export function ImproveAgent({ instrumentClient, onRefreshSuggestions }: ImproveAgentProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([WELCOME]);
  const [phase, setPhase] = useState<ConversationPhase>('idle');
  const [findings, setFindings] = useState<Finding[]>([]);
  const [currentIdx, setCurrentIdx] = useState(0);
  const [skipped, setSkipped] = useState<Set<number>>(new Set());
  const [appliedDescriptions, setAppliedDescriptions] = useState<string[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, phase]);

  const addMessage = useCallback((msg: Omit<ChatMessage, 'id' | 'timestamp'>) => {
    setMessages((prev) => [...prev, { ...msg, id: makeId(), timestamp: new Date() }]);
  }, []);

  // ── Scan ──────────────────────────────────────────────────

  const handleScan = useCallback(async () => {
    setPhase('scanning');
    addMessage({ role: 'user', type: 'text', content: 'Scan for improvements' });

    try {
      const raw = await instrumentClient.callTool('apply_improvements', {
        config_path: './agenttel.yml',
        dry_run: 'true',
      });
      const result: DryRunResult = JSON.parse(raw);

      if (result.total_findings === 0) {
        addMessage({
          role: 'agent',
          type: 'text',
          content:
            'Your configuration looks good! No improvements needed at this time.' +
            (result.health_data_available ? '' : ' Note: live health data was not available for deeper analysis.'),
        });
        setPhase('complete');
        return;
      }

      setFindings(result.findings);
      setCurrentIdx(0);

      const parts: string[] = [];
      if (result.by_risk.low > 0) parts.push(`${result.by_risk.low} low-risk`);
      if (result.by_risk.medium > 0) parts.push(`${result.by_risk.medium} medium-risk`);
      if (result.by_risk.high > 0) parts.push(`${result.by_risk.high} high-risk`);

      addMessage({
        role: 'agent',
        type: 'text',
        content:
          `Found ${result.total_findings} potential improvement${result.total_findings > 1 ? 's' : ''}: ` +
          `${parts.join(', ')}. Let me walk you through each one.` +
          (!result.health_data_available ? ' (Config analysis only — no live health data available.)' : ''),
      });

      addMessage({
        role: 'agent',
        type: 'finding',
        content: '',
        finding: result.findings[0],
        findingIndex: 0,
        status: 'pending',
      });

      setPhase('presenting');
    } catch {
      addMessage({
        role: 'agent',
        type: 'text',
        content: 'Failed to connect to the instrument agent. Make sure it is running.',
      });
      setPhase('idle');
    }
  }, [instrumentClient, addMessage]);

  // ── Advance ───────────────────────────────────────────────

  const advanceToNextFinding = useCallback(
    (nextFindings: Finding[], nextIdx: number, applied: string[], skippedSet: Set<number>) => {
      if (nextIdx >= nextFindings.length) {
        // All reviewed — summary
        const lines: string[] = [];
        if (applied.length > 0) {
          lines.push(`Applied ${applied.length} improvement${applied.length > 1 ? 's' : ''}:`);
          applied.forEach((d) => lines.push(`  ${d}`));
        }
        if (skippedSet.size > 0) {
          lines.push(`Skipped ${skippedSet.size} item${skippedSet.size > 1 ? 's' : ''}.`);
        }
        if (applied.length === 0 && skippedSet.size === 0) {
          lines.push('Review complete. No changes were made.');
        }

        setMessages((prev) => [
          ...prev,
          { id: makeId(), role: 'agent', type: 'summary', content: lines.join('\n'), timestamp: new Date() },
        ]);
        setPhase('complete');
        if (applied.length > 0) onRefreshSuggestions();
      } else {
        setCurrentIdx(nextIdx);
        setMessages((prev) => [
          ...prev,
          {
            id: makeId(),
            role: 'agent',
            type: 'finding',
            content: '',
            finding: nextFindings[nextIdx],
            findingIndex: nextIdx,
            status: 'pending',
            timestamp: new Date(),
          },
        ]);
        setPhase('presenting');
      }
    },
    [onRefreshSuggestions],
  );

  // ── Approve ───────────────────────────────────────────────

  const handleApprove = useCallback(async () => {
    const finding = findings[currentIdx];
    addMessage({ role: 'user', type: 'text', content: 'Approve', status: 'approved' });
    setPhase('applying');

    try {
      const raw = await instrumentClient.callTool('apply_single_improvement', {
        config_path: './agenttel.yml',
        trigger: finding.trigger,
        target: finding.target,
        ...(finding.suggested_value ? { suggested_value: finding.suggested_value } : {}),
      });
      const parsed = JSON.parse(raw);

      if (parsed.applied) {
        const desc = parsed.description;
        addMessage({ role: 'agent', type: 'text', content: `Applied: ${desc}` });
        const newApplied = [...appliedDescriptions, desc];
        setAppliedDescriptions(newApplied);
        advanceToNextFinding(findings, currentIdx + 1, newApplied, skipped);
      } else {
        addMessage({
          role: 'agent',
          type: 'text',
          content: `Could not apply: ${parsed.reason || 'unknown error'}. Moving on.`,
        });
        advanceToNextFinding(findings, currentIdx + 1, appliedDescriptions, skipped);
      }
    } catch {
      addMessage({ role: 'agent', type: 'text', content: 'Failed to apply this improvement. Moving on.' });
      advanceToNextFinding(findings, currentIdx + 1, appliedDescriptions, skipped);
    }
  }, [instrumentClient, findings, currentIdx, appliedDescriptions, skipped, addMessage, advanceToNextFinding]);

  // ── Skip ──────────────────────────────────────────────────

  const handleSkip = useCallback(() => {
    addMessage({ role: 'user', type: 'text', content: 'Skip', status: 'skipped' });
    const newSkipped = new Set(skipped);
    newSkipped.add(findings[currentIdx].id);
    setSkipped(newSkipped);
    advanceToNextFinding(findings, currentIdx + 1, appliedDescriptions, newSkipped);
  }, [findings, currentIdx, skipped, appliedDescriptions, addMessage, advanceToNextFinding]);

  // ── Why? ──────────────────────────────────────────────────

  const handleWhy = useCallback(() => {
    const finding = findings[currentIdx];
    addMessage({ role: 'user', type: 'text', content: 'Why this change?' });
    addMessage({ role: 'agent', type: 'text', content: buildDetailedExplanation(finding) });
  }, [findings, currentIdx, addMessage]);

  // ── Reset ─────────────────────────────────────────────────

  const handleReset = useCallback(() => {
    setMessages([{ ...WELCOME, id: makeId(), timestamp: new Date() }]);
    setPhase('idle');
    setFindings([]);
    setCurrentIdx(0);
    setSkipped(new Set());
    setAppliedDescriptions([]);
  }, []);

  // ── Approve All ───────────────────────────────────────────

  const handleApproveAll = useCallback(async () => {
    addMessage({ role: 'user', type: 'text', content: 'Approve all remaining', status: 'approved' });
    setPhase('applying');

    const newApplied = [...appliedDescriptions];
    for (let i = currentIdx; i < findings.length; i++) {
      const finding = findings[i];
      try {
        const raw = await instrumentClient.callTool('apply_single_improvement', {
          config_path: './agenttel.yml',
          trigger: finding.trigger,
          target: finding.target,
          ...(finding.suggested_value ? { suggested_value: finding.suggested_value } : {}),
        });
        const parsed = JSON.parse(raw);
        if (parsed.applied) {
          newApplied.push(parsed.description);
        }
      } catch {
        // skip failures
      }
    }

    setAppliedDescriptions(newApplied);
    const lines: string[] = [];
    if (newApplied.length > 0) {
      lines.push(`Applied ${newApplied.length} improvement${newApplied.length > 1 ? 's' : ''}:`);
      newApplied.forEach((d) => lines.push(`  ${d}`));
    }
    if (skipped.size > 0) {
      lines.push(`Skipped ${skipped.size} item${skipped.size > 1 ? 's' : ''}.`);
    }

    setMessages((prev) => [
      ...prev,
      { id: makeId(), role: 'agent', type: 'summary', content: lines.join('\n') || 'Done.', timestamp: new Date() },
    ]);
    setPhase('complete');
    if (newApplied.length > appliedDescriptions.length) onRefreshSuggestions();
  }, [instrumentClient, findings, currentIdx, appliedDescriptions, skipped, addMessage, onRefreshSuggestions]);

  // ── Render ────────────────────────────────────────────────

  return (
    <div style={cardStyle}>
      {/* Message area */}
      <div
        style={{
          maxHeight: '480px',
          overflowY: 'auto',
          paddingBottom: spacing.sm,
          scrollBehavior: 'smooth',
        }}
      >
        {messages.map((msg) => (
          <div
            key={msg.id}
            style={{
              marginBottom: spacing.md,
              animation: 'fadeIn 0.2s ease-out',
            }}
          >
            {msg.type === 'finding' ? (
              <FindingMessage
                msg={msg}
                totalFindings={findings.length}
                isCurrentFinding={msg.findingIndex === currentIdx && phase === 'presenting'}
                isApplying={phase === 'applying'}
                onApprove={handleApprove}
                onSkip={handleSkip}
                onWhy={handleWhy}
                onApproveAll={handleApproveAll}
                remaining={findings.length - currentIdx}
              />
            ) : msg.type === 'summary' ? (
              <SummaryMessage content={msg.content} />
            ) : msg.role === 'agent' ? (
              <AgentMessage content={msg.content} />
            ) : (
              <UserMessage content={msg.content} status={msg.status} />
            )}
          </div>
        ))}

        {/* Typing indicator */}
        {(phase === 'scanning' || phase === 'applying') && (
          <div style={{ marginBottom: spacing.md }}>
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: spacing.xs,
                padding: `${spacing.sm} ${spacing.md}`,
                backgroundColor: colors.surfaceElevated,
                borderRadius: radii.md,
                borderLeft: `2px solid ${colors.primaryLight}`,
              }}
            >
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span style={{ fontSize: font.size.xs, color: colors.textDim, marginLeft: spacing.xs }}>
                {phase === 'scanning' ? 'Analyzing...' : 'Applying...'}
              </span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Bottom bar */}
      <div
        style={{
          borderTop: `1px solid ${colors.borderSubtle}`,
          paddingTop: spacing.md,
          marginTop: spacing.xs,
        }}
      >
        {phase === 'idle' && (
          <button className="btn btn-primary" onClick={handleScan} style={{ width: '100%' }}>
            Scan for Improvements
          </button>
        )}
        {phase === 'complete' && (
          <button className="btn btn-secondary" onClick={handleReset} style={{ width: '100%' }}>
            Scan Again
          </button>
        )}
        {phase === 'presenting' && (
          <div style={{ fontSize: font.size.xs, color: colors.textDim, textAlign: 'center' }}>
            Reviewing finding {currentIdx + 1} of {findings.length}
          </div>
        )}
        {(phase === 'scanning' || phase === 'applying') && (
          <div style={{ fontSize: font.size.xs, color: colors.textDim, textAlign: 'center' }}>
            {phase === 'scanning' ? 'Scanning configuration and live traffic...' : 'Applying improvement...'}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Sub-components ──────────────────────────────────────────

function AgentMessage({ content }: { content: string }) {
  return (
    <div style={{ marginRight: '48px' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: spacing.xs,
          marginBottom: '3px',
        }}
      >
        <span
          style={{
            fontSize: '10px',
            padding: '1px 6px',
            borderRadius: '3px',
            backgroundColor: colors.primaryDim,
            color: colors.primaryLight,
            fontWeight: font.weight.medium,
            textTransform: 'uppercase',
            letterSpacing: '0.3px',
          }}
        >
          Improve Agent
        </span>
      </div>
      <div
        style={{
          padding: `${spacing.sm} ${spacing.md}`,
          backgroundColor: colors.surfaceElevated,
          borderRadius: radii.md,
          borderLeft: `2px solid ${colors.primaryLight}`,
          fontSize: font.size.sm,
          color: colors.textMuted,
          lineHeight: 1.6,
        }}
      >
        {content}
      </div>
    </div>
  );
}

function UserMessage({ content, status }: { content: string; status?: string }) {
  const isApprove = status === 'approved' || content === 'Approve' || content.includes('Approve');
  return (
    <div style={{ marginLeft: '48px', textAlign: 'right' }}>
      <span
        style={{
          display: 'inline-block',
          padding: `${spacing.xs} ${spacing.md}`,
          borderRadius: radii.md,
          backgroundColor: isApprove ? colors.successDim : 'rgba(0, 0, 0, 0.03)',
          color: isApprove ? colors.success : colors.textMuted,
          fontSize: font.size.sm,
          fontWeight: font.weight.medium,
          border: `1px solid ${isApprove ? 'rgba(30, 142, 62, 0.15)' : colors.borderSubtle}`,
        }}
      >
        {content}
      </span>
    </div>
  );
}

function FindingMessage({
  msg,
  totalFindings,
  isCurrentFinding,
  isApplying,
  onApprove,
  onSkip,
  onWhy,
  onApproveAll,
  remaining,
}: {
  msg: ChatMessage;
  totalFindings: number;
  isCurrentFinding: boolean;
  isApplying: boolean;
  onApprove: () => void;
  onSkip: () => void;
  onWhy: () => void;
  onApproveAll: () => void;
  remaining: number;
}) {
  const f = msg.finding!;
  const riskColor = riskColors[f.risk_level] || colors.textMuted;

  return (
    <div style={{ marginRight: '24px' }}>
      <div
        style={{
          padding: spacing.md,
          backgroundColor: colors.surfaceElevated,
          borderRadius: radii.md,
          borderLeft: `3px solid ${riskColor}`,
        }}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: spacing.sm,
            marginBottom: spacing.sm,
          }}
        >
          <span
            style={{
              fontSize: font.size.md,
              fontWeight: font.weight.medium,
              color: colors.text,
            }}
          >
            {formatTrigger(f.trigger)}
          </span>
          <span
            style={{
              fontSize: '10px',
              padding: '1px 6px',
              borderRadius: '3px',
              backgroundColor: `${riskColor}15`,
              color: riskColor,
              fontWeight: font.weight.medium,
              textTransform: 'uppercase',
            }}
          >
            {f.risk_level}
          </span>
          <span
            style={{
              fontSize: font.size.xs,
              color: colors.textDim,
              marginLeft: 'auto',
            }}
          >
            {(msg.findingIndex ?? 0) + 1}/{totalFindings}
          </span>
        </div>

        {/* Target */}
        <div
          style={{
            fontSize: font.size.sm,
            fontFamily: font.mono,
            color: colors.primaryLight,
            marginBottom: spacing.xs,
          }}
        >
          {f.target}
        </div>

        {/* Current -> Suggested */}
        {(f.current_value || f.suggested_value) && (
          <div style={{ fontSize: font.size.sm, color: colors.textMuted, marginBottom: spacing.xs }}>
            {f.current_value && (
              <span>
                Current: <code style={{ color: colors.textDim }}>{f.current_value}</code>
              </span>
            )}
            {f.current_value && f.suggested_value && <span style={{ color: colors.textDim }}> {'\u2192'} </span>}
            {f.suggested_value && (
              <span>
                Suggested: <code style={{ color: colors.success }}>{f.suggested_value}</code>
              </span>
            )}
          </div>
        )}

        {/* Reasoning */}
        <div style={{ fontSize: font.size.sm, color: colors.textMuted, lineHeight: 1.5 }}>{f.reasoning}</div>

        {/* Action buttons — only on current finding */}
        {isCurrentFinding && !isApplying && (
          <div style={{ display: 'flex', gap: spacing.sm, marginTop: spacing.md, flexWrap: 'wrap' }}>
            <button
              className="btn btn-primary"
              onClick={onApprove}
              style={{ fontSize: font.size.sm, padding: '4px 16px' }}
            >
              Approve
            </button>
            <button
              className="btn btn-secondary"
              onClick={onSkip}
              style={{ fontSize: font.size.sm, padding: '4px 16px' }}
            >
              Skip
            </button>
            <button
              className="btn btn-outline"
              onClick={onWhy}
              style={{ fontSize: font.size.sm, padding: '4px 16px' }}
            >
              Why?
            </button>
            {remaining > 1 && (
              <button
                className="btn btn-secondary"
                onClick={onApproveAll}
                style={{
                  fontSize: font.size.sm,
                  padding: '4px 16px',
                  marginLeft: 'auto',
                  color: colors.success,
                  borderColor: 'rgba(30, 142, 62, 0.15)',
                }}
              >
                Approve All ({remaining})
              </button>
            )}
          </div>
        )}

        {/* Past finding status */}
        {!isCurrentFinding && (
          <div
            style={{
              fontSize: font.size.xs,
              marginTop: spacing.sm,
              color: msg.status === 'approved' || msg.status === 'applied' ? colors.success : colors.textDim,
              fontStyle: 'italic',
            }}
          >
            {/* Status is inferred from the user message that follows */}
          </div>
        )}
      </div>
    </div>
  );
}

function SummaryMessage({ content }: { content: string }) {
  const lines = content.split('\n');
  return (
    <div style={{ marginRight: '48px' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: spacing.xs,
          marginBottom: '3px',
        }}
      >
        <span
          style={{
            fontSize: '10px',
            padding: '1px 6px',
            borderRadius: '3px',
            backgroundColor: colors.primaryDim,
            color: colors.primaryLight,
            fontWeight: font.weight.medium,
            textTransform: 'uppercase',
            letterSpacing: '0.3px',
          }}
        >
          Summary
        </span>
      </div>
      <div
        style={{
          padding: spacing.md,
          backgroundColor: colors.surfaceElevated,
          borderRadius: radii.md,
          borderLeft: `2px solid ${colors.success}`,
        }}
      >
        {lines.map((line, i) => {
          const isApplied = line.trim().startsWith('Applied') || line.trim().startsWith('\u2713');
          const isIndented = line.startsWith('  ');
          return (
            <div
              key={i}
              style={{
                fontSize: font.size.sm,
                color: isApplied ? colors.success : isIndented ? colors.success : colors.textMuted,
                fontFamily: isIndented ? font.mono : font.sans,
                lineHeight: 1.6,
                paddingLeft: isIndented ? spacing.md : 0,
              }}
            >
              {line}
            </div>
          );
        })}
      </div>
    </div>
  );
}
