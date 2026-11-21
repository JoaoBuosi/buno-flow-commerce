# Buno Verify hard gate — QA-only proposal

This branch introduces an **opt-in** protection for one transition: `PAID -> PROCESSING`.
With `COMMERCE_VERIFY_REQUIRED=true`, the normal status endpoint refuses this transition
without a valid short-lived HMAC-SHA256 proof generated inside Buno Verify.

## Trust boundaries
- Browser, agent runtime, MCP client: **never** receive the signing key or destination bearer token.
- Verify server: holds `BUNO_VERIFY_FLOW_QA_SIGNING_SECRET`, dedicated QA destination token.
- Flow backend: holds matching `COMMERCE_VERIFY_SIGNING_SECRET`, verifies proof and rejects missing/bad/expired proof before touching the database.
- PostgreSQL: consumes `operation_id` uniquely, alongside the order update and audit history, in one transaction.
- Proof binds method, path, store, order, operation ID, expiry, expected version, old and new status. The initial protected transition is exclusively `PAID -> PROCESSING`.

## QA rollout checklist
1. **Do not merge or deploy to live Flow Commerce.** Use a disposable Java+PostgreSQL 17 environment with V7 migration applied.
2. Generate a random >=32-byte secret in a vault; configure same value in isolated Verify and Flow backend (never commit it).
3. Set `COMMERCE_VERIFY_REQUIRED=true` only in that isolated backend; keep its port and DB inaccessible from agent runtimes.
4. Provision one dedicated, least-privilege test account; give its token only to the Verify server environment.
5. Remove/revoke all destination bearer tokens, service keys, and browser session access held by AI agents. Agents should know only their **Verify** credentials.
6. Confirm an untrusted agent directly calling PATCH without proof gets HTTP 403, even if it somehow possesses a normal Flow session.
7. Confirm approved Verify call with proof makes **one** transition and repeat/expired/wrong-store requests fail.
8. Validate that other user-operated status transitions remain supported. Enabling protection will intentionally block direct manual `PAID -> PROCESSING` too until a dedicated human UI flow exists.

## What this does not guarantee
- The guard is **off by default**; deployed production remains unchanged.
- Protection is limited to this status transition; it is not yet a universal MCP/tool proxy.
- Existing username/password sessions, other API endpoints, or direct DB/admin access must be isolated separately.
- HMAC uses a shared secret; compromise of Verify or Flow server voids the proof trust.
- The basic operation identity is not yet tied to a certified external audit log or company SSO.
- The automated tests use a CI-only secret, never production credentials.

Use the secret only in protected CI/QA server environments. Never embed it in the React bundle, a notebook, or an agent prompt.

### Dedicated service identity (QA)
The Verify gateway must use an opaque 64-character hex **service** bearer, not a browser/OWNER bearer. The service token is generated from a CSPRNG outside the app; only its SHA-256 hash is provisioned in `verify_service_credentials`, bound to one store, a technical account user, expiration and `ORDER_PROCESSING_ONLY`. Under `COMMERCE_VERIFY_REQUIRED=true`, service credentials are accepted by AuthService only when still valid, and VerifyServiceCredentialFilter restricts them to `GET /me`, `GET /orders/{id}`, `GET /orders/{id}/history` and `PATCH /orders/{id}/status`. A service bearer cannot directly create/cancel/export orders, and the PATCH route requires a valid signed proof and PROCESSING target. Normal browser sessions cannot apply signed protected transitions.

No migration or service bearer has been provisioned in Railway. CI issues short-lived disposable credentials only in its private PostgreSQL runner. Revoking a bearer means setting `revoked_at` or deleting its hash; never reveal real tokens in GitHub, browser code, or chat.
