import { useState } from "react";
import type { Api } from "../services/api";
import type { Forecast } from "../types";
import { useResource } from "../hooks/useResource";
import { Empty, Feedback } from "../components/ui";
function Result({ api, sku }: { api: Api; sku: string }) {
  const state = useResource(
    (signal) =>
      api.get<Forecast>(
        "/insights/demand?sku=" + encodeURIComponent(sku),
        signal,
      ),
    [api, sku],
  );
  const f = state.data;
  const valid =
    f?.status === "EXPERIMENTAL_ESTIMATE" &&
    f.predictedUnits !== null &&
    Number.isFinite(f.predictedUnits) &&
    f.predictedUnits >= 0;
  return (
    <>
      <Feedback {...state} retry={state.reload} />
      {f && (
        <div className="subPanel">
          <span className="eyebrow">ANÁLISE DE {f.sku}</span>
          <div className="stats two">
            <article>
              <span>Unidades · últimas 8 semanas completas</span>
              <strong>{f.unitsLast8FullWeeks}</strong>
            </article>
            <article>
              <span>Semanas com vendas</span>
              <strong>{f.activeWeeksLast8} / 8</strong>
            </article>
          </div>
          <h3>
            {valid
              ? `Estimativa: ${f.predictedUnits} unidades`
              : "Histórico insuficiente para estimar"}
          </h3>
          <p>{f.message}</p>
          {valid && (
            <p className="muted">
              Semana iniciada em {f.weekStart} ·{" "}
              {f.method === "FOUR_WEEK_AVERAGE"
                ? "Média das últimas quatro semanas"
                : f.method}
            </p>
          )}
          {f.modelLimitations && <p className="muted">{f.modelLimitations}</p>}
        </div>
      )}
    </>
  );
}
export function IntelligencePage({
  api,
  sku,
  navigate,
}: {
  api: Api;
  sku: string;
  navigate: (sku: string) => void;
}) {
  const [draft, setDraft] = useState(sku);
  return (
    <div className="columns">
      <section className="panel">
        <span className="eyebrow">DEMANDA POR PRODUTO</span>
        <h2>Consultar demanda por SKU</h2>
        <p className="muted">
          Analise o histórico de pedidos pagos, excluindo os cancelados.
        </p>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            if (draft.trim()) navigate(draft.trim());
          }}
        >
          <label>
            SKU do produto
            <input
              required
              maxLength={80}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Ex.: CAMISETA-PRETA-M"
            />
          </label>
          <button className="primary">Analisar SKU</button>
        </form>
        {sku ? (
          <Result api={api} sku={sku} />
        ) : (
          <Empty title="Qual produto você quer analisar?">
            <p>Informe o SKU usado nos itens dos seus pedidos.</p>
          </Empty>
        )}
      </section>
      <section className="panel">
        <span className="eyebrow">COMO INTERPRETAR</span>
        <h2>Uma referência para planejar</h2>
        <p className="muted">
          A estimativa exige pelo menos 6 semanas com vendas e 20 unidades nas
          últimas 8 semanas completas.
        </p>
        <hr />
        <h3>Histórico recente pode ser insuficiente</h3>
        <p className="muted">
          A semana atual não entra no histórico completo. Sem dados suficientes,
          nenhuma previsão é apresentada.
        </p>
        <h3>Estimativa experimental</h3>
        <p className="muted">
          Não é garantia de vendas nem um modelo comercial validado. Considere
          estoque, sazonalidade e contexto antes de decidir compras.
        </p>
      </section>
    </div>
  );
}
