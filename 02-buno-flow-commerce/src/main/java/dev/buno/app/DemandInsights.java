package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant-scoped weekly demand insights.
 * Works independently of ML. Optional model is a reviewed, portable Ridge JSON artifact.
 */
@Service
public final class DemandInsights {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final boolean pilotEnabled;
  private final String modelFile;
  public DemandInsights(JdbcTemplate db, ObjectMapper json,
       @Value("${commerce.ml.pilot-enabled:false}") boolean pilotEnabled,
       @Value("${commerce.ml.model-file:}") String modelFile) {
    this.db = db; this.json = json; this.pilotEnabled = pilotEnabled; this.modelFile = modelFile;
  }

  public Map<String,Object> forecast(AuthService.Session caller, String sku) {
    if(sku == null || sku.isBlank() || sku.length()>80)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"SKU inválido");
    LocalDate currentMonday=LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
    LocalDate oldestMonday=currentMonday.minusWeeks(8);
    String sql = """
      SELECT e.occurred_at, i.quantity
        FROM order_events e
        JOIN orders o ON o.id=e.order_id AND o.store_id=e.store_id
        JOIN order_items i ON i.order_id=o.id
       WHERE e.store_id=? AND i.sku=? AND e.new_status='PAID'
         AND o.status <> 'CANCELED' AND e.occurred_at >= ?
      """;
    var rows=db.queryForList(sql,caller.storeId(),sku,oldestMonday.atStartOfDay().atOffset(ZoneOffset.UTC));
    Map<LocalDate,Integer> byWeek=new HashMap<>();
    for(var row: rows){
      var instant=((java.sql.Timestamp)row.get("occurred_at")).toInstant();
      LocalDate monday=instant.atZone(ZoneOffset.UTC).toLocalDate().with(DayOfWeek.MONDAY);
      byWeek.merge(monday,((Number)row.get("quantity")).intValue(),Integer::sum);
    }
    double[] previous=new double[8];
    int nonzeroWeeks=0,totalUnits=0;
    for(int i=0;i<8;i++){
      previous[i]=byWeek.getOrDefault(currentMonday.minusWeeks(i+1),0);
      if(previous[i]>0)nonzeroWeeks++;
      totalUnits+=(int) previous[i];
    }
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("sku",sku);
    out.put("weekStart",currentMonday.toString());
    out.put("unitsLast8FullWeeks",totalUnits);
    out.put("activeWeeksLast8",nonzeroWeeks);
    // Never make a confident projection for sparse, recent or nonrepresentative histories.
    if(nonzeroWeeks<6 || totalUnits<20){
      out.put("status","INSUFFICIENT_HISTORY");
      out.put("predictedUnits",null);
      out.put("method","NONE");
      out.put("message","São necessárias ao menos 6 semanas com vendas e 20 unidades registradas nas últimas 8 semanas.");
      return out;
    }

    double mean4=(previous[0]+previous[1]+previous[2]+previous[3])/4.0;
    double var4=0.0;
    for(int j=0;j<4;j++)var4+=Math.pow(previous[j]-mean4,2)/4.0;
    int currentWeek=currentMonday.get(WeekFields.ISO.weekOfWeekBasedYear());
    double[] x={previous[0], previous[1], mean4, Math.sqrt(var4),
        Math.sin(2*Math.PI*currentWeek/52.18),Math.cos(2*Math.PI*currentWeek/52.18)};
    double prediction=mean4;
    String method="FOUR_WEEK_AVERAGE";
    if(pilotEnabled && modelFile!=null && !modelFile.isBlank()){
      try{
        Path path=Path.of(modelFile);
        if(Files.isRegularFile(path) && Files.size(path)<500_000){
          JsonNode model=json.readTree(Files.readString(path));
          if("buno.demand.ridge.v1".equals(model.path("format").asText())
             && "research_candidate".equals(model.path("status").asText())
             && model.path("evaluation").path("eligible_for_internal_pilot").asBoolean(false)){
            JsonNode means=model.path("mean"),scales=model.path("scale"),coefficients=model.path("coefficients");
            if(means.size()==x.length && scales.size()==x.length && coefficients.size()==x.length){
              double v=model.path("intercept").asDouble();
              for(int j=0;j<x.length;j++){
                double scale=scales.get(j).asDouble();
                if(!Double.isFinite(scale)||scale==0)throw new IllegalArgumentException("invalid scale");
                v+=coefficients.get(j).asDouble()*(x[j]-means.get(j).asDouble())/scale;
              }
              prediction=Math.max(0.0,Math.expm1(Math.min(20.0,v)));
              method="RESEARCH_MODEL_INTERNAL_PILOT";
            }
          }
        }
      }catch(Exception ignored){
        method="FOUR_WEEK_AVERAGE";
        prediction=mean4;
      }
    }
    out.put("status","EXPERIMENTAL_ESTIMATE");
    out.put("predictedUnits",Math.round(prediction));
    out.put("method",method);
    out.put("message","Estimativa de unidades da semana corrente, sem garantia. A partir de pedidos marcados como pagos na loja.");
    out.put("modelLimitations","Modelo de pesquisa treinado em dados britânicos de 2010–2011; NÃO validado para lojistas brasileiros.");
    return out;
  }
}
