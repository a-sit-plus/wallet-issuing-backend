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

`GET /ca/1` returns the X.509 Certificate for the key pair, that signs device binding certificates, if the PKI implementation supports this, i.e. only for the internal PKI (see below for configuration). When the external AERA service is used to sign device binding certificates, the CA certificate is available at an external URL.

`GET /crl/1` returns the X.509 Certificate Revocation List, if the PKI implementation supports this, i.e. only for the internal PKI (see below for configuration). When the external AERA service is used to sign device binding certificates, the CRL is available at an external URL (specified in the issued certificates).

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

### Device Binding

`POST /binding/start` initiates the device binding process in the Wallet App.

`POST /binding/create` returns the device binding to the App.

`POST /binding/confirm` finishes the device binding process.

The call to `/binding/start` requires authentication with an external nonce, to be sent in the header `X-Auth-ExtNonce`. The Wallet App extracts this nonce from a QR Code displayed by this service. Alternatively, the client may include a session identifier in the header `X-Auth-Token` after logging in with OIDC, see below.

Subsequent requests, to `/binding/create` and `/binding/confirm`, need to include the session identifier in the header `X-Auth-Token` (which in turn has been set by this service in the first response).

Request from client:

```
POST http://localhost:8080/binding/start
X-Auth-ExtNonce: 413ED1210D70ECDBE27B451936C753A9C2E2994BAC58A60E1348CC3093EA6BC9

{
  "deviceName": "Pixel 3"
}
```

Response from service:

```
HTTP/1.1 200
X-Auth-Token: c703200e-3a03-4157-beb8-ca0d550ba56b

{
  "challenge": "6j2a9M7P1J9bOUuGe5Tpto7Ylz+2DtbH54jdHh2YO/Y=",
  "subject": "CN=Binding-random-value,O=Wallet",
  "keyType": "EC"
}
```

Client creates a new key pair and a PKCS#10 certification request for the key pair (with the given `subject`), and includes its attestation statements (either [Android Key Attestation](https://developer.android.com/training/articles/security-key-attestation) or [Apple App Attestation](https://developer.apple.com/documentation/devicecheck/validating_apps_that_connect_to_your_server)).

Request from client (newlines for display purposes only):

```
POST http://localhost:8080/binding/create
X-Auth-Token: c703200e-3a03-4157-beb8-ca0d550ba56b

{
  "challenge": "6j2a9M7P1J9bOUuGe5Tpto7Ylz+2DtbH54jdHh2YO/Y=",
  "csr": "MIHNMHQCAQAwEjEQMA4GA1UEAwwHU3ViamVjdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA
          0IABEgRPVMGMgkAilfugC/3mncR8mot9gsC4/bJmlW0ugpxRMiIgi3srUmIlCMgTN9hMP
          GEAXdPd0Hvize9o9vuezagADAKBggqhkjOPQQDAgNJADBGAiEA2l1XvS1c1j/f6SN0AwT
          dJZNvTwnZP3tRQyNpzQMZMnMCIQDepERQmECr3mqFGS4AQzSnWpwZZBjGtmU1NWiK/E92
          Ew==",
  "attestationCerts": [
    "MIICpjCCAkqgAwIBAgIBATAMBggqhkjOPQQDAgUAMD8xEjAQBgNVBAwMCVN0cm9uZ0JveDEpMC
    cGA1UEBRMgMDY4NDJmODRiY2JhZGJkMTk2NDA1YmZkNmE2MzQ5ZWIwHhcNNzAwMTAxMDAwMDAwW
    hcNNDgwMTAxMDAwMDAwWjAfMR0wGwYDVQQDExRBbmRyb2lkIEtleXN0b3JlIEtleTBZMBMGByqG
    SM49AgEGCCqGSM49AwEHA0IABD1auUFhE6prEafZ90OHrq6CPZS6+hTJ3HLmeqOw2OCytf0NaCL
    Lz6DMLe1GV3EWxCDGi1UHe10UO5zwx/2OyFCjggFTMIIBTzAOBgNVHQ8BAf8EBAMCB4AwggE7Bg
    orBgEEAdZ5AgERBIIBKzCCAScCAWQKAQICAWQKAQIEJDQ1Y2ZiYWRhLWE5NTItNGVhNS05M2JjL
    WYyZWQzNjVlOGRiOAQAMEy/hUVIBEYwRDEeMBwEFmF0LmFzaXRwbHVzLmJpb21ldHJpY3MCAgFA
    MSIEIEFfrT4RcXh0HaTOlPpeZXwPjA8Z06Nw7B6ZSBe/nLXrMIGioQUxAwIBAqIDAgEDowQCAgE
    ApQUxAwIBBKoDAgEBv4N4AwIBAr+FPgMCAQC/hUBMMEoEIA9udcgBg7XewHSwBU1CcemTievksT
    awgZ3h8VC6D/nXAQH/CgEABCBmOJbI61T3+Ji7mfx/sIEdmd7/o4Vwizd3ttcqU2kaH7+FQQUCA
    wHUwL+FQgUCAwMVf7+FTgYCBAE0ZaG/hU8GAgQBNGWcMAwGCCqGSM49BAMCBQADSAAwRQIgae9O
    Oc3NwhakcZCAeA9IXRWyBauT47ADg9Dy9EtasnMCIQDH/fwrI3O45Oqo6OQdBpqNGI77GprvrXo
    Ks6kqldIjmA="
  ],
  "deviceName": "Pixel 3"
}
```

