# PupilId Backend Service

This backend service for provisioning and revoking PupilIds runs at <https://wallet.a-sit.at/>.

The configuration used there is the following:

```yaml
logging:
  level:
    at.asitplus: DEBUG
  file:
    name: service.log
server:
  port: 9400
  servlet:
    context-path: /
  use-forward-headers: true
backend:
  public-context: "https://wallet.a-sit.at/"
  credential-lifetime: P1D
  issuer-key:
    type: FILE
    file:
      private-key: file:data/issuer-key-private.pem
      public-key: file:data/issuer-key-public.pem
  debug:
    enabled: true
    random-photo-location: file:data/photos/
    authn:
      api-keys:
      - name: Quarto Dev
        key: 9tgvj6tji38fnj75hzc4zuhd6dznnqkm
spring:
  application:
    name: "Wallet Backend Master"
  boot:
    admin:
      client:
        url: "http://localhost:9900"
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
  datasource:
    url: "jdbc:h2:file:~/data/h2.db"
  web:
    resources:
      static-locations:
      - "classpath:/resources"
      - "classpath:/static"
      - "classpath:/public"
      - "file:apps"
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: "*"
```
