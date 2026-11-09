package dev.buno.app;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RestController
@RequestMapping("/api/v1")
public class CommerceController {
  private final AuthService auth;
  private final OrderStore orders;
  private final OrderImportService imports;
  private final DemandInsights insights;
  private final StoreProfileService storeProfiles;
  private final OrderExportService exports;
  private final PasswordResetService resets;
  public CommerceController(AuthService auth,OrderStore orders,OrderImportService imports,DemandInsights insights,StoreProfileService storeProfiles,OrderExportService exports,PasswordResetService resets){this.auth=auth;this.orders=orders;this.imports=imports;this.insights=insights;this.storeProfiles=storeProfiles;this.exports=exports;this.resets=resets;}
  public record Register(String email,String password,String storeName){}
  public record Login(String email,String password){}
  @PostMapping("/auth/register")
  public AuthService.Token register(@RequestBody Register data){
    if(data==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return auth.register(data.email(),data.password(),data.storeName());
  }
  @PostMapping("/auth/login")
  public AuthService.Token login(@RequestBody Login data){
    if(data==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return auth.login(data.email(),data.password());
  }
  @PostMapping("/auth/reset/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestReset(@RequestBody PasswordResetService.ResetRequest body){
    if(body==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    resets.request(body.email());
  }
  @PostMapping("/auth/reset/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeReset(@RequestBody PasswordResetService.ResetComplete body){
    if(body==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    resets.complete(body.token(),body.newPassword());
  }
  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestHeader(value="Authorization",required=false)String token){auth.logout(token);}
  private AuthService.Session who(String token,String storeId){return auth.require(token,storeId);}
  public record ChangePassword(String oldPassword,String newPassword){}
  @PostMapping("/auth/change-password")
  public AuthService.Token changePassword(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@RequestBody ChangePassword body){
    if(body==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return auth.changePassword(who(token,store),body.oldPassword(),body.newPassword());
  }
  @GetMapping("/store")
  public Map<String,Object> storeProfile(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store){return storeProfiles.get(who(token,store));}
  @PatchMapping("/store")
  public Map<String,Object> renameStore(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@RequestBody StoreProfileService.Update body){return storeProfiles.rename(who(token,store),body==null?null:body.name());}
  @GetMapping("/me")
  public AuthService.Session me(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store){return who(token,store);}
  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> create(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@RequestBody OrderStore.Create body){return orders.create(who(token,store),body);}
  @PostMapping(value="/orders/import", consumes="multipart/form-data")
  public OrderImportService.Result importCsv(@RequestHeader("Authorization") String token,@RequestHeader("X-Store-Id") String store,@RequestParam("file") org.springframework.web.multipart.MultipartFile file){return imports.importCsv(who(token,store),file);}
  @GetMapping(value="/orders/export", produces="text/csv")
  public org.springframework.http.ResponseEntity<byte[]> exportOrders(
      @RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store){
    byte[] data=exports.export(who(token,store));
    return org.springframework.http.ResponseEntity.ok()
      .header("Content-Disposition","attachment; filename=flow-commerce-orders.csv")
      .header("Cache-Control","no-store")
      .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
      .body(data);
  }
  @GetMapping("/insights/demand")
  public Map<String,Object> skuDemand(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@RequestParam String sku){return insights.forecast(who(token,store),sku);}
  @GetMapping("/dashboard")
  public Map<String,Object> dashboard(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store){
    return orders.dashboard(who(token,store));
  }
  @GetMapping("/orders/count")
  public Map<String,Object> orderCount(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,
    @RequestParam(required=false)String status,@RequestParam(required=false)String search){
    return Map.of("total",orders.count(who(token,store),status,search));
  }
  @GetMapping("/orders")
  public List<Map<String,Object>> list(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,
    @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status,@RequestParam(required=false)String search){
    return orders.list(who(token,store),page,size,status,search);
  }
  @GetMapping("/orders/{id}")
  public Map<String,Object> detail(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@PathVariable UUID id){return orders.detail(who(token,store),id);}
  @GetMapping("/orders/{id}/history")
  public List<Map<String,Object>> history(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@PathVariable UUID id){return orders.history(who(token,store),id);}
  @PatchMapping("/orders/{id}/status")
  public Map<String,Object> change(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@PathVariable UUID id,@RequestBody OrderStore.ChangeStatus change){
    if(change==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return orders.transition(who(token,store),id,change.status(),null);
  }
  @PostMapping("/orders/{id}/cancel")
  public Map<String,Object> cancel(@RequestHeader("Authorization")String token,@RequestHeader("X-Store-Id")String store,@PathVariable UUID id,@RequestBody OrderStore.Cancel cancel){
    if(cancel==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return orders.transition(who(token,store),id,"CANCELED",cancel.reason());
  }
}

@Configuration
class CommerceCors {
  @Bean
  WebMvcConfigurer cors(@Value("${commerce.cors-origin}") String origin){
    return new WebMvcConfigurer(){
      @Override public void addCorsMappings(CorsRegistry r){
        r.addMapping("/api/v1/**").allowedOrigins(origin).allowedMethods("GET","POST","PATCH","OPTIONS").allowedHeaders("Authorization","Content-Type","X-Store-Id");
      }
    };
  }
}
