package com.weekly.issues.repository;

import com.weekly.issues.domain.IssueActivityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link IssueActivityEntity} (Phase 6). */
public interface IssueActivityRepository extends JpaRepository<IssueActivityEntity, UUID> {

    List<IssueActivityEntity> findAllByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
