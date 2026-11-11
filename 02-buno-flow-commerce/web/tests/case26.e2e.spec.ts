import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";

// CASE26 smoke: runs against the ephemeral PostgreSQL in GitHub Actions.
// Never point this test at a live customer database.
const stamp = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);

test("CASE26 — roteiro demonstrável: painel, pedido, status, auditoria, CSV e exportação", async ({ page }) => {
  const batch = stamp();
  const customer = "Cliente CASE26 " + batch;

  await page.goto("/?case26=1");
  await expect(page.getByRole("region", { name: "Guia CASE26" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Bem-vindo de volta." })).toBeVisible();
  await expect(page.locator("img.brandMark").first()).toBeVisible();

  await page.getByRole("button", { name: "Criar conta e loja" }).click();
  await page.getByLabel("Nome da loja").fill("Buno Demo CASE26 " + batch);
  await page.getByLabel("Código de convite do piloto").fill(process.env.E2E_PILOT_CODE || "ci-private-test");
  await page.getByLabel("E-mail").fill("case26-" + batch + "@example.test");
  await page.getByLabel("Senha").fill("case26-safe-password-2026");
  await page.getByRole("button", { name: "Cadastrar e entrar" }).click();

  await expect(page.getByRole("heading", { name: "Seus pedidos. Sob controle." })).toBeVisible();
  await expect(page.getByRole("region", { name: "Primeiros passos" })).toBeVisible();
  const guide = page.getByRole("region", { name: "Guia CASE26" });
  await expect(guide).toBeVisible();
  await guide.getByRole("button", { name: "Verificar API e loja" }).click();
  await expect(guide.getByRole("status")).toContainText("Conta e indicadores acessíveis");
  const fixtureDownload = page.waitForEvent("download");
  await guide.getByRole("button", { name: "CSV dia 26" }).click();
  const demoFixture = await fixtureDownload;
  expect(demoFixture.suggestedFilename()).toMatch(/^flow-commerce-case26-dia-26-[a-f0-9]{8}\.csv$/i);
  const demoCsv = await readFile(await demoFixture.path()!);
  expect(demoCsv.toString()).toContain("order_key,customer,sku,description,quantity,price");
  expect(demoCsv.toString()).toContain("Cliente Demonstração");

  await page.getByRole("button", { name: "+ Novo pedido" }).click();
  await page.getByLabel("Cliente").fill(customer);
  await page.getByPlaceholder("SKU").fill("CASE26-101");
  await page.getByPlaceholder("Descrição").fill("Camiseta demonstração");
  await page.getByLabel("Quantidade").fill("2");
  await page.getByLabel("Preço").fill("39.90");

  const createdResponse = page.waitForResponse(r => r.url().endsWith("/api/v1/orders") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Criar pedido" }).click();
  expect((await createdResponse).status()).toBe(201);
  await expect(page.getByRole("row").filter({ hasText: customer })).toBeVisible();

  await page.getByRole("row").filter({ hasText: customer }).click();
  await page.getByRole("button", { name: "Avançar: Pago" }).click();
  await expect(page.getByText("Pedido atualizado")).toBeVisible();

  await page.locator("nav").getByRole("button", { name: /Histórico/ }).click();
  await expect(page.getByRole("heading", { name: "Auditoria por pedido" })).toBeVisible();
  await expect(page.locator(".event").first()).toBeVisible();

  await page.locator("nav").getByRole("button", { name: /Arquivos e CSV/ }).click();
  await expect(page.getByRole("heading", { name: "Importação CSV" })).toBeVisible();
  const importing = page.waitForResponse(r => r.url().endsWith("/api/v1/orders/import") && r.request().method() === "POST");
  await page.locator('.importArea input[type="file"]').setInputFiles({
    name: "case26-pedidos.csv",
    mimeType: "text/csv",
    buffer: demoCsv
  });
  expect((await importing).status()).toBe(200);
  await expect(page.getByRole("status")).toContainText("3 pedido(s) importado(s)");

  const downloading = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar pedidos em CSV" }).click();
  expect((await downloading).suggestedFilename()).toContain(".csv");

  await page.locator("nav").getByRole("button", { name: /Pedidos/ }).click();
  await expect(page.getByRole("row").filter({ hasText: "Cliente Exemplo" })).toBeVisible();
  await page.locator("nav").getByRole("button", { name: /Visão geral/ }).click();
  await expect(page.getByRole("heading", { name: "Pedidos recentes" })).toBeVisible();
  await expect(page.getByText("Cliente Exemplo").first()).toBeVisible();
});
