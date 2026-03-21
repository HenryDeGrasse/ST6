package com.weekly.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ExternalTicketLinkEntity}.
 */
@Repository
public interface ExternalTicketLinkRepository
        extends JpaRepository<ExternalTicketLinkEntity, UUID> {

    /**
     * Returns all ticket links for the given commit within the org.
     */
    List<ExternalTicketLinkEntity> findByOrgIdAndCommitId(UUID orgId, UUID commitId);

    /**
     * Returns all ticket links for the given provider within the org.
     */
    List<ExternalTicketLinkEntity> findByOrgIdAndProvider(UUID orgId, String provider);

    /**
     * Looks up an existing link by org, commit, provider, and external ticket ID.
     * Used to prevent duplicate links.
     */
    Optional<ExternalTicketLinkEntity> findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
            UUID orgId,
            UUID commitId,
            String provider,
            String externalTicketId
    );

    /**
     * Returns all links for a given provider and external ticket ID across all orgs.
     *
     * <p>Used by the {@link TicketSyncJob} and webhook handler to fan-out status
     * updates to every commit that references the same external ticket.
     */
    List<ExternalTicketLinkEntity> findByProviderAndExternalTicketId(
            String provider,
            String externalTicketId
    );

    /**
     * Returns all ticket links for a set of commit IDs within the org.
     *
     * <p>Used by {@link DefaultIntegrationService#getUnresolvedTicketsForUser}
     * to batch-load links for a user's recent commits in a single query.
     */
    List<ExternalTicketLinkEntity> findByOrgIdAndCommitIdIn(UUID orgId, List<UUID> commitIds);
}
