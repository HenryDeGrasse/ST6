package com.weekly.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditEventEntity} (append-only).
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findByOrgIdAndAggregateTypeAndAggregateId(
            UUID orgId, String aggregateType, UUID aggregateId);
}
