export const pages=["dashboard","orders","imports","history","intelligence","settings"] as const;
export type Page=(typeof pages)[number];
export const navigation:ReadonlyArray<{page:Page;label:string;icon:string}>= [
 {page:"dashboard",label:"Visão geral",icon:"◫"},
 {page:"orders",label:"Pedidos",icon:"▤"},
 {page:"imports",label:"Importar e exportar",icon:"⇅"},
 {page:"history",label:"Histórico e auditoria",icon:"◷"},
 {page:"intelligence",label:"Inteligência comercial",icon:"◇"},
 {page:"settings",label:"Configurações",icon:"⚙"}
];
export const pathFor=(page:Page)=>"/"+page;
export const pageFromPath=(path:string):Page=>{
 const found=pages.find(page=>path==="/"+page);
 return found||"dashboard";
};
export const pageTitles:Record<Page,{title:string;description:string}>={
 dashboard:{title:"Seus pedidos. Sob controle.",description:"Visão real da sua operação comercial."},
 orders:{title:"Gestão de pedidos.",description:"Cadastre, acompanhe e atualize pedidos da sua loja."},
 imports:{title:"Importação e exportação.",description:"Gerencie arquivos CSV com validação e rastreabilidade."},
 history:{title:"Histórico e auditoria.",description:"Consulte os eventos de cada pedido individualmente."},
 intelligence:{title:"Inteligência comercial.",description:"Analise a demanda por SKU utilizando dados da sua loja."},
 settings:{title:"Configurações da loja.",description:"Administre seu perfil, loja e segurança."}
};

