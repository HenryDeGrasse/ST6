package com.weekly.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production/staging stub for {@link RequestAuthenticator}.
 *
 * <p>Active in {@code prod} and {@code staging} profiles.
 * Intended to validate {@code Authorization: Bearer <jwt>} tokens against
 * the platform JWKS endpoint, extract {@code sub} (userId), {@code orgId},
 * and {@code roles} claims, and return a {@link UserPrincipal}.
 *
 * <p>Implementation TODO:
 * <ol>
 *   <li>Read {@code Authorization} header; strip {@code "Bearer "} prefix.</li>
 *   <li>Fetch JWKS from configured {@code security.jwks-uri} (cache 1h TTL,
 *       refresh on signature failure).</li>
 *   <li>Validate signature, expiry ({@code exp}), issuer ({@code iss}),
 *       and audience ({@code aud}).</li>
 *   <li>Extract {@code sub} → userId, {@code orgId} claim → orgId,
 *       {@code roles} claim → roles.</li>
 *   <li>Return {@link UserPrincipal}; throw {@link AuthenticationException}
 *       on any validation failure.</li>
 * </ol>
 *
 * <p>Until implemented, all prod/staging requests are rejected (fail secure).
 */
@Component
@Profile({"prod", "staging"})
public class JwksRequestAuthenticator implements RequestAuthenticator {

    @Override
    public UserPrincipal authenticate(HttpServletRequest request) {
        // TODO: implement JWKS endpoint JWT validation
        throw new AuthenticationException(
                "JWKS JWT validation is not yet implemented. "
                + "Configure security.jwks-uri and implement token validation "
                + "before deploying to production or staging."
        );
    }
}
