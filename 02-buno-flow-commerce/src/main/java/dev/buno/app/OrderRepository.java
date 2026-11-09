package dev.buno.app;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByTenantIdOrderByIdDesc(String tenantId);
    Optional<Order> findByIdAndTenantId(Long id, String tenantId);
}
