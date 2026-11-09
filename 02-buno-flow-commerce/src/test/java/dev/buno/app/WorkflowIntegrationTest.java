package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:buno_workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.listener.auto-startup=false",
        "buno.tenant-id=loja-teste",
        "buno.store-api-key=store-credential-for-tests-123",
        "buno.agent-api-key=agent-credential-for-tests-456",
        "buno.approver-api-key=approver-credential-for-tests-789",
        "buno.core-api-key=core-credential-for-tests-012"
})
@AutoConfigureMockMvc
class WorkflowIntegrationTest {
    private static final String STORE = "store-credential-for-tests-123";
    private static final String AGENT = "agent-credential-for-tests-456";
    private static final String APPROVER = "approver-credential-for-tests-789";
    private static final String CORE = "core-credential-for-tests-012";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean KafkaTemplate<String, Object> kafka;

    @Test
    void workflowRequiresHumanApprovalAndProducesAuditTrail() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());

        String orderResponse = mvc.perform(post("/api/orders")
                .header("X-Buno-Key", STORE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"cliente-001\",\"total\":129.90}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(orderResponse).get("id").asLong();
        assertThat(orderId).isPositive();

        String proposal = "{\"orderId\":" + orderId +
                ",\"desiredStatus\":\"REVIEWED\",\"reason\":\"Revisar pedido\"," +
                "\"idempotencyKey\":\"order-001-review\"}";

        mvc.perform(post("/api/v1/workflows/proposals")
                .header("X-Buno-Key", STORE)
                .contentType(MediaType.APPLICATION_JSON).content(proposal))
                .andExpect(status().isForbidden());

        String workflowResponse = mvc.perform(post("/api/v1/workflows/proposals")
                .header("X-Buno-Key", AGENT)
                .contentType(MediaType.APPLICATION_JSON).content(proposal))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        String workflowId = json.readTree(workflowResponse).get("id").asText();
        String path = "/api/v1/workflows/" + workflowId;

        mvc.perform(post(path + "/execute").header("X-Buno-Key", CORE))
                .andExpect(status().isConflict());
        mvc.perform(post(path + "/approve").header("X-Buno-Key", STORE))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/approve").header("X-Buno-Key", APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post(path + "/execute").header("X-Buno-Key", APPROVER))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/execute").header("X-Buno-Key", CORE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
        mvc.perform(post(path + "/execute").header("X-Buno-Key", CORE))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders/" + orderId).header("X-Buno-Key", STORE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));

        String replay = mvc.perform(post("/api/v1/workflows/proposals")
                .header("X-Buno-Key", AGENT)
                .contentType(MediaType.APPLICATION_JSON).content(proposal))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replay).get("id").asText()).isEqualTo(workflowId);

        mvc.perform(get(path + "/audit").header("X-Buno-Key", APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType").value("PROPOSED"))
                .andExpect(jsonPath("$[1].eventType").value("APPROVED"))
                .andExpect(jsonPath("$[2].eventType").value("EXECUTED"));
    }

    @Test
    void rejectedWorkflowCannotExecuteAndInputIsValidated() throws Exception {
        String order = mvc.perform(post("/api/orders").header("X-Buno-Key", STORE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"cliente-002\",\"total\":20.00}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(order).get("id").asLong();

        mvc.perform(post("/api/v1/workflows/proposals").header("X-Buno-Key", AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + id + ",\"desiredStatus\":\"PAID\"," +
                        "\"reason\":\"Nao permitido\",\"idempotencyKey\":\"invalid-001\"}"))
                .andExpect(status().isBadRequest());

        String created = mvc.perform(post("/api/v1/workflows/proposals").header("X-Buno-Key", AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + id + ",\"desiredStatus\":\"REVIEWED\"," +
                        "\"reason\":\"Revisar\",\"idempotencyKey\":\"reject-test-001\"}"))
                .andReturn().getResponse().getContentAsString();
        String path = "/api/v1/workflows/" + json.readTree(created).get("id").asText();
        mvc.perform(post(path + "/reject").header("X-Buno-Key", APPROVER))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/execute").header("X-Buno-Key", CORE))
                .andExpect(status().isConflict());
    }
}
