# innsending-api

Backend for innsending av dokumenter. Brukes av [FyllUt](https://github.com/navikt/skjemabygging-formio)
og [SendInn](https://github.com/navikt/send-inn-frontend). Før søknaden blir sendt inn mellomlagres den sammen med
metadataen i `innsending-api`,og når søknaden sendes inn blir søknadsfilene sendt videre
til [Søknads fillager](https://github.com/navikt/soknadsfillager) mens metadataen for søknaden blir sendt
til [Søknads mottaker](https://github.com/navikt/soknadsmottaker).
Se [Arktitektur Wiki](https://github.com/navikt/archiving-infrastructure/wiki) for mer informasjon om hvordan oppsettet
fungerer.

## Utvikling

### Embedded Postgres

Ved kjøring av `InnsendingApiApplication` vil en embedded Postgres
database ([opentable](https://github.com/opentable/otj-pg-embedded)) spinne opp som en docker
container og kjøre Flyway
migrasjonene.

### Docker-compose postgres

Embedded Postgres databasen spinner opp en ny database hver gang, med en ny port. For å ha en stabil database under
utvikling:

- Kjør `docker-compose up` i rotmappen til prosjektet
- Bytt til Spring profile: `docker`
- Kjør `InnsendingApiApplication`

### Testing

For mocking brukes blant
annet [mockK](https://mockk.io/), [mockwebserver](https://github.com/square/okhttp/tree/master/mockwebserver)
og [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)

### Kodeformattering

Som Intellij settings velg:

- Editor -> Code Style -> Kotlin -> Set from... -> Kotlin Style Guide
- Tools -> Actions on Save
	- Reformat code
	- Optimize imports
	- Rearrange code
	- Run code cleanup

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-fyllut-sendinn
