import { URL } from "node:url";

// Required: preserve raw multipart/CSV uploads without parsing credentials or
// buffering client files in the Vercel function.
export const config = { api: { bodyParser: false } };

const METHODS = new Set(["GET", "POST", "PATCH", "OPTIONS"]);
const PASSTHROUGH_HEADERS = ["authorization", "content-type", "x-store-id", "x-pilot-code"];
const RESPONSE_HEADERS = ["content-type", "content-disposition", "content-length"];

/**
 * Vercel Authentication protects this project. This proxy injects a secret
 * kept on the servers and NEVER forwards browser cookies or incoming edge
 * secret headers to Railway. Direct Railway requests must be rejected.
 */
export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Referrer-Policy", "no-referrer");

  const method = String(req.method || "").toUpperCase();
  if (!METHODS.has(method)) {
    res.statusCode = 405;
    res.setHeader("Allow", [...METHODS].join(", "));
    return res.end(JSON.stringify({ message: "Método não permitido" }));
  }

  const secret = process.env.COMMERCE_EDGE_SECRET || "";
  const originValue = process.env.COMMERCE_UPSTREAM_ORIGIN || "";
  let origin;
  try {
    origin = new URL(originValue);
    if (origin.protocol !== "https:" || origin.pathname !== "/" || origin.search ||
        origin.hash || origin.username || origin.password || origin.port) throw new Error("invalid upstream");
  } catch {
    res.statusCode = 503;
    return res.end(JSON.stringify({ message: "API de staging ainda não configurada" }));
  }
  if (secret.length < 32) {
    res.statusCode = 503;
    return res.end(JSON.stringify({ message: "Gateway de staging indisponível" }));
  }

  const incoming = new URL(req.url || "/", "https://vercel-proxy.invalid");
  if (!(incoming.pathname === "/api/v1" || incoming.pathname.startsWith("/api/v1/"))) {
    res.statusCode = 404;
    return res.end(JSON.stringify({ message: "Rota inválida" }));
  }

  const length = Number(req.headers["content-length"] || "0");
  if (!Number.isFinite(length) || length > 6_000_000) {
    res.statusCode = 413;
    return res.end(JSON.stringify({ message: "Arquivo muito grande" }));
  }

  const headers = new Headers();
  for (const name of PASSTHROUGH_HEADERS) {
    const value = req.headers[name];
    if (typeof value === "string") headers.set(name, value);
  }
  headers.set("X-Buno-Edge-Key", secret);
  const target = new URL(incoming.pathname + incoming.search, origin);
  try {
    const upstream = await fetch(target, {
      method,
      headers,
      body: method === "GET" || method === "OPTIONS" ? undefined : req,
      ...(method === "GET" || method === "OPTIONS" ? {} : { duplex: "half" }),
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(25_000)
    });
    res.statusCode = upstream.status;
    for (const name of RESPONSE_HEADERS) {
      const value = upstream.headers.get(name);
      if (value) res.setHeader(name, value);
    }
    // Reject redirect forwarding and prevent Location header from leaking a
    // Railway hostname. Errors remain generic at the gateway boundary.
    if (upstream.status >= 300 && upstream.status < 400) {
      res.statusCode = 502;
      return res.end(JSON.stringify({ message: "Resposta inesperada da API" }));
    }
    if (upstream.body) {
      for await (const chunk of upstream.body) res.write(chunk);
    }
    res.end();
  } catch {
    // Never include upstream URL, internal hostnames, secrets or stack traces.
    if (!res.headersSent) {
      res.statusCode = 502;
      res.setHeader("Content-Type", "application/json");
      return res.end(JSON.stringify({ message: "API de staging temporariamente indisponível" }));
    }
    res.destroy();
  }
}
