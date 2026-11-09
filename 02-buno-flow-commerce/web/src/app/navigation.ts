import { useEffect, useState } from "react";
export const routes = [
  ["dashboard", "Visão geral", "Sua operação, em um só lugar."],
  ["orders", "Pedidos", "Organize cada etapa, do cadastro à entrega."],
  [
    "imports",
    "Importação e exportação",
    "Movimente seus pedidos com arquivos CSV.",
  ],
  ["history", "Histórico e auditoria", "Acompanhe o que mudou em cada pedido."],
  [
    "intelligence",
    "Inteligência comercial",
    "Entenda a demanda dos seus produtos.",
  ],
  ["settings", "Configurações", "Cuide da sua loja e do seu acesso."],
] as const;
export type Page = (typeof routes)[number][0];
function read() {
  const [path, query = ""] = location.hash.slice(1).split("?");
  return {
    page: routes.some((r) => "/" + r[0] === path)
      ? (path.slice(1) as Page)
      : ("dashboard" as Page),
    params: new URLSearchParams(query),
  };
}
export function useNavigation() {
  const [route, setRoute] = useState(read);
  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return {
    ...route,
    navigate: (page: Page, params: Record<string, string> = {}) => {
      const query = new URLSearchParams(params).toString();
      location.hash = "/" + page + (query ? "?" + query : "");
    },
  };
}
