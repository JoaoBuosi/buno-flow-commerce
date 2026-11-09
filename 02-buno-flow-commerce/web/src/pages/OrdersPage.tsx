import { useEffect, useState } from "react";
import type { Api } from "../services/api";
import type { Order } from "../types";
import type { Page } from "../app/navigation";
import { useResource } from "../hooks/useResource";
import { Empty, Feedback, labels } from "../components/ui";
import { OrderTable } from "../components/OrderTable";
import { OrderForm } from "../components/OrderForm";
import { OrderDetail } from "../components/OrderDetail";
export function OrdersPage({
  api,
  params,
  navigate,
  notify,
}: {
  api: Api;
  params: URLSearchParams;
  navigate: (p: Page, q?: Record<string, string>) => void;
  notify: (s: string) => void;
}) {
  const search = params.get("search") || "",
    status = params.get("status") || "";
  const page = Math.min(100000, Math.max(0, Number(params.get("page")) || 0));
  const id = params.get("id");
  const [draft, setDraft] = useState(search);
  useEffect(() => setDraft(search), [search]);
  const query = new URLSearchParams({
    page: String(page),
    size: "25",
    ...(search ? { search } : {}),
    ...(status ? { status } : {}),
  }).toString();
  const state = useResource(
    async (signal) => {
      const [orders, count] = await Promise.all([
        api.get<Order[]>("/orders?" + query, signal),
        api.get<{ total: number }>("/orders/count?" + query, signal),
      ]);
      return { orders, total: Number(count.total) };
    },
    [api, query],
  );
  function go(values: Record<string, string>) {
    const q = Object.fromEntries(params);
    delete q.new;
    delete q.id;
    navigate("orders", { ...q, ...values });
  }
  return (
    <>
      <section className="panel">
        <div className="panelHead">
          <div>
            <h2>Pedidos da loja</h2>
            <p className="muted">Consulte, cadastre e acompanhe cada pedido.</p>
          </div>
          <button className="primary" onClick={() => go({ new: "1" })}>
            + Novo pedido
          </button>
        </div>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            go({ search: draft.trim(), page: "0" });
          }}
        >
          <label>
            Pesquisar por cliente
            <input
              placeholder="Nome do cliente"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
            />
          </label>
          <label>
            Status
            <select
              value={status}
              onChange={(e) => go({ status: e.target.value, page: "0" })}
            >
              <option value="">Todos os status</option>
              {Object.entries(labels).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <button className="secondary">Pesquisar</button>
          {(search || status) && (
            <button
              type="button"
              className="link"
              onClick={() => navigate("orders")}
            >
              Limpar filtros
            </button>
          )}
        </form>
        <Feedback {...state} retry={state.reload} />
        {state.data && (
          <>
            {state.data.orders.length ? (
              <OrderTable
                orders={state.data.orders}
                open={(id) => go({ id })}
              />
            ) : (
              <Empty
                title={
                  search || status
                    ? "Nenhum pedido corresponde aos filtros"
                    : "Nenhum pedido cadastrado"
                }
              >
                <p>
                  {search || status
                    ? "Tente outro cliente ou status."
                    : "Comece cadastrando seu primeiro pedido."}
                </p>
                <button
                  className="secondary"
                  onClick={() =>
                    search || status ? navigate("orders") : go({ new: "1" })
                  }
                >
                  {search || status
                    ? "Limpar filtros"
                    : "Criar primeiro pedido"}
                </button>
              </Empty>
            )}
            <div className="pagination">
              <span>
                {state.data.total} pedidos · Página {page + 1} de{" "}
                {Math.max(1, Math.ceil(state.data.total / 25))}
              </span>
              <div className="inline">
                <button
                  className="secondary"
                  disabled={page === 0}
                  onClick={() => go({ page: String(page - 1) })}
                >
                  Anterior
                </button>
                <button
                  className="secondary"
                  disabled={(page + 1) * 25 >= state.data.total}
                  onClick={() => go({ page: String(page + 1) })}
                >
                  Próxima
                </button>
              </div>
            </div>
          </>
        )}
      </section>
      {params.get("new") === "1" && (
        <OrderForm
          api={api}
          close={() => go({})}
          saved={(o) => {
            notify("Pedido cadastrado");
            navigate("orders", { id: o.id });
            state.reload();
          }}
        />
      )}
      {id && (
        <OrderDetail
          key={id}
          api={api}
          id={id}
          close={() => go({})}
          changed={() => {
            notify("Pedido atualizado");
            state.reload();
          }}
          audit={() => navigate("history", { id })}
        />
      )}
    </>
  );
}
