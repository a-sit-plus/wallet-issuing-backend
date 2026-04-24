# Wallet Issuing Backend

[![Assemble](https://github.com/a-sit-plus/wallet-issuing-backend/actions/workflows/assemble.yml/badge.svg)](https://github.com/a-sit-plus/wallet-issuing-backend/actions/workflows/assemble.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![VC-K](https://img.shields.io/badge/VC--K-5.12-blue)](https://github.com/a-sit-plus/vck)

OpenID for Verifiable Credential Issuance (OpenID4VCI) backend for issuing credentials to
EUDI Wallets and other compatible wallet implementations.

This service uses [VC-K](https://github.com/a-sit-plus/vck) as the credential, OpenID, and status-list
toolkit. It wires VC-K's issuer agent, OpenID4VCI issuer, OAuth2 authorization service, OpenID4VP
verifier, credential scheme mapper, and token status list support into a Spring Boot application that
can authenticate users, derive credential claims, and deliver wallet-ready credentials.

## What It Does

- Issues credentials over [OpenID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
  with authorization code, pushed authorization requests, token exchange, nonce, and credential endpoints.
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

- **Demo authentication is enabled.** `WebSecurityConfig` defines an in-memory `user` / `password` account
  with `withDefaultPasswordEncoder()`, disables CSRF protection, and permits every request except
  `/authorize` unless method-level annotations add further checks.
- **Sessions and transient issuance state are in memory.** Spring sessions use `MapSessionRepository`, and
  credential offers plus OpenID4VP login transactions use `DefaultMapStore`. Login state, offers, and
  transactions are lost on restart and are not shared across multiple service instances.
- **Default keys are ephemeral.** `backend.issuer-key.type` and `backend.verifier-key.type` default to
  `MEMORY`, which creates fresh self-signed key material on startup. A restart changes issuer/verifier
  identity unless file or keystore-backed keys are configured.
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
| `/login` | User login page for OIDC and EU PID based identity input |
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
  verifier-key:
    type: MEMORY
```

Key settings:

- `backend.public-context` is the externally reachable base URL. It is used in wallet-facing metadata,
  credential offers, redirects, and status-list URLs.
- `backend.credentials.lifetime` controls issued credential validity as an ISO-8601 duration, for example
  `PT60M`, `P7D`, or `P180D`.
- `backend.metadata.name` and `backend.metadata.logo` populate OpenID4VCI display metadata.
- `backend.issuer-key` signs issued credentials and status lists.
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
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: "*"
```

## Project Layout

```text
http/src/main/kotlin/at/asitplus/wallet/backend
  config/       Spring and VC-K wiring, key loading, credential claim builders
  controller/   OpenID4VCI, OAuth2, login, status-list, and revocation endpoints
  data/         JPA entities, repositories, and VC-K credential data provider
  service/      Revocation and status-list scheduling services
```

## Related Projects

- [VC-K](https://github.com/a-sit-plus/vck)
- [A-SIT Plus credentials collection](https://github.com/a-sit-plus/credentials-collection)
- [OpenID4VCI specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
