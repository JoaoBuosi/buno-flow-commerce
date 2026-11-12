import { test, expect } from "@playwright/test";

test("OWNER: login exige código antes de mostrar o painel", async ({ page }) => {
  let verifyCalls=0;
  await page.route("**/api/v1/auth/login",async route=>{
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      mfaRequired:true,enrollmentRequired:false,
      challengeToken:"a".repeat(64),storeId:"00000000-0000-0000-0000-000000000001",role:"OWNER"
    })});
  });
  await page.route("**/api/v1/auth/mfa/login/verify",async route=>{
    verifyCalls++;
    expect(route.request().postDataJSON()).toEqual({
      challengeToken:"a".repeat(64),code:"123456",recoveryCode:null
    });
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      accessToken:"b".repeat(64),storeId:"00000000-0000-0000-0000-000000000001",role:"OWNER"
    })});
  });
  await page.route("**/api/v1/**",async route=>{
    if(route.request().url().includes("/auth/"))return route.continue();
    await route.fulfill({status:200,contentType:"application/json",body:"{}"});
  });
  await page.goto("/");
  await page.getByLabel("E-mail",{exact:true}).fill("owner@example.test");
  await page.getByLabel("Senha",{exact:true}).fill("very-secure-password");
  await page.getByRole("button",{name:"Entrar",exact:true}).click();
  await expect(page.getByRole("heading",{name:"Confirme seu código."})).toBeVisible();
  await expect(page.getByText("Gestão de pedidos.",{exact:true})).toHaveCount(0);
  expect(verifyCalls).toBe(0);
  await page.getByLabel("Código de 6 dígitos").fill("123456");
  await page.getByRole("button",{name:"Validar e entrar"}).click();
  await expect(page.getByText("Visão geral",{exact:false}).first()).toBeVisible();
  expect(verifyCalls).toBe(1);
});

test("OWNER: cadastro exige ativação TOTP e exibe códigos uma única vez",async({page})=>{
  await page.route("**/api/v1/auth/register",async route=>{
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      mfaRequired:true,enrollmentRequired:true,
      challengeToken:"c".repeat(64),storeId:"00000000-0000-0000-0000-000000000001",role:"OWNER"
    })});
  });
  await page.route("**/api/v1/auth/mfa/enroll/start",async route=>{
    expect(route.request().postDataJSON()).toEqual({challengeToken:"c".repeat(64)});
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      secret:"JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
      otpauthUri:"otpauth://totp/Buno?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
    })});
  });
  await page.route("**/api/v1/auth/mfa/enroll/complete",async route=>{
    expect(route.request().postDataJSON()).toEqual({
      challengeToken:"c".repeat(64),code:"654321",recoveryCode:null
    });
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      accessToken:"d".repeat(64),storeId:"00000000-0000-0000-0000-000000000001",
      role:"OWNER",recoveryCodes:["f".repeat(20),"e".repeat(20)]
    })});
  });
  await page.route("**/api/v1/**",async route=>{
    if(route.request().url().includes("/auth/"))return route.continue();
    await route.fulfill({status:200,contentType:"application/json",body:"{}"});
  });
  await page.goto("/");
  await page.getByRole("button",{name:"Criar conta e loja"}).click();
  await page.getByLabel("Nome da loja").fill("Loja QA");
  await page.getByLabel("E-mail",{exact:true}).fill("owner@example.test");
  await page.getByLabel("Senha",{exact:true}).fill("very-secure-password");
  await page.getByRole("button",{name:"Cadastrar e entrar"}).click();
  await expect(page.getByRole("heading",{name:"Configure seu autenticador."})).toBeVisible();
  await page.getByRole("button",{name:"Gerar chave do autenticador"}).click();
  await expect(page.getByText("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")).toBeVisible();
  await page.getByLabel("Código de 6 dígitos").fill("654321");
  await page.getByRole("button",{name:"Validar e entrar"}).click();
  await expect(page.getByRole("heading",{name:"Códigos de recuperação"})).toBeVisible();
  await expect(page.getByText("f".repeat(20))).toBeVisible();
  await page.getByRole("button",{name:"Já salvei os códigos; continuar"}).click();
  await expect(page.getByRole("heading",{name:"Códigos de recuperação"})).toHaveCount(0);
});
