import { test, expect, type Page, type TestInfo } from "@playwright/test";

// Visual-review evidence must never run against production or a remote preview.
// The account is newly created in disposable CI PostgreSQL and not reused.
const api = new URL(process.env.API_URL || "http://127.0.0.1:8080/api/v1");
const frontend = new URL(process.env.FRONTEND_URL || "http://127.0.0.1:5173");
const viteApi = new URL(process.env.VITE_API_URL || api.toString());
if ([api, frontend, viteApi].some(url =>
      url.protocol !== "http:" ||
      !["127.0.0.1", "localhost", "::1"].includes(url.hostname)) ||
    process.env.EXTERNAL_FRONTEND_URL) {
  throw new Error("Synthetic screenshots require local disposable API and frontend only");
}
const password = "qa-visual-review-only-2026";

async function openDemoMerchant(page: Page) {
  const id = Math.random().toString(36).slice(2, 12);
  await page.goto("/");
  await page.getByRole("button", { name: "Criar conta e loja" }).click();
  await page.getByLabel("Nome da loja").fill("Loja fictícia de revisão visual");
  await page.getByLabel("Código de convite do piloto")
    .fill(process.env.E2E_PILOT_CODE || "ci-private-test");
  await page.getByLabel("E-mail", { exact: true })
    .fill("visual-" + id + "@example.test");
  await page.getByLabel("Senha", { exact: true }).fill(password);

  const registration = page.waitForResponse(response =>
    response.url().endsWith("/api/v1/auth/register") &&
    response.request().method() === "POST");
  await page.getByRole("button", { name: "Cadastrar e entrar" }).click();
  expect((await registration).status()).toBe(200);
  await expect(page.getByRole("region", { name: "Preparação do piloto privado" }))
    .toBeVisible();
  await expect(page.getByText("Não liberado comercialmente")).toBeVisible();
}

async function capture(page: Page, testInfo: TestInfo, name: string) {
  const width = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth
  }));
  expect(width.document, "No whole-page horizontal overflow").toBeLessThanOrEqual(width.viewport + 2);

  // Mask even synthetic store identifiers; do not attach traces, cookies, tokens
  // or raw request/response bodies.
  await page.screenshot({
    path: testInfo.outputPath("pilot-" + name + ".png"),
    fullPage: true,
    animations: "disabled",
    mask: [page.locator(".asideBottom b")]
  });
}

for (const view of [
  { name: "desktop", width: 1440, height: 900 },
  { name: "mobile", width: 390, height: 844 }
] as const) {
  test("evidência visual sintética do piloto: " + view.name, async ({ page }, info) => {
    await page.setViewportSize({ width: view.width, height: view.height });
    await openDemoMerchant(page);
    const pilot = page.getByRole("region", { name: "Preparação do piloto privado" });

    await capture(page, info, view.name + "-overview");
    await pilot.getByRole("button", { name: "Ver pedidos" }).click();
    await expect(page.getByRole("heading", { name: "Gestão de pedidos." })).toBeVisible();
    await capture(page, info, view.name + "-orders");
    await page.locator("nav").getByRole("button", { name: /Visão geral/ }).click();
    await pilot.getByRole("button", { name: "Segurança da conta" }).click();
    await expect(page.getByRole("heading", { name: "Segurança da conta" })).toBeVisible();
    await capture(page, info, view.name + "-settings");
  });
}
