import { test, expect, type Page } from "@playwright/test";

// Always use the local, disposable CI API. No preview/prod and no real merchants.
const api = new URL(process.env.API_URL || "http://127.0.0.1:8080/api/v1");
const frontend = new URL(process.env.FRONTEND_URL || "http://127.0.0.1:5173");
const viteApi = new URL(process.env.VITE_API_URL || api.toString());
if ([api, frontend, viteApi].some(u =>
      u.protocol !== "http:" || !["127.0.0.1", "localhost", "::1"].includes(u.hostname)) ||
    process.env.EXTERNAL_FRONTEND_URL) {
  throw new Error("A11y pilot tests are restricted to loopback disposable QA");
}
const password = "synthetic-a11y-test-2026";
async function merchant(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Criar conta e loja" }).click();
  const id = Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
  await page.getByLabel("Nome da loja").fill("Loja A11y " + id);
  await page.getByLabel("Código de convite do piloto").fill(process.env.E2E_PILOT_CODE || "ci-private-test");
  await page.getByLabel("E-mail", { exact: true }).fill("a11y-" + id + "@example.test");
  await page.getByLabel("Senha", { exact: true }).fill(password);
  const registered = page.waitForResponse(r =>
    r.url().endsWith("/api/v1/auth/register") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Cadastrar e entrar" }).click();
  expect((await registered).status()).toBe(200);
  await expect(page.getByRole("heading", { name: "Seus pedidos. Sob controle." })).toBeVisible();
}

test("Novo pedido: diálogo acessível, Tab confinado, Esc e retorno de foco", async ({page}) => {
  await merchant(page);
  const opener = page.getByRole("button", { name: "+ Novo pedido" });
  await opener.focus();
  await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Novo pedido" });
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute("aria-modal", "true");
  await expect(dialog.getByRole("textbox", { name: "Cliente" })).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(dialog.getByRole("button", { name: "Fechar novo pedido" })).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(dialog.getByRole("button", { name: "Criar pedido" })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(dialog.getByRole("button", { name: "Fechar novo pedido" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(opener).toBeFocused();
  await expect(page.locator("nav button[aria-current='page']")).toHaveCount(1);
  await expect(page.locator("nav button[aria-current='page']")).toContainText("Pedidos");
});

test("mobile: menu aponta seção ativa e controles têm toque mínimo de 44px", async ({page}) => {
  await page.setViewportSize({width:390,height:844});
  await merchant(page);
  const nav = page.locator("nav");
  const active = nav.locator("button[aria-current='page']");
  await expect(active).toContainText("Visão geral");
  await expect(page.getByText("Deslize o menu para ver mais seções")).toBeVisible();
  await page.getByRole("region", {name:"Preparação do piloto privado"})
    .getByRole("button", {name:"Ver pedidos"}).click();
  await expect(active).toContainText("Pedidos");
  const height = await page.getByRole("button", {name:"+ Novo pedido"}).evaluate(
    element => element.getBoundingClientRect().height
  );
  expect(height).toBeGreaterThanOrEqual(44);
  await expect(page.getByRole("heading", {name:"Gestão de pedidos."})).toBeVisible();
});
