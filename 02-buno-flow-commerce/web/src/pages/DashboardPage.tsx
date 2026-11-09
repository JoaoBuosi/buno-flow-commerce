import type { Api } from "../services/api";
import type { Dashboard, Order } from "../types";
import { useResource } from "../hooks/useResource";
import { Empty, Feedback, Icon, money } from "../components/ui";
import { OrderTable } from "../components/OrderTable";
import type { Page } from "../app/navigation";
export function DashboardPage({
  api,
  navigate,
}: {
  api: Api;
  navigate: (p: Page, q?: Record<string, string>) => void;
}) {
  const state = useResource(
    async (signal) => {
      const [stats, orders] = await Promise.all([
        api.get<Dashboard>("/dashboard", signal),
        api.get<Order[]>("/orders?page=0&size=5", signal),
      ]);
      return { stats, orders };
    },
    [api],
  );
  const s = state.data?.stats;
  return (
    <>
      <div className="pageActions">
        <span className="muted">Visão consolidada da loja</span>
        <button
          className="secondary"
          onClick={state.reload}
          disabled={state.loading}
        >
          Atualizar visão geral
        </button>
      </div>
      <Feedback {...state} retry={state.reload} />
      {s && (
        <>
          <section className="stats" aria-label="Indicadores da loja">
            {[
              ["Total de pedidos", s.order_count],
              ["Em andamento", s.active_count],
              ["Entregues", s.delivered_count],
              ["Cancelados", s.canceled_count],
            ].map(([name, value]) => (
              <article key={name}>
                <span>{name}</span>
                <strong>{value}</strong>
              </article>
            ))}
            <article className="valueCard">
              <span>Valor dos pedidos não cancelados</span>
              <strong>{money(s.order_value)}</strong>
              <small>Valor dos pedidos · não representa receita recebida</small>
            </article>
          </section>
          <section className="quickActions">
            <button onClick={() => navigate("orders", { new: "1" })}>
              <Icon name="orders" />
              <span>
                <strong>Criar pedido</strong>
                <small>Registre uma nova venda</small>
              </span>
              <span aria-hidden="true">↗</span>
            </button>
            <button onClick={() => navigate("imports")}>
              <Icon name="imports" />
              <span>
                <strong>Importar CSV</strong>
                <small>Adicione pedidos em lote</small>
              </span>
              <span aria-hidden="true">↗</span>
            </button>
          </section>
          <section className="panel">
            <div className="panelHead">
              <div>
                <h2>Últimos pedidos</h2>
                <p className="muted">
                  Os cinco registros mais recentes da loja.
                </p>
              </div>
              <button className="secondary" onClick={() => navigate("orders")}>
                Consultar pedidos
              </button>
            </div>
            {state.data!.orders.length ? (
              <OrderTable
                orders={state.data!.orders}
                open={(id) => navigate("orders", { id })}
              />
            ) : (
              <Empty title="Seu primeiro pedido começa aqui">
                <p>Cadastre um pedido ou importe sua operação por CSV.</p>
                <button
                  className="primary"
                  onClick={() => navigate("orders", { new: "1" })}
                >
                  + Novo pedido
                </button>
              </Empty>
            )}
          </section>
        </>
      )}
    </>
  );
}
