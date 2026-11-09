import { useState } from "react";
import type { Api } from "../services/api";
import type { Order } from "../types";
import { useResource } from "../hooks/useResource";
import { Badge, date, Feedback, labels, Modal, money } from "./ui";
const next: Record<string, string> = {
  CREATED: "PAID",
  PAID: "PROCESSING",
  PROCESSING: "SHIPPED",
  SHIPPED: "DELIVERED",
};
export function OrderDetail({
  api,
  id,
  close,
  changed,
  audit,
}: {
  api: Api;
  id: string;
  close: () => void;
  changed: () => void;
  audit: () => void;
}) {
  const state = useResource(
    (signal) => api.get<Order>("/orders/" + encodeURIComponent(id), signal),
    [api, id],
  );
  const [busy, setBusy] = useState(false),
    [cancel, setCancel] = useState(false),
    [reason, setReason] = useState(""),
    [error, setError] = useState("");
  async function update(target: string) {
    setBusy(true);
    setError("");
    try {
      await api.send(
        "/orders/" +
          encodeURIComponent(id) +
          (target === "CANCELED" ? "/cancel" : "/status"),
        target === "CANCELED" ? "POST" : "PATCH",
        target === "CANCELED" ? { reason: reason.trim() } : { status: target },
      );
      setCancel(false);
      state.reload();
      changed();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const o = state.data;
  return (
    <Modal title="Detalhes do pedido" onClose={close} busy={busy}>
      <Feedback {...state} retry={state.reload} />
      {o && (
        <>
          <div className="orderSummary">
            <span className="eyebrow">PEDIDO #{o.id.slice(0, 8)}</span>
            <h3>{o.customer_name}</h3>
            <Badge status={o.status} />
            <p className="muted">Registrado em {date(o.created_at)}</p>
          </div>
          <div className="detailItems">
            {o.items?.map((item, i) => (
              <div key={i}>
                <div>
                  <strong>{item.description}</strong>
                  <small>
                    {item.sku} · {item.quantity} un. × {money(item.unit_price)}
                  </small>
                </div>
                <strong>{money(item.subtotal)}</strong>
              </div>
            ))}
          </div>
          <div className="total">
            <span>Total</span>
            <strong>{money(o.total)}</strong>
          </div>
          {o.cancellation_reason && (
            <p className="error">
              Motivo do cancelamento: {o.cancellation_reason}
            </p>
          )}
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          {cancel ? (
            <form
              className="stack subPanel"
              onSubmit={(e) => {
                e.preventDefault();
                void update("CANCELED");
              }}
            >
              <h3>Confirmar cancelamento</h3>
              <p className="muted">
                O pedido será finalizado como cancelado. Esta ação não pode ser
                desfeita.
              </p>
              <label>
                Justificativa
                <textarea
                  autoFocus
                  required
                  maxLength={500}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <div className="inline">
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => setCancel(false)}
                >
                  Manter pedido
                </button>
                <button className="danger" disabled={busy || !reason.trim()}>
                  Confirmar cancelamento
                </button>
              </div>
            </form>
          ) : (
            <div className="inline actions">
              {next[o.status] && (
                <button
                  className="primary"
                  disabled={busy}
                  onClick={() => void update(next[o.status])}
                >
                  {busy ? "Atualizando…" : "Avançar: " + labels[next[o.status]]}
                </button>
              )}
              {next[o.status] && (
                <button
                  className="danger"
                  disabled={busy}
                  onClick={() => setCancel(true)}
                >
                  Cancelar pedido
                </button>
              )}
            </div>
          )}
          <button className="link" disabled={busy} onClick={audit}>
            Ver histórico deste pedido →
          </button>
        </>
      )}
    </Modal>
  );
}
