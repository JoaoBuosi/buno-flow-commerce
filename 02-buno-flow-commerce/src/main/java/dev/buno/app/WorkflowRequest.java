package dev.buno.app;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_requests",
    uniqueConstraints = @UniqueConstraint(name = "uq_workflow_tenant_idempotency",
        columnNames = {"tenant_id", "idempotency_key"}),
    indexes = @Index(name = "idx_workflow_tenant_status", columnList = "tenant_id,status"))
public class WorkflowRequest {
    @Id @Column(nullable = false, updatable = false)
    public UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    public String tenantId;

    @Column(name = "order_id", nullable = false)
    public Long orderId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    public String idempotencyKey;

    @Column(name = "desired_status", nullable = false, length = 30)
    public String desiredStatus;

    @Column(nullable = false, length = 1000)
    public String reason;

    @Column(nullable = false, length = 30)
    public String status;

    @Column(name = "requested_by", nullable = false, length = 30)
    public String requestedBy;

    @Column(name = "approved_by", length = 30)
    public String approvedBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    public Long version;

    protected WorkflowRequest() {}

    public WorkflowRequest(String tenantId, Long orderId, String idempotencyKey,
                           String desiredStatus, String reason) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.desiredStatus = desiredStatus;
        this.reason = reason;
        this.status = "PENDING_APPROVAL";
        this.requestedBy = "AGENT";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
