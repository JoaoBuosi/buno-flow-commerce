package dev.buno.app;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centraliza as transições permitidas para impedir mudanças arbitrárias
 * no ciclo de vida dos pedidos.
 */
public final class OrderStatusPolicy {
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.RECEIVED));
        TRANSITIONS.put(OrderStatus.RECEIVED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED, OrderStatus.ON_HOLD));
        TRANSITIONS.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.ON_HOLD));
        TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(
                OrderStatus.PROCESSING, OrderStatus.CANCELLED, OrderStatus.ON_HOLD));
        TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(
                OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.ON_HOLD));
        TRANSITIONS.put(OrderStatus.ON_HOLD, EnumSet.of(
                OrderStatus.RECEIVED, OrderStatus.PAYMENT_PENDING,
                OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStatusPolicy() {}

    public static boolean canTransition(OrderStatus current, OrderStatus next) {
        if (current == null || next == null || current == next) {
            return false;
        }
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public static void requireTransition(OrderStatus current, OrderStatus next) {
        if (!canTransition(current, next)) {
            throw new IllegalArgumentException(
                    "Transição de status não permitida: " + current + " -> " + next);
        }
    }
}
