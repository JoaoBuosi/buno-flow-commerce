package dev.buno.app;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_tenant", columnList = "tenant_id"))
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    public String tenantId;

    @Column(nullable = false, length = 120)
    public String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal total;

    @Column(nullable = false, length = 30)
    public String status;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    protected Order() {}

    public Order(String tenantId, String customerId, BigDecimal total) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.total = total;
        this.status = "CREATED";
    }
}
