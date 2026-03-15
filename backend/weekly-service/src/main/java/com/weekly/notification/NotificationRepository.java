package com.weekly.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationEntity}.
 */
@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID orgId, UUID userId);

    List<NotificationEntity> findByOrgIdAndUserIdOrderByCreatedAtDesc(
            UUID orgId, UUID userId);
}
