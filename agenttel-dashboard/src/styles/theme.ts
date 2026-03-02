// ── Color Palette ── GCP-inspired light theme ───────────────
// Clean whites, soft grays, Google Blue accents
export const colors = {
  bg: '#f8f9fa',
  surface: '#ffffff',
  surfaceHover: '#f1f3f4',
  surfaceActive: '#e8eaed',
  surfaceElevated: '#ffffff',
  border: '#dadce0',
  borderHover: 'rgba(25, 103, 210, 0.35)',
  borderSubtle: '#e8eaed',
  text: '#202124',
  textSecondary: '#3c4043',
  textMuted: '#5f6368',
  textDim: '#80868b',
  primary: '#1967d2',
  primaryLight: '#4285f4',
  primaryDim: 'rgba(25, 103, 210, 0.08)',
  primaryMuted: 'rgba(25, 103, 210, 0.04)',
  success: '#1e8e3e',
  successDim: 'rgba(30, 142, 62, 0.08)',
  warning: '#e37400',
  warningDim: 'rgba(227, 116, 0, 0.08)',
  error: '#d93025',
  errorDim: 'rgba(217, 48, 37, 0.08)',
  critical: '#c5221f',
  criticalDim: 'rgba(197, 34, 31, 0.08)',
  info: '#1a73e8',
  infoDim: 'rgba(26, 115, 232, 0.06)',
  // Agent attribution colors
  agentMonitor: '#1a73e8',
  agentInstrument: '#4285f4',
  agentMcp: '#1e8e3e',
};

// ── Status → Color Maps ───────────────────────────────────────
export const statusColors: Record<string, string> = {
  healthy: colors.success,
  ok: colors.success,
  normal: colors.success,
  running: colors.success,
  degraded: colors.warning,
  elevated: colors.warning,
  at_risk: colors.warning,
  critical: colors.critical,
  violated: colors.error,
  error: colors.error,
  unknown: colors.textMuted,
  idle: colors.textDim,
  done: colors.success,
  stopped: colors.textDim,
};

export const statusBgColors: Record<string, string> = {
  healthy: colors.successDim,
  ok: colors.successDim,
  normal: colors.successDim,
  running: colors.successDim,
  degraded: colors.warningDim,
  elevated: colors.warningDim,
  at_risk: colors.warningDim,
  critical: colors.criticalDim,
  violated: colors.errorDim,
  error: colors.errorDim,
  unknown: 'rgba(95, 99, 104, 0.06)',
};

export const riskColors: Record<string, string> = {
  low: colors.success,
  medium: colors.warning,
  high: colors.error,
};

// ── Spacing Scale ─────────────────────────────────────────────
export const spacing = {
  xs: '4px',
  sm: '8px',
  md: '12px',
  lg: '16px',
  xl: '20px',
  '2xl': '24px',
  '3xl': '32px',
  '4xl': '40px',
} as const;

// ── Typography ────────────────────────────────────────────────
export const font = {
  sans: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  mono: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
  size: {
    xs: '11px',
    sm: '12px',
    md: '13px',
    lg: '14px',
    xl: '16px',
    '2xl': '20px',
    '3xl': '24px',
    '4xl': '32px',
  },
  weight: {
    normal: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
} as const;

// ── Shadows ───────────────────────────────────────────────────
export const shadows = {
  subtle: '0 1px 2px rgba(60, 64, 67, 0.15), 0 1px 3px rgba(60, 64, 67, 0.08)',
  medium: '0 1px 3px rgba(60, 64, 67, 0.2), 0 4px 8px rgba(60, 64, 67, 0.1)',
  glow: (color: string) => `0 0 12px ${color}18, 0 0 3px ${color}12`,
  cardHover: '0 1px 3px rgba(60, 64, 67, 0.2), 0 4px 8px rgba(60, 64, 67, 0.08)',
} as const;

// ── Transitions ───────────────────────────────────────────────
export const transitions = {
  fast: 'all 0.15s cubic-bezier(0.4, 0, 0.2, 1)',
  normal: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
  slow: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
} as const;

// ── Radii ─────────────────────────────────────────────────────
export const radii = {
  sm: '4px',
  md: '8px',
  lg: '12px',
  xl: '16px',
  full: '9999px',
} as const;

// ── Reusable Style Mixins (CSSProperties) ─────────────────────
export const card: React.CSSProperties = {
  padding: spacing.xl,
  backgroundColor: colors.surface,
  border: `1px solid ${colors.border}`,
  borderRadius: radii.lg,
  boxShadow: shadows.subtle,
  transition: transitions.normal,
};

export const sectionHeader: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginBottom: spacing.lg,
};

export const sectionTitle: React.CSSProperties = {
  fontSize: font.size.xl,
  fontWeight: font.weight.semibold,
  color: colors.text,
};

export const viewAllLink: React.CSSProperties = {
  fontSize: font.size.sm,
  color: colors.primaryLight,
  textDecoration: 'none',
  cursor: 'pointer',
};
