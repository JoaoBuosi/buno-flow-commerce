import type { Order } from "../types";
import { Badge, date, money } from "./ui";
export function OrderTable({
  orders,
  open,
}: {
  orders: Order[];
  open: (id: string) => void;
}) {
  return (
    <div
      className="tableWrap"
      role="region"
      aria-label="Lista de pedidos"
      tabIndex={0}
    >
      <table>
        <thead>
          <tr>
            <th>Pedido / Cliente</th>
            <th>Registrado em</th>
            <th>Status</th>
            <th className="numeric">Valor total</th>
            <th>
              <span className="srOnly">Ações</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {orders.map((o) => (
            <tr key={o.id}>
              <td>
                <strong>{o.customer_name}</strong>
                <small>#{o.id.slice(0, 8)}</small>
              </td>
              <td>{date(o.created_at)}</td>
              <td>
                <Badge status={o.status} />
              </td>
              <td className="numeric">{money(o.total)}</td>
              <td>
                <button
                  className="tableAction"
                  aria-label={"Abrir pedido de " + o.customer_name}
                  onClick={() => open(o.id)}
                >
                  Ver pedido <span aria-hidden="true">↗</span>
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
