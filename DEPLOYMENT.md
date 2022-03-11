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

To update the service running at <https://wallet.a-sit.at> perform the following steps:

- Develop the changes on a local `feature/*` branch
- Push that branch to GitLab
- Run the CI step `publishSnapshot` manually for that pipeline, see [GitLab UI](https://gitlab.iaik.tugraz.at/wallet/backend/-/pipelines)
- Once the code is merged to `development`, a `publish` job will run automatically
- The outcome is a package in the [GitLab Package Registry](https://gitlab.iaik.tugraz.at/wallet/backend/-/packages)
- Create a [personal access token](https://gitlab.iaik.tugraz.at/-/profile/personal_access_tokens) with scope `read_api`, needed for the deploy script
- Locally (because we'll need a VPN connection) run `./deploy.sh 1.0.0-SNAPSHOT YOUR_PERSONAL_ACCESS_TOKEN`

