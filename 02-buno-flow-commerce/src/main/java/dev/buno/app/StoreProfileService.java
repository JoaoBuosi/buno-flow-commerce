package dev.buno.app;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class StoreProfileService {
  private final JdbcTemplate db;
  public StoreProfileService(JdbcTemplate db){this.db=db;}
  public record Update(String name){}
  public Map<String,Object> get(AuthService.Session caller){
    return db.queryForMap("SELECT s.id,s.name,s.created_at FROM stores s JOIN memberships m ON m.store_id=s.id WHERE m.user_id=? AND m.store_id=?",
      caller.userId(),caller.storeId());
  }
  @Transactional
  public Map<String,Object> rename(AuthService.Session caller,String name){
    if(!"OWNER".equals(caller.role()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente o proprietário pode alterar a loja");
    if(name==null || name.isBlank() || name.length()>120)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nome da loja inválido");
    int updated=db.update("UPDATE stores SET name=? WHERE id=? AND EXISTS(SELECT 1 FROM memberships WHERE user_id=? AND store_id=? AND role='OWNER')",
      name.trim(),caller.storeId(),caller.userId(),caller.storeId());
    if(updated!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return get(caller);
  }
}
