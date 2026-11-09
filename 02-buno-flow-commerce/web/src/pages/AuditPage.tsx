import type {Order,OrderEvent} from "../types/commerce";
import {dateTime,labels} from "../types/commerce";
type Props={order:Order|null;events:OrderEvent[];loading:boolean;onOrders:()=>void};
export function AuditPage({order,events,loading,onOrders}:Props){
 return <section className="panel"><div className="panelHead"><div><h2>Trilha de auditoria por pedido</h2><p className="muted">A API atual não oferece uma consulta global. Selecione um pedido para ver seu histórico.</p></div><button className="secondary" onClick={onOrders}>Selecionar pedido</button></div>
 {!order?<div className="empty">Nenhum pedido selecionado. Acesse Pedidos para consultar uma trilha real.</div>:<><div className="subPanel"><strong>{order.customer_name}</strong><p className="muted">Pedido {order.id} · {labels[order.status]||order.status}</p></div>
 {loading?<p role="status">Carregando eventos…</p>:events.length===0?<p className="empty">Nenhum evento retornado para este pedido.</p>:<div className="timeline">{events.map((e,i)=><article className="timelineItem" key={e.occurred_at+"-"+i}><strong>{e.action==="CREATED"?"Pedido criado":e.action==="CANCELED"?"Cancelamento":e.action==="STATUS_CHANGED"?"Status alterado":e.action}</strong><p className="muted">{dateTime(e.occurred_at)}{e.actor_id?" · Responsável (ID): "+e.actor_id:""}</p><p>{e.old_status?labels[e.old_status]||e.old_status:"—"} → {e.new_status?labels[e.new_status]||e.new_status:"—"}</p>{e.detail&&<p className="muted">{e.detail}</p>}</article>)}</div>}</>}</section>;
}

