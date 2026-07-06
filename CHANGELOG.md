# Changelog

7.0.0 (unreleased):
 - Update to VC-K 7.0.0-SNAPSHOT
 - Rework resolving credential schemes
 - Update to Spring Boot 4.1.0

6.0.0:
 - Update to VC-K 6.0.0
 - Update to EU PID 3.5.0
 - Remove `jvm` suffix from `vck` in `lib.versions` to restore composite build functionality 
 - Use `JwsCompactTyped` instead of `JwsSigned`

5.13.0:
 - Update to VC-K 5.13.0
 - Migrate to Spring Boot 4.0.6 (Spring Framework 7, Spring Security 7, Jakarta EE 11)
 - Update Spring Cloud to 2025.1.1 (Oakwood)
 - Update Spring Boot Admin client to 4.0.4
 - Upgrade JVM toolchain to Java 21
 - Enable virtual threads (`spring.threads.virtual.enabled`)
 - Switch primary HTTP message converter to kotlinx.serialization backed by `joseCompliantSerializer`


5.12.0:
 - Update to VC-K 5.12.0
 - Add DC API issuing process
 - Support explicit constants for [Age Verification](https://ageverification.dev/)

5.11.0:
 - Initial public release
 - Update to VC-K 5.11.0
 - Update dependencies, i.e. Spring Boot to 3.5.9
 - Remove HealthID and ePrescription credentials
