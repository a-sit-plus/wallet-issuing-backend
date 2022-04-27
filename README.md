# PupilId Backend Service

This is the backend service for provisioning and revoking PupilIds.

Default public key for issuing credentials is:

```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaCUPdgNqCIFLVXE8yn5lZGaYjbyC
ys0go5xhPtbXj0X2jNAUUOddCh8eYoB9dO/ARUyBbccxKmNxO01kd8+/Tg==
-----END PUBLIC KEY-----
```

with it's `kid` of `did:key:mEgACaCUPdgNqCIFLVXE8yn5lZGaYjbyCys0go5xhPtbXj0U=`.

## Endpoints

The OpenAPI spec is available at <http://localhost:8080/v3/api-docs>, the Swagger UI at <http://localhost:8080/swagger-ui/index.html>.

`GET /crl/1` returns the X.509 Certificate Revocation List, if the PKI implementation supports this (see below for configuration), i.e. only for the internal PKI. When AERA is used to sign device binding certificates, the CRL is available at an external URL.

`GET /credentials/status/1` returns the revocation list in a VC-compatible format, i.e. [Revocation List 2020](https://w3c-ccg.github.io/vc-status-rl-2020/).

Sample revocation list (transported as a JWS in compact representation, exploded here for readability):

```
{
  "kid": "did:key:mEgACaCUPdgNqCIFLVXE8yn5lZGaYjbyCys0go5xhPtbXj0U=",
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
    "issuer": "did:key:mEgACaCUPdgNqCIFLVXE8yn5lZGaYjbyCys0go5xhPtbXj0U=",
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
  "iss": "did:key:mEgACaCUPdgNqCIFLVXE8yn5lZGaYjbyCys0go5xhPtbXj0U=",
  "exp": 1645843570,
  "jti": "http://localhost:8080/credentials/status/1"
}
```

`GET /help/wallet` displays a help page if the user scans a debug initialization QR Code with a standard camera app (instead of the Wallet App).

`GET /help/verify` displays a help page if the user scans a QR code displayed by the Wallet App for verification with a standard camera app (instead of the Verifier App).

### Device Binding

The call to `/binding/start` requires authentication with a Nonce extracted from a QR Code displayed by ECO (or this service, in the EIDAS deployment), to be sent in the header `X-Auth-ExtNonce`. The second call, to `/binding/create` needs to include the session identifier to be sent in the header `X-Auth-Token` (which in turn has been set by the service in the first response).

`POST /binding/start` initiates the device binding process in the App. User needs to scan a QR Code with a nonce first to be authorized to access this endpoint.

`POST /binding/create` returns the device binding to the App. User is authenticated through the session established by the call to `/binding/start`.

`POST /binding/confirm` finishes the device binding process. User is authenticated through the session established by the call to `/binding/start`.

Client needs to scan the QR Code from ECO first, to get a value for `X-Auth-ExtNonce`.

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

Client creates a new key pair and a PKCS#10 certification request for the key pair (with the given `subject`), and includes its attestation statements (either Android Key Attestation or Apple App Attestation).

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
    "MIICpjCCAkqgAwIBAgIBATAMBggqhkjOPQQDAgUAMD8xEjAQBgNVBAwMCVN0cm9u
     Z0JveDEpMCcGA1UEBRMgMDY4NDJmODRiY2JhZGJkMTk2NDA1YmZkNmE2MzQ5ZWIw
     HhcNNzAwMTAxMDAwMDAwWhcNNDgwMTAxMDAwMDAwWjAfMR0wGwYDVQQDExRBbmRy
     b2lkIEtleXN0b3JlIEtleTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABD1auUFh
     E6prEafZ90OHrq6CPZS6+hTJ3HLmeqOw2OCytf0NaCLLz6DMLe1GV3EWxCDGi1UH
     e10UO5zwx/2OyFCjggFTMIIBTzAOBgNVHQ8BAf8EBAMCB4AwggE7BgorBgEEAdZ5
     AgERBIIBKzCCAScCAWQKAQICAWQKAQIEJDQ1Y2ZiYWRhLWE5NTItNGVhNS05M2Jj
     LWYyZWQzNjVlOGRiOAQAMEy/hUVIBEYwRDEeMBwEFmF0LmFzaXRwbHVzLmJpb21l
     dHJpY3MCAgFAMSIEIEFfrT4RcXh0HaTOlPpeZXwPjA8Z06Nw7B6ZSBe/nLXrMIGi
     oQUxAwIBAqIDAgEDowQCAgEApQUxAwIBBKoDAgEBv4N4AwIBAr+FPgMCAQC/hUBM
     MEoEIA9udcgBg7XewHSwBU1CcemTievksTawgZ3h8VC6D/nXAQH/CgEABCBmOJbI
     61T3+Ji7mfx/sIEdmd7/o4Vwizd3ttcqU2kaH7+FQQUCAwHUwL+FQgUCAwMVf7+F
     TgYCBAE0ZaG/hU8GAgQBNGWcMAwGCCqGSM49BAMCBQADSAAwRQIgae9OOc3Nwhak
     cZCAeA9IXRWyBauT47ADg9Dy9EtasnMCIQDH/fwrI3O45Oqo6OQdBpqNGI77Gprv
     rXoKs6kqldIjmA="
  ],
  "deviceName": "Pixel 3"
}
```

Response from server (newlines for display purposes only):

```
HTTP/1.1 200
X-Auth-Token: b297b9fb-9501-4352-af69-5856ad477a64

