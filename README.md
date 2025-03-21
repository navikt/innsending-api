# innsending-api

Backend for innsending av dokumenter. Brukes av [FyllUt](https://github.com/navikt/skjemabygging-formio)
og [SendInn](https://github.com/navikt/send-inn-frontend). Før søknaden blir sendt inn mellomlagres den sammen med
metadataen i `innsending-api`,og når søknaden sendes inn blir metadataen for søknaden sendt
til [Søknads mottaker](https://github.com/navikt/soknadsmottaker). [soknadsarkiverer](https://github.com/navikt/soknadsarkiverer)
henter søknadsfilene via REST endepunkt fra `innsending-api`.
Se [Arktitektur Wiki](https://github.com/navikt/archiving-infrastructure/wiki) for mer informasjon om hvordan oppsettet
fungerer.

## Utvikling

### Kjøre lokalt

Sett Spring profilen til `local` og kjør `InnsendingApiApplication`. En embedded Postgres
database ([opentable](https://github.com/opentable/otj-pg-embedded)) spinnes opp som en docker
container og kjører Flyway migrasjonene.

### Docker Compose

Applikasjonen (sammen med en Postgres database) kan også kjøres lokalt med Docker Compose:

```
docker compose up --build
```

Vær oppmerksom på at dette er ganske tidkrevende ved første kjøring siden den laster ned alle dependencies.
Ved kodeoppdatering eller bytting av branch vil det være nødvendig å kjøre den på nytt, men dependencies vil være
cachet.

### Testing

For mocking brukes blant
annet [mockK](https://mockk.io/), [mockwebserver](https://github.com/square/okhttp/tree/master/mockwebserver)
og [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)

### Antivirus

Opplastede filer fra brukere blir sjekket for virus med [ClamAV](https://www.clamav.net/) via
et [nais-endepunkt](https://docs.nais.io/security/antivirus/).
For å teste virussjekken kan standard [EICAR test-filer](https://github.com/fire1ce/eicar-standard-antivirus-test-files)
brukes.

### Skedulert merge og deploy

Applikasjonen kan deployes på et gitt tidspunkt ved å legge til `/schedule {TIDSPUNKT_SOM_ISO_8601_UTC}` i PR-teksten.
En action kjøres hver time for å lete etter slike tekster og deployer applikasjonen hvis tidspunktet er forbi.
(eksempel: `/schedule 2023-10-18T01:57` vil bli deployet 18. oktober 2023 kl 04:00 norsk tid). Merk at tidspunktet er
spesifisert i UTC.
Dette kan være nyttig for å deploye applikasjonen utenfor arbeidstid.

### Kodeformattering

Som Intellij settings velg:

- Editor -> Code Style -> Kotlin -> Set from... -> Kotlin Style Guide
- Tools -> Actions on Save
	- Reformat code
	- Optimize imports
	- Rearrange code
	- Run code cleanup

### Aksessloggene

Aksesslogger finnes i [Kibana](https://logs.adeo.no) under `Applikasjonslogger`.
Det er også satt opp secure logs under `Securelogs` for å kunne sikkert logge fødselsnummer og andre sensitiv data.

---

# Kode generert av GitHub Copilot

Dette repoet bruker GitHub Copilot til å generere kode.

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-fyllut-sendinn
