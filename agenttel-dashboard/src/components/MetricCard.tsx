import { colors, font, spacing, radii, card as cardStyle } from '../styles/theme';

interface MetricCardProps {
  label: string;
  value: string;
  trend?: 'up' | 'down' | 'stable';
  changePercent?: number;
  color?: string;
  subtitle?: string;
  children?: React.ReactNode;
}

const trendArrows: Record<string, string> = {
  up: '\u2197',
  down: '\u2198',
  stable: '\u2192',
};

const trendColors: Record<string, string> = {
  up: colors.success,
  down: colors.error,
  stable: colors.textMuted,
};

export function MetricCard({ label, value, trend, changePercent, color, subtitle, children }: MetricCardProps) {
  return (
    <div className="card-hover" style={cardStyle}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: spacing.sm,
        }}
      >
        <div style={{ fontSize: font.size.sm, color: colors.textMuted, textTransform: 'uppercase', letterSpacing: '0.3px' }}>
          {label}
        </div>
        {trend && (
          <div
            style={{
              fontSize: font.size.sm,
              color: trendColors[trend],
              fontWeight: font.weight.semibold,
              display: 'flex',
              alignItems: 'center',
              gap: '2px',
            }}
          >
            {trendArrows[trend]}
            {changePercent !== undefined && ` ${Math.abs(changePercent).toFixed(1)}%`}
          </div>
        )}
      </div>
      <div
        style={{
          fontSize: font.size['3xl'],
          fontWeight: font.weight.bold,
          color: color || colors.text,
          fontFamily: font.mono,
          lineHeight: 1.2,
        }}
      >
        {value}
      </div>
      {subtitle && (
        <div style={{ fontSize: font.size.xs, color: colors.textDim, marginTop: spacing.xs }}>
          {subtitle}
        </div>
      )}
      {children && <div style={{ marginTop: spacing.md }}>{children}</div>}
    </div>
  );
}
