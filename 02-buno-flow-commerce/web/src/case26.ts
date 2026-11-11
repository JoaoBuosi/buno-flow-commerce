/** Synthetic, on-device CASE26 CSV generator. No API calls, no real customer data. */
const FIELDS = ["order_key", "customer", "sku", "description", "quantity", "price"] as const;
const ITEMS: ReadonlyArray<readonly [string, string, string, number, string]> = [
  ["Cliente Exemplo", "CASE26-101", "Camiseta demonstrativa", 2, "39.90"],
  ["Cliente Demonstração", "CASE26-102", "Caneca demonstrativa", 1, "25.00"],
  ["Cliente Teste", "CASE26-103", "Bolsa demonstrativa", 1, "79.90"]
];

export function createCase26Csv(day: 26 | 27, batch?: string): { name: string; csv: string } {
  if (day !== 26 && day !== 27) throw new Error("Dia do CASE26 inválido");
  const code = batch ?? crypto.randomUUID().replace(/-/g, "").slice(0, 8).toUpperCase();
  if (!/^[A-Za-z0-9]{4,20}$/.test(code)) throw new Error("Identificador do ensaio inválido");
  const rows = ITEMS.map(([customer, sku, description, quantity, price], i) =>
    [`CASE26-${day}-${code}-${String(i + 1).padStart(3, "0")}`, customer, sku, description, quantity, price]
  );
  // Values are fixed strings with no commas or newlines. This file is only a
  // demonstration fixture, never a general-purpose CSV serializer.
  return {
    name: `flow-commerce-case26-dia-${day}-${code}.csv`,
    csv: [FIELDS.join(","), ...rows.map(row => row.join(",")), ""].join("\n")
  };
}

export function downloadCase26Csv(day: 26 | 27): string {
  const { name, csv } = createCase26Csv(day);
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Some browsers must finish starting the download before revoking its URL.
  window.setTimeout(() => URL.revokeObjectURL(url), 3000);
  return name;
}
