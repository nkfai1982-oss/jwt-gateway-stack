# JWT Gateway Stack

A stateless authentication starter built around one Spring Boot issuer, short-lived RS256 access tokens, and an OpenResty edge gateway. The gateway validates tokens against the issuer's cached JWKS and applies an audience and scope policy per backend. The Spring and FastAPI backends are reachable only on Compose's internal network.

## What is implemented

- Static accounts stored as Argon2id hashes; passwords are entered interactively and never written to the account file in plaintext.
- A Spring Boot auth service that issues RS256 access tokens and publishes a public-only JWKS at `/.well-known/jwks.json`.
- OpenResty validation of the explicit `RS256` algorithm, signature, issuer, time claims, audience, and required route scope. JWKS is cached locally for 60 seconds; the gateway fails closed when validation or JWKS retrieval fails.
- Component A (`Spring Boot`) requires `component-a` audience and `component-a.read` scope. Component B (`FastAPI`) requires `component-b` and `component-b.read`.
- Docker Compose publishes only the gateway. The backends and issuer have no host-published ports and share only the private backend network with the gateway.
- No database or Redis. Logout and immediate token revocation are not implemented; an issued token remains valid until its original expiry, including after a password change.

## Quickstart

Requirements: Docker Engine with the Compose plugin, OpenSSL, and Python 3 with `venv` support. Container builds use their own JDK images; Java 21 and Maven are needed only to run the Java tests directly on the host.

1. Initialize local development secrets and create an account. On first run, the script generates an RSA signing key, installs the account-tool dependencies, then prompts for a username and password. The default account can access both example components.

   ```sh
   ./scripts/dev-init.sh
   ```

2. Build and start the stack. Keep this terminal open while using the service.

   ```sh
   docker compose up --build
   ```

3. In another terminal, check that the gateway is ready:

   ```sh
   curl http://127.0.0.1:8080/healthz
   ```

   It should return `{"status":"ok"}`. The gateway listens on `http://127.0.0.1:8080` by default.

4. Request a token for Component A. Replace the username and password placeholders with the account created in step 1. The response includes an `access_token` (default lifetime: 600 seconds, maximum: one hour).

   ```sh
   curl -i http://127.0.0.1:8080/auth/token \
     -H 'Content-Type: application/json' \
     -d '{"username":"<your-username>","password":"<your-password>","audience":"component-a","scopes":["component-a.read"]}'
   ```

5. Use the returned access token to call the protected endpoint:

   ```sh
   curl -i http://127.0.0.1:8080/component-a/whoami \
     -H 'Authorization: Bearer <component-a-token>'
   ```

   To try Component B, request a token with audience `component-b` and scope `component-b.read`, then call `/component-b/whoami` with that token. Tokens are audience-specific: a Component A token cannot access Component B. Stop the stack with `docker compose down`.

The local Compose file publishes only the gateway on the loopback interface. Use TLS and a production-grade secrets manager before exposing the service beyond localhost. The `secrets/` directory is excluded from Git, created with owner-only directory permissions, and mounted read-only for the non-root auth process. Use the platform's native secret manager and stricter deployment-specific file permissions in production.

## Accounts and access control

`POST /auth/token` accepts an account, one allowed audience, and a requested subset of that account's scopes. An account cannot request permissions that were not assigned to it. Missing, malformed, expired, wrong-issuer, wrong-audience, or unverifiable tokens are rejected. The backends also check gateway-provided identity headers as defense in depth; those headers are not a substitute for the private network boundary.

The static account file is loaded at service startup. To add an account later, run the account tool with the desired permissions; it prompts for the username and password and refuses to overwrite an existing username. Restart the auth service after updating the file:

```sh
.venv/bin/python scripts/create-account.py \
  --audience component-a --scope component-a.read
docker compose up -d --force-recreate auth-service
```

Do not commit `secrets/`, production account data, signing keys, or real credentials.

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


## GitHub Agentic Workflows demo

This repository includes a small, **manual-only** [GitHub Agentic Workflows (`gh-aw`)](https://github.github.com/gh-aw/) example. The [workflow source](.github/workflows/jwt-gateway-demo.md) combines YAML frontmatter (trigger, AI engine, permissions, and allowed outputs) with plain-English instructions. The generated [`jwt-gateway-demo.lock.yml`](.github/workflows/jwt-gateway-demo.lock.yml) is the compiled GitHub Actions workflow; edit the Markdown source, then regenerate the lock file instead of editing it by hand.

The example inspects the JWT gateway in read-only mode and prepares at most one evidence-based architecture follow-up as a proposed issue. It runs only when manually started, and `safe-outputs.staged: true` means the proposal appears in the Actions run summary without creating an issue. This shows the basic loop: **describe the job → compile it → run an agent in Actions → review its proposed output**.

To try it:

1. Ensure GitHub Actions are enabled for the repository and configure Copilot authentication. Because this is a personal-account repository, the Copilot engine needs a fine-grained personal access token with the **Copilot Requests: Read** account permission, saved in repository Actions secrets as `COPILOT_GITHUB_TOKEN`. See GitHub's [Copilot authentication guide](https://github.github.com/gh-aw/reference/auth/).
2. Install the [`gh-aw` GitHub CLI extension](https://github.github.com/gh-aw/setup/quick-start/) if it is not already installed, then compile from the repository root:

   ```sh
   gh extension install github/gh-aw
   gh aw compile jwt-gateway-demo
   ```

3. Commit both the `.md` source and generated `.lock.yml`, push them to the default branch, then open **Actions → JWT Gateway demo → Run workflow**. Enter an optional focus hint and start the run. Review the proposed issue in the run summary.

Keep staging enabled while learning or tuning the prompt. To allow the workflow to create its proposed issue later, remove `staged: true` from the source and recompile; the output remains limited to the declared `create-issue` operation. For details, see GitHub's [workflow authoring guide](https://docs.github.com/en/copilot/how-tos/github-agentic-workflows/creating-github-agentic-workflows) and [staged mode guide](https://github.github.com/gh-aw/reference/staged-mode/).


The compiler also creates `.github/aw/actions-lock.json`. Commit this action-SHA cache with the workflow files so future compiles reuse the same immutable action pins; see the [action-pinning documentation](https://github.github.com/gh-aw/reference/compilation-process/).
