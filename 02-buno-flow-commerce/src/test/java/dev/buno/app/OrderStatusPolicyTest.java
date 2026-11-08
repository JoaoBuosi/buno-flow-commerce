package dev.buno.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusPolicyTest {

    @Test
    void shouldAllowNormalOrderLifecycle() {
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.RECEIVED, OrderStatus.CONFIRMED));
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.CONFIRMED, OrderStatus.PROCESSING));
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED));
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    void shouldAllowCancellationBeforeShipment() {
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.RECEIVED, OrderStatus.CANCELLED));
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        assertTrue(OrderStatusPolicy.canTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
    }

    @Test
    void shouldRejectSkippingAndReversingLifecycleSteps() {
        assertFalse(OrderStatusPolicy.canTransition(OrderStatus.RECEIVED, OrderStatus.SHIPPED));
        assertFalse(OrderStatusPolicy.canTransition(OrderStatus.PROCESSING, OrderStatus.CONFIRMED));
        assertFalse(OrderStatusPolicy.canTransition(OrderStatus.DELIVERED, OrderStatus.PROCESSING));
    }

    @Test
    void shouldRejectSameOrNullStatus() {
        assertFalse(OrderStatusPolicy.canTransition(OrderStatus.RECEIVED, OrderStatus.RECEIVED));
        assertFalse(OrderStatusPolicy.canTransition(null, OrderStatus.CONFIRMED));
        assertFalse(OrderStatusPolicy.canTransition(OrderStatus.RECEIVED, null));
    }

    @Test
    void shouldRejectInvalidTransitionWithUsefulMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OrderStatusPolicy.requireTransition(
                        OrderStatus.RECEIVED, OrderStatus.DELIVERED));

        assertTrue(exception.getMessage().contains("RECEIVED -> DELIVERED"));
    }
}
