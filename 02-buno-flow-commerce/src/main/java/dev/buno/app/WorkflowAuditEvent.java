package dev.buno.app;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_audit_events",
    indexes = @Index(name = "idx_workflow_audit_scope", columnList = "tenant_id,workflow_id"))
public class WorkflowAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    public String tenantId;

    @Column(name = "workflow_id", nullable = false)
    public UUID workflowId;

    @Column(name = "actor_role", nullable = false, length = 30)
    public String actorRole;

    @Column(name = "event_type", nullable = false, length = 30)
    public String eventType;

    @Column(nullable = false, length = 300)
    public String details;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    protected WorkflowAuditEvent() {}

    public WorkflowAuditEvent(WorkflowRequest request, String role, String eventType, String details) {
        this.tenantId = request.tenantId;
        this.workflowId = request.id;
        this.actorRole = role;
        this.eventType = eventType;
        this.details = details;
        this.createdAt = Instant.now();
    }
}
