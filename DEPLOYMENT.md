# PupilId Backend Service

This backend service for provisioning and revoking PupilIds runs at <https://wallet.a-sit.at/>.

The configuration used there is the following to load the configuration via the Spring Cloud Config Server from the repository at <https://extgit.iaik.tugraz.at/ckollmann/spring-cloud-config>:

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

Additional configuration (e.g. of the reverse proxy) is documented in the [Software Guidebook](https://gitlab.iaik.tugraz.at/groups/wallet/-/wikis/Software-Guidebook-Sch%C3%BClerausweis).

To update the service running at <https://wallet.a-sit.at> perform the following steps:

- Develop the changes on a local `feature/*` branch
- Push that branch to GitLab
- Run the CI step `publishSnapshot` manually for that pipeline, see [GitLab UI](https://gitlab.iaik.tugraz.at/wallet/backend/-/pipelines)
- Once the code is merged to `development`, a `publish` job will run automatically
- The outcome is a package in the [GitLab Package Registry](https://gitlab.iaik.tugraz.at/wallet/backend/-/packages)
- Create a [personal access token](https://gitlab.iaik.tugraz.at/-/profile/personal_access_tokens) with scope `read_api`, needed for the deploy script
- Locally (because we'll need a VPN connection) run `./deploy.sh 1.0.0-SNAPSHOT YOUR_PERSONAL_ACCESS_TOKEN`

## libwebp

See (libpweb/README.md)[libwebp/README.md].
