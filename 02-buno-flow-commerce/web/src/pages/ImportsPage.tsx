import type {ImportResult} from "../types/commerce";
const header="order_key,customer,sku,description,quantity,price";
const example=header+"\nEXEMPLO-001,Cliente Exemplo,SKU-001,Produto Exemplo,2,19.90\n";
export const CSV_HEADER=header;
type Props={loading:boolean;result:ImportResult|null;error:string;onImport:(file:File)=>void;onExport:()=>void};
export function ImportsPage({loading,result,error,onImport,onExport}:Props){
 const downloadExample=()=>{const url=URL.createObjectURL(new Blob(["\uFEFF"+example],{type:"text/csv;charset=utf-8"}));const a=document.createElement("a");a.href=url;a.download="modelo-flow-commerce.csv";a.click();URL.revokeObjectURL(url);};
 return <div className="columns"><section className="panel"><span className="eyebrow">IMPORTAÇÃO</span><h2>Importar pedidos por CSV</h2><p className="muted">Arquivo UTF-8 de até 1 MB e 500 linhas. Pedidos com vários itens repetem o mesmo order_key.</p>
  <div className="uploadZone"><label htmlFor="csv-file"><strong>Selecione um arquivo CSV</strong><span className="muted">O servidor valida os registros antes de importar.</span></label><input id="csv-file" type="file" accept=".csv,text/csv" disabled={loading} onChange={e=>{const f=e.target.files?.[0];if(f)onImport(f);e.currentTarget.value=""}}/></div>
  {loading&&<p role="status">Processando arquivo…</p>}{error&&<div role="alert" className="notice">{error}</div>}
  {result&&<div className="subPanel" role="status"><h3>Importação concluída</h3><p>{result.imported} pedido(s) importado(s) pelo backend.</p>{result.orders?.length? <p className="muted">Chaves externas importadas: {result.orders.map(o=>o.externalKey).join(", ")}</p>:null}</div>}
  <h3>Colunas obrigatórias</h3><code className="codeLine">{header}</code><p className="muted">Use nomes em inglês, separados por vírgula. Em price, utilize ponto decimal (19.90), quantidade inteira maior que zero e chaves externas exclusivas por loja.</p><button className="secondary" onClick={downloadExample}>Baixar modelo CSV</button>
 </section><section className="panel"><span className="eyebrow">EXPORTAÇÃO</span><h2>Exportar pedidos</h2><p className="muted">Gere um arquivo CSV com os pedidos da loja autenticada. O conteúdo é produzido diretamente pela API, sem dados simulados.</p><button className="primary" onClick={onExport} disabled={loading}>Exportar CSV</button><hr/><h3>Boas práticas</h3><p className="muted">Revise os dados antes de importar. A API rejeita pedidos externos duplicados e executa a importação como transação.</p></section></div>;
}

