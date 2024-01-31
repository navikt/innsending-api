package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.utils.Skjema.generateSkjemanr
import java.time.LocalDateTime
import java.util.*

data class SoknadDbDataTestBuilder(
	var id: Long? = null,
	var innsendingsId: String = UUID.randomUUID().toString(),
	var tittel: String = "Avtale om barnebidrag",
	var skjemanr: String = generateSkjemanr(),
	var tema: String = "BID",
	var spraak: String = "nb",
	var status: SoknadsStatus = SoknadsStatus.Opprettet,
	var brukerId: String = "12345678901",
	var ettersendingsId: String? = null,
	var opprettetDato: LocalDateTime = LocalDateTime.now(),
	var endretDato: LocalDateTime = LocalDateTime.now(),
	var innsendtdato: LocalDateTime = LocalDateTime.now(),
	var visningsSteg: Long = 0,
	var visningsType: VisningsType = VisningsType.fyllUt,
	var kanLasteOppAnnet: Boolean = false,
	var forsteinnsendingsdato: LocalDateTime? = null,
	var ettersendingsFrist: Long = 14,
	var arkiveringsStatus: ArkiveringsStatus = ArkiveringsStatus.IkkeSatt,
	var applikasjon: String? = "fyllut",
) {
	fun build() = SoknadDbData(
		id = id,
		innsendingsid = innsendingsId,
		tittel = tittel,
		skjemanr = skjemanr,
		tema = tema,
		spraak = spraak,
		status = status,
		brukerid = brukerId,
		ettersendingsid = ettersendingsId,
		opprettetdato = opprettetDato,
		endretdato = endretDato,
		innsendtdato = innsendtdato,
		visningssteg = visningsSteg,
		visningstype = visningsType,
		kanlasteoppannet = kanLasteOppAnnet,
		forsteinnsendingsdato = forsteinnsendingsdato,
		ettersendingsfrist = ettersendingsFrist,
		arkiveringsstatus = arkiveringsStatus,
		applikasjon = applikasjon,
	)
}
