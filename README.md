# Wallet Backend Service

This service creates pupil IDs, stores a reference for them in a database, can revoke them.

Functionality:
 - Endpoint for issuing of a new pupil ID at `/issue` (POST an `RequestCredential` message after getting an Out-of-Band invitation)
 - Endpoint to get a revocation list at `/credentials/status/1`
 - Demo web page showing QR codes to initialize a Wallet App (contains an Out-of-Band invitation) at `/initialize`
 - Demo web page to revoke credentials at `/revoke/list`
 - Stores references for issued credentials


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

Public endpoints are the following:

`GET /credentials/status/1` returns the revocation list in a VC-compatible format, i.e. [Revocation List 2020](https://w3c-ccg.github.io/vc-status-rl-2020/).

`GET /help/wallet` displays a help page if the user scans a debug initialization QR Code with a standard camera app (instead of the Wallet App).

`GET /invite/verify` displays a help page if the user scans a QR code displayed by the Wallet App for verification with a standard camera app (instead of the Verifier App).

### Device Binding

The call to `/binding/start` requires authentication with a Nonce extracted from a QR Code displayed by ECO, to be sent in the header `X-Auth-ExtNonce`. The second call, to `/binding/create` needs to include the session identifier to be sent in the header `X-Auth-Token` (which in turn has been set by the service in the first response).

`POST /binding/start` initiates the device binding process in the App. User needs to scan a QR Code with a nonce first to be authorized to access this endpoint.

`POST /binding/create` finishes the device binding process in the App. User is authenticated through the session established by the call to `/binding/start`.

### Issuing

`POST /pupilid/issue` issues a PupilId into the App. User needs to perform a device binding first (see above).

On the first call to `/pupilid/issue`, this service answers with HTTP Status 401 and a challenge in the header `WWW-Authenticate: Challenge OBU7Uz4vI2uRmeZtGzm5FbNmVNpwNnwWQ06P15fRpiI=`.

The client needs to build a JWS with the `challenge` in the payload and its device binding certificate in the `x5c` header:

```json
{
  "x5c": [
    "MIIBFjCBvKADAgECAggvna9LycsnxzAKBggqhkjOPQQDAjARMQ8wDQYDVQQDDAZJc3N1ZXIwHhcNMjIwMjIyMTUwOTE4WhcNMjIwMjIyMTUxMDE4WjARMQ8wDQYDVQQDDAZJc3N1ZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQZ6PJaq5YmlvQL/FwS99S1ZJo6zIKulIznMmkyOUInbE0KHsmrGVZHrGIjI/JhCZ0C6QfkXN1A4cx/6Fki1QnTMAoGCCqGSM49BAMCA0kAMEYCIQDBzn7EabGbWArbuL2sJqjaEUZAfEExzTHWEsT/ucpFLwIhANpiyoMjJra0WmWE9T5N/I9m1UQZvbhbxmM2FdJVaNtB"
  ],
  "alg": "ES256"
}
.
{
  "challenge": "OBU7Uz4vI2uRmeZtGzm5FbNmVNpwNnwWQ06P15fRpiI="
}
```

This JWS needs to be signed with the private key matching the public key in the device binding certificate and sent in the `Authorization` header to the service:

`Authorization: Response eyJ4NWMiOlsiTUlJQkZqQ0J2S0FEQWdFQ0FnZ3ZuYTlMeWNzbnh6QUtCZ2dxaGtqT1BRUURBakFSTVE4d0RRWURWUVFEREFaSmMzTjFaWEl3SGhjTk1qSXdNakl5TVRVd09URTRXaGNOTWpJd01qSXlNVFV4TURFNFdqQVJNUTh3RFFZRFZRUUREQVpKYzNOMVpYSXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUVo2UEphcTVZbWx2UUxcL0Z3Uzk5UzFaSm82eklLdWxJem5NbWt5T1VJbmJFMEtIc21yR1ZaSHJHSWpJXC9KaENaMEM2UWZrWE4xQTRjeFwvNkZraTFRblRNQW9HQ0NxR1NNNDlCQU1DQTBrQU1FWUNJUURCem43RWFiR2JXQXJidUwyc0pxamFFVVpBZkVFeHpUSFdFc1RcL3VjcEZMd0loQU5waXlvTWpKcmEwV21XRTlUNU5cL0k5bTFVUVp2YmhieG1NMkZkSlZhTnRCIl0sImFsZyI6IkVTMjU2In0.eyJjaGFsbGVuZ2UiOiJPQlU3VXo0dkkydVJtZVp0R3ptNUZiTm1WTnB3Tm53V1EwNlAxNWZScGlJPSJ9.DJKRanm6HvKWlnaajhzq2_CEJmFEdNgmekDAam_3dFvv3xuCz5CMgTgi3QGJeMfqdl5lVBmcLHYnU9lZS7miOw`

### Revocation

Clients are external services, and authenticated with an API key. The API key shall be sent in the header `X-API-Key`.

`POST /revoke/binding` revokes a device binding, by the pupil's `bpk` and/or `deviceId`.

`POST /revoke/pupilid` revokes all PupilId instances for one pupil, specified by their `bpk`.

`GET /revoke/devices?bpk={foo}` lists all devices for the pupil with `bpk`.

### Debug

These endpoints are only enabled if `backend.debug.enabled=true` is set.

`GET /debug/initialize` shows a QR code that can be used by the Wallet App to get a nonce to use as the authentication token during the device binding process.

`GET /debug/credential/list` displays a web page with a list of issued credentials.

`GET /debug/credential/revoke?vcId={foo}` revokes a credential.

`GET /debug/credential/create` creates a new credential.


## Configuration

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

server:
  port: 8080

backend:
  public-context: "http://localhost:8080"
  credential-lifetime: PT60M
  random-photo-location: file:/path/to/photos-in-jpg/
  issuer-key:
    type: FILE
    file:
      private-key: file:issuer-key-private.pem
      public-key: file:issuer-key-public.pem
  debug:
    enabled: true
    qr-code-size: 400
  authn:
    api-keys:
      - name: Quarto Dev
        key: 8tgvj6tji38fnj75hzc4zuhd6dznnqkn
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
