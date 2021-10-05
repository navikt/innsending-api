FROM navikt/java:11

ENV APPLICATION_PROFILE=docker

COPY target/innsending-api.jar /app/app.jar
EXPOSE 9064
