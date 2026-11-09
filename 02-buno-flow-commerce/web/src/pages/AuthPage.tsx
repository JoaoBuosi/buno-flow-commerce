import React, { useState } from "react";
import { apiReady, createApi } from "../services/api";
import type { Session } from "../types";
const api = createApi(null);
export function AuthPage({
  onLogin,
  message,
}: {
  onLogin: (s: Session) => void;
  message: string;
}) {
  const [authMode, setAuthMode] = useState<
    "login" | "register" | "forgot" | "reset"
  >(
    new URLSearchParams(location.search).has("reset_token") ? "reset" : "login",
  );
  const [resetToken, setResetToken] = useState(
    new URLSearchParams(location.search).get("reset_token") || "",
  );
  const [inviteCode, setInviteCode] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [shop, setShop] = useState("");
  const [notice, setNotice] = useState(message);
  const [loading, setLoading] = useState(false);
  const call = (
    path: string,
    method: string,
    body?: unknown,
    _s?: null,
    headers?: Record<string, string>,
  ) => api.send<Session>(path, method, body, headers);
  async function requestRecovery(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      await call("/auth/reset/request", "POST", { email });
      setNotice(
        "Se o endereço existir, enviaremos instruções para recuperar a conta.",
      );
      setAuthMode("login");
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  async function completeRecovery(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      await call("/auth/reset/complete", "POST", {
        token: resetToken,
        newPassword: password,
      });
      setPassword("");
      setResetToken("");
      window.history.replaceState(null, "", window.location.pathname);
      setAuthMode("login");
      setNotice("Senha atualizada. Entre com a nova senha.");
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  async function authenticate(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const data = await call(
        authMode === "login" ? "/auth/login" : "/auth/register",
        "POST",
        authMode === "login"
          ? { email, password }
          : { email, password, storeName: shop },
        null,
        authMode === "register" ? { "X-Pilot-Code": inviteCode } : {},
      );
      onLogin(data);
      setAuthMode("login");
      setPassword("");
      setInviteCode("");
      setNotice("Sessão iniciada");
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  if (!apiReady)
    return (
      <div className="authPage">
        <div className="authCard">
          <div className="logo">
            <span className="mark">b</span>
            <strong>
              buno<span style={{ color: "#a58eff" }}>.</span>
            </strong>
          </div>
          <span className="eyebrow">FLOW COMMERCE · PILOTO PRIVADO</span>
          <h1>Frontend pronto. API ainda não conectada.</h1>
          <p className="muted">
            O painel React já foi preparado para o ambiente de testes. A
            autenticação e os pedidos serão habilitados após a conexão HTTPS com
            o backend Java.
          </p>
          <p className="muted">
            Nenhum dado ou senha é enviado enquanto a conexão não estiver
            configurada.
          </p>
        </div>
      </div>
    );
  if (authMode === "forgot" || authMode === "reset")
    return (
      <div className="authPage">
        <div className="authCard">
          <div className="logo">
            <span className="mark">b</span>
            <strong>
              buno<span style={{ color: "#a58eff" }}>.</span>
            </strong>
          </div>
          <span className="eyebrow">FLOW COMMERCE · RECUPERAR ACESSO</span>
          <h1>
            {authMode === "forgot" ? "Recuperar senha." : "Criar nova senha."}
          </h1>
          <p className="muted">
            Enviaremos um link de uso único para o e-mail da sua conta.
          </p>
          <form
            onSubmit={
              authMode === "forgot" ? requestRecovery : completeRecovery
            }
            className="stack"
          >
            {authMode === "forgot" ? (
              <label>
                E-mail cadastrado
                <input
                  required
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </label>
            ) : (
              <label>
                Nova senha
                <input
                  required
                  type="password"
                  minLength={12}
                  maxLength={72}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>
            )}
            <button className="primary" disabled={loading}>
              {loading
                ? "Processando…"
                : authMode === "forgot"
                  ? "Enviar instruções"
                  : "Atualizar senha"}
            </button>
          </form>
          <button
            className="link"
            onClick={() => {
              setAuthMode("login");
              window.history.replaceState(null, "", window.location.pathname);
            }}
          >
            Voltar ao login
          </button>
          {notice && (
            <p role="status" className="notice">
              {notice}
            </p>
          )}
        </div>
      </div>
    );
  return (
    <div className="authPage">
      <div className="authCard">
        <div className="logo">
          <span className="mark">b</span>
          <strong>
            buno<span style={{ color: "#a58eff" }}>.</span>
          </strong>
        </div>
        <span className="eyebrow">FLOW COMMERCE · ACESSO SEGURO</span>
        <h1>
          {authMode === "login" ? "Bem-vindo de volta." : "Crie sua loja."}
        </h1>
        <p className="muted">
          Acesse sua loja e acompanhe seus pedidos em um só lugar.
        </p>
        <form onSubmit={authenticate} className="stack">
          {authMode === "register" && (
            <label>
              Nome da loja
              <input
                required
                maxLength={120}
                value={shop}
                onChange={(e) => setShop(e.target.value)}
              />
            </label>
          )}
          {authMode === "register" && (
            <label>
              Código de convite do piloto
              <input
                type="password"
                autoComplete="off"
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value)}
                placeholder="Código fornecido pela Buno"
              />
            </label>
          )}
          <label>
            E-mail
            <input
              required
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label>
            Senha
            <input
              required
              type="password"
              minLength={12}
              maxLength={72}
              autoComplete={
                authMode === "login" ? "current-password" : "new-password"
              }
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <button className="primary" disabled={loading}>
            {loading
              ? "Processando…"
              : authMode === "login"
                ? "Entrar"
                : "Cadastrar e entrar"}
          </button>
        </form>
        <button
          className="link"
          onClick={() =>
            setAuthMode(authMode === "login" ? "register" : "login")
          }
        >
          {authMode === "login" ? "Criar conta e loja" : "Já possuo conta"}
        </button>
        {authMode === "login" && (
          <button
            className="link"
            onClick={() => {
              setAuthMode("forgot");
              setNotice("");
            }}
          >
            Esqueci minha senha
          </button>
        )}
        {notice && (
          <p role="alert" className="notice">
            {notice}
          </p>
        )}
        <p className="muted">
          Não use dados de clientes reais durante os testes.
        </p>
      </div>
    </div>
  );
}
