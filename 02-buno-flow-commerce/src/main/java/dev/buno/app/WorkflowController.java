package dev.buno.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    public record Proposal(Long orderId, String desiredStatus, String reason, String idempotencyKey) {}

    @PostMapping("/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRequest propose(@RequestAttribute("bunoTenantId") String tenant,
                                   @RequestAttribute("bunoRole") String role,
                                   @RequestBody Proposal proposal) {
        BunoApiKeyFilter.requireRole(role, "AGENT");
        return service.propose(tenant, proposal.orderId(), proposal.desiredStatus(),
                proposal.reason(), proposal.idempotencyKey());
    }

    @GetMapping
    public List<WorkflowRequest> list(@RequestAttribute("bunoTenantId") String tenant,
                                      @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "STORE", "APPROVER");
        return service.list(tenant);
    }

    @PostMapping("/{id}/approve")
    public WorkflowRequest approve(@PathVariable UUID id,
                                   @RequestAttribute("bunoTenantId") String tenant,
                                   @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "APPROVER");
        return service.approve(tenant, id);
    }

    @PostMapping("/{id}/reject")
    public WorkflowRequest reject(@PathVariable UUID id,
                                  @RequestAttribute("bunoTenantId") String tenant,
                                  @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "APPROVER");
        return service.reject(tenant, id);
    }

    @PostMapping("/{id}/execute")
    public WorkflowRequest execute(@PathVariable UUID id,
                                   @RequestAttribute("bunoTenantId") String tenant,
                                   @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "CORE");
        return service.execute(tenant, id);
    }

    @GetMapping("/{id}/audit")
    public List<WorkflowAuditEvent> audit(@PathVariable UUID id,
                                          @RequestAttribute("bunoTenantId") String tenant,
                                          @RequestAttribute("bunoRole") String role) {
        BunoApiKeyFilter.requireRole(role, "STORE", "APPROVER");
        return service.audit(tenant, id);
    }
}
