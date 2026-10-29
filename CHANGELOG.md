# Changelog

9.0.0-SNAPSHOT (unreleased):
 - Update to VC-K 9.0.0-RC3
 - Include `vct#integrity` in issued SD-JWT VCs, the W3C SRI of the type metadata document of their `vct`
 - Serve on port 8080 and create no demo user by default again; configure `server.port` and
   `spring.security.user.password` explicitly, e.g. in a local profile
 - Stop writing status lists on every issuance: publish lists with valid entries for unassigned indices, growing by
   `backend.revocation-list.capacity-increment` (default 1000) once fewer than `minimum-free-indices` (default 500)
   are left, and write grown lists on the next dirty check
 - Include an optional registrar-signed WRPRC JWT in signed issuer metadata through VC-K's `issuer_info`,
   configured with `backend.metadata-registration-certificate`
 - Transport the configured metadata WRPAC leaf and intermediates in protected `x5c`, excluding a self-issued
   final root; keep metadata signing and registration configuration independently optional
 - Report PID login rejection causes from the complete validation report, including response-level failures
 - Load PID LoTEs alongside Wallet Provider LoTEs by default, with explicitly scoped A-SIT IACA 2026 anchors;
   configure PID credential issuers and status signers independently through `backend.trust-lists`
 - Validate PID login with VC-K's current credential/presentation API, requiring authorized issuers, current
   validity and trusted status when advertised; build the principal only from accepted validated credentials
 - Bind PID responses to the browser transaction and consume them once; rejected presentations and wallet
   cancellations remain unauthenticated, receive the protocol acknowledgement and display a login error
 - Configure loaded LoTE profiles, stages, URL overrides and profile-scoped issuance/status anchors through
   `backend.trust-lists`; default to Wallet Providers and PID on acceptance/development with the A-SIT IACA 2026 root
 - Resolve wallet-instance and key-storage status lists as JWT/CWT and require a separately authorized Wallet
   Provider status signer; reject revoked, expired, unavailable or untrusted status
 - Default shared and SD-JWT credential status-list URLs to JWT (`application/statuslist+jwt`), and per-key mdoc
   URLs to CWT (`application/statuslist+cwt`), using the URI's configured credential identifier; honor explicit
   `Accept` preferences, quality/exclusion values and send `Vary: Accept`
 - Adapt wallet and key attestation validation to current VC-K APIs, requiring trusted issuing anchors for
   supplied attestations even when key attestations are optional
 - Publish CWT status list tokens with the required COSE_Sign1 tag 18
 - Use VC-K prepared HTTP responses for OAuth2, credential issuance, and OpenID4VP login endpoints
 - Return credential token and DPoP errors with HTTP 401 and `WWW-Authenticate`
 - Forward protocol headers, including fresh attestation challenges, token cache controls, and `Vary: Accept`
 - Respect metadata content negotiation quality values and preserve the manual iPhone redirect page
 
8.0.0:
 - Select the credential signing key per credential scheme with `backend.credential-keys`, keyed by `vct` (SD-JWT)
   or ISO docType (mdoc)
 - Publish every configured signing key in the issuer JWKS
 - Give each configured credential its own status list, signed by its own key and served under
   `/credentials/status/<slug>/<timePeriod>`; `/credentials/status/current` aggregates all of them
 - **Breaking:** `backend.iso-mdoc-issuer-key` has been replaced by `backend.credential-keys`; list each mdoc
   docType explicitly to keep a separate mdoc key
 - Update to VC-K 8.0.0-RC1
 - Implement test cases for EUDI Launchpad 2026
 - Verify key attestations against the trusted Wallet Providers in wallet attestation mode, and only check the
   signature of optional key attestations otherwise
 - Serve `POST /challenge` for Attestation-Based Client Authentication, and send the
   `OAuth-Client-Attestation-Challenge` header with `use_attestation_challenge` errors
 - Serve issuer metadata as per OpenID4VCI 1.0: signed metadata carries `typ` `openidvci-issuer-metadata+jwt`,
   `sub`, `iat` and the display properties, and unsigned metadata uses snake_case keys

7.0.0:
 - Update to VC-K 7.0.0
 - Rework resolving credential schemes
 - Update to Spring Boot 4.1.0
 - Issue IDA 1.5 Binding credential

6.0.0:
 - Update to VC-K 6.0.0
 - Update to EU PID 3.5.0
 - Remove `jvm` suffix from `vck` in `lib.versions` to restore composite build functionality 
 - Use `JwsCompactTyped` instead of `JwsSigned`

5.13.0:
 - Update to VC-K 5.13.0
 - Migrate to Spring Boot 4.0.6 (Spring Framework 7, Spring Security 7, Jakarta EE 11)
 - Update Spring Cloud to 2025.1.1 (Oakwood)
 - Update Spring Boot Admin client to 4.0.4
 - Upgrade JVM toolchain to Java 21
 - Enable virtual threads (`spring.threads.virtual.enabled`)
 - Switch primary HTTP message converter to kotlinx.serialization backed by `joseCompliantSerializer`


5.12.0:
 - Update to VC-K 5.12.0
 - Add DC API issuing process
 - Support explicit constants for [Age Verification](https://ageverification.dev/)

5.11.0:
 - Initial public release
 - Update to VC-K 5.11.0
 - Update dependencies, i.e. Spring Boot to 3.5.9
 - Remove HealthID and ePrescription credentials
