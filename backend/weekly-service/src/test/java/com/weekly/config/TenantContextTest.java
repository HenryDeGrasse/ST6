package com.weekly.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant context thread-local behaviour and SQL generation.
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGetOrgId() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrgId(orgId);
        assertEquals(orgId, TenantContext.getOrgId());
    }

    @Test
    void clearRemovesOrgId() {
        TenantContext.setOrgId(UUID.randomUUID());
        TenantContext.clear();
        assertNull(TenantContext.getOrgId());
    }

    @Test
    void getOrgIdReturnsNullWhenNotSet() {
        assertNull(TenantContext.getOrgId());
    }

    @Test
    void setTenantSqlGeneratesSetLocalStatement() {
        UUID orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String sql = TenantConnectionInterceptor.setTenantSql(orgId);
        assertEquals("SET LOCAL app.current_org_id = '11111111-1111-1111-1111-111111111111'", sql);
    }

    @Test
    void setTenantSqlUsesSetLocal() {
        // SET LOCAL ensures the setting is transaction-scoped
        String sql = TenantConnectionInterceptor.setTenantSql(UUID.randomUUID());
        assertTrue(sql.startsWith("SET LOCAL app.current_org_id ="));
    }
}
