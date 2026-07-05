# ---- Stage 1: build the Spring Boot app ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: runtime = JRE + oha binary + app ----
FROM eclipse-temurin:21-jre-jammy

# Pin the oha version; check https://github.com/hatoo/oha/releases for the latest
ARG OHA_VERSION=1.4.5
# Set automatically by buildx (amd64 / arm64)
ARG TARGETARCH=amd64

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && case "$TARGETARCH" in \
      amd64) OHA_BIN="oha-linux-amd64" ;; \
      arm64) OHA_BIN="oha-linux-arm64" ;; \
      *) echo "unsupported arch: $TARGETARCH" && exit 1 ;; \
    esac \
 && curl -fsSL -o /usr/local/bin/oha \
      "https://github.com/hatoo/oha/releases/download/v${OHA_VERSION}/${OHA_BIN}" \
 && chmod +x /usr/local/bin/oha \
 && oha --version \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# SQLite lives here; mounted as a volume so history survives restarts
RUN mkdir -p /data
VOLUME /data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
