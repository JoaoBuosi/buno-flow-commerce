import { test, expect } from "@playwright/test";

const api = process.env.API_URL || "http://127.0.0.1:8080/api/v1";
const unique = () => Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 9);

test("lojistas isolados: cadastro, pedido, pagamento, CSV, auditoria, cancelamento, exportação e relogin", async ({ page, request }) => {
  const id = unique();
  const email = "pilot-" + id + "@example.test";
  const password = "pilot-safe-password-2026";
  const shop = "Loja QA " + id;
  const customer = "Cliente Teste " + id;

  await page.goto("/");
  await expect(page.locator("img.brandMark").first()).toBeVisible();
  const iconResponse = await request.get("/branding/favicon.ico");
  expect(iconResponse.ok()).toBeTruthy();
  expect(iconResponse.headers()["content-type"]).toContain("image");

  await page.getByRole("button", { name: "Criar conta e loja" }).click();
  await page.getByLabel("Nome da loja").fill(shop);
  await page.getByLabel("Código de convite do piloto").fill(process.env.E2E_PILOT_CODE || "ci-private-test");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(password);

  const registration = page.waitForResponse(r => r.url().endsWith("/api/v1/auth/register") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Cadastrar e entrar" }).click();
  const registered = await registration;
  expect(registered.ok()).toBeTruthy();
  const sessionA = await registered.json();
  expect(sessionA.accessToken).toBeTruthy();
  expect(sessionA.storeId).toBeTruthy();

  await expect(page.getByRole("heading", { name: "Seus pedidos. Sob controle." })).toBeVisible();
  await expect(page.getByRole("region", { name: "Primeiros passos" })).toBeVisible();
  await page.getByRole("button", { name: "Ver modelo CSV" }).click();
  await expect(page.getByRole("heading", { name: "Importação CSV" })).toBeVisible();
  const template = await request.get("/modelo-importacao.csv");
  expect(template.ok()).toBeTruthy();
  expect(await template.text()).toContain("order_key,customer,sku,description,quantity,price");
  await page.locator("nav").getByRole("button", { name: /Pedidos/ }).click();
  await expect(page.getByRole("heading", { name: "Gestão de pedidos." })).toBeVisible();

  await page.getByRole("button", { name: "+ Novo pedido" }).click();
  await page.getByLabel("Cliente").fill(customer);
  await page.getByPlaceholder("SKU").fill("SKU-STAGING");
  await page.getByPlaceholder("Descrição").fill("Produto de teste");
  await page.getByLabel("Quantidade").fill("2");
  await page.getByLabel("Preço").fill("19.90");

  const orderResponse = page.waitForResponse(r => r.url().endsWith("/api/v1/orders") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Criar pedido" }).click();
  const saved = await orderResponse;
  expect(saved.status()).toBe(201);
  const order = await saved.json();
  expect(Number(order.total)).toBe(39.8);
  const orderId = String(order.id);

  await expect(page.getByRole("row").filter({ hasText: customer })).toBeVisible();
  await page.getByRole("row").filter({ hasText: customer }).click();
  await expect(page.getByRole("heading", { name: customer })).toBeVisible();
  await page.getByRole("button", { name: "Avançar: Pago" }).click();
  await expect(page.getByText("Pedido atualizado")).toBeVisible();

  const history = await request.get(api + "/orders/" + orderId + "/history", {
    headers: { Authorization: "Bearer " + sessionA.accessToken, "X-Store-Id": sessionA.storeId }
  });
  expect(history.status()).toBe(200);
  const events = await history.json();
  expect(events.some((e: {new_status: string}) => e.new_status === "PAID")).toBeTruthy();

  await page.getByRole("button", { name: /Pedidos/ }).click();
  const csv = "order_key,customer,sku,description,quantity,price\n" +
    "EXT-" + id + ",Cliente importado,SKU-CSV,Item importado,3,10.50\n";
  const importResponse = page.waitForResponse(r => r.url().endsWith("/api/v1/orders/import") && r.request().method() === "POST");
  await page.locator('input[type="file"]').setInputFiles({
    name: "pedidos.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(csv, "utf-8")
  });
  const imported = await importResponse;
  expect(imported.status()).toBe(200);
  expect((await imported.json()).imported).toBe(1);
  await expect(page.getByRole("row").filter({ hasText: "Cliente importado" })).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar CSV" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain(".csv");

  await page.getByRole("row").filter({ hasText: customer }).click();
  page.once("dialog", dialog => dialog.accept("Cancelamento solicitado no teste"));
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(page.getByText("Cancelado", { exact: false }).first()).toBeVisible();
  const audit = await request.get(api + "/orders/" + orderId + "/history", {
    headers: { Authorization: "Bearer " + sessionA.accessToken, "X-Store-Id": sessionA.storeId }
  });
  expect((await audit.json()).some((e: {action: string}) => e.action === "CANCELED")).toBeTruthy();

  const denied = await request.post(api + "/auth/register", { data: {email:"uninvited-" + id + "@example.test",password,storeName:"Sem convite"}});
  expect(denied.status()).toBe(403);
  const b = await request.post(api + "/auth/register", {
    headers: {"X-Pilot-Code":process.env.E2E_PILOT_CODE || "ci-private-test"},
    data: {email: "other-" + id + "@example.test", password, storeName: "Loja B " + id}
  });
  expect(b.status()).toBe(200);
  const sessionB = await b.json();
  const headersB = { Authorization: "Bearer " + sessionB.accessToken, "X-Store-Id": sessionB.storeId };
  const attempt = await request.get(api + "/orders/" + orderId, {headers: headersB});
  expect(attempt.status()).toBe(404);
  const otherCount = await request.get(api + "/orders/count", {headers: headersB});
  expect((await otherCount.json()).total).toBe(0);
  const crossed = await request.get(api + "/orders/" + orderId, {
    headers: {Authorization: "Bearer " + sessionA.accessToken, "X-Store-Id": sessionB.storeId}
  });
  expect(crossed.status()).toBe(401);

  await page.getByRole("button", { name: "Sair" }).click();
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(password);
  await page.getByRole("button", { name: "Entrar", exact: true }).click();
  await expect(page.getByRole("row").filter({ hasText: "Cliente importado" })).toBeVisible();
});
