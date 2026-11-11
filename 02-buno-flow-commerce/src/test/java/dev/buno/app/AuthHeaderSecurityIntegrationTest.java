package dev.buno.app;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthHeaderSecurityIntegrationTest {
  @Autowired MockMvc mvc;

  @Test void overlyLargeOrInvalidTokensAreRejectedBeforeAnyDatabaseLookup() throws Exception {
    String store=UUID.randomUUID().toString();
    for (String token: new String[]{
      "z".repeat(64), "a".repeat(63), "a".repeat(65),
      "A".repeat(64), "a".repeat(8192)}) {
      mvc.perform(get("/api/v1/orders")
          .header("Authorization","Bearer "+token)
          .header("X-Store-Id",store))
        .andExpect(status().isUnauthorized());
    }
    mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+ "0".repeat(8000))
        .header("X-Store-Id",store))
      .andExpect(status().isNoContent());
  }
}
