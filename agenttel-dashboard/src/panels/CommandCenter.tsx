import { useMemo, useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { McpClient } from '../api/mcp-client';
import { useMcpTool } from '../hooks/useMcpTool';
import { config } from '../config';
import { colors, font, spacing, radii, card as cardStyle, statusColors } from '../styles/theme';
import { StatusBadge } from '../components/StatusBadge';
import { MetricCard } from '../components/MetricCard';
import { SloGauge } from '../components/SloGauge';
import { TraceLink } from '../components/TraceLink';
import { ImproveAgent } from '../components/ImproveAgent';

// ── Types ─────────────────────────────────────────────────────

interface HealthData {
  service: string;
  status: string;
  operations: { name: string; errorRate: string; p50: string; p99: string }[];
  slos: { name: string; budget: number; burnRate: number; status: string }[];
  totalRequests: number;
}

interface AgentStatus {
  monitor: { running: boolean; uptime_seconds?: number | null };
  instrument: { running: boolean; tools?: string[] };
  backend: { running: boolean; tools?: string[] };
  collector: { running: boolean };
  frontend_telemetry: { generated_count: number };
}

interface SuggestionItem {
  trigger: string;
  risk_level: string;
  target: string;
  current_value?: string;
  suggested_value?: string;
  reasoning: string;
  auto_applicable: boolean;
}

// ── Parsers ───────────────────────────────────────────────────

function parseHealth(raw: string): HealthData {
  const lines = raw.split('\n');
  let service = 'unknown';
  let status = 'unknown';
  const operations: HealthData['operations'] = [];
  const slos: HealthData['slos'] = [];
  let totalRequests = 0;

  for (const line of lines) {
    const serviceMatch = line.match(/^SERVICE:\s*(\S+)\s*\|\s*STATUS:\s*(\w+)/i);
    if (serviceMatch) {
      service = serviceMatch[1];
      status = serviceMatch[2].toLowerCase();
    }

    const opMatch = line.match(/^\s{2}(\S+(?:\s+\S+)*?):\s+err=([0-9.]+)%\s+p50=(\d+)ms\s+p99=(\d+)ms/);
    if (opMatch) {
      const opName = opMatch[1];
      // Filter out internal spans (agent actions, bare HTTP methods)
      if (opName.startsWith('agent.action:') || /^(GET|POST|PUT|DELETE|PATCH)$/.test(opName)) continue;
      operations.push({ name: opName, errorRate: opMatch[2], p50: opMatch[3], p99: opMatch[4] });
    }

    const sloMatch = line.match(/^\s{2}(\S+):\s+budget=([0-9.]+)%\s+burn=([0-9.]+)x/);
    if (sloMatch) {
      slos.push({
        name: sloMatch[1],
        budget: parseFloat(sloMatch[2]),
        burnRate: parseFloat(sloMatch[3]),
        status: parseFloat(sloMatch[2]) > 50 ? 'healthy' : parseFloat(sloMatch[2]) > 20 ? 'at_risk' : 'violated',
      });
    }

    const reqMatch = line.match(/(\d+)\s+total\s+requests/i);
    if (reqMatch) totalRequests = parseInt(reqMatch[1]);
  }

  return { service, status, operations, slos, totalRequests };
}

// ── Structured Agent Action Types ─────────────────────────────

interface AgentAction {
  status: string;
  name: string;
  type: string;
  reason: string;
  timestamp: Date;
  timeAgo: string;
}

interface ActionCycle {
  timestamp: Date;
  timeAgo: string;
  actions: AgentAction[];
}

function formatTimeAgo(date: Date): string {
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

const friendlyActionNames: Record<string, string> = {
  'remediation:toggle-payment-gateway-circuit-breaker': 'Toggled circuit breaker',
  'remediation:flush-payment-cache': 'Flushed payment cache',
  'remediation:scale-payment-workers': 'Scaled payment workers',
  'remediation:restart-payment-service': 'Restarted payment service',
};

function friendlyName(actionName: string): string {
  if (friendlyActionNames[actionName]) return friendlyActionNames[actionName];
  // Generic: extract last segment after ':', replace hyphens, capitalize
  const lastSegment = actionName.includes(':') ? actionName.split(':').pop()! : actionName;
  return lastSegment
    .split('-')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

function parseAgentActions(raw: string): AgentAction[] {
  if (raw.includes('No recent agent actions')) return [];
  const actions: AgentAction[] = [];
  const lines = raw.split('\n').filter((l) => l.trim().length > 0);
  // Format:   [STATUS] name (type) - reason @ timestamp  (note leading spaces)
  const regex = /\[(\w+)\]\s+(.+?)\s+\((\w+)\)\s*-\s*(.+?)\s*@\s*(.+)$/;
  for (const line of lines) {
    const match = line.match(regex);
    if (match) {
      const ts = new Date(match[5].trim());
      actions.push({
        status: match[1],
        name: match[2],
        type: match[3],
        reason: match[4],
        timestamp: ts,
        timeAgo: formatTimeAgo(ts),
      });
    }
  }
  return actions;
}

function groupIntoCycles(actions: AgentAction[]): ActionCycle[] {
  if (actions.length === 0) return [];
  // Sort newest first
  const sorted = [...actions].sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
  const cycles: ActionCycle[] = [];
  let currentCycle: AgentAction[] = [sorted[0]];

  for (let i = 1; i < sorted.length; i++) {
    const gap = currentCycle[currentCycle.length - 1].timestamp.getTime() - sorted[i].timestamp.getTime();
    if (gap <= 5000) {
      currentCycle.push(sorted[i]);
    } else {
      cycles.push({
        timestamp: currentCycle[0].timestamp,
        timeAgo: formatTimeAgo(currentCycle[0].timestamp),
        actions: currentCycle,
      });
      currentCycle = [sorted[i]];
    }
  }
  cycles.push({
    timestamp: currentCycle[0].timestamp,
    timeAgo: formatTimeAgo(currentCycle[0].timestamp),
    actions: currentCycle,
  });

  return cycles;
}

function parseSuggestions(raw: string): SuggestionItem[] {
  try {
    const parsed = JSON.parse(raw);
    if (parsed.suggestions) return parsed.suggestions;
  } catch {
    // not JSON
  }
  return [];
}

// ── Incident Context Parser ──────────────────────────────────

interface IncidentSection {
  title: string;
  content: string;
}

interface ParsedIncident {
  severity: string;
  summary: string;
  sections: IncidentSection[];
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

  return { severity, summary, sections };
}

const severityColors: Record<string, string> = {
  high: colors.error,
  critical: colors.critical,
  medium: colors.warning,
  low: colors.info,
};

// ── Agent Badge Component ─────────────────────────────────────

function AgentBadge({ agent }: { agent: 'monitor' | 'instrument' | 'mcp' }) {
  const labels = { monitor: 'Monitor Agent', instrument: 'Instrument Agent', mcp: 'MCP Server' };
  return (
    <span className={`agent-badge agent-badge-${agent}`}>
      {labels[agent]}
    </span>
  );
}

// ── Section Header Component ──────────────────────────────────

function SectionHeader({ title, subtitle, linkTo, linkLabel }: {
  title: string;
  subtitle?: string;
  linkTo?: string;
  linkLabel?: string;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: spacing.lg }}>
      <div>
        <h2 style={{ fontSize: font.size.xl, fontWeight: font.weight.semibold, color: colors.text, letterSpacing: '-0.01em' }}>
          {title}
        </h2>
        {subtitle && (
          <p style={{ fontSize: font.size.sm, color: colors.textDim, marginTop: '2px' }}>{subtitle}</p>
        )}
      </div>
      {linkTo && (
        <Link to={linkTo} style={{ fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }}>
          {linkLabel || 'View all'} {'\u2192'}
        </Link>
      )}
    </div>
  );
}

// ── Main Component ────────────────────────────────────────────

export function CommandCenter() {
  const mcpClient = useMemo(() => new McpClient(config.mcpBaseUrl), []);
  const instrumentClient = useMemo(() => new McpClient(config.instrumentBaseUrl), []);

  // Data fetching
  const health = useMcpTool(mcpClient, 'get_service_health', {}, config.pollIntervals.commandCenter, parseHealth);
  const summary = useMcpTool(mcpClient, 'get_executive_summary', {}, config.pollIntervals.executiveSummary, (r) => r);
  const agentActions = useMcpTool(mcpClient, 'get_recent_agent_actions', {}, config.pollIntervals.monitorDecisions, parseAgentActions);
  const suggestions = useMcpTool(
    instrumentClient, 'suggest_improvements', { config_path: './agenttel.yml' },
    config.pollIntervals.suggestions, parseSuggestions,
  );

  // Agent manager status
  const [agentStatus, setAgentStatus] = useState<AgentStatus | null>(null);
  const fetchAgentStatus = useCallback(async () => {
    try {
      const res = await fetch(`${config.adminBaseUrl}/status`);
      if (res.ok) setAgentStatus(await res.json());
    } catch {
      // agent manager not running
    }
  }, []);

  useEffect(() => {
    fetchAgentStatus();
    const id = window.setInterval(fetchAgentStatus, config.pollIntervals.agentStatus);
    return () => window.clearInterval(id);
  }, [fetchAgentStatus]);

  // Derived metrics
  const avgError = health.data?.operations.length
    ? health.data.operations.reduce((s, o) => s + parseFloat(o.errorRate), 0) / health.data.operations.length
    : 0;
  const avgP50 = health.data?.operations.length
    ? Math.round(health.data.operations.reduce((s, o) => s + parseInt(o.p50), 0) / health.data.operations.length)
    : 0;
  const sloHealthy = health.data?.slos.filter((s) => s.status === 'healthy').length || 0;
  const sloTotal = health.data?.slos.length || 0;

  const activeAgents = [
    agentStatus?.monitor.running,
    agentStatus?.instrument.running,
    agentStatus?.backend.running,
    agentStatus?.instrument.running, // Improve Agent (uses instrument MCP)
  ].filter(Boolean).length;
  const totalAgents = 4;

  // Agent Insights derived metrics
  const allActions = agentActions.data || [];
  const cycles = useMemo(() => groupIntoCycles(allActions), [allActions]);
  const remediationCount = allActions.filter((a) => a.name.startsWith('remediation:')).length;
  const completedCount = allActions.filter((a) => a.status === 'COMPLETED').length;
  const successRate = allActions.length > 0 ? Math.round((completedCount / allActions.length) * 100) : 0;
  const suggestionCount = suggestions.data?.length || 0;
  const opsCount = health.data?.operations.length || 0;

  // Incident cycles: cycles with at least one remediation
  const incidentCycles = cycles.filter((c) => c.actions.some((a) => a.name.startsWith('remediation:'))).length;

  // MTTR: median seconds from first action to last remediation in each incident cycle
  const mttrValues = cycles
    .filter((c) => c.actions.some((a) => a.name.startsWith('remediation:')))
    .map((c) => {
      const sorted = [...c.actions].sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
      const first = sorted[0].timestamp.getTime();
      const lastRem = sorted.filter((a) => a.name.startsWith('remediation:')).pop()!.timestamp.getTime();
      return (lastRem - first) / 1000;
    });
  const medianMttr = mttrValues.length > 0
    ? mttrValues.sort((a, b) => a - b)[Math.floor(mttrValues.length / 2)]
    : 0;
  const mttrFormatted = medianMttr < 60 ? `${Math.round(medianMttr)}s` : `${Math.round(medianMttr / 60)}m`;
  const mttrColor = medianMttr < 60 ? colors.success : medianMttr < 300 ? colors.warning : colors.error;

  // Improvement count
  const improvementCount = allActions.filter((a) => a.status === 'COMPLETED' && !a.name.startsWith('remediation:')).length;
  const appliedSuggestions = suggestions.data?.filter((s) => s.auto_applicable).length || 0;
  const pendingSuggestions = (suggestions.data?.length || 0) - appliedSuggestions;

  // Degraded operations → incident context
  const degradedOps = useMemo(
    () => health.data?.operations.filter((op) => parseFloat(op.errorRate) > 1) || [],
    [health.data],
  );
  const [incidentData, setIncidentData] = useState<ParsedIncident | null>(null);

  useEffect(() => {
    if (degradedOps.length === 0) {
      setIncidentData(null);
      return;
    }
    const fetchIncident = async () => {
      try {
        const result = await mcpClient.callTool('get_incident_context', { operation_name: degradedOps[0].name });
        setIncidentData(parseIncident(result));
      } catch {
        // ignore
      }
    };
    fetchIncident();
    const id = window.setInterval(fetchIncident, config.pollIntervals.incidentContext);
    return () => window.clearInterval(id);
  }, [degradedOps.length > 0 ? degradedOps[0].name : '', mcpClient]);

  return (
    <div>
      {/* ── Page Header ──────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing['3xl'] }}>
        <div>
          <h1 style={{ fontSize: font.size['3xl'], fontWeight: font.weight.bold, color: colors.text, letterSpacing: '-0.02em' }}>
            Command Center
          </h1>
          <p style={{ fontSize: font.size.lg, color: colors.textMuted, marginTop: spacing.xs }}>
            Agent-ready observability at a glance
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.lg }}>
          {agentStatus && (
            <div style={{
              display: 'flex', alignItems: 'center', gap: spacing.sm,
              padding: `${spacing.xs} ${spacing.md}`,
              backgroundColor: colors.surfaceElevated,
              borderRadius: radii.md,
              border: `1px solid ${colors.border}`,
            }}>
              <span style={{
                width: '6px', height: '6px', borderRadius: '50%',
                backgroundColor: activeAgents > 0 ? colors.success : colors.textDim,
                boxShadow: activeAgents > 0 ? `0 0 6px ${colors.success}` : 'none',
              }} />
              <span style={{ fontSize: font.size.sm, color: colors.textMuted }}>
                {activeAgents}/{totalAgents} agents active
              </span>
            </div>
          )}
          {health.lastUpdated && (
            <span style={{ fontSize: font.size.xs, color: colors.textDim }}>
              Updated {new Date(health.lastUpdated).toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      {/* ═══════════════════════════════════════════════════════
          SECTION 1: System Status
          ═══════════════════════════════════════════════════════ */}
      <SectionHeader title="System Status" subtitle="Live health, performance, and SLO compliance" />

      {/* KPI Cards */}
      <div className="grid-kpi" style={{ marginBottom: spacing.xl }}>
        <MetricCard
          label="Service Status"
          value={health.data?.status.toUpperCase() || '-'}
          color={health.data ? statusColors[health.data.status] : undefined}
          subtitle={health.data?.service}
        />
        <MetricCard
          label="Avg Latency"
          value={health.data ? `${avgP50}ms` : '-'}
          color={avgP50 > 200 ? colors.warning : colors.text}
          subtitle="p50 across operations"
        />
        <MetricCard
          label="Error Rate"
          value={health.data ? `${avgError.toFixed(2)}%` : '-'}
          color={avgError > 1 ? colors.error : avgError > 0 ? colors.warning : colors.success}
          subtitle="avg across operations"
        />
        <MetricCard
          label="SLO Health"
          value={sloTotal > 0 ? `${sloHealthy}/${sloTotal}` : '-'}
          color={sloHealthy === sloTotal ? colors.success : colors.warning}
          subtitle="targets met"
        />
      </div>

      <div className="grid-2" style={{ marginBottom: spacing['3xl'] }}>
        {/* Operations Table */}
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }}>
            <span style={{ fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }}>
              Operations
            </span>
            <Link to="/fleet" style={{ fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }}>
              Fleet {'\u2192'}
            </Link>
          </div>
          {health.loading && !health.data && <div className="skeleton" style={{ height: '80px' }} />}
          {health.data?.operations.length === 0 && (
            <div style={{ color: colors.textDim, fontSize: font.size.sm, padding: spacing.lg, textAlign: 'center' }}>
              No operations yet. Generate traffic to see data.
            </div>
          )}
          {health.data?.operations.map((op) => (
            <div
              key={op.name}
              className="row-hover"
              style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                padding: `${spacing.sm} ${spacing.md}`,
                borderBottom: `1px solid ${colors.borderSubtle}`,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
                <StatusBadge status={parseFloat(op.errorRate) > 1 ? 'degraded' : 'healthy'} size="sm" />
                <span style={{ fontSize: font.size.md, color: colors.text, fontFamily: font.mono }}>{op.name}</span>
              </div>
              <div style={{ display: 'flex', gap: spacing.lg, alignItems: 'center' }}>
                <span style={{ fontSize: font.size.sm, color: colors.textMuted, fontFamily: font.mono }}>
                  p50: {op.p50}ms
                </span>
                <span style={{ fontSize: font.size.sm, color: colors.textMuted, fontFamily: font.mono }}>
                  p99: {op.p99}ms
                </span>
                <span style={{
                  fontSize: font.size.sm, fontFamily: font.mono,
                  color: parseFloat(op.errorRate) > 1 ? colors.error : colors.textDim,
                }}>
                  err: {op.errorRate}%
                </span>
                <TraceLink service="payment-service" operation={op.name} label="Traces" />
              </div>
            </div>
          ))}
          {health.error && (
            <div style={{ color: colors.textDim, fontSize: font.size.sm, padding: spacing.lg, textAlign: 'center' }}>
              Waiting for service connection...
            </div>
          )}
        </div>

        {/* SLO Compliance */}
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }}>
            <span style={{ fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }}>
              SLO Compliance
            </span>
            <Link to="/slo" style={{ fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }}>
              Details {'\u2192'}
            </Link>
          </div>
          {health.loading && !health.data && <div className="skeleton" style={{ height: '80px' }} />}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: spacing.md }}>
            {health.data?.slos.map((s) => (
              <SloGauge key={s.name} name={s.name} budgetPercent={s.budget} burnRate={s.burnRate} status={s.status} />
            ))}
          </div>
          {health.data?.slos.length === 0 && (
            <div style={{ color: colors.textDim, fontSize: font.size.sm, padding: spacing.lg, textAlign: 'center' }}>
              No SLO data available yet.
            </div>
          )}
        </div>
      </div>

      {/* ═══════════════════════════════════════════════════════
          SECTION 2: Agent Insights
          ═══════════════════════════════════════════════════════ */}
      <SectionHeader
        title="Agent Insights"
        subtitle="Live agent work and impact on service reliability"
        linkTo="/agents"
        linkLabel="Manage agents"
      />

      {/* ── Sub-section 1: Agent Impact Strip ─────────────── */}
      <div className="grid-kpi" style={{ marginBottom: spacing.lg }}>
        {allActions.length > 0 ? (
          <>
            <MetricCard
              label="Incidents Handled"
              value={`${incidentCycles}`}
              color={incidentCycles > 0 ? colors.primary : colors.success}
              subtitle="auto-resolved"
            />
            <MetricCard
              label="Median Response"
              value={incidentCycles > 0 ? mttrFormatted : '-'}
              color={incidentCycles > 0 ? mttrColor : undefined}
              subtitle="detect to remediate"
            />
            <MetricCard
              label="Success Rate"
              value={`${successRate}%`}
              color={successRate >= 95 ? colors.success : successRate >= 80 ? colors.warning : colors.error}
              subtitle={`${completedCount}/${allActions.length} actions`}
            />
            <MetricCard
              label="Improvements"
              value={`${improvementCount}`}
              color={improvementCount > 0 ? colors.success : undefined}
              subtitle="config changes applied"
            />
          </>
        ) : (
          <>
            <MetricCard
              label="Operations Watched"
              value={`${opsCount}`}
              color={colors.primary}
              subtitle="under agent protection"
            />
            <MetricCard
              label="SLOs Protected"
              value={sloTotal > 0 ? `${sloHealthy}/${sloTotal}` : '-'}
              color={sloHealthy === sloTotal ? colors.success : colors.warning}
              subtitle="budgets healthy"
            />
            <MetricCard
              label="Active Agents"
              value={`${activeAgents}/${totalAgents}`}
              color={activeAgents === totalAgents ? colors.success : colors.textMuted}
              subtitle="online and watching"
            />
            <MetricCard
              label="Status"
              value={degradedOps.length > 0 ? 'DEGRADED' : 'ALL CLEAR'}
              color={degradedOps.length > 0 ? colors.warning : colors.success}
              subtitle={degradedOps.length > 0 ? `${degradedOps.length} operation${degradedOps.length > 1 ? 's' : ''} impacted` : 'no incidents detected'}
            />
          </>
        )}
      </div>

      {/* ── Sub-section 2: Agent Coverage Cards ───────────── */}
      <div className="grid-3" style={{ marginBottom: spacing.lg }}>
        {[
          {
            name: 'Monitor Agent',
            badge: 'monitor' as const,
            running: agentStatus?.monitor.running ?? false,
            stat: agentStatus?.monitor.running
              ? (incidentCycles > 0
                ? `${incidentCycles} incident${incidentCycles !== 1 ? 's' : ''} resolved`
                : 'All operations healthy')
              : 'Not active',
            detail: agentStatus?.monitor.running
              ? `Watching ${opsCount} operations \u00B7 ${formatUptime(agentStatus.monitor.uptime_seconds)}`
              : 'Start from Agents panel for autonomous monitoring',
            statColor: agentStatus?.monitor.running
              ? (incidentCycles > 0 ? colors.primary : colors.success)
              : colors.textDim,
          },
          {
            name: 'Instrument Agent',
            badge: 'instrument' as const,
            running: agentStatus?.instrument.running ?? false,
            stat: suggestionCount > 0
              ? `${suggestionCount} improvement${suggestionCount !== 1 ? 's' : ''} found`
              : (agentStatus?.instrument.running ? 'Config looks good' : 'Not connected'),
            detail: agentStatus?.instrument.running
              ? (suggestionCount > 0
                ? `${appliedSuggestions} auto-applicable \u00B7 ${pendingSuggestions} need review`
                : 'Analyzing config against live traffic')
              : 'Connect to analyze instrumentation coverage',
            statColor: suggestionCount > 0 ? colors.primary : (agentStatus?.instrument.running ? colors.success : colors.textDim),
          },
          {
            name: 'Backend MCP',
            badge: 'mcp' as const,
            running: agentStatus?.backend.running ?? false,
            stat: health.data?.totalRequests
              ? `${health.data.totalRequests.toLocaleString()} requests analyzed`
              : (agentStatus?.backend.running ? 'Ready for agent queries' : 'Not connected'),
            detail: agentStatus?.backend.running
              ? 'Health, SLO, incident, trend, and remediation tools'
              : 'Provides 9 MCP tools for AI agent consumption',
            statColor: health.data?.totalRequests ? colors.primary : (agentStatus?.backend.running ? colors.success : colors.textDim),
          },
        ].map((agent) => (
          <div key={agent.name} className="card-hover" style={cardStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm }}>
              <AgentBadge agent={agent.badge} />
              <StatusBadge status={agent.running ? 'running' : 'stopped'} size="sm" />
            </div>
            <div style={{ fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text, marginBottom: spacing.xs }}>
              {agent.name}
            </div>
            <div style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: agent.statColor, marginBottom: spacing.sm }}>
              {agent.stat}
            </div>
            <div style={{ fontSize: font.size.xs, color: colors.textDim }}>{agent.detail}</div>
          </div>
        ))}
      </div>

      {/* ── Sub-section 3: Active Issue Spotlight ─────────── */}
      {degradedOps.length > 0 && incidentData && incidentData.severity !== 'unknown' && (
        <div
          style={{
            ...cardStyle,
            borderLeft: `3px solid ${severityColors[incidentData.severity] || colors.warning}`,
            marginBottom: spacing.lg,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
              <StatusBadge status={incidentData.severity} size="sm" />
              <span style={{ fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }}>
                Active Issue: <span style={{ fontFamily: font.mono }}>{degradedOps[0].name}</span>
              </span>
            </div>
            <Link to="/incidents" style={{ fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }}>
              Full context {'\u2192'}
            </Link>
          </div>

          {incidentData.summary && (
            <div style={{ fontSize: font.size.md, color: colors.textMuted, marginBottom: spacing.md, lineHeight: 1.5 }}>
              {incidentData.summary}
            </div>
          )}

          {incidentData.sections.length > 0 && (
            <div className="grid-2" style={{ gap: spacing.sm }}>
              {incidentData.sections.slice(0, 4).map((section, i) => (
                <div
                  key={i}
                  style={{
                    padding: spacing.md,
                    backgroundColor: colors.bg,
                    borderRadius: radii.md,
                    border: `1px solid ${colors.borderSubtle}`,
                  }}
                >
                  <div style={{
                    fontSize: '10px',
                    fontWeight: font.weight.semibold,
                    color: colors.primaryLight,
                    marginBottom: spacing.xs,
                    textTransform: 'uppercase',
                    letterSpacing: '0.5px',
                  }}>
                    {section.title}
                  </div>
                  <div style={{
                    fontSize: font.size.sm,
                    color: colors.text,
                    lineHeight: 1.5,
                    maxHeight: '60px',
                    overflow: 'hidden',
                  }}>
                    {section.content.split('\n').slice(0, 3).join('\n')}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── Sub-section 4: Decision Timeline ──────────────── */}
      <div style={{ ...cardStyle, marginBottom: spacing['3xl'] }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
            <span style={{ fontSize: font.size.lg, fontWeight: font.weight.medium, color: colors.text }}>
              Decision Timeline
            </span>
            <AgentBadge agent="monitor" />
          </div>
          <Link to="/agents" style={{ fontSize: font.size.sm, color: colors.primaryLight, textDecoration: 'none' }}>
            Full timeline {'\u2192'}
          </Link>
        </div>

        {agentActions.loading && !agentActions.data && <div className="skeleton" style={{ height: '60px' }} />}
        {cycles.length > 0 ? (
          cycles.slice(0, 8).map((cycle, ci) => {
            const allCompleted = cycle.actions.every((a) => a.status === 'COMPLETED');
            const hasFailed = cycle.actions.some((a) => a.status === 'FAILED');
            return (
              <div
                key={ci}
                className="row-hover"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: spacing.md,
                  padding: `${spacing.sm} ${spacing.md}`,
                  borderBottom: ci < Math.min(cycles.length, 8) - 1 ? `1px solid ${colors.borderSubtle}` : 'none',
                }}
              >
                <span style={{
                  fontSize: font.size.xs, color: colors.textDim, minWidth: '52px', fontFamily: font.mono, flexShrink: 0,
                }}>
                  {cycle.timeAgo}
                </span>
                <div style={{ display: 'flex', gap: spacing.xs, flexWrap: 'wrap', flex: 1 }}>
                  {cycle.actions.map((action, ai) => {
                    const isRemediation = action.name.startsWith('remediation:');
                    return (
                      <span
                        key={ai}
                        title={action.reason}
                        style={{
                          fontSize: font.size.xs, padding: '2px 10px', borderRadius: radii.sm,
                          backgroundColor: isRemediation ? colors.primaryDim : 'rgba(0,0,0,0.04)',
                          color: isRemediation ? colors.primary : colors.textMuted,
                          fontWeight: font.weight.medium, whiteSpace: 'nowrap',
                        }}
                      >
                        {friendlyName(action.name)}
                      </span>
                    );
                  })}
                </div>
                <span style={{
                  fontSize: '14px', flexShrink: 0,
                  color: hasFailed ? colors.error : allCompleted ? colors.success : colors.textDim,
                }}>
                  {hasFailed ? '\u2717' : allCompleted ? '\u2713' : '\u2022'}
                </span>
              </div>
            );
          })
        ) : (
          <div style={{ textAlign: 'center', padding: spacing.lg }}>
            {agentStatus?.monitor.running ? (
              <>
                <div style={{ fontSize: font.size.md, color: colors.textMuted, marginBottom: spacing.md }}>
                  Agents are watching. No incidents to report.
                </div>
                {health.data?.operations && health.data.operations.length > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'center', gap: spacing.lg, flexWrap: 'wrap' }}>
                    {health.data.operations.map((op) => (
                      <div key={op.name} style={{ display: 'flex', alignItems: 'center', gap: spacing.xs }}>
                        <span style={{
                          width: '6px', height: '6px', borderRadius: '50%',
                          backgroundColor: parseFloat(op.errorRate) < 1 ? colors.success : colors.warning,
                        }} />
                        <span style={{ fontSize: font.size.sm, fontFamily: font.mono, color: colors.textDim }}>
                          {op.name}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div style={{ color: colors.textDim, fontSize: font.size.sm }}>
                Start the monitor from the{' '}
                <Link to="/agents" style={{ color: colors.primaryLight, textDecoration: 'none' }}>Agents</Link>{' '}
                panel for autonomous incident response.
              </div>
            )}
          </div>
        )}
      </div>

      {/* ═══════════════════════════════════════════════════════
          SECTION 3: Improve Agent
          ═══════════════════════════════════════════════════════ */}
      <SectionHeader
        title="Improve Agent"
        subtitle="Conversational assistant for config improvements"
        linkTo="/suggestions"
        linkLabel="All suggestions"
      />

      <div style={{ marginBottom: spacing['3xl'] }}>
        <ImproveAgent instrumentClient={instrumentClient} onRefreshSuggestions={suggestions.refresh} />
      </div>

      {/* ═══════════════════════════════════════════════════════
          SECTION 4: Quick Actions + Improvement Tracker
          ═══════════════════════════════════════════════════════ */}
      <div className="grid-2" style={{ marginBottom: spacing['3xl'] }}>
        {/* Quick Actions */}
        <div>
          <SectionHeader title="Quick Actions" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.md }}>
            <Link to="/traffic" style={{ textDecoration: 'none' }}>
              <div className="card-hover" style={{
                ...cardStyle,
                display: 'flex', alignItems: 'center', gap: spacing.md,
                background: `linear-gradient(135deg, ${colors.primaryDim}, transparent)`,
                borderColor: 'rgba(25, 103, 210, 0.12)',
              }}>
                <div style={{
                  width: '36px', height: '36px', borderRadius: radii.md,
                  backgroundColor: colors.primaryDim, display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '16px',
                }}>
                  {'\u25B6'}
                </div>
                <div>
                  <div style={{ fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }}>
                    Generate End-to-End Traffic
                  </div>
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted }}>
                    Browser spans + backend requests + distributed traces
                  </div>
                </div>
              </div>
            </Link>
            <Link to="/agents" style={{ textDecoration: 'none' }}>
              <div className="card-hover" style={{
                ...cardStyle,
                display: 'flex', alignItems: 'center', gap: spacing.md,
              }}>
                <div style={{
                  width: '36px', height: '36px', borderRadius: radii.md,
                  backgroundColor: colors.infoDim, display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '16px',
                }}>
                  {'\u2699'}
                </div>
                <div>
                  <div style={{ fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }}>
                    Manage Agents
                  </div>
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted }}>
                    Start/stop monitor, configure API key, view logs
                  </div>
                </div>
              </div>
            </Link>
            <Link to="/cross-stack" style={{ textDecoration: 'none' }}>
              <div className="card-hover" style={{
                ...cardStyle,
                display: 'flex', alignItems: 'center', gap: spacing.md,
              }}>
                <div style={{
                  width: '36px', height: '36px', borderRadius: radii.md,
                  backgroundColor: colors.successDim, display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '16px',
                }}>
                  {'\u2194'}
                </div>
                <div>
                  <div style={{ fontSize: font.size.md, fontWeight: font.weight.medium, color: colors.text }}>
                    Cross-Stack View
                  </div>
                  <div style={{ fontSize: font.size.sm, color: colors.textMuted }}>
                    Frontend-backend correlation with AgentTel enrichment
                  </div>
                </div>
              </div>
            </Link>
          </div>
        </div>

        {/* Executive Summary */}
        <div>
          <SectionHeader title="Executive Summary" linkTo="/summary" linkLabel="Full view" />
          <div style={cardStyle}>
            {summary.loading && !summary.data && <div className="skeleton" style={{ height: '150px' }} />}
            {summary.data ? (
              <pre style={{
                fontFamily: font.mono, fontSize: font.size.sm, color: colors.textMuted,
                whiteSpace: 'pre-wrap', lineHeight: 1.6, margin: 0,
                maxHeight: '240px', overflow: 'auto',
              }}>
                {summary.data}
              </pre>
            ) : summary.error ? (
              <div style={{ color: colors.textDim, fontSize: font.size.sm, textAlign: 'center', padding: spacing.lg }}>
                Generate traffic to see executive summary.
              </div>
            ) : null}
          </div>
        </div>
      </div>

    </div>
  );
}

function formatUptime(seconds: number | null | undefined): string {
  if (!seconds) return '-';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}
