import type {Forecast} from "../types/commerce";
type Props={sku:string;forecast:Forecast|null;loading:boolean;onSku:(v:string)=>void;onSearch:()=>void};
export function IntelligencePage({sku,forecast,loading,onSku,onSearch}:Props){
 const valid=forecast&&forecast.status==="OK"&&Number.isFinite(forecast.predictedUnits);
 return <section className="panel"><span className="eyebrow">INSIGHTS POR SKU · PILOTO</span><h2>Demanda semanal por produto</h2><p className="muted">Estimativa experimental baseada em pedidos pagos da loja. A API exige pelo menos 6 semanas com vendas e 20 unidades nas últimas 8 semanas completas.</p>
 <form className="inline controls" onSubmit={e=>{e.preventDefault();onSearch()}}><label className="fieldGrow">Código SKU<input placeholder="SKU do produto" value={sku} onChange={e=>onSku(e.target.value)}/></label><button className="primary" disabled={loading||!sku.trim()}>{loading?"Consultando…":"Consultar demanda"}</button></form>
 {forecast&&<div className="subPanel"><h3>{forecast.sku}</h3><p>Unidades nas últimas 8 semanas: {forecast.unitsLast8FullWeeks}</p><p>Semanas ativas: {forecast.activeWeeksLast8}</p><p>Método: {forecast.method}</p><p>{forecast.message}</p>{valid&&<h2>Estimativa: {forecast.predictedUnits} unidades</h2>}{forecast.modelLimitations&&<p className="muted">{forecast.modelLimitations}</p>}{!valid&&<p className="muted">Não há previsão válida disponível para este SKU.</p>}</div>}
 <hr/><p className="muted">Não é uma previsão comercial validada. O modelo experimental avançado não faz parte desta versão.</p></section>;
}

