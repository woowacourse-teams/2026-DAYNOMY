export function ReturnLine({ rates }: { rates: (number | null)[] }) {
  const range = Math.max(1, ...rates.map((rate) => Math.abs(rate ?? 0)));
  const x = (index: number) => ((index + 0.5) / rates.length) * 100;
  const y = (rate: number) => 72 - (rate / range) * 64;

  return (
    <svg
      className="league-return-line"
      viewBox="0 0 100 144"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <line className="league-zero-line" x1="0" y1="72" x2="100" y2="72" />
      {rates.map((rate, index) => {
        if (rate === null) return null;
        const previous = rates[index - 1];
        return (
          <g key={index}>
            {previous != null ? (
              <line x1={x(index - 1)} y1={y(previous)} x2={x(index)} y2={y(rate)} />
            ) : null}
            <circle cx={x(index)} cy={y(rate)} r="0.1" />
          </g>
        );
      })}
    </svg>
  );
}
