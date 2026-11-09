import { useEffect, useRef, type ReactNode } from "react";
export const money = (v: number) =>
  Number(v).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const date = (v: string) =>
  new Date(v).toLocaleString("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  });
export const labels: Record<string, string> = {
  CREATED: "Criado",
  PAID: "Pago",
  PROCESSING: "Em separação",
  SHIPPED: "Enviado",
  DELIVERED: "Entregue",
  CANCELED: "Cancelado",
};
export function Badge({ status }: { status: string }) {
  return (
    <span className={"status status-" + status}>
      {labels[status] || status}
    </span>
  );
}
export function Icon({ name }: { name: string }) {
  const paths: Record<string, string> = {
    dashboard: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
    orders: "M6 3h12v18H6z M9 7h6 M9 11h6 M9 15h4",
    imports: "M12 3v12 m-4-4 4 4 4-4 M4 17v4h16v-4",
    history: "M3 11a9 9 0 1 1 2 7 M3 4v7h7 M12 7v5l3 2",
    intelligence: "M3 20h18 M6 16v-4 M12 16V7 M18 16V3",
    settings:
      "M12 3v3 M12 18v3 M3 12h3 M18 12h3 M6 6l2 2 M16 16l2 2 M6 18l2-2 M16 8l2-2 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  };
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] || paths.orders} />
    </svg>
  );
}
export function Feedback({
  error,
  loading,
  retry,
}: {
  error?: string;
  loading?: boolean;
  retry?: () => void;
}) {
  return loading ? (
    <div role="status" className="loading">
      <span className="spinner" />
      Carregando dados…
    </div>
  ) : error ? (
    <div role="alert" className="error">
      <strong>Não foi possível carregar</strong>
      <p>{error}</p>
      {retry && (
        <button className="secondary" onClick={retry}>
          Tentar novamente
        </button>
      )}
    </div>
  ) : null;
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="emptyIcon">
        <Icon name="orders" />
      </span>
      <h3>{title}</h3>
      {children}
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
  busy = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.showModal();
    return () => {
      ref.current?.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="panelHead">
        <h2>{title}</h2>
        <button
          className="iconButton"
          aria-label="Fechar janela"
          disabled={busy}
          onClick={onClose}
        >
          ×
        </button>
      </div>
      {children}
    </dialog>
  );
}
