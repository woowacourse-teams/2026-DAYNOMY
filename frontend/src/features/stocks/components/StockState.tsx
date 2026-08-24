type StockStateProps = {
  title: string;
  description?: string;
  role?: 'alert';
  busy?: boolean;
};

export function StockState({ title, description, role, busy = false }: StockStateProps) {
  return (
    <section className="stock-state" role={role} aria-busy={busy}>
      <strong>{title}</strong>
      {description ? <p>{description}</p> : null}
    </section>
  );
}
