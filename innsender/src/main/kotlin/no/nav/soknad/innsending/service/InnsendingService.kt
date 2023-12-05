package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.*
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.KVITTERINGS_NR
import no.nav.soknad.innsending.util.mapping.*
import no.nav.soknad.innsending.util.models.erEttersending
import no.nav.soknad.innsending.util.models.hovedDokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import no.nav.soknad.pdfutilities.PdfGenerator
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Service
class InnsendingService(
	private val repo: RepositoryUtils,
	private val soknadService: SoknadService,
	private val filService: FilService,
	private val vedleggService: VedleggService,
	private val soknadsmottakerAPI: MottakerInterface,
	private val restConfig: RestConfig,
	private val exceptionHelper: ExceptionHelper,
	private val ettersendingService: EttersendingService,
	private val brukernotifikasjonPublisher: BrukernotifikasjonPublisher,
	private val innsenderMetrics: InnsenderMetrics,
	private val pdlInterface: PdlInterface,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val tilleggsstonadSkjema =
		listOf(
			"NAV 11-12.10",
			"NAV 11-12.11",
			"NAV 11-12.12",
			"NAV 11-12.13",
			"NAV 11-12.14",
			"NAV 11-12.12R",
			"NAV 11-12.12B",
			"NAV 11-12.12O",
			"NAV 11-12.12L",
			"NAV 11-12.12F",
		)

	@Transactional
	fun sendInnSoknadStart(soknadDtoInput: DokumentSoknadDto): Pair<List<VedleggDto>, List<VedleggDto>> {
		val operation = InnsenderOperation.SEND_INN.name

		// Anta at filene til et vedlegg allerede er konvertert til PDF ved lagring.
		// Eventuell varianter av hoveddokument skal være på annet format enn PDF, da formatet vil bli brukt for å mappe til arkivformat ved lagring, og for arkivformatet på variantene på et dokument må være ulike.
		// Det må valideres at en søknad har opplastet hoveddokument og at en ettersendingssøknad har opplastet vedlegg eller endring på et vedleggs opplastinsstatus.
		// Det må valideres på at totalstørrelsen på søknaden ikke overskrider maxFileSizeTotalSum
		// Dummy hoveddokument skal genereres og sendes inn for ettersendingssøknader
		// Kvittering skal genereres og sendes sammen med søknad/vedlegg

		// Etter vellykket innsending skal innsendingsdato settes og status for søknad/ettersendinngssøknad settes til INNSENDT.
		// Dato og opplastingsstatus settes på alle vedlegg som er endret
		// Merk at dersom det ikke er lastet opp filer på et obligatorisk vedlegg, skal status settes SENDES_SENERE. Dette vil trigge oppretting av ettersendingssøknad
		val soknadDto = if (soknadDtoInput.erEttersending)
			addDummyHovedDokumentToSoknad(soknadDtoInput)
		else {

			if (isTilleggsstonad(soknadDtoInput)) {
				addXmlDokumentvariantToSoknad(soknadDtoInput)
			} else {
				soknadDtoInput
			}
		}

		// Finn alle vedlegg med korrekt status i forhold til hvem som skal sendes inn
		val alleVedlegg: List<VedleggDto> = filService.ferdigstillVedleggsFiler(soknadDto)

		val opplastedeVedlegg = alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
		val manglendePakrevdeVedlegg =
			alleVedlegg.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") && (it.opplastingsStatus == OpplastingsStatusDto.sendSenere || it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) }

		validerAtSoknadHarEndringSomKanSendesInn(soknadDto, opplastedeVedlegg, alleVedlegg)

		// Verifiser at total størrelse på vedlegg som skal sendes inn ikke overskrider maxFileSizeSum
		Validerer().validerStorrelse(
			soknadDto.innsendingsId!!,
			filService.finnFilStorrelseSum(soknadDto, opplastedeVedlegg),
			0,
			restConfig.maxFileSizeSum.toLong(),
			ErrorCode.FILE_SIZE_SUM_TOO_LARGE,
		)

		logger.info("${soknadDtoInput.innsendingsId}: Opplastede vedlegg = ${opplastedeVedlegg.map { it.vedleggsnr + ':' + it.uuid + ':' + it.opprettetdato + ':' + it.document?.size }}")
		logger.info("${soknadDtoInput.innsendingsId}: Ikke opplastede påkrevde vedlegg = ${manglendePakrevdeVedlegg.map { it.vedleggsnr + ':' + it.opprettetdato }}")

		val kvitteringForArkivering =
			lagInnsendingsKvitteringOgLagre(soknadDto, opplastedeVedlegg, manglendePakrevdeVedlegg)

		// Ekstra sjekk på at søker ikke allerede har sendt inn søknaden. Dette fordi det har vært tilfeller der søker har klart å trigge innsending request av søknaden flere ganger på rappen
		val soknadDb = repo.hentSoknadDb(soknadDto.id!!)
		if (soknadDb.status == SoknadsStatus.Innsendt) {
			logger.warn("${soknadDto.innsendingsId}: Søknad allerede innsendt, avbryter")
			throw IllegalActionException(
				message = "Søknaden er allerede sendt inn. Søknaden er innsendt og kan ikke sendes på nytt.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}

		// send soknadmetada til soknadsmottaker
		try {
			soknadsmottakerAPI.sendInnSoknad(soknadDto, (listOf(kvitteringForArkivering) + opplastedeVedlegg))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			logger.error("${soknadDto.innsendingsId}: Feil ved sending av søknad til soknadsmottaker ${e.message}")
			throw BackendErrorException("Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV", e)
		}

		// oppdater vedleggstabellen med status og innsendingsdato for opplastede vedlegg.
		opplastedeVedlegg.forEach {
			repo.lagreVedlegg(
				mapTilVedleggDb(
					vedleggDto = it,
					soknadsId = soknadDto.id!!,
					url = it.skjemaurl,
					opplastingsStatus = OpplastingsStatus.INNSENDT
				)
			)
		}
		repo.lagreVedlegg(
			mapTilVedleggDb(
				vedleggDto = kvitteringForArkivering,
				soknadsId = soknadDto.id!!,
				url = kvitteringForArkivering.skjemaurl,
				opplastingsStatus = OpplastingsStatus.INNSENDT
			)
		)

		manglendePakrevdeVedlegg.forEach {
			repo.oppdaterVedleggStatus(
				innsendingsId = soknadDto.innsendingsId!!,
				vedleggsId = it.id!!,
				opplastingsStatus = OpplastingsStatus.SEND_SENERE,
				LocalDateTime.now()
			)
		}

		try {
			repo.lagreSoknad(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(message = "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}

		return Pair(opplastedeVedlegg, manglendePakrevdeVedlegg)
	}

	private fun isTilleggsstonad(soknadDto: DokumentSoknadDto): Boolean {
		return tilleggsstonadSkjema.contains(soknadDto.skjemanr)
	}

	private fun addXmlDokumentvariantToSoknad(soknadDto: DokumentSoknadDto): DokumentSoknadDto {

		val jsonVariant = soknadDto.hoveddokumentVariant
		if (jsonVariant == null) {
			logger.warn("${soknadDto.innsendingsId!!}: Json variant av hoveddokument mangler")
			throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")
		}

		// Create dokumentDto for xml variant of main document
		val xmlDocumentVariant = vedleggService.leggTilHoveddokumentVariant(soknadDto, Mimetype.applicationSlashXml)
		//
		val xmlFile = json2Xml(
			soknadDto = soknadDto,
			tilleggstonadJsonObj = convertToJson(
				soknadDto = soknadDto,
				json = filService.hentFiler(
					soknadDto = soknadDto,
					innsendingsId = soknadDto.innsendingsId!!,
					vedleggsId = jsonVariant.id!!,
					medFil = true
				).first().data
			)
		)
		// Persist created xml file
		filService.lagreFil(
			soknadDto,
			FilDto(
				vedleggsid = xmlDocumentVariant.id!!,
				filnavn = soknadDto.skjemanr + ".xml",
				mimetype = Mimetype.applicationSlashXml,
				storrelse = xmlFile.size,
				data = xmlFile,
				opprettetdato = OffsetDateTime.now()
			)
		)
		// Update state of json variant
		vedleggService.endreVedlegg(
			PatchVedleggDto(jsonVariant.tittel, OpplastingsStatusDto.sendesIkke),
			jsonVariant.id!!,
			soknadDto
		)

		return soknadService.hentSoknad(soknadDto.innsendingsId!!)

	}


	private fun validerAtSoknadHarEndringSomKanSendesInn(
		soknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>,
		alleVedlegg: List<VedleggDto>
	) {
		if (soknadDto.erEttersending) {
			validerAtDetErEndringSomKanSendesInnPaEttersending(soknadDto, opplastedeVedlegg, alleVedlegg)
		} else {
			validerAtSoknadKanSendesInn(opplastedeVedlegg)
		}
	}

	private fun validerAtDetErEndringSomKanSendesInnPaEttersending(
		soknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>,
		alleVedlegg: List<VedleggDto>
	) {
		// For å sende inn en ettersendingssøknad må det være lastet opp minst ett vedlegg, eller vært gjort endring på opplastingsstatus på vedlegg
		if ((opplastedeVedlegg.isEmpty() || opplastedeVedlegg.none { !it.erHoveddokument })) {
			val allePakrevdeBehandlet = alleVedlegg
				.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") }
				.none { !(it.opplastingsStatus == OpplastingsStatusDto.innsendt || it.opplastingsStatus == OpplastingsStatusDto.sendesAvAndre || it.opplastingsStatus == OpplastingsStatusDto.lastetOpp) }
			if (allePakrevdeBehandlet) {
				val separator = "\n"
				logger.warn("Søker har ikke lastet opp filer på ettersendingssøknad ${soknadDto.innsendingsId}, " +
					"men det er ikke gjenstående arbeid på noen av de påkrevde vedleggene. Vedleggsstatus:\n" +
					soknadDto.vedleggsListe.joinToString(separator) { it.tittel + ", med status = " + it.opplastingsStatus + "\n" })
			} else {
				throw IllegalActionException(
					message = "Innsending avbrutt da ingen vedlegg er lastet opp. Søker må ha ved ettersending til en søknad, ha lastet opp ett eller flere vedlegg for å kunnne sende inn søknaden",
					errorCode = ErrorCode.SEND_IN_ERROR_NO_CHANGE
				)
			}
		}
	}

	private fun validerAtSoknadKanSendesInn(opplastedeVedlegg: List<VedleggDto>) {
		// For å sende inn en søknad må det være lastet opp en fil på hoveddokumentet
		if ((opplastedeVedlegg.isEmpty() || opplastedeVedlegg.none { it.erHoveddokument && !it.erVariant })) {
			throw IllegalActionException(
				message = "Innsending avbrutt da hoveddokument ikke finnes. Søker må ha lastet opp dokumenter til søknaden for at den skal kunne sendes inn",
				errorCode = ErrorCode.SEND_IN_ERROR_NO_APPLICATION
			)
		}
	}

	@Transactional
	fun sendInnSoknad(soknadDtoInput: DokumentSoknadDto): KvitteringsDto {
		val operation = InnsenderOperation.SEND_INN.name
		val startSendInn = System.currentTimeMillis()

		try {
			val (opplastet, manglende) = sendInnSoknadStart(soknadDtoInput)

			val innsendtSoknadDto = kansellerBrukernotifikasjon(soknadDtoInput)

			ettersendingService.sjekkOgOpprettEttersendingsSoknad(innsendtSoknadDto, manglende, soknadDtoInput)

			return lagKvittering(innsendtSoknadDto, opplastet, manglende)

		} finally {
			innsenderMetrics.operationsCounterInc(operation, soknadDtoInput.tema)
			logger.debug("${soknadDtoInput.innsendingsId}: Tid: sendInnSoknad = ${System.currentTimeMillis() - startSendInn}")
		}
	}

	private fun lagInnsendingsKvitteringOgLagre(
		soknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>,
		manglendeVedlegg: List<VedleggDto>
	): VedleggDto {
		val person = pdlInterface.hentPersonData(soknadDto.brukerId)
		val sammensattNavn = listOfNotNull(person?.fornavn, person?.mellomnavn, person?.etternavn).joinToString(" ")

		val kvittering =
			PdfGenerator().lagKvitteringsSide(soknadDto, sammensattNavn.ifBlank { "NN" }, opplastedeVedlegg, manglendeVedlegg)

		val kvitteringsVedleggDto = VedleggDto(
			id = null,
			uuid = UUID.randomUUID().toString(),
			vedleggsnr = KVITTERINGS_NR,
			tittel = "Kvitteringsside for dokumentinnsending",
			label = "Kvitteringsside for dokumentinnsending",
			beskrivelse = null,
			erHoveddokument = false,
			erVariant = false,
			erPdfa = true,
			erPakrevd = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			opprettetdato = OffsetDateTime.now(),
			innsendtdato = null,
			mimetype = Mimetype.applicationSlashPdf,
			document = null,
			skjemaurl = null
		)

		val kvitteringsVedlegg = repo.lagreVedlegg(
			mapTilVedleggDb(kvitteringsVedleggDto, soknadDto.id!!)
		)
		repo.saveFilDbData(
			soknadDto.innsendingsId!!,
			FilDbData(
				id = null, vedleggsid = kvitteringsVedlegg.id!!,
				filnavn = "kvittering.pdf", mimetype = Mimetype.applicationSlashPdf.value,
				storrelse = kvittering.size,
				data = kvittering, opprettetdato = kvitteringsVedlegg.opprettetdato
			)
		)

		// Oppdaterer kvitteringsvedlegget med ny id fra database
		return kvitteringsVedleggDto.copy(id = kvitteringsVedlegg.id)
	}

	private fun addDummyHovedDokumentToSoknad(soknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val operation = InnsenderOperation.SEND_INN.name

		// Hvis ettersending, så må det genereres et dummy hoveddokument
		val dummySkjema = try {
			PdfGenerator().lagForsideEttersending(soknadDto)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(
				message = "Feil ved generering av forside for ettersendingssøknad ${soknadDto.innsendingsId}",
				cause = e
			)
		}
		val hovedDokumentDto = soknadDto.vedleggsListe.firstOrNull { it.erHoveddokument && !it.erVariant }
			?: lagVedleggDto(
				vedleggService.opprettHovedddokumentVedlegg(
					mapTilSoknadDb(
						soknadDto, soknadDto.innsendingsId!!,
						mapTilSoknadsStatus(soknadDto.status, null)
					),
					KodeverkSkjema(
						tittel = soknadDto.tittel,
						skjemanummer = soknadDto.skjemanr,
						beskrivelse = soknadDto.tittel,
						tema = soknadDto.tema
					)
				), null
			)

		val hovedDokFil = repo.hentFilerTilVedlegg(soknadDto.innsendingsId!!, hovedDokumentDto.id!!)
		if (hovedDokFil.isNotEmpty() && hovedDokFil.first().data != null) {
			return soknadService.hentSoknad(soknadDto.id!!)
		}
		val oppdatertSoknad = soknadService.hentSoknad(soknadDto.id!!)
		filService.lagreFil(
			oppdatertSoknad, FilDto(
				hovedDokumentDto.id!!, null, hovedDokumentDto.vedleggsnr!!, Mimetype.applicationSlashPdf,
				dummySkjema.size, dummySkjema, OffsetDateTime.now()
			)
		)

		return soknadService.hentSoknad(soknadDto.innsendingsId!!)
	}

	private fun lagKvittering(
		innsendtSoknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>, manglendePakrevdeVedlegg: List<VedleggDto>
	): KvitteringsDto {
		val hoveddokumentVedleggsId = innsendtSoknadDto.vedleggsListe.hovedDokument?.id
		val innsendingsId = innsendtSoknadDto.innsendingsId!!

		val hoveddokumentFilId = if (hoveddokumentVedleggsId != null && !innsendtSoknadDto.erEttersending) {
			repo.findAllByVedleggsid(innsendingsId, hoveddokumentVedleggsId).firstOrNull()?.id
		} else {
			null
		}

		val hovedDokumentRef = lenkeTilDokument(
			innsendingsId,
			hoveddokumentVedleggsId,
			hoveddokumentFilId
		)

		val innsendteVedlegg =
			opplastedeVedlegg.filter { !it.erHoveddokument }.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) }

		val skalSendesAvAndre = innsendtSoknadDto.vedleggsListe
			.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.sendesAvAndre }
			.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) }

		val skalEtterSendes = manglendePakrevdeVedlegg.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) }

		return KvitteringsDto(
			innsendingsId = innsendingsId,
			label = innsendtSoknadDto.tittel,
			mottattdato = innsendtSoknadDto.innsendtDato!!,
			hoveddokumentRef = hovedDokumentRef,
			innsendteVedlegg = innsendteVedlegg,
			skalEttersendes = skalEtterSendes,
			skalSendesAvAndre = skalSendesAvAndre,
			ettersendingsfrist = beregnInnsendingsfrist(innsendtSoknadDto)
		)
	}

	private fun beregnInnsendingsfrist(innsendtSoknadDto: DokumentSoknadDto): OffsetDateTime {
		return if (innsendtSoknadDto.erEttersending) {
			innsendtSoknadDto.forsteInnsendingsDato!!.plusDays(
				innsendtSoknadDto.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
			)
		} else {
			innsendtSoknadDto.innsendtDato!!.plusDays(
				innsendtSoknadDto.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
			)
		}
	}

	private fun lenkeTilDokument(innsendingsId: String, vedleggsId: Long?, filId: Long?) =
		if (filId == null) null else "frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId"

	private fun kansellerBrukernotifikasjon(soknadDtoInput: DokumentSoknadDto): DokumentSoknadDto {
		// send brukernotifikasjon ved endring av søknadsstatus til innsendt
		val innsendtSoknadDto = soknadService.hentSoknad(soknadDtoInput.innsendingsId!!)
		logger.info("${innsendtSoknadDto.innsendingsId}: Sendinn: innsendtdato på vedlegg med status innsendt= " +
			innsendtSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.map { it.vedleggsnr + ":" + mapTilLocalDateTime(it.innsendtdato) }
		)
		publiserBrukernotifikasjon(innsendtSoknadDto)
		return innsendtSoknadDto
	}

	private fun publiserBrukernotifikasjon(dokumentSoknadDto: DokumentSoknadDto): Boolean = try {
		brukernotifikasjonPublisher.soknadStatusChange(dokumentSoknadDto)
	} catch (e: Exception) {
		throw BackendErrorException("Feil i ved avslutning av brukernotifikasjon for søknad ${dokumentSoknadDto.tittel}", e)
	}


	fun getFiles(innsendingId: String, uuids: List<String>): List<SoknadFile> {
		val timer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		logger.info("$innsendingId: Skal hente ${uuids.joinToString(",")}")

		try {
			// Sjekk om det er noen hendelser for søknaden
			val hendelseDbData = repo.hentHendelse(innsendingId)
			if (hendelseDbData.isEmpty()) {
				logger.info("$innsendingId: ikke funnet innslag for søknad i hendelsesloggen")
				return emptySoknadFilesWithStatus(uuids, SoknadFile.FileStatus.notfound)
			}

			// Sjekk om søknaden er arkivert
			val erArkivert = hendelseDbData.any { it.hendelsetype == HendelseType.Arkivert }
			if (erArkivert) {
				logger.info("$innsendingId: søknaden er allerede arkivert")
				emptySoknadFilesWithStatus(uuids, SoknadFile.FileStatus.deleted)
			}

			return fetchSoknadFiles(innsendingId, uuids, erArkivert)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(timer)
		}
	}

	private fun fetchSoknadFiles(innsendingId: String, uuids: List<String>, erArkivert: Boolean): List<SoknadFile> {
		try {
			val soknadDbData = repo.hentSoknadDb(innsendingId)
			val vedleggDbDatas = repo.hentAlleVedleggGittSoknadsid(soknadDbData.id!!)
			val soknadUuids = vedleggDbDatas.map { it.uuid }.toList()

			// Sjekk om alle vedlegg finnes for søknaden
			if (uuids.any { !soknadUuids.contains(it) }) {
				logger.warn("$innsendingId: Forsøk på henting av vedlegg som ikke eksisterer for angitt søknad")
				return emptySoknadFilesWithStatus(uuids, SoknadFile.FileStatus.notfound)
			}

			// Sjekk om alle vedlegg er lastet opp
			val mergedVedlegg = mergeOgReturnerVedlegg(innsendingId, uuids, vedleggDbDatas)
			if (mergedVedlegg.any { it.document == null }) {
				logger.warn(
					"$innsendingId: Følgende vedlegg mangler opplastet fil: ${
						mergedVedlegg.filter { it.document == null }.map { it.uuid }
					}. " +
						"Følgende vedlegg har opplastet fil: ${mergedVedlegg.filter { it.document != null }}"
				)
			}

			// Har uuids og matchende vedleggsliste med filene som skal returneres til soknadsarkiverer
			val idResult = mapToSoknadFiles(uuids, mergedVedlegg, erArkivert, innsendingId)

			val hentet = idResult.joinToString(",") { it.id + "-" + it.fileStatus + ": size=" + it.content?.size }
			logger.info("$innsendingId: Hentet $hentet")
			return idResult

		} catch (e: ResourceNotFoundException) {
			logger.info("$innsendingId: søknaden er slettet")
			return emptySoknadFilesWithStatus(uuids, SoknadFile.FileStatus.deleted)
		}
	}

	private fun emptySoknadFilesWithStatus(
		uuids: List<String>,
		fileStatus: SoknadFile.FileStatus
	): List<SoknadFile> {
		return uuids.map {
			SoknadFile(
				id = it,
				content = null,
				createdAt = null,
				fileStatus = fileStatus
			)
		}
			.toList()
	}

	private fun mapToSoknadFiles(
		vedleggUrls: List<String>,
		mergedVedlegg: List<VedleggDto>,
		erArkivert: Boolean,
		innsendingId: String
	): List<SoknadFile> {
		val idResult = vedleggUrls
			.map { uuid ->
				mergedVedlegg.firstOrNull { it.uuid == uuid } ?: VedleggDto(
					tittel = "",
					label = "",
					erHoveddokument = false,
					uuid = uuid,
					erVariant = false,
					erPdfa = false,
					erPakrevd = false,
					opplastingsStatus = OpplastingsStatusDto.ikkeValgt,
					opprettetdato = OffsetDateTime.now(),
					document = null
				)
			}
			.map {
				SoknadFile(
					id = it.uuid!!,
					fileStatus = if (it.document != null && it.document!!.isNotEmpty()) SoknadFile.FileStatus.ok else if (erArkivert) SoknadFile.FileStatus.deleted else SoknadFile.FileStatus.notfound,
					content = it.document,
					createdAt = it.innsendtdato
				)
			}
			.onEach {
				if (it.fileStatus != SoknadFile.FileStatus.ok) {
					logger.info("$innsendingId: Failed to find vedlegg with uuid '${it.id}' in database")
				}
			}
		return idResult
	}

	private fun mergeOgReturnerVedlegg(
		innsendingId: String,
		vedleggsUuids: List<String>,
		soknadVedlegg: List<VedleggDbData>
	): List<VedleggDto> {
		return filService.hentOgMergeVedleggsFiler(
			innsendingId,
			soknadVedlegg.filter { vedleggsUuids.contains(it.uuid) }.toList()
		)

	}

}
