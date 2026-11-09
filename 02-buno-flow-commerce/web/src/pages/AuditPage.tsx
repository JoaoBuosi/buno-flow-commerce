import { useState } from "react";
import type { Api } from "../services/api";
import type { AuditEvent, Order } from "../types";
import { useResource } from "../hooks/useResource";
import { Badge, date, Empty, Feedback, labels } from "../components/ui";
function Timeline({ api, id }: { api: Api; id: string }) {
  const state = useResource(
    async (signal) => {
      const [order, events] = await Promise.all([
        api.get<Order>("/orders/" + encodeURIComponent(id), signal),
        api.get<AuditEvent[]>(
          "/orders/" + encodeURIComponent(id) + "/history",
          signal,
        ),
      ]);
      return { order, events };
    },
    [api, id],
  );
  return (
    <>
      <Feedback {...state} retry={state.reload} />
      {state.data && (
        <>
          <div className="panelHead">
            <div>
              <h2>{state.data.order.customer_name}</h2>
              <p className="muted breakWord">{id}</p>
            </div>
            <Badge status={state.data.order.status} />
          </div>
          {state.data.events.length ? (
            <ol className="timeline">
              {state.data.events.map((event, i) => (
                <li key={i}>
                  <span className="timelineDot" />
                  <time>{date(event.occurred_at)}</time>
                  <h3>
                    {{
                      CREATED: "Pedido criado",
                      STATUS_CHANGED: "Status atualizado",
                      CANCELED: "Pedido cancelado",
                    }[event.action] || event.action}
                  </h3>
                  <p>
                    {event.old_status
                      ? labels[event.old_status] || event.old_status
                      : "Início"}{" "}
                    →{" "}
                    {event.new_status
                      ? labels[event.new_status] || event.new_status
                      : "—"}
                  </p>
                  {event.detail && <p>{event.detail}</p>}
                  {event.actor_id && (
                    <small className="breakWord">
                      Responsável (ID): {event.actor_id}
                    </small>
                  )}
                </li>
              ))}
            </ol>
          ) : (
            <Empty title="Nenhum evento disponível" />
          )}
        </>
      )}
    </>
  );
}
export function AuditPage({
  api,
  id,
  navigate,
  orders,
}: {
  api: Api;
  id: string | null;
  navigate: (id: string) => void;
  orders: () => void;
}) {
  const [draft, setDraft] = useState(id || "");
  return (
    <section className="panel">
      <h2>Auditoria por pedido</h2>
      <p className="muted">
        Consulte um pedido pelo identificador ou abra o histórico a partir dos
        detalhes. A consulta é individual; não há histórico global nesta versão.
      </p>
      <form
        className="filters"
        onSubmit={(e) => {
          e.preventDefault();
          navigate(draft.trim());
        }}
      >
        <label>
          Identificador do pedido
          <input
            required
            pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            title="Informe o UUID completo do pedido"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Cole o ID completo do pedido"
          />
        </label>
        <button className="primary">Consultar histórico</button>
        <button className="secondary" type="button" onClick={orders}>
          Escolher em Pedidos
        </button>
      </form>
      {id ? (
        <Timeline api={api} id={id} />
      ) : (
        <Empty title="Escolha um pedido para começar">
          <p>Criações, mudanças de status e cancelamentos aparecerão aqui.</p>
        </Empty>
      )}
    </section>
  );
}
