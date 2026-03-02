// ── Color Palette ── Enterprise-grade, GCP-inspired ─────────
// Calmer, muted tones with purple identity preserved
export const colors = {
    bg: '#111318',
    surface: '#1a1d25',
    surfaceHover: '#21252f',
    surfaceActive: '#292e3a',
    surfaceElevated: '#1e2129',
    border: 'rgba(255, 255, 255, 0.07)',
    borderHover: 'rgba(139, 124, 246, 0.35)',
    borderSubtle: 'rgba(255, 255, 255, 0.04)',
    text: '#e1e4ed',
    textSecondary: '#c4c8d6',
    textMuted: '#8890a4',
    textDim: '#5a6178',
    primary: '#8b7cf6',
    primaryLight: '#b4a9f9',
    primaryDim: 'rgba(139, 124, 246, 0.10)',
    primaryMuted: 'rgba(139, 124, 246, 0.06)',
    success: '#3dce70',
    successDim: 'rgba(61, 206, 112, 0.08)',
    warning: '#f0b429',
    warningDim: 'rgba(240, 180, 41, 0.08)',
    error: '#e85d5d',
    errorDim: 'rgba(232, 93, 93, 0.08)',
    critical: '#d44040',
    criticalDim: 'rgba(212, 64, 64, 0.10)',
    info: '#6891e6',
    infoDim: 'rgba(104, 145, 230, 0.08)',
    // Agent attribution colors
    agentMonitor: '#6891e6',
    agentInstrument: '#b4a9f9',
    agentMcp: '#3dce70',
};
// ── Status → Color Maps ───────────────────────────────────────
export const statusColors = {
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
export const statusBgColors = {
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
    unknown: 'rgba(136, 144, 164, 0.06)',
};
export const riskColors = {
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
};
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
};
// ── Shadows ───────────────────────────────────────────────────
export const shadows = {
    subtle: '0 1px 2px rgba(0, 0, 0, 0.2)',
    medium: '0 2px 8px rgba(0, 0, 0, 0.25)',
    glow: (color) => `0 0 16px ${color}22, 0 0 4px ${color}18`,
    cardHover: '0 2px 12px rgba(139, 124, 246, 0.08), 0 0 1px rgba(139, 124, 246, 0.15)',
};
// ── Transitions ───────────────────────────────────────────────
export const transitions = {
    fast: 'all 0.15s cubic-bezier(0.4, 0, 0.2, 1)',
    normal: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
    slow: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
};
// ── Radii ─────────────────────────────────────────────────────
export const radii = {
    sm: '4px',
    md: '8px',
    lg: '12px',
    xl: '16px',
    full: '9999px',
};
// ── Reusable Style Mixins (CSSProperties) ─────────────────────
export const card = {
    padding: spacing.xl,
    backgroundColor: colors.surface,
    border: `1px solid ${colors.border}`,
    borderRadius: radii.lg,
    transition: transitions.normal,
};
export const sectionHeader = {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.lg,
};
export const sectionTitle = {
    fontSize: font.size.xl,
    fontWeight: font.weight.semibold,
    color: colors.text,
};
export const viewAllLink = {
    fontSize: font.size.sm,
    color: colors.primaryLight,
    textDecoration: 'none',
    cursor: 'pointer',
};
