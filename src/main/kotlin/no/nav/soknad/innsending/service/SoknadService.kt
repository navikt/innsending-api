package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerAPI
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.SoknadsmottakerAPI
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.FilDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.pdfutilities.PdfMerger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class SoknadService(
	private val skjemaService: HentSkjemaDataConsumer,
	private val soknadRepository: SoknadRepository,
	private val vedleggRepository: VedleggRepository,
	private val filRepository: FilRepository,
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
			VedleggDbData(null, savedSoknadDbData.id!!, OpplastingsStatus.IKKE_VALGT, true, ervariant = false, true
				, kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,kodeverkSkjema.tittel ?: "",
				null, UUID.randomUUID().toString(), null, LocalDateTime.now(), LocalDateTime.now())
		)

		// for hvert vedleggsnr hent fra Sanity og opprett vedlegg.
		val vedleggDbDataListe = vedleggsnrListe
			.map { nr -> skjemaService.hentSkjemaEllerVedlegg(nr, spraak) }
			.map { v ->  vedleggRepository.save(VedleggDbData(null, savedSoknadDbData.id, OpplastingsStatus.IKKE_VALGT,
				 false, ervariant = false, false, v.skjemanummer,v.tittel ?: "", null,
				UUID.randomUUID().toString(), null, LocalDateTime.now(), LocalDateTime.now())) }

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

	// Hent vedlegg, merk filene knyttet til vedlegget ikke lastes opp
	fun hentVedlegg(id: Long): VedleggDto {
		val vedleggDbData = vedleggRepository.findByVedleggsid(id)
		if (vedleggDbData == null) throw Exception("Vedlegg med id=$id ikke funnet")
		//val soknadDbData = soknadRepository.findById(vedleggDbData.soknadsid)

		//val filDbDataList = filRepository.findAllByVedleggsid(vedleggDbData.id!!)

		//return fillagerAPI.hentFiler(soknadDbData.get().innsendingsid, listOf(lagVedleggDto(vedleggDbData))).get(0)

		return lagVedleggDto(vedleggDbData)
	}

	@Transactional
	fun lagreFil(innsendingsId: String, filDto: FilDto): FilDto {
		// TODO Valider opplastet fil, og konverter eventuelt til PDF
		// Sjekk om vedlegget eksisterer
		val vedleggDbData = vedleggRepository.findByVedleggsid(filDto.vedleggsid)
		if (vedleggDbData == null) throw Exception("Vedlegg med id=${filDto.vedleggsid} ikke funnet")

		val savedFilDbData = filRepository.save(mapTilFilDb(filDto))

		return lagFilDto(savedFilDbData)
	}

	fun hentFil(innsendingsId: String, vedleggsId: Long, filId: Long): FilDto {
		// Sjekk om vedlegget eksisterer
		val vedleggDbData = vedleggRepository.findByVedleggsid(vedleggsId)
		if (vedleggDbData == null) throw Exception("Vedlegg med id=${vedleggsId} ikke funnet")

		val filDbDataOpt = filRepository.findById(filId)

		if (!filDbDataOpt.isPresent) throw Exception("Fil med id=$filId eksisterer ikke")
		return lagFilDto(filDbDataOpt.get())
	}

	fun hentFiler(innsendingsId: String, vedleggsId: Long, medFil: Boolean = false): List<FilDto> {
		// Sjekk om vedlegget eksisterer
		val vedleggDbData = vedleggRepository.findByVedleggsid(vedleggsId)
		if (vedleggDbData == null) throw Exception("Vedlegg med id=${vedleggsId} ikke funnet")

		val filDbDataList = filRepository.findAllByVedleggsid(vedleggsId)

		return filDbDataList.map { lagFilDto(it, medFil) }
	}

	fun slettFil(innsendingsId: String, vedleggsId: Long, filId: Long) {
		// Sjekk og vedlegget eksisterer
		val vedleggDbData = vedleggRepository.findByVedleggsid(vedleggsId)
		if (vedleggDbData == null) throw Exception("Vedlegg med id=${vedleggsId} ikke funnet")

		filRepository.deleteById(filId)
	}

	// Slette?
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
	fun slettSoknadAvBruker(innsendingsId: String) {
		// slett vedlegg og soknad
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status == SoknadsStatus.Innsendt) throw Exception("${dokumentSoknadDto.innsendingsId}: Kan ikke slette allerede innsendt soknad")

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { slettVedleggOgDensFiler(it) }
		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		soknadRepository.deleteById(dokumentSoknadDto.id!!)

		val slettetSoknad = lagDokumentSoknadDto(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.SlettetAvBruker)
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id)})
		brukerNotifikasjon.soknadStatusChange(slettetSoknad)
	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		filRepository.deleteFilDbDataForVedlegg(vedleggDto.id!!)
		vedleggRepository.deleteById(vedleggDto.id!!)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAutomatisk(innsendingsId: String) {
		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede filer slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status == SoknadsStatus.Innsendt) throw Exception("${dokumentSoknadDto.innsendingsId}: Kan ikke slette allerede innsendt soknad")

		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { filRepository.deleteFilDbDataForVedlegg(it.id!!)}
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
		soknadRepository.updateEndretDato(soknadsId, LocalDateTime.now())

		val soknadDto = hentSoknad(soknadsId)
		soknadRepository.save(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!))

		val vedleggDtoSaved = lagVedleggDto(vedleggDbData, vedleggDto.document)

		return vedleggDtoSaved
	}

	@Transactional
	fun slettVedleggOgDensFiler(vedleggDto: VedleggDto, soknadsId: Long) {
		// Ikke slette hovedskjema, og ikke obligatoriske. Slette vedlegget og dens opplastede filer
		if (!vedleggDto.erHoveddokument) {
			val soknadDto = hentSoknad(soknadsId)

			if (soknadDto.status == SoknadsStatus.Innsendt) throw Exception("Kan ikke slette vedlegg til allerede innsendt søknad")
			if (!vedleggDto.vedleggsnr.equals("N6")) throw Exception("Kan ikke slette påkrevd vedlegg")
			if (vedleggDto.opplastingsStatus==OpplastingsStatus.INNSENDT) throw Exception("Kan ikke slette ett allerede innsendt vedlegg")

			slettVedleggOgDensFiler(vedleggDto)
			// Oppdatere soknad.sisteendret
			soknadRepository.updateEndretDato(soknadsId, LocalDateTime.now())
			//fillagerAPI.slettFiler(soknadDto.innsendingsId!!, listOf(vedleggDto))
		} else {
			throw RuntimeException("Kan ikke slette hovedskjema på en søknad")
		}
	}

	@Transactional
	fun sendInnSoknad(innsendingsId: String) {

		// Det er ikke nødvendig å opprette og lagre kvittering(L7) i følge diskusjon 3/11.

		// anta at filene til et vedlegg allerede er konvertert til PDF ved lagring, men må merges og sendes til soknadsfillager
		// dersom det ikke er lastet opp filer på et obligatoris vedlegg, skal status settes SENDES_SENERE
		// etter at vedleggsfilen er overført soknadsfillager, skal lokalt lagrede filer på vedlegget slettes.

		val soknadDto = hentSoknad(innsendingsId)

		// Vedleggsliste med opplastede dokument og status= LASTET_OPP for de som skal sendes soknadsfillager
		val alleVedlegg: List<VedleggDto> = ferdigstillVedlegg(soknadDto)
		val opplastedeVedlegg = alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatus.LASTET_OPP }.toList()

		if (opplastedeVedlegg.isNullOrEmpty()) throw Exception("Innsending avbrutt da ingen opplastede filer å sende inn")

		fillagerAPI.lagreFiler(soknadDto.innsendingsId!!, opplastedeVedlegg)

		// send soknadmetada til soknadsmottaker
		soknadsmottakerAPI.sendInnSoknad(soknadDto)

		// oppdater databasen med status og innsendingsdato