The server verifies the CSR and sends it to the configured PKI service to get a signed certificate. The server also validates the attestation statement, and wraps the public key of the client in the structure `attestedPublicKey` (and signs it).

Response from server (newlines for display purposes only):

```
HTTP/1.1 200

{
  "certificate": "MIIBFzCBvaADAgECAgjWVAvsBy5UXDAKBggqhkjOPQQDAjASMRAwDgYDVQQDD
                  AdTdWJqZWN0MB4XDTIyMDIyMjE1MzM0NVoXDTIyMDIyMjE1MzQ0NVowETEPMA
                  0GA1UEAwwGSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPnNczNY
                  C/8QwBXZrKqBDdSwvzHQQKOi8UWpsy+33uW2zJorQXgAljj0qxCmVlgPs5FAo
                  F7zzQbM/4pF1DfK+6jAKBggqhkjOPQQDAgNJADBGAiEAs9sOHPs3vuHP5zbaT
                  UTxC2j4a/afLfW1GlMJdHGwsToCIQCiAbOdx7Bth+T7MjQhv9hsYo0zDzuMBv
                  xYKF+pbNtJdg==",
  "attestedPublicKey": "eyJhbGciOiJFUzI1NiJ9.eyJraWQiOiJkaWQ6a2V5Om1FcEFuUzFuQj
                        BXaVErVjFyMmRObWNoSGFSRVE1bUJpU1FsVGJqdXkxV0x2U1VzaTR3S
                        lNOdEJuMHZja2dNY1d1dFpXbEdOeGpSSUFTVkdRT1QyZk4zeGJiIn0.
                        oAQ27KKdyi6FpjXRC7wuLFaCrN6UoLOk1HGc42KhFju0U1IkR-khRLB
                        nmFMFD_K4yCkHXVWpz-YrUkLEoaQ2Bg"
}
```

The `attestedPublicKey` structure contains the public key of the device binding certificate:

```
{
  "alg": "ES256"
}
.
{
  "kid": "did:key:mEpB5VX93//pdfAWFspYll9BCmrmICeRgiBqn8QvMSaB/iA/Bf6jrMBAAWYDOE2RNAOK81BdhvmQf+/TOhVyUsTAu",
}
```

Client confirms the binding process:

