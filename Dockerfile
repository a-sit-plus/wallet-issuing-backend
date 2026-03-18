# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY http/build.gradle.kts http/build.gradle.kts
COPY http/src http/src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon :http:bootJar

RUN set -eux; \
    jar="$(find /workspace/http/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$jar"; \
    cp "$jar" /workspace/service.jar

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

ENV PORT=8080
ENV JAVA_OPTS=""

RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /app app

COPY --from=build /workspace/service.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
