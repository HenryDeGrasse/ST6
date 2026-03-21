package com.weekly.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OrgPolicyEntity}.
 */
@Repository
public interface OrgPolicyRepository extends JpaRepository<OrgPolicyEntity, UUID> {

    Optional<OrgPolicyEntity> findByOrgId(UUID orgId);
}
