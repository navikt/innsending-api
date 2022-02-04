package no.nav.soknad.innsending.rest

data class OpprettSoknadBody(val brukerId: String, val skjemanr: String, val sprak: String, val vedleggsListe: List<String>?)

data class OpprettEttersendingGittInnsendingsId(val brukerId: String, val ettersendingTilinnsendingsId: String)

data class OpprettEttersendingGittSkjemaNr(val brukerId: String, val skjemanr: String, val sprak: String, val vedleggsListe: List<String>?)

