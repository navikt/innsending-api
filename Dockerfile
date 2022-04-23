#FROM eclipse-temurin:17-jdk-focal
FROM navikt/java:17

ENV SPRING_PROFILES_ACTIVE=docker

COPY innsender/target/*.jar /app/app.jar
COPY export-vault-secrets.sh /init-scripts/50-export-vault-secrets.sh
EXPOSE 9064
