export type Session = { accessToken: string; storeId: string; role: string };
export type Profile = {
  userId: string;
  storeId: string;
  email: string;
  role: string;
};
export type Store = { id: string; name: string; created_at: string };
export type ItemInput = {
  sku: string;
  description: string;
  quantity: number;
  price: number;
};
export type Order = {
  id: string;
  customer_name: string;
  status: string;
  total: number;
  created_at: string;
  cancellation_reason?: string;
  items?: {
    sku: string;
    description: string;
    quantity: number;
    unit_price: number;
    subtotal: number;
  }[];
};
export type AuditEvent = {
  actor_id?: string;
  action: string;
  old_status: string | null;
  new_status: string | null;
  detail: string | null;
  occurred_at: string;
};
export type Dashboard = {
  order_count: number;
  active_count: number;
  delivered_count: number;
  canceled_count: number;
  order_value: number;
};
export type Forecast = {
  sku: string;
  weekStart: string;
  status: string;
  predictedUnits: number | null;
  method: string;
  message: string;
  unitsLast8FullWeeks: number;
  activeWeeksLast8: number;
  modelLimitations?: string;
};
export type ImportResult = {
  imported: number;
  orders: { externalKey: string; orderId: string }[];
};
