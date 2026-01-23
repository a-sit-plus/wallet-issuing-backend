# Wallet Issuing Service

TODO

## Web API

`GET /` displays a general information page for new users.

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
```

Options for revocation lists for Verifiable Credentials under `backend.revocation-list`:
 - `lifetime` to set the lifetime of a single revocation list, i.e. the validity of the Verifiable Credential which represents the revocation list for other credentials, defaults to `P7D`, i.e. 7 days.
 - `regular-write-timeout` to set the timeout after which a revocation list shall be written again, defaults to `P5D`, i.e. 5 days.
 - `dirty-check-rate` to set the rate at which the service shall check for dirty (i.e. where a credential has been revoked) revocation lists that need to be written, defaults to `PT10M`, i.e. 10 minutes.
 - `regular-check-rate` to set the rate at which the service shall check for outdated revocation lists (see `regular-write-timeout`) that need to be written, defaults to `PT1H`, i.e. 1 hour.
 - `cache-path` to set the path at which the revocation lists shall be written to and read from, e.g. `cache/revocation-list/`

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

The credential issuer metadata is available at `/.well-known/openid-credential-issuer`, the credential endpoint at `/credential`, the authorization endpoint at `/authorize`, and the token endpoint at `/token`.

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


Configuration to use dedicated PGSQL database:

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
