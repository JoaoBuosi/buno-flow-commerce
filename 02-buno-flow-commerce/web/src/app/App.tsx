import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createApi } from "../services/api";
import type { Session, Store } from "../types";
import { useResource } from "../hooks/useResource";
import { routes, useNavigation } from "./navigation";
import { Icon } from "../components/ui";
import { AuthPage } from "../pages/AuthPage";
import { DashboardPage } from "../pages/DashboardPage";
import { OrdersPage } from "../pages/OrdersPage";
import { ImportsPage } from "../pages/ImportsPage";
import { AuditPage } from "../pages/AuditPage";
import { IntelligencePage } from "../pages/IntelligencePage";
import { SettingsPage } from "../pages/SettingsPage";
export function App() {
  const [session, setSession] = useState<Session | null>(null),
    [message, setMessage] = useState("");
  const expire = useCallback(() => {
    setSession(null);
    setMessage("Sua sessão expirou. Entre novamente.");
  }, []);
  const api = useMemo(() => createApi(session, expire), [session, expire]);
  const { page, params, navigate } = useNavigation();
  return session ? (
    <Workspace
      key={session.storeId}
      api={api}
      page={page}
      params={params}
      navigate={navigate}
      onSession={setSession}
      exit={() => {
        setSession(null);
        setMessage("Sessão encerrada.");
        navigate("dashboard");
      }}
    />
  ) : (
    <AuthPage message={message} onLogin={setSession} />
  );
}
type WorkspaceProps = {
  api: ReturnType<typeof createApi>;
  page: ReturnType<typeof useNavigation>["page"];
  params: URLSearchParams;
  navigate: ReturnType<typeof useNavigation>["navigate"];
  onSession: (s: Session) => void;
  exit: () => void;
};
function Workspace({
  api,
  page,
  params,
  navigate,
  onSession,
  exit,
}: WorkspaceProps) {
  const [mobile, setMobile] = useState(false),
    [notice, setNotice] = useState(""),
    [leaving, setLeaving] = useState(false);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const store = useResource(
    (signal) => api.get<Store>("/store", signal),
    [api],
  );
  const route = routes.find((r) => r[0] === page)!;
  useEffect(() => {
    setMobile(false);
    document.title = route[1] + " · Flow Commerce";
    titleRef.current?.focus();
  }, [page]);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 7000);
    return () => clearTimeout(timer);
  }, [notice]);
  async function logout() {
    setLeaving(true);
    try {
      await api.send("/auth/logout", "POST");
      exit();
    } catch (e) {
      setNotice(
        "Não foi possível encerrar a sessão no servidor. " +
          (e as Error).message,
      );
    } finally {
      setLeaving(false);
    }
  }
  return (
    <div className="layout">
      <a
        className="skipLink"
        href="#main-content"
        onClick={(e) => {
          e.preventDefault();
          titleRef.current?.focus();
        }}
      >
        Pular para o conteúdo
      </a>
      <aside className={mobile ? "sidebar expanded" : "sidebar"}>
        <div className="brandRow">
          <a
            className="logo"
            href="#/dashboard"
            aria-label="Buno Flow Commerce — visão geral"
          >
            <span className="mark">b</span>
            <span>
              <strong>
                buno<span className="brandDot">.</span>
              </strong>
              <small>FLOW COMMERCE</small>
            </span>
          </a>
          <button
            className="secondary mobileToggle"
            aria-expanded={mobile}
            aria-controls="navigation"
            onClick={() => setMobile((v) => !v)}
          >
            {mobile ? "Fechar menu" : "Abrir menu"}
          </button>
        </div>
        <div className="navGroup" id="navigation">
          <span className="navCaption">ESPAÇO DE TRABALHO</span>
          <nav aria-label="Navegação principal">
            {routes.map(([key, label]) => (
              <a
                key={key}
                href={"#/" + key}
                aria-current={page === key ? "page" : undefined}
                onClick={() => setMobile(false)}
              >
                <Icon name={key} />
                <span>{label}</span>
              </a>
            ))}
          </nav>
          <div className="asideBottom">
            <div className="storeAvatar">
              {store.data?.name.slice(0, 1).toUpperCase() || "L"}
            </div>
            <div>
              <small>LOJA ATIVA</small>
              <strong>{store.data?.name || "Sua loja"}</strong>
            </div>
            <button
              className="link"
              disabled={leaving}
              onClick={() => void logout()}
            >
              {leaving ? "Saindo…" : "Sair"}
            </button>
          </div>
          <small className="version">Buno Flow Commerce · 0.4.2</small>
        </div>
      </aside>
      <main id="main-content">
        <header className="topbar">
          <span>
            Workspace <span aria-hidden="true">/</span>{" "}
            <strong>{route[1]}</strong>
          </span>
          <span className="privateTag">Piloto privado</span>
        </header>
        <section className="hero">
          <div>
            <span className="eyebrow">FLOW COMMERCE</span>
            <h1 tabIndex={-1} ref={titleRef}>
              {route[1]}
            </h1>
            <p>{route[2]}</p>
          </div>
          <span className="heroIcon">
            <Icon name={page} />
          </span>
        </section>
        <div className="pageContent" data-page={page}>
          {page === "dashboard" && (
            <DashboardPage api={api} navigate={navigate} />
          )}{" "}
          {page === "orders" && (
            <OrdersPage
              api={api}
              params={params}
              navigate={navigate}
              notify={setNotice}
            />
          )}{" "}
          {page === "imports" && (
            <ImportsPage
              api={api}
              notify={setNotice}
              openOrder={(id) => navigate("orders", { id })}
            />
          )}{" "}
          {page === "history" && (
            <AuditPage
              key={params.get("id") || ""}
              api={api}
              id={params.get("id")}
              navigate={(id) => navigate("history", { id })}
              orders={() => navigate("orders")}
            />
          )}{" "}
          {page === "intelligence" && (
            <IntelligencePage
              key={params.get("sku") || ""}
              api={api}
              sku={params.get("sku") || ""}
              navigate={(sku) => navigate("intelligence", { sku })}
            />
          )}{" "}
          {page === "settings" && (
            <SettingsPage
              api={api}
              onSession={onSession}
              notify={setNotice}
              logout={() => void logout()}
              storeChanged={store.reload}
            />
          )}
        </div>
        <footer className="pageFooter">
          Buno · Gestão de pedidos, com clareza.
        </footer>
      </main>
      {notice && (
        <div className="toast" role="status">
          <span>{notice}</span>
          <button
            className="iconButton"
            aria-label="Dispensar mensagem"
            onClick={() => setNotice("")}
          >
            ×
          </button>
        </div>
      )}
    </div>
  );
}
