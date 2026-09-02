# innsending-api

Backend for innsending av dokumenter. Brukes av [FyllUt](https://github.com/navikt/skjemabygging-formio)
og [SendInn](https://github.com/navikt/send-inn-frontend). Før søknaden blir sendt inn mellomlagres den sammen med
metadataen i `innsending-api`,og når søknaden sendes inn blir metadataen for søknaden sendt
til [Søknads mottaker](https://github.com/navikt/soknadsmottaker). [soknadsarkiverer](https://github.com/navikt/soknadsarkiverer)
henter søknadsfilene via REST endepunkt fra `innsending-api`.
Se [Arktitektur Wiki](https://github.com/navikt/archiving-infrastructure/wiki) for mer informasjon om hvordan oppsettet
fungerer.

## Utvikling

### Bygge lokalt

Følgende må være installert for å bygge prosjektet:

- Java 21
- Maven 3.9+
- Docker (pga. at testene bruker [testcontainers](https://testcontainers.com/))

Prosjektet bruker [mise](https://mise.jdx.dev/) til å styre riktige Java- og Maven-versjoner, definert
i `mise.toml`.
Om du ikke har mise installert på maskinen fra før, installer på rot med brew.
```
brew install mise
```

Naviger deretter til prosjektmappen for dette repoet på maskinen og installer mise i prosjektet:

```
mise install
```

Dette installerer Java 21 og Maven 3.9 lokalt for prosjektet. Aktiver mise i shellet ditt
```
mise activate <shell>
```
Se [installasjonsguiden](https://mise.jdx.dev/installing-mise.html) slik at
`java`/`mvn` peker på versjonene fra `mise.toml` når du står i prosjektmappen.


Du må være autentisert mot Github for å kunne laste ned Nav-artifakter fra Github Packages, se Nav's dokumentasjon for
[Tilgang til Github](https://navikt.github.io/ny-i-nav/en-nais-device.html):
1. Installer gh-cli: github.com/cli
2. Logg inn og autentiser mot Github
   $ gh auth login
3. Konfigurer git til å bruke gh-cli for autentisering.
   $ gh auth setup-git


### Kjøre lokalt i IntelliJ

Sett Spring profilen til `local` og kjør `InnsendingApiApplication`. En embedded Postgres
database ([opentable](https://github.com/opentable/otj-pg-embedded)) spinnes opp som en docker
container og kjører Flyway migrasjonene.

### Kjøre lokalt i terminal

Container-runtimen kjøres med [Colima](https://github.com/abiosoft/colima) istedenfor Docker Desktop.

Om du ikke har Colima, Docker og Docker-Compose installert fra før:
```
brew install colima docker docker-compose
```

Start container runtimen med Colima:
```
colima start
```

For å bygge prosjektet og kjøre testene:
```
mvn clean install
```

#### Jobbe lokalt
En effektiv måte å jobbe lokalt på er å kjøre opp Postgres og Google Storage lokalt med Docker Compose
```
docker compose up -d db cloud-storage
```
og så kjøre innsending-api i Intellij med Spring profilen
satt til `docker` og miljøvariabel `DATABASE_PORT=5450`.

Selve applikasjonen innsending-api kan også kjøres lokalt med Docker Compose:

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
