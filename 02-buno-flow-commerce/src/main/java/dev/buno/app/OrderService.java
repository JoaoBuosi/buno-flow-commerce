package dev.buno.app;

import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    public OrderService(OrderRepository repo, KafkaTemplate<String, Object> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @Transactional
    public Order create(String tenantId, String customer, BigDecimal total) {
        if (customer == null || customer.isBlank() || customer.length() > 120
            || total == null || total.signum() <= 0 || total.scale() > 2 || total.precision() > 19) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId e total valido sao obrigatorios");
        }
        Order order = repo.save(new Order(tenantId, customer.trim(), total));
        // Publicacao ainda sem outbox transacional: somente laboratorio, nunca fluxo financeiro.
        kafka.send("orders.created", String.valueOf(order.id), Map.of(
            "eventId", UUID.randomUUID().toString(),
            "type", "flow.order.created.v1",
            "tenantId", tenantId,
            "orderId", order.id,
            "customerId", customer.trim(),
            "total", total.toPlainString()));
        return order;
    }

    @Transactional
    public Order markReviewed(String tenantId, Long id, String workflowId) {
        try {
            UUID.fromString(workflowId);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowId UUID obrigatorio");
        }
        Order order = repo.findScopedForUpdate(id, tenantId).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
        if ("REVIEWED".equals(order.status)) {
            if (Objects.equals(order.lastWorkflowId, workflowId)) return order;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido ja revisado por outro workflow");
        }
        if (!"CREATED".equals(order.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transicao nao permitida");
        }
        order.status = "REVIEWED";
        order.lastWorkflowId = workflowId;
        return order;
    }
}
