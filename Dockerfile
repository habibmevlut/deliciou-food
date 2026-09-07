# Multi-module monorepo: tek Dockerfile, MODULE arg ile hangi servisin
# paketlenceğini seçer. Context her zaman repo kökü olmalı ki parent POM
# ve kardeş modüller görülsün.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY restaurant-service/pom.xml restaurant-service/pom.xml

RUN chmod +x mvnw \
    && ./mvnw -B -q -DskipTests dependency:go-offline || true

COPY order-service order-service
COPY payment-service payment-service
COPY restaurant-service restaurant-service

ARG MODULE
RUN ./mvnw -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/${MODULE}.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
