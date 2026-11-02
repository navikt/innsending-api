package no.nav.soknad.innsending.consumerapis.texas

/**
 * Vi fikk ikke token fra Texas. Feilen ligger hos oss, ikke hos den som kaller innsending-api,
 * så den skal gi 5xx og aldri 401 eller 403. Meldingen skal aldri inneholde token eller respons-body.
 */
class TexasUnavailableException(message: String) : RuntimeException(message)
