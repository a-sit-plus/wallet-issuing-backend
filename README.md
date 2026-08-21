<div align="center">


<picture>
  <source media="(prefers-color-scheme: dark)" srcset="issuer-light.png">
  <source media="(prefers-color-scheme: light)" srcset="issuer-dark.png">
  <img alt="Wallet Issuing Backend for Kotlin Multiplatform" src="issuer-dark.png">
</picture>
<br><br>

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![A-SIT Plus Official](https://img.shields.io/badge/A--SIT_Plus-official-005b79?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNDMuNzYyODYgMTg0LjgxOTk5Ij48ZGVmcz48Y2xpcFBhdGggaWQ9ImEiIGNsaXBQYXRoVW5pdHM9InVzZXJTcGFjZU9uVXNlIj48cGF0aCBkPSJNMCA1OTUuMjhoODQxLjg5VjBIMFoiLz48L2NsaXBQYXRoPjwvZGVmcz48ZyBjbGlwLXBhdGg9InVybCgjYSkiIHRyYW5zZm9ybT0ibWF0cml4KDEuMzMzMzMzMyAwIDAgLTEuMzMzMzMzMyAtNDgyLjI1IDUxNy41MykiPjxwYXRoIGZpbGw9IiMwMDViNzkiIGQ9Ik00MTUuNjcgMjQ5LjUzYy03LjE1LjA4LTEzLjk0IDEtMjAuMTcgMi43NWE1Mi4zMyA1Mi4zMyAwIDAgMC0xNy40OCA4LjQ2IDQwLjQzIDQwLjQzIDAgMCAwLTExLjk2IDE0LjU2Yy0yLjY4IDUuNDEtNC4xNCAxMS44NC00LjM1IDE5LjA5bC0uMDIgNi4xMnYyLjE3YS43MS43MSAwIDAgMCAuNy43M2gxNi41MmMuMzkgMCAuNy0uMzIuNzEtLjdsLjAxLTIuMmMwLTIuNi4wMi01LjgyLjAzLTYuMDcuMi00LjYgMS4yNC04LjY2IDMuMDgtMTIuMDZhMjguNTIgMjguNTIgMCAwIDEgOC4yMy05LjU4IDM1LjI1IDM1LjI1IDAgMCAxIDExLjk2LTUuNTggNTUuMzggNTUuMzggMCAwIDEgMTIuNTgtMS43NmM0LjMyLjEgOC42LjcgMTIuNzQgMS44YTM1LjA3IDM1LjA3IDAgMCAxIDExLjk2IDUuNTcgMjguNTQgMjguNTQgMCAwIDEgOC4yNCA5LjU3YzEuOTYgMy42NCAzIDguMDIgMy4xMiAxMy4wMnYyNC4wOUgzNjIuNGEuNy43IDAgMCAwLS43MS43VjMzNWMwIDguNDMuMDEgOC4wNS4wMSA4LjE0LjIgNy4zIDEuNjcgMTMuNzcgNC4zNiAxOS4yMmE0MC40MyA0MC40MyAwIDAgMCAxMS45NiAxNC41N2M1IDMuNzYgMTAuODcgNi42MSAxNy40OCA4LjQ2YTc3LjUgNzcuNSAwIDAgMCAyMC4wMiAyLjc3YzcuMTUtLjA3IDEzLjk0LTEgMjAuMTctMi43NGE1Mi4zIDUyLjMgMCAwIDAgMTcuNDgtOC40NiA0MC40IDQwLjQgMCAwIDAgMTEuOTUtMTQuNTdjMS42Mi0zLjI2IDMuNzctMTAuMDQgMy43Ny0xNC42OCAwLS4zOC0uMTctLjc0LS41NC0uODJsLTE2Ljg5LS40Yy0uMi0uMDQtLjM0LjM0LS4zNC41NCAwIC4yNy0uMDMuNC0uMDYuNi0uNSAyLjgyLTEuMzggNS40LTIuNjEgNy42OWEyOC41MyAyOC41MyAwIDAgMS04LjI0IDkuNTggMzUuMDEgMzUuMDEgMCAwIDEtMTEuOTYgNS41NyA1NS4yNSA1NS4yNSAwIDAgMS0xMi41NyAxLjc3Yy00LjMyLS4xLTguNjEtLjcxLTEyLjc1LTEuOGEzNS4wNSAzNS4wNSAwIDAgMS0xMS45Ni01LjU3IDI4LjUyIDI4LjUyIDAgMCAxLTguMjMtOS41OGMtMS44Ni0zLjQ0LTIuOS03LjU1LTMuMDktMTIuMmwtLjAxLTcuNDdoODkuMTZhLjcuNyAwIDAgMCAuNy0uNzJ2LTM5LjVjLS4xLTcuNjUtMS41OC0xNC40LTQuMzgtMjAuMDZhNDAuNCA0MC40IDAgMCAwLTExLjk1LTE0LjU2IDUyLjM3IDUyLjM3IDAgMCAwLTE3LjQ4LTguNDcgNzcuNTYgNzcuNTYgMCAwIDAtMjAuMDEtMi43N1oiLz48cGF0aCBmaWxsPSIjY2U0OTJlIiBkPSJNNDE5LjM4IDI4MC42M2gtNy41N2EuNy43IDAgMCAwLS43MS43MXYxNS40MmE4LjE3IDguMTcgMCAwIDAtMy43OCA2LjkgOC4yOCA4LjI4IDAgMCAwIDE2LjU0IDAgOC4yOSA4LjI5IDAgMCAwLTMuNzYtNi45di0xNS40MmEuNy43IDAgMCAwLS43Mi0uNzEiLz48L2c%2BPC9zdmc%2B&logoColor=white&labelColor=white)](https://a-sit-plus.github.io)
[![Powered by VC-K](https://img.shields.io/badge/VC--K-powered-8A2BE2?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA4LjAzIDkuNSI+PGcgZmlsbD0iIzhhMmJlMiIgZm9udC1mYW1pbHk9IlZBTE9SQU5UIiBmb250LXNpemU9IjEyLjciIHRleHQtYW5jaG9yPSJtaWRkbGUiPjxwYXRoIGQ9Ik01OS42NCAyMjIuMTNxMC0uOTguMzYtMS44Mi4zNy0uODQuOTgtMS40Ni42Mi0uNjIgMS40Ni0uOTYuODMtLjM2IDEuOC0uMzUgMS4wMy4wMiAxLjkuNDIuODcuNCAxLjUgMS4xMi4wNC4wNS4wMy4xMSAwIC4wNy0uMDUuMWwtMSAuODZxLS4wNi4wMy0uMTIuMDN0LS4xLS4wNnEtLjQyLS40OC0xLS43Ni0uNTYtLjMtMS4yMi0uMjgtLjYuMDEtMS4xMy4yNy0uNTQuMjQtLjkzLjY3LS40LjQyLS42Mi45OC0uMjMuNTYtLjIzIDEuMiAwIC42My4yNCAxLjE4LjI0LjU2LjY1Ljk4LjQuNDIuOTQuNjYuNTMuMjMgMS4xNC4yMy42My0uMDEgMS4yLS4zLjU1LS4yNy45Ni0uNzUuMDQtLjA1LjEtLjA1LjA2LS4wMi4xMS4wM2wxIC44NnEuMDYuMDMuMDYuMS4wMS4wNi0uMDMuMTEtLjY0LjczLTEuNTMgMS4xNC0uOS40MS0xLjk1LjQtLjk1IDAtMS43OS0uMzYtLjgyLS4zNy0xLjQzLS45OS0uNjEtLjYzLS45NS0xLjQ4LS4zNS0uODUtLjM1LTEuODN6IiBzdHlsZT0iLWlua3NjYXBlLWZvbnQtc3BlY2lmaWNhdGlvbjpWQUxPUkFOVDt0ZXh0LWFsaWduOmNlbnRlciIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTU5LjY0IC0yMTcuNDIpIi8+PHBhdGggZD0iTTY2LjIxIDIyMS4zNWgxLjNjLjEgMCAuMTYuMDYuMTYuMTd2MS4zOGMwIC4xMS0uMDUuMTctLjE2LjE3aC0xLjNjLS4xIDAtLjE2LS4wNi0uMTYtLjE3di0xLjM4YzAtLjExLjA1LS4xNy4xNi0uMTd6IiBsZXR0ZXItc3BhY2luZz0iLTMuMTIiIHN0eWxlPSItaW5rc2NhcGUtZm9udC1zcGVjaWZpY2F0aW9uOlZBTE9SQU5UO3RleHQtYWxpZ246Y2VudGVyIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNTkuNjQgLTIxNy40MikiLz48L2c+PC9zdmc+&logoColor=white&labelColor=white)](https://github.com/a-sit-plus/vck)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin)](http://kotlinlang.org)

</div>

OpenID for Verifiable Credential Issuance (OpenID4VCI) backend for issuing credentials to
EUDI Wallets and other compatible wallet implementations.

This service uses [VC-K](https://github.com/a-sit-plus/vck) as the credential, OpenID, and status-list
toolkit. It wires VC-K's issuer agent, OpenID4VCI issuer, OAuth2 authorization service, OpenID4VP
verifier, credential scheme mapper, and token status list support into a Spring Boot application that
can authenticate users, derive credential claims, and deliver wallet-ready credentials.


| ⚠️ Warning                                             |
|:-------------------------------------------------------|
| This service is intended as a Technology Demonstrator! |

## What It Does

- Issues credentials over [OpenID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
  with authorization code, pre-authorized code, pushed authorization requests, token exchange, nonce, and credential endpoints.
- Supports the [Digital Credentials API](https://wicg.github.io/digital-credentials/) for browser-based issuance
  without a redirect.
- Uses VC-K's issuer APIs to sign and deliver credentials as JWT VC, SD-JWT VC, and ISO mdoc where supported
  by the configured credential scheme.
- Publishes issuer, OAuth2/OIDC, and JWT VC metadata for wallet discovery.
- Authenticates users through Spring Security, external OIDC providers such as ID Austria, or EU PID
  presentation via OpenID4VP.
- Converts authenticated identity data into EUDI Wallet credential payloads.
- Tracks issued credentials and writes token status lists for revocation/status checks.
- Runs as a standard Spring Boot service with JPA persistence, H2 for local development, PostgreSQL for
  deployments, actuator endpoints, and optional Spring Boot Admin integration.

## VC-K Integration Highlights

VC-K is the core interoperability layer in this repository:

| Capability | How this backend uses VC-K |
| --- | --- |
| Credential issuance | `IssuerAgent` signs credentials and binds them to the holder key supplied by the wallet. |
| OpenID4VCI | `CredentialIssuer` implements the issuer metadata, nonce, and credential response logic. |
| OAuth2 for issuance | `SimpleAuthorizationService` handles PAR, authorization, token issuance, DPoP nonce handling, and credential authorization. |
| Credential formats | The configured VC-K credential schemes expose JWT VC, SD-JWT VC, and ISO mdoc representations where available. |
| OpenID4VP login | `OpenId4VpVerifier` verifies EU PID presentations used as an identity source for issuance. |
| Status lists | `StatusListAgent` creates token status lists and status-list aggregation metadata for issued credentials. |
| Metadata mapping | `DefaultCredentialSchemeMapper` maps VC-K credential schemes to OpenID4VCI supported credential formats. |

## Supported Credential Schemes

The backend registers credential schemes from the
[A-SIT Plus credentials collection](https://github.com/a-sit-plus/credentials-collection):

- EU PID
- EU PID SD-JWT
- Mobile Driving Licence
- Power of Representation
- Certificate of Residence
- Tax ID
- European Health Insurance Card
- Age Verification

The exact format availability depends on each scheme's VC-K representation support.

## Architecture

```text
Wallet
  |  OpenID4VCI / OpenID4VP
  v
Spring Boot HTTP service
  |-- OAuth2Controller          PAR, authorize, token, OAuth/OIDC metadata
  |-- OpenId4VciController      issuer metadata, nonce, credential endpoint
  |-- PublicController          login, OIDC login choices, EU PID OpenID4VP login
  |-- StatusListController      status list and aggregation endpoints
  |-- RevocationController      debug self-revocation UI
  |
  |-- VC-K IssuerAgent / CredentialIssuer / SimpleAuthorizationService
  |-- DataExtractor and OidcIssuerCredentialDataProvider
  |-- JPA repositories for prepared, issued, and revoked credentials
```

## Requirements

- JDK 21
- A database supported by Spring Data JPA
  - H2 works for local development and tests
  - PostgreSQL is the intended production option

## Production Limitations

This repository is useful as a reference implementation and test issuer, but it is not suitable to run
unchanged as a production service. The current code has several deliberate development shortcuts and
operational limitations:

- **Demo authentication requires explicit configuration.** A form-login demo user is only created when
  `spring.security.user.password` is set; without it no demo user exists and the only way to log in is
  through a configured OIDC provider or an EU PID presentation. CSRF protection is disabled, and every
  request is permitted except `/authorize` unless method-level annotations add further checks.
- **Sessions and transient issuance state are in memory.** Spring sessions use `MapSessionRepository`, and
  credential offers plus OpenID4VP login transactions use `DefaultMapStore`. Login state, offers, and
  transactions are lost on restart and are not shared across multiple service instances.
- **Default keys are ephemeral.** `backend.issuer-key.type`, `backend.iso-mdoc-issuer-key.type` when set,
  and `backend.verifier-key.type` default to `MEMORY`, which creates fresh self-signed key material on
  startup. A restart changes issuer/verifier identity unless file or keystore-backed keys are configured.
- **Credential claims are partly synthetic.** The claim builders in `DataExtractor.kt` and
  `FakeDataExtractor.kt` fill many fields with random or placeholder values, including document numbers,
  tax data, driving privileges, address fallbacks, biometrics, and development-user identity data.
- **Subject matching is not production-grade.** Revocation ownership maps ID Austria users by
  `givenName familyName birthDate`; the code comments note that this is only a development-stage
  workaround and that collisions may happen.
- **Status-list storage is local filesystem based.** `RevocationListWriter` writes JWT and CWT status lists
  under `backend.revocation-list.path`; `StatusListController` serves those files directly. This needs
  shared durable storage or another publication strategy for clustered deployments.
- **Scheduling and locking are single-node oriented.** Status-list refresh state is held in memory, and
  credential repository updates rely on a JVM-local `synchronized` lock. That does not coordinate multiple
  running instances.
- **Database management is minimal.** The README examples use `hibernate.ddl-auto: update`; there are no
  explicit schema migrations. Identity-column recovery is implemented only for H2 and PostgreSQL and should
  not replace normal migration and database operations.
- **Debug and demo surfaces are exposed.** The root page generates credential-offer QR codes, including
  pre-authorized offers for logged-in users, and `/revocation` is explicitly described in code as a debug
  self-revocation mechanism.
- **Logging may expose personal or credential data.** Several controllers and data providers log request
  bodies, authentication subjects, credential data, status-list content, and issuance details at info,
  debug, or verbose levels.
- **Credential support is not complete for every scheme/format combination.** Unsupported combinations in
  the claim builders currently reach `TODO(...)`, which can fail at runtime if new metadata exposes formats
  without matching claim construction.
- **Trust and authorization policy are application-specific.** The service demonstrates OIDC and EU PID
  input, but production deployments still need issuer policy, claim provenance checks, audit rules, rate
  limiting, secret management, TLS/proxy hardening, and incident operations around this code.

## Quick Start

Run the HTTP service locally:

```bash
./gradlew :http:bootRun
```

The service starts on port `8080` by default. Open:

- `http://localhost:8080/` for the issuer UI
- `http://localhost:8080/login` for OIDC and EU PID login options
- `http://localhost:8080/.well-known/openid-credential-issuer` for OpenID4VCI issuer metadata

Run the test suite:

```bash
./gradlew test
```

Build the bootable service artifact:

```bash
./gradlew :http:bootJar
```

## Docker

Build a container image:

```bash
docker build -t wallet-issuing-backend .
```

Run it locally:

```bash
docker run -p 8080:8080 wallet-issuing-backend
```

Pass JVM options or change the port through environment variables the image exposes:

```bash
docker run -p 9090:9090 \
  -e PORT=9090 \
  wallet-issuing-backend
```

Any `application.yml` property can be supplied as an environment variable using Spring Boot's
[relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
(e.g. `BACKEND_PUBLIC_CONTEXT=https://issuer.example.com`).

## Important Endpoints

| Endpoint | Purpose |
| --- | --- |
| `/.well-known/openid-credential-issuer` | OpenID4VCI credential issuer metadata |
| `/.well-known/openid-configuration` | OpenID provider metadata |
| `/.well-known/oauth-authorization-server` | OAuth2 authorization server metadata |
| `/.well-known/jwt-vc-issuer` | JWT VC issuer metadata |
| `/par` | Pushed authorization request endpoint |
| `/authorize` | Authorization endpoint |
| `/token` | Token endpoint |
| `/nonce` | Credential proof nonce endpoint |
| `/credential` | OpenID4VCI credential endpoint |
| `/credentials/status/current` | Current status-list aggregation |
| `/credentials/status/{timePeriod}` | Status list for a time period |
| `/offer/{nonce}` | Credential offer JSON resolved by nonce (linked from QR code) |
| `/dcapi/create-request/{nonce}` | Digital Credentials API issuance payload resolved by nonce |
| `/transaction/result` | OpenID4VP transaction result callback |
| `/transaction/get` | OpenID4VP transaction state polling |
| `/login` | User login page for OIDC and EU PID based identity input |
| `/logout` | Session logout |
| `/revocation` | Debug self-revocation view |

## Configuration

Custom properties live under the `backend` prefix and are defined in
[`BackendConfigurationProperties.kt`](http/src/main/kotlin/at/asitplus/wallet/backend/config/BackendConfigurationProperties.kt).

```yaml
backend:
  public-context: "http://localhost:8080/"
  credentials:
    lifetime: P7D
  revocation-list:
    lifetime: P7D
    regular-write-timeout: P5D
    dirty-check-rate: PT10M
    regular-check-rate: PT1H
    path: cache/revocation-lists/
  metadata:
    name: "A-SIT Plus Wallet Issuer"
    logo: "https://wallet.a-sit.at/assets/images/logo.svg"
  issuer-key:
    type: MEMORY
  credential-keys:
    "eu.europa.ec.eudi.pid.1":
      type: MEMORY
  verifier-key:
    type: MEMORY
```

Key settings:

- `backend.public-context` is the externally reachable base URL. It is used in wallet-facing metadata,
  credential offers, redirects, and status-list URLs.
- `backend.credentials.lifetime` controls issued credential validity as an ISO-8601 duration, for example
  `PT60M`, `P7D`, or `P180D`.
- `backend.metadata.name` and `backend.metadata.logo` populate OpenID4VCI display metadata.
- `backend.issuer-key` signs every credential without its own key in `backend.credential-keys`, and that
  credential's status lists.
- `backend.credential-keys` selects the signing key per credential, keyed by `vct` (SD-JWT) or ISO docType
  (mdoc), i.e. the identifier the wallet requests the credential under. See
  [Per-Credential Signing Keys](#per-credential-signing-keys).
- `backend.verifier-key` signs OpenID4VP authentication requests for EU PID login.

### Key Material

For local development, `MEMORY` creates an ephemeral key pair with a self-signed certificate:

```yaml
backend:
  issuer-key:
    type: MEMORY
  verifier-key:
    type: MEMORY
```

For deployments, load PEM encoded key material:

```yaml
backend:
  issuer-key:
    type: FILE
    file:
      private-key: file:issuer-key-private.pem
      public-key: file:issuer-key-public.pem
      certificate: file:issuer-cert.pem
```

Or load a Java KeyStore:

```yaml
backend:
  issuer-key:
    type: KEYSTORE
    keystore:
      path: file:/some/path/keystore.p12
      type: PKCS12
      provider: BC
      password: changeit
      alias: key1
      alias-password: changeit
```

### Per-Credential Signing Keys

Different credentials often need different PKI, for example an IACA-chained document signer certificate for
mdocs and a separate certificate for PID. `backend.credential-keys` assigns a signing key per credential,
keyed by the identifier the wallet requests it under: the `vct` for SD-JWT credentials, the ISO docType for
mdoc credentials. Both keys are the values listed in
[`CredentialCatalog`](http/src/main/kotlin/at/asitplus/wallet/backend/config/RemoteCredentialMetadata.kt); an
unknown identifier fails startup.

```yaml
backend:
  issuer-key:
    type: KEYSTORE
    keystore: { path: "file:/some/path/keystore.p12", type: PKCS12, alias: key1 }
  credential-keys:
    "eu.europa.ec.eudi.pid.1":       # EU PID as mdoc
      type: KEYSTORE
      keystore: { path: "file:/some/path/pid-mdoc.p12", type: PKCS12, alias: pid-ds }
    "urn:eudi:pid:1":                # EU PID as SD-JWT
      type: FILE
      file:
        private-key: file:pid-sdjwt-private.pem
        certificate: file:pid-sdjwt-cert.pem
```

Every configured key is published in the issuer's JWKS at `/.well-known/openid-credential-issuer`.

A credential with its own key also gets its own status list, signed by that key and served under
`/credentials/status/<slug>/<timePeriod>`, where `<slug>` is the identifier with every character outside
`[A-Za-z0-9._-]` replaced by `-`, e.g. `urn-eudi-pid-1`. Credentials signed with `backend.issuer-key` keep
using `/credentials/status/<timePeriod>`, so credentials issued before this setting existed keep resolving.
`/credentials/status/current` aggregates the lists of all keys.

All status lists share one index space, so a list may contain bits for indices issued under another key.
Those indices are never referenced by a credential in that list, so they cannot cause a false revocation, but
it does mean the lists are not partitioned per credential type.

> [!NOTE]
> `backend.iso-mdoc-issuer-key` has been replaced by `backend.credential-keys`. To keep a separate mdoc key,
> list each mdoc docType you issue explicitly. Note that its credentials then move to the new status list
> path; already-issued mdocs keep pointing at the old path, which stays served.

### Revocation And Status Lists

Token status list output is controlled by `backend.revocation-list`:

- `lifetime`: lifetime of a generated status list credential, default `P7D`.
- `regular-write-timeout`: maximum age before a list is written again, default `P5D`.
- `dirty-check-rate`: interval for writing lists after revocation changes, default `PT10M`.
- `regular-check-rate`: interval for refreshing outdated lists, default `PT1H`.
- `path`: directory where status lists are written, default `cache/revocation-lists/`.

## Identity Sources

Credentials are derived from the authenticated user's identity data.

The service supports:

- A development username/password user configured by Spring Security.
- Any Spring Security OAuth2 client registration.
- EU PID login through OpenID4VP presentation.

### Demo User

A form-login demo user is only created when `spring.security.user.password` is explicitly set.
Without it no local user exists and login is only possible through a configured OIDC provider or
an EU PID presentation.

```yaml
spring:
  security:
    user:
      name: alice        # defaults to "user" when omitted
      password: changeit
```

The password is stored in plain text internally (no hashing), so this account is intended only for
development and testing. Do not use it in production.

Example OIDC client registration for ID Austria:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          ida:
            client-id: "https://example.com"
            client-secret: "your-client-secret"
            client-name: "IDA"
            scope: "openid, profile"
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "https://example.com/login/oauth2/code/ida"
        provider:
          ida:
            issuer-uri: "https://idp.id-austria.gv.at"
```

Claim extraction and credential payload construction are implemented in
[`DataExtractor.kt`](http/src/main/kotlin/at/asitplus/wallet/backend/config/DataExtractor.kt) and
[`OidcIssuerCredentialDataProvider.kt`](http/src/main/kotlin/at/asitplus/wallet/backend/data/OidcIssuerCredentialDataProvider.kt).

## Digital Credentials API

The service includes an endpoint for browser-based issuance via the
[W3C Digital Credentials API](https://wicg.github.io/digital-credentials/). The index page
generates DC API payloads alongside the standard OpenID4VCI credential offer QR codes.

- `/dcapi/create-request/{nonce}` returns a pre-authorized `CredentialCreationOptions` payload
  that a browser page can pass directly to `navigator.credentials.create()`.
- The static file `dcapi.js` contains the client-side logic for triggering the DC API call from
  the index page.

This path skips the redirect-based authorization code flow and is suited for same-device issuance
in a browser that supports the Digital Credentials API.

## HAIP URL Schemes

The service uses custom URL schemes that follow the
[HAIP (High Assurance Interoperability Profile)](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile.html)
specification for wallet invocation:

- `haip-vci://` — credential offer deep links for HAIP-compliant wallets
- `haip-vp://` — verifiable presentation request deep links (EU PID login)
- `av://` — credential offer deep links for Age Verification

These are used in the QR codes and redirect URIs generated by the index and login pages.

## Database

For local development and tests, H2 is enough:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
  datasource:
    url: "jdbc:h2:mem:userstore"
```

For deployments, configure PostgreSQL:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
  datasource:
    url: "jdbc:postgresql://server:port/db_name"
    driver-class: "org.postgresql.Driver"
    username: username
    password: password
    hikari:
      auto-commit: false
```

## Deployment Notes

Spring Boot server settings can be configured in the usual way:

```yaml
server:
  port: 8080
  servlet:
    context-path: /
  forward-headers-strategy: framework
```

If the service is deployed under a context path, some wallet metadata lookups may still need to be
available from the web server root. One Apache rewrite setup is:

```apacheconf
RewriteRule ^jwt-vc-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^mdoc-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^jar-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^oauth-authorization-server/(.*)$ /$1/.well-known/oauth-authorization-server [L]
RewriteRule ^openid-credential-issuer/(.*)$ /$1/.well-known/openid-credential-issuer [L]
RewriteRule ^openid-configuration/(.*)$ /$1/.well-known/openid-configuration [L]
```

Logging example:

```yaml
logging:
  level:
    at.asitplus: DEBUG
  file:
    name: service.log
```

Spring Boot Admin client example:

```yaml
spring:
  application:
    name: "Wallet Backend"
  boot:
    admin:
      client:
        url: http://localhost:9900
        instance:
          metadata:
            user.name: actuator
            user.password: changeit
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: "*"
```

The service protects `/actuator/**` with a dedicated security filter chain:

- **SBA not configured** (`spring.boot.admin.client.enabled` absent or `false`): all actuator access
  is denied with HTTP 403.
- **SBA configured** (`spring.boot.admin.client.enabled=true`) and both `user.name` / `user.password`
  set: the actuator endpoints require HTTP Basic auth using exactly those credentials. Requests without
  credentials receive HTTP 401; wrong credentials receive HTTP 401; authenticated users without the
  `ACTUATOR` role (including the demo `user` account) receive HTTP 403.

Spring Boot Admin reads the same `user.name` / `user.password` metadata when it polls the actuator, so
the credentials only need to be configured once.

### Optional Spring Cloud Config Client

The service includes the Spring Cloud Config client, but it stays inactive unless you enable a config
import explicitly. This keeps local development and standalone deployments working without a config
server.

To enable remote configuration from an internal Spring Cloud Config Server, set these environment
variables:

```bash
SPRING_CLOUD_CONFIG_ENABLED=true
SPRING_CLOUD_CONFIG_URI=http://internal-config-service:8888
```

Recommended additions:

- `SPRING_CONFIG_IMPORT=optional:configserver:` if you want to override the default import value explicitly
- `SPRING_APPLICATION_NAME=wallet-issuing-backend` if your deployment overrides the application name and
  you want the config server lookup key to stay stable.
- `SPRING_PROFILES_ACTIVE=<profile>` if the remote config is profile-specific.

Behavior notes:

- With `optional:configserver:`, the service still starts if the internal config service is unavailable.
- If you want startup to fail when the config service cannot be reached, remove `optional:` and use
  `SPRING_CONFIG_IMPORT=configserver:`.

## Project Layout

```text
http/src/main/kotlin/at/asitplus/wallet/backend
  config/       Spring and VC-K wiring, key loading, credential claim builders
  controller/   OpenID4VCI, OAuth2, login, status-list, and revocation endpoints
  data/         JPA entities, repositories, and VC-K credential data provider
  service/      Revocation and status-list scheduling services
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Contributing

External contributions are greatly appreciated! Be sure to observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).
In particular, external contributions to this project are subject to the A-SIT Plus Contributor License Agreement (see also [CONTRIBUTING.md](CONTRIBUTING.md)).

<p align="center">
The Apache License does not apply to the logos, (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
