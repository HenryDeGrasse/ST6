package com.weekly.usermodel;

import com.weekly.shared.UserModelDataProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Shared adapter exposing persisted user-model snapshots to downstream modules.
 */
@Service
public class DefaultUserModelDataProvider implements UserModelDataProvider {

    private final UserModelSnapshotRepository userModelSnapshotRepository;

    public DefaultUserModelDataProvider(UserModelSnapshotRepository userModelSnapshotRepository) {
        this.userModelSnapshotRepository = userModelSnapshotRepository;
    }

    @Override
    public Optional<UserModelSnapshot> getLatestSnapshot(UUID orgId, UUID userId) {
        return userModelSnapshotRepository.findByOrgIdAndUserId(orgId, userId)
                .map(snapshot -> new UserModelSnapshot(
                        snapshot.getUserId(),
                        snapshot.getWeeksAnalyzed(),
                        snapshot.getModelJson(),
                        snapshot.getComputedAt() != null ? snapshot.getComputedAt().toString() : null));
    }
}
