import { useEffect, useState, type FormEvent } from "react";
import type { Api } from "../services/api";
import type { Profile, Session, Store } from "../types";
import { useResource } from "../hooks/useResource";
import { date, Feedback } from "../components/ui";
export function SettingsPage({
  api,
  onSession,
  notify,
  logout,
  storeChanged,
}: {
  api: Api;
  onSession: (s: Session) => void;
  notify: (s: string) => void;
  logout: () => void;
  storeChanged: () => void;
}) {
  const state = useResource(
    async (signal) => {
      const [profile, store] = await Promise.all([
        api.get<Profile>("/me", signal),
        api.get<Store>("/store", signal),
      ]);
      return { profile, store };
    },
    [api],
  );
  const [name, setName] = useState(""),
    [oldPassword, setOld] = useState(""),
    [newPassword, setNew] = useState(""),
    [busy, setBusy] = useState(""),
    [error, setError] = useState("");
  useEffect(() => {
    if (state.data) setName(state.data.store.name);
  }, [state.data]);
  async function rename(e: FormEvent) {
    e.preventDefault();
    setBusy("store");
    setError("");
    try {
      await api.send("/store", "PATCH", { name: name.trim() });
      notify("Dados da loja atualizados");
      state.reload();
      storeChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  async function password(e: FormEvent) {
    e.preventDefault();
    setBusy("password");
    setError("");
    try {
      const session = await api.send<Session>("/auth/change-password", "POST", {
        oldPassword,
        newPassword,
      });
      setOld("");
      setNew("");
      onSession(session);
      notify("Senha alterada e sessões antigas revogadas");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  return (
    <>
      <Feedback {...state} retry={state.reload} />
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {state.data && (
        <div className="settingsGrid">
          <section className="panel">
            <span className="eyebrow">PERFIL</span>
            <h2>Sua conta</h2>
            <dl>
              <dt>E-mail</dt>
              <dd>{state.data.profile.email}</dd>
              <dt>Permissão</dt>
              <dd>
                {state.data.profile.role === "OWNER"
                  ? "Proprietário"
                  : state.data.profile.role}
              </dd>
            </dl>
            <p className="muted">
              Os dados de identificação são somente para consulta.
            </p>
            <button className="secondary" onClick={logout}>
              Encerrar sessão
            </button>
          </section>
          <section className="panel">
            <span className="eyebrow">LOJA</span>
            <h2>Dados da loja</h2>
            <p className="muted breakWord">ID: {state.data.store.id}</p>
            <form className="stack" onSubmit={rename}>
              <label>
                Nome da loja
                <input
                  required
                  maxLength={120}
                  disabled={state.data.profile.role !== "OWNER" || !!busy}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </label>
              <button
                className="primary"
                disabled={
                  state.data.profile.role !== "OWNER" ||
                  !!busy ||
                  !name.trim() ||
                  name.trim() === state.data.store.name
                }
              >
                {busy === "store" ? "Salvando…" : "Salvar dados"}
              </button>
            </form>
            {state.data.profile.role !== "OWNER" && (
              <p className="muted">
                Somente o proprietário pode alterar o nome da loja.
              </p>
            )}
            <p className="muted">
              Criada em {date(state.data.store.created_at)}
            </p>
          </section>
          <section className="panel securityPanel">
            <span className="eyebrow">SEGURANÇA</span>
            <h2>Alterar senha</h2>
            <p className="muted">
              Ao salvar, todas as sessões anteriores são revogadas. Seu acesso
              atual será renovado.
            </p>
            <form className="stack" onSubmit={password}>
              <div className="columns">
                <label>
                  Senha atual
                  <input
                    required
                    type="password"
                    autoComplete="current-password"
                    value={oldPassword}
                    onChange={(e) => setOld(e.target.value)}
                  />
                </label>
                <label>
                  Nova senha
                  <input
                    required
                    type="password"
                    minLength={12}
                    maxLength={72}
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(e) => setNew(e.target.value)}
                  />
                </label>
              </div>
              <small className="muted">Use entre 12 e 72 caracteres.</small>
              <div>
                <button className="primary" disabled={!!busy}>
                  {busy === "password" ? "Alterando…" : "Alterar senha"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </>
  );
}
