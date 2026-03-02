import { colors, font } from '../styles/theme';
import { config } from '../config';

interface TraceLinkProps {
  traceId?: string;
  service?: string;
  operation?: string;
  label?: string;
  style?: React.CSSProperties;
}

export function TraceLink({ traceId, service, operation, label, style }: TraceLinkProps) {
  let href: string;
  let displayLabel: string;

  if (traceId) {
    href = `${config.jaegerExternalUrl}/trace/${traceId}`;
    displayLabel = label || `View trace`;
  } else if (service) {
    const params = new URLSearchParams({ service });
    if (operation) params.set('operation', operation);
    href = `${config.jaegerExternalUrl}/search?${params}`;
    displayLabel = label || `View in Jaeger`;
  } else {
    href = config.jaegerExternalUrl;
    displayLabel = label || 'Open Jaeger';
  }

  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      style={{
        color: colors.primaryLight,
        textDecoration: 'none',
        fontSize: font.size.sm,
        fontWeight: 500,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        transition: 'color 0.15s ease',
        ...style,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.color = colors.primary;
        e.currentTarget.style.textDecoration = 'underline';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.color = colors.primaryLight;
        e.currentTarget.style.textDecoration = 'none';
      }}
    >
      {displayLabel}
      <span style={{ fontSize: '10px', opacity: 0.7 }}>{'\u2197'}</span>
    </a>
  );
}
