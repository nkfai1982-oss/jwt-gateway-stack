# JWT Gateway Stack

An implementation plan for a stateless authentication service and an open-source NGINX-based gateway. The design keeps application backends private, uses one token issuer, and applies authorization policy per component.

## Architecture

```text
Client
  │ credentials
  ▼
Spring Boot Auth Service ── RS256 access token
  │                           │
  ├── Protected static account file (Argon2id password hashes)
  └── JWKS endpoint (public verification keys)
                              │
Client ── Bearer token ──► OpenResty Gateway (NGINX Open Source)
                              │ validate signature, issuer, time,
                              │ audience, and route scopes
                 ┌────────────┴─────────────┐
                 ▼                          ▼
          Component A backend        Component B backend
          private network only       private network only
```

The Spring Boot authentication service verifies credentials against a protected static file containing Argon2id hashes, then signs short-lived access tokens with an RS256 private key. It publishes only public verification keys through a JWKS endpoint. OpenResty, an open-source NGINX distribution with Lua support, validates tokens at the gateway before forwarding requests to backends that are not directly exposed to clients.

Use one issuer for the stack. Isolation is separation of concerns, not separate identity systems: each component has its own audience and scope requirements, enforced by gateway route policy. A request must satisfy the token's signature, issuer, time validity, the route's expected audience, and the required scopes before it is proxied.

## Decisions

- **Accounts:** Static password accounts; no RDBMS and no Redis.
- **Password storage:** Argon2id hashes in a protected static file. Do not store plaintext passwords.
- **Issuer and tokens:** One issuer, RS256-signed, short-lived access tokens, with public keys published as JWKS.
- **Gateway:** OpenResty using NGINX Open Source for JWT validation and routing.
- **Authorization boundaries:** Component-specific audiences, scopes, and route policies under the shared issuer.
- **Backend exposure:** Backends remain private and accept traffic only from the gateway or explicitly trusted internal callers.
- **Logout:** Not required. Tokens are stateless and expire naturally.

## Implementation phases

1. **Repository and configuration skeleton:** Define service boundaries, local development conventions, example environment/configuration files, and deployment topology. Keep secrets and private keys out of version control.
2. **Authentication service:** Implement credential verification against the static account file, Argon2id verification, login/token issuance, short access-token lifetimes, and a JWKS endpoint. Define safe key loading and rotation procedures.
3. **Gateway validation:** Configure OpenResty to retrieve/cache JWKS, validate RS256 signatures and standard claims, and fail closed on invalid or unverifiable tokens.
4. **Component policies and routing:** Map gateway routes to component-specific audiences and required scopes. Verify unauthorized requests are rejected before reaching a backend.
5. **Private backend integration:** Place backends on private network paths, restrict inbound access to the gateway, and add end-to-end smoke tests for permitted and denied traffic.
6. **Security and operations hardening:** Add tests for expiry, wrong issuer/audience, missing scopes, key rotation, and JWKS outages; document secure file permissions, key backup/rotation, deployment, and incident response.

## Security notes and caveats

- **Password changes do not revoke tokens already issued.** Existing access tokens remain valid until their expiry time. Keep token lifetimes short and account for this delay in password-change procedures.
- Protect the static account file and signing private key with restrictive filesystem permissions and deployment secret controls. Never commit production credentials, account files, or private keys.
- Validate the allowed signing algorithm explicitly; do not trust an algorithm selected by an untrusted token header. Validate `iss`, `exp`, `nbf` when present, `aud`, and route-specific scopes.
- Define JWKS caching and key-rotation overlap so newly issued tokens remain verifiable without extending acceptance of expired tokens.
- Keep backend network access private; gateway validation is not a substitute for network-level access controls.
- The no-logout decision means there is no immediate per-token revocation mechanism in this design. If immediate revocation later becomes necessary, revisit the architecture rather than assuming password changes invalidate existing tokens.

## Initial acceptance checks

- A valid token for the expected issuer and audience with the required scope reaches only its intended component.
- Invalid signature, expired token, wrong issuer, wrong audience, or missing scope is rejected at the gateway.
- Backend services cannot be reached directly from untrusted networks.
- Password change behavior is documented and tested: previously issued tokens work only until their original expiry.
- JWKS key rotation can verify tokens signed by both the current and retiring key during the planned overlap, then stops accepting the retiring key after the overlap.
