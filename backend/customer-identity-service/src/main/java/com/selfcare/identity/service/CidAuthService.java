package com.selfcare.identity.service;

import com.selfcare.identity.domain.Session;
import com.selfcare.identity.security.JwtIssuer;
import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.CidAuthProvider;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CID (authorization-code) login service.
 *
 * <p>Price parities the legacy {@code mda-auth-service} CID flow:
 * the operator exchanges the authorization code for a federated identity
 * via the tenant's {@link CidAuthProvider}, then this service records a
 * durable {@link Session} and issues the platform JWT pair (ADR-011).
 *
 * <p>The resolved identity is bound to the session as {@code CID:<cidUuid>}
 * so the JWT subject is stable across devices and downstream services can
 * route entitlement lookups by the same canonical identifier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CidAuthService {

    private final ApiAdapterRegistry<CidAuthProvider> registry;
    private final CustomerSessionService sessionService;
    private final JwtIssuer jwtIssuer;

    /**
     * Exchange a CID authorization code for a session + token pair.
     *
     * @param code              the authorization code from the operator redirect
     * @param redirectUri       optional redirect URI used during login
     * @param deviceId          device identifier (optional)
     * @param deviceDescription device description (optional)
     * @param ipAddress         client IP at login
     * @return the token pair plus the canonical identity from the provider
     * @throws UnauthorizedException when the provider cannot resolve the CID
     */
    public CidLoginResult exchange(String code, String redirectUri, String deviceId,
                                   String deviceDescription, String ipAddress) {
        String tenantId = TenantContext.get().getTenantId();
        CidAuthProvider provider = registry.getProvider(tenantId);

        CidAuthProvider.CidExchangeResult identity = provider.exchangeCid(
                tenantId, new CidAuthProvider.CidExchangeRequest(code, redirectUri));

        if (!identity.success()) {
            String reason = identity.failureReason() != null
                    ? identity.failureReason()
                    : "CID exchange failed";
            log.warn("CID exchange rejected for tenant={}: {}", tenantId, reason);
            throw new UnauthorizedException(reason);
        }

        String userId = "CID:" + identity.cidUuid();
        Session session = sessionService.createSession(
                userId, deviceId, deviceDescription, ipAddress);
        JwtIssuer.IssuedTokens tokens = jwtIssuer.issueForSession(session);

        log.info("CID login successful: tenant={}, cid={}, username={}, sessionId={}",
                tenantId, identity.cidUuid(), identity.username(), session.getSessionId());

        return new CidLoginResult(tokens, session.getSessionId(), identity);
    }

    /**
     * Result of a CID login: platform token pair + canonical identity.
     *
     * @param tokens  the issued access/refresh token pair
     * @param sessionId the durable session reference (legacy {@code uuid})
     * @param identity  the canonical identity returned by the operator provider
     */
    public record CidLoginResult(
            JwtIssuer.IssuedTokens tokens,
            String sessionId,
            CidAuthProvider.CidExchangeResult identity
    ) {}
}