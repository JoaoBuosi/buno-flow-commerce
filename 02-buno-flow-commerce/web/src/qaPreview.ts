/**
 * QA-only, memory-only simulation of the 0.5.11 merchant API.
 * This file lives exclusively on qa/flow-commerce-0511-mobile-synthetic-preview.
 * NO HTTP backend, PostgreSQL, Supabase, Railway, authentication, persistence,
 * e-mails or charges. Data is reset on reload. Not for customer use.
 */
export const qaEnabled = import.meta.env.VITE_QA_PREVIEW === "synthetic";
export const qaSession = Object.freeze({
  accessToken: "synthetic-ui-session-not-valid-anywhere",
  storeId: "qa-demo-store-0511",
  role: "OWNER"
});
type Item = {sku:string;description:string;quantity:number;price:number};
type Order = {id:string;customer_name:string;status:string;total:number;items:Item[];created_at:string};
type Audit = {action:string;old_status:string|null;new_status:string|null;detail:string|null;occurred_at:string};
const now = () => new Date().toISOString();
let storeName = "Loja fictícia Buno QA";
let sequence = 2;
let orders:Order[] = [{
  id:"qa-order-0001", customer_name:"Cliente Fictício",status:"CREATED",
  total:79.80, created_at:"2026-11-12T15:00:00.000Z",
  items:[{sku:"QA-100",description:"Caderno de demonstração",quantity:2,price:39.90}]
}];
let events:Record<string,Audit[]> = {
  "qa-order-0001":[{action:"CREATED",old_status:null,new_status:"CREATED",detail:"Pedido fictício gerado no navegador",occurred_at:now()}]
};
const asJson=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}});
const blocked=(detail:string,status=403)=>asJson({detail},status);
const safeCsv=(value:string)=>{const clean=value.replace(/\r|\n/g," ").replace(/^([=+@-])/,"'$1");return '"'+clean.replace(/"/g,'""')+'"'};
const filtered=(params:URLSearchParams)=>{
 const search=(params.get("search")||"").toLocaleLowerCase();
 const status=params.get("status")||"";
 return orders.filter(o=>(!status||o.status===status)&&(!search||o.customer_name.toLocaleLowerCase().includes(search)||o.id.toLowerCase().includes(search)));
};
const dashboard=()=>({
 order_count:orders.length,
 active_count:orders.filter(o=>!["DELIVERED","CANCELED"].includes(o.status)).length,
 delivered_count:orders.filter(o=>o.status==="DELIVERED").length,
 canceled_count:orders.filter(o=>o.status==="CANCELED").length,
 order_value:Number(orders.filter(o=>o.status!=="CANCELED").reduce((s,o)=>s+o.total,0).toFixed(2))
});
const record=(order:Order,action:string,old:string|null,detail:string|null=null)=>{
 (events[order.id]??=[]).push({action,old_status:old,new_status:order.status,detail,occurred_at:now()});
};
async function handle(path:URL,init?:RequestInit):Promise<Response>{
 const method=(init?.method||"GET").toUpperCase();
 // UI demo only. Session token is a static marker, not a security boundary.
 if(method!=="GET"&&!["POST","PATCH"].includes(method))return blocked("Operação indisponível no QA.");
 if(path.pathname==="/api/v1/auth/reset/request"&&method==="POST")return asJson({},202);
 if(path.pathname==="/api/v1/auth/logout"&&method==="POST")return new Response(null,{status:204});
 if(path.pathname==="/api/v1/auth/mfa/status"&&method==="GET")
  return asJson({enrolled:false,required:false,enrollmentAvailable:false});
 if(path.pathname.startsWith("/api/v1/auth/"))
  return blocked("Este preview não autentica contas reais. Use 'Entrar na loja fictícia'.");
 const headers=new Headers(init?.headers||{});
 if(headers.get("Authorization")!=="Bearer "+qaSession.accessToken ||
    headers.get("X-Store-Id")!==qaSession.storeId){
  return blocked("Sessão de demonstração inválida",401);
 }
 const json=async()=>{try{return JSON.parse(String(init?.body||"{}")) as Record<string,any>}catch{return {}}};
 if(path.pathname==="/api/v1/store"){
  if(method==="GET")return asJson({id:qaSession.storeId,name:storeName,created_at:now()});
  if(method==="PATCH"){const body=await json();if(typeof body.name!=="string"||!body.name.trim())return blocked("Nome inválido",400);storeName=body.name.trim().slice(0,120);return asJson({id:qaSession.storeId,name:storeName,created_at:now()});}
 }
 if(path.pathname==="/api/v1/dashboard"&&method==="GET")return asJson(dashboard());
 if(path.pathname==="/api/v1/orders/count"&&method==="GET")return asJson({total:filtered(path.searchParams).length});
 if(path.pathname==="/api/v1/orders/export"&&method==="GET"){
  const lines=["order_key,customer,sku,description,quantity,price"];
  for(const o of orders)for(const i of o.items)
   lines.push([o.id,o.customer_name,i.sku,i.description,String(i.quantity),i.price.toFixed(2)].map(safeCsv).join(","));
  return new Response(lines.join("\n")+"\n",{status:200,headers:{"Content-Type":"text/csv; charset=utf-8"}});
 }
 if(path.pathname==="/api/v1/orders/import"&&method==="POST"){
  if(!(init?.body instanceof FormData))return blocked("Envie CSV fictício.",400);
  const file=init.body.get("file");
  if(!(file instanceof File)||file.size>1_000_000||file.size===0)return blocked("CSV inválido ou maior que 1 MB.",400);
  const raw=await file.text();
  const lines=raw.trim().split(/\r?\n/);
  if(lines.length>501||lines[0]?.replace(/\s/g,"")!=="order_key,customer,sku,description,quantity,price")return blocked("Cabeçalho CSV inválido ou mais de 500 linhas.",400);
  const fresh:Order[]=[];
  for(const line of lines.slice(1)){
   if(!line.trim())continue;
   const parts=line.split(",").map(x=>x.trim().replace(/^"|"$/g,""));
   const [key,customer,sku,description,quantity,price]=parts;
   if(parts.length!==6||!key||!customer||!sku||!description||!Number.isFinite(Number(quantity))||Number(quantity)<=0||!Number.isFinite(Number(price))||Number(price)<=0)return blocked("Linha CSV inválida. Utilize o modelo fictício.",400);
   const id="qa-import-"+(++sequence);
   const item={sku,description,quantity:Number(quantity),price:Number(price)};
   fresh.push({id,customer_name:customer,status:"CREATED",total:Number((item.quantity*item.price).toFixed(2)),items:[item],created_at:now()});
  }
  for(const o of fresh){orders.unshift(o);events[o.id]=[];record(o,"CREATED",null,"Importação fictícia");}
  return asJson({imported:fresh.length});
 }
 if(path.pathname==="/api/v1/orders"){
  if(method==="GET"){
   const list=filtered(path.searchParams);
   const page=Math.max(0,Number(path.searchParams.get("page")||"0"));
   const size=Math.min(100,Math.max(1,Number(path.searchParams.get("size")||"25")));
   return asJson(list.slice(page*size,page*size+size));
  }
  if(method==="POST"){
   const data=await json();
   const items=Array.isArray(data.items)?data.items as Item[]:[];
   if(typeof data.customer!=="string"||!data.customer.trim()||!items.length||
     items.some(i=>!i.sku||!i.description||!(i.quantity>0)||!(i.price>0)))return blocked("Pedido fictício inválido.",400);
   const order:Order={id:"qa-order-"+String(++sequence).padStart(4,"0"),customer_name:data.customer.trim().slice(0,120),
     status:"CREATED",items:items.map(x=>({...x})),created_at:now(),total:Number(items.reduce((a,i)=>a+i.quantity*i.price,0).toFixed(2))};
   orders.unshift(order);events[order.id]=[];record(order,"CREATED",null,"Pedido fictício");return asJson(order,201);
  }
 }
 const match=path.pathname.match(/^\/api\/v1\/orders\/([^/]+)(?:\/(status|cancel|history))?$/);
 if(match){
  const order=orders.find(x=>x.id===decodeURIComponent(match[1]));
  if(!order)return blocked("Pedido não encontrado",404);
  const action=match[2];
  if(!action&&method==="GET")return asJson(order);
  if(action==="history"&&method==="GET")return asJson(events[order.id]||[]);
  if(action==="status"&&method==="PATCH"){
   const data=await json();const transitions:Record<string,string>={CREATED:"PAID",PAID:"PROCESSING",PROCESSING:"SHIPPED",SHIPPED:"DELIVERED"};
   if(data.status!==transitions[order.status])return blocked("Mudança de status inválida",409);
   const old=order.status;order.status=data.status;record(order,"STATUS_CHANGED",old);return asJson(order);
  }
  if(action==="cancel"&&method==="POST"){
   if(["DELIVERED","CANCELED"].includes(order.status))return blocked("Não é possível cancelar",409);
   const data=await json();
   if(!String(data.reason||"").trim())return blocked("Motivo obrigatório",400);
   const old=order.status;order.status="CANCELED";record(order,"CANCELED",old,String(data.reason).slice(0,100));return asJson(order);
  }
 }
 if(path.pathname==="/api/v1/insights/demand"&&method==="GET"){
  const sku=path.searchParams.get("sku")||"";
  return asJson({sku,weekStart:now().slice(0,10),status:"INSUFFICIENT_DATA",predictedUnits:null,method:"MOCK",message:"Simulação de interface sem cálculo preditivo real.",unitsLast8FullWeeks:0,activeWeeksLast8:0});
 }
 return blocked("Rota não simulada. Nenhum backend real foi chamado.",404);
}
export function installQaPreview(){
 if(!qaEnabled)return;
 const apiPrefix="/api/v1";
 // Deliberately NEVER use native fetch in synthetic preview. This prevents
 // accidentally forwarding credentials or data to a real backend.
 window.fetch=async(input:RequestInfo|URL,init?:RequestInit)=>{
  const u=new URL(input instanceof Request?input.url:String(input),window.location.origin);
  if(u.origin!==window.location.origin||!u.pathname.startsWith(apiPrefix))
   throw new Error("Requisição externa bloqueada no preview sintético.");
  return handle(u,init);
 };
}
