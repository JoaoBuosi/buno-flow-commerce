import { test, expect } from "@playwright/test";

// Run only against the compiled app served with its production CSP in the
// disposable GitHub Actions environment. Never targets staging or production.
test("strict production CSP preserves login/registration UI without inline styles", async ({page}) => {
  const violations: string[] = [];
  const pageErrors: string[] = [];
  await page.addInitScript(() => {
    (window as Window & { __cspViolations?: string[] }).__cspViolations = [];
    document.addEventListener("securitypolicyviolation", (event) => {
      (window as Window & { __cspViolations: string[] }).__cspViolations.push(
        event.violatedDirective + ":" + event.blockedURI
      );
    });
  });
  page.on("pageerror", error => pageErrors.push(error.message));
  const response = await page.goto("/", {waitUntil: "networkidle"});
  expect(response?.status()).toBe(200);
  const header = response?.headers()["content-security-policy"] || "";
  expect(header).toContain("style-src 'self'");
  expect(header).toContain("style-src-attr 'none'");
  expect(header).not.toContain("'unsafe-inline'");
  expect(header).toContain("script-src 'self'");

  await expect(page.getByRole("heading", {name: "Bem-vindo de volta."})).toBeVisible();
  await expect(page.getByLabel("E-mail", {exact: true})).toBeVisible();
  await page.getByRole("button", {name: "Criar conta e loja"}).click();
  await expect(page.getByRole("heading", {name: "Crie sua loja."})).toBeVisible();

  violations.push(...await page.evaluate(
    () => (window as Window & {__cspViolations?: string[]}).__cspViolations || []
  ));
  expect(violations, "No blocked CSP directives while rendering forms").toEqual([]);
  expect(pageErrors, "No uncaught React runtime errors").toEqual([]);
});
