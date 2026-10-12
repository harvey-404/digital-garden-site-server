# Unified Email Account Implementation Plan

> **For agentic workers:** Use subagent-driven-development. Root owns central account service and deployment integration; separate implementers own blog frontend, diet, and sunzi repositories. Review each result and the combined authentication flow.

**Goal:** One verified email/password account accesses every visitor tool while preserving original records.

**Architecture:** An opaque cookie session is owned by the Spring service. Existing module identities remain intact and are linked to the unified account. Independent backends introspect the session using a server-only shared key.

**Tech Stack:** Java21/Spring Boot3.2/JDBC/MySQL/Flyway/JavaMail; React19/TypeScript; existing FastAPI/SQLite services.

## Global Constraints

- No replacement or deletion of existing user IDs/business records; no matching users by numerical IDs or names.
- No plaintext secrets or verification codes in logs, source control, browser bundles, or production API responses.
- Host-only HttpOnly dg_session cookie, Path=/, SameSite=Lax, production Secure. All unsafe cookie requests enforce allowed Origin.
- Registration requires verified email; reset revokes sessions; challenge purpose/expiry/attempt cap and reuse are enforced.
- Ordinary site accounts never receive administrator access.
- No local Docker in sunzi; retain its history ownership and deployment guards.
- No deployment or mail-send success claims without observed evidence; absent mail configuration remains a reported external dependency.

## Task 1: Central account and migration

Files: create account package (AccountService, AccountController, AccountSessionFilter, AccountLinks, AccountMailSender, AccountPolicy, AccountRateLimit), migration V9, tests AccountHttpTest; modify SecurityConfig and pom/config.

- [x] Write HTTP tests for verified registration, cookie login, module access, reset revocation and old user binding; run `mvn -Dtest=AccountHttpTest test` and observe expected missing-endpoint failures.
- [x] Implement schema and hashed sessions/challenges, transactional ownership links, lazy module provision, SMTP adapter, origin checks and internal introspection.
- [x] Run targeted account tests and existing tracker/ledger security tests; fix concrete regressions.

## Task 2: Website experience

Files: new account API/context/pages; modify App.tsx, Navbar.tsx, tracker and ledger clients/auth integration.

- [x] Add failing browser tests for unified login and returning to a requested tool; implement matching Chinese forms.
- [x] Preserve legacy credentials only for explicit/automatic proof-based linking; new sessions never enter localStorage.
- [x] Implement register/code/login/reset/logout/account and old data binding UI; new sessions use cookie-only requests.
- [x] Run TypeScript build and relevant browser tests.

## Task 3: Diet identity bridge

Files: server/app/site_auth.py, models/db/auth routes/settings tests and web auth/client/routes.

- [x] Add failing tests for introspection, one identity per global account, old invite claim and data isolation.
- [x] Verify dg_session server side via shared key and trusted central URL; preserve old IDs/records; support claim before provision and fail closed.
- [x] Redirect unauthenticated visitors to `/login?next=/diet/`; share logout and account link entry; run pytest and frontend build.

## Task 4: Sunzi identity bridge

Files: existing auth/session modules, app configuration, auth frontend/tests, deploy documentation.

- [x] Add failing tests for site session auth, account-scoped history and old invite-owner binding.
- [x] Add trusted introspection and unique mapping while retaining old history; use unified login and logout.
- [x] Run backend/frontend tests and build; preserve invite-only deployment rollback guards.

## Task 5: Deploy preparation and integration review

Files: deploy compose/env examples/readme, cross-service verification script and completion report.

- [x] Add MAIL_* and SITE_AUTH_* variables without secrets, migration backup/runbook and Alibaba console/DNS setup instructions.
- [x] Inspect implementation diffs and test cookie/auth/CSRF/trust/data migration across all services; remediate actionable findings.
- [x] Build all local artifacts, run relevant suites and browser acceptance with mocked mail/session services; document limitations.
- [ ] After the user configures DirectMail, back up production databases, verify actual mail delivery, deploy all services and run real cross-service acceptance.

## Review corrections

- Password verification and reset hold the same account lock through session creation/revocation, with explicit READ_COMMITTED.
- Cookie module requests retain the identity lock through business commit; READ_COMMITTED prevents an old snapshot after waiting for a binding.
- Barrier HTTP tests cover both binding/write orderings and reset/login overlap; default test pool uses REPEATABLE_READ to exercise snapshot behavior.
- Empty identity checks include ledger profile/categories and tracker timezone preferences.
- Diet business writes reject retired auto identities under a SQLite write reservation; image streaming only signals completion after persistence.

Production activation and actual SMTP delivery remain pending because the user has not opened/configured Aliyun DirectMail. See the deployment repository UNIFIED_ACCOUNT_SETUP.md and UNIFIED_ACCOUNT_VERIFICATION.md.
