package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import javax.transaction.Transactional

@Service
open class SoknadService(
	private val skjemaService: HentSkjemaDataConsumer,
	private val soknadRepository: SoknadRepository,
	private val vedleggRepository: VedleggRepository
) {

	@Transactional
	open fun opprettSoknad(brukerId: String, skjemanr: String, spraak: String = "no", vedleggsnrListe: List<String>?): DokumentSoknadDto {
		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = skjemaService.hentSkjemaEllerVedlegg(skjemanr, spraak)

		// lagre soknad og vedlegg
		val soknadDbData = soknadRepository.save(
			SoknadDbData(null, UUID.randomUUID().toString(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
				kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, null, LocalDateTime.now(),
				LocalDateTime.now(), null, kodeverkSkjema.url)
		)

		val vedleggDbData = vedleggRepository.save(
			VedleggDbData(null,kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,
				null, OpplastingsStatus.IKKE_VALGT, true, ervariant = false, true,
				UUID.randomUUID().toString(), null, soknadDbData.id ?: 1L, LocalDateTime.now(), LocalDateTime.now())
		)

		// TODO lagre vedlegg basert på vedleggsnrsliste

		val vedleggsliste = listOf(lagVedleggDto(vedleggDbData))

		// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
		return lagDokumentSoknadDto(soknadDbData, vedleggsliste)

	}

	fun hentSoknad(id: Long): DokumentSoknadDto {

		val soknadDbDataOpt = soknadRepository.findById(id)

		if (soknadDbDataOpt.isPresent) {
			val soknadDbData = soknadDbDataOpt.get()
			val vedleggDbDataListe = vedleggRepository.findAllBySoknadsid(soknadDbData.id!!)
			val vedleggDtoListe = vedleggDbDataListe.map { lagVedleggDto(it) }

			return lagDokumentSoknadDto(soknadDbData, vedleggDtoListe)
		}
		throw RuntimeException("Ingen dokumentsoknad med id = $id funnet")
	}

	fun hentSoknad(behandlingsId: String): DokumentSoknadDto {

		val soknadDbDataOpt = soknadRepository.findByBehandlingsid(behandlingsId)

		if (soknadDbDataOpt.isPresent) {
			val soknadDbData = soknadDbDataOpt.get()
			val vedleggDbDataListe = vedleggRepository.findAllBySoknadsid(soknadDbData.id!!)
			val vedleggDtoListe = vedleggDbDataListe.map { lagVedleggDto(it) }

			return lagDokumentSoknadDto(soknadDbData, vedleggDtoListe)
		}
		throw RuntimeException("Ingen dokumentsoknad med id = $behandlingsId funnet")
	}

	fun hentVedlegg(id: Long): VedleggDto {

		val vedleggDbData = vedleggRepository.findByVedleggsid(id)

		return lagVedleggDto(vedleggDbData)
	}

	@Transactional
	fun opprettEllerOppdaterSoknad(dokumentSoknadDto: DokumentSoknadDto): Long {
		var soknadsid = dokumentSoknadDto.id
		// Hvis soknad ikke eksisterer må den lagres, før vedleggene
		if (dokumentSoknadDto.id == null) {
			val savedSoknadDbData = soknadRepository.save(mapTilSoknadDb(dokumentSoknadDto))
			soknadsid = savedSoknadDbData.id
		}
		dokumentSoknadDto.vedleggsListe
			.map { mapTilVedleggDb(it, soknadsid!!) }
			.forEach { vedleggRepository.save(it) }
		soknadRepository.save(mapTilSoknadDb(dokumentSoknadDto))
		return soknadsid!!
	}

	@Transactional
	fun slettSoknad(soknadId: Long) {
		// slett vedlegg og soknad
		val dokumentSoknadDto = hentSoknad(soknadId)
		dokumentSoknadDto.vedleggsListe.forEach { v -> vedleggRepository.deleteById(v.id!!) }
		soknadRepository.deleteById(dokumentSoknadDto.id!!)
	}

	fun lagreVedlegg(vedleggDto: VedleggDto, soknadsId: Long): VedleggDto {
		val vedleggDbData = vedleggRepository.save(mapTilVedleggDb(vedleggDto, soknadsId))
		// Oppdatere soknad.sisteendret
		val soknadDto = hentSoknad(soknadsId)
		soknadRepository.save(mapTilSoknadDb(soknadDto))
		return lagVedleggDto(vedleggDbData)
	}

	fun slettVedlegg(vedleggDto: VedleggDto, soknadsId: Long) {
		// Ikke slette hovedskjema
		if (!vedleggDto.erHoveddokument && vedleggDto.mimetype != "pdf") {
			vedleggRepository.deleteById(vedleggDto.id!!)
			// Oppdatere soknad.sisteendret
			val soknadDto = hentSoknad(soknadsId)
			soknadRepository.save(mapTilSoknadDb(soknadDto))
		}
		throw RuntimeException("Kan ikke slette hovedskjema på en søknad")
	}

	fun sendInnSoknad(soknadsId: Long) {

		// opprett kvittering(L7)

		// send vedlegg til soknadsfillager
		// send soknasmetada til soknadsmottaker
		// oppdater databasen med innsendingsdato
		// slett søknadens dokumenter i vedleggstabellen.
	}

	private fun lagVedleggDto(vedleggDbData: VedleggDbData) =
		VedleggDto(vedleggDbData.id!!, vedleggDbData.vedleggsnr, vedleggDbData.tittel,
			vedleggDbData.uuid, vedleggDbData.mimetype, vedleggDbData.dokument, vedleggDbData.erhoveddokument,
			vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.status, vedleggDbData.opprettetdato)

	private fun lagDokumentSoknadDto(soknadDbData: SoknadDbData, vedleggDtoListe: List<VedleggDto>) =
		DokumentSoknadDto(soknadDbData.id ?: 1L, soknadDbData.behandlingsid, soknadDbData.ettersendingsid,
			soknadDbData.brukerid, soknadDbData.skjemanr, soknadDbData.tittel, soknadDbData.tema, soknadDbData.spraak, soknadDbData.skjemaurl,
			soknadDbData.status, soknadDbData.opprettetdato, soknadDbData.endretdato, soknadDbData.innsendtdato, vedleggDtoListe)

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
		VedleggDbData(vedleggDto.id, vedleggDto.tittel, vedleggDto.vedleggsnr, vedleggDto.mimetype,
			vedleggDto.opplastingsStatus, vedleggDto.erHoveddokument, vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.uuid,
			vedleggDto.document, soknadsId, vedleggDto.opprettetdato, LocalDateTime.now())

	private fun mapTilSoknadDb(dokumentSoknadDto: DokumentSoknadDto) =
		SoknadDbData(dokumentSoknadDto.id, dokumentSoknadDto.behandlingsId ?: UUID.randomUUID().toString(),
			dokumentSoknadDto.tittel, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tema, dokumentSoknadDto.spraak,
			dokumentSoknadDto.status, dokumentSoknadDto.brukerId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.opprettetDato, LocalDateTime.now(), dokumentSoknadDto.innsendtDato,dokumentSoknadDto.skjemaurl)
}
