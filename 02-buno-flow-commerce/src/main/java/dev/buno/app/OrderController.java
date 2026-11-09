package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService service;
    private final OrderRepository repo;
    private final CoreWorkflowClient core;

    public OrderController(OrderService service, OrderRepository repo, CoreWorkflowClient core) {
        this.service = service;
        this.repo = repo;
        this.core = core;
    }

    public record CreateOrder(String customerId, BigDecimal total) {}
    public record MarkReviewed(String workflowId) {}

    @PostMapping("/orders")
    public Order create(@RequestAttribute("bunoTenantId") String tenant,
                        @RequestAttribute("bunoRole") String role,
                        @RequestBody CreateOrder request) {
        BunoServiceAuthFilter.requireRole(role, "STORE");
        return service.create(tenant, request.customerId(), request.total());
    }

    @GetMapping("/orders")
    public List<Order> all(@RequestAttribute("bunoTenantId") String tenant,
                           @RequestAttribute("bunoRole") String role) {
        BunoServiceAuthFilter.requireRole(role, "STORE");
        return repo.findAllByTenantIdOrderByIdDesc(tenant);
    }

    @GetMapping("/orders/{id}")
    public Order one(@PathVariable Long id,
                     @RequestAttribute("bunoTenantId") String tenant,
                     @RequestAttribute("bunoRole") String role) {
        BunoServiceAuthFilter.requireRole(role, "STORE");
        return lookup(id, tenant);
    }

    @GetMapping("/orders/{id}/workflows")
    public JsonNode workflows(@PathVariable Long id,
                              @RequestAttribute("bunoTenantId") String tenant,
                              @RequestAttribute("bunoRole") String role) {
        BunoServiceAuthFilter.requireRole(role, "STORE");
        lookup(id, tenant);
        return core.findByOrder(id);
    }

    @PostMapping("/internal/orders/{id}/mark-reviewed")
    public Order markReviewed(@PathVariable Long id,
                              @RequestAttribute("bunoTenantId") String tenant,
                              @RequestAttribute("bunoRole") String role,
                              @RequestBody MarkReviewed request) {
        BunoServiceAuthFilter.requireRole(role, "CORE");
        return service.markReviewed(tenant, id, request.workflowId());
    }

    private Order lookup(Long id, String tenant) {
        return repo.findByIdAndTenantId(id, tenant).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
    }
}
