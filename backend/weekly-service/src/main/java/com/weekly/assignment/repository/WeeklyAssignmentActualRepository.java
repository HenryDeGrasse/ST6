package com.weekly.assignment.repository;

import com.weekly.assignment.domain.WeeklyAssignmentActualEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link WeeklyAssignmentActualEntity} (Phase 6). */
public interface WeeklyAssignmentActualRepository
        extends JpaRepository<WeeklyAssignmentActualEntity, UUID> {

    Optional<WeeklyAssignmentActualEntity> findByAssignmentId(UUID assignmentId);
}
