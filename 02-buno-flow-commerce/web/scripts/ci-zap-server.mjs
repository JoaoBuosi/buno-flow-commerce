/**
 * Disposable, read-only DAST web target.
 *
 * Serves the REAL Vite production build under localhost with the security
 * headers from web/vercel.json, and forwards only GET/HEAD API requests to the
 * local Spring + PostgreSQL test stack. Never accepts a configurable upstream.
 *
 * This is not a production gateway and must never be deployed to Vercel.
 */
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { dirname, extname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const folder = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const publicDir = resolve(folder, "dist");
const securityHeaders = JSON.parse(await readFile(join(folder, "vercel.json"), "utf8")).headers;
const port = 5173;
const upstream = "http://127.0.0.1:8080";
const authFixtureFile = process.env.ZAP_AUTH_FIXTURE_PATH || "";
const authFixtures = authFixtureFile ?
  JSON.parse(await readFile(authFixtureFile, "utf8")) : null;
// Fail startup if the temporary fixture is malformed. All data is test-only.
for (const name of ["owner-a", "owner-b"]) {
  if (!authFixtures) break;
  const account = authFixtures[name];
  if (!account ||
      !/^[0-9a-f]{64}$/.test(account.token || "") ||
      !/^[0-9a-f-]{36}$/.test(account.storeId || "") ||
      !/^[0-9a-f-]{36}$/.test(account.orderId || "")) {
    throw new Error("Invalid CI-only authentication fixture");
  }
}
const authenticatedPaths = new Map([
  ["me", () => "/api/v1/me"],
  ["store", () => "/api/v1/store"],
  ["dashboard", () => "/api/v1/dashboard"],
  ["orders", () => "/api/v1/orders"],
  ["count", () => "/api/v1/orders/count"],
  ["mfa-status", () => "/api/v1/auth/mfa/status"],
  ["order", acc => "/api/v1/orders/" + acc.orderId],
  ["history", acc => "/api/v1/orders/" + acc.orderId + "/history"],
  ["export", () => "/api/v1/orders/export"]
]);

const mime = new Map([
  [".html", "text/html; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".svg", "image/svg+xml"],
  [".png", "image/png"],
  [".ico", "image/x-icon"],
  [".webp", "image/webp"],
  [".woff2", "font/woff2"]
]);
const safeGetPaths = [
  "/actuator/health",
  "/api/v1/me",
  "/api/v1/orders",
  "/api/v1/dashboard",
  "/api/v1/store",
  "/api/v1/auth/mfa/status"
];

function setHeaders(res, pathname) {
  // Match the repo's checked-in Vercel global/security and API header rules.
  for (const rule of securityHeaders) {
    if (rule.source === "/(.*)" ||
       (rule.source === "/api/(.*)" && pathname.startsWith("/api/"))) {
      for (const h of rule.headers) res.setHeader(h.key, h.value);
    }
  }
  res.setHeader("Cache-Control", "no-store");
}

function respond(res, code, contentType, body, method) {
  res.statusCode = code;
  res.setHeader("Content-Type", contentType);
  res.end(method === "HEAD" ? undefined : body);
}

const server = createServer(async (req, res) => {
  const method = req.method || "GET";
  const path = new URL(req.url || "/", "http://127.0.0.1").pathname;
  setHeaders(res, path);

  if (!["GET", "HEAD"].includes(method)) {
    respond(res, 405, "text/plain; charset=utf-8", "Read-only CI target", method);
    return;
  }

  // Server-side session injection for two synthetic, MFA-verified OWNER users.
  // The browser/ZAP never receives a bearer token: it only sees the resulting
  // authenticated, test-only GET responses. No writes are proxied.
  if (path === "/__ci__/auth-index") {
    if (!authFixtures) {
      respond(res, 404, "text/plain; charset=utf-8", "QA auth fixtures unavailable", method);
      return;
    }
    const links = ["owner-a", "owner-b"].flatMap(
      name => [...authenticatedPaths.keys()].map(
        key => '<li><a href="/__ci__/authenticated/' + name + '/' + key +
               '">' + name + ": " + key + "</a></li>"
      )
    ).join("");
    respond(res, 200, "text/html; charset=utf-8",
      "<!doctype html><html><head><title>Isolated authenticated GET checks</title></head>" +
      "<body><h1>Two isolated merchant sessions (MFA verified)</h1><ul>" +
      links + "</ul></body></html>", method);
    return;
  }
  if (path.startsWith("/__ci__/authenticated/")) {
    const parts = path.split("/");
    if (!authFixtures || parts.length !== 5 || !["owner-a", "owner-b"].includes(parts[3]) ||
        !authenticatedPaths.has(parts[4])) {
      respond(res, 404, "text/plain; charset=utf-8", "Not an allowed QA GET route", method);
      return;
    }
    const acc = authFixtures[parts[3]];
    const apiPath = authenticatedPaths.get(parts[4])(acc);
    try {
      const response = await fetch(upstream + apiPath, {
        method, redirect: "manual",
        headers: { "Authorization": "Bearer " + acc.token, "X-Store-Id": acc.storeId },
        signal: AbortSignal.timeout(6000)
      });
      res.statusCode = response.status;
      res.setHeader("Content-Type",
        response.headers.get("content-type") || "application/json; charset=utf-8");
      res.end(method === "HEAD" ? undefined : Buffer.from(await response.arrayBuffer()));
    } catch {
      respond(res, 503, "text/plain; charset=utf-8", "Isolated GET proxy unavailable", method);
    }
    return;
  }

  // Synthetic discovery index: only safe GET requests against the real backend.
  // It exists only inside the GitHub runner; production never serves this page.
  if (path === "/__ci__/api-index") {
    const links = safeGetPaths.map(p => '<li><a href="' + p + '">' + p + '</a></li>').join("");
    respond(res, 200, "text/html; charset=utf-8",
      '<!doctype html><html lang="en"><head><title>QA API GET discovery</title></head>' +
      '<body><h1>Flow Commerce isolated API GET-only checks</h1><ul>' + links +
      "</ul></body></html>", method);
    return;
  }

  if (safeGetPaths.includes(path)) {
    try {
      const response = await fetch(upstream + path, {
        method, redirect: "manual", signal: AbortSignal.timeout(5000)
      });
      res.statusCode = response.status;
      res.setHeader("Content-Type", response.headers.get("content-type") ||
        "application/json; charset=utf-8");
      res.end(method === "HEAD" ? undefined : Buffer.from(await response.arrayBuffer()));
    } catch {
      respond(res, 503, "text/plain; charset=utf-8", "Isolated API unavailable", method);
    }
    return;
  }
  if (path.startsWith("/api/") || path.startsWith("/actuator/")) {
    respond(res, 404, "text/plain; charset=utf-8", "Outside passive QA allowlist", method);
    return;
  }

  let localPath;
  try {
    localPath = resolve(publicDir, "." + decodeURIComponent(path));
  } catch {
    respond(res, 400, "text/plain; charset=utf-8", "Malformed path", method);
    return;
  }
  if (localPath !== publicDir && !localPath.startsWith(publicDir + sep)) {
    respond(res, 404, "text/plain; charset=utf-8", "Outside build output", method);
    return;
  }
  try {
    const info = await stat(localPath);
    if (info.isDirectory()) localPath = join(localPath, "index.html");
    const body = await readFile(localPath);
    respond(res, 200, mime.get(extname(localPath)) ||
      "application/octet-stream", body, method);
  } catch {
    // SPA fallback only for route paths, not for missing assets.
    if (extname(path)) {
      respond(res, 404, "text/plain; charset=utf-8", "Not found", method);
    } else {
      respond(res, 200, "text/html; charset=utf-8",
        await readFile(join(publicDir, "index.html")), method);
    }
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log("CI-only read-only DAST target ready on loopback");
});