```
POST http://localhost:8080/binding/confirm
X-Auth-Token: b297b9fb-9501-4352-af69-5856ad477a64

{
  "success": true
}
```

Response from server:

```
HTTP/1.1 200

{
  "success": true
}
```

Note that the server does not set the header `X-Auth-Token` in the response if the client has sent one in the request.

The `X-Auth-Token` from this device binding process can be used by clients to start the issuing process without any additional authentication.

### OpenID for Verifiable Credential Issuance

For EIDAS deployments, the endpoint `POST /eidasid/issue` is available instead, with the same semantics as above.

Alternatively, clients may use [OpenID for Verifiable Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) to retrieve credentials from this service. The client needs to get a device binding first (see above) and then use the session identifier.

The credential issuer metadata is available at `/.well-known/openid-credential-issuer`, the authorization endpoint at `/authorize`, the token endpoint at `/token`, and the credential endpoint at `/credential`.

### Revocation

Clients are external services, and authenticated with an API key. The API key shall be sent in the header `X-API-Key`.

`POST /revoke/binding` revokes a device binding, by the pupil's `bpk` and/or `deviceId`.

`POST /revoke/pupilid` revokes all PupilId instances for one pupil, specified by their `bpk`.

`GET /revoke/devices?bpk={foo}` lists all devices for the pupil with `bpk`.

Request from the client:

```
POST http://localhost:8080/revoke/binding
X-API-Key: 8tgvj6tji38fnj75hzc4zuhd6dznnqkn

{
  "bpk": "BF:j/NxdRQhp+tNyE9WhHdBSYuy3hA=",
  "deviceId": "81113d6f-aa19-438a-96e7-abd1ee56d5ae"
}
```

Response from the server:

```
HTTP/1.1 200

{
  "count": 1
}
```

### Debug

These endpoints are only enabled if `backend.debug.enabled=true` is set.

`GET /debug/nonce` returns a nonce that is valid to use for `X-Auth-ExtNonce` during the binding creation process.

`GET /debug/credential/revoke?vcId={foo}` revokes a credential.

`GET /debug/initialize` shows a QR code that can be used by the Wallet App to get a nonce to use as the authentication token during the device binding process.

`GET /debug/credential/list` displays a web page with a list of issued credentials.

`GET /debug/credential/qrcode` reads attributes values from request parameters `firstname`, `lastname`, `dateofbirth` and creates a QR code that can be scanned from the Wallet App to load a credential with these attribute values. Beware: Works only in EIDAS deployments! 

## Web API

`GET /` displays a general information page for new users.

`GET /help/wallet` displays a help page if the user scans a debug initialization QR Code with a standard camera app (instead of the Wallet App).

`GET /help/verify` displays a help page if the user scans a QR code displayed by the Wallet App for verification with a standard camera app (instead of the Verifier App).

#### EIDAS

For EIDAS deployments, the web page `GET /eidasid/initialize` is available, where the web browser displays a QR code that can be scanned by the Wallet App to load EIDAS credentials. This endpoint is available after the user has been logged in with OAuth2 (link on `/login`).

## Configuration

The default configuration file included in this service is minimal, i.e. it sets the default profile `pupilid` and disables cloud configuration.

This means that for every deployment, the configuration file (`application.yml` or `application.properties`) should be explicit in setting all needed options.

There are two profiles implemented in this service: `pupilid` (the default) and `eidasid` for EIDAS deployments.

When the profile `eidasid` is active (e.g. set with `spring.profiles.active=eidasid`), this service expects the user to log in on a desktop device with their E-ID. Then it displays a QR Code, that can be scanned with the Wallet App. The attributes issued as verifiable credentials match the attributes from the E-ID login.

There are several custom configuration properties, all under the key `backend`:

