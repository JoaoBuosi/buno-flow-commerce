package dev.buno.app;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderImportService {
  private final JdbcTemplate db;
  private final OrderStore orders;
  public OrderImportService(JdbcTemplate db,OrderStore orders){this.db=db;this.orders=orders;}
  public record Imported(String externalKey,String orderId){}
  public record Result(int imported,List<Imported> orders){}
  private record CsvLine(String orderKey,String customer,OrderStore.Item item){}
  private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}

  @Transactional
  public Result importCsv(AuthService.Session user,MultipartFile file) {
    if(file==null || file.isEmpty() || file.getSize()>1_000_000) throw bad("CSV vazio ou maior que 1 MB");
    LinkedHashMap<String,List<CsvLine>> grouped=new LinkedHashMap<>();
    Set<String> uniqueSku=new HashSet<>();
    try(var reader=new InputStreamReader(file.getInputStream(),StandardCharsets.UTF_8);
        var parser=CSVFormat.DEFAULT.builder()
          .setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).get().parse(reader)){
      List<String> required=List.of("order_key","customer","sku","description","quantity","price");
      if(parser.getHeaderMap()==null || !parser.getHeaderMap().keySet().containsAll(required)) throw bad("Cabeçalho CSV inválido");
      int lines=0;
      for(CSVRecord r:parser){
        if(++lines>500) throw bad("Limite de 500 linhas por importação");
        String key=r.get("order_key").trim(),customer=r.get("customer").trim(),
          sku=r.get("sku").trim(),description=r.get("description").trim();
        if(key.isBlank()||key.length()>100||customer.isBlank()||customer.length()>120||
          sku.isBlank()||sku.length()>80||description.isBlank()||description.length()>200)
          throw bad("Campos inválidos na linha "+r.getRecordNumber());
        int quantity;BigDecimal price;
        try{quantity=Integer.parseInt(r.get("quantity").trim());price=new BigDecimal(r.get("price").trim());}
        catch(NumberFormatException e){throw bad("Número inválido na linha "+r.getRecordNumber());}
        if(quantity<=0||price.signum()<=0||price.scale()>2)throw bad("Quantidade/preço inválido na linha "+r.getRecordNumber());
        if(!uniqueSku.add(key+"\u0000"+sku))throw bad("SKU duplicado no pedido "+key);
        var item=new OrderStore.Item(sku,description,quantity,price);
        var list=grouped.computeIfAbsent(key,k->new ArrayList<>());
        if(!list.isEmpty()&&!list.get(0).customer().equals(customer))throw bad("Cliente inconsistente no pedido "+key);
        list.add(new CsvLine(key,customer,item));
      }
    }catch(ResponseStatusException e){throw e;}
     catch(Exception e){throw bad("CSV malformado ou leitura inválida");}
    if(grouped.isEmpty())throw bad("Arquivo sem linhas válidas");
    List<Imported> imported=new ArrayList<>();
    for(var entry:grouped.entrySet()){
      String key=entry.getKey();
      if(db.queryForObject("SELECT count(*) FROM order_imports WHERE store_id=? AND external_key=?",Integer.class,user.storeId(),key)>0)
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Pedido externo duplicado: "+key);
      var lines=entry.getValue();
      var data=new OrderStore.Create(lines.get(0).customer(),lines.stream().map(CsvLine::item).toList());
      Map<String,Object> created=orders.create(user,data);
      UUID orderId=(UUID)created.get("id");
      db.update("INSERT INTO order_imports(store_id,external_key,order_id) VALUES(?,?,?)",user.storeId(),key,orderId);
      imported.add(new Imported(key,orderId.toString()));
    }
    return new Result(imported.size(),imported);
  }
}
