import { colors } from '../styles/theme';

interface TrendChartProps {
  data: { value: number }[];
  width?: number | string;
  height?: number;
  color?: string;
  label?: string;
  showGradient?: boolean;
}

export function TrendChart({
  data,
  width = 200,
  height = 60,
  color = colors.primaryLight,
  label,
  showGradient = true,
}: TrendChartProps) {
  if (data.length < 2) {
    return (
      <div
        style={{
          width,
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <span style={{ color: colors.textDim, fontSize: '11px' }}>No data</span>
      </div>
    );
  }

  const svgWidth = typeof width === 'number' ? width : 200;
  const values = data.map((d) => d.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const padding = 4;

  const coords = values.map((v, i) => ({
    x: padding + (i / (values.length - 1)) * (svgWidth - padding * 2),
    y: height - padding - ((v - min) / range) * (height - padding * 2),
  }));

  const polylinePoints = coords.map((c) => `${c.x},${c.y}`).join(' ');

  // Build a closed path for gradient fill
  const areaPath = showGradient
    ? `M ${coords[0].x},${height} ` +
      coords.map((c) => `L ${c.x},${c.y}`).join(' ') +
      ` L ${coords[coords.length - 1].x},${height} Z`
    : '';

  const gradientId = `grad-${Math.random().toString(36).slice(2, 8)}`;

  return (
    <div>
      {label && (
        <div style={{ fontSize: '11px', color: colors.textMuted, marginBottom: '4px' }}>
          {label}
        </div>
      )}
      <svg
        width={width}
        height={height}
        style={{ display: 'block', width: '100%' }}
        viewBox={`0 0 ${svgWidth} ${height}`}
        preserveAspectRatio="none"
      >
        {showGradient && (
          <>
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={color} stopOpacity="0.2" />
                <stop offset="100%" stopColor={color} stopOpacity="0" />
              </linearGradient>
            </defs>
            <path d={areaPath} fill={`url(#${gradientId})`} />
          </>
        )}
        <polyline
          points={polylinePoints}
          fill="none"
          stroke={color}
          strokeWidth="1.5"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}