{
  "certificate": "MIIBFzCBvaADAgECAgjWVAvsBy5UXDAKBggqhkjOPQQDAjASMRAwDgYDVQQDD
                  AdTdWJqZWN0MB4XDTIyMDIyMjE1MzM0NVoXDTIyMDIyMjE1MzQ0NVowETEPMA
                  0GA1UEAwwGSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPnNczNY
                  C/8QwBXZrKqBDdSwvzHQQKOi8UWpsy+33uW2zJorQXgAljj0qxCmVlgPs5FAo
                  F7zzQbM/4pF1DfK+6jAKBggqhkjOPQQDAgNJADBGAiEAs9sOHPs3vuHP5zbaT
                  UTxC2j4a/afLfW1GlMJdHGwsToCIQCiAbOdx7Bth+T7MjQhv9hsYo0zDzuMBv
                  xYKF+pbNtJdg==",
  "attestedPublicKey": "eyJhbGciOiJFUzI1NiJ9.eyJwayI6IkJFWHlSS3JVdWh6RHluV1N3YT
                        JEcytUanNzaEVQRDBOZEFGUDBHVVlha2krQUZoTUxxT0hYUnN3MUgre
                        FFNM2JmYXRoTlhJY3hicWg3N1dPaVJUMHFZTT0ifQ.OBdGISyFNba1Y
                        pPEMj8Su-wWgSKDEBuFNAUHAggugQ1bbT01cjuLxphmiGnHYuXXi86w
                        Sg_JkCOcgV-acUrysQ"
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

Response from server (header `X-Auth-Token` is empty):

```
HTTP/1.1 200
X-Auth-Token: 

{
  "success": true
}
```

### Issuing

`POST /pupilid/issue` issues a PupilId into the App. User needs to perform a device binding first (see above).

On the first call to `/pupilid/issue`, this service answers with HTTP Status 401 and a challenge in the header `WWW-Authenticate: Challenge OBU7Uz4vI2uRmeZtGzm5FbNmVNpwNnwWQ06P15fRpiI=`.

Alternatively, the client may call `GET /authn/devicebinding/challenge` to receive a valid challenge (again, Base64-encoded) in the response body.

Note that clients can call this endpoint without additional authentication when including the `X-Auth-Token` from a (successfully completed) device binding process.

The client needs to build a JWS with the `challenge` in the payload and its device binding certificate in the `x5c` header (newlines for display purposes only):

```
{
  "x5c": [
    "MIIBFjCBvKADAgECAggvna9LycsnxzAKBggqhkjOPQQDAjARMQ8wDQYDVQQDDAZJc3N1ZXIwHh
    cNMjIwMjIyMTUwOTE4WhcNMjIwMjIyMTUxMDE4WjARMQ8wDQYDVQQDDAZJc3N1ZXIwWTATBgcqh
    kjOPQIBBggqhkjOPQMBBwNCAAQZ6PJaq5YmlvQL/FwS99S1ZJo6zIKulIznMmkyOUInbE0KHsmr
    GVZHrGIjI/JhCZ0C6QfkXN1A4cx/6Fki1QnTMAoGCCqGSM49BAMCA0kAMEYCIQDBzn7EabGbWAr
    buL2sJqjaEUZAfEExzTHWEsT/ucpFLwIhANpiyoMjJra0WmWE9T5N/I9m1UQZvbhbxmM2FdJVaN
    tB"
  ],
  "alg": "ES256"
}
.
{
  "challenge": "OBU7Uz4vI2uRmeZtGzm5FbNmVNpwNnwWQ06P15fRpiI="
}
```

This JWS needs to be signed with the private key matching the public key in the device binding certificate and sent in the `Authorization` header to the service (newlines for display purposes only):

```
Authorization: Response eyJ4NWMiOlsiTUlJQkZqQ0J2S0FEQWdFQ0FnZ3ZuYTlMeWNzbnh6QUt
               CZ2dxaGtqT1BRUURBakFSTVE4d0RRWURWUVFEREFaSmMzTjFaWEl3SGhjTk1qSXd
               Nakl5TVRVd09URTRXaGNOTWpJd01qSXlNVFV4TURFNFdqQVJNUTh3RFFZRFZRUUR
               EQVpKYzNOMVpYSXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUVo
               2UEphcTVZbWx2UUxcL0Z3Uzk5UzFaSm82eklLdWxJem5NbWt5T1VJbmJFMEtIc21
               yR1ZaSHJHSWpJXC9KaENaMEM2UWZrWE4xQTRjeFwvNkZraTFRblRNQW9HQ0NxR1N
               NNDlCQU1DQTBrQU1FWUNJUURCem43RWFiR2JXQXJidUwyc0pxamFFVVpBZkVFeHp
               USFdFc1RcL3VjcEZMd0loQU5waXlvTWpKcmEwV21XRTlUNU5cL0k5bTFVUVp2Ymh
               ieG1NMkZkSlZhTnRCIl0sImFsZyI6IkVTMjU2In0.eyJjaGFsbGVuZ2UiOiJPQlU
               3VXo0dkkydVJtZVp0R3ptNUZiTm1WTnB3Tm53V1EwNlAxNWZScGlJPSJ9.DJKRan
               m6HvKWlnaajhzq2_CEJmFEdNgmekDAam_3dFvv3xuCz5CMgTgi3QGJeMfqdl5lVB
               mcLHYnU9lZS7miOw
```

#### EIDAS

For EIDAS deployments, the endpoint `POST /eidasid/issue` is available instead, with the same semantics as above.

In addition, the endpoint `GET /eidasid/initialize` is available, where the web browser displays a QR code that can be scanned by the Wallet App to load EIDAS credentials. This endpoint is available after the user has been logged in with OAuth2 (link on `/login`).

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

`GET /debug/initialize` shows a QR code that can be used by the Wallet App to get a nonce to use as the authentication token during the device binding process.

`GET /debug/nonce` returns a nonce that is valid to use for `X-Auth-ExtNonce` during the binding creation process.

`GET /debug/credential/list` displays a web page with a list of issued credentials.

`GET /debug/credential/revoke?vcId={foo}` revokes a credential.

`GET /debug/credential/create` creates a new credential.


## Configuration

There is no default configuration file included in this service, i.e. everything should be configured explicitly when running it, using an `application.yml` or `application.properties` file.

There are two profiles implemented in this service: `pupilid` (the default) and `eidasid` for EIDAS deployments.

When the profile `eidasid` is active (e.g. set with `spring.profiles.active=eidasid`, this service expects the user to log in on a desktop device with their E-ID. Then it displays a QR Code, that can be scanned with the Wallet App. The attributes issued as verifiable credentials match the attributes from the E-ID login.

There are several custom configuration properties, all under the key `backend`:

```yaml
backend:
  public-context: "http://localhost:8080"
  credentials:
    lifetime: PT60M
    one-credential-per-device-binding: true
  issuer-key: {{ KEY_CONFIG }}
    type: FILE
    file:
      private-key: file:issuer-key-private.pem
      public-key: file:issuer-key-public.pem
  debug:
    enabled: true
    qr-code-size: 400
  authn:
    challenge-timeout-seconds: 60
    api-keys:
      - name: External Caller
        key: asdfasdf
    device-binding:
      type: INTERNAL
  attribute-source:
    type: RANDOM
  pki:
    type: INTERNAL
    cert-validity-days: 182
    internal:
      issuer-name: "CN=Issuer"
      key: {{ KEY_CONFIG }}
```

Set `backend.credentials.one-credential-per-device-binding=true` if existing credentials for the same device binding should be revoked when a new credential is issued (e.g. as it is the case for PupilIds).

Alternative configuration for the device binding authentication (validation of the ext. nonce provided by the Wallet App):

```yaml
backend:
  authn:
    device-binding:
      type: INTERNAL
```

```yaml
backend:
  authn:
    device-binding:
      type: ECO
      eco: {{ SERVICE_CONFIG }}
```

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

```yaml
backend:
  attribute-source:
    type: ECO
    eco: {{ SERVICE_CONFIG }}
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

Alternative configuration for all trust configurations (e.g. in TLS connections), depicted as `{{ TRUST_CONFIG }}` above (if nothing is configured, the system-default truststore will be used):

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

Configuration to use ar in-memory H2 database for deployments in debug environments:

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


### Spring Boot Admin Client

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
