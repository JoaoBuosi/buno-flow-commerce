import React,{useEffect,useState,useRef} from "react";
import {createRoot} from "react-dom/client";
import {navigation,pageFromPath,pathFor,pageTitles,type Page} from "./app/navigation";
import {DashboardPage} from "./pages/DashboardPage";
import {OrdersPage,OrderEditor,CancelDialog} from "./pages/OrdersPage";
import {ImportsPage,CSV_HEADER} from "./pages/ImportsPage";
import {AuditPage} from "./pages/AuditPage";
import {IntelligencePage} from "./pages/IntelligencePage";
import {SettingsPage} from "./pages/SettingsPage";
import {money,labels,type Session,type NewItem,type Order,type OrderEvent,type Dashboard,type StoreProfile,type CurrentUser,type Forecast,type ImportResult} from "./types/commerce";
import "./style.css";
// Live staging refuses to send credentials unless the Java API has a configured HTTPS endpoint.
const configuredApi = String(import.meta.env.VITE_API_URL || "").trim();
const sameOrigin = configuredApi === "same-origin" && !import.meta.env.DEV;
const apiReady = import.meta.env.DEV || sameOrigin || /^https:\/\/[^\s]+\/api\/v1\/?$/.test(configuredApi);
const base=(sameOrigin ? window.location.origin + "/api/v1" : (configuredApi || (import.meta.env.DEV ? "http://localhost:8102/api/v1" : ""))).replace(/\/$/,"");
function App(){
 const [session,setSession]=useState<Session|null>(null);
 const [storeProfile,setStoreProfile]=useState<StoreProfile|null>(null);const[storeName,setStoreName]=useState("");
 const [oldPass,setOldPass]=useState("");const[newPass,setNewPass]=useState("");
 const [page,setPage]=useState<Page>(()=>pageFromPath(window.location.pathname));
 const [mobileMenu,setMobileMenu]=useState(false);
 const [importResult,setImportResult]=useState<ImportResult|null>(null);
 const [ordersError,setOrdersError]=useState("");
 const [currentUser,setCurrentUser]=useState<CurrentUser|null>(null);
 const [cancelOpen,setCancelOpen]=useState(false);
 const [cancelReason,setCancelReason]=useState("");
 const [eventsLoading,setEventsLoading]=useState(false);
 const requestId=useRef(0);
 const sessionRef=useRef<Session|null>(null);
 const [authMode,setAuthMode]=useState<"login"|"register"|"forgot"|"reset">(new URLSearchParams(window.location.search).has("reset_token")?"reset":"login");
 const [resetToken,setResetToken]=useState(new URLSearchParams(window.location.search).get("reset_token")||"");
 const [inviteCode,setInviteCode]=useState("");
 const [email,setEmail]=useState("");const[password,setPassword]=useState("");const[shop,setShop]=useState("");
 const [orders,setOrders]=useState<Order[]>([]);
 const [latestOrders,setLatestOrders]=useState<Order[]>([]);
 const [orderPage,setOrderPage]=useState(0),[resultCount,setResultCount]=useState(0);
 const [dashboard,setDashboard]=useState<Dashboard|null>(null);const[selected,setSelected]=useState<Order|null>(null);
 const [skuQuery,setSkuQuery]=useState("");const [forecast,setForecast]=useState<Forecast|null>(null);
 const [events,setEvents]=useState<OrderEvent[]>([]);const[items,setItems]=useState<NewItem[]>([{sku:"",description:"",quantity:1,price:1}]);
 const [customer,setCustomer]=useState("");const[showNew,setShowNew]=useState(false);
 const [search,setSearch]=useState("");const[status,setStatus]=useState("");const[notice,setNotice]=useState("");const[loading,setLoading]=useState(false);
 async function call(path:string,method="GET",body?:unknown,s:Session|null=session, extraHeaders:Record<string,string>={}){
  const res=await fetch(base+path,{method,headers:{"Content-Type":"application/json",...(s?{"Authorization":"Bearer "+s.accessToken,"X-Store-Id":s.storeId}:{}),...extraHeaders},body:body===undefined?undefined:JSON.stringify(body)});
  if(!res.ok){let msg="Erro HTTP "+res.status;try{const data=await res.json();msg=data.detail||data.message||msg}catch{}if(res.status===401&&s&&sessionRef.current?.accessToken===s.accessToken){requestId.current++;setSession(null);setOrders([]);setLatestOrders([]);setDashboard(null);setSelected(null)}throw new Error(msg)}
  return res.status===204?null:res.json();
 }
 async function requestRecovery(e:React.FormEvent){
   e.preventDefault();setLoading(true);
   try{
     await call("/auth/reset/request","POST",{email});
     setNotice("Se o endereço existir, enviaremos instruções para recuperar a conta.");
     setAuthMode("login");
   }catch(e){setNotice((e as Error).message)}finally{setLoading(false)}
 }
 async function completeRecovery(e:React.FormEvent){
   e.preventDefault();setLoading(true);
   try{
     await call("/auth/reset/complete","POST",{token:resetToken,newPassword:password});
     setPassword("");setResetToken("");window.history.replaceState(null,"",window.location.pathname);
     setAuthMode("login");setNotice("Senha atualizada. Entre com a nova senha.");
   }catch(e){setNotice((e as Error).message)}finally{setLoading(false)}
 }
 async function authenticate(e:React.FormEvent){e.preventDefault();setLoading(true);try{const data=await call(authMode==="login"?"/auth/login":"/auth/register","POST",authMode==="login"?{email,password}:{email,password,storeName:shop},null,authMode==="register"?{"X-Pilot-Code":inviteCode}:{});requestId.current++;setOrders([]);setLatestOrders([]);setDashboard(null);setSelected(null);setEvents([]);setStoreProfile(null);setCurrentUser(null);setSession(data);sessionRef.current=data;setAuthMode("login");setPassword("");setInviteCode("");setNotice("Sessão iniciada");}catch(e){setNotice((e as Error).message)}finally{setLoading(false)}}
 async function refresh(s:Session=session!,p=orderPage,q=search,f=status){
   if(!s)return;
   const sequence=++requestId.current;
   setOrdersError("");setLoading(true);
   try{
     const query=new URLSearchParams({page:String(p),size:"25"});
     if(q.trim())query.set("search",q.trim());
     if(f)query.set("status",f);
     const filters=new URLSearchParams();
     if(q.trim())filters.set("search",q.trim());
     if(f)filters.set("status",f);
     const [rows,count,kpis,latest]=await Promise.all([
       call("/orders?"+query.toString(),"GET",undefined,s),
       call("/orders/count?"+filters.toString(),"GET",undefined,s),
       call("/dashboard","GET",undefined,s),
       call("/orders?page=0&size=5","GET",undefined,s)
     ]);
     if(sequence!==requestId.current)return;
     setOrders(rows);setResultCount(Number(count.total));setDashboard(kpis);setLatestOrders(latest);
   }catch(e){if(sequence===requestId.current){setOrdersError((e as Error).message);setNotice((e as Error).message)}}
   finally{if(sequence===requestId.current)setLoading(false)}
 }
 async function inspect(id:string,s:Session=session!){
  setEventsLoading(true);setEvents([]);
  try{
    const [order,history]=await Promise.all([
      call("/orders/"+encodeURIComponent(id),"GET",undefined,s),
      call("/orders/"+encodeURIComponent(id)+"/history","GET",undefined,s)
    ]);
    setSelected(order);setEvents(history);
  }catch(e){setNotice((e as Error).message)}finally{setEventsLoading(false)}
 }
 async function loadStore(){
  try{const [profile,me]=await Promise.all([call("/store"),call("/me")]);setStoreProfile(profile);setStoreName(profile.name);setCurrentUser(me)}
  catch(e){setNotice((e as Error).message)}
 }
 async function renameStore(e:React.FormEvent){e.preventDefault();try{const profile=await call("/store","PATCH",{name:storeName});setStoreProfile(profile);setNotice("Dados da loja atualizados")}catch(e){setNotice((e as Error).message)}}
 async function changePassword(e:React.FormEvent){e.preventDefault();try{const next=await call("/auth/change-password","POST",{oldPassword:oldPass,newPassword:newPass});setSession(next);setOldPass("");setNewPass("");setNotice("Senha alterada e sessões antigas revogadas")}catch(e){setNotice((e as Error).message)}}
 async function loadForecast(){if(!skuQuery.trim())return;setLoading(true);try{const r=await call("/insights/demand?sku="+encodeURIComponent(skuQuery.trim()));setForecast(r)}catch(e){setNotice((e as Error).message)}finally{setLoading(false)}}
 async function exportOrders(){if(!session)return;setLoading(true);try{const r=await fetch(base+"/orders/export",{headers:{"Authorization":"Bearer "+session.accessToken,"X-Store-Id":session.storeId}});if(!r.ok)throw new Error("Falha na exportação: HTTP "+r.status);const blob=await r.blob(),url=URL.createObjectURL(blob),anchor=document.createElement("a");anchor.href=url;anchor.download="flow-commerce-orders.csv";anchor.click();URL.revokeObjectURL(url);setNotice("Exportação CSV gerada")}catch(e){setNotice((e as Error).message)}finally{setLoading(false)}}
 async function importCsv(file:File){
  if(!session)return;
  setImportResult(null);setOrdersError("");
  setLoading(true);
  try{
    if(!file.name.toLowerCase().endsWith(".csv"))throw new Error("Selecione um arquivo .csv.");
    if(!file.size||file.size>1_000_000)throw new Error("O CSV deve ter conteúdo e até 1 MB.");
    const firstLine=(await file.slice(0,512).text()).replace(/^\uFEFF/,"").split(/\r?\n/,1)[0];
    const headers=firstLine.split(",").map(x=>x.trim().replace(/^"|"$/g,""));
    if(!CSV_HEADER.split(",").every(h=>headers.includes(h)))throw new Error("Cabeçalho inválido. Consulte as seis colunas obrigatórias abaixo.");
    const body=new FormData();body.append("file",file);
    const res=await fetch(base+"/orders/import",{method:"POST",headers:{"Authorization":"Bearer "+session.accessToken,"X-Store-Id":session.storeId},body});
    if(!res.ok){const info=await res.json().catch(()=>({}));throw new Error(info.detail||info.message||"Importação recusada: HTTP "+res.status)}
    const result=await res.json() as ImportResult;
    setImportResult(result);setNotice(result.imported+" pedido(s) importado(s)");
    await refresh(session,0);
  }catch(e){setOrdersError((e as Error).message);setNotice((e as Error).message)}
  finally{setLoading(false)}
 }
 async function createOrder(e:React.FormEvent){e.preventDefault();setLoading(true);try{await call("/orders","POST",{customer,items});setShowNew(false);setCustomer("");setItems([{sku:"",description:"",quantity:1,price:1}]);setNotice("Pedido cadastrado");setOrderPage(0);await refresh(session!,0)}catch(e){setNotice((e as Error).message)}finally{setLoading(false)}}
 async function update(target:string){
  if(!selected)return;
  setLoading(true);
  try{
    await call("/orders/"+selected.id+"/status","PATCH",{status:target});
    setNotice("Pedido atualizado");
    await inspect(selected.id);
    await refresh();
  }catch(e){setNotice((e as Error).message)}finally{setLoading(false)}
 }
 async function confirmCancel(e:React.FormEvent){
  e.preventDefault();
  if(!selected||!cancelReason.trim())return;
  setLoading(true);
  try{
   await call("/orders/"+selected.id+"/cancel","POST",{reason:cancelReason.trim()});
   setCancelOpen(false);setCancelReason("");setNotice("Pedido cancelado");
   await inspect(selected.id);await refresh();
  }catch(e){setNotice((e as Error).message)}finally{setLoading(false)}
 }
 async function logout(){try{await call("/auth/logout","POST")}catch{}requestId.current++;sessionRef.current=null;setSession(null);setAuthMode("login");setSelected(null);setOrders([]);setLatestOrders([]);setEvents([]);setDashboard(null);setCurrentUser(null);setNotice("");setImportResult(null);window.history.replaceState(null,"","/");setPage("dashboard")}
 useEffect(()=>{sessionRef.current=session},[session]);
  useEffect(()=>{if(!session)return;const timer=window.setTimeout(()=>{void refresh(session)},250);return()=>window.clearTimeout(timer)},[session,orderPage,search,status]);
 useEffect(()=>{setOrderPage(0)},[search,status]);
 useEffect(()=>{if(page==="settings"&&session)void loadStore()},[page,session]);
 useEffect(()=>{
   const listener=()=>{setPage(pageFromPath(window.location.pathname));setMobileMenu(false)};
   window.addEventListener("popstate",listener);
   return ()=>window.removeEventListener("popstate",listener);
 },[]);
 function navigate(target:Page){setPage(target);setMobileMenu(false);if(window.location.pathname!==pathFor(target))window.history.pushState(null,"",pathFor(target))}
 function startNew(){navigate("orders");setShowNew(true)}
 function openFromDashboard(id:string){navigate("orders");void inspect(id)}
 if(!apiReady)return <div className="authPage"><div className="authCard"><div className="logo"><span className="mark">b</span><strong>buno<span style={{color:"#a58eff"}}>.</span></strong></div><span className="eyebrow">FLOW COMMERCE · STAGING PRIVADO 0.4</span><h1>Frontend pronto. API ainda não conectada.</h1><p className="muted">O painel React já foi preparado para o ambiente de testes. A autenticação e os pedidos serão habilitados após a conexão HTTPS com o backend Java.</p><p className="muted">Nenhum dado ou senha é enviado enquanto a conexão não estiver configurada.</p></div></div>;
 if(!session&&(authMode==="forgot"||authMode==="reset"))return <div className="authPage"><div className="authCard"><div className="logo"><span className="mark">b</span><strong>buno<span style={{color:"#a58eff"}}>.</span></strong></div><span className="eyebrow">FLOW COMMERCE · RECUPERAR ACESSO</span><h1>{authMode==="forgot"?"Recuperar senha.":"Criar nova senha."}</h1><p className="muted">A recuperação usa um link de uso único que expira em 20 minutos. O envio exige um provedor de e-mail configurado.</p><form onSubmit={authMode==="forgot"?requestRecovery:completeRecovery} className="stack">{authMode==="forgot"?<label>E-mail cadastrado<input required type="email" value={email} onChange={e=>setEmail(e.target.value)}/></label>:<label>Nova senha<input required type="password" minLength={12} maxLength={72} value={password} onChange={e=>setPassword(e.target.value)}/></label>}<button className="primary" disabled={loading}>{loading?"Processando…":authMode==="forgot"?"Enviar instruções":"Atualizar senha"}</button></form><button className="link" onClick={()=>{setAuthMode("login");window.history.replaceState(null,"",window.location.pathname)}}>Voltar ao login</button>{notice&&<p role="status" className="notice">{notice}</p>}</div></div>;
 if(!session)return <div className="authPage"><div className="authCard"><div className="logo"><span className="mark">b</span><strong>buno<span style={{color:"#a58eff"}}>.</span></strong></div><span className="eyebrow">FLOW COMMERCE · ACESSO SEGURO</span><h1>{authMode==="login"?"Bem-vindo de volta.":"Crie sua loja."}</h1><p className="muted">Conectado à API Spring Boot. Os dados são persistidos no PostgreSQL.</p><form onSubmit={authenticate} className="stack">{authMode==="register"&&<label>Nome da loja<input required maxLength={120} value={shop} onChange={e=>setShop(e.target.value)}/></label>}{authMode==="register"&&<label>Código de convite do piloto<input type="password" autoComplete="off" value={inviteCode} onChange={e=>setInviteCode(e.target.value)} placeholder="Código fornecido pela Buno"/></label>}<label>E-mail<input required type="email" value={email} onChange={e=>setEmail(e.target.value)}/></label><label>Senha<input required type="password" minLength={12} value={password} onChange={e=>setPassword(e.target.value)}/></label><button className="primary" disabled={loading}>{loading?"Processando…":authMode==="login"?"Entrar":"Cadastrar e entrar"}</button></form><button className="link" onClick={()=>setAuthMode(authMode==="login"?"register":"login")}>{authMode==="login"?"Criar conta e loja":"Já possuo conta"}</button>{authMode==="login"&&<button className="link" onClick={()=>{setAuthMode("forgot");setNotice("")}}>Esqueci minha senha</button>}{notice&&<p role="alert" className="notice">{notice}</p>}<p className="muted">Não use dados de clientes reais durante os testes.</p></div></div>;

 return <div className="layout">
 <aside className={mobileMenu?"sidebar menuOpen":"sidebar"}>
  <div className="logo"><span className="mark">b</span><strong>buno<span className="brandDot">.</span><small>FLOW COMMERCE</small></strong></div>
  <nav aria-label="Navegação principal">{navigation.map(n=><button key={n.page} className={page===n.page?"active":""} aria-current={page===n.page?"page":undefined} onClick={()=>navigate(n.page)}><span aria-hidden="true">{n.icon}</span> {n.label}</button>)}</nav>
  <div className="asideBottom">Loja ativa<br/><b>{storeProfile?.name||session.storeId.slice(0,8)+"…"}</b><p className="muted">Área autenticada · acesso privado</p><button className="secondary" onClick={()=>void logout()}>Sair</button></div>
 </aside>
 <main id="main-content">
  <header className="topBar"><div className="inline"><button className="secondary mobileToggle" onClick={()=>setMobileMenu(!mobileMenu)} aria-label={mobileMenu?"Fechar menu":"Abrir menu"} aria-expanded={mobileMenu}>☰</button><span>Flow Commerce / {navigation.find(n=>n.page===page)?.label}</span></div><span className="tag">Área autenticada</span></header>
  <section className="hero"><div><span className="eyebrow">BUNO · GESTÃO COMERCIAL</span><h1>{pageTitles[page].title}</h1><p className="muted">{pageTitles[page].description}</p></div></section>
  {notice&&<div role="status" className="notice">{notice}<button className="link" aria-label="Fechar aviso" onClick={()=>setNotice("")}>×</button></div>}
  {page==="dashboard"&&<DashboardPage dashboard={dashboard} orders={latestOrders} loading={loading} error={ordersError} onNew={startNew} onOrders={()=>navigate("orders")} onImports={()=>navigate("imports")} onSelect={openFromDashboard} onRefresh={()=>void refresh()}/>}
  {page==="orders"&&<OrdersPage orders={orders} count={resultCount} page={orderPage} search={search} status={status} selected={selected} events={events} loading={loading||eventsLoading} error={ordersError} onSearch={setSearch} onStatus={setStatus} onPage={setOrderPage} onInspect={id=>void inspect(id)} onHistory={()=>navigate("history")} onNew={startNew} onNext={target=>void update(target)} onCancel={()=>{setCancelReason("");setCancelOpen(true)}} onRefresh={()=>void refresh()}/>}
  {page==="imports"&&<ImportsPage loading={loading} result={importResult} error={ordersError} onImport={file=>void importCsv(file)} onExport={()=>void exportOrders()}/>}
  {page==="history"&&<AuditPage order={selected} events={events} loading={eventsLoading} onOrders={()=>navigate("orders")}/>}
  {page==="intelligence"&&<IntelligencePage sku={skuQuery} forecast={forecast} loading={loading} onSku={v=>{setSkuQuery(v);setForecast(null)}} onSearch={()=>void loadForecast()}/>}
  {page==="settings"&&<SettingsPage session={session} profile={storeProfile} user={currentUser} storeName={storeName} oldPass={oldPass} newPass={newPass} loading={loading} onStoreName={setStoreName} onOldPass={setOldPass} onNewPass={setNewPass} onRename={renameStore} onPassword={changePassword} onLogout={()=>void logout()}/>}
  <OrderEditor open={showNew} customer={customer} items={items} loading={loading} onCustomer={setCustomer} onItems={setItems} onClose={()=>setShowNew(false)} onSubmit={createOrder}/>
  <CancelDialog open={cancelOpen} reason={cancelReason} loading={loading} onReason={setCancelReason} onClose={()=>setCancelOpen(false)} onConfirm={confirmCancel}/>
 </main></div>
}
createRoot(document.getElementById("root")!).render(<React.StrictMode><App/></React.StrictMode>);
