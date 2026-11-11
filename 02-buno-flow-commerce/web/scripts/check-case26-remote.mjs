#!/usr/bin/env node
/**
 * Read-only authorized browser smoke test against the actual protected Vercel
 * deployment. No API secrets, no token capture, no cookies persisted to disk.
 * A real operator signs in manually to both Vercel and the Flow Commerce app.
 */
import { chromium, expect } from "@playwright/test";

async function main() {
  const url = process.argv[2];
  if (!url) {
    throw new Error("Uso: node scripts/check-case26-remote.mjs https://buno-flow-commerce-staging-<id>-<team>.vercel.app");
  }
  const base = new URL(url);
  // Hard refuse local files, third-party hosts and URLs embedding credentials.
  if (base.protocol !== "https:" || base.username || base.password || base.pathname !== "/" ||
      base.search || base.hash || !/^buno-flow-commerce-staging-[a-z0-9-]+\.vercel\.app$/.test(base.hostname)) {
    throw new Error("A URL precisa ser a origem HTTPS da implantação privada Flow Commerce na Vercel.");
  }

  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ acceptDownloads: false });
  const page = await context.newPage();
  try {
    console.log("Abra a autenticação na janela do navegador. Entre com a conta Vercel autorizada e com uma conta de LOJA DEMONSTRAÇÃO.");
    console.log("Nenhuma credencial será capturada, impressa ou salva pelo verificador.");
    await page.goto(base.origin + "/?case26=1", { waitUntil: "domcontentloaded" });
    // The merchant login is deliberately manual. The Vercel SSO boundary
    // remains intact; the test does not create sessions or bypass protection.
    const guide = page.getByRole("region", { name: "Guia CASE26" });
    await guide.waitFor({ state: "visible", timeout: 240_000 });

    const seen = { store: false, dashboard: false };
    page.on("response", response => {
      const u = response.url();
      if (response.status() !== 200 || !u.startsWith(base.origin + "/api/v1/")) return;
      if (u.includes("/api/v1/store")) seen.store = true;
      if (u.includes("/api/v1/dashboard")) seen.dashboard = true;
    });
    await guide.getByRole("button", { name: /Verificar (API e loja|novamente)/ }).click();
    await expect(guide.getByRole("status")).toHaveText("Conta e indicadores acessíveis", { timeout: 30_000 });
    if (!seen.store || !seen.dashboard) throw new Error("Faltam respostas HTTP 200 do store/dashboard remotos.");
    console.log("PASSOU: página com SSO + conta do lojista + API de store/dashboard respondendo HTTP 200.");
    console.log("NÃO TESTADO: escrita de pedidos, CSV, isolamento entre lojas, backup/restauração e cobrança.");
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch(e => {
  // Do not print URL containing tokens, response headers or exception stacks.
  console.error("FALHOU: " + (e instanceof Error ? e.message : "erro desconhecido"));
  process.exitCode = 1;
});
