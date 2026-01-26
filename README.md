# Wallet Issuing Service (IDA)

This service implements OpenID for Verifiable Credential Issuance (OpenID4VCI) using [VC-K](https://github.com/a-sit-plus/vck) to issue Verifiable Credentials to compatible wallets.

## Main Features

- OpenID4VCI issuer endpoints for credential issuance
- Configurable credential lifetime and issuer signing keys
- Revocation list generation and refresh scheduling
- Spring Boot service with standard server, logging, and database configuration

## Quick Start

Build and run the HTTP service:

```bash
./gradlew :http:bootRun
```

Build an executable jar:

```bash
./gradlew :http:bootJar
```

## Web API

`GET /` displays a general information page for new users.

## Configuration

The default configuration file included in this service is minimal, i.e. it sets the default profile `pupilid` and disables cloud configuration.

This means that for every deployment, the configuration file (`application.yml` or `application.properties`) should be explicit in setting all needed options.

There are several custom configuration properties, all under the key `backend`, defined in
`http/src/main/kotlin/at/asitplus/wallet/backend/config/BackendConfigurationProperties.kt`.

```yaml
backend:
  public-context: "http://localhost:8080/"
  credentials:
    lifetime: PT60M
  revocation-list:
    lifetime: P7D
    regular-write-timeout: P5D
    dirty-check-rate: PT10M
    regular-check-rate: PT1H
    path: cache/revocation-lists/
  metadata:
    name: "Issuing Service"
    logo: "https://wallet.a-sit.at/assets/images/logo.svg"
  issuer-key: {{ KEY_CONFIG }}
  verifier-key: {{ KEY_CONFIG }}
```

Options for the issuer public URL and credential lifetimes:
- `public-context` is the externally reachable base URL of this service (used in metadata and links sent to wallets).
- `credentials.lifetime` is the validity duration for issued credentials (ISO-8601 duration, e.g. `PT60M`, `P180D`).

Options for `backend.metadata`, to be used in OID4VCI metadata:
- `name`
- `logo`

Options for revocation lists for Verifiable Credentials under `backend.revocation-list`:
 - `lifetime` to set the lifetime of a single revocation list, i.e. the validity of the Verifiable Credential which represents the revocation list for other credentials, defaults to `P7D`, i.e. 7 days.
 - `regular-write-timeout` to set the timeout after which a revocation list shall be written again, defaults to `P5D`, i.e. 5 days.
 - `dirty-check-rate` to set the rate at which the service shall check for dirty (i.e. where a credential has been revoked) revocation lists that need to be written, defaults to `PT10M`, i.e. 10 minutes.
 - `regular-check-rate` to set the rate at which the service shall check for outdated revocation lists (see `regular-write-timeout`) that need to be written, defaults to `PT1H`, i.e. 1 hour.
 - `path` to set the directory for revocation list storage, e.g. `cache/revocation-lists/`

Alternative configuration for the issuer signing key under `backend.issuer-key`, or the verifier key under `backend.verifier-key`, depicted as `{{ KEY_CONFIG }}` above:

```yaml
type: MEMORY
```

```yaml
type: FILE
file:
  private-key: file:issuer-key-private.pem
  public-key: file:issuer-key-public.pem
  certificate: file:issuer-cert.pem
```

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

### OpenID for Verifiable Credential Issuance

Clients may use [OpenID for Verifiable Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) to retrieve credentials from this service.

Issuer metadata and endpoints:
- `/.well-known/openid-credential-issuer` (credential issuer metadata)
- `/.well-known/openid-configuration` (OpenID provider metadata)
- `/.well-known/oauth-authorization-server` (OAuth2 AS metadata)
- `/.well-known/jwt-vc-issuer` (JWT VC issuer metadata)
- `/authorize` (authorization endpoint)
- `/token` (token endpoint)
- `/credential` (credential endpoint)

OpenID4VCI handling is implemented with VC-K, and the issuer metadata uses `backend.public-context` as its base URL.

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
            issuer-uri: "https://eid.egiz.gv.at"
```

Clients may call this service at `http://example.com/login` to get redirected to the configured OpenID provider. After authentication at that external system, the client is redirected back to the URL configured above. This service then exchanges the received authorization code to an ID token at the OpenID provider. The client then gets set a session identifier in the header `X-Auth-Token`, that can be used to start the device binding process. 


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

