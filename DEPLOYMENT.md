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
  forward-headers-strategy: framework
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
  attribute-source:
    type: ECO
    eco:
      url: "https://edureg.quarto.at/edudigicardapi"
      server-tls: true
      api-key: MASKED
  authn:
    api-keys:
    - name: Quarto Dev
      key: 9tgvj6tji38fnj75hzc4zuhd6dznnqkm
    device-binding:
      type: ECO
      external:
        url: "https://edureg.quarto.at/edudigicardapi"
        server-tls: true
        api-key: MASKED
spring:
  application:
    name: "Wallet Backend PupilId"
  profiles:
    active: pupilid
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

The Apache2 reverse proxy is configured in this way:

```

<Location "/">
  ProxyPass "http://localhost:9400/"
  ProxyPassReverse "http://localhost:9400/"
  ProxyPreserveHost On
  RequestHeader set X-Forwarded-Proto https
  RequestHeader set X-Forwarded-Port 443
</Location>

<Location "/v3/api-docs">
  <RequireAny>
    Require ip 129.27.0.0/255.255.0.0
    Require ip  10.27.0.0/255.255.0.0
    Require ip 195.34.137.58/255.255.255.255
    Require ip 195.34.137.59/255.255.255.255
    Require ip 195.34.137.60/255.255.255.255
    Require ip 195.34.137.61/255.255.255.255
  </RequireAny>
</Location>

<Location "/swagger-ui">
  <RequireAny>
    Require ip 129.27.0.0/255.255.0.0
    Require ip  10.27.0.0/255.255.0.0
    Require ip 195.34.137.58/255.255.255.255
    Require ip 195.34.137.59/255.255.255.255
    Require ip 195.34.137.60/255.255.255.255
    Require ip 195.34.137.61/255.255.255.255
  </RequireAny>
</Location>

<Location "/revoke">
  <RequireAny>
    Require ip 129.27.0.0/255.255.0.0
    Require ip  10.27.0.0/255.255.0.0
    Require ip 195.34.137.58/255.255.255.255
    Require ip 195.34.137.59/255.255.255.255
    Require ip 195.34.137.60/255.255.255.255
    Require ip 195.34.137.61/255.255.255.255
  </RequireAny>
</Location>
```

To update the service running at <https://wallet.a-sit.at> perform the following steps:

- Develop the changes on a local `feature/*` branch
- Push that branch to GitLab
- Run the CI step `publishSnapshot` manually for that pipeline, see [GitLab UI](https://gitlab.iaik.tugraz.at/wallet/backend/-/pipelines)
- Once the code is merged to `development`, a `publish` job will run automatically
- The outcome is a package in the [GitLab Package Registry](https://gitlab.iaik.tugraz.at/wallet/backend/-/packages)
- Create a [personal access token](https://gitlab.iaik.tugraz.at/-/profile/personal_access_tokens) with scope `read_api`, needed for the deploy script
- Locally (because we'll need a VPN connection) run `./deploy.sh 1.0.0-SNAPSHOT YOUR_PERSONAL_ACCESS_TOKEN`

## EidasId Backend Service

This backend service for provisioning and revoking EidasIds runs at <https://eid.a-sit.at/wallet>.

```yaml
logging:
  level:
    at.asitplus: DEBUG
  file:
    name: service.log
server:
  port: 9400
  servlet:
    context-path: /wallet
  forward-headers-strategy: framework
backend:
  public-context: "https://eid.a-sit.at/wallet/"
  credential-lifetime: P1D
  issuer-key:
    type: FILE
    file:
      private-key: file:data/issuer-key-private.pem
      public-key: file:data/issuer-key-public.pem
  debug:
    enabled: true
  attribute-source:
    type: EIDAS
  authn:
    device-binding:
      type: INTERNAL
spring:
  application:
    name: "Wallet Backend EidasId"
  profiles:
    active: eidasid
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
  security:
    oauth2:
      client:
        registration:
          eidea:
            client-id: https://eid.a-sit.at/wallet
            client-secret: MASKED
            client-name: "EID EA"
            scope: "openid, profile"
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: https://eid.a-sit.at/wallet/login/oauth2/code/eidea
        provider:
          eidea:
            issuer-uri: "https://eid.egiz.gv.at"
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: "*"
```
