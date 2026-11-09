package dev.buno.app;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByTenantIdOrderByIdDesc(String tenantId);
    Optional<Order> findByIdAndTenantId(Long id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.tenantId = :tenant")
    Optional<Order> findScopedForUpdate(@Param("id") Long id, @Param("tenant") String tenant);
}
