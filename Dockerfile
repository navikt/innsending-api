FROM navikt/java:11

ENV APPLICATION_PROFILE=docker

COPY target/innsending-api.jar /app/app.jar
COPY export-vault-secrets.sh /init-scripts/50-export-vault-secrets.sh
EXPOSE 9064
