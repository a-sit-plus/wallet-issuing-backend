# Wallet Backend Service

This service creates pupil IDs, stores a reference for them in a database, can revoke them.

Functionality:
 - Endpoint for issuing of a new pupil ID at `/issue` (POST an `RequestCredential` message after getting an Out-of-Band invitation)
 - Endpoint to get a revocation list at `/credentials/status/1`
 - Demo web page showing QR codes to initialize a Wallet App (contains an Out-of-Band invitation) at `/demo`
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
```
