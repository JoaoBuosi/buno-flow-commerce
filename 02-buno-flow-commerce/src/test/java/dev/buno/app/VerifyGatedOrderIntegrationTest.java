package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

@SpringBootTest(properties={
  "commerce.verify.required=true",
  "commerce.verify.signing-secret=integration-only-verify-secret-12345678"
})
@AutoConfigureMockMvc
class VerifyGatedOrderIntegrationTest {
  private static final String SECRET="integration-only-verify-secret-12345678";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  record Identity(String token,String store) {}

  private Identity user() throws Exception {
    MvcResult r=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of("email","verify-"+UUID.randomUUID()+"@example.test",
        "password","safe-password-2026","storeName","Test")))).andExpect(status().isOk()).andReturn();
    JsonNode n=mapper.readTree(r.getResponse().getContentAsString());
    return new Identity(n.get("accessToken").asText(),n.get("storeId").asText());
  }
  private JsonNode create(Identity u) throws Exception {
    MvcResult r=mvc.perform(post("/api/v1/orders").header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store()).contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of("customer","Test",
        "items",new Object[]{Map.of("sku","s1","description","Item","quantity",1,"price",10.0)}))))
      .andExpect(status().isCreated()).andReturn();
    return mapper.readTree(r.getResponse().getContentAsString());
  }
  @Test void noAgentCanBypassEnabledGateUsingOrdinarySession() throws Exception {
    var u=user();var order=create(u);String id=order.get("id").asText();
    String endpoint="/api/v1/orders/"+id+"/status";
    mvc.perform(patch(endpoint).header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store()).contentType(MediaType.APPLICATION_JSON)
      .content("{\"status\":\"PAID\"}")).andExpect(status().isOk());
    mvc.perform(patch(endpoint).header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store()).contentType(MediaType.APPLICATION_JSON)
      .content("{\"status\":\"PROCESSING\"}")).andExpect(status().isForbidden());
    var read=mvc.perform(get("/api/v1/orders/"+id).header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store())).andExpect(status().isOk()).andReturn();
    long version=mapper.readTree(read.getResponse().getContentAsString()).get("version").asLong();
    String op="op_"+UUID.randomUUID().toString().replace("-","");
    long expires=Instant.now().getEpochSecond()+60;
    String signature=VerifiedCommandGuard.sign(SECRET,VerifiedCommandGuard.message(
      UUID.fromString(u.store()),UUID.fromString(id),op,expires,version));
    var valid=patch(endpoint).header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store()).header("X-Buno-Verify-Operation-Id",op)
      .header("X-Buno-Verify-Expires",Long.toString(expires))
      .header("X-Buno-Verify-Expected-Version",Long.toString(version))
      .header("X-Buno-Verify-Signature",signature)
      .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PROCESSING\"}");
    mvc.perform(valid).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
    mvc.perform(patch(endpoint).header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store()).header("X-Buno-Verify-Operation-Id",op)
      .header("X-Buno-Verify-Expires",Long.toString(expires))
      .header("X-Buno-Verify-Expected-Version",Long.toString(version))
      .header("X-Buno-Verify-Signature",signature)
      .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PROCESSING\"}"))
      .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/orders/"+id+"/history").header("Authorization","Bearer "+u.token())
      .header("X-Store-Id",u.store()))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].operation_id").value(op.substring(3,11)+"-"+op.substring(11,15)+"-"+op.substring(15,19)+"-"+op.substring(19,23)+"-"+op.substring(23)));
  }
}
