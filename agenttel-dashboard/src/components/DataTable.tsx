import { colors, font, spacing } from '../styles/theme';

interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
  width?: string;
  align?: 'left' | 'right' | 'center';
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  keyField: string;
}

export function DataTable<T extends Record<string, unknown>>({
  columns,
  data,
  keyField,
}: DataTableProps<T>) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table
        style={{
          width: '100%',
          borderCollapse: 'collapse',
          fontSize: font.size.md,
        }}
      >
        <thead>
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                style={{
                  padding: `${spacing.sm} ${spacing.md}`,
                  textAlign: (col.align || 'left') as 'left' | 'right' | 'center',
                  color: colors.textDim,
                  borderBottom: `1px solid ${colors.border}`,
                  fontWeight: font.weight.semibold,
                  fontSize: font.size.xs,
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                  width: col.width,
                }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr
              key={String(row[keyField])}
              className="row-hover"
              style={{
                borderBottom: `1px solid ${colors.border}`,
                transition: 'background-color 0.1s ease',
              }}
            >
              {columns.map((col) => (
                <td
                  key={col.key}
                  style={{
                    padding: `${spacing.sm} ${spacing.md}`,
                    color: colors.text,
                    textAlign: (col.align || 'left') as 'left' | 'right' | 'center',
                  }}
                >
                  {col.render ? col.render(row) : String(row[col.key] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
