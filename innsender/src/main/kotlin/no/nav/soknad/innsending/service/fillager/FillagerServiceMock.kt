package no.nav.soknad.innsending.service.fillager

import no.nav.soknad.innsending.service.FilValidatorService
import no.nav.soknad.pdfutilities.KonverterTilPdfInterface
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.util.UUID

@Profile("endtoend")
@Service
class FillagerServiceMock(
	private val filValidatorService: FilValidatorService,
	private val konverterTilPdf: KonverterTilPdfInterface,
	): FillagerInterface {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val soknadMap = mutableMapOf<String, Soknad>()
	private val filBlob =  mutableMapOf<String, ByteArray>()

	override fun lagreFil(fil: Resource, vedleggId: String, innsendingId: String, namespace: FillagerNamespace, spraak: String? ): FilMetadata {
		val filtype = filValidatorService.validerFil(
			fil = fil,
			innsendingsId = innsendingId,
			antivirusEnabled = true,
		)
		val filinnholdBytes = fil.contentAsByteArray

		val (filSomPdf, antallSider) = konverterTilPdf.tilPdf(
			filinnholdBytes,
			innsendingId,
			filtype,
			fil.filename ?: "ukjent",
			spraak
		)

		logger.info("$innsendingId: Fil validert ok og konvertert til pdf - filtype: $filtype, antall sider: $antallSider)")
		filValidatorService.validerAntallSider(antallSider)

		val filId = UUID.randomUUID().toString()
		filBlob.put(filId, filSomPdf)
		val filnavn = fil.filename ?: "ukjent"
		val vedlegg = soknadMap.getOrPut(innsendingId) { Soknad() }.vedleggsMap.getOrPut(vedleggId) { Vedlegg() }
		val filMetadata = FilMetadata(filId, vedleggId, innsendingId, filnavn, antallSider, "ukjent", FilStatus.LASTET_OPP)
		vedlegg.filMetadata.put(filId, filMetadata)
		logger.info("$innsendingId: Fil lagret til fillager med id $filId")
		return filMetadata
	}

	override fun hentFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Fil?{
		logger.info("$innsendingId: Henter fil med id $filId fra fillager")
		return Fil(filBlob.get(filId)!!, FilMetadata(filId, "", innsendingId, "", 0, "ukjent", FilStatus.LASTET_OPP))
	}

	override fun hentFilinnhold(filId: String, innsendingId: String, namespace: FillagerNamespace): ByteArray? {
		logger.info("$innsendingId: Henter fil med id $filId fra fillager")
		return filBlob.get(filId)
	}

	override fun oppdaterStatusForInnsending(innsendingId: String, namespace: FillagerNamespace, status: FilStatus) {
		logger.info("$innsendingId: Oppdaterer status for innsending til $status. Ikke implementert i mocken" )
		// Finn og oppdater status for alle filene på alle søknadens vedlegg
	}

	override fun slettFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Boolean {
		logger.info("$innsendingId: Sletter fil med id $filId")
		val result = filBlob.remove(filId)
		soknadMap[innsendingId]?.vedleggsMap?.remove(filId)
		logger.info("$innsendingId: Fil med id $filId slettet fra fillager: ${result?.isNotEmpty()}")
		return result?.isNotEmpty() ?: true
	}

	override fun slettFiler(innsendingId: String, vedleggId: String?, namespace: FillagerNamespace): Boolean {
		logger.info("$innsendingId: Slett alle filer på vedlegg med id $vedleggId")
		val keys = soknadMap[innsendingId]?.vedleggsMap?.get(vedleggId)?.filMetadata?.keys
		logger.info("$innsendingId: Sletter ${keys?.size ?: 0} filer fra vedlegg med id $vedleggId")
		keys?.forEach { filBlob.remove(it) }
		soknadMap[innsendingId]?.vedleggsMap?.remove(vedleggId)
		logger.info("$innsendingId: Slettet alle filer fra vedlegg med id $vedleggId")
		return keys?.isNotEmpty() ?: false
	}

	class Soknad {
		val vedleggsMap = mutableMapOf<String, Vedlegg>()
	}
	class Vedlegg {
		val filMetadata = mutableMapOf<String, FilMetadata>()
	}

}
