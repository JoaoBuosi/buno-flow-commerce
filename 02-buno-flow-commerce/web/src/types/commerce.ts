export type Session={accessToken:string;storeId:string;role:string};
export type NewItem={sku:string;description:string;quantity:number;price:number};
export type OrderItem={sku:string;description:string;quantity:number;unit_price?:number;subtotal?:number};
export type Order={id:string;customer_name:string;status:string;total:number;items?:OrderItem[];created_at:string;cancellation_reason?:string|null};
export type OrderEvent={actor_id?:string;action:string;old_status:string|null;new_status:string|null;detail:string|null;occurred_at:string};
export type Dashboard={order_count:number;active_count:number;delivered_count:number;canceled_count:number;order_value:number};
export type StoreProfile={id:string;name:string;created_at:string};
export type CurrentUser={userId?:string;storeId?:string;role?:string};
export type Forecast={sku:string;weekStart:string;status:string;predictedUnits:number|null;method:string;message:string;unitsLast8FullWeeks:number;activeWeeksLast8:number;modelLimitations?:string};
export type ImportResult={imported:number;orders?:Array<{externalKey:string;orderId:string}>};
export const money=(v:number)=>Number(v).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
export const labels:Record<string,string>={CREATED:"Criado",PAID:"Pago",PROCESSING:"Em separação",SHIPPED:"Enviado",DELIVERED:"Entregue",CANCELED:"Cancelado"};
export const next:Record<string,string>={CREATED:"PAID",PAID:"PROCESSING",PROCESSING:"SHIPPED",SHIPPED:"DELIVERED"};
export const dateTime=(value:string)=>new Date(value).toLocaleString("pt-BR",{dateStyle:"short",timeStyle:"short"});

