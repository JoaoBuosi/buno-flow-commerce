import { test, expect } from "@playwright/test";

// This is a REAL Spring Boot + PostgreSQL browser test (not a fetch mock).
// It runs only in CI with a disposable local PostgreSQL 17 service.
const enabled=process.env.COMMERCE_DISPOSABLE_BACKEND_QA === "explicit-local-only";
const api=new URL(process.env.API_URL || "http://127.0.0.1:8080/api/v1");
if(enabled && (api.protocol!=="http:" ||
   !["127.0.0.1","localhost","::1"].includes(api.hostname) ||
   process.env.EXTERNAL_FRONTEND_URL ||
   process.env.VITE_QA_PREVIEW)) {
  throw Error("Real backend QA requires exclusively disposable loopback services");
}
test.skip(!enabled, "Run only in the explicit, isolated PostgreSQL 17 CI job.");

const unique=()=>Date.now().toString(36)+"-"+Math.random().toString(36).slice(2,9);
const password=["qa","postgres17","pilot","2026"].join("-");
const invite=process.env.E2E_PILOT_CODE || "ci-private-test";

test("backend real: pedido persiste após reload, API responde e duas lojas continuam isoladas",async({page,request})=>{
 const id=unique();
 const email="real-"+id+"@example.test";
 const customer="Cliente fictício "+id;
 const other="other-"+id+"@example.test";

 const anonymous=await request.get(api.toString()+"/orders/count");
 expect(anonymous.status()).toBe(401);

 await page.goto("/");
 await expect(page.getByRole("heading",{name:"Bem-vindo de volta."})).toBeVisible();
 await page.getByRole("button",{name:"Criar conta e loja"}).click();
 await page.getByLabel("Nome da loja").fill("Loja real QA "+id);
 await page.getByLabel("Código de convite do piloto").fill(invite);
 await page.getByLabel("E-mail",{exact:true}).fill(email);
 await page.getByLabel("Senha",{exact:true}).fill(password);
 const register=page.waitForResponse(r=>r.url().endsWith("/api/v1/auth/register")&&r.request().method()==="POST");
 await page.getByRole("button",{name:"Cadastrar e entrar"}).click();
 const registration=await register;
 expect(registration.status()).toBe(200);
 const auth=await registration.json() as {accessToken:string;storeId:string;role:string};
 expect(auth.accessToken.length).toBeGreaterThan(12);
 expect(auth.storeId).toBeTruthy();
 await expect(page.getByRole("heading",{name:"Seus pedidos. Sob controle."})).toBeVisible();

 const headers={Authorization:"Bearer "+auth.accessToken,"X-Store-Id":auth.storeId};
 const me=await request.get(api.toString()+"/store",{headers});
 expect(me.status()).toBe(200);
 expect((await me.json()).id).toBe(auth.storeId);

 await page.locator("nav").getByRole("button",{name:/Pedidos/}).click();
 await page.getByRole("button",{name:"+ Novo pedido"}).click();
 const dialog=page.getByRole("dialog",{name:"Novo pedido"});
 await dialog.getByRole("textbox",{name:"Cliente"}).fill(customer);
 await dialog.getByPlaceholder("SKU").fill("QA-517");
 await dialog.getByPlaceholder("Descrição").fill("Item sintético persistente");
 await dialog.getByLabel("Quantidade").fill("2");
 await dialog.getByLabel("Preço").fill("15.50");
 const create=page.waitForResponse(r=>r.url().endsWith("/api/v1/orders")&&r.request().method()==="POST");
 await dialog.getByRole("button",{name:"Criar pedido"}).click();
 const created=await create;
 expect(created.status()).toBe(201);
 const order=await created.json() as {id:string;total:number};
 expect(Number(order.total)).toBe(31);
 const row=page.getByRole("row").filter({hasText:customer});
 await expect(row).toBeVisible();
 const persisted=await request.get(api.toString()+"/orders/"+order.id,{headers});
 expect(persisted.status()).toBe(200);
 expect((await persisted.json()).customer_name).toBe(customer);

 // Browser state is lost after reload, but Spring/Postgres still hold the order.
 await page.reload();
 await expect(page.getByRole("heading",{name:"Bem-vindo de volta."})).toBeVisible();
 await page.getByLabel("E-mail",{exact:true}).fill(email);
 await page.getByLabel("Senha",{exact:true}).fill(password);
 await page.getByRole("button",{name:"Entrar",exact:true}).click();
 await page.locator("nav").getByRole("button",{name:/Pedidos/}).click();
 await expect(page.getByRole("row").filter({hasText:customer})).toBeVisible();

 const loginB=await request.post(api.toString()+"/auth/register",{
  data:{email:other,password,storeName:"Loja B sintética "+id},
  headers:{"X-Pilot-Code":invite}
 });
 expect(loginB.status()).toBe(200);
 const b=await loginB.json() as {accessToken:string;storeId:string};
 expect(b.storeId).not.toBe(auth.storeId);
 const forbidden=await request.get(api.toString()+"/orders/"+order.id,{
  headers:{Authorization:"Bearer "+b.accessToken,"X-Store-Id":b.storeId}
 });
 expect([403,404]).toContain(forbidden.status());
 const crossed=await request.get(api.toString()+"/orders/"+order.id,{
  headers:{Authorization:"Bearer "+auth.accessToken,"X-Store-Id":b.storeId}
 });
 expect([401,403,404]).toContain(crossed.status());
});
