# Wallet Issuing Service

This service implements [OpenID for Verifiable Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) (OpenID4VCI) using [VC-K](https://github.com/a-sit-plus/vck) to issue Verifiable Credentials to compatible wallets. Users can log in with [ID Austria](https://www.id-austria.gv.at/) (or any other notified eIDAS scheme) or with their EU PID to provide their identity data. This service then converts that data into the requested credential, with several [Verifiable Credential schemes](https://github.com/a-sit-plus/credentials-collection) being supported.

## Main Features

- ✨ Full OpenID4VCI issuance stack: implements authorization code flow and pre-authorized flow for smooth wallet onboarding
- 🧭 Built‑in OAuth2 authorization server purpose‑built for OID4VCI (pushed authorization, authorize, and token endpoints)
- 🪪 EIDAS‑ready: converts identity data from the external ID Austria Service (or any other eIDAS‑notified e‑ID) into verifiable credentials
- 🧩 Standards‑driven metadata: Publishes OID4VCI issuer and OAuth2/OIDC metadata for automatic client configuration
- 🔐 Configurable credential lifetime and issuer signing keys
- 🔁 Revocation list generation with refresh scheduling
- ⚙️ Spring Boot service with standard server, logging, and database configuration

## Quick Start

Build and run the HTTP service:

```bash
./gradlew :http:bootRun
```

## Configuration

There are several custom configuration properties, all under the key `backend`, defined in
`http/src/main/kotlin/at/asitplus/wallet/backend/config/BackendConfigurationProperties.kt`.

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

Options for the issuer public URL and credential lifetimes:
- `public-context` is the externally reachable base URL of this service (used in metadata and links sent to wallets).
- `credentials.lifetime` is the validity duration for issued credentials (ISO-8601 duration, e.g. `PT60M`, `P180D`).

Options for `backend.metadata`, to be used in [OID4VCI metadata](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-12.2.4-2.10.1):
- `name` for `display.name`
- `logo` for `display.logo.uri`

Options for revocation lists for Verifiable Credentials under `backend.revocation-list`:
 - `lifetime` to set the lifetime of a single revocation list, i.e. the validity of the Verifiable Credential which represents the revocation list for other credentials, defaults to `P7D`, i.e. 7 days.
 - `regular-write-timeout` to set the timeout after which a revocation list shall be written again, defaults to `P5D`, i.e. 5 days.
 - `dirty-check-rate` to set the rate at which the service shall check for dirty (i.e. where a credential has been revoked) revocation lists that need to be written, defaults to `PT10M`, i.e. 10 minutes.
 - `regular-check-rate` to set the rate at which the service shall check for outdated revocation lists (see `regular-write-timeout`) that need to be written, defaults to `PT1H`, i.e. 1 hour.
 - `path` to set the directory for revocation list storage, e.g. `cache/revocation-lists/`

There are several options to configure the issuer signing key under `backend.issuer-key`, or the verifier key under `backend.verifier-key`:

Key type `memory`:

```yaml
type: MEMORY
```

will create an ephemeral key pair with a self-signed certificate.


Key type `file`:

```yaml
type: FILE
file:
  private-key: file:issuer-key-private.pem
  public-key: file:issuer-key-public.pem
  certificate: file:issuer-cert.pem
```

will load the private key, public key and certificate from `PEM` encoded files.

Key type `keystore`:

```yaml
type: KEYSTORE
keystore:
  path: file:/some/path/keystore.p12
  type: PKCS12
  provider: BC                     # may be null
  password: changeit               # may be null
  alias: key1
  alias-password: changeit         # may be null
```

will load a Java KeyStore object and use key and certificate from there.

### OpenID for Verifiable Credential Issuance

Issuer metadata and endpoints:
- `/.well-known/openid-credential-issuer` (credential issuer metadata)
- `/.well-known/openid-configuration` (OpenID provider metadata)
- `/.well-known/oauth-authorization-server` (OAuth2 AS metadata)
- `/.well-known/jwt-vc-issuer` (JWT VC issuer metadata)
- `/authorize` (authorization endpoint)
- `/token` (token endpoint)
- `/credential` (credential endpoint)

When this service is deployed under a context, e.g. `https://example.com/issuer`, the metadata files need to be accessible from the root of the webserver too (`https://example.com/jwt-vc-issuer/issuer`). One possible way to implement this requirement is the following `.htaccess` file placed into the root of the webserver:

```
RewriteRule ^jwt-vc-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^mdoc-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^jar-issuer/(.*)$ /$1/.well-known/jwt-vc-issuer [L]
RewriteRule ^oauth-authorization-server/(.*)$ /$1/.well-known/oauth-authorization-server [L]
RewriteRule ^openid-credential-issuer/(.*)$ /$1/.well-known/openid-credential-issuer [L]
RewriteRule ^openid-configuration/(.*)$ /$1/.well-known/openid-configuration [L]
```


### ID Austria

To use the issuing process, clients need to authenticate using ID Austria first, see configuration below:

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

Note that any other OpenID Provider may be used to load the user's data.

### Server

This service starts an internal Tomcat server, that can be configured in this way:

```yaml
server:
  port: 8080
  servlet:
    context-path: /
  forward-headers-strategy: framework
```

### Logging

This Spring Boot service can be configured to log to a file:

```yaml
logging:
  level:
    at.asitplus: DEBUG
  file:
    name: service.log
```

### Database

Configuration to use an in-memory H2 database for deployments in debug environments:

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


Configuration to use dedicated Postgres database:

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

### Spring Boot Admin Client

Configuration to connect to a [Spring Boot Admin Server](https://github.com/codecentric/spring-boot-admin):

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
