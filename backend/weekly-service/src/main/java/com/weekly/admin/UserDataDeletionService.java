package com.weekly.admin;

import com.weekly.audit.AuditEventEntity;
import com.weekly.audit.AuditEventRepository;
import com.weekly.audit.AuditService;
import com.weekly.idempotency.IdempotencyKeyRepository;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that implements the GDPR right-to-be-forgotten process (PRD §14.7).
 *
 * <p>All operations are executed inside a single {@link Transactional} boundary so
 * that either every data-removal step succeeds or the entire request is rolled back.
 *
 * <h3>Deletion steps</h3>
 * <ol>
 *   <li>Soft-delete all {@code weekly_commits} belonging to the user's plans.</li>
 *   <li>Soft-delete all {@code weekly_plans} owned by the user.</li>
 *   <li>Anonymise {@code audit_events}: replace the user's {@code actor_user_id}
 *       with a deterministic SHA-256-derived UUID.</li>
 *   <li>Recompute the existing audit hash chain for the organisation so the
 *       sanctioned anonymisation does not register as tampering.</li>
 *   <li>Hard-delete all {@code notifications} for the user.</li>
 *   <li>Hard-delete all {@code idempotency_keys} for the user.</li>
 *   <li>Record an audit event for the deletion request itself, using the requesting
 *       admin's user ID as the actor.</li>
 * </ol>
 */
@Service
public class UserDataDeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(UserDataDeletionService.class);

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationRepository notificationRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditService auditService;

    public UserDataDeletionService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            AuditEventRepository auditEventRepository,
            NotificationRepository notificationRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            AuditService auditService
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.auditEventRepository = auditEventRepository;
        this.notificationRepository = notificationRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.auditService = auditService;
    }

    /**
     * Deletes all personal data for the specified user within the given organisation.
     *
     * @param orgId         the organisation in which the deletion is scoped
     * @param userId        the user whose data should be erased
     * @param adminUserId   the admin who initiated the deletion request (for audit)
     * @param ipAddress     the IP address of the admin request (nullable)
     * @param correlationId the request correlation ID (nullable)
     */
    @Transactional
    public void deleteUserData(
            UUID orgId,
            UUID userId,
            UUID adminUserId,
            String ipAddress,
            String correlationId
    ) {
        // 1. Soft-delete commits that belong to the user's plans first, while the
        //    plan-to-user mapping is still accessible via the subquery.
        int deletedCommits = commitRepository.softDeleteCommitsByUser(orgId, userId);

        // 2. Soft-delete the plans themselves.
        int deletedPlans = planRepository.softDeletePlansByUser(orgId, userId);

        // 3. Anonymise audit events. Because actor_user_id contributes to the
        //    tamper-detection payload, any affected hashes must be recomputed
        //    before we append the deletion event itself.
        UUID anonymisedId = computeAnonymisedUserId(userId);
        int anonymisedEvents = auditEventRepository.anonymizeActorUserId(
                orgId, userId, anonymisedId);
        if (anonymisedEvents > 0) {
            rehashAuditChain(orgId);
        }

        // 5. Hard-delete notifications.
        int deletedNotifications = notificationRepository.deleteByOrgIdAndUserId(orgId, userId);

        // 6. Hard-delete idempotency keys.
        int deletedKeys = idempotencyKeyRepository.deleteByOrgIdAndUserId(orgId, userId);

        // 7. Record a self-audit event for the deletion request so that the act of
        //    erasure is itself traceable.
        auditService.record(
                orgId,
                adminUserId,
                "USER_DATA_DELETED",
                "User",
                userId,
                null,
                null,
                "GDPR right-to-be-forgotten: all user data erased",
                ipAddress,
                correlationId
        );

        LOG.info(
                "GDPR deletion complete: org={}, user={}, plans={}, commits={}, "
                + "auditEventsAnonymised={}, notifications={}, idempotencyKeys={}",
                orgId, userId,
                deletedPlans, deletedCommits,
                anonymisedEvents,
                deletedNotifications, deletedKeys
        );
    }

    private void rehashAuditChain(UUID orgId) {
        List<AuditEventEntity> events = auditEventRepository.findAllByOrgIdOrderByCreatedAtAsc(orgId);
        String previousHash = "";
        for (AuditEventEntity event : events) {
            String recalculatedHash = event.computeChainedHash(previousHash);
            if (!Objects.equals(recalculatedHash, event.getHash())) {
                auditEventRepository.updateHashById(event.getId(), recalculatedHash);
            }
            previousHash = recalculatedHash;
        }
    }

    /**
     * Computes a deterministic anonymised UUID for the given user ID.
     *
     * <p>The algorithm is: {@code SHA-256(userId.toString())} interpreted as a
     * UUID (first 16 bytes, big-endian). This is not reversible but is stable:
     * running the same input always produces the same anonymised ID, which allows
     * cross-referencing anonymised audit rows if needed (e.g. to confirm a previous
     * deletion was already applied).
     *
     * @param userId the original user ID
     * @return a deterministic anonymised UUID
     */
    UUID computeAnonymisedUserId(UUID userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    userId.toString().getBytes(StandardCharsets.UTF_8));
            // Re-interpret the first 16 bytes of the SHA-256 digest as a UUID
            // (big-endian long pair).
            long msb = 0L;
            long lsb = 0L;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xFFL);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xFFL);
            }
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
