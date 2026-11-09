package dev.buno.app;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkflowAuditRepository extends JpaRepository<WorkflowAuditEvent, Long> {
    List<WorkflowAuditEvent> findAllByTenantIdAndWorkflowIdOrderByIdAsc(String tenantId, UUID workflowId);
}
