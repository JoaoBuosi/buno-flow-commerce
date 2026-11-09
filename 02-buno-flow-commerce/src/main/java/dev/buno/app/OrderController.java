package dev.buno.app;

import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final OrderRepository repo;

    public OrderController(OrderService service, OrderRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    public record CreateOrder(String customerId, BigDecimal total) {}

    @PostMapping
    public Order create(@RequestAttribute("bunoTenantId") String tenantId,
                        @RequestAttribute("bunoRole") String role,
                        @RequestBody CreateOrder request) {
        BunoApiKeyFilter.requireRole(role, "STORE");
        return service.create(tenantId, request.customerId(), request.total());
    }

    @GetMapping
    public List<Order> all(@RequestAttribute("bunoTenantId") String tenantId,
                           @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "STORE", "APPROVER");
        return repo.findAllByTenantIdOrderByIdDesc(tenantId);
    }

    @GetMapping("/{id}")
    public Order one(@PathVariable Long id,
                     @RequestAttribute("bunoTenantId") String tenantId,
                     @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "STORE", "APPROVER");
        return repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
    }
}
