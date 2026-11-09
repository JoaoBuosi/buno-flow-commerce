package dev.buno.app;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<WorkflowRequest, UUID> {
    Optional<WorkflowRequest> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    List<WorkflowRequest> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkflowRequest w where w.id = :id and w.tenantId = :tenantId")
    Optional<WorkflowRequest> findScopedForUpdate(@Param("id") UUID id,
                                                  @Param("tenantId") String tenantId);
}
