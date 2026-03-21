package com.weekly.auth;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped view of the authenticated user for the current request.
 *
 * <p>Controllers can inject this bean instead of reading raw headers. The
 * underlying {@link UserPrincipal} is sourced from Spring Security's
 * authenticated {@link Authentication} and therefore reflects only validated
 * credentials.
 */
@Component
@RequestScope
public class AuthenticatedUserContext {

    public UserPrincipal getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authenticated principal is available");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new IllegalStateException("Authenticated principal is not a UserPrincipal");
        }

        return userPrincipal;
    }

    public UUID userId() {
        return getPrincipal().userId();
    }

    public UUID orgId() {
        return getPrincipal().orgId();
    }

    public Set<String> roles() {
        return getPrincipal().roles();
    }

    public boolean hasRole(String role) {
        return getPrincipal().hasRole(role);
    }

    public boolean isManager() {
        return getPrincipal().isManager();
    }

    public boolean isAdmin() {
        return getPrincipal().isAdmin();
    }
}
