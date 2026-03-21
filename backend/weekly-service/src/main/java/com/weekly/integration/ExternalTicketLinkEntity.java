package com.weekly.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code external_ticket_links} table.
 *
 * <p>Represents a link between a weekly commit and a ticket in an external
 * issue-tracker (Jira, Linear, etc.). Each row is scoped to an org via
 * {@code org_id} for row-level tenant isolation.
 */
@Entity
@Table(name = "external_ticket_links")
public class ExternalTicketLinkEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "commit_id", nullable = false, updatable = false)
    private UUID commitId;

    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "external_ticket_id", nullable = false, updatable = false, length = 200)
    private String externalTicketId;

    @Column(name = "external_ticket_url", length = 2000)
    private String externalTicketUrl;

    @Column(name = "external_status", length = 100)
    private String externalStatus;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExternalTicketLinkEntity() {
        // JPA
    }

    public ExternalTicketLinkEntity(
            UUID id,
            UUID orgId,
            UUID commitId,
            String provider,
            String externalTicketId,
            String externalTicketUrl,
            String externalStatus
    ) {
        this.id = id;
        this.orgId = orgId;
        this.commitId = commitId;
        this.provider = provider;
        this.externalTicketId = externalTicketId;
        this.externalTicketUrl = externalTicketUrl;
        this.externalStatus = externalStatus;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getCommitId() {
        return commitId;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalTicketId() {
        return externalTicketId;
    }

    public String getExternalTicketUrl() {
        return externalTicketUrl;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Setters (used when syncing status updates) ────────────

    public void setExternalTicketUrl(String externalTicketUrl) {
        this.externalTicketUrl = externalTicketUrl;
    }

    public void setExternalStatus(String externalStatus) {
        this.externalStatus = externalStatus;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
