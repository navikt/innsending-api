# innsending-api

## OpenAPI compatibility

Version 2.0.0 removes the unused `/fyllUt/v2/*`, legacy `/v1/nologin-*`,
`GET /ekstern/v1/skjema/{skjemanr}/soknader`, and unused Send Inn attachment
read operations. The disabled `/frontend/v1/soknad` and
`/frontend/v1/ettersendPaSkjema` creation operations are removed together with
the corresponding page in `send-inn-frontend`. Deploy that frontend change
before deploying this API change.

`DELETE /fyllUt/v1/soknad/{innsendingsId}` remains available because it still
receives production traffic. External ettersending and oppgaver APIs are unchanged.

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

Digitale vedlegg lagres før den asynkrone virussjekken starter. Innsending blokkeres ikke av
resultatet. Nologin-vedlegg må fortsatt bestå en synkron virussjekk før de lagres, uten retry.

Den asynkrone sjekken gjør maksimalt tre forsøk ved HTTP 500, 502, 503 eller 504 og ved
midlertidige nettverksfeil. Ventetiden dobles fra 2 til 4 sekunder, med tilfeldig variasjon
på ±25 %. Virusfunn, andre HTTP-feil, ugyldige svar og et `ERROR`-resultat fra skanneren
prøves ikke på nytt. Avbrutte oppgaver stopper uten nye forsøk.

Midlertidige feil logges på `WARN`. En endelig mislykket virussjekk gir én `ERROR`, som fanges av
Grafana-varselet for feillogger. En sjekk som lykkes etter retry logges på `INFO`, uten
`ERROR`. Virusfunn logges fortsatt på `WARN`. Loggene har `scan_attempts`, `scan_result`,
`scan_failure_reason`, `http_status`, `exception_type` og fil-ID-er for korrelasjon med `trace_id`, men ikke
filnavn, dokumentinnhold eller responsinnhold fra skanneren.

| Metrikk | Hva den måler |
|---|---|
| `innsendingapi_antivirus_scan_total{namespace,mode,result}` | Ett endelig resultat per sjekk, også når alle forsøk feiler |
| `innsendingapi_antivirus_scan_attempts_total{namespace,result,reason}` | Hvert asynkront forsøk, inkludert feil som senere blir løst |
| `innsendingapi_antivirus_scan_retries_total{namespace,reason}` | Ekstra forsøk etter det første |
| `innsendingapi_antivirus_scan_recovered_total{namespace,result}` | Sjekker som gir `ok` eller `found` etter retry |
| `innsendingapi_antivirus_scan_duration_seconds{namespace,result}` | Histogram for hele den asynkrone sjekken, inkludert backoff |

Alle etikettverdier kommer fra faste utfall og feilkategorier. Tellerne starter på null,
slik at det første feiltilfellet eller den første retry-en kan måles med `increase`.
ID-er brukes ikke som etiketter i metrikkene.
Den eksisterende `VIRUS_SCAN`-latensmetrikken måler fortsatt hvert HTTP-forsøk uten backoff.

Eksempler for Grafana Explore, med cluster-etiketten `prod`:

```promql
sum by (result) (increase(innsendingapi_antivirus_scan_total{k8s_cluster_name="prod",mode="async"}[15m]))
sum by (reason) (increase(innsendingapi_antivirus_scan_retries_total{k8s_cluster_name="prod"}[15m]))
sum by (result) (increase(innsendingapi_antivirus_scan_recovered_total{k8s_cluster_name="prod"}[15m]))
```

```logql
{k8s_cluster_name="prod",service_name="innsending-api"} | json | scan_failure_reason="http_server_error"
```

Retry skjer i den eksisterende bakgrunnsoppgaven, ikke i en varig kø. Oppgaver overlever
ikke en pod-restart, og en endelig mislykket virussjekk sletter ikke vedlegget eller utsetter
innsendingen. Resultater må lagres og knyttes til riktig filversjon før virussjekken kan
brukes til å blokkere innsending.

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