```yaml
backend:
  public-context: "http://localhost:8080"
  credentials:
    lifetime: PT60M
    one-credential-per-device-binding: true
    pictures:
      compress: true
      format: webp
      quality: 30
      scale: true
      height: 154
      width: 120
      path-to-jni-lib: "/data/libwebp_jni.so"
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
    challenge-timeout-seconds: 60
    api-keys:
      - name: External Caller
        key: asdfasdf
    device-binding:
      type: INTERNAL
    attestation: [...]
  attribute-source:
    type: RANDOM
  pki:
    type: INTERNAL
    cert-validity-days: 182
    internal:
      issuer-name: "CN=WalletBackend"
      key: {{ KEY_CONFIG }}
```

Options for credential issuance under `backend.credentials`:
 - Set `backend.credentials.one-credential-per-device-binding=true` if existing credentials for the same device binding should be revoked when a new credential is issued (e.g. as it is the case for PupilIds).

Options for pictures in credentials under `backend.credentials.pictures`:
 - `compress=true` to enable compressing
 - `format=webp` to enable compressing pictures into WebP format
 - `quality=30` to set the level of compression
 - `scale=true` to enable scaling
 - `height=154` to set the height of the scaled picture
 - `width=120` to set the width of the scaled picture
 - `pathLibJni=/data/libwebp_jni.so` to set the filename of the WebP-JNI library. If it is not specified, this service will try to load the library `webp-jni` using the default system paths.
 - `pathLibWebp=/data/libwebp.so.7` to set the filename of the WebP library. If it is not specified, this service will try to load the library `webp` using the default system paths.
 - `pathLibWebp=/data/libsharpyuv.so.0` to set the filename of the SharpYUV library. If it is not specified, this service will try to load the library `sharpyuv` using the default system paths.

Please note that the `libwebp_jni` (provided in `http/lib`) as well as `libwebp` is necessary to start this service.

Options for revocation lists for Verifiable Credentials under `backend.revocation-list`:
 - `lifetime` to set the lifetime of a single revocation list, i.e. the validity of the Verifiable Credential which represents the revocation list for other credentials, defaults to `P7D`, i.e. 7 days.
 - `regular-write-timeout` to set the timeout after which a revocation list shall be written again, defaults to `P5D`, i.e. 5 days.
 - `dirty-check-rate` to set the rate at which the service shall check for dirty (i.e. where a credential has been revoked) revocation lists that need to be written, defaults to `PT10M`, i.e. 10 minutes.
 - `regular-check-rate` to set the rate at which the service shall check for outdated revocation lists (see `regular-write-timeout`) that need to be written, defaults to `PT1H`, i.e. 1 hour.
 - `cache-path` to set the path at which the revocation lists shall be written to and read from, e.g. `cache/revocation-list/`

Alternative configuration for the device binding authentication (i.e. the validation of the ext. nonce provided by the Wallet App):

```yaml
backend:
  authn:
    device-binding:
      type: INTERNAL
```

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

Alternative configuration for the PKI service to use a remote instance of the AERA service to sign device binding certificates:

```yaml
backend:
  pki:
    type: AERA
    cert-validity-days: 182
    aera: {{ SERVICE_CONFIG }}
```

Configuration of external services, depicted as `{{ SERVICE_CONFIG }}` above:

```yaml
url: https://example.com/
client-tls: false
server-tls: true
key: {{ KEY_CONFIG }}      # may be null
trust: {{ TRUST_CONFIG }}  # may be null
http-basic:                # may be null
  username: bar
  password: foo
api-key: asdfasdf          # may be null
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

```yaml
type: REMOTE
remote:
  key-name: key1
  certificate: "file:/path/to/certificate/containing/public/key.cer"
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

Using keys from a remote crypto service also requires setting the general connection properties:

```yaml
backend:
  remote-crypto:
    enabled: true
    hostname: remote.example.com
    port: 443
    api-key: "YOUR_API_KEY"
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

### ID Austria Authentication

When the profile `authnida` or `eidasid` is active, clients may authenticate using OIDC and the ID Austria system.

```yaml
spring:
  profiles:
    active: authnida
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
