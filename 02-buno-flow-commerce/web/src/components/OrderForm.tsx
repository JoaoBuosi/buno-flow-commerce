import { useState, type FormEvent } from "react";
import type { Api } from "../services/api";
import type { ItemInput, Order } from "../types";
import { Modal, money } from "./ui";
const empty = (): ItemInput => ({
  sku: "",
  description: "",
  quantity: 1,
  price: 1,
});
export function OrderForm({
  api,
  close,
  saved,
}: {
  api: Api;
  close: () => void;
  saved: (o: Order) => void;
}) {
  const [customer, setCustomer] = useState("");
  const [items, setItems] = useState([empty()]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  function edit(index: number, value: Partial<ItemInput>) {
    setItems((all) =>
      all.map((v, i) => (i === index ? { ...v, ...value } : v)),
    );
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (
        !customer.trim() ||
        items.some((i) => !i.sku.trim() || !i.description.trim())
      )
        throw new Error(
          "Preencha cliente, SKU e descrição sem deixar campos em branco.",
        );
      const order = await api.send<Order>("/orders", "POST", {
        customer: customer.trim(),
        items,
      });
      saved(order);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title="Novo pedido" onClose={close} busy={busy}>
      <p className="muted">Preencha os dados do cliente e os itens da venda.</p>
      <form className="stack" onSubmit={submit}>
        <label>
          Cliente
          <input
            autoFocus
            required
            maxLength={120}
            value={customer}
            onChange={(e) => setCustomer(e.target.value)}
          />
        </label>
        <fieldset disabled={busy}>
          <legend>Itens do pedido</legend>
          {items.map((item, index) => (
            <div className="itemEditor" key={index}>
              <div className="panelHead">
                <h3>Item {index + 1}</h3>
                <button
                  className="link"
                  type="button"
                  disabled={items.length === 1}
                  aria-label={"Remover item " + (index + 1)}
                  onClick={() =>
                    setItems((all) => all.filter((_, i) => i !== index))
                  }
                >
                  Remover
                </button>
              </div>
              <div className="itemGrid">
                <label>
                  SKU
                  <input
                    placeholder="SKU"
                    required
                    maxLength={80}
                    value={item.sku}
                    onChange={(e) => edit(index, { sku: e.target.value })}
                  />
                </label>
                <label>
                  Descrição
                  <input
                    placeholder="Descrição"
                    required
                    maxLength={200}
                    value={item.description}
                    onChange={(e) =>
                      edit(index, { description: e.target.value })
                    }
                  />
                </label>
                <label>
                  Quantidade
                  <input
                    type="number"
                    required
                    min="1"
                    max="100000"
                    step="1"
                    value={item.quantity}
                    onChange={(e) =>
                      edit(index, { quantity: Number(e.target.value) })
                    }
                  />
                </label>
                <label>
                  Preço
                  <input
                    type="number"
                    required
                    min="0.01"
                    step="0.01"
                    value={item.price}
                    onChange={(e) =>
                      edit(index, { price: Number(e.target.value) })
                    }
                  />
                </label>
              </div>
            </div>
          ))}
          <button
            className="secondary"
            type="button"
            disabled={items.length >= 100}
            onClick={() => setItems((all) => [...all, empty()])}
          >
            + Adicionar item
          </button>
        </fieldset>
        <div className="total">
          <span>Total do pedido</span>
          <strong>
            {money(items.reduce((sum, i) => sum + i.quantity * i.price, 0))}
          </strong>
        </div>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <div className="dialogActions">
          <button
            className="secondary"
            type="button"
            disabled={busy}
            onClick={close}
          >
            Voltar
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Salvando…" : "Criar pedido"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
