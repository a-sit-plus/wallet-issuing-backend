# Wallet Backend Service

This service creates pupil IDs, stores a reference for them in a database, can revoke them.

Functionality:
 - Endpoint for issuing of a new pupil ID at `/issue` (POST an `RequestCredential` message after getting an Out-of-Band invitation)
 - Endpoint to get a revocation list at `/credentials/status/1`
 - Provides a demo web page showing QR codes to initialize a Wallet App (contains an Out-of-Band invitation)
 - Stores references for issued credentials

View a list of open issues at <https://gitlab.iaik.tugraz.at/wallet/backend/-/boards>

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
```