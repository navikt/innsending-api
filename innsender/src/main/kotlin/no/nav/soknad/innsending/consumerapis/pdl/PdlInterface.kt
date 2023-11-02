package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.pdl.generated.GetPrefilledPersonInfo

interface PdlInterface {

	fun hentPersonData(brukerId: String): PersonDto?

	fun hentPersonIdents(brukerId: String): List<IdentDto>

	suspend fun getPrefillPersonInfo(ident: String): GetPrefilledPersonInfo.Result?

}
