import { statusColors, colors } from '../styles/theme';

interface StatusBadgeProps {
  status: string;
  size?: 'sm' | 'md';
}

export function StatusBadge({ status, size = 'md' }: StatusBadgeProps) {
  const normalized = status.toLowerCase();
  const color = statusColors[normalized] || colors.textMuted;
  const dotSize = size === 'sm' ? '7px' : '9px';
  const fontSize = size === 'sm' ? '10px' : '11px';
  const isCritical = normalized === 'critical' || normalized === 'error' || normalized === 'violated';

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
      <span
        className={isCritical ? 'status-pulse' : undefined}
        style={{
          width: dotSize,
          height: dotSize,
          borderRadius: '50%',
          backgroundColor: color,
          display: 'inline-block',
          boxShadow: `0 0 6px ${color}`,
          flexShrink: 0,
        }}
      />
      <span
        style={{
          fontSize,
          color,
          textTransform: 'uppercase',
          fontWeight: 600,
          letterSpacing: '0.5px',
        }}
      >
        {status}
      </span>
    </span>
  );
}
