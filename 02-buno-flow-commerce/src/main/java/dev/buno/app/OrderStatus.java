package dev.buno.app;

/**
 * Estados oficiais do ciclo de vida do pedido.
 * CREATED permanece para leitura de registros legados.
 */
public enum OrderStatus {
    CREATED,
    RECEIVED,
    PAYMENT_PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    ON_HOLD
}
