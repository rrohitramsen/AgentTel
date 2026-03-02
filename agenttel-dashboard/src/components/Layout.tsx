import { NavLink, Outlet } from 'react-router-dom';
import { colors, font, spacing, transitions } from '../styles/theme';
import { config } from '../config';

interface NavSection {
  label: string;
  items: { to: string; label: string; icon: string; external?: boolean }[];
}

const navSections: NavSection[] = [
  {
    label: 'Overview',
    items: [
      { to: '/', label: 'Command Center', icon: '\u25C9' },
      { to: '/fleet', label: 'Fleet Health', icon: '\u229A' },
    ],
  },
  {
    label: 'Reliability',
    items: [
      { to: '/slo', label: 'SLO Compliance', icon: '\u25CE' },
      { to: '/trends', label: 'Trends', icon: '\u2197' },
      { to: '/incidents', label: 'Incidents', icon: '\u26A0' },
    ],
  },
  {
    label: 'Intelligence',
    items: [
      { to: '/monitor', label: 'Monitor Agent', icon: '\u229A' },
      { to: '/cross-stack', label: 'Cross-Stack', icon: '\u2194' },
      { to: '/summary', label: 'Summary', icon: '\u2630' },
    ],
  },
  {
    label: 'Improve',
    items: [
      { to: '/gaps', label: 'Coverage Gaps', icon: '\u25A3' },
      { to: '/suggestions', label: 'Suggestions', icon: '\u2606' },
    ],
  },
  {
    label: 'Traffic',
    items: [
      { to: '/traffic', label: 'Generate Traffic', icon: '\u25B6' },
    ],
  },
  {
    label: 'Management',
    items: [
      { to: '/agents', label: 'Agents', icon: '\u2699' },
    ],
  },
];

export function Layout() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: colors.bg }}>
      {/* Sidebar */}
      <nav
        style={{
          width: '240px',
          backgroundColor: colors.surface,
          borderRight: `1px solid ${colors.border}`,
          flexShrink: 0,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'auto',
        }}
      >
        {/* Logo */}
        <div
          style={{
            padding: `${spacing.xl} ${spacing.xl} ${spacing.lg}`,
            borderBottom: `1px solid ${colors.border}`,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
            <div
              style={{
                width: '28px',
                height: '28px',
                borderRadius: '8px',
                background: `linear-gradient(135deg, ${colors.primary}, ${colors.primaryLight})`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '13px',
                color: '#fff',
              }}
            >
              {'\u25C9'}
            </div>
            <div>
              <div style={{ fontSize: font.size.lg, fontWeight: font.weight.bold, color: colors.text, letterSpacing: '-0.01em' }}>
                AgentTel
              </div>
              <div style={{ fontSize: '10px', color: colors.textDim, letterSpacing: '0.8px', textTransform: 'uppercase' }}>
                Command Center
              </div>
            </div>
          </div>
        </div>

        {/* Nav sections */}
        <div style={{ padding: `${spacing.md} 0`, flex: 1 }}>
          {navSections.map((section) => (
            <div key={section.label} style={{ marginBottom: spacing.sm }}>
              <div
                style={{
                  padding: `${spacing.sm} ${spacing.xl}`,
                  fontSize: '10px',
                  fontWeight: font.weight.semibold,
                  color: colors.textDim,
                  textTransform: 'uppercase',
                  letterSpacing: '0.8px',
                }}
              >
                {section.label}
              </div>
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  style={({ isActive }) => ({
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    padding: `7px ${spacing.xl}`,
                    color: isActive ? colors.text : colors.textMuted,
                    backgroundColor: isActive ? colors.surfaceHover : 'transparent',
                    textDecoration: 'none',
                    fontSize: font.size.md,
                    fontWeight: isActive ? font.weight.medium : font.weight.normal,
                    borderLeft: isActive
                      ? `2px solid ${colors.primary}`
                      : '2px solid transparent',
                    transition: transitions.fast,
                  })}
                >
                  <span style={{ fontSize: '13px', width: '18px', textAlign: 'center', opacity: 0.7 }}>
                    {item.icon}
                  </span>
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: spacing.lg,
            borderTop: `1px solid ${colors.border}`,
          }}
        >
          <a
            href={config.jaegerExternalUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: `${spacing.sm} ${spacing.md}`,
              color: colors.textMuted,
              textDecoration: 'none',
              fontSize: font.size.sm,
              borderRadius: '8px',
              transition: transitions.fast,
              backgroundColor: 'rgba(0,0,0,0.02)',
              border: `1px solid ${colors.border}`,
            }}
          >
            <span style={{ fontSize: '13px' }}>{'\u2B95'}</span>
            Traces (Jaeger)
            <span style={{ marginLeft: 'auto', fontSize: '10px', opacity: 0.4 }}>{'\u2197'}</span>
          </a>
        </div>
      </nav>

      {/* Main content */}
      <main
        style={{
          flex: 1,
          padding: spacing['3xl'],
          overflow: 'auto',
          minWidth: 0,
        }}
      >
        <div className="panel-enter" style={{ maxWidth: '1400px' }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
