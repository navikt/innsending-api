FROM ghcr.io/navikt/baseimages/temurin:17

ENV SPRING_PROFILES_ACTIVE=docker

COPY innsender/target/*.jar /app/app.jar
EXPOSE 9064
