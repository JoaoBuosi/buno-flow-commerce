package dev.buno.app;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Map;
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
                || total == null || total.signum() <= 0 || total.scale() > 2
                || total.precision() > 19) {
            throw new IllegalArgumentException("customerId valido e total positivo com ate 2 casas sao obrigatorios");
        }

        Order order = repo.save(new Order(tenantId, customer.trim(), total));
        // Contrato inicial para Agent Platform. Entrega duravel via outbox sera o proximo incremento.
        kafka.send("orders.created", String.valueOf(order.id), Map.of(
                "eventId", UUID.randomUUID().toString(),
                "type", "flow.order.created.v1",
                "tenantId", tenantId,
                "orderId", order.id,
                "customerId", customer.trim(),
                "total", total.toPlainString()));
        return order;
    }
}
