package dev.buno.app;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderExportService {
  private final JdbcTemplate db;
  public OrderExportService(JdbcTemplate db){this.db=db;}

  /** Returns only tenant-owned orders, with spreadsheet formula-injection protection. */
  public byte[] export(AuthService.Session caller) {
    var lines=db.queryForList("""
      SELECT o.id,o.customer_name,o.status,o.total,o.created_at,
             i.sku,i.description,i.quantity,i.unit_price,i.subtotal
        FROM orders o JOIN order_items i ON i.order_id=o.id
       WHERE o.store_id=?
       ORDER BY o.created_at DESC,o.id DESC,i.sku
       LIMIT 25001
      """, caller.storeId());
    if(lines.size()>25000)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Exportação limitada a 25 mil itens. Solicite exportação assistida.");
    try(var writer=new StringWriter();
        var csv=new CSVPrinter(writer,CSVFormat.DEFAULT.builder()
            .setHeader("order_id","customer","status","order_total","created_at","sku","description","quantity","unit_price","subtotal").get())) {
      for(Map<String,Object> r:lines){
        csv.printRecord(r.get("id"),safe(r.get("customer_name")),r.get("status"),r.get("total"),
          r.get("created_at"),safe(r.get("sku")),safe(r.get("description")),
          r.get("quantity"),r.get("unit_price"),r.get("subtotal"));
      }
      csv.flush();
      return writer.toString().getBytes(StandardCharsets.UTF_8);
    }catch(IOException e){throw new IllegalStateException("Erro ao exportar arquivo",e);}
  }
  static String safe(Object value){
    String text=String.valueOf(value==null?"":value);
    if(text.isEmpty())return text;
    // Spreadsheet apps may ignore whitespace, BOM or zero-width prefix before a formula.
    int offset=0;
    while(offset<text.length()){
      int cp=text.codePointAt(offset);
      if(!Character.isWhitespace(cp) && cp!=0xFEFF && cp!=0x200B)break;
      offset+=Character.charCount(cp);
    }
    if(offset==text.length())return text;
    char first=text.charAt(offset);
    return first=='='||first=='+'||first=='-'||first=='@'?"'"+text:text;
  }
}
