import type { Session } from "../types";
const configured = String(import.meta.env.VITE_API_URL || "").trim();
const sameOrigin = configured === "same-origin";
export const apiReady =
  import.meta.env.DEV ||
  sameOrigin ||
  /^https:\/\/[^\s]+\/api\/v1\/?$/.test(configured);
const base = (
  sameOrigin
    ? window.location.origin + "/api/v1"
    : configured || (import.meta.env.DEV ? "http://localhost:8102/api/v1" : "")
).replace(/\/$/, "");
export type Api = ReturnType<typeof createApi>;
export function createApi(
  session: Session | null,
  expired: () => void = () => {},
) {
  async function request(path: string, options: RequestInit = {}) {
    if (!apiReady)
      throw new Error("Conexão indisponível. Tente novamente mais tarde.");
    const headers = new Headers(options.headers);
    if (session) {
      headers.set("Authorization", "Bearer " + session.accessToken);
      headers.set("X-Store-Id", session.storeId);
    }
    if (options.body && !(options.body instanceof FormData))
      headers.set("Content-Type", "application/json");
    let res: Response;
    try {
      res = await fetch(base + path, {
        ...options,
        headers,
        cache: "no-store",
      });
    } catch (e) {
      if ((e as Error).name === "AbortError") throw e;
      throw new Error(
        "Não foi possível conectar. Verifique sua conexão e tente novamente.",
      );
    }
    if (!res.ok) {
      if (res.status === 401 && session) expired();
      const data = await res.json().catch(() => ({}));
      throw new Error(
        data.detail ||
          data.message ||
          {
            401: "Sua sessão expirou. Entre novamente.",
            403: "Você não tem permissão para esta ação.",
            409: "Os dados mudaram. Atualize e tente novamente.",
          }[res.status] ||
          "Não foi possível concluir a operação. Tente novamente.",
      );
    }
    return res;
  }
  return {
    async get<T>(path: string, signal?: AbortSignal): Promise<T> {
      return (await request(path, { signal })).json();
    },
    async send<T = void>(
      path: string,
      method: string,
      body?: unknown,
      headers?: HeadersInit,
    ): Promise<T> {
      const r = await request(path, {
        method,
        headers,
        body:
          body === undefined
            ? undefined
            : body instanceof FormData
              ? body
              : JSON.stringify(body),
      });
      const text = await r.text();
      return text ? JSON.parse(text) : undefined;
    },
    async download(path: string) {
      return (await request(path)).blob();
    },
  };
}
export function downloadFile(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob),
    a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
