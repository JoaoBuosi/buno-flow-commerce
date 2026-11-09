import Papa from "papaparse";
export const csvColumns = [
  "order_key",
  "customer",
  "sku",
  "description",
  "quantity",
  "price",
];
export const csvTemplate =
  "order_key,customer,sku,description,quantity,price\nEXEMPLO-001,Cliente exemplo,SKU-001,Produto exemplo,2,19.90\n";
export function validateCsv(text: string) {
  const parsed = Papa.parse<string[]>(text, { skipEmptyLines: true });
  if (parsed.errors.length)
    throw new Error("CSV malformado. Confira aspas, separadores e linhas.");
  const [raw, ...rows] = parsed.data;
  const headers = raw?.map((s) => s.trim()) || [];
  if (
    csvColumns.some((c) => !headers.includes(c)) ||
    new Set(headers).size !== headers.length
  )
    throw new Error(
      "Cabeçalho inválido. Use as seis colunas do modelo, separadas por vírgula.",
    );
  if (!rows.length || rows.length > 500)
    throw new Error("O CSV deve conter entre 1 e 500 linhas de itens.");
  const orders = new Map<string, { customer: string; skus: Set<string> }>();
  rows.forEach((row, index) => {
    const line = index + 2;
    const get = (c: string) => (row[headers.indexOf(c)] || "").trim();
    if (row.length !== headers.length)
      throw new Error("Quantidade de colunas inválida na linha " + line + ".");
    for (const [c, max] of [
      ["order_key", 100],
      ["customer", 120],
      ["sku", 80],
      ["description", 200],
    ] as const)
      if (!get(c) || get(c).length > max)
        throw new Error("Campo " + c + " inválido na linha " + line + ".");
    if (
      !/^\+?\d+$/.test(get("quantity")) ||
      Number(get("quantity")) < 1 ||
      Number(get("quantity")) > 100000
    )
      throw new Error(
        "Quantidade inválida na linha " +
          line +
          ". Use um inteiro de 1 a 100000.",
      );
    // Plain decimal notation is the documented CSV format. The server remains authoritative.
    if (
      !/^\+?\d+(\.\d{1,2})?$/.test(get("price")) ||
      !Number.isFinite(Number(get("price"))) ||
      Number(get("price")) <= 0
    )
      throw new Error(
        "Preço inválido na linha " +
          line +
          ". Use ponto decimal e até duas casas.",
      );
    const key = get("order_key"),
      group = orders.get(key) || {
        customer: get("customer"),
        skus: new Set<string>(),
      };
    if (group.customer !== get("customer"))
      throw new Error("Cliente inconsistente no pedido " + key + ".");
    if (group.skus.has(get("sku")))
      throw new Error("SKU duplicado no pedido " + key + ".");
    group.skus.add(get("sku"));
    if (group.skus.size > 100)
      throw new Error("Máximo de 100 itens por pedido.");
    orders.set(key, group);
  });
  return { rows: rows.length, orders: orders.size };
}
