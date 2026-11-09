package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class CommerceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  record Identity(String token,String store) {}

  private Identity register() throws Exception {
    String email="test-"+UUID.randomUUID()+"@example.test";
    MvcResult result=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of("email",email,"password","safe-password-2026","storeName","Loja Teste"))))
      .andExpect(status().isOk()).andReturn();
    JsonNode data=json.readTree(result.getResponse().getContentAsString());
    return new Identity(data.get("accessToken").asText(),data.get("storeId").asText());
  }
  private String create(Identity user) throws Exception {
    String data=json.writeValueAsString(Map.of("customer","Cliente teste","items",new Object[]{
      Map.of("sku","SKU-1","description","Produto A","quantity",2,"price",12.40),
      Map.of("sku","SKU-2","description","Produto B","quantity",1,"price",5.10)
    }));
    MvcResult result=mvc.perform(post("/api/v1/orders").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store()).contentType(MediaType.APPLICATION_JSON).content(data))
      .andExpect(status().isCreated()).andReturn();
    JsonNode order=json.readTree(result.getResponse().getContentAsString());
    assertEquals(0,order.get("total").decimalValue().compareTo(new java.math.BigDecimal("29.90")));
    return order.get("id").asText();
  }

  @Test void ordersArePersistedAndStrictlyTenantScoped() throws Exception {
    Identity a=register(),b=register();
    String id=create(a);
    mvc.perform(get("/api/v1/orders/"+id).header("Authorization","Bearer "+a.token())
      .header("X-Store-Id",a.store())).andExpect(status().isOk());
    mvc.perform(get("/api/v1/orders/"+id).header("Authorization","Bearer "+b.token())
      .header("X-Store-Id",b.store())).andExpect(status().isNotFound());
    mvc.perform(patch("/api/v1/orders/"+id+"/status").header("Authorization","Bearer "+b.token())
      .header("X-Store-Id",b.store()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
      .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+b.token())
      .header("X-Store-Id",b.store())).andExpect(status().isOk())
      .andExpect(content().json("[]"));
    mvc.perform(get("/api/v1/orders/"+id).header("Authorization","Bearer "+a.token())
      .header("X-Store-Id",b.store())).andExpect(status().isUnauthorized());
  }

  @Test void lifecycleRejectsInvalidTransitionsAndRecordsEvents() throws Exception {
    Identity user=register();String id=create(user);
    mvc.perform(patch("/api/v1/orders/"+id+"/status").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
      .andExpect(status().isConflict());
    mvc.perform(patch("/api/v1/orders/"+id+"/status").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
      .andExpect(status().isOk());
    mvc.perform(post("/api/v1/orders/"+id+"/cancel").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
      .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/orders/"+id+"/cancel").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cliente solicitou\"}"))
      .andExpect(status().isOk());
    mvc.perform(get("/api/v1/orders/"+id+"/history").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
  }


  @Test void csvImportIsAtomicAndRejectsDuplicates() throws Exception {
    Identity user=register();
    String csv="order_key,customer,sku,description,quantity,price\nEXT-11,Ana,SKU-A,Caneca,2,10.50\nEXT-11,Ana,SKU-B,Copo,1,5.00\n";
    var upload=new org.springframework.mock.web.MockMultipartFile("file","orders.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(multipart("/api/v1/orders/import").file(upload).header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store())).andExpect(status().isOk())
      .andExpect(jsonPath("$.imported").value(1));
    mvc.perform(multipart("/api/v1/orders/import").file(upload).header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store())).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+user.token())
      .header("X-Store-Id",user.store())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  }

  @Test void forecastingIsScopedAndDoesNotInventSparsePredictions() throws Exception {
    Identity storeA=register(),storeB=register();
    String aId=create(storeA);
    mvc.perform(patch("/api/v1/orders/"+aId+"/status")
      .header("Authorization","Bearer "+storeA.token())
      .header("X-Store-Id",storeA.store()).contentType(MediaType.APPLICATION_JSON)
      .content("{\"status\":\"PAID\"}")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/insights/demand").param("sku","SKU-1")
      .header("Authorization","Bearer "+storeA.token())
      .header("X-Store-Id",storeA.store()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_HISTORY"))
      .andExpect(jsonPath("$.predictedUnits").doesNotExist());
    mvc.perform(get("/api/v1/insights/demand").param("sku","SKU-1")
      .header("Authorization","Bearer "+storeB.token())
      .header("X-Store-Id",storeB.store()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.unitsLast8FullWeeks").value(0));
    mvc.perform(get("/api/v1/insights/demand").param("sku","SKU-1")
      .header("Authorization","Bearer "+storeA.token())
      .header("X-Store-Id",storeB.store())).andExpect(status().isUnauthorized());
  }

  @Test void passwordChangeRevokesOldTokenAndIsolatesStores() throws Exception {
    Identity owner=register(),other=register();
    mvc.perform(get("/api/v1/store")
      .header("Authorization","Bearer "+owner.token()).header("X-Store-Id",owner.store()))
      .andExpect(status().isOk());
    mvc.perform(patch("/api/v1/store")
      .header("Authorization","Bearer "+owner.token()).header("X-Store-Id",owner.store())
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of("name","Loja atualizada"))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Loja atualizada"));
    mvc.perform(get("/api/v1/store")
      .header("Authorization","Bearer "+owner.token()).header("X-Store-Id",other.store()))
      .andExpect(status().isUnauthorized());
    MvcResult changed=mvc.perform(post("/api/v1/auth/change-password")
      .header("Authorization","Bearer "+owner.token()).header("X-Store-Id",owner.store())
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of("oldPassword","safe-password-2026",
         "newPassword","safe-new-password-2026"))))
      .andExpect(status().isOk()).andReturn();
    assertTrue(json.readTree(changed.getResponse().getContentAsString()).has("accessToken"));
    mvc.perform(get("/api/v1/store")
      .header("Authorization","Bearer "+owner.token()).header("X-Store-Id",owner.store()))
      .andExpect(status().isUnauthorized());
  }
  @Test void orderCsvExportCannotReadAnotherStore() throws Exception {
    Identity a=register(),b=register();
    create(a);
    var exportA=mvc.perform(get("/api/v1/orders/export")
      .header("Authorization","Bearer "+a.token())
      .header("X-Store-Id",a.store()))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var exportB=mvc.perform(get("/api/v1/orders/export")
      .header("Authorization","Bearer "+b.token())
      .header("X-Store-Id",b.store()))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(exportA.contains("SKU-1"));
    assertFalse(exportB.contains("SKU-1"));
    assertEquals("'=1+1",OrderExportService.safe("=1+1"));
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate verificationDb;

  @Test void tablesLiveOutsidePublicSchema() throws Exception {
    Integer privateOrders=verificationDb.queryForObject(
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='flow_commerce' AND table_name='orders'",Integer.class);
    Integer publicOrders=verificationDb.queryForObject(
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='orders'",Integer.class);
    assertEquals(1,privateOrders);
    assertEquals(0,publicOrders);
  }

  @Test void unauthenticatedRequestsAreRejected() throws Exception {
    mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }
}
