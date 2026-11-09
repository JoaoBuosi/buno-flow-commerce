import test from "node:test";
import assert from "node:assert/strict";
import { Readable } from "node:stream";
import handler from "../api/gateway.js";

const setup=({url="/api/v1/orders",method="GET",headers={},body=""}={})=>{
  const req=Readable.from(body ? [Buffer.from(body)] : []);
  Object.assign(req,{method,url,headers});
  const output=[];
  const res={
    headers:{},headersSent:false,statusCode:200,
    setHeader(k,v){this.headers[k.toLowerCase()]=v;},
    write(chunk){output.push(Buffer.from(chunk));this.headersSent=true;},
    end(chunk){if(chunk)output.push(Buffer.from(chunk));this.finished=true;},
    destroy(){this.destroyed=true;},
    get content(){return Buffer.concat(output).toString();}
  };
  return {req,res};
};

const withEnv=async (vars,fn)=>{
  const old={COMMERCE_EDGE_SECRET:process.env.COMMERCE_EDGE_SECRET,
    COMMERCE_UPSTREAM_ORIGIN:process.env.COMMERCE_UPSTREAM_ORIGIN};
  Object.assign(process.env,vars);
  try{return await fn();}finally{
    for(const [k,v] of Object.entries(old)){if(v===undefined)delete process.env[k];else process.env[k]=v;}
  }
};

test("fails closed when upstream and secret not configured",async()=>{
  const {req,res}=setup();
  await withEnv({COMMERCE_EDGE_SECRET:"",COMMERCE_UPSTREAM_ORIGIN:""},()=>handler(req,res));
  assert.equal(res.statusCode,503);
  assert.equal(res.headers["cache-control"],"no-store");
});

test("injects server-only secret and strips browser cookies and spoofed headers",async()=>{
  const {req,res}=setup({url:"/api/gateway?proxy_path=orders&size=2",headers:{
    cookie:"vercel-auth-sensitive","x-buno-edge-key":"forged","authorization":"Bearer user-token",
    "x-store-id":"store-a","x-forwarded-for":"fake-ip"
  }});
  const original=globalThis.fetch;
  globalThis.fetch=async(url,opt)=>{
    assert.equal(url.toString(),"https://backend.example.test/api/v1/orders?size=2");
    assert.equal(opt.headers.get("X-Buno-Edge-Key"),"server-side-secret-value-2026-of-sufficient-length");
    assert.equal(opt.headers.get("authorization"),"Bearer user-token");
    assert.equal(opt.headers.get("x-store-id"),"store-a");
    assert.equal(opt.headers.get("cookie"),null);
    assert.equal(opt.headers.get("x-forwarded-for"),null);
    return new Response(JSON.stringify({message:"Autenticação necessária"}),{
      status:401,headers:{"Content-Type":"application/json"}
    });
  };
  try{
    await withEnv({
      COMMERCE_EDGE_SECRET:"server-side-secret-value-2026-of-sufficient-length",
      COMMERCE_UPSTREAM_ORIGIN:"https://backend.example.test"
    },()=>handler(req,res));
    assert.equal(res.statusCode,401);
    assert.match(res.content,/Autenticação necessária/);
  }finally{globalThis.fetch=original;}
});

test("streams JSON uploads without losing the body",async()=>{
  const body=JSON.stringify({customer:"Cliente",items:[{sku:"X",quantity:1}]});
  const {req,res}=setup({url:"/api/v1/orders",method:"POST",
    headers:{"content-type":"application/json","content-length":String(Buffer.byteLength(body))},body});
  const original=globalThis.fetch;
  globalThis.fetch=async(_url,opt)=>{
    let received="";
    for await (const chunk of opt.body)received+=chunk.toString();
    assert.equal(received,body);
    assert.equal(opt.method,"POST");
    assert.equal(opt.duplex,"half");
    return new Response('{"id":"order-1"}',{status:201,headers:{"Content-Type":"application/json"}});
  };
  try{
    await withEnv({
      COMMERCE_EDGE_SECRET:"server-side-secret-value-2026-of-sufficient-length",
      COMMERCE_UPSTREAM_ORIGIN:"https://backend.example.test"
    },()=>handler(req,res));
    assert.equal(res.statusCode,201);
    assert.match(res.content,/order-1/);
  }finally{globalThis.fetch=original;}
});

test("rejects arbitrary paths, unapproved methods and oversized uploads",async()=>{
  await withEnv({
    COMMERCE_EDGE_SECRET:"server-side-secret-value-2026-of-sufficient-length",
    COMMERCE_UPSTREAM_ORIGIN:"https://backend.example.test"
  },async()=>{
    let x=setup({url:"/internal/root"});
    await handler(x.req,x.res);assert.equal(x.res.statusCode,404);
    x=setup({url:"/api/v1/orders",method:"DELETE"});
    await handler(x.req,x.res);assert.equal(x.res.statusCode,405);
    x=setup({url:"/api/v1/orders",method:"POST",headers:{"content-length":"8000000"}});
    await handler(x.req,x.res);assert.equal(x.res.statusCode,413);
  });
});
