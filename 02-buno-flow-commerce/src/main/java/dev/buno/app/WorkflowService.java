package dev.buno.app;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkflowService {
    private final WorkflowRepository workflows;
    private final WorkflowAuditRepository audit;
    private final OrderRepository orders;

    public WorkflowService(WorkflowRepository workflows, WorkflowAuditRepository audit,
                           OrderRepository orders) {
        this.workflows = workflows;
        this.audit = audit;
        this.orders = orders;
    }

    @Transactional
    public WorkflowRequest propose(String tenant, Long orderId, String desiredStatus,
                                   String reason, String idempotencyKey) {
        if (orderId == null || !"REVIEWED".equals(desiredStatus)
                || reason == null || reason.isBlank() || reason.length() > 1000
                || idempotencyKey == null || idempotencyKey.length() > 120
                || !idempotencyKey.matches("[A-Za-z0-9._-]{8,120}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe orderId, desiredStatus REVIEWED, reason e idempotencyKey valido");
        }
        var existing = workflows.findByTenantIdAndIdempotencyKey(tenant, idempotencyKey);
        if (existing.isPresent()) {
            WorkflowRequest previous = existing.get();
            if (!Objects.equals(previous.orderId, orderId)
                    || !Objects.equals(previous.desiredStatus, desiredStatus)
                    || !Objects.equals(previous.reason, reason)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "idempotencyKey reutilizada com conteudo diferente");
            }
            return previous;
        }

        Order order = orders.findByIdAndTenantId(orderId, tenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
        if (!"CREATED".equals(order.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido nao admite esta proposta");
        }

        WorkflowRequest created = workflows.save(new WorkflowRequest(
                tenant, orderId, idempotencyKey, desiredStatus, reason.trim()));
        audit.save(new WorkflowAuditEvent(created, "AGENT", "PROPOSED",
                "orderId=" + orderId + "; desiredStatus=" + desiredStatus));
        return created;
    }

    @Transactional(readOnly = true)
    public List<WorkflowRequest> list(String tenant) {
        return workflows.findAllByTenantIdOrderByCreatedAtDesc(tenant);
    }

    @Transactional
    public WorkflowRequest approve(String tenant, UUID id) {
        WorkflowRequest item = locked(tenant, id);
        if ("APPROVED".equals(item.status)) return item;
        requireStatus(item, "PENDING_APPROVAL");
        item.status = "APPROVED";
        item.approvedBy = "APPROVER";
        item.updatedAt = Instant.now();
        audit.save(new WorkflowAuditEvent(item, "APPROVER", "APPROVED", "Aprovacao humana registrada"));
        return item;
    }

    @Transactional
    public WorkflowRequest reject(String tenant, UUID id) {
        WorkflowRequest item = locked(tenant, id);
        if ("REJECTED".equals(item.status)) return item;
        requireStatus(item, "PENDING_APPROVAL");
        item.status = "REJECTED";
        item.updatedAt = Instant.now();
        audit.save(new WorkflowAuditEvent(item, "APPROVER", "REJECTED", "Proposta recusada"));
        return item;
    }

    @Transactional
    public WorkflowRequest execute(String tenant, UUID id) {
        WorkflowRequest item = locked(tenant, id);
        if ("EXECUTED".equals(item.status)) return item;
        requireStatus(item, "APPROVED");
        Order order = orders.findByIdAndTenantId(item.orderId, tenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
        if (!"CREATED".equals(order.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Estado do pedido mudou");
        }
        order.status = item.desiredStatus;
        item.status = "EXECUTED";
        item.updatedAt = Instant.now();
        audit.save(new WorkflowAuditEvent(item, "CORE", "EXECUTED",
                "orderId=" + item.orderId + "; newStatus=" + order.status));
        return item;
    }

    @Transactional(readOnly = true)
    public List<WorkflowAuditEvent> audit(String tenant, UUID id) {
        if (workflows.findById(id).filter(w -> tenant.equals(w.tenantId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow nao encontrado");
        }
        return audit.findAllByTenantIdAndWorkflowIdOrderByIdAsc(tenant, id);
    }

    private WorkflowRequest locked(String tenant, UUID id) {
        return workflows.findScopedForUpdate(id, tenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow nao encontrado"));
    }

    private static void requireStatus(WorkflowRequest workflow, String expected) {
        if (!expected.equals(workflow.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transicao invalida a partir do status " + workflow.status);
        }
    }
}
