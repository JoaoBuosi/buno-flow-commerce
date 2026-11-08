package dev.buno.app;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String customerId;

    @Column(precision = 19, scale = 2)
    public BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public OrderStatus status;

    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    protected Order() {}

    public Order(String customerId, BigDecimal total) {
        this.customerId = customerId;
        this.total = total;
        this.status = OrderStatus.RECEIVED;
    }
}
