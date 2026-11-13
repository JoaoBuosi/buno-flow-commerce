import { test, expect, type Page } from "@playwright/test";

// Every registration below creates synthetic QA data in the disposable CI Postgres.
// Intentionally fail rather than run mutating tests against external environments.
const api = new URL(process.env.API_URL || "http://127.0.0.1:8080/api/v1");
if (!["127.0.0.1", "localhost", "::1"].includes(api.hostname) ||
    api.protocol !== "http:" || process.env.EXTERNAL_FRONTEND_URL) {
  throw new Error("Pilot browser tests only run against isolated loopback services");
}
const unique = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
const password = "qa-pilot-experience-password-2026";

async function createSyntheticMerchant(page: Page) {
  const id = unique();
  const email = "experience-" + id + "@example.test";
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Bem-vindo de volta." })).toBeVisible();
  await expect(page.getByRole("region", { name: "Preparação do piloto privado" })).toHaveCount(0);
  await page.getByRole("button", { name: "Criar conta e loja" }).click();
  await page.getByLabel("Nome da loja").fill("Loja fictícia " + id);
  await page.getByLabel("Código de convite do piloto").fill(process.env.E2E_PILOT_CODE || "ci-private-test");
  await page.getByLabel("E-mail", { exact: true }).fill(email);
  await page.getByLabel("Senha", { exact: true }).fill(password);
  const registeredResponse = page.waitForResponse(r =>
    r.url().endsWith("/api/v1/auth/register") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Cadastrar e entrar" }).click();
  const response = await registeredResponse;
  expect(response.status()).toBe(200);
  const session = await response.json();
  expect(session.accessToken).toBeTruthy();
  expect(session.storeId).toBeTruthy();
  await expect(page.getByRole("region", { name: "Preparação do piloto privado" })).toBeVisible();
  return session as { accessToken: string; storeId: string; role: string };
}

test("painel privado: consulta a própria loja sem criar pedidos ou liberar piloto", async ({ page }) => {
  const session = await createSyntheticMerchant(page);
  const pilot = page.getByRole("region", { name: "Preparação do piloto privado" });
  await expect(pilot.getByText("Não liberado comercialmente")).toBeVisible();
  await expect(pilot.getByText("Verificação de acesso ainda não realizada.")).toBeVisible();

  const observed: Array<{method: string; path: string; store: string | undefined; authorized: boolean}> = [];
  page.on("request", req => {
    if (!req.url().includes("/api/v1/")) return;
    observed.push({
      method: req.method(),
      path: new URL(req.url()).pathname,
      store: req.headers()["x-store-id"],
      authorized: req.headers()["authorization"] === "Bearer " + session.accessToken
    });
  });
  const self = page.waitForResponse(r => r.url().endsWith("/api/v1/store") && r.request().method() === "GET");
  const dashboard = page.waitForResponse(r => r.url().endsWith("/api/v1/dashboard") && r.request().method() === "GET");
  await pilot.getByRole("button", { name: "Verificar meu acesso" }).click();
  expect((await self).status()).toBe(200);
  expect((await dashboard).status()).toBe(200);
  await expect(pilot.getByText("Conexão da conta e dados da loja confirmados. Isso não equivale à autorização do piloto.")).toBeVisible();
  expect(observed.some(x => x.path.endsWith("/store") && x.method === "GET")).toBeTruthy();
  expect(observed.some(x => x.path.endsWith("/dashboard") && x.method === "GET")).toBeTruthy();
  expect(observed.every(x => x.method === "GET" && x.store === session.storeId && x.authorized)).toBeTruthy();
});

test("navegação mobile: piloto, pedidos e segurança utilizáveis a 390px", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await createSyntheticMerchant(page);
  const pilot = page.getByRole("region", { name: "Preparação do piloto privado" });
  await expect(pilot.getByRole("button", { name: "Ver pedidos" })).toBeVisible();
  await pilot.getByRole("button", { name: "Ver pedidos" }).click();
  await expect(page.getByRole("heading", { name: "Gestão de pedidos." })).toBeVisible();
  await page.locator("nav").getByRole("button", { name: /Visão geral/ }).click();
  await expect(pilot).toBeVisible();
  await pilot.getByRole("button", { name: "Segurança da conta" }).click();
  await expect(page.getByRole("heading", { name: "Configurações da loja." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Segurança da conta" })).toBeVisible();
  // Do not mistake an internally scrollable nav/table for whole-page horizontal overflow.
  const viewport = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth
  }));
  expect(viewport.document).toBeLessThanOrEqual(viewport.viewport + 2);
});

test("API indisponível: painel falha fechado e impede falsa confirmação", async ({ page }) => {
  await createSyntheticMerchant(page);
  await page.route("**/api/v1/store", async route => {
    if (route.request().method() === "GET") {
      await route.fulfill({ status: 503, contentType: "application/json", body: '{"detail":"Serviço indisponível"}' });
    } else {
      await route.continue();
    }
  });
  const pilot = page.getByRole("region", { name: "Preparação do piloto privado" });
  await pilot.getByRole("button", { name: "Verificar meu acesso" }).click();
  await expect(pilot.getByText("Verificação de acesso falhou. Não prossiga com a demonstração.")).toBeVisible();
  await expect(pilot.getByText("Conexão da conta e dados da loja confirmados.", { exact: false })).toHaveCount(0);
});

test("loja divergente: retorno de outra loja nunca confirma a demonstração", async ({ page, request }) => {
  const ownerA = await createSyntheticMerchant(page);
  const identity = unique();
  const b = await request.post(api.toString() + "/auth/register", {
    headers: { "X-Pilot-Code": process.env.E2E_PILOT_CODE || "ci-private-test" },
    data: { email: "other-" + identity + "@example.test", password, storeName: "Loja B fictícia " + identity }
  });
  expect(b.status()).toBe(200);
  const ownerB = await b.json();
  expect(ownerA.storeId).not.toEqual(ownerB.storeId);
  // Backend must reject a bearer from A combined with B's store id.
  const crossed = await request.get(api.toString() + "/store", {
    headers: { Authorization: "Bearer " + ownerA.accessToken, "X-Store-Id": ownerB.storeId }
  });
  expect([401, 403, 404]).toContain(crossed.status());

  // Even if a broken gateway returned B's profile, the pilot UI refuses to mark QA as passed.
  await page.route("**/api/v1/store", route => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ id: ownerB.storeId, name: "Loja B fictícia", created_at: new Date().toISOString() })
  }));
  const pilot = page.getByRole("region", { name: "Preparação do piloto privado" });
  await pilot.getByRole("button", { name: "Verificar meu acesso" }).click();
  await expect(pilot.getByText("Verificação de acesso falhou. Não prossiga com a demonstração.")).toBeVisible();
});
