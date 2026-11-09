package dev.buno.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Staging-only launch guard. A private frontend does not protect an independently
 * exposed API: invitation checks and tenant auth must also hold server-side.
 */
@SpringBootTest(properties={
  "commerce.pilot-only=true",
  "commerce.pilot-invite-code=ci-invite-only",
  "commerce.cors-origin=https://flow-commerce-staging.example.test"
})
@AutoConfigureMockMvc
class PilotGateIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String registrationBody() throws Exception {
    return json.writeValueAsString(Map.of(
      "email","pilot-"+UUID.randomUUID()+"@example.test",
      "password","long-and-safe-password-2026",
      "storeName","Loja privada QA"));
  }

  @Test void unknownAndMissingInvitationCannotRegister() throws Exception {
    mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON).content(registrationBody()))
      .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Pilot-Code","wrong-invitation")
        .contentType(MediaType.APPLICATION_JSON).content(registrationBody()))
      .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Pilot-Code","ci-invite-only")
        .contentType(MediaType.APPLICATION_JSON).content(registrationBody()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andExpect(jsonPath("$.storeId").isNotEmpty());
  }

  @Test void allBusinessDataRemainsProtectedWithoutSession() throws Exception {
    for(String path:new String[]{"/api/v1/orders","/api/v1/orders/export",
        "/api/v1/dashboard","/api/v1/store","/api/v1/orders/count",
        "/api/v1/insights/demand"}) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized())
        .andExpect(header().string("Cache-Control","no-store"))
        .andExpect(header().string("X-Content-Type-Options","nosniff"))
        .andExpect(header().string("X-Frame-Options","DENY"));
    }
  }

  @Test void corsRejectsCrossOriginAndAllowsOnlyPrivateFrontend() throws Exception {
    mvc.perform(options("/api/v1/orders")
        .header("Origin","https://unknown.example.test")
        .header("Access-Control-Request-Method","GET")
        .header("Access-Control-Request-Headers","authorization,x-store-id"))
      .andExpect(status().isForbidden());
    mvc.perform(options("/api/v1/orders")
        .header("Origin","https://flow-commerce-staging.example.test")
        .header("Access-Control-Request-Method","GET")
        .header("Access-Control-Request-Headers","authorization,x-store-id"))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin",
        "https://flow-commerce-staging.example.test"));
  }
}
