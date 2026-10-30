import {
  CSV_HOLDING_FIELDS,
  csvMappingError,
  csvTableColumns,
  guessCsvColumns,
  type CsvColumnMapping,
  type CsvHoldingField,
  type CsvHoldingTable,
} from '../csvHoldingsImport';

export function PortfolioCsvColumnMapping({
  table,
  headerIndex,
  mapping,
  disabled,
  previewReady,
  onChange,
  onDelimiter,
}: {
  table: CsvHoldingTable;
  headerIndex: number;
  mapping: CsvColumnMapping;
  disabled: boolean;
  previewReady: boolean;
  onChange: (headerIndex: number, mapping: CsvColumnMapping) => void;
  onDelimiter: (delimiter: string) => void;
}) {
  const columns = csvTableColumns(table, headerIndex);
  const sample = table.rows[headerIndex + 1]?.cells ?? [];
  const error = csvMappingError(mapping);
  return (
    <details className="portfolio-csv-mapping" open={!previewReady && Boolean(error)}>
      <summary>
        열 연결 설정 · {Object.values(mapping).filter((column) => column >= 0).length}개 연결됨
      </summary>
      <div className="portfolio-csv-mapping-body">
        <label className="portfolio-csv-header-row">
          구분자
          <select
            aria-label="CSV 구분자"
            disabled={disabled}
            value={table.delimiter}
            onChange={(event) => onDelimiter(event.target.value)}
          >
            <option value=",">쉼표 (,)</option>
            <option value={'\t'}>탭</option>
            <option value=";">세미콜론 (;)</option>
            <option value="|">파이프 (|)</option>
          </select>
        </label>
        <label className="portfolio-csv-header-row">
          열 이름이 있는 행
          <select
            aria-label="CSV 헤더 행"
            value={headerIndex}
            disabled={disabled}
            onChange={(event) => {
              const index = Number(event.target.value);
              onChange(index, guessCsvColumns(index < 0 ? [] : table.rows[index].cells));
            }}
          >
            <option value={-1}>열 이름 없음</option>
            {table.rows.slice(0, 20).map((row, index) => (
              <option key={row.line} value={index}>
                {row.line}행 · {row.cells.join(' / ').slice(0, 70)}
              </option>
            ))}
          </select>
        </label>
        <div className="portfolio-csv-mapping-fields">
          {Object.entries(CSV_HOLDING_FIELDS).map(([key, title]) => {
            const field = key as CsvHoldingField;
            return (
              <label key={key}>
                {title}
                <select
                  aria-label={`CSV ${title} 열`}
                  value={mapping[field]}
                  disabled={disabled}
                  onChange={(event) =>
                    onChange(headerIndex, { ...mapping, [key]: Number(event.target.value) })
                  }
                >
                  <option value={-1}>연결 안 함</option>
                  {columns.map((column, index) => (
                    <option key={index} value={index}>
                      {index + 1}. {column} · {sample[index] || '빈 값'}
                    </option>
                  ))}
                </select>
              </label>
            );
          })}
        </div>
      </div>
      <div className="portfolio-csv-mapping-actions">
        <span>{error || '열 연결을 바꾸면 미리보기에 자동 반영됩니다.'}</span>
      </div>
    </details>
  );
}
