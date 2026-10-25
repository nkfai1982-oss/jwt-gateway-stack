# JWT Gateway Stack

A stateless authentication starter built around one Spring Boot issuer, short-lived RS256 access tokens, and an OpenResty edge gateway. The gateway validates tokens against the issuer's cached JWKS and applies an audience and scope policy per backend. The Spring and FastAPI backends are reachable only on Compose's internal network.

## What is implemented

- Static accounts stored as Argon2id hashes; passwords are entered interactively and never written to the account file in plaintext.
- A Spring Boot auth service that issues RS256 access tokens and publishes a public-only JWKS at `/.well-known/jwks.json`.
- OpenResty validation of the explicit `RS256` algorithm, signature, issuer, time claims, audience, and required route scope. JWKS is cached locally for 60 seconds; the gateway fails closed when validation or JWKS retrieval fails.
- Component A (`Spring Boot`) requires `component-a` audience and `component-a.read` scope. Component B (`FastAPI`) requires `component-b` and `component-b.read`.
- Docker Compose publishes only the gateway. The backends and issuer have no host-published ports and share only the private backend network with the gateway.
- No database or Redis. Logout and immediate token revocation are not implemented; an issued token remains valid until its original expiry, including after a password change.

## Run locally

Requirements: Docker Engine with the Compose plugin, OpenSSL, and Python 3 with `venv` support.
To run the Java tests directly on the host, also install Java 21 and Maven; container builds use the JDK in their build images.

1. Run `./scripts/dev-init.sh`. On first run it creates a local RSA key and prompts for an account name and password. The password is Argon2id-hashed into the ignored `secrets/accounts.json` file. Choose the audiences and scopes the local account should have; the starter defaults grant both example components.
2. Run `docker compose up --build`.
3. The gateway listens on `http://127.0.0.1:8080` by default. Only this local development listener is published; use TLS and a production-grade secrets manager before exposing the service beyond localhost.

The `secrets/` directory is excluded from Git and created with owner-only directory permissions. The local Compose file mounts read-only key and account files for the non-root auth process. Production should use the platform's native secret manager and stricter deployment-specific file permissions.

## Get and use a token

`POST /auth/token` accepts an account, one allowed audience, and the requested subset of that account's scopes:

```json
{
  "username": "alice",
  "password": "your local password",
  "audience": "component-a",
  "scopes": ["component-a.read"]
}
```

The response contains `access_token`, `token_type` (`Bearer`), and `expires_in`. The default lifetime is 600 seconds and is capped at one hour.

Send the returned token to the intended component:

```sh
curl -i http://127.0.0.1:8080/component-a/whoami \
  -H "Authorization: Bearer <component-a-token>"

curl -i http://127.0.0.1:8080/component-b/whoami \
  -H "Authorization: Bearer <component-b-token>"
```

A component-A token cannot be used for component B, and a token without that component's required scope is denied at the gateway before proxying. Missing, malformed, expired, wrong-issuer, wrong-audience, or unverifiable tokens are rejected. The backends also check the gateway-provided identity headers as defense in depth; those headers are not a substitute for the private network boundary.

The static account file is loaded at service startup. To add an account later, run the account tool with the desired permissions, then restart the auth service:

```sh
.venv/bin/python scripts/create-account.py \
  --audience component-a --scope component-a.read
```

The tool prompts for the username and password. It refuses to overwrite an existing username. Do not commit `secrets/`, production account data, signing keys, or real credentials.

## Verify locally

```sh
mvn -f services/auth/pom.xml test
mvn -f services/spring-api/pom.xml test

python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt -r services/python-api/requirements.txt
PYTHONPATH=services/python-api .venv/bin/pytest tests services/python-api/tests
```

The Compose smoke check requires Docker. After startup, `curl http://127.0.0.1:8080/healthz` should return `{"status":"ok"}`; invalid or cross-component tokens should receive a 401 or 403 and must not reach a backend.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `GATEWAY_PORT` | `8080` | Local host port for the gateway. |
| `JWT_ISSUER` | `http://jwt-gateway-stack/auth` | Shared issuer identifier; used by the issuer and gateway verifier. |
| `JWT_KEY_ID` | `dev-rsa-1` | Public JWKS key identifier. |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `600` | Access-token lifetime, 60–3600 seconds. |

If changing `JWT_ISSUER`, set the same value in both the issuer and gateway configuration. The gateway's JWKS cache currently uses a single active signing key for this starter. Planned hardening includes a deliberate dual-key rotation overlap, rate limiting, TLS/deployment configuration, and end-to-end Compose tests. Do not treat the initial dev key generation script as a production key-management process.
