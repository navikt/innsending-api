package no.nav.soknad.innsending.rest.sendinn

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.SendinnFilApi
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.FilValidatorService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.pdfutilities.KonverterTilPdfInterface
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class FilRestApi(
	private val soknadService: SoknadService,
	private val tilgangskontroll: Tilgangskontroll,
	private val filService: FilService,
	private val filValidatorService: FilValidatorService,
	private val konverterTilPdf: KonverterTilPdfInterface
) : SendinnFilApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater opplasting av en fil.
	@Timed(InnsenderOperation.LAST_OPP)
	override fun lagreFil(innsendingsId: String, vedleggsId: Long, file: Resource): ResponseEntity<FilDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å lagre fil på vedlegg $vedleggsId til søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val vedleggDto = soknadDto.vedleggsListe.first { it.id == vedleggsId }
		if (vedleggDto == null)
			throw ResourceNotFoundException("Vedlegg $vedleggsId eksisterer ikke for søknad $innsendingsId")

		// Ved opplasting av fil skal den valideres (f.eks. lovlig format, summen av størrelsen på filene på et vedlegg må være innenfor max størrelse).
		filValidatorService.validerFil(file, innsendingsId)
		val opplastet = (file as ByteArrayResource).byteArray

		// Alle opplastede filer skal lagres som flatede (dvs. ikke skrivbar PDF) PDFer.
		val (fil, antallsider) = konverterTilPdf.tilPdf(
			opplastet,
			soknadDto,
			sammensattNavn = null,
			vedleggsTittel = vedleggDto.tittel
		)

		// Lagre
		val lagretFilDto = filService.lagreFil(
			soknadDto,
			FilDto(
				vedleggsid = vedleggsId,
				id = null,
				filnavn = file.filename ?: "",
				mimetype = Mimetype.applicationSlashPdf,
				storrelse = fil.size,
				antallsider = antallsider,
				data = fil,
				opprettetdato = OffsetDateTime.now()
			)
		)

		combinedLogger.log("$innsendingsId: Lagret fil ${lagretFilDto.id} på vedlegg $vedleggsId til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(lagretFilDto)
	}

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater henting av en allerede opplastet fil.
	@Timed(InnsenderOperation.LAST_NED)
	@CrossOrigin
	override fun hentFil(innsendingsId: String, vedleggsId: Long, filId: Long): ResponseEntity<Resource> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å hente fil $filId på vedlegg $vedleggsId til søknad", brukerId)

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		if (!(soknadDto.kanGjoreEndringer ||
				(soknadDto.status == SoknadsStatusDto.Innsendt && soknadDto.vedleggsListe.any { it.id == vedleggsId && it.erHoveddokument && !it.erVariant }))
		) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}

		val filDto = filService.hentFil(soknadDto, vedleggsId, filId)

		combinedLogger.log("$innsendingsId: Hentet fil ${filDto.id} på vedlegg $vedleggsId til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.contentType(MediaType.APPLICATION_PDF)
			.contentLength(filDto.data?.size?.toLong()!!)
			.body(mapTilResource(filDto))
	}

	private fun mapTilResource(filDto: FilDto): Resource {
		if (filDto.data == null) throw ResourceNotFoundException("Fant ikke angitt fil på ${filDto.id}")
		return ByteArrayResource(filDto.data!!)
	}

	@Timed(InnsenderOperation.HENT)
	override fun hentFilInfoForVedlegg(innsendingsId: String, vedleggsId: Long): ResponseEntity<List<FilDto>> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å hente filinfo til vedlegg $vedleggsId til søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val filDtoListe = filService.hentFiler(soknadDto, innsendingsId, vedleggsId)

		combinedLogger.log(
			"$innsendingsId: Hentet informasjon om opplastede filer på vedlegg $vedleggsId til søknad",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(filDtoListe)
	}

	@Timed(InnsenderOperation.SLETT_FIL)
	@CrossOrigin
	override fun slettFil(innsendingsId: String, vedleggsId: Long, filId: Long): ResponseEntity<VedleggDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("Kall for å slette fil $filId på vedlegg $vedleggsId til søknad $innsendingsId", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val vedleggDto = filService.slettFil(soknadDto, vedleggsId, filId)

		combinedLogger.log("$innsendingsId: Slettet fil $filId på vedlegg $vedleggsId til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggDto)
	}


	private fun hentOgValiderSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		if (!soknadDto.kanGjoreEndringer) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}
		return soknadDto
	}
}