/*
		alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatus.LASTET_OPP }. forEach { vedleggRepository.updateStatus(it.id!!, OpplastingsStatus.INNSENDT, LocalDateTime.now()) }
		alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }. forEach { vedleggRepository.updateStatus(it.id!!, OpplastingsStatus.SEND_SENERE, LocalDateTime.now()) }
*/
		opplastedeVedlegg. forEach { vedleggRepository.save(mapTilVedleggDb(it, soknadDto.id!!, OpplastingsStatus.INNSENDT)) }
		alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatus.IKKE_VALGT }. forEach { vedleggRepository.save(mapTilVedleggDb(it, soknadDto.id!!, OpplastingsStatus.SEND_SENERE)) }
		vedleggRepository.flush()

		soknadRepository.saveAndFlush(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt ))
		// send brukernotifikasjon
		brukerNotifikasjon.soknadStatusChange(hentSoknad(innsendingsId))
		// Slett opplastede vedlegg som er sendt til soknadsfillager.
		opplastedeVedlegg.forEach { filRepository.deleteFilDbDataForVedlegg(it.id!!) }
	}

	// For alle vedlegg til søknaden:
	// Hoveddokument kan ha ulike varianter. Hver enkelt av disse sendes som ulike filer til soknadsfillager.
	// Bruker kan ha lastet opp flere filer for øvrige vedlegg. Disse må merges og sendes som en fil.
	private fun ferdigstillVedlegg(soknadDto: DokumentSoknadDto): List<VedleggDto> {
		var vedleggDtos = mutableListOf<VedleggDto>()

		// Listen av varianter av hoveddokumenter, hent lagrede filer og opprett
		soknadDto.vedleggsListe.filter{ it.erHoveddokument }.forEach {
			vedleggDtos.add(lagVedleggDtoMedOpplastetFil(mergeFilDto(soknadDto.innsendingsId!!, it), it) ) }
		// For hvert øvrige vedlegg merge filer og legg til
		soknadDto.vedleggsListe.filter { !it.erHoveddokument }.forEach {
			vedleggDtos.add(lagVedleggDtoMedOpplastetFil(mergeFilDto(soknadDto.innsendingsId!!, it), it)) }
		return vedleggDtos
	}

	private fun mergeFilDto(innsendingsId: String, vedleggDto: VedleggDto): FilDto? {
		val filer = hentFiler(innsendingsId, vedleggDto.id!!, true).filter { it.data != null }
		val mergedFil = if (filer.isNotEmpty()) PdfMerger().mergePdfer(filer.map { it.data!! }) else return null
		return FilDto(null, vedleggDto.id, vedleggDto.vedleggsnr!!, "application/pdf", mergedFil, filer.get(0).opprettetdato)
	}

	private fun lagVedleggDtoMedOpplastetFil(filDto: FilDto?, vedleggDto: VedleggDto) =
		VedleggDto(vedleggDto.id!!, vedleggDto.vedleggsnr, vedleggDto.tittel,
			vedleggDto.uuid, filDto?.mimetype ?: vedleggDto.mimetype, filDto?.data,vedleggDto.erHoveddokument,
			vedleggDto.erVariant, vedleggDto.erPdfa, if (filDto?.data != null) OpplastingsStatus.LASTET_OPP else vedleggDto.opplastingsStatus
			, filDto?.opprettetdato ?: vedleggDto.opprettetdato)



	private  fun lagFilDto(filDbData: FilDbData, medFil: Boolean = true) = FilDto(filDbData.id, filDbData.vedleggsid
									, filDbData.filnavn, filDbData.mimetype, if (medFil) filDbData.data else null, filDbData.opprettetdato)

	private fun lagVedleggDto(vedleggDbData: VedleggDbData, document: ByteArray? = null) =
		VedleggDto(vedleggDbData.id!!, vedleggDbData.vedleggsnr, vedleggDbData.tittel,
			vedleggDbData.uuid, vedleggDbData.mimetype, document ?: vedleggDbData.dokument, vedleggDbData.erhoveddokument,
			vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.status, vedleggDbData.opprettetdato)

	private fun lagDokumentSoknadDto(soknadDbData: SoknadDbData, vedleggDbDataListe: List<VedleggDbData>) =
		DokumentSoknadDto(soknadDbData.id!!, soknadDbData.innsendingsid, soknadDbData.ettersendingsid,
			soknadDbData.brukerid, soknadDbData.skjemanr, soknadDbData.tittel, soknadDbData.tema, soknadDbData.spraak, soknadDbData.skjemaurl,
			soknadDbData.status, soknadDbData.opprettetdato, soknadDbData.endretdato, soknadDbData.innsendtdato
			, vedleggDbDataListe.map { lagVedleggDto(it) })

	private fun mapTilFilDb(filDto: FilDto) = FilDbData(filDto.id, filDto.vedleggsid, filDto.filnavn
							, filDto.mimetype, filDto.data, filDto.opprettetdato ?: LocalDateTime.now())

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, opplastingsStatus: OpplastingsStatus) =
		VedleggDbData(vedleggDto.id, soknadsId, opplastingsStatus
			, vedleggDto.erHoveddokument, vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.vedleggsnr, vedleggDto.tittel
			, vedleggDto.mimetype, vedleggDto.uuid,	vedleggDto.document
			, vedleggDto.opprettetdato, LocalDateTime.now())

	private fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
		VedleggDbData(vedleggDto.id, soknadsId, vedleggDto.opplastingsStatus
			, vedleggDto.erHoveddokument, vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.vedleggsnr, vedleggDto.tittel
			, vedleggDto.mimetype, vedleggDto.uuid,	vedleggDto.document
			, vedleggDto.opprettetdato, LocalDateTime.now())

	private fun mapTilSoknadDb(dokumentSoknadDto: DokumentSoknadDto, innsendingsId: String, status: SoknadsStatus? = SoknadsStatus.Opprettet) =
		SoknadDbData(dokumentSoknadDto.id, innsendingsId,
			dokumentSoknadDto.tittel, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tema, dokumentSoknadDto.spraak,
			status ?: dokumentSoknadDto.status, dokumentSoknadDto.brukerId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.opprettetDato, LocalDateTime.now(), if (status == SoknadsStatus.Innsendt) LocalDateTime.now() else dokumentSoknadDto.innsendtDato, dokumentSoknadDto.skjemaurl)
}
