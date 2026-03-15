package com.weekly.config;

import java.util.UUID;

/**
 * Holds the current tenant (org) context for the request.
 *
 * <p>The orgId is extracted from the validated JWT and set at the
 * start of each request. It is used to set the Postgres session
 * variable {@code app.current_org_id} for RLS enforcement (§9.6).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORG_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrgId(UUID orgId) {
        CURRENT_ORG_ID.set(orgId);
    }

    public static UUID getOrgId() {
        return CURRENT_ORG_ID.get();
    }

    public static void clear() {
        CURRENT_ORG_ID.remove();
    }
}
