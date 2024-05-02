# Wallet Backend Service

This is the backend service for provisioning and revoking [Verifiable Credentials](https://w3c.github.io/vc-data-model/), representing `IdAustriaCredentials` or anything else.

The default public key that signs the credentials is:

```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaCUPdgNqCIFLVXE8yn5lZGaYjbyC
ys0go5xhPtbXj0X2jNAUUOddCh8eYoB9dO/ARUyBbccxKmNxO01kd8+/Tg==
-----END PUBLIC KEY-----
```

with it's `kid` of `did:key:mEpBoJQ92A2oIgUtVcTzKfmVkZpiNvILKzSCjnGE+1tePRfaM0BRQ510KHx5igH1078BFTIFtxzEqY3E7TWR3z79O`.

## REST API

The OpenAPI spec is available at <http://localhost:8080/v3/api-docs>, the Swagger UI at <http://localhost:8080/swagger-ui/index.html>. Note that access to these resources should be restricted in public deployments.

`GET /credentials/status/current` returns a simple list of currently available revocation lists.

`GET /credentials/status/{year}` returns the revocation list in a VC-compatible format, that is [Revocation List 2020](https://w3c-ccg.github.io/vc-status-rl-2020/). It is essentially a bitstring in which a bit is set if the verifiable credential with this `statusListIndex` is revoked. The bitstring is then zlib compressed and base64 encoded to be transported inside an JWS. The `{year}` variable may be filled with an entry from the `current` revocation lists, or is contained in an issued ID.

Sample revocation list (transported as a JWS in compact representation, exploded here for readability):

```
{
  "kid": "did:key:mEpBoJQ92A2oIgUtVcTzKfmVkZpiNvILKzSCjnGE+1tePRfaM0BRQ510KHx5igH1078BFTIFtxzEqY3E7TWR3z79O",
  "typ": "JWT",
  "alg": "ES256"
}
.
{
  "vc": {
    "id": "http://localhost:8080/credentials/status/1",
    "type": [
      "VerifiableCredential",
      "RevocationList2020"
    ],
    "issuer": "did:key:mEpBoJQ92A2oIgUtVcTzKfmVkZpiNvILKzSCjnGE+1tePRfaM0BRQ510KHx5igH1078BFTIFtxzEqY3E7TWR3z79O",
    "issuanceDate": "2022-02-23T14:46:10.969279Z",
    "expirationDate": "2022-02-26T02:46:10.969281Z",
    "credentialSubject": {
      "type": "RevocationList2020",
      "id": "http://localhost:8080/credentials/status/1#list",
      "encodedList": "eJxjBAAAAgAC"
    }
  },
  "sub": "http://localhost:8080/credentials/status/1#list",
  "nbf": 1645627570,
  "iss": "did:key:mEpBoJQ92A2oIgUtVcTzKfmVkZpiNvILKzSCjnGE+1tePRfaM0BRQ510KHx5igH1078BFTIFtxzEqY3E7TWR3z79O",
  "exp": 1645843570,
  "jti": "http://localhost:8080/credentials/status/1"
}
```


### Debug

These endpoints are only enabled if `backend.debug.enabled=true` is set.

`GET /debug/credential/revoke?vcId={foo}` revokes a credential.

`GET /debug/credential/list` displays a web page with a list of issued credentials.


## Web API

`GET /` displays a general information page for new users.

`GET /help/wallet` displays a help page if the user scans a debug initialization QR Code with a standard camera app (instead of the Wallet App).

`GET /help/verify` displays a help page if the user scans a QR code displayed by the Wallet App for verification with a standard camera app (instead of the Verifier App).

## Configuration

The default configuration file included in this service is minimal, i.e. it sets the default profile `pupilid` and disables cloud configuration.

This means that for every deployment, the configuration file (`application.yml` or `application.properties`) should be explicit in setting all needed options.

There are several custom configuration properties, all under the key `backend`:

```yaml
backend:
  public-context: "http://localhost:8080"
  credentials:
    lifetime: PT60M
  revocation-list:
    lifetime: P7D
    regular-write-timeout: P5D
    dirty-check-rate: PT10M
    regular-check-rate: PT1H
    cache-path: cache/revocation-list/
  issuer-key: {{ KEY_CONFIG }}
  hsm-facade:
    enabled: false
  debug:
    enabled: true
    qr-code-size: 400
  cleanup:
    enabled: false
  authn:
    attestation: [...]
```

Options for revocation lists for Verifiable Credentials under `backend.revocation-list`:
 - `lifetime` to set the lifetime of a single revocation list, i.e. the validity of the Verifiable Credential which represents the revocation list for other credentials, defaults to `P7D`, i.e. 7 days.
 - `regular-write-timeout` to set the timeout after which a revocation list shall be written again, defaults to `P5D`, i.e. 5 days.
 - `dirty-check-rate` to set the rate at which the service shall check for dirty (i.e. where a credential has been revoked) revocation lists that need to be written, defaults to `PT10M`, i.e. 10 minutes.
 - `regular-check-rate` to set the rate at which the service shall check for outdated revocation lists (see `regular-write-timeout`) that need to be written, defaults to `PT1H`, i.e. 1 hour.
 - `cache-path` to set the path at which the revocation lists shall be written to and read from, e.g. `cache/revocation-list/`

Key Attestation is considered a key feature, but it can be disabled for testing:

```yaml
backend:
  authn:
    attestation:
      noop: true
```

```yaml
backend:
  authn:
    attestation:
      verification-offset: PT10M
      android:
        package-name: android.package.name
        signature-digests:
          - ...
        patch-level:
          year: 2020
          month: 01
        android-version: 9000
        require-strong-box: false
        ignore-leaf-validity: false
      ios:
        bundle-identifier: ios.bundle.identifier
        team-identifier: DEADBEEF42
        sandbox: false
        ios-version: 14
```

There are more options for configuring validation of the Key Attestation provided by clients, under `backend.authn.attestation`:
- `verification-offset` may add some offset to temporal validity checks, to account for slightly off clocks.
- `android.package-name` defines the expected name of the client application running on Android.
- `android.signature-digests` is a list of hex encoded SHA-256 fingerprints of valid App signing certificates.
- `android.patch-level` is optional, e.g. `android.patch-level.year=2020` and `android.patch-level.month=01`, omitting the values defines no minimum patch level.
- `android.android-version` is also optional, e.g. `9000` for Android 9, or `4200` for Android 4.2.
- `android.require-strong-box` is `false` by default, may be set to `true` to enforce StrongBox-compatible hardware on Android clients.
- `android.ignore-leaf-validity` is `false` by default, may be set to `true` to ignore the timely validity of the attestation leaf certificate (looking at you, Samsung!).
- `ios.bundle-identifier` is the App bundle identifier, similar to Android package name, e.g. `ios.wallet.app`.
- `ios.team-identifier` is the Apple Development Team identifier of the valid client App.
- `ios.sandbox` may be set to `true` to enable "development" stage attestation, or to `false` to enable "production" stage attestation.
- `ios.ios-version` defines optionally the minimal iOS version running on devices, e.g. `14.1`

Alternative configuration of the attribute source (which attributes to issue for the Wallet App):

```yaml
backend:
  attribute-source:
    type: RANDOM
    random:
      photo-location: file:photos/
```

```yaml
backend:
  attribute-source:
    type: EIDAS
```

Alternative configuration for all cryptographic keys (e.g. for signing verifiable credentials or in Client TLS connections), depicted as `{{ KEY_CONFIG }}` above:

```yaml
type: MEMORY
```

```yaml
type: FILE
file:
  private-key: file:issuer-key-private.pem
  public-key: file:issuer-key-public.pem
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

```yaml
type: HSMFACADE
hsmfacade:
  key-store-name: keystore-at-hsmfacade
  key-store-alias: key1
```

Using keys from a remote HsmFacade service also requires setting the general connection properties:

```yaml
backend:
  hsmfacade:
    enabled: true
    root-certificate: file:/data/hsm-facade-root.pem
    hostname: hsmf.example.com
    port: 8686
    username: user
    password: password
    timeout: 30
```

Alternative configuration for all trust configurations (e.g. in TLS connections), depicted as `{{ TRUST_CONFIG }}` above:

```yaml
type: SYSTEM
```

```yaml
type: KEYSTORE
truststore:
  path: file:/some/path/keystore.p12
  type: PKCS12
  provider: BC                     # may be null
  password: changeit               # may be null
```

### OpenID for Verifiable Credential Issuance

Clients may use [OpenID for Verifiable Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) to retrieve credentials from this service.

The credential issuer metadata is available at `/.well-known/openid-credential-issuer`, the credential endpoint at `/credential`.

Authorization endpoint (`/authorize`), and the token endpoint (`/token`) shall be available at the authorization server, i.e. the ID Austria System.


```yaml
authorization-server:
  public-context: "https://eid.egiz.gv.at/"
  authorization-endpoint: "https://eid.egiz.gv.at/idp/profile/oidc/authorize"
  token-endpoint: "https://eid.egiz.gv.at/idp/profile/oidc/token"
  userinfo-endpoint: "https://eid.egiz.gv.at/idp/profile/oidc/userinfo"
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


Configuration to use dedicated PGSQL database:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: "at.asitplus.wallet.backend.data.FixedPostgreSQLDialect"
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

### Cleanup

Old entries of the database (i.e. expired bindings, expired credentials) can be deleted periodically, if the configuration is enabled (it is disabled by default):

```yaml
backend:
  cleanup:
    enabled: true
    bindings-scheduling-rate: PT24H
    bindings-expiration-days: 30
    credentials-scheduling-rate: PT24H
    credentials-expiration-days: 30
```

The scheduling rate shall be configured in a [Java Duration](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-) compatible format (e.g. `PT24H`).

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

### Cloud Configuration

Configuration to pull the configuration from a [Spring Cloud Config Server](https://cloud.spring.io/spring-cloud-config/reference/html/):

```yaml
spring:
  profiles:
    active: pupilid
  application:
    name: wallet
  config:
    import: optional:configserver:http://localhost:9910/
  cloud:
    config:
      enabled: true
```

### Error Handling

By default, Spring Boot handles errors thrown by our code and transforms them into a JSON document in the form of

```json
{
  "error": "Internal Server Error",
  "path": "/credentials/status/1",
  "status": 500,
  "timestamp": "2022-05-18T08:24:17.239+00:00"
}
```

If these configuration properties are set:

```yaml
server:
  error:
    include-exception: true
    include-message: always
```

and the application throws an exception like `IllegalArgumentException("foo")`, the response contains:

```json
{
  "error": "Internal Server Error",
  "exception": "java.lang.IllegalArgumentException",
  "message": "foo",
  "path": "/credentials/status/1",
  "status": 500,
  "timestamp": "2022-05-18T08:24:17.239+00:00"
}
```

### Logging

MDC-based assigment of unique transaction IDs for each incoming request is supported (into the variable `txID`), but requires a customized logger pattern, e.g.:

```yaml
logging:
  pattern:
    file: "%d{dd-MM-yyyy HH:mm:ss.SSS} [%X{txID:-00000000-0000-0000-0000-000000000000}] %-5level %-50logger{50}:%-4line - %msg%n"
    console: "%d{dd-MM-yyyy HH:mm:ss.SSS} [%X{txID:-00000000-0000-0000-0000-000000000000}] %-5level %-50logger{50}:%-4line - %msg%n"
```
