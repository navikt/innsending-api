FROM ghcr.io/navikt/baseimages/temurin:17

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

COPY innsender/target/*.jar /app/app.jar
EXPOSE 9064
