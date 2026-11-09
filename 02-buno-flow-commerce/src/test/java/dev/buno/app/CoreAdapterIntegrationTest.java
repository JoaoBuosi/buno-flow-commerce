package dev.buno.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:flow_adapter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.listener.auto-startup=false",
    "buno.tenant-id=loja-demo",
    "buno.store-api-key=store-secret-01234567890123456789",
    "buno.core-api-key=core-secret-012345678901234567890"
})
@AutoConfigureMockMvc
class CoreAdapterIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean KafkaTemplate<String, Object> kafka;
    @MockitoBean CoreWorkflowClient core;

    static final String STORE = "store-secret-01234567890123456789";
    static final String EXECUTOR = "core-secret-012345678901234567890";

    @Test
    void callbacksAreScopedAndOnlyCoreCanMarkReviewed() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        String response = mvc.perform(post("/api/orders")
            .header("X-Buno-Key", STORE).contentType(MediaType.APPLICATION_JSON)
            .content("{\"customerId\":\"cliente-a\",\"total\":129.90}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(response).get("id").asLong();
        String flowId = UUID.randomUUID().toString();
        String payload = "{\"workflowId\":\"" + flowId + "\"}";
        String callback = "/api/internal/orders/" + id + "/mark-reviewed";

        mvc.perform(post(callback).header("X-Buno-Key", STORE)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isForbidden());

        mvc.perform(post(callback).header("X-Buno-Key", EXECUTOR)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEWED"))
            .andExpect(jsonPath("$.lastWorkflowId").value(flowId));

        // Retry com mesmo workflow id e idempotente.
        mvc.perform(post(callback).header("X-Buno-Key", EXECUTOR)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk());

        // Workflow diferente nao pode sobrescrever operacao concluida.
        mvc.perform(post(callback).header("X-Buno-Key", EXECUTOR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"workflowId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isConflict());

        mvc.perform(get("/api/orders/" + id).header("X-Buno-Key", STORE))
            .andExpect(jsonPath("$.status").value("REVIEWED"));

        // A consulta deve passar por CoreWorkflowClient; o Core armazena workflows.
        when(core.findByOrder(anyLong())).thenReturn(mapper.readTree("[]"));
        mvc.perform(get("/api/orders/" + id + "/workflows").header("X-Buno-Key", STORE))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
        mvc.perform(get("/api/orders/" + id + "/workflows").header("X-Buno-Key", EXECUTOR))
            .andExpect(status().isForbidden());
    }
}
