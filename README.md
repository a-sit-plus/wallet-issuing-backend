# Wallet Backend Service

This service creates pupil IDs, stores a reference for them in a database, can revoke them.

Functionality:
 - Endpoint for issuing of a new pupil ID at `/issue` (POST an `RequestCredential` message after getting an Out-of-Band invitation)
 - Endpoint to get a revocation list at `/credentials/status/1`
 - Demo web page showing QR codes to initialize a Wallet App (contains an Out-of-Band invitation) at `/initialize`
 - Demo web page to revoke credentials at `/revoke/list`
 - Stores references for issued credentials

View a list of open issues at <https://gitlab.iaik.tugraz.at/wallet/backend/-/boards>

Default public key for issuing credentials is:

```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaCUPdgNqCIFLVXE8yn5lZGaYjbyC
ys0go5xhPtbXj0X2jNAUUOddCh8eYoB9dO/ARUyBbccxKmNxO01kd8+/Tg==
-----END PUBLIC KEY-----
```

with it's `kid` of `did:key:mEgACaCUPdgNqCIFLVXE8yn5lZGaYjbyCys0go5xhPtbXj0U=`.

## Endpoints

`GET /credentials/status/1` returns the revocation list in a VC-compatible format, i.e. [Revocation List 2020](https://w3c-ccg.github.io/vc-status-rl-2020/).

`POST /issue` for the MVP to issue credentials into the App. User needs to scan an invitation barcode from `GET /initialize`.

`GET /initialize` displays QR codes for the MVP to scan with the App and start getting credentials.

`GET /revoke/list` displays a list of revoked credentials for the MVP.

`GET /revoke?vcId={foo}` revokes a credential for the MVP.

`POST /pupilid/issue` issues a PupilId into the App. User needs to perform a device binding first.

`POST /binding/start` initiates the device binding process in the App. User needs to scan a QR Code with a nonce first to be authorized to access this endpoint.

`POST /binding/create` finishes the device binding process in the App. User is authenticated through the session established by the call to `/binding/start`.

`POST /revoke/binding` revokes a device binding, by the pupil's `bpk` or `deviceId`. Clients are external services, and authenticated with an API key.

`POST /revoke/pupilid` revokes a PupilId instance, by the pupil's `bpk` or `deviceId`. Clients are external services, and authenticated with an API key.

`GET /revoke/devices?bpk={foo}` lists all devices for the pupil with `bpk`. Clients are external services, and authenticated with an API key.

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
