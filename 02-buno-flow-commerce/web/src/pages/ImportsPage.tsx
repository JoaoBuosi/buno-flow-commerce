import { useRef, useState } from "react";
import { downloadFile, type Api } from "../services/api";
import { csvColumns, csvTemplate, validateCsv } from "../services/csv";
import type { ImportResult } from "../types";
import { Icon } from "../components/ui";
export function ImportsPage({
  api,
  notify,
  openOrder,
}: {
  api: Api;
  notify: (s: string) => void;
  openOrder: (id: string) => void;
}) {
  const [file, setFile] = useState<File | null>(null),
    [preview, setPreview] = useState<{ rows: number; orders: number } | null>(
      null,
    ),
    [result, setResult] = useState<ImportResult | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState("");
  const version = useRef(0);
  async function select(f?: File) {
    const current = ++version.current;
    setFile(null);
    setPreview(null);
    setResult(null);
    setError("");
    if (!f) return;
    setBusy("validating");
    try {
      if (!f.size || f.size > 1000000)
        throw new Error("Selecione um CSV não vazio de até 1 MB.");
      const parsed = validateCsv(await f.text());
      if (current === version.current) {
        setPreview(parsed);
        setFile(f);
      }
    } catch (e) {
      if (current === version.current) setError((e as Error).message);
    } finally {
      if (current === version.current) setBusy("");
    }
  }
  async function upload() {
    if (!file) return;
    setBusy("import");
    setError("");
    try {
      const body = new FormData();
      body.append("file", file);
      const r = await api.send<ImportResult>("/orders/import", "POST", body);
      setResult(r);
      setFile(null);
      setPreview(null);
      notify(r.imported + " pedido(s) importado(s)");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  async function exportCsv() {
    setBusy("export");
    setError("");
    try {
      downloadFile(
        await api.download("/orders/export"),
        "flow-commerce-orders.csv",
      );
      notify("Exportação CSV gerada. Download iniciado.");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  return (
    <>
      <div className="columns">
        <section className="panel">
          <span className="eyebrow">ENTRADA DE PEDIDOS</span>
          <h2>Importar pedidos</h2>
          <p className="muted">
            Cada linha representa um item. Repita o order_key para agrupar itens
            do mesmo pedido.
          </p>
          <div className="uploadArea">
            <Icon name="imports" />
            <label>
              Selecionar arquivo CSV
              <input
                type="file"
                accept=".csv,text/csv"
                disabled={!!busy}
                onChange={(e) => {
                  void select(e.target.files?.[0]);
                  e.currentTarget.value = "";
                }}
              />
            </label>
            <small>UTF-8 · até 1 MB · até 500 linhas</small>
          </div>
          {preview && (
            <div className="subPanel">
              <strong>{file?.name}</strong>
              <p>
                {preview.orders} pedido(s) · {preview.rows} item(ns) validados
              </p>
              <p className="muted">
                A importação será confirmada após a validação da loja pelo
                servidor.
              </p>
            </div>
          )}
          <button
            className="primary"
            disabled={!file || !!busy}
            onClick={() => void upload()}
          >
            {busy === "import"
              ? "Importando…"
              : busy === "validating"
                ? "Validando arquivo…"
                : "Importar CSV"}
          </button>
          {result && (
            <div className="success" role="status">
              <h3>{result.imported} pedido(s) importado(s)</h3>
              <ul>
                {result.orders.map((o) => (
                  <li key={o.orderId}>
                    <button
                      className="link"
                      onClick={() => openOrder(o.orderId)}
                    >
                      Abrir pedido {o.externalKey} →
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
        <section className="panel">
          <span className="eyebrow">SAÍDA DE DADOS</span>
          <h2>Exportar pedidos da loja</h2>
          <p className="muted">
            Baixe os pedidos da loja em CSV, com os campos fornecidos pela
            exportação. Os filtros da página Pedidos não limitam este arquivo.
          </p>
          <button
            className="secondary"
            disabled={!!busy}
            onClick={() => void exportCsv()}
          >
            {busy === "export" ? "Preparando arquivo…" : "Exportar CSV"}
          </button>
          <div className="subPanel">
            <h3>Antes de importar</h3>
            <p className="muted">
              Use vírgula como separador e ponto para valores decimais. O mesmo
              pedido precisa manter o cliente e não pode repetir SKU. Chaves já
              importadas são recusadas; nenhum pedido é salvo se o lote falhar.
            </p>
            <button
              className="link"
              onClick={() => {
                downloadFile(
                  new Blob([csvTemplate], { type: "text/csv;charset=utf-8" }),
                  "modelo-pedidos.csv",
                );
                notify(
                  "Modelo CSV gerado. Substitua os dados de exemplo antes de importar.",
                );
              }}
            >
              Baixar modelo de CSV ↓
            </button>
          </div>
        </section>
      </div>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <section className="panel">
        <h2>Colunas obrigatórias</h2>
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Coluna</th>
                <th>Como preencher</th>
              </tr>
            </thead>
            <tbody>
              {csvColumns.map((c, i) => (
                <tr key={c}>
                  <td>
                    <code>{c}</code>
                  </td>
                  <td>
                    {
                      [
                        "Chave externa única por pedido, até 100 caracteres",
                        "Nome do cliente, até 120 caracteres",
                        "Código do produto, até 80 caracteres",
                        "Descrição do item, até 200 caracteres",
                        "Quantidade inteira, de 1 a 100000",
                        "Preço unitário positivo, com até duas casas decimais (19.90)",
                      ][i]
                    }
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted">
          O arquivo exportado é um relatório; utilize o modelo acima para novas
          importações.
        </p>
      </section>
    </>
  );
}
