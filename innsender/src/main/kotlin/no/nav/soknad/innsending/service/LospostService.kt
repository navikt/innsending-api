package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.LospostDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.innsending.util.mapping.lagDokumentSoknadDto
import no.nav.soknad.innsending.util.mapping.mapTilLospost
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Service
class LospostService(
	private val repo: RepositoryUtils,
	private val subjectHandler: SubjectHandlerInterface,
	private val brukernotifikasjon: BrukernotifikasjonPublisher
) {

	@Transactional
	fun saveLospostInnsending(
		brukerId: String,
		tema: String,
		tittel: String,
		dokumentTittel: String,
		sprak: String,
	): LospostDto {
		val innsendingsId = Utilities.laginnsendingsId()
		val applikasjon = subjectHandler.getClientId()
		val now = LocalDateTime.now()
		val soknad = repo.lagreSoknad(
			SoknadDbData(
				id = null,
				brukerid = brukerId,
				innsendingsid = innsendingsId,
				spraak = finnSpraakFraInput(sprak),
				kanlasteoppannet = true,
				skjemanr = Constants.LOSPOST_SKJEMANUMMER,
				tittel = tittel,
				tema = tema,
				status = SoknadsStatus.Opprettet,
				visningstype = VisningsType.lospost,
				opprettetdato = now,
				endretdato = now,
				arkiveringsstatus = ArkiveringsStatus.IkkeSatt,
				applikasjon = applikasjon,
				forsteinnsendingsdato = null,
				ettersendingsid = null,
				innsendtdato = null,
				skalslettesdato = OffsetDateTime.now().plusDays(Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD)
			)
		)
		val vedlegg = repo.lagreVedlegg(
			VedleggDbData(
				id = null,
				soknadsid = soknad.id!!,
				uuid = UUID.randomUUID().toString(),
				status = OpplastingsStatus.IKKE_VALGT,
				tittel = dokumentTittel,
				label = dokumentTittel,
				vedleggsnr = "N6",
				opprettetdato = now,
				endretdato = now,
				erhoveddokument = true,
				erpakrevd = true,
				ervariant = false,
				erpdfa = false,
				beskrivelse = null,
				mimetype = null,
				innsendtdato = null,
				vedleggsurl = null,
				formioid = null,
			)
		)
		try {
			val dokumentSoknadDto = lagDokumentSoknadDto(soknad, listOf(vedlegg))
			brukernotifikasjon.soknadStatusChange(dokumentSoknadDto)
		} catch (e: Exception) {
			throw BackendErrorException(
				"$innsendingsId: Feil under opprettelse av brukernotifikasjon i forbindelse med løspost",
				e
			)
		}
		return mapTilLospost(soknad, vedlegg)
	}

}
