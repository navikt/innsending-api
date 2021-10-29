package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerAPI
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.SoknadsmottakerAPI
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import javax.transaction.Transactional

@Service
class SoknadService(
	private val skjemaService: HentSkjemaDataConsumer,
	private val soknadRepository: SoknadRepository,
	private val vedleggRepository: VedleggRepository,
	private val brukerNotifikasjon: BrukernotifikasjonPublisher,
	private val fillagerAPI: FillagerAPI,
	private val soknadsmottakerAPI: SoknadsmottakerAPI
) {

	@Transactional
	fun opprettSoknad(brukerId: String, skjemanr: String, spraak: String = "no", vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = skjemaService.hentSkjemaEllerVedlegg(skjemanr, spraak)

		// lagre soknad og vedlegg
		val savedSoknadDbData = soknadRepository.save(
			SoknadDbData(null, Utilities.laginnsendingsId(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
				kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, null, LocalDateTime.now(),
				LocalDateTime.now(), null, kodeverkSkjema.url)
		)

		// Lagre skjema
		val skjemaDbData = vedleggRepository.save(
			VedleggDbData(null,kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,
				null, OpplastingsStatus.IKKE_VALGT, true, ervariant = false, true,
				UUID.randomUUID().toString(), null, savedSoknadDbData.id!!, LocalDateTime.now(), LocalDateTime.now())
		)

		// for hvert vedleggsnr hent fra Sanity og opprett vedlegg.
		val vedleggDbDataListe = vedleggsnrListe
			.map { nr -> skjemaService.hentSkjemaEllerVedlegg(nr, spraak) }
			.map { v ->  vedleggRepository.save(VedleggDbData(null,v.tittel ?: "", v.skjemanummer,
				null, OpplastingsStatus.IKKE_VALGT, false, ervariant = false, true,
				UUID.randomUUID().toString(), null, savedSoknadDbData.id, LocalDateTime.now(), LocalDateTime.now())) }

		val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

		val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
		brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto)
		// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
		return dokumentSoknadDto
	}

	// Hent soknad gitt id med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(id: Long): DokumentSoknadDto {
		val soknadDbDataOpt = soknadRepository.findById(id)
		return hentAlleVedlegg(soknadDbDataOpt, id.toString())
	}

	// Hent soknad gitt innsendingsid med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDbDataOpt = soknadRepository.findByInnsendingsid(innsendingsId)
		return hentAlleVedlegg(soknadDbDataOpt, innsendingsId)
	}

	private fun hentAlleVedlegg(soknadDbDataOpt: Optional<SoknadDbData>, ident: String): DokumentSoknadDto {
		if (soknadDbDataOpt.isPresent) {
			val soknadDbData = soknadDbDataOpt.get()
			val vedleggDbDataListe = vedleggRepository.findAllBySoknadsid(soknadDbData.id!!)

			return lagDokumentSoknadDto(soknadDbData, vedleggDbDataListe)
		}
		throw RuntimeException("Ingen dokumentsoknad med id = $ident funnet")

	}

	// Hent vedlegg med eventuelt opplastet dokument hentet fra fillager
	fun hentVedlegg(id: Long): VedleggDto {
		val vedleggDbData = vedleggRepository.findByVedleggsid(id)
		if (vedleggDbData == null) throw Exception("Vedlegg med id=$id ikke funnet")
		val soknadDbData = soknadRepository.findById(vedleggDbData.soknadsid)

		return fillagerAPI.hentFiler(soknadDbData.get().innsendingsid, listOf(lagVedleggDto(vedleggDbData))).get(0)
	}

	@Transactional
	fun opprettEllerOppdaterSoknad(dokumentSoknadDto: DokumentSoknadDto): String {
		val nySoknad = dokumentSoknadDto.id == null
		val innsendingsId = dokumentSoknadDto.innsendingsId ?: Utilities.laginnsendingsId()
		// Hvis soknad ikke eksisterer må den lagres, før vedleggene
		val savedSoknadDbData = soknadRepository.save(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
		val soknadsid = savedSoknadDbData.id
		val savedVedleggDbData = dokumentSoknadDto.vedleggsListe
			.map { vedleggRepository.save(mapTilVedleggDb(it, soknadsid!!)) }

		val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)
		brukerNotifikasjon.soknadStatusChange(savedDokumentSoknadDto)

		return innsendingsId
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknad(innsendingsId: String) {
		// slett vedlegg og soknad
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status == SoknadsStatus.Innsendt) throw Exception("${dokumentSoknadDto.innsendingsId}: Kan ikke slette allerede innsendt soknad")

		dokumentSoknadDto.vedleggsListe.forEach { v -> vedleggRepository.deleteById(v.id!!) }
		fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		soknadRepository.deleteById(dokumentSoknadDto.id!!)

		val slettetSoknad = lagDokumentSoknadDto(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.SlettetAvBruker)
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id)})
		brukerNotifikasjon.soknadStatusChange(slettetSoknad)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAutomatisk(innsendingsId: String) {
		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede dokumenter slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status == SoknadsStatus.Innsendt) throw Exception("${dokumentSoknadDto.innsendingsId}: Kan ikke slette allerede innsendt soknad")

		fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		val slettetSoknadDb = soknadRepository.save(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet))

		val slettetSoknadDto = lagDokumentSoknadDto(slettetSoknadDb
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!)})
		brukerNotifikasjon.soknadStatusChange(slettetSoknadDto)
	}

	@Transactional
	fun lagreVedlegg(vedleggDto: VedleggDto, soknadsId: Long): VedleggDto {

		// Lagre vedlegget i databasen
		val vedleggDbData = vedleggRepository.save(mapTilVedleggDb(vedleggDto, soknadsId))

		// Oppdater soknadens sist endret dato
		val soknadDto = hentSoknad(soknadsId)
		soknadRepository.save(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!))

		val vedleggDtoSaved = lagVedleggDto(vedleggDbData, vedleggDto.document)

		// Lagre dokumentet i soknadsfillager
		fillagerAPI.lagreFiler(soknadDto.innsendingsId, listOf(vedleggDtoSaved))
		return vedleggDtoSaved
	}

	@Transactional
	fun slettVedlegg(vedleggDto: VedleggDto, soknadsId: Long) {
		// Ikke slette hovedskjema, og ikke obligatoriske? Slette kun dokumentet?
		if (!vedleggDto.erHoveddokument) {
			val soknadDto = hentSoknad(soknadsId)
			if (soknadDto.status == SoknadsStatus.Innsendt) throw Exception("Kan ikke slette vedlegg til allerede innsendt søknad")
			vedleggRepository.deleteById(vedleggDto.id!!)
			// Oppdatere soknad.sisteendret
			soknadRepository.save(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!))
			fillagerAPI.slettFiler(soknadDto.innsendingsId, listOf(vedleggDto))
		} else {
			throw RuntimeException("Kan ikke slette hovedskjema på en søknad")
		}
	}

	fun sendInnSoknad(innsendingsId: String) {

		// opprett kvittering(L7)?

		// antar at vedlegg allerede sendt til soknadsfillager, men skal kanskje også sende kvittering?
		// slett søknadens dokumenter i vedleggstabellen er ikke nødvendig da de lagres i soknadsfillager istedenfor

		val soknadDto = hentSoknad(innsendingsId)
		// send soknadmetada til soknadsmottaker
		soknadsmottakerAPI.sendInnSoknad(soknadDto)
		// oppdater databasen med innsendingsdato
		soknadRepository.saveAndFlush(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt ))
		// send brukernotifikasjon
		brukerNotifikasjon.soknadStatusChange(hentSoknad(innsendingsId))
	}

	private fun lagVedleggDto(vedleggDbData: VedleggDbData, document: ByteArray? = null) =
		VedleggDto(vedleggDbData.id!!, vedleggDbData.vedleggsnr, vedleggDbData.tittel,
			vedleggDbData.uuid, vedleggDbData.mimetype, document ?: vedleggDbData.dokument, vedleggDbData.erhoveddokument,
			vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.status, vedleggDbData.opprettetdato)

	private fun lagDokumentSoknadDto(soknadDbData: SoknadDbData, vedleggDbDataListe: List<VedleggDbData>) =
		DokumentSoknadDto(soknadDbData.id!!, soknadDbData.innsendingsid, soknadDbData.ettersendingsid,
			soknadDbData.brukerid, soknadDbData.skjemanr, soknadDbData.tittel, soknadDbData.tema, soknadDbData.spraak, soknadDbData.skjemaurl,
			soknadDbData.status, soknadDbData.opprettetdato, soknadDbData.endretdato, soknadDbData.innsendtdato
			, vedleggDbDataListe.map { lagVedleggDto(it) })

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
		VedleggDbData(vedleggDto.id, vedleggDto.tittel, vedleggDto.vedleggsnr, vedleggDto.mimetype,
			vedleggDto.opplastingsStatus, vedleggDto.erHoveddokument, vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.uuid,
			vedleggDto.document, soknadsId, vedleggDto.opprettetdato, LocalDateTime.now())

	private fun mapTilSoknadDb(dokumentSoknadDto: DokumentSoknadDto, innsendingsId: String, status: SoknadsStatus? = SoknadsStatus.Opprettet) =
		SoknadDbData(dokumentSoknadDto.id, innsendingsId,
			dokumentSoknadDto.tittel, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tema, dokumentSoknadDto.spraak,
			status ?: dokumentSoknadDto.status, dokumentSoknadDto.brukerId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.opprettetDato, LocalDateTime.now(), if (status == SoknadsStatus.Innsendt) LocalDateTime.now() else dokumentSoknadDto.innsendtDato, dokumentSoknadDto.skjemaurl)
}
