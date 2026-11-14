import { test, afterEach } from "node:test";
import assert from "node:assert/strict";
import gateway from "../api/gateway.js";

// Pure contract tests for the REAL Vercel gateway. No HTTP server, database,
// external fetch, personal data, session or real authentication secret.
const previous = {
  COMMERCE_EDGE_SECRET: process.env.COMMERCE_EDGE_SECRET,
  COMMERCE_UPSTREAM_ORIGIN: process.env.COMMERCE_UPSTREAM_ORIGIN
};
const nativeFetch = globalThis.fetch;
const endpoint = "https://qa-backend.example.test";
const placeholderKey = "ci-only-" + "q".repeat(48);

afterEach(() => {
  for (const [k,v] of Object.entries(previous)) {
    if (v === undefined) delete process.env[k];
    else process.env[k] = v;
  }
  globalThis.fetch = nativeFetch;
});
function request(url, method="GET", headers={}) {
  return {url, method, headers};
}
class CaptureResponse {
  statusCode = 200;
  headers = new Map();
  chunks = [];
  headersSent = false;
  setHeader(name, value) { this.headers.set(name.toLowerCase(), value); }
  write(chunk) { this.headersSent = true; this.chunks.push(Buffer.from(chunk)); return true; }
  end(chunk) { if (chunk !== undefined) this.write(chunk); this.headersSent = true; }
  destroy() { this.headersSent = true; }
  text() { return Buffer.concat(this.chunks).toString("utf8"); }
  header(name) { return this.headers.get(name.toLowerCase()); }
}
async function run(req) {
  const res = new CaptureResponse();
  await gateway(req,res);
  return res;
}
function configure() {
  process.env.COMMERCE_UPSTREAM_ORIGIN = endpoint;
  process.env.COMMERCE_EDGE_SECRET = placeholderKey;
}

test("gateway fails closed when backend or edge key is missing", async () => {
  delete process.env.COMMERCE_UPSTREAM_ORIGIN;
  delete process.env.COMMERCE_EDGE_SECRET;
  globalThis.fetch = () => {throw Error("must not call upstream")};
  const result = await run(request("/api/gateway?proxy_path=orders"));
  assert.equal(result.statusCode, 503);
  assert.equal(result.header("cache-control"),"no-store");
});

test("gateway refuses prohibited methods and path traversal", async () => {
  configure();
  globalThis.fetch = () => {throw Error("must not call upstream")};
  const forbidden = await run(request("/api/gateway?proxy_path=orders","DELETE"));
  assert.equal(forbidden.statusCode,405);
  const traversal = await run(request("/api/gateway?proxy_path=orders%2F..%2Fstore"));
  assert.equal(traversal.statusCode,404);
  const external = await run(request("/admin"));
  assert.equal(external.statusCode,404);
});

test("real API calls are routed only to HTTPS upstream and never forward browser cookies", async () => {
  configure();
  let seen=null;
  globalThis.fetch = async (target, options) => {
    seen={target:new URL(target),method:options.method,headers:options.headers,redirect:options.redirect,cache:options.cache};
    return new Response(JSON.stringify({order_count:0}), {
      status:200, headers:{"Content-Type":"application/json","Cache-Control":"no-store"}
    });
  };
  const out=await run(request("/api/gateway?proxy_path=dashboard", "GET", {
    authorization:"Bearer synthetic-test-session",
    "x-store-id":"synthetic-store",
    cookie:"user_session_cookie=must-not-be-forwarded",
    "x-buno-edge-key":"malicious-client-value",
    "x-forwarded-for":"203.0.113.19",
    "x-vercel-forwarded-for":"198.51.100.27"
  }));
  assert.equal(out.statusCode,200);
  assert.deepEqual(JSON.parse(out.text()),{order_count:0});
  assert.equal(seen.target.toString(),endpoint+"/api/v1/dashboard");
  assert.equal(seen.method,"GET");
  assert.equal(seen.headers.get("authorization"),"Bearer synthetic-test-session");
  assert.equal(seen.headers.get("x-store-id"),"synthetic-store");
  assert.equal(seen.headers.get("x-buno-edge-key"),placeholderKey);
  assert.equal(seen.headers.get("x-buno-client-ip"),"198.51.100.27");
  assert.equal(seen.headers.has("cookie"),false);
  assert.equal(seen.headers.has("x-forwarded-for"),false);
  assert.equal(seen.redirect,"manual");
  assert.equal(seen.cache,"no-store");
});

test("gateway forwards POST request stream without copying body into logs", async () => {
  configure();
  let seen=null;
  globalThis.fetch = async (_target,opts) => {
    seen=opts;
    return new Response(JSON.stringify({id:"synthetic-order"}), {
      status:201,headers:{"Content-Type":"application/json"}
    });
  };
  const req=request("/api/gateway?proxy_path=orders","POST",{
    authorization:"Bearer synthetic-test-session",
    "content-type":"application/json",
    "x-store-id":"synthetic-store"
  });
  const out=await run(req);
  assert.equal(out.statusCode,201);
  assert.equal(seen.body,req);
  assert.equal(seen.duplex,"half");
  assert.equal(seen.headers.get("content-type"),"application/json");
  assert.equal(seen.headers.has("cookie"),false);
});

test("gateway rejects upstream redirects and hides internal Location", async () => {
  configure();
  globalThis.fetch=async()=>new Response(null,{status:302,headers:{Location:endpoint+"/internal"}});
  const out=await run(request("/api/gateway?proxy_path=orders"));
  assert.equal(out.statusCode,502);
  assert.equal(out.header("location"),undefined);
  assert.match(out.text(),/Resposta inesperada/);
});

test("gateway returns generic failure without leaking API hostname or key", async () => {
  configure();
  globalThis.fetch = async () => {throw new Error("sensitive upstream detail")};
  const out=await run(request("/api/gateway?proxy_path=orders"));
  assert.equal(out.statusCode,502);
  assert.doesNotMatch(out.text(),/sensitive|qa-backend|ci-only/);
});

test("gateway refuses API requests exceeding maximum size",async()=>{
  configure();
  globalThis.fetch=()=>{throw Error("must not call upstream")};
  const out=await run(request("/api/gateway?proxy_path=orders/import","POST",{"content-length":"6000001"}));
  assert.equal(out.statusCode,413);
});
