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
