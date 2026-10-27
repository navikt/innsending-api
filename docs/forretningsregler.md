# Forretningsregler og funksjonalitet

## Aktører for en søknad

Eier, berørt bruker og avsender beskriver ulike roller. De kan være samme person, men skal ikke behandles som samme rolle. Hvem som oppretter eller sender inn søknaden, beskriver en handling og hører hjemme i hendelsesloggen.

### Roller og lagring

| Rolle eller begrep | Betydning | Tabell og kolonne | Bruk og plan for kolonnen |
| --- | --- | --- | --- |
| Eier | Den søknaden tilhører. | `soknad.owner`, JSON med `id` og `idType` | Lagres i dag som personeier med `idType=FNR`. Skal styre tilgang, brukerrettede lister og varselmottaker. |
| Berørt bruker | Personen saken gjelder. | `soknad.affecteduser`, JSON med `id` og `idType` | Skal angi personen saken gjelder ved arkivering, uavhengig av eier. I dag brukes `brukerid` hvis feltet mangler. |
| Avsender | Parten som oppgis som avsender av dokumentene. Ikke nødvendigvis personen som utfører innsendingen. | `soknad.avsender`, JSON med feltene `id`, `idType` og `navn` | Skal angi avsender ved arkivering. I dag brukes `brukerid` som personavsender hvis feltet mangler. |
| Brukeridentifikator | Historisk kobling mellom søknaden og en bruker. | `soknad.brukerid` | Skal fjernes fordi feltet blander tilgang, varsling og partsopplysninger. Styrer fortsatt tilgang, lister og varsling, og er reserveverdi for berørt bruker og avsender. Heter `brukerId` i søknadens DTO. |
| Innlogget bruker | Personen som utfører det aktuelle kallet. | Hentes fra TokenX-tokenet. Ingen egen aktørkolonne. | Brukes ved oppretting og tilgangskontroll. |
| Opprettet av og sendt inn av | Den som utførte opprettingen eller bekreftet innsendingen. | `hendelse.hendelsetype`, `hendelse.tidspunkt` og `hendelse.innsendingsid` beskriver og kobler hendelsen til søknaden. Ingen aktørkolonne i dag. | Aktøridentiteten skal ligge på hendelsen, ikke på søknaden. Innsendingshendelsen skal bevare hvem som bekreftet innsendingen. `hendelse.applikasjon` identifiserer ikke en person. |

### Eierskap og tilgang

Ved vanlig innlogget oppretting settes `brukerid` fra tokenidentiteten, og `owner` settes fra denne verdien. Oppdateringer bevarer owner. Ettersendinger knyttet til en original arver originalens owner, også når verdien er `NULL`.

Manglende owner på historiske søknader i etablerte digitale personflyter skal fylles fra `brukerid` med `idType=FNR`. Uinnloggede søknader skal ha SQL `NULL` i owner, selv om en bruker er oppgitt i requesten.

Ved digital innsending på vegne av en annen person lagres denne som `affecteduser`. Det endrer ikke owner eller gir den berørte brukeren tilgang.

Personbasert tilgang skal bruke gyldig owner mot aktuelle og historiske PDL-identer, uten fallback til `brukerid`, berørt bruker eller avsender. Dette erstatter dagens kontroll mot `brukerid`.

### Hvem som bekrefter innsendingen

Identiteten skal hentes fra det verifiserte tokenet når personen bekrefter innsendingen, og lagres varig på hendelsen. Historisk `brukerid` beviser ikke hvem som utførte handlingen. Ukjent aktør skal forbli ukjent. Det er ikke et krav å registrere aktør for alle handlinger.

Organisasjonseierskap støttes ikke i dag. Når eieren er en organisasjon, skal hendelsen kunne vise personen som bekreftet innsendingen, og organisasjonen personen handlet på vegne av. Dette gir ikke personen varig tilgang. Tilgang skal følge gjeldende rettigheter til å representere eieren.

Se [databaseskjemaet](database-schema.md) for tabeller og kolonner, og [auditlogging](auditlogging.md) for den separate tilgangsloggingen.
