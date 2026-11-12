import { test, expect } from "@playwright/test";

// Browser-only contract tests; route mocks prevent real password changes or emails.
// API expiration, one-time use and session revocation are covered in Spring/Postgres CI.
test("recuperação: solicitação mostra confirmação sem revelar a existência do usuário", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Bem-vindo de volta." })).toBeVisible();
  await page.getByRole("button", { name: "Esqueci minha senha" }).click();
  await expect(page.getByRole("heading", { name: "Recuperar senha." })).toBeVisible();

  await page.route("**/api/v1/auth/reset/request", async route => {
    const payload=route.request().postDataJSON();
    expect(payload).toEqual({email:"qa-no-real-account@example.test"});
    await route.fulfill({
      status:202,
      contentType:"application/json",
      body:"{}"
    });
  });
  await page.getByLabel("E-mail cadastrado").fill("qa-no-real-account@example.test");
  await page.getByRole("button", { name: "Enviar instruções" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo de volta." })).toBeVisible();
  await expect(page.getByText("Se o endereço existir, enviaremos instruções")).toBeVisible();
});

test("recuperação: link de token solicita nova senha e limpa URL após sucesso", async ({ page }) => {
  const token="c".repeat(64);
  const changedPassword="qa-reset-password-e2e-2026";
  await page.route("**/api/v1/auth/reset/complete", async route => {
    expect(route.request().postDataJSON()).toEqual({token,newPassword:changedPassword});
    await route.fulfill({status:204});
  });
  await page.goto("/?reset_token="+token);
  await expect(page.getByRole("heading", { name: "Criar nova senha." })).toBeVisible();
  await page.getByLabel("Nova senha").fill(changedPassword);
  await page.getByRole("button", { name: "Atualizar senha" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo de volta." })).toBeVisible();
  await expect(page.getByText("Senha atualizada. Entre com a nova senha.")).toBeVisible();
  expect(new URL(page.url()).searchParams.has("reset_token")).toBe(false);
});

test("recuperação: token expirado mantém formulário e mostra mensagem segura", async ({ page }) => {
  const expired="d".repeat(64);
  await page.route("**/api/v1/auth/reset/complete", async route => {
    await route.fulfill({
      status:400,
      contentType:"application/json",
      body:JSON.stringify({detail:"Solicitação inválida ou expirada"})
    });
  });
  await page.goto("/?reset_token="+expired);
  await page.getByLabel("Nova senha").fill("qa-expired-password-e2e-2026");
  await page.getByRole("button", { name: "Atualizar senha" }).click();
  await expect(page.getByRole("heading", { name: "Criar nova senha." })).toBeVisible();
  await expect(page.getByRole("status")).toContainText("Solicitação inválida ou expirada");
});
