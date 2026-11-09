package dev.buno.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderStore {
  private final JdbcTemplate db;
  public OrderStore(JdbcTemplate db){this.db=db;}
  public record Item(String sku,String description,Integer quantity,BigDecimal price){}
  public record Create(String customer,List<Item> items){}
  public record ChangeStatus(String status){}
  public record Cancel(String reason){}
  private static ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
  private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido não encontrado");}
  private static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}

  @Transactional
  public Map<String,Object> create(AuthService.Session user,Create data){
    if(data==null || data.customer()==null || data.customer().isBlank() || data.customer().length()>120 ||
       data.items()==null || data.items().isEmpty() || data.items().size()>100) throw bad("Cliente ou itens inválidos");
    BigDecimal total=BigDecimal.ZERO;
    List<BigDecimal> subtotals=new ArrayList<>();
    for(Item item:data.items()){
      if(item==null || item.sku()==null || item.sku().isBlank() || item.sku().length()>80 ||
         item.description()==null || item.description().isBlank() || item.description().length()>200 ||
         item.quantity()==null || item.quantity()<1 || item.quantity()>100000 ||
         item.price()==null || item.price().signum()<=0 || item.price().scale()>2) throw bad("Item inválido");
      BigDecimal subtotal=money(item.price().multiply(BigDecimal.valueOf(item.quantity())));
      if(subtotal.compareTo(new BigDecimal("99999999999999999.99"))>0) throw bad("Valor excessivo");
      total=total.add(subtotal);subtotals.add(subtotal);
    }
    if(total.compareTo(new BigDecimal("99999999999999999.99"))>0) throw bad("Total excessivo");
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO orders(id,store_id,customer_name,status,total) VALUES(?,?,?,'CREATED',?)",
      id,user.storeId(),data.customer().trim(),total);
    for(int j=0;j<data.items().size();j++){
      Item item=data.items().get(j);
      db.update("INSERT INTO order_items(id,order_id,sku,description,quantity,unit_price,subtotal) VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID(),id,item.sku().trim(),item.description().trim(),item.quantity(),item.price(),subtotals.get(j));
    }
    event(user,id,"CREATED",null,"CREATED","Pedido criado");
    return detail(user,id);
  }
  public List<Map<String,Object>> list(AuthService.Session user,int page,int size,String status,String search){
    if(page<0 || page>100000 || size<1 || size>100) throw bad("Paginação inválida");
    String sql="SELECT id,customer_name,status,total,created_at,updated_at FROM orders WHERE store_id=?";
    List<Object> args=new ArrayList<>();args.add(user.storeId());
    if(status!=null && !status.isBlank()){checkStatus(status);sql+=" AND status=?";args.add(status);}
    if(search!=null&&!search.isBlank()){sql+=" AND lower(customer_name) LIKE ?";args.add("%"+search.toLowerCase(java.util.Locale.ROOT).replace("%","\\%").replace("_","\\_")+"%");sql=sql.replace(" LIKE ?"," LIKE ? ESCAPE '\\'");}
    sql+=" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?";args.add(size);args.add(page*size);
    return db.queryForList(sql,args.toArray());
  }
  /** Counts are calculated across the whole tenant, never merely the displayed page. */
  public Map<String,Object> dashboard(AuthService.Session user){
    return db.queryForMap("""
      SELECT count(*) AS order_count,
        count(*) FILTER (WHERE status NOT IN ('CANCELED','DELIVERED')) AS active_count,
        count(*) FILTER (WHERE status='DELIVERED') AS delivered_count,
        count(*) FILTER (WHERE status='CANCELED') AS canceled_count,
        coalesce(sum(total) FILTER (WHERE status<>'CANCELED'),0) AS order_value
      FROM orders WHERE store_id=?
      """,user.storeId());
  }
  public long count(AuthService.Session user,String status,String search){
    String sql="SELECT count(*) FROM orders WHERE store_id=?";
    List<Object> args=new ArrayList<>();args.add(user.storeId());
    if(status!=null&&!status.isBlank()){checkStatus(status);sql+=" AND status=?";args.add(status);}
    if(search!=null&&!search.isBlank()){
      sql+=" AND lower(customer_name) LIKE ? ESCAPE '\\'";
      args.add("%"+search.toLowerCase(java.util.Locale.ROOT).replace("%","\\%").replace("_","\\_")+"%");
    }
    return db.queryForObject(sql,Long.class,args.toArray());
  }

  public Map<String,Object> detail(AuthService.Session user,UUID id){
    var list=db.queryForList("SELECT id,customer_name,status,total,cancellation_reason,created_at,updated_at,version FROM orders WHERE id=? AND store_id=?",id,user.storeId());
    if(list.isEmpty()) throw notFound();
    Map<String,Object> result=new LinkedHashMap<>(list.get(0));
    result.put("items",db.queryForList("SELECT sku,description,quantity,unit_price,subtotal FROM order_items WHERE order_id=? ORDER BY sku",id));
    return result;
  }
  public List<Map<String,Object>> history(AuthService.Session user,UUID id){
    detail(user,id);
    return db.queryForList("SELECT actor_id,action,old_status,new_status,detail,occurred_at FROM order_events WHERE order_id=? AND store_id=? ORDER BY occurred_at DESC",id,user.storeId());
  }
  @Transactional
  public Map<String,Object> transition(AuthService.Session user,UUID id,String target,String reason){
    String old=db.query("SELECT status FROM orders WHERE id=? AND store_id=? FOR UPDATE",rs->rs.next()?rs.getString(1):null,id,user.storeId());
    if(old==null) throw notFound();
    if("CANCELED".equals(target)){
      if(reason==null || reason.isBlank() || reason.length()>500) throw bad("Motivo obrigatório (máx. 500 caracteres)");
      if("DELIVERED".equals(old)||"CANCELED".equals(old)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Pedido finalizado");
      db.update("UPDATE orders SET status='CANCELED',cancellation_reason=?,updated_at=now(),version=version+1 WHERE id=? AND store_id=?",reason.trim(),id,user.storeId());
      event(user,id,"CANCELED",old,target,reason.trim());
    }else{
      checkStatus(target);
      String next=switch(old){case "CREATED"->"PAID";case "PAID"->"PROCESSING";case "PROCESSING"->"SHIPPED";case "SHIPPED"->"DELIVERED";default->"";};
      if(!target.equals(next))throw new ResponseStatusException(HttpStatus.CONFLICT,"Transição inválida");
      db.update("UPDATE orders SET status=?,updated_at=now(),version=version+1 WHERE id=? AND store_id=?",target,id,user.storeId());
      event(user,id,"STATUS_CHANGED",old,target,null);
    }
    return detail(user,id);
  }
  private void event(AuthService.Session user,UUID order,String action,String previous,String next,String detail){
    db.update("INSERT INTO order_events(id,order_id,store_id,actor_id,action,old_status,new_status,detail) VALUES(?,?,?,?,?,?,?,?)",
      UUID.randomUUID(),order,user.storeId(),user.userId(),action,previous,next,detail);
  }
  private void checkStatus(String status){
    if(!List.of("CREATED","PAID","PROCESSING","SHIPPED","DELIVERED","CANCELED").contains(status))throw bad("Status desconhecido");
  }
}
