import { test, expect } from "@playwright/test";

test("OWNER ativa 2FA voluntariamente em Configurações sem perder acesso antes da confirmação", async({page})=>{
  let enabled=false;
  let enrollCalls=0;
  const storeId="00000000-0000-0000-0000-000000000001";
  await page.route("**/api/v1/auth/login",async route=>{
    await route.fulfill({status:200,contentType:"application/json",
      body:JSON.stringify({accessToken:"a".repeat(64),storeId,role:"OWNER"})});
  });
  await page.route("**/api/v1/auth/mfa/status",async route=>{
    await route.fulfill({status:200,contentType:"application/json",
      body:JSON.stringify({enrolled:enabled,required:enabled})});
  });
  await page.route("**/api/v1/auth/mfa/self/enroll",async route=>{
    enrollCalls++;
    expect(route.request().headers()["authorization"]).toBe("Bearer "+"a".repeat(64));
    expect(route.request().postDataJSON()).toEqual({currentPassword:"confirm-owner-password"});
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      mfaRequired:true,enrollmentRequired:true,challengeToken:"c".repeat(64),
      storeId,role:"OWNER"
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
    expect(route.request().postDataJSON()).toEqual({challengeToken:"c".repeat(64),
      code:"123456",recoveryCode:null});
    enabled=true;
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({
      accessToken:"b".repeat(64),storeId,role:"OWNER",
      recoveryCodes:Array.from({length:8},(_,i)=>String(i).repeat(20))
    })});
  });
  await page.route("**/api/v1/**",async route=>{
    if(route.request().url().includes("/auth/"))return route.fallback();
    const path=new URL(route.request().url()).pathname;
    const data=path.endsWith("/orders/count")?{total:0}:
      path.endsWith("/dashboard")?{
        order_count:0,active_count:0,delivered_count:0,
        canceled_count:0,order_value:0
      }:path.endsWith("/orders")?[]:
      path.endsWith("/store")?{id:storeId,name:"Loja QA"}:{};
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(data)});
  });

  await page.goto("/");
  await page.getByLabel("E-mail",{exact:true}).fill("owner@example.test");
  await page.getByLabel("Senha",{exact:true}).fill("very-secure-password");
  await page.getByRole("button",{name:"Entrar",exact:true}).click();
  await page.getByRole("button",{name:/Configurações/}).click();
  await expect(page.getByRole("heading",{name:"Autenticação em duas etapas (2FA)"})).toBeVisible();
  await page.getByLabel("Confirme sua senha para ativar o 2FA").fill("confirm-owner-password");
  await page.getByRole("button",{name:"Configurar aplicativo autenticador"}).click();
  expect(enrollCalls).toBe(1);
  await expect(page.getByRole("heading",{name:"Configure seu autenticador."})).toBeVisible();
  await page.getByRole("button",{name:"Gerar chave do autenticador"}).click();
  await page.getByLabel("Código de 6 dígitos").fill("123456");
  await page.getByRole("button",{name:"Validar e entrar"}).click();
  await expect(page.getByRole("heading",{name:"Códigos de recuperação"})).toBeVisible();
  const continueButton=page.getByRole("button",{name:"Já salvei os códigos; continuar"});
  await expect(continueButton).toBeDisabled();
  await page.getByLabel(/Confirmo que salvei meus oito códigos/).check();
  await expect(continueButton).toBeEnabled();
  await continueButton.click();
  await expect(page.getByText("2FA ativo.",{exact:false})).toBeVisible();
});
