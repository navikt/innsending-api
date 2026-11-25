# Forretningsregler og funksjonalitet

## Aktører for en søknad

Eier, berørt bruker og avsender beskriver ulike roller. De kan være samme person, men skal ikke behandles som samme rolle. Hvem som oppretter eller sender inn søknaden, beskriver en handling og hører hjemme i hendelsesloggen.

Reglene nedenfor beskriver kontrakten for release 2 av eiermigreringen. Se [databaseskjemaet](database-schema.md#deployment-og-rollback-av-eiermigreringen) for forutsetninger for deploy og rollback.

### Roller og lagring

| Rolle eller begrep | Betydning | Tabell og kolonne | Bruk og plan for kolonnen |
| --- | --- | --- | --- |
| Eier | Den søknaden tilhører. | `soknad.owner`, JSON med `id` og `idType` | Personeier med `idType=FNR`. Styrer personbasert tilgang, søknadsoversikt og varsler. |
| Berørt bruker | Personen saken gjelder. | `soknad.affecteduser`, JSON med `id` og `idType` | Angir personen saken gjelder ved arkivering, uavhengig av eier. Ved innsending og retry skal manglende verdi ikke erstattes med eier eller `brukerid`. |
| Avsender | Parten som oppgis som avsender av dokumentene. Ikke nødvendigvis personen som utfører innsendingen. | `soknad.avsender`, JSON med feltene `id`, `idType` og `navn` | Skal angi avsender ved arkivering. I dag brukes `brukerid` som personavsender hvis feltet mangler. |
| Brukeridentifikator | Historisk kobling mellom søknaden og en bruker. | `soknad.brukerid` | Beholdes som historisk felt, men skal på sikt fjernes. Gir ikke personbasert tilgang og er ikke reserveverdi for berørt bruker i løpende innsending eller retry. Brukes fortsatt som reserveverdi for avsender ved arkivering. Heter `brukerId` i søknadens DTO. Nav-appene sender personen saken gjelder, ikke nødvendigvis eieren. |
| Innlogget bruker | Personen som utfører det aktuelle kallet. | Hentes fra TokenX-tokenet. Ingen egen aktørkolonne. | Brukes ved oppretting og tilgangskontroll. |
| Opprettet av og sendt inn av | Den som utførte opprettingen eller bekreftet innsendingen. | `hendelse.hendelsetype`, `hendelse.tidspunkt` og `hendelse.innsendingsid` beskriver og kobler hendelsen til søknaden. Ingen aktørkolonne i dag. | Vi planlegger å lagre aktøridentiteten på hendelsen, ikke på søknaden. Dette er ikke implementert ennå. Innsendingshendelsen skal bevare hvem som bekreftet innsendingen. `hendelse.applikasjon` identifiserer ikke en person. |

### Eierskap og tilgang

Ved vanlig innlogget oppretting settes `brukerid` fra tokenidentiteten, og `owner` settes fra denne verdien. Oppdateringer bevarer owner.

En ettersending som opprettes av en innlogget bruker, får denne brukeren som eier, også når den kobles til en original med en annen eier. Ved identbytte brukes den nye identen.

Nav-initierte ettersendinger koblet til en lokal original og automatiske etterfølgere arver originalens verifiserte, gyldige owner. Kan eieren på den lokale originalen ikke verifiseres, avvises flyten fremfor å bruke `brukerId` som reserveeier. Finnes arkivoriginalen ikke lokalt, beholdes dagens regel som setter eier fra `brukerId`. Nav-appene sender personen saken gjelder som `brukerId`; denne personen er ikke nødvendigvis originalens eier.

Søknader sendt inn uinnlogget ignoreres når vi velger en original for ettersending. Vi velger neste aktuelle søknad, eller oppretter en frittstående ettersending hvis ingen aktuelle originaler finnes. Sjekken bruker dagens markører, `visningstype = nologin` lokalt og kanalen `NAV_NO_UINNLOGGET` i arkivet, ikke manglende owner.

Det er fortsatt uavklart om en personopprettet ettersending bør kobles til en original som den innloggede brukeren ikke eide. Inntil videre beholdes denne koblingen.

V1.45 skal bevare en eksisterende gyldig owner, også når eieren er forskjellig fra `brukerid`. Manglende owner på historiske søknader skal bare fylles fra `brukerid` i etablerte digitale personflyter, med `idType=FNR`. Migreringen skal ikke utlede historiske representantforhold. Uinnloggede søknader skal ha SQL `NULL` i owner, selv om en bruker er oppgitt i requesten.

Som engangs historisk backfill fyller V1.45 også manglende `affecteduser` fra `brukerid`, både for digitale søknader og nologin. Dette bevarer identiteten som den tidligere arkiveringskontrakten brukte. En eksisterende gyldig digital part bevares, også når den er ulik eieren. Datagrunnlaget kan ikke rekonstruere en historisk uidentifisert tredjeperson. Denne migreringsregelen er ikke en løpende fallback ved innsending eller retry; nye nullverdier skal bevares.

Ved digital innsending på vegne av en annen person lagres denne som `affecteduser`. Det endrer ikke owner eller gir den berørte brukeren tilgang.

Personruter og personlige søknadslister skal bare bruke gyldig, lagret owner mot innlogget persons aktuelle og historiske PDL-identer. Det er ingen fallback til `brukerid`, berørt bruker eller avsender. Manglende eller ugyldig owner gir ikke personbasert tilgang.

Den særskilt autoriserte Nav-flyten finner originaler ut fra personen saken gjelder i `affecteduser`, med aktuelle og historiske PDL-identer. Oppgavelister og duplikatsjekker i denne flyten bruker samme avgrensning, uten fallback til historisk `brukerid` eller owner. Slike oppslag gir ikke den berørte personen tilgang gjennom personrutene. Oppgavelisten kan også inneholde personopprettede ettersendinger for den berørte personen; responsens `brukerId` beholder den lagrede legacy-identiteten og er ikke nødvendigvis personen Nav søkte på.

### Berørt bruker ved innsending

Feltet `bruker` i den endelige innsendingsrequesten bestemmer lagret `affecteduser`. Utelatt felt og eksplisitt `null` fjerner den berørte parten, også når et utkast allerede har en verdi. Verken owner eller `brukerid` skal brukes som reserveverdi.

Den eldre innsendingsflyten uten request-body bevarer lagret `affecteduser`, også `NULL`. Arkivering og retry bruker den lagrede parten uendret. Dette endrer ikke owner eller personbasert tilgang.

### Hvem som bekrefter innsendingen

Identiteten skal hentes fra det verifiserte tokenet når personen bekrefter innsendingen, og lagres varig på hendelsen. Historisk `brukerid` beviser ikke hvem som utførte handlingen. Ukjent aktør skal forbli ukjent. Det er ikke et krav å registrere aktør for alle handlinger.

Organisasjonseierskap støttes ikke i dag. Når eieren er en organisasjon, skal hendelsen kunne vise personen som bekreftet innsendingen, og organisasjonen personen handlet på vegne av. Dette gir ikke personen varig tilgang. Tilgang skal følge gjeldende rettigheter til å representere eieren.

Se [databaseskjemaet](database-schema.md) for tabeller og kolonner, og [auditlogging](auditlogging.md) for den separate tilgangsloggingen.
