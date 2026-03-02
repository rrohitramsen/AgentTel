import { colors, statusColors, font, spacing, radii } from '../styles/theme';

interface SloGaugeProps {
  name: string;
  budgetPercent: number;
  burnRate: number;
  status: string;
}

export function SloGauge({ name, budgetPercent, burnRate, status }: SloGaugeProps) {
  const radius = 28;
  const circumference = 2 * Math.PI * radius;
  const filled = Math.max(0, Math.min(100, budgetPercent));
  const offset = circumference - (filled / 100) * circumference;
  const color = statusColors[status.toLowerCase()] || colors.textMuted;

  return (
    <div
      className="card-hover"
      style={{
        padding: spacing.lg,
        backgroundColor: colors.surface,
        border: `1px solid ${colors.border}`,
        borderRadius: radii.md,
        display: 'flex',
        alignItems: 'center',
        gap: spacing.lg,
      }}
    >
      <svg width="64" height="64" viewBox="0 0 64 64">
        <circle
          cx="32"
          cy="32"
          r={radius}
          fill="none"
          stroke="rgba(0,0,0,0.08)"
          strokeWidth="5"
        />
        <circle
          cx="32"
          cy="32"
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth="5"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform="rotate(-90 32 32)"
          style={{ transition: 'stroke-dashoffset 0.6s ease' }}
        />
        <text
          x="32"
          y="32"
          textAnchor="middle"
          dominantBaseline="central"
          fill={colors.text}
          fontSize="11"
          fontWeight="700"
          fontFamily={font.mono}
        >
          {filled.toFixed(0)}%
        </text>
      </svg>
      <div>
        <div style={{ fontSize: font.size.md, fontWeight: font.weight.semibold, color: colors.text }}>
          {name}
        </div>
        <div style={{ fontSize: font.size.xs, color: colors.textMuted, marginTop: '2px' }}>
          Burn rate: <span style={{ color, fontWeight: font.weight.semibold }}>{burnRate.toFixed(1)}x</span>
        </div>
      </div>
    </div>
  );
}
