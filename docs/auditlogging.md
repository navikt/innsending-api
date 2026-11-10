# Finne og forstå audit-logger

pgAudit logger SQL-operasjoner fra databasebrukere som ikke er unntatt fra auditing. Bruk [Logs Explorer](https://console.cloud.google.com/logs/query) for å undersøke databaseoppslag og skriving. Vanlig API-trafikk skal ikke gi pgAudit-treff når unntaket for applikasjonsbrukeren er aktivt.

## Oppsettet i innsending-api

[Nais-manifestet](../.nais/nais-gcp.yml) konfigurerer disse standardverdiene for Cloud SQL-instansen.

| Flagg | Verdi | Betydning |
| --- | --- | --- |
| `cloudsql.enable_pgaudit` | `on` | Aktiverer pgAudit-støtte på instansen. Oppretter ikke extension i databasen. |
| `pgaudit.log` | `read,write` | Logger lese- og skriveoperasjoner, blant annet `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` og relevante `COPY`-operasjoner. Aktiverer ikke `DDL`- eller `ROLE`-klassene. |
| `pgaudit.log_parameter` | `off` | Utelater verdiene til bundne SQL-parametere. |
| `pgaudit.log_relation` | `on` | Gir relasjonsspesifikke oppføringer for tabellene i operasjonen. En spørring kan gi flere loggoppføringer. |
| `cloudsql.pgaudit_mask_literals` | `on` | Erstatter literaler i SQL-teksten med plassholdere. |

Flaggene er ikke hele oppsettet. pgAudit-extension må være opprettet, og Data Access-logging må være aktivert i GCP. Databasebrukeren `innsending-api` skal ha et rolle- og databaseunntak med `pgaudit.log=none`. Unntaket hindrer logging av rutinemessig applikasjonstrafikk. Det ligger i databasen, ikke i Flyway-migreringene eller manifestet. Kontroller den effektive tilstanden i hvert miljø, særlig etter gjenoppretting eller erstatning av instansen. Uten unntaket kan også applikasjonstrafikken bli logget.

Maskering anonymiserer ikke hele loggen. Databaseidentiteter, objektnavn og spørringsstruktur er fortsatt synlige. Behandle audit-logger som tilgangsbegrenset informasjon. Ikke kopier persondata, hemmeligheter eller faktiske loggutdrag til dokumentasjon, issues eller pull requests.

## Velg prosjekt, instans og tidsrom

1. Åpne [Logs Explorer i prod](https://console.cloud.google.com/logs/query?project=team-soknad-prod-fa49), eller velg et annet GCP-prosjekt i prosjektvelgeren. Kontroller at loggomfanget er avgrenset til prosjektet du undersøker.
2. Velg et tidsrom rundt en kjent operasjon fra en personlig databasebruker. Start med fem minutter før til ti minutter etter operasjonen. Kontroller tidssonen og utvid tidsrommet ved behov.
3. Vis søkefeltet med **Show query**, lim inn grunnsøket og kjør det.

Grunnsøket bruker bare ressurs- og pgAudit-typen. Prosjektet er valgt i GCP, så du trenger ikke skrive inn prosjekt-ID, loggnavn, database eller instans i søket.

```text
resource.type="cloudsql_database"
protoPayload.request.@type="type.googleapis.com/google.cloud.sql.audit.v1.PgAuditEntry"
```

Hvis prosjektet har flere Cloud SQL-instanser, avgrens resultatet med et feltfilter i Logs Explorer. Velg feltet `resource.labels.database_id` i filterbyggeren og velg riktig instans. Kontroller at loggomfanget fortsatt dekker prosjektet du undersøker.

Du kan legge til ett av disse valgfrie filtrene for å avgrense operasjonsklassen:

```text
protoPayload.request.auditClass="READ"
```

```text
protoPayload.request.auditClass="WRITE"
```

Bruk strukturerte felt fremfor søk etter verdier i SQL-teksten. Parametere er utelatt og literaler skal være maskert. Ikke opphev maskeringen for å finne en bestemt rad.

## Les en loggoppføring

Åpne oppføringen og utvid `protoPayload.request`.

| Felt | Hva du kan lese ut |
| --- | --- |
| `timestamp` | Tidspunktet for hendelsen. Kontroller tidssonen i Logs Explorer. `receiveTimestamp` er tidspunktet Cloud Logging mottok den. |
| `resource.labels.database_id` | GCP-prosjekt og Cloud SQL-instans. |
| `protoPayload.request.database` | Databasen SQL-operasjonen kjørte mot. |
| `protoPayload.request.user` | Databasebrukeren. En personlig IAM-identitet kan identifisere den som koblet til databasen. En delt applikasjonsbruker identifiserer ikke sluttbrukeren av API-et. |
| `protoPayload.request.auditClass` | Operasjonsklasse, for eksempel `READ` eller `WRITE`. |
| `protoPayload.request.command` | SQL-kommandoen. |
| `protoPayload.request.statement` | SQL-tekst med maskerte literaler. Viser spørringsstruktur, ikke en komplett gjengivelse av dataene. |
| `protoPayload.request.parameter` | Skal ikke inneholde parameterverdier med dette oppsettet. |
| `protoPayload.request.databaseSessionId`, `statementId`, `substatementId` | Hjelper deg å knytte oppføringer til samme sesjon og SQL-operasjon. Bruk dem sammen med instans, tidspunkt og bruker. |
| `protoPayload.request.chunkCount`, `chunkIndex` | Lange SQL-tekster kan være delt i flere oppføringer. Én oppføring trenger ikke inneholde hele teksten. |

pgAudit lagrer ikke endringshistorikk med gamle og nye feltverdier. En skriveoppføring beviser heller ikke alene at transaksjonen ble committet. Operasjoner som senere rulles tilbake kan fortsatt være logget. Loggene dekker bare aktivitet som oppsettet fanget opp og Cloud Logging mottok og beholdt.

## Skill mellom loggtypene

| Kilde | Bruk |
| --- | --- |
| pgAudit i Data Access-loggen | SQL-operasjoner fra ikke-unntatte databasebrukere. Logger ikke HTTP-kall eller tilgang til filer i Cloud Storage. |
| Applikasjonens `hendelse`-tabell | Utvalgte hendelser i søknadsflyten. Applikasjonen oppretter hendelser gjennom egne kall, ikke gjennom en databasetrigger. Tabellen er ikke en full logg over lesing eller alle endringer. Se [databaseskjemaet](database-schema.md). |
| Aksess- og applikasjonslogger | Forespørsler og applikasjonsatferd. De er ikke bevis på alle SQL-operasjoner. Loggruting styres blant annet av [logback.xml](../innsender/src/main/resources/logback.xml) og manifestets `observability.logging`. |
| Administrative Cloud Audit Logs | Administrasjon av Cloud SQL-ressursen via GCP, som konfigurasjonsendringer. De dekker ikke SQL inne i databasen. Det gjør pgAudit-oppføringene. |

## Tilgang og manglende treff

Data Access-logger krever tilgang til private logger, normalt `roles/logging.privateLogViewer`, eller tilsvarende rettigheter gjennom teamets godkjente tilgangsløsning. Vanlig Logs Viewer gir ikke alene tilgang til Data Access-logger i standardoppsettet. Egendefinerte loggvisninger kan kreve egen tilgang. Tilgang til å lese logger gir ikke i seg selv rett til å koble til databasen eller endre audit-oppsettet.

Ved manglende treff:

1. Kontroller valgt GCP-prosjekt, loggomfang, instans og tidsrom. Fjern eventuelle `READ`- eller `WRITE`-filtre først. Vent noen minutter på logglevering.
2. Kontroller tilgang til Data Access-loggen. Tomt resultat beviser ikke at du har riktig tilgang eller at ingen operasjoner skjedde.
3. Kontroller hvilken databasebruker operasjonen kjørte som. `innsending-api` skal være unntatt. Manglende logger fra vanlig API-trafikk er derfor forventet, ikke en feil i søket.
4. Kontroller de fem flaggene i Cloud SQL mot manifestet. Kontroller at pgAudit-extension finnes og at rolle- og databaseinnstillinger ikke overstyrer `pgaudit.log` for den aktuelle brukeren. Eksisterende forbindelser kan beholde eldre innstillinger. Bruk en ny personlig forbindelse ved kontroll.
5. Kontroller Data Access-oppsettet under **IAM & Admin → Audit Logs**, samt loggruting, eventuelle eksklusjoner, loggbøtter, loggvisninger og lagringstid. Dette konfigureres ikke av audit-flaggene i repoet. Be den som har ansvar for miljøet undersøke dette hvis du mangler tilgang.
6. Kontroller at tidspunktet er etter aktivering og innenfor lagringstiden. pgAudit lager ikke logger for tidligere aktivitet. Google beskriver også en innlesingsgrense på 4 MB/s og midlertidig diskbruk som kan føre til tap ved lagringsproblemer.

Ikke aktiver parameterlogging, fjern applikasjonsunntaket eller endre instansflagg for å få treff. Slike endringer kan eksponere data, øke loggvolumet eller restarte databasen. Følg teamets godkjente driftsprosedyre hvis oppsettet må endres.

Miljøverifisering av grunnsøket og filtrene for `READ` og `WRITE` gjenstår.

## Kilder og vedlikehold

- [Google Cloud, Audit for PostgreSQL using pgAudit](https://docs.cloud.google.com/sql/docs/postgres/pg-audit) beskriver oppsett, søk, loggformat og begrensninger.
- [Google Cloud, Data Access audit logs](https://docs.cloud.google.com/logging/docs/audit/configure-data-access) beskriver aktivering i GCP.
- [Google Cloud, Access control with IAM](https://docs.cloud.google.com/logging/docs/access-control) beskriver tilgang til logger.
- Bruk [refresh-database-schema-docs](../.github/skills/refresh-database-schema-docs/SKILL.md) ved endringer i audit-oppsettet.
