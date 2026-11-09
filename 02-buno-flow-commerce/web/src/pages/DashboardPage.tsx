import type {Dashboard,Order} from "../types/commerce";
import {dateTime,labels,money} from "../types/commerce";
type Props={dashboard:Dashboard|null;orders:Order[];loading:boolean;error:string;onNew:()=>void;onOrders:()=>void;onImports:()=>void;onSelect:(id:string)=>void;onRefresh:()=>void};
export function DashboardPage({dashboard,orders,loading,error,onNew,onOrders,onImports,onSelect,onRefresh}:Props){
 return <>
  <section aria-label="Indicadores da loja" className="stats statsFive">
   <article><span>Total de pedidos</span><strong>{dashboard?.order_count??"—"}</strong></article>
   <article><span>Em andamento</span><strong>{dashboard?.active_count??"—"}</strong></article>
   <article><span>Entregues</span><strong>{dashboard?.delivered_count??"—"}</strong></article>
   <article><span>Cancelados</span><strong>{dashboard?.canceled_count??"—"}</strong></article>
   <article><span>Valor não cancelado</span><strong>{dashboard?money(dashboard.order_value):"—"}</strong></article>
  </section>
  <section className="panel quickActions"><div><h2>Ações rápidas</h2><p className="muted">Trabalhe com pedidos e arquivos da sua loja.</p></div><div className="inline">
    <button className="primary" onClick={onNew}>+ Novo pedido</button>
    <button className="secondary" onClick={onImports}>Importar CSV</button>
    <button className="secondary" onClick={onOrders}>Consultar pedidos</button>
  </div></section>
  <section className="panel"><div className="panelHead"><div><h2>Últimos pedidos registrados</h2><p className="muted">Até 5 pedidos mais recentes retornados pela API.</p></div><button className="secondary" onClick={onRefresh} disabled={loading}>Atualizar</button></div>
  {loading&&!dashboard?<p role="status" className="empty">Carregando indicadores…</p>:error?<div role="alert" className="notice">{error} <button className="link" onClick={onRefresh}>Tentar novamente</button></div>:orders.length===0?<div className="empty">Nenhum pedido registrado. <button className="link" onClick={onNew}>Criar primeiro pedido</button></div>:<div className="tableWrap"><table><thead><tr><th>Pedido</th><th>Cliente</th><th>Data</th><th>Valor</th><th>Status</th><th>Ação</th></tr></thead><tbody>{orders.slice(0,5).map(o=><tr key={o.id}><td>{o.id.slice(0,8)}</td><td>{o.customer_name}</td><td>{dateTime(o.created_at)}</td><td>{money(o.total)}</td><td><span className={"status status-"+o.status.toLowerCase()}>{labels[o.status]||o.status}</span></td><td><button className="link" onClick={()=>onSelect(o.id)} aria-label={"Abrir pedido de "+o.customer_name}>Ver pedido</button></td></tr>)}</tbody></table></div>}
  </section>
 </>;
}

